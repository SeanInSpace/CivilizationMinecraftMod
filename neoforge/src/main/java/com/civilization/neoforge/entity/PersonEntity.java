package com.civilization.neoforge.entity;

import com.civilization.neoforge.CivilizationAttachments;
import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.net.PersonInventoryPayload;
import com.civilization.sim.culture.Race;
import com.civilization.sim.person.Appetite;
import com.civilization.sim.person.Foods;
import com.civilization.sim.person.Inventory;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.KingPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The view entity for a simulated person — a plain humanoid, not a villager.
 *
 * <p>Deliberately almost mindless. The old villager Brain assumed the entity was
 * the source of truth (its own home, job, gossip); in Civilization all of that lives
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

    /**
     * The movement-speed attribute every body is registered with.
     *
     * <p>What a navigation modifier is a fraction of: {@link Pace#WALK} of this
     * is 0.35 blocks a tick, which is the number the game actually walks a
     * settler at. A race multiplies <em>this</em> (see {@link #applyRace}), never
     * the modifier, so "nobody is ever given a speed above the walking pace"
     * stays true of every race without the paces having to know about races.
     */
    public static final double BASE_MOVEMENT_SPEED = 0.5;

    /** The health a body is registered with, before its race is applied. */
    public static final double BASE_MAX_HEALTH = 20.0;

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    /**
     * Stamps a race's body onto this one, and fills it up.
     *
     * <p>Attributes are registered per entity type and there is one settler
     * type, so the race cannot live in {@link #createAttributes} — it is set on
     * the body when the body is made, which is the only moment a person's
     * culture is known to the thing holding their legs. Called once, on embody;
     * a released body is discarded outright and the next one is built fresh, so
     * there is no second application to keep idempotent.
     *
     * <p>Healing afterwards is not a kindness, it is arithmetic: raising a max
     * health does not raise the current one, so an orc who was not healed would
     * spawn at twenty out of thirty and look wounded from the moment he
     * appeared.
     */
    public void applyRace(Race race) {
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(race.maxHealth());
        }
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(BASE_MOVEMENT_SPEED * race.paceFactor());
        }
        setHealth(getMaxHealth());
    }

    /**
     * Puts the crown on this body, or takes it off.
     *
     * <p>Two things, and they go together because they are the same fact: a gold
     * helmet so you can pick the king out of a camp of thirty from the wall, and
     * {@link KingPlanner#KING_HEALTH_FACTOR} on top of his race's health so he is
     * harder to put down than the warband he leads.
     *
     * <p><strong>Not folded into {@link #applyRace}.</strong> That runs once, at
     * embodiment, because a person's race cannot change; a person's <em>title</em>
     * can, and does — a guard is crowned when the great hut is finished and a
     * king is uncrowned when he is killed or when {@code /civ culture} takes the
     * orcs off a town. So this runs every pass and is idempotent by
     * construction: it asks what is already set before it sets anything, which
     * is the same rule the watch's kit keeps.
     *
     * <p>Healed on the way up and never on the way down, for the reason
     * {@link #applyRace} gives: raising a max health does not raise the current
     * one, so a king who was not healed would take the throne looking wounded.
     * Lowering it clamps on its own, which is correct — a man who stops being
     * king does not become healthier for it.
     */
    public void wearCrown(boolean king, Race race) {
        double wanted = king ? race.maxHealth() * KingPlanner.KING_HEALTH_FACTOR
                : race.maxHealth();
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() != wanted) {
            health.setBaseValue(wanted);
            if (king) {
                setHealth(getMaxHealth());
            }
        }
        boolean worn = getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET);
        if (king == worn) {
            return;
        }
        setItemSlot(EquipmentSlot.HEAD,
                king ? new ItemStack(Items.GOLDEN_HELMET) : ItemStack.EMPTY);
        // It is a title and not loot. A crown that dropped would be a crown a
        // player could put on, and the next thing they would want is for it to
        // mean something.
        setDropChance(EquipmentSlot.HEAD, 0.0F);
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

    /**
     * A sleeping settler lies still.
     *
     * <p>Not decoration, and not something vanilla does for us. A villager sleeps
     * through its Brain, whose sleep activity simply replaces everything that
     * moves it; this mob is driven by the old Goal system, and {@code Mob}'s AI
     * step ticks its goals, its navigation and its move control without ever
     * asking whether it is in bed. Without this, the stroll goal fires a few
     * seconds after somebody turns in and walks the body out of the house still
     * lying down.
     *
     * <p>{@code isImmobile} is the one lever that stops all of it at once:
     * {@code LivingEntity.aiStep} skips the entire server AI step when it is
     * true, which is how a Player sleeps. The cost is that the flee goal stops
     * watching too — so a creeper that walks in on a sleeping town is noticed by
     * the manager's peril sweep instead, a second later, and everybody is turned
     * out of bed by that.
     */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isSleeping();
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
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, Pace.STROLL));
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
     *       hungry they are. In creative that opens a screen; in survival it is
     *       a line of chat, as it has always been.</li>
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
            // Third use of isCreative() in the mod, and the first that gates a
            // capability rather than a cost — the other two (above, and the
            // founding charter) only decide whether an item is consumed. The
            // screen is a builder's instrument: it reads a settler's pockets at
            // a glance, which is exactly what you want while testing a town and
            // exactly what you should not have while playing one. Survival keeps
            // the chat line, and so does a body the simulation cannot identify.
            //
            // The instanceof is already true — the guard at the top of this
            // method means the level is a ServerLevel, so a player interacting
            // with an entity in it is a ServerPlayer. Left as a test rather than
            // a cast so an unforeseen caller gets the chat line instead of a
            // ClassCastException.
            if (person != null && player.isCreative() && player instanceof ServerPlayer viewer) {
                PacketDistributor.sendToPlayer(viewer, PersonInventoryPayload.of(name, person));
                return InteractionResult.SUCCESS;
            }
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
                .append(" — ").append(Appetite.of(person.hunger()).word());
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
     * Where this settler runs to when they would rather be indoors: their own
     * house if the simulation knows of one, the town center otherwise.
     *
     * <p>Kept on the body because the goals cannot see the simulation and the
     * manager can. It is refreshed every manager pass, which is often enough —
     * a person's front door does not move between seconds — and it is the same
     * destination the alarm already sends people to, deliberately: a settler who
     * flees a creeper and a settler who is called in by the bell should be seen
     * heading for the same door, or the town looks like it has two ideas about
     * where safety is.
     *
     * <p>Null until the first pass, and null for a body whose record cannot be
     * found. A flight with no shelter falls back to running away, which is what
     * it always did.
     */
    private net.minecraft.core.BlockPos shelter;

    /** See {@link #shelter}. */
    public net.minecraft.core.BlockPos shelter() {
        return shelter;
    }

    /** See {@link #shelter}. Set by the manager, which knows the town. */
    public void setShelter(net.minecraft.core.BlockPos shelter) {
        this.shelter = shelter;
    }

    /**
     * Whether something the danger table calls hostile is inside this settler's
     * notice radius.
     *
     * <p>Distinct from {@link #fleeing}, which is only ever about creepers and is
     * set the instant one comes into view. This is the wider question — a
     * skeleton, a raider, a modded horror, anything {@code Menace} scores above
     * nothing — asked once a manager pass, and it is what stops a miner going
     * back down the shaft while the thing is still standing there. Work resumes
     * when the radius is clear, not when the fear wears off.
     */
    private boolean threatened;

    /** See {@link #threatened}. */
    public boolean isThreatened() {
        return threatened;
    }

    /** See {@link #threatened}. Set by the manager's sweep. */
    public void setThreatened(boolean threatened) {
        this.threatened = threatened;
    }

    /**
     * Whether this settler has any business being at work: no, if they are
     * running from a creeper or standing within notice of anything hostile.
     */
    public boolean isInDanger() {
        return fleeing || threatened;
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
                || !hasData(CivilizationAttachments.PERSON_ID.get())) {
            return null;
        }
        SimWorld world = CivilizationMod.simulationFor(serverLevel);
        if (world == null) {
            return null;
        }
        UUID id = getData(CivilizationAttachments.PERSON_ID.get());
        Person.Id personId = new Person.Id(id);
        Optional<Settlement> settlement = world.settlementOf(personId);
        return settlement.map(s -> s.resident(personId)).orElse(null);
    }
}
