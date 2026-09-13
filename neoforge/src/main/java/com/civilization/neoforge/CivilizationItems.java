package com.civilization.neoforge;

import com.civilization.neoforge.item.ExcavationStakeItem;
import com.civilization.neoforge.item.FoundingCharterItem;
import com.civilization.neoforge.item.OrcWeapons;
import com.civilization.neoforge.item.PersonSpawnEggItem;
import com.civilization.neoforge.item.TownMapItem;
import com.civilization.neoforge.item.WayfinderItem;
import com.civilization.neoforge.trade.Currency;
import com.civilization.sim.combat.Weaponry;
import com.civilization.sim.culture.Race;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** Item registration. Everything here lands in the mod's own tab; see {@link CivilizationTabs}. */
public final class CivilizationItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CivilizationMod.MOD_ID);

    public static final DeferredItem<Item> FOUNDING_CHARTER = ITEMS.registerItem(
            "founding_charter",
            FoundingCharterItem::new,
            () -> new Item.Properties().stacksTo(1));

    /**
     * The money, made holdable.
     *
     * <p>Registered under {@link Currency#ID} rather than a literal, because the
     * name is not settled and {@code Currency} is the one place in Java it is
     * allowed to be written down. Everything else — messages, the stall's
     * footer, what the storehouse takes — goes through that class.
     *
     * <p>A plain item with no behavior and no recipe. It is not craftable on
     * purpose: the only thing that issues money is a town paying out of its
     * treasury, and the only thing that takes it is a town being paid, so a
     * crafting grid that could make one would print money that no ledger ever
     * debited. Stacks to 64 like any other small thing, which is also what
     * makes a reward of a hundred and twenty arrive as two stacks.
     */
    public static final DeferredItem<Item> COIN = ITEMS.registerItem(
            Currency.ID,
            Item::new,
            () -> new Item.Properties().stacksTo(64));

    /**
     * Hold it and every building's bounds light up.
     *
     * <p>Purely a lens — it places nothing and changes nothing, which is why it
     * needs no use behavior at all. The drawing is done server-side from the
     * footprints the settlement already records.
     */
    public static final DeferredItem<Item> SURVEYORS_LAMP = ITEMS.registerItem(
            "surveyors_lamp",
            Item::new,
            () -> new Item.Properties().stacksTo(1));

    /**
     * Marks a box and sets a town clearing it. The excavation, on demand.
     */
    public static final DeferredItem<Item> EXCAVATION_STAKE = ITEMS.registerItem(
            "excavation_stake",
            ExcavationStakeItem::new,
            () -> new Item.Properties().stacksTo(1));

    /** A plan of the nearest town: blank ground, buildings in green. */
    public static final DeferredItem<Item> TOWN_MAP = ITEMS.registerItem(
            "town_map",
            TownMapItem::new,
            () -> new Item.Properties().stacksTo(1));

    /**
     * A compass whose needle finds towns rather than spawn.
     *
     * <p>Single stack, because it carries a target and a stack of two would have
     * to carry one between them. Vanilla's compass has the same reason.
     */
    public static final DeferredItem<Item> WAYFINDER = ITEMS.registerItem(
            "wayfinder",
            WayfinderItem::new,
            () -> new Item.Properties().stacksTo(1));

    /** So the camp post can be placed by hand, and moved. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LUMBER_CAMP =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.LUMBER_CAMP, () -> new Item.Properties());


    public static final DeferredItem<net.minecraft.world.item.BlockItem> TOWN_HALL =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.TOWN_HALL, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> HOUSE =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.HOUSE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> GRANARY =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.GRANARY, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FARM =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.FARM, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MARKET =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.MARKET, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STOREHOUSE =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.STOREHOUSE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WORKSHOP =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.WORKSHOP, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WATCHTOWER =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.WATCHTOWER, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MINE =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.MINE, () -> new Item.Properties());

    public static final DeferredItem<net.minecraft.world.item.BlockItem> COTTAGE =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.COTTAGE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MILL =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.MILL, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CARPENTRY =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.CARPENTRY, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> INN =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.INN, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CAMP_POST =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.CAMP_POST, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CACHE =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.CACHE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BUNKHOUSE =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.BUNKHOUSE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> HEARTH =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.HEARTH, () -> new Item.Properties());

    public static final DeferredItem<net.minecraft.world.item.BlockItem> WAREHOUSE =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.WAREHOUSE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SMITH =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.SMITH, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ANIMAL_FARM =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.ANIMAL_FARM, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> QUEST_BOARD =
            ITEMS.registerSimpleBlockItem(CivilizationBlocks.QUEST_BOARD, () -> new Item.Properties());

    /**
     * The orcs' armory: five weapons, each forged twice.
     *
     * <p>Registered from the table in {@code :common} rather than written out
     * ten times, so a sixth weapon is one line there and nothing here. The map
     * is keyed by the registered name — {@code orc_falchion},
     * {@code orc_falchion_forged} — because that is the key the simulation
     * side speaks in and it cannot see an {@code Item} to speak in any other.
     *
     * <p>Ordered, so the creative tab lists them in the table's own order rather
     * than in whatever order a hash bucket happened to fall out in. See
     * {@link CivilizationTabs}, which walks the whole registry.
     */
    private static final Map<String, DeferredItem<Item>> ORC_WEAPONS = registerOrcWeapons();

    private static Map<String, DeferredItem<Item>> registerOrcWeapons() {
        Map<String, DeferredItem<Item>> armory = new LinkedHashMap<>();
        for (Weaponry weapon : Weaponry.values()) {
            for (boolean forged : new boolean[] {false, true}) {
                String name = weapon.nameAt(forged);
                armory.put(name, ITEMS.registerItem(
                        name,
                        properties -> OrcWeapons.make(weapon, forged, properties),
                        // Single stack: a weapon with a durability bar cannot
                        // stack anyway, and saying so here is cheaper than
                        // finding out from the game.
                        () -> new Item.Properties().stacksTo(1)));
            }
        }
        return java.util.Collections.unmodifiableMap(armory);
    }

    /**
     * The two settler eggs: a human and an orc.
     *
     * <p>Neither one spawns anything by itself — see {@link PersonSpawnEggItem},
     * which recruits into a town of that race instead, because a settler body
     * with no person behind it is culled the instant it joins the world. Both
     * name the one settler entity type; the race is what differs, and it travels
     * in the egg's own entity-data component.
     *
     * <p>A goblin egg is missing on purpose: the goblins have exactly one culture
     * and no town in an ordinary world is of it, so the egg's only possible
     * answer today would be "no goblin settlement here to join".
     *
     * <p>One wrinkle in sharing a type, now answered: vanilla's
     * {@code SpawnEggItem.byId} looks an egg up <em>by</em> entity type and takes
     * any match, so creative middle-click on a settler would hand you whichever
     * of these two the map happened to hold — regardless of what he is. The
     * answer is not an entity type per race, which would put the race back in the
     * attribute table the whole design takes it out of: the body already knows
     * its own race, so {@link com.civilization.neoforge.entity.PersonEntity#getPickResult}
     * overrides the lookup and hands back {@link #eggFor} of that race.
     */
    public static final DeferredItem<Item> HUMAN_SPAWN_EGG = ITEMS.registerItem(
            "human_spawn_egg",
            properties -> new PersonSpawnEggItem(Race.HUMAN, properties),
            () -> PersonSpawnEggItem.properties(Race.HUMAN));

    public static final DeferredItem<Item> ORC_SPAWN_EGG = ITEMS.registerItem(
            "orc_spawn_egg",
            properties -> new PersonSpawnEggItem(Race.ORC, properties),
            () -> PersonSpawnEggItem.properties(Race.ORC));

    /**
     * The egg that recruits one race, or null if that race has no egg.
     *
     * <p>The goblins have none — see above — and a race added tomorrow will not
     * have one until somebody registers it, so this is nullable rather than
     * falling back to the human egg: handing a player the wrong egg is the fault
     * being fixed here, and handing them none is honest.
     */
    public static Item eggFor(Race race) {
        return switch (race) {
            case HUMAN -> HUMAN_SPAWN_EGG.get();
            case ORC -> ORC_SPAWN_EGG.get();
            default -> null;
        };
    }

    /** The registered item of that name, or null if the armory has no such thing. */
    public static Item orcWeapon(String name) {
        DeferredItem<Item> entry = ORC_WEAPONS.get(name);
        return entry == null ? null : entry.get();
    }

    /** The registered item for one weapon at one tier. */
    public static Item orcWeapon(Weaponry weapon, boolean forged) {
        return orcWeapon(weapon.nameAt(forged));
    }

    /** Every name the armory registered, for anything that wants to check them all. */
    public static java.util.Set<String> orcWeaponNames() {
        return ORC_WEAPONS.keySet();
    }

    private CivilizationItems() {
    }
}
