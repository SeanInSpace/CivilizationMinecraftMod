package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.MarketDealPayload;
import com.kingdoms.neoforge.net.MarketPayload;
import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.TownStores;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

import static com.kingdoms.neoforge.client.KingdomsPanel.AMOUNT;
import static com.kingdoms.neoforge.client.KingdomsPanel.HEADER;
import static com.kingdoms.neoforge.client.KingdomsPanel.LABEL;
import static com.kingdoms.neoforge.client.KingdomsPanel.PADDING;
import static com.kingdoms.neoforge.client.KingdomsPanel.ROW;
import static com.kingdoms.neoforge.client.KingdomsPanel.SUBTLE;

/**
 * The stall, with the reason for every price written beside it.
 *
 * <p>That is the whole of why this exists rather than the villager trading
 * screen it replaced. A merchant screen shows that grain costs six and has
 * nowhere to put <em>they are starving</em> — and a price that moves with a
 * town's trouble is only a game if the trouble is legible. Every row here is
 * one line: what you get, why they are asking what they are asking, and a
 * button that names the price in emeralds.
 *
 * <p>The board is a snapshot from the server and this screen never guesses at
 * one. Pressing a button sends a request and the server sends a fresh board
 * back, which {@link #update} folds in without closing anything — reopening the
 * screen for every trade would throw the player's place away eight units at a
 * time.
 *
 * <p>What the client <em>does</em> decide for itself is what is in the player's
 * own pockets, which is the one fact it holds better than the server does. It
 * is only used to grey a button out; every refusal that matters is made again
 * on the server.
 */
public final class MarketScreen extends Screen {

    /**
     * Wide enough for a row that carries an icon, a quantity, a sentence and a
     * button. The supply screen's 260 fits everything but the sentence, and the
     * sentence is the point.
     */
    private static final int PANEL_WIDTH = 320;

    private static final int FOOTER = 24;

    private static final int BUTTON_WIDTH = 66;
    private static final int BUTTON_HEIGHT = 16;

    /** Where the reason starts, measured from the panel's left edge. */
    private static final int REASON_X = PADDING + 92;

    /** A town in real trouble. The one colour beyond the shared palette. */
    private static final int URGENT = 0xFFFF7755;

    /** Short of something, but not desperate about it. */
    private static final int WANTING = 0xFFFFAA55;

    /** More than it can store, and glad to see the back of it. */
    private static final int SPARE = 0xFF88CC88;

    private MarketPayload market;

    /** One button per row, in the board's order, so {@link #tick} can find them. */
    private final List<Button> rows = new ArrayList<>();

    public MarketScreen(MarketPayload market) {
        super(Component.literal(market.town() + " market"));
        this.market = market;
    }

