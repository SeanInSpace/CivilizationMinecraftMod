package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Curfew;
import com.civilization.sim.person.Leisure;
import com.civilization.sim.person.NightRest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What people do with an afternoon.
 *
 * <p>The whole of this unit's arithmetic is here, which is the point of the
 * arithmetic being in {@code common} at all: whether a working farmer can be
 * pulled off his field, whether a guard leaves his post during a raid, and
 * whether the evening actually sends anybody to the inn are all questions that
 * decide whether the feature is a bug, and none of them should need a world to
 * answer.
 *
 * <p>What is <em>not</em> here is everything the platform does with the answer —
 * walking there, sitting down, the smoke and the bell. Those need a level; see
 * the note in the changelog about what is untested.
 */
class LeisureTest {

    /** A person, for the hash to chew on. Fixed, so a failure is reproducible. */
    private static final UUID SOMEBODY = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static final UUID SOMEBODY_ELSE =
            UUID.fromString("00000000-0000-4000-8000-000000000002");

    /** A town with one of everything, so the chooser always has a full hand. */
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

    /** Somebody with nothing whatever to do. */
    private static Leisure.Idleness freeOf(boolean guard) {
        return new Leisure.Idleness(true, guard, false, false, false, false, false);
    }

    // --- who gets one ------------------------------------------------------------

    @Test
    void aWorkingPersonIsNeverGivenAPastime() {
        Leisure.Idleness atWork = new Leisure.Idleness(
                true, false, false, false, false, false, true);
        assertFalse(Leisure.mayRest(atWork),
                "a farmer on his own field is not somebody with an afternoon");
    }

    @Test
    void theBellOutranksTheWell() {
        Leisure.Idleness called = new Leisure.Idleness(
                true, false, true, false, false, false, false);
        assertFalse(Leisure.mayRest(called));
    }

    @Test
    void guardsOnAlarmAreNeverGivenOne() {
        // The watch's "called" is the town being up in arms at all, which is how
        // the platform asks it: Alarm.callsIn is false for a guard by design,
        // because the watch goes toward the trouble rather than indoors.
        Leisure.Idleness onAlarm = new Leisure.Idleness(
                true, true, true, false, false, false, false);
        assertFalse(Leisure.mayRest(onAlarm),
                "a sentry does not lean on his post through a raid");
    }

    @Test
    void aQuietGuardLeansOnHisOwnPostAndNowhereElse() {
        assertTrue(Leisure.mayRest(freeOf(true)));
        assertSame(Leisure.Pastime.POST, Leisure.forGuard(),
                "a guard who strolled to the inn would not be off duty, he would "
                        + "be off the wall");
    }

    @Test
    void nobodyUnwatchedHasAnAfternoon() {
        Leisure.Idleness bodiless = new Leisure.Idleness(
                false, false, false, false, false, false, false);
        assertFalse(Leisure.mayRest(bodiless),
                "leisure exists only where there is a body, so it must never be "
                        + "worth anything");
    }

    @Test
    void sleepingDangerAndErrandsAllOutrankIt() {
        assertFalse(Leisure.mayRest(new Leisure.Idleness(
                true, false, false, true, false, false, false)), "in danger");
        assertFalse(Leisure.mayRest(new Leisure.Idleness(
                true, false, false, false, true, false, false)), "asleep");
        assertFalse(Leisure.mayRest(new Leisure.Idleness(
                true, false, false, false, false, true, false)), "on an errand");
    }

    // --- the hour -----------------------------------------------------------------

    @Test
    void theEveningIsExactlyTheCurfewsEvening() {
        long lead = Curfew.LEAD_TICKS;
        assertEquals(Leisure.Hour.DAY, Leisure.hourOf(0, lead));
        assertEquals(Leisure.Hour.DAY, Leisure.hourOf(NightRest.DUSK - lead - 1, lead));
        assertEquals(Leisure.Hour.EVENING, Leisure.hourOf(NightRest.DUSK - lead, lead));
        assertEquals(Leisure.Hour.EVENING, Leisure.hourOf(NightRest.DUSK - 1, lead));
        assertEquals(Leisure.Hour.NIGHT, Leisure.hourOf(NightRest.DUSK, lead));
    }

    // --- choosing -------------------------------------------------------------------

