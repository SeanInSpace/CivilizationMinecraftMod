package com.kingdoms.sim;

import com.kingdoms.sim.settlement.Alarm;
import com.kingdoms.sim.settlement.Danger;
import com.kingdoms.sim.settlement.RaidPlanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scale every alarm decision is measured on.
 *
 * <p>The rungs were pulled out of three files that each held their own bare
 * integers, and the whole value of doing that is lost if the ladder can be
 * re-ordered or re-based without anything noticing. Two things are asserted
 * here: that naming the numbers did not move any of them, and that the
 * relationships the design rests on are relationships rather than coincidences.
 */
class DangerTest {

    @Test
    void namingTheRungsDidNotMoveAnyOfThem() {
        // Literal on purpose. Every number below was argued for before it had a
        // name, and this is the record of what was decided.
        assertEquals(0, Danger.NONE);
        assertEquals(1, Danger.ROUTINE);
        assertEquals(2, Danger.AWKWARD);
        assertEquals(3, Danger.FULL_ATTENTION);
        assertEquals(4, Danger.DANGEROUS);
        assertEquals(5, Danger.DIRE);
        assertEquals(6, Danger.OVERMATCH);
        assertEquals(10, Danger.HOPELESS);
    }

    @Test
    void theLadderAscends() {
        int[] ladder = {
                Danger.NONE, Danger.ROUTINE, Danger.AWKWARD, Danger.FULL_ATTENTION,
                Danger.DANGEROUS, Danger.DIRE, Danger.OVERMATCH, Danger.HOPELESS,
        };
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i] > ladder[i - 1],
                    "rung " + i + " is not worse than the one below it");
        }
    }

    @Test
    void theTierThatEmptiesTheStreetsIsExactlyTwoGuards() {
        // The load-bearing relationship. RaidPlanner asks whether the danger
        // outweighs guards * GUARD_CAPACITY; Alarm asks whether it has reached
        // ALARMED_AT. Those are the same question about one guard and two only
        // because both are counted in the same unit.
        assertEquals(Danger.FULL_ATTENTION, RaidPlanner.GUARD_CAPACITY);
        assertEquals(2 * RaidPlanner.GUARD_CAPACITY, Alarm.ALARMED_AT);
    }

    @Test
    void theThresholdsAreRungsRatherThanNumbersOfTheirOwn() {
        assertEquals(Danger.ROUTINE, Alarm.WARY_AT, "one of anything makes a town look up");
        assertEquals(Danger.OVERMATCH, Alarm.ALARMED_AT);
        assertEquals(Danger.DANGEROUS, RaidPlanner.BELL_FLOOR);
    }

    @Test
    void theWorstSingleCreatureStillSitsBelowThePanicTier() {
        // A ravager is DIRE, and the rule that one creature is never a panic
        // depends on no ordinary creature reaching ALARMED_AT on its own. The
        // cap in Settlement.sighted is the belt; this is the braces.
        assertTrue(Danger.DIRE < Alarm.ALARMED_AT);
        assertTrue(Danger.HOPELESS > Alarm.ALARMED_AT,
                "a warden must still read as worse than a crowd, cap or no cap");
    }

    @Test
    void threeOfAnythingUnrecognisedIsEnoughToEmptyTheStreets() {
        // The price of the platform's default, pinned here because it is paid
        // over on this side of the seam. A creature nobody has named reads as a
        // skeleton, and three of those reach the panic tier where it used to
        // take six shambling ones.
        assertTrue(3 * Danger.AWKWARD >= Alarm.ALARMED_AT,
                "the default rung and the panic tier are one design, not two");
        assertTrue(5 * Danger.ROUTINE < Alarm.ALARMED_AT,
                "and five of the least alarming thing there is still is not a panic");
    }

    @Test
    void theBellRingsBeforeTheTiersDoAndNotOverNothing() {
        assertTrue(RaidPlanner.BELL_FLOOR < Alarm.ALARMED_AT,
                "a thin watch panics sooner, which is the whole point of the bell");
        assertTrue(RaidPlanner.BELL_FLOOR > Danger.ROUTINE,
                "and not over a wandering zombie");
    }
}
