package com.kingdoms.neoforge.view;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * How often something that is meant to run on a fixed beat actually ran.
 *
 * <p>Written for one question that six instrumented runs failed to answer:
 * {@link PersonEntityManager#tick()} is scheduled every
 * {@link PersonEntityManager#TICK_INTERVAL} game ticks — once a second — and on
 * a server carrying {@code Can't keep up! Running 19429ms or 388 ticks behind}
 * it plainly does not. Counting probe lines in a log put it at about once every
 * five seconds, which was a guess made from a side effect. This measures it in
 * the function that does the work.
 *
 * <p><strong>Every pass is recorded.</strong> No sampling interval, no cap on
 * the number of calls looked at, nothing periodic that could beat against the
 * length of a ring or the cadence of a build. Each of those was a wrong
 * conclusion in an earlier attempt, and none of them buys anything here: at one
 * pass a second the window below holds about sixty readings, and the server's
 * own twenty ticks a second bounds how many it could ever hold.
 *
 * <p><strong>The window is one minute of real time, and that is the window that
 * matters.</strong> Long enough that a healthy reading is an average of sixty
 * passes rather than a spot check, and short enough that a starvation which has
 * ended stops being reported within a minute of ending rather than being
 * averaged into the rest of the session forever.
 *
 * <p>Deliberately <em>not</em> the audit sweep's interval, which is 1200 game
 * ticks and is therefore stretched by exactly the thing being measured: on the
 * server this was written for, consecutive vitals lines are about five real
 * minutes apart. So a vitals line describes the last real minute before it was
 * printed and not the whole gap since the previous line, and {@code paceover}
 * states which minute that was. Widening the window to cover the gap would only
 * move the lie: a stall that ended four minutes ago would then be reported as
 * though it were still happening.
 *
 * <p>Intervals are held, not timestamps. An interval that began before the
 * window and ended inside it is counted whole, which is what makes a stall
 * longer than the window itself visible: a manager that has not run for ten
 * minutes reports one interval of ten minutes and a rate near zero, where a
 * window that evicted the old endpoint would have nothing left to compare
 * against and would report no reading at all — silence in exactly the case
 * worth hearing about.
 *
 * <p>The clock is handed in rather than read from {@code System}, the same seam
 * and for the same reason as {@code WorldView}'s step count: measured code that
 * reads the real clock cannot be driven by a test, and the arithmetic here is
 * the whole instrument.
 */
public final class TickRate {

    /** The window every figure here is stated over. */
    public static final long WINDOW_NANOS = 60_000_000_000L;

    /**
     * What the report says when there is nothing to report.
     *
     * <p>The same three tokens either way, so a scripted run splits the line the
     * same however the server is doing — a missing field and a field reading
     * {@code ?} are not the same news, and a parser that has to tell them apart
     * by counting words will one day get it wrong.
     */
    public static final String NO_READING = "pace=?/min pacegap=?ms paceover=0s";

    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long MILLIS_PER_MINUTE = 60_000L;

    /** One interval between two passes: when it ended, and how long it was. */
    private record Gap(long endNanos, long lengthNanos) {
    }

    private final LongSupplier clock;
    private final Deque<Gap> gaps = new ArrayDeque<>();
    private long lastPass;
    private boolean started;

    public TickRate(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Records that the measured pass has just begun.
     *
     * <p>At the top of the pass, so what is timed is the interval between
     * arrivals — the thing being asked about — and not the interval between
     * completions, which would fold the pass's own cost into the starvation.
     */
    public void mark() {
        long now = clock.getAsLong();
        if (started) {
            gaps.addLast(new Gap(now, now - lastPass));
        }
        lastPass = now;
        started = true;
        // Ends only increase, so the front is always the oldest. The interval
        // just added ends now, so it can never be the one evicted: there is
        // always a reading once there have been two passes.
        while (!gaps.isEmpty() && now - gaps.peekFirst().endNanos() > WINDOW_NANOS) {
            gaps.removeFirst();
        }
    }

    /** Intervals between passes held in the window. Two passes make one. */
    public int intervalsHeld() {
        return gaps.size();
    }

    /** How much real time those intervals cover, which is what the rate is per. */
    public long spanMillis() {
        return spanNanos() / NANOS_PER_MILLI;
    }

    private long spanNanos() {
        long total = 0;
        for (Gap gap : gaps) {
            total += gap.lengthNanos();
        }
        return total;
    }

    /**
     * Passes per real minute, or -1 before there have been two.
     *
     * <p>Sixty is the intended rate. The mean interval is not reported
     * separately because it is this figure's reciprocal by construction —
     * 60000/rate milliseconds — and two spellings of one measurement drift.
     */
    public double perMinute() {
        long span = spanNanos();
        if (span <= 0) {
            return -1;
        }
        return (double) gaps.size() * NANOS_PER_SECOND * 60.0 / span;
    }

    /**
     * The longest single gap in the window, or -1 before there have been two passes.
     *
     * <p>Reported beside the rate because they answer different questions and a
     * mean hides the one that matters. Fifty-nine passes a second apart and one
     * gap of nineteen seconds reads as a healthy 45/min, and the nineteen
     * seconds is the whole of what went wrong.
     */
    public long worstGapMillis() {
        long worst = -1;
        for (Gap gap : gaps) {
            worst = Math.max(worst, gap.lengthNanos() / NANOS_PER_MILLI);
        }
        return worst;
    }

    /**
     * One line of tokens for the log and the report alike.
     *
     * <p>Deliberately the same string in both places. Two formats of one
     * measurement is how a report and a log come to disagree about a server,
     * and the whole point of this class is that there is one answer.
     *
     * <p>{@code paceover} is the time actually held, not the window: a meter
     * thirty seconds into a session says so rather than implying a minute of
     * evidence it has not got.
     */
    public String describe() {
        double rate = perMinute();
        if (rate < 0) {
            return NO_READING;
        }
        return String.format(Locale.ROOT, "pace=%.1f/min pacegap=%dms paceover=%ds",
                rate, worstGapMillis(), spanMillis() / 1000);
    }

    /** What a healthy manager reads, for anything wanting to say how far off this is. */
    public static double intendedPerMinute(int tickInterval) {
        return MILLIS_PER_MINUTE / (tickInterval * 50.0);
    }
}
