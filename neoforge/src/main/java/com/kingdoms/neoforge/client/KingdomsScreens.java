package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.MarketPayload;
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

    /**
     * Opens the stall, or refreshes the one already open.
     *
     * <p>The only screen here sent more than once. Every trade is answered with
     * a fresh board — prices move, and a stall still showing the price that was
     * true before the town stopped starving is the one thing worse than no
     * stall — and replacing the screen for each one would throw away the
     * player's place eight units at a time.
     *
     * <p>A board that is not opening one is only ever folded into a stall
     * already on screen. Closing a screen has to mean it stays closed: press a
     * button, hit escape, and the reply arriving a tick later must not put the
     * stall back up.
     */
    public static void openMarket(MarketPayload market) {
        Minecraft client = Minecraft.getInstance();
        // 26.2 moved the open screen onto the Gui; there is no Minecraft.screen.
        if (client.gui.screen() instanceof MarketScreen open) {
            open.update(market);
        } else if (market.opening()) {
            client.setScreenAndShow(new MarketScreen(market));
        }
    }
}
