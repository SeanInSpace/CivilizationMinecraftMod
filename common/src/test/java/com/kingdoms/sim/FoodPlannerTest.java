package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the subsistence loop: fields feed mouths, and hungry towns stop growing. */
class FoodPlannerTest {

    //                                             id             work  minPop  base  per  priority  capacity
    private static final BuildingType FARM  = new BuildingType("test:farm",   5,  9999,    0,   0,       70,       0);
    private static final BuildingType STORE = new BuildingType("test:storehouse", 5, 9999, 0,  0,       55,       0);
    private static final BuildingType HOUSE = new BuildingType("test:house",  5,  9999,    0,   0,       80,       4);

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public void materializeBlueprint(String blueprintId, SimPos origin) { }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    private static Settlement settlement(int farmers, int others, int farms) {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        s.setCatalogue(List.of(FARM, STORE, HOUSE));
        for (int i = 0; i < farmers; i++) {
            s.addResident(new Person(Person.Id.random(), "Farmer " + i, Profession.FARMER, new SimPos(0, 64, 0)));
        }
        for (int i = 0; i < others; i++) {
            s.addResident(new Person(Person.Id.random(), "Other " + i, Profession.BUILDER, new SimPos(0, 64, 0)));
        }
        for (int i = 0; i < farms; i++) {
            s.addBuilding(new Building(FARM.id(), new SimPos(20 + i * 8, 64, 0), 0, true));
        }
        return s;
    }

    @Test
    void staffedFieldsFeedTheTown() {
        Settlement s = settlement(2, 6, 1);   // 2 farmers on 1 farm: 12 in, 8 eaten
        s.setFoodStock(50);

        FoodPlanner.advance(s, CTX);

        assertEquals(54, s.foodStock(), "a staffed field outpaces the table");
    }

    @Test
    void farmersWithoutFieldsProduceNothing() {
        Settlement s = settlement(4, 0, 0);
        s.setFoodStock(50);

        FoodPlanner.advance(s, CTX);

        assertEquals(46, s.foodStock(), "no fields, no harvest — the granary only drains");
    }

    @Test
    void fieldsOnlyEmploySoManyHands() {
        Settlement s = settlement(6, 0, 1);   // 1 farm employs 2 of the 6 farmers
        s.setFoodStock(100);

        FoodPlanner.advance(s, CTX);

        assertEquals(100 + 2 * FoodPlanner.FOOD_PER_FARMER_PER_STEP - 6, s.foodStock(),
                "surplus farmers contribute nothing until more fields exist");
    }

    @Test
    void granaryCapsWhatCanBeBanked() {
        Settlement s = settlement(2, 0, 2);
        s.setFoodStock(FoodPlanner.BASE_GRANARY - 1);

        FoodPlanner.advance(s, CTX);

        assertEquals(FoodPlanner.BASE_GRANARY, s.foodStock(), "surplus beyond the granary is wasted");
    }

    @Test
    void storehousesExtendTheGranary() {
        Settlement s = settlement(0, 0, 0);
        s.addBuilding(new Building(STORE.id(), new SimPos(30, 64, 0), 0, true));

        assertEquals(FoodPlanner.BASE_GRANARY + FoodPlanner.GRANARY_PER_STOREHOUSE,
                FoodPlanner.granaryCapacity(s));
    }

    @Test
    void provisionsAboveCapacityDrainInsteadOfSnapping() {
        Settlement s = settlement(0, 4, 0);
        s.setFoodStock(1000);   // far above the 200 granary

        FoodPlanner.advance(s, CTX);

        assertEquals(996, s.foodStock(), "held stock is eaten down, never confiscated to capacity");
    }

    @Test
    void hungryTownsPauseBirthsUntilFed() {
        Settlement s = settlement(2, 0, 0);
        s.addBuilding(new Building(HOUSE.id(), new SimPos(40, 64, 0), 0, true));
        s.setFoodStock(0);

        for (int i = 0; i < PopulationPlanner.STEPS_PER_BIRTH * 3; i++) {
            s.step(CTX);
        }
        assertEquals(2, s.population(), "no banked food, no children");

        s.setFoodStock(100_000);
        s.step(CTX);
        assertEquals(3, s.population(),
                "held growth progress delivers immediately once the granary fills");
    }

    @Test
    void anEmptyGranaryEntersTheHistoryOnce() {
        Settlement s = settlement(0, 5, 0);
        s.setFoodStock(3);

        s.step(CTX);   // 3 -> 0: logged
        s.step(CTX);   // stays 0: not logged again
        s.step(CTX);

        long granaryEvents = s.events().stream()
                .filter(e -> e.message().contains("granary is empty"))
                .count();
        assertEquals(1, granaryEvents, "the empty granary is news once, not a drumbeat");
    }
}
