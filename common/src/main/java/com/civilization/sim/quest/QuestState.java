package com.civilization.sim.quest;

/**
 * Where a quest has got to.
 *
 * <p>Four states and three buttons: an offer is taken, a job in hand is
 * abandoned or finished, and a finished one is claimed. Claiming is not a state
 * — it takes the quest off the board altogether and into the town's memory of
 * what has been done for it, which is what {@link QuestBoard#completed()}
 * holds.
 */
public enum QuestState {

    /** On the board, nobody's. */
    OFFERED,

    /** Somebody has taken it. Only they can make progress on it. */
    ACCEPTED,

    /** The work is done and the reward is waiting to be collected. */
    DONE,

    /**
     * Nobody got to it in time.
     *
     * <p>A state rather than an outright deletion because the board wants to be
     * able to say so for the step it happens on — a quest that simply vanishes
     * between two right-clicks reads as a bug.
     */
    EXPIRED;

    public static QuestState parse(String name, QuestState fallback) {
        if (name == null) {
            return fallback;
        }
        for (QuestState known : values()) {
            if (known.name().equals(name)) {
                return known;
            }
        }
        return fallback;
    }
}
