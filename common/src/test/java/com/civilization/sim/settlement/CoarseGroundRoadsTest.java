package com.civilization.sim.settlement;

import com.civilization.sim.RecordedTerrain;
import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hillside read coarsely is still a hillside.
 *
 * <p>The fault this class holds shut was in the world and in no fixture: the
 * town at the world spawn stood with no road between most of its buildings
 * while every road test in this suite was green. The two facts are one fact.
 *
 * <p>{@code PathPlanner.unwalkable} decides whether a stretch of the plan is
 * ground anybody could walk, and it asked the question column by column — is
 * this column more than a step above the one before it? Every fixture in this
 * project answers that from a height it knows per column: {@code TerrainFake}
 * from three sine waves, {@code RecordedTerrain} from a recording of the real
 * ground. On a fixture the question is exactly the question it reads as.
 *
 * <p>The live world does not answer per column. {@code TerrainOracle} remembers
 * ground on a four-block grid, because sampling the generator per column took
 * sixty seconds of a tick and killed the server twice; its own note says four
 * blocks is finer than anything reading it can tell. One caller could tell. A
 * slope of one block per column, rounded to that grid, is handed back as four
 * blocks of table top and then a four-block cliff — and the gate refused it,
 * every time, on ground a player walks up without noticing there is a slope.
 *
 * <p>So a world now says how finely it can see ({@code WorldBridge.groundGrain})
 * and the gate scales its allowance by that. These ask it of both resolutions.
 * A bridge reading every column is the old rule, unchanged in what it permits;
 * a bridge reading one column in four is the world.
 */
class CoarseGroundRoadsTest {

    /**
     * How coarse the live reading is, which is what this fixture imitates.
     *
     * <p>{@code TerrainOracle.GRAIN}, restated rather than imported: that class
     * is the platform's and this module cannot see it. If the live number ever
     * moves, this test goes on measuring what it was written to measure —
     * whether a reading coarser than a block can invent a cliff — and the exact
     * coarseness is not the point.
     */
    private static final int GRAIN = 4;

    /**
     * A world that is nothing but an even slope, reported at a stated grain.
     *
     * <p>Rounded down to the grain exactly as the oracle remembers columns:
     * every column of a cell answers with the cell's own corner, and the bridge
     * says so.
     */
    private record Slope(int riseEveryBlock, int grain) implements WorldBridge {

        @Override
        public int surfaceHeight(SimPos pos) {
            return 64 + (pos.x() - Math.floorMod(pos.x(), grain)) * riseEveryBlock;
        }

        @Override
        public int groundGrain() {
            return grain;
        }

        @Override
        public boolean isLoaded(SimPos pos) {
            return true;
        }

        @Override
        public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }

