package com.civilization.neoforge.client;

/**
 * Where the four things in a quest row stand, and how wide each of them is.
 *
 * <p>The board's rows used to be drawn by right-aligning the reward against the
 * button and letting the title and its reason run from the left at whatever
 * length they happened to be. Nothing stopped the two meeting, and in a real
 * town they met constantly: "The larder will not see us through the winter" ran
 * straight under "48 Coin, +2 standing" and out past the edge of the panel. A
 * row drawn that way is not a layout, it is two layouts that happen not to
 * collide on short strings.
 *
 * <p>So the row is columns, and they are declared here rather than worked out
 * twice at two different call sites. Left to right, and adding up to exactly
 * {@link #PANEL_WIDTH}:
 *
 * <pre>
 *   14  padding
 *   16  the item icon
 *    6  gap
 *  120  title, and the reason under it        &lt;- clipped to this
 *    8  gap
 *  112  the count, and the reward under it    &lt;- right-aligned inside this
 *    8  gap
 *   62  the button
 *   14  padding
 *  ---
 *  360
 * </pre>
 *
 * <p>Every number is in GUI pixels, which is what makes this arithmetic enough
 * to check. A GUI scale multiplies the whole screen uniformly — the panel, the
 * font and the gaps together — so a row that fits at scale 1 fits at 2 and at 3,
 * and one that overlaps at scale 1 overlaps identically at every other scale.
 * There is no scale at which the old row stopped overlapping and none at which
 * this one starts.
 *
 * <p>Split out of {@link QuestBoardScreen} so it can be asserted on without a
 * client: the screen itself cannot be constructed in a unit test, and this is
 * the half of it worth testing.
 */
public final class QuestBoardLayout {

    private QuestBoardLayout() {
    }

    /**
     * Wide enough for a title, a sentence under it, a reward and a button.
     *
     * <p>Held at 360 deliberately. The smallest screen Minecraft will ever hand
     * a GUI is 426 wide — the virtual width at scale 3 on a 1280-pixel window,
     * the narrowest of the common ones — so 360 is the widest round number that
     * still sits inside the frame there. The market's 320 fits everything but
     * the sentence, and the sentence is the point.
     */
    public static final int PANEL_WIDTH = 360;

    /** Blank margin down each side. Shared with every other panel in the mod. */
    public static final int PADDING = CivilizationPanel.PADDING;

    /** Two lines of text and air: a title over its reason. */
    public static final int ROW = 32;

    public static final int FOOTER = 30;

    public static final int BUTTON_WIDTH = 62;
    public static final int BUTTON_HEIGHT = 16;

    /** A rendered item stack. */
    public static final int ICON = 16;

    /** Between the icon and the words beside it. */
    public static final int ICON_GAP = 6;

    /** Between one column and the next. Wide enough to read as a column break. */
    public static final int COLUMN_GAP = 8;

    /**
     * The reward column.
     *
     * <p>A hundred pixels is the longest ordinary reward — "48 Coin, +2
     * standing" — so this is that with a little room. A reward longer than the
     * column (one that pays in goods as well) is clipped rather than allowed to
     * grow leftward into the sentence, which is exactly what it used to do.
     */
    public static final int REWARD_WIDTH = 112;

    /** Where the title and the reason start, measured from the panel's left edge. */
    public static final int TEXT_LEFT = PADDING + ICON + ICON_GAP;

    /** The button's left edge: it sits against the right margin. */
    public static final int BUTTON_LEFT = PANEL_WIDTH - PADDING - BUTTON_WIDTH;

    /** The reward column is right-aligned to here. */
    public static final int REWARD_RIGHT = BUTTON_LEFT - COLUMN_GAP;

    public static final int REWARD_LEFT = REWARD_RIGHT - REWARD_WIDTH;

    /** How much room the title and the reason get before they are clipped. */
    public static final int TEXT_WIDTH = REWARD_LEFT - COLUMN_GAP - TEXT_LEFT;

    /**
     * How wide the sentence under the rule may be.
     *
     * <p>The footer had the same fault as the rows, one line lower: it was drawn
     * from the left margin at full length with the tab button sitting on top of
     * it, so it read "Deliveries at the sto|Done (0)". It stops short of the
     * button now, and wraps to a second line rather than being cut off.
     */
    public static final int FOOTER_TEXT_WIDTH = BUTTON_LEFT - COLUMN_GAP - PADDING;

    /** How many lines of footer fit between the rule and the bottom edge. */
    public static final int FOOTER_LINES = 2;

    // --- and how far down the board may go -----------------------------------

    /**
     * The header the panel shares with every other screen in the mod.
     *
     * <p>Aliased here, the way {@link #PADDING} is, so that the vertical
     * arithmetic below reads as one sum in one place rather than reaching across
     * two classes halfway through.
     */
    public static final int HEADER = CivilizationPanel.HEADER;

