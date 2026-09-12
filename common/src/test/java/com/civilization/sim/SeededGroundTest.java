package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town the world raises must be sited on ground somebody has read.
 *
 * <p>The report this exists for. A burgher village was raised at world start on
 * unloaded ground: its plots were laid by geometry, the terrain test answered
 * "suitable" to every chunk nobody had loaded, its ring road was paid for on
 * the first step, and from above it was perfect. Then the player arrived, the
 * chunks loaded, and the placement pass discovered the truth one building at a
 * time — the farm sixty blocks, then thirty more; the bunkhouse a hundred and
 * eighty blocks out and a hundred and two up; the storehouse, the inn, the
 * carpentry, the hearth and the hall after it. The ring road stayed where it
 * was drawn, circling the empty wood, and the town read as cabins scattered
 * over a hillside. On a superflat the same town is perfect, which is the whole
 * diagnosis: the fault is not the layout, it is that nothing had looked.
 *
 * <p><strong>Why the fixture is built the way it is.</strong> {@link
 * RecordedTerrain} is real rough ground and answers one question one way, so a
 * town sited against it and then stepped against it can never disagree with
 * itself — and the disagreement <em>is</em> the fault. The live bridge has two
 * answers, not one: a smooth estimate judged loosely for ground nobody has
 * read, and the real jagged ground judged strictly once somebody has. So that
 * shape is what is modelled here, over the recorded ground rather than over
 * anything invented: the estimate is the same hillside averaged out, which is
 * what a generator's noise is, and the strict answer is {@code RecordedTerrain}
 * exactly as it stands.
 */
class SeededGroundTest {

    /** Where the playtest's town stood, on the seed every survey here uses. */
    private static final SimPos MILLBROOK = new SimPos(106, 0, 180);

    private static final String BURGHER = "civilization:human/burgher";

    private static final String RADIAL = "civilization:radial_concentric";

    /** Long enough that a town that is going to come apart has come apart. */
    private static final int STEPS = 300;

    /**
     * The recorded hillside, answered the two ways the world answers it.
     *
     * <p>Unread: the ground averaged over a chunk — smooth, which is what a
     * generator's noise is, and wrong by whatever the real terrain does inside
     * that square — judged against the looser allowance the live bridge gives
     * an estimate. Read: the recorded ground itself at the strict allowance.
     * Both halves are load-bearing. A fixture with only the first cannot site a
     * town wrongly; one with only the second cannot fail to notice.
     */
    private static final class BlindThenRead implements WorldBridge {

        private final RecordedTerrain ground =
                RecordedTerrain.of(RecordedTerrain.SEED_8675309);

        /** Whether the chunks under the town have been generated and read. */
        boolean read;

        /** What an estimate is allowed to fall, matching the live bridge's eight. */
        private static final int MAX_FALL_UNSEEN = 8;

        /** How wide the ground is averaged when it is only estimated: one chunk. */
        private static final int SMOOTH = 16;

        @Override public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }

        @Override public boolean isLoaded(SimPos pos) {
            return read;
        }

        @Override public int surfaceHeight(SimPos pos) {
            // Exactly the live contract: an unloaded column keeps the y it was
            // handed, which is why everything downstream has to ask groundHeight.
            return read ? ground.surfaceHeight(pos) : pos.y();
        }

        @Override public int groundHeight(SimPos pos) {
            return read ? ground.groundHeight(pos) : estimate(pos.x(), pos.z());
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
            if (read) {
                return ground.standsInWater(pos, radius);
            }
            for (int dx = -radius; dx <= radius; dx += radius == 0 ? 1 : radius) {
                for (int dz = -radius; dz <= radius; dz += radius == 0 ? 1 : radius) {
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
            if (read) {
                return ground.siteFault(plot, radius);
            }
            if (standsInWater(plot, radius)) {
                return SITE_FAULT_OPEN_WATER;
            }
            int fall = estimatedFall(plot, radius);
            // Refused loosely and graded strictly, which is what the live bridge
            // does and for the reason written there: an estimate that charged by
            // its own loose allowance would make unread ground read better than
            // identical ground somebody had looked at.
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
            return read && ground.isSiteLevelable(plot, radius);
        }

        @Override public int woodedness(SimPos center, int radius) {
            return ground.woodedness(center, radius);
        }

        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return read ? ground.materializeBlueprint(id, origin, surveyed, facing)
                    : Footprint.UNKNOWN;
        }

        @Override public void log(String message) {
        }
    }