    @Test
    void theChooserIsDeterministic() {
        for (long tick = 0; tick < 500; tick += 37) {
            Leisure.Place once = Leisure.choose(SOMEBODY, tick, Leisure.Hour.DAY, wholeTown());
            Leisure.Place again = Leisure.choose(SOMEBODY, tick, Leisure.Hour.DAY, wholeTown());
            assertEquals(once, again, "the same person at the same tick");
            assertEquals(Leisure.ticksFor(SOMEBODY, tick), Leisure.ticksFor(SOMEBODY, tick));
        }
    }

    @Test
    void twoPeopleAtTheSameMomentDoNotMoveAsAFlock() {
        int apart = 0;
        for (long tick = 0; tick < 400; tick += 20) {
            Leisure.Place mine = Leisure.choose(SOMEBODY, tick, Leisure.Hour.DAY, wholeTown());
            Leisure.Place yours =
                    Leisure.choose(SOMEBODY_ELSE, tick, Leisure.Hour.DAY, wholeTown());
            if (!mine.equals(yours)) {
                apart++;
            }
        }
        assertTrue(apart >= 10,
                "two settlers who always chose the same thing would be a flock, not "
                        + "a town; they differed on " + apart + " of 20 moments");
    }

    @Test
    void oneDayOfAfternoonsIsVaried() {
        Map<Leisure.Pastime, Integer> taken = dayOf(Leisure.Hour.DAY);
        assertTrue(taken.size() >= 4,
                "a day that offered eight pastimes and produced " + taken.size()
                        + " is a day with a habit, not a day");
    }

    @Test
    void theEveningPrefersTheInn() {
        Map<Leisure.Pastime, Integer> evening = dayOf(Leisure.Hour.EVENING);
        int inn = evening.getOrDefault(Leisure.Pastime.INN, 0);
        for (Map.Entry<Leisure.Pastime, Integer> other : evening.entrySet()) {
            if (other.getKey() == Leisure.Pastime.INN) {
                continue;
            }
            assertTrue(inn > other.getValue(),
                    "the inn should beat " + other.getKey() + " in the evening, and "
                            + inn + " is not more than " + other.getValue());
        }
        assertTrue(inn > dayOf(Leisure.Hour.DAY).getOrDefault(Leisure.Pastime.INN, 0),
                "an evening that went to the inn no more than an afternoon did is "
                        + "not an evening");
    }

    @Test
    void nobodyIsSentBackOutOfDoorsAfterTheCurfew() {
        for (Leisure.Hour hour : List.of(Leisure.Hour.EVENING, Leisure.Hour.NIGHT)) {
            for (Map.Entry<Leisure.Pastime, Integer> taken : dayOf(hour).entrySet()) {
                assertTrue(taken.getKey().isIndoors(),
                        hour + " sent somebody to the " + taken.getKey()
                                + ", which undoes the curfew that just walked them in");
            }
        }
    }

    @Test
    void aTownWithNoRoofToSitUnderHasNoEvening() {
        List<Leisure.Place> outdoorsOnly = List.of(
                new Leisure.Place(Leisure.Pastime.SQUARE, new SimPos(0, 64, 0)),
                new Leisure.Place(Leisure.Pastime.BENCH, new SimPos(3, 64, 3)));
        assertNull(Leisure.choose(SOMEBODY, 100, Leisure.Hour.EVENING, outdoorsOnly),
                "no inn and no hearth means people walk home exactly as they did "
                        + "before any of this existed");
        assertNotNull(Leisure.choose(SOMEBODY, 100, Leisure.Hour.DAY, outdoorsOnly));
    }

    @Test
    void anEmptyOfferIsNotAnError() {
        assertNull(Leisure.choose(SOMEBODY, 7, Leisure.Hour.DAY, List.of()));
        assertNull(Leisure.restFor(SOMEBODY, 7, Leisure.Hour.DAY, List.of()));
    }

    @Test
    void everyStayIsBetweenTheTwoBounds() {
        for (long tick = 0; tick < 2000; tick++) {
            int ticks = Leisure.ticksFor(SOMEBODY, tick);
            assertTrue(ticks >= Leisure.MIN_TICKS && ticks <= Leisure.MAX_TICKS,
                    "a stay of " + ticks + " ticks");
        }
    }

