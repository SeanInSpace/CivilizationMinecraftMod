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
}
