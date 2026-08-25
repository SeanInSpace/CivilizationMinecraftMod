package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.entity.PersonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One settler taking one block apart with their hands, in real time.
 *
 * <p>{@link Excavation} already does this properly, but only for a building
 * site: it owns a yard of cells, reserves standing squares, and shares a crew
 * across a plot. A lumberjack at a trunk and a miner at a rock face need none of
 * that — they pick their own target and walk to it themselves — but they do need
 * the part that makes digging read as work rather than as deletion.
 *
 * <p>Both of them used to call {@code destroyBlock} the instant they arrived.
 * One swing of the arm and a whole log was gone. That is the same
 * disappearing-block problem the excavation was rebuilt to fix, left standing in
 * two places because they happened not to go through the excavation.
 *
 * <p>So this is the strike loop and nothing else. Time comes from
 * {@link Excavation#digTicks}, which is vanilla's own break arithmetic for the
 * right tool, so an oak log takes a settler what it takes you. Progress goes out
 * as the same crack overlay a player sees, the arm swings on the beat of the
 * animation, and the block ticks its hit sound.
 *
 * <p><strong>The caller keeps the block.</strong> This never destroys anything.
 * It reports the tick the block finally gives, and what happens then — timber to
 * the camp, stone to the store, a tally recorded — belongs to the worker who
 * asked, not here.
 *
 * <p>Ticked every tick, like the excavation and for the same reason: hardness is
 * measured in ticks, and anything coarser cannot reproduce the time a player
 * would spend on the same block with the same tool.
 */
public final class HandDig {

    /** Ticks between arm swings. Matches the length of the swing animation. */
    private static final int SWING_INTERVAL = 6;

    /** Ticks between hit sounds, so a long dig is audible without being a drum roll. */
    private static final int SOUND_INTERVAL = 4;

    private HandDig() {
    }

    /** What one settler is currently working on, and how far in they are. */
    private static final class Bite {
        BlockPos at;
        int needed;
        int spent;
        int stage = -1;

        Bite(BlockPos at, int needed) {
            this.at = at;
            this.needed = needed;
        }
    }

    private static final Map<UUID, Bite> BITES = new HashMap<>();

    /**
     * Spends one tick working at this block.
     *
     * <p>Switching target starts again from nothing, which is correct: a settler
     * who walks off a half-dug trunk has not banked the swings they took at it.
     *
     * @return true on the single tick the block gives way, and never again for
     *         that block unless it is aimed at afresh
     */
    public static boolean strike(ServerLevel level, PersonEntity worker, BlockPos target) {
        UUID id = worker.getUUID();
        BlockState state = level.getBlockState(target);
        if (state.isAir()) {
            stop(level, worker);
            return false;
        }
        int needed = Excavation.digTicks(level, target, state);
        if (needed == Integer.MAX_VALUE) {
            stop(level, worker);
            return false;   // bedrock and its kind: nobody is getting through it
        }

        Bite bite = BITES.get(id);
        if (bite == null || !bite.at.equals(target)) {
            if (bite != null) {
                clearOverlay(level, worker, bite);
            }
            bite = new Bite(target, needed);
            BITES.put(id, bite);
        }
        bite.needed = needed;   // a tool handed over mid-dig changes the price

        bite.spent++;
        worker.getLookControl().setLookAt(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (bite.spent % SWING_INTERVAL == 1 || SWING_INTERVAL == 1) {
            worker.swing(InteractionHand.MAIN_HAND);
        }
        if (bite.spent % SOUND_INTERVAL == 0) {
            level.playSound(null, target, state.getSoundType().getHitSound(),
                    SoundSource.BLOCKS, 0.25F, 0.7F);
        }

        if (bite.spent < bite.needed) {
            showProgress(level, worker, bite,
                    (int) (10.0 * bite.spent / Math.max(1, bite.needed)));
            return false;
        }
        clearOverlay(level, worker, bite);
        BITES.remove(id);
        return true;
    }

    /** Gives up on whatever this settler was digging, and takes the cracks off it. */
    public static void stop(ServerLevel level, PersonEntity worker) {
        Bite bite = BITES.remove(worker.getUUID());
        if (bite != null) {
            clearOverlay(level, worker, bite);
        }
    }

    /** How far through its current block this settler is, 0 to 1. For callers that show it. */
    public static double progress(PersonEntity worker) {
        Bite bite = BITES.get(worker.getUUID());
        return bite == null ? 0 : Math.min(1.0, (double) bite.spent / Math.max(1, bite.needed));
    }

    /** The block this settler is part-way into, or null if they are not digging. */
    public static BlockPos targetOf(PersonEntity worker) {
        Bite bite = BITES.get(worker.getUUID());
        return bite == null ? null : bite.at;
    }

    /** Whether this settler is part-way into a block right now. */
    public static boolean isWorking(PersonEntity worker, BlockPos at) {
        Bite bite = BITES.get(worker.getUUID());
        return bite != null && bite.at.equals(at);
    }

    /**
     * Forgets every dig in progress.
     *
     * <p>Session-scoped by nature: these are entity ids and block positions in a
     * world about to close, and a crack overlay outlives neither.
     */
    public static void forget() {
        BITES.clear();
    }

    private static void showProgress(ServerLevel level, PersonEntity worker, Bite bite, int stage) {
        if (bite.stage == stage) {
            return;   // the overlay only needs telling when the crack deepens
        }
        bite.stage = stage;
        level.destroyBlockProgress(worker.getId(), bite.at, stage);
    }

    private static void clearOverlay(ServerLevel level, PersonEntity worker, Bite bite) {
        if (bite.stage >= 0) {
            level.destroyBlockProgress(worker.getId(), bite.at, -1);
        }
    }
}
