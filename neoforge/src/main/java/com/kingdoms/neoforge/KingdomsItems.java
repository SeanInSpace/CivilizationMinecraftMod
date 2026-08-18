package com.kingdoms.neoforge;

import com.kingdoms.neoforge.item.FoundingCharterItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registration. One item so far — the legitimate way to start a settlement. */
public final class KingdomsItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KingdomsMod.MOD_ID);

    public static final DeferredItem<Item> FOUNDING_CHARTER = ITEMS.registerItem(
            "founding_charter",
            FoundingCharterItem::new,
            () -> new Item.Properties().stacksTo(1));

    /** So the camp post can be placed by hand, and moved. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LUMBER_CAMP =
            ITEMS.registerSimpleBlockItem(KingdomsBlocks.LUMBER_CAMP, () -> new Item.Properties());

    private KingdomsItems() {
    }

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FOUNDING_CHARTER.get());
            event.accept(LUMBER_CAMP.get());
        }
    }
}
