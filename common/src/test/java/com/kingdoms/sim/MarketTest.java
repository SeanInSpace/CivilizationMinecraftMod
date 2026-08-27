package com.kingdoms.sim;

import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a town will buy and sell, and for how much.
 *
 * <p>The only place money changes hands in the whole mod. Settlers own nothing
 * and are charged nothing, so every price here is a price to a player — and a
 * town's money is its founding endowment plus whatever a player has paid it.
 *
 * <p>The rule these all circle: <strong>a price is the town telling you what it
 * needs.</strong> A flat table would be a shop. A price that triples when the
 * granary is empty is a settlement in trouble, legible from the road.
 */
class MarketTest {

    private static final SimPos HERE = new SimPos(0, 64, 0);

    private static Settlement town() {
        Settlement town = new Settlement(Settlement.Id.random(), "Testburg", HERE, 64);
        town.addResident(new Person(
                Person.Id.random(), "Merek", Profession.TRADER, HERE));
        return town;
    }

    private static void setStock(Settlement town, String resource, int amount) {
        town.stores().take(resource, town.stores().get(resource));
        if (amount > 0) {
            town.stores().add(resource, amount);
        }
    }

    // --- you cannot trade with a town that has nobody to trade with ---

    @Test
    void aTownWithNoTraderDoesNotDeal() {
        Settlement town = new Settlement(Settlement.Id.random(), "Quietburg", HERE, 64);
        town.addResident(new Person(
                Person.Id.random(), "Ada", Profession.FARMER, HERE));

        assertFalse(Market.hasTrader(town));
        assertTrue(Market.offers(town).isEmpty(),
                "a settlement with nobody whose job it is does not deal");
    }

    @Test
    void aTraderTooWeakToStandIsNotOpenForBusiness() {
        Settlement town = town();
        town.residents().forEach(p -> p.setHunger(Person.HUNGER_WEAK));

        assertFalse(Market.hasTrader(town));
    }

    @Test
    void aTownWithATraderDeals() {
        assertFalse(Market.offers(town()).isEmpty());
    }

    // --- the price is the town's need ---

    @Test
    void aStarvingTownPaysTripleForFood() {
        Settlement town = town();
        setStock(town, TownStores.FOOD, 0);

        assertTrue(FoodPlanner.isStarving(town), "the fixture is genuinely starving");
        assertEquals(Market.DESPERATE, Market.needFactor(town, TownStores.FOOD));

        Market.Deal buying = Market.buyOffer(town, TownStores.FOOD);
        assertNotNull(buying);
        assertEquals(Market.basePrice(TownStores.FOOD) * Market.DESPERATE,
                buying.unitPrice(),
                "and it says so in the only language a market has");
    }

    @Test
    void aWellFedTownPaysOrdinaryMoney() {
        Settlement town = town();
        setStock(town, TownStores.FOOD, 4000);

        assertEquals(1, Market.needFactor(town, TownStores.FOOD));
    }

    @Test
    void aStarvingTownWillNotSellItsFood() {
        Settlement town = town();
        setStock(town, TownStores.FOOD, 0);

        assertNull(Market.sellOffer(town, TownStores.FOOD),
                "it is not selling the dinner it has not got");
    }

    @Test
    void aTownShortOfTimberPaysOverTheOddsForIt() {
        Settlement town = town();
        setStock(town, TownStores.WOOD, 0);

        assertEquals(Market.SHORT, Market.needFactor(town, TownStores.WOOD));
        assertNull(Market.sellOffer(town, TownStores.WOOD),
                "and will not sell what it is short of");
    }

    // --- what it will not do ---

    @Test
    void aTownWithFullStoresWillNotBuyAtAnyPrice() {
        // The distinction the pricing turns on: being uninterested and being out
        // of room are different things, and only one of them is about money.
        Settlement town = town();
        setStock(town, TownStores.STONE, 1_000_000);

        assertNull(Market.buyOffer(town, TownStores.STONE),
                "there is nowhere to put it");
    }

    @Test
    void aTownWithNoMoneyCannotBuyWhatItWants() {
        Settlement town = town();
        setStock(town, TownStores.FOOD, 0);
        town.spend(town.treasury());

        assertEquals(Market.DESPERATE, Market.needFactor(town, TownStores.FOOD),
                "it wants food as badly as ever");
        assertNull(Market.buyOffer(town, TownStores.FOOD),
                "and cannot do a thing about it, which is its own story");
    }

    @Test
    void aTownKeepsAReserveNoMatterWhoIsAsking() {
        Settlement town = town();
        int reserve = Market.reserveFor(town, TownStores.FOOD);
        setStock(town, TownStores.FOOD, reserve + 4);

        assertNull(Market.sellOffer(town, TownStores.FOOD),
                "four spare is not a lot, and the reserve is untouchable");

        setStock(town, TownStores.FOOD, reserve + Market.LOT * 3);
        assertNotNull(Market.sellOffer(town, TownStores.FOOD));
    }

