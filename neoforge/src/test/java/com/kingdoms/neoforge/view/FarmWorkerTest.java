package com.kingdoms.neoforge.view;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What a farmer does next.
 *
 * <p>Three lines of ordering that quietly cost a town half its field. Tending
 * used to come before planting, and a field with any growing crop in it always
 * has something to tend — so the planting branch was reached only in the
 * instant every crop was simultaneously ripe. Fields filled to whatever they
 * happened to plant early and then stopped, which read in the audit as
 * something destroying the wheat.
 */
class FarmWorkerTest {

    private static final BlockPos HARVEST = new BlockPos(1, 64, 1);
    private static final BlockPos PLANT = new BlockPos(2, 64, 2);
    private static final BlockPos TEND = new BlockPos(3, 64, 3);

    @Test
    void aRipeCropIsCutBeforeAnythingElse() {
        // Food standing out in the weather, and harvesting replants the cell in
        // the same motion — so it is strictly the best thing to be doing.
        assertSame(HARVEST, FarmWorker.nextJob(HARVEST, PLANT, TEND));
        assertSame(HARVEST, FarmWorker.nextJob(HARVEST, null, TEND));
        assertSame(HARVEST, FarmWorker.nextJob(HARVEST, PLANT, null));
    }

    @Test
    void bareSoilIsPlantedBeforeAGrowingCropIsHurried() {
        // The fix. Planting adds a cell that then grows on its own; tending only
        // hurries one that already is.
        assertSame(PLANT, FarmWorker.nextJob(null, PLANT, TEND),
                "fill the field before optimising it");
    }

    @Test
    void aFullFieldIsTended() {
        // Nothing to plant means the field is full, and then tending is exactly
        // the right thing to be doing.
        assertSame(TEND, FarmWorker.nextJob(null, null, TEND));
    }

    @Test
    void aFieldOfYoungCropsWithRoomLeftIsNeverStuck() {
        // The failing shape, stated as the test that would have caught it: a
        // field with growing crops AND bare soil must choose the bare soil.
        BlockPos chosen = FarmWorker.nextJob(null, PLANT, TEND);

        assertEquals(PLANT, chosen,
                "a field that always has something to tend must still fill its bare rows");
    }

    @Test
    void aFieldWithNothingToDoAsksForNothing() {
        assertNull(FarmWorker.nextJob(null, null, null),
                "a full field of half-grown crops needs nothing this instant");
    }
}
