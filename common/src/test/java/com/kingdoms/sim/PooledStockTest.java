package com.kingdoms.sim;

import com.kingdoms.sim.settlement.PooledStock;
import com.kingdoms.sim.settlement.Stock;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town's holdings spread across the buildings that hold them.
 *
 * <p>The cases that matter are the ones where "where is it" changes the answer:
 * a spend that has to reach across two stores, a spend that must not half-drain
 * them when it cannot be paid, and a transfer between stores — which the old
 * single pool could not see at all, and which is exactly the thing a courier
 * will one day have to walk.
 */
class PooledStockTest {

    private static final String WOOD = TownStores.WOOD;

    private final List<Stock> holders = new ArrayList<>();
    private final PooledStock pool = new PooledStock(() -> holders);

    private TownStores holder(int wood) {
        TownStores store = new TownStores();
        store.set(WOOD, wood);
        holders.add(store);
        return store;
    }

    @Test
    void theTownTotalIsWhatItsBuildingsAddUpTo() {
        holder(300);
        holder(180);

        assertEquals(480, pool.get(WOOD), "nothing stores the total; it is counted");
        assertTrue(pool.has(WOOD, 480));
        assertFalse(pool.has(WOOD, 481));
        assertEquals(480, pool.all().get(WOOD), "and the merged view agrees");
    }

    @Test
    void aSpendReachesAcrossStoresInOrder() {
        TownStores first = holder(300);
        TownStores second = holder(180);

        assertTrue(pool.take(WOOD, 400), "the town owns four hundred, in two places");

        assertEquals(0, first.get(WOOD), "the nearer store is emptied first");
        assertEquals(80, second.get(WOOD), "and the rest comes from the next one along");
        assertEquals(80, pool.get(WOOD));
    }

    @Test
    void aSpendItCannotAffordLeavesEveryStoreUntouched() {
        // The failure worth guarding: drawing holder by holder until the money
        // runs out would empty two buildings and then report that nothing was
        // bought, with the goods already gone.
        TownStores first = holder(300);
        TownStores second = holder(180);

        assertFalse(pool.take(WOOD, 500), "four hundred and eighty does not buy five hundred");

        assertEquals(300, first.get(WOOD), "and no building paid toward it");
        assertEquals(180, second.get(WOOD));
    }

    @Test
    void takeUpToDrainsAsFarAsItReachesAndSaysSo() {
        TownStores first = holder(300);
        TownStores second = holder(180);

        assertEquals(480, pool.takeUpTo(WOOD, 900), "it reports what it got, not what it wanted");

        assertEquals(0, first.get(WOOD));
        assertEquals(0, second.get(WOOD));
    }

    @Test
    void produceLandsInOnePlaceRatherThanBeingSmearedAcrossTheTown() {
        TownStores first = holder(0);
        TownStores second = holder(180);

        pool.add(WOOD, 64);

        assertEquals(64, first.get(WOOD), "a felled log goes somewhere in particular");
        assertEquals(180, second.get(WOOD), "and the far store never saw it");
    }

    @Test
    void aCeilingIsMeasuredAgainstTheTownAndNotOneBuilding() {
        holder(300);
        holder(180);

        // A cap of 500 against a town already holding 480 leaves room for 20,
        // even though neither building is anywhere near 500 on its own.
        assertEquals(20, pool.addCapped(WOOD, 64, 500), "the ceiling is the town's");
        assertEquals(500, pool.get(WOOD));
        assertEquals(0, pool.addCapped(WOOD, 64, 500), "and once full, nothing more fits");
    }

    @Test
    void carryingStockBetweenStoresIsARealTransfer() {
        // The whole point of the reshape. Under one town-wide number this was
        // invisible, which is what let two chests mirroring the same figure
        // hand out timber twice. Now the goods are somewhere, and moving them
        // is a thing that happened.
        TownStores warehouse = holder(300);
        TownStores storehouse = holder(180);

        int carried = warehouse.takeUpTo(WOOD, 180);
        storehouse.add(WOOD, carried);

        assertEquals(120, warehouse.get(WOOD), "the warehouse is lighter by what left it");
        assertEquals(360, storehouse.get(WOOD), "and the storehouse heavier by the same");
        assertEquals(480, pool.get(WOOD), "the town is no richer for the walk");
    }

    @Test
    void aTownWithNowhereToPutGoodsDropsThemRatherThanInventingAStore() {
        // Documented rather than desirable: an empty pool has no holder to
        // deposit into. A settlement must always contribute its own pile as
        // the first holder, and this test exists so that rule is not quietly
        // forgotten by whoever wires the settlement up.
        assertEquals(0, pool.add(WOOD, 64), "nothing held it, so nothing is held");
        assertEquals(0, pool.addCapped(WOOD, 64, 500), "and the ceiling is not billed for it");
        assertEquals(0, pool.get(WOOD));
        assertFalse(pool.take(WOOD, 1), "and there is nothing to spend");
    }

    @Test
    void losingABuildingLosesWhatWasInIt() {
        // Holders are read fresh every call precisely so that this works: a
        // razed warehouse takes its contents with it, without anything having
        // to remember to adjust a town-wide figure.
        holder(300);
        TownStores razed = holder(180);

        assertEquals(480, pool.get(WOOD));
        holders.remove(razed);
        assertEquals(300, pool.get(WOOD), "the town is poorer by exactly what burned");
    }
}
