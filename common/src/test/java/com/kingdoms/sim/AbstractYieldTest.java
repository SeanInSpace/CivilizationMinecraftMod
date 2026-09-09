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
 * for a field that makes a single loaf a step, where a naive multiply would
 * have made every setting below a hundred an off switch — and what the shipped
 * defaults, which are now zero and zero, actually do to a town left alone.
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

    /** A field with one farmer on it: exactly one loaf a step at full yield. */
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
    void aSingleLoafAStepAtSeventyPercentIsSevenLoavesInTen() {
        // One field, one farmer, ten steps: ten loaves at full yield. Seventy
        // percent of a single loaf, floored, is nothing — so this is the case
        // that says whether the remainder is carried or thrown away.
        assertEquals(7, grownOver(10, new Empty(), YieldPolicy.uniform(70, 0)),
                "seventy percent of one loaf a step is seven loaves in ten, not none");
    }

    @Test
    void aSingleLoafAStepAtFullYieldIsTenLoavesInTen() {
        assertEquals(10, grownOver(10, new Empty(), YieldPolicy.FULL),
                "the golden figure the seventy-percent case is measured against");
    }

    @Test
    void aWatchedFieldWithNobodyCuttingWheatGrowsNothingByDefault() {
        assertEquals(0, grownOver(10, new Watched(), YieldPolicy.DEFAULTS),
                "a watched field is farmed by hands, or it is not farmed");
    }

    @Test
    void aWatchedFieldStillGrowsWhenTheFloorIsRaisedToFull() {
        assertEquals(10, grownOver(10, new Watched(), YieldPolicy.uniform(70, 100)),
                "a floor of a hundred is the old behavior, and must still be reachable");
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
        assertEquals(0, grownOver(50, new Empty(), YieldPolicy.DEFAULTS),
                "nor any bread");
    }

    /**
     * What a founding party actually does at the shipped defaults, measured.
     *
     * <p>It dies, and that is the honest answer rather than a fault to be
     * papered over. At 0/0 nothing anywhere is conjured, so the only food a
     * town can gain is food real hands harvested in front of a player, or wild
     * food actually growing where it pitched. A camp nobody ever visits has
     * neither. Four pioneers left entirely alone on this ground reach four
     * hundred steps still four strong and out of bread, two of them too weak to
     * work, and by eight hundred there is nobody left.
     *
     * <p>Measured here rather than argued about, because it is the whole shape
     * of the setting: a world at 0/0 is a world where towns you never go back
     * to eventually end, and a world at 70 is not. The same run at 70 percent
     * unwatched ends at four hundred steps a TOWN of thirteen with 196 loaves.
     */
    @Test
    void aCampLeftEntirelyAloneAtTheShippedDefaultsRunsDown() {
        Settlement camp = campOfFour();

        SimSettings settings = with(YieldPolicy.DEFAULTS);
        for (int step = 1; step <= 400; step++) {
            camp.step(new SimContext(new Empty(), step, settings));
        }

        assertEquals(0, FoodPlanner.totalFood(camp),
                "nothing was conjured, and there is nothing left");
        assertTrue(camp.isStarving(),
                "a town gaining nothing at all ends up starving, and this one is not");
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
