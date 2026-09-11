package com.kingdoms.neoforge;

import com.kingdoms.neoforge.item.ExcavationStakeItem;
import com.kingdoms.neoforge.item.FoundingCharterItem;
import com.kingdoms.neoforge.item.OrcWeapons;
import com.kingdoms.neoforge.item.TownMapItem;
import com.kingdoms.neoforge.item.WayfinderItem;
import com.kingdoms.sim.combat.Weaponry;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** Item registration. Everything here lands in the mod's own tab; see {@link KingdomsTabs}. */
public final class KingdomsItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KingdomsMod.MOD_ID);

    public static final DeferredItem<Item> FOUNDING_CHARTER = ITEMS.registerItem(
            "founding_charter",
            FoundingCharterItem::new,
            () -> new Item.Properties().stacksTo(1));

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
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.LUMBER_CAMP, () -> new Item.Properties());


    public static final DeferredItem<net.minecraft.world.item.BlockItem> TOWN_HALL =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.TOWN_HALL, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> HOUSE =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.HOUSE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> GRANARY =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.GRANARY, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FARM =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.FARM, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MARKET =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.MARKET, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STOREHOUSE =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.STOREHOUSE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WORKSHOP =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.WORKSHOP, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WATCHTOWER =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.WATCHTOWER, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MINE =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.MINE, () -> new Item.Properties());

    public static final DeferredItem<net.minecraft.world.item.BlockItem> COTTAGE =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.COTTAGE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MILL =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.MILL, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CARPENTRY =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.CARPENTRY, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> INN =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.INN, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CAMP_POST =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.CAMP_POST, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CACHE =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.CACHE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BUNKHOUSE =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.BUNKHOUSE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> HEARTH =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.HEARTH, () -> new Item.Properties());

    public static final DeferredItem<net.minecraft.world.item.BlockItem> WAREHOUSE =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.WAREHOUSE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SMITH =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.SMITH, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ANIMAL_FARM =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.ANIMAL_FARM, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> QUEST_BOARD =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.QUEST_BOARD, () -> new Item.Properties());

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
     * {@link KingdomsTabs}, which walks the whole registry.
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

    private KingdomsItems() {
    }
}
