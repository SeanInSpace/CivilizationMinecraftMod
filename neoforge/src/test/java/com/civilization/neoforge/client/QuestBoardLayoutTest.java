package com.civilization.neoforge.client;

import org.junit.jupiter.api.Test;

import static com.civilization.neoforge.client.QuestBoardLayout.BUTTON_LEFT;
import static com.civilization.neoforge.client.QuestBoardLayout.BUTTON_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.COLUMN_GAP;
import static com.civilization.neoforge.client.QuestBoardLayout.FOOTER;
import static com.civilization.neoforge.client.QuestBoardLayout.FOOTER_LINES;
import static com.civilization.neoforge.client.QuestBoardLayout.FOOTER_TEXT_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.HEADER;
import static com.civilization.neoforge.client.QuestBoardLayout.ICON;
import static com.civilization.neoforge.client.QuestBoardLayout.MIN_SCREEN_HEIGHT;
import static com.civilization.neoforge.client.QuestBoardLayout.ICON_GAP;
import static com.civilization.neoforge.client.QuestBoardLayout.PADDING;
import static com.civilization.neoforge.client.QuestBoardLayout.PANEL_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.REWARD_LEFT;
import static com.civilization.neoforge.client.QuestBoardLayout.REWARD_RIGHT;
import static com.civilization.neoforge.client.QuestBoardLayout.REWARD_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.ROW;
import static com.civilization.neoforge.client.QuestBoardLayout.TEXT_LEFT;
import static com.civilization.neoforge.client.QuestBoardLayout.TEXT_WIDTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quest board's columns fit inside the quest board.
 *
 * <p>They did not. The reason under each title was drawn from the left at
 * whatever width the sentence happened to be, and the reward was drawn
 * right-aligned against the button, so the two grew towards each other and met:
 * "The larder will not see us through the winter" ran under "48 Coin, +2
 * standing" and on past the edge of the panel, and the footer did the same thing
 * one line lower under the tab button — "Deliveries at the sto|Done (0)".
 *
 * <p>Nothing about that is visible from the code. Two independent pieces of
 * arithmetic overlap or they do not depending on the strings, and the strings
 * come from the simulation. What can be checked is that the row is one piece of
 * arithmetic rather than two: that the columns are declared, that they are laid
 * end to end, and that they add up to the width of the panel with nothing over.
 * A column that fits leaves no room for a collision.
 *
 * <p>The same argument turned on its side is the second half of this file. The
 * rows were stacked downward from the header with nothing counting them, so six
 * notices reached the bottom edge of a 720p screen at GUI scale 3 and twelve put
 * the header, the footer and the tab button all off it. The list is bounded to a
 * viewport now and scrolls inside it, and the arithmetic for that lives here for
 * the same reason the columns do: a screen cannot be built in a unit test and
 * this can.
 */
class QuestBoardLayoutTest {

    @Test
    void theColumnsAddUpToThePanel() {
        assertEquals(PANEL_WIDTH,
                PADDING + ICON + ICON_GAP
                        + TEXT_WIDTH + COLUMN_GAP
                        + REWARD_WIDTH + COLUMN_GAP
                        + BUTTON_WIDTH + PADDING,
                "the row's columns do not add up to the panel they are drawn in");
    }

    @Test
    void theColumnsAreLaidEndToEnd() {
        assertEquals(PADDING + ICON + ICON_GAP, TEXT_LEFT,
                "the words do not start after the icon");
        assertEquals(REWARD_LEFT, TEXT_LEFT + TEXT_WIDTH + COLUMN_GAP,
                "the reward column does not start after the text column");
        assertEquals(REWARD_RIGHT, REWARD_LEFT + REWARD_WIDTH);
        assertEquals(BUTTON_LEFT, REWARD_RIGHT + COLUMN_GAP,
                "the button does not start after the reward column");
    }

    @Test
    void theButtonSitsAtTheRightMargin() {
        assertEquals(PANEL_WIDTH - PADDING, BUTTON_LEFT + BUTTON_WIDTH,
                "the button does not end at the right margin");
    }

    @Test
    void everyColumnHasRoomToSayItsPiece() {
        // "48 Coin, +2 standing" is the longest reward the board pays for an
        // ordinary ask, and it is a hundred pixels of the default font. A reward
        // column narrower than that would clip the usual case rather than the
        // unusual one.
        assertTrue(REWARD_WIDTH >= 100,
                "the reward column is too narrow for an ordinary reward: " + REWARD_WIDTH);
        // Twenty characters of a reason, give or take, at six pixels each. Short,
        // and the point is that it is a known amount rather than whatever is
        // left over.
        assertTrue(TEXT_WIDTH >= 110,
                "there is not enough room left for a title and a reason: " + TEXT_WIDTH);
    }

