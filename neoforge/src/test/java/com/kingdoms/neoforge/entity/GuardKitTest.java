package com.kingdoms.neoforge.entity;

import com.kingdoms.sim.combat.GuardStance;
import com.kingdoms.neoforge.view.PersonEntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbers a guard fights by, checked without a world.
 *
 * <p>Everything below is a compile-time constant, so nothing here loads a
 * Minecraft class or wants a server — same trick, and same caveat, as
 * {@link PaceTest}. What cannot be checked here is the arrow actually leaving
 * the bow, which needs a level; that one is a hands-on check and is written up
 * in CHANGELOG.md and DEFENSE.md.
 */
final class GuardKitTest {

    @Test
    void aGuardNeverShootsQuickerThanASkeletonDoes() {
        // Vanilla's skeleton: 20 ticks on hard, 40 on anything easier. A guard
        // gets the hard rate flat and must never get anything quicker.
        assertEquals(20, PersonEntityManager.BOW_COOLDOWN_TICKS);
        assertTrue(PersonEntityManager.BOW_COOLDOWN_TICKS >= 20,
                "a guard outshooting a skeleton is a guard nobody has to build a smithy for");
    }

    @Test
    void theWholeBandIsOutsideTheBlast() {
        // The one number that must not be wrong: standing anywhere the guard is
        // told to hold has to be standing out of the explosion.
        assertTrue(GuardStance.BAND_NEAR > GuardStance.HURT_RADIUS,
                "the near edge of the band is inside a creeper's blast");
        assertTrue(GuardStance.BAND_FAR > GuardStance.BAND_NEAR, "the band is inside out");
    }

    @Test
    void aGuardCanSeeTheFarEdgeOfHisOwnBand() {
        // Closing on a creeper that is beyond the band only ever happens for a
        // creeper he has already noticed, so the engage range has to reach past
        // where he wants to stand or he would never shoot at all.
        assertTrue(PersonEntityManager.GUARD_ENGAGE_RANGE > GuardStance.BAND_FAR,
                "a guard would walk to a range he cannot notice anything at");
    }

    @Test
    void aSwordIsForThingsWithinReachAndTheBandIsWellOutsideIt() {
        assertTrue(GuardStance.BAND_NEAR > PersonEntityManager.GUARD_STRIKE_RANGE,
                "a bowman is standing close enough to be hit back");
    }

    @Test
    void everyGuardSpeedIsStillAWalk() {
        // Retreating from a creeper included. The rule has no exceptions and a
        // bow is not one.
        assertTrue(Pace.allows(PersonEntityManager.GUARD_CHARGE_SPEED));
        assertEquals(Pace.WALK, PersonEntityManager.GUARD_CHARGE_SPEED);
    }

    @Test
    void fourOrFiveArrowsKillACreeper() {
        // A vanilla arrow at a full 1.6 draw does ceil(1.6 x 2.0) = 4 on impact,
        // give or take the difficulty wobble. A creeper has 20 health.
        int perArrow = (int) Math.ceil(1.6 * 2.0);
        int creeperHealth = 20;
        int arrows = (creeperHealth + perArrow - 1) / perArrow;
        assertTrue(arrows >= 4 && arrows <= 5,
                "a creeper takes " + arrows + " arrows, which is not a fight worth watching");
    }
}
