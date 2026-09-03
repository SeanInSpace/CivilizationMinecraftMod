package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.SupplyPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The bill for whatever the town is building: what is left to lay, and whether
 * the town can pay for it.
 *
 * <p>A line is amber when the town is short of the stock that block is made from
 * — that is the line a player can do something about by handing materials over
 * at the warehouse.
 */
public final class SupplyScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int HEADER = 44;
    private static final int ROW = 20;
    private static final int FOOTER = 22;
    private static final int PADDING = 14;

    private static final int PANEL = 0xF0201010;
    private static final int BORDER = 0xFF6A6A6A;
    private static final int RULE = 0xFF4A4A4A;
    private static final int STRIPE = 0x18FFFFFF;
    private static final int TITLE = 0xFFFFE0A0;
    private static final int LABEL = 0xFFC8C8C8;
    private static final int SHORT = 0xFFFFAA55;
    private static final int SUBTLE = 0xFF9A9A9A;

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

        graphics.fill(x, y, x + PANEL_WIDTH, y + h, PANEL);
        graphics.outline(x, y, PANEL_WIDTH, h, BORDER);

        graphics.centeredText(font, title, x + PANEL_WIDTH / 2, y + 12, TITLE);
        graphics.centeredText(font, Component.literal(supply.percent() + "% raised"),
                x + PANEL_WIDTH / 2, y + 26, SUBTLE);
        graphics.fill(x + PADDING, y + 38, x + PANEL_WIDTH - PADDING, y + 39, RULE);

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
                graphics.fill(x + PADDING - 4, rowY - 2,
                        x + PANEL_WIDTH - PADDING + 4, rowY + ROW - 4, STRIPE);
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