    @Test
    void aPastimeTheHourNoLongerAllowsIsGivenUp() {
        assertTrue(Leisure.stillFits(Leisure.Pastime.BENCH, Leisure.Hour.DAY));
        assertFalse(Leisure.stillFits(Leisure.Pastime.BENCH, Leisure.Hour.EVENING),
                "somebody on a bench when the light goes gets up and walks in");
        assertTrue(Leisure.stillFits(Leisure.Pastime.POST, Leisure.Hour.NIGHT),
                "the watch keeps the night; that is what the watch is");
    }

    /** What one person takes over a day of choosing, counted by kind. */
    private static Map<Leisure.Pastime, Integer> dayOf(Leisure.Hour hour) {
        Map<Leisure.Pastime, Integer> taken = new EnumMap<>(Leisure.Pastime.class);
        for (long tick = 0; tick < NightRest.DAY; tick += Leisure.MAX_TICKS) {
            Leisure.Place place = Leisure.choose(SOMEBODY, tick, hour, wholeTown());
            if (place != null) {
                taken.merge(place.what(), 1, Integer::sum);
            }
        }
        return taken;
    }

    // --- pairs ------------------------------------------------------------------------

    @Test
    void pairsFormOnlyWithinThreeBlocks() {
        List<Leisure.Attendee<String>> near = List.of(
                at("ann", Leisure.Pastime.WELL, 0, 0),
                at("bob", Leisure.Pastime.WELL, 2, 1));
        assertEquals(1, Leisure.talks(near, 100).size(), "two blocks apart at the well");

        List<Leisure.Attendee<String>> far = List.of(
                at("ann", Leisure.Pastime.WELL, 0, 0),
                at("bob", Leisure.Pastime.WELL, 9, 0));
        assertTrue(Leisure.talks(far, 100).isEmpty(),
                "nine blocks is somebody else at the same well, not a conversation");
    }

    @Test
    void thePastimeGathersPeopleRatherThanThePairing() {
        List<Leisure.Attendee<String>> passing = List.of(
                at("ann", Leisure.Pastime.WELL, 0, 0),
                at("bob", Leisure.Pastime.FARM_GATE, 1, 0));
        assertTrue(Leisure.talks(passing, 100).isEmpty(),
                "two people who happen to pass within a block on separate errands "
                        + "are not in conversation");
    }

    @Test
    void nobodyIsInTwoConversationsAtOnce() {
        List<Leisure.Attendee<String>> three = List.of(
                at("ann", Leisure.Pastime.SQUARE, 0, 0),
                at("bob", Leisure.Pastime.SQUARE, 1, 0),
                at("cal", Leisure.Pastime.SQUARE, 1, 1));
        List<Leisure.Chat<String>> chats = Leisure.talks(three, 100);
        assertEquals(1, chats.size(), "a pair and an onlooker, not a three-way");
        List<String> talking = new ArrayList<>();
        talking.add(chats.getFirst().a());
        talking.add(chats.getFirst().b());
        assertFalse(talking.contains("cal"), "the third is left listening");
    }

    @Test
    void everyExchangeIsBetweenTheTwoBounds() {
        List<Leisure.Attendee<String>> pair = List.of(
                at("ann", Leisure.Pastime.WELL, 0, 0),
                at("bob", Leisure.Pastime.WELL, 1, 0));
        for (long tick = 0; tick < 1000; tick++) {
            int ticks = Leisure.talks(pair, tick).getFirst().ticks();
            assertTrue(ticks >= Leisure.TALK_MIN_TICKS && ticks <= Leisure.TALK_MAX_TICKS,
                    "an exchange of " + ticks + " ticks");
        }
    }

    @Test
    void oneAloneTalksToNobody() {
        assertTrue(Leisure.talks(List.of(at("ann", Leisure.Pastime.WELL, 0, 0)), 1).isEmpty());
        assertTrue(Leisure.talks(List.<Leisure.Attendee<String>>of(), 1).isEmpty());
    }

    private static Leisure.Attendee<String> at(String who, Leisure.Pastime what,
                                               int x, int z) {
        return new Leisure.Attendee<>(who, what, new SimPos(x, 64, z));
    }
}
