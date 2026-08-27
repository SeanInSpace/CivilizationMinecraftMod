package com.kingdoms.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The growth that has to come down before a stretch of wall can go up.
 *
 * <p>The palisade used to be built straight through a wood. Its ground is found
 * with the {@code MOTION_BLOCKING_NO_LEAVES} heightmap, which steps over leaves
 * and <em>not</em> over logs — so a post whose column held a trunk was founded
 * on top of the trunk, and the wall climbed the tree. Worse, a canopy that
 * reached across the line gave anything outside a floor to walk in on, which is
 * the one thing a wall exists to prevent.
 *
 * <p>So a tree in the way is a job before it is an obstacle. A builder fells it
 * by hand with {@link HandDig}, at the speed an axe actually takes, and the post
 * goes up afterwards — the same order a person would do it in.
 *
 * <p><strong>Why this is checked at the post rather than marked when the ring is
 * staked.</strong> Staking happens on the abstract clock, often across chunks
 * nobody has loaded, where the trees are not there to be found. A mark made then
 * would be a guess, and a guess that goes stale the moment a lumberjack replants.
 * Asking at the station asks the world the question at the only moment the
 * answer is both knowable and current.
 */
public final class WallClearing {

    private WallClearing() {
    }

    /**
     * How far either side of the line to keep clear of growth.
     *
     * <p>One is not enough: leaves reach further than trunks, and a branch that
     * overhangs the walkway is a bridge. Two takes the canopy back past anything
     * that could be stepped onto from outside.
     */
    public static final int CLEAR_SIDEWAYS = 2;

    /**
     * How far above the footing to keep clear.
     *
     * <p>The wall stands two high. Anything within another two above that could
     * be dropped onto it from a branch, so the corridor is cut to four.
     */
    public static final int CLEAR_UP = 4;

    /** Whether this is a tree rather than the world's ground or the town's work. */
    public static boolean isGrowth(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    /**
     * The first piece of tree standing in this post's way, or null if the line
     * here is clear.
     *
     * <p>Trunks before leaves, and lower before higher: felling the stump is
     * what brings a whole tree down, so going for the wood a person could
     * actually reach gets the canopy with it rather than picking at foliage
     * fifteen blocks up. See {@code Excavation.reduceTrees} for the same
     * reasoning on a building site.
     */
    public static BlockPos inTheWay(ServerLevel level, BlockPos footing) {
        BlockPos leaves = null;
        for (int dy = 0; dy < CLEAR_UP; dy++) {
            for (int dx = -CLEAR_SIDEWAYS; dx <= CLEAR_SIDEWAYS; dx++) {
                for (int dz = -CLEAR_SIDEWAYS; dz <= CLEAR_SIDEWAYS; dz++) {
                    BlockPos at = footing.offset(dx, dy, dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    if (state.is(BlockTags.LOGS)) {
                        return at;   // the trunk: take this and the rest follows
                    }
                    if (leaves == null && state.is(BlockTags.LEAVES)) {
                        leaves = at;
                    }
                }
            }
        }
        return leaves;
    }

    /**
     * Whether anything is still growing across this stretch of the line.
     */
    public static boolean isBlocked(ServerLevel level, BlockPos footing) {
        return inTheWay(level, footing) != null;
    }
}
