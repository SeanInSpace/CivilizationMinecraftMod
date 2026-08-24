package com.keystone.api;

import com.keystone.blueprint.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A blueprint resolved, transformed, and put in build order.
 *
 * <p>This is the type consuming mods actually hold. Two views of the same
 * structure:
 *
 * <ul>
 *   <li>{@link #sequence()} — the <strong>construction order</strong>: bottom
 *       course first, full blocks before partial ones within a course, and
 *       deterministic below that. Air is left out, because a builder cannot
 *       carry it and a cleared site is already empty. The list index is the
 *       build cursor.</li>
 *   <li>{@link #all()} — everything, air included, for stamping a structure into
 *       the world in one go. Here air matters: it carves.</li>
 * </ul>
 *
 * <p>Structure void is absent from both. It means "leave whatever is here",
 * which is the one instruction that is never a placement.
 *
 * <p>Ordering is computed against {@link EmptyBlockGetter}, not a real level, so
 * it depends only on the blueprint. That is what makes a resolved blueprint safe
 * to cache and share between sites.
 */
public final class LoadedBlueprint {

    private final Vec3i size;
    private final net.minecraft.core.BlockPos anchor;
    private final List<PlannedBlock> all;
    private final List<PlannedBlock> sequence;

    public LoadedBlueprint(Blueprint blueprint) {
        this.size = blueprint.size();
        this.anchor = blueprint.anchor();

        List<PlannedBlock> everything = new ArrayList<>(blueprint.blocks().size());
        for (Blueprint.BlueprintBlock block : blueprint.blocks()) {
            if (block.state().is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            everything.add(new PlannedBlock(block.pos(), block.state(), block.nbt()));
        }
        this.all = List.copyOf(everything);

        List<PlannedBlock> solid = new ArrayList<>(everything.size());
        for (PlannedBlock block : everything) {
            if (!block.state().isAir()) {
                solid.add(block);
            }
        }
        // The mason's order. This IS the construction sequence, so a supply gate
        // later simply stops the cursor part-way down the list.
        solid.sort(Comparator
                .comparingInt((PlannedBlock b) -> b.offset().getY())
                .thenComparing(b -> isFullBlock(b.state()) ? 0 : 1)
                .thenComparingInt(b -> b.offset().getX())
                .thenComparingInt(b -> b.offset().getZ()));
        this.sequence = List.copyOf(solid);
    }

    private static boolean isFullBlock(BlockState state) {
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** Bounding size after any rotation. */
    /**
     * The cell this structure lines up by, after any rotation.
     *
     * <p>Usually what the author put at the front door. A caller wanting the
     * building to sit where a plot says should place its origin so that this
     * cell lands on the plot.
     */
    public net.minecraft.core.BlockPos anchor() {
        return anchor;
    }

    public Vec3i size() {
        return size;
    }

    /** Blocks in build order, air excluded. The index is the build cursor. */
    public List<PlannedBlock> sequence() {
        return sequence;
    }

    /** Every block including air, for placing the structure whole. */
    public List<PlannedBlock> all() {
        return all;
    }

    public int blockCount() {
        return sequence.size();
    }
}
