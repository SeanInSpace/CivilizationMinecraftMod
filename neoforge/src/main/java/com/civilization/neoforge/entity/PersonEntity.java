package com.civilization.neoforge.entity;

import com.civilization.neoforge.CivilizationAttachments;
import com.civilization.neoforge.CivilizationItems;
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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.item.Item;
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
     * The race this body wears, on the wire.
     *
     * <p>The server knows a settler's race from their town's culture; the client
     * has no settlements, no cultures and no {@code Person}, and the renderer
     * still has to pick a skin. Tracked entity data is the one channel that
     * arrives with the spawn packet rather than after it, so a body is never
     * drawn as the wrong race for a frame on the way in.
     *
     * <p>An ordinal rather than a name because it is one byte either way and a
     * byte is what the serializer takes. Out-of-range values read as
     * {@link Race#HUMAN} — see {@link #race()} — for the same reason
     * {@link Race#of} falls back rather than throwing.
     */
    private static final EntityDataAccessor<Byte> DATA_RACE =
            SynchedEntityData.defineId(PersonEntity.class, EntityDataSerializers.BYTE);

    /**
     * Which of that race's skins this particular settler wears.
     *
     * <p>Synced rather than worked out on the client, and that is the whole
     * subtlety here. The obvious thing is to hash the entity's UUID, which the
     * client already has — but a body is a disposable view: the manager discards
     * it whenever the player walks away and builds a fresh one with a fresh UUID
     * on the way back, so a warband's paint would be reshuffled every time you
     * turned your back. The <em>person's</em> id is stable for a life, and only
     * the server can see it, so the choice is made there and sent.
     */
    private static final EntityDataAccessor<Byte> DATA_SKIN =
            SynchedEntityData.defineId(PersonEntity.class, EntityDataSerializers.BYTE);

    /** The save key the race travels under, and what a spawn egg writes. */
    public static final String RACE_TAG = "civ_race";

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RACE, (byte) Race.HUMAN.ordinal());
        builder.define(DATA_SKIN, (byte) 0);
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
     *
     * <p>The person's id comes along because the same call has to settle what
     * this settler <em>looks</em> like as well as what he can take: the skin and
     * the health are one fact about one body, decided once, at the only moment
     * both the race and the person are in the same room. See {@link #DATA_SKIN}
     * for why the choice cannot be left to the client.
     */
    public void applyRace(Race race, UUID personId) {
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(race.maxHealth());
        }
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(BASE_MOVEMENT_SPEED * race.paceFactor());
        }
        setHealth(getMaxHealth());
        entityData.set(DATA_RACE, (byte) race.ordinal());
        entityData.set(DATA_SKIN, (byte) skinFor(personId));
    }

    /**
     * How many skins a race may have. Two: plain, and war-painted.
     *
     * <p>Not a per-race count on purpose. A race with one skin simply ignores
     * the second index — the renderer's table decides how many of these it can
     * actually honor, and a race that grows a third variant is a row there and
     * nothing here.
     */
    public static final int SKINS_PER_RACE = 2;

    /**
     * Which skin a person wears, spread evenly and the same every time.
     *
     * <p>{@code floorMod} rather than {@code %} because a UUID's hash is as
     * often negative as not, and a negative index would put half a warband in
     * no skin at all.
     */
    public static int skinFor(UUID personId) {
        return personId == null ? 0
                : Math.floorMod(personId.hashCode(), SKINS_PER_RACE);
    }

    /** The race this body wears, as the client sees it. Never null. */
    public Race race() {
        int ordinal = entityData.get(DATA_RACE) & 0xFF;
        Race[] races = Race.values();
        return ordinal < races.length ? races[ordinal] : Race.HUMAN;
    }

    /** Which of that race's skins this body wears; see {@link #SKINS_PER_RACE}. */
    public int skin() {
        return entityData.get(DATA_SKIN) & 0xFF;
    }

    /**
     * The egg a creative middle-click on this settler puts in your hand.
     *
     * <p>Vanilla answers this from {@code SpawnEggItem.byId}, which is keyed by
     * <em>entity type</em>. There is one settler type for every race, so two eggs
     * name it and the map hands back whichever one it happens to hold — an orc
     * middle-clicked gave you a human egg as often as not, and the reverse.
     *
     * <p>One entity type is the design and stays the design: the race lives on the
     * body ({@link #DATA_RACE}) rather than in the attribute table, which is what
     * lets one type carry three kinds of person. So the body answers for itself.
     * It is exactly the same question {@link #race()} already answers, and it is
     * answered on the client, where the pick happens and where {@code DATA_RACE}
     * has arrived with the spawn packet.
     *
     * <p>A race with no registered egg — the goblins, today — falls through to
     * vanilla's lookup rather than to an empty hand: whatever that hands back is
     * no worse than the behavior this method replaces, and an empty stack would
     * read as a broken pick.
     */
    @Override
    public ItemStack getPickResult() {
        Item egg = CivilizationItems.eggFor(race());
        return egg == null ? super.getPickResult() : new ItemStack(egg);
    }

    /**
     * The race, on disk as well as on the wire.
     *
     * <p>A body that reaches the save file is a stale duplicate the join hook is
     * about to cull, so this is not really about persistence — it is the channel
     * a spawn egg speaks through. {@code DataComponents.ENTITY_DATA} is merged
     * into an entity's own saved tag and read back through here (see
     * {@code TypedEntityData.loadInto}), so writing {@link #RACE_TAG} onto the
     * egg is what makes an orc egg produce an orc.
     *
     * <p>Stored as the race's word rather than its ordinal because this one is
     * read by a human with an NBT viewer, and {@code /give ... entity_data} is a
     * thing players type.
     */
    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString(RACE_TAG, race().word());
        output.putByte("civ_skin", (byte) skin());
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_RACE,
                (byte) Race.of(input.getStringOr(RACE_TAG, Race.HUMAN.word())).ordinal());
        entityData.set(DATA_SKIN, input.getByteOr("civ_skin", (byte) 0));
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
     * Whether this body is wearing a title.
     *
     * <p>Read off the crown itself rather than off a flag beside it, which is the
     * same discipline {@link #wearCrown} already keeps in the other direction: the
     * helmet <em>is</em> the record, so there is nothing for a second copy to drift
     * from. It also means the client knows — equipment is synced and a boolean on
     * the server is not — which matters because the one thing that reads this is a
     * danger table, and a danger table has to give the same answer everywhere.
     */
    public boolean isCrowned() {
        return getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET);
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
