package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.PersonInventoryPayload;
import com.kingdoms.neoforge.net.SupplyPayload;
import com.kingdoms.neoforge.net.TownMapPayload;
import com.kingdoms.neoforge.net.TownOverviewPayload;
import net.minecraft.client.Minecraft;

/**
 * Client-only entry points for opening the mod's screens.
 *
 * <p>Kept apart from the payloads so the server side never mentions a class that
 * only exists on a client. A payload handler naming {@code Minecraft} directly
 * would load it on a dedicated server the moment the class is verified.
 */
public final class KingdomsScreens {

    private KingdomsScreens() {
    }

    public static void openTownOverview(TownOverviewPayload town) {
        // 26.2 renamed this from setScreen; Keystone's wand screen uses the same call.
        Minecraft.getInstance().setScreenAndShow(new TownOverviewScreen(town));
    }

    public static void openTownMap(TownMapPayload town) {
        Minecraft.getInstance().setScreenAndShow(new TownMapScreen(town));
    }

    public static void openSupply(SupplyPayload supply) {
        Minecraft.getInstance().setScreenAndShow(new SupplyScreen(supply));
    }

    public static void openPersonInventory(PersonInventoryPayload person) {
        Minecraft.getInstance().setScreenAndShow(new PersonInventoryScreen(person));
    }
}
