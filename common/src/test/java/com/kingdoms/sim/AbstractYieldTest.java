package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.YieldPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much of a town's income is conjured, and who gets to decide.
 *
 * <p>Every gain in the mod used to be abstract when nobody was looking, and
 * abstract again the moment a watched worker went quiet for twelve steps. That
 * is the only way an unwatched town keeps existing, but "all of it, always" is
 * not a design decision, it is the absence of one. Two tables now say how much:
 * one for a building with nobody near it, one for the floor under a watched
 * building whose hands have stopped.
 *
 * <p><strong>The tables now govern nothing.</strong> That is what this file has
 * become, and it is worth stating plainly rather than deleting quietly. Food
 * went first: a field is not a percentage of an imagined harvest, it is
 * seventy-one crop blocks that ripen and get cut. Timber and stone have
 * followed it. A camp's yield is the stand of trees it actually claims and a
 * mine's is the rock actually under it — see {@code Stand} and {@code Seam} —
 * so {@code wood}, {@code saplings}, {@code stone} and {@code iron} are keys
 * that still exist in {@link YieldPolicy} and that nothing anywhere reads.
 *
 * <p>So what is pinned here is the negative: every setting the tables have,
 * including the ones a player can still write into a config file, makes no
 * difference to anything a town produces. The record itself is left standing
 * for whoever removes it — a knob that quietly does nothing is worth a test
 * saying so, and worth more than a knob that quietly does something.
 */
class AbstractYieldTest {

    private static final com.kingdoms.sim.settlement.BuildingType FARM =
            new com.kingdoms.sim.settlement.BuildingType("test:farm", 5, 9999, 0, 0, 70, 0);

    /** A world where nobody is standing anywhere. */
    private static final class Empty implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** A world where somebody is always standing in the town. */
    private static final class Watched implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static SimSettings with(YieldPolicy policy) {
        return SimSettings.SANDBOX.withYields(policy);
    }

