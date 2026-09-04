package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town nobody is watching feeds itself.
 *
 * <p>Reported from play: 43 of 96 settlers starving in a town holding thousands
 * of loaves. Reproduced here at almost exactly that ratio — 41 weak of 96 —
 * before the fix, and none after.
 *
 * <p>The chain that feeds somebody is long: fields to granary, granary to
 * stall, stall to family larder, larder to mouth. Every link is an errand
 * somebody has to be free, fed and unoccupied to run. Under {@code /civ step}
 * they fail together, because nothing walks anywhere while hunger climbs on its
 * own clock — so the town accumulates food it cannot get to anybody, and the
 * larders stay empty while the granary fills.
 *
 * <p>The rule: <strong>out of sight, a town ends up in the state hands would
 * have put it in.</strong> The same doctrine construction, the wall and the
 * roads follow, applied to dinner.
 */
class UnwatchedFeedingTest {

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

    private static Settlement steppedTown(int steps) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Newholt", new SimPos(0, 64, 0), 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        town.setFoodStock(FoodPlanner.STARTING_PROVISIONS);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov"}) {
            town.addResident(new Person(Person.Id.random(), name, Profession.PIONEER,
                    new SimPos(0, 64, 0)));
        }
        for (int step = 1; step <= steps; step++) {
            town.step(new SimContext(new QuietBridge(), step, SimSettings.SANDBOX));
        }
        return town;
    }

    private static long countAtLeast(Settlement town, int hunger) {
        return town.residents().stream().filter(p -> p.hunger() >= hunger).count();
    }

    @Test
    void nobodyIsWeakWithHungerInATownThatHasFood() {
        Settlement town = steppedTown(900);

        assertTrue(town.foodStock() > 0, "the fixture is a town with food, not a famine");
        assertEquals(0, countAtLeast(town, Person.HUNGER_WEAK),
                "settlers too weak to work, in a town holding "
                        + town.foodStock() + " loaves");
    }

    @Test
    void theLardersAreStockedRatherThanTheGranaryHoarding() {
        // The specific failure: food piles up centrally and never reaches a
        // family. Ten of thirty-five families had anything in the house before.
        Settlement town = steppedTown(900);

        long housed = town.households().stream()
                .filter(h -> h.size() > 0 && h.isHoused())
                .count();
        long stocked = town.households().stream()
                .filter(h -> h.size() > 0 && h.isHoused() && h.pantry() > 0)
                .count();

        assertTrue(housed > 0, "the fixture grew families");
        assertTrue(stocked * 2 >= housed,
                "only " + stocked + " of " + housed + " families had food at home");
    }

    @Test
    void theClockStillDoesTheFeedingAndNobodyHasToFetchTheirOwn() {
        // Somebody past the weak line now walks to the nearest food themselves.
        // That is a last resort for a settler the chain has failed, and an
        // unwatched town must not quietly come to depend on it: out of sight the
        // last leg is the clock's, exactly as it was. If the meal errand were
        // doing the feeding here, this would catch people out on one.
        Settlement town = steppedTown(900);

        assertEquals(0, town.residents().stream().filter(FoodPlanner::isGoingToEat).count(),
                "nobody in a fed town has had to go and get their own dinner");
        assertEquals(0, countAtLeast(town, Person.HUNGER_WEAK),
                "which is only worth saying because nobody was weak enough to");
    }

    @Test
    void aTownWithNoFoodIsStillAllowedToStarve() {
        // The distribution moves food; it must never invent any. Without this
        // the fix would read as "nobody ever goes hungry", which is not a
        // simulation.
        Settlement town = new Settlement(
                Settlement.Id.random(), "Barrenburg", new SimPos(0, 64, 0), 64);
        town.stores().take(com.kingdoms.sim.settlement.TownStores.FOOD, town.foodStock());
        town.addResident(new Person(
                Person.Id.random(), "Bruno", Profession.FARMER, town.centre()));

        for (int step = 1; step <= 600; step++) {
            town.step(new SimContext(new QuietBridge(), step, SimSettings.SANDBOX));
        }

        assertEquals(0, town.population(), "an empty town is still fatal");
    }
}
