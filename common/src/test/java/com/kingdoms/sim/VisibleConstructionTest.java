package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        Boolean lastSurveyed;

        SiteBridge(boolean loaded) {
            this.loaded = loaded;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) { return loaded; }
        @Override public boolean isLoaded(SimPos pos) { return loaded; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed, int facing) {
            stamped++;
            lastSurveyed = surveyed;
            return new Footprint(origin.y(), 3, 3, 3);
        }
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
    void aSurveyedSiteKeepsTheHeightTheBuildersWorkedTo() {
        Settlement s = townWithBuilders(1, false);
        BuildTask task = surveyedTask(200);       // siteY measured against real ground
        s.enqueueBuild(task);
        SiteBridge bridge = new SiteBridge(false);
        SimContext ctx = new SimContext(bridge, 0, SimSettings.SANDBOX);

        for (int step = 0; step < 30 && !s.buildQueue().isEmpty(); step++) {
            s.step(ctx);
        }
        bridge.loaded = true;
        s.step(ctx);

        assertEquals(Boolean.TRUE, bridge.lastSurveyed,
                "placement must not re-measure a site the builders already worked to — "
                        + "that is what put a stamped building a course above a started one");
    }

    @Test
    void anUnsurveyedSiteIsSnappedToTheGround() {
        Settlement s = townWithBuilders(1, false);
        // No siteY: planned and finished while its chunk was never loaded, so the
        // recorded height is a guess and placement has to find the real ground.
        BuildTask task = new BuildTask("test:house", new SimPos(10, 64, 10), 20);
        s.enqueueBuild(task);
        SiteBridge bridge = new SiteBridge(false);
        SimContext ctx = new SimContext(bridge, 0, SimSettings.SANDBOX);

        for (int step = 0; step < 30 && !s.buildQueue().isEmpty(); step++) {
            s.step(ctx);
        }
        bridge.loaded = true;
        s.step(ctx);

        assertEquals(Boolean.FALSE, bridge.lastSurveyed);
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
    void abuilderInTheWorldIsGivenAFairSpellBeforeTheClockRuns() {
        Settlement s = townWithBuilders(1, true);
        s.enqueueBuild(surveyedTask(200));
        SimContext ctx = new SimContext(new SiteBridge(true), 0, SimSettings.SANDBOX);

        // Inside the grace: the hands are there and might yet start.
        for (int step = 0; step < Settlement.WATCHED_BUILD_GRACE_STEPS; step++) {
            s.step(ctx);
        }

        assertEquals(1, s.buildQueue().size(),
                "the task is still queued, because no block has actually been laid");
        assertEquals(0.0, s.buildQueue().getFirst().completionFraction(), 1e-9,
                "and it reads as untouched while the crew still has its chance");
        assertTrue(s.buildings().isEmpty(), "and nothing was recorded as built");
    }

    @Test
    void aCrewThatNeverLaysABlockDoesNotStopTheTownBuilding() {
        // The failure this replaced an invariant for. Builders can be embodied
        // and standing on a loaded site and lay nothing for a very long time:
        // mob navigation cannot climb everything a town builds on, and /civ step
        // passes no game ticks at all — so the player who typed it is the switch
        // that turned the clock off while nothing turned the hands on. Ten
        // thousand steps of that used to leave everybody dead.
        Settlement s = townWithBuilders(1, true);
        s.enqueueBuild(surveyedTask(200));
        SimContext ctx = new SimContext(new SiteBridge(true), 0, SimSettings.SANDBOX);

        for (int step = 0; step < 120; step++) {
            s.step(ctx);
        }

        assertTrue(s.buildQueue().isEmpty() || s.buildQueue().getFirst().progress() > 0,
                "after a fair spell of nothing, the clock has to be what runs");
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

        // Not "the queue is empty" — a town with everything it wants starts
        // improving what it has, so there is always something queued. What matters
        // is that this build finished.
        assertEquals(1, s.buildings().size(),
                "...but with nobody embodied, it must still progress");
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

        assertEquals(1, s.buildings().size(),
                "the chunk is not loaded, so the clock is allowed to run");
        assertEquals(0, bridge.stamped, "with nowhere to put it yet");

        bridge.loaded = true;   // the player arrives
        s.step(ctx);
        assertEquals(1, bridge.stamped,
                "and it is stamped into the world whole, since no hand laid it");
    }

    @Test
    void theClockNeitherNeedsNorSpendsWhatABuildersHandsHold() {
        // The carry rule is the watched fidelity and only that. A town nobody is
        // looking at has no hands to fill and nowhere to walk to fill them, so
        // requiring a load here would stop unwatched construction outright. The
        // crew is given loads they were carrying when the player walked away, and
        // the clock has to ignore them in both directions: it must not wait for
        // them, and it must not spend them.
        Settlement s = townWithBuilders(2, false);
        // Held onto, not re-read from the roster afterwards: a town that raises a
        // house over twenty-five steps may well have taken somebody in, and a
        // newcomer carrying nothing says nothing about the rule.
        List<Person> loaded = List.copyOf(s.residents());
        for (Person person : loaded) {
            person.setCarry(TownStores.WOOD, 5);
        }
        s.enqueueBuild(surveyedTask(200));
        SimContext ctx = new SimContext(new SiteBridge(false), 0, SimSettings.SANDBOX);

        for (int step = 0; step < 25; step++) {
            s.step(ctx);
        }

        assertEquals(1, s.buildings().size(), "the clock raised it regardless");
        for (Person person : loaded) {
            assertEquals(5, person.carriedLoad(),
                    "and not one block of it came out of anybody's arms");
            assertEquals(TownStores.WOOD, person.carriedMaterial());
        }
    }

    @Test
    void awatchedCrewThatCanNeverLayABlockDoesNotWedgeTheQueue() {
        // The regression the carry rule most risks, and the reason the stall
        // assist was closed rather than deleted. Builders can now refuse to lay
        // anything at all — an empty town, or shelves they cannot path to — and
        // the assist no longer papers over it by placing blocks for empty hands.
        // A watched town that could never finish a building would be a worse bug
        // than the one the rule fixes, so the simulation's own patience has to be
        // what ends it: WATCHED_BUILD_GRACE_STEPS of nothing, and then the head
        // stops being the head.
        //
        // Deliberately says nothing about how it resolves. Whether a build the
        // hands abandoned should be raised free, charged for, or given up on is
        // an older question than this rule and lives in advanceBuildQueue; all
        // that is asserted here is that the site does not sit there forever.
        Settlement s = townWithBuilders(1, true);
        BuildTask stuck = surveyedTask(200);
        s.enqueueBuild(stuck);
        SimContext ctx = new SimContext(new SiteBridge(true), 0, SimSettings.SANDBOX);

        // The view layer stands in as a crew with nothing in hand: it is given
        // work every step and lays not one block of it.
        for (int step = 0; step < Settlement.WATCHED_BUILD_GRACE_STEPS + 2; step++) {
            s.step(ctx);
        }

        assertFalse(s.buildQueue().contains(stuck),
                "a site that cannot be worked has to come off the queue eventually");
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
