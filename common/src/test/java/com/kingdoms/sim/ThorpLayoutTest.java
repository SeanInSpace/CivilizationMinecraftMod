package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.ThorpLayout;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.culture.Wander;
import com.kingdoms.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What makes a thorp a thorp, rather than a comb somebody has flattened.
 *
 * <p>The shared rules — determinism, no plot in the road, frontage, separation —
 * are checked for every arrangement in {@code LayoutTest} and are not repeated
 * here. What is here is the shape itself, because the shape is the only thing
 * this class exists to produce and it is the only thing no shared rule protects.
 * The warren has the same test for the same reason and it is worth reading:
 * pulling its knots together until every plot cleared the overlap box left huts
 * in neighboring knots closer than huts in the same one, which broke nothing
 * and deleted the layout.
 */
class ThorpLayoutTest {

    private static final SimPos CENTRE = new SimPos(0, 64, 0);

    /** A layout of its own, so no other test's cached plan answers for it. */
    private static ThorpLayout thorp() {
        return new ThorpLayout();
    }

    @Test
    void theThorpIsRegisteredUnderTheNameACultureWouldAskFor() {
        assertSame(Layouts.THORP, Layouts.of(Culture.LAYOUT_THORP));
        assertEquals(Culture.LAYOUT_THORP, Layouts.THORP.id());
        assertTrue(Layouts.isStreetsFirst(Layouts.THORP),
                "a thorp draws its track before its farms");
    }

    @Test
    void itsTrackBendsGentlyEnoughToBuildOn() {
        // The bar the wander's own javadoc records: past a slope of a quarter the
        // frontage along a street starts fouling itself and drops to the
        // outskirts, and the town keeps its shape while losing its houses.
        assertTrue(thorp().wander().slope() <= Wander.SAFE_SLOPE + 1e-9,
                "the track bends at " + thorp().wander().slope()
                        + ", past the " + Wander.SAFE_SLOPE + " its frontage survives");
    }

    @Test
    void aYardIsAYardAndNotJustMoreLane() {
        // The thing this arrangement exists to be. A lane that merely stops is a
        // dead end; a lane that ends in a yard is a place, and the difference
        // from above is whether the buildings round one yard sit closer to each
        // other than to the buildings round the next.
        //
        // This is not idle. The first draft set the lanes twenty-six apart, which
        // put the flanking rows of two yards on the same side of a track twelve
        // blocks apart -- legal, every rule kept, and the blobs had run together
        // into a continuous hedge along the track. The lanes are thirty-four
        // apart now and the gap is twenty-eight against the fourteen inside a
        // yard.
        TownPlan plan = thorp().planFor(CENTRE, 140);
        Map<Integer, List<SimPos>> yards = yardsOf(plan);
        assertTrue(yards.size() >= 8,
                "only " + yards.size() + " yards in a hamlet of 140");

        double within = Double.MAX_VALUE;
        double between = Double.MAX_VALUE;
        List<Integer> keys = new ArrayList<>(yards.keySet());
        for (int a = 0; a < keys.size(); a++) {
            List<SimPos> one = yards.get(keys.get(a));
            for (int i = 0; i < one.size(); i++) {
                for (int j = i + 1; j < one.size(); j++) {
                    within = Math.min(within, one.get(i).horizontalDistance(one.get(j)));
                }
            }
            for (int b = a + 1; b < keys.size(); b++) {
                for (SimPos here : one) {
                    for (SimPos there : yards.get(keys.get(b))) {
                        between = Math.min(between, here.horizontalDistance(there));
                    }
                }
            }
        }
        System.out.println("thorp yards: " + yards.size() + " of them, "
                + Math.round(within) + " blocks between neighbors in a yard and "
                + Math.round(between) + " between yards");
        assertTrue(between > within * 1.5,
                "buildings in different yards are " + Math.round(between)
                        + " apart and buildings in the same yard " + Math.round(within)
                        + " — the yards have dissolved into a scatter");
    }

    @Test
    void eachLaneWidensAtItsHeadInsteadOfRunningStraightOut() {
        // The other half of what a yard is: it is a WIDENING. A lane whose
        // frontage stands the same distance off it all the way along is a street,
        // and this arrangement already has one of those running through it.
        //
        // Measured on the complete yards of the whole plan rather than on a
        // hamlet's worth, because a town of a hundred and forty stops partway
        // through its outermost lanes and a half-built yard has no head yet.
        TownPlan plan = thorp().fullPlan(CENTRE);
        int checked = 0;
        for (Map.Entry<Integer, List<SimPos>> yard : yardsOf(plan).entrySet()) {
            TownPlan.Street lane = plan.streets().get(yard.getKey());
            if (yard.getValue().size() < 7) {
                continue;   // a yard the plan stopped partway through
            }
            checked++;
            double nearWidest = 0;
            double farWidest = 0;
            double run = Math.abs(lane.to().x() - lane.from().x());
            for (SimPos plot : yard.getValue()) {
                double along = Math.abs(plot.x() - lane.from().x());
                double across = Math.abs(plot.z() - lane.from().z());
                if (along < run / 2) {
                    nearWidest = Math.max(nearWidest, across);
                } else {
                    farWidest = Math.max(farWidest, across);
                }
            }
            assertTrue(farWidest > nearWidest,
                    "the lane at " + lane.from() + " is " + Math.round(farWidest)
                            + " wide at its head and " + Math.round(nearWidest)
                            + " at its mouth — it does not open into anything");
        }
        assertTrue(checked >= 8, "only " + checked + " complete yards to judge");
    }

