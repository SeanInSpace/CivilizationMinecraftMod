package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nobody starves in front of a full granary because their pockets are full.
 *
 * <p>Reported from play: a town holding three thousand loaves, with its people
 * starving to death. The chain that feeds somebody ends by putting bread
 * <em>into their inventory</em> and then eating out of it — and
 * {@code Inventory.add} returns nothing when all six slots are taken and none
 * of them holds bread.
 *
 * <p>That could not happen until settlers began picking things up off the
 * ground. Before that an inventory only ever held food, so there was always
 * either a bread slot to top up or a free one to make. Afterwards a settler
 * fills up with wildflowers and seeds on the way to work and quietly loses the
 * ability to be fed.
 *
 * <p>The rule these pin: <strong>eating is not a logistics problem.</strong>
 * Somebody standing at the granary does not need a free pocket to eat.
 */
class FullPocketsTest {

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(
                String id, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** A town with plenty to eat and one settler whose hands are full of rubbish. */
    private static Settlement townWithAFullPocketedSettler() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        town.stores().add(TownStores.FOOD, 3000);
        Person hand = new Person(
                Person.Id.random(), "Ada", Profession.FARMER, town.center());
        for (int i = 0; i < Inventory.SLOTS; i++) {
            hand.inventory().add("minecraft:wildflowers_" + i, 1);
        }
        town.addResident(hand);
        return town;
    }

    private static Person only(Settlement town) {
        return town.residents().iterator().next();
    }

    @Test
    void aSettlerWithNoFreePocketCannotBeHandedBread() {
        // The mechanism, stated on its own so the fix cannot be mistaken for
        // something else having changed.
        Person hand = only(townWithAFullPocketedSettler());

        assertEquals(0, hand.inventory().add(Foods.PROVISION, 4),
                "six slots taken and none of them bread: nothing goes in");
    }

    @Test
    void andEatsAnyway() {
        Settlement town = townWithAFullPocketedSettler();
        Person hand = only(town);

        for (int step = 1; step <= 400; step++) {
            town.step(new SimContext(new QuietBridge(), step, SimSettings.SANDBOX));
        }

        assertTrue(town.population() > 0,
                "a settler starved to death in front of three thousand loaves");
        assertTrue(hand.hunger() < Person.HUNGER_MAX,
                "and they are not on the edge of it either");
        // Deliberately no assertion about the size of the larder here. Over four
        // hundred steps the town grows, puts more people in the fields and ends
        // up holding MORE than it started with — the first draft of this test
        // asserted the stock had fallen and failed for that reason rather than
        // for anything being wrong. What is eaten is measured over a short run
        // instead; see theTownStillPaysForWhatIsEaten.
    }

    @Test
    void theTownStillPaysForWhatIsEaten() {
        // Eating on the spot must not be free. A meal taken straight from the
        // granary is still a loaf out of the granary.
        Settlement town = townWithAFullPocketedSettler();
        int before = town.foodStock();

        for (int step = 1; step <= 200; step++) {
            town.step(new SimContext(new QuietBridge(), step, SimSettings.SANDBOX));
        }

        assertTrue(town.foodStock() < before, "stock fell");
        assertTrue(town.foodStock() > before - 200,
                "but one settler did not eat two hundred loaves in two hundred steps");
    }

    @Test
    void aTownWithNoFoodStillStarves() {
        // The fix must not make anybody immortal: it removes a way to starve
        // beside food, not the ability to starve.
        Settlement town = new Settlement(
                Settlement.Id.random(), "Barrenburg", new SimPos(0, 64, 0), 64);
        town.stores().take(TownStores.FOOD, town.foodStock());
        town.addResident(new Person(
                Person.Id.random(), "Bruno", Profession.FARMER, town.center()));

        for (int step = 1; step <= 600; step++) {
            town.step(new SimContext(new QuietBridge(), step, SimSettings.SANDBOX));
        }

        assertEquals(0, town.population(),
                "an empty town is still fatal, and should be");
    }
}
