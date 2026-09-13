package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.Grade;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What actually happens to a seeded town on the step somebody walks up to it.
 *
 * <p>Two reports, measured here because {@link SeededGroundTest} cannot show
 * either of them and says so: its ground is either wholly estimated or wholly
 * read, and on the read half nothing moves at all. A real world is neither. The
 * platform reads the ground under the claim before the town is raised — sixty-four
 * blocks of it, {@code Founding.INITIAL_CLAIM}, which is what
 * {@code WorldgenSettlements.readClaim} asks for — and a village's plan reaches
 * well past that. So the inner plots are sited against ground somebody has read
 * and judged strictly, and the outer ones against the generator's noise judged
 * loosely; then the player arrives, the whole claim loads, and the outer ones are
 * re-judged against real terrain. That is the shape modeled here, and it is where
 * the last four moves of fourteen come from.
 *
 * <p>What is counted:
 *
 * <ul>
 *   <li><strong>Moves on arrival</strong> — a building leaving the plot it was
 *       drawn into. The report: "four of fourteen seeded buildings still move on
 *       arrival, 32-47 blocks."</li>
 *   <li><strong>Swaps</strong> — a building moving onto a site another building in
 *       the same town was refused at. The report: "the carpentry was refused at
 *       (115,84,279) and moved to (128,80,244) — the market's old site — and the
 *       market was refused at (147,69,277) and moved to (115,84,279), the site
 *       refused for the carpentry a step earlier."</li>
 *   <li><strong>Auditor faults</strong> — buried, perched, or no way in, by the
 *       geometry in {@link Grade}, which is the auditor's own. The report:
 *       "Millbrook's hearth, lumber camp and mine buried, the ground standing up
 *       to 3 above the floor on every side."</li>
 * </ul>
 */
class SeededArrivalTest {

    /** Where the playtest's town stood, on the seed every survey here uses. */
    private static final SimPos MILLBROOK = new SimPos(106, 0, 180);

    private static final String BURGHER = "civilization:human/burgher";

    private static final String RADIAL = "civilization:radial_concentric";

    /** Long enough that a town that is going to rearrange itself has done it. */
    private static final int STEPS = 120;

    /**
     * How far out the ground is read once the player is properly there.
     *
     * <p>Past the furthest plot any seeded village has, so "arrived" means the
     * whole town is standing on ground somebody has looked at.
     */
    private static final int WHOLE_CLAIM = 256;

    /**
     * The recorded hillside, answered the way a live world answers it: read
     * inside the claim, estimated outside it, and wholly read once somebody is
     * standing in the town.
     *
     * <p>Every clause here is the live bridge's, not an invention. An unread
     * column answers from the generator's noise, which is the real ground
     * averaged over a chunk. An unread plot is refused only past
     * {@code MAX_SLOPE_UNSEEN} — eight — because an estimate is coarse and
     * refusing on it is expensive to get wrong; a read plot is refused past the
     * strict allowance. And the fault an unread plot is <em>scored</em> at is
     * measured against the strict allowance even so, because charging unread
     * ground by the loose one would make it read better than identical ground
     * somebody had looked at.
     */
    private static final class ReadTheClaim implements WorldBridge {

        private final RecordedTerrain ground =
                RecordedTerrain.of(RecordedTerrain.SEED_8675309);

        /** The middle of the claim whose chunks were generated before seeding. */
        private final SimPos claim;

        /** How far out the ground was read. Beyond it, the generator's noise. */
        private int readWithin;

        /** Whether somebody is now standing in the town, so everything is read. */
        boolean arrived;

        /**
         * Walks the horizon out by a chunk, which is what arriving actually is.
         *
         * <p>The difference between this and a flag is the whole of the swap
         * report. Flip everything to read at once and a building that moves, moves
         * onto ground the town has just judged for real, so it moves once and
         * stops. A player walks in: the chunks come over several steps, so a
         * building moves onto a plot that is <em>still</em> only estimated, that
         * plot loads a step later and is refused, and the building moves again —
         * onto the plot the last one just left, which is how the carpentry and the
         * market came to trade sites twice.
         */
        void walkIn() {
            readWithin += SMOOTH;
        }

        int horizon() {
            return readWithin;
        }

        private static final int MAX_FALL_UNSEEN = 8;

        /** How wide the ground is averaged when it is only estimated: one chunk. */
        private static final int SMOOTH = 16;

        ReadTheClaim(SimPos claim, int readWithin) {
            this.claim = claim;
            this.readWithin = readWithin;
        }

        private boolean read(int x, int z) {
            return arrived || Math.max(Math.abs(x - claim.x()), Math.abs(z - claim.z()))
                    <= readWithin;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }

        @Override public boolean isLoaded(SimPos pos) {
            return read(pos.x(), pos.z());
        }

        @Override public int surfaceHeight(SimPos pos) {
            return read(pos.x(), pos.z()) ? ground.surfaceHeight(pos) : pos.y();
        }

