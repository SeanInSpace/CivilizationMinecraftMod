package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Leisure;
import com.civilization.sim.person.Profession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who goes inside when it rains, and who does not.
 *
 * <p>Here for the reason the rest of {@link Leisure} is here: "a wet farmer has
 * nothing to be standing in a field for and a wet guard has everything to be
 * standing on a wall for" is a list, and a list that lived in a steering loop
 * would be a list nobody could check. What is <em>not</em> here is reading the
 * weather off a level, which is four lines of platform and one comment about
 * heightmaps; see {@code Pastimes.skyOver}.
 *
 * <p>The load-bearing test in this file is
 * {@link #theWeatherCannotReachTheBooks}. Everything else says the rain does the
 * pretty thing; that one says it does nothing else — which is the promise that
 * decides whether this feature was allowed to be written at all.
 */
class RainShelterTest {

    private static final UUID SOMEBODY =
            UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    /** A town with somewhere dry and somewhere wet to be. */
    private static List<Leisure.Place> wholeTown() {
        return List.of(
                new Leisure.Place(Leisure.Pastime.SQUARE, new SimPos(0, 64, 0)),
                new Leisure.Place(Leisure.Pastime.WELL, new SimPos(4, 64, 0)),
                new Leisure.Place(Leisure.Pastime.INN, new SimPos(0, 64, 12)),
                new Leisure.Place(Leisure.Pastime.HEARTH, new SimPos(-8, 64, 0)),
                new Leisure.Place(Leisure.Pastime.DOORWAY, new SimPos(-8, 64, 8)),
                new Leisure.Place(Leisure.Pastime.BENCH, new SimPos(3, 64, 3)),
                new Leisure.Place(Leisure.Pastime.FENCE, new SimPos(12, 64, 2)),
                new Leisure.Place(Leisure.Pastime.FARM_GATE, new SimPos(30, 64, 30)));
    }

    /** A town with everything to work at. */
    private static Leisure.Openings everything() {
        return new Leisure.Openings(false, true, true, true, true, true, true, true, true);
    }

    // --- who shelters ------------------------------------------------------------

    @Test
    void theOutdoorTradesComeOffTheRosterInTheRain() {
        for (Profession what : List.of(Profession.FARMER, Profession.LUMBERJACK,
                Profession.MINER, Profession.SHEPHERD, Profession.FORAGER)) {
            assertTrue(Leisure.hasWork(what, Leisure.Hour.DAY, Leisure.Sky.FAIR,
                            false, everything()),
                    what + " has nothing to do on a dry working day, which is a"
                            + " town standing about in its own square");
            assertFalse(Leisure.hasWork(what, Leisure.Hour.DAY, Leisure.Sky.WET,
                            false, everything()),
                    what + " is still out in it");
        }
    }

    /**
     * A builder on a site is the one outdoor trade the town has already claimed
     * with {@code handsOnAWork}, so the weather has to be asked before that and
     * not after it.
     */
    @Test
    void aBuilderOnAnOpenSiteComesInToo() {
        Leisure.Openings handed =
                new Leisure.Openings(true, false, false, false, false, false,
                        false, false, false);
        assertTrue(Leisure.hasWork(Profession.BUILDER, Leisure.Hour.DAY,
                Leisure.Sky.FAIR, false, handed));
        assertFalse(Leisure.hasWork(Profession.BUILDER, Leisure.Hour.DAY,
                        Leisure.Sky.WET, false, handed),
                "a wall goes on rising in a downpour with nobody under a roof,"
                        + " which is the one case the site claims somebody first");
        assertFalse(Leisure.hasWork(Profession.PIONEER, Leisure.Hour.DAY,
                Leisure.Sky.WET, false, handed));
    }

    @Test
    void theIndoorTradesAreUnaffected() {
        for (Profession what : List.of(Profession.SMITH, Profession.MILLER,
                Profession.CARPENTER, Profession.TRADER)) {
            assertEquals(
                    Leisure.hasWork(what, Leisure.Hour.DAY, Leisure.Sky.FAIR,
                            false, everything()),
                    Leisure.hasWork(what, Leisure.Hour.DAY, Leisure.Sky.WET,
                            false, everything()),
                    what + " downed tools over the weather, and its roof is the"
                            + " whole reason the building was raised");
        }
    }

    /**
     * The watch keeps the wall in the rain exactly as it keeps it in the dark.
     *
     * <p>A guard never answers {@code hasWork} in the affirmative and never
     * takes a pastime from the offer — his is his own post — so what has to be
     * pinned is that nothing here gave the rain a way to move him.
     */
    @Test
    void theWatchStaysOut() {
        assertFalse(Leisure.isOutdoorTrade(Profession.GUARD),
                "a wall nobody is on because it was drizzling is not a wall");
        assertEquals(Leisure.Pastime.POST, Leisure.forGuard());
        assertTrue(Leisure.stillFits(Leisure.Pastime.POST, Leisure.Hour.DAY,
                        Leisure.Sky.WET),
                "the sentry was taken off his post by the weather");
    }

    // --- where they go ------------------------------------------------------------

    @Test
    void everyPastimeOfferedInTheRainIsUnderARoof() {
        for (Leisure.Hour hour : Leisure.Hour.values()) {
            for (int tick = 0; tick < 200; tick++) {
                Leisure.Place taken = Leisure.choose(SOMEBODY, tick, hour,
                        Leisure.Sky.WET, wholeTown());
                assertNotNull(taken, "nowhere to be at " + hour + " in the rain,"
                        + " in a town with an inn and a hearth in it");
                assertTrue(Leisure.underCover(taken.what()),
                        hour + ": somebody was sent to the " + taken.what()
                                + " in the rain");
            }
        }
    }

    /**
     * The table's half of the rule, said exhaustively rather than by sampling.
     *
     * <p>{@link #everyPastimeOfferedInTheRainIsUnderARoof} says that a
     * particular town's offer never yields an open-air answer, which is a
     * statement about that offer. This says the stronger thing the platform is
     * entitled to rely on: every pastime {@link Leisure#underCover} rejects is
     * refused outright in the wet, at every hour, whatever else is or is not on
     * the table. Written as its own test because the platform now sieves its
     * offer through {@code Leisure.shelterIn} on the strength of it.
     */
    @Test
    void everyOpenAirPastimeIsRefusedInTheWetAtEveryHour() {
        for (Leisure.Pastime what : Leisure.Pastime.values()) {
            if (Leisure.underCover(what)) {
                continue;
            }
            for (Leisure.Hour hour : Leisure.Hour.values()) {
                assertFalse(Leisure.stillFits(what, hour, Leisure.Sky.WET),
                        what + " still fits at " + hour + " in the rain, so"
                                + " somebody is standing at one");
                // And the same said through the chooser, which is the only way
                // anybody is ever sent to one: an offer of nothing but this
                // pastime weighs nothing at all in the wet.
                assertNull(Leisure.choose(SOMEBODY, 3L, hour, Leisure.Sky.WET,
                                List.of(new Leisure.Place(what, new SimPos(1, 64, 1)))),
                        what + " was chosen at " + hour + " in the rain");
            }
        }
    }

    /**
     * An offer with no roof in it yields no shelter, rather than an open-air
     * place wearing a sheltered name.
     *
     * <p>This is the rain fault stated as a rule. The platform used to put a
     * {@code DOORWAY} on the table whenever the town had families in it, housed
     * or not, with the middle of the market square as its position — and a
     * doorway is the heaviest thing in the wet table, weighing more at
     * {@link Leisure.Hour#DAY} than the inn and the hearth together. So a town
     * whose families were not yet housed offered a porch it did not have, and
     * every settler the rain took off the rows was walked into the open to
     * stand in it: the census read cover flat at 14 while the count under open
     * sky went from 9 to 12, and the farm rows emptied from 3 to 0. Nothing
     * here could have caught that, because the fault was in the position and
     * not the kind — so what is pinned instead is the sieve the platform now
     * runs its offer through, and the fact that an empty sieve is an empty
     * answer rather than a fallback.
     */
    @Test
    void anOfferWithNoRoofInItYieldsNoShelterRatherThanAnOpenAirOne() {
        List<Leisure.Place> outdoorsOnly = List.of(
                new Leisure.Place(Leisure.Pastime.SQUARE, new SimPos(0, 64, 0)),
                new Leisure.Place(Leisure.Pastime.WELL, new SimPos(4, 64, 0)),
                new Leisure.Place(Leisure.Pastime.BENCH, new SimPos(3, 64, 3)),
                new Leisure.Place(Leisure.Pastime.FENCE, new SimPos(12, 64, 2)),
                new Leisure.Place(Leisure.Pastime.FARM_GATE, new SimPos(30, 64, 30)));
        assertTrue(Leisure.shelterIn(outdoorsOnly).isEmpty(),
                "a town of open ground found a roof in itself");
        for (Leisure.Hour hour : Leisure.Hour.values()) {
            assertNull(Leisure.restFor(SOMEBODY, 11L, hour, Leisure.Sky.WET,
                            Leisure.shelterIn(outdoorsOnly)),
                    "somewhere dry was invented at " + hour + " out of a square,"
                            + " a well, a bench, a fence and a field gate");
        }

        // And the sieve keeps what is really there, in the order it was given,
        // so nothing that was on offer is lost by passing through it.
        assertEquals(
                List.of(new Leisure.Place(Leisure.Pastime.INN, new SimPos(0, 64, 12)),
                        new Leisure.Place(Leisure.Pastime.HEARTH, new SimPos(-8, 64, 0)),
                        new Leisure.Place(Leisure.Pastime.DOORWAY, new SimPos(-8, 64, 8))),
                Leisure.shelterIn(wholeTown()));
        assertTrue(Leisure.shelterIn(List.of()).isEmpty());
        assertTrue(Leisure.shelterIn(null).isEmpty());

        // A town with one roof in it still has an answer, which is the third
        // thing the rule needs: the sieve must not be a way of answering null.
        for (Leisure.Pastime roof : List.of(Leisure.Pastime.INN,
                Leisure.Pastime.HEARTH, Leisure.Pastime.DOORWAY)) {
            List<Leisure.Place> one = new ArrayList<>(outdoorsOnly);
            one.add(new Leisure.Place(roof, new SimPos(-2, 64, -2)));
            Leisure.Rest rest = Leisure.restFor(SOMEBODY, 11L, Leisure.Hour.DAY,
                    Leisure.Sky.WET, Leisure.shelterIn(one));
            assertNotNull(rest, "a town with a " + roof + " in it sent nobody to it");
            assertEquals(roof, rest.what());
            assertEquals(new SimPos(-2, 64, -2), rest.where());
        }
    }

    // --- the cap on getting there ----------------------------------------------------

    /**
     * Walking out of the rain is not rationed the way strolling to the well is.
     *
     * <p>The second half of the fault, and the reason the first half was not
     * the whole of it. Rain does not arrive one settler at a time: it arrives
     * for the town, and {@link Leisure#hasWork} takes every outdoor trade off
     * the roster in the same pass. A town with a dozen people in its fields
     * therefore wants a dozen doorways on one tick, {@link
     * Leisure#WALKS_AT_ONCE} admits six, and the other six keep standing in the
     * rows — which is the count under open sky going up rather than down.
     *
     * <p>What is asserted is that the two caps are distinct, that the looser one
     * is reachable only by a walk that is genuinely a run for cover, and that
     * the cap still exists: an unbounded answer here would be a town handing
     * vanilla eighty routes on the tick the weather turned.
     */
    @Test
    void aRunForCoverIsCappedMoreLooselyThanAStrollToTheWell() {
        assertTrue(Leisure.SHELTER_WALKS_AT_ONCE > Leisure.WALKS_AT_ONCE,
                "the rain cap is no looser than the fair-weather one, so half a"
                        + " town is still standing in the wet");
        assertTrue(Leisure.SHELTER_WALKS_AT_ONCE < Integer.MAX_VALUE,
                "the cap was removed rather than widened; it is there to bound"
                        + " pathfinding and a downpour is when that matters most");

        for (Leisure.Pastime what : Leisure.Pastime.values()) {
            // Fair weather is untouched, for every kind. Nothing about a dry day
            // changed, and a dry day is when nearly every pastime is taken.
            assertFalse(Leisure.isShelterRun(what, Leisure.Sky.FAIR));
            assertEquals(Leisure.WALKS_AT_ONCE,
                    Leisure.walkCap(what, Leisure.Sky.FAIR),
                    what + " got the rain's cap on a dry day");
            assertEquals(Leisure.WALKS_AT_ONCE, Leisure.walkCap(what, null),
                    what + " got the rain's cap with no sky read at all");

            // And in the wet only the roofs are let through, which is the point
            // of asking the question of the walk rather than of the weather: a
            // walk to the square can never reach the looser cap even if some
            // future offer contrives to put one on the table in a downpour.
            assertEquals(Leisure.underCover(what),
                    Leisure.isShelterRun(what, Leisure.Sky.WET));
            assertEquals(Leisure.underCover(what)
                            ? Leisure.SHELTER_WALKS_AT_ONCE : Leisure.WALKS_AT_ONCE,
                    Leisure.walkCap(what, Leisure.Sky.WET),
                    what + " is capped wrongly in the rain");
        }
    }

    @Test
    void aTownWithNoRoofOnOfferSendsNobodyAnywhere() {
        List<Leisure.Place> outdoorsOnly = new ArrayList<>();
        for (Leisure.Place place : wholeTown()) {
            if (!Leisure.underCover(place.what())) {
                outdoorsOnly.add(place);
            }
        }
        assertNull(Leisure.choose(SOMEBODY, 10L, Leisure.Hour.DAY, Leisure.Sky.WET,
                        outdoorsOnly),
                "a camp of four huts in a downpour invented a shelter it has not"
                        + " got; its people should stand where they were standing");
    }

    /** Somebody on a bench in the square when it starts gets up and walks in. */
    @Test
    void theRainEndsAPastimeAlreadyUnderWay() {
        assertTrue(Leisure.stillFits(Leisure.Pastime.WELL, Leisure.Hour.DAY,
                Leisure.Sky.FAIR));
        assertFalse(Leisure.stillFits(Leisure.Pastime.WELL, Leisure.Hour.DAY,
                        Leisure.Sky.WET),
                "a settler sat at the well through a thunderstorm");
        assertTrue(Leisure.stillFits(Leisure.Pastime.INN, Leisure.Hour.DAY,
                        Leisure.Sky.WET),
                "and one at the inn was turned out of it");
    }

    // --- what still outranks it -----------------------------------------------------

    /**
     * The bell, danger, sleep and an errand all beat the weather, because they
     * beat leisure and the weather only ever decides which leisure.
     */
    @Test
    void theAlarmOutranksTheWeather() {
        Leisure.Idleness called = new Leisure.Idleness(
                true, false, true, false, false, false, false);
        assertFalse(Leisure.mayRest(called),
                "a town in arms went to the inn because it was raining");
        Leisure.Idleness threatened = new Leisure.Idleness(
                true, false, false, true, false, false, false);
        assertFalse(Leisure.mayRest(threatened));
        // And the ranking is not reachable from the weather at all: mayRest is
        // the gate, it is asked first, and it has no sky in it.
        Leisure.Idleness free = new Leisure.Idleness(
                true, false, false, false, false, false, false);
        assertTrue(Leisure.mayRest(free));
    }

    // --- and the whole point --------------------------------------------------------

    /**
     * The weather cannot reach the books.
     *
     * <p>Not a measurement but a proof by construction, and the construction is
     * the argument: every method the rain rule touches is static, takes only its
     * arguments, and hands back a value. Driving the whole of it with
     * {@link Leisure.Sky#WET} for a great many steps therefore mutates nothing
     * that a ledger could read — there is nothing for it to mutate — so a
     * watched town in a downpour and an unwatched one keep identical books by
     * construction rather than by luck.
     *
     * <p>Said as a test rather than as a comment because the thing that would
     * break it is somebody giving this class a field. The run below takes the
     * same decision twice, a thousand steps apart in the tick stream, with the
     * two skies interleaved; if any of it had accumulated state the fair answers
     * would drift away from the fair answers taken on their own.
     */
    @Test
    void theWeatherCannotReachTheBooks() {
        List<Leisure.Place> town = wholeTown();
        List<Leisure.Place> fairAlone = new ArrayList<>();
        for (long tick = 0; tick < 1000; tick++) {
            fairAlone.add(Leisure.choose(SOMEBODY, tick, Leisure.Hour.DAY,
                    Leisure.Sky.FAIR, town));
        }
        List<Leisure.Place> fairInterleaved = new ArrayList<>();
        for (long tick = 0; tick < 1000; tick++) {
            Leisure.choose(SOMEBODY, tick, Leisure.Hour.DAY, Leisure.Sky.WET, town);
            Leisure.hasWork(Profession.FARMER, Leisure.Hour.DAY, Leisure.Sky.WET,
                    false, everything());
            fairInterleaved.add(Leisure.choose(SOMEBODY, tick, Leisure.Hour.DAY,
                    Leisure.Sky.FAIR, town));
        }
        assertEquals(fairAlone, fairInterleaved,
                "a thousand steps of rain changed what a dry town does, so the"
                        + " rule is keeping state and could be keeping a ledger");
    }

    /**
     * And the same said about the one place it could have leaked: the old
     * signatures. Everything that was calling {@code Leisure} before this unit
     * still gets the fair-weather answer, so no caller was quietly given
     * weather it never asked for.
     */
    @Test
    void theOlderSignaturesStillMeanFairWeather() {
        assertEquals(Leisure.hasWork(Profession.FARMER, Leisure.Hour.DAY, false,
                        everything()),
                Leisure.hasWork(Profession.FARMER, Leisure.Hour.DAY,
                        Leisure.Sky.FAIR, false, everything()));
        assertEquals(Leisure.choose(SOMEBODY, 7L, Leisure.Hour.DAY, wholeTown()),
                Leisure.choose(SOMEBODY, 7L, Leisure.Hour.DAY, Leisure.Sky.FAIR,
                        wholeTown()));
        assertEquals(Leisure.stillFits(Leisure.Pastime.WELL, Leisure.Hour.DAY),
                Leisure.stillFits(Leisure.Pastime.WELL, Leisure.Hour.DAY,
                        Leisure.Sky.FAIR));
    }

    @Test
    void theSkyIsReadOffTheTwoFlagsALevelAnswersWith() {
        assertEquals(Leisure.Sky.FAIR, Leisure.skyOf(false, false));
        assertEquals(Leisure.Sky.WET, Leisure.skyOf(true, false));
        assertEquals(Leisure.Sky.WET, Leisure.skyOf(false, true));
        assertEquals(Leisure.Sky.WET, Leisure.skyOf(true, true));
    }
}
