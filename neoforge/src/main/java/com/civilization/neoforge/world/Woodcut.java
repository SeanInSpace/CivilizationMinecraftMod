package com.civilization.neoforge.world;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.work.InteriorClearing;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Takes down the wood a town's streets enclose.
 *
 * <p>{@code InteriorClearing} decides which ground and in what order; this finds
 * what is standing on it. The split is the usual one and it is not arbitrary: the
 * simulation has no idea where trees are and must never pretend to, so it names a
 * cell of ground and this answers what is in it.
 *
 * <p><strong>Trunks only, and their crowns come down by themselves.</strong> A
 * tree is its logs — {@code Felling} owns that definition and the bounds on it —
 * and the leaves it orphans decay under vanilla's own rule, which is what a player
 * expects to see and is also the only way to avoid taking a whole wood for one
 * oak. Ground cover is not touched at all: grass under a lamp is not spawnable and
 * a village on scraped dirt reads as a building site.
 *
 * <p><strong>Spoil is the clearing's, not the forester's.</strong> The logs go to
 * the town's shelves through {@link Yield}, which is the same route a road driven
 * through a wood already uses. Nothing here touches a lumber camp's {@code Stand}:
 * a stand is the timber a town has <em>counted</em> and works down deliberately,
 * and crediting clearance against it would have a town's forester report a felled
 * belt because somebody cleared the market square.
 */
public final class Woodcut {

    private Woodcut() {
    }

    /**
     * How far up and down from the ground a cell is searched for a trunk.
     *
     * <p>Sixteen up covers a tall spruce's stump on a slope and a trunk standing
     * on a knoll inside the cell; four down catches one rooted in a dip. Reading
     * the whole column would be cheaper to write and would find the branch of a
     * tree rooted in the next cell, which is a tree somebody else's cell owns.
     */
    private static final int LOOK_UP = 16;

    private static final int LOOK_DOWN = 4;

    /** Cells the clock's sweep brings down per second. */
    static final int CELLS_PER_SECOND = 2;

    /** How far behind the sweeps may fall before the catch-up is capped. */
    private static final int CATCH_UP_SECONDS = 10;

    /** How many cells of the cleared prefix one sweep will look at. */
    private static final int SCAN = 16;

    /**
     * What a swing at a cell came to: whether the cell is finished with, and
     * whether a tree actually came down in it.
     *
     * <p>Two answers rather than one, for the reason the wall's swing needs two: a
     * cell with nothing left in it is a station the crew must be moved off, and it
     * is not a tree felled. A crew paid spoil for an empty cell would be a town
     * making timber out of walking about.
     */
    public record Swing(boolean done, boolean worked) {
    }

    /**
     * Fells one tree standing in this cell, by hand.
     *
     * <p>The whole tree from the one trunk block found, exactly as the wall's
     * clearing does it: a crown fifteen blocks up is not picked at from a ladder
     * nobody has. One tree a swing, so a cell with four oaks in it is four visits —
     * which is what felling a wood looks like.
     *
     * @param hands the person doing it, so the logs go into their arms rather than
     *              straight onto the shelves; null for the clock
     */
    public static Swing fellOneIn(ServerLevel level, Settlement settlement, Person hands,
                                  SimPos cell) {
        BlockPos trunk = trunkIn(level, settlement, cell);
        if (trunk == null) {
            // Either the cell is open ground, or none of it is loaded. Both are a
            // station the crew is finished with: standing over unread chunks
            // forever is the one outcome that helps nobody, and the sweep comes
            // back to the cell when somebody loads it.
            return new Swing(true, false);
        }
        java.util.function.Predicate<BlockPos> isLog =
                at -> level.isLoaded(at) && level.getBlockState(at).is(BlockTags.LOGS);
        for (BlockPos log : com.civilization.neoforge.world.Felling.treeAt(trunk, isLog)) {
            BlockState state = level.getBlockState(log);
            level.destroyBlock(log, false, null, 512);
            // CLEARING, and this is the call the whole distinction was drawn for.
            // A trunk taken off the ground between a town's own streets is spoil:
            // the crew was not working the wood, and debiting the forester's
            // Stand for it would have a camp report a felled belt because somebody
            // cleared the market square. See Yield.Cause.
            Yield.keep(settlement, hands, state, log, Yield.Cause.CLEARING);
        }
        // Done when nothing else is standing in it. Asked again rather than
        // assumed, so a cell with two trees in it is not written off after one.
        return new Swing(trunkIn(level, settlement, cell) == null, true);
    }

