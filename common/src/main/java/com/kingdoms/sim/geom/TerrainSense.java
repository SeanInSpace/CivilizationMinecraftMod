package com.kingdoms.sim.geom;

/**
 * What the ground is doing, for code that has to route across it.
 *
 * <p>Narrower than a {@code WorldBridge} on purpose. A router wants two facts
 * about a column and nothing else, and handing it the whole bridge would let it
 * reach for buildings, players and blueprints — none of which are its business,
 * and all of which would make it impossible to test on a hillside somebody drew
 * by hand.
 *
 * <p>It is also the seam a {@code Layout} would need if plans are ever to be
 * drawn with the ground in mind rather than corrected against it afterwards.
 * That is not done here — the plan stays a flat drawing and the road bends
 * within a corridor around it — but the interface a terrain-aware plan would
 * take is this one, so adopting it later costs nothing.
 */
public interface TerrainSense {

    /** The surface height of this column. */
    int heightAt(int x, int z);

    /** Whether this column stands under water. */
    boolean wetAt(int x, int z);

    /** A table top, for callers with no ground to consult. */
    TerrainSense FLAT = new TerrainSense() {
        @Override
        public int heightAt(int x, int z) {
            return 0;
        }

        @Override
        public boolean wetAt(int x, int z) {
            return false;
        }
    };
}