    /**
     * The smallest screen the board must fit inside, in GUI pixels.
     *
     * <p>720 pixels of window at GUI scale 3, which is the case the fault was
     * reported on: a 1280×720 window is the smallest anybody plays at and 3 is
     * the largest scale that window offers. Everything wider or less magnified
     * gives more room, never less, so a board that fits here fits everywhere.
     *
     * <p>The horizontal bound is {@link #PANEL_WIDTH} against 426 — the same
     * window, the same scale — and it has been asserted since the columns
     * landed. This is that argument's other half, and it was missing: rows were
     * stacked from the top of the panel with nothing counting them, so the panel
     * simply grew taller than the screen it was centered in. At six notices the
     * first row and the footer were both off the edge.
     */
    public static final int MIN_SCREEN_HEIGHT = 240;

    /**
     * A slim scrollbar, in the right-hand margin.
     *
     * <p>In the margin on purpose: the row's columns add up to exactly
     * {@link #PANEL_WIDTH} and a scrollbar that took a column of its own would
     * have to take those pixels off the reason or off the reward, which are the
     * two things on the row worth reading. {@link #PADDING} is fourteen, so four
     * pixels of bar with five of air either side fits in ground that was blank
     * anyway, and the bar is only drawn when there is something to scroll.
     */
    public static final int SCROLLBAR_WIDTH = 4;

    /** The bar's left edge, inside the right margin. */
    public static final int SCROLLBAR_LEFT = PANEL_WIDTH - 5 - SCROLLBAR_WIDTH;

    /**
     * The shortest the thumb may be drawn.
     *
     * <p>A hundred notices in a viewport of five would otherwise give a thumb of
     * one pixel, which is a mark rather than a handle.
     */
    public static final int MIN_THUMB = 8;

    /**
     * How many rows of this height the screen has room for between the header and
     * the footer.
     *
     * <p>At least one, always: a screen too short for even a single row gets a
     * clipped row rather than a panel with no list in it, because a board that
     * shows nothing is indistinguishable from a town that is asking for nothing.
     */
    public static int rowsThatFit(int screenHeight, int rowHeight) {
        int room = screenHeight - HEADER - FOOTER;
        return Math.max(1, room / rowHeight);
    }

    /** How many rows are actually drawn: what fits, or all of them if fewer. */
    public static int visibleRows(int screenHeight, int rowHeight, int rowCount) {
        return Math.min(Math.max(1, rowCount), rowsThatFit(screenHeight, rowHeight));
    }

    /** The height of the list itself — the part that scrolls. */
    public static int viewportHeight(int screenHeight, int rowHeight, int rowCount) {
        return visibleRows(screenHeight, rowHeight, rowCount) * rowHeight;
    }

    /**
     * The whole panel: header, as much list as fits, footer.
     *
     * <p>This is the number that used to grow without bound, and the only reason
     * it is a function of the screen rather than of the board.
     */
    public static int panelHeight(int screenHeight, int rowHeight, int rowCount) {
        return HEADER + viewportHeight(screenHeight, rowHeight, rowCount) + FOOTER;
    }

    /** The furthest down the list can be scrolled, counted in rows. */
    public static int maxScroll(int screenHeight, int rowHeight, int rowCount) {
        return Math.max(0, rowCount - visibleRows(screenHeight, rowHeight, rowCount));
    }

    /** A scroll position held inside its own range. */
    public static int clampScroll(int scroll, int screenHeight, int rowHeight,
                                  int rowCount) {
        return Math.max(0, Math.min(scroll, maxScroll(screenHeight, rowHeight, rowCount)));
    }

    /** How tall the thumb is: the visible share of the list, floored at {@link #MIN_THUMB}. */
    public static int thumbHeight(int screenHeight, int rowHeight, int rowCount) {
        int viewport = viewportHeight(screenHeight, rowHeight, rowCount);
        int visible = visibleRows(screenHeight, rowHeight, rowCount);
        int count = Math.max(1, rowCount);
        return Math.max(MIN_THUMB, Math.min(viewport, viewport * visible / count));
    }

    /**
     * How far down the track the thumb's top sits, in pixels from the top of the
     * viewport.
     *
     * <p>Zero when there is nothing to scroll, and exactly
     * {@code viewport - thumb} at the bottom of the list — so the thumb touching
     * the bottom of the track means the last notice is on screen, which is the
     * one thing a scrollbar has to tell the truth about.
     */
    public static int thumbOffset(int scroll, int screenHeight, int rowHeight,
                                  int rowCount) {
        int range = maxScroll(screenHeight, rowHeight, rowCount);
        if (range <= 0) {
            return 0;
        }
        int travel = viewportHeight(screenHeight, rowHeight, rowCount)
                - thumbHeight(screenHeight, rowHeight, rowCount);
        return clampScroll(scroll, screenHeight, rowHeight, rowCount) * travel / range;
    }
}
