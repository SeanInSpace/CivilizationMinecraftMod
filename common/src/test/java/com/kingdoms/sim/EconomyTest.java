package com.kingdoms.sim;

import com.kingdoms.sim.economy.Economy;
import com.kingdoms.sim.economy.Valuation;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The town's money, and the fact that only the town has any.
 *
 * <p>A settler owns nothing and wants for nothing: what they cut, grow, mine or
 * find goes to the common stores, and what they need comes back out of it at no
 * charge. There are no wages, no purses and no prices between one settler and
 * another.
 *
 * <p>There used to be all three. They ran correctly and added nothing anybody
 * could see — {@code Person.spend} was never called by a single caller, and a
 * settler holding four hundred coin behaved exactly like one holding none.
 */
class EconomyTest {

    private static Settlement town(int workers) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        for (int i = 0; i < workers; i++) {
            town.addResident(new Person(
                    Person.Id.random(), "Hand " + i, Profession.FARMER, town.centre()));
        }
        return town;
    }

    private static Person first(Settlement town) {
        return town.residents().iterator().next();
    }

    /** Collects what a hand-in would put on the shelves. */
    private static Map<String, Integer> shelf() {
        return new HashMap<>();
    }

    private static int handIn(Settlement town, Person person, Map<String, Integer> onto) {
        return Economy.handIn(town, person, (id, count) -> onto.merge(id, count, Integer::sum));
    }

    // --- the whole money supply ---

    @Test
    void aTownIsFoundedHoldingItsEntireSupply() {
        assertEquals(Settlement.FOUNDING_TREASURY, town(0).treasury(),
                "the endowment is the money, and there is no other source");
        assertEquals(2000, Settlement.FOUNDING_TREASURY);
    }

    @Test
    void producingGoodsDoesNotCreateMoney() {
        // Production used to mint a coin for every four units, which made a
        // town's wealth a measure of how long it had been running rather than
        // of anything it had done with anybody.
        Settlement town = town(2);
        int before = town.treasury();

        town.produceNear(town.centre(), TownStores.WOOD, 400, 100000);

        assertEquals(before, town.treasury(), "goods are not money");
        assertTrue(town.woodStock() > 0, "but the goods are certainly there");
    }

    @Test
    void spendingIsRealAndCannotBeUndoneByWorking() {
        // The point of a fixed supply: a town that spends its endowment on a
        // wall has spent it, and no amount of cutting timber brings it back.
        Settlement town = town(2);
        town.spend(1500);
        town.produceNear(town.centre(), TownStores.STONE, 4000, 100000);

        assertEquals(Settlement.FOUNDING_TREASURY - 1500, town.treasury());
    }

    @Test
    void aTownNeverGoesNegative() {
        Settlement town = town(1);
        assertFalse(town.spend(Settlement.FOUNDING_TREASURY + 1),
                "it cannot spend what it has not got");
        assertEquals(Settlement.FOUNDING_TREASURY, town.treasury());
    }

    @Test
    void theEndowmentCoversAWallAndNotMuchElse() {
        // A settlement can afford to fortify itself once. That is the intended
        // shape of the constraint, and it is worth knowing if either number moves.
        int wall = 420 * PerimeterPlanner.COIN_PER_POST;
        assertTrue(wall < Settlement.FOUNDING_TREASURY,
                "a town can wall itself from what it was founded with");
        assertTrue(wall > Settlement.FOUNDING_TREASURY / 2,
                "and doing so is most of everything it has");
    }

    // --- a settler owns nothing ---

    @Test
    void whatASettlerCarriesBelongsToTheTown() {
        Settlement town = town(1);
        Person finder = first(town);
        finder.inventory().add("minecraft:diamond_chestplate", 1);
        Map<String, Integer> onto = shelf();

        int given = handIn(town, finder, onto);

        assertEquals(1, given);
        assertEquals(1, onto.get("minecraft:diamond_chestplate"));
        assertEquals(0, finder.inventory().count("minecraft:diamond_chestplate"),
                "it was the town's before they picked it up");
    }

    @Test
    void handingInCostsTheTownNothing() {
        // The internal half of the economy in its entirety: goods move, coin
        // does not.
        Settlement town = town(1);
        Person finder = first(town);
        finder.inventory().add("minecraft:diamond_chestplate", 1);
        int before = town.treasury();

        handIn(town, finder, shelf());

        assertEquals(before, town.treasury(), "nobody is paid for their own work");
    }

    @Test
    void aSettlerKeepsTheirDinner() {
        // Stripping a settler's rations into the granary as they walk past would
        // have them starve in front of a full larder.
        Settlement town = town(1);
        Person walker = first(town);
        walker.inventory().add(Foods.PROVISION, 6);
        walker.inventory().add("minecraft:iron_sword", 1);
        Map<String, Integer> onto = shelf();

        handIn(town, walker, onto);

        assertEquals(6, walker.inventory().count(Foods.PROVISION), "still theirs to eat");
        assertEquals(1, onto.get("minecraft:iron_sword"), "the sword is not");
    }

    @Test
    void carryingNothingButFoodIsNotWorthAWalk() {
        Settlement town = town(1);
        Person walker = first(town);
        walker.inventory().add(Foods.PROVISION, 8);

        assertFalse(Economy.wantsToUnload(walker),
                "a settler carrying their lunch is not on an errand");
    }

    @Test
    void somethingWorthCarryingInIsWorthTheWalk() {
        Settlement town = town(1);
        Person finder = first(town);
        finder.inventory().add("minecraft:diamond_chestplate", 1);

        assertTrue(Economy.wantsToUnload(finder));
    }

    @Test
    void aTrifleIsCarriedRatherThanWalkedIn() {
        // Downing tools to walk across the village with one flint would be a
        // worse settler than one who finishes the row first.
        Settlement town = town(1);
        Person worker = first(town);
        worker.inventory().add("minecraft:flint", 1);

        assertFalse(Economy.wantsToUnload(worker));
    }

    @Test
    void fullPocketsAreWorthTheWalkWhateverTheyHold() {
        // Somebody with nowhere left to put anything has stopped being able to
        // pick things up, which is the one case where the trip is the useful
        // thing to do regardless of value.
        Settlement town = town(1);
        Person worker = first(town);
        for (int i = 0; i < com.kingdoms.sim.person.Inventory.SLOTS; i++) {
            worker.inventory().add("minecraft:stick_" + i, 1);
        }

        assertTrue(Economy.pocketsFull(worker));
        assertTrue(Economy.wantsToUnload(worker));
    }

    @Test
    void anErrandForTheTownOutranksTheirOwnArmful() {
        Settlement town = town(1);
        Person hauler = first(town);
        hauler.inventory().add("minecraft:diamond_chestplate", 1);
        hauler.setHaul(new com.kingdoms.sim.person.HaulTask(
                com.kingdoms.sim.person.HaulTask.Store.FARM, new SimPos(0, 64, 0),
                com.kingdoms.sim.person.HaulTask.Store.MARKET, new SimPos(8, 64, 8), 4));

        assertFalse(Economy.wantsToUnload(hauler),
                "they are already carrying the town's business");
    }

    // --- what things are worth, which is now only the player's concern ---

    @Test
    void valuationStillPricesFindsForTheOneTradeThatCosts() {
        // Nobody inside the town pays for anything. The table survives because
        // the market will charge a player from it -- see TRADE.md -- and because
        // it is still the best measure of whether a load is worth carrying in.
        assertTrue(Valuation.priceOf("minecraft:diamond_chestplate") > 0);
        assertTrue(Valuation.priceOf("minecraft:diamond_chestplate")
                > Valuation.priceOf("minecraft:iron_chestplate"));
    }

    @Test
    void rationsAreNotMerchandise() {
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf(Foods.PROVISION));
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf("minecraft:bread"));
    }

    @Test
    void anUnknownThingIsWorthNothingRatherThanThrowing() {
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf("somemod:mystery"));
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf(null));
    }

    // --- what the money is actually for ---

    @Test
    void aPostCostsCoinAsWellAsTimber() {
        Settlement town = town(1);
        town.stores().add(TownStores.WOOD, 100);
        town.spend(town.treasury());

        assertFalse(PerimeterPlanner.payForPost(town),
                "a town with no money cannot wall itself, however much timber it has");

        town.bank(PerimeterPlanner.COIN_PER_POST);
        assertTrue(PerimeterPlanner.payForPost(town));
    }

    @Test
    void payingForAPostIsBothOrNeither() {
        Settlement town = town(1);
        town.stores().take(TownStores.WOOD, town.woodStock());
        int coin = town.treasury();

        assertFalse(PerimeterPlanner.payForPost(town), "no timber, no post");
        assertEquals(coin, town.treasury(), "and nothing was taken for nothing");
    }
}
