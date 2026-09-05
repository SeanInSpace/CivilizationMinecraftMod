package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a town's goods actually are.
 *
 * <p>The town total is no longer a number anybody writes down — it is the sum
 * of the buildings holding the goods, plus whatever is still lying in the open.
 * These are the cases where that distinction is visible: a founding party with
 * nowhere to put anything, the moment it finally has somewhere, and the order
 * in which produce is stored and spent.
 */
class SettlementStoresTest {

    private static Settlement town() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
    }

    private static Building storehouse(boolean built) {
        return new Building("kingdoms:storehouse", new SimPos(4, 64, 4), 1, built);
    }

    private static Building storehouseAt(int x, int z) {
        return new Building("kingdoms:storehouse", new SimPos(x, 64, z), 1, true);
    }

    @Test
    void aFoundingPartyCarriesItsKitInTheOpen() {
        Settlement s = town();

        assertEquals(TownStores.FOUNDING_WOOD, s.stores().get(TownStores.WOOD),
                "the town owns its kit");
        assertEquals(TownStores.FOUNDING_WOOD, s.loosePile().get(TownStores.WOOD),
                "and every log of it is still on the ground, because there is no store yet");
    }

    @Test
    void theKitIsPutAwayAsSoonAsThereIsSomewhereToPutIt() {
        // This is the answer to a charter announcing four hundred and eighty
        // timber with not one log anywhere in the world. The moment the party
        // raises a storehouse, the kit is in a building that can be opened.
        Settlement s = town();
        Building store = storehouse(true);
        s.addBuilding(store);

        s.putAwayLoosePile();

        assertEquals(TownStores.FOUNDING_WOOD, store.stores().get(TownStores.WOOD),
                "the timber is in the storehouse");
        assertEquals(0, s.loosePile().get(TownStores.WOOD), "and no longer lying about");
        assertEquals(TownStores.FOUNDING_WOOD, s.stores().get(TownStores.WOOD),
                "and the town is neither richer nor poorer for the tidying");
    }

    @Test
    void aStoreTheSimulationRaisedHoldsGoodsBeforeAnybodyHasSeenIt() {
        // Materialization says whether the blocks have been stamped into the
        // world, which happens only once somebody is near enough to see them.
        // A town that raised a storehouse has a storehouse either way.
        Settlement s = town();
        Building unstamped = storehouse(false);
        s.addBuilding(unstamped);

        s.putAwayLoosePile();

        assertEquals(TownStores.FOUNDING_WOOD, unstamped.stores().get(TownStores.WOOD),
                "the kit is in the store the town built");
        assertEquals(TownStores.FOUNDING_WOOD, s.stores().get(TownStores.WOOD),
                "and not one log left the books on the way in");
    }

    @Test
    void upgradingAStoreDoesNotMakeTheTownsGoodsVanish() {
        // The failure this guards. Raising a building a level sets it back to
        // unstamped while the new blocks go down; if that stopped it counting
        // as a holder, every log in it would drop out of the town's reckoning
        // — builders idle for want of timber standing right there, and a
        // hungry town's food gone from its own books.
        Settlement s = town();
        Building store = storehouse(true);
        s.addBuilding(store);
        s.putAwayLoosePile();
        int owned = s.stores().get(TownStores.WOOD);
        assertTrue(owned > 0, "there is something to lose");

        store.setMaterialized(false);

        assertEquals(owned, s.stores().get(TownStores.WOOD),
                "still in the storehouse, and still on the books");
    }

    @Test
    void produceLandsInTheStoreRatherThanOnTheGround() {
        Settlement s = town();
        Building store = storehouse(true);
        s.addBuilding(store);
        s.putAwayLoosePile();

        s.stores().add(TownStores.WOOD, 64);

        assertEquals(TownStores.FOUNDING_WOOD + 64, store.stores().get(TownStores.WOOD),
                "a felled log is carried to the store");
        assertEquals(0, s.loosePile().get(TownStores.WOOD));
    }

    @Test
    void spendingEmptiesTheStoreBeforeItTouchesWhatIsLyingAbout() {
        Settlement s = town();
        Building store = storehouse(true);
        s.addBuilding(store);
        s.putAwayLoosePile();
        store.stores().set(TownStores.WOOD, 100);
        s.loosePile().set(TownStores.WOOD, 50);

        assertTrue(s.stores().take(TownStores.WOOD, 120), "the town owns a hundred and fifty");

        assertEquals(0, store.stores().get(TownStores.WOOD), "the store went first");
        assertEquals(30, s.loosePile().get(TownStores.WOOD), "and the rest came off the ground");
    }

    @Test
    void aTownCannotSpendWhatItsBuildingsDoNotBetweenThemHold() {
        Settlement s = town();
        Building store = storehouse(true);
        s.addBuilding(store);
        s.putAwayLoosePile();

        int owned = s.stores().get(TownStores.WOOD);
        assertFalse(s.stores().take(TownStores.WOOD, owned + 1), "one log short is short");
        assertEquals(owned, store.stores().get(TownStores.WOOD),
                "and the storehouse was not quietly emptied on the way to finding out");
    }

    @Test
    void settingTheTownsStockIsWellDefinedEvenSpreadAcrossBuildings() {
        Settlement s = town();
        Building store = storehouse(true);
        s.addBuilding(store);
        s.putAwayLoosePile();

        s.setStock(TownStores.WOOD, 42);

        assertEquals(42, s.stores().get(TownStores.WOOD), "exactly what was asked for");
        assertEquals(42, store.stores().get(TownStores.WOOD), "all of it in one place");
        assertEquals(0, s.loosePile().get(TownStores.WOOD), "and nothing left over anywhere else");
    }

    // --- which store a worker walks to ---

    @Test
    void aTownWithNoStoreHasNowhereToSendAnybody() {
        assertNull(town().nearestStore(new SimPos(0, 64, 0)),
                "a party that has just stepped off the road has no shelves at all");
    }

    @Test
    void theNearestStoreIsTheOneActuallyNearest() {
        Settlement s = town();
        Building close = storehouseAt(10, 0);
        Building far = storehouseAt(200, 0);
        s.addBuilding(far);
        s.addBuilding(close);

        assertEquals(close, s.nearestStore(new SimPos(0, 64, 0)),
                "not the first one raised, the one you can see from here");
        assertEquals(far, s.nearestStore(new SimPos(300, 64, 0)),
                "and from the other side of the village, the other one");
    }

    @Test
    void anEmptyStoreUnderfootDoesNotStrandABuilder() {
        // Locality must not become a deadlock. Until couriers exist there is
        // nothing to carry goods to the store a builder happens to stand in,
        // so a builder asked for timber goes to where timber actually is.
        Settlement s = town();
        Building empty = storehouseAt(10, 0);
        Building stocked = storehouseAt(200, 0);
        s.addBuilding(empty);
        s.addBuilding(stocked);
        stocked.stores().set(TownStores.WOOD, 64);

        SimPos here = new SimPos(0, 64, 0);
        assertEquals(empty, s.nearestStore(here), "the closest shelves are still the closest");
        assertEquals(stocked, s.nearestStore(here, TownStores.WOOD),
                "but the closest shelves with timber on them are the ones worth the walk");
    }

    @Test
    void aWorkerIsSentToAStoreEvenBeforeItsBlocksAreDrawn() {
        Settlement s = town();
        Building unstamped = storehouse(false);
        s.addBuilding(unstamped);

        assertEquals(unstamped, s.nearestStore(new SimPos(0, 64, 0)),
                "the store exists as far as the town is concerned, so that is where they go");
    }

    // --- produce lands where it was made ---

    @Test
    void produceGoesToTheStoreNearestWhereItWasMade() {
        Settlement s = town();
        Building west = storehouseAt(-200, 0);
        Building east = storehouseAt(200, 0);
        s.addBuilding(west);
        s.addBuilding(east);

        s.produceNear(new SimPos(190, 64, 0), TownStores.STONE, 64, 100_000);

        assertEquals(64, east.stores().get(TownStores.STONE),
                "cut beside the east store, stacked in the east store");
        assertEquals(0, west.stores().get(TownStores.STONE),
                "and the one across the village never saw it");
    }

    @Test
    void aSecondStorehouseIsNotDecorative() {
        // The failure this exists to end. Depositing into "the town" put every
        // log in whichever store came first in the list, so the second one a
        // town had paid for stood empty for the rest of its life.
        Settlement s = town();
        Building byTheWoods = storehouseAt(-200, 0);
        Building byTheMine = storehouseAt(200, 0);
        s.addBuilding(byTheWoods);
        s.addBuilding(byTheMine);

        s.produceNear(new SimPos(-195, 64, 0), TownStores.WOOD, 64, 100_000);
        s.produceNear(new SimPos(195, 64, 0), TownStores.STONE, 64, 100_000);

        assertEquals(64, byTheWoods.stores().get(TownStores.WOOD), "timber by the woods");
        assertEquals(64, byTheMine.stores().get(TownStores.STONE), "stone by the mine");
        assertEquals(0, byTheWoods.stores().get(TownStores.STONE),
                "and neither store is doing the other's job");
        assertEquals(0, byTheMine.stores().get(TownStores.WOOD));
    }

    @Test
    void theCeilingBelongsToTheTownEvenThoughTheShelvesAreLocal() {
        // Measuring room against one building instead of the town would let a
        // settlement hold one cap per storehouse, which is a way to raise the
        // limit on anything by building another shed.
        Settlement s = town();
        s.addBuilding(storehouseAt(-200, 0));
        s.addBuilding(storehouseAt(200, 0));
        s.setStock(TownStores.IRON, 0);

        assertEquals(100, s.produceNear(new SimPos(-200, 64, 0), TownStores.IRON, 100, 100),
                "the first hundred fits");
        assertEquals(0, s.produceNear(new SimPos(200, 64, 0), TownStores.IRON, 100, 100),
                "and the far store cannot start a second hundred");
        assertEquals(100, s.stores().get(TownStores.IRON), "the town holds one cap, not two");
    }

    @Test
    void produceWithNowhereToPutItStaysOnOpenGround() {
        Settlement s = town();

        s.produceNear(new SimPos(50, 64, 50), TownStores.IRON, 10, 1000);

        assertEquals(10, s.loosePile().get(TownStores.IRON),
                "a camp with no storehouse still keeps what it makes");
    }

    @Test
    void theKitIsSweptToTheStoreNearestTheTownCenter() {
        Settlement s = town();
        Building far = storehouseAt(400, 400);
        Building near = storehouseAt(6, 6);
        s.addBuilding(far);
        s.addBuilding(near);

        s.putAwayLoosePile();

        assertEquals(TownStores.FOUNDING_WOOD, near.stores().get(TownStores.WOOD),
                "put away in the store by the square, not the one at the far fence");
        assertEquals(0, far.stores().get(TownStores.WOOD));
    }
}
