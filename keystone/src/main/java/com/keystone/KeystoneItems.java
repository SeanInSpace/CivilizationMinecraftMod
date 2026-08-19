package com.keystone;

import com.keystone.item.BlueprintWandItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class KeystoneItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(KeystoneMod.MOD_ID);

    public static final DeferredItem<Item> BLUEPRINT_WAND = ITEMS.registerItem(
            "blueprint_wand",
            BlueprintWandItem::new,
            () -> new Item.Properties().stacksTo(1));

    private KeystoneItems() {
    }

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BLUEPRINT_WAND.get());
        }
    }
}
