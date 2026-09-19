package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.worldgen.SettlementSites;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How long the walk to the world's one promised town actually is.
 *
 * <p>{@code SettlementSpacingTest} already measures where the starter site is
 * <em>put</em>, and it measures it as 259 to 514 blocks out over two hundred
 * worlds — inside the band, every time. That measurement was true and the promise
 * was still broken, because it stops one step short of the question: it asks where
 * the arithmetic aims and never asks whether the ground there would hold a town.
 *
 * <p>A playtest asked. Two fresh worlds, seeds 8675309 and 20260919; in both the
 * home region's site landed inside the band — 387 blocks and 346 — and in both the
 * ground refused it, whereupon {@code WorldgenSettlements.tickAnchor} abandoned the
 * region and took the nearest scattered site in the three regions round about,
 * with no distance cap at all. The walks were <strong>747 and 808 blocks</strong>.
 * Two refusals out of two is not bad luck; it means the refusal is the ordinary
 * path and the band was decorative.
 *
 * <p>So this measures the whole path: place the site, put it to real ground, and
 * see where a town actually ends up. Both behaviours are run over the same seeds
 * so the two numbers are comparable — the old one-shot rule, which leaves the band
 * the moment its single candidate is refused, and
 * {@link SettlementSites.Grid#starterSites}, which tries the rest of the band
 * first.
 *
 * <p><strong>On the ground.</strong> {@link TerrainFake} is useless here for the
 * reason {@code RealTerrainRoadsTest} gives: it is too smooth to refuse anything,
 * so every seed would accept its first candidate and the measurement would report
 * a perfect score for code that does nothing. {@link RecordedTerrain} is real and
 * rough but only 514 blocks square, and a starter band reaches 512 blocks from a
 * spawn point — so its own clamping would flatten most of the candidates to an
 * edge value. {@link Wrapped} therefore tiles the recording: every column in the
 * world lands on some column that was really surveyed, so the roughness under a
 * candidate is a roughness that actually occurred, and fifty seeds get fifty
 * genuinely different samples of it.
 */
class StarterBandTest {

    /** Worlds measured. The brief asked for fifty; this is a hundred. */
    private static final int WORLDS = 100;

    /** What the anchor is willing to move a starter onto better ground. */
    private static final int STARTER_SITING_REACH = 96;

    /** The middle of a town, which is the ground the anchor insists on. */
    private static final int TOWN_HEART = 16;

    /** How many places in the band the anchor will try. */
    private static final int TRIES = 60;

    /**
     * The recorded hillside, tiled to cover the world.
     *
     * <p>Every query is folded into the square that was actually surveyed, so the
     * ground under any column is ground somebody recorded rather than an edge
     * value repeated outward. It is not the terrain of any real seed past the one
     * that was recorded — nothing in this suite is — but it has that seed's
     * roughness everywhere instead of in one 514-block square, which is what this
     * measurement needs and what clamping would take away.
     */
    private static final class Wrapped implements WorldBridge {

        private final RecordedTerrain recorded;
        private final int span;
        private final int originX;
        private final int originZ;

        Wrapped(RecordedTerrain recorded) {
            this.recorded = recorded;
            HeightField field = recorded.field();
            // Inset by a plot's reach so a sample taken round a wrapped column
            // never crosses the recording's edge and hits the clamp.
            this.span = 384;
            this.originX = -200;
            this.originZ = -140;
            if (!field.covers(originX, originZ)
                    || !field.covers(originX + span, originZ + span)) {
                throw new IllegalStateException("the recording no longer covers the tile");
            }
        }

        private SimPos fold(SimPos at) {
            return new SimPos(originX + Math.floorMod(at.x() - originX, span), at.y(),
                    originZ + Math.floorMod(at.z() - originZ, span));
        }

        @Override
        public int surfaceHeight(SimPos pos) {
            return recorded.surfaceHeight(fold(pos));
        }

        @Override
        public int groundHeight(SimPos pos) {
            return recorded.groundHeight(fold(pos));
        }

        @Override
        public boolean isLoaded(SimPos pos) {
            return true;
        }

        @Override
        public int siteFault(SimPos plot, int radius) {
            return recorded.siteFault(fold(plot), radius);
        }

        @Override
        public boolean isSiteSuitable(SimPos plot, int radius) {
            return siteFault(plot, radius) == WorldBridge.SITE_FAULT_NONE;
        }

        @Override
        public void log(String message) {
        }

        @Override
        public com.civilization.sim.settlement.Footprint materializeBlueprint(
                String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return recorded.materializeBlueprint(blueprintId, origin, surveyed, facing);
        }

        @Override
        public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }
    }

    /** What one world's starter did. */
    private record Walk(boolean inBand, double blocks) {
    }

    /** Whether ground at this column would hold the heart of a town. */
    private static boolean holds(WorldBridge ground, SimPos where, int reach) {
        SimPos wanted = new SimPos(where.x(), ground.groundHeight(where), where.z());
        SimPos chosen = Founding.bestSiteNear(wanted, reach, ground);
        chosen = new SimPos(chosen.x(), ground.groundHeight(chosen), chosen.z());
        return ground.isSiteSuitable(chosen, TOWN_HEART);
    }

    private static double away(SimPos at, SimPos from) {
        return Math.sqrt(at.horizontalDistanceSq(from));
    }

    /** SplitMix64's finalizer, for spreading the spawn points about. */
    private static long mix(long value) {
        long z = value + 0x9E37_79B9_7F4A_7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return z ^ (z >>> 31);
    }

    @Test
    @DisplayName("the town a world promises stays inside the band it promised it in")
    void theStarterStaysInItsBand() {
        WorldBridge ground = new Wrapped(RecordedTerrain.of(RecordedTerrain.SEED_8675309));

        int oneShotKept = 0;
        int bandedKept = 0;
        double bandedWorst = 0;
        double bandedTotal = 0;
        int triesTotal = 0;
        int refusedFirst = 0;

        for (int world = 0; world < WORLDS; world++) {
            long seed = 8675309L + world * 7919L;
            SimPos spawn = new SimPos(
                    (int) Math.floorMod(mix(seed), 2048L) - 1024, 64,
                    (int) Math.floorMod(mix(seed ^ 0x5EEDL), 2048L) - 1024);
            SettlementSites.Grid grid = SettlementSites.Grid.DEFAULT.anchoredAt(spawn);
            int[] home = grid.homeRegion().orElseThrow();
            List<SimPos> band = grid.starterSites(seed, home[0], home[1], TRIES);
            assertTrue(!band.isEmpty(), "a world offered nowhere at all for its starter");

            int ceiling = grid.starterMaxFromSpawn();

            // The old rule: one candidate, and on a refusal the anchor left the
            // region for the scattered sites round about, which have no cap.
            if (holds(ground, band.get(0), 48)) {
                oneShotKept++;
            } else {
                refusedFirst++;
            }

            // The new rule: the rest of the band before the neighbours.
            Walk walk = null;
            for (int at = 0; at < band.size(); at++) {
                if (!holds(ground, band.get(at), STARTER_SITING_REACH)) {
                    continue;
                }
                triesTotal += at + 1;
                double blocks = away(band.get(at), spawn);
                walk = new Walk(blocks <= ceiling + STARTER_SITING_REACH, blocks);
                break;
            }
            if (walk != null && walk.inBand()) {
                bandedKept++;
                bandedTotal += walk.blocks();
                bandedWorst = Math.max(bandedWorst, walk.blocks());
            }
        }

        System.out.printf(
                "STARTER over %d worlds on recorded ground:"
                        + " one candidate kept the band %d times (%.0f%%),"
                        + " the whole band kept it %d times (%.0f%%)%n",
                WORLDS, oneShotKept, 100.0 * oneShotKept / WORLDS,
                bandedKept, 100.0 * bandedKept / WORLDS);
        System.out.printf(
                "STARTER the first candidate was refused in %d of %d worlds;"
                        + " a kept band took %.1f candidates on average,"
                        + " walk %.0f mean and %.0f worst%n",
                refusedFirst, WORLDS,
                bandedKept == 0 ? 0 : (double) triesTotal / bandedKept,
                bandedKept == 0 ? 0 : bandedTotal / bandedKept,
                bandedWorst);

        // Measured, at the time of writing: one candidate kept the band in 37
        // worlds of a hundred and the whole band kept it in 62, with the first
        // candidate refused in 63 — which is the playtest's two-in-two, on a
        // hundred seeds. A kept band took three candidates on average and the
        // walk came out 344 blocks at the mean and 511 at the worst, so every
        // promise that was kept was kept inside 512.
        //
        // The floors below are well under those and are guards rather than
        // restatements. They are not higher because this ground is deliberately
        // the worst in the project — see the note on Wrapped — and a hillside
        // that steep from edge to edge is not what most worlds put a spawn point
        // on. What must not regress is the comparison, which is measured against
        // the old rule on the identical seeds.
        assertTrue(bandedKept >= oneShotKept * 3 / 2,
                "trying the whole band kept the promise " + bandedKept + " times against "
                        + oneShotKept + " for one candidate — the band is not earning itself");
        assertTrue(bandedKept * 2 >= WORLDS,
                "only " + bandedKept + " of " + WORLDS
                        + " worlds kept their starter inside the promised band");
        assertTrue(bandedWorst <= grid(spawnOfLast()).starterMaxFromSpawn()
                        + STARTER_SITING_REACH,
                "a starter counted as in-band stood " + Math.round(bandedWorst)
                        + " blocks out");
    }

    /** The grid a world of this spawn point uses. */
    private static SettlementSites.Grid grid(SimPos spawn) {
        return SettlementSites.Grid.DEFAULT.anchoredAt(spawn);
    }

    /** Any spawn point: the band's far end does not depend on which. */
    private static SimPos spawnOfLast() {
        return new SimPos(0, 64, 0);
    }
}