    @Test
    void theTeethOfTheCombAlternateSides() {
        // A comb whose teeth all point one way is not a comb, it is a rake -- and
        // a rake grows lopsided, because every yard it opens is on the same side
        // of the route and the track ends up along one edge of its own hamlet.
        TownPlan plan = thorp().fullPlan(CENTRE);
        Map<Integer, TreeMap<Integer, Integer>> byTrack = new LinkedHashMap<>();
        for (TownPlan.Street street : plan.streets()) {
            if (street.kind() != TownPlan.Kind.LANE) {
                continue;
            }
            byTrack.computeIfAbsent(trackOf(plan, street), k -> new TreeMap<>())
                    .put(street.from().z(), Integer.signum(
                            street.to().x() - street.from().x()));
        }
        assertTrue(byTrack.size() >= 2, "a hamlet of 256 should have opened a second track");
        for (Map.Entry<Integer, TreeMap<Integer, Integer>> track : byTrack.entrySet()) {
            Integer last = null;
            for (Map.Entry<Integer, Integer> lane : track.getValue().entrySet()) {
                if (last != null) {
                    assertTrue(last + lane.getValue() == 0,
                            "two lanes in a row leave the track at index " + track.getKey()
                                    + " on the same side, at z=" + lane.getKey());
                }
                last = lane.getValue();
            }
        }
    }

    /**
     * Which track a lane leaves, found rather than assumed.
     *
     * <p>By the nearest one, so the test does not carry its own copy of how far
     * apart the tracks run. A test that repeats a constant is a test that agrees
     * with itself after somebody changes it.
     */
    private static int trackOf(TownPlan plan, TownPlan.Street lane) {
        int nearest = -1;
        int gap = Integer.MAX_VALUE;
        for (int i = 0; i < plan.streets().size(); i++) {
            TownPlan.Street street = plan.streets().get(i);
            if (street.kind() != TownPlan.Kind.SPINE) {
                continue;
            }
            int away = Math.abs(street.from().x() - lane.from().x());
            if (away < gap) {
                gap = away;
                nearest = i;
            }
        }
        return nearest;
    }

    @Test
    void theTrackRunsThroughRatherThanStoppingAtTheLastFarm() {
        // A route that begins at the first yard and gives up after the last is a
        // driveway. The track carries on past both ends, which is what makes the
        // hamlet a place on the way to somewhere.
        TownPlan plan = thorp().fullPlan(CENTRE);
        for (int i = 0; i < plan.streets().size(); i++) {
            TownPlan.Street street = plan.streets().get(i);
            if (street.kind() != TownPlan.Kind.SPINE) {
                continue;
            }
            int outermost = 0;
            for (TownPlan.Street lane : plan.streets()) {
                if (lane.kind() == TownPlan.Kind.LANE && trackOf(plan, lane) == i) {
                    outermost = Math.max(outermost, Math.abs(lane.from().z() - CENTRE.z()));
                }
            }
            assertTrue(Math.abs(street.from().z() - CENTRE.z()) > outermost,
                    "the track stops at its outermost lane rather than running on");
            assertTrue(Math.abs(street.to().z() - CENTRE.z()) > outermost,
                    "the track stops at its outermost lane rather than running on");
        }
    }

    @Test
    void aThorpReadsAsACombFromTheAir() {
        // Drawn rather than asserted at, because the failure this catches is one
        // no number describes: a plan can keep every rule in the file and still
        // come out as a slab, and the only way anybody has ever noticed that in
        // this project is by looking at it. The map goes in the build log so the
        // looking does not cost a five-minute round trip into the game.
        //
        // The assertions beside it are the bars, not the point. Frontage is the
        // number every collapse this codebase has had showed up in first, and the
        // reach is what separates a hamlet from a set of farms that share a name:
        // LayoutFitnessTest fails a town that sprawls past 340, and a layout built
        // round a through route is exactly the kind that would.
        for (int wanted : new int[] {64, 140}) {
            TownPlan plan = thorp().planFor(CENTRE, wanted);
            int reach = 0;
            for (TownPlan.Plot plot : plan.plots()) {
                reach = Math.max(reach, (int) Math.round(Math.hypot(
                        plot.at().x() - CENTRE.x(), plot.at().z() - CENTRE.z())));
            }
            System.out.println();
            System.out.println("thorp at " + wanted + " plots: "
                    + plan.size() + " plots, " + plan.streets().size() + " streets, "
                    + plan.frontagePercent() + "% fronting, furthest " + reach + " blocks");
            System.out.println(map(plan));

            assertEquals(wanted, plan.size(), "the plan came up short");
            assertTrue(plan.frontagePercent() >= 95,
                    "only " + plan.frontagePercent() + "% of a thorp of " + wanted
                            + " fronts one of its own roads");
            assertTrue(reach <= 250,
                    "a thorp of " + wanted + " spread to " + reach
                            + " blocks, which is a set of farms, not a hamlet");
            assertTrue(reach >= 60,
                    "a thorp of " + wanted + " reached only " + reach + " blocks");
        }
    }

