package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildPlanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What it costs to level a plot, and who is allowed to decide.
 *
 * <p>Refusing uneven ground was the only answer siting had, and it is what made
 * a town smaller the better its rules became: plots pushed to the outskirts,
 * doors stranded from streets, quarters given up for a dip a few barrows of
 * earth would fill. A settlement does not walk away from a hummock.
 *
 * <p><strong>What is not tested here, and should be.</strong> A whole town that
 * actually levels a plot and builds on it. On the recorded ground of a real
 * world the path never fires — siting finds flat ground first, which is correct
 * and also means the decision below is, today, reachable rather than reached. An
 * attempt to force it with ground that refuses every site turned into a fight
 * with the relocation machinery and tested nothing about leveling. The honest
 * state is: the arithmetic and the seam are covered, the end-to-end is not.
 */
class LevelingTest {

    /** Ground that falls away by a fixed amount across the plot. */
    private static WorldBridge dippingBy(int fall) {
        return new WorldBridge() {
            @Override
            public int surfaceHeight(SimPos pos) {
                // A bowl: the middle sits low, the rim stands at the top.
                boolean inTheDip = Math.abs(pos.x()) <= 4 && Math.abs(pos.z()) <= 4;
                return 72 - (inTheDip ? fall : 0);
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
            public boolean standsInWater(SimPos pos, int radius) {
                return false;
            }

            @Override
            public com.kingdoms.sim.settlement.Footprint materializeBlueprint(
                    String id, SimPos origin, boolean surveyed, int facing) {
                return com.kingdoms.sim.settlement.Footprint.UNKNOWN;
            }

            @Override
            public int woodedness(SimPos center, int radius) {
                return 0;
            }

            @Override
            public void log(String message) {
            }
        };
    }

    @Test
    void aDipCostsTheEarthItTakesToFillIt() {
        // The price is the hole, not the plot: a shallow dip is cheap and a deep
        // one is not, so a town's decision to level scales with the work.
        int shallow = BuildPlanner.earthToLevel(
                new SimPos(0, 72, 0), 11, dippingBy(1));
        int deep = BuildPlanner.earthToLevel(
                new SimPos(0, 72, 0), 11, dippingBy(4));
        assertTrue(shallow > 0, "a dip cost nothing to fill");
        assertTrue(deep > shallow,
                "a four-course dip (" + deep + ") cost no more than a one-course dip ("
                        + shallow + ")");
    }

    @Test
    void levelGroundCostsNothing() {
        assertEquals(0, BuildPlanner.earthToLevel(
                new SimPos(0, 72, 0), 11, dippingBy(0)),
                "flat ground was charged for leveling");
    }

    @Test
    void theFallIsMeasuredOnTheBulkNotTheExtremes() {
        // The same measure siting uses, so a single boulder does not condemn a
        // shelf and the two rules cannot disagree about what "uneven" means.
        assertEquals(0, BuildPlanner.fallAcross(new SimPos(0, 72, 0), 11, dippingBy(0)));
        assertTrue(BuildPlanner.fallAcross(new SimPos(0, 72, 0), 11, dippingBy(6)) > 0,
                "a six-course bowl read as level ground");
    }

    @Test
    void groundDecidesWhetherItCanBeLeveled() {
        // The seam, and the reason it exists. isSiteSuitable says no without
        // saying why, and the reasons are not alike: a lake cannot be filled
        // with a barrow of earth and a hummock can. The first version of this
        // had the simulation infer "not wet, therefore levelable" and promptly
        // put a house in a lake, because the ground it was testing against
        // reported water through isSiteSuitable and nothing else.
        //
        // So the default is NO. A bridge that has not thought about leveling
        // keeps exactly the behavior it had.
        WorldBridge silent = dippingBy(2);
        assertFalse(silent.isSiteLevelable(new SimPos(0, 72, 0), 6),
                "a bridge that never considered leveling licensed it anyway");

        RecordedTerrain real = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        boolean anyLevelable = false;
        boolean anyRefused = false;
        for (int x = -100; x <= 100; x += 8) {
            for (int z = -100; z <= 100; z += 8) {
                SimPos at = new SimPos(16 + x, 64, 80 + z);
                if (!real.isSiteSuitable(at, 6)) {
                    anyRefused = true;
                    if (real.isSiteLevelable(at, 6)) {
                        anyLevelable = true;
                    }
                }
            }
        }
        assertTrue(anyRefused, "the recorded ground refused nothing, so it is too smooth");
        assertTrue(anyLevelable,
                "no refused site on real ground was a dip worth leveling, which"
                        + " would make the whole workstream pointless there");
    }
}
