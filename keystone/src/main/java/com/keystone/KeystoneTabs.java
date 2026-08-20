package com.keystone;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Keystone's own creative tab. Deliberately makes no reference to Kingdoms —
 * this mod stands alone and is placed relative to the vanilla tabs only.
 */
public final class KeystoneTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KeystoneMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.keystone"))
                    .icon(() -> new ItemStack(KeystoneItems.BLUEPRINT_WAND.get()))
                    .withTabsAfter(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((parameters, output) -> {
                        // Driven off the registry rather than a hand-written list, so
                        // anything registered later shows up here without this class
                        // needing to be touched.
                        for (DeferredHolder<Item, ? extends Item> entry : KeystoneItems.ITEMS.getEntries()) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    private KeystoneTabs() {
    }
}
