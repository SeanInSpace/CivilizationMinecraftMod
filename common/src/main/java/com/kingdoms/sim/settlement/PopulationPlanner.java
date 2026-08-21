package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

import java.util.HashSet;
import java.util.List;
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

    /** Steps a family needs before a birth, once it has room. */
    public static final int STEPS_PER_BIRTH = 8;

    private static final List<String> FAMILY_NAMES = List.of(
            "Baker", "Miller", "Smith", "Cooper", "Fletcher", "Mason", "Turner", "Weaver");

    private static final List<String> GIVEN_NAMES = List.of(
            "Ada", "Bren", "Cyn", "Dov", "Esa", "Fen", "Gil", "Hana", "Ivo", "Jor");

    private PopulationPlanner() {
    }

    /** One population step: settle newcomers, house families, then grow them. */
    public static void advance(Settlement settlement, SimContext ctx) {
        groupUnassignedResidents(settlement);
        assignHomes(settlement);
        growFamilies(settlement, ctx.settings().stepsPerBirth(),
                ctx.settings().maxSettlementPopulation());
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

    // --- 3. growth ---

    private static void growFamilies(Settlement settlement, int stepsPerBirth, int populationCap) {
        // Copied because splitting appends to the household list as we iterate.
        for (Household household : List.copyOf(settlement.households())) {
            if (!household.isHoused()) {
                continue;
            }

            if (household.growthProgress() < stepsPerBirth) {
                household.addGrowthProgress(1);
            }
            if (household.growthProgress() < stepsPerBirth) {
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

            int capacity = capacityOfHome(settlement, household);
            if (household.size() < capacity) {
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
                givenName(household.size()) + " " + household.name(),
                trade,
                household.home());

        settlement.addResident(child);
        household.addMember(child.id());
    }

    /** The most recently added member leaves to found a family in the empty house. */
    private static void splitFamilyInto(Settlement settlement, Household parent, SimPos vacant) {
        Person.Id leaver = parent.members().getLast();
        parent.removeMember(leaver);

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

    public static int capacityOf(Settlement settlement, String blueprintId) {
        return settlement.catalogue().stream()
                .filter(type -> type.id().equals(BuildPlanner.baseIdOf(blueprintId)))
                .mapToInt(BuildingType::capacity)
                .findFirst()
                .orElse(0);
    }

    /** Capacity of the house this family lives in, or 0 if unhoused. */
    public static int capacityOfHome(Settlement settlement, Household household) {
        if (!household.isHoused()) {
            return 0;
        }
        return settlement.buildings().stream()
                .filter(building -> building.origin().equals(household.home()))
                .mapToInt(building -> capacityOf(settlement, building.blueprintId()))
                .findFirst()
                .orElse(0);
    }

    /** First house nobody has claimed, or null if the settlement is fully occupied. */
    public static SimPos firstVacantHome(Settlement settlement) {
        Set<SimPos> taken = new HashSet<>();
        for (Household household : settlement.households()) {
            if (household.isHoused()) {
                taken.add(household.home());
            }
        }
        for (Building building : settlement.buildings()) {
            if (capacityOf(settlement, building.blueprintId()) <= 0) {
                continue;
            }
            if (!taken.contains(building.origin())) {
                return building.origin();
            }
        }
        return null;
    }

    /** Total beds in the settlement, housed or not. */
    public static int totalHousingCapacity(Settlement settlement) {
        return settlement.buildings().stream()
                .mapToInt(building -> capacityOf(settlement, building.blueprintId()))
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

    private static String nextFamilyName(Settlement settlement) {
        int index = settlement.households().size();
        String base = FAMILY_NAMES.get(index % FAMILY_NAMES.size());
        int wrap = index / FAMILY_NAMES.size();
        return wrap == 0 ? base : base + " " + (wrap + 1);
    }

    private static String givenName(int index) {
        return GIVEN_NAMES.get(index % GIVEN_NAMES.size());
    }
}
