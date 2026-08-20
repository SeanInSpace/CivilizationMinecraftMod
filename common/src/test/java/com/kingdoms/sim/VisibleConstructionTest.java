package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the rule that when somebody is watching, the blocks that actually stand
 * are what "how far along is it" means — and that a build therefore cannot finish
 * on the clock while its walls are still half up.
 */
class VisibleConstructionTest {

    private static final BuildingType HOUSE =
            new BuildingType("test:house", 20, 1, 1, 0, 80, 4);

    /** A bridge with chunk load switchable, since that is the whole subject here. */
    private static final class SiteBridge implements WorldBridge {
        boolean loaded;
        int stamped;

        SiteBridge(boolean loaded) {
            this.loaded = loaded;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) { return loaded; }
        @Override public boolean isLoaded(SimPos pos) { return loaded; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public void materializeBlueprint(String blueprintId, SimPos origin) { stamped++; }
        @Override public void log(String message) { }
    }

    private static Settlement townWithBuilders(int builders, boolean embodied) {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        s.setCatalogue(List.of(HOUSE));
        for (int i = 0; i < builders; i++) {
            Person p = new Person(Person.Id.random(), "Builder " + i, Profession.BUILDER, s.centre());
            p.setEmbodied(embodied);   // standing in the world, or only on the roster
            s.addResident(p);
        }
        return s;
    }

    /** A task the view layer has surveyed: all laying, no ground in the way. */
    private static BuildTask surveyedTask(int placeWork) {
        return surveyedTask(placeWork, 0);
    }

    /** A surveyed task with {@code digWork} of excavation charged on top. */
    private static BuildTask surveyedTask(int placeWork, int digWork) {
        BuildTask task = new BuildTask("test:house", new SimPos(10, 64, 10), 20);
        task.setSiteY(64);
        task.setPlan(placeWork + digWork, placeWork);
        return task;
    }

    /** Stand in for the view layer: do whatever the builders have been cleared for. */
    private static void work(BuildTask task, int steps, int costEach) {
        for (int i = 0; i < steps && task.canAfford(costEach); i++) {
            task.recordStepDone(costEach);
        }
    }

    @Test
    void diggingCostsMoreThanLaying() {
        // 10 blocks to lay, plus ground worth 30 to shift: the same pace, but the
        // job is four times the work, and the readout has to say so.
        BuildTask task = surveyedTask(10, 30);
        assertEquals(40, task.planWork());

        task.grantWork(40);
        task.recordStepDone(4);   // one stubborn block of ground
        assertEquals(0.1, task.completionFraction(), 1e-9,
                "one dig is worth four lays, and reads as four lays of progress");

        task.recordStepDone(1);   // one block laid
        assertEquals(0.125, task.completionFraction(), 1e-9);
    }

    @Test
    void percentageCountsBlocksNotTheClock() {
        BuildTask task = surveyedTask(200);
        task.addProgress(20);   // the work clock says finished

        assertEquals(0.0, task.completionFraction(), 1e-9,
                "but nothing is standing, so it reads as nothing built");

        task.grantWork(100);
        work(task, 100, 1);
        assertEquals(0.5, task.completionFraction(), 1e-9);
    }

    @Test
    void layingBlocksDragsTheWorkFigureAlong() {
        BuildTask task = surveyedTask(100);
        task.grantWork(25);
        work(task, 25, 1);
        // A quarter of a 20-work job, so the abstract figure has to agree.
        assertEquals(5, task.progress(),
                "so a build that loses its audience carries on from where the masonry got to");
    }

    @Test
    void abuildIsSpreadOverTheSameNumberOfStepsItAlwaysTook() {
        BuildTask task = surveyedTask(200);
        // 200 blocks of laying over 20 builder-steps: one builder lays ten a step.
        assertEquals(10, task.workForStep(1));
        assertEquals(20, task.workForStep(2));
        assertEquals(0, task.workForStep(0));

        // Excavation does not change that rate, so it is charged on top rather
        // than squeezed into the same budget: digging a site out takes longer.
        BuildTask onAHill = surveyedTask(200, 300);
        assertEquals(10, onAHill.workForStep(1), "same pace...");
        assertEquals(500, onAHill.planWork(), "...over more work, so more steps");
    }

