package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.Grade;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town the world wrote down is not buried by the time anybody walks up to it.
 *
 * <p><strong>The report.</strong> Run B of the 2026-09-19 playtest, a highland
 * thorp on seed 20260919: {@code /civ audit} found <em>eleven of sixteen</em>
 * buildings buried, the mill under seven courses, the inn and the hall under six.
 * Nine of the eleven were step-0 seeded buildings. The shelf rule had been
 * written and {@code Founding.roomFor} had been asking it since it was written,
 * so on the face of it this could not happen.
 *
 * <p><strong>Why it happened anyway, and why the existing sweep could not see
 * it.</strong> {@code GradeTest} seeds a town against {@link RecordedTerrain} and
 * finds nothing condemned — correctly, because that fixture hands the siting the
 * <em>real</em> ground, and against real ground {@code roomFor} refuses every
 * plot the audit would condemn. A world does not do that. A seeded plan is laid
 * in chunks nobody has loaded, where the bridge answers from the generator's
 * estimate or from the town centre's own height, and the shelf the siting was
 * promised is not the shelf the world has. The one moment the truth is readable
 * is {@code Settlement.relocatePending}, called from {@code materializePending} a
 * line before the blueprint is drawn — and it asked {@code isSiteSuitable}, which
 * measures how far the ground falls across the bulk of a plot. A terrace is flat.
 * It passes. So the shelf rule was applied to fiction and never re-applied to the
 * world.
 *
 * <p>So the ground here lies the way a world lies — an estimate while nothing is
 * loaded, the truth once somebody arrives — and the town is seeded blind, walked
 * up to, and then audited with the auditor's own geometry.
 *
 * <p>Two fixtures, and they answer different questions. {@link RecordedTerrain}
 * is the regression guard: real captured ground, every seeded village clean. It
 * was clean before this fix as well, and that is worth writing down rather than
 * glossing — swept plot by plot over the whole captured field, {@code Grade.shelf}
 * returns {@code BURIED} nought times out of six and a half thousand, because
 * open hillside almost never stands over a floor on <em>every</em> side. The
 * recording cannot exhibit this fault. {@link PittedPlateau} can, and does.
 */
class SeededShelfTest {

    /** How coarsely the estimate reads the ground it has not loaded. */
    private static final int ESTIMATE_GRAIN = 32;

    /**
     * Ground that answers an estimate until somebody looks at it.
     *
     * <p>This is the state every seeded plan is laid in, and getting the shape of
     * the lie right is the whole of the fixture. The live bridge does not answer
     * an unread column with nothing and it does not answer it with a flat table
     * either — it answers from the generator's own noise, which is right about the
     * hillside and wrong about the column, by about eight courses. So the estimate
     * here is the recorded ground read on a coarse lattice: the same valley in the
     * large, the wrong height in the small.
     *
     * <p>A flat table would not do. Tried first, it made the seeded siting refuse
     * almost every plot outright on arrival and the sweep went green whatever the
     * relocation asked — a fixture that cannot exhibit the fault certifies it, and
     * that sentence is already written at the top of {@link RecordedTerrain}.
     */
    private static final class BlindUntilVisited implements WorldBridge {

        private final RecordedTerrain real;
        private boolean arrived;

        BlindUntilVisited(RecordedTerrain real) {
            this.real = real;
        }

        void arrive() {
            arrived = true;
        }

        /** The column the estimate actually reads, on its coarse lattice. */
        private static int coarse(int v) {
            return Math.floorDiv(v + ESTIMATE_GRAIN / 2, ESTIMATE_GRAIN) * ESTIMATE_GRAIN;
        }

        @Override
        public boolean isLoaded(SimPos pos) {
            return arrived;
        }

        @Override
        public boolean playerWithin(SimPos pos, double radius) {
            return false;   // unwatched, which is how a seeded town is found
        }

        @Override
        public int surfaceHeight(SimPos pos) {
            return arrived ? real.surfaceHeight(pos)
                    : real.groundAt(coarse(pos.x()), coarse(pos.z()));
        }