    /**
     * A town sited blind and then met with real ground comes apart.
     *
     * <p>The control, and the fault as reported. It is kept as a test rather
     * than deleted with the bug because the fixture is only worth anything if
     * it can still show the thing it was built to show.
     */
    @Test
    void aTownSitedBlindMovesItsBuildingsWhenTheGroundIsFinallyRead() {
        BlindThenRead world = new BlindThenRead();
        Settlement town = Founding.seeded(MILLBROOK, "Millbrook", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, BURGHER, Founding.AS_THE_STAGE_HOUSES, RADIAL);
        world.read = true;   // the player arrives and the chunks come in

        Moves moved = run(town, world);
        assertTrue(moved.count() > 0,
                "the fixture must be able to exhibit the fault it was built for");
        System.out.println("SITED BLIND: " + moved);
    }

    /**
     * A town sited on ground that was read has nothing left to discover.
     *
     * <p>Zero, and it has to be zero rather than fewer. Every move is a building
     * leaving the plan it was drawn into; a town that makes one is a town whose
     * streets no longer go anywhere.
     */
    @Test
    void aTownSitedOnGroundThatWasReadStaysWhereItWasPut() {
        BlindThenRead world = new BlindThenRead();
        world.read = true;   // the platform generated the claim before seeding
        Settlement town = Founding.seeded(MILLBROOK, "Millbrook", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, BURGHER, Founding.AS_THE_STAGE_HOUSES, RADIAL, world);
        assertTrue(town.seededGroundRead(),
                "a town handed a world to read knows it has read one");

        Moves moved = run(town, world);
        System.out.println("SITED ON READ GROUND: " + moved);
        assertEquals(0, moved.count(),
                "a town sited on ground that was read has nothing to discover: " + moved);
    }

    /**
     * And so does every town across the whole of the recorded ground.
     *
     * <p>One center proves nothing about siting: (106,180) is a gentle shelf on
     * this recording and a village laid there has one unfit plot out of fourteen,
     * where a village laid at (200,100) has fourteen out of fourteen. So the
     * claim is put to thirty centers spread across the field, and the two halves
     * are run against the same ground: blind, which is the fault, and read,
     * which is the fix.
     *
     * <p>Blind is not asserted at a number — it is terrain, and a number pinned
     * here would be a test of the recording. It is asserted to be a great many,
     * because a fix measured against nothing is not measured.
     */
    @Test
    void andSoDoesEveryTownAcrossTheRecordedGround() {
        Moves blind = sweep(false);
        Moves read = sweep(true);
        System.out.println("SWEEP blind: " + blind);
        System.out.println("SWEEP read:  " + read);
        assertTrue(blind.count() > 100,
                "thirty towns sited blind on a real hillside come apart, and this "
                        + "fixture has to be able to show it: " + blind);
        assertEquals(0, read.count(),
                "and not one of them moves a building when the ground was read "
                        + "first: " + read);
    }