        @Override public int groundHeight(SimPos pos) {
            return read(pos.x(), pos.z()) ? ground.groundAt(pos.x(), pos.z())
                    : estimate(pos.x(), pos.z());
        }

        /** The hillside averaged over a chunk — a generator's-eye view of it. */
        private int estimate(int x, int z) {
            int total = 0;
            int samples = 0;
            for (int dx = 0; dx < SMOOTH; dx += 4) {
                for (int dz = 0; dz < SMOOTH; dz += 4) {
                    total += ground.groundAt(x - Math.floorMod(x, SMOOTH) + dx,
                            z - Math.floorMod(z, SMOOTH) + dz);
                    samples++;
                }
            }
            return total / samples;
        }

        @Override public boolean standsInWater(SimPos pos, int radius) {
            if (read(pos.x(), pos.z())) {
                return ground.standsInWater(pos, radius);
            }
            for (int dx = -radius; dx <= radius; dx += Math.max(1, radius)) {
                for (int dz = -radius; dz <= radius; dz += Math.max(1, radius)) {
                    if (estimate(pos.x() + dx, pos.z() + dz) < ground.seaLevel()) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override public boolean isSiteSuitable(SimPos plot, int radius) {
            return siteFault(plot, radius) == SITE_FAULT_NONE;
        }

        @Override public int siteFault(SimPos plot, int radius) {
            if (read(plot.x(), plot.z())) {
                return ground.siteFault(plot, radius);
            }
            if (standsInWater(plot, radius)) {
                return SITE_FAULT_OPEN_WATER;
            }
            int fall = estimatedFall(plot, radius);
            return fall <= MAX_FALL_UNSEEN ? SITE_FAULT_NONE
                    : fall - RecordedTerrain.MAX_FALL;
        }

        private int estimatedFall(SimPos plot, int radius) {
            List<Integer> heights = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx += 3) {
                for (int dz = -radius; dz <= radius; dz += 3) {
                    heights.add(estimate(plot.x() + dx, plot.z() + dz));
                }
            }
            Collections.sort(heights);
            return heights.get((heights.size() * 4) / 5) - heights.get(heights.size() / 5);
        }

        @Override public boolean isSiteLevelable(SimPos plot, int radius) {
            return read(plot.x(), plot.z()) && ground.isSiteLevelable(plot, radius);
        }

        @Override public int woodedness(SimPos center, int radius) {
            return ground.woodedness(center, radius);
        }

        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return read(origin.x(), origin.z())
                    ? ground.materializeBlueprint(id, origin, surveyed, facing)
                    : Footprint.UNKNOWN;
        }

        @Override public void log(String message) {
        }
    }

    // --- the measurements ---

    /**
     * Millbrook, on the seed and at the center the report was written from.
     *
     * <p>Printed rather than only asserted, because the numbers are the point of
     * the test: a fix here is a change to them.
     */
    @Test
    void millbrookArrivesWithoutRearrangingItself() {
        Arrival arrival = arriveAt(MILLBROOK, true);
        System.out.println("MILLBROOK ON ARRIVAL: " + arrival);
        assertEquals(0, arrival.swaps(),
                "no building may take a site another was refused at: " + arrival);
        assertTrue(arrival.movedTwice() == 0,
                "a building that has moved once and is refused again stays and is cut "
                        + "into the ground: " + arrival);
    }

    /**
     * And the same across the whole recorded field.
     *
     * <p>One center proves nothing about siting — (106,180) is a gentle shelf on
     * this recording — so the claim is put to thirty centers spread across it.
     */
    @Test
    void andSoDoesEveryTownAcrossTheRecordedGround() {
        Arrival total = sweep();
        System.out.println("SWEEP ON ARRIVAL: " + total);
        assertEquals(0, total.swaps(),
                "no building may take a site another was refused at: " + total);
        assertEquals(0, total.movedTwice(),
                "and none may move twice: " + total);
    }

    /**
     * What the auditor would say about a seeded town's buildings when it is drawn.
     *
     * <p>The geometry is {@link Grade}'s, which is the auditor's own — see its
     * javadoc for why one copy of it exists rather than two.
     */
    @Test
    void nothingSeededIsBuriedOrOpensOntoAir() {
        int faults = 0;
        int buildings = 0;
        List<String> worst = new ArrayList<>();
        for (int x = -100; x <= 200; x += 60) {
            for (int z = 60; z <= 300; z += 60) {
                SimPos center = new SimPos(x, 0, z);
                ReadTheClaim world = new ReadTheClaim(center, readTo(center));
                Settlement town = seed(center, world);
                world.arrived = true;
                for (Building standing : town.buildings()) {
                    buildings++;
                    Grade.Shelf verdict = shelfOf(world, town, standing);
                    if (verdict != Grade.Shelf.LEVEL) {
                        faults++;
                        if (worst.size() < 6) {
                            worst.add(standing.blueprintId() + " at " + standing.origin()
                                    + " " + verdict);
                        }
                    }
                }
            }
        }
        System.out.println("AUDITOR GEOMETRY: " + faults + " faults over " + buildings
                + " seeded buildings; first few " + worst);
        assertEquals(0, faults,
                "a seeded building must stand on ground its own placer can make level: "
                        + worst);
    }

