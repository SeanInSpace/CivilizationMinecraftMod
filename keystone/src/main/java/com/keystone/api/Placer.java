package com.keystone.api;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Putting planned blocks into a world.
 *
 * <p>Blocks go down with {@link Block#UPDATE_CLIENTS} and no neighbor updates,
 * which is both faster and <em>more correct</em> here: a blueprint already stores
 * the fully-connected state of every fence, wall and stair, so asking the world
 * to recompute connections could only spoil what was authored. It also stops a
 * half-built structure tearing itself apart — a door's lower half placed on its
 * own would pop off the moment it was told to re-evaluate.
 */
public final class Placer {

    private Placer() {
    }

    /** Stamps the whole structure, air included, so it carves as authored. */
    public static void placeAll(ServerLevel level, LoadedBlueprint blueprint, BlockPos base) {
        for (PlannedBlock block : blueprint.all()) {
            place(level, block, base);
        }
    }

    /** Lays one block and restores its block-entity contents, if it has any. */
    public static void place(ServerLevel level, PlannedBlock block, BlockPos base) {
        BlockPos pos = block.at(base);
        level.setBlock(pos, block.state(), Block.UPDATE_CLIENTS);
        applyBlockEntity(level, pos, block.nbt());
    }

    private static void applyBlockEntity(ServerLevel level, BlockPos pos, CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return;
        }
        entity.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), nbt));
    }
}
