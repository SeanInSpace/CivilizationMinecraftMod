package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
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
 * Covers births, families, and the housing constraint.
 *
 * <p>The catalogue here uses an unreachable minimum population so the build planner
 * never queues anything. Houses are placed by hand, which is what makes these tests
 * able to isolate population behaviour from construction.
 */
class PopulationTest {

    //                                             id             work  minPop  base  per  priority  capacity
    private static final BuildingType HOUSE = new BuildingType("test:house",  5,  9999,    0,   2,       80,       4);
    private static final BuildingType SHED  = new BuildingType("test:shed",   5,  9999,    0,   0,       10,       0);

    private static final List<BuildingType> CATALOGUE = List.of(HOUSE, SHED);

    private static final class LoadedBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    // SANDBOX: raids off — their schedule hashes the settlement's random id,
    // which would make growth arithmetic vary run to run.
    private static final SimContext CTX = new SimContext(new LoadedBridge(), 0, SimSettings.SANDBOX);

    private static Settlement settlement(int residents, Profession trade, int houses) {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        s.setCatalogue(CATALOGUE);
        s.setFoodStock(100_000);   // these tests isolate housing; food is tested separately
        for (int i = 0; i < residents; i++) {
            s.addResident(new Person(Person.Id.random(), "Settler " + i, trade, new SimPos(0, 64, 0)));
        }
        for (int i = 0; i < houses; i++) {
            s.addBuilding(new Building(HOUSE.id(), new SimPos(10 + i * 8, 64, 0), 0, true));
        }
        return s;
    }

    private static void steps(Settlement s, int count) {
        for (int i = 0; i < count; i++) {
            s.step(CTX);
        }
    }

    // --- families forming ---

    @Test
    void newResidentsAreGroupedIntoFamilies() {
        Settlement s = settlement(6, Profession.BUILDER, 0);

        s.step(CTX);

        assertEquals(2, s.households().size(), "six settlers should form a family of four and one of two");
        assertEquals(4, s.households().get(0).size());
        assertEquals(2, s.households().get(1).size());
    }

    @Test
    void everyResidentEndsUpInExactlyOneFamily() {
        Settlement s = settlement(9, Profession.FARMER, 0);

        s.step(CTX);

        int counted = s.households().stream().mapToInt(Household::size).sum();
        assertEquals(9, counted);
    }

    // --- the housing constraint ---

    @Test
    void familyWithNoHomeNeverGrows() {
        Settlement s = settlement(2, Profession.BUILDER, 0);

        steps(s, PopulationPlanner.STEPS_PER_BIRTH * 4);

        assertEquals(2, s.population(), "no house means no children");
        assertTrue(s.households().getFirst().home() == null);
        assertEquals(1, s.unhousedHouseholds().size());
    }

    @Test
    void familyClaimsAnEmptyHouse() {
        Settlement s = settlement(2, Profession.BUILDER, 1);

        s.step(CTX);

        Household family = s.households().getFirst();
        assertTrue(family.isHoused());
        assertEquals(new SimPos(10, 64, 0), family.home());
        assertTrue(s.unhousedHouseholds().isEmpty());
    }

    @Test
    void housedFamilyGrowsOnSchedule() {
        Settlement s = settlement(2, Profession.BUILDER, 1);

        steps(s, PopulationPlanner.STEPS_PER_BIRTH - 1);
        assertEquals(2, s.population(), "not due yet");

        s.step(CTX);
        assertEquals(3, s.population(), "a child should arrive on the due step");
        assertEquals(3, s.households().getFirst().size());
    }

    @Test
    void familyStopsGrowingWhenTheHouseIsFull() {
        Settlement s = settlement(4, Profession.BUILDER, 1);

        steps(s, PopulationPlanner.STEPS_PER_BIRTH * 5);

        assertEquals(4, s.population(), "a full house cannot take another child");
        assertEquals(1, s.households().size(), "and with nowhere to move, no new family forms");
    }

    @Test
    void fullFamilySplitsIntoAVacantHouse() {
        Settlement s = settlement(4, Profession.BUILDER, 2);

        steps(s, PopulationPlanner.STEPS_PER_BIRTH);

        assertEquals(2, s.households().size(), "someone should move into the empty house");
        assertEquals(3, s.households().get(0).size());
        assertEquals(1, s.households().get(1).size());
        assertEquals(4, s.population(), "moving out is not a birth");
        assertTrue(s.households().get(1).isHoused());
    }

