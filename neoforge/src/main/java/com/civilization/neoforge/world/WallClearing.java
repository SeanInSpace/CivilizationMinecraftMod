package com.civilization.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.List;

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

    // --- what a post treads on, as opposed to what it has to be cleared of ---

    /**
     * How many cells of its own column a post has to have to itself.
     *
     * <p>Three: two courses of fence and the cell the lantern stands in. A plant
     * in any of them is a plant the post has to displace.
     */
    public static final int POST_COLUMN = 3;

    /**
     * A column of the world, asked cell by cell.
     *
     * <p>The seam, and it is a small one on purpose. Everything below is a
     * question about two block states and the cell one of them is in, so it can
     * be asked of a map in a test as easily as of a chunk — and the fault it
     * exists for is exactly the kind that a map can hold and a running game
     * cannot be persuaded to reproduce on demand.
     */
    public interface Standing {

        BlockState at(BlockPos pos);
    }

    /**
     * Whether a post simply treads this down on its way in.
     *
     * <p><strong>The lilac.</strong> {@code /civ wall} on Millbrook reported
     * three positions as GENUINELY MISSING and named what was standing in each:
     * a lilac. The wall's own clearing takes wood — {@link #isGrowth}, logs and
     * leaves — and had no opinion about anything else, on the reasonable theory
     * that a flower is not in anybody's way. It is: the post is placed only into
     * something {@code canBeReplaced()} says will give, and the four tall
     * flowers (lilac, sunflower, peony, rose bush) are in no such list. Short
     * grass gave way, tall grass gave way, a lilac stopped the wall dead and
     * said nothing, because the laid count had already moved past it.
     *
     * <p>So the rule is {@code Overgrowth}'s ground cover, which is the same
     * question every other part of the town already asks about what stands on
     * the surface — with one addition. A {@link DoublePlantBlock} counts
     * outright, ahead of the tag lookup, and that is not belt and braces: block
     * tags are bound when a server loads its datapacks, so {@code
     * state.is(BlockTags.FLOWERS)} is false in a JUnit run for a lilac itself,
     * and a rule that could only be checked against a running server is a rule
     * the next person changes blind.
     *
     * <p>Water is not a plant. The ring crosses streams and a post stands at the
     * waterline; a rule that uprooted water would dig a trench through every
     * pond the line touches.
     */
    public static boolean isPlant(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof DoublePlantBlock) {
            return true;
        }
        return Overgrowth.coverOf(state) == Overgrowth.Cover.GROUND;
    }

    /**
     * The cell holding the rest of a two-tall plant, or null for a one-block one.
     *
     * <p>Vanilla only volunteers half of this. {@code DoublePlantBlock.updateShape}
     * drops an upper half the moment the cell below it stops being the matching
     * lower half — so taking the bottom out with a block update takes the top
     * with it — and the lower half's own survival test asks about the ground
     * underneath and nothing above, so taking the <em>top</em> out leaves a stem
     * standing. The wall has to name both cells itself, because it does not get
     * to choose which half its footing lands on.
     */
    public static BlockPos otherHalf(BlockState state, BlockPos at) {
        if (!(state.getBlock() instanceof DoublePlantBlock)
                || !state.hasProperty(DoublePlantBlock.HALF)) {
            return null;
        }
        return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER
                ? at.above() : at.below();
    }

    /**
     * Every cell that has to be emptied of growing things for a post to stand.
     *
     * <p>The post's own column and no wider: the ring already takes wood two
     * blocks either side of the line, and taking the flowers as well would leave
     * a five-wide bald strip round the whole town, which is the scraped-pad look
     * {@code Overgrowth} keeps a verge to avoid. A palisade through a meadow
     * should look like a palisade through a meadow.
     *
     * <p>Both halves of a two-tall plant, in whichever order they were met.
     * Returned bottom-up, so the lower half is taken first and the upper is
     * usually already gone by the time its own turn comes round — which is the
     * cheap case, not the one that had to be got right.
     */
    public static List<BlockPos> uproot(Standing world, BlockPos footing, int courses) {
        List<BlockPos> pull = new ArrayList<>();
        for (int dy = 0; dy < courses; dy++) {
            BlockPos at = footing.above(dy);
            BlockState state = world.at(at);
            if (!isPlant(state)) {
                continue;
            }
            if (!pull.contains(at)) {
                pull.add(at);
            }
            BlockPos other = otherHalf(state, at);
            if (other != null && world.at(other).is(state.getBlock())
                    && !pull.contains(other)) {
                pull.add(other);
            }
        }
        return pull;
    }
}
