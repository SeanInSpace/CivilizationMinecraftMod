package com.keystone.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotating and mirroring a blueprint.
 *
 * <p>Two halves that must agree: the block <em>positions</em> move, and each
 * block <em>state</em> turns with them. Getting only the first right is what
 * produces a rotated building whose stairs still face the original north.
 *
 * <p>Vanilla's {@code StructureTemplate.transform} rotates about an arbitrary
 * pivot and can hand back negative coordinates. Blueprints are always anchored
 * at their own minimum corner, so the maths here rotates about the origin and
 * then translates the result back into the positive octant — which keeps a
 * blueprint's contract ("nothing is outside 0..size-1") true under any rotation.
 *
 * <p>{@link #position} is deliberately pure integer arithmetic with no registry
 * or world access, so the geometry can be unit-tested without booting Minecraft.
 */
public final class Transforms {

    private Transforms() {
    }

    /** The bounding size after rotation: a quarter turn swaps X and Z. */
    public static Vec3i size(Vec3i size, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(size.getZ(), size.getY(), size.getX());
            default -> size;
        };
    }

    /**
     * Moves one position, mirroring first and then rotating, matching vanilla's
     * order so a blueprint transformed here lands where a structure block would
     * put it.
     *
     * @param size the size <em>before</em> transformation
     */
    public static BlockPos position(BlockPos pos, Vec3i size, Rotation rotation, Mirror mirror) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        switch (mirror) {
            case LEFT_RIGHT -> z = size.getZ() - 1 - z;
            case FRONT_BACK -> x = size.getX() - 1 - x;
            default -> {
            }
        }

        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(size.getZ() - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(size.getX() - 1 - x, y, size.getZ() - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, size.getX() - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    /**
     * Applies a transformation to a whole blueprint, states and all.
     *
     * <p>Uses the deprecated {@code BlockState.rotate(Rotation)} rather than the
     * level-aware overload, on purpose. Vanilla's own structure placement calls
     * the same method, and staying world-free is what lets a transformed
     * blueprint be computed once and reused at every site. The level-aware
     * version exists for blocks whose rotation depends on surroundings, which a
     * stored structure has none of at transform time.
     */
    @SuppressWarnings("deprecation")
    public static Blueprint apply(Blueprint blueprint, Rotation rotation, Mirror mirror) {
        if (rotation == Rotation.NONE && mirror == Mirror.NONE) {
            return blueprint;
        }
        Vec3i size = blueprint.size();
        List<Blueprint.BlueprintBlock> moved = new ArrayList<>(blueprint.blocks().size());
        for (Blueprint.BlueprintBlock block : blueprint.blocks()) {
            moved.add(new Blueprint.BlueprintBlock(
                    position(block.pos(), size, rotation, mirror),
                    block.state().mirror(mirror).rotate(rotation),
                    block.nbt()));
        }
        return new Blueprint(size(size, rotation), moved);
    }
}
