package com.kingdoms.neoforge.entity;

import com.kingdoms.neoforge.KingdomsAttachments;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The view entity for a simulated person — a plain humanoid, not a villager.
 *
 * <p>Deliberately almost mindless. The old villager Brain assumed the entity was
 * the source of truth (its own home, job, gossip); in Kingdoms all of that lives
 * in {@code Person}/{@code Settlement} records, and the entity is a disposable
 * view. So this mob carries only ambience through the simple Goal system — float,
 * wander, glance at players — while everything meaningful (walking home, guard
 * combat, jobs, families) is driven from the records by {@code PersonEntityManager}.
 * <p>The reasoning, since the document that held it is gone: the vanilla villager
 * Brain assumes the entity owns its own state — memories, schedule, job site —
 * which fights a records-first architecture at every turn. Settlers are a plain
 * humanoid with almost no AI of their own, and everything meaningful is driven
 * from outside by the simulation. JobPlanner is our "assign profession",
 * PopulationPlanner our beds-and-breeding, the settlement event log our gossip.
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

    /** Roughly where a settler's eyes sit above their feet, for reach arithmetic. */
    public static final double EYE_ABOVE_FEET = 1.62;

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
        // Above the door goal and everything below it: getting away from a
        // creeper outranks getting to work, and outranks tidily shutting the
        // door behind you.
        goalSelector.addGoal(1, new FleeCreepersGoal(this));
        // Real wooden doors — the kind authored blueprints carry — open for the
        // people who live behind them. Fence gates are not doors to vanilla and
        // are handled by the manager instead; see PersonEntityManager.tendGates.
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(
            net.minecraft.world.level.Level level) {
        net.minecraft.world.entity.ai.navigation.GroundPathNavigation navigation =
                new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        return navigation;
    }

    /**
     * Three ways to deal with a settler by hand:
     *
     * <ul>
     *   <li><strong>Offer food</strong> — hold something edible and right-click to
     *       hand it over. They will eat it when hunger bites, so you can save a
     *       starving town yourself.</li>
     *   <li><strong>Sneak right-click</strong> — read their pockets and how
     *       hungry they are.</li>
     *   <li><strong>Right-click</strong> — a word in passing.</li>
     * </ul>
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.SUCCESS;
        }
        Person person = person();
        String name = hasCustomName() ? getCustomName().getString() : "Settler";

        ItemStack offered = player.getItemInHand(hand);
        if (person != null && !offered.isEmpty() && !player.isShiftKeyDown()) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(offered.getItem()).toString();
            if (Foods.isFood(itemId)) {
                int taken = person.inventory().add(itemId, offered.getCount());
                if (taken > 0) {
                    if (!player.isCreative()) {
                        offered.shrink(taken);
                    }
                    player.sendSystemMessage(Component.literal(
                            name + " accepts " + taken + " " + Foods.displayName(itemId) + "."));
                    return InteractionResult.SUCCESS;
                }
                player.sendSystemMessage(Component.literal(name + " has no room for that."));
                return InteractionResult.SUCCESS;
            }
        }

        if (player.isShiftKeyDown()) {
            player.sendSystemMessage(Component.literal(describe(name, person)));
            return InteractionResult.SUCCESS;
        }

        String line = GREETINGS.get(Math.floorMod(getUUID().hashCode(), GREETINGS.size()));
        player.sendSystemMessage(Component.literal(name + ": \"" + line + "\""));
        return InteractionResult.SUCCESS;
    }

    /** Pockets, appetite, and whatever load they are carrying. */
    private String describe(String name, Person person) {
        if (person == null) {
            return name + " is not answering to any settlement right now.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(name).append(" ===");
        sb.append("\nHunger ").append(person.hunger()).append("/").append(Person.HUNGER_MAX)
                .append(" — ").append(appetite(person.hunger()));
        sb.append("\nCarrying: ").append(person.inventory());
        if (!person.inventory().isEmpty()) {
            sb.append("\n  (worth ").append(person.inventory().totalNutrition())
                    .append(" hunger of meals)");
        }
        if (person.haul() != null) {
            sb.append("\nErrand: ").append(person.haul());
        }
        return sb.toString();
    }

    private static String appetite(int hunger) {
        if (hunger >= Person.HUNGER_SEVERE) {
            return "starving";
        }
        if (hunger >= Person.HUNGER_WEAK) {
            return "weak with hunger";
        }
        if (hunger >= Person.HUNGER_HUNGRY) {
            return "hungry";
        }
        return "well fed";
    }

    /**
     * Whether this body is currently running from something.
     *
     * <p>Owned by {@link FleeCreepersGoal} and read by {@code PersonEntityManager},
     * which otherwise steers every settler toward their workplace once a tick and
     * would drag a fleeing farmer straight back into the blast.
     */
    private boolean fleeing;

    /** See {@link #fleeing}. */
    public boolean isFleeing() {
        return fleeing;
    }

    /** See {@link #fleeing}. Set by the goal that owns the flight. */
    public void setFleeing(boolean fleeing) {
        this.fleeing = fleeing;
    }

    /**
     * Whether the simulation has this body down as one of the watch.
     *
     * <p>A body whose record cannot be found is <em>not</em> a guard. The two
     * callers both use this to decide whether to stand and fight, and an
     * unidentified settler should not be volunteered for that.
     */
    public boolean isGuard() {
        Person person = person();
        return person != null && person.profession() == Profession.GUARD;
    }

    /** The record this body stands for, if the simulation still knows them. */
    private Person person() {
        if (!(level() instanceof ServerLevel serverLevel)
                || !hasData(KingdomsAttachments.PERSON_ID.get())) {
            return null;
        }
        SimWorld world = KingdomsMod.simulationFor(serverLevel);
        if (world == null) {
            return null;
        }
        UUID id = getData(KingdomsAttachments.PERSON_ID.get());
        Person.Id personId = new Person.Id(id);
        Optional<Settlement> settlement = world.settlementOf(personId);
        return settlement.map(s -> s.resident(personId)).orElse(null);
    }
}