    @Test
    void buildersAreNeverClearedForMoreThanThePlanHasLeft() {
        BuildTask task = surveyedTask(10);
        task.grantWork(1000);
        assertEquals(10, task.pendingWork());

        work(task, 10, 1);
        task.grantWork(1000);
        assertEquals(0, task.pendingWork(), "a finished plan grants nothing further");
    }

    @Test
    void abuilderInTheWorldMeansNoClockRunsBesideThem() {
        Settlement s = townWithBuilders(1, true);
        s.enqueueBuild(surveyedTask(200));
        SimContext ctx = new SimContext(new SiteBridge(true), 0, SimSettings.SANDBOX);

        // Far more steps than the 20 builder-steps the job costs.
        for (int step = 0; step < 60; step++) {
            s.step(ctx);
        }

        assertEquals(1, s.buildQueue().size(),
                "the task is still queued, because no block has actually been laid");
        assertEquals(0.0, s.buildQueue().getFirst().completionFraction(), 1e-9,
                "and it reads as untouched, however long the clock ran");
        assertTrue(s.buildings().isEmpty(), "and nothing was recorded as built");
        assertTrue(s.buildQueue().getFirst().pendingWork() > 0,
                "the builders are simply cleared for work nobody has done yet");
    }

    @Test
    void aloadedChunkWithEveryoneReleasedDoesNotFreeze() {
        // Roughly 128-160 blocks out: chunks still loaded, but every settler has
        // been released. Nobody can lay a block, so the clock has to be what runs
        // — otherwise construction stops dead in a band you walk through often.
        Settlement s = townWithBuilders(1, false);
        s.enqueueBuild(surveyedTask(200));
        SiteBridge bridge = new SiteBridge(true);   // loaded...
        SimContext ctx = new SimContext(bridge, 0, SimSettings.SANDBOX);

        for (int step = 0; step < 25; step++) {
            s.step(ctx);
        }

        assertTrue(s.buildQueue().isEmpty(), "...but with nobody embodied, it must still progress");
        assertEquals(1, s.buildings().size());
        assertEquals(1, bridge.stamped, "and it is stamped in, since no hand laid it");
    }

    @Test
    void anunwatchedBuildStillFinishesOnTheClock() {
        Settlement s = townWithBuilders(1, false);
        s.enqueueBuild(surveyedTask(200));
        SiteBridge bridge = new SiteBridge(false);
        SimContext ctx = new SimContext(bridge, 0, SimSettings.SANDBOX);

        for (int step = 0; step < 25; step++) {
            s.step(ctx);
        }

        assertTrue(s.buildQueue().isEmpty(), "the chunk is not loaded, so the clock is allowed to run");
        assertEquals(1, s.buildings().size());
        assertEquals(0, bridge.stamped, "with nowhere to put it yet");

        bridge.loaded = true;   // the player arrives
        s.step(ctx);
        assertEquals(1, bridge.stamped,
                "and it is stamped into the world whole, since no hand laid it");
    }

    @Test
    void ahandBuiltStructureIsNeverStampedOverOnCompletion() {
        Settlement s = townWithBuilders(1, true);
        BuildTask task = surveyedTask(50);
        s.enqueueBuild(task);
        SiteBridge bridge = new SiteBridge(true);
        SimContext ctx = new SimContext(bridge, 0, SimSettings.SANDBOX);

        // Stand in for the view layer: whatever the step clears, gets laid.
        for (int step = 0; step < 40 && !s.buildQueue().isEmpty(); step++) {
            s.step(ctx);
            while (task.canAfford(1)) {
                task.recordStepDone(1);
            }
        }

        assertTrue(s.buildQueue().isEmpty(), "the build finished once its last block went down");
        assertEquals(1, s.buildings().size());
        assertEquals(0, bridge.stamped,
                "and nothing was ever stamped on top of the builders' work");
    }
}
