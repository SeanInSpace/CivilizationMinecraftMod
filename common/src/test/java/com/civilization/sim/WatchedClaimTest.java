package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.HaulTask;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.view.EmbodimentPlanner;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Watchedness belongs to the claim, not to the site.
 *
 * <p>Millbrook is the town that made the case. Its hall, mine and mill were all
 * logged {@code Materialized … surveyed false} — stamped in whole by the clock —
 * while the player who reported it stood at the town center 109 blocks away and
 * watched them appear in the distance. Every one of those sites was honestly
 * unwatched by the old test, because the old test asked at the site and the ring
 * is wider than the observed radius. The rule was right and the question was
 * wrong.
 *
 * <p>So the question is asked of the town now. A player within
 * {@code observed_radius} of any part of a town's claim makes the whole town
 * watched, and a watched town has no clock at all: every site is raised by hand,
 * the roads are walked out, the wall goes up post by post, and work with nobody
 * at it waits and says so.
 */
class WatchedClaimTest {

    private static final BuildingType HOUSE =
            new BuildingType("test:house", 20, 1, 1, 0, 80, 4);

    private static final double RADIUS = SimSettings.SANDBOX.observedRadius();

    /** A town 130 blocks across the claim: wider than the observed radius, as real ones are. */
    private static final int CLAIM = 130;

    /** A world with at most one player in it, standing still. */
    private static class Bridge implements WorldBridge {
        SimPos player;
        int stamped;
        int asked;

        Bridge(SimPos player) {
            this.player = player;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) {
            asked++;
            return player != null
                    && Math.hypot(pos.x() - player.x(), pos.z() - player.z()) <= radius;
        }

        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            stamped++;
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static Settlement town() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Millbrook", new SimPos(0, 64, 0), CLAIM);
        town.setCatalog(List.of(HOUSE));
        return town;
    }

    private static Settlement townWithBuilder(boolean embodied) {
        Settlement town = town();
        Person hand = new Person(
                Person.Id.random(), "Alder", Profession.BUILDER, town.center());
        hand.setEmbodied(embodied);
        town.addResident(hand);
        return town;
    }

    private static BuildTask surveyed(SimPos at) {
        BuildTask task = new BuildTask("test:house", at, 20);
        task.setSiteY(at.y());
        task.setPlan(200, 200);
        return task;
    }

    // --- the claim is the unit ---

    @Test
    void aplayerInsideTheClaimWatchesEverySiteInIt() {
        // Millbrook's own arithmetic: the player is 100 blocks from the center,
        // which is nowhere near either outlying plot, and every one of them is
        // his to watch because all of them are his town.
        Settlement town = town();
        Bridge bridge = new Bridge(new SimPos(100, 64, 0));
        SimContext ctx = new SimContext(bridge, 1, SimSettings.SANDBOX);

        assertTrue(town.isWatched(ctx), "he is standing in the claim");
        for (SimPos site : List.of(
                new SimPos(0, 64, 0),        // the square, 100 off him
                new SimPos(-109, 64, 0),     // the mine, 209 off him
                new SimPos(0, 64, -120),     // the mill, over the ridge
                new SimPos(90, 64, 90))) {   // the hall
            assertTrue(town.isWatched(ctx, site),
                    "every site of a watched town is watched: " + site);
        }
    }

    @Test
    void aplayerOutsideTheClaimAndItsRadiusWatchesNothing() {
        Settlement town = town();
        Bridge bridge = new Bridge(new SimPos(CLAIM + (int) RADIUS + 40, 64, 0));
        SimContext ctx = new SimContext(bridge, 1, SimSettings.SANDBOX);

        assertFalse(town.isWatched(ctx), "past the claim and past the radius both");
        assertFalse(town.isWatched(ctx, town.center()));
        assertFalse(town.isWatched(ctx, new SimPos(-109, 64, 0)));
    }

