package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.OrcRingLayout;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The orc war camp, held to the things that make it a camp.
 *
 * <p>The six invariants every arrangement keeps are checked in
 * {@link LayoutTest} for everything the registry holds, and this one is in the
 * registry, so they come for free and are deliberately not repeated here. What
 * is here is the shape's own claims — the ones a passing edit could break
 * without turning any of those red:
 *
 * <ul>
 *   <li>the plan keeps the middle for the chief;</li>
 *   <li>the yard round it is empty, so the middle is a muster ground and not a
 *       roundabout with a building in it;</li>
 *   <li>no lane crosses the yard, which is the one geometric difference between
 *       this and the vale folk's Rundling;</li>
 *   <li>it is round — as wide as it is long, at every size.</li>
 * </ul>
 *
 * <p>And a picture, printed rather than asserted. Every siting fault this
 * project has had was found by a person looking at one.
 */
class OrcRingLayoutTest {

    private static final SimPos CENTER = new SimPos(0, 64, 0);

    /**
     * A camp with an empty plan cache.
     *
     * <p>Planned layouts remember plans by the town's x and z and every test in
     * this JVM shares the registry's one instance, so asking the shared one for a
     * camp of sixty-four at the origin returns whatever the largest camp any
     * other test grew there was.
     */
    private static OrcRingLayout fresh() {
        OrcRingLayout like = (OrcRingLayout) Layouts.ORC_RING;
        return new OrcRingLayout(like.id(), like.wander());
    }

    // --- the middle -----------------------------------------------------------

    @Test
    void thePlanKeepsTheMiddleForTheChief() {
        // The plan offers the center and offers are taken nearest-first, so this
        // is really a test that the offer survives the sort and the fits check --
        // both of which have silently refused the equivalent offer on the
        // radial-concentric town during its development.
        //
        // About the PLAN, and deliberately not about the finished camp. Which
        // building actually lands on plot zero is the siting code's business and
        // it is the same answer every arrangement gets: a town builds shelter,
        // then food, then safety, so the camp post takes the middle on the first
        // step and the great hut is raised later, wherever the plot cursor has
        // reached. Culture.BURGHER's javadoc records the same thing about the
        // radial town's green. Reserving a plot for a particular building is a
        // change to siting rather than to a layout, and it is not made here.
        TownPlan plan = fresh().planFor(CENTER, 64);
        assertEquals(CENTER.x(), plan.plots().get(0).at().x(),
                "the first plot of a war camp is the chief's own ground");
        assertEquals(CENTER.z(), plan.plots().get(0).at().z(),
                "the first plot of a war camp is the chief's own ground");
        assertTrue(!plan.plots().get(0).frontsAStreet(),
                "the great hut fronts the yard, not a carriageway; naming a street"
                        + " would drag it off the middle to that street's curb");
    }

    @Test
    void theYardRoundTheGreatHutIsEmptyAndRingedWithHuts() {
        // What makes the middle a muster ground rather than a roundabout. The
        // nearest huts stand on the inner face of the ring road, a setback in
        // from it, and everything between them and the chief is trampled earth.
        //
        // Both halves are asserted, and the second half is the one that was
        // broken and invisible. At the ordinary straight-street setback the rim
        // held four huts of eight -- the four on the axes -- and the plan went
        // on reporting full frontage, because frontage counts the plots that
        // were taken and says nothing about the offers that were refused.
        TownPlan plan = fresh().planFor(CENTER, 140);
        int nearest = Integer.MAX_VALUE;
        int onTheRim = 0;
        for (int i = 1; i < plan.plots().size(); i++) {
            double away = CENTER.horizontalDistance(plan.plots().get(i).at());
            nearest = Math.min(nearest, (int) Math.round(away));
            if (away < 30) {
                onTheRim++;
            }
        }
        assertTrue(nearest >= 14,
                "something stands " + nearest + " blocks from the chief; the yard"
                        + " is supposed to be open ground the whole way to the ring");
        assertEquals(8, onTheRim,
                "the yard is ringed by " + onTheRim + " huts, not eight — half a"
                        + " rim is what a setback one block tight on a curve costs,"
                        + " and nothing else in the plan reports it");
    }

    @Test
    void noLaneCrossesTheYard() {
        // The one geometric difference between this and ring_streets, which
        // strikes its lanes out THROUGH its green from the middle. A camp whose
        // spokes reached the center would be a Rundling with orcs in it.
        TownPlan plan = fresh().planFor(CENTER, 140);
        for (TownPlan.Street street : plan.streets()) {
            for (SimPos point : street.path()) {
                assertTrue(CENTER.horizontalDistance(point) >= 20,
                        "a " + street.kind() + " street runs through the yard at "
                                + point);
            }
        }
    }

