package com.civilization.neoforge.client;

import com.civilization.neoforge.net.QuestActionPayload;
import com.civilization.neoforge.net.QuestBoardPayload;
import com.civilization.neoforge.trade.Currency;
import com.civilization.sim.quest.QuestKind;
import com.civilization.sim.quest.QuestState;
import com.civilization.sim.quest.Reputation;
import com.civilization.sim.settlement.Resources;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

import static com.civilization.neoforge.client.CivilizationPanel.AMOUNT;
import static com.civilization.neoforge.client.CivilizationPanel.HEADER;
import static com.civilization.neoforge.client.CivilizationPanel.LABEL;
import static com.civilization.neoforge.client.CivilizationPanel.PADDING;
import static com.civilization.neoforge.client.CivilizationPanel.SUBTLE;
import static com.civilization.neoforge.client.QuestBoardLayout.BUTTON_HEIGHT;
import static com.civilization.neoforge.client.QuestBoardLayout.BUTTON_LEFT;
import static com.civilization.neoforge.client.QuestBoardLayout.BUTTON_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.FOOTER;
import static com.civilization.neoforge.client.QuestBoardLayout.FOOTER_LINES;
import static com.civilization.neoforge.client.QuestBoardLayout.FOOTER_TEXT_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.PANEL_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.REWARD_RIGHT;
import static com.civilization.neoforge.client.QuestBoardLayout.REWARD_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.ROW;
import static com.civilization.neoforge.client.QuestBoardLayout.TEXT_LEFT;
import static com.civilization.neoforge.client.QuestBoardLayout.TEXT_WIDTH;

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
 *
 * <p><strong>The list is bounded and scrolls.</strong> Rows used to be stacked
 * downward from the header for as long as the board was, so the panel's height
 * was the town's business rather than the screen's: twelve notices on a 720p
 * window at GUI scale 3 put the header off the top and the footer — with the only
 * button that changes face in it — off the bottom. Every number in that sum now
 * comes from {@link QuestBoardLayout}, which is where it can be asserted on
 * without a client.
 */
public final class QuestBoardScreen extends Screen {

    /** Work that is finished and waiting to be paid for. */
    private static final int READY = 0xFF88CC88;

    /** Work in hand: taken, not yet done. */
    private static final int IN_HAND = 0xFFFFDD77;

    /** Running out of time. */
    private static final int URGENT = 0xFFFF7755;

    /** An ask lapses inside this many steps and the clock turns red. */
    private static final int SOON = 120;

    /**
     * What a cut sentence ends with.
     *
     * <p>Three periods rather than the single ellipsis character, because the
     * default font is not the only font this can be drawn in and three periods
     * exist in all of them.
     */
    private static final String ELLIPSIS = "...";

    private QuestBoardPayload board;

    /** Which face of the board is up: the asks, or what the town remembers. */
    private boolean showingDone;

    /**
     * The first row on screen, counted in rows rather than pixels.
     *
     * <p>Rows because every row on one face of the board is the same height, so a
     * row index is exact where a pixel offset would have to be rounded back to
     * one anyway — and because a button belongs to a row: a list scrolled half a
     * row would put half a button through the header.
     */
    private int scroll;

    public QuestBoardScreen(QuestBoardPayload board) {
        super(Component.literal(board.town() + " board"));
        this.board = board;
    }

