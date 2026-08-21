package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.TownOverviewPayload;
import com.kingdoms.sim.settlement.Tallies;
import com.kingdoms.sim.settlement.TownStores;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The town at a glance: what it is called, how many live there, and everything
 * it owns.
 *
 * <p>Drawn rather than textured, like Keystone's screens — a panel, a header and
 * a list of rows need no art, and shipping none keeps the mod's assets to what
 * actually has to exist.
 *
 * <p>The ledger is whatever the server sent, in the server's order. Nothing here
 * knows the list of resources in advance, so a store this build has never heard
 * of still gets a row; it simply falls back to a generic icon.
 *
 * <p>Note the 26.2 shape: screens no longer draw immediately. They <em>extract</em>
 * render state, and the framework calls {@code extractBackground} before
 * {@code extractRenderState} on its own, so neither is invoked from here.
 */
public final class TownOverviewScreen extends Screen {

    private static final int PANEL_WIDTH = 240;
    private static final int HEADER_HEIGHT = 46;
    private static final int ROW_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 14;
    private static final int PADDING = 14;

    private static final int PANEL = 0xF0201010;
    private static final int BORDER = 0xFF6A6A6A;
    private static final int RULE = 0xFF4A4A4A;
    private static final int STRIPE = 0x18FFFFFF;
    private static final int TITLE = 0xFFFFE0A0;
    private static final int LABEL = 0xFFC8C8C8;
    private static final int SUBTLE = 0xFF9A9A9A;
    private static final int AMOUNT = 0xFFFFFFFF;

    private final TownOverviewPayload town;

    public TownOverviewScreen(TownOverviewPayload town) {
        super(Component.literal(town.town()));
        this.town = town;
    }

    private int panelHeight() {
        int rows = Math.max(1, town.lines().size());
        return HEADER_HEIGHT + rows * ROW_HEIGHT + FOOTER_HEIGHT;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = left();
        int y = top();
        int h = panelHeight();

        graphics.fill(x, y, x + PANEL_WIDTH, y + h, PANEL);
        graphics.outline(x, y, PANEL_WIDTH, h, BORDER);

        graphics.centeredText(font, title, x + PANEL_WIDTH / 2, y + 12, TITLE);
        graphics.centeredText(font,
                Component.literal(town.population() + " residents"),
                x + PANEL_WIDTH / 2, y + 24, SUBTLE);

        graphics.fill(x + PADDING, y + 38, x + PANEL_WIDTH - PADDING, y + 39, RULE);

        List<TownOverviewPayload.Line> lines = town.lines();
        if (lines.isEmpty()) {
            graphics.centeredText(font, Component.literal("The stores are empty."),
                    x + PANEL_WIDTH / 2, y + HEADER_HEIGHT + 4, SUBTLE);
            super.extractRenderState(graphics, mouseX, mouseY, a);
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            TownOverviewPayload.Line line = lines.get(i);
            int rowY = y + HEADER_HEIGHT + i * ROW_HEIGHT;

            // Banded rows: at a dozen resources an unbroken list is hard to read
            // across, and a stripe costs nothing.
            if (i % 2 == 1) {
                graphics.fill(x + PADDING - 4, rowY - 2,
                        x + PANEL_WIDTH - PADDING + 4, rowY + ROW_HEIGHT - 4, STRIPE);
            }

            graphics.item(new ItemStack(iconFor(line.resource())), x + PADDING, rowY);

            graphics.text(font, Component.literal(Tallies.pretty(line.resource())),
                    x + PADDING + 22, rowY + 4, LABEL, false);

            String amount = Integer.toString(line.amount());
            int amountWidth = font.width(amount);
            graphics.text(font, Component.literal(amount),
                    x + PANEL_WIDTH - PADDING - amountWidth, rowY + 4, AMOUNT, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /**
     * Something recognisable to stand for each store.
     *
     * <p>Falls through to a chest for anything unknown, so a resource added by a
     * datapack or another mod still gets a row rather than crashing the screen.
     */
    private static Item iconFor(String resource) {
        return switch (resource) {
            case TownStores.FOOD -> Items.BREAD;
            case TownStores.WOOD -> Items.OAK_PLANKS;
            case TownStores.STONE -> Items.COBBLESTONE;
            case TownStores.SAPLINGS -> Items.OAK_SAPLING;
            case TownStores.IRON -> Items.IRON_INGOT;
            case TownStores.TOOLS -> Items.IRON_PICKAXE;
            case TownStores.WEAPONS -> Items.IRON_SWORD;
            case TownStores.ARMOUR -> Items.IRON_CHESTPLATE;
            default -> Items.CHEST;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
