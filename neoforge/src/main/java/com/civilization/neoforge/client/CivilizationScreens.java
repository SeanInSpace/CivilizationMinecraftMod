package com.civilization.neoforge.client;

import com.civilization.neoforge.net.MarketPayload;
import com.civilization.neoforge.net.PersonInventoryPayload;
import com.civilization.neoforge.net.QuestBoardPayload;
import com.civilization.neoforge.net.SupplyPayload;
import com.civilization.neoforge.net.TownMapPayload;
import com.civilization.neoforge.net.TownOverviewPayload;
import net.minecraft.client.Minecraft;

/**
 * Client-only entry points for opening the mod's screens.
 *
 * <p>Kept apart from the payloads so the server side never mentions a class that
 * only exists on a client. A payload handler naming {@code Minecraft} directly
 * would load it on a dedicated server the moment the class is verified.
 */
public final class CivilizationScreens {

    private CivilizationScreens() {
    }

    public static void openTownOverview(TownOverviewPayload town) {
        // 26.2 renamed this from setScreen; Keystone's wand screen uses the same call.
        Minecraft.getInstance().setScreenAndShow(new TownOverviewScreen(town));
    }

    /**
     * Opens the town map, or refreshes the one already open.
     *
     * <p>The map asks the server for a fresh reading every second and folds the
     * answer into the screen that asked, so the player's pan, zoom, tab and
     * selection survive it. Replacing the screen once a second would make the
     * map unusable — you could not drag it anywhere without being thrown back to
     * the middle.
     *
     * <p>A reading that is not opening one is only ever folded into a map
     * already on screen, for the same reason the market's board is: closing a
     * screen has to mean it stays closed, and a reply arriving a tick after
     * escape must not put the map back up.
     */
    public static void openTownMap(TownMapPayload town) {
        Minecraft client = Minecraft.getInstance();
        // 26.2 moved the open screen onto the Gui; there is no Minecraft.screen.
        if (client.gui.screen() instanceof TownMapScreen open) {
            open.update(town);
        } else if (town.opening()) {
            client.setScreenAndShow(new TownMapScreen(town));
        }
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

    /**
     * Opens the board, or refreshes the one already open.
     *
     * <p>The same rule the stall follows, for the same two reasons. A board
     * still showing a row as unclaimed after the reward has been collected is
     * worse than no board; and a reply that arrives after the player has hit
     * escape must not put the panel back up, which is what {@code opening} is
     * for.
     */
    public static void openQuestBoard(QuestBoardPayload board) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof QuestBoardScreen open) {
            open.update(board);
        } else if (board.opening()) {
            client.setScreenAndShow(new QuestBoardScreen(board));
        }
    }
}
