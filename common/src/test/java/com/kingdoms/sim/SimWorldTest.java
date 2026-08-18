package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The point of these tests is that they run in milliseconds with no Minecraft,
 * no client, and no world. If the simulation ever stops being testable this way,
 * something has leaked across the platform boundary.
 */
class SimWorldTest {

    /** A bridge that records calls instead of touching a world. */
    private static final class FakeBridge implements WorldBridge {
        final List<String> materialized = new ArrayList<>();
        boolean observed = false;
        boolean loaded = false;

        @Override
        public boolean playerWithin(SimPos pos, double radius) {
            return observed;
        }

        @Override
        public boolean isLoaded(SimPos pos) {
            return loaded;
        }

        @Override
        public int surfaceHeight(SimPos pos) {
            return pos.y();
        }

        @Override
        public void materializeBlueprint(String blueprintId, SimPos origin) {
            materialized.add(blueprintId + "@" + origin);
        }

        @Override
        public void log(String message) {
            // no-op in tests
        }
    }

    private static Settlement settlementWithBuilders(int builders, BuildTask task) {
        Settlement settlement = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        for (int i = 0; i < builders; i++) {
            settlement.addResident(new Person(
                    Person.Id.random(), "Builder " + i, Profession.BUILDER, new SimPos(i, 64, 0)));
        }
        if (task != null) {
            settlement.enqueueBuild(task);
        }
        return settlement;
    }

    private static SimWorld worldWith(FakeBridge bridge, Settlement settlement) {
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Normandy", "kingdoms:norman");
        kingdom.addSettlement(settlement);
        SimWorld world = new SimWorld(bridge);
        world.addKingdom(kingdom);
        return world;
    }

    @Test
    void simulationRunsWithoutMinecraft() {
        SimWorld world = worldWith(new FakeBridge(), settlementWithBuilders(3, null));

        world.step();

        assertEquals(1, world.stepsElapsed());
        assertEquals(3, world.totalPopulation());
    }

    @Test
    void slowTickOnlyStepsOnTheInterval() {
        SimWorld world = new SimWorld(new FakeBridge());

        int stepsFired = 0;
        for (int tick = 0; tick < SimWorld.SIM_INTERVAL_TICKS * 3; tick++) {
            if (world.onGameTick()) {
                stepsFired++;
            }
        }

        assertEquals(3, stepsFired, "three intervals of ticks should produce exactly three steps");
        assertEquals(3, world.stepsElapsed());
        assertFalse(world.onGameTick(), "the tick right after a step must not step again");
    }

    @Test
    void buildQueueAdvancesWhileUnobserved() {
        FakeBridge bridge = new FakeBridge();
        BuildTask bakery = new BuildTask("kingdoms:norman/bakery", new SimPos(10, 64, 10), 6);
        Settlement settlement = settlementWithBuilders(2, bakery);
        SimWorld world = worldWith(bridge, settlement);

        // Two builders contribute 2 work per step, so 3 steps completes 6 work.
        world.step();
        assertEquals(2, bakery.progress());
        world.step();
        world.step();

        assertTrue(bakery.isComplete());
        assertTrue(settlement.buildQueue().isEmpty(), "completed task should leave the queue");
    }

    @Test
    void buildQueueStallsWithNoBuilders() {
        FakeBridge bridge = new FakeBridge();
        BuildTask bakery = new BuildTask("kingdoms:norman/bakery", new SimPos(10, 64, 10), 6);
        Settlement settlement = settlementWithBuilders(0, bakery);
        settlement.addResident(new Person(
                Person.Id.random(), "Farmer", Profession.FARMER, new SimPos(0, 64, 0)));

        settlement.step(new SimContext(bridge, 0));

        assertEquals(0, bakery.progress());
        assertTrue(settlement.buildings().isEmpty());
    }

