package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Births, families, and who lives where.
 *
 * <p>The single rule that shapes everything: <strong>a family only grows if it has
 * a home with room left in it.</strong> No house, no children. Full house, no
 * children until somebody moves out into a new one.
 *
 * <p>That makes housing construction the pacing mechanism for the whole simulation.
 * Population cannot outrun what the builders have put up.
 *
 * <p>Deterministic throughout — no randomness anywhere. Plain-English write-up in
 * {@code POPULATION.md}.
 */
public final class PopulationPlanner {

    /**
     * Steps a family needs before a birth in a young settlement.
     *
     * <p>Was eight. The base rate matters far less than {@link #CROWDING_SCALE}
     * below, because the problem with the old arrangement was never the number.
     */
    public static final int STEPS_PER_BIRTH = 24;

    /**
     * How many people a settlement adds before a birth takes twice as long.
     *
     * <p>This is the load-bearing one, and it is here because slowing the base
     * rate does not work. Housing supply scales with population, so every new
     * person eventually enables another house, which enables another person:
     * growth is <em>exponential</em>, and dividing an exponent by five buys
     * time and changes nothing. Measured, with the hard cap lifted and births
     * merely five times slower: 7 people at step 250, 51 at 500, 184 at 750,
     * 567 at 1000, and 1069 by 1250 — which is the thousand-person town the cap
     * was put there to prevent, arriving an hour later than it used to.
     *
     * <p>So the rate now falls as the town fills. A family in a hamlet of four
     * has a child in {@link #STEPS_PER_BIRTH} steps; the same family in a town
     * of two hundred takes many times longer. Because the number of families
     * rises with population and the rate per family falls with it, the two
     * cancel: births per step settle roughly constant and the curve is close to
     * a straight line rather than a hockey stick.
     *
     * <p>That is what removing the cap should mean — a village bounded by land,
     * food and time rather than by an arbitrary number it slams into.
     */
    public static final int CROWDING_SCALE = 16;

    /**
     * What a birth actually costs this settlement right now.
     *
     * <p>The base rate, stretched by how crowded the place already is.
     */
    public static int stepsPerBirthIn(int baseSteps, int population) {
        int crowding = 1 + Math.max(0, population) / Math.max(1, CROWDING_SCALE);
        return Math.max(1, baseSteps) * crowding;
    }

    private static final List<String> FAMILY_NAMES = List.of(
            "Baker", "Miller", "Smith", "Cooper", "Fletcher", "Mason", "Turner", "Weaver");

    private static final List<String> GIVEN_NAMES = List.of(
            "Ada", "Bren", "Cyn", "Dov", "Esa", "Fen", "Gil", "Hana", "Ivo", "Jor");

    private PopulationPlanner() {
    }

    /**
     * One population step: clear the dead, settle newcomers, house families,
     * then grow them.
     *
     * <p>Retiring comes first so that a house freed this step is on the market
     * for {@link #assignHomes} in the same step, rather than standing empty
     * until the next one.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        retireEmptyHouseholds(settlement);
        groupUnassignedResidents(settlement);
        assignHomes(settlement);
        rehouseIntoFamilyHomes(settlement);
        growFamilies(settlement, ctx.settings().stepsPerBirth(),
                ctx.settings().maxSettlementPopulation());
    }

    // --- 0. the dead ---

    /**
     * A household with nobody left in it stops being a household, and its house
     * goes back on the market.
     *
     * <p>Death already does this — {@link Settlement#removePerson} drops a
     * household when it takes its last member — but death is not the only thing
     * that empties one. {@link #splitFamilyInto} takes a member out directly,
     * and a family living somewhere that reports a capacity of zero counts as
     * permanently overcrowded, so it sheds a member every birth cycle until
     * there is nobody left. A house the catalog cannot size no longer reports
     * zero — see {@link #capacityOf} — but a house that really does hold nobody
     * still does. That household went on existing, and went on being
     * {@code isHoused()}, which meant {@link #firstVacantHome} counted its house
     * as taken — reserved, in perpetuity, for a family that no longer existed.
     * A town could fill up with houses nobody lived in and nobody could move
     * into.
     *
     * <p>It is also what turned a bookkeeping oddity into a crash: an empty
     * household that is still housed is the one input {@code growFamilies} could
     * not survive.
     */
    private static void retireEmptyHouseholds(Settlement settlement) {
        for (Household household : List.copyOf(settlement.households())) {
            if (household.members().isEmpty()) {
                settlement.removeHousehold(household);
            }
        }
    }

