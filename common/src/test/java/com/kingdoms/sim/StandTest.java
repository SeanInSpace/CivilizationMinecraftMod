package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Stand;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stand's own arithmetic: what a camp holds, spends and grows back.
 *
 * <p>{@link Stand} is to a lumber camp what {@code Field} is to a farm — the
 * thing the yield table used to stand in for. These are its ledger rules in
 * isolation, and then the two-fidelity rules that hang off them.
 */
class StandTest {

    /** Nobody anywhere, and ground that can be read. */
    private static final class Alone implements WorldBridge {
        int trees = 12;
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int countTreesNear(SimPos center, int radius) { return trees; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** The same ground with a player standing on it. */
    private static final class Standing extends Object implements WorldBridge {
        int trees = 12;
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int countTreesNear(SimPos center, int radius) { return trees; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimSettings SHIPPED = SimSettings.SANDBOX;

    private static Building camp() {
        return new Building("kingdoms:lumber_camp", new SimPos(12, 64, 0), 0, true);
    }

    private static Settlement campTown(int jacks) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Timberton", new SimPos(0, 64, 0), 128);
        town.addBuilding(camp());
        for (int i = 0; i < jacks; i++) {
            town.addResident(new Person(Person.Id.random(), "Jack " + i,
                    Profession.LUMBERJACK, town.center()));
        }
        town.setStock(TownStores.WOOD, 0);
        town.setStock(TownStores.SAPLINGS, 0);
        return town;
    }

    private static Building only(Settlement town) {
        return town.buildings().getFirst();
    }

    // --- the ledger ---

    @Test
    void aCountedStandIsWorthSixLogsATree() {
        Building camp = camp();
        Stand.recount(camp, 12);

        assertEquals(12, Stand.trees(camp));
        assertEquals(12 * Stand.LOGS_PER_TREE, Stand.logs(camp));
    }

    @Test
    void fellingTakesWholeLogsAndNoMoreThanAreStanding() {
        Building camp = camp();
        Stand.recount(camp, 1);

        assertEquals(4, Stand.fell(camp, 4), "only what was asked for");
        assertEquals(2, Stand.fell(camp, 9), "and only what is standing");
        assertEquals(0, Stand.logs(camp));
    }

    @Test
    void anUncountedStandIsNotABareOne() {
        Building camp = camp();

        assertTrue(!Stand.isCounted(camp), "nobody has been here");
        assertTrue(!Stand.isBare(camp),
                "and 'nobody has looked' must never read as 'it has been felled'");

        Stand.recount(camp, 0);
        assertTrue(Stand.isCounted(camp) && Stand.isBare(camp),
                "once somebody has looked and found grass, it is bare");
    }

    @Test
    void aSaplingIsATreesWorthOfTimberComing() {
        Building camp = camp();
        Stand.recount(camp, 0);
        Stand.plant(camp);

        assertEquals(1, Stand.growing(camp));
        assertEquals(0, Stand.trees(camp), "and it is not a tree yet");
    }

    @Test
    void aPlantedClaimComesUpOverAboutADayOfGrowing() {
        Building camp = camp();
        Stand.recount(camp, 0);
        for (int i = 0; i < 24; i++) {
            Stand.plant(camp);
        }
        SimContext ctx = new SimContext(new Alone(), 1, SHIPPED);

        for (int step = 0; step < Stand.growingSteps(SHIPPED); step++) {
            Stand.grow(camp, ctx);
        }

        assertTrue(Stand.trees(camp) >= 12,
                "half the wood at least is up within one day of planting, and "
                        + Stand.trees(camp) + " is not — see Stand.grow on why"
                        + " they do not all come up on the same afternoon");
        assertTrue(Stand.growing(camp) > 0,
                "and some of it is still coming, which is what a memoryless"
                        + " sapling actually does");
    }

    @Test
    void nothingComesUpWhereNothingWasPlanted() {
        Building camp = camp();
        Stand.recount(camp, 3);
        SimContext ctx = new SimContext(new Alone(), 1, SHIPPED);

        for (int step = 0; step < 500; step++) {
            Stand.grow(camp, ctx);
        }

        assertEquals(3, Stand.trees(camp), "a wood does not seed itself in this mod");
    }

    // --- the two fidelities ---

    @Test
    void anUnwatchedCampFellsAtItsJacksOwnPace() {
        Settlement town = campTown(2);

        LumberPlanner.advance(town, new SimContext(new Alone(), 1, SHIPPED));

        assertEquals(2 * Stand.LOGS_PER_JACK_PER_STEP,
                town.stores().get(TownStores.WOOD));
        assertEquals(12 * Stand.LOGS_PER_TREE - 2 * Stand.LOGS_PER_JACK_PER_STEP,
                Stand.logs(only(town)), "off the stand, log for log");
    }

    @Test
    void aWatchedCampIsLeftToItsAxes() {
        Settlement town = campTown(2);

        for (int step = 1; step <= 20; step++) {
            LumberPlanner.advance(town, new SimContext(new Standing(), step, SHIPPED));
        }

        assertEquals(0, town.stores().get(TownStores.WOOD),
                "the clock does not fell a tree somebody could be watching");
        assertEquals(12 * Stand.LOGS_PER_TREE, Stand.logs(only(town)),
                "and the wood is all still standing, for them to go and look at");
    }

    @Test
    void aRealFellingSpendsTheSameLogTheClockWouldHave() {
        // The hook LumberjackWorker calls. Without it the ledger stands full
        // behind a watched camp and pays out the moment the player leaves.
        Building camp = camp();
        Stand.recount(camp, 2);

        Stand.fell(camp, 1);

        assertEquals(2 * Stand.LOGS_PER_TREE - 1, Stand.logs(camp));
    }

    @Test
    void theLedgerIsSpentByWhatTheAxeTookAndNotByATreeAtATime() {
        // LumberjackWorker calls this once per log it actually destroys, so a
        // stand of two trees goes out one log at a time and lands exactly on
        // empty. Debiting a flat tree's worth per stroke — or a tree's worth for
        // a stroke that took one log — is how a ledger drifts off the wood.
        Building camp = camp();
        Stand.recount(camp, 2);

        int taken = 0;
        for (int stroke = 0; stroke < 2 * Stand.LOGS_PER_TREE; stroke++) {
            taken += Stand.fell(camp, 1);
        }

        assertEquals(2 * Stand.LOGS_PER_TREE, taken, "every log asked for was there");
        assertEquals(0, Stand.logs(camp), "and the stand is spent, to the log");
        assertEquals(0, Stand.fell(camp, 1), "with nothing left to take");
    }

    @Test
    void aStandIsDebitedOneTreePerTreeFelled() {
        // The same rule read the other way: a whole tree's worth of logs off the
        // stand is one tree off the stand, whoever swung the axe.
        Building camp = camp();
        Stand.recount(camp, 3);

        Stand.fell(camp, Stand.LOGS_PER_TREE);

        assertEquals(2, Stand.trees(camp), "three trees less the one that came down");
    }

    @Test
    void walkingUpToACampCountsWhatIsActuallyThere() {
        Settlement town = campTown(1);
        Standing arriving = new Standing();
        arriving.trees = 3;
        // A ledger that says one thing while the world says another — a player
        // has been through with an axe of their own, say.
        Stand.recount(only(town), 40);

        LumberPlanner.advance(town, new SimContext(arriving, 1, SHIPPED));

        assertEquals(3, Stand.trees(only(town)),
                "the trees are their own truth, and the count on arrival wins");
    }

    @Test
    void theCountIsTakenOnArrivalAndNotEveryStepAfterIt() {
        Settlement town = campTown(1);
        Standing world = new Standing();
        world.trees = 5;

        LumberPlanner.advance(town, new SimContext(world, 1, SHIPPED));
        world.trees = 99;   // the world moves on; nobody arrives again
        LumberPlanner.advance(town, new SimContext(world, 2, SHIPPED));

        assertEquals(5, Stand.trees(only(town)),
                "counting the whole claim every step is a scan the busiest moment"
                        + " in the mod cannot afford, and the ledger is running");
    }

    // --- the woodland renewing itself ---

    @Test
    void theSaplingsOffTheCrownsGoBackIntoTheGround() {
        Settlement town = campTown(2);
        Alone world = new Alone();

        for (int step = 1; step <= 12; step++) {
            LumberPlanner.advance(town, new SimContext(world, step, SHIPPED));
        }

        assertEquals(0, Stand.trees(only(town)), "the stand of twelve is down");
        assertTrue(Stand.growing(only(town)) > 0,
                "and what the jacks saved off the crowns is in the ground again");
    }

    @Test
    void aCampWithNoTreesAndNoSaplingsIsSimplyIdle() {
        Settlement town = campTown(2);
        Alone bare = new Alone();
        bare.trees = 0;

        for (int step = 1; step <= 100; step++) {
            LumberPlanner.advance(town, new SimContext(bare, step, SHIPPED));
        }

        assertEquals(0, town.stores().get(TownStores.WOOD),
                "there is nothing to fell and nothing to wait for, and that is"
                        + " the visible truth rather than a number to be tuned");
        assertTrue(Stand.isBare(only(town)));
        assertTrue(LumberPlanner.wantsMoreTimber(town),
                "the town wants timber; it simply has nowhere to get any");
    }

    @Test
    void aCampWithSeedInTheBoxPlantsItOnGroundItHasCleared() {
        Settlement town = campTown(1);
        Alone bare = new Alone();
        bare.trees = 0;
        town.setStock(TownStores.SAPLINGS, 6);

        LumberPlanner.advance(town, new SimContext(bare, 1, SHIPPED));

        assertEquals(6 - Stand.SAPLINGS_PER_JACK_PER_STEP,
                town.stores().get(TownStores.SAPLINGS),
                "a jack puts back what he can carry in a step, and no more");
        assertEquals(Stand.SAPLINGS_PER_JACK_PER_STEP, Stand.growing(only(town)));
    }
}