    @Test
    void theFooterStopsShortOfTheTabButton() {
        assertEquals(PANEL_WIDTH,
                PADDING + FOOTER_TEXT_WIDTH + COLUMN_GAP + BUTTON_WIDTH + PADDING,
                "the footer sentence and the tab button do not share the footer");
        // Two lines of nine-pixel text plus the rule above them, inside thirty.
        assertTrue(FOOTER_LINES * 10 + 2 <= FOOTER,
                "the wrapped footer does not fit between the rule and the bottom edge");
    }

    /**
     * The panel fits the screen at every GUI scale a player can choose.
     *
     * <p>A GUI scale multiplies the whole screen uniformly, so the column
     * arithmetic above is scale-free — a row that fits at one scale fits at all
     * of them, and one that overlaps overlaps identically everywhere. What the
     * scale does change is how much virtual screen there is to sit in. The
     * narrowest of the common windows is 1280 pixels, which at scale 3 leaves
     * 426 across, so that is what the panel has to fit inside; scale 2 on the
     * same window gives 640 and is no constraint at all.
     */
    @Test
    void thePanelFitsTheNarrowestScreenAtScaleThree() {
        assertTrue(PANEL_WIDTH <= 1280 / 3,
                "the panel is wider than a 1280-pixel window at GUI scale 3");
        assertTrue(PANEL_WIDTH <= 1280 / 2);
        // And the row is two lines of text and air, which is what makes a reason
        // fit under a title at all.
        assertTrue(ROW >= 2 * 10 + 4, "a row is too short for a title and a reason");
    }

    // --- and the vertical bound, which is the other half of that argument -----

    /**
     * Every board fits the screen it is drawn on, at one notice, at five and at
     * twelve.
     *
     * <p>Three counts, chosen rather than swept: one is the smallest list there
     * is, five is exactly what the smallest screen holds — so it is the last
     * count that needs no scrolling and the one an off-by-one lands on — and
     * twelve is a real town's board, which is where the fault was found. Six
     * notices used to make a panel 236 tall on a screen 240 tall and it looked
     * fine; twelve made it 428 on that same screen, with the header and the
     * footer both off the edge and the tab button unreachable.
     */
    @Test
    void aBoardOfAnyLengthFitsTheSmallestScreen() {
        for (int notices : new int[] {1, 5, 12}) {
            int h = QuestBoardLayout.panelHeight(MIN_SCREEN_HEIGHT, ROW, notices);
            assertTrue(h <= MIN_SCREEN_HEIGHT,
                    notices + " notices draw a panel " + h + " tall on a screen "
                            + MIN_SCREEN_HEIGHT + " tall. The panel is centered, so"
                            + " what overflows is the header at the top and the"
                            + " footer -- and the tab button in it -- at the bottom");
            // The list is at least a row, whatever else is true, and never more
            // rows than the board has.
            int visible = QuestBoardLayout.visibleRows(MIN_SCREEN_HEIGHT, ROW, notices);
            assertTrue(visible >= 1, notices + " notices draw no rows at all");
            assertTrue(visible <= notices,
                    notices + " notices draw " + visible + " rows, which is more"
                            + " rows than there are notices");
        }
    }

    @Test
    void fiveNoticesAreTheMostTheSmallestScreenShowsAtOnce() {
        // The number the fault was reported against: "more than five notices
        // overflow a 720p window at GUI scale 3". Five is what fits, and it is
        // written down here so that a taller row or a deeper footer cannot
        // quietly make it four without somebody deciding to.
        assertEquals(5, QuestBoardLayout.rowsThatFit(MIN_SCREEN_HEIGHT, ROW),
                "the smallest screen no longer holds five notices");
        assertEquals(0, QuestBoardLayout.maxScroll(MIN_SCREEN_HEIGHT, ROW, 5),
                "a board of five has something to scroll on the screen that fits five");
        assertEquals(7, QuestBoardLayout.maxScroll(MIN_SCREEN_HEIGHT, ROW, 12),
                "twelve notices in a viewport of five is seven rows of scroll");
    }