    /**
     * Turns out whoever was living at a home that is no longer there.
     *
     * <p>The mirror of {@link #retireEmptyHouseholds}: that one has a house with
     * nobody left in it, this one has a family with no house left. Both exist to
     * leave the same thing behind — housing books that describe buildings which
     * actually stand. Called by {@link Settlement#removeBuilding}, because a
     * demolition is the only way a home disappears out from under a family.
     *
     * <p>Rehomed where the town has somewhere to put them, and made homeless
     * where it does not. Homeless is not a failure state and is deliberately not
     * dressed up as one: an unhoused family is what housing demand is made of —
     * see {@link Settlement#unhousedHouseholds} — so a town that loses a cottage
     * wants another cottage, which is the whole behavior a demolition ought to
     * produce. Dissolving the family instead would scatter its members back
     * through {@link #groupUnassignedResidents} and throw away the name, the
     * pantry and the growth they had between them.
     *
     * <p>A household that was already empty is retired outright, exactly as it
     * would have been at the top of the next population step. That is the one
     * case where the family really does end here rather than move.
     */
    public static void evict(Settlement settlement, SimPos home) {
        if (home == null) {
            return;
        }
        for (Household household : List.copyOf(settlement.households())) {
            if (!samePlot(home, household.home())) {
                continue;
            }
            if (household.members().isEmpty()) {
                settlement.removeHousehold(household);
                continue;
            }
            // Asked afresh per family, so two households out of one longhouse do
            // not both claim the same empty cottage: the first is recorded in it
            // before the second is asked.
            SimPos moved = firstVacantHome(settlement);
            household.setHome(moved);
            if (moved == null) {
                continue;   // homeless where they stand; there is nowhere to send them
            }
            // Their bodies as well as their address, exactly as every other move
            // in this class does it. Without this a family is recorded as living
            // across town while every member is still standing in the crater,
            // which is where the view layer would put them back.
            for (Person.Id member : household.members()) {
                Person person = settlement.resident(member);
                if (person != null) {
                    person.setPosition(moved);
                }
            }
        }
    }

    /**
     * Whether two addresses name the same plot, whatever height each was read at.
     *
     * <p>The same rule the upgrade path keeps, and for the same reason: a
     * building's x and z are its plot and never move, while its y is wherever
     * the ground turned out to be. A house finished in an unloaded chunk carries
     * an estimated height, a family can be housed in it before anybody has been
     * near it, and {@code setOriginY} then writes the real height when it is
     * drawn — after which the family's recorded address and the building's own
     * differ by a block or two. Matching on all three would quietly fail to
     * evict exactly those families, which is the state this whole method exists
     * to prevent.
     */
    private static boolean samePlot(SimPos a, SimPos b) {
        return a != null && b != null && a.x() == b.x() && a.z() == b.z();
    }

    // --- 1. newcomers ---

    /**
     * Residents who belong to no family yet — people added by a command, or by
     * whatever migration system exists later — are gathered into families rather
     * than each becoming a lone household, which would need one house each.
     */
    private static void groupUnassignedResidents(Settlement settlement) {
        int groupSize = largestHousingCapacity(settlement);
        for (Person person : settlement.residents()) {
            if (belongsToAFamily(settlement, person.id())) {
                continue;
            }
            Household household = lastFamilyWithRoom(settlement, groupSize);
            if (household == null) {
                household = new Household(Household.Id.random(), nextFamilyName(settlement));
                settlement.addHousehold(household);
            }
            household.addMember(person.id());
        }
    }

    // --- 2. housing ---

