package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.work.DigYard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.tags.BlockTags;

import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A dig in progress, in the world.
 *
 * <p>{@link DigYard} decides <em>what</em> may be dug and <em>by whom</em>; this
 * decides how it looks and how long it takes. The two halves are split because
 * the first is arithmetic worth testing and the second needs a running server.
 *
 * <p>Three things here matter more than the rest:
 *
 * <ul>
 *   <li><strong>A block takes exactly as long as it takes a player.</strong>
 *       {@link #digTicks} is vanilla's own break arithmetic, and it is spent one
 *       tick at a time from the server tick — not sampled every fifth tick, and
 *       not rounded into a budget. Dirt with a shovel is three ticks for a digger
 *       for the same reason it is three ticks for you.</li>
 *   <li><strong>Diggers stand beside the block, never in it.</strong> A stand is
 *       a horizontal neighbour at or above the target, checked for a body-sized
 *       gap, for footing, and for an actual A* path before it is used. Stands are
 *       reserved, so two people are never sent to the same square.</li>
 *   <li><strong>The block visibly cracks.</strong> Progress goes out as the same
 *       overlay a player mining sees, and the block ticks its hit sound, so a
 *       long dig reads as a long dig instead of a pause followed by a
 *       disappearance.</li>
 * </ul>
 */
public final class Excavation {

    /** Ticks between arm swings. Matches the length of the swing animation. */
    private static final int SWING_INTERVAL = 6;

    /** Ticks between hit sounds, so a dig is audible without being a drum roll. */
    private static final int SOUND_INTERVAL = 4;

    /**
     * Ticks between re-issuing a path.
     *
     * <p>Short, and it has to be. A person carries an idle stroll goal for
     * ambience, and that goal grabs the navigator the moment it comes free — so a
     * digger told once a second where to go spends most of the second wandering
     * back off again. Four times a second beats the stroll goal to it, which is
     * the same cadence the masonry loop has always used for the same reason.
     */
    private static final int REPATH_INTERVAL = 5;

    /**
     * How close a digger has to be to the block to work on it.
     *
     * <p>Vanilla player reach. Deliberately measured against the block rather
     * than against the square they were sent to: navigation routinely halts a
     * block short of an exact target, and a digger stood a pace off their mark
     * but plainly within arm's length of the ground should be digging, not
     * shuffling.
     */
    private static final double DIG_REACH = 4.5;

    /**
     * How close a digger must be before they are given a cell at all.
     *
     * <p>Claiming is settled with an A* to the exact square they would stand on,
     * and mob navigation will not path across a village. A digger who is still on
     * the other side of town therefore fails every candidate, claims nothing, and
     * stands there — so they walk to the dig first and look for work once they
     * are on it, which is also the order a person would do it in.
     */
    private static final double APPROACH_RADIUS = 24.0;

    private static final double DIG_WALK_SPEED = 0.7;

    /** Cells a digger may try in one tick before giving up until the next. */
    private static final int CELL_ATTEMPTS = 8;

    /**
     * Failed attempts before a block is set aside as out of anyone's reach.
     *
     * <p>Some blocks have nowhere legal to stand: a spur of rock out over a drop,
     * an overhang. Standing in mid-air is not an option and neither is flying, so
     * the block is put down rather than destroyed — and, crucially, taken off the
     * column beneath it, so the dig carries on at the highest layer somebody can
     * actually get to instead of stopping dead underneath one it cannot.
     */
    private static final int STRIKES_BEFORE_DEFER = 12;

    /** How long a block nobody could reach waits before it is tried again. */
    private static final int DEFER_TICKS = 100;

    /** Most blocks one stroke of felling may bring down, so a forest is not one tree. */
    private static final int MAX_TREE_BLOCKS = 512;

    /**
     * How long a digger may hold a cell without landing a single tick of work.
     *
     * <p>A claim is renewed by being served, not by getting anywhere, so without
     * this a digger who cannot actually reach the block they were given holds the
     * cell forever and everybody else is told the job is fully staffed. This is
     * what turns that into a handover.
     */
    private static final int STUCK_TICKS = 200;

    /**
     * How far from a block a digger may stand, in squares, on each axis.
     *
     * <p>Two, not one. Adjacent is the rule and where the search starts, but a
     * block set back inside a hillside has no adjacent square that is not solid
     * rock, and insisting on one means the crew stands at the mouth of a hole
     * they are not allowed to reach into. Two squares still puts them within a
     * player's arm and still reads as working the face.
     */
    private static final int STAND_SEARCH = 2;

    /**
     * How far below a block a digger may stand and still work it.
     *
     * <p>The rule used to be that you only ever dug at or below your own feet,
     * which is sound for cutting into ground and hopeless for pulling a building
     * down: the roof of a hall is five courses up, so the only squares level with
     * it are on the roof itself, and no crew is going to path up there. Somebody
     * standing on the ground beside the hall can plainly reach its eaves — a
     * player does it without thinking — so the search now works downward too, and
     * reach is what decides it.
     *
     * <p>The dig order is untouched: blocks still come away top down, which is
     * what stops anybody undermining themselves. This only widens where you may
     * stand to take one.
     */
    private static final int STAND_REACH_DOWN = 4;

    /** Stand candidates worth paying an A* for. Ordered, so the best go first. */
    private static final int PATH_ATTEMPTS = 4;

    /**
     * Ticks a digger waits before hunting for work again after finding none.
     *
     * <p>Searching walks every cell in the job. That is nothing on a house plot
     * and real work on a thirty-cubed clearance, and a crew with nothing to do
     * would otherwise repeat it twenty times a second between them.
     */
    private static final int SEARCH_COOLDOWN = 10;

    /** What one digger is doing to one block. */
    private static final class Digging {
        BlockPos block;
        BlockPos stand;
        int ticksNeeded;
        int ticksSpent;
        int stage = -1;
        /**
         * When this block was last pathed to. Never a sentinel: the obvious
         * Long.MIN_VALUE overflows the moment you subtract it from a tick count,
         * which quietly made every walk-to-the-block test read as not due yet and
         * meant a claimed block was never actually walked to at all.
         */
        long lastPath;
        long startedAt;
        /** Whether finishing this brings a whole tree down rather than one block. */
        boolean tree;
        /** Kept so the crack overlay can be cleared for a digger who is already gone. */
        int entityId;
    }

    private final DigYard yard;
    private final String label;

    /** The middle of the job, for walking towards before there is a cell to hold. */
    private final BlockPos centre;
    private final Map<UUID, Digging> active = new HashMap<>();

    /** Stands somebody is already using, so two diggers never share a square. */
    private final Set<BlockPos> reserved = new HashSet<>();

    /** When each digger may next go looking for a cell, having found none. */
    private final Map<UUID, Long> searchAfter = new HashMap<>();

    /** When each digger was last pointed at the job, so paths are not spammed. */
    private final Map<UUID, Long> walkedAt = new HashMap<>();

    /**
     * Why diggers came away empty, since the last time anybody asked.
     *
     * <p>Kept permanently rather than as scaffolding, because every way this can
     * stall looks identical from outside: a crew still walking, a crew holding
     * cells they cannot find footing in, and a crew with no ready cell at all all
     * present as a hole that is not getting deeper. These are the only numbers
     * that tell them apart, and {@link #describe()} is where they surface.
     */
    private int walking;
    private int noCell;
    private int noStand;
    private int gaveUp;

    /** Blocks nobody could find a footing for, and how many times each has failed. */
    private final Map<BlockPos, Integer> strikes = new HashMap<>();
    private int setAside;

    /**
     * Stumps standing in the way, and what the whole tree above each is worth.
     *
     * <p>A tree is not ground and is not excavated block by block. Its crown is
     * out of reach from anywhere a person can stand, so a top-down dig could only
     * ever set the whole thing aside and build around a trunk left standing in the
     * middle of the floor. It is felled instead: one job at the stump, priced at
     * the time every log in it would take, and the tree comes down when the last
     * of that time is spent.
     */
    private final Map<BlockPos, Integer> stumps = new HashMap<>();


    public Excavation(ServerLevel level, List<SimPos> targets, String label) {
        List<SimPos> reduced = reduceTrees(level, targets);
        this.yard = new DigYard(reduced);
        this.label = label;
        // Middle of the job, but at its floor. Averaging the height too aimed the
        // approach at the middle of a column of blocks, which on a deep dig is a
        // point in mid-air that nobody can walk to.
        BlockPos middle = middleOf(reduced);
        this.centre = new BlockPos(middle.getX(), yard.floorY(), middle.getZ());
    }

    /**
     * Swaps every tree in the target list for the stump it stands on.
     *
     * <p>The rest of the tree stops being the excavation's business: felling the
     * stump takes it. This is what stops a crown ten blocks up being set aside as
     * unreachable — which it genuinely is, from anywhere a person can stand.
     */
    private List<SimPos> reduceTrees(ServerLevel level, List<SimPos> targets) {
        Set<BlockPos> handled = new HashSet<>();
        List<SimPos> reduced = new ArrayList<>(targets.size());
        for (SimPos target : targets) {
            BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
            if (handled.contains(pos)) {
                continue;   // already coming down with a tree we have seen
            }
            if (!isTree(level.getBlockState(pos))) {
                reduced.add(target);
                continue;
            }
            BlockPos stump = stumpUnder(level, pos);
            Set<BlockPos> tree = gatherTree(level, stump);
            handled.addAll(tree);

            int ticks = 0;
            for (BlockPos part : tree) {
                BlockState state = level.getBlockState(part);
                if (state.is(BlockTags.LOGS)) {
                    ticks += digTicks(level, part, state);
                }
            }
            stumps.put(stump, Math.max(1, ticks));
            reduced.add(new SimPos(stump.getX(), stump.getY(), stump.getZ()));
        }
        return reduced;
    }

    private static boolean isTree(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    /** The bottom of the trunk this block belongs to, which is where an axe goes. */
    private static BlockPos stumpUnder(ServerLevel level, BlockPos pos) {
        BlockPos stump = pos;
        // Down through the leaves first, if that is where we came in, then down
        // the trunk itself until there is ground under it.
        while (!level.getBlockState(stump).is(BlockTags.LOGS)
                && level.getBlockState(stump.below()).is(BlockTags.LOGS)) {
            stump = stump.below();
        }
        while (level.getBlockState(stump.below()).is(BlockTags.LOGS)) {
            stump = stump.below();
        }
        return stump;
    }

    /**
     * Everything that comes down with one tree.
     *
     * <p>Logs and leaves together, out from the stump. Leaves bridge to a
     * neighbour in a close-grown wood, so this is capped: felling two trees at
     * once is a fair outcome, felling the forest is not.
     */
    private static Set<BlockPos> gatherTree(ServerLevel level, BlockPos stump) {
        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        found.add(stump);
        queue.add(stump);
        while (!queue.isEmpty() && found.size() < MAX_TREE_BLOCKS) {
            BlockPos at = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = at.offset(dx, dy, dz);
                        if (found.contains(next) || !level.isLoaded(next)
                                || !isTree(level.getBlockState(next))) {
                            continue;
                        }
                        found.add(next);
                        queue.add(next);
                        if (found.size() >= MAX_TREE_BLOCKS) {
                            return found;
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Brings a whole tree down at the stump. */
    private void fell(ServerLevel level, BlockPos stump) {
        for (BlockPos part : gatherTree(level, stump)) {
            if (part.equals(stump)) {
                continue;   // the stump itself is retired by the caller
            }
            if (!level.getBlockState(part).isAir()) {
                level.destroyBlock(part, false, null, 512);
            }
            yard.remove(toSim(part));   // harmless if it was never wanted
        }
    }

    /** The middle of a job, which is where a digger walks before choosing a face. */
    static BlockPos middleOf(List<SimPos> targets) {
        if (targets.isEmpty()) {
            return BlockPos.ZERO;
        }
        long x = 0;
        long y = 0;
        long z = 0;
        for (SimPos target : targets) {
            x += target.x();
            y += target.y();
            z += target.z();
        }
        int count = targets.size();
        return new BlockPos((int) (x / count), (int) (y / count), (int) (z / count));
    }

    /** How many trees stand in the way, each felled by one stroke at its stump. */
    public int treeCount() {
        return stumps.size();
    }

    /** What this dig is for, for status lines and logs. */
    public String label() {
        return label;
    }

    /**
     * One line on how the hole is getting on, and on what is holding it up.
     *
     * <p>The tallies are since this was last called, so reading it repeatedly
     * gives a rate rather than a running total.
     */
    public String describe() {
        int ready = 0;
        for (SimPos block : yard.remainingBlocks()) {
            if (yard.isReady(DigYard.cellOf(block))) {
                ready++;
            }
        }
        String out = label + " " + cleared() + "/" + total() + " dug, "
                + active.size() + " at the face, " + ready + " exposed"
                + " (walking " + walking + ", no cell " + noCell
                + ", no stand " + noStand + ", gave up " + gaveUp
                + ", set aside " + yard.deferredCount()
                + ", abandoned " + yard.abandonedCount() + ")";
        walking = 0;
        noCell = 0;
        noStand = 0;
        gaveUp = 0;
        return out;
    }

    public int total() {
        return yard.total();
    }

    public int cleared() {
        return yard.cleared();
    }

    public int remaining() {
        return yard.remaining();
    }

    /** Blocks the job gave up on because nobody could ever stand to work them. */
    public int abandonedCount() {
        return yard.abandonedCount();
    }

    public boolean isComplete() {
        return yard.isComplete();
    }

    // --- the tick ---

    /**
     * One tick of one digger.
     *
     * @return true if they are engaged with this dig, walking to it included
     */
    public boolean serve(ServerLevel level, PersonEntity digger, long tick) {
        yard.reconsider(tick);
        UUID id = digger.getUUID();
        Digging job = active.get(id);

        if (job != null && !stillWanted(level, job)) {
            // Somebody else got there, or it fell, or a player mined it.
            harvest(level, digger, job);
            job = null;
        }
        if (job == null) {
            if (digger.distanceToSqr(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5)
                    > APPROACH_RADIUS * APPROACH_RADIUS) {
                walking++;
                approach(digger, tick);
                return true;
            }
            Long waitUntil = searchAfter.get(id);
            if (waitUntil != null && tick < waitUntil) {
                return false;   // nothing for them a moment ago; do not rescan yet
            }
            job = nextJob(level, digger, tick);
            if (job == null) {
                noCell++;
                searchAfter.put(id, tick + SEARCH_COOLDOWN);
                return false;
            }
            searchAfter.remove(id);
        }
        yard.touch(id, tick);

        double toBlock = digger.distanceToSqr(
                job.block.getX() + 0.5, job.block.getY() + 0.5, job.block.getZ() + 0.5);
        if (toBlock > DIG_REACH * DIG_REACH) {
            if (tick - job.startedAt > STUCK_TICKS) {
                // They have had long enough. Hand the cell back rather than sit on
                // it: somebody standing somewhere else may have a clear run at it.
                gaveUp++;
                abandon(level, digger, job, tick);
                return false;
            }
            // Not there yet. Progress resets while they are away, exactly as it
            // does for a player who looks off the block mid-swing.
            showProgress(level, digger, job, -1);
            job.ticksSpent = 0;
            if (tick - job.lastPath >= REPATH_INTERVAL) {
                job.lastPath = tick;
                digger.getNavigation().moveTo(job.stand.getX() + 0.5, job.stand.getY(),
                        job.stand.getZ() + 0.5, DIG_WALK_SPEED);
            }
            return true;
        }

        return swing(level, digger, job, tick);
    }

    /**
     * Walks a digger towards the job.
     *
     * <p>Re-issued rather than set once, because a path across a village comes
     * back partial: navigation gets as far as it can see and stops, and asking
     * again from there is what carries them the rest of the way.
     */
    private void approach(PersonEntity digger, long tick) {
        Long last = walkedAt.get(digger.getUUID());
        if (last != null && tick - last < REPATH_INTERVAL) {
            return;
        }
        walkedAt.put(digger.getUUID(), tick);
        digger.getNavigation().moveTo(
                centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, DIG_WALK_SPEED);
    }

    /** One tick of actual digging, once they are in position. */
    private boolean swing(ServerLevel level, PersonEntity digger, Digging job, long tick) {
        BlockState state = level.getBlockState(job.block);
        digger.getNavigation().stop();
        digger.getLookControl().setLookAt(job.block.getX() + 0.5,
                job.block.getY() + 0.5, job.block.getZ() + 0.5);
        hold(digger, toolFor(state));

        job.ticksSpent++;
        if (job.ticksSpent % SWING_INTERVAL == 1) {
            digger.swing(InteractionHand.MAIN_HAND);
        }
        if (job.ticksSpent % SOUND_INTERVAL == 0) {
            level.playSound(null, job.block, state.getSoundType().getHitSound(),
                    SoundSource.BLOCKS, 0.25F, 0.5F);
        }
        showProgress(level, digger, job,
                Math.min(9, job.ticksSpent * 10 / Math.max(1, job.ticksNeeded)));

        if (job.ticksSpent < job.ticksNeeded) {
            return true;
        }
        showProgress(level, digger, job, -1);
        // No drops. There is nowhere for spoil to go yet, and a site knee-deep in
        // dirt items is worse than no spoil at all. That includes the plant
        // standing ON the block: destroyBlock keeps the dug block quiet but
        // still updates its neighbours, so the grass above popped off as a seed
        // item every time — the source of a mysterious drizzle of leaf
        // litter, kelp and wheat seeds over every worksite in town.
        clearPlantAbove(level, job.block);
        if (job.tree) {
            fell(level, job.block);
            stumps.remove(job.block);
        }
        level.destroyBlock(job.block, false, digger, 512);
        harvest(level, digger, job);
        return true;
    }

    /** Retires a block from the job and frees what the digger was holding for it. */
    private void harvest(ServerLevel level, PersonEntity digger, Digging job) {
        showProgress(level, digger, job, -1);
        strikes.remove(job.block);
        yard.remove(toSim(job.block));
        reserved.remove(job.stand);
        active.remove(digger.getUUID());
    }

    /** Puts a block back on the pile, untouched, and lets the cell go. */
    private void abandon(ServerLevel level, PersonEntity digger, Digging job, long tick) {
        showProgress(level, digger, job, -1);
        reserved.remove(job.stand);
        active.remove(digger.getUUID());
        yard.release(digger.getUUID());
        searchAfter.put(digger.getUUID(), tick + SEARCH_COOLDOWN);
    }

    private boolean stillWanted(ServerLevel level, Digging job) {
        return yard.contains(toSim(job.block)) && !level.getBlockState(job.block).isAir();
    }

    private void showProgress(ServerLevel level, PersonEntity digger, Digging job, int stage) {
        if (job.stage == stage) {
            return;   // the overlay only needs telling when the crack deepens
        }
        job.stage = stage;
        level.destroyBlockProgress(digger.getId(), job.block, stage);
    }

    // --- picking work ---

    /** Finds this digger something to do: their current cell, or a new one. */
    private Digging nextJob(ServerLevel level, PersonEntity digger, long tick) {
        UUID id = digger.getUUID();
        DigYard.Cell held = yard.claimOf(id);
        if (held != null) {
            Digging job = assign(level, digger, held, tick);
            if (job != null) {
                return job;
            }
            yard.release(id);   // nothing left in it we can actually get at
        }
        List<DigYard.Cell> open = yard.openCells(id, toSim(digger.blockPosition()), tick);
        int tried = 0;
        for (DigYard.Cell cell : open) {
            if (tried++ >= CELL_ATTEMPTS) {
                break;
            }
            if (!yard.claim(cell, id, tick)) {
                continue;
            }
            Digging job = assign(level, digger, cell, tick);
            if (job != null) {
                return job;
            }
            yard.release(id);
        }
        return null;
    }

    /** Picks a block out of a claimed cell and works out where to stand for it. */
    private Digging assign(ServerLevel level, PersonEntity digger, DigYard.Cell cell, long tick) {
        List<BlockPos> blocks = new ArrayList<>();
        for (SimPos block : yard.blocksIn(cell)) {
            blocks.add(new BlockPos(block.x(), block.y(), block.z()));
        }
        blocks.sort(Comparator.comparingDouble(block ->
                digger.distanceToSqr(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5)));

        for (BlockPos block : blocks) {
            BlockState state = level.getBlockState(block);
            if (state.isAir()) {
                yard.remove(toSim(block));   // gone without us; stop wanting it
                continue;
            }
            if (!yard.isExposed(toSim(block))) {
                continue;   // still has ground over it
            }
            BlockPos stand = standFor(level, digger, block);
            if (stand == null) {
                noStand++;
                if (strikes.merge(block, 1, Integer::sum) >= STRIKES_BEFORE_DEFER) {
                    // Out of reach of anybody, from anywhere. Put it down for a
                    // while: that frees its cell, and lifts it off the column
                    // underneath so the dig can get on at a layer people can reach.
                    yard.defer(toSim(block), tick + DEFER_TICKS);
                    strikes.remove(block);
                    setAside++;
                }
                continue;
            }
            Digging job = new Digging();
            job.block = block;
            job.stand = stand;
            job.entityId = digger.getId();
            job.startedAt = tick;
            job.lastPath = tick - REPATH_INTERVAL;   // due immediately
            Integer treeTicks = stumps.get(block);
            job.ticksNeeded = treeTicks != null ? treeTicks : digTicks(level, block, state);
            job.tree = treeTicks != null;
            reserved.add(stand);
            active.put(digger.getUUID(), job);
            return job;
        }
        return null;
    }

    /**
     * Every square a digger could stand on to work this block, best first.
     *
     * <p>Separated from {@link #standFor} because everything here is geometry
     * and an ordering, and neither needs a world: what a square must be like to
     * be usable arrives as a predicate, and the last tiebreak — how far the
     * digger has to walk — arrives as a measure. That leaves the part worth
     * testing testable, while the part that genuinely needs the game's own
     * pathfinder stays where it is.
     *
     * <p>Beside the block, never on it and never in it. Feet are level with the
     * block or one above, so a digger only ever works at or below their own feet
     * — the other half of why nobody drops themselves down a hole: the block
     * coming out is never the one they are standing on.
     *
     * <p>Ordered level with the block first, then a course lower and so on down;
     * within a level the adjacent square, and within that the shortest walk.
     * Ordering purely on closeness to the block sent people round the far side
     * of a mound for a square a foot nearer the target, and a walk like that
     * outlasts the patience that hands the cell to somebody else.
     */
    static List<BlockPos> standCandidates(BlockPos block,
                                          java.util.function.Predicate<BlockPos> usable,
                                          java.util.function.ToDoubleFunction<BlockPos> walk) {
        List<BlockPos> candidates = new ArrayList<>(32);
        for (int feetY = block.getY() + 1; feetY >= block.getY() - STAND_REACH_DOWN; feetY--) {
            for (int dx = -STAND_SEARCH; dx <= STAND_SEARCH; dx++) {
                for (int dz = -STAND_SEARCH; dz <= STAND_SEARCH; dz++) {
                    if (dx == 0 && dz == 0) {
                        // The block's own column, at any height: inside it,
                        // under it, or on top of it. The first two are obvious;
                        // the third was not excluded, and since standing on top
                        // is zero blocks from the target it sorted ahead of
                        // every square beside it — so the rule this method
                        // documents, and the "last resort" standFor adds
                        // afterwards, were both quietly inverted. On top is a
                        // real fallback and standFor still offers it; it is not
                        // the first thing to try.
                        continue;
                    }
                    BlockPos feet = new BlockPos(block.getX() + dx, feetY, block.getZ() + dz);
                    if (!reaches(feet, block) || !usable.test(feet)) {
                        continue;
                    }
                    candidates.add(feet);
                }
            }
        }
        candidates.sort(Comparator
                .comparingInt((BlockPos feet) -> Math.abs(feet.getY() - (block.getY() + 1)))
                .thenComparingInt(feet -> Math.max(
                        Math.abs(feet.getX() - block.getX()),
                        Math.abs(feet.getZ() - block.getZ())))
                .thenComparingDouble(walk));
        return candidates;
    }

    /**
     * Somewhere to stand to dig this block.
     *
     * <p>Beside it, never on it and never in it. Feet are level with the block or
     * one above, so a digger only ever works at or below their own feet — which is
     * the other half of why nobody drops themselves down a hole: the block coming
     * out is never the one they are standing on.
     *
     * <p>Candidates are filtered on cheap geometry first and only then handed to
     * the navigator, because A* over eight squares per block would cost more than
     * the digging does.
     */
    private BlockPos standFor(ServerLevel level, PersonEntity digger, BlockPos block) {
        List<BlockPos> candidates = standCandidates(block,
                feet -> !reserved.contains(feet)
                        && fits(level, feet) && hasFooting(level, feet),
                feet -> digger.distanceToSqr(
                        feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));

        // Last resort: on top of the block itself. Standing beside it is the rule
        // and this breaks it, but a spur of rock with nothing but air around it
        // has no neighbouring square to stand on, and refusing to dig it at all
        // strands the whole excavation on one block. Dropping the height of the
        // block you were standing on is not a fall worth avoiding.
        BlockPos above = block.above();
        if (!reserved.contains(above) && fits(level, above)) {
            candidates.add(above);
        }

        int paths = 0;
        for (BlockPos feet : candidates) {
            if (within(digger, feet, DIG_REACH)) {
                return feet;   // already close enough to walk the last step unaided
            }
            if (paths++ >= PATH_ATTEMPTS) {
                continue;
            }
            // Accuracy of one block: navigation habitually stops a step short of
            // an exact square, and demanding the exact square rejected stands the
            // digger was standing next to.
            Path route = digger.getNavigation().createPath(feet, 1);
            if (route != null && route.canReach()) {
                return feet;
            }
        }
        return null;
    }

    /**
     * Whether somebody standing here could actually put a tool on that block.
     *
     * <p>Measured from the eyes rather than the feet, which is why a digger can
     * reach further up than down: the whole reason diggers stand beside a block
     * and never in it is that this has to be answerable before anybody walks.
     */
    static boolean reaches(BlockPos feet, BlockPos block) {
        double dx = (feet.getX() + 0.5) - (block.getX() + 0.5);
        double dy = (feet.getY() + PersonEntity.EYE_ABOVE_FEET) - (block.getY() + 0.5);
        double dz = (feet.getZ() + 0.5) - (block.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= DIG_REACH * DIG_REACH;
    }

    private static boolean within(PersonEntity digger, BlockPos pos, double reach) {
        return digger.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                <= reach * reach;
    }

    /** Room for a body: two clear blocks. */
    private static boolean fits(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    /** Something solid under the feet. Standing in mid-air is not a stand. */
    private static boolean hasFooting(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    // --- timing and tools ---

    /**
     * How much longer a settler takes over a block than you would.
     *
     * <p>Vanilla's number is the time a player takes with a fresh tool and
     * somewhere to be. A settler has a shared tool, no hurry, and six colleagues
     * on the same face — and at parity a hillside site simply evaporated, which
     * read as the ground being deleted rather than dug.
     *
     * <p>Two rather than three, deliberately. Watched digging is not on the
     * clock: an embodied crew suppresses the abstract builder, and the stall
     * guard measures whether the head of the queue <em>moved</em>, not how fast
     * — so a crew making slow honest progress never trips it. Slowing the dig
     * therefore slows a watched town's building outright, which is the same
     * shape as the bug where standing and watching a town starved it. Two is a
     * step toward labour reading as labour with room left to go further once a
     * run has been watched over it.
     */
    public static final int LABOUR_FACTOR = 2;

    /**
     * How many ticks this block takes, for a digger holding the right tool.
     *
     * <p>Vanilla's arithmetic: tool speed over hardness, divided by 30 when the
     * tool can harvest the block and 100 when it cannot — then multiplied by
     * {@link #LABOUR_FACTOR}, which is the only fudge in it. There is still no
     * cap. Obsidian genuinely takes a digger longer than it takes you, which is
     * the point: a town that wants to build on an obsidian outcrop can spend
     * the afternoon on it.
     */
    public static int digTicks(ServerLevel level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) {
            return Integer.MAX_VALUE;   // bedrock; excavation never schedules these
        }
        if (hardness == 0) {
            return LABOUR_FACTOR;   // grass and litter: brief, but not instant
        }
        ItemStack tool = new ItemStack(toolFor(state));
        float speed = tool.getDestroySpeed(state);
        boolean harvests = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        float perTick = speed / hardness / (harvests ? 30.0F : 100.0F);
        int ticks = perTick >= 1.0F ? 1 : (int) Math.ceil(1.0F / perTick);
        return ticks * LABOUR_FACTOR;
    }

    /**
     * The right tool for shifting this block.
     *
     * <p>Diggers are handed it rather than having to own it — there is no tool
     * economy yet — but it is the correct one, so the time it takes is the time it
     * would take you holding the same thing.
     */
    public static Item toolFor(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return Items.IRON_SHOVEL;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return Items.IRON_AXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return Items.IRON_HOE;
        }
        return Items.IRON_PICKAXE;
    }

    private static void hold(PersonEntity digger, Item item) {
        if (digger.getMainHandItem().is(item)) {
            return;
        }
        digger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        // Tools are scenery, not loot — a digger killed on site must not shower
        // the ground with the shovel they happened to be issued.
        digger.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    // --- housekeeping ---

    /** Lets go of everything this digger held, on death, release or reassignment. */
    public void forget(ServerLevel level, PersonEntity digger) {
        forget(level, digger.getUUID());
    }

    /**
     * As above, for somebody whose entity has already gone.
     *
     * <p>The half-dug block still has a crack drawn on it in every watching
     * client, and clearing that needs the id of the digger it was attributed to
     * — which is why the id is kept on the job rather than read off the entity.
     */
    public void forget(ServerLevel level, UUID digger) {
        Digging job = active.remove(digger);
        if (job != null) {
            level.destroyBlockProgress(job.entityId, job.block, -1);
            reserved.remove(job.stand);
        }
        searchAfter.remove(digger);
        walkedAt.remove(digger);
        yard.release(digger);
    }

    /**
     * Empties the whole job at once.
     *
     * <p>For {@code /civ step}, where no game ticks pass and so nothing can be
     * dug a tick at a time. Everything else goes through {@link #serve}.
     */
    public void flush(ServerLevel level) {
        for (SimPos block : yard.remainingBlocks()) {
            BlockPos pos = new BlockPos(block.x(), block.y(), block.z());
            if (!level.getBlockState(pos).isAir()) {
                clearPlantAbove(level, pos);
                level.destroyBlock(pos, false, null, 512);
            }
            yard.remove(block);
        }
        active.clear();
        reserved.clear();
    }

    /** Removes a supported plant before its support, so it cannot drop. */
    private static void clearPlantAbove(ServerLevel level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState overhead = level.getBlockState(above);
        if (!overhead.isAir() && overhead.canBeReplaced()) {
            level.setBlock(above, net.minecraft.world.level.block.Blocks.AIR
                    .defaultBlockState(), 2);
        }
    }

    private static SimPos toSim(BlockPos pos) {
        return new SimPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
