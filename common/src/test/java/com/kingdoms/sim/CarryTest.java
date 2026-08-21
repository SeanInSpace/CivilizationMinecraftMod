package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers a builder carrying a load rather than conjuring materials at the wall. */
class CarryTest {

    private static Person builder() {
        return new Person(Person.Id.random(), "Digger", Profession.BUILDER, new SimPos(0, 64, 0));
    }

    @Test
    void abuilderStartsEmptyHanded() {
        Person person = builder();
        assertNull(person.carriedMaterial());
        assertEquals(0, person.carriedLoad());
        assertFalse(person.carries(TownStores.WOOD));
    }

    @Test
    void aloadPaysOnlyForWhatItIsMadeOf() {
        Person person = builder();
        person.setCarry(TownStores.WOOD, 4);

        assertTrue(person.carries(TownStores.WOOD));
        assertFalse(person.carries(TownStores.STONE), "timber does not pay for masonry");
        assertFalse(person.carries(null));
    }

    @Test
    void aloadIsSpentBlockByBlockAndThenGone() {
        Person person = builder();
        person.setCarry(TownStores.STONE, 2);

        person.spendCarry();
        assertEquals(1, person.carriedLoad());
        assertTrue(person.carries(TownStores.STONE));

        person.spendCarry();
        assertEquals(0, person.carriedLoad());
        assertNull(person.carriedMaterial(), "an empty load is no load at all");
        assertFalse(person.carries(TownStores.STONE));
    }

    @Test
    void spendingPastEmptyIsHarmless() {
        Person person = builder();
        person.spendCarry();
        assertEquals(0, person.carriedLoad());
    }

    @Test
    void stockLeavesTheLedgerWhenItIsPickedUp() {
        // The whole point of drawing at the warehouse rather than at the wall: a
        // load in transit is genuinely out of the stores, so a carrier killed on
        // the road takes it with them.
        TownStores stores = new TownStores();
        stores.set(TownStores.WOOD, 20);
        Person person = builder();

        int drawn = stores.takeUpTo(TownStores.WOOD, 16);
        person.setCarry(TownStores.WOOD, drawn);

        assertEquals(16, drawn);
        assertEquals(4, stores.get(TownStores.WOOD), "the stores are lighter immediately");
        assertEquals(16, person.carriedLoad());
    }

    @Test
    void anEmptyStoreLoadsNobody() {
        TownStores stores = new TownStores();
        Person person = builder();

        int drawn = stores.takeUpTo(TownStores.WOOD, 16);
        person.setCarry(TownStores.WOOD, drawn);

        assertEquals(0, drawn);
        assertFalse(person.carries(TownStores.WOOD));
    }
}
