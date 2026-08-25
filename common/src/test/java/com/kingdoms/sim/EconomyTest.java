package com.kingdoms.sim;

import com.kingdoms.sim.economy.Economy;
import com.kingdoms.sim.economy.Valuation;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Money, and the loop it goes round.
 *
 * <p>A settlement of this culture is not a commune. The town owns what its
 * people are paid to produce; a person owns what they come across, and can sell
 * it. Everything here is that one distinction, made to hold under arithmetic.
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

    // --- what a thing is worth ---

    @Test
    void aFindIsWorthCoinAndProduceIsNot() {
        assertTrue(Valuation.priceOf("minecraft:diamond_chestplate") > 0);
        assertTrue(Valuation.priceOf("minecraft:iron_sword") > 0);
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf("minecraft:oak_log"),
                "the town paid somebody to cut that; it does not buy it back");
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf("minecraft:cobblestone"));
    }

    @Test
    void nobodySellsTheTownItsOwnDinner() {
        // A settler who could sell their rations back at a profit would do
        // nothing else for the rest of their life.
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf(Foods.PROVISION));
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf("minecraft:bread"));
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf("minecraft:golden_apple"),
                "priced in the table, but food is refused before the table is read");
    }

    @Test
    void anUnknownThingIsWorthNothingRatherThanThrowing() {
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf("somemod:mystery"));
        assertEquals(Valuation.WORTHLESS, Valuation.priceOf(null));
    }

    @Test
    void betterArmourFetchesMore() {
        assertTrue(Valuation.priceOf("minecraft:diamond_chestplate")
                > Valuation.priceOf("minecraft:iron_chestplate"));
    }

    // --- where money comes from ---

    @Test
    void aTownEarnsFromWhatItProduces() {
        Settlement town = town(2);
        assertEquals(0, town.treasury(), "a new town has nothing");

        town.produceNear(town.centre(), com.kingdoms.sim.settlement.TownStores.WOOD, 40, 1000);

        assertEquals(Economy.levyOn(40), town.treasury());
        assertTrue(town.treasury() > 0, "work turns into coin");
    }

    @Test
    void aTownThatMakesNothingEarnsNothing() {
        // The anchor on the whole economy: coin is created only by production,
        // so an idle settlement cannot pay anybody and has to show it.
        Settlement town = town(3);
        assertEquals(0, town.treasury());
    }

    @Test
    void aSingleLogIsNotACoin() {
        assertEquals(0, Economy.levyOn(1), "rounded down, or every twig mints money");
        assertEquals(0, Economy.levyOn(Economy.LEVY_PER - 1));
        assertEquals(Economy.LEVY_COIN, Economy.levyOn(Economy.LEVY_PER));
    }

    // --- and where it goes ---

    @Test
    void everyWorkerDrawsAWageOnPayday() {
        Settlement town = town(3);
        town.bank(100);

        int paid = Economy.payWages(town);

        assertEquals(3 * Economy.WAGE, paid);
        assertEquals(100 - paid, town.treasury());
        for (Person worker : town.residents()) {
            assertEquals(Economy.WAGE, worker.purse());
        }
    }

    @Test
    void aBrokeTownPaysAsManyAsItCanAndNoMore() {
        // All or nothing per person: half a coin to everybody is worse than a
        // whole coin to as many as the town can actually afford.
        Settlement town = town(5);
        town.bank(2 * Economy.WAGE);

        int paid = Economy.payWages(town);

        assertEquals(2 * Economy.WAGE, paid);
        assertEquals(0, town.treasury(), "spent to the last coin, never past it");
        assertEquals(2, town.residents().stream().filter(p -> p.purse() > 0).count());
    }

    @Test
    void anIdlerDrawsNothing() {
        Settlement town = town(1);
        Person idler = new Person(
                Person.Id.random(), "Idle", Profession.IDLER, town.centre());
        town.addResident(idler);
        town.bank(100);

        Economy.payWages(town);

        assertEquals(0, idler.purse(), "the wage is for the work");
        assertEquals(Economy.WAGE, first(town).purse());
    }

    @Test
    void somebodyTooWeakToWorkHasNotWorked() {
        Settlement town = town(1);
        Person weak = first(town);
        weak.setHunger(Person.HUNGER_WEAK);
        town.bank(100);

        Economy.payWages(town);

        assertEquals(0, weak.purse());
    }

    @Test
    void aPaydayIsOccasionalRatherThanConstant() {
        assertTrue(Economy.isPayday(Economy.PAYDAY_EVERY));
        assertFalse(Economy.isPayday(Economy.PAYDAY_EVERY - 1));
        assertFalse(Economy.isPayday(0), "the first step is not a payday");
    }

    // --- selling a find ---

    @Test
    void theTownBuysWhatSomebodyFound() {
        // The whole arrangement in one test: a settler picks up armour, the town
        // has money, and the armour changes hands for coin.
        Settlement town = town(1);
        town.bank(500);
        Person finder = first(town);
        finder.inventory().add("minecraft:diamond_chestplate", 1);

        Economy.Sale sale = Economy.sellOne(town, finder);

        assertNotNull(sale);
        assertEquals("minecraft:diamond_chestplate", sale.itemId());
        assertEquals(Valuation.priceOf("minecraft:diamond_chestplate"), sale.price());
        assertEquals(sale.price(), finder.purse(), "paid into their own purse");
        assertEquals(500 - sale.price(), town.treasury());
        assertEquals(0, finder.inventory().count("minecraft:diamond_chestplate"),
                "and it is the town's now");
    }

    @Test
    void aTownThatCannotAffordItDoesNotGetIt() {
        // The reason the treasury is finite. A poor town has to say no, and the
        // settler keeps their sword.
        Settlement town = town(1);
        town.bank(1);
        Person finder = first(town);
        finder.inventory().add("minecraft:diamond_chestplate", 1);

        assertNull(Economy.sellOne(town, finder));
        assertEquals(1, town.treasury(), "not a coin moved");
        assertEquals(0, finder.purse());
        assertEquals(1, finder.inventory().count("minecraft:diamond_chestplate"),
                "they still have it, and can try again when the town is richer");
    }

    @Test
    void theBestThingIsSoldFirst() {
        Settlement town = town(1);
        town.bank(500);
        Person finder = first(town);
        finder.inventory().add("minecraft:iron_boots", 1);
        finder.inventory().add("minecraft:diamond_chestplate", 1);

        Economy.Sale sale = Economy.sellOne(town, finder);

        assertEquals("minecraft:diamond_chestplate", sale.itemId(),
                "one trip to the market should be the trip that mattered");
    }

    @Test
    void carryingNothingWorthSellingIsNotASale() {
        Settlement town = town(1);
        town.bank(500);
        Person finder = first(town);
        finder.inventory().add(Foods.PROVISION, 8);
        finder.inventory().add("minecraft:cobblestone", 4);

        assertNull(Economy.sellOne(town, finder));
        assertEquals(500, town.treasury());
    }

    // --- the loop closes ---

    @Test
    void coinCirculatesRatherThanDraining() {
        // Produce, get paid, and the town is still solvent -- which is the whole
        // point of a levy rather than a founding purse that only ever shrinks.
        Settlement town = town(2);
        for (int round = 0; round < 5; round++) {
            town.produceNear(town.centre(),
                    com.kingdoms.sim.settlement.TownStores.WOOD, 40, 100000);
            Economy.payWages(town);
        }

        assertTrue(town.treasury() > 0, "the town is still solvent after five paydays");
        for (Person worker : town.residents()) {
            assertTrue(worker.purse() > 0, "and everybody has been paid");
        }
    }

    @Test
    void nobodyEverGoesNegative() {
        Settlement town = town(1);
        Person pauper = first(town);

        assertFalse(town.spend(5), "a town cannot spend what it has not got");
        assertFalse(pauper.spend(5), "and neither can a person");
        assertEquals(0, town.treasury());
        assertEquals(0, pauper.purse());
    }
}