    /**
     * The plots round each yard, keyed by the lane they front.
     *
     * <p>A lane is one street with one index, so the plots that name it are
     * exactly the ones round its yard — which is the whole reason a plot records
     * its street by index rather than by whichever road happens to be nearest.
     */
    private static Map<Integer, List<SimPos>> yardsOf(TownPlan plan) {
        Map<Integer, List<SimPos>> yards = new LinkedHashMap<>();
        for (TownPlan.Plot plot : plan.plots()) {
            if (!plot.frontsAStreet()) {
                continue;
            }
            if (plan.streets().get(plot.street()).kind() != TownPlan.Kind.LANE) {
                continue;   // the cottages along the track front no yard
            }
            yards.computeIfAbsent(plot.street(), k -> new ArrayList<>()).add(plot.at());
        }
        yards.values().removeIf(where -> where.size() < 2);
        return yards;
    }

    /** How many blocks one column of the map covers. */
    private static final int ACROSS = 4;

    /**
     * How many blocks one row covers.
     *
     * <p>Twice the column, because a character cell is about twice as tall as it
     * is wide. A map drawn at one scale in both directions is a map of a town
     * stretched to twice its height, which is a poor thing to judge a shape by.
     */
    private static final int DOWN = 8;

    /** The plan drawn: {@code #} a building, {@code .} a road, {@code +} the middle. */
    private static String map(TownPlan plan) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TownPlan.Plot plot : plan.plots()) {
            minX = Math.min(minX, plot.at().x());
            maxX = Math.max(maxX, plot.at().x());
            minZ = Math.min(minZ, plot.at().z());
            maxZ = Math.max(maxZ, plot.at().z());
        }
        minX -= ACROSS * 2;
        maxX += ACROSS * 2;
        minZ -= DOWN;
        maxZ += DOWN;
        int wide = (maxX - minX) / ACROSS + 1;
        int tall = (maxZ - minZ) / DOWN + 1;
        char[][] grid = new char[tall][wide];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }

        // Roads first, so a building drawn on top of one wins the cell -- what a
        // reader needs to see is where the houses are, and a road is context.
        for (TownPlan.Street street : plan.streets()) {
            List<SimPos> path = street.path();
            for (int i = 1; i < path.size(); i++) {
                SimPos a = path.get(i - 1);
                SimPos b = path.get(i);
                int steps = Math.max(1, Math.max(Math.abs(b.x() - a.x()),
                        Math.abs(b.z() - a.z())));
                for (int s = 0; s <= steps; s++) {
                    int x = a.x() + (b.x() - a.x()) * s / steps;
                    int z = a.z() + (b.z() - a.z()) * s / steps;
                    put(grid, minX, minZ, x, z, '.');
                }
            }
        }
        put(grid, minX, minZ, plan.centre().x(), plan.centre().z(), '+');
        for (TownPlan.Plot plot : plan.plots()) {
            put(grid, minX, minZ, plot.at().x(), plot.at().z(), '#');
        }

        StringBuilder drawn = new StringBuilder();
        for (char[] row : grid) {
            drawn.append(new String(row).stripTrailing()).append('\n');
        }
        return drawn.toString();
    }

    private static void put(char[][] grid, int minX, int minZ, int x, int z, char mark) {
        int col = (x - minX) / ACROSS;
        int row = (z - minZ) / DOWN;
        if (row >= 0 && row < grid.length && col >= 0 && col < grid[row].length) {
            grid[row][col] = mark;
        }
    }

    @Test
    void aThorpIsNotAHighStreetWithExtraSteps() {
        // The claim the arrangement makes. Five arrangements that come out the
        // same shape would make culture decoration, and the two that hang lanes
        // off a spine are the pair most at risk of it.
        Layout thorp = thorp();
        int shared = 0;
        for (int i = 0; i < 60; i++) {
            SimPos here = thorp.plotFor(CENTRE, i);
            for (int j = 0; j < 60; j++) {
                if (here.equals(Layouts.HIGH_STREET.plotFor(CENTRE, j))) {
                    shared++;
                    break;
                }
            }
        }
        assertTrue(shared < 6, "a thorp and a high street shared " + shared
                + " of their first sixty plots");
    }
}
