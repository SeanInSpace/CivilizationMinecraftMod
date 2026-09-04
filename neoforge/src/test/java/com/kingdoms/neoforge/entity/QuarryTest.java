package com.kingdoms.neoforge.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a citizen sits among the things a creature already hunts.
 *
 * <p>Only the decisions that can be made without a body. Everything else
 * {@link Quarry} does needs a live mob with a goal list on it, and a live mob
 * cannot be built here — {@code create} wants a {@code Level} and there is not
 * one. What a creature makes of a citizen at all is a pure question about a
 * class and a registration, and is asked in {@code MenaceTest}.
 */
class QuarryTest {

    @Test
    void aGoalCountsAsTheCreaturesHuntForPeopleIfAPersonWouldSatisfyIt() {
        assertTrue(Quarry.isPlayerHunt(Player.class));
        assertTrue(Quarry.isPlayerHunt(LivingEntity.class),
                "a guardian hunts anything alive, players among them, and should not "
                        + "hold back for a citizen");
        assertFalse(Quarry.isPlayerHunt(AbstractVillager.class),
                "the villager slot is the fallback, not the answer");
        assertFalse(Quarry.isPlayerHunt(IronGolem.class));
        assertFalse(Quarry.isPlayerHunt(Turtle.class));
        assertFalse(Quarry.isPlayerHunt(PersonEntity.class));
    }

    @Test
    void aCitizenGoalSitsOneSlotBehindTheCreaturesOwnHuntForPlayers() {
        // Not alongside it: goals holding the same flag only give way to a
        // strictly lower number, so a tie is a lock-out rather than a tie. One
        // behind is also where vanilla itself puts a hunt for villagers, in the
        // two mobs whose player slot differs and in every one that agrees.
        assertEquals(3, Quarry.citizenSlot(OptionalInt.of(2)),
                "a zombie hunts players at 2 and villagers at 3");
        assertEquals(4, Quarry.citizenSlot(OptionalInt.of(3)),
                "a ravager hunts players at 3 and villagers at 4");
        assertEquals(2, Quarry.citizenSlot(OptionalInt.of(1)),
                "a creeper hunts players at 1 and villagers nowhere");
        assertEquals(8, Quarry.citizenSlot(OptionalInt.of(7)),
                "a modded mob that puts people behind six other errands keeps that order");
        assertEquals(3, Quarry.citizenSlot(OptionalInt.empty()),
                "and a creature that hunts nobody falls where the common case lands");
    }

    @Test
    void whatAGoalHuntsCanStillBeRead() {
        // What this catches is a rename: the field a targeting goal keeps its
        // quarry's class in. What it cannot catch is the module layer refusing
        // the read in the shipped game, because JUnit loads Minecraft off the
        // plain classpath where setAccessible always succeeds. That failure is
        // survivable by construction — every creature lands in the villager slot
        // instead of its own — and is on the list of things only a world can
        // answer.
        Field field = assertDoesNotThrow(
                () -> NearestAttackableTargetGoal.class.getDeclaredField("targetType"),
                "a targeting goal no longer keeps its quarry's class in 'targetType'");
        assertEquals(Class.class, field.getType());
        assertTrue(Quarry.readsWhatGoalsHunt());
    }
}
