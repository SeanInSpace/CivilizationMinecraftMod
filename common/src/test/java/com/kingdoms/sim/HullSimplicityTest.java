package com.kingdoms.sim;

import com.kingdoms.sim.geom.Hull;
import com.kingdoms.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wall must be a loop, not a knot.
 *
 * <p>The concave hull is what a town's palisade is staked from, and it was
 * allowed to cross itself. Its only guard was a length ratio — the detour by
 * way of a new point had to be under twice the edge it replaced — with a
 * comment claiming that stopped the loop folding back through itself. It does
 * not. A point in the middle of town satisfies it easily, so the line dug in to
 * reach that point and crossed its own far side getting there.
 *
 * <p>What that looked like in play: 68 vertices and 2758 posts around a 289x285
 * town, drawn as nested boxes, corridors ending in nothing, and two full-width
 * walls straight through the centre. Every post of it was placed correctly. The
 * shape was the fault, and a shape has no count that reveals it — which is why
 * the test is the property itself.
 */
class HullSimplicityTest {

    /** Whether any two non-adjacent edges of the loop cross. */
    private static String selfIntersection(List<SimPos> loop) {
        int n = loop.size();
        for (int i = 0; i < n; i++) {
            SimPos a = loop.get(i);
            SimPos b = loop.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                if (j == i || (j + 1) % n == i || (i + 1) % n == j) {
                    continue;   // shares a corner by construction
                }
                SimPos c = loop.get(j);
                SimPos d = loop.get((j + 1) % n);
                if (segmentsCross(a, b, c, d)) {
                    return "edge " + i + " (" + a + "->" + b + ") crosses edge "
                            + j + " (" + c + "->" + d + ")";
                }
            }
        }
        return null;
    }

    private static boolean segmentsCross(SimPos a, SimPos b, SimPos c, SimPos d) {
        long d1 = turn(c, d, a);
        long d2 = turn(c, d, b);
        long d3 = turn(a, b, c);
        long d4 = turn(a, b, d);
        if (d1 != 0 && d2 != 0 && d3 != 0 && d4 != 0) {
            return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0));
        }
        return false;   // touching is judged elsewhere; this test hunts crossings
    }

    private static long turn(SimPos a, SimPos b, SimPos c) {
        return (long) (b.x() - a.x()) * (c.z() - a.z())
                - (long) (b.z() - a.z()) * (c.x() - a.x());
    }

    private static SimPos at(int x, int z) {
        return new SimPos(x, 64, z);
    }

    @Test
    void aScatterOfPlotsIsWrappedInALoopThatDoesNotCrossItself() {
        // A town: a dense middle with outlying farms, which is the arrangement
        // that produced the knot -- the middle is exactly what the line dug in
        // to reach.
        List<SimPos> plots = new ArrayList<>();
        for (int x = -40; x <= 40; x += 10) {
            for (int z = -40; z <= 40; z += 10) {
                plots.add(at(x, z));
            }
        }
        plots.add(at(-140, 0));
        plots.add(at(150, 20));
        plots.add(at(10, -130));
        plots.add(at(-30, 145));

        List<SimPos> loop = Hull.concave(plots, 24);

        assertTrue(loop.size() >= 3, "a town of eighty-five plots has a hull");
        String fault = selfIntersection(loop);
        assertTrue(fault == null,
                "the palisade crosses itself: " + fault + " (" + loop.size()
                        + " vertices)");
    }

    @Test
    void randomScattersNeverProduceAKnot() {
        // Deliberately many shapes rather than one. The knot was not a freak
        // arrangement -- it is what this hull did to any point set with an
        // interior, and one lucky fixture would have hidden that.
        Random random = new Random(20260827L);
        for (int trial = 0; trial < 60; trial++) {
            List<SimPos> plots = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                plots.add(at(random.nextInt(300) - 150, random.nextInt(300) - 150));
            }
            List<SimPos> loop = Hull.concave(plots, 20 + random.nextInt(40));
            String fault = selfIntersection(loop);
            assertTrue(fault == null, "trial " + trial + ": " + fault);
        }
    }

    @Test
    void theLoopStillFollowsTheTownRatherThanBoxingIt() {
        // The guard must not have simply turned the concave hull back into the
        // convex one. A wall that boxes a town is the fault the concave hull
        // was introduced to fix, and trading one for the other is not a repair.
        List<SimPos> plots = new ArrayList<>();
        for (int x = -60; x <= 60; x += 12) {
            plots.add(at(x, -60));
            plots.add(at(x, 60));
        }
        plots.add(at(0, 0));
        plots.add(at(-200, 0));   // one far outlier the line should not sweep round

        List<SimPos> convex = Hull.convex(plots);
        List<SimPos> concave = Hull.concave(plots, 20);

        assertTrue(concave.size() > convex.size(),
                "the concave loop dug in nowhere at all: " + concave.size()
                        + " vertices against the convex hull's " + convex.size());
    }
}