    /** Unhoused families claim empty houses, in order, until the houses run out. */
    private static void assignHomes(Settlement settlement) {
        for (Household household : settlement.households()) {
            if (household.isHoused()) {
                continue;
            }
            SimPos vacant = firstVacantHome(settlement);
            if (vacant == null) {
                return;
            }
            household.setHome(vacant);
        }
    }

    /**
     * Couples move out of communal housing the moment a family home stands.
     *
     * <p>The bunkhouse shelters the whole party and breeds nobody; cottages are
     * where families begin. Without this the founding household sat in its
     * bunks forever — {@link #assignHomes} only houses the homeless — and the
     * cottages the VILLAGE program raised stood empty while the stage waited
     * on the families they were for.
     */
    private static void rehouseIntoFamilyHomes(Settlement settlement) {
        for (Household household : List.copyOf(settlement.households())) {
            if (!household.isHoused()
                    || settlement.isFamilyHome(household.home())) {
                continue;
            }
            // The building rather than its position, so the beds counted here
            // are the ones the vacancy search counted. Looking the position back
            // up would have been a second scan that could answer differently
            // where two buildings share an origin.
            Building vacant = firstVacantFamilyHome(settlement);
            if (vacant == null) {
                return;
            }
            SimPos door = vacant.origin();
            if (household.size() <= bedsPromisedBy(settlement, vacant.blueprintId())) {
                household.setHome(door);
                for (Person.Id member : household.members()) {
                    Person person = settlement.resident(member);
                    if (person != null) {
                        person.setPosition(door);
                    }
                }
            } else {
                moveCoupleInto(settlement, household, door);
            }
            return;   // one move a step keeps the town legible
        }
    }

    /** Two members found a new household in the vacant family home. */
    private static void moveCoupleInto(Settlement settlement, Household parent, SimPos vacant) {
        Household founded = new Household(Household.Id.random(), nextFamilyName(settlement));
        for (int i = 0; i < 2 && parent.members().size() > 1; i++) {
            Person.Id leaver = parent.members().getLast();
            parent.removeMember(leaver);
            founded.addMember(leaver);
            Person person = settlement.resident(leaver);
            if (person != null) {
                person.setPosition(vacant);
            }
        }
        founded.setHome(vacant);
        settlement.addHousehold(founded);
    }

    /** First unclaimed building a family may grow in, or null. */
    private static Building firstVacantFamilyHome(Settlement settlement) {
        Set<SimPos> taken = spokenFor(settlement);
        for (Building building : settlement.buildings()) {
            // A shed is not a home, and neither is a house the catalog cannot
            // size: an unknown capacity is not a spare bed the town may offer.
            if (bedsPromisedBy(settlement, building.blueprintId()) <= 0
                    || !settlement.isFamilyHome(building.origin())
                    || taken.contains(plotOf(building.origin()))) {
                continue;
            }
            return building;
        }
        return null;
    }

    /**
     * The plots families have already claimed.
     *
     * <p>Plots, not positions, and that is the whole of it — see
     * {@link #samePlot}. A family housed in a cottage the world had not yet
     * drawn holds the estimated height; drawing the cottage writes the real one
     * into the building and not into the family. Comparing the two in full then
     * reports an occupied house as empty, and the next family to want one is
     * moved in on top of the one already living there — with the older family's
     * capacity reading zero afterwards, which reads as permanently overcrowded
     * and stops it growing.
     *
     * <p>A set rather than a scan, because {@link #assignHomes} asks once per
     * unhoused family per step and a scan makes that quadratic in the size of
     * the town.
     */
    private static Set<SimPos> spokenFor(Settlement settlement) {
        Set<SimPos> taken = new HashSet<>();
        for (Household household : settlement.households()) {
            if (household.isHoused()) {
                taken.add(plotOf(household.home()));
            }
        }
        return taken;
    }

    /** An address with its height dropped, so two of them compare as plots. */
    private static SimPos plotOf(SimPos at) {
        return new SimPos(at.x(), 0, at.z());
    }

    // --- 3. growth ---

