package com.civilization.sim;

import com.civilization.sim.culture.Layout;
import com.civilization.sim.culture.Layouts;
import com.civilization.sim.culture.TownPlan;
import com.civilization.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far the first twenty buildings of a town stand from its heart.
 *
 * <p>Every other measure in the suite is taken on a town of a hundred and forty
 * or on the whole plan of two hundred and fifty-six, and every one of them was
 * passing while a settlement somebody flew out to look at read as fifteen
 * buildings strung round a three-hundred-block ring with nothing but grass
 * inside it. A plan can be dense, fully fronted and inside its sprawl bar at a
 * hundred and forty plots and still have nothing at all near the middle at
 * twenty, because the twenty are a <em>prefix</em> of a plan drawn for the
 * larger town — and nobody was measuring the prefix.
 *
 * <p>So: the prefix is measured here, for every arrangement, and the numbers
 * are printed rather than only asserted. The bars below are per-arrangement and
 * deliberately loose; what they are for is that a change which empties the
 * middle of a town again fails a test instead of being noticed from the air in
 * six months.
 *
 * <h2>Why "twenty within forty blocks" is not the bar</h2>
 *
 * <p>It was the goal, and it is not reachable, and the arithmetic that says so
 * is worth writing down because it will come up again. A plot stands
 * {@code SETBACK} — thirteen — off the street it fronts, and the plan refuses
 * any plot whose square comes within a half-carriageway of <em>any</em>
 * carriageway, which is {@code ROAD_HALF + onACurve(DEFAULT_SPAN / 2 + CURB)},
 * thirteen again. So a road forbids a band twenty-six blocks wide centered on
 * itself, and carries one rank of frontage along each edge of it.
 *
 * <p>A disc of forty blocks therefore has room for about two ranks of frontage
 * across it in any one direction, and a rank is the separation of eleven apart.
 * The best any streets-first arrangement here manages inside forty blocks is
 * sixteen plots, and it is the stronghold's grid that manages it. A round town
 * does worse again, because its spokes are roads too and each one eats
 * twenty-six blocks of arc out of every ring it crosses.
 *
 * <p>What is actually achievable, and what the arrangements are held to here, is
 * that a young town fills the frontage nearest its middle <em>before</em> it
 * opens anything further out. {@code PlannedLayout.take} sorts every offer by
 * distance from the center, so that ordering is already guaranteed by
 * construction — which is exactly why the ring town's empty middle was so hard
 * to see. The ordering was right the whole time. There was nothing near the
 * middle to order.
 */
class LayoutHuddleTest {

    private static final SimPos CENTER = new SimPos(0, 64, 0);

    /** As many buildings as a town has when somebody first flies out to look. */
    private static final int YOUNG = 20;

    /**
     * How far a young town of any arrangement may reach.
     *
     * <p>A ceiling rather than a target. The warren is the worst at eighty-eight
     * and is meant to be — knots of huts with open ground between them is what it
     * is — so this is set clear of it and catches an arrangement that has started
     * marching outward rather than filling in.
     */
    private static final int YOUNG_REACH_LIMIT = 95;

    @Test
    void everyArrangementIsMeasuredAsAYoungTown() {
        StringBuilder table = new StringBuilder(String.format(
                "%n== the first %d plots, as a young town stands ==%n", YOUNG));
        List<String> tooFar = new ArrayList<>();
        for (Layout layout : Layouts.all()) {
            TownPlan plan = layout.planFor(CENTER, YOUNG);
            List<Double> reach = new ArrayList<>();
            for (TownPlan.Plot plot : plan.plots()) {
                reach.add(CENTER.horizontalDistance(plot.at()));
            }
            Collections.sort(reach);
            long max = Math.round(reach.get(reach.size() - 1));
            long median = Math.round(reach.get(reach.size() / 2));
            int near = 0;
            for (double one : reach) {
                if (one <= 40) {
                    near++;
                }
            }
            table.append(String.format(
                    "%-20s max %3d  median %3d  within 40 blocks %2d  fronting %3d%%%n",
                    layout.id(), max, median, near, plan.frontagePercent()));
            if (max > YOUNG_REACH_LIMIT) {
                tooFar.add(layout.id() + " reached " + max);
            }
        }
        System.out.println(table);
        assertTrue(tooFar.isEmpty(),
                "a town of " + YOUNG + " buildings should still be a huddle: " + tooFar);
    }

