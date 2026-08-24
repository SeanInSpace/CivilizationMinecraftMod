package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SupplyPlanner;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrying bulk goods to the store that is about to need them.
 *
 * <p>The property worth defending is that this settles. An "even the stores
 * out" rule oscillates — every move creates the imbalance that justifies moving
 * something back — so most of these are about when a courier is <em>not</em>
 * sent.
 */
class SupplyPlannerTest {

    private static final String WOOD = TownStores.WOOD;

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX =
            new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    private static Settlement town() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        s.setStock(WOOD, 0);
        s.setStock(TownStores.STONE, 0);
        s.setStock(TownStores.FOOD, 0);
        return s;
    }

    private static Building storeAt(Settlement s, int x) {
        Building store = new Building("kingdoms:storehouse", new SimPos(x, 64, 0), 1, true);
        s.addBuilding(store);
        return store;
    }

    private static Person hand(Settlement s) {
        Person person = new Person(Person.Id.random(), "Hand", Profession.PIONEER, s.centre());
        s.addResident(person);
        return person;
    }

    /** A build at this spot, so the planner has somewhere it wants goods. */
    private static void buildingAt(Settlement s, int x) {
        s.enqueueBuild(new com.kingdoms.sim.settlement.BuildTask(
                "kingdoms:house", new SimPos(x, 64, 0), 40));
    }

    @Test
    void aStoreShortOfTimberBesideTheBuildGetsALoad() {
        Settlement s = town();
        Building near = storeAt(s, 0);
        Building far = storeAt(s, 300);
        far.stores().set(WOOD, SupplyPlanner.SHORTAGE + SupplyPlanner.LOAD);
        Person carrier = hand(s);
        buildingAt(s, 10);

        SupplyPlanner.advance(s, CTX);

        HaulTask errand = carrier.haul();
        assertNotNull(errand, "somebody was sent");
        assertEquals(WOOD, errand.resource());
        assertEquals(far.origin(), errand.fromPos(), "from the store that can spare it");
        assertEquals(near.origin(), errand.toPos(), "to the one beside the work");
        assertEquals(HaulTask.Store.STORE, errand.fromStore());
    }

    @Test
    void nobodyIsSentWhenTheTownIsBuildingNothing() {
        // No build is no demand. Goods sit where they were made, which is the
        // whole reason they were put there.
        Settlement s = town();
        storeAt(s, 0);
        storeAt(s, 300).stores().set(WOOD, 1000);
        Person carrier = hand(s);

        SupplyPlanner.advance(s, CTX);

        assertNull(carrier.haul(), "nothing is waiting on goods, so nothing moves");
    }

    @Test
    void aSourceIsNeverDrainedIntoAShortageOfItsOwn() {
        // The oscillation guard. The far store has more than the near one, but
        // not a whole load above the shortage line — moving sixty-four would
        // simply reverse which of them was short, and next step the courier
        // would carry it back.
        Settlement s = town();
        storeAt(s, 0);
        Building far = storeAt(s, 300);
        far.stores().set(WOOD, SupplyPlanner.SHORTAGE + SupplyPlanner.LOAD - 1);
        Person carrier = hand(s);
        buildingAt(s, 10);

        SupplyPlanner.advance(s, CTX);

        assertNull(carrier.haul(), "a load it cannot spare is a load it does not send");
    }

    @Test
    void aStoreThatAlreadyHasEnoughIsLeftAlone() {
        Settlement s = town();
        storeAt(s, 0).stores().set(WOOD, SupplyPlanner.SHORTAGE);
        storeAt(s, 300).stores().set(WOOD, 1000);
        Person carrier = hand(s);
        buildingAt(s, 10);

        SupplyPlanner.advance(s, CTX);

        assertNull(carrier.haul(), "at the line is not short");
    }

    @Test
    void oneCourierAStepAndNoMore() {
        // Several errands against the same shelves all look affordable at the
        // moment they are handed out, because the arithmetic happens when a
        // load is picked up. Sending everybody at once would strip the source.
        Settlement s = town();
        storeAt(s, 0);
        storeAt(s, 300).stores().set(WOOD, 10_000);
        Person first = hand(s);
        Person second = hand(s);
        Person third = hand(s);
        buildingAt(s, 10);

        SupplyPlanner.advance(s, CTX);

        long sent = java.util.stream.Stream.of(first, second, third)
                .filter(p -> p.haul() != null).count();
        assertEquals(1, sent, "one errand a step, however many hands are idle");
    }

    @Test
    void aTownWithOneStoreHasNowhereToCarryFrom() {
        Settlement s = town();
        storeAt(s, 0).stores().set(WOOD, 0);
        Person carrier = hand(s);
        buildingAt(s, 10);

        SupplyPlanner.advance(s, CTX);

        assertNull(carrier.haul(), "a store cannot restock itself");
    }

    @Test
    void nobodyFreeMeansNobodyGoes() {
        Settlement s = town();
        storeAt(s, 0);
        Building far = storeAt(s, 300);
        far.stores().set(WOOD, 1000);
        Person busy = hand(s);
        busy.setHaul(new HaulTask(HaulTask.Store.GRANARY, s.centre(),
                HaulTask.Store.HOME, s.centre(), 8));
        buildingAt(s, 10);

        SupplyPlanner.advance(s, CTX);

        assertEquals(HaulTask.Store.GRANARY, busy.haul().fromStore(),
                "the errand they were already on is not taken off them");
    }

    @Test
    void theCourierPicksTheFullestStoreThatCanSpareIt() {
        Settlement s = town();
        storeAt(s, 0);
        Building modest = storeAt(s, 200);
        Building flush = storeAt(s, 400);
        modest.stores().set(WOOD, SupplyPlanner.SHORTAGE + SupplyPlanner.LOAD);
        flush.stores().set(WOOD, 5000);
        Person carrier = hand(s);
        buildingAt(s, 10);

        SupplyPlanner.advance(s, CTX);

        assertEquals(flush.origin(), carrier.haul().fromPos(),
                "a town evens out from the top, not by passing its last stack around");
    }

    @Test
    void aRoundTripActuallyMovesTheGoods() {
        // End to end through HaulPlanner: the load leaves one building's ledger
        // and arrives in the other's, with nothing created or lost on the way.
        Settlement s = town();
        Building near = storeAt(s, 0);
        Building far = storeAt(s, 300);
        far.stores().set(WOOD, 500);
        Person carrier = hand(s);
        buildingAt(s, 10);

        SupplyPlanner.advance(s, CTX);
        assertNotNull(carrier.haul());

        // Walk them there and back; unwatched travel advances on its own.
        for (int step = 0; step < 200 && carrier.haul() != null; step++) {
            com.kingdoms.sim.settlement.HaulPlanner.advance(s, CTX);
        }

        assertEquals(500, near.stores().get(WOOD) + far.stores().get(WOOD),
                "the town is no richer and no poorer for the walk");
        assertTrue(near.stores().get(WOOD) > 0, "and the near store has something now");
    }
}
