package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Trodden ways between buildings.
 *
 * <p>A village reads as a village when its doors are joined up. Paths are drawn
 * from one entrance to another along the terrain, one block wide, following the
 * surface up and down rather than cutting through it — a track worn by use, not
 * a road cut by engineers.
 *
 * <p>Only natural ground is paved and only headroom above it is cleared, so a
 * path can never eat a wall it happens to run past.
 */
public final class PathLayer {

    /** Longest run laid in one go, so a distant plot cannot cost a tick. */
    private static final int MAX_LENGTH = 96;

    /** Blocks of clearance kept above the track. */
    private static final int HEADROOM = 2;

    private PathLayer() {
    }

    /**
     * Lays a track between two points, following the ground.
     *
     * @return how many blocks of path were laid
     */
    public static int connect(ServerLevel level, SimPos from, SimPos to) {
        int x0 = from.x();
        int z0 = from.z();
        int x1 = to.x();
        int z1 = to.z();

        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        if (dx + dz > MAX_LENGTH) {
            return 0;   // too far to be a village path; leave it be
        }
        int stepX = x0 < x1 ? 1 : -1;
        int stepZ = z0 < z1 ? 1 : -1;
        int error = dx - dz;

        int laid = 0;
        int x = x0;
        int z = z0;
        for (int guard = 0; guard <= MAX_LENGTH; guard++) {
            laid += pave(level, x, z);
            if (x == x1 && z == z1) {
                break;
            }
            int doubled = error * 2;
            if (doubled > -dz) {
                error -= dz;
                x += stepX;
            }
            if (doubled < dx) {
                error += dx;
                z += stepZ;
            }
        }
        return laid;
    }

    /**
     * Paves one column: the surface block becomes a path, and the air above it
     * is opened up.
     *
     * @return 1 if anything was laid
     */
    private static int pave(ServerLevel level, int x, int z) {
        BlockPos surface = new BlockPos(x, level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        if (!level.isLoaded(surface)) {
            return 0;
        }
        BlockState state = level.getBlockState(surface);
        if (!isPaveable(state)) {
            return 0;   // a floor, a roof, or water: not ours to pave
        }
        level.setBlock(surface, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_CLIENTS);

        for (int dy = 1; dy <= HEADROOM; dy++) {
            BlockPos above = surface.above(dy);
            BlockState overhead = level.getBlockState(above);
            // Only foliage is cleared. Anything built stays exactly where it is.
            if (overhead.is(BlockTags.LEAVES) || overhead.is(BlockTags.FLOWERS)
                    || overhead.is(Blocks.SHORT_GRASS) || overhead.is(Blocks.TALL_GRASS)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return 1;
    }

    /** Grass and dirt only — the things a track actually wears into. */
    private static boolean isPaveable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT);
    }
}
