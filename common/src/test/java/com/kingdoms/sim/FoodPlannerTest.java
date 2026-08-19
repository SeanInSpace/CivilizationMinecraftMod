package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.HaulPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the food chain — field, farmer, granary, market hand, pantry, mouth —
 * and what happens to the people at the end of it when a link breaks.
 */
class FoodPlannerTest {

    private static final BuildingType FARM   = new BuildingType("test:farm",   5, 9999, 0, 0, 70, 0);
    private static final BuildingType MARKET = new BuildingType("test:market", 5, 9999, 0, 0, 65, 0);
    private static final BuildingType HOUSE  = new BuildingType("test:house",  5, 9999, 0, 0, 80, 4);

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public void materializeBlueprint(String blueprintId, SimPos origin) { }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    private static Settlement settlement() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        s.setCatalogue(List.of(FARM, MARKET, HOUSE));
        return s;
    }

    private static Person add(Settlement s, Profession profession) {
        Person p = new Person(Person.Id.random(),
                profession + " " + s.population(), profession, new SimPos(0, 64, 0));
        s.addResident(p);
        return p;
    }

    private static Building addBuilding(Settlement s, BuildingType type, int index) {
        Building b = new Building(type.id(), new SimPos(20 + index * 8, 64, 0), 0, true);
        s.addBuilding(b);
        return b;
    }

    /**
     * Runs the food chain and the haulers, without the rest of the settlement
     * step — job retraining would reassign the very people these tests rely on.
     */
    private static void chainSteps(Settlement s, int count) {
        for (int i = 0; i < count; i++) {
            FoodPlanner.advance(s, CTX);
            HaulPlanner.advance(s, CTX);
        }
    }

    private static Household house(Settlement s, Person... members) {
        Household h = new Household(Household.Id.random(), "Family");
        for (Person p : members) {
            h.addMember(p.id());
        }
        h.setHome(new SimPos(5, 64, 5));
        s.addHousehold(h);
        return h;
    }

    // --- the chain, link by link ---

    @Test
    void harvestWaitsAtTheFarmUntilSomebodyCarriesIt() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Building farm = addBuilding(s, FARM, 0);
        Person farmer = add(s, Profession.FARMER);

        // The field fills, and the farmer is sent to fetch — but nothing has
        // reached the granary while they are still walking out to the field.
        chainSteps(s, 1);
        assertTrue(farm.foodStored() > 0, "harvest sits in the field until collected");
        assertEquals(0, s.foodStock(), "and cannot arrive before the farmer does");
        assertNotNull(farmer.haul(), "the farmer has been sent for it");

        chainSteps(s, 6);
        assertTrue(s.foodStock() > 0, "the harvest reached the granary on somebody's back");
    }

    @Test
    void fieldsOnlyEmploySoManyHands() {
        Settlement s = settlement();
        s.setFoodStock(0);
        addBuilding(s, FARM, 0);
        for (int i = 0; i < 6; i++) {
            add(s, Profession.FARMER);
        }

        FoodPlanner.advance(s, CTX);

        assertEquals(FoodPlanner.FARMERS_PER_FARM * FoodPlanner.FOOD_PER_FARMER_PER_STEP,
                s.foodStock() + FoodPlanner.farmStock(s),
                "one field feeds work to two farmers; the rest wait for more fields");
    }

    @Test
    void marketHandsStockTheMarketFromTheGranary() {
        Settlement s = settlement();
        s.setFoodStock(50);
        Building market = addBuilding(s, MARKET, 0);
        add(s, Profession.TRADER);

        chainSteps(s, 6);

        assertEquals(FoodPlanner.TRADER_CARRY, market.foodStored(), "one load walked to the stall");
        assertTrue(s.foodStock() < 50, "and it left the granary");
    }

    @Test
    void familiesFetchFromTheMarketToThePantry() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Building market = addBuilding(s, MARKET, 0);
        market.setFoodStored(40);
        Household family = house(s, add(s, Profession.IDLER), add(s, Profession.IDLER));

        chainSteps(s, 8);

        assertEquals(2 * FoodPlanner.PANTRY_PER_MEMBER, family.pantry(),
                "a member shopped the pantry up to its target");
        assertEquals(40 - family.pantry(), market.foodStored(), "and the stall is down by that much");
    }

    @Test
    void youngTownsFetchStraightFromTheGranary() {
        Settlement s = settlement();
        s.setFoodStock(30);
        Household family = house(s, add(s, Profession.IDLER));

        chainSteps(s, 6);

        assertTrue(family.pantry() > 0, "no market yet, so the granary serves families directly");
    }

    @Test
    void aCarriedLoadIsNeverInTwoPlacesAtOnce() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Building market = addBuilding(s, MARKET, 0);
        market.setFoodStored(20);
        Household family = house(s, add(s, Profession.IDLER));
        Person shopper = s.residents().iterator().next();

        // Step until the load is on their back but not yet delivered.
        for (int i = 0; i < 8 && (shopper.haul() == null || !shopper.haul().isLoaded()); i++) {
            chainSteps(s, 1);
        }

        assertNotNull(shopper.haul(), "still running the errand");
        assertTrue(shopper.haul().isLoaded(), "carrying the load");
        assertEquals(20 - shopper.haul().carried(), market.foodStored(),
                "goods left the stall the moment they were picked up");
        assertEquals(0, family.pantry(), "and have not arrived home yet");
    }

    @Test
    void theHungryEatWhatTheyCarry() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Person person = add(s, Profession.BUILDER);
        Household family = house(s, person);
        family.setPantry(10);
        person.setHunger(Person.HUNGER_HUNGRY + 2);

        FoodPlanner.advance(s, CTX);

        assertTrue(person.hunger() < Person.HUNGER_HUNGRY, "eating pushed hunger back down");
        assertEquals(FoodPlanner.CARRY_WHEN_EATING - 1,
                person.inventory().count(Foods.PROVISION),
                "took a couple of loaves from the larder, ate one on the spot");
    }

    // --- when the chain breaks ---

    @Test
    void hungerClimbsWithNothingToEat() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Person person = add(s, Profession.BUILDER);

        for (int i = 0; i < 5; i++) {
            FoodPlanner.advance(s, CTX);
        }

        assertEquals(5 * FoodPlanner.HUNGER_PER_STEP, person.hunger());
    }

    @Test
    void theWeakStopWorking() {
        Settlement s = settlement();
        s.setFoodStock(0);
        addBuilding(s, FARM, 0);
        Person farmer = add(s, Profession.FARMER);
        farmer.setHunger(Person.HUNGER_WEAK);

        FoodPlanner.advance(s, CTX);

        assertEquals(0, s.foodStock() + FoodPlanner.farmStock(s),
                "a farmer too weak to work brings in nothing — hunger compounds itself");
    }

    @Test
    void starvationKillsAfterTheGrace() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Person person = add(s, Profession.BUILDER);
        person.setHunger(Person.HUNGER_MAX);

        for (int i = 0; i < FoodPlanner.STARVATION_GRACE_STEPS; i++) {
            assertEquals(1, s.population(), "alive through the grace period");
            FoodPlanner.advance(s, CTX);
        }

        assertEquals(0, s.population(), "then starvation takes them");
        assertTrue(s.events().stream().anyMatch(e -> e.message().contains("starved")),
                "and the town remembers");
    }

    @Test
    void eatingResetsTheStarvationClock() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Person person = add(s, Profession.BUILDER);
        Household family = house(s, person);
        person.setHunger(Person.HUNGER_MAX);
        person.setStarvingSteps(FoodPlanner.STARVATION_GRACE_STEPS - 1);
        family.setPantry(5);

        FoodPlanner.advance(s, CTX);

        assertEquals(1, s.population(), "food arrived in time");
        assertEquals(0, person.starvingSteps(), "the clock resets on a meal");
    }

    // --- unchanged foundations ---

    @Test
    void birthsStillRequireBankedFood() {
        Settlement s = settlement();
        s.setFoodStock(0);
        assertTrue(!FoodPlanner.canFeedAnotherMouth(s) || s.population() > 0);
        s.setFoodStock(1000);
        assertTrue(FoodPlanner.canFeedAnotherMouth(s));
    }

    @Test
    void granaryBuildingsExtendCapacity() {
        Settlement s = settlement();
        s.addBuilding(new Building("test:granary", new SimPos(30, 64, 0), 0, true));

        assertEquals(FoodPlanner.BASE_GRANARY + FoodPlanner.GRANARY_PER_BUILDING,
                FoodPlanner.granaryCapacity(s));
    }
}