    /**
     * Takes a fresh board after a trade.
     *
     * <p>Called from {@link KingdomsScreens} rather than opening a second
     * screen, so the panel stays put and only its numbers move.
     */
    public void update(MarketPayload fresh) {
        this.market = fresh;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private List<MarketPayload.Offer> offers() {
        return market.offers();
    }

    private int panelHeight() {
        return HEADER + Math.max(1, offers().size()) * ROW + FOOTER;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    @Override
    protected void init() {
        rows.clear();
        int x = left();
        int y = top();
        List<MarketPayload.Offer> offers = offers();
        for (int i = 0; i < offers.size(); i++) {
            MarketPayload.Offer offer = offers.get(i);
            int rowY = y + HEADER + i * ROW;
            Button button = Button.builder(buttonLabel(offer), pressed -> take(offer))
                    .bounds(x + PANEL_WIDTH - PADDING - BUTTON_WIDTH,
                            rowY + (ROW - BUTTON_HEIGHT) / 2 - 1,
                            BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            button.active = canAfford(offer);
            rows.add(button);
            addRenderableWidget(button);
        }
    }

    /**
     * What the player is carrying changes while the stall is open.
     *
     * <p>This is not a pause screen, so a hopper can fill a pocket behind it —
     * but the case that matters is the plainest one: a player who opens the
     * stall with no emeralds would otherwise see every Buy button greyed out for
     * as long as they stood there, because the only thing that rebuilt them was
     * a successful trade and no trade was possible.
     */
    @Override
    public void tick() {
        List<MarketPayload.Offer> offers = offers();
        for (int i = 0; i < rows.size() && i < offers.size(); i++) {
            rows.get(i).active = canAfford(offers.get(i));
        }
    }

    /**
     * The verb is the player's, not the town's.
     *
     * <p>{@code townBuys} is the right way round in the ledger and the wrong way
     * round on a button: a row the town is buying is a row the player sells.
     * Turning it round exactly once, here, is what stops the two readings ever
     * meeting.
     */
    private static Component buttonLabel(MarketPayload.Offer offer) {
        return Component.literal((offer.townBuys() ? "Sell " : "Buy ") + offer.lotPrice());
    }

    private void take(MarketPayload.Offer offer) {
        ClientPacketDistributor.sendToServer(new MarketDealPayload(
                market.post(), offer.resource(), offer.townBuys()));
    }

    /**
     * Whether the player is carrying what this row would cost them.
     *
     * <p>Counted against the same canonical item the counter will take — a
     * button that lights up for a stack of birch planks the server then refuses
     * is worse than one that stays grey.
     */
    private boolean canAfford(MarketPayload.Offer offer) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        var inventory = minecraft.player.getInventory();
        if (!offer.townBuys()) {
            return inventory.countItem(Items.EMERALD) >= offer.lotPrice();
        }
        return inventory.countItem(iconFor(offer)) >= Market.LOT;
    }

    /**
     * The item a row is drawn and counted as.
     *
     * <p>A board can name a resource this client's dictionary has never heard of
     * — the ledger stores anything under any name, and a server may be the newer
     * of the two — so an unknown row draws as a barrier rather than taking the
     * screen down.
     */
    private static Item iconFor(MarketPayload.Offer offer) {
        String itemId = Resources.itemFor(offer.resource());
        return ItemIcons.of(itemId == null ? "" : itemId);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float a) {
        int x = left();
        int h = panelHeight();
        int y = top();

        KingdomsPanel.frame(graphics, x, y, PANEL_WIDTH, h);
        KingdomsPanel.header(graphics, font, x, y, PANEL_WIDTH, title,
                Component.literal("Treasury " + market.treasury() + " coin"), SUBTLE);

        List<MarketPayload.Offer> offers = offers();
        if (offers.isEmpty()) {
            graphics.centeredText(font, Component.literal(
                            "Nothing they want, and nothing they can spare."),
                    x + PANEL_WIDTH / 2, y + HEADER + 4, SUBTLE);
            footer(graphics, x, y, h);
            super.extractRenderState(graphics, mouseX, mouseY, a);
            return;
        }

        for (int i = 0; i < offers.size(); i++) {
            MarketPayload.Offer offer = offers.get(i);
            int rowY = y + HEADER + i * ROW;
            if (i % 2 == 1) {
                KingdomsPanel.stripe(graphics, x, rowY, PANEL_WIDTH);
            }

            graphics.item(new ItemStack(iconFor(offer)), x + PADDING, rowY);
            graphics.text(font, Component.literal(Market.LOT + " " + offer.resource()),
                    x + PADDING + 22, rowY + 4, AMOUNT, false);
            graphics.text(font, Component.literal(reasonWords(offer)),
                    x + REASON_X, rowY + 4, reasonColour(offer), false);
        }

        footer(graphics, x, y, h);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void footer(GuiGraphicsExtractor graphics, int x, int y, int h) {
        KingdomsPanel.rule(graphics, x, y + h - FOOTER + 4, PANEL_WIDTH);
        graphics.centeredText(font, Component.literal(
                        "Prices move with what the town is short of. Paid in emeralds."),
                x + PANEL_WIDTH / 2, y + h - 14, SUBTLE);
    }

    /**
     * The reason, in words.
     *
     * <p>Worded here rather than in {@code Market} because the simulation has
     * never known what language anybody reads. Food gets its own sentence for
     * the desperate case: "they are desperate for grain" is a shop, and "they
     * are starving" is a town.
     */
    private static String reasonWords(MarketPayload.Offer offer) {
        boolean food = TownStores.FOOD.equals(offer.resource());
        return switch (reasonOf(offer)) {
            case DESPERATE -> food ? "They are starving." : "They are desperate for it.";
            case SHORT -> "They are short of it.";
            case GLUT -> "More than they can store.";
            case ORDINARY -> offer.townBuys() ? "They will take it." : "They can spare it.";
        };
    }

    private static int reasonColour(MarketPayload.Offer offer) {
        return switch (reasonOf(offer)) {
            case DESPERATE -> URGENT;
            case SHORT -> WANTING;
            case GLUT -> SPARE;
            case ORDINARY -> LABEL;
        };
    }

    /**
     * The reason came off the wire as a word, and a client can be older than the
     * server that sent it. An unknown one reads as the plain case rather than
     * taking the screen down with it.
     */
    private static Market.Reason reasonOf(MarketPayload.Offer offer) {
        for (Market.Reason known : Market.Reason.values()) {
            if (known.name().equals(offer.reason())) {
                return known;
            }
        }
        return Market.Reason.ORDINARY;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