    @Test
    void growthResumesOnceMoreHousingExists() {
        Settlement s = settlement(4, Profession.BUILDER, 1);

        steps(s, PopulationPlanner.STEPS_PER_BIRTH * 2);
        assertEquals(4, s.population(), "stalled on housing");

        // The builders finish another house.
        s.addBuilding(new Building(HOUSE.id(), new SimPos(30, 64, 0), 0, true));
        steps(s, PopulationPlanner.STEPS_PER_BIRTH * 2);

        assertTrue(s.population() > 4, "population should climb again once there is room");
    }

    // --- inheritance and bookkeeping ---

    @Test
    void settlementStaffsItselfBeforeChildrenArrive() {
        // Two farmers, no builders. Surplus retraining converts one farmer within
        // a step or two — long before the first birth — so by the time the child
        // arrives the town is already staffed and the child follows the family.
        Settlement s = settlement(2, Profession.FARMER, 1);

        steps(s, PopulationPlanner.STEPS_PER_BIRTH);

        assertEquals(3, s.population());
        assertTrue(JobPlanner.count(s, Profession.BUILDER) >= 1,
                "the builder shortfall must be fixed by retraining, not left for newborns");
        assertTrue(JobPlanner.mostNeeded(s).isEmpty(), "no job should remain unfilled");
    }

    @Test
    void childInheritsTheFamilyTradeWhenNothingIsShort() {
        Settlement s = settlement(0, Profession.FARMER, 1);
        s.addResident(new Person(Person.Id.random(), "Elder", Profession.FARMER, new SimPos(0, 64, 0)));
        s.addResident(new Person(Person.Id.random(), "Mason", Profession.BUILDER, new SimPos(0, 64, 0)));

        steps(s, PopulationPlanner.STEPS_PER_BIRTH);

        assertEquals(3, s.population());
        assertEquals(2, JobPlanner.count(s, Profession.FARMER),
                "with every job staffed, the child follows the family's eldest");
    }

    @Test
    void childIsBornAtTheFamilyHome() {
        Settlement s = settlement(2, Profession.BUILDER, 1);

        steps(s, PopulationPlanner.STEPS_PER_BIRTH);

        Person.Id newest = s.households().getFirst().members().getLast();
        assertEquals(new SimPos(10, 64, 0), s.resident(newest).position());
    }

    @Test
    void onlyHousingCountsTowardCapacity() {
        Settlement s = settlement(2, Profession.BUILDER, 2);
        s.addBuilding(new Building(SHED.id(), new SimPos(50, 64, 0), 0, true));

        assertEquals(8, PopulationPlanner.totalHousingCapacity(s), "two houses of four, shed contributes nothing");
        assertEquals(0, PopulationPlanner.capacityOf(s, SHED.id()));
    }

    @Test
    void vacantHomeLookupIgnoresOccupiedHouses() {
        Settlement s = settlement(2, Profession.BUILDER, 2);

        assertNotNull(PopulationPlanner.firstVacantHome(s), "both houses start empty");

        s.step(CTX);
        assertEquals(new SimPos(18, 64, 0), PopulationPlanner.firstVacantHome(s),
                "the family took the first house, so the second is next");
    }

    @Test
    void settlementWithNoHousingHasNoVacancy() {
        Settlement s = settlement(2, Profession.BUILDER, 0);
        s.addBuilding(new Building(SHED.id(), new SimPos(50, 64, 0), 0, true));

        assertNull(PopulationPlanner.firstVacantHome(s), "a shed is not a home");
    }

    @Test
    void populationCapStopsBirths() {
        // Plenty of housing, but a hard ceiling of three people: exactly one
        // birth happens and then the town holds, however long we wait.
        Settlement s = settlement(2, Profession.BUILDER, 2);
        SimSettings capped = new SimSettings(100, 8, 96.0, 64, 50, false, 3);
        SimContext ctx = new SimContext(new LoadedBridge(), 0, capped);

        for (int i = 0; i < PopulationPlanner.STEPS_PER_BIRTH * 4; i++) {
            s.step(ctx);
        }

        assertEquals(3, s.population(), "births must stop at the ceiling");
    }

    @Test
    void emptySettlementFormsNoFamilies() {
        Settlement s = settlement(0, Profession.BUILDER, 2);

        steps(s, PopulationPlanner.STEPS_PER_BIRTH * 2);

        assertTrue(s.households().isEmpty());
        assertEquals(0, s.population());
        assertFalse(s.buildings().isEmpty());
    }
}
