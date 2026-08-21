package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers a town noticing somebody locked out of their own house and fixing it. */
class AccessRepairTest {

    private static final BuildingType HOUSE = new BuildingType("test:house", 20, 9999, 0, 0, 80, 4);

    /** Nobody present, so the repair flight is built on the clock. */
    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);
    private static final SimPos DOORWAY = new SimPos(10, 70, 12);

    private static Settlement settlement() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        s.setCatalogue(List.of(HOUSE));
        return s;
    }

    @Test
    void aLockedOutResidentGetsStepsOrdered() {
        Settlement s = settlement();

        assertTrue(BuildPlanner.requestAccessStairs(s, DOORWAY, 5, 7));

        assertEquals(1, s.buildQueue().size());
        BuildTask ordered = s.buildQueue().getFirst();
        assertEquals(BuildPlanner.ACCESS_STAIRS, ordered.blueprintId());
        assertEquals(DOORWAY, ordered.origin());
        assertTrue(s.events().stream().anyMatch(e -> e.message().contains("nobody could reach")),
                "and the town records why it is building them");
    }

    @Test
    void aTallerClimbIsALongerJob() {
        Settlement shallow = settlement();
        Settlement deep = settlement();

        BuildPlanner.requestAccessStairs(shallow, DOORWAY, 2, 0);
        BuildPlanner.requestAccessStairs(deep, DOORWAY, 9, 0);

        assertTrue(deep.buildQueue().getFirst().requiredWork()
                        > shallow.buildQueue().getFirst().requiredWork(),
                "a longer flight takes more building");
    }

    @Test
    void repairsJumpAheadOfOrdinaryWork() {
        Settlement s = settlement();
        s.enqueueBuild(new BuildTask("test:workshop", new SimPos(30, 64, 0), 40));

        BuildPlanner.requestAccessStairs(s, DOORWAY, 4, 0);

        assertEquals(BuildPlanner.ACCESS_STAIRS, s.buildQueue().getFirst().blueprintId(),
                "a family locked out beats the next workshop");
        assertEquals(2, s.buildQueue().size(), "and the paused job is still queued behind it");
    }

    @Test
    void aStuckResidentCannotFloodTheQueue() {
        Settlement s = settlement();

        assertTrue(BuildPlanner.requestAccessStairs(s, DOORWAY, 4, 0));
        assertFalse(BuildPlanner.requestAccessStairs(s, DOORWAY, 4, 1),
                "the flight is already ordered");
        assertFalse(BuildPlanner.requestAccessStairs(s, DOORWAY, 4, 2));

        assertEquals(1, s.buildQueue().size());
    }

    @Test
    void stepsAreNotRebuiltOnceTheyStand() {
        Settlement s = settlement();
        s.addBuilding(new Building(BuildPlanner.ACCESS_STAIRS, DOORWAY, 0, true));

        assertFalse(BuildPlanner.requestAccessStairs(s, DOORWAY, 4, 0),
                "the way up is already there");
        assertTrue(s.buildQueue().isEmpty());
    }

    @Test
    void separateDoorsGetSeparateFlights() {
        Settlement s = settlement();
        SimPos otherDoor = new SimPos(40, 70, 12);

        assertTrue(BuildPlanner.requestAccessStairs(s, DOORWAY, 4, 0));
        assertTrue(BuildPlanner.requestAccessStairs(s, otherDoor, 4, 0),
                "a second locked-out family is a separate problem");

        assertEquals(2, s.buildQueue().size());
    }

    @Test
    void orderedStepsAreActuallyBuiltAndRecorded() {
        Settlement s = settlement();
        for (int i = 0; i < 4; i++) {
            s.addResident(new Person(
                    Person.Id.random(), "Builder " + i, Profession.BUILDER, new SimPos(0, 64, 0)));
        }
        BuildPlanner.requestAccessStairs(s, DOORWAY, 2, 0);

        for (int i = 0; i < 10 && !s.buildQueue().isEmpty(); i++) {
            s.step(CTX);
        }

        assertTrue(s.buildQueue().isEmpty(), "the flight was built");
        assertTrue(s.buildings().stream()
                        .anyMatch(b -> b.blueprintId().equals(BuildPlanner.ACCESS_STAIRS)),
                "and stands as a recorded structure, so it is never ordered twice");
    }
}
