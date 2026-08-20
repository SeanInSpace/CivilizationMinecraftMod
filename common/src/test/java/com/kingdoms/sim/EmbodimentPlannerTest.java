package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.view.EmbodimentPlanner;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hydration state machine, tested without a game. Every bug this file
 * catches is one that would otherwise be a villager duplicating or vanishing
 * in a twenty-minute play session.
 */
class EmbodimentPlannerTest {

    /** A bridge with one player standing at the origin. */
    private static final class PlayerBridge implements WorldBridge {
        final SimPos player = new SimPos(0, 64, 0);

        @Override
        public boolean playerWithin(SimPos pos, double radius) {
            return pos.horizontalDistance(player) <= radius;
        }

        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed) {
            return origin.y();
        }
        @Override public void log(String message) { }
    }

    private static Settlement withPersonAt(SimPos pos, boolean embodied) {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 512);
        Person person = new Person(Person.Id.random(), "Watcher", Profession.FARMER, pos);
        person.setEmbodied(embodied);
        s.addResident(person);
        return s;
    }

    private static EmbodimentPlanner.Plan plan(Settlement s) {
        return EmbodimentPlanner.plan(s, new PlayerBridge(), SimSettings.DEFAULTS);
    }

    @Test
    void nearbyPersonIsEmbodied() {
        EmbodimentPlanner.Plan plan = plan(withPersonAt(new SimPos(50, 64, 0), false));

        assertEquals(1, plan.toEmbody().size());
        assertTrue(plan.toRelease().isEmpty());
    }

    @Test
    void distantPersonStaysAbstract() {
        assertTrue(plan(withPersonAt(new SimPos(500, 64, 0), false)).isEmpty());
    }

    @Test
    void borderlinePersonIsNeverFlickered() {
        // Between the spawn radius (96) and the release boundary (96 + 32): an
        // embodied person stays and an abstract one is not spawned. That dead
        // zone is the hysteresis — without it, someone standing right on the
        // radius would blink in and out as the player shifts their feet.
        SimPos edge = new SimPos(110, 64, 0);

        assertTrue(plan(withPersonAt(edge, true)).isEmpty(), "no release inside the margin");
        assertTrue(plan(withPersonAt(edge, false)).isEmpty(), "no spawn beyond the radius");
    }

    @Test
    void personFarBeyondTheMarginIsReleased() {
        EmbodimentPlanner.Plan plan = plan(withPersonAt(new SimPos(200, 64, 0), true));

        assertEquals(1, plan.toRelease().size());
        assertTrue(plan.toEmbody().isEmpty());
    }

    @Test
    void embodiedPeopleAreNotSpawnedAgain() {
        assertTrue(plan(withPersonAt(new SimPos(50, 64, 0), true)).isEmpty(),
                "an existing entity must never be doubled");
    }

    @Test
    void capBoundsHowManyAreShown() {
        Settlement s = new Settlement(Settlement.Id.random(), "Bigton", new SimPos(0, 64, 0), 512);
        for (int i = 0; i < 5; i++) {
            s.addResident(new Person(
                    Person.Id.random(), "Person " + i, Profession.FARMER, new SimPos(i, 64, 0)));
        }
        SimSettings cappedAtTwo = new SimSettings(100, 8, 96.0, 2);

        EmbodimentPlanner.Plan plan = EmbodimentPlanner.plan(s, new PlayerBridge(), cappedAtTwo);

        assertEquals(2, plan.toEmbody().size(), "the cap protects the tick budget");
    }
}
