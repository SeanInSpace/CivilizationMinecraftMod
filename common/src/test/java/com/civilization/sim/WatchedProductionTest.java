package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.LumberPlanner;
import com.civilization.sim.settlement.MinePlanner;
import com.civilization.sim.settlement.Seam;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.Stand;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of the two fidelities is allowed to produce, and when.
 *
 * <p>There used to be a third answer here, and it was the wrong one. A watched
 * camp whose axes had gone quiet for twelve steps got its timber from the clock
 * anyway — a <em>grace floor</em> — because the alternative was a town that
 * stopped earning for as long as somebody stood in it. That was the right fix
 * for the wrong fault: the reason a watched camp earned nothing was that it had
 * nothing to fell, and paying it for felling nothing is not an improvement on
 * saying so.
 *
 * <p>The floor is gone, along with the grace period and the yield tables under
 * both trades. So, now, is the rule it was propping up. <em>Where there is a
 * hand there is no clock</em> read beautifully and starved towns: the playtest of
 * 2026-09-19 ran one town for three hundred steps twice over, changing nothing
 * but where the player stood, and got a granary of 449 with him six hundred
 * blocks off against 144 and falling with him in the square — and a dead town at
 * step 1213 with sixty people in it. Hands are not a second implementation of the
 * ledger. They are bodies, and a body can be out of reach, unspawned, walking,
 * asleep, boxed in by terrain, or simply given no game tick at all, which is
 * every step of a {@code /civ step} burst.
 *
 * <p>What is left is <strong>one budget</strong>. The step's yield is worked out
 * exactly as it always was; the real axes and picks book what they spend of it
 * ({@code Building.creditByHand}) and the clock credits the remainder. A watched
 * camp and an unwatched one therefore come out log for log — and a camp with no
 * trees and no saplings still produces nothing at either fidelity, which is not a
 * bug to be papered over but the state of the woodland, visibly.
 */
class WatchedProductionTest {

