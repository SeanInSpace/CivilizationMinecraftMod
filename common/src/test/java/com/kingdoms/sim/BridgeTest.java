package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.geom.TerrainSense;
import com.kingdoms.sim.settlement.RoadRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a river is a wall or a price.
 *
 * <p>It was a wall, in three places at once: the search skipped a wet cell, the
 * fine check between two cells refused one, and the layer had nothing to put
 * down over water. None of those was a decision about rivers — each was what you
 * get when the only answer a stage can give is no. What it added up to was that
 * a town founded on two banks was two towns that shared a name, and no road in
 * the mod had ever crossed anything.
 */
class BridgeTest {

    /** A river of a given width running north-south, on otherwise level ground. */
    private static TerrainSense riverAt(int x, int width) {
        return new TerrainSense() {
            @Override
            public int heightAt(int px, int pz) {
                // The bed is well below the banks, as a real channel is. This is
                // the part that made a crossing look like a cliff to a router
                // that priced the ground under the water.
                return wetAt(px, pz) ? 54 : 64;
            }

            @Override
            public boolean wetAt(int px, int pz) {
                return Math.abs(px - x) * 2 < width;
            }
        };
    }

    @Test
    void aRoadCrossesANarrowRiver() {
        List<SimPos> ideal = List.of(new SimPos(-40, 64, 0), new SimPos(40, 64, 0));
        List<SimPos> routed = RoadRouter.route(
                ideal, riverAt(0, 8), RoadRouter.Keepout.NOTHING);
        assertNotNull(routed, "an eight-block river cut the road in half");
        assertTrue(routed.get(0).x() < 0 && routed.get(routed.size() - 1).x() > 0,
                "the route did not actually reach the far bank");
    }

    @Test
    void aRoadRefusesOpenWater() {
        // A bound rather than a budget. Something has to be too wide, or a road
        // would be laid across a sea -- and the deck would be the most visible
        // bug in the mod.
        int tooWide = RoadRouter.LONGEST_BRIDGE * 3;
        List<SimPos> ideal = List.of(
                new SimPos(-tooWide, 64, 0), new SimPos(tooWide, 64, 0));
        assertNull(RoadRouter.route(ideal, riverAt(0, tooWide), RoadRouter.Keepout.NOTHING),
                "a road was laid across " + tooWide + " blocks of open water");
    }

    @Test
    void aRoadWouldRatherStayDry() {
        // Water is dear, so a crossing is what a road does when there is nothing
        // better -- not the first thing it reaches for. A river that stops short
        // of the corridor's edge should be walked around rather than bridged.
        TerrainSense stub = new TerrainSense() {
            @Override
            public int heightAt(int px, int pz) {
                return wetAt(px, pz) ? 54 : 64;
            }

            @Override
            public boolean wetAt(int px, int pz) {
                return Math.abs(px) <= 6 && pz <= 4;
            }
        };
        List<SimPos> ideal = List.of(new SimPos(-40, 64, 0), new SimPos(40, 64, 0));
        List<SimPos> routed = RoadRouter.route(ideal, stub, RoadRouter.Keepout.NOTHING);
        assertNotNull(routed, "the road was refused when there was a dry way round");
        for (SimPos at : routed) {
            assertTrue(!stub.wetAt(at.x(), at.z()) || at.z() > 4,
                    "the road waded through " + at + " with dry ground eight blocks north");
        }
    }

    @Test
    void aCrossingIsJudgedByItsSpanAndNotByItsBed() {
        // The fine check walks the winning line column by column and refuses a
        // step it could not grade. A channel ten blocks deep is exactly such a
        // step, twice, and judging it that way refuses every river on the map --
        // which is very nearly what the old rule did, by another route.
        List<SimPos> across = List.of(new SimPos(-20, 64, 0), new SimPos(20, 64, 0));
        assertTrue(RoadRouter.walkable(across, riverAt(0, 6), 0),
                "a six-block river with a ten-block channel was read as a cliff");

        int tooWide = RoadRouter.LONGEST_BRIDGE + 2;
        assertTrue(!RoadRouter.walkable(across, riverAt(0, tooWide), 0),
                "a " + tooWide + "-block span passed a check that is meant to bound it");
    }
}
