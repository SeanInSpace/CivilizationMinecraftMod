package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.SupplyPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.kingdoms.neoforge.client.KingdomsPanel.HEADER;
import static com.kingdoms.neoforge.client.KingdomsPanel.LABEL;
import static com.kingdoms.neoforge.client.KingdomsPanel.PADDING;
import static com.kingdoms.neoforge.client.KingdomsPanel.ROW;
import static com.kingdoms.neoforge.client.KingdomsPanel.SUBTLE;

/**
 * The bill for whatever the town is building: what is left to lay, and whether
 * the town can pay for it.
 *
 * <p>A line is amber when the town is short of the stock that block is made from
 * — that is the line a player can do something about by handing materials over
 * at the warehouse.
 */
public final class SupplyScreen extends Screen {

    /**
     * Wider than the town overview's 240, and deliberately so: a bill line
     * carries an item name and "12  (town has 3)" beside it, which is more than
     * a ledger row and did not fit.
     */
    private static final int PANEL_WIDTH = 260;

    private static final int FOOTER = 22;

    /** Short of the stock this block is made from: the one color beyond the shared palette. */
    private static final int SHORT = 0xFFFFAA55;

    /** Longest bill drawn. A hall's plan is hundreds of lines; nobody reads those. */
    private static final int MAX_ROWS = 10;

    private final SupplyPayload supply;

    public SupplyScreen(SupplyPayload supply) {
        super(Component.literal("Building: " + supply.building()));
        this.supply = supply;
    }

    private List<SupplyPayload.Need> shown() {
        List<SupplyPayload.Need> needs = supply.needs();
        return needs.size() <= MAX_ROWS ? needs : needs.subList(0, MAX_ROWS);
    }

    private int panelHeight() {
        return HEADER + Math.max(1, shown().size()) * ROW + FOOTER;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = (width - PANEL_WIDTH) / 2;
        int h = panelHeight();
        int y = (height - h) / 2;

        KingdomsPanel.frame(graphics, x, y, PANEL_WIDTH, h);
        KingdomsPanel.header(graphics, font, x, y, PANEL_WIDTH, title,
                Component.literal(supply.percent() + "% raised"), SUBTLE);

        List<SupplyPayload.Need> needs = shown();
        if (needs.isEmpty()) {
            graphics.centeredText(font, Component.literal("Nothing is being built."),
                    x + PANEL_WIDTH / 2, y + HEADER + 4, SUBTLE);
            super.extractRenderState(graphics, mouseX, mouseY, a);
            return;
        }

        for (int i = 0; i < needs.size(); i++) {
            SupplyPayload.Need need = needs.get(i);
            int rowY = y + HEADER + i * ROW;
            if (i % 2 == 1) {
                KingdomsPanel.stripe(graphics, x, rowY, PANEL_WIDTH);
            }

            Item item = ItemIcons.of(need.item());
            graphics.item(new ItemStack(item), x + PADDING, rowY);
            graphics.text(font, Component.translatable(item.getDescriptionId()),
                    x + PADDING + 22, rowY + 4, LABEL, false);

            boolean short_ = need.held() < need.needed();
            String count = need.needed() + "  (town has " + need.held() + ")";
            int wide = font.width(count);
            graphics.text(font, Component.literal(count),
                    x + PANEL_WIDTH - PADDING - wide, rowY + 4,
                    short_ ? SHORT : LABEL, false);
        }

        int more = supply.needs().size() - needs.size();
        graphics.centeredText(font, Component.literal(more > 0
                        ? more + " more kinds not shown   ·   use the post holding a stack to give"
                        : "Use the post holding a stack to give"),
                x + PANEL_WIDTH / 2, y + h - 14, SUBTLE);

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
