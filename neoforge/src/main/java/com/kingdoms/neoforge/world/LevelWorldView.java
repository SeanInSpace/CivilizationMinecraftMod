package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.phys.AABB;

/**
 * The real world, answering the auditor's questions.
 *
 * <p>Every Minecraft call the audit makes lives in this one class, which is the
 * point of {@link WorldView}: what is left in {@link TownAuditor} is geometry,
 * and geometry can be tested.
 */
public record LevelWorldView(ServerLevel level) implements WorldView {

    @Override
    public boolean isLoaded(BlockPos pos) {
        return level.isLoaded(pos);
    }

    @Override
    public boolean isTicking(BlockPos pos) {
        return level.isPositionEntityTicking(pos);
    }

    @Override
    public boolean isPassable(BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public boolean isStandable(BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public boolean hasFluid(BlockPos pos) {
        return !level.getFluidState(pos).isEmpty();
    }

    @Override
    public boolean isFarmland(BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.FARMLAND);
    }

    @Override
    public boolean isCrop(BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.CROPS);
    }

    @Override
    public boolean isFenceGate(BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof FenceGateBlock;
    }

    @Override
    public String blockNameAt(BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }

    @Override
    public int groundLevel(int x, int z) {
        return BlueprintPlacer.groundLevel(level, x, z);
    }

    @Override
    public int looseItemsIn(AABB box) {
        return level.getEntities((Entity) null, box,
                entity -> entity instanceof ItemEntity && entity.isAlive()).size();
    }

    @Override
    public long stepsElapsed() {
        SimWorld world = KingdomsMod.simulationFor(level);
        return world == null ? 0L : world.stepsElapsed();
    }
}
