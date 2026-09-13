package com.civilization.sim;

import com.civilization.sim.culture.Layouts;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.Grade;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry the auditor condemns a building by, asked before it is built.
 *
 * <p>The report: the in-game audit on arrival said the seeded hearth, lumber camp
 * and mine of Millbrook were "buried — the ground stands up to 3 above its floor
 * on every side", Stonebridge's hearth and mine the same, and a cottage "no way in
 * — outside the gap is air". The seeded siting had passed every one of those plots,
 * because it was asking {@code siteFault} — how far does the ground fall across the
 * bulk of this plot — and the auditor asks something else entirely.
 *
 * <p><strong>Why the ground here is built by hand.</strong> The recorded hillside
 * cannot exhibit this fault: measured over three hundred and seventy-five seeded
 * buildings on seed 8675309, it produces nought shelf faults by either floor rule.
 * A bowl nine blocks across and four deep is what it takes, and the recording is
 * sampled at a grain of two, so the shapes that do it are smoothed out of it. The
 * lesson is the one {@code HeightField}'s own javadoc draws: a fixture that cannot
 * show the fault certifies the fault. So the shapes are constructed — a bowl, a
 * knoll, a shelf — and the recorded ground is used for the thing it is good for,
 * which is measuring how often the rule fires on real terrain.
 */
class GradeTest {

    /** A hillside a test can state in one line: a height per column, by formula. */
    private abstract static class Shaped implements WorldBridge {
        abstract int heightAt(int x, int z);

        @Override public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }

        @Override public boolean isLoaded(SimPos pos) {
            return true;
        }

        @Override public int surfaceHeight(SimPos pos) {
            return heightAt(pos.x(), pos.z());
        }

        @Override public int groundHeight(SimPos pos) {
            return heightAt(pos.x(), pos.z());
        }

        @Override public boolean isSiteSuitable(SimPos plot, int radius) {
            return siteFault(plot, radius) == SITE_FAULT_NONE;
        }

