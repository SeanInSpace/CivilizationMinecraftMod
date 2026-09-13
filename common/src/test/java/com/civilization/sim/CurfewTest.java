package com.civilization.sim;

import com.civilization.sim.person.Curfew;
import com.civilization.sim.person.NightRest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dusk curfew: who sets off when, and whether they are actually indoors by
 * dark.
 *
 * <p>The arithmetic is the whole feature. What killed seventeen people was not that
 * the town had no bedtime — it had one, and {@code NightRest} has enforced it for
 * as long as there have been beds — but that a farmer on a field a hundred and
 * sixty-five blocks out who downs tools <em>at</em> dusk spends the first eight
 * minutes of the night walking through a wood. So the test that matters is the last
 * one here: for every distance anybody could be at, does the lead actually get them
 * home before the light goes?
 */
class CurfewTest {

    /** Where dusk is, so the numbers below read as times rather than as offsets. */
    private static final long DUSK = NightRest.DUSK;

    @Test
    void theFurthestAwayLeavesFirst() {
        double near = 20;
        double far = 165;
        assertTrue(Curfew.leadFor(far) > Curfew.leadFor(near),
                "somebody with eight times the walk has to set off sooner");
    }

    @Test
    void theLeadIsNeverMoreThanTheTownAllows() {
        assertEquals(Curfew.LEAD_TICKS, Curfew.leadFor(100_000),
                "a curfew that started at noon for one outlying forester would be a "
                        + "town that stops working at noon");
    }

    @Test
    void somebodyStandingOnTheirOwnDoorstepKeepsWorking() {
        assertEquals(0, Curfew.leadFor(0));
        assertFalse(Curfew.sendsHome(DUSK - 1, 0, Curfew.LEAD_TICKS),
                "no walk, no early finish");
    }

    @Test
    void everybodyIsIndoorsByDarkAtAWalkingPace() {
        // Every distance from the middle of a claim to its far corner, which is
        // further than anybody in the measured town actually was.
        for (int blocks = 0; blocks <= 400; blocks += 5) {
            long lead = Curfew.leadFor(blocks, Curfew.LEAD_TICKS);
            long setsOffAt = DUSK - lead;
            long walkTakes = (long) Math.ceil(blocks / Curfew.BLOCKS_PER_TICK);
            if (lead >= Curfew.LEAD_TICKS) {
                // Beyond the lead's reach. Such a person leaves as early as the
                // curfew allows and is simply late, which is a fact about the
                // claim being too big rather than something arithmetic can fix.
                continue;
            }
            assertTrue(setsOffAt + walkTakes <= DUSK,
                    blocks + " blocks out sets off at " + setsOffAt + " and arrives at "
                            + (setsOffAt + walkTakes) + ", after dusk at " + DUSK);
        }
    }

    @Test
    void theSlackIsRealTimeAndNotAPercentage() {
        long tenBlocks = Curfew.leadFor(10);
        assertTrue(tenBlocks >= Curfew.SLACK_TICKS,
                "the gate, the door and the last steps to the mattress do not get "
                        + "shorter because the journey was short");
    }

    // --- the town's own hours ---

    @Test
    void theTownStopsHandingOutErrandsBeforeItGetsDark() {
        assertFalse(Curfew.isCurfew(DUSK - Curfew.LEAD_TICKS - 1, Curfew.LEAD_TICKS));
        assertTrue(Curfew.isCurfew(DUSK - Curfew.LEAD_TICKS, Curfew.LEAD_TICKS));
        assertTrue(Curfew.isCurfew(DUSK, Curfew.LEAD_TICKS), "and all through the night");
        assertTrue(Curfew.isCurfew(DUSK + 5000, Curfew.LEAD_TICKS));
    }

    @Test
    void dawnEndsIt() {
        assertFalse(Curfew.isCurfew(0, Curfew.LEAD_TICKS), "they go back out at dawn");
        assertFalse(Curfew.isCurfew(NightRest.DAY, Curfew.LEAD_TICKS),
                "and on every day after it");
    }

    @Test
    void theWatchIsExempt() {
        assertTrue(Curfew.maySetToWork(DUSK + 1000, true, Curfew.LEAD_TICKS),
                "a guard who went to bed would be a town with no watch");
        assertFalse(Curfew.maySetToWork(DUSK + 1000, false, Curfew.LEAD_TICKS));
    }

    @Test
    void thecurfewAndTheBedtimeKeepTheSameHour() {
        // Not merely close: the same figure. A curfew whose dusk differed from the
        // bedtime's would be people standing about outdoors in the gap.
        assertEquals(0, Curfew.untilDusk(DUSK));
        assertTrue(NightRest.isNight(DUSK));
        assertFalse(NightRest.isNight(DUSK - 1));
        assertEquals(1, Curfew.untilDusk(DUSK - 1));
    }

    // --- the departures ---

    @Test
    void departuresAreOrderedByDistance() {
        List<Curfew.Walk<String>> ordered = Curfew.departureOrder(List.of(
                new Curfew.Walk<>("miller", 12.0),
                new Curfew.Walk<>("forester", 165.0),
                new Curfew.Walk<>("farmer", 90.0),
                new Curfew.Walk<>("smith", 4.0)));

        assertEquals(List.of("forester", "farmer", "miller", "smith"),
                ordered.stream().map(Curfew.Walk::who).toList(),
                "the longest walk is steered first, because it is the one that can "
                        + "least afford to lose a pass");
    }

    @Test
    void orderingAnEmptyTownIsNotAnError() {
        assertTrue(Curfew.departureOrder(List.<Curfew.Walk<String>>of()).isEmpty());
    }

    // --- the two fidelities ---

    @Test
    void theUnwatchedClockIdlesOnTheSameHourTheTownWalksHome() {
        // The parity that the doctrine demands: if one fidelity works through the
        // night and the other does not, a watched town falls behind one nobody is
        // standing in, which is a reason to stop playing.
        for (long time = 0; time < NightRest.DAY; time += 100) {
            assertEquals(Curfew.isCurfew(time, Curfew.LEAD_TICKS),
                    Curfew.idlesUnwatchedWork(time, Curfew.LEAD_TICKS),
                    "the clock and the bodies disagree about the hour at " + time);
        }
    }
}
