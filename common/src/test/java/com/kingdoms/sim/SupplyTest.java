package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers supply actually being limited: a town spends what it has, and when it
 * runs out it goes and builds whatever would make more.
 */
class SupplyTest {

    private static final BuildingType LUMBER_CAMP =
            new BuildingType("kingdoms:lumber_camp", 30, 1, 1, 0, 68, 0);

    private static Settlement town() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        s.setCatalogue(List.of(LUMBER_CAMP));
        return s;
    }

    @Test
    void takingIsAllOrNothing() {
        TownStores stores = new TownStores();
        stores.set(TownStores.WOOD, 10);

        assertFalse(stores.take(TownStores.WOOD, 11), "cannot pay what it does not have");
        assertEquals(10, stores.get(TownStores.WOOD), "and a refused payment costs nothing");

        assertTrue(stores.take(TownStores.WOOD, 10));
        assertEquals(0, stores.get(TownStores.WOOD));
    }

    @Test
    void storesNeverGoNegative() {
        TownStores stores = new TownStores();
        stores.add(TownStores.STONE, -50);
        assertEquals(0, stores.get(TownStores.STONE));

        stores.set(TownStores.STONE, 5);
        assertEquals(3, stores.takeUpTo(TownStores.STONE, 3));
        assertEquals(2, stores.takeUpTo(TownStores.STONE, 99), "takes what is there, no more");
        assertEquals(0, stores.get(TownStores.STONE));
    }

    @Test
    void addingIsCappedByStorage() {
        TownStores stores = new TownStores();
        stores.set(TownStores.WOOD, 90);
        assertEquals(10, stores.addCapped(TownStores.WOOD, 50, 100), "only what fits");
        assertEquals(100, stores.get(TownStores.WOOD));
        assertEquals(0, stores.addCapped(TownStores.WOOD, 50, 100), "and nothing once full");
    }

    @Test
    void afoundingPartyArrivesWithEnoughToStart() {
        Settlement s = town();
        assertTrue(s.woodStock() > 0, "a town that starts with nothing could never build the");
        assertTrue(s.stoneStock() > 0, "very buildings that let it produce anything");
        assertTrue(s.foodStock() > 0, "and it has to eat while it works");
    }

    @Test
    void runningOutOrdersTheBuildingThatFixesIt() {
        Settlement s = town();
        s.enqueueBuild(new BuildTask("kingdoms:house", new SimPos(10, 64, 10), 20));

        assertTrue(BuildPlanner.requestProducer(s, TownStores.WOOD, 5));
        assertEquals("kingdoms:lumber_camp", s.buildQueue().getFirst().blueprintId(),
                "the camp jumps the queue, ahead of the house nobody can pay for");
        assertTrue(s.events().stream().anyMatch(e -> e.message().contains("Out of wood")),
                "and the town says why it changed its mind");
    }

    @Test
    void aTownThatAlreadyHasOneJustGoesShort() {
        Settlement s = town();
        s.addBuilding(new Building("kingdoms:lumber_camp", new SimPos(4, 64, 4), 1, true));

        assertFalse(BuildPlanner.requestProducer(s, TownStores.WOOD, 5),
                "the camp exists — the shortage is real, not a missing building");
        assertTrue(s.buildQueue().isEmpty());
    }

    @Test
    void theSameProducerIsNeverOrderedTwice() {
        Settlement s = town();
        assertTrue(BuildPlanner.requestProducer(s, TownStores.WOOD, 5));
        assertFalse(BuildPlanner.requestProducer(s, TownStores.WOOD, 6),
                "already on the way; a standing shortage must not flood the queue");
        assertEquals(1, s.buildQueue().size());
    }

    @Test
    void nothingIsOrderedForAResourceNobodyMakes() {
        Settlement s = town();
        assertFalse(BuildPlanner.requestProducer(s, "emeralds", 5));
        assertTrue(s.buildQueue().isEmpty());
    }
}