    /** A world where somebody is always standing in the town. */
    private static final class Watched implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int countTreesNear(SimPos center, int radius) { return 12; }
        @Override public int countStoneBelow(SimPos c, int radius, int depth) { return 2000; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** The same ground, with nobody on it. */
    private static final class Alone implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int countTreesNear(SimPos center, int radius) { return 12; }
        @Override public int countStoneBelow(SimPos c, int radius, int depth) { return 2000; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** Ground nobody has ever loaded: no count can be taken of it. */
    private static final class Unseen implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static SimContext at(WorldBridge world, long step) {
        return new SimContext(world, step, SimSettings.SANDBOX);
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

    private static Building only(Settlement town) {
        return town.buildings().getFirst();
    }

    // --- timber ---

    /**
     * Fifty steps of idle axes under a player's eye earn what fifty unwatched
     * steps earn, because idleness is not a property of the wood.
     *
     * <p>This was {@code aWatchedCampEarnsNothingFromTheClockHoweverLongItsAxesAreIdle},
     * and it asserted nought — "there is no length of idleness that puts the clock
     * back in the wood". Fifty steps of a stand of twelve trees standing untouched
     * in front of somebody is exactly the state the playtest found and exactly why
     * its towns starved: nobody was <em>refusing</em> to fell, there was simply no
     * body there to swing, and the ledger was told to pretend that meant the trees
     * were unfellable.
     */
    @Test
    void aWatchedCampFellsWhatAnUnwatchedOneFellsHoweverIdleItsAxesAre() {
        Settlement watched = townWith("civilization:lumber_camp", Profession.LUMBERJACK);
        Settlement alone = townWith("civilization:lumber_camp", Profession.LUMBERJACK);

        for (int step = 1; step <= 50; step++) {
            LumberPlanner.advance(watched, at(new Watched(), step));
            LumberPlanner.advance(alone, at(new Alone(), step));
        }

        assertEquals(alone.stores().get(TownStores.WOOD),
                watched.stores().get(TownStores.WOOD),
                "fifty steps, one claim, and the only difference is who is looking"
                        + " at it — the books have to come out the same");
        assertTrue(watched.stores().get(TownStores.WOOD) > 0,
                "and the claim was worth felling, which is what makes the"
                        + " comparison above mean anything");
    }

    @Test
    void anUnwatchedCampFellsWhatIsActuallyStanding() {
        Settlement town = townWith("civilization:lumber_camp", Profession.LUMBERJACK);

        LumberPlanner.advance(town, at(new Alone(), 1));

        assertEquals(3 * Stand.LOGS_PER_JACK_PER_STEP, town.stores().get(TownStores.WOOD),
                "three jacks, four logs each, off a stand of twelve trees");
    }

    /** Ground somebody has looked at and found bare. */
    private static final class Bare implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int countTreesNear(SimPos center, int radius) { return 0; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    @Test
    void aCampWithNothingStandingAndNothingPlantedProducesNothing() {
        Settlement town = townWith("civilization:lumber_camp", Profession.LUMBERJACK);
        town.setStock(TownStores.SAPLINGS, 0);

        for (int step = 1; step <= 40; step++) {
            LumberPlanner.advance(town, at(new Bare(), step));
        }

        assertEquals(0, town.stores().get(TownStores.WOOD),
                "a camp on open grass with no seed in the box brings in nothing,"
                        + " and the town can see that it is standing on nothing");
        assertTrue(Stand.isBare(only(town)),
                "which is a counted camp with nothing in it, not an uncounted one");
    }

    @Test
    void aFelledClaimComesBackOutOfItsOwnSaplings() {
        Settlement town = townWith("civilization:lumber_camp", Profession.LUMBERJACK);
        Alone world = new Alone();
        // Twelve trees is seventy-two logs, and three jacks take twelve a step.
        for (int step = 1; step <= 6; step++) {
            LumberPlanner.advance(town, at(world, step));
        }
        // The reserve, not nothing. See Stand.reserveTrees: a camp that fells
        // its claim to the last trunk is the playtest's "0 trees standing, 591
        // coming up", and the point of this test — that a claim comes back out
        // of its own saplings — is better made by a wood that never went away.
        assertEquals(Stand.reserveTrees(only(town)), Stand.trees(only(town)),
                "the stand of twelve is cut back to its reserve");

        for (int step = 7; step <= 400; step++) {
            LumberPlanner.advance(town, at(world, step));
        }

        assertTrue(Stand.growing(only(town)) > 0,
                "there is wood coming up on ground the camp cleared");
        assertTrue(town.stores().get(TownStores.WOOD) > Stand.LOGS_PER_TREE * 12,
                "and the camp has cut more timber than ever stood there,"
                        + " because it put back what it took");
    }

    @Test
    void groundNobodyHasEverLoadedIsGroundNobodyHasCounted() {
        Settlement town = townWith("civilization:lumber_camp", Profession.LUMBERJACK);

        LumberPlanner.advance(town, at(new Unseen(), 1));
        // What is still standing plus what came off it on the same step.
        int guessed = Stand.logs(only(town)) + town.stores().get(TownStores.WOOD);

        assertEquals(Stand.UNSURVEYED * Stand.LOGS_PER_TREE, guessed,
                "a camp on ground nobody can load works the stand its own siting"
                        + " implies — the alternative is a town that produces"
                        + " nothing for as long as nobody visits it, which is the"
                        + " fault this whole ledger was written to fix");

        // And the guess is a placeholder, not a gift: the day somebody walks up,
        // the real trunks are counted and whatever they say is what the camp has.
        //
        // Measured as standing-plus-taken rather than as a bare tree count. This
        // line used to read assertEquals(12, Stand.trees(...)), which held only
        // because a watched camp felled nothing on the step somebody arrived —
        // the clock stood aside for axes that were not there. It settles up with
        // them now, so the arrival step also cuts its twelve logs, and what the
        // assertion is really about is whose number the ledger is running on.
        int guessedStore = town.stores().get(TownStores.WOOD);
        LumberPlanner.advance(town, at(new Watched(), 2));
        int cutOnArrival = town.stores().get(TownStores.WOOD) - guessedStore;

        assertEquals(12 * Stand.LOGS_PER_TREE,
                Stand.logs(only(town)) + cutOnArrival,
                "the world's number, taken on arrival and believed: twelve trees'"
                        + " worth standing and stacked, and not a log of the"
                        + " seventy-two the camp had been guessing at");
        assertTrue(cutOnArrival > 0,
                "and the step that took the count still worked the claim, because"
                        + " being counted is not a holiday");
    }

    // --- stone ---

    /**
     * The stone half of the same correction. This was
     * {@code aWatchedMineEarnsNothingFromTheClockHoweverLongItsPicksAreIdle} and
     * asserted nought; a seam of two thousand blocks does not stop being two
     * thousand blocks because somebody walked up to the adit.
     */
    @Test
    void aWatchedMineCutsWhatAnUnwatchedOneCutsHoweverIdleItsPicksAre() {
        Settlement watched = townWith("civilization:mine", Profession.MINER);
        Settlement alone = townWith("civilization:mine", Profession.MINER);

        for (int step = 1; step <= 50; step++) {
            MinePlanner.advance(watched, at(new Watched(), step));
            MinePlanner.advance(alone, at(new Alone(), step));
        }

        assertEquals(alone.stores().get(TownStores.STONE),
                watched.stores().get(TownStores.STONE),
                "the rock under a mine is not a percentage and it is not an"
                        + " audience either");
        assertTrue(watched.stores().get(TownStores.STONE) > 0,
                "and there was rock down there to cut");
    }

    @Test
    void anUnwatchedMineCutsTheSeamItWasSizedFrom() {
        Settlement town = townWith("civilization:mine", Profession.MINER);

        MinePlanner.advance(town, at(new Alone(), 1));

        assertEquals(3 * Seam.STONE_PER_MINER_PER_STEP, town.stores().get(TownStores.STONE),
                "three miners, six blocks each");
        assertEquals(2000 - 3 * Seam.STONE_PER_MINER_PER_STEP, Seam.remaining(only(town)),
                "and every block of it came out of the seam");
    }

    @Test
    void aMineThatIsCutOutStops() {
        Settlement town = townWith("civilization:mine", Profession.MINER);
        Alone world = new Alone();
        MinePlanner.advance(town, at(world, 1));
        Seam.recount(only(town), 4);          // nearly spent

        MinePlanner.advance(town, at(world, 2));
        int held = town.stores().get(TownStores.STONE);
        MinePlanner.advance(town, at(world, 3));

        assertTrue(Seam.isExhausted(only(town)), "the last of the rock has come out");
        assertEquals(held, town.stores().get(TownStores.STONE),
                "and a mine that is cut out cuts nothing more");
    }
}