        @Override
        public boolean standsInWater(SimPos pos, int radius) {
            return arrived ? real.standsInWater(pos, radius)
                    : real.wetAt(coarse(pos.x()), coarse(pos.z()));
        }

        @Override
        public boolean isSiteSuitable(SimPos plot, int radius) {
            return siteFault(plot, radius) == SITE_FAULT_NONE;
        }

        @Override
        public int siteFault(SimPos plot, int radius) {
            if (arrived) {
                return real.siteFault(plot, radius);
            }
            if (standsInWater(plot, radius)) {
                return SITE_FAULT_OPEN_WATER;
            }
            // The estimate's own verdict on the estimate's own ground. A lattice
            // that coarse reads almost everything as level, which is exactly what
            // makes an unread claim look perfect from above.
            return SITE_FAULT_NONE;
        }

        @Override
        public boolean isSiteLevelable(SimPos plot, int radius) {
            return !arrived || real.isSiteLevelable(plot, radius);
        }

        @Override
        public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed,
                                              int facing) {
            return real.materializeBlueprint(id, origin, surveyed, facing);
        }

        @Override
        public int woodedness(SimPos center, int radius) {
            return real.woodedness(center, radius);
        }

        @Override
        public void log(String message) {
        }
    }

    /** Centres spread over the recorded field, terraces and all. */
    private static final List<SimPos> CENTRES = List.of(
            new SimPos(-160, 0, -100), new SimPos(-100, 0, -40),
            new SimPos(-40, 0, 20), new SimPos(16, 0, 80),
            new SimPos(80, 0, 140), new SimPos(140, 0, 200),
            new SimPos(200, 0, 260), new SimPos(-40, 0, 260),
            new SimPos(140, 0, -40), new SimPos(60, 0, 0));

    /** The arrangements a human town is drawn in, the thorp of the report first. */
    private static final List<String> ARRANGEMENTS =
            List.of("thorp", "ring", "crossroads", "organic", "high_street");

    /** A floor under the sweep, so it cannot go green by seeding nothing. */
    private static final int BUILDINGS_WORTH_SWEEPING = 400;

    /**
     * Steps after the arrival. One is enough for {@code materializePending} to
     * reach every building; a few more prove nothing moves back.
     */
    private static final int STEPS_AFTER_ARRIVAL = 5;

    /**
     * Every seeded village, on ground it was sited blind against, audits clean.
     *
     * <p>The audit's own geometry, not a recomputation of the siting's: the
     * footprint the placement reported and the floor it wrote, which is exactly
     * what {@code TownAuditor.checkShelf} reads. That is the number the report
     * came from and the only one worth pinning.
     */
    @Test
    void aSeededVillageIsNotBuriedByTheTimeAnybodyWalksUpToIt() {
        RecordedTerrain real = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        List<String> condemned = new ArrayList<>();
        int judged = 0;
        for (String layout : ARRANGEMENTS) {
            for (SimPos centre : CENTRES) {
                BlindUntilVisited ground = new BlindUntilVisited(real);
                SimPos site = new SimPos(centre.x(), ground.groundHeight(centre),
                        centre.z());
                Settlement town = Founding.seeded(site, "Ashmarch",
                        SettlementStage.VILLAGE, BuildCatalog.DEFAULT,
                        "civilization:human/highland", Founding.AS_THE_STAGE_HOUSES,
                        layout, ground);

                // Somebody walks up. From here the ground tells the truth, and
                // this is the only window in which a building can still move.
                ground.arrive();
                for (int step = 1; step <= STEPS_AFTER_ARRIVAL; step++) {
                    town.stores().add(TownStores.WOOD, 8);
                    town.stores().add(TownStores.STONE, 6);
                    town.step(new SimContext(ground, step, SimSettings.SANDBOX));
                }

                for (Building standing : town.buildings()) {
                    if (!standing.isMaterialized()) {
                        continue;   // nothing is drawn, so there is no shelf to read
                    }
                    Footprint plot = standing.footprint();
                    if (!plot.isKnown()) {
                        continue;
                    }
                    judged++;
                    Grade.Reading reading = readTheAudit(real, standing, plot);
                    if (reading.shelf() != Grade.Shelf.LEVEL) {
                        condemned.add(layout + " at " + centre + ": "
                                + standing.blueprintId() + " " + standing.origin()
                                + " " + reading.shelf() + " by "
                                + Math.max(reading.worstAbove(), reading.worstBelow()));
                    }
                }
            }
        }
        assertTrue(judged >= BUILDINGS_WORTH_SWEEPING,
                "only " + judged + " buildings were judged, so this sweep is no"
                        + " longer measuring anything");
        assertEquals(List.of(), condemned,
                condemned.size() + " of " + judged + " seeded buildings are buried"
                        + " or perched on arrival: " + condemned);
    }

    /**
     * And the fixture really does lie, which is what makes the sweep above a
     * measurement rather than a tautology.
     *
     * <p>If the blind bridge answered the same ground the recording does, the
     * test would be seeding against the truth exactly as {@code GradeTest} does,
     * and would go green whatever the relocation paths asked.
     */
    @Test
    void theBlindGroundReallyDisagreesWithTheWorldItHides() {
        RecordedTerrain real = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        SimPos centre = new SimPos(16, 0, 80);
        BlindUntilVisited ground = new BlindUntilVisited(real);
        int disagreed = 0;
        for (int dx = -120; dx <= 120; dx += 8) {
            for (int dz = -120; dz <= 120; dz += 8) {
                SimPos at = new SimPos(centre.x() + dx, 0, centre.z() + dz);
                if (ground.groundHeight(at) != real.groundHeight(at)) {
                    disagreed++;
                }
            }
        }
        assertTrue(disagreed > 400,
                "the blind bridge agreed with the recording at all but " + disagreed
                        + " columns, so it is not modelling an unread chunk at all");
    }

    /**
     * The audit's reading of a building, as {@code TownAuditor.checkShelf} takes
     * it: the placed footprint's own floor and its own rectangle.
     */
    private static Grade.Reading readTheAudit(RecordedTerrain real, Building standing,
                                              Footprint plot) {
        SimPos at = standing.origin();
        BuildingSizes.Size size = BuildingSizes.of(standing.blueprintId());
        int width = Math.min(plot.width(), size.width());
        int depth = Math.min(plot.depth(), size.depth());
        return Grade.around(
                (x, z) -> real.groundHeight(new SimPos(x, at.y(), z)),
                at,
                Math.max(1, width / 2 - BuildingSizes.APRON),
                Math.max(1, depth / 2 - BuildingSizes.APRON),
                plot.y());
    }

    /**
     * A plateau with hollows in it that the estimate did not show.
     *
     * <p><strong>Why a made-up world and not the recording.</strong> The
     * recording cannot exhibit this fault. Swept plot by plot over the whole
     * captured field — six and a half thousand of them — {@code Grade.shelf}
     * returns {@code BURIED} exactly nought times, because "buried" means the
     * ground stands over the floor on <em>every</em> side and open hillside almost
     * never does that. Seed 8675309 is where the road faults and the siting faults
     * reproduce; this one it cannot reach, and a sweep that goes green on ground
     * with no pits in it is not evidence about pits.
     *
     * <p>Run B's ground was terraced, and the eleven buried buildings were under
     * six and seven courses. That is this shape: flat to build on, walled on every
     * side, and — the part that matters — absent from the estimate the plan was
     * drawn against.
     *
     * <p><strong>The hollows are dug after the plan is laid.</strong> That is not
     * a cheat; it is the causality of the fault stated exactly. A seeded plan is
     * drawn against ground nobody has read, so the real ground cannot have
     * informed it, and digging afterwards is the only way to be certain the
     * siting was blind to them — which is the premise the whole report rests on.
     */
    private static final class PittedPlateau implements WorldBridge {

        /** The plain the town is laid out on. */
        static final int PLATEAU = 80;

        /** How deep a hollow is: past the cut's reach, so it reads as buried. */
        private static final int HOLLOW_DEPTH = 7;

        /**
         * A hollow is exactly as wide as the walls of the building over it.
         *
         * <p>Which is the shape the fault has, and the only shape that has it. A
         * hollow narrower than the plot leaves half the plot up on the plateau,
         * so the floor is taken from the plateau and the building sits on it
         * level; a hollow wider than the doorstep ring is a valley the building
         * stands level in the bottom of. Only a hollow the size of the plot
         * itself puts the floor down in the hole and the ring up on the rim,
         * which is "buried — the ground stands up to 7 above its floor on every
         * side" word for word.
         */
        private final java.util.Map<Long, Integer> hollows = new java.util.HashMap<>();

        private boolean arrived;

        private static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }

        void digAHollowAt(SimPos at, int half) {
            hollows.put(key(at.x(), at.z()), half);
        }

        void arrive() {
            arrived = true;
        }

        boolean inAHollow(int x, int z) {
            for (var hollow : hollows.entrySet()) {
                int half = hollow.getValue();
                for (int dx = -half; dx <= half; dx++) {
                    for (int dz = -half; dz <= half; dz++) {
                        if (key(x + dx, z + dz) == hollow.getKey()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean isLoaded(SimPos pos) {
            return arrived;
        }

        @Override
        public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }

        @Override
        public int surfaceHeight(SimPos pos) {
            return arrived && inAHollow(pos.x(), pos.z())
                    ? PLATEAU - HOLLOW_DEPTH : PLATEAU;
        }

        @Override
        public boolean standsInWater(SimPos pos, int radius) {
            return false;   // dry, so nothing here is settled by the water rule
        }

        @Override
        public boolean isSiteSuitable(SimPos plot, int radius) {
            return true;    // flat across its bulk wherever you stand: see above
        }

        @Override
        public int siteFault(SimPos plot, int radius) {
            return SITE_FAULT_NONE;
        }

        @Override
        public boolean isSiteLevelable(SimPos plot, int radius) {
            return true;
        }

        @Override
        public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed,
                                              int facing) {
            int span = BuildPlanner.plotSpanOf(id, BuildCatalog.DEFAULT);
            return new Footprint(
                    Grade.floorFor(this, origin, span, Grade.isField(id)),
                    span, span, 5);
        }

        @Override
        public int woodedness(SimPos center, int radius) {
            return 40;
        }

        @Override
        public void log(String message) {
        }
    }

    /**
     * The fault itself: a village the world wrote down onto ground it could not
     * see, arriving to find a third of itself in pits.
     *
     * <p>This is the test that was red. Without the shelf rule in the relocation
     * paths, the plots chosen against the estimate are drawn exactly where the
     * estimate put them — {@code Settlement.relocatePending} asks
     * {@code isSiteSuitable}, the hollow floors are perfectly flat, and it says
     * yes — and the audit then reports them buried under seven courses, which is
     * the report word for word.
     */
    @Test
    void aVillageSitedAgainstAnEstimateIsRescuedWhenTheGroundTurnsOutToBePitted() {
        List<String> condemned = new ArrayList<>();
        int judged = 0;
        int dug = 0;
        for (String layout : ARRANGEMENTS) {
            for (int spot = 0; spot < 6; spot++) {
                PittedPlateau ground = new PittedPlateau();
                SimPos site = new SimPos(spot * 37, PittedPlateau.PLATEAU, spot * 53);
                Settlement town = Founding.seeded(site, "Ashmarch",
                        SettlementStage.VILLAGE, BuildCatalog.DEFAULT,
                        "civilization:human/highland", Founding.AS_THE_STAGE_HOUSES,
                        layout, ground);

                // And now the ground turns out to be something else. Every third
                // building of the plan is standing over a hollow nobody could
                // have seen when the plan was drawn.
                int nth = 0;
                for (Building planned : town.buildings()) {
                    if (nth++ % 3 == 0) {
                        int span = BuildPlanner.plotSpanOf(planned.blueprintId(),
                                BuildCatalog.DEFAULT);
                        ground.digAHollowAt(planned.origin(),
                                Math.max(1, span / 2 - BuildingSizes.APRON));
                        dug++;
                    }
                }

                ground.arrive();
                for (int step = 1; step <= STEPS_AFTER_ARRIVAL; step++) {
                    town.stores().add(TownStores.WOOD, 8);
                    town.stores().add(TownStores.STONE, 6);
                    town.step(new SimContext(ground, step, SimSettings.SANDBOX));
                }

                for (Building standing : town.buildings()) {
                    if (!standing.isMaterialized() || !standing.footprint().isKnown()) {
                        continue;
                    }
                    judged++;
                    SimPos at = standing.origin();
                    Footprint plot = standing.footprint();
                    Grade.Reading reading = Grade.around(
                            (x, z) -> ground.surfaceHeight(new SimPos(x, at.y(), z)),
                            at,
                            Math.max(1, plot.width() / 2 - BuildingSizes.APRON),
                            Math.max(1, plot.depth() / 2 - BuildingSizes.APRON),
                            plot.y());
                    if (reading.shelf() != Grade.Shelf.LEVEL) {
                        condemned.add(layout + " " + standing.blueprintId() + " "
                                + at + " " + reading.shelf() + " by "
                                + Math.max(reading.worstAbove(), reading.worstBelow()));
                    }
                }
            }
        }
        assertTrue(dug > 50,
                "only " + dug + " hollows were dug, so this fixture is not putting"
                        + " the siting to the question at all");
        assertTrue(judged >= 100,
                "only " + judged + " buildings were judged; the sweep has stopped"
                        + " measuring anything");
        assertEquals(List.of(), condemned,
                condemned.size() + " of " + judged + " seeded buildings were raised"
                        + " on ground the audit condemns, with " + dug + " hollows"
                        + " under the plan: " + condemned);
    }

    /**
     * The rule itself, stated where it can be read: both questions, one number.
     *
     * <p>A terrace is the shape the whole fault turns on — flat across its bulk,
     * walled on every side — so it is worth having one place that says outright
     * that {@code siteFault} passes it and {@code Grade} does not.
     */
    @Test
    void aTerraceIsFlatGroundThatNobodyCanWalkInto() {
        int floorGrade = 70;
        // Flat ground in a box, with a wall of hillside round the outside of it.
        WorldBridge terrace = new WorldBridge() {
            @Override
            public boolean isLoaded(SimPos pos) {
                return true;
            }

            @Override
            public boolean playerWithin(SimPos pos, double radius) {
                return false;
            }

            @Override
            public int surfaceHeight(SimPos pos) {
                boolean inside = Math.abs(pos.x()) <= 5 && Math.abs(pos.z()) <= 5;
                return inside ? floorGrade + 1 : floorGrade + 8;
            }

            @Override
            public Footprint materializeBlueprint(String id, SimPos origin,
                                                  boolean surveyed, int facing) {
                return Footprint.UNKNOWN;   // nothing is drawn here; only judged
            }

            @Override
            public void log(String message) {
            }
        };
        SimPos plot = new SimPos(0, floorGrade, 0);
        assertEquals(Grade.Shelf.BURIED, Grade.shelf(terrace, plot, 13, false),
                "a shelf of hillside eight courses proud is not something a crew"
                        + " can cut away");
        assertEquals(WorldBridge.SITE_FAULT_NONE,
                terrace.siteFault(plot, BuildPlanner.PLOT_PROBE_RADIUS),
                "the fall across the bulk of a terrace is nil, which is exactly"
                        + " why asking only that question buried a town");
        assertTrue(Grade.groundFault(terrace, plot, 13, false)
                        > BuildPlanner.LEVELABLE_FALL,
                "the one number has to carry the shelf's verdict, or every path"
                        + " that reads it is back to asking half the question");
    }
}
