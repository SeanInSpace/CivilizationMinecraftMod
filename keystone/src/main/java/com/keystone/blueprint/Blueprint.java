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
public record Blueprint(Vec3i size, List<BlueprintBlock> blocks, BlockPos anchor) {

    public Blueprint {
        blocks = List.copyOf(blocks);
        if (anchor == null) {
            anchor = defaultAnchor(size);
        }
    }

    /** A blueprint that says nothing about where it should be lined up. */
    public Blueprint(Vec3i size, List<BlueprintBlock> blocks) {
        this(size, blocks, defaultAnchor(size));
    }

    /**
     * Where a structure lines up when it does not say.
     *
     * <p>The middle of its floor, which is what a build plot is: a point, with
     * the building drawn around it. A file that names its own anchor overrides
     * this — see {@code StructurizeNbt}, where ignoring the stated one is what
     * used to bury an imported building half into the hillside beside its plot.
     */
    public static BlockPos defaultAnchor(Vec3i size) {
        return new BlockPos((size.getX() - 1) / 2, 0, (size.getZ() - 1) / 2);
    }

    /**
     * Whether a stated anchor is a cell this structure actually has.
     *
     * <p>An anchor outside the box cannot be lined up with anything, and a file
     * carrying one is describing a structure it does not contain.
     */
    public static boolean anchorFits(BlockPos anchor, Vec3i size) {
        return anchor.getX() >= 0 && anchor.getX() < size.getX()
                && anchor.getY() >= 0 && anchor.getY() < size.getY()
                && anchor.getZ() >= 0 && anchor.getZ() < size.getZ();
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
