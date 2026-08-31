package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.geom.TerrainSense;
import com.kingdoms.sim.settlement.RoadRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The router, on ground drawn by hand so the answer is knowable.
 *
 * <p>Fast, and deliberately not a grown town. A town on recorded terrain says
 * whether the whole pipeline works; these say whether the router does what it
 * claims when the hill is exactly here and the gap is exactly there — which is
 * the only way to tell a router that bends from one that happens to be lucky.
 */
class RoadRouterTest {

    /** A wall across the world at a given x, with a gap in it. */
    private static TerrainSense ridgeAt(int x, int gapFrom, int gapTo) {
        return new TerrainSense() {
            @Override
            public int heightAt(int px, int pz) {
                boolean throughGap = pz >= gapFrom && pz <= gapTo;
                return Math.abs(px - x) <= 2 && !throughGap ? 40 : 0;
            }

            @Override
            public boolean wetAt(int px, int pz) {
                return false;
            }
        };
    }

    private static final TerrainSense LEVEL = TerrainSense.FLAT;

    @Test
    void aStreetOnLevelGroundKeepsItsLine() {
        List<SimPos> ideal = List.of(new SimPos(0, 64, 0), new SimPos(64, 64, 0));
        List<SimPos> routed = RoadRouter.route(ideal, LEVEL, RoadRouter.Keepout.NOTHING);
        assertNotNull(routed, "a level run was refused");
        assertEquals(ideal.get(0), routed.get(0));
        assertEquals(ideal.get(ideal.size() - 1), routed.get(routed.size() - 1));
        for (SimPos at : routed) {
            assertTrue(Math.abs(at.z()) <= 1,
                    "wandered to " + at + " with no reason to leave the line");
        }
    }

    @Test
    void aStreetBendsThroughTheGapInARidge() {
        // The whole point. The drawn line runs straight into a wall; there is a
        // way round eight blocks north; a router that can only refuse gives up
        // here and leaves the far end of the street unbuilt.
        TerrainSense ridge = ridgeAt(32, 8, 16);
        List<SimPos> ideal = List.of(new SimPos(0, 64, 0), new SimPos(64, 64, 0));
        List<SimPos> routed = RoadRouter.route(ideal, ridge, RoadRouter.Keepout.NOTHING);
        assertNotNull(routed, "the router refused a street that had a way through");
        boolean wentForTheGap = routed.stream()
                .anyMatch(at -> Math.abs(at.x() - 32) <= 4 && at.z() >= 6);
        assertTrue(wentForTheGap,
                "did not route through the gap; went " + routed);
        assertTrue(RoadRouter.walkable(routed, ridge, 0),
                "routed a line it cannot walk: " + routed);
    }

    @Test
    void aStreetWithNoWayThroughIsRefused() {
        // Refusal still has to exist. A corridor walled end to end is a street
        // the town should not have, and saying so is better than a stair.
        TerrainSense wall = new TerrainSense() {
            @Override
            public int heightAt(int x, int z) {
                return Math.abs(x - 32) <= 2 ? 40 : 0;
            }

            @Override
            public boolean wetAt(int x, int z) {
                return false;
            }
        };
        List<SimPos> routed = RoadRouter.route(
                List.of(new SimPos(0, 64, 0), new SimPos(64, 64, 0)),
                wall, RoadRouter.Keepout.NOTHING);
        assertNull(routed, "routed a line straight over a cliff");
    }

    @Test
    void aStreetWillNotCrossGroundSomebodyHolds() {
        // Plots are not obstacles to be priced, they are ground the road may not
        // have. The corridor is narrower than the plan's setback precisely so a
        // road can bend without ever needing to.
        RoadRouter.Keepout plot = (x, z) -> Math.abs(x - 32) <= 6 && Math.abs(z) <= 6;
        List<SimPos> routed = RoadRouter.route(
                List.of(new SimPos(0, 64, 0), new SimPos(64, 64, 0)),
                LEVEL, plot);
        assertNotNull(routed, "refused a street that could simply go round");
        for (int i = 1; i < routed.size(); i++) {
            SimPos a = routed.get(i - 1);
            SimPos b = routed.get(i);
            int steps = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
            for (int s = 0; s <= steps; s++) {
                int x = a.x() + Math.round((float) (b.x() - a.x()) * s / Math.max(1, steps));
                int z = a.z() + Math.round((float) (b.z() - a.z()) * s / Math.max(1, steps));
                assertFalse(plot.blocked(x, z),
                        "routed through held ground at " + x + "," + z);
            }
        }
    }

    @Test
    void theSameStreetRoutesTheSameWayTwice() {
        // The answer is persisted, so a router that disagreed with itself would
        // have a town lay the same street twice down two different lines.
        TerrainSense ridge = ridgeAt(32, 8, 16);
        List<SimPos> ideal = List.of(new SimPos(0, 64, 0), new SimPos(64, 64, 0));
        assertEquals(RoadRouter.route(ideal, ridge, RoadRouter.Keepout.NOTHING),
                RoadRouter.route(ideal, ridge, RoadRouter.Keepout.NOTHING));
    }

    @Test
    void aStreetWillNotWanderOutOfItsCorridor() {
        // A road that strays past the setback is a road in somebody's garden.
        TerrainSense ridge = ridgeAt(32, 40, 48);   // the gap is far off the line
        List<SimPos> ideal = List.of(new SimPos(0, 64, 0), new SimPos(64, 64, 0));
        List<SimPos> routed = RoadRouter.route(ideal, ridge, RoadRouter.Keepout.NOTHING);
        if (routed != null) {
            for (SimPos at : routed) {
                assertTrue(Math.abs(at.z()) <= RoadRouter.CORRIDOR_HALF,
                        "strayed to " + at + ", past the corridor it is allowed");
            }
        }
    }

    @Test
    void aRoutedLineIsCheckedColumnByColumn() {
        // The lattice is four blocks wide and a cliff is one. Four courses
        // between two cells might be four honest steps or one wall, and only a
        // column-by-column walk can tell.
        TerrainSense stair = new TerrainSense() {
            @Override
            public int heightAt(int x, int z) {
                return x / 4;   // one course every four blocks: perfectly walkable
            }

            @Override
            public boolean wetAt(int x, int z) {
                return false;
            }
        };
        TerrainSense cliff = new TerrainSense() {
            @Override
            public int heightAt(int x, int z) {
                return x < 32 ? 0 : 4;   // the same four courses, all at once
            }

            @Override
            public boolean wetAt(int x, int z) {
                return false;
            }
        };
        List<SimPos> line = List.of(new SimPos(0, 64, 0), new SimPos(64, 64, 0));
        assertTrue(RoadRouter.walkable(line, stair, 0), "refused a gentle climb");
        assertFalse(RoadRouter.walkable(line, cliff, 0), "accepted a four-block wall");
    }
}
