package com.kingdoms.neoforge.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The chrome every drawn screen in this mod shares: the frame, the header and
 * the banding on a list of rows.
 *
 * <p>None of these screens is textured. They are rectangles and text, which is
 * cheap and scales to any GUI scale, and it means the whole visual identity of
 * the mod is eight color constants and three pieces of arithmetic — which four
 * separate screens had each written out for themselves. That is the kind of
 * duplication that does not announce itself when it drifts: the town overview's
 * subtitle had wandered two pixels above everybody else's and its header two
 * pixels below, and nothing was wrong enough for anyone to notice.
 *
 * <p>So the palette and the geometry live here once. What stays with each screen
 * is what genuinely differs — its width, what its header says, and everything
 * below the rule.
 */
public final class KingdomsPanel {

    private KingdomsPanel() {
    }

    // --- the palette ---

    /** The ground a panel is drawn on: near-black, faintly warm, mostly opaque. */
    public static final int PANEL = 0xF0201010;

    public static final int BORDER = 0xFF6A6A6A;

    /** A divider. Dimmer than the border, or it reads as a second edge. */
    public static final int RULE = 0xFF4A4A4A;

    /** The banding on alternate rows: white at a tenth, so it lifts without glowing. */
    public static final int STRIPE = 0x18FFFFFF;

    public static final int TITLE = 0xFFFFE0A0;
    public static final int LABEL = 0xFFC8C8C8;
    public static final int SUBTLE = 0xFF9A9A9A;

    /** A number, against {@link #LABEL} for the name beside it. */
    public static final int AMOUNT = 0xFFFFFFFF;

    // --- geometry ---

    /**
     * Height of the header: title, subtitle, and the rule beneath them.
     *
     * <p>What a screen puts below the rule is its own business, but everything
     * starts at the same place, so two panels open side by side line up.
     */
    public static final int HEADER = 44;

    /** A row of icon and text. Tall enough for a 16-pixel item with air round it. */
    public static final int ROW = 20;

    /** Blank margin down each side of a panel's contents. */
    public static final int PADDING = 14;

    // --- drawing ---

    /** The panel itself: ground and edge. */
    public static void frame(GuiGraphicsExtractor graphics, int x, int y,
                             int width, int height) {
        frame(graphics, x, y, width, height, PANEL);
    }

    /**
     * The same frame on a ground of your choosing.
     *
     * <p>The town map wants a colder, flatter black than the ledgers do: it is
     * mostly a drawing of terrain, and the warm cast that suits a page of text
     * behind a green plan looks like a stain.
     */
    public static void frame(GuiGraphicsExtractor graphics, int x, int y,
                             int width, int height, int ground) {
        graphics.fill(x, y, x + width, y + height, ground);
        graphics.outline(x, y, width, height, BORDER);
    }

    /**
     * Title, subtitle and the rule under them.
     *
     * <p>The subtitle takes a color because it is the one line on these screens
     * that says how the thing is doing rather than what it is — how hungry a
     * settler is, how far a build has got — and the screens that have something
     * to say warm it up. {@link #SUBTLE} is the neutral answer.
     */
    public static void header(GuiGraphicsExtractor graphics, Font font, int x, int y,
                              int width, Component title, Component subtitle,
                              int subtitleColour) {
        int centre = x + width / 2;
        graphics.centeredText(font, title, centre, y + 12, TITLE);
        graphics.centeredText(font, subtitle, centre, y + 26, subtitleColour);
        rule(graphics, x, y + 38, width);
    }

    /** A one-pixel divider, inset by the padding at both ends. */
    public static void rule(GuiGraphicsExtractor graphics, int x, int y, int width) {
        graphics.fill(x + PADDING, y, x + width - PADDING, y + 1, RULE);
    }

    /**
     * The band behind an odd-numbered row.
     *
     * <p>Wider than the padding at both ends on purpose: a stripe flush with the
     * text looks like a mistake, and four extra pixels each side reads as a band
     * the row sits in. At a dozen resources an unbroken list is hard to track
     * across, and this costs nothing.
     */
    public static void stripe(GuiGraphicsExtractor graphics, int x, int rowY, int width) {
        graphics.fill(x + PADDING - 4, rowY - 2,
                x + width - PADDING + 4, rowY + ROW - 4, STRIPE);
    }
}
