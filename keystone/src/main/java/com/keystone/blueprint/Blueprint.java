package com.keystone.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A structure held in memory: a bounding size and the blocks that fill it.
 *
 * <p>Positions are <em>relative to the blueprint's own origin</em> — the minimum
 * corner is (0, 0, 0). Nothing here knows where in a world it will end up.
 *
 * <p>There is deliberately no size limit. The familiar 48-block ceiling belongs
 * to the structure <em>block</em>, which is only one way of authoring a file;
 * the NBT format itself has never had one.
 */
public record Blueprint(Vec3i size, List<BlueprintBlock> blocks) {

    public Blueprint {
        blocks = List.copyOf(blocks);
    }

    /**
     * One block of a structure.
     *
     * @param pos   relative to the blueprint origin
     * @param state the full block state — facing, half, shape and all, which is
     *              the entire reason blueprints beat hand-coded geometry
     * @param nbt   block-entity data (chest contents, sign text), or null
     */
    public record BlueprintBlock(BlockPos pos, BlockState state, CompoundTag nbt) {
    }

    public int volume() {
        return size.getX() * size.getY() * size.getZ();
    }
}
