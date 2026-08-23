package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.StorehousePlanner;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The storehouse's half of the player economy: donations in, timber out,
 * and the reserve that is not for sale at any price.
 */
class StorehouseTradeTest {

    private static Settlement town() {
        return new Settlement(Settlement.Id.random(), "Testburg",
                new SimPos(0, 64, 0), 128);
    }

    @Test
    void timberSellsByTheEmeraldDownToTheReserveAndNoFurther() {
        Settlement s = town();
        s.setStock(TownStores.WOOD, StorehousePlanner.RESERVE_WOOD + 20);

        assertEquals(20, StorehousePlanner.timberForSale(s),
                "everything above the reserve is on the table");
        assertEquals(16, StorehousePlanner.sellTimber(s, 2),
                "two emeralds buy two full bundles");
        assertEquals(4, StorehousePlanner.sellTimber(s, 5),
                "a rich buyer still only gets what is above the reserve");
        assertEquals(StorehousePlanner.RESERVE_WOOD, s.woodStock(),
                "the reserve survives every sale — repairs are not for purchase");
        assertEquals(0, StorehousePlanner.sellTimber(s, 1),
                "at the reserve the answer is no");
    }

    @Test
    void donationsFillTheStoreAndStopAtItsCapacity() {
        Settlement s = town();
        int ceiling = LumberPlanner.woodCapacity(s);
        s.setStock(TownStores.WOOD, ceiling - 5);

        assertEquals(5, StorehousePlanner.donate(s, s.centre(), TownStores.WOOD, 64),
                "a generous donation is taken only as far as the racks hold");
        assertEquals(ceiling, s.woodStock(),
                "the store sits exactly at capacity afterwards");
        assertEquals(0, StorehousePlanner.donate(s, s.centre(), TownStores.WOOD, 1),
                "a full store takes nothing more");
        assertEquals(0, StorehousePlanner.donate(s, s.centre(), TownStores.IRON, 10),
                "stores with no donation channel refuse politely");
    }
}
