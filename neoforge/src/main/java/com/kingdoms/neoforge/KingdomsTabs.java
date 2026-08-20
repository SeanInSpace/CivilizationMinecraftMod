package com.kingdoms.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own creative tab, so everything Kingdoms adds sits in one place
 * instead of being scattered through the vanilla tabs.
 */
public final class KingdomsTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KingdomsMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.kingdoms"))
                    .icon(() -> new ItemStack(KingdomsItems.FOUNDING_CHARTER.get()))
                    // No withTabsBefore/withTabsAfter here on purpose. NeoForge already
                    // chains any tab that declares no ordering to sit after the last
                    // vanilla tab, which is exactly where these belong. Asking for it
                    // explicitly contradicts that chain and deadlocks the toposort:
                    // withTabsAfter(X) means "X comes after me", so naming a late vanilla
                    // tab creates a cycle rather than moving this one to the end.
                    .displayItems((parameters, output) -> {
                        // Driven off the registry rather than a hand-written list, so
                        // anything registered later shows up here without this class
                        // needing to be touched.
                        for (DeferredHolder<Item, ? extends Item> entry : KingdomsItems.ITEMS.getEntries()) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    private KingdomsTabs() {
    }
}
