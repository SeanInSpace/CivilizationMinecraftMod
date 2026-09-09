package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.PathPlanner;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.RepairPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
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
 * <p>And "looking" is judged at the work, not at the town. A player in the
 * square is not watching a plot two hundred blocks out over the ridge, and the
 * clock is welcome to raise it.
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

    @Test
    void asiteOverTheRidgeIsUnwatchedEvenWhenTheSquareIsNot() {
        // The subtlety the whole rule turns on. Watched is a property of the
        // work, not of the town: judging it at the center would freeze every
        // outlying plot of every town anybody ever walked into.
        Settlement town = townWithBuilders(false);
        SimPos faraway = new SimPos((int) RADIUS * 3, 64, 0);
        BuildTask task = surveyed(faraway);
        town.enqueueBuild(task);
        Bridge bridge = inTheSquare();

        assertTrue(bridge.playerWithin(town.center(), RADIUS), "the square is watched...");
        assertFalse(bridge.playerWithin(faraway, RADIUS), "...and the plot is not");

        for (int step = 0; step < 500; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertFalse(town.buildQueue().contains(task),
                "so the clock raised it, with nobody there to be fooled");
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