    private static void growFamilies(Settlement settlement, int stepsPerBirth, int populationCap) {
        // Copied because splitting appends to the household list as we iterate.
        for (Household household : List.copyOf(settlement.households())) {
            if (!household.isHoused()) {
                continue;
            }

            // The bunkhouse shelters everyone and breeds no one — communal
            // bunks are a stage, not a destination. Gating births on a family
            // home is what makes the cottage unlock at VILLAGE a real unlock,
            // and it is why a founding party stays a founding party.
            if (!settlement.isFamilyHome(household.home())) {
                continue;
            }

            // A household nobody is left in is not a family that can grow or
            // split, it is the record of one that died out. Both paths below
            // reach into members() for a first or last member and neither
            // survives finding none: this crashed the server tick outright and
            // every tick after it, because a house whose capacity read zero
            // also read as full, and a full house sends somebody out. The
            // capacity half of that is fixed further down; this guard stays,
            // because a household can still be emptied by a house that is
            // genuinely full.
            if (household.members().isEmpty()) {
                continue;
            }

            // Stretched by how full the town already is -- see CROWDING_SCALE.
            // A hamlet grows quickly and a town barely at all, which is what
            // keeps the curve from running away now the hard cap is gone.
            int needed = stepsPerBirthIn(stepsPerBirth, settlement.population());
            if (household.growthProgress() < needed) {
                household.addGrowthProgress(1);
            }
            if (household.growthProgress() < needed) {
                continue;
            }

            // The settlement is full. Housing scales with population, so without
            // this ceiling growth is exponential and never stops. Progress holds
            // at the threshold, so growth resumes the moment deaths make room.
            if (settlement.population() >= populationCap) {
                continue;
            }

            // And a hungry town does not grow: births wait for banked food, so
            // the fields pace the village as much as the housing does.
            if (!FoodPlanner.canFeedAnotherMouth(settlement)) {
                continue;
            }

            // A standing house the catalog cannot size gets no decision made
            // about it at all. This used to read zero, and zero reads as full —
            // `size() < 0` is false — so a family in a renamed cottage, or one
            // from a mod no longer loaded, or a save older than the entry,
            // counted as permanently overcrowded and shed a member into every
            // vacancy that appeared. Measured on the fixture in
            // UnknownCapacityTest, with four empty houses standing to shed into:
            // three members at step 0, two at 24, one at 48, the household gone
            // by 72.
            //
            // Neither a birth, then, nor a shedding. Not a birth because a town
            // that cannot count the beds in a house cannot promise there is a
            // spare one, which is the same reading that keeps such a house off
            // the vacancy list. Growth progress is banked and held exactly as it
            // is for a full house with nowhere to move to, so if the catalog
            // entry comes back — a datapack reloaded, a mod put back — the child
            // arrives on the next step rather than the clock starting again.
            OptionalInt capacity = capacityOfHome(settlement, household);
            if (capacity.isEmpty()) {
                continue;
            }
            if (household.size() < capacity.getAsInt()) {
                household.resetGrowthProgress();
                bearChild(settlement, household);
                continue;
            }

            // The house is full. Somebody moves out — but only if there is
            // somewhere to move to. Otherwise the family waits, holding its
            // progress, and splits the moment a house becomes available.
            SimPos vacant = firstVacantHome(settlement);
            if (vacant != null) {
                household.resetGrowthProgress();
                splitFamilyInto(settlement, household, vacant);
            }
        }
    }

    /**
     * Children take the job the settlement is most short of, falling back to the
     * family trade when nothing is short. This is how a town founded entirely by
     * builders grows its own guards and farmers — see {@link JobPlanner}.
     */
    private static void bearChild(Settlement settlement, Household household) {
        Person elder = settlement.resident(household.members().getFirst());
        Profession trade = JobPlanner.mostNeeded(settlement)
                .orElseGet(() -> elder != null ? elder.profession() : Profession.IDLER);

        Person child = new Person(
                Person.Id.random(),
                givenName(settlement, household.size()) + " " + household.name(),
                trade,
                household.home());

        settlement.addResident(child);
        household.addMember(child.id());
    }

