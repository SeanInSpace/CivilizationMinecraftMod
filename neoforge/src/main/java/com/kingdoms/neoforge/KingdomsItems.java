package com.kingdoms.neoforge;

import com.kingdoms.neoforge.item.ExcavationStakeItem;
import com.kingdoms.neoforge.item.FoundingCharterItem;
import com.kingdoms.neoforge.item.TownMapItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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
     * needs no use behaviour at all. The drawing is done server-side from the
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

    public static final DeferredItem<net.minecraft.world.item.BlockItem> WAREHOUSE =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.WAREHOUSE, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SMITH =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.SMITH, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ANIMAL_FARM =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.ANIMAL_FARM, () -> new Item.Properties());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> QUEST_BOARD =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.QUEST_BOARD, () -> new Item.Properties());

    private KingdomsItems() {
    }
}
