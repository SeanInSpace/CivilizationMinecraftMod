package com.kingdoms.sim.settlement;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a charter actually creates.
 *
 * <p>Lives here rather than in the item because a founding party is a fact
 * about the simulation, not about right-clicking. It was written out longhand
 * inside {@code FoundingCharterItem}, which meant the one path a player can
 * actually take was also the one path no test could reach — and meant
 * {@code /civ found}, which every playtest and every scripted run goes through,
 * quietly created something else entirely: a settlement with a kit and nobody
 * to spend it. Towns founded that way sat at population zero forever, and the
 * scripts had to follow with {@code /civ populate} to paper over it.
 *
 * <p>Both now come through here, so a headless run founds the same party a
 * player's charter does.
 */
public final class Founding {

    /**
     * The claim a brand-new settlement lays around its center.
     *
     * <p>The same for a charter, a console command and a daughter colony —
     * three places that had each written 64 down separately.
     */
    public static final int INITIAL_CLAIM = 64;

    /**
     * How far a town founded by the world itself may be moved to find ground.
     *
     * <p>A daughter colony or a generated town has no opinion about where it
     * lands, so it may look properly.
     */
    public static final int SITING_REACH = 64;

    /**
     * How far a town founded by a person may be moved.
     *
     * <p>Much less, and the difference is the whole point. Somebody who plants
     * a charter has chosen that spot — for the view, for the river, for reasons
     * the simulation cannot see — and a settlement that appears sixty blocks
     * away has overruled them. A dozen blocks is the difference between
     * "shifted off the cliff edge" and "went somewhere else".
     */
    public static final int CHARTER_REACH = 12;

    /** How wide a patch is judged when weighing a site. */
    private static final int SITE_RADIUS = 24;

    /** How far apart the candidate centers are tried. */
    private static final int SITE_STEP = 8;

    private Founding() {
    }

