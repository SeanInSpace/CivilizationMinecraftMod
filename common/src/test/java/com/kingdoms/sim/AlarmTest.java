package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.RaidPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Alarm;
import com.kingdoms.sim.platform.Sighting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How worried a town is, and who that sends indoors.
 *
 * <p>There used to be one rule — any hostile at all and every civilian ran home
 * — and it was wrong three times over. One wandering skeleton produced the same
 * response as a sixteen-strong raid; the count came from a box thirty-two blocks
 * deep, so a zombie in a cave under the town hall emptied the streets for as long
 * as it lived; and every creature counted as one, which made a creeper and a
 * zombie the same news.
 *
 * <p>Now it is weighted danger somebody has actually seen, and the load-bearing
 * rule is that <em>one</em> of anything is never a panic. See
 * {@link #aLoneCreeperIsTheWatchsProblemNotTheTowns()}.
 */
class AlarmTest {

    @Test
    void aTownThatHasSeenNothingIsCalm() {
        assertEquals(Alarm.CALM, Alarm.of(0));
        assertFalse(Alarm.CALM.isRaised());
    }

    @Test
    void aLittleDangerIsAThingForTheGuards() {
        assertEquals(Alarm.WARY, Alarm.of(1), "one zombie");
        assertEquals(Alarm.WARY, Alarm.of(4), "a creeper, or two skeletons");
        assertEquals(Alarm.WARY, Alarm.of(Alarm.ALARMED_AT - 1));
        assertTrue(Alarm.WARY.isRaised());
    }

    @Test
    void noSingleOrdinaryCreatureIsWorthAPanicOnItsOwn() {
        // The tier that empties the streets sits above anything one creature is
        // worth, so the arithmetic alone cannot get there from a lone mob. The
        // cap in Settlement.sighted is the belt; this is the braces.
        assertTrue(Alarm.ALARMED_AT > 5,
                "a ravager is 5; the panic tier must sit above the worst ordinary mob");
    }

    @Test
    void enoughOfThemAndEverybodyGoesIn() {
        assertEquals(Alarm.ALARMED, Alarm.of(Alarm.ALARMED_AT));
        assertEquals(Alarm.ALARMED, Alarm.of(16));
    }

    // --- one of anything is never a panic ---

    @Test
    void aLoneCreeperIsTheWatchsProblemNotTheTowns() {
        // The request this rule exists for. A creeper is worth 4 — more than a
        // skeleton, more than a zombie — and the town takes that seriously. What
        // it does not do is stop.
        Settlement town = watchOf(0, 4);

        town.sighted(new Sighting(4, 1));

        assertEquals(Alarm.WARY, town.alarm(),
                "one creeper is a thing to watch, not a thing to hide from");
        assertFalse(RaidPlanner.outmatched(town, new Sighting(4, 1)),
                "and not a thing to ring the bell about, even with no watch at all");
    }

    @Test
    void notEvenTheWorstSingleThingEmptiesTheStreets() {
        Settlement town = watchOf(0, 4);

        town.sighted(new Sighting(10, 1));   // a warden

        assertEquals(Alarm.WARY, town.alarm(),
                "however bad it is, one of it is what the watch is for");
        assertEquals(Alarm.ALARMED_AT - 1, town.threatLevel(),
                "capped one rung below panic — the danger still registers");
    }

    @Test
    void twoOfThemIsADifferentMatterEntirely() {
        Settlement town = watchOf(0, 4);

        town.sighted(new Sighting(8, 2));   // two creepers

        assertEquals(Alarm.ALARMED, town.alarm(),
                "the cap is about being alone, not about being survivable");
    }

    @Test
    void aCreeperAndAZombieOutweighFourZombies() {
        // The whole point of weighting: the same head count, different news.
        assertEquals(Alarm.WARY, Alarm.of(new Sighting(4, 4).danger()),
                "four zombies is four");
        assertEquals(Alarm.ALARMED, Alarm.of(new Sighting(4 + 1 + 1, 3).danger()),
                "a creeper with two zombies is six");
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
    void theWatchRingsWhenWhatIsComingOutweighsIt() {
        assertTrue(RaidPlanner.outmatched(watchOf(1, 5), new Sighting(6, 3)),
                "one guard holds three danger, and six is coming");
    }

    @Test
    void aWatchThatCanHandleItDoesNotRing() {
        assertFalse(RaidPlanner.outmatched(watchOf(4, 5), new Sighting(6, 6)),
                "four guards and six zombies is a Tuesday");
    }

    @Test
    void theSameTwoCreepersAreDifferentNewsToDifferentTowns() {
        // The reason the bell is not simply another threshold: it weighs what is
        // coming against who is standing.
        Sighting pair = new Sighting(8, 2);
        assertTrue(RaidPlanner.outmatched(watchOf(1, 8), pair));
        assertFalse(RaidPlanner.outmatched(watchOf(3, 8), pair));
    }

    @Test
    void aThinWatchPanicsSoonerRatherThanAlways() {
        // Below the floor the bell stays quiet however defenceless the town is —
        // otherwise a town whose one guard was hungry would ring over two zombies.
        Settlement defenceless = watchOf(0, 4);
        assertFalse(RaidPlanner.outmatched(defenceless, new Sighting(3, 3)),
                "three zombies is not an emergency, it is a Tuesday with no guards");
        assertTrue(RaidPlanner.outmatched(defenceless, new Sighting(4, 2)),
                "two skeletons and nobody to meet them is");
    }

    @Test
    void aWatchTooHungryToStandDoesNotCount() {
        Settlement town = watchOf(2, 4);
        town.residents().forEach(person -> person.setHunger(Person.HUNGER_WEAK));

        assertTrue(RaidPlanner.outmatched(town, new Sighting(4, 2)),
                "guards who cannot lift a sword are not a garrison");
    }

    @Test
    void nothingSeenIsNothingToRingAbout() {
        assertFalse(RaidPlanner.outmatched(watchOf(0, 4), Sighting.NONE));
    }

    @Test
    void soundingTheAlarmPanicsTheTownWhateverTheCount() {
        // One hostile, seen by a guard who judges it beyond them: the whole town
        // goes in, even though the count alone would only make it wary.
        Settlement town = watchOf(0, 4);
        town.sighted(new Sighting(1, 1));
        assertEquals(Alarm.WARY, town.alarm());

        town.soundAlarm();

        assertEquals(Alarm.ALARMED, town.alarm());
        assertTrue(town.remembersSighting(), "and the town holds that belief for a while");
    }

    @Test
    void theBellNeverQuietensATownAlreadyWorseOff() {
        Settlement town = watchOf(1, 4);
        town.sighted(new Sighting(9, 5));

        town.soundAlarm();

        assertEquals(9, town.threatLevel(),
                "ringing about nine danger must not talk it down to the panic floor");
    }
}
