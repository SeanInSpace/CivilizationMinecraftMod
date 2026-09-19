package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.PathPlanner;
import com.civilization.sim.settlement.PerimeterPlanner;
import com.civilization.sim.settlement.RepairPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where there is a player there is no clock.
 *
 * <p>One rule, four kinds of work. If somebody is inside the observed radius of
 * the place the work is happening, that work is done by hands or it is not done
 * at all — no building, no wall post, no stretch of street, no repair. There is
 * no grace period, no fallthrough after a patient interval, and no exception for
 * a town that has nobody free to send: a structure that rises in front of a
 * player with nobody touching it is magic whichever of those excuses produced
 * it, and the honest alternative is bare ground and a line on {@code /civ info}
 * saying why.
 *
 * <p>The other half of the rule matters just as much and is tested just as
 * hard: with nobody looking, the clock runs exactly as it always did. A town you
 * have never visited still grows, because that is what makes it a world rather
 * than a stage set.
 *
 * <p>And "looking" is judged generously and per site: a whole view distance —
 * twice the observed radius — around the very ground that would change. See
 * {@link Settlement#isOverlooked}. That number has been wrong in both directions
 * and the history is worth keeping. Judged tightly at the plot, a player in the
 * square counted as not watching a plot a hundred blocks out over the ridge, and
 * Millbrook's hall, mine and mill were duly stamped in front of somebody standing
 * at the town center. Judged for the whole claim — the fix that followed — run A
 * of the 2026-09-19 playtest grew a town 424 by 497 blocks across, so a player in
 * its square made ground four hundred blocks away "watched": no hand could walk
 * there and the clock was forbidden to touch it, and eight buildings sat at
 * {@code [PENDING placement]} for the rest of the run.
 *
 * <p>A render distance is the honest line, because this was never a question
 * about work. It is a question about magic, and past your own view distance there
 * is nothing to watch. What the tighter observed radius still decides is where
 * the world's blocks must be made to agree with the ledger, and — since the clock
 * settles up with the hands rather than standing aside for them — nothing at all
 * about what a site produces. See {@code WatchedBooksTest}.
 */
class WatchedWorkTest {

    private static final BuildingType HOUSE =
            new BuildingType("test:house", 20, 1, 1, 0, 80, 4);
    private static final BuildingType COTTAGE =
            new BuildingType("test:cottage", 100, 0, 0, 0, 80, 4);

    /** How far a player has to be from a site before it stops being watched. */
    private static final double RADIUS = SimSettings.SANDBOX.observedRadius();

    /**
     * A world with one player standing at a named spot.
     *
     * <p>Everything is loaded, deliberately: chunk load is not the question any
     * more and a fake that conflated the two would let the old bug back in
     * through the door it came in by.
     */
    private static class Bridge implements WorldBridge {
        final SimPos player;
        int stamped;
        int mended;

        Bridge(SimPos player) {
            this.player = player;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) {
            if (player == null) {
                return false;
            }
            return Math.hypot(pos.x() - player.x(), pos.z() - player.z()) <= radius;
        }

        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            stamped++;
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** Nobody anywhere. */
    private static Bridge empty() {
        return new Bridge(null);
    }

    /** Somebody standing in the middle of the town. */
    private static Bridge inTheSquare() {
        return new Bridge(new SimPos(0, 64, 0));
    }

    // --- buildings ---

    private static Settlement townWithBuilders(boolean embodied) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 512);
        town.setCatalog(List.of(HOUSE));
        Person hand = new Person(
                Person.Id.random(), "Alder", Profession.BUILDER, town.center());
        hand.setEmbodied(embodied);
        town.addResident(hand);
        return town;
    }

    /** A task the view layer has surveyed, so there is a plan to lay. */
    private static BuildTask surveyed(SimPos at) {
        BuildTask task = new BuildTask("test:house", at, 20);
        task.setSiteY(at.y());
        task.setPlan(200, 200);
        return task;
    }

    @Test
    void aWatchedSiteWithNoHandsGainsNothingInFiveHundredSteps() {
        Settlement town = townWithBuilders(false);
        BuildTask task = surveyed(new SimPos(10, 64, 10));
        town.enqueueBuild(task);
        Bridge bridge = inTheSquare();

        for (int step = 0; step < 500; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertEquals(0, task.workDone(), "not one block of it");
        assertEquals(0.0, task.completionFraction(), 1e-9);
        assertTrue(town.buildings().isEmpty(), "and nothing finished behind the ground");
        assertEquals(0, bridge.stamped, "and nothing stamped in front of the player");
    }

    @Test
    void theSameTownUnwatchedFinishesItOnTheClock() {
        Settlement town = townWithBuilders(false);
        BuildTask task = surveyed(new SimPos(10, 64, 10));
        town.enqueueBuild(task);
        Bridge bridge = empty();

        for (int step = 0; step < 500; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertFalse(town.buildQueue().contains(task), "the clock saw it through");
        assertEquals(1, town.countBuildings("test:house"));
    }

    /**
     * A plot over the ridge but inside a view distance waits for hands; a plot
     * past one is the clock's again.
     *
     * <p>This was {@code asiteOverTheRidgeIsWatchedBecauseItsTownIs}, and it
     * asserted that a plot three observed radii out — 288 blocks — waited for
     * hands, because it was inside the claim and the claim was the unit. Both
     * halves of the old reasoning are here, corrected: Millbrook's hundred-odd
     * blocks are still hand-work, because a player at the square would watch that
     * building rise, and 288 blocks is not, because he would not.
     *
     * <p>Fault N6 of the 2026-09-19 playtest is why the line moved. Run A's town
     * ended 424 by 497 blocks across, with a maximum radius of 429 from the
     * middle, so "inside the claim" stopped meaning anything a player could see.
     * Eight of its buildings were stuck at {@code [PENDING placement]} under the
     * words <em>waiting for hands, out of sight</em> — hands that could not walk
     * there and a clock that was not allowed to.
     */
    @Test
    void aplotWithinAviewDistanceWaitsForHandsAndOneBeyondItDoesNot() {
        // Millbrook's own distance: past the observed radius, well inside a view
        // distance. Nothing may raise this but a builder.
        Settlement millbrook = townWithBuilders(false);
        SimPos overTheRidge = new SimPos(109, 64, 0);
        BuildTask nearby = surveyed(overTheRidge);
        millbrook.enqueueBuild(nearby);
        Bridge watching = inTheSquare();

        assertFalse(watching.playerWithin(overTheRidge, RADIUS),
                "nobody is standing at the plot itself...");
        assertTrue(watching.playerWithin(overTheRidge, RADIUS * Settlement.SIGHT_RADII),
                "...but he is looking straight at it, which is what decides it");

        for (int step = 0; step < 500; step++) {
            millbrook.step(new SimContext(watching, step, SimSettings.SANDBOX));
        }

        assertTrue(millbrook.buildQueue().contains(nearby),
                "so the clock does not raise it; it waits for hands");
        assertEquals(0, watching.stamped, "and nothing appeared in the distance");

        // And the far side of a grown claim, which is the case that condemned the
        // whole-claim rule: three observed radii out, past anybody's horizon, on
        // ground no crew was ever going to walk to and back from.
        Settlement sprawl = townWithBuilders(false);
        SimPos faraway = new SimPos((int) RADIUS * 3, 64, 0);
        BuildTask far = surveyed(faraway);
        sprawl.enqueueBuild(far);
        Bridge alsoWatching = inTheSquare();

        assertTrue(faraway.horizontalDistance(sprawl.center()) <= sprawl.claimRadius(),
                "still inside the claim, which used to be the whole argument");
        assertFalse(alsoWatching.playerWithin(faraway, RADIUS * Settlement.SIGHT_RADII),
                "and past a whole view distance, which is the argument now");

        for (int step = 0; step < 500; step++) {
            sprawl.step(new SimContext(alsoWatching, step, SimSettings.SANDBOX));
        }

        assertFalse(sprawl.buildQueue().contains(far),
                "so the clock raises it — there is nobody out there to be fooled,"
                        + " and a plot nobody can reach or see is exactly what the"
                        + " clock is for");
        assertEquals(1, sprawl.countBuildings("test:house"));
    }

    @Test
    void awatchedSiteSaysWhyItIsNotMoving() {
        Settlement town = townWithBuilders(false);
        BuildTask task = surveyed(new SimPos(10, 64, 10));
        town.enqueueBuild(task);

        town.step(new SimContext(inTheSquare(), 0, SimSettings.SANDBOX));

        assertEquals("no builder has reached the site", task.waitingOnHands(),
                "a site standing at nought per cent has to be able to say why");
    }

    // --- the wall ---

    private static Settlement walled(WorldBridge bridge) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Wallburg", new SimPos(0, 64, 0), 256);
        town.addResident(new Person(
                Person.Id.random(), "Hand", Profession.BUILDER, town.center()));
        town.bank(1000);
        town.setStock(TownStores.WOOD, 4000);
        town.setPerimeter(PerimeterPlanner.stake(
                town, new SimContext(bridge, 0, SimSettings.SANDBOX)));
        return town;
    }

    @Test
    void awatchedWallStretchRaisesNoPostsOnTheClock() {
        Bridge bridge = inTheSquare();
        Settlement town = walled(bridge);

        for (int step = 1; step <= 500; step++) {
            PerimeterPlanner.advance(town, new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertEquals(0, town.perimeter().laid(),
                "five hundred steps of clock beside a player, and not one post");
    }

    @Test
    void anunwatchedWallStillGoesUp() {
        Bridge bridge = empty();
        Settlement town = walled(bridge);

        for (int step = 1; step <= 20; step++) {
            PerimeterPlanner.advance(town, new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertTrue(town.perimeter().laid() > 0, "nobody is looking, so the clock lays it");
    }

    // --- the roads ---

    private static Settlement withOneStretch() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Wegholt", new SimPos(0, 64, 0), 256);
        town.paths().add(new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(8, 64, 0)));
        return town;
    }

    @Test
    void awatchedStretchOfStreetIsNotOpenedByTheClock() {
        Bridge bridge = inTheSquare();
        Settlement town = withOneStretch();

        for (int step = 1; step <= 500; step++) {
            PathPlanner.advance(town, new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertFalse(town.paths().isOpened(0),
                "a street does not unroll itself in front of anybody");
    }

    @Test
    void anunwatchedStretchOfStreetIsStillOpened() {
        Bridge bridge = empty();
        Settlement town = withOneStretch();

        PathPlanner.advance(town, new SimContext(bridge, 1, SimSettings.SANDBOX));

        assertTrue(town.paths().isOpened(0), "nobody is looking, so the clock opens it");
    }

    // --- repairs ---

    /** A town with a standing cottage, a builder, and the stock to mend it. */
    private static Settlement mendable(boolean embodied) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Mendburg", new SimPos(0, 64, 0), 64);
        town.setCatalog(List.of(COTTAGE));
        Building cottage = new Building(COTTAGE.id(), new SimPos(10, 64, 10), 1, true);
        cottage.setFootprint(new Footprint(64, 5, 5, 4));
        town.addBuilding(cottage);
        Person hand = new Person(
                Person.Id.random(), "Alder", Profession.BUILDER, town.center());
        hand.setEmbodied(embodied);
        town.addResident(hand);
        town.setStock(TownStores.WOOD, 4000);
        town.setStock(TownStores.STONE, 4000);
        return town;
    }

    /** Counts a cottage that is missing a quarter of itself. */
    private static final class HoleyBridge extends Bridge {
        int standing = 200;

        HoleyBridge(SimPos player) {
            super(player);
        }

        @Override public int solidBlocksIn(SimPos origin, Footprint plot) { return standing; }

        @Override public int repairBlueprint(String id, SimPos origin, int facing) {
            mended++;
            int missing = Math.max(0, 200 - standing);
            standing = 200;
            return missing;
        }
    }

    private static HoleyBridge damagedCottage(SimPos player) {
        HoleyBridge bridge = new HoleyBridge(player);
        bridge.standing = 200;
        return bridge;
    }

    @Test
    void awatchedRepairIsNeverPaidForByTheClock() {
        HoleyBridge bridge = damagedCottage(new SimPos(10, 64, 10));
        Settlement town = mendable(false);

        // Seen whole once, so there is a census to fall short of.
        RepairPlanner.advance(town, new SimContext(bridge, 0, SimSettings.SANDBOX));
        bridge.standing = 150;

        for (int step = 1; step <= 500; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertEquals(0, bridge.mended,
                "the missing blocks were never conjured back beside the player");
        assertEquals(150, bridge.standing, "so the hole is still a hole");
    }

    @Test
    void anunwatchedRepairIsStillSeenTo() {
        HoleyBridge bridge = damagedCottage(null);
        Settlement town = mendable(false);

        RepairPlanner.advance(town, new SimContext(bridge, 0, SimSettings.SANDBOX));
        bridge.standing = 150;

        for (int step = 1; step <= 500; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertTrue(bridge.mended > 0, "nobody is looking, so the clock puts it back");
        assertEquals(200, bridge.standing);
    }
}
