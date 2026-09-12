package com.civilization.neoforge.item;

import com.civilization.neoforge.CivilizationEntities;
import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.bridge.NeoForgeWorldBridge;
import com.civilization.neoforge.entity.PersonEntity;
import com.civilization.sim.culture.Race;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.settlement.Newcomer;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A spawn egg that recruits rather than spawns.
 *
 * <p><strong>Why it cannot simply spawn a mob.</strong> There is one settler
 * entity type for every citizen of every town, and a body is a view of a
 * {@code Person} record rather than a thing in its own right:
 * {@code CivilizationMod.onEntityJoin} cancels any settler body the entity
 * manager does not own, and the manager only owns bodies it made for people who
 * exist. A vanilla spawn egg would therefore produce a settler that either
 * vanished on the same tick or stood there forever answering to nothing. So this
 * egg puts a <em>person</em> into the simulation and lets the manager give them a
 * body on its next pass — half a second later, at the spot that was clicked,
 * with the race's health and pace applied by the ordinary embodiment path.
 *
 * <p><strong>And why it is still a {@link SpawnEggItem}.</strong> Everything
 * about a spawn egg except the spawning is worth keeping: the creative-only
 * feel, the peaceful-mode tooltip, and above all
 * {@code DataComponents.ENTITY_DATA}, which is where 26.2 keeps the egg's target
 * type. In 26.2 the type is not a constructor argument at all — the item is a
 * bare {@code SpawnEggItem(Properties)} and {@code SpawnEggItem.getType} reads
 * the component, which {@code Item.Properties.spawnEgg(EntityType)} installs as
 * a {@code TypedEntityData} carrying the type and an empty tag. So both eggs
 * name the one settler type, and the race rides in that same component's tag —
 * which is a channel with a bottom to it: the tag is merged into the entity's
 * own saved data and read back through
 * {@link PersonEntity#readAdditionalSaveData}, so a settler spawned by any other
 * route with the same tag would also come out an orc.
 *
 * <p><strong>An egg is not a charter.</strong> With no town of that race whose
 * claim covers the spot, nothing is founded and nothing is spawned; the player is
 * told so. Founding is the charter's job and costs a charter.
 */
public final class PersonSpawnEggItem extends SpawnEggItem {

    private final Race race;

    public PersonSpawnEggItem(Race race, Properties properties) {
        super(properties);
        this.race = race;
    }

    /** The race this egg recruits. */
    public Race race() {
        return race;
    }

    /**
     * The item properties for one race's egg: a settler egg carrying that race.
     *
     * <p>{@code spawnEgg(type)} first, for the component's type and the entity's
     * required features, and then the same component again with the race written
     * into its tag — a later {@code component} call replaces the earlier one, so
     * this is the supported way to hand a spawn egg custom data.
     *
     * <p>Reading {@code CivilizationEntities.PERSON.get()} here is safe because
     * this supplier runs while the item registry is being filled, and
     * {@code BuiltInRegistries} declares {@code ENTITY_TYPE} before {@code ITEM}
     * — which is the order NeoForge fires the registration events in, so the
     * settler type exists by the time any item is built.
     */
    public static Properties properties(Race race) {
        return new Properties()
                .spawnEgg(CivilizationEntities.PERSON.get())
                .component(DataComponents.ENTITY_DATA, entityData(race));
    }

    /**
     * The exact component one race's egg carries: the settler type, and the race.
     *
     * <p>A named method rather than two lines inside {@link #properties} so that
     * the pair can be checked without an item. In 26.2 an item's components are
     * not stored on the item at all — they are baked into
     * {@code BuiltInRegistries.DATA_COMPONENT_INITIALIZERS} and bound to the
     * registry holder later, which the mod's JUnit harness never does. So this is
     * the last point at which the type and the tag are both in one object that a
     * test can hold.
     */
    public static TypedEntityData<EntityType<?>> entityData(Race race) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PersonEntity.RACE_TAG, race.word());
        return TypedEntityData.of(CivilizationEntities.PERSON.get(), tag);
    }

    /**
     * The race an egg is carrying, read back out of its own component.
     *
     * <p>Goes through the component rather than the field so that an egg a player
     * built with {@code /give ... [entity_data={civ_race:"orc"}]} behaves as the
     * tag says — the tag is the truth on the wire, and the field is only the
     * default the registered item was born with.
     */
    public static Race raceOf(ItemStack stack) {
        return raceOf(stack.get(DataComponents.ENTITY_DATA));
    }

    /**
     * The same read, off the component itself.
     *
     * <p>Split out because neither an {@code ItemStack} nor a bound
     * {@code Item.components()} exists in this mod's JUnit environment, and the
     * tag is the half worth testing. See {@link #entityData}, which is the object
     * the test hands this.
     */
    public static Race raceOf(TypedEntityData<?> data) {
        if (data == null) {
            return Race.HUMAN;
        }
        CompoundTag tag = data.copyTagWithoutId();
        return Race.of(tag.getStringOr(PersonEntity.RACE_TAG, Race.HUMAN.word()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }
        // One block up, the way the charter does it: the clicked position is the
        // block that was hit and a settler standing inside it would be inside a
        // wall.
        return recruit(level, player, context.getItemInHand(),
                context.getClickedPos().above());
    }

    /**
     * Right-clicking air does nothing, and says nothing.
     *
     * <p>Vanilla's {@code use} exists only to let an egg be thrown into a fluid;
     * there is nowhere to stand in a fluid, so there is nothing here to do.
     * {@code PASS} rather than {@code FAIL} so the hand does not swing at
     * nothing.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    private InteractionResult recruit(ServerLevel level, Player player, ItemStack egg, BlockPos where) {
        SimWorld world = CivilizationMod.simulationFor(level);
        if (world == null) {
            return InteractionResult.FAIL;
        }
        Race wanted = raceOf(egg);
        SimPos spot = NeoForgeWorldBridge.toSimPos(where);
        Settlement town = Newcomer.townFor(world, wanted, spot);
        if (town == null) {
            player.sendSystemMessage(Component.literal(
                    "No " + wanted.word() + " settlement here to join."));
            return InteractionResult.FAIL;
        }

        Person arrival = Newcomer.arrive(town, spot, world.stepsElapsed());
        if (!player.isCreative()) {
            egg.shrink(1);
        }
        player.sendSystemMessage(Component.literal(
                arrival.name() + " joins " + town.name() + "."));
        return InteractionResult.SUCCESS;
    }
}
