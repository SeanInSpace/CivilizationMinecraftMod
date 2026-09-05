package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.BuildLoad;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.work.PublicWorks;
import com.kingdoms.sim.work.Worksite;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public works: the things a town builds that are not buildings.
 *
 * <p>The wall and the roads used to appear — the simulation decided how much of
 * each existed and a layer stamped it in, with every builder in the village
 * standing somewhere else. They are jobs somebody walks to now, and the shape
 * they have in common is {@link Worksite}, so the next one is a class answering
 * three questions rather than another worker and another tick pass.
 */
class PublicWorksTest {

    private static Settlement town() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        town.addResident(new Person(
                Person.Id.random(), "Hand", Profession.BUILDER, town.centre()));
        return town;
    }

    /** Puts the town's builder into the world, which is what makes hands possible. */
    private static void embodyTheBuilder(Settlement town) {
        for (Person person : town.residents()) {
            person.setEmbodied(true);
        }
    }

    private static Worksite roads() {
        return new PublicWorks.RoadWork();
    }

    private static Worksite wall() {
        return new PublicWorks.WallWork();
    }

    private static Worksite dismantling() {
        return new PublicWorks.DismantleWork();
    }

    /** A town with a staked ring and enough in the bank to raise it. */
    private static Settlement walled(WorldBridge bridge) {
        Settlement town = town();
        town.bank(1000);
        town.setPerimeter(PerimeterPlanner.stake(town,
                new SimContext(bridge, 0, SimSettings.SANDBOX)));
        return town;
    }

    // --- roads ---

    @Test
    void aPlannedRoadIsNotAnOpenedRoad() {
        // The distinction the whole change turns on. Planning a street is the
        // simulation's business; opening one is a job with a place to stand.
        Settlement town = town();
        town.paths().add(new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(8, 64, 0)));

        assertFalse(town.paths().isOpened(0), "planned, and nobody has walked it yet");
        assertNotNull(roads().nextStation(town), "so there is a job to go and do");
    }

    /**
     * A run is opened when the crew reaches the far end of it, not before.
     *
     * <p>The claim that changed, and it is the whole of the roads complaint. A
     * station used to be the middle of a run and one swing there opened the
     * entire street — the paving was then stamped in behind the builder by a
     * sweep. A crew paves as it walks now, so a column done is not a street
     * opened: the town's books have no notion of half a street, so the opening
     * is recorded once, at the end, by {@code finishStretch}.
     */
    @Test
    void aStretchIsOpenedAtTheEndOfTheWalkAndNotAtTheStart() {
        Settlement town = town();
        town.paths().add(new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(8, 64, 0)));

        roads().completeOne(town, true);
        assertFalse(town.paths().isOpened(0),
                "one cross-section paved is not a street anybody can walk");

        roads().finishStretch(town);

        assertTrue(town.paths().isOpened(0));
        assertNull(roads().nextStation(town), "nothing left outstanding");
    }

    @Test
    void aRoadCrewStartsAtTheNearEndOfTheRun() {
        // The station used to be the middle, because one walk covered the whole
        // stretch. The crew walks it now, so they start at one end of it.
        Settlement town = town();
        PathNetwork.Segment run = new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(10, 64, 0));
        town.paths().add(run);

        assertEquals(run.positions().getFirst(), roads().nextStation(town));
    }

    @Test
    void aTownWithNoRoadsPlannedHasNoRoadWorkToDo() {
        assertNull(roads().nextStation(town()));
    }

    @Test
    void aTrackIsTroddenRatherThanCarried() {
        // Why a paving crew never walks to the storehouse: there is nothing
        // there for them. A dirt path is the ground that was already underfoot.
        assertNull(roads().material(),
                "a shovel job has no load, so nothing can hold it up but the walking");
    }

    // --- the wall ---

    @Test
    void theWallIsWorkedInTheOrderItWasStaked() {
        // A wall raised nearest-first closes as a scatter of disconnected posts.
        Settlement town = walled(new QuietBridge());

        SimPos first = wall().nextStation(town);
        assertEquals(town.perimeter().ringPositions().get(0), first);

        wall().pay(town);
        wall().completeOne(town, true);

        assertEquals(town.perimeter().ringPositions().get(1), wall().nextStation(town),
                "the next one along, so the line closes as a line");
    }

    /**
     * The wall gives way to the timber a building needs — by a whole load.
     *
     * <p>The claim that changed: the reserve used to be measured against one
     * post, because that is what the wall took from the pooled ledger between
     * one check and the next. A builder takes a whole load at the shelves now,
     * so a town at exactly the reserve passed the check and dropped fifteen
     * planks under it — and those fifteen were then in somebody's arms, where
     * the build queue the reserve is kept for cannot see them at all.
     */
    @Test
    void theWallGivesWayToTimberABuildingNeeds() {
        Settlement town = walled(new QuietBridge());
        town.stores().take(TownStores.WOOD, town.woodStock());
        town.stores().add(TownStores.WOOD, PerimeterPlanner.TIMBER_KEPT_FOR_BUILDING);

        assertFalse(wall().isWorthStarting(town),
                "that timber is spoken for; the fence can wait");

        town.stores().add(TownStores.WOOD, PerimeterPlanner.WOOD_PER_POST);
        assertFalse(wall().isWorthStarting(town),
                "one post's worth to spare is not one load's worth, and a load is"
                        + " what the builder actually takes off the shelf");

        town.stores().add(TownStores.WOOD, BuildLoad.LOAD_SIZE);
        assertTrue(wall().isWorthStarting(town), "and now there is a load to spare");
    }

    /**
     * A post is a plank and three coin, and the crew is charged for the coin.
     *
     * <p>The claim that changed. {@code pay} used to take both out of the ledger,
     * which was right while the wall was a number going up: nobody was carrying
     * anything. A watched wall is planks out of a storehouse now, and the plank
     * leaves the books when a builder shoulders it — so charging for it again at
     * the line would take two logs out of a town for one post, which is the same
     * double charge a carried load prevents everywhere else in construction.
     */
    @Test
    void aPostAtTheLineCostsTheCoinAndNotTheTimberAgain() {
        Settlement town = walled(new QuietBridge());
        int timber = town.woodStock();
        int coin = town.treasury();

        assertEquals(TownStores.WOOD, wall().material(),
                "a post is a plank somebody carries out of the storehouse");
        assertTrue(wall().pay(town));

        assertEquals(timber, town.woodStock(),
                "the plank left the books at the storehouse, not here");
        assertEquals(coin - PerimeterPlanner.COIN_PER_POST, town.treasury());
    }

    @Test
    void theWholePriceOfAPostIsStillAPlankAndThreeCoin() {
        // The clock's half, which is what an unwatched town pays. The two paths
        // have to come to the same total or a wall would be cheaper to build
        // while nobody was looking at it.
        Settlement town = walled(new QuietBridge());
        int timber = town.woodStock();
        int coin = town.treasury();

        assertTrue(PerimeterPlanner.payForPost(town));

        assertEquals(timber - PerimeterPlanner.WOOD_PER_POST, town.woodStock());
        assertEquals(coin - PerimeterPlanner.COIN_PER_POST, town.treasury());
    }

    /**
     * Where there is a hand there is no clock.
     *
     * <p>The whole complaint, stated as a measurement: a watched town's wall
     * advances because somebody planted a post, and not because a step went by.
     * The bridge says the line is loaded and the town has an embodied builder,
     * which is exactly the case in which a player is standing there watching.
     */
    @Test
    void aWatchedWallIsRaisedByItsBuildersAndNotByTheStep() {
        Settlement town = walled(new LoadedBridge());
        embodyTheBuilder(town);
        SimContext ctx = new SimContext(new LoadedBridge(), 1, SimSettings.SANDBOX);

        for (int step = 1; step <= 20; step++) {
            PerimeterPlanner.advance(town, ctx);
        }
        assertEquals(0, town.perimeter().laid(),
                "twenty steps of clock raised the wall of a town with hands on it");

        wall().pay(town);
        wall().completeOne(town, true);
        assertEquals(1, town.perimeter().laid(), "and the post somebody planted did");
    }

    @Test
    void anUnwatchedWallIsStillRaisedByTheStep() {
        // The other half, unchanged: a town nobody is looking at has no hands to
        // fill, so it goes on paying per post out of the ledger.
        Settlement town = walled(new QuietBridge());
        embodyTheBuilder(town);
        SimContext ctx = new SimContext(new QuietBridge(), 1, SimSettings.SANDBOX);

        PerimeterPlanner.advance(town, ctx);

        assertTrue(town.perimeter().laid() > 0,
                "nobody is there, so the clock is all the town has");
    }

    // --- the line the wall replaced ---

    /**
     * A retired ring inside a wider standing one, with every post of it raised.
     *
     * <p>The two loops must not share ground, or {@link Perimeter#retiredPositions}
     * drops the shared columns — rightly, since pulling down a post the standing
     * wall is built on would be knocking a hole in the new wall.
     */
    private static Perimeter reStaked() {
        List<SimPos> older = box(8);
        Perimeter old = new Perimeter(older, List.of(), 0);
        return new Perimeter(box(24), List.of(), 0,
                List.of(new Perimeter.Retired(older, old.length())), 0L);
    }

    private static List<SimPos> box(int half) {
        return List.of(
                new SimPos(-half, 64, -half), new SimPos(half, 64, -half),
                new SimPos(half, 64, half), new SimPos(-half, 64, half));
    }

    @Test
    void theOldLineComesDownPostByPostInTheOrderItWasWalked() {
        Settlement town = town();
        town.setPerimeter(reStaked());
        List<SimPos> old = town.perimeter().retiredPositions();

        assertFalse(old.isEmpty(), "a re-staked town has an old line to take down");
        assertEquals(old.get(0), dismantling().nextStation(town));

        dismantling().completeOne(town, true);

        assertEquals(1, town.perimeter().pulled());
        assertEquals(old.get(1), dismantling().nextStation(town),
                "the next one along, so a crew is not sent across the town for each");
    }

    @Test
    void halfTheTimberOfAPulledPostComesBack() {
        // A post that has stood through a generation of weather is firewood
        // rather than lumber. Getting the whole wall back would make moving one
        // free, and a town would re-stake at a profit.
        Settlement town = town();
        town.setPerimeter(reStaked());
        town.stores().take(TownStores.WOOD, town.woodStock());

        for (int post = 0; post < 16; post++) {
            dismantling().completeOne(town, true);
        }

        assertEquals(8 * PerimeterPlanner.WOOD_PER_POST, town.woodStock(),
                "sixteen posts up, eight planks back");
    }

    @Test
    void aPostNobodyPulledUpYieldsNoTimber() {
        // Where the salvage rule would otherwise mint wood. The away sweep pulls
        // down whatever the crew never reached and keeps no count, so a crew
        // walks over cleared ground often -- and a town paid half a plank for
        // every second empty position would be making timber out of nothing and
        // moving its wall at a profit.
        Settlement town = town();
        town.setPerimeter(reStaked());
        town.stores().take(TownStores.WOOD, town.woodStock());

        for (int post = 0; post < 16; post++) {
            dismantling().completeOne(town, false);
        }

        assertEquals(16, town.perimeter().pulled(),
                "the positions are still crossed off, or the crew walks them for ever");
        assertEquals(0, town.woodStock(), "and nothing came out of the ground");
    }

    @Test
    void aLineWhollyTakenDownIsNoLongerCarried() {
        Settlement town = town();
        town.setPerimeter(reStaked());
        int posts = town.perimeter().retiredPositions().size();

        for (int post = 0; post < posts; post++) {
            dismantling().completeOne(town, true);
        }

        assertTrue(town.perimeter().retired().isEmpty(),
                "the old wall is down, so the town stops remembering where it was");
        assertNull(dismantling().nextStation(town), "and there is nothing left to do");
    }

    @Test
    void aTownThatNeverMovedItsWallHasNothingToDismantle() {
        Settlement town = walled(new QuietBridge());
        assertNull(dismantling().nextStation(town));
    }

    // --- whose hands are on what ---

    /** A town with somewhere to keep its timber, so the wall has a shelf to draw on. */
    private static Settlement withAStorehouse(Settlement town) {
        Building store = new Building("kingdoms:storehouse", new SimPos(4, 64, 4), 1, true);
        store.stores().add(TownStores.WOOD, 512);
        town.addBuilding(store);
        return town;
    }

    private static PathNetwork.Segment aRunToOpen(Settlement town) {
        PathNetwork.Segment run = new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(8, 64, 0));
        town.paths().add(run);
        return run;
    }

    @Test
    void aTownWithNobodyInItHasNoHandsOnAnything() {
        // Which is what leaves the clock everything. The whole rule is a
        // negation: where there is a hand there is no clock.
        Settlement town = town();
        aRunToOpen(town);

        assertNull(PublicWorks.handsAreOn(town, new LoadedBridge()),
                "a builder on the roster is not a builder standing in the world");
    }

    @Test
    void aBuilderWithAHouseToRaiseHasNoHandsToSpare() {
        Settlement town = town();
        embodyTheBuilder(town);
        aRunToOpen(town);
        town.enqueueBuild(new com.kingdoms.sim.settlement.BuildTask(
                "kingdoms:cottage", new SimPos(20, 64, 20), 40));

        assertNull(PublicWorks.handsAreOn(town, new LoadedBridge()),
                "shelter and stores before roads and walls");
        assertTrue(PublicWorks.leaveItToTheCrew(town, new LoadedBridge(), roads()),
                "and the clock still waits for them, because a house is a finite"
                        + " thing and they are on their way back to the street");
    }

    @Test
    void aTownWithOnlyRoadsOutstandingHasItsHandsOnTheRoads() {
        Settlement town = town();
        embodyTheBuilder(town);
        aRunToOpen(town);

        assertTrue(PublicWorks.handsAreOn(town, new LoadedBridge())
                        instanceof PublicWorks.RoadWork,
                "nothing else is outstanding, so this is where they would be sent");
    }

    /**
     * A wall to raise means nobody is on the roads, so the road clock still runs.
     *
     * <p>The rule the reordering forced. Roads used to be the first work a crew
     * was offered, so "is anybody here a builder" and "is anybody on the roads"
     * were the same question, and the road clock could stand aside for the first.
     * Under the wall they are not the same question at all: a town with a ring
     * still going up has builders who are never coming to the street, and a clock
     * that waited for them would leave it opened by nobody at all for as long as
     * the wall took.
     */
    @Test
    void aCrewOnTheWallIsNotACrewOnTheRoads() {
        Settlement town = withAStorehouse(walled(new LoadedBridge()));
        embodyTheBuilder(town);
        aRunToOpen(town);

        assertFalse(PublicWorks.handsAreOn(town, new LoadedBridge())
                        instanceof PublicWorks.RoadWork,
                "the wall is above the roads, and that is where the crew goes");
        assertFalse(PublicWorks.leaveItToTheCrew(town, new LoadedBridge(), roads()),
                "so the clock must not wait for them: a ring is hundreds of posts"
                        + " and the street would be opened by nobody at all");
    }

    @Test
    void aCrewOnTheRoadsStandsTheRoadClockDown() {
        Settlement town = town();
        embodyTheBuilder(town);
        aRunToOpen(town);

        assertTrue(PublicWorks.leaveItToTheCrew(town, new LoadedBridge(), roads()),
                "somebody is walking it out, so nothing else should");
    }

    @Test
    void anUnwatchedTownStillOpensOneStretchAStep() {
        // The other half, unchanged: nobody is there, so the clock is all the
        // town has. PavedStreetsTest measures the same rule over a grown town.
        Settlement town = town();
        embodyTheBuilder(town);
        aRunToOpen(town);

        assertNull(PublicWorks.handsAreOn(town, new QuietBridge()),
                "the street is in a chunk nobody has loaded, so nobody is on it");
        assertFalse(PublicWorks.leaveItToTheCrew(town, new QuietBridge(), roads()),
                "and the clock is all the town has");
    }

    // --- and the order between them ---

    /**
     * The whole priority system: first in the list with a job to do wins.
     *
     * <p>The wall now comes before the roads, which is the other way round from
     * how this list started. A road is what lets everybody else get to work
     * faster — a good argument about a town still filling in, and the wall is not
     * staked until the charter, by which time the streets it encloses are mostly
     * walked out. What roads-first produced was a town answering every new
     * outlying shed with a fresh stretch of track and never getting back to the
     * ring, because a growing town always has one more street planned.
     *
     * <p>And the old line before either, because while it stands there are two
     * walls round one town.
     */
    @Test
    void theTownOffersItsWorksInTheOrderItCaresAboutThem() {
        List<Worksite> works = PublicWorks.of(town());

        assertEquals("dismantle", works.get(0).name(),
                "a town does not keep two walls");
        assertEquals("wall", works.get(1).name(),
                "a half-built wall is a town that cannot shut its gate");
        assertEquals("road", works.get(2).name(),
                "a half-built lane is a walk across grass");
    }

    @Test
    void everyWorkAnswersTheSameThreeQuestions() {
        // The point of the seam: a fourth work is an entry in that list, not a
        // new worker and a new tick pass.
        Settlement town = town();
        for (Worksite work : PublicWorks.of(town)) {
            assertNotNull(work.name(), "a work says what it is");
            work.nextStation(town);        // may be null; must not throw
            work.isWorthStarting(town);    // must not throw
            work.material();               // may be null; must not throw
        }
    }

    /** Nothing loaded, nothing watching. */
    private static class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** The ground of the wall is loaded, which is what makes hands possible. */
    private static final class LoadedBridge extends QuietBridge {
        @Override public boolean isLoaded(SimPos pos) { return true; }
    }
}
