package com.civilization.sim;

import com.civilization.sim.combat.GuardStance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A guard picks his weapon off what he is looking at, and his feet off how far
 * away it is.
 */
final class GuardStanceTest {

    /** A guard's sword reach, as the view uses it. */
    private static final double REACH = 2.5;

    private static GuardStance.Stance atCreeper(double distance) {
        return GuardStance.against(true, distance, REACH);
    }

    private static GuardStance.Stance atZombie(double distance) {
        return GuardStance.against(false, distance, REACH);
    }

    @Test
    void anythingThatDoesNotExplodeIsMetWithTheSword() {
        assertEquals(GuardStance.Weapon.SWORD, atZombie(1.0).weapon());
        assertEquals(GuardStance.Weapon.SWORD, atZombie(12.0).weapon());
    }

    @Test
    void aZombieOutOfReachIsWalkedAtAndOneInReachIsHit() {
        assertEquals(GuardStance.Move.CLOSE_IN, atZombie(9.0).move());
        assertEquals(GuardStance.Move.HOLD, atZombie(REACH).move());
    }

    @Test
    void aGuardNeverGivesGroundToSomethingThatDoesNotExplode() {
        // Backing away from a zombie is letting it past, and what is behind the
        // guard is a farmer.
        for (double distance = 0.0; distance <= 20.0; distance += 0.5) {
            assertEquals(GuardStance.Weapon.SWORD, atZombie(distance).weapon());
            assertTrue(atZombie(distance).move() != GuardStance.Move.BACK_OFF,
                    "gave ground to a zombie at " + distance);
        }
    }

    @Test
    void aCreeperIsAlwaysMetWithTheBow() {
        for (double distance = 0.0; distance <= 20.0; distance += 0.5) {
            assertEquals(GuardStance.Weapon.BOW, atCreeper(distance).weapon(),
                    "drew a sword on a creeper at " + distance);
        }
    }

    @Test
    void theBandIsEntirelyOutsideTheBlast() {
        // The whole point of the band: standing in it is standing out of the way
        // of the thing going off.
        assertTrue(GuardStance.BAND_NEAR > GuardStance.HURT_RADIUS);
        assertEquals(GuardStance.Move.HOLD, atCreeper(GuardStance.BAND_NEAR).move());
        assertEquals(GuardStance.Move.HOLD, atCreeper(11.0).move());
        assertEquals(GuardStance.Move.HOLD, atCreeper(GuardStance.BAND_FAR).move());
    }

    @Test
    void aCreeperInsideTheBandIsBackedAwayFrom() {
        assertEquals(GuardStance.Move.BACK_OFF, atCreeper(7.9).move());
        assertEquals(GuardStance.Move.BACK_OFF, atCreeper(1.0).move());
    }

    @Test
    void aCreeperBeyondTheBandIsClosedOn() {
        assertEquals(GuardStance.Move.CLOSE_IN, atCreeper(14.1).move());
        assertEquals(GuardStance.Move.CLOSE_IN, atCreeper(19.0).move());
    }

    @Test
    void aBowmanShootsWhileHoldingAndWhileGivingGround() {
        assertTrue(atCreeper(11.0).shoot());
        assertTrue(atCreeper(3.0).shoot(), "a bowman walking backwards is still a bowman");
        assertTrue(atCreeper(GuardStance.BAND_FAR).shoot());
    }

    @Test
    void nobodyLoosesAnArrowAtSomethingTooFarToHit() {
        assertFalse(atCreeper(14.5).shoot());
        assertFalse(atCreeper(20.0).shoot());
    }

    @Test
    void aSwordsmanNeverLoosesAnArrow() {
        assertFalse(atZombie(2.0).shoot());
        assertFalse(atZombie(11.0).shoot());
    }
}
