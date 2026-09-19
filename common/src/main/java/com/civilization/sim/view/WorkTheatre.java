package com.civilization.sim.view;

import com.civilization.sim.geom.Mix;
import com.civilization.sim.person.Profession;

import java.util.UUID;

/**
 * When a trade is worth making a noise about.
 *
 * <p>The town's outdoor work already sounds like something, because felling a
 * tree and cutting a seam go through {@code destroyBlock} and vanilla makes the
 * noise for free. Everything under a roof was silent: a smithy with a blast
 * furnace, an anvil and a grindstone in it that nobody ever struck, a mill that
 * ground nothing anybody could hear, a carpentry with a bench and no sound of a
 * bench. A settler at a workplace was a body standing at a workplace, which is
 * exactly the "switched off" reading that {@code Leisure} was written to cure
 * for the idle half of the town and had never been cured for the busy half.
 *
 * <p><strong>None of it is worth anything.</strong> A strike here is a sound,
 * an arm swing and a few particles. It moves no goods, takes nothing off a
 * shelf, adds nothing to a yield and is not counted anywhere. A watched town
 * and an unwatched town produce identical books, and that is not a tuning
 * decision — it is the one asymmetry between the two fidelities this mod does
 * not allow, and the reason this class is a <em>gate</em> rather than a step:
 * it reads {@code buildingHasWork} off the planners' own ledgers and never
 * writes to them.
 *
 * <p>Here in {@code common} for {@code Leisure}'s reason: whether somebody
 * strikes is a question about a distance, a clock and a boolean, and the
 * platform's half is finding the anvil and playing the sound.
 */
public final class WorkTheatre {

    private WorkTheatre() {
    }

    // --- what it costs --------------------------------------------------------

    /**
     * How near a player has to be for a trade to be worth playing: 32 blocks.
     *
     * <p>Half the chimneys' range, and shorter on purpose. Smoke over a roof is
     * a thing you see from across a valley; a hammer on an anvil is a thing you
     * hear from the next street. Beyond this the sound would arrive as a noise
     * from nowhere, which is worse than silence.
     */
    public static final double WATCH_RANGE = 32.0;

    /**
     * How near the workplace somebody has to be standing to be working at it:
     * 4 blocks.
     *
     * <p>Wider than a bench ({@code Pastimes.ARRIVE} is two) and far tighter
     * than the eight the work steering calls "somewhere about the place". A
     * smith eight blocks from his forge is in the street outside it, and a
     * hammer ringing out of the street is the bug this measurement exists to
     * avoid; a smith four blocks from the origin of a smithy is inside it.
     */
    public static final double AT_WORK = 4.0;

    /**
     * The shortest gap between two strikes: 40 ticks, two seconds.
     *
     * <p>A blacksmith swings faster than this and it does not matter: what is
     * being drawn is that somebody is working, and a hammer every second from
     * every forge in a town of sixty is a town that sounds like a factory. Two
     * seconds reads as work; half a second reads as a stuck sound.
     */
    public static final int MIN_GAP_TICKS = 40;

    /** The longest: 80 ticks, four seconds. Past that the forge reads as cold. */
    public static final int MAX_GAP_TICKS = 80;

    /** A worker who has never struck. */
    public static final long NEVER = Long.MIN_VALUE;

    // --- what there is to hear -------------------------------------------------

    /**
     * The trades that have something to strike.
     *
     * <p>Not a list of professions: it is a list of <em>pictures</em>, and the
     * platform reads each one as a sound, a particle and a place to stand. What
     * decides membership is whether the trade has a workplace with a thing in it
     * that makes a noise — which is why the guard, the trader and the king are
     * not here and why the farmer, the lumberjack and the miner are not either:
     * their work is already audible, because it is real blocks breaking.
     */
    public enum Trade {

        /** The anvil, and the hottest thing in any town. */
        SMITH,

        /** The bench: a saw, a chisel and a mallet. */
        CARPENTER,

        /** The millstone turning over. */
        MILLER
    }

    /** Which trade's picture this profession plays, or null for somebody silent. */
    public static Trade tradeOf(Profession what) {
        if (what == null) {
            return null;
        }
        return switch (what) {
            case SMITH -> Trade.SMITH;
            case CARPENTER -> Trade.CARPENTER;
            case MILLER -> Trade.MILLER;
            default -> null;
        };
    }

    // --- the gate ---------------------------------------------------------------

    /**
     * Everything about one worker that decides whether they strike now.
     *
     * <p>{@code Leisure.Idleness}'s shape and its argument: three negatives and
     * a clock spread over a steering loop is how a forge comes to ring in an
     * empty town. All four are the platform's to answer.
     *
     * @param watched          a player is inside {@link #WATCH_RANGE} of them
     * @param atWorkplace      they are standing at the building of their trade
     * @param buildingHasWork  the trade's own ledger says there is work in front
     *                         of it — {@code SmithPlanner.hasWorkInFront},
     *                         {@code FoodPlanner.millHasWork}, a queued build for
     *                         the carpentry. Read, never written.
     * @param embodied         they have a body at all; a record has no arm
     */
    public record Scene(boolean watched, boolean atWorkplace, boolean buildingHasWork,
                        boolean embodied) {
    }

    /**
     * Whether this worker strikes on this tick.
     *
     * <p>Every clause is a refusal and the order does not matter, which is the
     * point of them being in one expression. An unwatched forge is silent
     * because nobody is there to hear it and because a sound that costs a packet
     * for nobody is a cost with no picture. A smith walking to the forge is
     * silent because a hammer from a man in the road is nonsense. A forge with no
     * iron is silent because <em>that</em> is the one this whole thing is
     * about — the theatre must never say the town is working when its books say
     * it is not, or a player would learn to read the noise as production and the
     * noise is not production.
     *
     * @param last the tick this worker last struck, or {@link #NEVER}
     * @param gap  their own interval, from {@link #gapFor}
     */
    public static boolean strikes(Scene scene, long last, long now, long gap) {
        if (!scene.embodied() || !scene.watched() || !scene.atWorkplace()
                || !scene.buildingHasWork()) {
            return false;
        }
        return last == NEVER || now - last >= gap;
    }

    /**
     * How long this worker waits between strikes.
     *
     * <p>Deterministic in the person and nothing else, so two smiths at one
     * forge fall out of step and stay out of step rather than hammering in
     * unison — which is the difference between a smithy and a drum. The same
     * mixer everything else in the mod picks with, for the same reason: ids
     * allotted in sequence share their high bits, and reading them raw would
     * hand a whole town one interval.
     */
    public static long gapFor(UUID who) {
        if (who == null) {
            return MIN_GAP_TICKS;
        }
        int span = MAX_GAP_TICKS - MIN_GAP_TICKS + 1;
        return MIN_GAP_TICKS + Math.floorMod(Mix.of(
                who.getMostSignificantBits() ^ who.getLeastSignificantBits() * 31L), span);
    }
}
