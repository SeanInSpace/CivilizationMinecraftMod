package com.civilization.sim;

import com.civilization.sim.geom.Hull;
import com.civilization.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wrapping a town's buildings in a loop.
 *
 * <p>The property that matters for a wall is the boring one: everything the
 * town owns has to end up inside it. A clever line that leaves a farm outside
 * is worse than a dull rectangle.
 */
class HullTest {

    private static SimPos at(int x, int z) {
        return new SimPos(x, 64, z);
    }

    private static List<SimPos> square(int half) {
        return List.of(at(-half, -half), at(half, -half), at(half, half), at(-half, half));
    }

    @Test
    void fourCornersWrapToFourCorners() {
        List<SimPos> loop = Hull.convex(square(10));

        assertEquals(4, loop.size());
        for (SimPos corner : square(10)) {
            assertTrue(loop.contains(corner), corner + " should be on the loop");
        }
    }

    @Test
    void aPointInsideIsNotOnTheLoop() {
        List<SimPos> points = new ArrayList<>(square(10));
        points.add(at(0, 0));

        assertEquals(4, Hull.convex(points).size(), "the middle of a square is not a corner");
    }

    @Test
    void aPointOnAStraightRunIsNotACorner() {
        // A wall gains nothing from a vertex halfway along a straight side, and
        // ringPositions walks the segment either way.
        List<SimPos> points = new ArrayList<>(square(10));
        points.add(at(0, -10));

        assertEquals(4, Hull.convex(points).size());
    }

    @Test
    void everythingEndsUpInside() {
        List<SimPos> points = List.of(at(-30, -12), at(-4, -28), at(19, -20),
                at(26, 6), at(3, 27), at(-22, 17), at(0, 0), at(8, -6));

        List<SimPos> loop = Hull.convex(points);

        for (SimPos point : points) {
            assertTrue(Hull.contains(loop, point), point + " must be inside the wall");
        }
    }

    @Test
    void aConcaveLoopStillHoldsEverything() {
        // The whole point of digging in is to follow the buildings more closely
        // without ever leaving one out in the cold.
        List<SimPos> points = List.of(at(-40, -40), at(40, -40), at(40, 40), at(-40, 40),
                at(0, -38), at(0, 38), at(-38, 0), at(38, 0), at(12, 9), at(-15, 22));

        List<SimPos> loop = Hull.concave(points, 20);

        for (SimPos point : points) {
            assertTrue(Hull.contains(loop, point), point + " must be inside the wall");
        }
    }

    @Test
    void aTightLoopFollowsThePointsMoreCloselyThanTheConvexOne() {
        // An outlying farm should not drag the whole line out around it and
        // leave the town fortifying a large empty field.
        List<SimPos> points = new ArrayList<>();
        for (int x = -20; x <= 20; x += 10) {
            points.add(at(x, -20));
            points.add(at(x, 20));
        }
        points.add(at(0, 60));   // the outlier

        List<SimPos> loose = Hull.convex(points);
        List<SimPos> tight = Hull.concave(points, 12);

        assertTrue(tight.size() >= loose.size(),
                "digging in adds vertices; it never takes the shape further out");
        for (SimPos point : points) {
            assertTrue(Hull.contains(tight, point), point + " must still be inside");
        }
    }

    @Test
    void aLoopWithNoRoomToDigInIsLeftAlone() {
        List<SimPos> loop = Hull.concave(square(10), 1000);

        assertEquals(4, loop.size(), "every edge is already short enough");
    }

    @Test
    void tooFewPointsToWrapIsNotACrash() {
        assertEquals(0, Hull.convex(List.of()).size());
        assertEquals(1, Hull.convex(List.of(at(0, 0))).size());
        assertEquals(2, Hull.convex(List.of(at(0, 0), at(5, 5))).size());
        assertFalse(Hull.contains(List.of(at(0, 0), at(5, 5)), at(1, 1)),
                "two points enclose nothing");
    }

    @Test
    void twoBuildingsOnTheSameSpotAreOneCorner() {
        List<SimPos> points = new ArrayList<>(square(10));
        points.addAll(square(10));

        assertEquals(4, Hull.convex(points).size());
    }

    @Test
    void aStartingLegDrawnAcrossAPlotIsRepaired() {
        // The hull begins at the convex hull of the points it is given, and the
        // dig-in only ever looked at legs longer than maxEdge -- so a plot lying
        // under a short starting leg was crossed with nothing in the whole of the
        // staking able to correct it. Every other keepout rule refuses a MOVE
        // across a plot; none of them repairs a crossing that was there first.
        //
        // The plot here is the ordinary case: ground the town has ORDERED, which
        // is a keepout and deliberately not a point the ring has to enclose, so
        // it contributes no corner the line could dig in to. Two thirds of the
        // crossings measured over the grown fixtures were exactly this.
        List<SimPos> points = List.of(at(-40, -40), at(40, -40), at(40, 40), at(-40, 40));
        Hull.Keepout ordered = new Hull.Keepout(40, 0, 6);

        List<SimPos> plain = Hull.concave(points, 1000, List.of());
        assertTrue(Hull.crossesKeepout(at(40, -40), at(40, 40), List.of(ordered)),
                "fixture: the eastern leg runs straight through that plot");
        assertEquals(4, plain.size(), "fixture: nothing else would split that leg");

        List<SimPos> repaired = Hull.concave(points, 1000, List.of(ordered));

        for (int i = 0; i < repaired.size(); i++) {
            SimPos from = repaired.get(i);
            SimPos to = repaired.get((i + 1) % repaired.size());
            assertFalse(Hull.crossesKeepout(from, to, List.of(ordered)),
                    "the line still runs from " + from + " to " + to
                            + " through the plot at " + ordered);
        }
        for (SimPos point : points) {
            assertTrue(Hull.contains(repaired, point),
                    "the repair left " + point + " outside the wall");
        }
        assertTrue(repaired.size() > plain.size(),
                "nothing was added, so nothing was repaired");
    }

    @Test
    void aPlotNowhereNearTheLineChangesNothing() {
        // The other side of the same rule, and what keeps the repair from being a
        // licence to fret: a keepout the line does not touch is not a reason to
        // add a single vertex.
        List<SimPos> points = List.of(at(-40, -40), at(40, -40), at(40, 40), at(-40, 40));

        assertEquals(Hull.concave(points, 1000, List.of()),
                Hull.concave(points, 1000, List.of(new Hull.Keepout(0, 0, 6))),
                "a plot in the middle of town moved the wall");
    }

    @Test
    void aPointOnTheWallCountsAsInside() {
        // Otherwise a building whose corner touches the line reads as excluded
        // and the planner pushes the wall out forever chasing it.
        List<SimPos> loop = Hull.convex(square(10));

        assertTrue(Hull.contains(loop, at(10, 0)), "on the edge is in the town");
        assertTrue(Hull.contains(loop, at(-10, -10)), "and so is a corner");
    }
}
