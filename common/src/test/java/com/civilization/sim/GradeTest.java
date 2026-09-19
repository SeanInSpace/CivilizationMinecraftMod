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

    /**
     * The audit reads the same ring at the same reach the siting does.
     *
     * <p><strong>The second fault in the report, and the sharper one.</strong>
     * There were two shelf tolerances for one ring. {@link Grade} allowed three
     * courses, because that is what {@code BlueprintPlacer.finish} cuts off the
     * apron and what {@code BlueprintPlacer.foundation} packs back up on it;
     * {@code TownAuditor} allowed one, over the same ring, and had its own
     * constant to do it with. {@code Grade} also carried a dead
     * {@code SHELF_TOLERANCE} of one, documented as mirroring the auditor's and
     * read by nothing, so the disagreement was written down three times and
     * enforced once.
     *
     * <p>The placer settles which number is right, and it is three. The apron cut
     * runs over the columns at {@code rx + APRON_MARGIN} — with
     * {@link BuildingSizes#APRON} at one, exactly the ring one step outside the
     * walls the auditor measures — for {@code APRON_HEADROOM} courses above the
     * floor. The crew really does take three courses off that exact ring, so an
     * auditor tolerating one was condemning buildings its own colleagues had
     * already levelled. "Buried — the ground stands up to 3 above its floor on
     * every side" is that sentence in the audit's own words, and the 3 in it is
     * the cut's reach.
     *
     * <p>Entered through {@code Grade.around}, which is the call
     * {@code TownAuditor.checkShelf} now makes: the two are one piece of
     * arithmetic rather than two copies of it, so this pins the audit and not a
     * lookalike.
     */
    @Test
    void theAuditReadsTheSameRingAtTheSameReachTheCutHas() {
        SimPos middle = new SimPos(0, GRADE, 0);
        int span = 9;
        int wallHalf = Math.max(1, span / 2 - BuildingSizes.APRON);

        // A floor sunk exactly as far as the apron cut reaches down to it. The
        // crew shovels this out; it is ground, not a pit.
        Grade.Reading reachable = Grade.around(
                (x, z) -> GRADE, middle, wallHalf, wallHalf, GRADE - 1 - Grade.CUT_REACH);
        assertEquals(Grade.Shelf.LEVEL, reachable.shelf(),
                "the audit must not condemn a shelf the apron cut takes off — "
                        + "that was the whole of the disagreement");

        // One course further and nothing in the placement pass can reach it.
        Grade.Reading beyond = Grade.around(
                (x, z) -> GRADE, middle, wallHalf, wallHalf,
                GRADE - 1 - (Grade.CUT_REACH + 1));
        assertEquals(Grade.Shelf.BURIED, beyond.shelf(),
                "and it must still condemn ground nothing can reach");
        assertEquals(Grade.CUT_REACH + 1, beyond.worstAbove(),
                "and say by how much, which is what the report quoted");

        // The mirror, on the fill's side: the doorstep goes in whole or not at all
        // and reaches FILL_REACH down, so that is where perched begins.
        assertEquals(Grade.Shelf.LEVEL, Grade.around(
                        (x, z) -> GRADE, middle, wallHalf, wallHalf,
                        GRADE - 1 + Grade.FILL_REACH).shelf(),
                "a drop the doorstep packs up is a step, not a perch");
        assertEquals(Grade.Shelf.PERCHED, Grade.around(
                        (x, z) -> GRADE, middle, wallHalf, wallHalf,
                        GRADE - 1 + Grade.FILL_REACH + 1).shelf(),
                "and one course past its reach is a floor hanging in the air");
    }

    /**
     * And on real ground: nothing the siting accepts is a building the audit
     * calls buried.
     *
     * <p>The invariant the two tolerances broke, swept over the recorded hillside
     * rather than over one constructed shape — every arrangement, at a spread of
     * centers, seeded and then judged with the geometry {@code TownAuditor} uses:
     * the footprint the placement reported, and the floor {@link Grade#floorFor}
     * gives it.
     *
     * <p><strong>It reads zero, and that is the result rather than an absence of
     * one.</strong> {@code Founding.roomFor} refuses ground the shelf rule
     * condemns, so a seeded town has no buried buildings <em>if and only if</em>
     * the rule the siting refused by and the rule the audit condemns by are the
     * same rule. They were not, and that is what the report was. This goes red the
     * moment somebody gives either side a number of its own again.
     */
    @Test
    void nothingTheSitingAcceptsIsBuriedWhenTheAuditReadsIt() {
        RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        List<String> condemned = new ArrayList<>();
        int judged = 0;
        for (String layout : SWEPT_ARRANGEMENTS) {
            for (int x = -100; x <= 200; x += 100) {
                for (int z = 60; z <= 300; z += 80) {
                    Settlement town = Founding.seeded(new SimPos(x, 0, z), "Agree",
                            SettlementStage.VILLAGE, BuildCatalog.DEFAULT,
                            "civilization:human/burgher",
                            Founding.AS_THE_STAGE_HOUSES, layout, ground);
                    for (Building standing : town.buildings()) {
                        Footprint plot = standing.footprint();
                        if (!plot.isKnown()) {
                            continue;   // never placed; there is no shelf to read
                        }
                        int span = BuildPlanner.plotSpanOf(standing.blueprintId(),
                                town.catalog());
                        SimPos at = new SimPos(standing.origin().x(),
                                ground.groundHeight(standing.origin()),
                                standing.origin().z());
                        int floor = Grade.floorFor(ground, at, span,
                                Grade.isField(standing.blueprintId()));
                        Grade.Reading reading = Grade.around(
                                (cx, cz) -> ground.groundHeight(new SimPos(cx, at.y(), cz)),
                                at,
                                Math.max(1, plot.width() / 2 - BuildingSizes.APRON),
                                Math.max(1, plot.depth() / 2 - BuildingSizes.APRON),
                                floor);
                        judged++;
                        if (reading.shelf() != Grade.Shelf.LEVEL) {
                            condemned.add(layout + " " + standing.blueprintId() + " at "
                                    + standing.origin() + " " + reading.shelf()
                                    + " by " + Math.max(reading.worstAbove(),
                                            reading.worstBelow()));
                        }
                    }
                }
            }
        }
        assertTrue(judged >= BUILDINGS_WORTH_SWEEPING,
                "only " + judged + " buildings were judged, so this sweep is no"
                        + " longer measuring anything");
        assertEquals(List.of(), condemned,
                condemned.size() + " of " + judged + " seeded buildings would be"
                        + " condemned by the audit that passed the siting");
    }

    /**
     * The arrangements the sweep above seeds in.
     *
     * <p>Named rather than gathered from {@code Culture}, because this seeds one
     * people's town in each and a goblin camp laid out as a bastide is not a thing
     * the sweep is about. These are the arrangements a human town is drawn in, and
     * between them they cover the lattice ones and the street-first ones, which is
     * the distinction any of this turns on.
     */
    private static final List<String> SWEPT_ARRANGEMENTS =
            List.of("ring", "radial_concentric", "ring_streets", "high_street",
                    "crossroads", "bastide", "green", "thorp");

    /** A floor under the sweep, so it cannot go green by seeding nothing. */
    private static final int BUILDINGS_WORTH_SWEEPING = 500;

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
