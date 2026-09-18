package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.NightRest;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.work.Furnishings;
import com.civilization.sim.work.Signage;
import com.civilization.sim.world.SimWorld;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a town writes on its own boards.
 *
 * <p>Every sign in every town was blank, and the reason given was that the
 * drawing seam carried no text. It carries text now, and the half worth pinning
 * is this one: what the words are, that they fit, and that they are the same
 * words on the same town every time it is asked — because a board that changed
 * its mind on a reload would be worse than a board that said nothing.
 *
 * <p>What is <em>not</em> here is the writing itself. Putting a {@code SignText}
 * onto a {@code SignBlockEntity} needs a level; see the note in the changelog
 * about what is read rather than measured.
 */
class SignageTest {

    private static final SimPos CENTER = new SimPos(0, 72, 0);

    private static Settlement town(String name, SettlementStage stage, long firstStep) {
        Settlement town = new Settlement(Settlement.Id.random(), name, CENTER, 128);
        town.setCultureId(Culture.NORMAN.id());
        town.setStage(stage);
        town.setFirstStep(firstStep);
        return town;
    }

    // --- the board on the square -----------------------------------------------

    @Test
    void theBoardSaysWhoWhatAndWhen() {
        Signage.Plaque plaque = Signage.of(
                town("Millbrook", SettlementStage.VILLAGE, 0), SimWorld.SIM_INTERVAL_TICKS);
        List<String> board = Signage.board(plaque);

        assertEquals("Millbrook", board.get(0));
        assertEquals("village", board.get(1));
        assertEquals("founded day 1", board.get(2));
    }

    /**
     * A board that says anything says it on four lines, and a board with nothing
     * behind it carries no lines at all rather than four empty ones — the second
     * would be an instruction to scrub whatever is written there.
     */
    @Test
    void aboardIsFourLinesOrNoneAtAll() {
        assertEquals(Signage.LINES,
                Signage.board(new Signage.Plaque("Ashmarch", "town", 3, "Red Lion")).size());
        assertTrue(Signage.board(Signage.Plaque.NONE).isEmpty());
        assertTrue(Signage.BLANK.isEmpty());
    }

    /**
     * The stage is on the board because a settlement is a thing that becomes
     * another thing, and a player wants to know which one they have walked into.
     */
    @Test
    void theBoardFollowsTheTownAsItGrows() {
        Settlement growing = town("Ashmarch", SettlementStage.CAMP, 0);
        assertEquals("camp",
                Signage.board(Signage.of(growing, SimWorld.SIM_INTERVAL_TICKS)).get(1));

        growing.setStage(SettlementStage.TOWN);
        assertEquals("town",
                Signage.board(Signage.of(growing, SimWorld.SIM_INTERVAL_TICKS)).get(1));
    }

    // --- the founding line ---------------------------------------------------------

    /**
     * A step is not a day and a step number is not a thing to write on a board.
     * One in-game day is {@code NightRest.DAY} ticks and a step is the sim
     * interval, so a town founded a day in was founded on day two.
     */
    @Test
    void thebirthdayIsCountedInDaysRatherThanSteps() {
        long stepsPerDay = NightRest.DAY / SimWorld.SIM_INTERVAL_TICKS;

        assertEquals(1, Signage.foundedDay(0, SimWorld.SIM_INTERVAL_TICKS));
        assertEquals(1, Signage.foundedDay(stepsPerDay - 1, SimWorld.SIM_INTERVAL_TICKS));
        assertEquals(2, Signage.foundedDay(stepsPerDay, SimWorld.SIM_INTERVAL_TICKS));
        assertEquals(5, Signage.foundedDay(stepsPerDay * 4, SimWorld.SIM_INTERVAL_TICKS));
    }

    /** A town that has never stepped has no birthday, and says nothing about one. */
    @Test
    void atownThatHasNotLivedHasNoFoundingLine() {
        assertEquals(Signage.NEVER,
                Signage.foundedDay(Settlement.NOT_YET_LIVED, SimWorld.SIM_INTERVAL_TICKS));
        assertEquals("", Signage.foundedLine(Signage.NEVER));
    }

    /**
     * A world left running has towns founded in five figures, and the game does
     * not truncate an overlong line — it squeezes it, so the whole line goes
     * illegible rather than its tail going missing.
     */
    @Test
    void thefoundingLineGivesUpTheWordBeforeItGivesUpTheNumber() {
        assertEquals("founded day 999", Signage.foundedLine(999));
        assertTrue(Signage.foundedLine(999).length() <= Signage.LINE_WIDTH);

        String big = Signage.foundedLine(41203);
        assertTrue(big.length() <= Signage.LINE_WIDTH, big);
        assertTrue(big.contains("41203"), big);
    }

    // --- the post at the crossing ---------------------------------------------------

