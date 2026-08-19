package com.keystone.api;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One block of a build, ready to lay.
 *
 * @param offset relative to the structure's minimum corner, already transformed
 * @param state  the full state, already rotated to match
 * @param nbt    block-entity data, or null
 */
public record PlannedBlock(BlockPos offset, BlockState state, CompoundTag nbt) {

    /** Where this block goes when the structure is anchored at {@code base}. */
    public BlockPos at(BlockPos base) {
        return base.offset(offset);
    }
}
