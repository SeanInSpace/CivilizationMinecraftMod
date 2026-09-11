package com.civilization.sim.person;

/**
 * When a settler turns in, and what gets them back up.
 *
 * <p>A decision, not a state. The simulation clock does not model sleep and is
 * not going to: a town's step is the same step whether it is noon or midnight,
 * and nothing about grain, trade or building changes because the people are
 * lying down. What sleeps is the <em>body</em> — the view entity the platform
 * layer spawns when a player is near enough to see it — so the rule about when
 * a body gets into bed lives here, where it can be read and tested without a
 * world, and the platform layer does as it says.
 *
 * <p>The asymmetry between the two questions is the point of there being two.
 * Going to bed loses to any errand at all, including a meal somebody is already
 * walking to; getting <em>out</em> of bed takes more than an errand, because a
 * settler who is merely peckish and hauls a loaf home every evening would
 * otherwise be turned out of bed on the pass after they got in, every night,
 * forever. Only real weakness gets them up for food.
 */
public final class NightRest {

    private NightRest() {
    }

    /** Ticks in a Minecraft day. */
    public static final long DAY = 24000L;

    /**
     * When the light goes and vanilla villagers turn in.
     *
     * <p>The same figure Minecraft uses for its own beds, deliberately: a town
     * that went to sleep on a schedule of its own would read as a town keeping
     * different hours from the world around it.
     */
    public static final long DUSK = 12000L;

    /** Whether the clock has turned past dusk. */
    public static boolean isNight(long dayTime) {
        return Math.floorMod(dayTime, DAY) >= DUSK;
    }

    /** How long until the sun is up again, in ticks. */
    public static long untilDawn(long dayTime) {
        long time = Math.floorMod(dayTime, DAY);
        return isNight(time) ? DAY - time : 0L;
    }

    /**
     * Whether this settler should be heading for their own bed.
     *
     * @param night      the clock is past dusk and it is dark out
     * @param guard      one of the watch, who keep the night and never turn in
     * @param called     the bell is ringing for this trade
     * @param threatened something hostile is inside notice
     * @param fleeing    running from a creeper, which outranks everything
     * @param errand     already walking to something to eat
     */
    public static boolean wantsBed(boolean night, boolean guard, boolean called,
                                   boolean threatened, boolean fleeing, boolean errand) {
        return night && !guard && !called && !threatened && !fleeing && !errand;
    }

    /**
     * Whether somebody already asleep has to get up.
     *
     * <p>Not simply the negation of {@link #wantsBed}: an errand does not wake a
     * sleeper, only being too weak to work does. Everything else that stops a
     * settler going to bed also turns them out of one.
     *
     * @param night          the clock is still past dusk; dawn is the ordinary wake
     * @param called         the bell is ringing for this trade
     * @param threatened     something hostile is inside notice
     * @param fleeing        running from a creeper
     * @param weakWithHunger hungry enough that sleeping it off makes it worse
     */
    public static boolean mustWake(boolean night, boolean called, boolean threatened,
                                   boolean fleeing, boolean weakWithHunger) {
        return !night || called || threatened || fleeing || weakWithHunger;
    }
}
