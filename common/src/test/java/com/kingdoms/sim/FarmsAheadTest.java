package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Garrison;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.YieldPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town nobody is watching keeps its own fields ahead of its own mouths.
 *
 * <p>With the clock crediting nothing — the shipped 0/0 — every loaf a town
 * eats has to come off a field it built, staffed and harvested. Two links were
 * missing and both are here.
 *
 * <p><strong>Nobody was working the field.</strong> The staffing table wanted
 * {@code population / 5} farmers, so a founding party of four with a farm
 * standing in the middle of it wanted none. Measured before the fix: the farm
 * went up on step 30, the party never assigned a single farmer to it, ate its
 * hundred founding loaves down to nothing by step 360 and was extinct by step
 * 500 — with a working farm in the middle of the town the whole time.
 *
 * <p><strong>Nothing ever ordered a second one.</strong> The catalog does not
 * run below VILLAGE, and the queue is head-blocking, so a town could want a
 * field silently forever behind a cottage it had no timber for and meet its
 * famine with a full queue.
 */
class FarmsAheadTest {

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** The shipped world: raids off so the arithmetic holds still, nothing conjured. */
    private static final SimSettings NOTHING_CONJURED =
            SimSettings.SANDBOX.withYields(YieldPolicy.DEFAULTS);

