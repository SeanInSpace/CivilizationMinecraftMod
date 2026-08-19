package com.keystone.client;

import com.keystone.KeystoneMod;
import com.keystone.item.BlueprintWandItem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Everything that only exists where there is a screen.
 *
 * <p>Dist-guarded, so a dedicated server never loads any of it.
 */
@Mod(value = KeystoneMod.MOD_ID, dist = Dist.CLIENT)
public final class KeystoneClient {

    public KeystoneClient(IEventBus modBus, ModContainer container) {
        // Hand the wand a way to open its naming screen. Common code holds only
        // this function, never the screen class itself.
        BlueprintWandItem.saveScreen = (a, b) ->
                Minecraft.getInstance().setScreenAndShow(new SaveBlueprintScreen(a, b));
    }
}