    @Test
    void theEdgeOfTheClaimPlusTheRadiusIsTheLine() {
        Settlement town = town();
        SimContext justInside = new SimContext(
                new Bridge(new SimPos(CLAIM + (int) RADIUS - 1, 64, 0)), 1, SimSettings.SANDBOX);
        SimContext justOutside = new SimContext(
                new Bridge(new SimPos(CLAIM + (int) RADIUS + 1, 64, 0)), 1, SimSettings.SANDBOX);

        assertTrue(town.isWatched(justInside));
        assertFalse(town().isWatched(justOutside));
    }

    @Test
    void asiteOutsideTheClaimStillAnswersForItsOwnGround() {
        // The one thing the claim circle cannot speak for: an outlying field or
        // mine sited past the ring, with somebody standing in it and the town
        // itself alone.
        Settlement town = town();
        SimPos outlier = new SimPos(600, 64, 0);
        Bridge bridge = new Bridge(outlier);
        SimContext ctx = new SimContext(bridge, 1, SimSettings.SANDBOX);

        assertFalse(town.isWatched(ctx), "nobody is anywhere near the town");
        assertTrue(town.isWatched(ctx, outlier), "but somebody is standing on that ground");
    }

    // --- decided once a step ---

    @Test
    void theAnswerIsDecidedOnceAstepAndRemembered() {
        Settlement town = town();
        Bridge bridge = new Bridge(new SimPos(0, 64, 0));
        SimContext step = new SimContext(bridge, 7, SimSettings.SANDBOX);

        assertTrue(town.isWatched(step));
        int askedOnce = bridge.asked;

        // The player leaves halfway through the step. A step is one moment and
        // has one answer: the town does not get to run half its lanes on hands
        // and half on the clock.
        bridge.player = null;
        assertTrue(town.isWatched(step), "the step keeps the answer it began with");
        assertEquals(askedOnce, bridge.asked, "and does not ask the world again");

        // The next step reads the world afresh.
        assertFalse(town.isWatched(new SimContext(bridge, 8, SimSettings.SANDBOX)));
    }

    // --- what a far site says while it waits ---

    @Test
    void afarSiteInAwatchedTownSaysItIsWaitingOutOfSight() {
        Settlement town = townWithBuilder(false);
        SimPos overTheRidge = new SimPos(109, 64, 0);
        BuildTask task = surveyed(overTheRidge);
        town.enqueueBuild(task);
        Bridge bridge = new Bridge(new SimPos(0, 64, 0));

        town.step(new SimContext(bridge, 1, SimSettings.SANDBOX));

        assertTrue(town.buildQueue().contains(task), "no clock raised it");
        assertEquals(0, bridge.stamped);
        assertEquals("waiting for hands at (109, 0), out of sight", task.waitingOnHands(),
                "a player who cannot see the stalled plot has to be told where it is");
    }

    @Test
    void asiteTheplayerCanSeeSaysSoInTheOlderWords() {
        Settlement town = townWithBuilder(false);
        BuildTask task = surveyed(new SimPos(10, 64, 10));
        town.enqueueBuild(task);

        town.step(new SimContext(new Bridge(new SimPos(0, 64, 0)), 1, SimSettings.SANDBOX));

        assertEquals("no builder has reached the site", task.waitingOnHands());
    }

    // --- and the clock is untouched where nobody is ---

