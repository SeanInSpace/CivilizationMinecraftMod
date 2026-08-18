package com.kingdoms.neoforge.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The view entity for a simulated person — a plain humanoid, not a villager.
 *
 * <p>Deliberately almost mindless. The old villager Brain assumed the entity was
 * the source of truth (its own home, job, gossip); in Kingdoms all of that lives
 * in {@code Person}/{@code Settlement} records, and the entity is a disposable
 * view. So this mob carries only ambience through the simple Goal system — float,
 * wander, glance at players — while everything meaningful (walking home, guard
 * combat, jobs, families) is driven from the records by {@code PersonEntityManager}.
 * See VILLAGER_AI.md for the full reasoning.
 */
public final class PersonEntity extends PathfinderMob {

    private static final List<String> GREETINGS = List.of(
            "Fine day for it.",
            "The town grows, doesn't it?",
            "Plenty of work to be done.",
            "Have you seen the walls? Sturdy work.",
            "We manage, raids and all.",
            "New faces are always welcome.");

    public PersonEntity(EntityType<? extends PersonEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    /**
     * Advances the swing timer, which vanilla does not do for peaceful mobs.
     *
     * <p>In 26.2 only {@code Player}, {@code RemotePlayer}, {@code Mannequin} and
     * {@code Monster} call {@code updateSwingTime()} — never {@code LivingEntity}
     * or {@code Mob}. Vanilla has no passive mob that swings, so nothing advances
     * {@code attackAnim} for one: {@code swing()} sets the flag and broadcasts the
     * packet, the timer stays at zero, and the arm never moves. That is why
     * zombies visibly swing and our builders did not.
     *
     * <p>Runs on both sides — the client renders from its own copy of the timer.
     */
    @Override
    public void tick() {
        super.tick();
        updateSwingTime();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** A word in passing. Trades went with the villager body; dialogue can grow here later. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && hand == InteractionHand.MAIN_HAND) {
            String name = hasCustomName() ? getCustomName().getString() : "Settler";
            String line = GREETINGS.get(Math.floorMod(getUUID().hashCode(), GREETINGS.size()));
            player.sendSystemMessage(Component.literal(name + ": \"" + line + "\""));
        }
        return InteractionResult.SUCCESS;
    }
}
