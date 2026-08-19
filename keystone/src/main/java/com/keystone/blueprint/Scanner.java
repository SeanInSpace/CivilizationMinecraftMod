package com.keystone.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a region of the world into a blueprint.
 *
 * <p><strong>There is no size limit.</strong> The familiar 48-block ceiling is
 * enforced by the structure <em>block</em>, not by the format, and this does not
 * go anywhere near a structure block — so a keep, a curtain wall or a whole
 * street can be captured in one go. That is the single reason this class exists.
 *
 * <p>Air is not recorded. It keeps files proportional to the building rather than
 * to its bounding box, which is what makes large scans practical at all — an
 * empty 100-cube would otherwise be a million entries. The cost is that a
 * blueprint does not carve out space when placed; use {@code structure_void} if
 * you want a region explicitly left alone.
 */
public final class Scanner {

    private Scanner() {
    }

    /** Captures everything between two corners, inclusive, in either order. */
    public static Blueprint scan(ServerLevel level, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));

        Vec3i size = new Vec3i(
                max.getX() - min.getX() + 1,
                max.getY() - min.getY() + 1,
                max.getZ() - min.getZ() + 1);

        List<Blueprint.BlueprintBlock> blocks = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    blocks.add(new Blueprint.BlueprintBlock(
                            new BlockPos(x - min.getX(), y - min.getY(), z - min.getZ()),
                            state,
                            contentsOf(level, cursor)));
                }
            }
        }
        return new Blueprint(size, blocks);
    }

    /** Chest contents, sign text and the like, so a scan keeps what was inside. */
    private static CompoundTag contentsOf(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.saveCustomOnly(level.registryAccess());
    }

    /** How many blocks a region holds, for warning before a very large scan. */
    public static long volumeOf(BlockPos a, BlockPos b) {
        long w = Math.abs(a.getX() - b.getX()) + 1L;
        long h = Math.abs(a.getY() - b.getY()) + 1L;
        long d = Math.abs(a.getZ() - b.getZ()) + 1L;
        return w * h * d;
    }
}
