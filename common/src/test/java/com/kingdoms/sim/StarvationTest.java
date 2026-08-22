package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the one rule a town breaks its own rules for: it must never sit idle
 * while its people starve.
 *
 * <p>Written against the settlement that died in a playtest. It raised its hall,
 * ran out of stone, and then simply stopped — the queue head could not be paid
 * for, nothing new is ever ordered while something is queued, and so the farm
 * that would have fed everybody could not even be wanted. Every rule involved was
 * individually reasonable, which is why the crisis has to suspend them rather
 * than any one of them being wrong.
 */
class StarvationTest {

    // A hall the town wants from day one, and food buildings it does not: the
    // farm and granary both sit behind a population threshold the founding party
    // has not reached, which is exactly the gate the crisis lane steps over.
    private static final BuildingType HALL =
            new BuildingType("test:hall", 40, 1, 1, 0, 100, 0);
    private static final BuildingType FARM =
            new BuildingType("test:farm", 20, 4, 0, 6, 70, 0);
    private static final BuildingType GRANARY =
            new BuildingType("test:granary", 25, 4, 1, 20, 75, 0);

    /** Nobody is watching and nothing is loaded, so the clock builds and hauls. */
    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    /**
     * A town with nothing in any store, and both producers already standing with
     * nobody to work them.
     *
     * <p>The camp and the mine are there so the producer bootstrap has nothing
     * left to offer: this town's shortage is a real shortage, and a job it cannot
     * pay for stays stuck rather than being quietly rescued by a camp order. One
     * resident, so the staffing table has no surplus to shuffle and every job
     * change in these tests is the crisis lane's doing.
     */
    private static Settlement destituteTown() {
        Settlement s = new Settlement(Settlement.Id.random(), "Lastditch", new SimPos(0, 64, 0), 128);
        s.setCatalogue(List.of(HALL, FARM, GRANARY));
        s.setFoodStock(0);
        s.setWoodStock(0);
        s.setStoneStock(0);
        s.addBuilding(new Building("kingdoms:lumber_camp", new SimPos(60, 64, 60), 1, true));
        s.addBuilding(new Building("kingdoms:mine", new SimPos(-60, 64, -60), 1, true));
        s.addResident(new Person(
                Person.Id.random(), "Alder", Profession.BUILDER, s.centre()));
        return s;
    }

    @Test
    void afrozenQueueHeadStopsBlockingTheFieldThatWouldEndTheFamine() {
        Settlement s = destituteTown();
        BuildTask hall = new BuildTask(HALL.id(), new SimPos(20, 64, 0), HALL.workCost());
        hall.addProgress(12);
        s.enqueueBuild(hall);

        for (int i = 0; i < Settlement.STALLED_HEAD_STEPS; i++) {
            s.step(CTX);
            assertEquals(HALL.id(), s.buildQueue().getFirst().blueprintId(),
                    "the hall keeps its turn while there is any chance it moves");
        }

        s.step(CTX);

        assertEquals(FARM.id(), s.buildQueue().getFirst().blueprintId(),
                "a queue frozen on a job nobody can pay for must not outlast the town");
        assertEquals(HALL.id(), s.buildQueue().get(1).blueprintId(),
                "and the hall is parked behind the field, not given up on");
        assertEquals(12, s.buildQueue().get(1).progress(),
                "with every hour already spent on it still counted");
    }

    @Test
    void thefieldThatEndsAFamineIsNotBlockedByTheFamine() {
        // Ordinary buildings are paid for out of the stores, and a starving town
        // has no stores. Without the same free pass the lumber camp gets, a farm
        // ordered to save a town would stall in exactly the spot the hall did.
        Settlement s = destituteTown();

        for (int step = 0; step < 40 && s.countBuildings(FARM.id()) == 0; step++) {
            s.step(CTX);
        }

        assertEquals(1, s.countBuildings(FARM.id()),
                "the field goes up on credit, for the same reason the lumber camp does");
    }

    @Test
    void awellFedTownFinishesWhatItStarted() {
        // The control. Being short of stone is an ordinary problem, and a town
        // with a full granary waits for the stone rather than queue-jumping
        // itself a farm it has no particular need of.
        Settlement s = destituteTown();
        s.setFoodStock(500);
        s.enqueueBuild(new BuildTask(HALL.id(), new SimPos(20, 64, 0), HALL.workCost()));

        for (int i = 0; i < Settlement.STALLED_HEAD_STEPS * 3; i++) {
            s.step(CTX);
        }

        assertFalse(s.isStarving());
        assertEquals(HALL.id(), s.buildQueue().getFirst().blueprintId(),
                "an affordable-tomorrow job keeps its place in a town that can wait");
        assertTrue(s.buildQueue().stream().noneMatch(t -> t.blueprintId().equals(FARM.id())),
                "and no field jumps ahead of it");
    }

    @Test
    void thetownSaysWhyItChangedItsMind() {
        Settlement s = destituteTown();
        s.enqueueBuild(new BuildTask(HALL.id(), new SimPos(20, 64, 0), HALL.workCost()));

        for (int i = 0; i <= Settlement.STALLED_HEAD_STEPS; i++) {
            s.step(CTX);
        }

        assertEquals(1, s.events().stream()
                        .filter(e -> e.message().contains("Starving")).count(),
                "said once when the town changes its mind, not every step it stays hungry");
    }

    @Test
    void anAnimalFarmIsNotTheFieldThatFeedsAnybody() {
        // Roles are matched on the last segment of the id. Matching on the tail
        // would have a town in a famine raise a pen of cows and call the problem
        // solved.
        assertTrue(FoodPlanner.isSurvivalBuilding("kingdoms:farm"));
        assertTrue(FoodPlanner.isSurvivalBuilding("kingdoms:norman/farm"));
        assertTrue(FoodPlanner.isSurvivalBuilding("kingdoms:farm_l2"), "an improved farm is a farm");
        assertTrue(FoodPlanner.isSurvivalBuilding("kingdoms:granary"));
        assertFalse(FoodPlanner.isSurvivalBuilding("kingdoms:animal_farm"));
        assertFalse(FoodPlanner.isSurvivalBuilding("kingdoms:town_hall"));
    }
}
