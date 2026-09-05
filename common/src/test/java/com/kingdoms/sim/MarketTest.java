package com.kingdoms.sim;

import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Building;
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

    private static Building storehouseAt(int x, int z) {
        return new Building("kingdoms:storehouse", new SimPos(x, 64, z), 1, true);
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

    /**
     * The price signal, walked all the way down and back up again.
     *
     * <p>The individual thresholds are covered above; what none of them says is
     * that the thing is <em>monotone</em> — that a granary emptying makes the
     * price rise every time and a granary filling makes it fall back to exactly
     * where it started. A price that ratcheted, or that stuck at desperate once
     * it had been there, would pass every one of the cases above.
     */
    @Test
    void thePriceRisesAsTheGranaryEmptiesAndFallsBackAsItFills() {
        Settlement town = town();
        // Comfortably inside the granary's 200: an offer needs a lot of room
        // left, so "well stocked" cannot mean "at the ceiling" here.
        int comfortable = 160;

        setStock(town, TownStores.FOOD, comfortable);
        int easy = Market.buyOffer(town, TownStores.FOOD).unitPrice();

        setStock(town, TownStores.FOOD, FoodPlanner.STARTING_PROVISIONS - 4);
        int short_ = Market.buyOffer(town, TownStores.FOOD).unitPrice();

        setStock(town, TownStores.FOOD, 0);
        int desperate = Market.buyOffer(town, TownStores.FOOD).unitPrice();

        setStock(town, TownStores.FOOD, comfortable);
        int recovered = Market.buyOffer(town, TownStores.FOOD).unitPrice();

        assertTrue(short_ > easy, "running low is worth more than being well stocked");
        assertTrue(desperate > short_, "and starving is worth more than running low");
        assertEquals(easy, recovered,
                "a town that has been fed pays what it paid before it was hungry");
    }

    /**
     * A town that has just been fed stops paying the emergency price, which is
     * what makes a second lot of grain a different trade from the first.
     */
    @Test
    void feedingATownIsWhatEndsTheEmergencyPrice() {
        Settlement town = town();
        setStock(town, TownStores.FOOD, 0);
        assertEquals(Market.DESPERATE, Market.needFactor(town, TownStores.FOOD));

        int dear = Market.buyOffer(town, TownStores.FOOD).unitPrice();
        Market.townBuys(town, HERE, TownStores.FOOD, Market.LOT * 8);
        int after = Market.buyOffer(town, TownStores.FOOD).unitPrice();

        assertTrue(after < dear,
                "sixty-four loaves is the difference between starving and merely short");
    }

    // --- the reason, which is why the town has a screen of its own ---

    @Test
    void everyDealSaysWhyItIsPricedAsItIs() {
        Settlement town = town();
        setStock(town, TownStores.FOOD, 0);

        assertEquals(Market.Reason.DESPERATE,
                Market.buyOffer(town, TownStores.FOOD).reason());

        setStock(town, TownStores.FOOD, FoodPlanner.STARTING_PROVISIONS - 4);
        assertEquals(Market.Reason.SHORT,
                Market.buyOffer(town, TownStores.FOOD).reason());

        setStock(town, TownStores.FOOD, 160);
        assertEquals(Market.Reason.ORDINARY,
                Market.buyOffer(town, TownStores.FOOD).reason());
    }

    @Test
    void aTownWithNoRoomLeftSaysSoOnEveryOfferItStillMakes() {
        // The two halves of a glut are one fact: no room is exactly why it will
        // not buy, and exactly why it is glad to sell. Two thresholds for that
        // would let a town claim to be overflowing while still buying, which a
        // player would be right to read as a lie.
        Settlement town = town();
        setStock(town, TownStores.STONE, 1_000_000);

        assertTrue(Market.isGlutted(town, TownStores.STONE));
        assertNull(Market.buyOffer(town, TownStores.STONE),
                "glutted and still buying would be the disagreement");
        assertEquals(Market.Reason.GLUT,
                Market.sellOffer(town, TownStores.STONE).reason());
    }

    @Test
    void aGlutDoesNotMakeTheGoodsCheaperThanTheTownWillBuyThemBack() {
        // TRADE.md would have a full town sell at a discount and it cannot: buy
        // a lot cheap and the shelves come down by exactly that lot, the town
        // wants it again at base, and the treasury is a fountain. With stone's
        // base at one there is no room below "base plus one" to discount into.
        Settlement town = town();
        setStock(town, TownStores.STONE, 1_000_000);

        Market.Deal glutted = Market.sellOffer(town, TownStores.STONE);
        assertEquals(Market.Reason.GLUT, glutted.reason(), "it says it is overflowing");
        assertTrue(glutted.unitPrice() > Market.basePrice(TownStores.STONE),
                "and still sells dearer than the base it would buy the lot back at");
    }

    // --- which shelves a sale actually comes off ---

    @Test
    void aSaleComesOffTheShelvesThatActuallyHaveTheGoods() {
        // The refusal this ends. The store nearest the counter is not
        // necessarily a store with anything in it, and asking only that one and
        // giving up was a town refusing to sell stone it demonstrably owned
        // because the market happened to be built beside the granary — silently,
        // because a deal declined and a deal unreachable look the same.
        Settlement town = town();
        Building byTheStall = storehouseAt(0, 0);
        Building acrossTown = storehouseAt(200, 0);
        town.addBuilding(byTheStall);
        town.addBuilding(acrossTown);
        town.putAwayLoosePile();

        byTheStall.stores().set(TownStores.STONE, 0);
        acrossTown.stores().set(TownStores.STONE, 4_000);
        int treasury = town.treasury();

        int paid = Market.townSells(town, HERE, TownStores.STONE, Market.LOT);

        assertTrue(paid > 0, "the town owns four thousand stone; it can sell eight");
        assertEquals(treasury + paid, town.treasury(), "and the coin arrived");
        assertEquals(4_000 - Market.LOT, acrossTown.stores().get(TownStores.STONE),
                "off the shelves that had it");
        assertEquals(0, byTheStall.stores().get(TownStores.STONE),
                "and the empty ones beside the counter are no worse off than empty");
    }

    @Test
    void aSaleTooBigForOneStoreEmptiesTheNearestFirst() {
        // Locality decides the order the shelves come down in, not whether the
        // sale happens at all — the same rule that stops an empty store
        // underfoot stranding a builder.
        Settlement town = town();
        Building byTheStall = storehouseAt(0, 0);
        Building acrossTown = storehouseAt(200, 0);
        town.addBuilding(byTheStall);
        town.addBuilding(acrossTown);
        town.putAwayLoosePile();

        int reserve = Market.reserveFor(town, TownStores.STONE);
        byTheStall.stores().set(TownStores.STONE, Market.LOT);
        acrossTown.stores().set(TownStores.STONE, reserve + Market.LOT * 4);

        assertTrue(Market.townSells(town, HERE, TownStores.STONE, Market.LOT * 2) > 0);

        assertEquals(0, byTheStall.stores().get(TownStores.STONE),
                "the shelves you are standing at go first");
        assertEquals(reserve + Market.LOT * 3, acrossTown.stores().get(TownStores.STONE),
                "and the rest of the lot comes from across the village");
    }

    /**
     * The armory the stall would otherwise have sold.
     *
     * <p>A settlement's ledger takes any word at all, and the smith stocks
     * {@code weapons} and {@code armour} under two of them. Neither has a base
     * price, so the sell price fell out as "at least base plus one" — a coin —
     * and neither has a reserve, so the whole holding read as spare. The board
     * never listed those rows, but the board is not what a request is answered
     * against: a message naming the word is answered by the offer, so anyone who
     * could name it could buy a town's armory at a coin an ingot.
     */
    @Test
    void aTownDealsInTheFourThingsItDealsInAndNothingElse() {
        Settlement town = town();
        town.stores().add(TownStores.WEAPONS, 64);
        town.stores().add(TownStores.ARMOR, 64);
        town.stores().add(TownStores.SAPLINGS, 64);

        for (String hoard : new String[] {
                TownStores.WEAPONS, TownStores.ARMOR, TownStores.TOOLS,
                TownStores.SAPLINGS, TownStores.EARTH, "not_a_resource"}) {
            assertNull(Market.sellOffer(town, hoard), hoard + " is not merchandise");
            assertNull(Market.buyOffer(town, hoard), hoard + " is not merchandise");
            assertEquals(0, Market.townSells(town, HERE, hoard, Market.LOT), hoard);
        }
        assertEquals(64, town.stores().get(TownStores.WEAPONS), "the armory is intact");
        assertEquals(64, town.stores().get(TownStores.ARMOR));
    }

    // --- the levy, which this changes nothing about ---

    @Test
    void tradingIsStillTheOnlyWayCoinEntersATown() {
        // The open question TRADE.md records is whether a town nobody trades
        // with should grow rich on its own. This is not the answer to it: it is
        // the assertion that the answer has not been quietly changed by
        // building the market. Production mints nothing, the endowment is what
        // it was, and every coin above it came off a player.
        assertEquals(2000, Settlement.FOUNDING_TREASURY,
                "the whole money supply of a town nobody has traded with");

        Settlement town = town();
        setStock(town, TownStores.STONE, 100_000);
        town.produceNear(HERE, TownStores.WOOD, 4_000, 1_000_000);

        assertEquals(Settlement.FOUNDING_TREASURY, town.treasury(),
                "four thousand logs is not a levy and never was");

        int sold = Market.townSells(town, HERE, TownStores.STONE, Market.LOT);
        assertEquals(Settlement.FOUNDING_TREASURY + sold, town.treasury(),
                "and the only coin the town has ever gained came out of a pocket");
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
                    "offered but not honored: " + deal.resource()
                            + (deal.townBuys() ? " (buying)" : " (selling)"));
        }
    }
}
