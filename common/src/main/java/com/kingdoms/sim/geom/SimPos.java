package com.kingdoms.sim.geom;

/**
 * An integer block position, independent of any Minecraft type.
 *
 * <p>The simulation deals in these so that it can reason about places in
 * unloaded chunks and in dimensions that are not currently in memory. The
 * platform layer converts to and from {@code BlockPos} at the boundary.
 */
public record SimPos(int x, int y, int z) {

    public static final SimPos ORIGIN = new SimPos(0, 0, 0);

    public SimPos offset(int dx, int dy, int dz) {
        return new SimPos(x + dx, y + dy, z + dz);
    }

    /** Squared distance, ignoring Y. Settlements are mostly a 2D problem. */
    public long horizontalDistanceSq(SimPos other) {
        long dx = (long) x - other.x;
        long dz = (long) z - other.z;
        return dx * dx + dz * dz;
    }

    public double horizontalDistance(SimPos other) {
        return Math.sqrt(horizontalDistanceSq(other));
    }

    /** Chunk coordinates, useful for territory claims. */
    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
