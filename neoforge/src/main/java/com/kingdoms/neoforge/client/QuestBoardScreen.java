package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.net.QuestActionPayload;
import com.kingdoms.neoforge.net.QuestBoardPayload;
import com.kingdoms.sim.quest.QuestKind;
import com.kingdoms.sim.quest.QuestState;
import com.kingdoms.sim.quest.Reputation;
import com.kingdoms.sim.settlement.Resources;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

import static com.kingdoms.neoforge.client.KingdomsPanel.AMOUNT;
import static com.kingdoms.neoforge.client.KingdomsPanel.HEADER;
import static com.kingdoms.neoforge.client.KingdomsPanel.LABEL;
import static com.kingdoms.neoforge.client.KingdomsPanel.PADDING;
import static com.kingdoms.neoforge.client.KingdomsPanel.SUBTLE;

/**
 * The noticeboard, with the reason for every ask written under it.
 *
 * <p>The same argument the market screen makes, one building over. A list of
 * tasks is a menu; a list of tasks that each say what has gone wrong in the town
 * is a place. "Every job on the books is waiting on logs" is the whole of why a
 * player carries logs, and there is nowhere to put that sentence except on the
 * row it belongs to — which is why the rows here are two lines high and the
 * screen is wider than any other in the mod.
 *
 * <p>The board is a snapshot from the server and this screen never guesses at
 * one. Pressing a button sends an id and the server sends a fresh board back,
 * which {@link #update} folds in without closing anything.
 */
public final class QuestBoardScreen extends Screen {

    /**
     * Wide enough for a title, a sentence under it, a reward and a button. The
     * market's 320 fits everything but the sentence at full length, and the
     * sentence is the point.
     */
    private static final int PANEL_WIDTH = 360;

    /** Two lines of text and air: a title over its reason. */
    private static final int ROW = 32;

    private static final int FOOTER = 30;

    private static final int BUTTON_WIDTH = 62;
    private static final int BUTTON_HEIGHT = 16;

    /** Work that is finished and waiting to be paid for. */
    private static final int READY = 0xFF88CC88;

    /** Work in hand: taken, not yet done. */
    private static final int IN_HAND = 0xFFFFDD77;

    /** Running out of time. */
    private static final int URGENT = 0xFFFF7755;

    /** An ask lapses inside this many steps and the clock turns red. */
    private static final int SOON = 120;

    private QuestBoardPayload board;

    /** Which face of the board is up: the asks, or what the town remembers. */
    private boolean showingDone;

    public QuestBoardScreen(QuestBoardPayload board) {
        super(Component.literal(board.town() + " board"));
        this.board = board;
    }

