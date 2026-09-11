package com.civilization.sim;

import com.civilization.sim.combat.FiringPoint;
import com.civilization.sim.combat.GuardStance;
import com.civilization.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a bowman walks to when the creeper is behind something.
 *
 * <p>No world here — the wall is a {@link Predicate}, which is exactly how the
 * view hands one in.
 */
final class FiringPointTest {

    private static final SimPos CREEPER = new SimPos(0, 64, 0);

    /** Everything is a usable stand. */
    private static final Predicate<SimPos> ANYWHERE = stand -> true;

    /** Nothing is. */
    private static final Predicate<SimPos> NOWHERE = stand -> false;

    /** A stand is usable only if it is on the far side of the x = 0 line. */
    private static Predicate<SimPos> onlyWest() {
        return stand -> stand.x() < 0;
    }

    @Test
    void heWalksToTheNearestStandHeCanShootFrom() {
        // Due east of the creeper, with nothing in the way: the stand he wants is
        // the one due east of it, at the ring radius, which is the nearest point
        // of the ring to him and also the one he barely has to move for.
        SimPos guard = new SimPos(20, 64, 0);
        Optional<SimPos> stand = FiringPoint.nearest(guard, CREEPER, ANYWHERE);
        assertTrue(stand.isPresent(), "an unobstructed ring offered nowhere to stand");
        assertEquals(new SimPos((int) Math.round(FiringPoint.RING_RADIUS), 64, 0),
                stand.get(), "he was sent past the nearest stand on the ring");

        // And nothing on the ring is nearer to him than what he was given.
        double chosen = stand.get().horizontalDistance(guard);
        for (SimPos other : everyStand(guard)) {
            assertTrue(other.horizontalDistance(guard) >= chosen - 1e-9,
                    other + " is nearer than the stand he was sent to");
        }
    }

    @Test
    void aWallOnHisSideSendsHimRoundIt() {
        // He is east of the creeper; only western stands are usable. He must be
        // sent west, and to the nearest western one rather than the far side.
        SimPos guard = new SimPos(20, 64, 0);
        SimPos stand = FiringPoint.nearest(guard, CREEPER, onlyWest()).orElseThrow();
        assertTrue(stand.x() < 0, "he was sent to a stand the predicate refused");

        double chosen = stand.horizontalDistance(guard);
        for (SimPos other : everyStand(guard)) {
            if (other.x() < 0) {
                assertTrue(other.horizontalDistance(guard) >= chosen - 1e-9,
                        "there was a usable stand nearer than the one he was sent to");
            }
        }
    }

    @Test
    void nowhereToStandIsAnAnswerAndNotAGuess() {
        SimPos guard = new SimPos(20, 64, 0);
        assertFalse(FiringPoint.nearest(guard, CREEPER, NOWHERE).isPresent(),
                "a guard with no clear stand was sent somewhere anyway");
    }

    @Test
    void heIsNeverSentIntoTheBlast() {
        SimPos guard = new SimPos(3, 64, 0);
        // Even asked for a ring drawn well inside the blast, and with a predicate
        // that would happily accept any of it.
        assertFalse(FiringPoint.nearest(guard, CREEPER, 4.0, 12, ANYWHERE).isPresent(),
                "a ring inside the hurt radius produced a stand");

        // And on the real ring, every candidate is outside it.
        for (SimPos stand : everyStand(guard)) {
            assertTrue(stand.horizontalDistance(CREEPER) > GuardStance.HURT_RADIUS,
                    stand + " is inside a creeper's blast");
        }
    }

    @Test
    void theRingIsInsideTheBandAtBothEdges() {
        // A stand outside the band is a stand GuardStance would immediately walk
        // him off again, so the ring has to sit between the two.
        assertTrue(FiringPoint.RING_RADIUS > GuardStance.BAND_NEAR,
                "the ring is inside the blast band and he would back off it");
        assertTrue(FiringPoint.RING_RADIUS < GuardStance.BAND_FAR,
                "the ring is beyond his range and he would close on it");
    }

    @Test
    void theSearchIsBoundedAndAsksNoMoreThanItHasTo() {
        List<SimPos> asked = new ArrayList<>();
        SimPos guard = new SimPos(20, 64, 0);

        FiringPoint.nearest(guard, CREEPER, stand -> {
            asked.add(stand);
            return false;
        });
        assertTrue(asked.size() <= FiringPoint.CANDIDATES,
                "the search asked the world " + asked.size() + " questions for one guard");
        assertFalse(asked.isEmpty(), "the search asked nothing at all");

        // And it stops the moment it has an answer.
        asked.clear();
        FiringPoint.nearest(guard, CREEPER, stand -> {
            asked.add(stand);
            return true;
        });
        assertEquals(1, asked.size(), "it kept searching after it had found a stand");
    }

    @Test
    void noStandIsOfferedTwice() {
        List<SimPos> asked = new ArrayList<>();
        FiringPoint.nearest(new SimPos(20, 64, 0), CREEPER, stand -> {
            asked.add(stand);
            return false;
        });
        assertEquals(asked.size(), asked.stream().distinct().count(),
                "the same stand was checked more than once");
    }

    /** Every candidate the chooser would consider, in the order it considers them. */
    private static List<SimPos> everyStand(SimPos guard) {
        List<SimPos> all = new ArrayList<>();
        FiringPoint.nearest(guard, CREEPER, stand -> {
            all.add(stand);
            return false;
        });
        return all;
    }
}