    private static Grade.Shelf shelfOf(WorldBridge world, Settlement town, Building of) {
        int span = BuildPlanner.plotSpanOf(of.blueprintId(), town.catalog());
        return Grade.shelf(world, of.origin(), span, Grade.isField(of.blueprintId()));
    }

    // --- running one town ---

    /**
     * How far the ground is read before the town is raised.
     *
     * <p>The platform's own answer, asked the way the platform asks it — see
     * {@code WorldgenSettlements.readClaim}. Pinning a number here instead would
     * make this fixture a test of the number rather than of the siting.
     */
    private static int readTo(SimPos center) {
        return Founding.groundToRead(center, RADIAL, BuildCatalog.DEFAULT);
    }

    private static Settlement seed(SimPos center, WorldBridge world) {
        return Founding.seeded(center, "Millbrook", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, BURGHER, Founding.AS_THE_STAGE_HOUSES, RADIAL,
                world);
    }

    /** What one town's arrival did to the buildings that were standing for it. */
    private record Arrival(int buildings, int moved, int movedTwice, int swaps,
                           long furthest) {

        Arrival plus(Arrival other) {
            return new Arrival(buildings + other.buildings, moved + other.moved,
                    movedTwice + other.movedTwice, swaps + other.swaps,
                    Math.max(furthest, other.furthest));
        }

        @Override
        public String toString() {
            return moved + " of " + buildings + " moved (" + movedTwice
                    + " twice), " + swaps + " swaps, furthest " + furthest + " blocks";
        }
    }

    private Arrival sweep() {
        Arrival total = new Arrival(0, 0, 0, 0, 0);
        for (int x = -100; x <= 200; x += 60) {
            for (int z = 60; z <= 300; z += 60) {
                total = total.plus(arriveAt(new SimPos(x, 0, z), false));
            }
        }
        return total;
    }

    /**
     * Seeds a town against a read claim, walks up to it, and counts.
     *
     * <p>A "refused site" is a column a building was standing on and left. That
     * is the report's own sense of the word: the carpentry was refused at
     * (115,84,279) means the carpentry was there and the town judged the ground
     * unfit for it.
     */
    private Arrival arriveAt(SimPos center, boolean say) {
        ReadTheClaim world = new ReadTheClaim(center, readTo(center));
        Settlement town = seed(center, world);

        Map<String, SimPos> was = new LinkedHashMap<>();
        Map<String, SimPos> at = new LinkedHashMap<>();
        Map<String, Integer> moves = new LinkedHashMap<>();
        int index = 0;
        for (Building standing : town.buildings()) {
            String who = (index++) + " " + standing.blueprintId();
            was.put(who, standing.origin());
            at.put(who, standing.origin());
            moves.put(who, 0);
        }
        Set<String> refused = new LinkedHashSet<>();
        int swaps = 0;
        for (int step = 1; step <= STEPS; step++) {
            if (world.horizon() < WHOLE_CLAIM) {
                world.walkIn();
            } else {
                world.arrived = true;
            }
            town.step(new SimContext(world, step, SimSettings.SANDBOX));
            index = 0;
            for (Building standing : town.buildings()) {
                String who = (index++) + " " + standing.blueprintId();
                SimPos before = at.get(who);
                if (before == null || sameColumn(before, standing.origin())) {
                    continue;
                }
                if (refused.contains(column(standing.origin()))) {
                    swaps++;
                    if (say) {
                        System.out.println("  SWAP: " + standing.blueprintId()
                                + " took " + standing.origin()
                                + ", a site this town had already refused");
                    }
                }
                refused.add(column(before));
                at.put(who, standing.origin());
                moves.put(who, moves.get(who) + 1);
                if (say) {
                    System.out.println("  " + standing.blueprintId() + " " + before
                            + " -> " + standing.origin());
                }
            }
        }
        int moved = 0;
        int twice = 0;
        long furthest = 0;
        for (Map.Entry<String, Integer> entry : moves.entrySet()) {
            if (entry.getValue() == 0) {
                continue;
            }
            moved++;
            if (entry.getValue() > 1) {
                twice++;
            }
            furthest = Math.max(furthest, Math.round(Math.sqrt(
                    was.get(entry.getKey()).horizontalDistanceSq(at.get(entry.getKey())))));
        }
        return new Arrival(was.size(), moved, twice, swaps, furthest);
    }

    private static boolean sameColumn(SimPos a, SimPos b) {
        return a.x() == b.x() && a.z() == b.z();
    }

    private static String column(SimPos at) {
        return at.x() + "," + at.z();
    }
}
