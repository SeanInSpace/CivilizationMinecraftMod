package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.kingdom.Kingdom;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimWorld;
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
        public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed, int facing) {
            materialized.add(blueprintId + "@" + origin);
            return new Footprint(origin.y(), 3, 3, 3);
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
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Normandy", "civilization:human/norman");
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
        BuildTask bakery = new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 6);
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
        BuildTask bakery = new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 6);
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
        BuildTask bakery = new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 2);
        // Construction surveyed the real terrain at y=71 while building visibly.
        bakery.setSiteY(71);
        Settlement settlement = settlementWithBuilders(2, bakery);
        SimWorld world = worldWith(bridge, settlement);

        world.step();

        assertEquals(new SimPos(10, 71, 10), settlement.buildings().getFirst().origin(),
                "the record must stand where the blocks actually went");
    }

    @Test
    void handBuiltStructuresAreNeverReStamped() {
        FakeBridge bridge = new FakeBridge();
        bridge.loaded = true;
        BuildTask bakery = new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 2);
        // The builders laid every block themselves while the player watched.
        bakery.setPlan(120, 120);
        bakery.setWorkDone(120);
        Settlement settlement = settlementWithBuilders(2, bakery);
        SimWorld world = worldWith(bridge, settlement);

        world.step();
        world.step();

        assertTrue(settlement.buildings().getFirst().isMaterialized(),
                "a hand-built structure already stands");
        assertTrue(bridge.materialized.isEmpty(),
                "and must never be wiped and stamped over by a placement pass");
    }

    @Test
    void unwatchedConstructionStillGetsPlacedWhole() {
        FakeBridge bridge = new FakeBridge();
        BuildTask bakery = new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 2);
        // Nobody was watching: the plan was never surveyed, no blocks laid.
        Settlement settlement = settlementWithBuilders(2, bakery);
        SimWorld world = worldWith(bridge, settlement);

        world.step();
        world.step();
        assertEquals(1, settlement.buildings().size(), "it finished while nobody was there");
        assertEquals(0, bridge.materialized.size(), "with nowhere yet to put it");

        // The player comes back and the chunk loads.
        bridge.loaded = true;
        world.step();

        assertEquals(1, bridge.materialized.size(),
                "construction nobody saw must still appear, whole");
    }

    /**
     * Ground that is loaded and has nobody on it is ground the clock draws on.
     *
     * <p>Written from a wrong hypothesis that cost six runs. A town grown
     * unwatched inside a force-loaded box was reported to have materialized
     * nothing — 120 buildings all pending, the ring read as nothing looked at —
     * and the conclusion drawn was that the unwatched drawing refuses
     * force-loaded ground. It does not, and there is no third state for it to
     * refuse: the only question {@code materializePending} asks is
     * {@code isLoaded}. The measurement was an artifact of the harness, whose
     * force-load never existed ({@code /forceload add} takes at most 256 chunks
     * a command and the box asked for 400), so the ground genuinely was not
     * loaded and the refusal was correct.
     *
     * <p>Re-measured on a dedicated server, seed 8675309: a town founded and
     * grown 500 steps with no player in the world and its claim genuinely
     * force-loaded drew 12 of 12 buildings; a seeded town in the same box drew
     * 16 of 16. So this is the rule, pinned in the one direction the old
     * test left implicit — {@code loaded} is never flipped here and nobody is
     * ever near.
     */
    @Test
    void loadedGroundWithNobodyNearIsStillDrawnOn() {
        FakeBridge bridge = new FakeBridge();
        bridge.loaded = true;       // held open by a force-load, or by somebody far away
        bridge.observed = false;    // and not one player within sight of it
        BuildTask bakery = new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 2);
        Settlement settlement = settlementWithBuilders(2, bakery);
        SimWorld world = worldWith(bridge, settlement);

        world.step();
        world.step();

        assertEquals(1, settlement.buildings().size(), "the clock finished it");
        assertEquals(1, bridge.materialized.size(),
                "loaded ground is drawn on whether or not anybody is standing on it");
        assertTrue(settlement.buildings().getFirst().isMaterialized(),
                "and the record says so, rather than staying pending for an arrival");
    }

    /**
     * The other half of the same rule: ground nobody has loaded is left alone
     * however long the town runs, and the building waits rather than being lost.
     *
     * <p>Measured in the same session: a town founded 4000 blocks out on ground
     * nothing had loaded and grown 400 steps held all 17 of its buildings
     * pending — correctly — and drew every one of them on the first step after
     * a force-load covered its claim.
     */
    @Test
    void unloadedGroundIsNeverDrawnOnHoweverLongItRuns() {
        FakeBridge bridge = new FakeBridge();
        BuildTask bakery = new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 2);
        Settlement settlement = settlementWithBuilders(2, bakery);
        SimWorld world = worldWith(bridge, settlement);

        for (int step = 0; step < 20; step++) {
            world.step();
        }

        assertEquals(1, settlement.buildings().size(), "it was built and recorded");
        assertTrue(bridge.materialized.isEmpty(), "and never stamped onto ground nobody has read");
        assertFalse(settlement.buildings().getFirst().isMaterialized(), "so it is still owed a drawing");

        bridge.loaded = true;
        world.step();

        assertEquals(1, bridge.materialized.size(), "and gets it on the first step the ground answers");
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
        // Unloaded, so the work runs on the clock. Where the world is real the
        // only thing that finishes a building is a builder laying its last block.
        SimPos origin = new SimPos(10, 64, 10);
        Settlement settlement = settlementWithBuilders(2, new BuildTask("civilization:norman/bakery", origin, 2));
        SimWorld world = worldWith(bridge, settlement);

        world.step();

        assertEquals(1, settlement.buildings().size(), "finished work must be recorded, not discarded");
        Building bakery = settlement.buildings().getFirst();
        assertEquals("civilization:norman/bakery", bakery.blueprintId());
        assertEquals(origin, bakery.origin());
        assertEquals(0, bakery.completedOnStep(), "should record the step it finished on");
    }

    @Test
    void buildingCompletedWhileUnloadedIsRecordedButNotDrawn() {
        FakeBridge bridge = new FakeBridge();
        bridge.loaded = false;
        Settlement settlement = settlementWithBuilders(
                2, new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 2));
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
                2, new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 2));
        SimWorld world = worldWith(bridge, settlement);

        world.step();
        assertTrue(bridge.materialized.isEmpty());

        // The player comes back.
        bridge.loaded = true;
        world.step();

        assertEquals(1, bridge.materialized.size(), "should be drawn once the chunk is available");
        assertEquals("civilization:norman/bakery@(10, 64, 10)", bridge.materialized.getFirst());
        assertTrue(settlement.pendingBuildings().isEmpty());
    }

    @Test
    void buildingIsNeverDrawnTwice() {
        FakeBridge bridge = new FakeBridge();
        Settlement settlement = settlementWithBuilders(
                2, new BuildTask("civilization:norman/bakery", new SimPos(10, 64, 10), 2));
        SimWorld world = worldWith(bridge, settlement);

        world.step();
        world.step();
        bridge.loaded = true;   // the player arrives; it is drawn once
        world.step();
        world.step();
        world.step();

        assertEquals(1, bridge.materialized.size(), "repeated steps must not repaint a finished building");
    }
}
