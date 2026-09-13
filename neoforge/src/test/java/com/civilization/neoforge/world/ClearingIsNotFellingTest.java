package com.civilization.neoforge.world;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.Stand;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.settlement.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The town's supplies and the forester's ledger are two different books.
 *
 * <p>The fault, measured in a playtest on seed 8675309: Millbrook's 57 standing
 * trees became 4 in 218 steps, {@code trees_felled} read 962, and the camp's
 * timber swung from 1072 to 1. Nothing had gone wrong with the wood. The town was
 * clearing its own building plots, every trunk it took off one was charged to the
 * lumber camp's stand, and the clock — reading a stand it believed had been felled
 * bare — stopped crediting the camp for wood it actually had.
 *
 * <p>{@link Yield} is the one place the spoil rule is written, and it had no way
 * to tell a site crew from a forester. Now it does, and this is what the two mean.
 *
 * <p><strong>Why this runs without a world.</strong> Block tags are bound when a
 * server loads its datapacks, so every {@code state.is(BlockTags.LOGS)} is false
 * in a JUnit run — which is exactly why {@code BlueprintPlacer.spoilOf} keeps a
 * name table behind the tags, and why an oak log classifies correctly here. See
 * {@code SpoilYieldTest}, which measures where that wall stands.
 */
class ClearingIsNotFellingTest {

    private static final SimPos CENTER = new SimPos(0, 64, 0);

    /** The camp's own wood, well clear of the village, with twenty logs standing. */
    private static Settlement townWithAStand() {
        Settlement town = new Settlement(Settlement.Id.random(), "Millbrook", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.VILLAGE);
        Building camp = new Building("civilization:lumber_camp",
                new SimPos(80, 64, 0), 1, true);
        town.addBuilding(camp);
        town.setLumberArea(new WorkArea(new SimPos(80, 64, 0), 24));
        camp.setStandThousandths(20 * Stand.PER_LOG);
        return town;
    }

    private static Building campOf(Settlement town) {
        return town.buildingWithRole(
                com.civilization.sim.settlement.BuildingRole.LUMBER_CAMP);
    }

    @Test
    void aTrunkClearedOffAPlotIsTimberAndNotATreeOffTheStand() {
        Settlement town = townWithAStand();
        int was = Stand.logs(campOf(town));

        // Inside the camp's own claim, which is the case that used to be charged.
        Yield.keep(town, null, Blocks.OAK_LOG.defaultBlockState(),
                new BlockPos(84, 64, 4), Yield.Cause.CLEARING);

        assertEquals(was, Stand.logs(campOf(town)),
                "a crew clearing a plot is not the forester working his wood, and "
                        + "charging the camp for it is how a stand of 57 became 4");
        assertTrue(town.woodStock() > 0,
                "and the timber still went to the town, because it always does — "
                        + "there is no such thing as spoil");
    }

    @Test
    void aTrunkFelledForTimberInTheCampsWoodDoesComeOffTheStand() {
        Settlement town = townWithAStand();
        int was = Stand.logs(campOf(town));

        Yield.keep(town, null, Blocks.OAK_LOG.defaultBlockState(),
                new BlockPos(84, 64, 4), Yield.Cause.FELLING);

        assertEquals(was - 1, Stand.logs(campOf(town)),
                "leave a worked trunk on the books and the clock pays the town for "
                        + "the same tree again the moment everybody walks away");
    }

    @Test
    void aTreeFelledForTimberInTheVillageIsStillNobodysStand() {
        Settlement town = townWithAStand();
        int was = Stand.logs(campOf(town));

        Yield.keep(town, null, Blocks.OAK_LOG.defaultBlockState(),
                new BlockPos(CENTER.x(), CENTER.y(), CENTER.z()), Yield.Cause.FELLING);

        assertEquals(was, Stand.logs(campOf(town)),
                "a tree on a house plot in the middle of the village was never "
                        + "the forester's");
    }

    @Test
    void theStoneUnderAFloorIsNeverChargedToAnybody() {
        // The same argument one layer down, and the reason the mine has never had
        // this bug: nothing anywhere debits a Seam for a foundation.
        Settlement town = townWithAStand();
        Yield.keep(town, null, Blocks.STONE.defaultBlockState(),
                new BlockPos(84, 60, 4), Yield.Cause.CLEARING);
        assertTrue(town.stores().get(TownStores.STONE) > 0,
                "the rock out from under a floor is the town's stone");
    }
}