        /**
         * Falls across the bulk, exactly as the live bridge and the recorded
         * fixture score it — the twentieth to eightieth percentile of the plot's
         * columns, against the same allowance.
         */
        @Override public int siteFault(SimPos plot, int radius) {
            List<Integer> heights = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx += 3) {
                for (int dz = -radius; dz <= radius; dz += 3) {
                    heights.add(heightAt(plot.x() + dx, plot.z() + dz));
                }
            }
            java.util.Collections.sort(heights);
            int fall = heights.get((heights.size() * 4) / 5) - heights.get(heights.size() / 5);
            return Math.max(0, fall - RecordedTerrain.MAX_FALL);
        }

        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return Footprint.UNKNOWN;
        }

        @Override public void log(String message) {
        }
    }

    private static final int GRADE = 72;

    /** How deep the dip is. Past the apron cut's reach, which is what condemns it. */
    private static final int DEEP = Grade.CUT_REACH + 2;

    /**
     * Half a hollow that is exactly the blind spot, and the arithmetic that makes
     * it one.
     *
     * <p>Six. {@code siteFault} probes {@link BuildPlanner#PLOT_PROBE_RADIUS} — six
     * blocks — so a flat-bottomed hollow this wide is one every sample of the probe
     * lands inside: it scores a perfect nought. The shelf of a fifteen-wide farm is
     * its ring at seven, which is outside — so the farm's floor is set in the hollow
     * and the ground stands {@code DEEP} above it on every side.
     *
     * <p>One block either way and the fixture shows nothing. At seven the ring is
     * inside the hollow too and the farm reads level; at five the probe reaches the
     * rim and the old rule refuses the plot on its own. That narrowness is the
     * finding as much as the fixture is: for every building up to thirteen wide the
     * probe is wider than the shelf, so the rule siting already had was looking at
     * the ground the auditor judges. The gap is the two widest things a town builds.
     */
    private static final int HOLLOW_HALF = BuildPlanner.PLOT_PROBE_RADIUS;

    /**
     * Half the walls of a plot of this span — the square the bowl has to cover.
     *
     * <p>Exactly the walls and no more, which is the whole of getting these
     * fixtures right. The shelf the auditor judges is the ring ONE STEP OUTSIDE
     * the walls; a bowl a block wider than that swallows the ring as well, the
     * ring reads level with the floor, and the fixture quietly stops being able
     * to show anything.
     */
    private static int wallsHalf(int span) {
        return Math.max(1, span / 2 - BuildingSizes.APRON);
    }

    /** A flat plain with one square bowl in it, wide enough to swallow a hut. */
    private static final class Bowl extends Shaped {
        private final SimPos middle;
        private final int half;

        Bowl(SimPos middle, int half) {
            this.middle = middle;
            this.half = half;
        }

        @Override int heightAt(int x, int z) {
            boolean inside = Math.abs(x - middle.x()) <= half
                    && Math.abs(z - middle.z()) <= half;
            return inside ? GRADE - DEEP : GRADE;
        }
    }

    @Test
    void aPlotInABowlIsGradedAsBuried() {
        // The bowl is the fault. The floor is set in the bottom of it and the ring
        // of ground one step outside the walls stands DEEP above that on every
        // side — past what the apron cut can take off, so the placer leaves it and
        // the auditor reports the building buried under it.
        SimPos middle = new SimPos(0, GRADE, 0);
        int span = 9;
        Bowl bowl = new Bowl(middle, wallsHalf(span));

        assertEquals(Grade.Shelf.BURIED, Grade.shelf(bowl, middle, span, false),
                "a building whose every side stands over its floor is buried");
        // And what the rule siting already had says about the same ground: a
        // hummock, scored one, which worthLeveling accepts and leastBad ranks near
        // the top. It is not that siteFault says nothing — it is that what it says
        // is "a few barrows of earth", and what the auditor says is "buried".
        assertTrue(bowl.siteFault(middle, BuildPlanner.PLOT_PROBE_RADIUS) <= 1,
                "the rule siting had scores this ground as a trifle, which is the "
                        + "whole of why a second rule was needed");
    }

    @Test
    void aDipTheCutCanReachIsNotBuried() {
        // The other half, so the rule is not simply refusing dips. A bowl within
        // the cut's reach is a bowl the crew shovels out, and the building stands
        // in it with its doorstep at grade.
        SimPos middle = new SimPos(0, GRADE, 0);
        int span = 9;
        WorldBridge shallow = new Shaped() {
            @Override int heightAt(int x, int z) {
                boolean inside = Math.abs(x) <= wallsHalf(span) && Math.abs(z) <= wallsHalf(span);
                return inside ? GRADE - Grade.CUT_REACH : GRADE;
            }
        };

        assertEquals(Grade.Shelf.LEVEL, Grade.shelf(shallow, middle, span, false),
                "a dip the apron cut reaches is ground, not a pit");
    }

    @Test
    void aKnollThePlacerCannotReachDownToIsGradedAsPerched() {
        // The mirror image, and the auditor's other word for it. A plot standing
        // proud of everything around it by more than the underpinning can pack up
        // has its doorway in the air on every side: "no way in — outside the gap
        // is air", which is what the cottage in the report said.
        SimPos middle = new SimPos(0, GRADE, 0);
        int span = 9;
        WorldBridge knoll = new Shaped() {
            @Override int heightAt(int x, int z) {
                boolean inside = Math.abs(x) <= wallsHalf(span) && Math.abs(z) <= wallsHalf(span);
                return inside ? GRADE : GRADE - (Grade.FILL_REACH + 2);
            }
        };

        assertEquals(Grade.Shelf.PERCHED, Grade.shelf(knoll, middle, span, false),
                "a floor hanging over the ground on every side is perched");
    }

    @Test
    void oneOpenSideIsAllAnEntranceNeeds() {
        // The auditor's own rule, which this must not be stricter than: partial
        // banking is left alone, because a hillside build is banked uphill by
        // nature and one open side is a door.
        SimPos middle = new SimPos(0, GRADE, 0);
        int span = 9;
        WorldBridge hillside = new Shaped() {
            @Override int heightAt(int x, int z) {
                return z > wallsHalf(span) ? GRADE : GRADE + 20;   // uphill on one side
            }
        };

        assertEquals(Grade.Shelf.LEVEL, Grade.shelf(hillside, middle, span, false),
                "one side the ground can be brought to is enough");
    }

    /**
     * And the siting actually asks — on the one shape it was ever blind to.
     *
     * <p>This took some finding and the finding is the useful part. A bowl that
     * would bury a <em>small</em> building is a bowl {@code siteFault} already sees,
     * because it probes {@link BuildPlanner#PLOT_PROBE_RADIUS} — six blocks — and
     * the shelf a nine-wide building is judged by is its ring at four. The probe is
     * wider than the ring, so for everything up to a thirteen-wide hall the old rule
     * was already looking at the ground the new rule looks at.
     *
     * <p>The blind spot is the buildings <em>wider</em> than the probe: the farm at
     * fifteen and the compound at seventeen, whose shelves stand at seven and eight
     * while the probe only reaches six. A flat-bottomed hollow fifteen across scores
     * a perfect nought — every sample the probe takes is on its floor — and buries a
     * farm to five courses on every side. That is what this ground is.
     */
    @Test
    void theSeededSitingPassesOverGroundTheAuditorWouldCondemn() {
        SimPos center = new SimPos(0, GRADE, 0);
        // A hollow sunk under every other plot the plan offers. Put where the plan
        // actually puts buildings rather than on a grid of its own, because a grid
        // that happens to miss the plots tests nothing — an earlier version of this
        // fixture pitched its hollows at twenty-four and caught not one plot in
        // fourteen.
        List<SimPos> hollows = new ArrayList<>();
        for (int at = 0; at < Founding.PLOTS_ENOUGH_FOR_ANY_PROGRAM; at += 2) {
            hollows.add(Layouts.RING.plotFor(center, at));
        }
        WorldBridge dimpled = new Shaped() {
            @Override int heightAt(int x, int z) {
                for (SimPos hollow : hollows) {
                    if (Math.abs(x - hollow.x()) <= HOLLOW_HALF
                            && Math.abs(z - hollow.z()) <= HOLLOW_HALF) {
                        return GRADE - DEEP;
                    }
                }
                return GRADE;
            }
        };

        Settlement town = Founding.seeded(center, "Dimple", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, "civilization:human/burgher",
                Founding.AS_THE_STAGE_HOUSES, Layouts.RING.id(), dimpled);

        List<String> condemned = new ArrayList<>();
        for (Building standing : town.buildings()) {
            int span = BuildPlanner.plotSpanOf(standing.blueprintId(), town.catalog());
            Grade.Shelf verdict = Grade.shelf(dimpled, standing.origin(), span,
                    Grade.isField(standing.blueprintId()));
            if (verdict != Grade.Shelf.LEVEL) {
                condemned.add(standing.blueprintId() + " (span " + span + ") at "
                        + standing.origin() + " " + verdict);
            }
        }
        System.out.println("DIMPLED GROUND: " + condemned.size() + " of "
                + town.buildings().size() + " seeded buildings condemned; " + condemned);
        assertTrue(town.buildings().size() >= 10,
                "and the town is still a village rather than three sheds: "
                        + town.buildings().size());
        assertEquals(List.of(), condemned,
                "a seeded building must stand where its own placer can reach the "
                        + "ground round it");
    }

    @Test
    void theFloorRuleIsTheOneThePlacerUses() {
        // The other disagreement the report came from, stated as arithmetic. A crew
        // surveys the plot and takes its median; the unwatched placement pass took
        // the origin column alone. Where the origin sits low, the two differ and
        // the difference is a building in a pit of its own.
        int[] plot = {70, 71, 71, 72, 72, 72, 73, 73, 74};
        assertEquals(71, Grade.floorAcross(plot, false),
                "the median course, less one so the floor replaces the topsoil");
        // Held down to what the underpinning can reach: one cave mouth in a corner
        // must not drag the whole building down to it.
        int[] withAHole = {40, 71, 71, 72, 72, 72, 73, 73, 74};
        assertEquals(71, Grade.floorAcross(withAHole, false),
                "one hole is not the low ground");
        int[] onASlope = {60, 61, 62, 70, 71, 72, 80, 81, 82};
        assertEquals(61 + Grade.FILL_REACH, Grade.floorAcross(onASlope, false),
                "and across a real slope the fill's reach is what sets the floor");
    }
}
