package com.kingdoms.sim;

import com.kingdoms.sim.person.Profession;
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
}
