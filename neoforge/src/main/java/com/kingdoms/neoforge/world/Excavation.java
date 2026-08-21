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
     * Failed attempts before a block is written off as out of anyone's reach.
     *
     * <p>Some blocks have nowhere legal to stand: the top of a tree trunk with
     * ten blocks of air round it, a spur of rock over a drop. Standing in mid-air
     * is not an option and neither is flying, so the honest answer is that the
     * town leaves it — and the excavation has to be able to say so, because a
     * cell that can never be finished otherwise holds up the whole job forever.
     * A written-off block inside the walls is covered by the masonry anyway; one
     * out on the apron is a stump somebody can fell later.
     */
    private static final int STRIKES_BEFORE_WRITE_OFF = 20;

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

    /** Stand candidates worth paying an A* for. Ordered, so the best go first. */
    private static final int PATH_ATTEMPTS = 3;

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
    private int writtenOff;


    public Excavation(List<SimPos> targets, String label) {
        this.yard = new DigYard(targets);
        this.label = label;
        this.centre = middleOf(targets);
    }

    private static BlockPos middleOf(List<SimPos> targets) {
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
                + ", written off " + writtenOff + ")";
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
        // dirt items is worse than no spoil at all.
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
                if (strikes.merge(block, 1, Integer::sum) >= STRIKES_BEFORE_WRITE_OFF) {
                    // Out of reach of anybody, from anywhere. Stop wanting it, or
                    // it holds its cell — and through the cell, the excavation.
                    yard.remove(toSim(block));
                    strikes.remove(block);
                    writtenOff++;
                }
                continue;
            }
            Digging job = new Digging();
            job.block = block;
            job.stand = stand;
            job.entityId = digger.getId();
            job.startedAt = tick;
            job.lastPath = tick - REPATH_INTERVAL;   // due immediately
            job.ticksNeeded = digTicks(level, block, state);
            reserved.add(stand);
            active.put(digger.getUUID(), job);
            return job;
        }
        return null;
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
        List<BlockPos> candidates = new ArrayList<>(16);
        for (int feetY : new int[]{block.getY() + 1, block.getY()}) {
            for (int dx = -STAND_SEARCH; dx <= STAND_SEARCH; dx++) {
                for (int dz = -STAND_SEARCH; dz <= STAND_SEARCH; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;   // never inside the block itself
                    }
                    BlockPos feet = new BlockPos(block.getX() + dx, feetY, block.getZ() + dz);
                    if (reserved.contains(feet) || !reaches(feet, block)
                            || !fits(level, feet) || !hasFooting(level, feet)) {
                        continue;
                    }
                    candidates.add(feet);
                }
            }
        }
        // Adjacent squares first, and within those the one nearest the digger.
        // Sorting purely on closeness to the block sent people round the far side
        // of a mound to a square a foot nearer the target, and a walk like that
        // outlasts the patience that hands the cell to somebody else.
        candidates.sort(Comparator
                .comparingInt((BlockPos feet) -> Math.max(
                        Math.abs(feet.getX() - block.getX()),
                        Math.abs(feet.getZ() - block.getZ())))
                .thenComparingDouble(feet -> digger.distanceToSqr(
                        feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5)));

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

    /** Whether somebody standing here could actually put a tool on that block. */
    private static boolean reaches(BlockPos feet, BlockPos block) {
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
     * How many ticks this block takes, for a digger holding the right tool.
     *
     * <p>Vanilla's arithmetic verbatim: tool speed over hardness, divided by 30
     * when the tool can harvest the block and 100 when it cannot. There is no
     * fudge factor and no cap. Obsidian genuinely takes a digger as long as it
     * takes you, which is the point — a town that wants to build on an obsidian
     * outcrop can spend the afternoon on it.
     */
    public static int digTicks(ServerLevel level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) {
            return Integer.MAX_VALUE;   // bedrock; excavation never schedules these
        }
        if (hardness == 0) {
            return 1;
        }
        ItemStack tool = new ItemStack(toolFor(state));
        float speed = tool.getDestroySpeed(state);
        boolean harvests = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        float perTick = speed / hardness / (harvests ? 30.0F : 100.0F);
        return perTick >= 1.0F ? 1 : (int) Math.ceil(1.0F / perTick);
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
                level.destroyBlock(pos, false, null, 512);
            }
            yard.remove(block);
        }
        active.clear();
        reserved.clear();
    }

    private static SimPos toSim(BlockPos pos) {
        return new SimPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