    @Test
    void asignpostNamesTheTownAndPointsAtTheHall() {
        Signage.Plaque plaque = new Signage.Plaque("Millbrook", "town", 3, "Red Lion");

        assertEquals("Millbrook", Signage.signpost(plaque, 0).get(0));
        // Furnishings faces every post toward the middle, so the board's own face
        // is the way to the hall and the compass word says the same thing.
        assertEquals("south", Signage.signpost(plaque, 0).get(2));
        assertEquals("west", Signage.signpost(plaque, 1).get(2));
        assertEquals("north", Signage.signpost(plaque, 2).get(2));
        assertEquals("east", Signage.signpost(plaque, 3).get(2));
    }

    /** Facing is a quarter turn and wraps; a post is never pointed nowhere. */
    @Test
    void afacingOutsideTheFourStillNamesADirection() {
        Signage.Plaque plaque = new Signage.Plaque("Millbrook", "town", 3, "");
        assertEquals("south", Signage.signpost(plaque, 4).get(2));
        assertEquals("north", Signage.signpost(plaque, -2).get(2));
    }

    // --- the inn --------------------------------------------------------------------

    @Test
    void theInnBoardNamesTheInnAndTheTown() {
        List<String> board =
                Signage.innBoard(new Signage.Plaque("Millbrook", "town", 3, "Red Lion"));

        assertEquals("The Red Lion", board.get(0));
        assertEquals("inn", board.get(1));
        assertEquals("Millbrook", board.get(2));
    }

    /** A people with no inn in their camp has nothing to write, and writes nothing. */
    @Test
    void anInnWithNoNameIsABlankBoard() {
        assertEquals(Signage.BLANK,
                Signage.innBoard(new Signage.Plaque("Gritmaw", "camp", 3, "")));
    }

    /**
     * Every people has inn names of their own, and every one of them fits on the
     * board with "The " in front of it. A thirteenth name that did not fit would
     * be a board reading "The Wheatshea".
     */
    @Test
    void everyPeopleHasInnNamesAndEveryOneOfThemFits() {
        for (Culture culture : Culture.all()) {
            List<String> pool = culture.innNames();
            assertFalse(pool.isEmpty(), culture.id() + " has nowhere to drink");
            for (String name : pool) {
                assertEquals(name, name.strip(), culture.id() + ": " + name);
                assertTrue(("The " + name).length() <= Signage.LINE_WIDTH,
                        culture.id() + ": \"The " + name + "\" is too wide for a board");
            }
        }
    }

    /** The peoples do not share a sign painter any more than they share a family name. */
    @Test
    void theInnsOfOnePeopleAreNotTheInnsOfAnother() {
        assertNotEquals(Culture.NORMAN.innNames(), Culture.GOBLIN.innNames());
        assertNotEquals(Culture.NORMAN.innNames(), Culture.ORC.innNames());
        assertNotEquals(Culture.BURGHER.innNames(), Culture.HIGHLAND.innNames());
    }

    /**
     * The same town drinks at the same sign every time it is asked — which is the
     * whole reason the name is drawn from the id rather than rolled: a sign is not
     * a save format either, and an inn that was the Red Lion this session and the
     * Black Swan the next is not an inn anybody would drink at twice.
     */
    @Test
    void anInnKeepsItsNameAcrossEveryAsking() {
        Settlement millbrook = town("Millbrook", SettlementStage.TOWN, 12);
        String first = Signage.of(millbrook, SimWorld.SIM_INTERVAL_TICKS).innName();
        for (int i = 0; i < 20; i++) {
            assertEquals(first,
                    Signage.of(millbrook, SimWorld.SIM_INTERVAL_TICKS).innName());
        }
        assertTrue(Culture.NORMAN.innNames().contains(first), first);
    }

    /** And two towns of one people do not all drink at the Red Lion. */
    @Test
    void neighbouringTownsDoNotAllDrinkAtTheSameSign() {
        long distinct = java.util.stream.LongStream.range(0, 200)
                .mapToObj(seed -> Signage.innNameFor(Culture.NORMAN, seed))
                .distinct()
                .count();
        assertTrue(distinct >= Culture.NORMAN.innNames().size() - 1,
                "only " + distinct + " of the pool was ever drawn");
    }

    // --- the width ------------------------------------------------------------------

    /**
     * Fifteen characters, which is ninety pixels of the default font — what
     * {@code SignBlockEntity.MAX_TEXT_LINE_WIDTH} allows in 26.2.
     */
    @Test
    void nothingAnySignSaysIsWiderThanTheSignIs() {
        Signage.Plaque longest = new Signage.Plaque(
                "Llanfairpwllgwyngyll", "homestead", 41203, "Gold Fleece");
        for (List<String> board : List.of(Signage.board(longest),
                Signage.signpost(longest, 2), Signage.innBoard(longest))) {
            assertEquals(Signage.LINES, board.size());
            for (String line : board) {
                assertTrue(line.length() <= Signage.LINE_WIDTH,
                        "\"" + line + "\" is " + line.length() + " wide");
            }
        }
    }

