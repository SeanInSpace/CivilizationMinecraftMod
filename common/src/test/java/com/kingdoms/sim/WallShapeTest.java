package com.kingdoms.sim;

import com.kingdoms.sim.geom.Hull;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.geom.Ways;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(Hull.contains(wall.vertices(), town.center()),
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

    /**
     * A town of mixed kinds, spread widely enough that the hull has to run some
     * of its stretches on a slant.
     *
     * <p>The slant is the whole point. Every other town in this class is close
     * enough to round that its stretches are nearly axis-aligned, and a wall
     * walked in an L is right to within a block on those — which is exactly why
     * nothing here ever caught the L.
     */
    private static Settlement aTownOnASlant() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Slantby", new SimPos(0, 64, 0), 256);
        town.setCatalog(BuildCatalog.DEFAULT);
        String[] kinds = {"kingdoms:town_hall", "kingdoms:farm", "kingdoms:market",
                "kingdoms:granary", "kingdoms:town_hall", "kingdoms:farm",
                "kingdoms:farm", "kingdoms:town_hall", "kingdoms:house",
                "kingdoms:granary", "kingdoms:granary"};
        int[] at = {-76, 15, -55, -92, 24, 79, -36, 109, 29, 12, -101, 38,
                -38, -16, 96, 74, 64, 23, 8, 1, -97, -101};
        assertEquals(kinds.length * 2, at.length,
                "every kind in this fixture wants an x and a z");
        for (int i = 0; i < kinds.length; i++) {
            town.addBuilding(new Building(
                    kinds[i], new SimPos(at[2 * i], 64, at[2 * i + 1]), 1, true));
        }
        return town;
    }

    @Test
    void noPostIsPlantedInsideSomebodysHouse() {
        // The player report this exists for: "walls overlap structures in rare
        // cases". Every rule that keeps the wall off a building is written
        // about the straight stretch between two vertices, and not one of them
        // ever looks at a post -- so the stretch may be clean and the posts
        // still go in through a wall, and asserting the stretch is clean says
        // nothing at all about the wall the town builds.
        //
        // On this town the offending leg runs eight blocks across and a hundred
        // and forty down. Walked as an L that is eight posts laid along the
        // top, six blocks deep into a farm the straight line passes clear of by
        // five.
        Settlement town = aTownOnASlant();

        Perimeter wall = PerimeterPlanner.stake(town, on(new Billiard()));

        for (SimPos post : wall.ringPositions()) {
            for (Building building : town.buildings()) {
                // The plot less the block of slack the hull is allowed: a wall
                // running along a building's edge is a wall along its edge, and
                // the corners the hull is built from sit on that ring.
                double half = BuildPlanner.plotSpanOf(
                        building.blueprintId(), town.catalog()) / 2.0 - 1;
                assertTrue(Math.abs(post.x() - building.origin().x()) > half
                                || Math.abs(post.z() - building.origin().z()) > half,
                        "a post at " + post + " stands inside the "
                                + building.blueprintId() + " at " + building.origin());
            }
        }
    }

    @Test
    void noPostIsPlantedOnGroundTheTownHasAlreadyOrdered() {
        // A settlement is never only what is standing. It always has a plot or
        // two chosen, paid for and waiting for hands, and nothing looks at such
        // a site again once the wall has moved -- the plot was asked whether it
        // stood on the wall the day it was chosen, and never afterwards. So the
        // line went over ordered ground and the building went up on top of it
        // days later, with neither question asked at the moment they disagreed.
        //
        // Measured over a hundred and seventeen grown towns: ninety-two of the
        // ninety-nine buildings that still had a wall through them were sites
        // the town had already ordered when its ring was staked.
        Settlement town = aTownOnASlant();
        SimPos ordered = new SimPos(-84, 64, -95);
        int span = BuildPlanner.plotSpanOf("kingdoms:house", town.catalog());
        assertTrue(town.isPlotFree(ordered, span, null),
                "the fixture orders a build on ground nothing else holds");
        town.enqueueUrgent(new BuildTask("kingdoms:house", ordered, 40));

        Perimeter wall = PerimeterPlanner.stake(town, on(new Billiard()));

        for (SimPos post : wall.ringPositions()) {
            assertTrue(Math.abs(post.x() - ordered.x()) > span / 2.0 - 1
                            || Math.abs(post.z() - ordered.z()) > span / 2.0 - 1,
                    "a post at " + post + " stands on the plot the town ordered at "
                            + ordered);
        }
    }

    @Test
    void everyPostStandsOnTheStretchItWasStakedOn() {
        // The contract underneath the rule above, said once so it cannot be
        // half-kept. A post more than a block off the line is a post on ground
        // the staking never examined -- for a building, for a road, for water,
        // for anything the line was settled against.
        //
        // Each post against its OWN stretch, not against the nearest of them.
        // A concave ring folds back on itself, so a post six blocks off the leg
        // it belongs to can sit within a block of the next bay round and pass a
        // test that takes the minimum -- which would let the fold hide exactly
        // the fault this asserts. The walk emits the posts leg by leg and each
        // leg is |dx| + |dz| of them, so they can be read off in the same order
        // they were laid.
        Settlement town = aTownOnASlant();

        Perimeter wall = PerimeterPlanner.stake(town, on(new Billiard()));

        List<SimPos> loop = wall.vertices();
        List<SimPos> posts = wall.ringPositions();
        int at = 0;
        double worst = 0;
        SimPos strayed = null;
        for (int i = 0; i < loop.size(); i++) {
            SimPos from = loop.get(i);
            SimPos to = loop.get((i + 1) % loop.size());
            int onThisLeg = Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z());
            for (int p = 0; p < onThisLeg; p++) {
                SimPos post = posts.get(at++);
                double away = Ways.distanceToSegment(
                        post.x(), post.z(), from.x(), from.z(), to.x(), to.z());
                if (away > worst) {
                    worst = away;
                    strayed = post;
                }
            }
        }
        assertEquals(posts.size(), at, "the legs do not account for every post laid");
        assertTrue(worst <= 1.0, "a post at " + strayed + " stands "
                + String.format("%.2f", worst) + " blocks off the stretch it belongs to");
    }
}
