package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.TownOverviewPayload;
import com.kingdoms.neoforge.net.TownOverviewPayload.Distress;
import com.kingdoms.sim.settlement.Tallies;
import com.kingdoms.sim.settlement.TownStores;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.kingdoms.neoforge.client.KingdomsPanel.AMOUNT;
import static com.kingdoms.neoforge.client.KingdomsPanel.HEADER;
import static com.kingdoms.neoforge.client.KingdomsPanel.LABEL;
import static com.kingdoms.neoforge.client.KingdomsPanel.PADDING;
import static com.kingdoms.neoforge.client.KingdomsPanel.ROW;
import static com.kingdoms.neoforge.client.KingdomsPanel.SUBTLE;
import java.util.Locale;

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
 * <p>A town in trouble gets a band under its own name, above the ledger, before
 * the player has read a single number. The ledger alone never said what the
 * numbers cost — a town starved to death with this screen available and nothing
 * on it warning that it was happening.
 *
 * <p>Note the 26.2 shape: screens no longer draw immediately. They <em>extract</em>
 * render state, and the framework calls {@code extractBackground} before
 * {@code extractRenderState} on its own, so neither is invoked from here.
 */
public final class TownOverviewScreen extends Screen {

    /** Narrower than the warehouse bill's 260: a ledger row is a name and a number. */
    private static final int PANEL_WIDTH = 240;

    private static final int FOOTER_HEIGHT = 14;

    /**
     * The distress band, which hangs directly off the header so the space it
     * reserves and the space it paints are measured from one anchor.
     */
    private static final int BAND_LINE = 10;
    private static final int BAND_PAD = 4;
    private static final int BAND_GAP = 6;

    /** Warmer as it worsens; the amber matches the short-of-stock rows elsewhere. */
    private static final int ALARM_TEXT_HUNGRY = 0xFFFFE070;
    private static final int ALARM_TEXT_FAILING = 0xFFFFAA55;
    private static final int ALARM_TEXT_DYING = 0xFFFF6055;
    private static final int ALARM_BAND_HUNGRY = 0x30FFD040;
    private static final int ALARM_BAND_FAILING = 0x40FF8020;
    private static final int ALARM_BAND_DYING = 0x55FF3020;

    private final TownOverviewPayload town;

    public TownOverviewScreen(TownOverviewPayload town) {
        super(Component.literal(town.town()));
        this.town = town;
    }

    /** Two lines for a hungry town, three when there is also something to do. */
    private int bandLines() {
        Distress distress = town.distress();
        if (distress.silent()) {
            return 0;
        }
        return distress.showsRemedy() ? 3 : 2;
    }

    /** Zero when the town is fine, so a steady panel is exactly the size it was. */
    private int distressHeight() {
        int lines = bandLines();
        return lines == 0 ? 0 : BAND_PAD * 2 + lines * BAND_LINE + BAND_GAP;
    }

    private int panelHeight() {
        int rows = Math.max(1, town.lines().size());
        return HEADER + distressHeight() + rows * ROW + FOOTER_HEIGHT;
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

        KingdomsPanel.frame(graphics, x, y, PANEL_WIDTH, h);
        KingdomsPanel.header(graphics, font, x, y, PANEL_WIDTH, title,
                Component.literal(town.population() + " residents"), SUBTLE);

        // The band goes in before the empty-ledger exit below, because an empty
        // ledger is precisely what a dying town has and that is exactly when it
        // needs saying. Its height then pushes every row beneath it down.
        int band = distressHeight();
        drawDistress(graphics, x, y);

        List<TownOverviewPayload.Line> lines = town.lines();
        if (lines.isEmpty()) {
            graphics.centeredText(font, Component.literal("The stores are empty."),
                    x + PANEL_WIDTH / 2, y + HEADER + band + 4, SUBTLE);
            super.extractRenderState(graphics, mouseX, mouseY, a);
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            TownOverviewPayload.Line line = lines.get(i);
            int rowY = y + HEADER + band + i * ROW;

            // Banded rows: at a dozen resources an unbroken list is hard to read
            // across, and a stripe costs nothing.
            if (i % 2 == 1) {
                KingdomsPanel.stripe(graphics, x, rowY, PANEL_WIDTH);
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
     * The alarm, tinted across the full width so it reads as a state of the town
     * rather than one more row of the ledger.
     */
    private void drawDistress(GuiGraphicsExtractor graphics, int x, int y) {
        int lines = bandLines();
        if (lines == 0) {
            return;
        }
        Distress distress = town.distress();
        int alarm = distress.alarm();
        int bandTop = y + HEADER;
        int bandBottom = bandTop + BAND_PAD * 2 + lines * BAND_LINE;

        graphics.fill(x + 1, bandTop, x + PANEL_WIDTH - 1, bandBottom, alarmBand(alarm));

        int textY = bandTop + BAND_PAD;
        graphics.centeredText(font,
                Component.literal("THIS TOWN IS " + distress.word().toUpperCase(Locale.ROOT)),
                x + PANEL_WIDTH / 2, textY, alarmText(alarm));
        graphics.centeredText(font, Component.literal(distress.fact()),
                x + PANEL_WIDTH / 2, textY + BAND_LINE, LABEL);
        if (distress.showsRemedy()) {
            graphics.centeredText(font, Component.literal(distress.remedy()),
                    x + PANEL_WIDTH / 2, textY + BAND_LINE * 2, SUBTLE);
        }
    }

    private static int alarmText(int alarm) {
        return switch (alarm) {
            case Distress.ALARM_DYING -> ALARM_TEXT_DYING;
            case Distress.ALARM_FAILING -> ALARM_TEXT_FAILING;
            default -> ALARM_TEXT_HUNGRY;
        };
    }

    private static int alarmBand(int alarm) {
        return switch (alarm) {
            case Distress.ALARM_DYING -> ALARM_BAND_DYING;
            case Distress.ALARM_FAILING -> ALARM_BAND_FAILING;
            default -> ALARM_BAND_HUNGRY;
        };
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