    @Test
    void anunwatchedTownIsBuiltByTheClockExactlyAsBefore() {
        Settlement town = townWithBuilder(false);
        BuildTask task = surveyed(new SimPos(10, 64, 10));
        town.enqueueBuild(task);
        Bridge bridge = new Bridge(null);

        for (int step = 1; step <= 500; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertFalse(town.buildQueue().contains(task), "the clock saw it through");
        assertEquals(1, town.countBuildings("test:house"));
    }

    // --- bodies for the far work ---

    @Test
    void awatchedTownKeepsTheBodyItSentToTheFarSite() {
        // The deadlock the claim rule would otherwise create. The plot is inside
        // the claim, so the clock will not touch it; the builder walking out to
        // it passes the release margin, and releasing him would leave the work
        // waiting forever on hands it was never going to be allowed to have.
        Settlement town = townWithBuilder(true);
        SimPos farSide = new SimPos(-120, 64, 0);
        town.enqueueBuild(surveyed(farSide));
        Person builder = town.residents().iterator().next();
        builder.setPosition(farSide);
        // The player stands on the ring rather than in the square, which is what
        // puts the far side of the claim out past the release margin at all: a
        // claim is only a little wider than the margin, and the two edges of it
        // are a quarter of a kilometer apart.
        Bridge bridge = new Bridge(new SimPos(CLAIM, 64, 0));

        assertFalse(bridge.playerWithin(builder.position(),
                        RADIUS + EmbodimentPlanner.RELEASE_MARGIN),
                "he is well past the release margin");

        EmbodimentPlanner.Plan plan =
                EmbodimentPlanner.plan(town, bridge, SimSettings.SANDBOX);

        assertTrue(plan.toRelease().isEmpty(), "and he stays, because the town needs him");
    }

    @Test
    void awatchedTownEmbodiesTheHandsItsFarWorkWaitsOn() {
        Settlement town = townWithBuilder(false);
        town.enqueueBuild(surveyed(new SimPos(109, 64, 0)));
        Person builder = town.residents().iterator().next();
        builder.setPosition(new SimPos(-120, 64, 0));   // the far side of the claim
        Bridge bridge = new Bridge(new SimPos(100, 64, 0));

        assertFalse(bridge.playerWithin(builder.position(), RADIUS),
                "nobody is near him");

        EmbodimentPlanner.Plan plan =
                EmbodimentPlanner.plan(town, bridge, SimSettings.SANDBOX);

        assertEquals(1, plan.toEmbody().size(),
                "a watched town's build queue waits on hands, so the hands get bodies");
    }

    @Test
    void ahaulerKeepsHisBodyUntilTheLoadIsDown() {
        Settlement town = town();
        Building store = new Building("test:house", new SimPos(0, 64, 0), 1, true);
        town.addBuilding(store);
        Person carrier = new Person(
                Person.Id.random(), "Bryn", Profession.MILLER, new SimPos(-125, 64, 0));
        carrier.setEmbodied(true);
        carrier.setHaul(new HaulTask(TownStores.WOOD, HaulTask.Store.STORE,
                new SimPos(-125, 64, 0), HaulTask.Store.STORE, new SimPos(0, 64, 0), 4));
        town.addResident(carrier);
        Bridge bridge = new Bridge(new SimPos(100, 64, 0));

        EmbodimentPlanner.Plan plan =
                EmbodimentPlanner.plan(town, bridge, SimSettings.SANDBOX);

        assertTrue(plan.toRelease().isEmpty(), "an errand under way is not abandoned");
    }

    @Test
    void anidlerAcrossAwatchedClaimIsStillLeftAsArecord() {
        // The other half: the claim rule is about work, not about population.
        // Nobody is waiting on this man, so he has no reason to be an entity.
        Settlement town = town();
        Person idler = new Person(
                Person.Id.random(), "Cuth", Profession.IDLER, new SimPos(-125, 64, 0));
        town.addResident(idler);
        Bridge bridge = new Bridge(new SimPos(100, 64, 0));

        SimContext ctx = new SimContext(bridge, 1, SimSettings.SANDBOX);
        assertTrue(town.isWatched(ctx), "the town is watched...");
        assertTrue(EmbodimentPlanner.plan(town, bridge, SimSettings.SANDBOX).isEmpty(),
                "...and he is still a record two hundred blocks from anybody");
    }

    @Test
    void nobodyIsEmbodiedForAnunwatchedTown() {
        Settlement town = townWithBuilder(false);
        town.enqueueBuild(surveyed(new SimPos(109, 64, 0)));
        Bridge bridge = new Bridge(null);

        assertTrue(EmbodimentPlanner.plan(town, bridge, SimSettings.SANDBOX).isEmpty(),
                "an unwatched town's work is the clock's, and needs no bodies");
    }
}
