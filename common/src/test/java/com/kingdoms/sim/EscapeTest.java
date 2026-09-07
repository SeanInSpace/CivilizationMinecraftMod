package com.kingdoms.sim;

import com.kingdoms.sim.geom.Escape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arithmetic a frightened settler does instead of running faster. */
final class EscapeTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void aPointBesideTheRunIsItsPerpendicularDistance() {
        assertEquals(3.0, Escape.distanceToRun(0, 0, 10, 0, 5, 3), EPSILON);
    }

    @Test
    void aPointOnTheRunIsOnIt() {
        assertEquals(0.0, Escape.distanceToRun(0, 0, 10, 0, 4, 0), EPSILON);
    }

    @Test
    void theRunIsASegmentAndNotAnInfiniteLine() {
        // Twenty blocks behind the start, dead on the line extended backwards.
        // A creeper there is not standing in the way ahead.
        assertEquals(20.0, Escape.distanceToRun(0, 0, 10, 0, -20, 0), EPSILON);
    }

    @Test
    void aRunOfNoLengthIsJustThePlaceItStarts() {
        assertEquals(5.0, Escape.distanceToRun(2, 2, 2, 2, 2, 7), EPSILON);
    }

    @Test
    void aDoorBeyondTheCreeperIsRefused() {
        // Settler at the origin, door twenty blocks east, creeper squarely
        // between them. Running for that door runs at the creeper.
        assertTrue(Escape.runPassesWithin(0, 0, 20, 0, 9, 1, 7.0));
    }

    @Test
    void aDoorWellOffTheCreepersSideIsAllowed() {
        assertFalse(Escape.runPassesWithin(0, 0, 20, 0, 9, 12, 7.0));
    }

    @Test
    void theRadiusIsInclusiveAtTheEdge() {
        assertTrue(Escape.runPassesWithin(0, 0, 20, 0, 10, 7, 7.0));
    }

    @Test
    void aWalkerNeedsFarMoreWarningThanTheBlastRadius() {
        // The live numbers: a blast that hurts to seven blocks; a creeper whose
        // movement-speed attribute is 0.25 with a modifier of 1.0; a settler
        // whose 0.5 attribute times the one walking pace of 0.7 is 0.35; a
        // manager pass every twenty ticks, so up to a second before the body is
        // actually under way; three seconds of a bending path over which only
        // half the pace is outward progress.
        double notice = Escape.noticeDistance(7.0, 0.25, 0.35, 20, 60, 0.5);
        //  7            the margin that must survive
        // +5    = 0.25 * 20      given away standing still
        // +4.5  = (0.25 - 0.175) * 60   given away while the path straightens
        assertEquals(16.5, notice, EPSILON);
    }

    @Test
    void aSettlerAlreadyPullingAwayLosesNothingToTheBend() {
        // Same walk, but on open ground where the path points away at once.
        // Outward progress 0.35 beats the creeper's 0.25, so the settling term
        // is zero rather than negative -- distance gained is not credit.
        double notice = Escape.noticeDistance(7.0, 0.25, 0.35, 20, 60, 1.0);
        assertEquals(12.0, notice, EPSILON);
    }

    @Test
    void aFasterThreatDemandsMoreWarning() {
        double slow = Escape.noticeDistance(7.0, 0.25, 0.35, 20, 60, 0.5);
        double quick = Escape.noticeDistance(7.0, 0.30, 0.35, 20, 60, 0.5);
        assertTrue(quick > slow, "a threat that closes quicker has to be seen sooner");
    }
}
