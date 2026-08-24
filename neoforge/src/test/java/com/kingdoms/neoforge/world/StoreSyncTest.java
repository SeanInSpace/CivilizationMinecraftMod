package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mirror between a store's ledger and its shelves.
 *
 * <p>Both ways this once made timber out of nothing were found by reading the
 * code, because there was no way to run it without a chest in a world. The
 * arithmetic underneath has always been covered in {@code :common}; what had
 * never been exercised was everything around it — which building's ledger a set
 * of shelves answers to, what happens to a slot the stores do not recognise,
 * and whether a rewrite happens at all.
 */
class StoreSyncTest {

    private static final String WOOD = TownStores.WOOD;
    private static final String STONE = TownStores.STONE;

    /** A world of shelves you hand out per building, counting saves. */
    private static final class FakeStoreWorld implements StoreWorld {
        private final Map<Building, Shelves> shelves = new HashMap<>();
        int ledgerSaves;

        FakeStoreWorld give(Building building, Shelves what) {
            shelves.put(building, what);
            return this;
        }

        @Override
        public Shelves shelvesOf(Building building) {
            return shelves.get(building);
        }

        @Override
        public void ledgerChanged() {
            ledgerSaves++;
        }
    }

    private static Settlement town() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
    }

    private static Building store(String name, int x) {
        return new Building("kingdoms:" + name, new SimPos(x, 64, 0), 1, true);
    }

    // --- the quiet case ---

    @Test
    void shelvesThatAlreadyAgreeAreLeftAlone() {
        Settlement s = town();
        Building store = store("storehouse", 0);
        s.addBuilding(store);
        store.stores().set(WOOD, 64);
        FakeShelves shelves = new FakeShelves(54).holding(0, WOOD, 64).synced(WOOD, 64);
        FakeStoreWorld world = new FakeStoreWorld().give(store, shelves);

        StoreSync.reconcile(world, s);

        assertEquals(64, store.stores().get(WOOD), "nothing happened, so nothing moved");
        assertEquals(0, world.ledgerSaves, "and the saved data was not troubled");
        assertEquals(0, shelves.finishes,
                "nor the chunk — redrawing identical stacks once a second was rewriting it to "
                        + "disk on every autosave");
    }

    // --- a player's hand ---

    @Test
    void aStackTakenOutIsAStackTheBuildingNoLongerHas() {
        Settlement s = town();
        Building store = store("storehouse", 0);
        s.addBuilding(store);
        store.stores().set(WOOD, 128);
        FakeShelves shelves = new FakeShelves(54).holding(0, WOOD, 64).synced(WOOD, 128);
        FakeStoreWorld world = new FakeStoreWorld().give(store, shelves);

        StoreSync.reconcile(world, s);

        assertEquals(64, store.stores().get(WOOD), "sixty-four left the shelves");
        assertEquals(1, world.ledgerSaves, "and the books were saved, because they moved");
    }

    @Test
    void aStackPutInIsADonation() {
        Settlement s = town();
        Building store = store("storehouse", 0);
        s.addBuilding(store);
        store.stores().set(WOOD, 64);
        FakeShelves shelves = new FakeShelves(54)
                .holding(0, WOOD, 64).holding(1, WOOD, 64).synced(WOOD, 64);
        FakeStoreWorld world = new FakeStoreWorld().give(store, shelves);

        StoreSync.reconcile(world, s);

        assertEquals(128, store.stores().get(WOOD), "the town keeps what it was given");
    }

    // --- laying the ledger out ---

    @Test
    void aLedgerWithNoShelvesShowingItIsDrawnOut() {
        Settlement s = town();
        Building store = store("storehouse", 0);
        s.addBuilding(store);
        store.stores().set(WOOD, 100);
        FakeShelves shelves = new FakeShelves(54);
        FakeStoreWorld world = new FakeStoreWorld().give(store, shelves);

        StoreSync.reconcile(world, s);

        assertEquals(100, shelves.totalOf(WOOD), "all of it, across as many slots as it takes");
        assertEquals(100, shelves.lastSynced(WOOD),
                "and the snapshot records what was actually laid");
        assertEquals(1, shelves.finishes);
    }

    @Test
    void shelvesTooSmallShowWhatFitsAndMeasureAgainstThat() {
        // One slot holds sixty-four; the building holds a hundred. The overflow
        // is out of sight, not out of the ledger — and a withdrawal must be
        // measured against what was on the shelves, not what the town owns.
        Settlement s = town();
        Building store = store("storehouse", 0);
        s.addBuilding(store);
        store.stores().set(WOOD, 100);
        FakeShelves shelves = new FakeShelves(1);
        FakeStoreWorld world = new FakeStoreWorld().give(store, shelves);

        StoreSync.reconcile(world, s);
        assertEquals(64, shelves.totalOf(WOOD), "one slot's worth is shown");
        assertEquals(64, shelves.lastSynced(WOOD), "and that is what is measured against");
        assertEquals(100, store.stores().get(WOOD), "the rest is still owned");

        // Now somebody empties the visible slot.
        shelves.empty(0);
        StoreSync.reconcile(world, s);
        assertEquals(36, store.stores().get(WOOD),
                "sixty-four taken, not a hundred — the unseen stock was never on offer");
    }

    // --- the two dupes, now reachable ---

    @Test
    void eachStoreAnswersToItsOwnLedgerAndNoOneElses() {
        // The dupe: two chests once mirrored a single town-wide figure, so both
        // were entitled to show all of it and whichever went unread kept its
        // stock for the taking.
        Settlement s = town();
        Building warehouse = store("warehouse", 0);
        Building storehouse = store("storehouse", 200);
        s.addBuilding(warehouse);
        s.addBuilding(storehouse);
        warehouse.stores().set(WOOD, 300);
        storehouse.stores().set(WOOD, 180);

        FakeShelves near = new FakeShelves(54);
        FakeShelves far = new FakeShelves(54);
        StoreSync.reconcile(new FakeStoreWorld().give(warehouse, near).give(storehouse, far), s);

        assertEquals(300, near.totalOf(WOOD), "the warehouse shows the warehouse's timber");
        assertEquals(180, far.totalOf(WOOD), "and the storehouse shows its own");
        assertEquals(300, warehouse.stores().get(WOOD), "neither ledger was touched by the other");
        assertEquals(180, storehouse.stores().get(WOOD));
    }

    @Test
    void carryingStockBetweenStoresIsAWithdrawalAndADonation() {
        Settlement s = town();
        Building warehouse = store("warehouse", 0);
        Building storehouse = store("storehouse", 200);
        s.addBuilding(warehouse);
        s.addBuilding(storehouse);
        warehouse.stores().set(WOOD, 128);
        storehouse.stores().set(WOOD, 0);

        FakeShelves near = new FakeShelves(54).holding(0, WOOD, 64).synced(WOOD, 128);
        FakeShelves far = new FakeShelves(54).holding(0, WOOD, 64).synced(WOOD, 0);
        FakeStoreWorld world = new FakeStoreWorld().give(warehouse, near).give(storehouse, far);

        StoreSync.reconcile(world, s);

        assertEquals(64, warehouse.stores().get(WOOD), "the warehouse is lighter by what left it");
        assertEquals(64, storehouse.stores().get(WOOD), "and the storehouse heavier by the same");
    }

    // --- the crash, and the things around it ---

    @Test
    void somethingTheStoresHaveNoUseForIsSteppedAroundAndLeftAlone() {
        // A diamond dropped in. It must not be counted, must not be cleared —
        // the chest is not the mirror's to tidy — and must not stop the pass.
        Settlement s = town();
        Building store = store("storehouse", 0);
        s.addBuilding(store);
        store.stores().set(WOOD, 64);
        FakeShelves shelves = new FakeShelves(54).foreign(0);
        FakeStoreWorld world = new FakeStoreWorld().give(store, shelves);

        StoreSync.reconcile(world, s);

        assertTrue(shelves.isForeign(0), "somebody else's diamond is still their diamond");
        assertEquals(64, shelves.totalOf(WOOD), "and the timber went in beside it");
        assertEquals(64, store.stores().get(WOOD), "with the ledger none the wiser");
    }

    @Test
    void aResourceWithNothingToPayItOutInIsSkippedRatherThanCrashed() {
        Settlement s = town();
        Building store = store("storehouse", 0);
        s.addBuilding(store);
        store.stores().set(WOOD, 64);
        store.stores().set(STONE, 64);
        FakeShelves shelves = new FakeShelves(54).withNothingToPayOut(WOOD);
        FakeStoreWorld world = new FakeStoreWorld().give(store, shelves);

        StoreSync.reconcile(world, s);

        assertEquals(0, shelves.totalOf(WOOD), "nothing to show it with, so nothing is shown");
        assertEquals(64, shelves.totalOf(STONE), "and the rest of the store is unaffected");
        assertEquals(64, store.stores().get(WOOD), "the goods are still owned, just not visible");
    }

    // --- which buildings are even asked ---

    @Test
    void aBuildingWithNoShelvesIsPassedOver() {
        Settlement s = town();
        Building store = store("storehouse", 0);
        s.addBuilding(store);
        store.stores().set(WOOD, 64);

        StoreSync.reconcile(new FakeStoreWorld(), s);

        assertEquals(64, store.stores().get(WOOD),
                "a store whose chunk is away keeps its goods; that is the whole design");
    }

    @Test
    void aBuildingThatIsNotAStoreIsNeverAsked() {
        Settlement s = town();
        Building smithy = new Building("kingdoms:smith", new SimPos(0, 64, 0), 1, true);
        s.addBuilding(smithy);
        FakeShelves shelves = new FakeShelves(54).holding(0, WOOD, 64);

        StoreSync.reconcile(new FakeStoreWorld().give(smithy, shelves), s);

        assertEquals(64, shelves.totalOf(WOOD), "the forge's own chest is not the town's store");
        assertEquals(0, smithy.stores().get(WOOD), "and nothing was read out of it");
    }

    @Test
    void aRewriteClearsWhatTheStoresRecogniseBeforeLayingOutAgain() {
        // Otherwise the old stacks and the new ones would both be counted on
        // the following pass, which reads as a donation the town never had.
        Settlement s = town();
        Building store = store("storehouse", 0);
        s.addBuilding(store);
        store.stores().set(WOOD, 10);
        FakeShelves shelves = new FakeShelves(54)
                .holding(5, WOOD, 64).holding(9, WOOD, 64).synced(WOOD, 128);
        FakeStoreWorld world = new FakeStoreWorld().give(store, shelves);

        StoreSync.reconcile(world, s);

        assertEquals(10, shelves.totalOf(WOOD), "exactly the ledger, laid out fresh");
        assertTrue(shelves.isEmpty(5) || shelves.resourceAt(0) != null,
                "the old stacks were cleared before the new one went down");
        assertNull(shelves.resourceAt(9), "and nothing was left behind further along");
        assertFalse(shelves.isEmpty(0), "the fresh stack starts at the front");
    }
}
