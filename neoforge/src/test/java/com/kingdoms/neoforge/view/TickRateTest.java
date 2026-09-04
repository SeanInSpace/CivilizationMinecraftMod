package com.kingdoms.neoforge.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The manager's cadence, driven by a clock a test can turn.
 *
 * <p>Six instrumented runs failed to establish this figure and the warning they
 * left behind is that every one of them measured a side effect instead of the
 * thing. So the thing is measured, and here it is measured against a clock that
 * is told what to say — which is the only way to know that a reading of twelve
 * a minute means twelve a minute.
 */
class TickRateTest {

    private static final long MILLI = 1_000_000L;

    /** A clock that only moves when the test moves it. */
    private static final class HandClock {
        private long nanos = 1_000_000_000_000L;   // not zero: a clock is an offset

        long now() {
            return nanos;
        }

        void advanceMillis(long millis) {
            nanos += millis * MILLI;
        }
    }

    @Test
    void aServerKeepingUpReadsTheIntendedSixtyAMinute() {
        HandClock clock = new HandClock();
        TickRate rate = new TickRate(clock::now);
        for (int i = 0; i < 60; i++) {
            rate.mark();
            clock.advanceMillis(1000);
        }
        assertEquals(60.0, rate.perMinute(), 0.01,
                "one pass a second is sixty a minute");
        assertEquals(1000, rate.worstGapMillis());
        assertEquals(60.0, TickRate.intendedPerMinute(PersonEntityManager.TICK_INTERVAL), 0.01,
                "and sixty is what TICK_INTERVAL asks for");
    }

    @Test
    void theStarvedServerReadsTwelveAMinute() {
        // The state the log describes: Can't keep up! Running 19429ms behind, and
        // a pass scheduled every second arriving about every five.
        HandClock clock = new HandClock();
        TickRate rate = new TickRate(clock::now);
        for (int i = 0; i < 12; i++) {
            rate.mark();
            clock.advanceMillis(5000);
        }
        assertEquals(12.0, rate.perMinute(), 0.01,
                "one pass every five seconds is twelve a minute, not sixty");
    }

    @Test
    void oneLongStallIsReportedBesideTheRateRatherThanAveragedIntoIt() {
        HandClock clock = new HandClock();
        TickRate rate = new TickRate(clock::now);
        for (int i = 0; i < 40; i++) {
            rate.mark();
            clock.advanceMillis(1000);
        }
        rate.mark();
        clock.advanceMillis(19429);
        rate.mark();
        assertEquals(19429, rate.worstGapMillis(),
                "the gap that ruined the minute is stated, not smoothed away");
        assertTrue(rate.perMinute() > 20 && rate.perMinute() < 60,
                "while the rate alone still reads healthy-ish: " + rate.perMinute());
    }

    @Test
    void aStallThatHasEndedStopsBeingReportedOnceItLeavesTheWindow() {
        HandClock clock = new HandClock();
        TickRate rate = new TickRate(clock::now);
        rate.mark();
        clock.advanceMillis(30_000);
        rate.mark();                       // a thirty-second gap, now on the record
        assertEquals(30_000, rate.worstGapMillis());
        // A minute and a half of healthy passes after it. The window is a
        // minute, so the stall must be gone: a meter that never forgets is a
        // meter that describes the session rather than the server.
        for (int i = 0; i < 90; i++) {
            clock.advanceMillis(1000);
            rate.mark();
        }
        assertEquals(1000, rate.worstGapMillis(), "the old stall has left the window");
        assertEquals(60.0, rate.perMinute(), 0.01);
        assertTrue(rate.spanMillis() <= TickRate.WINDOW_NANOS / MILLI + 1000,
                "and no more than a window of history is held: " + rate.spanMillis());
    }

    @Test
    void aStallLongerThanTheWindowIsStillVisible() {
        // The eviction hazard worth a test of its own. Ten minutes with no pass
        // at all is the loudest possible reading, and a window that dropped the
        // interval's near end would have nothing left to compare and would
        // report no reading -- silence in the one case worth hearing.
        HandClock clock = new HandClock();
        TickRate rate = new TickRate(clock::now);
        rate.mark();
        clock.advanceMillis(600_000);
        rate.mark();
        assertEquals(600_000, rate.worstGapMillis());
        assertEquals(0.1, rate.perMinute(), 0.001, "a tenth of a pass a minute");
    }

    @Test
    void oneAppearanceSaysSoRatherThanGuessing() {
        HandClock clock = new HandClock();
        TickRate rate = new TickRate(clock::now);
        assertEquals(-1, rate.perMinute(), "nothing has been seen yet");
        rate.mark();
        assertEquals(-1, rate.perMinute(), "one pass is not a rate");
        assertEquals(0, rate.intervalsHeld());
        assertEquals(TickRate.NO_READING, rate.describe(),
                "and the report says so rather than printing a made-up number");
        clock.advanceMillis(1000);
        rate.mark();
        assertEquals(1, rate.intervalsHeld());
        assertEquals(60.0, rate.perMinute(), 0.01, "two passes make a rate");
    }

    @Test
    void theReportStatesHowMuchHistoryItIsSpeakingFrom() {
        // Thirty seconds into a session it must not imply a minute of evidence.
        HandClock clock = new HandClock();
        TickRate rate = new TickRate(clock::now);
        for (int i = 0; i < 30; i++) {
            rate.mark();
            clock.advanceMillis(1000);
        }
        assertEquals("pace=60.0/min pacegap=1000ms paceover=29s", rate.describe());
    }

    @Test
    void theLineHasTheSameShapeWhetherThereIsAReadingOrNot() {
        // The audit line is grepped by the harness, so the fields must not come
        // and go with the server's health -- that is exactly the moment a
        // scripted run is reading it.
        HandClock clock = new HandClock();
        TickRate rate = new TickRate(clock::now);
        clock.advanceMillis(1000);
        rate.mark();
        clock.advanceMillis(1000);
        rate.mark();
        assertEquals(TickRate.NO_READING.split(" ").length, rate.describe().split(" ").length);
        for (String key : new String[]{"pace=", "pacegap=", "paceover="}) {
            assertTrue(rate.describe().contains(key), key + " missing from " + rate.describe());
            assertTrue(TickRate.NO_READING.contains(key), key + " missing when there is no reading");
        }
    }

    @Test
    void everyPassCountsAndNothingIsSampled() {
        // No interval, no cap, nothing periodic: a probe capped to the first
        // twelve calls only ever saw the growth phase, and a sample interval
        // that aliased with a ring length made a moving cursor look frozen.
        // Six hundred passes at ten a second is a hundred times the nominal
        // rate, and all of the last minute of them are counted.
        HandClock clock = new HandClock();
        TickRate rate = new TickRate(clock::now);
        for (int i = 0; i < 600; i++) {
            rate.mark();
            clock.advanceMillis(100);
        }
        assertEquals(600.0, rate.perMinute(), 0.01, "ten a second is six hundred a minute");
        assertEquals(599, rate.intervalsHeld(),
                "every gap between those six hundred passes, none of them thrown away");
    }
}
