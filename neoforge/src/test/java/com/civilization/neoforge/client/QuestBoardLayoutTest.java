package com.civilization.neoforge.client;

import org.junit.jupiter.api.Test;

import static com.civilization.neoforge.client.QuestBoardLayout.BUTTON_LEFT;
import static com.civilization.neoforge.client.QuestBoardLayout.BUTTON_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.COLUMN_GAP;
import static com.civilization.neoforge.client.QuestBoardLayout.FOOTER;
import static com.civilization.neoforge.client.QuestBoardLayout.FOOTER_LINES;
import static com.civilization.neoforge.client.QuestBoardLayout.FOOTER_TEXT_WIDTH;
import static com.civilization.neoforge.client.QuestBoardLayout.ICON;
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
}
