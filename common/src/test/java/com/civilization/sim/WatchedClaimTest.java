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
 * Two questions about one player, and the difference between them.
 *
 * <p>Millbrook is the town that started it. Its hall, mine and mill were all
 * logged {@code Materialized … surveyed false} — stamped in whole by the clock —
 * while the player who reported it stood at the town center 109 blocks away and
 * watched them appear in the distance. Asking at the site said those plots were
 * unwatched, and they were, by the observed radius; they were also perfectly
 * visible. So this file used to say <em>watchedness belongs to the claim</em>:
 * a player within {@code observed_radius} of any part of a town made the whole
 * town watched, and a watched town had no clock at all.
 *
 * <p>That answer was too big, and fault N6 of the 2026-09-19 playtest is the
 * bill. Run A's town finished 424 by 497 blocks across, 429 from the middle to
 * its furthest corner. A player in the square therefore made ground four hundred
 * blocks away "watched" — which meant it was neither raised by hand, because
 * nobody could walk there and back, nor by the clock, because the town counted as
 * watched. Eight buildings sat at {@code [PENDING placement]} for the whole run
 * under the words <em>waiting for hands, out of sight</em>. Out of sight is
 * precisely the case the clock exists for.
 *
 * <p>So there are two questions now, and they are different sizes.
 * {@link Settlement#isWatched(SimContext, SimPos)} asks whether anybody is
 * standing on <em>this</em> ground, within the observed radius, and it decides
 * where the world's blocks have to be made to agree with the ledger.
 * {@link Settlement#isOverlooked} asks whether anybody could <em>see</em> this
 * ground change — a whole view distance, twice the observed radius — and it is
 * what the build queue, the roads and the wall ask, because Millbrook was never a
 * question about work. It was a question about magic.
 *
 * <p>What neither of them decides any more is what a site produces. A farm, a
 * stand or a seam is worth the same on both sides of both lines; see
 * {@code WatchedBooksTest}.
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

    // --- the claim, the site, and the sight of it ---

    /**
     * The split fault N6 forced, measured on Millbrook's own arithmetic.
     *
     * <p>This was {@code aplayerInsideTheClaimWatchesEverySiteInIt} and it looped
     * over four sites asserting {@code isWatched} of every one: "every site of a
     * watched town is watched". That is the exact rule a 429-block claim made
     * absurd — under it, ground four hundred blocks from anybody was work no hand
     * could reach and no clock would touch, and run A's eight
     * {@code [PENDING placement]} buildings are what that looks like from inside
     * the game.
     *
     * <p>One player, one step, four pieces of ground, three different answers.
     */
    @Test
    void eachSiteAnswersForItsOwnGroundAndSightIsTheWiderQuestion() {
        Settlement town = town();
        Bridge bridge = new Bridge(new SimPos(100, 64, 0));
        SimContext ctx = new SimContext(bridge, 1, SimSettings.SANDBOX);

        assertTrue(town.isWatched(ctx), "he is standing in the claim");

        // The hall, 90 blocks off him: he is standing near enough to it that the
        // world's blocks there must be made to agree with the ledger.
        SimPos hall = new SimPos(90, 64, 90);
        assertTrue(town.isWatched(ctx, hall), "somebody is on that ground");
        assertTrue(town.isOverlooked(ctx, hall), "and can obviously see it");

        // The square (100 off) and the mill over the ridge (156 off): nobody is
        // standing on either, so neither is hands-only work — but both are well
        // inside a view distance, and a cottage must not stand up out of bare
        // ground while he is looking that way. This is Millbrook, and it is still
        // fixed.
        for (SimPos inSight : List.of(new SimPos(0, 64, 0), new SimPos(0, 64, -120))) {
            assertFalse(town.isWatched(ctx, inSight),
                    "nobody is standing at " + inSight + ", and the claim does not"
                            + " get to say otherwise");
            assertTrue(town.isOverlooked(ctx, inSight),
                    "but he would watch it change: " + inSight);
        }

        // The mine, 209 blocks off him and over his own horizon. Nobody can reach
        // it and nobody can see it, so it is the clock's — which is the whole of
        // the correction, and the difference between a town that builds itself
        // out and eight plots that wait forever.
        SimPos mine = new SimPos(-109, 64, 0);
        assertFalse(town.isWatched(ctx, mine), "no hand is anywhere near the mine");
        assertFalse(town.isOverlooked(ctx, mine),
                "and past a whole view distance there is nothing to watch appear,"
                        + " so the clock may have it");
    }

    /**
     * And the sight question is exactly twice the standing question, which is one
     * view distance at the shipped radius.
     */
    @Test
    void sightIsTwoObservedRadiiAndNotTheClaim() {
        Settlement town = town();
        SimPos site = new SimPos(0, 64, 0);
        double sight = RADIUS * Settlement.SIGHT_RADII;

        SimContext justInside = new SimContext(
                new Bridge(new SimPos((int) sight - 1, 64, 0)), 1, SimSettings.SANDBOX);
        SimContext justOutside = new SimContext(
                new Bridge(new SimPos((int) sight + 1, 64, 0)), 1, SimSettings.SANDBOX);

        assertTrue(town.isOverlooked(justInside, site));
        assertFalse(town.isOverlooked(justOutside, site),
                "a line drawn at a render distance rather than at a claim radius:"
                        + " the claim of a grown town is nothing a player can see"
                        + " across");
        assertFalse(town.isWatched(justInside, site),
                "and it is the wider of the two questions, not the same one");
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

    /**
     * A body is kept for work the clock will not do, and not for work it will.
     *
     * <p>This was {@code awatchedTownKeepsTheBodyItSentToTheFarSite}, and it
     * asserted only the first half: a builder past the release margin stays
     * embodied while anything at all is queued, because under the claim rule the
     * clock would never touch a queued plot and releasing him would strand the
     * work forever. Fault N6 of the 2026-09-19 playtest is that rule applied to a
     * town 424 by 497 blocks across. The crew was permanently <em>needed</em> at
     * whatever stood at the head of the queue, and the head of the queue was four
     * hundred blocks off — a walk no step ever finished. Eight buildings sat at
     * {@code [PENDING placement]} and the builders sat in the field on the way to
     * the first of them.
     *
     * <p>{@code Settlement.needsHandsFrom} takes the world now, and asks whether
     * the queue head is somewhere a player would actually see a building rise
     * ({@code Settlement.isOverlooked}). Ground nobody can see is the clock's, so
     * nobody is bent out of the distance rule to reach it — and the deadlock the
     * bend existed to prevent cannot arise, because the work is no longer waiting
     * on hands at all.
     */
    @Test
    void abodyIsKeptForWorkNoClockWillDoAndReleasedForWorkItWill() {
        SimPos farSide = new SimPos(-120, 64, 0);
        // The player stands on the ring rather than in the square, which is what
        // puts the far side of the claim out past the release margin at all: a
        // claim is only a little wider than the margin, and the two edges of it
        // are a quarter of a kilometer apart.
        Bridge bridge = new Bridge(new SimPos(CLAIM, 64, 0));

        // A queue head he would watch rise: it is thirty blocks from where he is
        // standing. Nothing may build it but hands, so the hand sent out to the
        // far side of the claim is kept however far away he has walked.
        Settlement needed = townWithBuilder(true);
        needed.enqueueBuild(surveyed(new SimPos(100, 64, 0)));
        Person kept = needed.residents().iterator().next();
        kept.setPosition(farSide);

        assertFalse(bridge.playerWithin(kept.position(),
                        RADIUS + EmbodimentPlanner.RELEASE_MARGIN),
                "he is well past the release margin");
        assertTrue(EmbodimentPlanner.plan(needed, bridge, SimSettings.SANDBOX)
                        .toRelease().isEmpty(),
                "and he stays, because that plot cannot be raised by anything else");

        // The same builder in the same place, with the queue head out on the far
        // side instead — 250 blocks from the player, past any view distance he
        // has. That is the clock's ground now, so the town is not waiting on him
        // and there is no reason to hold a body in a field nobody can see.
        Settlement clocks = townWithBuilder(true);
        clocks.enqueueBuild(surveyed(farSide));
        Person spare = clocks.residents().iterator().next();
        spare.setPosition(farSide);

        SimContext ctx = new SimContext(bridge, 1, SimSettings.SANDBOX);
        assertFalse(clocks.isOverlooked(ctx, farSide),
                "nobody could watch that plot go up");
        assertFalse(clocks.needsHandsFrom(spare, bridge, SimSettings.SANDBOX),
                "so the town does not want him there — which is what run A's crew"
                        + " was never told");
        assertTrue(EmbodimentPlanner.plan(clocks, bridge, SimSettings.SANDBOX)
                        .toRelease().contains(spare),
                "and he goes back to being a record rather than standing in a"
                        + " field four hundred blocks from anybody");
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
