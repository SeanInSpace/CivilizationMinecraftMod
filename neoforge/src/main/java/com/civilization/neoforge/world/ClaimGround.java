package com.civilization.neoforge.world;

/**
 * Which chunks a town's claim covers, and what reading them costs.
 *
 * <p>Arithmetic and nothing else — no level, no oracle, no generator — so the
 * bound on the one expensive thing {@link WorldgenSettlements} does can be
 * stated, tested and reasoned about without a world to run it in.
 *
 * <p>The expensive thing: a town raised on unloaded ground sites its plots
 * blind, because the terrain test answers "suitable" to every chunk nobody has
 * loaded. The cure is to generate the claim's chunks far enough to have real
 * terrain in them — as far as the carvers, so that ravines are in it; see
 * {@link TerrainOracle#readGround} — before a single plot is chosen. That is
 * worth doing and it is not free, so the cost is counted here rather than hoped
 * about.
 *
 * <p>A disc rather than the square that bounds it, which is the claim's own
 * shape and is worth about a fifth of the chunks: a claim of sixty-four blocks
 * is a hundred chunks squared and sixty-nine round.
 */
public final class ClaimGround {

    private ClaimGround() {
    }

    /** Blocks along one side of a chunk. */
    public static final int CHUNK = 16;

    /** Somewhere to send each chunk of a claim. */
    public interface Sink {
        /**
         * One chunk of the claim.
         *
         * @return true to carry on, false to stop the walk
         */
        boolean chunk(int chunkX, int chunkZ);
    }

    /**
     * Walks the chunks a claim of this radius covers, nearest the middle first.
     *
     * <p>Nearest first because a read can be cut short — by a budget, by a
     * generator that refuses — and the ground under the town's own square is
     * worth more than the ground at the edge of its claim. Every plot a village
     * program takes is inside the first ring or two.
     */
    public static void forEachChunk(int centerX, int centerZ, int radius, Sink sink) {
        int middleX = centerX >> 4;
        int middleZ = centerZ >> 4;
        int rings = (radius >> 4) + 1;
        for (int ring = 0; ring <= rings; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;   // the ring's edge only; the inside is done
                    }
                    int chunkX = middleX + dx;
                    int chunkZ = middleZ + dz;
                    if (!touchesClaim(centerX, centerZ, radius, chunkX, chunkZ)) {
                        continue;
                    }
                    if (!sink.chunk(chunkX, chunkZ)) {
                        return;
                    }
                }
            }
        }
    }

    /** How many chunks a claim of this radius covers. */
    public static int chunkCount(int centerX, int centerZ, int radius) {
        int[] counted = {0};
        forEachChunk(centerX, centerZ, radius, (x, z) -> {
            counted[0]++;
            return true;
        });
        return counted[0];
    }

    /**
     * Ticks a claim of this size takes to read at a given budget per tick.
     *
     * <p>The number the cost of this whole business comes down to. Nine spawn
     * towns of a sixty-four block claim is about six hundred and twenty chunks;
     * at eight a tick that is seventy-eight ticks, which is under four seconds of
     * world start with nobody yet in the world to feel it.
     */
    public static int ticksToRead(int centerX, int centerZ, int radius, int perTick) {
        if (perTick <= 0) {
            throw new IllegalArgumentException("a budget of " + perTick + " never finishes");
        }
        int chunks = chunkCount(centerX, centerZ, radius);
        return (chunks + perTick - 1) / perTick;
    }

    /**
     * Whether any part of this chunk lies inside the claim.
     *
     * <p>The chunk's nearest column to the center, against the radius. A chunk
     * clipped by the disc is in — the plots near a claim's edge are real plots
     * and a building sited on half-read ground is the fault this exists to fix.
     */
    private static boolean touchesClaim(int centerX, int centerZ, int radius,
                                        int chunkX, int chunkZ) {
        int lowX = chunkX << 4;
        int lowZ = chunkZ << 4;
        long dx = nearest(centerX, lowX, lowX + CHUNK - 1);
        long dz = nearest(centerZ, lowZ, lowZ + CHUNK - 1);
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    /** How far this coordinate is from the nearest point of a span. */
    private static int nearest(int at, int low, int high) {
        if (at < low) {
            return low - at;
        }
        if (at > high) {
            return at - high;
        }
        return 0;
    }
}
