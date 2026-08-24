package com.kingdoms.sim;

import com.kingdoms.sim.geom.Hull;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape a wall takes around a town.
 *
 * <p>One rule is absolute and the rest are preferences: everything the town
 * owns must end up inside. A cleverer line that leaves a farm outside the gate
 * is worse than a dull rectangle, so the terrain is only ever allowed to move
 * the wall between positions that all still enclose the place.
 */
class WallShapeTest {

    /** Flat ground everywhere, so only the shape is under test. */
    private static final class Billiard implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return 64; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /**
     * A ridge running north-south at x = 40: rough to cross, flat either side.
     *
     * <p>Gives the contour something to prefer, so "does it read the ground at
     * all" is answerable rather than a matter of faith.
     */
    private static final class Ridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) {
            return Math.abs(pos.x() - 40) <= 2 ? 64 + 12 * (3 - Math.abs(pos.x() - 40)) : 64;
        }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static SimContext on(WorldBridge bridge) {
        return new SimContext(bridge, 0, SimSettings.SANDBOX);
    }

    private static Settlement townWith(int... offsets) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        for (int i = 0; i + 1 < offsets.length; i += 2) {
            town.addBuilding(new Building("kingdoms:house",
                    new SimPos(offsets[i], 64, offsets[i + 1]), 1, true));
        }
        return town;
    }

    /** Every corner of every plot, which is what the wall must enclose. */
    private static void assertEnclosesEverything(Perimeter wall, Settlement town) {
        for (Building building : town.buildings()) {
            SimPos at = building.origin();
            assertTrue(Hull.contains(wall.vertices(), at),
                    at + " is outside its own town wall");
        }
        assertTrue(Hull.contains(wall.vertices(), town.centre()),
                "the town square is outside the wall");
    }

    @Test
    void aWallEnclosesEveryBuilding() {
        Settlement town = townWith(30, 0, -25, 12, 8, -28, -14, -20, 22, 19);

        Perimeter wall = PerimeterPlanner.stake(town, on(new Billiard()));

        assertEnclosesEverything(wall, town);
    }

    @Test
    void aTownWithNothingBuiltStillGetsARing() {
        Settlement town = townWith();

        Perimeter wall = PerimeterPlanner.stake(town, on(new Billiard()));

        assertTrue(wall.vertices().size() >= 3, "a ring needs at least three corners");
        assertTrue(wall.length() > 0, "and some posts to put in it");
        assertEnclosesEverything(wall, town);
    }

    @Test
    void anOutlyingFarmDoesNotDragTheWholeLineOutAroundIt() {
        // The reason for a concave hull at all. A convex line around a cluster
        // plus one distant building fortifies the empty field between them.
        Settlement compact = townWith(12, 0, -12, 0, 0, 12, 0, -12);
        Settlement strung = townWith(12, 0, -12, 0, 0, 12, 0, -12, 90, 90);

        Perimeter tight = PerimeterPlanner.stake(compact, on(new Billiard()));
        Perimeter stretched = PerimeterPlanner.stake(strung, on(new Billiard()));

        assertEnclosesEverything(stretched, strung);
        assertTrue(stretched.vertices().size() >= tight.vertices().size(),
                "reaching a far building adds corners rather than just a bigger box");
    }

    @Test
    void theWallStillHoldsEverythingAfterTheGroundHasMovedIt() {
        // The terrain may nudge the line about as much as it likes; it may
        // never talk it into abandoning a building.
        Settlement town = townWith(30, 0, 34, 10, -25, 12, 8, -28, 20, 25);

        Perimeter wall = PerimeterPlanner.stake(town, on(new Ridge()));

        assertEnclosesEverything(wall, town);
    }

    @Test
    void theWallPrefersEvenGroundWhenItHasTheChoice() {
        // Same town, two worlds. On the ridge world the line should not sit
        // squarely on the steep ground if flatter ground was within reach.
        Settlement town = townWith(30, 0, 34, 10, -25, 12, 8, -28, 20, 25);
        Ridge ridge = new Ridge();

        Perimeter wall = PerimeterPlanner.stake(town, on(ridge));

        int onSteepGround = 0;
        for (SimPos vertex : wall.vertices()) {
            int here = ridge.surfaceHeight(vertex);
            int east = ridge.surfaceHeight(new SimPos(vertex.x() + 1, vertex.y(), vertex.z()));
            if (Math.abs(east - here) >= 12) {
                onSteepGround++;
            }
        }
        assertTrue(onSteepGround <= wall.vertices().size() / 2,
                "a wall that reads the ground does not put half its posts on a cliff face");
    }

    @Test
    void theRingIsWalkableAsAClosedLoop() {
        Settlement town = townWith(30, 0, -25, 12, 8, -28);

        Perimeter wall = PerimeterPlanner.stake(town, on(new Billiard()));

        assertTrue(wall.ringPositions().size() > wall.vertices().size(),
                "the corners are walked out into a continuous line of posts");
        assertTrue(wall.gates().size() > 0, "and a wall with no gate is a wall nobody can use");
    }
}
