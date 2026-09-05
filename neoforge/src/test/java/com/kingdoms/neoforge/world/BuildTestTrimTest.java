package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which ground the renderer decides to pave.
 *
 * <p>This exists because the rule has now been wrong twice and neither time was
 * caught by looking. The first version kept a stretch when either end was inside
 * a radius, which cannot trim a street made of one stretch — so a ring town's
 * spokes paved in full, and the photograph of them running off the frame read as
 * roads leaving town. The second measured that radius from the town's center,
 * which throws away a road running <em>alongside</em> the town: a thorp's outer
 * tracks sit further from the middle than its furthest house, so all three went,
 * and their lanes were left in the grass with yards on the end and no road home.
 *
 * <p>Both faults are invisible in a screenshot and obvious in a number.
 */
class BuildTestTrimTest {

    /** A house on a road that runs past the middle of town, and one that does not. */
    private static final SimPos NEAR_THE_MIDDLE = new SimPos(0, 64, 0);
    private static final SimPos OUT_ON_A_FLANK = new SimPos(150, 64, 0);

    @Test
    void groundBesideAHouseIsPaved() {
        assertTrue(BuildTest.servesSomebody(new SimPos(13, 64, 0),
                List.of(NEAR_THE_MIDDLE)), "a road at the setback serves the house on it");
    }

    @Test
    void groundBetweenTwoHousesIsPavedSoAStreetIsNotDashed() {
        // The midpoint of a street between neighbors a pitch apart, at the
        // setback: about twenty-one blocks from either. If this fails a street
        // comes out in pieces.
        List<SimPos> houses = List.of(new SimPos(0, 64, 0), new SimPos(0, 64, 28));
        assertTrue(BuildTest.servesSomebody(new SimPos(13, 64, 14), houses),
                "the road between two houses is still their road");
    }

    @Test
    void groundFarPastTheLastHouseIsNotPaved() {
        assertFalse(BuildTest.servesSomebody(new SimPos(200, 64, 0),
                List.of(NEAR_THE_MIDDLE)), "a spoke stops a little past the last house");
    }

    @Test
    void aRoadRunningAlongsideTheTownIsKeptEvenThoughItIsFarFromTheMiddle() {
        // The fault that stranded a thorp's lanes. This ground is 150 blocks from
        // the center and 3 from a house; the old rule asked the first number.
        assertTrue(BuildTest.servesSomebody(new SimPos(153, 64, 0),
                List.of(OUT_ON_A_FLANK)),
                "a track alongside the town serves the houses on it");
    }

    @Test
    void aStretchIsCutIntoPiecesAlongItsLength() {
        SimPos from = new SimPos(0, 71, 0);
        SimPos to = new SimPos(100, 71, 0);
        assertEquals(new SimPos(0, 71, 0), BuildTest.along(from, to, 0.0));
        assertEquals(new SimPos(50, 71, 0), BuildTest.along(from, to, 0.5));
        assertEquals(new SimPos(100, 71, 0), BuildTest.along(from, to, 1.0));
    }

    @Test
    void aPieceKeepsTheHeightOfTheStretch() {
        SimPos cut = BuildTest.along(new SimPos(0, 71, 0), new SimPos(0, 71, 80), 0.25);
        assertEquals(71, cut.y(), "y is the plan's, not the ground's");
        assertEquals(20, cut.z());
    }
}
