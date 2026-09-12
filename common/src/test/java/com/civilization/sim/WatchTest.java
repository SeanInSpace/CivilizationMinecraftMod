package com.civilization.sim;

import com.civilization.sim.combat.GuardStance;
import com.civilization.sim.combat.Watch;
import com.civilization.sim.combat.Weaponry;
import com.civilization.sim.settlement.Alarm;
import com.civilization.sim.settlement.Danger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether the watch answers, and whether anybody else does.
 *
 * <p><strong>What was reported.</strong> Skullwatch, an orc town on superflat,
 * normal difficulty. A creeper was put down thirteen blocks east of a farmer with
 * a guard about twenty blocks off; twelve seconds later it was untouched at full
 * health and no arrow had ever existed. A zombie put down a block and a half from
 * another orc civilian was still at full health eight seconds later. The town's
 * threat level read zero throughout, with the creeper inside the claim.
 *
 * <p>Everything below is the decision that was wrong and the two that were not,
 * pinned where they can be checked without a world: the reach, the absence of any
 * alarm gate, and a civilian's three refusals.
 */
final class WatchTest {

    @Test
    void theWatchLooksFurtherThanItsTownsfolkNoticeACreeper() {
        // The derivation, and the reason 26 is not a number picked for comfort.
        // A civilian notices a creeper at 18 and runs the blast radius' worth of
        // ground away from it; a watch that sees less far than that is a watch
        // whose farmers run from things nobody is coming for. The 18 itself lives
        // in the view layer (FleeCreepersGoal.NOTICE) and is checked against this
        // in GuardReachTest; the arithmetic is here.
        double civilianNotices = 18.0;
        assertTrue(Watch.ENGAGE_RANGE > civilianNotices + GuardStance.HURT_RADIUS,
                "a farmer would run from a creeper no guard had looked at");
    }

    @Test
    void theEngageRangeIsAtLeastTwentyFourBlocks() {
        // The floor the playtest bought. Twenty was the old figure and it was
        // spent as the half-width of a box rather than as a distance: a guard
        // twenty blocks from a farmer, with a creeper thirteen blocks the other
        // side of him, was between twenty-four and thirty-three blocks off it and
        // declined the fight without anything being logged.
        assertTrue(Watch.ENGAGE_RANGE >= 24.0,
                "the reach that let a creeper stand inside a claim unanswered");
    }

    @Test
    void aHostileInsideTheRangeIsAnsweredAtEveryAlarmTier() {
        // The first guess anybody makes about "threatLevel was 0 and no guard
        // moved" is that the watch was waiting to be told. It was not, and this
        // is the test that stops that gate from ever being added: the alarm is
        // handed in and must make no difference to the answer.
        for (int danger = 0; danger <= Danger.HOPELESS; danger++) {
            Alarm alarm = Alarm.of(danger);
            assertTrue(Watch.answers(true, 1.0, alarm),
                    "a hostile in arm's reach went unanswered at " + alarm);
            assertTrue(Watch.answers(true, Watch.ENGAGE_RANGE, alarm),
                    "a hostile at the edge of the range went unanswered at " + alarm);
            assertFalse(Watch.answers(false, 1.0, alarm),
                    "the watch went for something the town is not afraid of at " + alarm);
        }
    }

    @Test
    void nothingPastTheRangeIsAnybodysBusinessYet() {
        assertFalse(Watch.answers(true, Watch.ENGAGE_RANGE + 0.01, Alarm.ALARMED));
        assertFalse(Watch.answers(true, 100.0, Alarm.ALARMED),
                "a guard is posted over a town, not over a world");
    }

    /** A guard's sword reach, as the view layer uses it. */
    private static final double MELEE = 2.5;

    private static boolean civilian(boolean struckByIt, boolean itBlowsUp, double range) {
        return Watch.strikesBack(true, struckByIt, itBlowsUp, range, MELEE);
    }

    @Test
    void aCivilianDoesNotStrikeAZombieThatHasNotTouchedHim() {
        // The zombie put down a block and a half from an orc civilian. Nothing
        // happened to it and nothing should have: he is not a guard, and a
        // settler who swings at whatever walks past him is a watch the town never
        // posted. This half of the report is the code behaving correctly.
        assertFalse(civilian(false, false, 1.5),
                "an armed settler went looking for a fight");
    }

    @Test
    void aCivilianDoesStrikeAZombieThatHasBittenHim() {
        assertTrue(civilian(true, false, 1.5));
        assertTrue(civilian(true, false, MELEE), "right at the end of his reach");
    }

    @Test
    void aCivilianNeverAnswersACreeper() {
        // A cleaver has never been the answer to a fuse. The flight goal has this
        // one and always did.
        assertFalse(civilian(true, true, 1.0));
    }

    @Test
    void aCivilianDoesNotChaseWhatHasWalkedOff() {
        assertFalse(civilian(true, false, MELEE + 0.01));
        assertFalse(civilian(true, false, Weaponry.CIVILIAN_REACH),
                "his own reach is the looser of the two bounds and must not bind");
    }

    @Test
    void anUnarmedSettlerSwingsAtNothingHoweverHardHeHasBeenHit() {
        assertFalse(Watch.strikesBack(false, true, false, 1.0, MELEE));
    }

    @Test
    void theWatchSeesFurtherThanAnybodyElseReaches() {
        // The two rules must not cross: a civilian's quarrel ends inside arm's
        // length and a guard's begins across a field.
        assertTrue(Watch.ENGAGE_RANGE > Weaponry.CIVILIAN_REACH);
        assertTrue(Watch.ENGAGE_RANGE > GuardStance.BAND_FAR,
                "a guard would close to a band he cannot notice anything at");
    }
}
