package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Leisure;
import com.civilization.sim.person.Profession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four people in a town who never sat down.
 *
 * <p>The smith, the miller, the carpenter and the king. Not because anybody
 * decided they should not: the rule that says whether somebody is working was a
 * switch in the steering loop whose last arm read {@code default -> true}, so
 * every trade nobody had thought about was permanently at work and the town's
 * most conspicuous figures were its stiffest. The rule is here now, where it can
 * be read, and every one of them answers off a ledger like everybody else.
 *
 * <p>The half that must not move is the other direction: a forge with iron in it
 * still keeps its smith. A pastime never outbids work.
 */
class TradeLeisureTest {

    private static final SimPos HALL = new SimPos(4, 72, 4);
    private static final SimPos SQUARE = new SimPos(0, 72, 0);
    private static final SimPos INN = new SimPos(-12, 72, 8);
    private static final UUID SOMEBODY =
            UUID.fromString("00000000-0000-4000-8000-00000000000f");

    /** A town where nothing at all is going on. */
    private static Leisure.Openings quiet() {
        return new Leisure.Openings(false, false, false, false, false, false,
                false, false, false);
    }

    /** The same town with one trade's work in front of it. */
    private static Leisure.Openings withForge() {
        return new Leisure.Openings(false, false, false, false, false, false,
                true, false, false);
    }

    private static Leisure.Openings withMill() {
        return new Leisure.Openings(false, false, false, false, false, false,
                false, true, false);
    }

    private static Leisure.Openings withBench() {
        return new Leisure.Openings(false, false, false, false, false, false,
                false, false, true);
    }

    // --- the trades that keep a building --------------------------------------------

    @Test
    void asmithAtAColdForgeIsSomebodyWithAnAfternoon() {
        assertFalse(Leisure.hasWork(Profession.SMITH, Leisure.Hour.DAY, false, quiet()));
    }

    @Test
    void andASmithWithIronInFrontOfHimIsNot() {
        assertTrue(Leisure.hasWork(Profession.SMITH, Leisure.Hour.DAY, false, withForge()));
    }

    @Test
    void amillerWithAnEmptyHopperMayGoAndSitDown() {
        assertFalse(Leisure.hasWork(Profession.MILLER, Leisure.Hour.DAY, false, quiet()));
        assertTrue(Leisure.hasWork(Profession.MILLER, Leisure.Hour.DAY, false, withMill()));
    }

    @Test
    void acarpenterWithNothingQueuedMayToo() {
        assertFalse(Leisure.hasWork(Profession.CARPENTER, Leisure.Hour.DAY, false, quiet()));
        assertTrue(Leisure.hasWork(Profession.CARPENTER, Leisure.Hour.DAY, false,
                withBench()));
    }

    /**
     * And no trade's work is another's. A forge with iron in it does not put the
     * miller back to work, which is the failure the old {@code default} arm was:
     * one answer for everybody who had not been thought about.
     */
    @Test
    void oneTradesWorkIsNotAnothers() {
        assertFalse(Leisure.hasWork(Profession.MILLER, Leisure.Hour.DAY, false, withForge()));
        assertFalse(Leisure.hasWork(Profession.CARPENTER, Leisure.Hour.DAY, false,
                withForge()));
        assertFalse(Leisure.hasWork(Profession.SMITH, Leisure.Hour.DAY, false, withMill()));
    }

    // --- the king ---------------------------------------------------------------------

    /**
     * A king does no work — that is what a king is, and the staffing table says
     * so by leaving him out of it. The shaman is the same case in a camp.
     */
    @Test
    void thekingIsIdleByRightAndSoIsTheShaman() {
        for (Leisure.Openings busy : List.of(quiet(), withForge(), withMill(),
                withBench())) {
            assertFalse(Leisure.hasWork(Profession.KING, Leisure.Hour.DAY, false, busy));
            assertFalse(Leisure.hasWork(Profession.SHAMAN, Leisure.Hour.DAY, false, busy));
        }
    }

    @Test
    void akingsOfferIsHisHallTheSquareAndTheInn() {
        List<Leisure.Place> town = List.of(
                new Leisure.Place(Leisure.Pastime.SQUARE, SQUARE),
                new Leisure.Place(Leisure.Pastime.WELL, SQUARE),
                new Leisure.Place(Leisure.Pastime.INN, INN),
                new Leisure.Place(Leisure.Pastime.BENCH, SQUARE),
                new Leisure.Place(Leisure.Pastime.FARM_GATE, SQUARE));
        List<Leisure.Place> his = Leisure.forKing(HALL, town);

        assertEquals(Leisure.KING_HALL_SHARE + 2, his.size());
        assertTrue(his.stream().anyMatch(p -> p.what() == Leisure.Pastime.SQUARE));
        assertTrue(his.stream().anyMatch(p -> p.what() == Leisure.Pastime.INN));
        // Not the well, not a bench, not the field gate. A king does not lean on
        // a farm gate.
        assertFalse(his.stream().anyMatch(p -> p.what() == Leisure.Pastime.WELL));
        assertFalse(his.stream().anyMatch(p -> p.what() == Leisure.Pastime.BENCH));
        assertFalse(his.stream().anyMatch(p -> p.what() == Leisure.Pastime.FARM_GATE));
    }

