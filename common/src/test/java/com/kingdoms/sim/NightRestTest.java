package com.kingdoms.sim;

import com.kingdoms.sim.person.NightRest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a settler turns in, and what gets them up.
 *
 * <p>The sleeping itself needs a world — a body, a bed block and a pose — and so
 * cannot be tested here. The <em>decision</em> does not, and it is the half that
 * has the rules in it, so it is a function and this is its test.
 *
 * <p>The manual check for the other half: {@code /civ spawn} a town, wait for
 * dusk, and watch the homes. Everybody but the guards should walk to a bed and
 * lie in it; the beds should be the color of the culture; ringing the bell, a
 * skeleton wandering in, or morning should all empty them.
 */
class NightRestTest {

    // --- the clock -----------------------------------------------------------

    @Test
    void nightStartsAtDuskAndEndsAtDawn() {
        assertFalse(NightRest.isNight(0), "sunrise");
        assertFalse(NightRest.isNight(6000), "noon");
        assertFalse(NightRest.isNight(NightRest.DUSK - 1));
        assertTrue(NightRest.isNight(NightRest.DUSK), "the hour villagers turn in");
        assertTrue(NightRest.isNight(18000), "midnight");
        assertTrue(NightRest.isNight(NightRest.DAY - 1));
        assertFalse(NightRest.isNight(NightRest.DAY), "the next sunrise");
    }

    @Test
    void theClockKeepsRunningAfterTheFirstDay() {
        // A world clock counts up forever; a day is what it is modulo the day.
        assertTrue(NightRest.isNight(100L * NightRest.DAY + 13000));
        assertFalse(NightRest.isNight(100L * NightRest.DAY + 1000));
        // And it survives a clock somebody has set backward.
        assertTrue(NightRest.isNight(-6000), "six thousand short of sunrise is night");
    }

    @Test
    void dawnIsHowLongIsLeftOfTheNight() {
        assertEquals(0L, NightRest.untilDawn(6000), "it is daytime; dawn is not coming");
        assertEquals(NightRest.DAY - NightRest.DUSK, NightRest.untilDawn(NightRest.DUSK));
        assertEquals(1L, NightRest.untilDawn(NightRest.DAY - 1));
    }

    // --- going to bed --------------------------------------------------------

    /** An ordinary settler on an ordinary night: nothing wrong anywhere. */
    private static boolean quietNight() {
        return NightRest.wantsBed(true, false, false, false, false, false);
    }

    @Test
    void anOrdinarySettlerGoesToBedAtNight() {
        assertTrue(quietNight());
    }

    @Test
    void nobodyGoesToBedInBroadDaylight() {
        assertFalse(NightRest.wantsBed(false, false, false, false, false, false));
    }

    @Test
    void theWatchKeepsTheNight() {
        assertFalse(NightRest.wantsBed(true, true, false, false, false, false),
                "a guard on watch is the reason the rest of the town can sleep");
    }

    @Test
    void everyKindOfTroubleKeepsSomebodyOutOfBed() {
        assertFalse(NightRest.wantsBed(true, false, true, false, false, false),
                "the bell is ringing for this trade");
        assertFalse(NightRest.wantsBed(true, false, false, true, false, false),
                "something hostile is standing in the notice radius");
        assertFalse(NightRest.wantsBed(true, false, false, false, true, false),
                "running from a creeper");
        assertFalse(NightRest.wantsBed(true, false, false, false, false, true),
                "dinner outranks the end of the day, as it always has");
    }

    // --- and getting out of it ----------------------------------------------

    /** A sleeper nothing is happening to. */
    private static boolean undisturbed() {
        return NightRest.mustWake(true, false, false, false, false);
    }

    @Test
    void aSleeperNothingIsHappeningToSleepsOn() {
        assertFalse(undisturbed());
    }

    @Test
    void dawnGetsEverybodyUp() {
        assertTrue(NightRest.mustWake(false, false, false, false, false));
    }

    @Test
    void theBellDangerAndACreeperAllGetASleeperUp() {
        assertTrue(NightRest.mustWake(true, true, false, false, false), "the bell");
        assertTrue(NightRest.mustWake(true, false, true, false, false), "something hostile");
        assertTrue(NightRest.mustWake(true, false, false, true, false), "a creeper");
    }

    @Test
    void anErrandDoesNotWakeASleeperButRealWeaknessDoes() {
        // The asymmetry the second function exists for. Being peckish enough to
        // walk a loaf home stops somebody going to bed and does not turn them out
        // of one -- otherwise a settler who is mildly hungry every evening is
        // woken on the pass after they get in, every night, forever.
        assertFalse(NightRest.mustWake(true, false, false, false, false),
                "a mild appetite is not an emergency");
        assertTrue(NightRest.mustWake(true, false, false, false, true),
                "too weak to work is not something to sleep off");
    }
}
