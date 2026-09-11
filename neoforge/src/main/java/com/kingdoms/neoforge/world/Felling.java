package com.kingdoms.neoforge.world;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which blocks are <em>one tree</em>, when somebody puts an axe to it.
 *
 * <p><strong>A tree is its trunk.</strong> Felling gathers the wood that is
 * joined to the stump by wood, and nothing else. It used to gather logs and
 * leaves together, out from the stump, on the reasoning that a felled trunk
 * should take its own canopy with it — and in a close-grown wood that is a
 * catastrophe, because two oaks five blocks apart interlock their crowns. Leaves
 * touch leaves touch leaves, and one swing at one stump took the whole forest
 * down in a single tick. A player who chopped a tree watched the horizon fall
 * over.
 *
 * <p>So leaves are not gathered, and are not touched at all. Vanilla takes them
 * on its own: {@code LeavesBlock} decays a leaf whose {@code PERSISTENT} is
 * false once its {@code DISTANCE} reaches 7, and that distance is counted one
 * step per face out from the nearest log. Pull the logs and every leaf more than
 * six from any surviving log falls by itself, over the next minute or two,
 * leaf by leaf — which is what a player expects to see and what they see when
 * they fell a tree themselves. Every leaf in this mod's world comes from a
 * vanilla configured tree feature (see {@code Woodland}, which runs
 * {@code TreeFeatures.OAK} and its kin), and worldgen never sets
 * {@code PERSISTENT}: only a leaf block placed by a player's hand is persistent.
 * So the decay rule always applies to the trees a town fells.
 *
 * <p>Pure, and deliberately so: it takes a question about a position rather than
 * a level, so the shape of a tree can be tested without a server.
 */
public final class Felling {

    /**
     * Most logs one stroke of felling may bring down: 64.
     *
     * <p>Generous for a tree and small for a wood. An ordinary oak is four to six
     * logs; a fancy oak with its branches is twenty-odd; a dark oak's 2×2 trunk
     * with its corner branches is around forty. The one vanilla shape that
     * genuinely exceeds this is a giant jungle or spruce tree, whose 2×2 column
     * can run past thirty courses — that one simply comes down in two strokes,
     * because whatever is left standing is found again on the next pass.
     */
    public static final int MOST_LOGS = 64;

    /**
     * How far sideways from the struck block a tree may reach: 7.
     *
     * <p>Taken off the widest vanilla trunk there is. A fancy oak draws its limbs
     * out to a radius of {@code treeShape * (random + 0.328)}, which for the
     * tallest one it will generate lands a little under six blocks from the
     * trunk; seven leaves a course of margin. Past that, whatever is joined on is
     * a second tree that happens to be leaning in, not this one.
     */
    public static final int REACH_SIDEWAYS = 7;

    /**
     * How far above the struck block a tree may reach: 30.
     *
     * <p>A giant jungle tree is the tallest thing that grows, and it does not
     * clear thirty courses.
     */
    public static final int REACH_UP = 30;

    /**
     * How far below the struck block a tree may reach: 8.
     *
     * <p>A lumberjack aims at the stump and never needs this. A wall builder does
     * — {@code WallClearing} hands over the first log crossing the line, which is
     * wherever the trunk happens to meet the corridor, so the rest of the trunk
     * is underneath the block that gave.
     */
    public static final int REACH_DOWN = 8;

    private Felling() {
    }

    /**
     * Every log of the tree the given block belongs to.
     *
     * <p>Breadth-first from {@code from}, through logs only, bounded by
     * {@link #MOST_LOGS} and by the box {@link #REACH_SIDEWAYS} out,
     * {@link #REACH_UP} above and {@link #REACH_DOWN} below it. The starting
     * block is included when it is itself a log, and the result is empty when it
     * is not — a leaf is not a tree, it is one leaf.
     *
     * <p><strong>The neighborhood is all twenty-six touching positions</strong>,
     * and it has to be, because vanilla draws trees that way:
     * <ul>
     *   <li>{@code FancyTrunkPlacer.makeLimb} rasterizes a branch along a line,
     *       stepping {@code max(|dx|,|dy|,|dz|)} times and placing one log per
     *       step. Consecutive logs on a limb therefore differ by one on all three
     *       axes at once, and a limb that runs out further than it rises has
     *       steps that are purely horizontal diagonals. Six-connectivity leaves
     *       most of a fancy oak's branches hanging in the air.</li>
     *   <li>{@code DarkOakTrunkPlacer} hangs its branch columns on the corners of
     *       the 2×2 trunk — offsets of −1 and +2 on both x and z — so those
     *       columns meet the trunk on a diagonal and never on a face.</li>
     *   <li>{@code BendingTrunkPlacer}, which is what an acacia is, steps the
     *       trunk sideways as it rises, so the bend is a diagonal joint too.</li>
     * </ul>
     * Twenty-six through <em>logs only</em> is safe where twenty-six through
     * leaves was ruinous: two trees have to be touching wood to wood to be taken
     * as one, and that is the vanilla reality — see {@link #isOneTree}.
     */
    public static Set<BlockPos> treeAt(BlockPos from, Predicate<BlockPos> isLog) {
        Set<BlockPos> logs = new LinkedHashSet<>();
        if (!isLog.test(from)) {
            return logs;
        }
        Deque<BlockPos> queue = new ArrayDeque<>();
        logs.add(from);
        queue.add(from);
        while (!queue.isEmpty() && logs.size() < MOST_LOGS) {
            BlockPos at = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = at.offset(dx, dy, dz);
                        if (logs.contains(next) || !within(from, next) || !isLog.test(next)) {
                            continue;
                        }
                        logs.add(next);
                        if (logs.size() >= MOST_LOGS) {
                            return logs;
                        }
                        queue.add(next);
                    }
                }
            }
        }
        return logs;
    }

    /**
     * Whether two trunks are the same tree.
     *
     * <p>They are exactly when their logs touch, and that is not a simplification
     * — it is what the world actually looks like. A dark oak's trunk is 2×2 and
     * comes down as one thing; two saplings a player pushed in side by side grow
     * into one tangle of wood and come down as one thing too. Two oaks five
     * blocks apart whose crowns have grown into each other are two trees, because
     * there is no wood between them, and taking both for the price of one is the
     * fault this whole class exists to fix.
     */
    public static boolean isOneTree(BlockPos stump, BlockPos other, Predicate<BlockPos> isLog) {
        return treeAt(stump, isLog).contains(other);
    }

    /** Whether this position is still inside the box one tree may occupy. */
    private static boolean within(BlockPos from, BlockPos at) {
        int dy = at.getY() - from.getY();
        return Math.abs(at.getX() - from.getX()) <= REACH_SIDEWAYS
                && Math.abs(at.getZ() - from.getZ()) <= REACH_SIDEWAYS
                && dy <= REACH_UP && dy >= -REACH_DOWN;
    }
}
