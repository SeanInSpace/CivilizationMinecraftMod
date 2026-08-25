package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.work.PublicWorks;
import com.kingdoms.sim.work.Worksite;
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

    private static Worksite roads() {
        return new PublicWorks.RoadWork();
    }

    private static Worksite wall() {
        return new PublicWorks.WallWork();
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

    @Test
    void openingAStretchTakesItOffTheList() {
        Settlement town = town();
        town.paths().add(new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(8, 64, 0)));

        roads().completeOne(town);

        assertTrue(town.paths().isOpened(0));
        assertNull(roads().nextStation(town), "nothing left outstanding");
    }

    @Test
    void aRoadStationIsInTheMiddleOfTheRunNotItsEnd() {
        // One walk should cover a stretch, rather than a settler pacing it
        // column by column.
        Settlement town = town();
        town.paths().add(new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(10, 64, 0)));

        SimPos station = roads().nextStation(town);

        assertTrue(station.x() > 2 && station.x() < 8,
                "somewhere along it, not at either end: " + station);
    }

    @Test
    void aTownWithNoRoadsPlannedHasNoRoadWorkToDo() {
        assertNull(roads().nextStation(town()));
    }

    // --- the wall ---

    @Test
    void theWallIsWorkedInTheOrderItWasStaked() {
        // A wall raised nearest-first closes as a scatter of disconnected posts.
        Settlement town = town();
        town.bank(1000);
        town.setPerimeter(com.kingdoms.sim.settlement.PerimeterPlanner.stake(town,
                new com.kingdoms.sim.world.SimContext(
                        new QuietBridge(), 0, com.kingdoms.sim.world.SimSettings.SANDBOX)));

        SimPos first = wall().nextStation(town);
        assertEquals(town.perimeter().ringPositions().get(0), first);

        wall().pay(town);
        wall().completeOne(town);

        assertEquals(town.perimeter().ringPositions().get(1), wall().nextStation(town),
                "the next one along, so the line closes as a line");
    }

    @Test
    void theWallGivesWayToTimberABuildingNeeds() {
        // The wall is the one work with no queue behind it and no deadline, so
        // it is the one that should stand aside.
        Settlement town = town();
        town.bank(1000);
        town.setPerimeter(com.kingdoms.sim.settlement.PerimeterPlanner.stake(town,
                new com.kingdoms.sim.world.SimContext(
                        new QuietBridge(), 0, com.kingdoms.sim.world.SimSettings.SANDBOX)));
        town.stores().take(TownStores.WOOD, town.woodStock());
        town.stores().add(TownStores.WOOD, PerimeterPlanner.TIMBER_KEPT_FOR_BUILDING);

        assertFalse(wall().isWorthStarting(town),
                "that timber is spoken for; the fence can wait");

        town.stores().add(TownStores.WOOD, PerimeterPlanner.WOOD_PER_POST);
        assertTrue(wall().isWorthStarting(town), "and now there is some to spare");
    }

    // --- and the order between them ---

    @Test
    void theTownOffersItsWorksInTheOrderItCaresAboutThem() {
        // The whole priority system: first in the list with a job to do wins.
        List<Worksite> works = PublicWorks.of(town());

        assertEquals("road", works.get(0).name(),
                "a road is what lets everybody else get to work faster");
        assertEquals("wall", works.get(1).name(),
                "a wall is what a finished town puts round itself");
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
        }
    }

    /** Nothing loaded, nothing watching. */
    private static final class QuietBridge
            implements com.kingdoms.sim.platform.WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public com.kingdoms.sim.settlement.Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return new com.kingdoms.sim.settlement.Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }
}