    private static Settlement charter(String name) {
        Settlement town = new Settlement(
                Settlement.Id.random(), name, new SimPos(0, 64, 0), 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setFoodStock(FoodPlanner.STARTING_PROVISIONS);
        for (String settler : new String[] {"Ada", "Bruno", "Cass", "Dov"}) {
            town.addResident(new Person(Person.Id.random(), settler, Profession.PIONEER,
                    new SimPos(0, 64, 0)));
        }
        return town;
    }

    private static void step(Settlement town, int step) {
        town.step(new SimContext(new QuietBridge(), step, NOTHING_CONJURED));
    }

    private static int foodEverywhere(Settlement town) {
        int total = town.foodStock();
        for (var building : town.buildings()) {
            total += building.foodStored();
        }
        return total;
    }

    private static long farmers(Settlement town) {
        return town.residents().stream()
                .filter(person -> town.laborsAs(person, Profession.FARMER))
                .count();
    }

    // ---- the arithmetic -------------------------------------------------

    /**
     * Fifteen mouths a farm, and the sum that says so.
     *
     * <p>Written out here rather than only in the javadoc, because this is the
     * number the whole rule rests on and it is derived from four other numbers
     * that people will change.
     */
    @Test
    void aFarmIsReckonedToFeedFifteen() {
        int loavesPerFarm = FoodPlanner.FARMERS_PER_FARM * FoodPlanner.FOOD_PER_FARMER_PER_STEP;
        int stepsPerLoaf = Foods.nutrition(Foods.PROVISION) / FoodPlanner.HUNGER_PER_STEP;

        assertEquals(2, loavesPerFarm, "a fully staffed field, per step");
        assertEquals(15, stepsPerLoaf, "steps one loaf carries one person");
        assertEquals(30, loavesPerFarm * stepsPerLoaf, "mouths a field covers flat out");
        assertEquals(15, BuildPlanner.MOUTHS_PER_FARM,
                "and half of that is the margin the planner actually plans on");
    }

    /** Rounded up, and never nought — the two departures from every other want. */
    @Test
    void theFieldCountRoundsUpAndNeverReachesZero() {
        assertEquals(1, BuildPlanner.farmsWanted(1), "one settler still wants a field");
        assertEquals(1, BuildPlanner.farmsWanted(4), "and so does a founding charter");
        assertEquals(1, BuildPlanner.farmsWanted(15));
        assertEquals(2, BuildPlanner.farmsWanted(16), "one over, and the next is wanted");
        assertEquals(2, BuildPlanner.farmsWanted(30));
        assertEquals(3, BuildPlanner.farmsWanted(31));
        assertEquals(3, BuildPlanner.farmsWanted(40));
    }

    // ---- the town -------------------------------------------------------

    /**
     * The whole of it: four settlers, nobody watching, nothing conjured, and a
     * town that is still there twenty-five minutes later.
     */
    @Test
    void anUnwatchedTownFeedsItselfOnItsOwnFields() {
        Settlement town = charter("Newholt");
        for (int at = 1; at <= 1500; at++) {
            step(town, at);
        }

        assertEquals(4, town.population(), "the charter party is alive");
        assertTrue(town.countBuildings(BuildPlanner.FARM) >= 1, "on a field it built itself");
        assertTrue(foodEverywhere(town) > FoodPlanner.STARTING_PROVISIONS,
                "and holding more food than it set out with, having conjured none: "
                        + foodEverywhere(town));
        assertEquals(0, town.residents().stream()
                        .filter(p -> p.hunger() >= Person.HUNGER_WEAK).count(),
                "nobody too weak to work");
    }

    /**
     * The field is up and staffed long before the founding provisions are gone.
     *
     * <p>The failing number was 360 — the step the old town first counted as
     * starving. This asserts the farm is standing with hands in it while the
     * larder is still comfortably full, which is the difference between farming
     * and reacting to a famine.
     */
    @Test
    void theFieldIsWorkedWhileThereIsStillFoodInTheLarder() {
        Settlement town = charter("Earlyfield");
        int firstWorkedFarm = -1;
        int foodThen = -1;
        for (int at = 1; at <= 400; at++) {
            step(town, at);
            if (firstWorkedFarm < 0
                    && town.countBuildings(BuildPlanner.FARM) >= 1
                    && farmers(town) >= FoodPlanner.FARMERS_PER_FARM) {
                firstWorkedFarm = at;
                foodThen = foodEverywhere(town);
            }
            assertFalse(town.isStarving(),
                    "the town should never have got hungry enough to notice, at step " + at);
        }

        assertTrue(firstWorkedFarm > 0, "a farm with farmers in it was never reached");
        assertTrue(foodThen >= FoodPlanner.STARTING_PROVISIONS / 2,
                "the field was worked at step " + firstWorkedFarm + " with only "
                        + foodThen + " loaves left — that is a rescue, not a plan");
    }

    /**
     * Farms keep pace with mouths as the town grows, and do it unprompted.
     *
     * <p>The population is added rather than grown because unwatched housing is
     * its own problem: what is under test is whether the planner notices more
     * mouths, not how they arrived.
     */
    @Test
    void farmsKeepPaceWithMouths() {
        Settlement town = charter("Broadacre");
        for (int at = 1; at <= 200; at++) {
            step(town, at);
        }
        assertEquals(1, town.countBuildings(BuildPlanner.FARM),
                "four settlers want exactly one field");

        for (int i = 0; i < 36; i++) {
            town.addResident(new Person(Person.Id.random(), "Incomer" + i, Profession.IDLER,
                    new SimPos(0, 64, 0)));
        }
        assertEquals(40, town.population());
        assertEquals(3, BuildPlanner.farmsWanted(40));

        for (int at = 201; at <= 500; at++) {
            step(town, at);
            assertFalse(town.isStarving(),
                    "the fields were ordered late enough to let the town go hungry, step " + at);
        }

        assertTrue(town.countBuildings(BuildPlanner.FARM) >= 3,
                "forty mouths, and only " + town.countBuildings(BuildPlanner.FARM) + " fields");
    }

    // ---- the hands ------------------------------------------------------

    /**
     * Farmers track farms, not the census.
     *
     * <p>Sampled every step: from twenty steps after a farm finishes, the town
     * has two hands per field. The bound is the town itself — nobody can be
     * spared past the last builder, who is the only person who can raise the
     * next one.
     *
     * <p>The same twenty steps of grace are allowed after a stage change, and
     * for the same reason. Graduating is an upheaval: below VILLAGE the fields
     * are worked by pioneers who have no trade at all, at VILLAGE the staffing
     * table takes over and has to name every one of them — and it names one
     * person per step, deliberately, so that a town's working lives change
     * legibly rather than all at once.
     */
    @Test
    void farmersArriveWithinTwentyStepsOfTheFieldTheyWork() {
        Settlement town = charter("Handstead");
        int farmsBefore = 0;
        SettlementStage stageBefore = town.stage();
        int upheaval = -1;
        for (int at = 1; at <= 600; at++) {
            step(town, at);
            int farms = town.countBuildings(BuildPlanner.FARM);
            if (farms != farmsBefore || town.stage() != stageBefore) {
                farmsBefore = farms;
                stageBefore = town.stage();
                upheaval = at;
            }
            assertEquals(0, town.residents().stream().filter(Person::isEmbodied).count(),
                    "nobody is embodied in an unwatched town");
            if (farms == 0 || upheaval < 0 || at < upheaval + 20) {
                continue;
            }
            int wanted = Math.min(farms * FoodPlanner.FARMERS_PER_FARM,
                    town.population() - 1);
            assertTrue(farmers(town) >= wanted,
                    "step " + at + ": " + farms + " field(s), " + town.population()
                            + " residents, and only " + farmers(town) + " working them");
        }
        assertTrue(farmsBefore >= 1, "the fixture built a farm");
    }

    /** Below VILLAGE nobody has a trade, and the fields are still worked. */
    @Test
    void pioneersFarmBeforeThereAreFarmers() {
        Settlement town = charter("Firstfurrow");
        town.setStage(SettlementStage.HOMESTEAD);

        assertTrue(town.residents().stream()
                        .allMatch(p -> town.laborsAs(p, Profession.FARMER)),
                "a homestead's pioneers all turn a hand to the field");

        town.setStage(SettlementStage.VILLAGE);
        assertEquals(0, farmers(town),
                "and from VILLAGE the staffing table has to name them");
    }

    /**
     * The muster never takes the last farmer, however frightened the town is.
     *
     * <p>{@code spareHandsForTheWatch} says it does not. This is a town with
     * more threat than watch, one farmer and one builder, stepped long enough
     * for the retraining lane to have taken anybody it was ever going to.
     */
    @Test
    void themusterLeavesTheLastFarmerInTheField() {
        Settlement town = charter("Alarmwell");
        town.setStage(SettlementStage.VILLAGE);
        var people = town.residents().stream().toList();
        people.get(0).setProfession(Profession.FARMER);
        people.get(1).setProfession(Profession.BUILDER);
        people.get(2).setProfession(Profession.TRADER);
        people.get(3).setProfession(Profession.TRADER);

        for (int at = 1; at <= 60; at++) {
            town.setThreatLevel(40);
            assertTrue(Garrison.outnumbered(town), "the fixture is a frightened town");
            JobPlanner.retrainOne(town);
            assertTrue(JobPlanner.count(town, Profession.FARMER) >= 1,
                    "the watch was raised out of the last farmer, at step " + at);
            assertTrue(JobPlanner.count(town, Profession.BUILDER) >= 1,
                    "and out of the last builder, at step " + at);
        }
        assertTrue(JobPlanner.count(town, Profession.GUARD) >= 1,
                "it did raise a watch out of somebody");
    }
}
