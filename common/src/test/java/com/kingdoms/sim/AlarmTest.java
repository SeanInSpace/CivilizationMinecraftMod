package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.RaidPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Alarm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How worried a town is, and who that sends indoors.
 *
 * <p>There used to be one rule — any hostile at all and every civilian ran home
 * — and it was wrong twice over. One wandering skeleton produced the same
 * response as a sixteen-strong raid, and the count came from a box thirty-two
 * blocks deep, so a zombie in a cave under the town hall emptied the streets
 * for as long as it lived.
 */
class AlarmTest {

    @Test
    void aTownThatHasSeenNothingIsCalm() {
        assertEquals(Alarm.CALM, Alarm.of(0));
        assertFalse(Alarm.CALM.isRaised());
    }

    @Test
    void oneHostileIsAThingForTheGuards() {
        assertEquals(Alarm.WARY, Alarm.of(1));
        assertEquals(Alarm.WARY, Alarm.of(2));
        assertTrue(Alarm.WARY.isRaised());
    }

    @Test
    void enoughOfThemAndEverybodyGoesIn() {
        assertEquals(Alarm.ALARMED, Alarm.of(Alarm.ALARMED_AT));
        assertEquals(Alarm.ALARMED, Alarm.of(16));
    }

    @Test
    void aNonsenseCountIsNotAnEmergency() {
        assertEquals(Alarm.CALM, Alarm.of(-1),
                "a negative sighting is a bug elsewhere, not a reason to panic");
    }

    // --- who comes in ---

    @Test
    void aCalmTownCallsNobodyIn() {
        for (Profession trade : Profession.values()) {
            assertFalse(Alarm.CALM.callsIn(trade), trade + " has no reason to be indoors");
        }
    }

    @Test
    void aWaryTownCallsInOnlyTheTradesThatWorkOutside() {
        assertTrue(Alarm.WARY.callsIn(Profession.LUMBERJACK), "the woods are past the wall");
        assertTrue(Alarm.WARY.callsIn(Profession.MINER), "so is the mine head");

        assertFalse(Alarm.WARY.callsIn(Profession.FARMER),
                "a farmer on a ring plot is behind the palisade");
        assertFalse(Alarm.WARY.callsIn(Profession.BUILDER));
        assertFalse(Alarm.WARY.callsIn(Profession.SHEPHERD));
        assertFalse(Alarm.WARY.callsIn(Profession.SMITH));
        assertFalse(Alarm.WARY.callsIn(Profession.PIONEER),
                "a founding party cannot afford to stop over one skeleton");
    }

    @Test
    void anAlarmedTownCallsInEverybodyButTheWatch() {
        for (Profession trade : Profession.values()) {
            if (trade == Profession.GUARD) {
                continue;
            }
            assertTrue(Alarm.ALARMED.callsIn(trade), trade + " should be indoors");
        }
    }

    @Test
    void theGuardsAreNeverCalledIn() {
        // At any level. They are the reason the rest of the town does not have
        // to care about one skeleton.
        for (Alarm alarm : Alarm.values()) {
            assertFalse(alarm.callsIn(Profession.GUARD), alarm + " must not send the watch home");
        }
    }

    @Test
    void onlyTheOutdoorTradesAreOutdoorTrades() {
        assertTrue(Profession.LUMBERJACK.worksBeyondTheWalls());
        assertTrue(Profession.MINER.worksBeyondTheWalls());
        for (Profession trade : Profession.values()) {
            if (trade == Profession.LUMBERJACK || trade == Profession.MINER) {
                continue;
            }
            assertFalse(trade.worksBeyondTheWalls(), trade + " works inside the claim");
        }
    }

    // --- the bell ---

    private static Settlement watchOf(int guards, int civilians) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        for (int i = 0; i < guards; i++) {
            town.addResident(new Person(
                    Person.Id.random(), "Watch " + i, Profession.GUARD, town.centre()));
        }
        for (int i = 0; i < civilians; i++) {
            town.addResident(new Person(
                    Person.Id.random(), "Hand " + i, Profession.FARMER, town.centre()));
        }
        return town;
    }

    @Test
    void theWatchRingsWhenItIsOutnumbered() {
        assertTrue(RaidPlanner.outmatched(watchOf(1, 5), 2),
                "one guard against two is a thing to tell the town about");
    }

    @Test
    void aWatchThatCanHandleItDoesNotRing() {
        assertFalse(RaidPlanner.outmatched(watchOf(4, 5), 3),
                "four guards and three zombies is a Tuesday");
    }

    @Test
    void theSameThreeZombiesAreDifferentNewsToDifferentTowns() {
        // The reason the bell is not simply another threshold: it weighs what is
        // coming against who is standing.
        assertTrue(RaidPlanner.outmatched(watchOf(1, 8), 3));
        assertFalse(RaidPlanner.outmatched(watchOf(3, 8), 3));
    }

    @Test
    void aTownWithNoWatchRingsAtTheFirstThingItSees() {
        assertTrue(RaidPlanner.outmatched(watchOf(0, 4), 1),
                "there is nobody whose job it was, so everybody's business");
    }

    @Test
    void aWatchTooHungryToStandDoesNotCount() {
        Settlement town = watchOf(2, 4);
        town.residents().forEach(person -> person.setHunger(Person.HUNGER_WEAK));

        assertTrue(RaidPlanner.outmatched(town, 1),
                "guards who cannot lift a sword are not a garrison");
    }

    @Test
    void nothingSeenIsNothingToRingAbout() {
        assertFalse(RaidPlanner.outmatched(watchOf(0, 4), 0));
    }

    @Test
    void soundingTheAlarmPanicsTheTownWhateverTheCount() {
        // One hostile, seen by a guard who judges it beyond them: the whole town
        // goes in, even though the count alone would only make it wary.
        Settlement town = watchOf(0, 4);
        town.sighted(1);
        assertEquals(Alarm.WARY, town.alarm());

        town.soundAlarm();

        assertEquals(Alarm.ALARMED, town.alarm());
        assertTrue(town.remembersSighting(), "and the town holds that belief for a while");
    }

    @Test
    void theBellNeverQuietensATownAlreadyWorseOff() {
        Settlement town = watchOf(1, 4);
        town.sighted(9);

        town.soundAlarm();

        assertEquals(9, town.threatLevel(),
                "ringing about nine hostiles must not talk it down to three");
    }
}
