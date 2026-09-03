package com.kingdoms.sim.person;

/**
 * How hungry somebody is, as a rung rather than a number.
 *
 * <p>Every surface that reports a settler reads the same four thresholds off
 * {@link Person} and turns them into something a player understands — a word in
 * chat, a colour on a screen. Each surface used to walk the ladder itself, which
 * is fine until a threshold moves and only one of them is updated: the screen
 * then says "starving" in the colour it uses for "weak". One ladder, read twice.
 *
 * <p>Lives beside the thresholds in the simulation, not on the platform layer or
 * a network payload, because it is a fact about a person rather than about the
 * wire or a screen.
 */
public enum Appetite {

    FED("well fed"),
    HUNGRY("hungry"),
    WEAK("weak with hunger"),
    STARVING("starving");

    private final String word;

    Appetite(String word) {
        this.word = word;
    }

    /** What to call this rung to a player. */
    public String word() {
        return word;
    }

    /** Worst first: each threshold is the floor of its rung. */
    public static Appetite of(int hunger) {
        if (hunger >= Person.HUNGER_SEVERE) {
            return STARVING;
        }
        if (hunger >= Person.HUNGER_WEAK) {
            return WEAK;
        }
        if (hunger >= Person.HUNGER_HUNGRY) {
            return HUNGRY;
        }
        return FED;
    }
}