    /** Every center of the recorded field, sited one way or the other. */
    private Moves sweep(boolean groundRead) {
        int moved = 0;
        long furthest = 0;
        long highest = 0;
        for (int x = -100; x <= 200; x += 60) {
            for (int z = 60; z <= 300; z += 60) {
                BlindThenRead world = new BlindThenRead();
                world.read = groundRead;
                Settlement town = Founding.seeded(new SimPos(x, 0, z), "Sweep",
                        SettlementStage.VILLAGE, BuildCatalog.DEFAULT, BURGHER,
                        Founding.AS_THE_STAGE_HOUSES, RADIAL, groundRead ? world : null);
                world.read = true;   // and then the player arrives either way
                Moves one = run(town, world, false);
                moved += one.count();
                furthest = Math.max(furthest, one.furthest());
                highest = Math.max(highest, one.highest());
            }
        }
        return new Moves(moved, furthest, highest);
    }

    /**
     * Ground the world refuses is ground the town does not build on.
     *
     * <p>The narrow claim under the wide one, put to a world that refuses a
     * stripe for no reason but that it was told to. Nothing sophisticated is
     * being tested — only that the answer is actually consulted, which is
     * precisely what was not happening.
     */
    @Test
    void aBandTheGroundRefusesGetsNothing() {
        int from = 200;
        int to = 240;
        WorldBridge refusesABand = new WorldBridge() {
            @Override public boolean playerWithin(SimPos pos, double radius) {
                return false;
            }

            @Override public boolean isLoaded(SimPos pos) {
                return true;
            }

            @Override public int surfaceHeight(SimPos pos) {
                return 64;
            }

            @Override public boolean isSiteSuitable(SimPos plot, int radius) {
                return plot.z() < from || plot.z() > to;
            }

            @Override public int siteFault(SimPos plot, int radius) {
                return isSiteSuitable(plot, radius) ? SITE_FAULT_NONE : SITE_FAULT_UNGRADED;
            }

            @Override public Footprint materializeBlueprint(
                    String id, SimPos origin, boolean surveyed, int facing) {
                return Footprint.UNKNOWN;
            }

            @Override public void log(String message) {
            }
        };

        Settlement town = Founding.seeded(MILLBROOK, "Millbrook", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, BURGHER, Founding.AS_THE_STAGE_HOUSES, RADIAL,
                refusesABand);
        for (Building standing : town.buildings()) {
            SimPos at = standing.origin();
            assertTrue(at.z() < from || at.z() > to,
                    standing.blueprintId() + " was put down at " + at
                            + ", in the band the ground refuses");
        }
        assertTrue(town.buildings().size() >= 10,
                "and the town is still a village, not three sheds: "
                        + town.buildings().size() + " buildings");
    }

    // --- the counting ---

    /** What a run did to the buildings that were standing when it began. */
    private record Moves(int count, long furthest, long highest) {
        @Override
        public String toString() {
            return count + " moved, furthest " + furthest + " blocks, "
                    + highest + " courses";
        }
    }

    private Moves run(Settlement town, WorldBridge bridge) {
        return run(town, bridge, true);
    }

    /** Steps the town and counts which of its seeded buildings left their plots. */
    private Moves run(Settlement town, WorldBridge bridge, boolean say) {
        Map<String, SimPos> was = new LinkedHashMap<>();
        int index = 0;
        for (Building standing : town.buildings()) {
            was.put((index++) + " " + standing.blueprintId(), standing.origin());
        }
        for (int step = 1; step <= STEPS; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }
        int moved = 0;
        long furthest = 0;
        long highest = 0;
        index = 0;
        for (Building standing : town.buildings()) {
            SimPos before = was.get((index++) + " " + standing.blueprintId());
            if (before == null) {
                continue;   // raised after the seeding; growth, not relocation
            }
            long away = Math.round(
                    Math.sqrt(before.horizontalDistanceSq(standing.origin())));
            if (away == 0) {
                continue;
            }
            moved++;
            furthest = Math.max(furthest, away);
            highest = Math.max(highest, Math.abs(standing.origin().y() - before.y()));
            if (say) {
                System.out.println("  " + standing.blueprintId() + " " + before + " -> "
                        + standing.origin() + " (" + away + " blocks)");
            }
        }
        return new Moves(moved, furthest, highest);
    }
}