    @Test
    void fitNeverLeavesAStrandedSpaceOrANull() {
        assertEquals("", Signage.fit(null));
        assertEquals("", Signage.fit("   "));
        assertEquals("Ashmarch", Signage.fit("  Ashmarch  "));
        assertEquals(Signage.LINE_WIDTH, Signage.fit("a".repeat(40)).length());
    }

    // --- which piece carries which board ---------------------------------------------

    @Test
    void onlyTheThreeBoardsCarryWords() {
        Signage.Plaque plaque = new Signage.Plaque("Millbrook", "town", 3, "Red Lion");

        assertEquals(Signage.board(plaque),
                Signage.linesFor(Furnishings.Piece.SQUARE, plaque, 0));
        assertEquals(Signage.signpost(plaque, 2),
                Signage.linesFor(Furnishings.Piece.SIGNPOST, plaque, 2));
        assertEquals(Signage.innBoard(plaque),
                Signage.linesFor(Furnishings.Piece.INN_SIGN, plaque, 0));

        for (Furnishings.Piece piece : Furnishings.Piece.values()) {
            if (piece == Furnishings.Piece.SQUARE || piece == Furnishings.Piece.SIGNPOST
                    || piece == Furnishings.Piece.INN_SIGN) {
                continue;
            }
            assertEquals(Signage.BLANK, Signage.linesFor(piece, plaque, 0),
                    piece + " has writing on it");
        }
    }

    // --- the one board that is not about the town ----------------------------

    /**
     * A headstone says who lies under it, what they did, and when.
     *
     * <p>{@code Settlement.Grave.epitaph} is the same sentence on one line —
     * "Ada Baker, Farmer" — and seventeen characters do not go on a fifteen-glyph
     * line without the game squeezing them. So the epitaph is set <em>across</em>
     * the board instead of onto it.
     */
    @Test
    void aHeadstoneSaysWhoLiesUnderIt() {
        List<String> lines = Signage.headstone(new Settlement.Grave(
                "Ada Baker", com.civilization.sim.person.Profession.FARMER, 11));
        assertEquals("Ada Baker", lines.get(0));
        assertEquals("Farmer", lines.get(1));
        assertEquals("died day 12", lines.get(2),
                "day one, not day zero: the first day of a world is Day 1"
                        + " everywhere a player is shown one");
        assertEquals(Signage.LINES, lines.size());
        for (String line : lines) {
            assertTrue(line.length() <= Signage.LINE_WIDTH,
                    "\"" + line + "\" is " + line.length() + " glyphs, and a line"
                            + " that overflows is squeezed rather than cut");
        }
    }

    /** Somebody who never had a trade gets a name and a date and no insult. */
    @Test
    void anIdlerGetsNoTradeLine() {
        assertEquals("", Signage.headstone(new Settlement.Grave(
                "Ada Baker", com.civilization.sim.person.Profession.IDLER, 3)).get(1),
                "\"Ada Baker / Idler\" is not an epitaph, it is an insult");
    }

    /** A very long name is cut to what a board draws rather than squeezed into it. */
    @Test
    void aLongNameIsCutToTheBoard() {
        List<String> lines = Signage.headstone(new Settlement.Grave(
                "Bartholomew Fitzwilliam",
                com.civilization.sim.person.Profession.LUMBERJACK, 0));
        assertEquals(Signage.LINE_WIDTH, lines.get(0).length());
        assertTrue("Bartholomew Fitzwilliam".startsWith(lines.get(0)));
    }

    /**
     * A grave asks nothing of the plaque, which is what makes it the one board
     * legible in a town with no name recorded.
     */
    @Test
    void aHeadstoneNeedsNoPlaque() {
        Settlement.Grave whose = new Settlement.Grave(
                "Ada Baker", com.civilization.sim.person.Profession.FARMER, 4);
        assertEquals(Signage.headstone(whose),
                Signage.linesFor(Furnishings.Piece.GRAVE, Signage.Plaque.NONE, 0, whose));
        assertEquals(Signage.BLANK,
                Signage.linesFor(Furnishings.Piece.GRAVE, Signage.Plaque.NONE, 0, null),
                "a stone the roster has no name for keeps whatever is cut into it,"
                        + " and an empty line list is what leaves a board alone");
    }

    /**
     * A plan drawn without a town — which is what every size test does — writes
     * nothing at all rather than writing four empty lines, so those tests go on
     * seeing exactly the blocks they always saw.
     */
    @Test
    void apieceWithNoTownBehindItIsBlank() {
        assertEquals(Signage.BLANK,
                Signage.linesFor(Furnishings.Piece.SQUARE, Signage.Plaque.NONE, 0));
        assertTrue(Signage.Plaque.NONE.isBlank());
    }
}