    /**
     * The best ground for a town within reach of where one was wanted.
     *
     * <p>Founding never looked at the ground at all. A town planted across a
     * ravine fights it forever: every street it plans runs into the cut, every
     * plot on the far side is refused, and no amount of cleverness downstream
     * recovers what choosing eight blocks to the left would have given for
     * nothing. The roads work that prompted this could route around a hillside
     * and could not undo having been founded on one.
     *
     * <p>Judged on the two things that actually stop a town building: standing
     * water, and how far the ground falls across the patch a town first fills.
     * Measured on the bulk rather than the extremes — the twentieth and
     * eightieth percentiles, the same rule the siting code uses — so a single
     * boulder does not condemn a shelf and a genuine slope is not excused by
     * flat ground either side of it.
     *
     * <p>Ties go to staying put: the score a candidate must beat includes how
     * far it has strayed, so a town only moves when moving is clearly better.
     * With no bridge to ask, it does not move at all.
     */
    public static SimPos bestSiteNear(SimPos wanted, int reach,
                                      com.kingdoms.sim.platform.WorldBridge ground) {
        if (ground == null || reach <= 0) {
            return wanted;
        }
        SimPos best = wanted;
        double bestScore = scoreSite(wanted, ground);
        for (int dz = -reach; dz <= reach; dz += SITE_STEP) {
            for (int dx = -reach; dx <= reach; dx += SITE_STEP) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double away = Math.hypot(dx, dz);
                if (away > reach) {
                    continue;   // a disc, not a box: a corner is not "near"
                }
                SimPos candidate = new SimPos(
                        wanted.x() + dx, wanted.y(), wanted.z() + dz);
                // Strayed ground has to be better by more than it has strayed.
                double score = scoreSite(candidate, ground) + away * DRIFT_PENALTY;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best.equals(wanted) ? wanted
                : new SimPos(best.x(), ground.surfaceHeight(best), best.z());
    }

    /** What a stretch of ground costs a town, lower being better. */
    private static double scoreSite(SimPos centre,
                                    com.kingdoms.sim.platform.WorldBridge ground) {
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        int wet = 0;
        int samples = 0;
        for (int dz = -SITE_RADIUS; dz <= SITE_RADIUS; dz += SITE_STEP) {
            for (int dx = -SITE_RADIUS; dx <= SITE_RADIUS; dx += SITE_STEP) {
                SimPos at = new SimPos(centre.x() + dx, centre.y(), centre.z() + dz);
                heights.add(ground.groundHeight(at));
                samples++;
                if (ground.standsInWater(at, 0)) {
                    wet++;
                }
            }
        }
        java.util.Collections.sort(heights);
        int low = heights.get(heights.size() / 5);
        int high = heights.get((heights.size() * 4) / 5);
        double fall = high - low;
        double drowned = samples == 0 ? 0 : (double) wet / samples;
        // Water is worse than slope: a town can terrace a hillside and cannot
        // drain a lake.
        return fall + drowned * DROWNED_PENALTY;
    }

    /** What a block of straying costs, against a course of fall. */
    private static final double DRIFT_PENALTY = 0.06;

    /** What being wholly under water costs, against courses of fall. */
    private static final double DROWNED_PENALTY = 60;

    /**
     * A settlement as a charter makes one: a camp, a party, and rations.
     *
     * <p>Pioneers, all of them — generalists who build, farm and haul as the
     * camp needs, with professions crystallizing as the stages demand them. A
     * party split into half builders and half idlers is how one ended up with
     * idlers it could not turn into farmers.
     *
     * <p>The food is carried by the settlers rather than banked, because until
     * the first house stands there is no larder to fetch from: what they have
     * on them is what they live on. The timber and stone come from
     * {@link TownStores#founding}, laid on open ground until somebody raises a
     * store to put it in.
     */
    public static Settlement party(SimPos site, String name) {
        Settlement settlement = new Settlement(Settlement.Id.random(), name, site, INITIAL_CLAIM);
        // Fresh foundings live the ladder. Only loaded saves default to TOWN.
        settlement.setStage(SettlementStage.CAMP);
        for (int i = 0; i < TownStores.FOUNDING_SETTLERS; i++) {
            Person settler = new Person(Person.Id.random(), "Settler " + (i + 1),
                    Profession.PIONEER, site);
            settler.inventory().add(Foods.PROVISION, TownStores.FOUNDING_PROVISIONS_EACH);
            settlement.addResident(settler);
        }
        return settlement;
    }

    // --- a settlement that skipped the road ---

    /**
     * Pass this as the resident count to take whatever the stage houses.
     *
     * <p>Zero rather than a magic negative because "no opinion" is what the
     * caller actually has, and the count is otherwise a positive number.
     */
    public static final int AS_THE_STAGE_HOUSES = 0;

    /**
     * The step a seeded building is recorded as having been finished on.
     *
     * <p>Zero, because it was not finished on any step: nothing built it. The
     * field is only ever read for "raised N steps ago" in reports, and claiming
     * a step that never happened would be worse than claiming the first one.
     */
    private static final long BEFORE_THE_FIRST_STEP = 0;

    /**
     * Courses a seeded building claims before anybody has measured it.
     *
     * <p>The same story {@code Settlement.expectedFootprint} assumes for a
     * building finished out of sight, and for the same reason: nothing reads
     * the height until the structure is drawn, and a precise number nobody
     * checked would be worse than a plain one.
     */
    private static final int A_STOREY = 4;

    /**
     * How many plots a seeded town asks its plan for.
     *
     * <p>The whole cumulative program is fifteen buildings; this is room for
     * that with a wide margin, and it costs nothing — a planned layout lays two
     * hundred and fifty-six plots however few are asked for and hands back a
     * prefix.
     */
    private static final int PLOTS_ENOUGH_FOR_ANY_PROGRAMME = 64;

    /**
     * A settlement that is already what a founding party spends four hundred
     * steps becoming.
     *
     * <p>This is what world generation needs and {@link #party} cannot give it.
     * A town discovered by a player should be <em>alive</em> — standing, staffed
     * and stocked — rather than four settlers who happen to have been placed
     * early, and it must then go on living: the bar is that the result is
     * indistinguishable, to every planner that reads it, from one that climbed
     * the ladder honestly.
     *
     * <p>So nothing here invents a parallel idea of what a stage is. The
     * buildings are whatever {@link StagePlanner#nextProgramWant} asks for,
     * asked repeatedly with each stage set in turn, which is exactly the
     * sequence {@code planNextBuild} would have ordered — including skipping
     * program entries this catalog has never heard of. The professions are
     * whatever {@link StagePlanner#crystallize} and {@link JobPlanner} name. The
     * plots are the town plan's own, taken nearest-center first, so the town
     * fills outward from the post it staked on the day it arrived.
     *
     * <p><strong>The hall is not in the middle</strong>, and that is the
     * program's doing rather than an oversight. It is the last entry of the
     * last stage — the capstone the whole staging design exists to hold back —
     * so the plots nearer the center are long since taken by the camp post, the
     * cache and the bunkhouse, and the hall stands at the frontier of what the
     * town has built. That is also where an unwalled town would have put it: a
     * grown one lands its hall nearer the middle only because by TOWN it has a
     * perimeter, and {@code Settlement.chooseSite} rescans from the center for
     * civic buildings once there is a ring to stay inside. A seeded town has no
     * ring yet, so it has no such gap to fill.
     *
     * <p><strong>The programs are cumulative.</strong> A village did not skip
     * its bunkhouse on the way past, so a seeded one has not either. What it
     * does <em>not</em> have is anything the catalog scan would have added
     * along the way — a second granary, the houses a growing town raises — and
     * that is deliberate: the program is a definition, and "whatever the scan
     * happened to want at the time" is not. The town raises those itself,
     * starting on its first step, which is the point.
     *
     * <p><strong>A seeded stage is a stage about to end.</strong> Standing the
     * whole of a stage's program is, by {@link StagePlanner#readyToAdvance}'s
     * own definition, most of what it takes to leave that stage — so a seeded
     * CAMP is a HOMESTEAD one step later, a seeded FORTIFIED a VILLAGE, and a
     * seeded VILLAGE a TOWN. That is not a fault to be papered over: it is
     * exactly what an honestly-grown town does on the step after it finishes
     * its program, and the bar this method is held to is being
     * indistinguishable from one. The alternative — seeding only the stages
     * <em>below</em> the one asked for — would hand back a "village" with no
     * cottages, no market and no mill, which is a fortified camp wearing a
     * label. Only HOMESTEAD holds, because its gate is a fed streak the town
     * has to earn in front of you.
     *
     * <p>The buildings are recorded <em>unmaterialized</em>. A seeded town is
     * data until somebody is near enough to see it, and
     * {@code Settlement.materializePending} is what draws it — the same path a
     * town grown while the player was away comes down. Nothing here loads a
     * chunk, and nothing here decides where the ground is: the origins carry the
     * center's height as an estimate and are marked unsurveyed, so the placement
     * pass may still move one off a river before a single block is laid.
     *
     * @param stage     what the town has already become
     * @param catalogue what it knows how to build
     * @param cultureId whose town it is — and therefore, through the
     *                  arrangement, where every building stands. A caller that
     *                  hands this to a kingdom afterwards must pass the
     *                  kingdom's culture here; see the note on {@code /civ seed}.
     */
    public static Settlement seeded(SimPos site, String name, SettlementStage stage,
                                    List<BuildingType> catalogue, String cultureId) {
        return seeded(site, name, stage, catalogue, cultureId, AS_THE_STAGE_HOUSES);
    }

    /**
     * The same, with the population named rather than derived.
     *
     * @param residents how many people live here, or {@link #AS_THE_STAGE_HOUSES}
     *                  for as many as the stage's program has beds for
     */
    public static Settlement seeded(SimPos site, String name, SettlementStage stage,
                                    List<BuildingType> catalogue, String cultureId,
                                    int residents) {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(stage, "stage");
        Settlement town = new Settlement(Settlement.Id.random(), name, site, INITIAL_CLAIM);
        // Culture before anything else: the arrangement is read from it, and
        // every plot below comes out of that arrangement's plan.
        town.setCultureId(cultureId);
        town.setCatalogue(Objects.requireNonNull(catalogue, "catalogue"));

        raiseTheProgrammes(town, stage);
        town.setStage(stage);
        town.setClaimRadius(claimAround(town));
        settlePeople(town, residents);
        stockTheStores(town);
        nameTheTrades(town, stage);
        return town;
    }

    /**
     * Every stage's program up to this one, standing, in the order they were
     * wanted.
     *
     * <p>The stage is set and re-asked rather than the programs being read out
     * of {@link StagePlanner}, because the programs are private and should
     * stay that way: one place decides what a stage builds, and a second copy of
     * that list would be wrong the first time somebody edited the first.
     */
    private static void raiseTheProgrammes(Settlement town, SettlementStage upTo) {
        TownPlan plan = town.arrangement()
                .planFor(town.centre(), PLOTS_ENOUGH_FOR_ANY_PROGRAMME);
        int taken = 0;
        for (SettlementStage reached : SettlementStage.values()) {
            town.setStage(reached);
            // Bounded by the plan rather than by the program: a want that
            // could not be placed must end the loop, or a plan smaller than the
            // program would spin here forever.
            while (taken < plan.size()) {
                Optional<BuildingType> want = StagePlanner.nextProgramWant(town);
                if (want.isEmpty()) {
                    break;
                }
                taken = roomFor(town, want.get(), plan, taken);
                if (taken >= plan.size()) {
                    // The plan ran out before the program did. Nothing today
                    // reaches this — the ring layout spends nineteen of sixty-four
                    // plots on the whole fifteen-building program — but the
                    // program, the plot spans and the per-culture layouts all
                    // grow independently, and a town that quietly reports itself
                    // a TOWN with no hall is a fault nobody can trace. So it says
                    // so, in the settlement's own history, where /civ info shows it.
                    town.logEvent(BEFORE_THE_FIRST_STEP, "Seeded short: the "
                            + reached.pretty() + " program wanted a "
                            + want.get().id() + " and the plan had no room left");
                    break;
                }
                town.addBuilding(standing(town, want.get(), plan.plot(taken++)));
                town.tallies().record(Tallies.BUILDINGS_RAISED);
            }
            if (reached == upTo) {
                break;
            }
        }
        // The cursor the ordinary siting code carries on from. Without this the
        // town's next build starts at plot zero and spends its first dozen
        // attempts discovering that its own buildings are already there.
        town.setNextPlotIndex(taken);
    }

    /**
     * The first plot from here on that this building actually fits on.
     *
     * <p>A plan keeps its plots {@code Layout.MIN_PLOT_SEPARATION} apart, and
     * that is stated for two plots of the default span. A farm is fifteen across
     * and a hall thirteen, so a pair of them offered adjacent plots fouls each
     * other — the plan says as much, and the siting loop's answer is to burn the
     * offer and take the next one. Walking the plan without asking produced a
     * seeded homestead with its farm through its granary.
     *
     * <p>Burning rather than reconsidering, exactly as {@code chooseSite} does: a
     * plot passed over is spent, so nothing comes back to it and the cursor only
     * ever moves outward.
     */
    private static int roomFor(Settlement town, BuildingType type, TownPlan plan, int from) {
        int span = BuildPlanner.plotSpanOf(type.id(), town.catalogue());
        int at = from;
        while (at < plan.size() && !town.isPlotFree(plan.plot(at).at(), span, null)) {
            at++;
        }
        return at;
    }

    /** One building of the program, as the unwatched clock would have left it. */
    private static Building standing(Settlement town, BuildingType type, TownPlan.Plot plot) {
        Building raised = new Building(type.id(), plot.at(), BEFORE_THE_FIRST_STEP, false);
        raised.setFacing(plot.facing());
        int span = BuildPlanner.plotSpanOf(type.id(), town.catalogue());
        raised.setFootprint(new Footprint(plot.at().y(), span, span, A_STOREY));
        // Left unsurveyed on purpose. Surveyed means somebody stood on this
        // ground and worked to it; nobody has, so the height is an estimate and
        // relocatePending is still allowed to move the building off a river.
        return raised;
    }

    /** Far enough out to hold everything the programs put on the ground. */
    private static int claimAround(Settlement town) {
        int reach = INITIAL_CLAIM;
        for (Building standing : town.buildings()) {
            reach = Math.max(reach, BuildPlanner.claimRadiusFor(
                    town.centre(), standing.origin(), town.arrangement().claimMargin()));
        }
        return reach;
    }

    /**
     * The people, a family to every dwelling, in the number the stage has beds
     * for.
     *
     * <p>Housing is what actually paces a growing town — a family in a full
     * house cannot bear and cannot split — so the beds the program raised are
     * the population that town would have converged on. A camp houses nobody at
     * all, hence the floor at the charter party: four settlers in the open is a
     * camp, and zero is nothing.
     *
     * <p><strong>Families before people, so a family shares a name.</strong>
     * Making the residents first and grouping them afterwards is the obvious
     * order and produces households whose members are called something else —
     * a family named Miller with three Bakers living in it, whose children
     * {@code PopulationPlanner.bearChild} then names Miller. A person's name is
     * final, so the only way to have them agree is to know the household before
     * the person exists.
     *
     * <p>{@code PopulationPlanner} would house them on the first step anyway,
     * and cruder — it groups by the <em>largest</em> capacity in the catalog
     * and then houses whoever, so a party of twelve becomes two families of six
     * and one is put in a three-bed cottage it immediately has to shed people
     * out of. A town that has been lived in does not look like that.
     *
     * <p>Anyone the dwellings cannot take stays unhoused, which is a real state
     * and the one the ordinary planner is for.
     *
     * <p>Everyone arrives a pioneer and carries their own rations, exactly as a
     * chartered party does. The rations are not generosity: below
     * {@link Person#HUNGER_SEVERE} nobody helps themselves from the granary, so
     * a town seeded with empty pockets walks its whole population through the
     * too-weak-to-work band before it eats for the first time.
     */
    private static void settlePeople(Settlement town, int wanted) {
        int souls = wanted > 0 ? wanted
                : Math.max(TownStores.FOUNDING_SETTLERS,
                        PopulationPlanner.totalHousingCapacity(town));
        List<String> given = namesOr(Culture.of(town.cultureId()).givenNames(), "Settler");
        List<String> surnames = namesOr(Culture.of(town.cultureId()).familyNames(), "Baker");
        int born = 0;
        for (Building home : town.buildings()) {
            // A founding raises its buildings from its own catalog, so nothing
            // here should be unsizeable; if one ever is, nobody is seeded into
            // it, which is the same answer a shed gets.
            int beds = PopulationPlanner.bedsPromisedBy(town, home.blueprintId());
            if (beds <= 0 || born >= souls) {
                continue;
            }
            Household family = new Household(Household.Id.random(),
                    familyName(surnames, town.households().size()));
            for (int bed = 0; bed < beds && born < souls; bed++) {
                town.addResident(person(family, given, born++, home.origin()));
            }
            family.setHome(home.origin());
            // A larder in the house, because a lived-in house has one. The
            // chain that fills it — granary to stall to pantry — is an errand a
            // settled town has been running for as long as it has stood.
            family.setPantry(family.size() * FoodPlanner.PANTRY_PER_MEMBER);
            town.addHousehold(family);
        }
        // Whoever the dwellings could not take, which for a camp is everybody.
        // Gathered into families the size of the largest house in the
        // catalog, which is exactly what PopulationPlanner would do with them
        // on the first step -- so the town reads right straight away rather
        // than one step later, and reads the same either way.
        //
        // Capped rather than poured into one household, and the cap is
        // load-bearing. /civ seed exposes a count of up to two hundred: a
        // hundred and eighty-eight unhoused people in a single family is a
        // state the ordinary planner cannot produce, and two things break on
        // it. assignHomes houses a household without checking its size, so that
        // family "moves into" the first four-bed house the town raises and
        // StagePlanner.familyHoused then counts all hundred and eighty-eight as
        // housed -- a village graduating on beds that do not exist. And
        // growFamilies sheds a member every cycle forever, because the house is
        // permanently overcrowded.
        int perFamily = largestHouse(town);
        Household homeless = null;
        while (born < souls) {
            if (homeless == null || homeless.size() >= perFamily) {
                homeless = new Household(Household.Id.random(),
                        familyName(surnames, town.households().size()));
                town.addHousehold(homeless);
            }
            town.addResident(person(homeless, given, born++, town.centre()));
        }
    }

    /**
     * The most beds any house in this catalog has, which is how big a family
     * the planner gathers strangers into.
     *
     * <p>Mirrors {@code PopulationPlanner.largestHousingCapacity}, which is
     * private. One is the floor: a catalog with no housing in it would
     * otherwise gather everybody into a family of nobody.
     */
    private static int largestHouse(Settlement town) {
        return Math.max(1, town.catalogue().stream()
                .filter(BuildingType::isHousing)
                .mapToInt(BuildingType::capacity)
                .max()
                .orElse(1));
    }

    /** One resident, born into a family and standing where that family lives. */
    private static Person person(Household family, List<String> given,
                                 int index, SimPos where) {
        Person person = new Person(Person.Id.random(),
                given.get(index % given.size()) + " " + family.name(),
                Profession.PIONEER, where);
        person.inventory().add(Foods.PROVISION, TownStores.FOUNDING_PROVISIONS_EACH);
        family.addMember(person.id());
        return person;
    }

    /**
     * The nth family name, wrapping the way {@code PopulationPlanner} wraps.
     *
     * <p>Numbered past the end of the pool rather than repeated, so a town
     * seeded with two hundred residents does not hold several people with the
     * same name — which reads as a bug in the name generator and is the sort of
     * thing somebody spends an afternoon on.
     */
    private static String familyName(List<String> pool, int index) {
        String base = pool.get(index % pool.size());
        int wrap = index / pool.size();
        return wrap == 0 ? base : base + " " + (wrap + 1);
    }

    /**
     * What the town has by it: a larder, and materials to keep building with.
     *
     * <p><strong>Set, not added.</strong> A settlement is born holding the
     * founding kit — {@code Settlement.loosePile} is initialized with it — so
     * adding here gave a seeded camp twice the timber a chartered one gets, and
     * the doubling was invisible because both numbers looked plausible.
     *
     * <p>The larder is {@link StagePlanner#FED_WINDOW_STEPS} steps of the whole
     * town's appetite, which is not a number picked here: it is precisely the
     * larder a homestead has to hold, for ten steps running, before it is
     * allowed to graduate, so every stage above HOMESTEAD has demonstrably held
     * it. Floored at what a charter party carries, because a camp is a charter
     * party and must not arrive poorer than one, and capped by the granary so
     * nothing starts over its own ceiling.
     *
     * <p>The materials are the founding kit, capped the same way. It is the only
     * materials figure in this codebase sized against a real program (see
     * {@link TownStores#FOUNDING_WOOD}), and a town that has just finished a
     * stage is exactly a town partway through spending one. Restated here rather
     * than left to the field initializer, so what a seeded town holds is a
     * decision at this end and does not quietly change when that one does.
     *
     * <p>Left flat rather than scaled by population, deliberately: a fuller
     * store would arrive at the timber ceiling, and a lumber camp that opens
     * into a full store fells nothing at all.
     *
     * <p>Whatever is left loose is then put away, which is the settlement's own
     * rule for where goods live — so a camp with nowhere to put anything leaves
     * it stacked on the ground, and a fortified town with a storehouse has it on
     * shelves a builder can walk to.
     */
    private static void stockTheStores(Settlement town) {
        town.setFoodStock(Math.min(FoodPlanner.granaryCapacity(town),
                Math.max(FoodPlanner.STARTING_PROVISIONS,
                        town.population() * StagePlanner.FED_WINDOW_STEPS)));
        town.setWoodStock(Math.min(TownStores.FOUNDING_WOOD,
                LumberPlanner.woodCapacity(town)));
        town.setStoneStock(Math.min(TownStores.FOUNDING_STONE,
                MinePlanner.stoneCapacity(town)));
        town.putAwayLoosePile();
        // The fed streak is deliberately NOT set here. It looked like something
        // a settled town should arrive holding, and it is inert: the only
        // readers are the HOMESTEAD arm of readyToAdvance and the camp post's
        // homestead display, and Settlement.trackFedStreak recomputes the field
        // at the end of the first step regardless. A line that reads as a
        // decision and does nothing is worse than no line.
    }

    /**
     * Who does what, decided by the same two things that decide it in a growing
     * town.
     *
     * <p>Each stage crystallizes what it crystallizes — the sentry and the
     * woodcutter at FORTIFIED, the pioneers dissolving at VILLAGE — and from
     * VILLAGE the staffing table places the idlers. Run to a standstill here
     * rather than one a step, because the town has already had those steps.
     */
    private static void nameTheTrades(Settlement town, SettlementStage stage) {
        for (SettlementStage reached : SettlementStage.values()) {
            if (reached != SettlementStage.CAMP && stage.atLeast(reached)) {
                StagePlanner.crystallize(town, reached);
            }
        }
        if (StagePlanner.pioneersLabour(stage)) {
            return;   // generalists still, and the table does not staff them
        }
        // Bounded by the population: every pass places at most one person, so a
        // table that somehow never settles cannot spin here.
        for (int placed = 0; placed < town.population(); placed++) {
            if (!JobPlanner.retrainOne(town)) {
                return;
            }
        }
    }

    /** A culture's own list, or a single stand-in when it defined none. */
    private static List<String> namesOr(List<String> pool, String fallback) {
        return pool == null || pool.isEmpty() ? List.of(fallback) : pool;
    }
}