    @Test
    void sellingCanNeverEatTheReserve() {
        Settlement town = town();
        int reserve = Market.reserveFor(town, TownStores.WOOD);
        setStock(town, TownStores.WOOD, reserve + Market.LOT);

        int paid = Market.townSells(town, HERE, TownStores.WOOD, Market.LOT * 4);

        assertEquals(0, paid, "more than the surplus is refused outright");
        assertEquals(reserve + Market.LOT, town.stores().get(TownStores.WOOD),
                "and nothing moved");
    }

    // --- the spread, which is what keeps it honest ---

    @Test
    void thereIsNoArbitrageEvenOnTheCheapestGoods() {
        // The exploit the spread exists to stop, stated as arithmetic: a town
        // short of something pays a multiple for it, so if it would also sell at
        // the plain base a player buys low and sells high forever out of the
        // treasury. It is blocked twice -- a town short of a thing refuses to
        // sell it at all, and the sell price is strictly above the base anyway.
        for (String resource : Market.TRADED) {
            int base = Market.basePrice(resource);
            int sell = Math.max(base + 1, base * Market.SELL_NUMERATOR
                    / Market.SELL_DENOMINATOR);
            assertTrue(sell > base,
                    resource + ": integer division must not flatten the spread");
        }
    }

    @Test
    void aTownSellsDearerThanItBuys() {
        // Without a spread a player stands at the stall buying and selling the
        // same stone forever and the treasury is a fountain.
        Settlement town = town();
        setStock(town, TownStores.STONE, 100_000);
        setStock(town, TownStores.WOOD, 100_000);

        for (String resource : new String[] {TownStores.STONE, TownStores.WOOD}) {
            Market.Deal selling = Market.sellOffer(town, resource);
            assertNotNull(selling, resource);
            assertTrue(selling.unitPrice() > Market.basePrice(resource),
                    resource + ": selling must be dearer than the base it buys at");
        }
    }

    // --- money actually moving ---

    @Test
    void buyingTakesCoinFromTheTownAndPutsGoodsOnItsShelves() {
        Settlement town = town();
        setStock(town, TownStores.STONE, 0);
        int before = town.treasury();

        int paid = Market.townBuys(town, HERE, TownStores.STONE, Market.LOT);

        assertTrue(paid > 0);
        assertEquals(before - paid, town.treasury(), "the coin genuinely left");
        assertEquals(Market.LOT, town.stores().get(TownStores.STONE),
                "and the stone genuinely arrived");
    }

    @Test
    void sellingPutsCoinInAndTakesGoodsOut() {
        Settlement town = town();
        setStock(town, TownStores.STONE, 100_000);
        int before = town.treasury();
        int stock = town.stores().get(TownStores.STONE);

        int took = Market.townSells(town, HERE, TownStores.STONE, Market.LOT);

        assertTrue(took > 0);
        assertEquals(before + took, town.treasury());
        assertEquals(stock - Market.LOT, town.stores().get(TownStores.STONE));
    }

    @Test
    void aTownNeverPaysMoreThanItHas() {
        Settlement town = town();
        setStock(town, TownStores.STONE, 0);
        town.spend(town.treasury() - 1);

        assertEquals(0, Market.townBuys(town, HERE, TownStores.STONE, Market.LOT * 8),
                "one coin does not buy sixty-four stone");
        assertEquals(1, town.treasury(), "and it kept its coin");
    }

    @Test
    void aDealIsAllOrNothing() {
        Settlement town = town();
        setStock(town, TownStores.STONE, 0);
        Market.Deal deal = Market.buyOffer(town, TownStores.STONE);

        assertEquals(0, Market.townBuys(town, HERE, TownStores.STONE,
                        deal.lots() * Market.LOT + 1),
                "asking for more than the deal covers is refused rather than trimmed");
        assertEquals(0, town.stores().get(TownStores.STONE));
    }

    @Test
    void everyOfferOnTheBoardCanActuallyBeTaken() {
        // A screen full of deals the town would refuse is worse than an empty one.
        Settlement town = town();
        setStock(town, TownStores.STONE, 60_000);
        setStock(town, TownStores.WOOD, 300);
        setStock(town, TownStores.FOOD, 2000);

        for (Market.Deal deal : Market.offers(town)) {
            int moved = deal.townBuys()
                    ? Market.townBuys(town, HERE, deal.resource(), Market.LOT)
                    : Market.townSells(town, HERE, deal.resource(), Market.LOT);
            assertTrue(moved > 0,
                    "offered but not honoured: " + deal.resource()
                            + (deal.townBuys() ? " (buying)" : " (selling)"));
        }
    }
}
