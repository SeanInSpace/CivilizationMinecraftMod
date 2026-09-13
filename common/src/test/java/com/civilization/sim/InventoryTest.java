package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Foods;
import com.civilization.sim.person.Inventory;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers what settlers carry, and eating the actual thing they hold. */
class InventoryTest {

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    private static Person settler(Settlement s) {
        Person p = new Person(Person.Id.random(), "Settler", Profession.IDLER, new SimPos(0, 64, 0));
        s.addResident(p);
        return p;
    }

    private static Settlement settlement() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        s.setFoodStock(0);
        return s;
    }

    // --- carrying ---

    @Test
    void itemsStackUpToTheirLimit() {
        Inventory inv = new Inventory();

        assertEquals(5, inv.add(Foods.PROVISION, 5));
        assertEquals(5, inv.count(Foods.PROVISION));
        assertEquals(Inventory.STACK - 5, inv.add(Foods.PROVISION, 99), "the rest will not fit");
        assertEquals(Inventory.STACK, inv.count(Foods.PROVISION));
    }

    @Test
    void pocketsRunOut() {
        Inventory inv = new Inventory();
        String[] kinds = {"minecraft:bread", "minecraft:apple", "minecraft:carrot",
                "minecraft:potato", "minecraft:beetroot", "minecraft:wheat"};
        for (String kind : kinds) {
            assertEquals(1, inv.add(kind, 1));
        }

        assertEquals(0, inv.add("minecraft:cooked_beef", 1), "no slot free for a seventh kind");
    }

    @Test
    void takingMoreThanIsHeldTakesWhatThereIs() {
        Inventory inv = new Inventory();
        inv.add(Foods.PROVISION, 3);

        assertEquals(3, inv.remove(Foods.PROVISION, 10));
        assertTrue(inv.isEmpty(), "an emptied slot is dropped entirely");
    }

    // --- what counts as a meal ---

    @Test
    void theBestMealIsEatenFirst() {
        Inventory inv = new Inventory();
        inv.add(Foods.GRAIN, 5);
        inv.add(Foods.PROVISION, 1);

        assertEquals(Foods.PROVISION, inv.bestFood(),
                "a handed-over loaf beats the raw grain they were saving");
    }

    @Test
    void inedibleThingsAreCarriedButNeverEaten() {
        Inventory inv = new Inventory();
        inv.add("minecraft:stone_pickaxe", 1);

        assertFalse(inv.isEmpty());
        assertNull(inv.bestFood(), "a settler will starve holding a pickaxe");
        assertEquals(0, inv.totalNutrition());
    }

    // --- eating for real ---

    @Test
    void settlersEatTheFoodTheyActuallyHold() {
        Settlement s = settlement();
        Person person = settler(s);
        person.inventory().add("minecraft:apple", 2);
        person.setHunger(Person.HUNGER_HUNGRY + 4);

        FoodPlanner.advance(s, CTX);

        assertEquals(1, person.inventory().count("minecraft:apple"), "one apple eaten");
        assertEquals(Person.HUNGER_HUNGRY + 4 + FoodPlanner.HUNGER_PER_STEP
                        - Foods.nutrition("minecraft:apple"),
                person.hunger(), "and it went exactly as far as an apple goes");
    }

    @Test
    void richerFoodGoesFurther() {
        Settlement s = settlement();
        Person plain = settler(s);
        Person feasting = settler(s);
        plain.inventory().add(Foods.GRAIN, 1);
        feasting.inventory().add("minecraft:cooked_beef", 1);
        plain.setHunger(80);
        feasting.setHunger(80);

        FoodPlanner.advance(s, CTX);

        assertTrue(feasting.hunger() < plain.hunger(),
                "beef undoes more hunger than a handful of wheat");
    }

    @Test
    void aGiftOfFoodSavesAStarvingSettler() {
        Settlement s = settlement();
        Person person = settler(s);
        person.setHunger(Person.HUNGER_MAX - FoodPlanner.HUNGER_PER_STEP);

        // What a player handing over bread amounts to.
        person.inventory().add(Foods.PROVISION, 1);
        FoodPlanner.advance(s, CTX);

        assertEquals(1, s.population(), "the gift arrived in time");
        assertTrue(person.hunger() < Person.HUNGER_SEVERE,
                "and the loaf bought back a whole band; hunger " + person.hunger());
        assertTrue(person.inventory().isEmpty(), "the loaf was eaten");
    }
}
