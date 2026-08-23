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
    void anUnfinishedStoreIsNowhereToPutAnything() {
        Settlement s = town();
        Building halfBuilt = storehouse(false);
        s.addBuilding(halfBuilt);

        s.putAwayLoosePile();

        assertFalse(halfBuilt.hasStores(), "a building that is not there yet holds nothing");
        assertEquals(TownStores.FOUNDING_WOOD, s.loosePile().get(TownStores.WOOD),
                "so the kit stays in the open until the roof is on");
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
    void aStoreStillBeingBuiltIsNotSomewhereToSendAnybody() {
        Settlement s = town();
        s.addBuilding(storehouse(false));

        assertNull(s.nearestStore(new SimPos(0, 64, 0)),
                "a roofless frame is not a store yet");
    }
}
