package com.civilization.neoforge.entity;

import com.civilization.neoforge.view.FarmWorker;
import com.civilization.neoforge.view.Foreman;
import com.civilization.neoforge.view.LumberjackWorker;
import com.civilization.neoforge.view.MinerWorker;
import com.civilization.neoforge.view.PersonEntityManager;
import com.civilization.neoforge.view.ShepherdWorker;
import com.civilization.neoforge.world.Excavation;
import com.civilization.sim.culture.Race;
import com.civilization.sim.geom.Escape;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nobody in this town has a second gear.
 *
 * <p>Every speed a citizen can be handed is named here, and the rule is that
 * none of them is above {@link Pace#WALK}. That is the whole of the fix for
 * "citizens move dramatically quicker when mobs are nearby": the panic speeds
 * are gone, not tuned.
 *
 * <p>These are all compile-time constants, so the numbers below are inlined at
 * javac time and nothing here loads a Minecraft class or wants a server. The
 * cost of that is the cost of any inlined constant -- this test sees the value
 * as of the last compile, which for a Gradle build is the value in the file.
 */
final class PaceTest {

    /** Every speed the game can hand a citizen, by where it is handed out. */
    private static Map<String, Double> everySpeedACitizenIsGiven() {
        Map<String, Double> speeds = new LinkedHashMap<>(Pace.all());
        speeds.put("daily routine", PersonEntityManager.WALK_SPEED);
        speeds.put("called in by the alarm", PersonEntityManager.SHELTER_SPEED);
        speeds.put("guard closing on a hostile", PersonEntityManager.GUARD_CHARGE_SPEED);
        speeds.put("fleeing a creeper, with room", FleeCreepersGoal.RETREAT_SPEED);
        speeds.put("fleeing a creeper, without", FleeCreepersGoal.PANIC_SPEED);
        speeds.put("farmer to the next row", FarmWorker.WALK_SPEED);
        speeds.put("builder to the next post", Foreman.WALK_SPEED);
        speeds.put("lumberjack to the next tree", LumberjackWorker.WALK_SPEED);
        speeds.put("miner to the next face", MinerWorker.WALK_SPEED);
        speeds.put("shepherd to the pens", ShepherdWorker.WALK_SPEED);
        speeds.put("digger to the next stand", Excavation.DIG_WALK_SPEED);
        return speeds;
    }

    @Test
    void nobodyIsEverGivenASpeedAboveTheWalkingPace() {
        for (Map.Entry<String, Double> speed : everySpeedACitizenIsGiven().entrySet()) {
            assertTrue(Pace.allows(speed.getValue()),
                    speed.getKey() + " is " + speed.getValue()
                            + ", above the walking pace of " + Pace.WALK);
        }
    }

    @Test
    void dangerBuysNoSpeedAtAll() {
        // The specific complaint: a settler who sees a creeper, a town that hears
        // the bell, and a guard on his way to a fight all move at exactly the
        // pace they were moving at a moment before.
        assertEquals(Pace.WALK, FleeCreepersGoal.PANIC_SPEED);
        assertEquals(Pace.WALK, FleeCreepersGoal.RETREAT_SPEED);
        assertEquals(Pace.WALK, PersonEntityManager.SHELTER_SPEED);
        assertEquals(Pace.WALK, PersonEntityManager.GUARD_CHARGE_SPEED);
        assertEquals(Pace.WALK, PersonEntityManager.WALK_SPEED);
    }

    @Test
    void theWalkingPaceKeepsTheFastestOrdinaryWalkTheTownAlreadyHad() {
        // 0.7 was a lumberjack's, a miner's and a digger's walk before any of
        // this. Collapsing the range upward is the choice where nobody is
        // visibly slower than players have already seen them.
        assertEquals(0.7, Pace.WALK);
    }

    @Test
    void aStrollIsSlowerThanAWalkAndIsTheOnlyThingThatIs() {
        assertTrue(Pace.STROLL < Pace.WALK);
    }

    @Test
    void theNoticeRadiusCoversWhatAWalkerActuallyNeeds() {
        // A creeper's movement-speed attribute is 0.25 with a modifier of 1.0; a
        // settler's 0.5 attribute at the walking pace is 0.35. Allow a manager
        // pass (twenty ticks) before the body is under way, and three seconds of
        // a path that only points half its length outward while it bends.
        double needed = Escape.noticeDistance(FleeCreepersGoal.HURT, 0.25,
                0.5 * Pace.WALK, 20, 60, 0.5);
        assertEquals(16.5, needed, 1.0e-9);
        assertTrue(FleeCreepersGoal.NOTICE >= needed,
                "a settler noticed at " + FleeCreepersGoal.NOTICE
                        + " cannot stay outside " + FleeCreepersGoal.HURT
                        + " blocks; they need " + needed);
    }

    @Test
    void theOldTenBlockNoticeWouldNotHaveBeenEnough() {
        double needed = Escape.noticeDistance(FleeCreepersGoal.HURT, 0.25,
                0.5 * Pace.WALK, 20, 60, 0.5);
        assertTrue(10.0 < needed, "the reported bug was real: ten blocks was short");
    }

    @Test
    void aWalkingSettlerStillOutpacesACreeperOnOpenGround() {
        // The pace had to stay above the creeper's 0.25 blocks a tick or the
        // whole plan is a settler walking calmly to their death.
        assertTrue(PersonEntity.BASE_MOVEMENT_SPEED * Pace.WALK > 0.25);
    }

    /** What the game actually walks one of these at, in blocks per tick. */
    private static double walkingSpeedOf(Race race) {
        return PersonEntity.BASE_MOVEMENT_SPEED * race.paceFactor() * Pace.WALK;
    }

    @Test
    void anOrcWalksSlowerThanAManAndAGoblinFaster() {
        // The user's ask, as an ordering rather than as three numbers: orcs
        // minimally slower, and a goblin at the other end so the table is a body
        // and not a difficulty setting.
        assertTrue(walkingSpeedOf(Race.ORC) < walkingSpeedOf(Race.HUMAN),
                "an orc at " + walkingSpeedOf(Race.ORC) + " is not slower than a man at "
                        + walkingSpeedOf(Race.HUMAN));
        assertTrue(walkingSpeedOf(Race.GOBLIN) > walkingSpeedOf(Race.HUMAN),
                "a goblin at " + walkingSpeedOf(Race.GOBLIN) + " is not quicker than a man");
        assertEquals(0.35, walkingSpeedOf(Race.HUMAN), 1.0e-9,
                "a man's walk must stay exactly what it has always been");
    }

    @Test
    void theRaceLivesInTheAttributeAndNeverInThePace() {
        // The whole reason the factor multiplies the base attribute: every
        // navigation call in the mod still hands over Pace.WALK, so "nobody is
        // ever given a speed above their walking pace" holds per race without a
        // single pace having to know a race exists. A race that reached the
        // modifier instead would need this rule restated three times.
        for (Race race : Race.values()) {
            double ownWalk = walkingSpeedOf(race);
            for (Map.Entry<String, Double> speed
                    : everySpeedACitizenIsGiven().entrySet()) {
                double effective = PersonEntity.BASE_MOVEMENT_SPEED
                        * race.paceFactor() * speed.getValue();
                assertTrue(effective <= ownWalk + 1.0e-9,
                        "a " + race.word() + " " + speed.getKey() + " moves at "
                                + effective + ", above their own walk of " + ownWalk);
            }
        }
    }

    @Test
    void everyRaceStillOutpacesACreeperAndIsNoticedInTime() {
        // The slowest race is the one this can fail for. An orc walks at 0.315
        // against a creeper's 0.25, and needs 17.55 blocks of warning against the
        // eighteen the goal gives — which holds, but not by much, and is exactly
        // the sort of margin that disappears silently when somebody retunes a
        // pace factor.
        for (Race race : Race.values()) {
            double walk = walkingSpeedOf(race);
            assertTrue(walk > 0.25,
                    race + " walks at " + walk + ", slower than a creeper");
            double needed = Escape.noticeDistance(FleeCreepersGoal.HURT, 0.25,
                    walk, 20, 60, 0.5);
            assertTrue(FleeCreepersGoal.NOTICE >= needed,
                    race + " needs " + needed + " blocks of warning and is noticed at "
                            + FleeCreepersGoal.NOTICE);
        }
    }

    @Test
    void anOrcTakesMorePunishmentThanAManAndAGoblinLess() {
        // The health half of the same table. Applied to the body on embody by
        // PersonEntity.applyRace, which cannot be built without a level -- so
        // this checks the numbers it will apply, and the manual check is in the
        // changelog entry: /civ found an orc town, hit a settler, count the hits.
        assertTrue(Race.ORC.maxHealth() > Race.HUMAN.maxHealth());
        assertTrue(Race.GOBLIN.maxHealth() < Race.HUMAN.maxHealth());
        assertEquals(PersonEntity.BASE_MAX_HEALTH, Race.HUMAN.maxHealth(),
                "a settler's registered health and a human's must be the same number, "
                        + "or applying a race to a human would change him");
    }
}