    @Test
    void threatDecaysOverTime() {
        FakeBridge bridge = new FakeBridge();
        SimContext ctx = new SimContext(bridge, 0);
        Settlement settlement = settlementWithBuilders(1, null);
        settlement.setThreatLevel(3);

        settlement.step(ctx);
        assertEquals(2, settlement.threatLevel());

        settlement.step(ctx);
        settlement.step(ctx);
        assertEquals(0, settlement.threatLevel());

        settlement.step(ctx);
        assertEquals(0, settlement.threatLevel(), "threat should not go negative");
    }

    @Test
    void completedBuildingStandsAtTheSurveyedSite() {
        FakeBridge bridge = new FakeBridge();
        BuildTask bakery = new BuildTask("kingdoms:norman/bakery", new SimPos(10, 64, 10), 2);
        // Construction surveyed the real terrain at y=71 while building visibly.
        bakery.setSiteY(71);
        Settlement settlement = settlementWithBuilders(2, bakery);
        SimWorld world = worldWith(bridge, settlement);

        world.step();

        assertEquals(new SimPos(10, 71, 10), settlement.buildings().getFirst().origin(),
                "the record must stand where the blocks actually went");
    }

    @Test
    void claimRadiusBoundsTerritory() {
        Settlement settlement = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);

        assertTrue(settlement.contains(new SimPos(60, 64, 0)));
        assertFalse(settlement.contains(new SimPos(100, 64, 0)));
    }

    // --- completed buildings ---

    @Test
    void completedBuildingIsRecordedInSettlement() {
        FakeBridge bridge = new FakeBridge();
        bridge.loaded = true;
        SimPos origin = new SimPos(10, 64, 10);
        Settlement settlement = settlementWithBuilders(2, new BuildTask("kingdoms:norman/bakery", origin, 2));
        SimWorld world = worldWith(bridge, settlement);

        world.step();

        assertEquals(1, settlement.buildings().size(), "finished work must be recorded, not discarded");
        Building bakery = settlement.buildings().getFirst();
        assertEquals("kingdoms:norman/bakery", bakery.blueprintId());
        assertEquals(origin, bakery.origin());
        assertEquals(0, bakery.completedOnStep(), "should record the step it finished on");
    }

    @Test
    void buildingCompletedWhileUnloadedIsRecordedButNotDrawn() {
        FakeBridge bridge = new FakeBridge();
        bridge.loaded = false;
        Settlement settlement = settlementWithBuilders(
                2, new BuildTask("kingdoms:norman/bakery", new SimPos(10, 64, 10), 2));
        SimWorld world = worldWith(bridge, settlement);

        world.step();

        assertEquals(1, settlement.buildings().size(), "the building exists in the simulation");
        assertTrue(bridge.materialized.isEmpty(), "but nothing should be placed in an unloaded chunk");
        assertEquals(1, settlement.pendingBuildings().size());
    }

    @Test
    void pendingBuildingIsDrawnOnceTheChunkLoads() {
        FakeBridge bridge = new FakeBridge();
        bridge.loaded = false;
        Settlement settlement = settlementWithBuilders(
                2, new BuildTask("kingdoms:norman/bakery", new SimPos(10, 64, 10), 2));
        SimWorld world = worldWith(bridge, settlement);

        world.step();
        assertTrue(bridge.materialized.isEmpty());

        // The player comes back.
        bridge.loaded = true;
        world.step();

        assertEquals(1, bridge.materialized.size(), "should be drawn once the chunk is available");
        assertEquals("kingdoms:norman/bakery@(10, 64, 10)", bridge.materialized.getFirst());
        assertTrue(settlement.pendingBuildings().isEmpty());
    }

    @Test
    void buildingIsNeverDrawnTwice() {
        FakeBridge bridge = new FakeBridge();
        bridge.loaded = true;
        Settlement settlement = settlementWithBuilders(
                2, new BuildTask("kingdoms:norman/bakery", new SimPos(10, 64, 10), 2));
        SimWorld world = worldWith(bridge, settlement);

        world.step();
        world.step();
        world.step();

        assertEquals(1, bridge.materialized.size(), "repeated steps must not repaint a finished building");
    }
}