    /** The most recently added member leaves to found a family in the empty house. */
    private static void splitFamilyInto(Settlement settlement, Household parent, SimPos vacant) {
        Person.Id leaver = parent.members().getLast();
        parent.removeMember(leaver);
        if (parent.members().isEmpty()) {
            // The last one out. Taking a member straight off a household is the
            // one path that can empty it without going through removePerson, so
            // this is where a family quietly became a reserved empty house.
            settlement.removeHousehold(parent);
        }

        Household founded = new Household(Household.Id.random(), nextFamilyName(settlement));
        founded.addMember(leaver);
        founded.setHome(vacant);
        settlement.addHousehold(founded);

        Person person = settlement.resident(leaver);
        if (person != null) {
            person.setPosition(vacant);
        }
    }

    // --- helpers ---

    /**
     * The catalog entry that describes this blueprint, or empty when the
     * catalog has never heard of it.
     *
     * <p>Matched the way {@link BuildingRole} matches: on the building's own
     * name, with namespace, culture folder and level suffix all stripped, so
     * {@code kingdoms:house}, {@code kingdoms:house_l2} and
     * {@code kingdoms:norman/house} are one building at three addresses. That
     * matters here more than anywhere, because the whole point of the method
     * below is to say when the catalog genuinely has nothing — and a raised
     * or styled house is not nothing, it is the same house written differently.
     * The exact id is tried first and on its own, so a datapack that really does
     * list two folders separately keeps them apart — and so the ordinary case,
     * where every id in play is the plain one, never takes a substring of
     * anything.
     *
     * <p>{@link Settlement#isFamilyHome} was widened to match too, or a culture's
     * own bunkhouse would have been found here, reported six beds, and been
     * offered to families as somewhere to breed. Three further places do this
     * lookup by hand with only {@link BuildPlanner#baseIdOf} —
     * {@link RaidPlanner#defenseBonusOf}, {@link BuildPlanner#chooseUpgrade} and
     * {@code upgradePriority}. Those are deliberately left alone: widening what
     * they match would move defense numbers and upgrade eligibility, which is
     * not this change.
     */
    private static Optional<BuildingType> typeOf(Settlement settlement, String blueprintId) {
        if (blueprintId == null) {
            return Optional.empty();
        }
        String exactId = BuildPlanner.baseIdOf(blueprintId);
        for (BuildingType type : settlement.catalogue()) {
            if (type.id().equals(exactId)) {
                return Optional.of(type);
            }
        }
        String bareName = BuildingRole.bareName(blueprintId);
        for (BuildingType type : settlement.catalogue()) {
            if (BuildingRole.bareName(type.id()).equals(bareName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * How many people the catalog says live in this blueprint, or empty when
     * the catalog cannot say.
     *
     * <p><strong>Empty is not zero.</strong> Zero is a shed: the catalog has
     * been asked and has answered that nobody lives there. Empty is a renamed
     * cottage, a building from a mod that is no longer loaded, a save older than
     * the entry — the town has no opinion, and every caller has to say what it
     * does about that rather than inherit one from an {@code orElse(0)}.
     *
     * <p>An {@link OptionalInt} rather than a sentinel because a sentinel would
     * not have fixed anything: {@code -1} read through {@code size() < capacity}
     * is still "full", which is the exact fault, and it would have slipped
     * silently past every comparison in this file that nobody remembered to
     * update. This will not compile as a number.
     */
    public static OptionalInt capacityOf(Settlement settlement, String blueprintId) {
        Optional<BuildingType> type = typeOf(settlement, blueprintId);
        return type.isPresent() ? OptionalInt.of(type.get().capacity()) : OptionalInt.empty();
    }

    /**
     * Beds in the house this family lives in, or empty when the catalog cannot
     * size the building standing there.
     *
     * <p>Empty means one thing only: <em>a building is standing on this spot and
     * the town cannot say how many live in it.</em> A family with no home at all,
     * and a family whose recorded home has no building on it, both answer zero,
     * which is what they answered before and what they still mean — no house, no
     * beds. Those are facts; the unrecognized blueprint is the shrug.
     *
     * <p>The distinction is worth the extra branch. Both zero cases end in the
     * family shedding a member into real housing and the household eventually
     * being retired, which is a recovery: the people end up in houses that exist.
     * Reading them as unknown would freeze such a family at a phantom address
     * forever — never grown, never split, never rehoused, because
     * {@link Settlement#isFamilyHome} answers {@code true} for a spot it has no
     * record of and nothing else ever clears a home. That is a worse answer than
     * the one being replaced.
     */
    public static OptionalInt capacityOfHome(Settlement settlement, Household household) {
        if (!household.isHoused()) {
            return OptionalInt.of(0);
        }
        Building standing = settlement.buildingAt(household.home());
        return standing == null
                ? OptionalInt.of(0)
                : capacityOf(settlement, standing.blueprintId());
    }

    /**
     * Beds a caller may count on, which is none unless the catalog said so.
     *
     * <p>Reading unknown as zero is safe here for a reason the comparisons in
     * {@link #growFamilies} do not share: nothing is being done <em>to</em> a
     * household. It is a count of beds the town can offer, and a house the town
     * cannot size offers none — the same answer a shed gets, arrived at honestly
     * rather than by accident.
     *
     * <p>Public so {@link Founding} states the policy by name instead of keeping
     * its own {@code orElse(0)}. That copy is how the next caller would have
     * learned the wrong lesson from a change whose whole argument is that callers
     * must choose.
     */
    public static int bedsPromisedBy(Settlement settlement, String blueprintId) {
        return capacityOf(settlement, blueprintId).orElse(0);
    }

    /** First house nobody has claimed, or null if the settlement is fully occupied. */
    public static SimPos firstVacantHome(Settlement settlement) {
        Set<SimPos> taken = spokenFor(settlement);
        for (Building building : settlement.buildings()) {
            // As above: unknown is not an offer of a bed.
            if (bedsPromisedBy(settlement, building.blueprintId()) <= 0) {
                continue;
            }
            if (!taken.contains(plotOf(building.origin()))) {
                return building.origin();
            }
        }
        return null;
    }

    /** Total beds in the settlement, housed or not. */
    public static int totalHousingCapacity(Settlement settlement) {
        return settlement.buildings().stream()
                .mapToInt(building -> bedsPromisedBy(settlement, building.blueprintId()))
                .sum();
    }

    private static int largestHousingCapacity(Settlement settlement) {
        return settlement.catalogue().stream()
                .filter(BuildingType::isHousing)
                .mapToInt(BuildingType::capacity)
                .max()
                .orElse(1);
    }

    private static boolean belongsToAFamily(Settlement settlement, Person.Id personId) {
        return settlement.households().stream().anyMatch(h -> h.contains(personId));
    }

    private static Household lastFamilyWithRoom(Settlement settlement, int groupSize) {
        List<Household> households = settlement.households();
        if (households.isEmpty()) {
            return null;
        }
        Household last = households.getLast();
        return last.size() < groupSize ? last : null;
    }

    /**
     * The next family name this town has not used, in its own language.
     *
     * <p>Read from the settlement's culture rather than the one list everybody
     * used to share. A goblin warren whose families were called Baker and
     * Cooper was the clearest sign that culture reached the beasts in the pens
     * and almost nothing else.
     */
    private static String nextFamilyName(Settlement settlement) {
        List<String> pool = namesOr(Culture.of(settlement.cultureId()).familyNames(),
                FAMILY_NAMES);
        int index = settlement.households().size();
        String base = pool.get(index % pool.size());
        int wrap = index / pool.size();
        return wrap == 0 ? base : base + " " + (wrap + 1);
    }

    private static String givenName(Settlement settlement, int index) {
        List<String> pool = namesOr(Culture.of(settlement.cultureId()).givenNames(),
                GIVEN_NAMES);
        return pool.get(index % pool.size());
    }

    /** A culture's own list, or the lowland one if it never defined any. */
    private static List<String> namesOr(List<String> pool, List<String> fallback) {
        return pool == null || pool.isEmpty() ? fallback : pool;
    }
}
