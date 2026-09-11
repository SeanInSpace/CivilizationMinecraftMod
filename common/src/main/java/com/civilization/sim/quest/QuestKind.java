package com.civilization.sim.quest;

/**
 * What a town is actually asking for.
 *
 * <p>An enum rather than a free string, because every kind needs a completion
 * rule and a rule nobody wrote is a quest nobody can finish. {@link #UNKNOWN} is
 * what a reader answers with when it meets a name it has never heard of — a
 * board sent by a newer server, or a save written by one — so a client one
 * version behind draws a row it cannot act on instead of taking the screen down
 * with it.
 */
public enum QuestKind {

    /** Carry goods to the storehouse. Counted by the donation path. */
    DELIVER,

    /** Kill hostiles anywhere inside the town's claim. Counted by the death hook. */
    SLAY,

    /**
     * Kill hostiles at one particular place — a wreck, a site nobody will work.
     *
     * <p>The same counter as {@link #SLAY} with a place attached, and the
     * difference is the whole point: a town that wants its streets safer asks
     * for slaying, and a town that wants <em>that</em> building back asks for
     * clearing.
     */
    CLEAR,

    /** Go and stand somewhere. Completed by the player's own position. */
    VISIT,

    /** A kind this build has never heard of. Never generated, only read. */
    UNKNOWN;

    /** The kind of that name, or {@link #UNKNOWN} — never an exception. */
    public static QuestKind parse(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        for (QuestKind known : values()) {
            if (known.name().equals(name)) {
                return known;
            }
        }
        return UNKNOWN;
    }

    /** Whether a hostile dying somewhere can count toward this. */
    public boolean countsKills() {
        return this == SLAY || this == CLEAR;
    }

    /** Whether this kind is anchored to one spot on the ground. */
    public boolean hasPlace() {
        return this == CLEAR || this == VISIT;
    }
}