    // --- and it is round ------------------------------------------------------

    @Test
    void aCampIsAsWideAsItIsLong() {
        // A ring town that has stopped being round has stopped being this
        // arrangement. Measured as the reach on each axis, which is what the
        // wall is drawn round.
        for (int wanted : new int[] {24, 64, 140}) {
            TownPlan plan = fresh().planFor(CENTER, wanted);
            int acrossX = 0;
            int acrossZ = 0;
            for (TownPlan.Plot plot : plan.plots()) {
                acrossX = Math.max(acrossX, Math.abs(plot.at().x() - CENTER.x()));
                acrossZ = Math.max(acrossZ, Math.abs(plot.at().z() - CENTER.z()));
            }
            double ratio = (double) Math.max(acrossX, acrossZ)
                    / Math.max(1, Math.min(acrossX, acrossZ));
            assertTrue(ratio <= 1.35,
                    "a camp of " + wanted + " reaches " + acrossX + " by " + acrossZ
                            + ", which is an oblong rather than a ring");
        }
    }

    @Test
    void everyHutInACampFrontsOneOfItsRoads() {
        // The bar LayoutTest holds every planned arrangement to, said again here
        // with the numbers printed, because this is the measurement that has
        // collapsed silently on every ring layout ever written -- and the one
        // plot that fronts nothing is the great hut, on purpose.
        for (int wanted : new int[] {24, 60, 140}) {
            TownPlan plan = fresh().planFor(CENTER, wanted);
            assertTrue(plan.frontagePercent() >= 95,
                    "a camp of " + wanted + " fronted only " + plan.frontagePercent()
                            + "% of its huts on its own roads");
        }
    }

    @Test
    void theOrcsBuildInItFirst() {
        assertEquals(Culture.LAYOUT_ORC_RING, Culture.ORC.layouts().get(0));
        assertEquals(Layouts.ORC_RING, Layouts.of(Culture.LAYOUT_ORC_RING));
    }

    // --- the picture ----------------------------------------------------------

    @Test
    void printTheCamp() {
        for (int wanted : new int[] {64, 140}) {
            OrcRingLayout camp = fresh();
            TownPlan plan = camp.planFor(CENTER, wanted);
            int reach = 0;
            for (TownPlan.Plot plot : plan.plots()) {
                reach = Math.max(reach, Math.max(
                        Math.abs(plot.at().x() - CENTER.x()),
                        Math.abs(plot.at().z() - CENTER.z())));
            }
            System.out.println();
            System.out.println("=== orc_ring at " + wanted + " plots: reach " + reach
                    + ", frontage " + plan.frontagePercent() + "%, "
                    + plan.streets().size() + " streets ===");
            System.out.println(map(plan, reach));
        }
    }

    /**
     * The plan as characters: {@code #} a hut, {@code @} the great hut,
     * {@code .} carriageway.
     *
     * <p>One character to a {@link #CELL}-block square, because a camp of a
     * hundred and forty reaches two hundred blocks and nothing anybody can read
     * is two hundred characters wide.
     */
    private static final int CELL = 6;

    private static String map(TownPlan plan, int reach) {
        int half = (reach + CELL) / CELL;
        char[][] grid = new char[2 * half + 1][2 * half + 1];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }
        for (TownPlan.Street street : plan.streets()) {
            java.util.List<SimPos> path = street.path();
            for (int i = 1; i < path.size(); i++) {
                SimPos from = path.get(i - 1);
                SimPos to = path.get(i);
                int steps = (int) Math.max(1, from.horizontalDistance(to));
                for (int s = 0; s <= steps; s++) {
                    int x = from.x() + (to.x() - from.x()) * s / steps;
                    int z = from.z() + (to.z() - from.z()) * s / steps;
                    plot(grid, half, x, z, '.');
                }
            }
        }
        for (int i = 0; i < plan.plots().size(); i++) {
            SimPos at = plan.plots().get(i).at();
            plot(grid, half, at.x() - CENTER.x(), at.z() - CENTER.z(),
                    i == 0 ? '@' : '#');
        }
        StringBuilder out = new StringBuilder();
        for (char[] row : grid) {
            out.append(new String(row).replaceAll("\\s+$", "")).append('\n');
        }
        return out.toString();
    }

    private static void plot(char[][] grid, int half, int x, int z, char mark) {
        int col = Math.floorDiv(x, CELL) + half;
        int row = Math.floorDiv(z, CELL) + half;
        if (row < 0 || col < 0 || row >= grid.length || col >= grid[row].length) {
            return;
        }
        if (grid[row][col] == ' ' || mark != '.') {
            grid[row][col] = mark;
        }
    }
}