    @Test
    void andHisHallIsWhereHeMostlyIs() {
        List<Leisure.Place> his = Leisure.forKing(HALL,
                List.of(new Leisure.Place(Leisure.Pastime.SQUARE, SQUARE)));

        long hall = his.stream()
                .filter(p -> p.what() == Leisure.Pastime.DOORWAY && p.where().equals(HALL))
                .count();
        assertEquals(Leisure.KING_HALL_SHARE, hall);

        int atTheHall = 0;
        for (long tick = 0; tick < 400; tick++) {
            Leisure.Place taken = Leisure.choose(SOMEBODY, tick, Leisure.Hour.DAY, his);
            assertNotNull(taken);
            if (taken.where().equals(HALL)) {
                atTheHall++;
            }
        }
        assertTrue(atTheHall > 200, "the king was at his own hall only " + atTheHall
                + " times in 400");
    }

    /** A camp with no hall at all still offers its chieftain the middle of it. */
    @Test
    void akingWithNoHallStillHasSomewhereToBe() {
        List<Leisure.Place> his = Leisure.forKing(null,
                List.of(new Leisure.Place(Leisure.Pastime.SQUARE, SQUARE)));
        assertEquals(1, his.size());
        assertEquals(Leisure.Pastime.SQUARE, his.getFirst().what());
    }

    /** And a king with nothing on offer is simply a king who stands where he is. */
    @Test
    void anOfferOfNothingIsStillAnOfferOfNothing() {
        assertTrue(Leisure.forKing(null, List.of()).isEmpty());
        assertTrue(Leisure.forKing(null, null).isEmpty());
    }

    // --- what did not change -----------------------------------------------------------

    /** The hour still outranks every trade: nobody works after the curfew. */
    @Test
    void noTradeWorksOutsideTheWorkingDay() {
        Leisure.Openings everything = new Leisure.Openings(true, true, true, true,
                true, true, true, true, true);
        for (Profession what : Profession.values()) {
            assertFalse(Leisure.hasWork(what, Leisure.Hour.EVENING, false, everything),
                    what + " was still at it in the evening");
            assertFalse(Leisure.hasWork(what, Leisure.Hour.NIGHT, false, everything),
                    what + " was still at it at night");
        }
    }

    /** And somebody too weak to work is somebody sitting down, whatever their trade. */
    @Test
    void thehungryAreOffTheJobWhateverTheirTrade() {
        Leisure.Openings everything = new Leisure.Openings(true, true, true, true,
                true, true, true, true, true);
        for (Profession what : Profession.values()) {
            assertFalse(Leisure.hasWork(what, Leisure.Hour.DAY, true, everything), what.name());
        }
    }

    /**
     * Hands already on a public work outrank every trade below them, which is how
     * a smith conscripted onto a build stops being a smith for the afternoon.
     */
    @Test
    void handsAlreadyOnAWorkBeatEveryTrade() {
        Leisure.Openings handed = new Leisure.Openings(true, false, false, false,
                false, false, false, false, false);
        for (Profession what : Profession.values()) {
            assertTrue(Leisure.hasWork(what, Leisure.Hour.DAY, false, handed), what.name());
        }
    }

    /** The trades that were already answering off a ledger go on doing so. */
    @Test
    void theOldAnswersAreUnchanged() {
        assertFalse(Leisure.hasWork(Profession.IDLER, Leisure.Hour.DAY, false, quiet()));
        assertFalse(Leisure.hasWork(Profession.BUILDER, Leisure.Hour.DAY, false, quiet()));
        assertFalse(Leisure.hasWork(Profession.GUARD, Leisure.Hour.DAY, false, quiet()));
        assertFalse(Leisure.hasWork(Profession.FARMER, Leisure.Hour.DAY, false, quiet()));

        Leisure.Openings fields = new Leisure.Openings(false, true, false, false,
                false, false, false, false, false);
        assertTrue(Leisure.hasWork(Profession.FARMER, Leisure.Hour.DAY, false, fields));
    }
}
