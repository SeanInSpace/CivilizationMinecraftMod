package com.kingdoms.neoforge.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turning an interval into an allowance.
 *
 * <p>The half of the wall's pacing that does not need a world. Everything else
 * about the drawing ends in a real chunk and stays a playtest concern; this is
 * arithmetic, and arithmetic that was wrong for a year — a budget spent per
 * sweep on a server whose sweeps arrived a fifth as often as they were
 * scheduled to, which is the whole of why a town grown unwatched had almost no
 * wall standing when somebody arrived.
 */
class DrawBudgetTest {

    private static final long SECOND = DrawBudget.NANOS_PER_SECOND;

    /** The wall's own numbers, so the cases below are the cases that happen. */
    private static final int POSTS = 24;
    private static final int CAP = 5;

    @Test
    void oneSecondEarnsTheRate() {
        assertEquals(POSTS, DrawBudget.forElapsed(SECOND, POSTS, CAP),
                "a sweep arriving on time draws exactly what the rate says");
    }

    @Test
    void theStarvedServerActuallyMeasuredEarnsFiveSecondsOfWall() {
        // The case this exists for: PersonEntityManager.tick is scheduled once a
        // second and on the measured server ran about once every five.
        assertEquals(5 * POSTS, DrawBudget.forElapsed(5 * SECOND, POSTS, CAP),
                "five seconds between sweeps still buys five seconds of wall");
    }

    @Test
    void aTenMinuteStallDoesNotLayAThousandPostsInOneTick() {
        assertEquals(CAP * POSTS, DrawBudget.forElapsed(600 * SECOND, POSTS, CAP),
                "arrears are repaid up to the cap and the rest is forgiven");
    }

    @Test
    void theCapIsTheOnlyCeiling() {
        // Anything past the cap reads the same, so no length of absence can
        // produce a bigger sweep than the one the cap was costed for.
        assertEquals(DrawBudget.forElapsed(CAP * SECOND, POSTS, CAP),
                DrawBudget.forElapsed(Long.MAX_VALUE, POSTS, CAP),
                "a stall measured in centuries costs what a five-second one costs");
    }

    @Test
    void aSweepFasterThanTheRateStillDrawsSomething() {
        // Twenty sweeps a second at twenty-four posts a second is 1.2 posts
        // each. Rounding that to nothing every time, while the timestamp moved
        // on every time, would starve the wall completely -- the opposite fault
        // to the one being fixed and a far quieter one.
        assertEquals(1, DrawBudget.forElapsed(SECOND / 20, POSTS, CAP),
                "a fraction of a post is still a post, or the ring never rises");
    }

    @Test
    void aClockThatWentBackwardsEarnsTheFloorRatherThanANegativeBudget() {
        assertEquals(1, DrawBudget.forElapsed(-5 * SECOND, POSTS, CAP));
        assertEquals(1, DrawBudget.forElapsed(0, POSTS, CAP));
    }

    @Test
    void theBudgetGrowsWithTheGapUpToTheCap() {
        int previous = 0;
        for (long millis = 100; millis <= CAP * 1000; millis += 100) {
            int budget = DrawBudget.forElapsed(millis * 1_000_000L, POSTS, CAP);
            assertTrue(budget >= previous,
                    "a longer wait can never earn less: " + millis + "ms");
            previous = budget;
        }
        assertEquals(CAP * POSTS, previous);
    }

    @Test
    void theWallsOwnRatesArePacedByTheseSameNumbers() {
        // Pinning the constants themselves, not a copy of them: the arithmetic
        // being right is no use if the wall passes it something else.
        assertEquals(PerimeterLayer.POSTS_PER_SECOND,
                DrawBudget.forElapsed(SECOND, PerimeterLayer.POSTS_PER_SECOND,
                        PerimeterLayer.CATCH_UP_SECONDS),
                "a sweep on time lays the intended posts");
        assertEquals(PerimeterLayer.CATCH_UP_SECONDS * PerimeterLayer.POSTS_PER_SECOND,
                DrawBudget.forElapsed(3600 * SECOND, PerimeterLayer.POSTS_PER_SECOND,
                        PerimeterLayer.CATCH_UP_SECONDS),
                "an hour unwatched lays one capped sweep's worth");
    }

    @Test
    void aCappedSweepsAllowanceFitsInsideOneScan() {
        // The looking is bounded per sweep and the placing per second, so the
        // two have to be kept in step by hand: an allowance wider than the scan
        // could never be spent, and the wall would go on drawing at the old rate
        // while every comment in the file said otherwise.
        assertTrue(PerimeterLayer.CATCH_UP_SECONDS * PerimeterLayer.POSTS_PER_SECOND
                        <= PerimeterLayer.SCAN,
                "a five-second catch-up of " + PerimeterLayer.CATCH_UP_SECONDS
                        * PerimeterLayer.POSTS_PER_SECOND + " posts does not fit in a scan of "
                        + PerimeterLayer.SCAN);
    }

    @Test
    void aRateOrCapOfNothingIsRefusedRatherThanSilentlyDrawingNothing() {
        assertThrows(IllegalArgumentException.class,
                () -> DrawBudget.forElapsed(SECOND, 0, CAP));
        assertThrows(IllegalArgumentException.class,
                () -> DrawBudget.forElapsed(SECOND, POSTS, 0));
    }
}
