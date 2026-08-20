package com.kingdoms.neoforge;

import com.kingdoms.neoforge.item.FoundingCharterItem;
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
