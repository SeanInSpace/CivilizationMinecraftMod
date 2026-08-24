package com.kingdoms.sim;

import com.kingdoms.sim.settlement.Workforce;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dividing a crew between the places it works.
 *
 * <p>The invariant that matters is that the shares add back up to the crew. The
 * rate unwatched production is priced at was worked out from the whole crew, so
 * a split that loses somebody to rounding quietly slows the town down, and one
 * that gains somebody quietly speeds it up.
 */
class WorkforceTest {

    private static int total(int crew, int sites) {
        int sum = 0;
        for (int site = 0; site < Math.max(1, sites); site++) {
            sum += Workforce.shareOf(crew, site, sites);
        }
        return sum;
    }

    @Test
    void everybodyIsCountedSomewhere() {
        for (int crew = 0; crew <= 20; crew++) {
            for (int sites = 1; sites <= 6; sites++) {
                assertEquals(crew, total(crew, sites),
                        crew + " across " + sites + " sites must still be " + crew);
            }
        }
    }

    @Test
    void anEvenCrewDividesEvenly() {
        assertEquals(3, Workforce.shareOf(6, 0, 2));
        assertEquals(3, Workforce.shareOf(6, 1, 2));
    }

    @Test
    void theRemainderGoesToTheEarliestSites() {
        // Five across two camps is three and two — not two and two with one
        // lumberjack standing about unaccounted for.
        assertEquals(3, Workforce.shareOf(5, 0, 2));
        assertEquals(2, Workforce.shareOf(5, 1, 2));
    }

    @Test
    void aCrewSmallerThanItsSitesLeavesSomeIdle() {
        assertEquals(1, Workforce.shareOf(2, 0, 5));
        assertEquals(1, Workforce.shareOf(2, 1, 5));
        assertEquals(0, Workforce.shareOf(2, 2, 5), "and the rest of the camps stand empty");
        assertEquals(2, total(2, 5), "with nobody invented to fill them");
    }

    @Test
    void aTownWithNoSitesStillCountsItsCrewOnce() {
        // Callers fall back to the town centre when there is no camp at all, so
        // "no sites" has to behave as one site or the produce vanishes.
        assertEquals(4, Workforce.shareOf(4, 0, 0));
        assertEquals(4, total(4, 0));
    }

    @Test
    void nobodyWorkingIsNobodyCounted() {
        assertEquals(0, Workforce.shareOf(0, 0, 3));
        assertEquals(0, Workforce.shareOf(-1, 0, 3), "and a negative crew is not a bonus");
    }

    @Test
    void anIndexOutsideTheSitesGetsNobody() {
        assertEquals(0, Workforce.shareOf(6, 2, 2));
        assertEquals(0, Workforce.shareOf(6, -1, 2));
    }
}
