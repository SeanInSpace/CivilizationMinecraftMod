package com.civilization.neoforge.world;

import com.civilization.sim.geom.SimPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.List;

/**
 * Growing the wood a settled town has always had.
 *
 * <p>The world half of {@code ForesterStand}. The simulation says which squares
 * a tree may stand on and how many are wanted; this decides what actually grows
 * there and puts it in — by running the same configured tree feature world
 * generation runs, so the stand is made of the region's own trees rather than
 * something this mod drew.
 *
 * <p><strong>It never makes a square usable.</strong> No levelling, no filling,
 * no clearing. A square that is lake, stone, cliff or already under a canopy is
 * skipped and the next candidate tried, which is why the simulation hands over
 * more candidates than it wants trees. A camp on a lakeshore ends up with a
 * smaller stand, and that is the honest answer.
 */
public final class Woodland {

    /** Air a sapling's worth of trunk needs before a tree is worth trying. */
    private static final int HEADROOM = 5;

    private Woodland() {
    }

    /**
     * Grows up to {@code wanted} trees on the first candidate squares that will
     * take one.
     *
     * @return how many went in
     */
    public static int plant(ServerLevel level, List<SimPos> spots, int wanted) {
        if (wanted <= 0 || spots.isEmpty()) {
            return 0;
        }
        var features = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int planted = 0;
        for (SimPos spot : spots) {
            if (planted >= wanted) {
                break;
            }
            BlockPos foot = rootFor(level, spot);
            if (foot == null) {
                continue;
            }
            Holder.Reference<ConfiguredFeature<?, ?>> tree =
                    features.get(speciesAt(level, foot)).orElse(null);
            if (tree == null) {
                continue;   // a datapack has taken this tree away; try the next square
            }
            if (tree.value().place(level, generator, level.getRandom(), foot)) {
                planted++;
            }
        }
        return planted;
    }

    /**
     * The block a trunk would start on, or null if this square will not take a
     * tree.
     *
     * <p>Deliberately the same three questions {@code LumberjackWorker} asks of a
     * planting spot — soil underfoot, air above — so a tree planted here is a
     * tree the town's own forester would have planted, and the ground it stands
     * on is ground they will replant when it comes down. Never loads a chunk:
     * ground nobody has loaded is simply not planted on.
     *
     * <p><strong>The soil is the tag a sapling itself stands on, and asking a
     * narrower one cost every town on a grass world its whole wood.</strong>
     * {@code #minecraft:dirt} is dirt, coarse dirt and rooted dirt — and nothing
     * else. It does not contain grass, which is what the top block of a plain, a
     * forest, a meadow or a superflat actually is, so a camp on any of them was
     * offered two dozen perfectly good squares and refused every one of them in
     * turn. {@code #minecraft:supports_vegetation} is the list vanilla itself
     * checks before it will let a sapling stand — grass, podzol and mycelium,
     * the dirts, mud, moss and farmland — and "where a sapling may stand" is
     * exactly the question being asked here.
     */
    private static BlockPos rootFor(ServerLevel level, SimPos spot) {
        BlockPos column = new BlockPos(spot.x(), spot.y(), spot.z());
        if (!level.isLoaded(column)) {
            return null;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spot.x(), spot.z());
        BlockPos ground = new BlockPos(spot.x(), surface - 1, spot.z());
        if (!level.getBlockState(ground).is(BlockTags.SUPPORTS_VEGETATION)) {
            return null;   // water, stone, sand, or somebody's roof
        }
        BlockPos foot = ground.above();
        for (int up = 0; up < HEADROOM; up++) {
            if (!level.getBlockState(foot.above(up)).isAir()) {
                return null;   // a canopy, an overhang, or the underside of a cliff
            }
        }
        return foot;
    }

    /**
     * What grows here.
     *
     * <p>Read from the biome rather than from the culture, because a stand is
     * part of the landscape before it is part of the town: spruce in the taiga
     * and acacia on the savanna is what makes a seeded wood look like it grew
     * there. Oak is the default and the fallback, being the tree that grows
     * almost everywhere.
     *
     * <p>Tags first where a tag exists, so modded biomes that declare themselves
     * taiga or jungle get the right wood without being listed here.
     */
    private static ResourceKey<ConfiguredFeature<?, ?>> speciesAt(ServerLevel level,
                                                                  BlockPos at) {
        Holder<Biome> biome = level.getBiome(at);
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return TreeFeatures.JUNGLE_TREE;
        }
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return TreeFeatures.SPRUCE;
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return TreeFeatures.ACACIA;
        }
        if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
            return TreeFeatures.BIRCH;
        }
        if (biome.is(Biomes.DARK_FOREST)) {
            return TreeFeatures.DARK_OAK;
        }
        if (biome.is(Biomes.PALE_GARDEN)) {
            return TreeFeatures.PALE_OAK;
        }
        if (biome.is(Biomes.CHERRY_GROVE)) {
            return TreeFeatures.CHERRY;
        }
        if (biome.is(Biomes.SWAMP)) {
            return TreeFeatures.SWAMP_OAK;
        }
        return TreeFeatures.OAK;
    }
}