    /**
     * The ground inside a ring town's first ring road is built on.
     *
     * <p>The one thing this file exists to stop coming back, and the counts it
     * holds each arrangement to are low because the geometry is mean: the ring
     * road's own keepout forbids everything from twenty-seven blocks out to
     * fifty-three, the six spokes forbid thirteen blocks either side of
     * themselves all the way in, and what is left of the ground inside the first
     * ring is six thin slivers between the spokes. Six plots is the whole
     * capacity of a ring town's green, and {@code ring_streets} gets fewer again
     * because its ring wanders nine blocks and spends much of its circuit bent
     * inward where the slivers close up.
     *
     * <p><strong>It was nought.</strong> Not "a few" — none, in every ring town
     * ever laid, at every size. The inner face of the first ring was offered at
     * an even count worked out from the arc pitch alone, nine offers at forty
     * degrees, against six spokes at sixty; every one of the nine landed within
     * twenty degrees of a spoke, and twenty degrees at a radius of
     * twenty-seven is nine blocks, inside the thirteen a road demands. All nine
     * were refused, every time. Nothing in the suite could see it: frontage
     * counts the plots that were <em>taken</em>, so a refused offer is invisible
     * to it, and the plan simply reported a hundred percent of a town that began
     * at fifty-three blocks out.
     *
     * <p>Held a little under what each measures rather than at it, so a block of
     * rounding somewhere else does not fail this. Measured: two, five and nine.
     */
    @Test
    void aRingTownBuildsOnItsOwnGreenBeforeItLeavesIt() {
        // The first ring runs at forty for the villages and thirty-four for the
        // camp; anything nearer the middle than the road itself stands on the
        // green inside it.
        check("ring_streets", 40, 2);
        check("radial_concentric", 40, 4);
        check("orc_ring", 34, 8);
    }

    private static void check(String id, int firstRing, int atLeast) {
        TownPlan plan = Layouts.of(id).planFor(CENTER, YOUNG);
        int inside = 0;
        for (TownPlan.Plot plot : plan.plots()) {
            if (CENTER.horizontalDistance(plot.at()) < firstRing) {
                inside++;
            }
        }
        System.out.println();
        System.out.println("== " + id + " @ " + YOUNG + " plots: "
                + plan.frontagePercent() + "% fronting, " + plan.streets().size()
                + " streets, " + inside + " inside the first ring ==");
        System.out.println(map(plan));
        assertTrue(inside >= atLeast,
                id + " stood " + inside + " of its first " + YOUNG
                        + " buildings inside its own first ring road, against the "
                        + atLeast + " the green holds -- which leaves a town of"
                        + " twenty with an empty middle");
    }

    /** How wide a map is drawn, matching {@code LayoutTest} so they compare. */
    private static final int WINDOW = 180;

    /** How many blocks one character of a map stands for. */
    private static final int CELL = 5;

    /** A plan from above: {@code #} a building, {@code .} a street. */
    private static String map(TownPlan plan) {
        int half = WINDOW / CELL;
        int side = 2 * half + 1;
        char[][] grid = new char[side][side];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }
        for (TownPlan.Street street : plan.streets()) {
            List<SimPos> path = street.path();
            for (int i = 1; i < path.size(); i++) {
                SimPos a = path.get(i - 1);
                SimPos b = path.get(i);
                int steps = Math.max(1, (int) Math.hypot(b.x() - a.x(), b.z() - a.z()));
                for (int t = 0; t <= steps; t++) {
                    ink(grid, plan.center(), half,
                            a.x() + (b.x() - a.x()) * t / steps,
                            a.z() + (b.z() - a.z()) * t / steps, '.');
                }
            }
        }
        for (TownPlan.Plot plot : plan.plots()) {
            ink(grid, plan.center(), half, plot.at().x(), plot.at().z(), '#');
        }
        StringBuilder out = new StringBuilder();
        for (char[] row : grid) {
            out.append(new String(row).replaceAll("\\s+$", "")).append('\n');
        }
        return out.toString();
    }

    private static void ink(char[][] grid, SimPos center, int half, int x, int z,
                            char mark) {
        int col = Math.floorDiv(x - center.x() + CELL / 2, CELL) + half;
        int row = Math.floorDiv(z - center.z() + CELL / 2, CELL) + half;
        if (row >= 0 && row < grid.length && col >= 0 && col < grid.length) {
            grid[row][col] = mark;
        }
    }
}
