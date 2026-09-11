package com.civilization.neoforge.entity;

import com.civilization.sim.combat.FiringPoint;
import com.civilization.sim.combat.GuardStance;
import com.civilization.neoforge.view.PersonEntityManager;
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
    void heWatchesForASecondBeforeHeShoots() {
        // Vanilla's RangedBowAttackGoal gates its shot on seeTime >= 20 ticks of
        // unbroken sight. A guard gets the same wait, and it must never be
        // nothing: zero would put him back to firing at a creeper that crossed a
        // doorway one frame ago.
        assertEquals(20, PersonEntityManager.SIGHTED_TICKS_BEFORE_SHOT);
        assertTrue(PersonEntityManager.SIGHTED_TICKS_BEFORE_SHOT >= 0,
                "a negative wait is not a wait");
        assertTrue(PersonEntityManager.SIGHTED_TICKS_BEFORE_SHOT > 0,
                "a guard who fires on the first frame he glimpses anything");
    }

    @Test
    void theWaitCostsHimOnePassAndNotAFight() {
        // The wait is spent in whole passes, so anything above one pass' worth
        // would have him watching for two seconds before the first arrow.
        assertTrue(PersonEntityManager.SIGHTED_TICKS_BEFORE_SHOT
                        <= PersonEntityManager.TICK_INTERVAL,
                "a guard would spend more than one pass watching before he shoots");
    }

    @Test
    void theRingHeWalksToIsSomewhereHeIsAllowedToStand() {
        // A firing point outside the band is one GuardStance would walk him
        // straight off again, and one inside the blast is a dead guard.
        assertTrue(FiringPoint.RING_RADIUS > GuardStance.BAND_NEAR,
                "he would be sent to a stand he has to back off from");
        assertTrue(FiringPoint.RING_RADIUS < GuardStance.BAND_FAR,
                "he would be sent to a stand that is out of his own range");
        assertTrue(FiringPoint.RING_RADIUS > GuardStance.HURT_RADIUS,
                "he would be sent to stand in a creeper's blast");
        assertTrue(FiringPoint.RING_RADIUS < PersonEntityManager.GUARD_ENGAGE_RANGE,
                "he would walk to a place he can no longer see the creeper from");
    }

    @Test
    void theSearchForSomewhereToShootFromIsBounded() {
        // Every candidate costs a block clip, once a second, per guard fighting a
        // creeper. The count is a budget and the test is here to keep it one.
        assertTrue(FiringPoint.CANDIDATES > 0, "no candidates is no search");
        assertTrue(FiringPoint.CANDIDATES <= 16,
                "a guard would run " + FiringPoint.CANDIDATES
                        + " ray casts a second looking for somewhere to stand");
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
