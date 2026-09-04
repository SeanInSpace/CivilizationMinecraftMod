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

    /** A building's reserved ground: a span about an origin, as the town keeps it. */
    private record Plot(int x, int z, int span) { }

    /**
     * The points the planner hands the hull, for a town of these plots.
     *
     * <p>Copied in shape from {@code PerimeterPlanner.plotCorners} rather than
     * invented: every plot's eight boundary points and each of those pushed a
     * margin further out from the middle of town. A fixture that fed the hull
     * bare origins would be testing a hull nothing stakes.
     */
    private static List<SimPos> cornersOf(List<Plot> plots, int margin) {
        List<SimPos> points = new ArrayList<>();
        for (Plot plot : plots) {
            int half = plot.span() / 2;
            for (int sx = -1; sx <= 1; sx++) {
                for (int sz = -1; sz <= 1; sz++) {
                    if (sx == 0 && sz == 0) {
                        continue;
                    }
                    int cx = plot.x() + sx * half;
                    int cz = plot.z() + sz * half;
                    points.add(at(cx, cz));
                    double away = Math.hypot(cx, cz);
                    if (away < 1) {
                        continue;
                    }
                    points.add(at(cx + (int) Math.round(margin * cx / away),
                            cz + (int) Math.round(margin * cz / away)));
                }
            }
        }
        return points;
    }

    /** Whether two plots foul one another, as {@code Layout.farEnoughApart} has it. */
    private static boolean overlap(Plot a, Plot b) {
        double apart = a.span() / 2.0 + b.span() / 2.0 + 2;
        return Math.abs(a.x() - b.x()) < apart && Math.abs(a.z() - b.z()) < apart;
    }

    private static List<Hull.Keepout> keepoutsOf(List<Plot> plots) {
        List<Hull.Keepout> squares = new ArrayList<>();
        for (Plot plot : plots) {
            squares.add(new Hull.Keepout(plot.x(), plot.z(), plot.span() / 2.0));
        }
        return squares;
    }

    /** The first stretch of loop drawn across somebody's ground, or nothing. */
    private static String throughAPlot(List<SimPos> loop, List<Plot> plots) {
        List<Hull.Keepout> squares = keepoutsOf(plots);
        for (int i = 0; i < loop.size(); i++) {
            SimPos from = loop.get(i);
            SimPos to = loop.get((i + 1) % loop.size());
            for (int p = 0; p < plots.size(); p++) {
                if (Hull.crossesKeepout(from, to, List.of(squares.get(p)))) {
                    Plot plot = plots.get(p);
                    return "stretch " + i + " (" + from + "->" + to
                            + ") is drawn through the " + plot.span() + "-wide plot at "
                            + plot.x() + "," + plot.z();
                }
            }
        }
        return null;
    }

    /**
     * A town whose plots are the sizes the catalogue reserves, arranged so the
     * near-edge ones are what a long stretch would cut across: a ring of
     * outliers with a dense middle, which is the arrangement that put a
     * measured town's wall through ten of its sixteen buildings.
     */
    private static List<Plot> aTownOfPlots() {
        List<Plot> plots = new ArrayList<>();
        int[] spans = {11, 13, 7, 15, 23};
        int n = 0;
        // Thirty apart, because no town ever offers two plots that overlap --
        // Layout.farEnoughApart refuses them -- and a fixture that did would be
        // asking the wall to reach a corner standing inside somebody else's
        // floor, which is unanswerable rather than merely hard.
        for (int x = -60; x <= 60; x += 30) {
            for (int z = -60; z <= 60; z += 30) {
                plots.add(new Plot(x, z, spans[n++ % spans.length]));
            }
        }
        plots.add(new Plot(-130, 10, 13));
        plots.add(new Plot(140, -20, 11));
        plots.add(new Plot(20, 135, 15));
        plots.add(new Plot(-30, -140, 23));
        return plots;
    }

    @Test
    void noStretchOfWallIsDrawnThroughABuildingsPlot() {
        // The third rule of the wall. "Nothing may cross" is the loop against
        // itself and "nothing may end up outside" is about the points; a
        // building is neither, and its corners sit happily inside a line that
        // runs across its floor. That fence in the kitchen only ever read as a
        // closed wall because a building's own wall stops people walking
        // through it -- which is what `shutByBuilding` was quietly forgiving.
        List<Plot> plots = aTownOfPlots();

        List<SimPos> loop = Hull.concave(
                cornersOf(plots, 4), 24, keepoutsOf(plots));

        assertTrue(loop.size() >= 3, "a town of twenty-eight plots has a hull");
        String fault = throughAPlot(loop, plots);
        assertTrue(fault == null, "the palisade is staked through a house: " + fault);
        assertTrue(selfIntersection(loop) == null,
                "and the keepouts must not have bought that at the price of a knot");
    }

    @Test
    void randomTownsNeverGetAWallThroughAHouse() {
        // Sixty arrangements rather than one, for the reason the knot test
        // gives: this is a property of the hull on any town with an interior,
        // and a single lucky fixture would hide the cases it is not.
        Random random = new Random(20260904L);
        for (int trial = 0; trial < 60; trial++) {
            List<Plot> plots = new ArrayList<>();
            int[] spans = {7, 11, 13, 15, 23};
            for (int i = 0; i < 200 && plots.size() < 24; i++) {
                Plot candidate = new Plot(random.nextInt(300) - 150,
                        random.nextInt(300) - 150, spans[random.nextInt(spans.length)]);
                if (plots.stream().noneMatch(other -> overlap(candidate, other))) {
                    plots.add(candidate);
                }
            }
            List<SimPos> loop = Hull.concave(
                    cornersOf(plots, 4), 20 + random.nextInt(30), keepoutsOf(plots));
            String fault = throughAPlot(loop, plots);
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