    @Test
    void aShortBoardIsDrawnNoTallerThanItNeeds() {
        // The bound is a ceiling, not a floor. A board of one ask must not open a
        // panel with four empty rows in it.
        assertEquals(HEADER + ROW + FOOTER,
                QuestBoardLayout.panelHeight(MIN_SCREEN_HEIGHT, ROW, 1),
                "a single notice opens a panel taller than one row");
        // And an empty board still gets a row to write "they are asking for
        // nothing" in.
        assertEquals(HEADER + ROW + FOOTER,
                QuestBoardLayout.panelHeight(MIN_SCREEN_HEIGHT, ROW, 0));
    }

    @Test
    void theScrollPositionIsHeldInsideItsOwnList() {
        // Claiming a job takes a row off the board, and the board is re-sent
        // while the screen is open. A position past the end of the new list would
        // draw from beyond it.
        assertEquals(7, QuestBoardLayout.clampScroll(99, MIN_SCREEN_HEIGHT, ROW, 12),
                "a scroll position past the end of the list is not pulled back");
        assertEquals(0, QuestBoardLayout.clampScroll(-3, MIN_SCREEN_HEIGHT, ROW, 12),
                "a negative scroll position is not pulled back");
        assertEquals(0, QuestBoardLayout.clampScroll(4, MIN_SCREEN_HEIGHT, ROW, 5),
                "a list that fits can still be scrolled off its own top");
    }

    /**
     * The thumb says where in the list you are, and says it truthfully at both
     * ends.
     *
     * <p>The end conditions are the whole of what a scrollbar promises: at the
     * top it is at the top, and at the bottom it is flush with the bottom of the
     * track. A bar that stops three pixels short at the end of a list reads as
     * "there is more", and the player keeps scrolling at a list that has run out.
     */
    @Test
    void theThumbReachesBothEndsOfItsTrack() {
        int viewport = QuestBoardLayout.viewportHeight(MIN_SCREEN_HEIGHT, ROW, 12);
        int thumb = QuestBoardLayout.thumbHeight(MIN_SCREEN_HEIGHT, ROW, 12);

        assertTrue(thumb >= QuestBoardLayout.MIN_THUMB, "the thumb is too small to see");
        assertTrue(thumb < viewport,
                "the thumb fills its whole track on a list that scrolls");
        assertEquals(0, QuestBoardLayout.thumbOffset(0, MIN_SCREEN_HEIGHT, ROW, 12),
                "the thumb does not start at the top of the track");
        assertEquals(viewport - thumb,
                QuestBoardLayout.thumbOffset(7, MIN_SCREEN_HEIGHT, ROW, 12),
                "the thumb does not reach the bottom of the track at the end of the list");
    }

    @Test
    void theScrollbarSitsInTheMarginAndNotInAColumn() {
        // The columns above add up to exactly the panel, so a bar with a column
        // of its own would have to be taken out of the reason or the reward. It
        // goes in the blank margin instead: inside the panel, past the button.
        assertTrue(QuestBoardLayout.SCROLLBAR_LEFT >= BUTTON_LEFT + BUTTON_WIDTH,
                "the scrollbar overlaps the row buttons");
        assertTrue(QuestBoardLayout.SCROLLBAR_LEFT + QuestBoardLayout.SCROLLBAR_WIDTH
                        < PANEL_WIDTH,
                "the scrollbar is drawn on or past the panel's own border");
    }

    /**
     * The board's memory scrolls too, at its own row height.
     *
     * <p>A town remembers more than it asks: the done face is a plain list of
     * lines at {@link CivilizationPanel#ROW} rather than a two-line notice, so it
     * fits more of them and bounds at a different count. It is the same
     * arithmetic either way, which is the point of passing the row height in.
     */
    @Test
    void theDoneFaceIsBoundedAtItsOwnRowHeight() {
        int done = CivilizationPanel.ROW;
        assertEquals(8, QuestBoardLayout.rowsThatFit(MIN_SCREEN_HEIGHT, done));
        for (int lines : new int[] {1, 5, 12}) {
            assertTrue(QuestBoardLayout.panelHeight(MIN_SCREEN_HEIGHT, done, lines)
                            <= MIN_SCREEN_HEIGHT,
                    lines + " remembered lines draw a panel off the smallest screen");
        }
        assertEquals(4, QuestBoardLayout.maxScroll(MIN_SCREEN_HEIGHT, done, 12));
    }
}
