package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a street leaving town gets cut.
 *
 * <p>This exists because the first version of the trim was checked by looking at
 * the town from the air and was wrong: a ring town's spokes are a single stretch
 * each, so the test "is either end inside the edge" was true of all six of them
 * and none was trimmed at all. The picture showed six roads running past the
 * outermost ring and that read as roads leaving town rather than as the bug it
 * was. Arithmetic this small should be checked by arithmetic.
 */
class BuildTestTrimTest {

    private static final SimPos CENTRE = new SimPos(0, 64, 0);

    @Test
    void aStretchIsCutWhereItCrossesTheEdge() {
        SimPos cut = BuildTest.whereItCrosses(
                CENTRE, new SimPos(14, 64, 0), new SimPos(200, 64, 0), 143);
        assertEquals(143, cut.x(), "cut on the circle, due east");
        assertEquals(0, cut.z());
    }

    @Test
    void theCutKeepsTheHeightOfTheStretch() {
        SimPos cut = BuildTest.whereItCrosses(
                CENTRE, new SimPos(0, 71, 14), new SimPos(0, 71, 200), 143);
        assertEquals(71, cut.y(), "y is the plan's, not the ground's");
    }

    @Test
    void aDiagonalStretchIsCutOnTheCircleNotTheBox() {
        SimPos cut = BuildTest.whereItCrosses(
                CENTRE, new SimPos(10, 64, 10), new SimPos(300, 64, 300), 100);
        double away = Math.hypot(cut.x(), cut.z());
        assertTrue(Math.abs(away - 100) <= 1,
                "a diagonal should be cut at radius 100, was " + away);
    }

    @Test
    void aStretchThatNeverLeavesIsNotMoved() {
        SimPos inside = new SimPos(10, 64, 0);
        SimPos alsoInside = new SimPos(20, 64, 0);
        SimPos cut = BuildTest.whereItCrosses(CENTRE, inside, alsoInside, 143);
        assertEquals(alsoInside, cut, "nothing to cut: hand back the far end");
    }

    @Test
    void aStretchOfNoLengthIsHandedBackRatherThanDividedByZero() {
        SimPos here = new SimPos(30, 64, 30);
        assertEquals(here, BuildTest.whereItCrosses(CENTRE, here, here, 143));
    }
}