    private static Settlement townWith(String blueprintId, Profession trade) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        town.addBuilding(new Building(blueprintId, new SimPos(12, 64, 0), 1, true));
        for (int i = 0; i < 3; i++) {
            town.addResident(new Person(
                    Person.Id.random(), "Hand " + i, trade, town.center()));
        }
        town.setStock(TownStores.WOOD, 0);
        town.setStock(TownStores.STONE, 0);
        return town;
    }

    /** Timber a camp brings in over some steps, under one policy, unwatched. */
    private static int timberOver(int steps, YieldPolicy policy) {
        Settlement town = townWith("kingdoms:lumber_camp", Profession.LUMBERJACK);
        SimSettings settings = with(policy);
        for (int step = 1; step <= steps; step++) {
            LumberPlanner.advance(town, new SimContext(new Empty(), step, settings));
        }
        return town.stores().get(TownStores.WOOD);
    }

    /** Stone a mine brings in over some steps, under one policy, unwatched. */
    private static int stoneOver(int steps, YieldPolicy policy) {
        Settlement town = townWith("kingdoms:mine", Profession.MINER);
        SimSettings settings = with(policy);
        for (int step = 1; step <= steps; step++) {
            MinePlanner.advance(town, new SimContext(new Empty(), step, settings));
        }
        return town.stores().get(TownStores.STONE);
    }

    // --- unwatched ---

    @Test
    void aHundredPercentIsExactlyTheYieldThatShippedBefore() {
        assertEquals(timberOver(20, YieldPolicy.FULL),
                timberOver(20, YieldPolicy.uniform(100, 100)),
                "a table of hundreds must be indistinguishable from no table at all");
        assertEquals(stoneOver(20, YieldPolicy.FULL),
                stoneOver(20, YieldPolicy.uniform(100, 100)));
    }

    @Test
    void noTableHasAnySayOverWhatACampFells() {
        int full = timberOver(20, YieldPolicy.FULL);
        int throttled = timberOver(20, YieldPolicy.uniform(70, 0));
        int off = timberOver(20, YieldPolicy.uniform(0, 0));

        assertEquals(full, throttled, "seventy percent of a tree is a tree");
        assertEquals(full, off,
                "and nought percent of one is still a tree — a camp fells what is"
                        + " standing in its claim, and no number in a config file"
                        + " is standing in its claim");
        assertTrue(full > 0, "which is to say it fells something");
    }

    @Test
    void noTableHasAnySayOverWhatAMineCuts() {
        int full = stoneOver(20, YieldPolicy.FULL);
        int throttled = stoneOver(20, YieldPolicy.uniform(70, 0));
        int off = stoneOver(20, YieldPolicy.uniform(0, 0));

        assertEquals(full, throttled);
        assertEquals(full, off, "the rock under a mine is not a percentage either");
        assertTrue(full > 0);
    }

    @Test
    void switchingOffOneResourceNoLongerSwitchesOffAnything() {
        Settlement town = townWith("kingdoms:mine", Profession.MINER);
        // Stone off, as far as the table is concerned — and the table is no
        // longer concerned. Kept as a case because this is the shape of config a
        // player may already have written, and it must not now be read as some
        // other instruction; it must simply do nothing.
        YieldPolicy policy = new YieldPolicy(
                java.util.Map.of(TownStores.STONE, 0),
                java.util.Map.of());
        SimSettings settings = with(policy);
        for (int step = 1; step <= 40; step++) {
            MinePlanner.advance(town, new SimContext(new Empty(), step, settings));
        }

        assertTrue(town.stores().get(TownStores.STONE) > 0,
                "the seam is cut whatever the table says about stone");
        assertTrue(town.stores().get(TownStores.IRON) > 0,
                "and the ore comes up with it");
    }

    // --- the watched floor ---

    @Test
    void aWatchedCampWithIdleAxesEarnsNothingByDefault() {
        Settlement town = townWith("kingdoms:lumber_camp", Profession.LUMBERJACK);
        SimSettings settings = with(YieldPolicy.DEFAULTS);

        for (int step = 1; step <= 30; step++) {
            LumberPlanner.advance(town, new SimContext(new Watched(), step, settings));
        }

        assertEquals(0, town.stores().get(TownStores.WOOD),
                "in front of a player only real work counts");
    }

    @Test
    void noTablePutsTheClockBackIntoAWatchedCamp() {
        // There used to be a floor, and a table of a hundred restored the old
        // conjuring outright. It is gone at every setting there is, exactly as
        // it went from the fields: a watched camp is worked by axes or it is not
        // worked. See WatchedProductionTest.
        Settlement town = townWith("kingdoms:lumber_camp", Profession.LUMBERJACK);
        SimSettings settings = with(YieldPolicy.uniform(70, 100));

        for (int step = 1; step <= 30; step++) {
            LumberPlanner.advance(town, new SimContext(new Watched(), step, settings));
        }

        assertEquals(0, town.stores().get(TownStores.WOOD),
                "no table anywhere fells a tree in front of a player");
    }

    @Test
    void aWatchedMineWithIdlePicksEarnsNothingByDefault() {
        Settlement town = townWith("kingdoms:mine", Profession.MINER);
        SimSettings settings = with(YieldPolicy.DEFAULTS);

        for (int step = 1; step <= 30; step++) {
            MinePlanner.advance(town, new SimContext(new Watched(), step, settings));
        }

        assertEquals(0, town.stores().get(TownStores.STONE),
                "in front of a player only real work counts");
    }

    // --- rounding ---
    //
    // The rounding these cases were written for is still exactly the trap it
    // always was — a percentage of one unit a step, floored, is nothing forever
    // — but food is no longer one of the things a percentage is taken of. A
    // field's yield is the field's own arithmetic now, carried in hundredths of
    // a crop block by Field rather than in hundredths of a loaf by the tables,
    // and it is pinned in UnwatchedFarmingTest. What is left here is the timber
    // and stone the tables do still govern, above.

    /** A field with one farmer on it. */
    private static Settlement oneField() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Onefield", new SimPos(0, 64, 0), 256);
        town.setCatalog(java.util.List.of(FARM));
        town.setFoodStock(0);
        town.addBuilding(new Building(FARM.id(), new SimPos(20, 64, 0), 0, true));
        town.addResident(new Person(Person.Id.random(), "Solitary",
                Profession.FARMER, new SimPos(0, 64, 0)));
        return town;
    }

    /** Everything the field grew, wherever in town it has since been carried. */
    private static int grown(Settlement town) {
        return town.foodStock() + FoodPlanner.farmStock(town);
    }

    private static int grownOver(int steps, WorldBridge world, YieldPolicy policy) {
        Settlement town = oneField();
        SimSettings settings = with(policy);
        for (int step = 1; step <= steps; step++) {
            FoodPlanner.advance(town, new SimContext(world, step, settings));
        }
        return grown(town);
    }

    @Test
    void noYieldTableHasAnySayOverWhatAFieldGrows() {
        // The whole of the change, in one assertion. Food used to be the most
        // table-governed thing in the mod; it is now the least. Nought percent
        // and a hundred percent grow the same wheat, because the wheat is real.
        assertEquals(grownOver(40, new Empty(), YieldPolicy.FULL),
                grownOver(40, new Empty(), YieldPolicy.uniform(0, 0)),
                "a field is not a percentage of anything");
        assertTrue(grownOver(40, new Empty(), YieldPolicy.DEFAULTS) > 0,
                "and at the shipped zeroes it still feeds the town");
    }

    @Test
    void aWatchedFieldWithNobodyCuttingWheatGrowsNothing() {
        assertEquals(0, grownOver(10, new Watched(), YieldPolicy.DEFAULTS),
                "a watched field is farmed by hands, or it is not farmed");
    }

    @Test
    void aWatchedFieldWithNobodyCuttingWheatGrowsNothingAtAnySetting() {
        // There used to be a floor: after a grace period the clock took a
        // watched farm back over, and a table of a hundred restored the old
        // conjuring outright. Both are gone, at every setting there is. A
        // watched field of ripe wheat with nobody in it stays a field of ripe
        // wheat, and the player can go and look at it.
        assertEquals(0, grownOver(10, new Watched(), YieldPolicy.uniform(70, 100)),
                "no table anywhere puts the clock back into a watched field");
    }

    // --- what the shipped defaults now are ---

    @Test
    void theShippedDefaultsConjureNothingAtAll() {
        for (String resource : YieldPolicy.SCALED_RESOURCES) {
            assertEquals(0, YieldPolicy.DEFAULTS.unwatched(resource),
                    resource + " is credited out of nothing to an unwatched town");
            assertEquals(0, YieldPolicy.DEFAULTS.watchedFloor(resource),
                    resource + " is credited out of nothing in front of a player");
        }
    }

    @Test
    void anUnwatchedCampAtTheShippedDefaultsWorksAnyway() {
        // The line that has moved furthest. At the shipped zeroes an unwatched
        // camp used to bring in nothing whatever, which was recorded here as the
        // honest answer — and it was, to the wrong question. Nothing is conjured
        // now either; the timber is simply real, and a camp works its claim
        // whether or not anybody is there to watch it.
        assertTrue(timberOver(50, YieldPolicy.DEFAULTS) > 0,
                "the shipped world fells real trees");
        assertTrue(stoneOver(50, YieldPolicy.DEFAULTS) > 0,
                "and cuts real rock");
    }

    /**
     * What a founding party actually does at the shipped defaults, measured.
     *
     * <p>It used to die. At 0/0 nothing anywhere is conjured, and when food was
     * a percentage of an imagined harvest the percentage was nought — so a camp
     * nobody visited reached four hundred steps out of bread with two of its
     * four too weak to work, and by eight hundred there was nobody left. That
     * was recorded here as the honest answer, and it was, to the wrong question.
     *
     * <p>Food is not a percentage any more. It is the field: seventy-one crop
     * blocks that ripen and are cut by the town's own farmers, watched or not,
     * with no table anywhere in the arithmetic. So the same four pioneers on the
     * same ground reach four hundred steps a VILLAGE with bread in hand, and are
     * still standing at fifteen hundred. What they do not do is grow, and that
     * is a different fault in a different place — see
     * {@code UnwatchedFarmingTest}, which owns the measurement now and names it.
     */
    @Test
    void aCampLeftEntirelyAloneAtTheShippedDefaultsLivesOffItsField() {
        Settlement camp = campOfFour();

        SimSettings settings = with(YieldPolicy.DEFAULTS);
        for (int step = 1; step <= 400; step++) {
            camp.step(new SimContext(new Empty(), step, settings));
        }

        assertTrue(camp.population() >= 4,
                "the party that arrived is here, and then some");
        assertTrue(FoodPlanner.totalFood(camp) > 0,
                "and they have bread, out of a field, with nothing conjured");
        assertTrue(camp.tallies().get(com.kingdoms.sim.settlement.Tallies.TREES_FELLED) > 0,
                "and the timber they built with came off a claim they felled,"
                        + " which is what the tables being at zero now means");
    }

    @Test
    void theSameCampAtSeventyPercentProspers() {
        Settlement camp = campOfFour();

        SimSettings settings = with(YieldPolicy.uniform(70, 0));
        for (int step = 1; step <= 400; step++) {
            camp.step(new SimContext(new Empty(), step, settings));
        }

        assertTrue(camp.population() >= 4,
                "seventy percent of the yield must still feed the party that arrived, "
                        + "and " + camp.population() + " are left");
        assertTrue(FoodPlanner.totalFood(camp) > 0,
                "a camp at seventy percent must not be living on its last loaf");
        assertTrue(camp.stage().ordinal() >= SettlementStage.HOMESTEAD.ordinal(),
                "a camp left alone at seventy percent still earns its homestead, "
                        + "and stood at " + camp.stage());
    }

    private static Settlement campOfFour() {
        Settlement camp = new Settlement(
                Settlement.Id.random(), "Newholt", new SimPos(0, 64, 0), 128);
        camp.setCatalog(BuildCatalog.DEFAULT);
        camp.setStage(SettlementStage.CAMP);
        camp.setFoodStock(FoodPlanner.STARTING_PROVISIONS);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov"}) {
            camp.addResident(new Person(Person.Id.random(), name, Profession.PIONEER,
                    new SimPos(0, 64, 0)));
        }
        return camp;
    }
}
