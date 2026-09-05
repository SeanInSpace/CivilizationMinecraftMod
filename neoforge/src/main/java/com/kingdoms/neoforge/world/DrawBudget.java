package com.kingdoms.neoforge.world;

/**
 * How much of a paced job one sweep may do, given how long it has been since
 * the last one.
 *
 * <p>Everything drawn a slice at a time in this mod is budgeted per sweep, and
 * a per-sweep budget is a per-second budget only while the sweeps arrive once a
 * second. They do not. {@code PersonEntityManager.tick} is scheduled every
 * twenty game ticks, and on a server behind on its ticks the same twenty ticks
 * take five real seconds — so a wall meant to rise at twenty-four posts a second
 * rose at about five, and a town grown unwatched had almost nothing standing
 * when a player arrived to look at it. Nothing was wrong with the drawing. It
 * was being asked five times less often than it was written for.
 *
 * <p>Pacing by the clock rather than by the call makes the rate mean what it
 * says whatever the server is doing. The amortized cost is unchanged — it is
 * the same posts per second the constants always claimed — only its arrival
 * changes: a starved server does one larger sweep instead of five small ones it
 * never got round to.
 *
 * <p>Pure arithmetic on purpose, and nothing of Minecraft in it, because the
 * drawing itself needs a running world and this does not. It is the half that
 * can be pinned by a test.
 */
public final class DrawBudget {

    public static final long NANOS_PER_SECOND = 1_000_000_000L;

    private DrawBudget() {
    }

    /**
     * The allowance one sweep has earned.
     *
     * <p><strong>The cap is the whole reason this is not one multiplication.</strong>
     * Elapsed time is unbounded: a rejoin, a chunk-generation storm or a player
     * away for ten minutes would otherwise hand a single tick ten minutes of
     * arrears and lay a thousand posts in one frame — trading a wall that draws
     * too slowly for a server that stops. So arrears are repaid up to
     * {@code capSeconds} and the rest is forgiven.
     *
     * <p>And a floor of one, which is the opposite hazard. Sweeps arriving
     * faster than the rate would each round to nothing, the timestamp would move
     * on every time, and the job would earn no allowance at all however long it
     * ran. One is never wrong: it is at worst the old per-sweep behavior.
     *
     * @param elapsedNanos real time since this job's last sweep; a negative or
     *                     zero reading (a clock that went backwards) earns the floor
     * @param perSecond    the rate the job is written for
     * @param capSeconds   the most arrears a single sweep may repay
     */
    public static int forElapsed(long elapsedNanos, int perSecond, int capSeconds) {
        if (perSecond <= 0) {
            throw new IllegalArgumentException("a rate of " + perSecond + " a second draws nothing");
        }
        if (capSeconds <= 0) {
            throw new IllegalArgumentException("a cap of " + capSeconds + " seconds forgives everything");
        }
        // The clamp is the cap, applied to the interval rather than to the
        // answer. Doing it the other way round would round a day's arrears into
        // a number no int holds and then cast it, which wraps -- a ten-hour
        // absence coming back as a negative budget, or a small positive one.
        long owed = Math.max(0L, Math.min(elapsedNanos, (long) capSeconds * NANOS_PER_SECOND));
        long earned = Math.round((double) perSecond * owed / NANOS_PER_SECOND);
        return (int) Math.max(1L, earned);
    }
}