    /**
     * The trunk a crew standing in this cell would walk to, or null for open
     * ground.
     *
     * <p>Nearest the middle of the cell, so a crew works outward from where it is
     * standing rather than pacing the cell diagonally. The forester's belt is
     * spared here as well as in {@code InteriorClearing.cells}, and the belt-of
     * test is asked of the <em>column</em> rather than of the cell: a cell may
     * legitimately straddle the claim edge, and the trees on the far side of that
     * line are the forester's however the cell was numbered.
     */
    public static BlockPos trunkIn(ServerLevel level, Settlement settlement, SimPos cell) {
        Overgrowth.Spared belt = Overgrowth.woodlandOf(settlement);
        int half = InteriorClearing.CELL / 2;
        BlockPos best = null;
        long closest = Long.MAX_VALUE;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int x = cell.x() + dx;
                int z = cell.z() + dz;
                if (belt.covers(x, z)) {
                    continue;   // the forester's, and never this work's
                }
                BlockPos found = trunkAt(level, x, z);
                if (found == null) {
                    continue;
                }
                long distance = (long) dx * dx + (long) dz * dz;
                if (distance < closest) {
                    closest = distance;
                    best = found;
                }
            }
        }
        return best;
    }

    /** The lowest log in this column, or null if nothing is growing here. */
    private static BlockPos trunkAt(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(new BlockPos(x, level.getSeaLevel(), z))) {
            return null;
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, column);
        for (int dy = -LOOK_DOWN; dy <= LOOK_UP; dy++) {
            BlockPos at = ground.above(dy);
            if (!level.isLoaded(at)) {
                continue;
            }
            if (level.getBlockState(at).is(BlockTags.LOGS)) {
                return at;
            }
        }
        return null;
    }

    /**
     * Whether there is anything left standing in this cell for a crew to walk to.
     *
     * <p>What the foreman asks before it sends anybody: a cell of open ground is a
     * cell the crew should be crossed off where they stand rather than walked
     * across the town to look at.
     */
    public static boolean anythingStandingIn(ServerLevel level, Settlement settlement,
                                             SimPos cell) {
        return trunkIn(level, settlement, cell) != null;
    }

    /**
     * Brings down the wood in the cells the clock has already cleared.
     *
     * <p>The clock's half of the work, and it is a <em>drawing</em> rather than a
     * second clock: {@code InteriorClearing.advance} has already written down that
     * the cell is cleared, and this is the blocks catching up when somebody loads
     * the ground. Exactly the arrangement {@code PerimeterLayer.draw} has for a
     * ring a town raised while nobody was looking, and it is the reason the clock
     * can take no spoil for a cell it clears: the logs are credited here, where
     * they actually come out of the ground, so nothing is minted and nothing is
     * counted twice.
     *
     * <p>Stands aside for a crew, and stands aside for a player. A wood that fells
     * itself in front of somebody is the one thing this must never look like, so
     * the sweep skips any cell within the observed radius and leaves it for the
     * hands that are presumably walking to it.
     *
     * @param handsOnTheClearing whether a builder is out clearing by hand
     */
    public static void draw(ServerLevel level, Settlement settlement,
                            boolean handsOnTheClearing, double observedRadius) {
        if (!settlement.hasLivingResidents() || handsOnTheClearing) {
            return;
        }
        int cleared = settlement.interiorCleared();
        if (cleared <= 0) {
            return;
        }
        List<SimPos> cells = InteriorClearing.cells(settlement);
        int limit = Math.min(cleared, cells.size());
        if (limit <= 0) {
            return;
        }
        long now = System.nanoTime();
        Long previous = LAST_DRAW.put(settlement.id(), now);
        long elapsed = previous == null ? DrawBudget.NANOS_PER_SECOND : now - previous;
        int budget = DrawBudget.forElapsed(elapsed, CELLS_PER_SECOND, CATCH_UP_SECONDS);
        int start = CURSOR.getOrDefault(settlement.id(), 0);
        if (start >= limit) {
            start = 0;
        }
        int felled = 0;
        int looked = 0;
        int examined = 0;
        int i = start;
        while (looked < SCAN && examined < limit && felled < budget) {
            SimPos cell = cells.get(i);
            BlockPos column = new BlockPos(cell.x(), cell.y(), cell.z());
            if (level.isLoaded(column)
                    && !playerNear(level, cell, observedRadius)) {
                if (fellOneIn(level, settlement, null, cell).worked()) {
                    felled++;
                }
                looked++;
            }
            examined++;
            i++;
            if (i >= limit) {
                i = 0;
            }
        }
        CURSOR.put(settlement.id(), i);
    }

    private static boolean playerNear(ServerLevel level, SimPos at, double radius) {
        return level.getNearestPlayer(at.x() + 0.5, at.y(), at.z() + 0.5, radius,
                false) != null;
    }

    /** Where each town's sweep stopped, so the budget travels round the town. */
    private static final Map<Settlement.Id, Integer> CURSOR = new java.util.HashMap<>();

    /** When each town's clearing was last swept, so the budget is real time. */
    private static final Map<Settlement.Id, Long> LAST_DRAW = new java.util.HashMap<>();

    /** Drops what the sweep remembers about a world that is closing. */
    public static void forget() {
        CURSOR.clear();
        LAST_DRAW.clear();
    }
}
