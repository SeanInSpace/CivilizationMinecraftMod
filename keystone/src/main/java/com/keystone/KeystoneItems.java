package com.keystone;

import com.keystone.item.BlueprintWandItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registration. Everything here lands in the mod's own tab; see {@link KeystoneTabs}. */
public final class KeystoneItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(KeystoneMod.MOD_ID);

    public static final DeferredItem<Item> BLUEPRINT_WAND = ITEMS.registerItem(
            "blueprint_wand",
            BlueprintWandItem::new,
            () -> new Item.Properties().stacksTo(1));

    private KeystoneItems() {
    }
}
