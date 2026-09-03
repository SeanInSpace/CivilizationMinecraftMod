package com.kingdoms.sim;

import com.kingdoms.sim.person.Appetite;
import com.kingdoms.sim.person.Person;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The hunger ladder, pinned at its rungs.
 *
 * <p>Two surfaces report a settler's hunger — a line of chat and a screen — and
 * both read this. The thresholds themselves are tested by the starvation model;
 * what is untested elsewhere is whether the reading lines up with them, so a
 * threshold that moves takes the word with it instead of leaving a settler
 * described as starving in the colour used for merely weak.
 */
class AppetiteTest {

    @Test
    void eachThresholdIsTheFloorOfItsRung() {
        assertEquals(Appetite.STARVING, Appetite.of(Person.HUNGER_SEVERE));
        assertEquals(Appetite.WEAK, Appetite.of(Person.HUNGER_WEAK));
        assertEquals(Appetite.HUNGRY, Appetite.of(Person.HUNGER_HUNGRY));
        assertEquals(Appetite.FED, Appetite.of(0));
    }

    @Test
    void oneShortOfAThresholdIsStillTheRungBelow() {
        assertEquals(Appetite.WEAK, Appetite.of(Person.HUNGER_SEVERE - 1));
        assertEquals(Appetite.HUNGRY, Appetite.of(Person.HUNGER_WEAK - 1));
        assertEquals(Appetite.FED, Appetite.of(Person.HUNGER_HUNGRY - 1));
    }

    @Test
    void theWorstAPersonCanGetIsStarving() {
        assertEquals(Appetite.STARVING, Appetite.of(Person.HUNGER_MAX));
    }

    @Test
    void everyRungHasSomethingToSay() {
        for (Appetite appetite : Appetite.values()) {
            assertEquals(appetite.word().strip(), appetite.word());
            assertFalse(appetite.word().isEmpty(), appetite + " has no word");
        }
    }
}
