package com.civilization.sim;

import com.civilization.sim.person.Profession;
import com.civilization.sim.view.WorkTheatre;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a trade is worth making a noise about.
 *
 * <p>Three conditions and a clock, and the interesting one is the third: the
 * forge is silent when the forge has no iron. That is not tuning. A player who
 * learns to read the hammering as "the smithy is working" has learned something
 * true, and a hammer over an empty rack would make it false — so the gate is
 * pinned here, where it can be read, rather than in the steering loop where the
 * last one like it turned into a {@code default} arm that answered yes to
 * everybody.
 *
 * <p>The platform's half — the anvil sound, the sparks, the arm — needs a level
 * and is read rather than measured; see the changelog.
 */
class WorkTheatreTest {

    private static final UUID SOMEBODY =
            UUID.fromString("00000000-0000-4000-8000-000000000001");

    /** A worker at their bench, with a player there and work in front of them. */
    private static final WorkTheatre.Scene READY =
            new WorkTheatre.Scene(true, true, true, true);

    // --- who has a picture -------------------------------------------------------

    @Test
    void theThreeIndoorTradesHaveSomethingToStrike() {
        assertEquals(WorkTheatre.Trade.SMITH, WorkTheatre.tradeOf(Profession.SMITH));
        assertEquals(WorkTheatre.Trade.MILLER, WorkTheatre.tradeOf(Profession.MILLER));
        assertEquals(WorkTheatre.Trade.CARPENTER,
                WorkTheatre.tradeOf(Profession.CARPENTER));
    }

    /**
     * Nobody else does, and the outdoor trades least of all: felling a tree and
     * cutting a seam go through {@code destroyBlock}, so they are already audible
     * and a second noise on top would be two axes for one swing.
     */
    @Test
    void everybodyElseIsSilent() {
        for (Profession what : Profession.values()) {
            if (what == Profession.SMITH || what == Profession.MILLER
                    || what == Profession.CARPENTER) {
                continue;
            }
            assertNull(WorkTheatre.tradeOf(what), what + " was given a noise");
        }
        assertNull(WorkTheatre.tradeOf(null));
    }

    // --- the gate -----------------------------------------------------------------

    @Test
    void asmithAtAHotForgeWithSomebodyWatchingStrikes() {
        assertTrue(WorkTheatre.strikes(READY, WorkTheatre.NEVER, 0, 40));
    }

    /** The one that matters most: a cold forge is a quiet one. */
    @Test
    void aforgeWithNothingInItIsSilentHoweverCloselyItIsWatched() {
        WorkTheatre.Scene cold = new WorkTheatre.Scene(true, true, false, true);
        assertFalse(WorkTheatre.strikes(cold, WorkTheatre.NEVER, 0, 40));
        assertFalse(WorkTheatre.strikes(cold, WorkTheatre.NEVER, 100_000, 40));
    }

    /** Nobody there to hear it, so there is nothing to draw and no packet to spend. */
    @Test
    void anUnwatchedTownWorksInSilence() {
        assertFalse(WorkTheatre.strikes(new WorkTheatre.Scene(false, true, true, true),
                WorkTheatre.NEVER, 0, 40));
    }

    /** A hammer from a man walking up the street is nonsense. */
    @Test
    void asmithOnHisWayToTheForgeDoesNotHammer() {
        assertFalse(WorkTheatre.strikes(new WorkTheatre.Scene(true, false, true, true),
                WorkTheatre.NEVER, 0, 40));
    }

    /** A record in a ledger has no arm to swing. */
    @Test
    void anUnembodiedSmithStrikesNothing() {
        assertFalse(WorkTheatre.strikes(new WorkTheatre.Scene(true, true, true, false),
                WorkTheatre.NEVER, 0, 40));
    }

    // --- the clock ------------------------------------------------------------------

    @Test
    void nobodyStrikesTwiceInsideTheirOwnGap() {
        assertFalse(WorkTheatre.strikes(READY, 1000, 1000 + 39, 40));
        assertTrue(WorkTheatre.strikes(READY, 1000, 1000 + 40, 40));
        assertTrue(WorkTheatre.strikes(READY, 1000, 1000 + 400, 40));
    }

    /**
     * Two to four seconds. A blacksmith swings faster and it does not matter:
     * what is being drawn is that somebody is working, and a hammer every second
     * from every forge in a town of sixty is a factory.
     */
    @Test
    void everyGapIsBetweenTwoAndFourSeconds() {
        for (int i = 0; i < 500; i++) {
            long gap = WorkTheatre.gapFor(UUID.randomUUID());
            assertTrue(gap >= WorkTheatre.MIN_GAP_TICKS && gap <= WorkTheatre.MAX_GAP_TICKS,
                    "gap of " + gap);
        }
        assertEquals(WorkTheatre.MIN_GAP_TICKS, WorkTheatre.gapFor(null));
    }

    /** The same man always waits the same while, or the rate limit would not hold. */
    @Test
    void agapIsTheSameEveryTimeItIsAsked() {
        long first = WorkTheatre.gapFor(SOMEBODY);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, WorkTheatre.gapFor(SOMEBODY));
        }
    }

    /**
     * And two smiths at one forge fall out of step and stay out of step, which is
     * the difference between a smithy and a drum.
     */
    @Test
    void twoSmithsDoNotHammerInUnison() {
        Set<Long> gaps = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            gaps.add(WorkTheatre.gapFor(UUID.randomUUID()));
        }
        assertTrue(gaps.size() > 20, "only " + gaps.size() + " distinct intervals");
    }

    // --- the ranges ------------------------------------------------------------------

    /**
     * Shorter than the chimneys' sixty-four, and on purpose. Smoke over a roof is
     * a thing you see from across a valley; a hammer is a thing you hear from the
     * next street.
     */
    @Test
    void theRangesAreTheOnesTheCommentsClaim() {
        assertEquals(32.0, WorkTheatre.WATCH_RANGE);
        assertEquals(4.0, WorkTheatre.AT_WORK);
        assertNotNull(WorkTheatre.Trade.valueOf("SMITH"));
    }
}
