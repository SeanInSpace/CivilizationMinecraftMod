package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.LumberPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.Stand;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
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
        return new Building("civilization:lumber_camp", new SimPos(12, 64, 0), 0, true);
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

    /**
     * A watched claim is worth exactly what an unwatched one is worth.
     *
     * <p>This read {@code aWatchedCampIsLeftToItsAxes} and asserted the opposite:
     * twenty steps of clock beside a player brought in nought logs and left the
     * stand of twelve entire, "for them to go and look at". That was the timber
     * half of <em>where there is a hand there is no clock</em>, and the playtest
     * of 2026-09-19 measured what it costs. The same town over the same three
     * hundred steps kept a granary of 449 with the player six hundred blocks off
     * and 144 and falling with him standing in the square; it died at step 1213
     * with sixty people in it. Timber went the same way food did, and for the
     * same reason: an axe swings only where a body is spawned, in reach, awake
     * and given a game tick, and none of those is a fact about the wood.
     *
     * <p>So the jacks' pace for the step is worked out once and spent by
     * whichever fidelity is in a position to spend it — see
     * {@code Building.creditByHand} and {@code LumberPlanner.fell}. These two
     * camps have no axes swinging at all (nothing here embodies anybody), which
     * is precisely the case that used to yield nothing, and they must come out
     * log for log.
     */
    @Test
    void aWatchedCampFellsExactlyWhatAnUnwatchedOneFells() {
        Settlement watched = campTown(2);
        Settlement alone = campTown(2);

        for (int step = 1; step <= 20; step++) {
            LumberPlanner.advance(watched, new SimContext(new Standing(), step, SHIPPED));
            LumberPlanner.advance(alone, new SimContext(new Alone(), step, SHIPPED));
        }

        assertEquals(alone.stores().get(TownStores.WOOD),
                watched.stores().get(TownStores.WOOD),
                "one ground, one bit — and the bit is only whether somebody is"
                        + " standing in the wood. It cannot change what the wood"
                        + " is worth");
        assertEquals(Stand.logs(only(alone)), Stand.logs(only(watched)),
                "and the same trees came down out of the same claim");
        assertTrue(watched.stores().get(TownStores.WOOD) > 0,
                "which is a camp that is actually working, not two that are"
                        + " identically idle");
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

    /**
     * The recount happens on arrival and once, which is what keeps the scan off
     * the busiest step in the mod.
     *
     * <p>Unchanged in subject and corrected in arithmetic. It used to read
     * {@code assertEquals(5, Stand.trees(...))} after two watched steps, which
     * worked only because a watched camp was felling nothing: the clock stood
     * aside for hands that were never going to swing. The clock works a watched
     * claim now — see {@code LumberPlanner.fell} and the fault it was written
     * against — so the stand is two steps shorter by the end, and the thing to
     * pin is the <em>ledger's provenance</em> rather than a frozen number. What
     * is standing plus what came off it still adds up to the five trees counted
     * on arrival, and not to the ninety-nine the world grew behind the camp's
     * back.
     */
    @Test
    void theCountIsTakenOnArrivalAndNotEveryStepAfterIt() {
        Settlement town = campTown(1);
        Standing world = new Standing();
        world.trees = 5;

        LumberPlanner.advance(town, new SimContext(world, 1, SHIPPED));
        world.trees = 99;   // the world moves on; nobody arrives again
        LumberPlanner.advance(town, new SimContext(world, 2, SHIPPED));

        assertEquals(5 * Stand.LOGS_PER_TREE,
                Stand.logs(only(town)) + town.stores().get(TownStores.WOOD),
                "counting the whole claim every step is a scan the busiest moment"
                        + " in the mod cannot afford, so the ledger runs off the"
                        + " count it was given — five trees' worth, standing or"
                        + " stacked, and not a log of the ninety-nine");
        assertTrue(town.stores().get(TownStores.WOOD) > 0,
                "and the camp did work both steps, which is what makes the sum"
                        + " above worth adding up");
    }

    // --- the woodland renewing itself ---

    @Test
    void theSaplingsOffTheCrownsGoBackIntoTheGround() {
        Settlement town = campTown(2);
        Alone world = new Alone();

        for (int step = 1; step <= 12; step++) {
            LumberPlanner.advance(town, new SimContext(world, step, SHIPPED));
        }

        // Down to the reserve and no further. This read `assertEquals(0, ...)`
        // and "the stand of twelve is down", which is the fault a playtest
        // reported rather than the rule: the town map of a grown town read
        // "0 trees standing, 591 coming up" and the claim was a bowl of bare
        // terraces. A camp keeps Stand.reserveTrees standing however much
        // timber the town wants.
        assertEquals(Stand.reserveTrees(only(town)), Stand.trees(only(town)),
                "the stand of twelve is cut back to its reserve");
        assertTrue(Stand.growing(only(town)) > 0,
                "and what the jacks saved off the crowns is in the ground again");
    }

    /**
     * The reserve is a share, and the share is the whole point of it.
     *
     * <p>A flat six was the first shape of this rule, and it is the right number
     * for the wood it was measured in and a ruinous one for the wood a town is
     * founded in: six of a founding camp's twelve trees is half the claim locked
     * up on the day the party most needs it, which cost two towns their growth —
     * see the changelog. A quarter keeps the intent and moves with the ground.
     */
    @Test
    void aBigWoodIsNeverFelledBelowAQuarterOfWhatWasFound() {
        Settlement town = campTown(3);
        Alone wood = new Alone();
        wood.trees = 60;

        int floor = 60 / Stand.RESERVE_SHARE;
        int lowest = Integer.MAX_VALUE;
        for (int step = 1; step <= 400; step++) {
            LumberPlanner.advance(town, new SimContext(wood, step, SHIPPED));
            lowest = Math.min(lowest, Stand.trees(only(town)));
        }

        assertEquals(floor, Stand.reserveTrees(only(town)),
                "a sixty-tree wood keeps fifteen");
        assertTrue(lowest >= floor,
                "and is never cut below them on any step of four hundred: the"
                        + " lowest the stand ever read was " + lowest);
        assertTrue(town.stores().get(TownStores.WOOD) > 0
                        || Stand.growing(only(town)) > 0,
                "while still being a wood the town actually works");
    }

    /** And a founding camp's dozen keeps three rather than half of itself. */
    @Test
    void aSmallStandKeepsAQuarterAndNotAHalf() {
        Building camp = camp();
        Stand.recount(camp, 12);

        assertEquals(3, Stand.reserveTrees(camp), "a quarter of the dozen it was found in");
        assertEquals(9 * Stand.LOGS_PER_TREE, Stand.fellableLogs(camp),
                "which leaves nine trees to build the town out of, not six");
    }

    /**
     * And the reserve does not ratchet downwards as the axes work.
     *
     * <p>A share of what is <em>standing</em> would: a quarter of eight is two and
     * a quarter of two is nothing, so the floor would walk itself down to the
     * clear-cut it exists to forbid. The share is of the stand as first counted,
     * which is why the camp carries the number.
     */
    @Test
    void theShareIsOfTheWoodAsFoundAndNotOfWhatIsLeft() {
        Building camp = camp();
        Stand.recount(camp, 40);
        int reserve = Stand.reserveTrees(camp);

        Stand.fell(camp, 30 * Stand.LOGS_PER_TREE);
        assertEquals(reserve, Stand.reserveTrees(camp),
                "felling does not lower the floor felling is measured against");

        // And a later count -- a player walks up and the trunks are counted for
        // real -- is a count of what the axes have left, so it does not either.
        Stand.recount(camp, 10);
        assertEquals(reserve, Stand.reserveTrees(camp),
                "nor does re-counting the wood that is left of it");
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
