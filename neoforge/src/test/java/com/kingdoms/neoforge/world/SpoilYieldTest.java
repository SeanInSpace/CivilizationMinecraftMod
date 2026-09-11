package com.kingdoms.neoforge.world;

import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.work.Spoil;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What clearing a site is actually worth, from the plan to the ledger.
 *
 * <p>Two halves of one rule meet here. {@link Overgrowth} decides which cells
 * have to come off a plot; {@link BlueprintPlacer#spoilOf} says what each of
 * them is; {@code Spoil} says what that comes to in the town's four columns.
 * Until yesterday the answer was "nothing" — a plot's trees were called spoil
 * and destroyed, on the argument that a town should have to raise a lumber camp
 * before it had timber. A player watching six oaks vanish to make room for a
 * cottage said otherwise, and was right: the camp is what makes timber grow
 * back, not what makes a felled tree real.
 *
 * <p><strong>Why this can run without a world.</strong> Block tags are bound
 * when a server loads its datapacks and a JUnit run has no server, so every
 * {@code state.is(BlockTags.LOGS)} here is false — which is exactly why the
 * classifier has a name table behind the tags, and exactly why this is the test
 * that can prove the name table right. The registry <em>names</em> are
 * available; {@code StoreChestBlockEntityTest} measures where that wall stands.
 * Geometry comes through {@link Overgrowth.Sky} in {@link Overgrowth.Cover},
 * the same fake-sky trick {@code OvergrowthPlanTest} uses.
 */
class SpoilYieldTest {

    private static final int FLOOR = 64;

    /** A sky you stack cell by cell, as {@code OvergrowthPlanTest} does. */
    private static final class FakeSky implements Overgrowth.Sky {

        private final Map<BlockPos, Overgrowth.Cover> cells = new HashMap<>();

        FakeSky put(int x, int y, int z, Overgrowth.Cover cover) {
            cells.put(new BlockPos(x, y, z), cover);
            return this;
        }

        FakeSky ground(int reach) {
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    put(x, FLOOR, z, Overgrowth.Cover.KEEP);
                }
            }
            return this;
        }

        /** A trunk standing in one column, from the floor up. */
        FakeSky trunk(int x, int z, int height) {
            for (int dy = 1; dy <= height; dy++) {
                put(x, FLOOR + dy, z, Overgrowth.Cover.LOG);
            }
            return this;
        }

        /** A mound of earth standing over the floor line in one column. */
        FakeSky hill(int x, int z, int height) {
            for (int dy = 1; dy <= height; dy++) {
                put(x, FLOOR + dy, z, Overgrowth.Cover.GROUND);
            }
            return this;
        }

        /** Leaves across a square at one height. */
        FakeSky canopy(int y, int reach) {
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    put(x, y, z, Overgrowth.Cover.LEAF);
                }
            }
            return this;
        }

        @Override
        public Overgrowth.Cover at(int x, int y, int z) {
            return cells.getOrDefault(new BlockPos(x, y, z), Overgrowth.Cover.NONE);
        }

        @Override
        public int topOf(int x, int z) {
            int top = FLOOR;
            for (Map.Entry<BlockPos, Overgrowth.Cover> cell : cells.entrySet()) {
                if (cell.getKey().getX() == x && cell.getKey().getZ() == z) {
                    top = Math.max(top, cell.getKey().getY());
                }
            }
            return top + 1;
        }
    }

    /** A 7x7 building with its one-block doorstep ring: a 9x9 plot. */
    private static Footprint plot() {
        return new Footprint(FLOOR, 9, 9, 5);
    }

    /**
     * What the crew would be holding, having dug this plan out of this sky.
     *
     * <p>The sky says what kind of cell each one is and this turns that back
     * into the block a crew would actually meet, which is what the classifier
     * takes. The pairing is the whole point: the plan and the ledger have to be
     * reading the same plot.
     */
    private static Map<String, Integer> yieldOf(FakeSky sky, Overgrowth.Clearing clearing) {
        List<Spoil.Kind> broken = new ArrayList<>();
        for (BlockPos dug : clearing.dug()) {
            broken.add(BlueprintPlacer.spoilOf(
                    blockFor(sky.at(dug.getX(), dug.getY(), dug.getZ()))));
        }
        return Spoil.tally(broken);
    }

    private static BlockState blockFor(Overgrowth.Cover cover) {
        return switch (cover) {
            case LOG -> Blocks.OAK_LOG.defaultBlockState();
            case GROUND -> Blocks.DIRT.defaultBlockState();
            case LEAF -> Blocks.OAK_LEAVES.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    // --- a plot, end to end ---

    @Test
    void twoTrunksAndAHillUnderThemComeToTimberAndEarth() {
        // The plot the complaint was about: trees standing on it and ground
        // humped up under where the floor goes. Two six-log trunks inside the
        // footprint, and a four-course mound in one corner of it.
        FakeSky site = new FakeSky().ground(8)
                .trunk(2, 2, 6)
                .trunk(-2, -1, 6)
                .hill(3, -3, 4);

        Overgrowth.Clearing clearing = Overgrowth.overPlot(
                site, new BlockPos(0, FLOOR, 0), plot(), Overgrowth.NOTHING_SPARED);
        Map<String, Integer> got = yieldOf(site, clearing);

        assertEquals(12, got.get(TownStores.WOOD),
                "two six-log trunks off a house plot are twelve timber, not spoil");
        assertEquals(4, got.get(TownStores.EARTH),
                "and the mound dug out from under the floor is four earth");
        assertEquals(2, got.size(), "nothing else was worth anything");
    }

    @Test
    void theCanopyOverTheRoofIsStillWorthNothing() {
        // The one deliberate exception to "a broken block is worth something",
        // and it is not an exception to the rule so much as a fact about leaves:
        // vanilla pays a player sticks and the odd sapling for a crown, and the
        // town has no column for either. The canopy is also never dug -- nobody
        // can stand eight blocks up -- so it never reaches a pair of hands at
        // all. Both halves are asserted, because either alone would pass by
        // accident.
        FakeSky wood = new FakeSky().ground(8).canopy(FLOOR + 8, 8);

        Overgrowth.Clearing clearing = Overgrowth.overPlot(
                wood, new BlockPos(0, FLOOR, 0), plot(), Overgrowth.NOTHING_SPARED);

        assertTrue(clearing.dug().isEmpty(), "nobody was sent up for a leaf");
        assertFalse(clearing.stripped().isEmpty(), "and the canopy is still taken");
        assertEquals(Spoil.Kind.NOTHING,
                BlueprintPlacer.spoilOf(Blocks.OAK_LEAVES.defaultBlockState()),
                "a crown paid the town timber");
    }

    @Test
    void aBarePlotOnFlatGroundOwesTheTownNothing() {
        FakeSky bare = new FakeSky().ground(8);

        Overgrowth.Clearing clearing = Overgrowth.overPlot(
                bare, new BlockPos(0, FLOOR, 0), plot(), Overgrowth.NOTHING_SPARED);

        assertTrue(yieldOf(bare, clearing).isEmpty(),
                "a site with nothing on it made the town richer");
    }

    // --- the classifier itself ---

    @Test
    void theTagsThisRunsWithoutAreGenuinelyUnbound() {
        // The premise of every assertion below. If tags ever do get bound in
        // this environment, the name table would stop being exercised here and
        // these tests would quietly go on passing for the wrong reason.
        assertFalse(Blocks.OAK_LOG.defaultBlockState().is(BlockTags.LOGS),
                "tags are bound after all: the fallback is no longer being tested");
    }

    @Test
    void everyBlockInTheTableIsClassifiedByNameWhenTheTagsCannotAnswer() {
        Map<Block, Spoil.Kind> table = new HashMap<>();
        for (Block log : List.of(Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG,
                Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
                Blocks.STRIPPED_OAK_LOG, Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_WOOD,
                Blocks.CRIMSON_STEM, Blocks.WARPED_HYPHAE)) {
            table.put(log, Spoil.Kind.TIMBER);
        }
        for (Block rock : List.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE,
                Blocks.COBBLED_DEEPSLATE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE,
                Blocks.TUFF, Blocks.CALCITE, Blocks.COPPER_ORE, Blocks.GOLD_ORE,
                Blocks.COAL_ORE)) {
            table.put(rock, Spoil.Kind.ROCK);
        }
        for (Block soil : List.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
                Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MUD, Blocks.SAND, Blocks.RED_SAND,
                Blocks.GRAVEL, Blocks.CLAY, Blocks.SNOW_BLOCK)) {
            table.put(soil, Spoil.Kind.SOIL);
        }
        table.put(Blocks.IRON_ORE, Spoil.Kind.ORE);
        table.put(Blocks.DEEPSLATE_IRON_ORE, Spoil.Kind.ORE);
        for (Block nothing : List.of(Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.WHEAT,
                Blocks.GLASS, Blocks.FURNACE, Blocks.OAK_PLANKS, Blocks.TORCH,
                Blocks.POPPY, Blocks.SHORT_GRASS, Blocks.OAK_FENCE, Blocks.AIR)) {
            table.put(nothing, Spoil.Kind.NOTHING);
        }

        for (Map.Entry<Block, Spoil.Kind> want : table.entrySet()) {
            assertEquals(want.getValue(),
                    BlueprintPlacer.spoilOf(want.getKey().defaultBlockState()),
                    want.getKey() + " is worth the wrong thing");
        }
    }

    @Test
    void aBlockWithNothingBehindItIsWorthNothingRatherThanThrowing() {
        // The classifier is asked about whatever the world hands it, which on a
        // modded server is anything at all. A block nobody has a rule for is
        // worth nothing, and must not be worth an exception.
        assertEquals(Spoil.Kind.NOTHING, BlueprintPlacer.spoilOf(null));
        assertEquals(Spoil.Kind.NOTHING,
                BlueprintPlacer.spoilOf(Blocks.AIR.defaultBlockState()));
        assertNull(Spoil.Kind.NOTHING.resource());
    }
}