    /** Takes a fresh board after a button, without closing the panel. */
    public void update(QuestBoardPayload fresh) {
        this.board = fresh;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private List<QuestBoardPayload.Row> rows() {
        return board.rows();
    }

    private int shownRows() {
        return showingDone
                ? Math.max(1, board.done().size())
                : Math.max(1, rows().size());
    }

    private int rowHeight() {
        return showingDone ? KingdomsPanel.ROW : ROW;
    }

    private int panelHeight() {
        return HEADER + shownRows() * rowHeight() + FOOTER;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    @Override
    protected void init() {
        int x = left();
        int y = top();
        int h = panelHeight();

        // The tab first, so it is in the same place whichever face is up.
        addRenderableWidget(Button.builder(
                        Component.literal(showingDone
                                ? "Asks" : "Done (" + board.done().size() + ")"),
                        pressed -> {
                            showingDone = !showingDone;
                            rebuildWidgets();
                        })
                .bounds(x + PANEL_WIDTH - PADDING - BUTTON_WIDTH,
                        y + h - FOOTER + 6, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        if (showingDone) {
            return;   // the town's memory has nothing to press
        }

        List<QuestBoardPayload.Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            QuestBoardPayload.Row row = rows.get(i);
            String verb = verbFor(row);
            if (verb == null) {
                continue;   // somebody else's job; there is nothing for you to do
            }
            int rowY = y + HEADER + i * ROW;
            addRenderableWidget(Button.builder(Component.literal(verb),
                            pressed -> send(row, verb))
                    .bounds(x + PANEL_WIDTH - PADDING - BUTTON_WIDTH,
                            rowY + (ROW - BUTTON_HEIGHT) / 2,
                            BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }
    }

    /**
     * What this row's button says, or null for a row with no button.
     *
     * <p>Three verbs, and which one depends on whose the job is: an offer is
     * taken, your own unfinished job is given back, and your own finished one is
     * collected for. Somebody else's job gets no button at all rather than a
     * dead one — the screen says who has it in the line beside the title.
     */
    private static String verbFor(QuestBoardPayload.Row row) {
        QuestState state = QuestState.parse(row.state(), QuestState.OFFERED);
        if (state == QuestState.OFFERED) {
            return "Accept";
        }
        if (!row.mine()) {
            return null;
        }
        return state == QuestState.DONE ? "Claim" : "Abandon";
    }

    private void send(QuestBoardPayload.Row row, String verb) {
        String action = switch (verb) {
            case "Accept" -> QuestActionPayload.ACCEPT;
            case "Claim" -> QuestActionPayload.CLAIM;
            default -> QuestActionPayload.ABANDON;
        };
        ClientPacketDistributor.sendToServer(
                new QuestActionPayload(board.post(), row.id(), action));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float a) {
        int x = left();
        int h = panelHeight();
        int y = top();

        KingdomsPanel.frame(graphics, x, y, PANEL_WIDTH, h);
        KingdomsPanel.header(graphics, font, x, y, PANEL_WIDTH, title,
                Component.literal("Standing: " + Reputation.wordFor(board.standing())
                        + " (" + board.standing() + ")"),
                board.standing() >= Reputation.TRUSTED_AT ? READY : SUBTLE);

        if (showingDone) {
            drawDone(graphics, x, y);
        } else {
            drawAsks(graphics, x, y);
        }

        KingdomsPanel.rule(graphics, x, y + h - FOOTER + 2, PANEL_WIDTH);
        graphics.text(font, Component.literal(showingDone
                        ? "What the town remembers being done for it."
                        : "Paid out of the town's own purse. Deliveries at the storehouse."),
                x + PADDING, y + h - FOOTER + 10, SUBTLE, false);

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void drawAsks(GuiGraphicsExtractor graphics, int x, int y) {
        List<QuestBoardPayload.Row> rows = rows();
        if (rows.isEmpty()) {
            graphics.centeredText(font, Component.literal(
                            "They are asking for nothing. Come back when they are not."),
                    x + PANEL_WIDTH / 2, y + HEADER + 8, SUBTLE);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            QuestBoardPayload.Row row = rows.get(i);
            int rowY = y + HEADER + i * ROW;
            if (i % 2 == 1) {
                graphics.fill(x + PADDING - 4, rowY - 2,
                        x + PANEL_WIDTH - PADDING + 4, rowY + ROW - 4,
                        KingdomsPanel.STRIPE);
            }

            graphics.item(new ItemStack(iconFor(row)), x + PADDING, rowY + 4);
            graphics.text(font, Component.literal(row.title()),
                    x + PADDING + 22, rowY + 1, AMOUNT, false);
            graphics.text(font, Component.literal(row.detail()),
                    x + PADDING + 22, rowY + 12, SUBTLE, false);

            String tally = tallyOf(row);
            int wide = font.width(tally);
            graphics.text(font, Component.literal(tally),
                    x + PANEL_WIDTH - PADDING - BUTTON_WIDTH - 8 - wide, rowY + 1,
                    stateColor(row), false);

            String pay = payWords(row);
            int payWide = font.width(pay);
            graphics.text(font, Component.literal(pay),
                    x + PANEL_WIDTH - PADDING - BUTTON_WIDTH - 8 - payWide, rowY + 12,
                    LABEL, false);
        }
    }

    private void drawDone(GuiGraphicsExtractor graphics, int x, int y) {
        List<String> done = board.done();
        if (done.isEmpty()) {
            graphics.centeredText(font, Component.literal(
                            "Nobody has done anything for this town yet."),
                    x + PANEL_WIDTH / 2, y + HEADER + 4, SUBTLE);
            return;
        }
        for (int i = 0; i < done.size(); i++) {
            int rowY = y + HEADER + i * KingdomsPanel.ROW;
            if (i % 2 == 1) {
                KingdomsPanel.stripe(graphics, x, rowY, PANEL_WIDTH);
            }
            graphics.text(font, Component.literal(done.get(i)),
                    x + PADDING, rowY + 4, LABEL, false);
        }
    }

    /**
     * The count, or whose job it is, or how long is left.
     *
     * <p>One line that changes with the state, because the three facts are never
     * wanted at once: an offer nobody has taken has no progress worth showing,
     * and a finished job's clock has stopped.
     */
    private static String tallyOf(QuestBoardPayload.Row row) {
        QuestState state = QuestState.parse(row.state(), QuestState.OFFERED);
        return switch (state) {
            case DONE -> "Done";
            case ACCEPTED -> row.mine()
                    ? row.progress() + " of " + row.amount()
                    : "Taken";
            default -> row.amount() + " wanted";
        };
    }

    private static int stateColor(QuestBoardPayload.Row row) {
        QuestState state = QuestState.parse(row.state(), QuestState.OFFERED);
        if (state == QuestState.DONE) {
            return READY;
        }
        if (state == QuestState.ACCEPTED && row.mine()) {
            return IN_HAND;
        }
        return row.stepsLeft() <= SOON ? URGENT : LABEL;
    }

    /** The reward in as few words as it can be said in. */
    private static String payWords(QuestBoardPayload.Row row) {
        QuestBoardPayload.Pay pay = row.pay();
        StringBuilder words = new StringBuilder();
        if (pay.coin() > 0) {
            words.append(pay.coin()).append(" coin");
        }
        if (pay.goodsAmount() > 0) {
            if (words.length() > 0) {
                words.append(", ");
            }
            words.append(pay.goodsAmount()).append(' ').append(pay.goods());
        }
        if (pay.standing() > 0) {
            if (words.length() > 0) {
                words.append(", ");
            }
            words.append('+').append(pay.standing()).append(" standing");
        }
        return words.length() == 0 ? "Nothing but thanks" : words.toString();
    }

    /**
     * What a row is drawn as.
     *
     * <p>A delivery shows the thing it wants, because that is the one row where
     * the item <em>is</em> the ask. The rest take a stand-in. An unknown kind —
     * a board from a newer server — draws as paper, which is what a notice
     * nobody can read looks like.
     */
    private static Item iconFor(QuestBoardPayload.Row row) {
        QuestKind kind = QuestKind.parse(row.kind());
        return switch (kind) {
            case DELIVER -> {
                String wanted = Resources.itemFor(row.target());
                yield ItemIcons.of(wanted == null ? "" : wanted);
            }
            case SLAY -> Items.IRON_SWORD;
            case CLEAR -> Items.TORCH;
            case VISIT -> Items.COMPASS;
            case UNKNOWN -> Items.PAPER;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