    /**
     * Takes a fresh board after a button, without closing the panel.
     *
     * <p>The scroll position is kept rather than reset — a player who has just
     * accepted the twelfth notice is looking at the twelfth notice — but it is
     * clamped, because claiming a job takes a row off the list and the old
     * position can be past the end of the new one.
     */
    public void update(QuestBoardPayload fresh) {
        this.board = fresh;
        this.scroll = clampedScroll();
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private List<QuestBoardPayload.Row> rows() {
        return board.rows();
    }

    /** How many rows the face that is up actually has. */
    private int rowCount() {
        return showingDone ? board.done().size() : rows().size();
    }

    private int rowHeight() {
        return showingDone ? CivilizationPanel.ROW : ROW;
    }

    /** How many of them are on screen at once. */
    private int visibleRows() {
        return QuestBoardLayout.visibleRows(height, rowHeight(), rowCount());
    }

    private int panelHeight() {
        return QuestBoardLayout.panelHeight(height, rowHeight(), rowCount());
    }

    private int clampedScroll() {
        return QuestBoardLayout.clampScroll(scroll, height, rowHeight(), rowCount());
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    /**
     * A notch of the wheel moves the list one notice.
     *
     * <p>One rather than three: a viewport is five notices tall on the screen this
     * is bounded for, and a three-row jump in a five-row window is a page, not a
     * scroll. The buttons are rebuilt because a button belongs to the row it is
     * drawn beside — scrolling without rebuilding leaves "Accept" pressing the
     * notice that used to be there.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
                                 double scrollY) {
        if (QuestBoardLayout.maxScroll(height, rowHeight(), rowCount()) <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int wanted = scroll - (int) Math.signum(scrollY);
        int held = QuestBoardLayout.clampScroll(wanted, height, rowHeight(), rowCount());
        if (held == scroll) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scroll = held;
        rebuildWidgets();
        return true;
    }

    @Override
    protected void init() {
        int x = left();
        int y = top();
        int h = panelHeight();
        scroll = clampedScroll();

        // The tab first, so it is in the same place whichever face is up.
        addRenderableWidget(Button.builder(
                        Component.literal(showingDone
                                ? "Asks" : "Done (" + board.done().size() + ")"),
                        pressed -> {
                            showingDone = !showingDone;
                            // Each face counts its own rows at its own height, so a
                            // position on one is not a position on the other. Back
                            // to the top, which is also where a player expects a
                            // list they have just opened to be.
                            scroll = 0;
                            rebuildWidgets();
                        })
                .bounds(x + BUTTON_LEFT, y + h - FOOTER + 6, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        if (showingDone) {
            return;   // the town's memory has nothing to press
        }

        // Only the rows on screen get buttons. A button built for a row that is
        // scrolled out of the viewport would be a clickable nothing sitting over
        // the header or under the footer.
        List<QuestBoardPayload.Row> rows = rows();
        int first = scroll;
        int last = Math.min(rows.size(), first + visibleRows());
        for (int i = first; i < last; i++) {
            QuestBoardPayload.Row row = rows.get(i);
            String verb = verbFor(row);
            if (verb == null) {
                continue;   // somebody else's job; there is nothing for you to do
            }
            int rowY = y + HEADER + (i - first) * ROW;
            addRenderableWidget(Button.builder(Component.literal(verb),
                            pressed -> send(row, verb))
                    .bounds(x + BUTTON_LEFT, rowY + (ROW - BUTTON_HEIGHT) / 2,
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

        CivilizationPanel.frame(graphics, x, y, PANEL_WIDTH, h);
        CivilizationPanel.header(graphics, font, x, y, PANEL_WIDTH, title,
                Component.literal("Standing: " + Reputation.wordFor(board.standing())
                        + " (" + board.standing() + ")"),
                board.standing() >= Reputation.TRUSTED_AT ? READY : SUBTLE);

        if (showingDone) {
            drawDone(graphics, x, y);
        } else {
            drawAsks(graphics, x, y);
        }
        drawScrollbar(graphics, x, y);

        CivilizationPanel.rule(graphics, x, y + h - FOOTER + 2, PANEL_WIDTH);
        drawFooter(graphics, x, y + h - FOOTER, Component.literal(showingDone
                ? "What the town remembers being done for it."
                : "Paid out of the town's own purse. Deliveries at the storehouse."));

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /**
     * The sentence under the rule, kept clear of the tab button.
     *
     * <p>Wrapped to the width the button leaves rather than cut, because unlike
     * a row's reason this line has somewhere to go: the footer is thirty pixels
     * tall and one line of text is nine, so a second line fits with room over.
     * Two is the limit and the second is cut if it comes to it, which is what
     * keeps the footer from growing into the bottom edge of the panel.
     */
    private void drawFooter(GuiGraphicsExtractor graphics, int x, int footerTop,
                            Component sentence) {
        List<FormattedCharSequence> lines = font.split(sentence, FOOTER_TEXT_WIDTH);
        int shown = Math.min(lines.size(), FOOTER_LINES);
        // One line sits where it always did; a second pushes the pair apart
        // around that same middle, so the block stays centered in the footer.
        int firstY = 10 - (shown - 1) * 5;
        for (int i = 0; i < shown; i++) {
            graphics.text(font, lines.get(i), x + PADDING, footerTop + firstY + i * 10,
                    SUBTLE, false);
        }
    }

    /**
     * The bar down the right margin, and only when the list runs past its own
     * viewport.
     *
     * <p>Drawn rather than a widget: it is a readout, not a control — the wheel
     * scrolls, and there is nothing here to drag. What it is for is that a
     * bounded list otherwise lies by omission, showing five notices out of twelve
     * with no sign that the other seven exist.
     */
    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y) {
        int rowHeight = rowHeight();
        int count = rowCount();
        if (QuestBoardLayout.maxScroll(height, rowHeight, count) <= 0) {
            return;
        }
        int trackTop = y + HEADER;
        int trackHeight = QuestBoardLayout.viewportHeight(height, rowHeight, count);
        int thumb = QuestBoardLayout.thumbHeight(height, rowHeight, count);
        int thumbTop = trackTop + QuestBoardLayout.thumbOffset(scroll, height, rowHeight, count);
        int barLeft = x + QuestBoardLayout.SCROLLBAR_LEFT;
        graphics.fill(barLeft, trackTop, barLeft + QuestBoardLayout.SCROLLBAR_WIDTH,
                trackTop + trackHeight, CivilizationPanel.RULE);
        graphics.fill(barLeft, thumbTop, barLeft + QuestBoardLayout.SCROLLBAR_WIDTH,
                thumbTop + thumb, SUBTLE);
    }

    private void drawAsks(GuiGraphicsExtractor graphics, int x, int y) {
        List<QuestBoardPayload.Row> rows = rows();
        if (rows.isEmpty()) {
            graphics.centeredText(font, Component.literal(
                            "They are asking for nothing. Come back when they are not."),
                    x + PANEL_WIDTH / 2, y + HEADER + 8, SUBTLE);
            return;
        }
        int first = clampedScroll();
        int last = Math.min(rows.size(), first + visibleRows());
        for (int i = first; i < last; i++) {
            QuestBoardPayload.Row row = rows.get(i);
            int rowY = y + HEADER + (i - first) * ROW;
            if (i % 2 == 1) {
                graphics.fill(x + PADDING - 4, rowY - 2,
                        x + PANEL_WIDTH - PADDING + 4, rowY + ROW - 4,
                        CivilizationPanel.STRIPE);
            }

            graphics.item(new ItemStack(iconFor(row)), x + PADDING, rowY + 4);

            // The left column. Both lines are cut to the same width, so the
            // reason can no longer run under the reward beside it -- which is
            // the whole fault this layout exists to stop.
            graphics.text(font, clipped(row.title()), x + TEXT_LEFT, rowY + 1,
                    AMOUNT, false);
            graphics.text(font, clipped(row.detail()), x + TEXT_LEFT, rowY + 12,
                    SUBTLE, false);

            // The right column, right-aligned inside its own fixed width.
            String tally = tallyOf(row);
            graphics.text(font, tally, x + REWARD_RIGHT - font.width(tally), rowY + 1,
                    stateColor(row), false);
            drawReward(graphics, payWords(row), x + REWARD_RIGHT, rowY + 12);
        }
    }

    /**
     * The reward, right-aligned inside its column.
     *
     * <p>The column is wide enough for the rewards the board ordinarily pays. A
     * job that pays in coin <em>and</em> goods <em>and</em> standing can outrun
     * it, and when it does the line ends in an ellipsis rather than simply
     * stopping — a reward that has been cut short has to look cut short, or the
     * screen is quietly offering less than the town is paying.
     */
    private void drawReward(GuiGraphicsExtractor graphics, Component reward,
                            int right, int y) {
        List<FormattedCharSequence> whole = font.split(reward, REWARD_WIDTH);
        if (whole.size() <= 1) {
            FormattedCharSequence only = whole.isEmpty()
                    ? FormattedCharSequence.EMPTY : whole.getFirst();
            graphics.text(font, only, right - font.width(only), y, LABEL, false);
            return;
        }
        int dots = font.width(ELLIPSIS);
        List<FormattedCharSequence> head = font.split(reward, Math.max(1, REWARD_WIDTH - dots));
        if (head.isEmpty()) {
            graphics.text(font, ELLIPSIS, right - dots, y, LABEL, false);
            return;
        }
        FormattedCharSequence first = head.getFirst();
        graphics.text(font, first, right - font.width(first) - dots, y, LABEL, false);
        graphics.text(font, ELLIPSIS, right - dots, y, LABEL, false);
    }

    /**
     * A sentence cut to the width of the left column, with an ellipsis if it
     * did not fit.
     *
     * <p>Cut rather than wrapped because a row is two lines tall and both of
     * them are spoken for: the title has one and the reason has the other. The
     * full sentence is not lost — it is the same sentence the town says in chat
     * when the ask is taken — and half of it under a title still says which of
     * the town's troubles this job is about, which is what the line is for.
     */
    private String clipped(String text) {
        return clipped(text, TEXT_WIDTH);
    }

    private String clipped(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        int room = Math.max(0, width - font.width(ELLIPSIS));
        return font.plainSubstrByWidth(text, room).stripTrailing() + ELLIPSIS;
    }

    private void drawDone(GuiGraphicsExtractor graphics, int x, int y) {
        List<String> done = board.done();
        if (done.isEmpty()) {
            graphics.centeredText(font, Component.literal(
                            "Nobody has done anything for this town yet."),
                    x + PANEL_WIDTH / 2, y + HEADER + 4, SUBTLE);
            return;
        }
        int first = clampedScroll();
        int last = Math.min(done.size(), first + visibleRows());
        for (int i = first; i < last; i++) {
            int rowY = y + HEADER + (i - first) * CivilizationPanel.ROW;
            if (i % 2 == 1) {
                CivilizationPanel.stripe(graphics, x, rowY, PANEL_WIDTH);
            }
            // Nothing sits to the right of a remembered line, so it gets the
            // whole width between the margins -- and stops there. Except the
            // scrollbar, when there is one: the line stops short of it rather
            // than running under it.
            int room = PANEL_WIDTH - 2 * PADDING
                    - (QuestBoardLayout.maxScroll(height, CivilizationPanel.ROW,
                            done.size()) > 0 ? PADDING : 0);
            graphics.text(font, clipped(done.get(i), room),
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

    /**
     * The reward in as few words as it can be said in.
     *
     * <p>A component rather than a string because the money's own name is
     * translated — see {@link Currency}, and {@code docs/CURRENCY.md} for why
     * nothing may write that word down twice.
     */
    private static MutableComponent payWords(QuestBoardPayload.Row row) {
        QuestBoardPayload.Pay pay = row.pay();
        MutableComponent words = Component.empty();
        boolean anything = false;
        if (pay.coin() > 0) {
            words.append(Currency.amount(pay.coin()));
            anything = true;
        }
        if (pay.goodsAmount() > 0) {
            if (anything) {
                words.append(", ");
            }
            words.append(pay.goodsAmount() + " " + pay.goods());
            anything = true;
        }
        if (pay.standing() > 0) {
            if (anything) {
                words.append(", ");
            }
            words.append("+" + pay.standing() + " standing");
        }
        return anything ? words : Component.literal("Nothing but thanks");
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
