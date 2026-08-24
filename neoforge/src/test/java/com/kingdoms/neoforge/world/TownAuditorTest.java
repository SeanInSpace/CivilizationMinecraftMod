package com.kingdoms.neoforge.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first tests this module has ever had.
 *
 * <p>Everything in {@code neoforge} can reach Minecraft, and Minecraft cannot
 * simply be constructed inside a JUnit run — so eleven thousand lines went
 * untested and the only instrument was a playtest. That is how a farm emptying
 * itself and four doorless buildings survived weeks of green builds.
 *
 * <p>The auditor is the right thing to start with, because it is the instrument
 * everything else is judged by. A silent auditor and a healthy town read
 * identically from outside.
 */
class TownAuditorTest {

    @Test
    void theAuditorAgreesWithItself() {
        List<String> results = TownAuditor.selfTest();

        assertFalse(results.isEmpty(), "a self-test that checks nothing passes trivially");
        List<String> failed = results.stream().filter(line -> line.startsWith("FAIL")).toList();
        assertTrue(failed.isEmpty(), "the auditor failed its own checks: " + failed);
    }

    // --- the larder, which is what the vitals line warns on ---

    @Test
    void anEmptyTownHasNoAppetite() {
        assertEquals(0, TownAuditor.reserveSteps(100, 0),
                "nobody left to go hungry; dividing by them would be worse");
    }

    @Test
    void reserveGrowsWithFoodAndShrinksWithMouths() {
        assertTrue(TownAuditor.reserveSteps(1000, 10) > TownAuditor.reserveSteps(100, 10),
                "a fuller larder lasts longer");
        assertTrue(TownAuditor.reserveSteps(100, 5) > TownAuditor.reserveSteps(100, 50),
                "and more mouths eat it faster");
    }

    // --- the verdict ---

    @Test
    void aStarvingResidentOutranksAFullGranary() {
        int full = TownAuditor.LEAN_RESERVE_STEPS * 100;

        assertEquals(TownAuditor.Distress.SEVERE,
                TownAuditor.distress(com.kingdoms.sim.person.Person.HUNGER_SEVERE, full, 10),
                "food in the store is no comfort to somebody who cannot reach it");
    }

    @Test
    void anEmptyLarderWarnsBeforeAnybodyHasGoneHungry() {
        assertEquals(TownAuditor.Distress.LEAN, TownAuditor.distress(0, 0, 10),
                "hunger is the lagging indicator; this is the whole point of the reserve");
    }

    @Test
    void aFedTownWithFoodInHandIsLeftInPeace() {
        assertEquals(TownAuditor.Distress.NONE,
                TownAuditor.distress(0, TownAuditor.LEAN_RESERVE_STEPS * 100, 10),
                "an audit that cries wolf is an audit nobody reads");
    }

    @Test
    void anEmptyTownIsAnObituaryRatherThanAFamine() {
        assertEquals(TownAuditor.Distress.NONE,
                TownAuditor.distress(com.kingdoms.sim.person.Person.HUNGER_SEVERE, 0, 0),
                "there is nobody left to save");
    }

    @Test
    void theDistressTokensAreStableBecauseTheHarnessGrepsThem() {
        assertEquals("severe", TownAuditor.Distress.SEVERE.token());
        assertEquals("weak", TownAuditor.Distress.WEAK.token());
        assertEquals("lean", TownAuditor.Distress.LEAN.token());
        assertEquals("none", TownAuditor.Distress.NONE.token());
    }
}
