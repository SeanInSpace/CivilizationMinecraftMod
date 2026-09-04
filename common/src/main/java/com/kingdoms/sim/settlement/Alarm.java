package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Profession;

/**
 * How worried a town is, and what it does about it.
 *
 * <p>There used to be one rule: any hostile at all inside the claim and every
 * civilian ran home. Two things were wrong with that. A single wandering
 * skeleton produced the same response as a sixteen-strong raid, and the count
 * came from a box thirty-two blocks deep — so a zombie in a cave under the town
 * hall, which nobody could see and which was never coming up, emptied the
 * streets and kept them empty for as long as it lived.
 *
 * <p>Now the reckoning is of hostiles somebody has actually <em>seen</em>,
 * weighted by what each of them is worth — a creeper is not a zombie — and the
 * response is graduated. A little is a thing for the guards. A lot is everybody
 * indoors.
 *
 * <p>These are danger totals, not head counts. The scale is {@link Danger},
 * whose unit is one guard: an ordinary zombie is a routine afternoon, a skeleton
 * an awkward one, a creeper more than one guard should meet alone.
 */
public enum Alarm {

    /** Nobody has seen anything. The town works. */
    CALM,

    /**
     * Something is out there.
     *
     * <p>The guards go for it — they always do, whatever this says — and the
     * trades that work beyond the walls come inside. Everyone else carries on:
     * a town that downs tools over one skeleton is a town that never gets
     * anything done, and the whole point of having a wall and a watch is not
     * having to.
     */
    WARY,

    /** Enough of them that everybody but the watch goes home. */
    ALARMED;

    /**
     * This much danger in sight and the town is wary.
     *
     * <p>The smallest thing on the scale, so anything at all makes a town look
     * up — which is the point of a watch that is worth keeping.
     */
    public static final int WARY_AT = Danger.ROUTINE;

    /**
     * This much danger and it stops pretending everything is fine.
     *
     * <p>{@link Danger#OVERMATCH}: two guards' full attention, which is six
     * zombies, or three skeletons, or a creeper with company. Set deliberately
     * above what any one ordinary creature is worth, so that the only way to
     * reach it is for there to be several of them — see
     * {@link Settlement#sighted}, which enforces that from the other end.
     */
    public static final int ALARMED_AT = Danger.OVERMATCH;

    /** What a town does about this much danger in sight. */
    public static Alarm of(int danger) {
        if (danger >= ALARMED_AT) {
            return ALARMED;
        }
        return danger >= WARY_AT ? WARY : CALM;
    }

    /** Whether the town is worried at all. */
    public boolean isRaised() {
        return this != CALM;
    }

    /**
     * Whether this trade should be indoors under this alarm.
     *
     * <p>Guards never are. Below {@link #ALARMED} only the trades that work out
     * past the walls are called in, because they are the ones a hostile
     * actually reaches first — a farmer on a ring plot is behind the palisade
     * and a lumberjack is not.
     */
    public boolean callsIn(Profession trade) {
        if (trade == Profession.GUARD) {
            return false;
        }
        return switch (this) {
            case CALM -> false;
            case WARY -> trade.worksBeyondTheWalls();
            case ALARMED -> true;
        };
    }
}
