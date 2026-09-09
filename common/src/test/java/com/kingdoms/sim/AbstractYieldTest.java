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
 * <p>What is pinned here is that the percentages mean what they say — including
 * carrying the remainder, where a naive multiply would have made every setting
 * below a hundred an off switch — and what the shipped defaults, which are now
 * zero and zero, actually do to a town left alone.
 *
 * <p>Food is no longer one of the things the tables govern. A field is not a
 * percentage of an imagined harvest; it is seventy-one crop blocks that ripen
 * and get cut. What is left here is the timber and the stone, and the two cases
 * below that pin food's independence from the tables outright. See
 * {@link UnwatchedFarmingTest} for the field's own arithmetic.
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
    void seventyPercentCreditsSeventyPercentOfTheTimber() {
        int full = timberOver(20, YieldPolicy.FULL);
        int cut = timberOver(20, YieldPolicy.uniform(70, 0));

        assertTrue(Math.abs(cut - full * 70 / 100) <= 1,
                "twenty steps at seventy percent of " + full + " should be about "
                        + (full * 70 / 100) + ", not " + cut);
    }

    @Test
    void seventyPercentCreditsSeventyPercentOfTheStone() {
        int full = stoneOver(20, YieldPolicy.FULL);
        int cut = stoneOver(20, YieldPolicy.uniform(70, 0));

        assertTrue(Math.abs(cut - full * 70 / 100) <= 1,
                "twenty steps at seventy percent of " + full + " should be about "
                        + (full * 70 / 100) + ", not " + cut);
    }

    @Test
    void zeroPercentMeansAnUnwatchedTownConjuresNothing() {
        assertEquals(0, timberOver(50, YieldPolicy.uniform(0, 0)),
                "at zero the only timber is timber somebody felled");
        assertEquals(0, stoneOver(50, YieldPolicy.uniform(0, 0)));
    }

    @Test
    void aResourceCanBeThrottledWithoutTouchingItsNeighbors() {
        Settlement town = townWith("kingdoms:mine", Profession.MINER);
        // Stone off, iron left alone.
        YieldPolicy policy = new YieldPolicy(
                java.util.Map.of(TownStores.STONE, 0),
                java.util.Map.of());
        SimSettings settings = with(policy);
        for (int step = 1; step <= 40; step++) {
            MinePlanner.advance(town, new SimContext(new Empty(), step, settings));
        }

        assertEquals(0, town.stores().get(TownStores.STONE),
                "stone is switched off in this world");
        assertTrue(town.stores().get(TownStores.IRON) > 0,
                "iron was never mentioned, so it is credited in full");
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
    void aWatchedCampStillEarnsWhenTheFloorIsRaisedToFull() {
        Settlement town = townWith("kingdoms:lumber_camp", Profession.LUMBERJACK);
        SimSettings settings = with(YieldPolicy.uniform(70, 100));

        LumberPlanner.advance(town, new SimContext(new Watched(), 100, settings));

        assertTrue(town.stores().get(TownStores.WOOD) > 0,
                "a floor of a hundred is the old behavior, and must still be reachable");
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

    /**
     * Everything the field grew, wherever in town it has since been carried.
     *
     * <p>Grain counts. A cut block is a sheaf now and only becomes a loaf at an
     * oven, and this fixture has no oven -- what is being measured is what the
     * ground yields, not what a bakery does with it afterwards.
     */
    private static int grown(Settlement town) {
        return town.foodStock() + FoodPlanner.farmStock(town) + FoodPlanner.farmGrain(town);
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
    void anUnwatchedCampAtTheShippedDefaultsBringsInNothing() {
        assertEquals(0, timberOver(50, YieldPolicy.DEFAULTS),
                "the shipped world conjures no timber");
        assertEquals(0, stoneOver(50, YieldPolicy.DEFAULTS),
                "nor any stone");
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

        assertEquals(4, camp.population(),
                "the party that arrived is the party that is here");
        assertTrue(FoodPlanner.totalFood(camp) > 0,
                "and they have bread, out of a field, with nothing conjured");
        assertTrue(camp.stores().get(TownStores.WOOD) == 0,
                "which is not because the tables came back — no timber is conjured");
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