        @Override
        public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed,
                                              int facing) {
            return Footprint.UNKNOWN;   // nothing stands on this slope; it is a run of ground
        }

        @Override
        public void log(String message) {
        }
    }

    private static SimContext on(WorldBridge bridge) {
        return new SimContext(bridge, 1, SimSettings.SANDBOX);
    }

    /** A straight run of forty blocks east, which is what a lane out of a door is. */
    private static PathNetwork.Segment run() {
        return new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(40, 64, 0));
    }

    @Test
    void anOrdinarySlopeIsWalkableHoweverCoarselyItIsRead() {
        // One block up for every block along: the gentlest slope there is, and
        // the one a whole town is built on. Read per column it climbs a block a
        // step; read on the oracle's grid it climbs four blocks every fourth
        // step, and the second reading is the same hillside.
        for (int grain : new int[] {1, GRAIN}) {
            assertFalse(PathPlanner.unwalkable(run(), on(new Slope(1, grain))),
                    "a one-in-one slope was refused when read at a grain of " + grain);
        }
    }

    @Test
    void andSoIsTheSteepestOneTheCrewCanGrade() {
        // Two a block is what PathLayer.grade earns with a spadeful, so the gate
        // has to pass it or the town refuses roads its own crew would build.
        for (int grain : new int[] {1, GRAIN}) {
            assertFalse(PathPlanner.unwalkable(run(), on(new Slope(2, grain))),
                    "a two-in-one slope was refused when read at a grain of " + grain);
        }
    }

    @Test
    void andACliffIsStillACliff() {
        // The other half, and the half a looser rule could have thrown away.
        // Three a block is past grading at any resolution: no single block moved
        // makes it walkable, and a coarse reading of it is coarser still.
        for (int grain : new int[] {1, GRAIN}) {
            assertTrue(PathPlanner.unwalkable(run(), on(new Slope(3, grain))),
                    "a three-in-one cliff was accepted when read at a grain of " + grain);
        }
    }

    @Test
    void andAFineReadingIsJudgedNoMoreKindlyThanBefore() {
        // The cost of the fix, bounded. Scaling the allowance by the grain must
        // not hand a licence to a world that has no grain: a wall in a fixture
        // that knows every column is still a wall, and the number it is held to
        // is the one PathLayer can grade.
        assertTrue(PathPlanner.unwalkable(
                        new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(8, 64, 0)),
                        on(new Wall(3))),
                "a three-block wall was accepted by a bridge that can see every column");
        assertFalse(PathPlanner.unwalkable(
                        new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(8, 64, 0)),
                        on(new Wall(2))),
                "a two-block step was refused by a bridge that can see every column");
        assertFalse(PathPlanner.unwalkable(run(), on(new Slope(0, 1))),
                "level ground was refused");
    }

    /** Flat ground with one step up in the middle of it, reported per column. */
    private record Wall(int step) implements WorldBridge {

        @Override
        public int surfaceHeight(SimPos pos) {
            return pos.x() < 4 ? 64 : 64 + step;
        }

        @Override
        public boolean isLoaded(SimPos pos) {
            return true;
        }

        @Override
        public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }

        @Override
        public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed,
                                              int facing) {
            return Footprint.UNKNOWN;
        }

        @Override
        public void log(String message) {
        }
    }

    /**
     * And the town on the recorded hillside opens the same roads either way.
     *
     * <p>The statement above made about a whole settlement rather than one run.
     * The recorded ground is the same square of seed 8675309 every survey in
     * this project uses; the only difference between the two towns below is how
     * finely the bridge is willing to report a height, and whether it says so.
     * A town reading the same ground through a coarser instrument has to build
     * the same roads, or the ledger is a record of the instrument rather than of
     * the world.
     *
     * <p>Not bit-identical, and the allowance is named rather than fudged: the
     * router asks the same estimate when it chooses which way round a hill a
     * street runs, so the coarse town lays slightly different lines and not the
     * same lines judged differently. {@link #ROUTING_DRIFT} is what that is
     * worth.
     */
    @Test
    void andAWholeTownOpensTheSameRoadsAtEitherResolution() {
        int fine = opened(new Grained(1));
        int coarse = opened(new Grained(GRAIN));
        assertTrue(fine > 0, "the fine reading opened no roads at all, so nothing is measured");
        assertTrue(coarse >= fine - ROUTING_DRIFT,
                "reading the same hillside one column in " + GRAIN + " cost the town "
                        + (fine - coarse) + " of its " + fine + " opened stretches");
    }

    /**
     * How many stretches the two towns are allowed to differ by, and why.
     *
     * <p>Two. The coarse reading is not judged only by the road gate — the
     * router reads it too, when it decides which way round a hill a street goes
     * — so the two towns lay slightly different networks, and demanding an exact
     * match would be demanding that the router ignore the ground. One stretch is
     * what that costs as measured. The fault this pins cost four, so the
     * allowance cannot swallow it.
     */
    private static final int ROUTING_DRIFT = 2;

    /** The recorded ground, reported at whatever grain the oracle would use. */
    private static final class Grained implements WorldBridge {
        private final RecordedTerrain ground =
                RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        private final int grain;

        Grained(int grain) {
            this.grain = grain;
        }

        /**
         * The one thing that differs, because it is the one thing the oracle
         * rounds. Siting, water and levelling all read {@code surfaceHeight} and
         * are left alone, so the two towns are the same town on the same ground
         * and only the road gate can tell them apart.
         */
        @Override
        public int groundHeight(SimPos pos) {
            return ground.groundAt(pos.x() - Math.floorMod(pos.x(), grain),
                    pos.z() - Math.floorMod(pos.z(), grain));
        }

        @Override
        public int groundGrain() {
            return grain;
        }

        @Override public int surfaceHeight(SimPos pos) { return ground.surfaceHeight(pos); }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean standsInWater(SimPos p, int r) { return ground.standsInWater(p, r); }
        @Override public boolean isSiteSuitable(SimPos p, int r) { return ground.isSiteSuitable(p, r); }
        @Override public int siteFault(SimPos p, int r) { return ground.siteFault(p, r); }
        @Override public boolean isSiteLevelable(SimPos p, int r) { return ground.isSiteLevelable(p, r); }
        @Override public int woodedness(SimPos c, int r) { return ground.woodedness(c, r); }

        @Override
        public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed,
                                              int facing) {
            return ground.materializeBlueprint(id, origin, surveyed, facing);
        }

        @Override public void log(String message) { }
    }

    /** Where every survey in this project puts its town. */
    private static final SimPos SITE = new SimPos(16, 64, 80);

    /** A seeded village stepped once, which is the whole of a worldgen town's life so far. */
    private static int opened(Grained bridge) {
        SimPos site = new SimPos(SITE.x(), bridge.surfaceHeight(SITE), SITE.z());
        Settlement town = Founding.seeded(site, "Wayside", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, Culture.NORMAN.id());
        town.step(new SimContext(bridge, 1, SimSettings.SANDBOX));
        return town.paths().openedCount();
    }
}
