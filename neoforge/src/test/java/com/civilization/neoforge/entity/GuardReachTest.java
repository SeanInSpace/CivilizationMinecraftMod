package com.civilization.neoforge.entity;

import com.civilization.neoforge.view.PersonEntityManager;
import com.civilization.sim.combat.GuardStance;
import com.civilization.sim.combat.Watch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far the watch looks, tied to how far everybody else looks.
 *
 * <p>{@code WatchTest} has the arithmetic; this is the join. The distance a
 * civilian notices a creeper at lives in the view layer, in {@link
 * FleeCreepersGoal}, and the watch's reach lives in {@code :common} — so the one
 * assertion that actually keeps the two honest can only be made here, and this
 * file exists to make it.
 *
 * <p>Constants only, so nothing wants a server.
 */
final class GuardReachTest {

    @Test
    void theWatchNoticesFurtherThanTheFarmersDo() {
        // The fault, stated as the rule it broke: a creeper thirteen blocks from
        // a farmer had already stopped that farmer working and sent him home --
        // NOTICE is eighteen -- while the guard twenty blocks the other side of
        // him had declined to look at it, because the old reach was twenty. A
        // town whose civilians can see further than its watch is a town telling
        // the player the watch does not work.
        assertTrue(PersonEntityManager.GUARD_ENGAGE_RANGE > FleeCreepersGoal.NOTICE,
                "the watch sees less far than the people it is posted over");
    }

    @Test
    void theWatchIsAlreadyWalkingBeforeAnybodyHasToRun() {
        // And by more than the ground the farmer covers getting clear of the
        // blast, which is the whole margin: a guard who starts when the farmer
        // starts arrives at an explosion.
        assertTrue(PersonEntityManager.GUARD_ENGAGE_RANGE
                        > FleeCreepersGoal.NOTICE + GuardStance.HURT_RADIUS,
                "the watch and the panic start at the same moment");
    }

    @Test
    void theViewLayerAndTheRuleAgreeOnOneNumber() {
        // Two names for the reach would be two reaches the day somebody edits one.
        assertEquals(Watch.ENGAGE_RANGE, PersonEntityManager.GUARD_ENGAGE_RANGE);
    }

    @Test
    void aCreeperIsFledFromWellInsideTheBlastItIsFledFrom() {
        // The number the notice distance is derived from, restated here so that
        // this file's two comparisons cannot quietly become vacuous.
        assertEquals(GuardStance.HURT_RADIUS, FleeCreepersGoal.HURT,
                "the guards' blast radius and the civilians' disagree");
    }
}
