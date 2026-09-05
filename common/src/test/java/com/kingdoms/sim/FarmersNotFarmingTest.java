package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FieldRoster;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.HaulPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SupplyPlanner;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Farmers are not farming."
 *
 * <p>Every way a farmer stops working the rows, one test each, so the next
 * report of this can be answered by asking which of them it is.
 *
 * <p>The one that was a genuine fault is the first: an errand fired for a single
 * loaf. A farmer with a haul is a farmer out of the field — {@code
 * PersonEntityManager.workFarmers} skips them, correctly, because they are on
 * the road — so a field that sent for collection at one loaf sent for it every
 * step, and a player standing in the field watched three farmers walking laps.
 * The rest are here as the record of what was checked and found sound.
 *
 * <p>Two of the reasons are not repeated here because {@code FoodPlannerTest}
 * already holds them, and a second copy of an assertion is a second thing to
 * keep true rather than a second guarantee. Named so the list stays complete:
 * a farmer too weak with hunger downs tools while the town can still feed him
 * and keeps cutting once it cannot ({@code theWeakStopWorkingWhileTheTownCanStillFeedThem},
 * {@code theWeakKeepFarmingOnceTheTownIsStarving}), and a field no real hands
 * can reach — a cliff, a fence, a pathing failure — is credited by the clock
 * after {@code WATCHED_HARVEST_GRACE_STEPS}
 * ({@code theClockFloorsAWatchedFarmNobodyCanReach}). That last one is the
 * difference a player sees: the town does not starve, but the rows stand
 * untended while the granary fills, so a report of "farmers not farming" on a
 * fenced-in field is the floor working, not a fault.
 */
class FarmersNotFarmingTest {

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX =
            new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    /**
     * A fed town, so the starvation lane stays out of these — it is allowed to
     * break every rule below and does. Comfortably fed and comfortably under the
     * bare granary's two hundred, so there is room to deliver into as well.
     */
    private static Settlement fedTown() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 512);
        s.setFoodStock(100);
        s.setStock(TownStores.WOOD, 0);
        s.setStock(TownStores.STONE, 0);
        return s;
    }

    private static Building field(Settlement s, int x, int held) {
        Building farm = new Building("kingdoms:farm", new SimPos(x, 64, 0), 1, true);
        farm.setFoodStored(held);
        s.addBuilding(farm);
        return farm;
    }

    private static Person farmer(Settlement s) {
        Person person = new Person(Person.Id.random(),
                "Farmer " + s.population(), Profession.FARMER, s.centre());
        s.addResident(person);
        return person;
    }

    // --- the fault ---

    @Test
    void aSingleLoafIsNotWorthLeavingTheRowsFor() {
        Settlement s = fedTown();
        field(s, 40, 1);
        Person farmer = farmer(s);

        FoodPlanner.advance(s, CTX);

        assertNull(farmer.haul(),
                "one loaf does not buy a round trip; he keeps cutting");
    }

    @Test
    void aFullLoadIsFetched() {
        Settlement s = fedTown();
        Building farm = field(s, 40, FoodPlanner.WORTH_LEAVING_THE_ROWS);
        Person farmer = farmer(s);

        FoodPlanner.advance(s, CTX);

        assertNotNull(farmer.haul(), "a load worth carrying is carried");
        assertEquals(farm.origin(), farmer.haul().fromPos());
    }

    @Test
    void oneFieldFeedsOneErrand() {
        // Three farmers all dispatched to the same twelve loaves is one delivery
        // and two wasted walks — and on a watched farm a wasted walk is a farmer
        // who was not farming.
        Settlement s = fedTown();
        field(s, 40, FoodPlanner.WORTH_LEAVING_THE_ROWS);
        Person first = farmer(s);
        Person second = farmer(s);
        Person third = farmer(s);

        FoodPlanner.advance(s, CTX);

        long sent = java.util.stream.Stream.of(first, second, third)
                .filter(p -> p.haul() != null).count();
        assertEquals(1, sent, "one field, one errand, two farmers still in the rows");
    }

    @Test
    void twoFullFieldsSendTwoFarmers() {
        Settlement s = fedTown();
        field(s, 40, FoodPlanner.WORTH_LEAVING_THE_ROWS);
        field(s, 80, FoodPlanner.WORTH_LEAVING_THE_ROWS);
        Person first = farmer(s);
        Person second = farmer(s);

        FoodPlanner.advance(s, CTX);

        assertNotNull(first.haul());
        assertNotNull(second.haul());
        assertTrue(!first.haul().fromPos().equals(second.haul().fromPos()),
                "and to different fields, not both to the same one");
    }

    @Test
    void aStarvingTownFetchesEveryLoafItHas() {
        // The exemption, and the one this rule must never override. A loaf is a
        // life here, and the walk is worth making for any of it.
        Settlement s = new Settlement(Settlement.Id.random(), "Hungry", new SimPos(0, 64, 0), 512);
        s.setFoodStock(0);
        field(s, 40, 1);
        Person farmer = farmer(s);

        assertTrue(s.isStarving(), "the premise");
        FoodPlanner.advance(s, CTX);

        assertNotNull(farmer.haul(), "starving, a single loaf is worth the walk");
    }

    @Test
    void aFullGranaryIsNotOverfilledByLoadsAlreadyOnTheRoad() {
        // The cost of making every load a full twelve. The granary's stock does
        // not move until a carrier arrives, so a budget that only subtracts this
        // step's errands offers the same headroom again on every step of a walk
        // that takes several -- and deposit spoils the overshoot rather than
        // duplicating it. At one or two loaves a trip that was rounding; at
        // twelve it is destroyed food.
        Settlement s = fedTown();
        s.setFoodStock(FoodPlanner.BASE_GRANARY - 20);   // room for one load, not two
        field(s, 40, FoodPlanner.WORTH_LEAVING_THE_ROWS);
        field(s, 80, FoodPlanner.WORTH_LEAVING_THE_ROWS);
        Person first = farmer(s);
        Person second = farmer(s);

        FoodPlanner.advance(s, CTX);
        FoodPlanner.advance(s, CTX);   // nothing has arrived yet; the walk is not over

        long sent = java.util.stream.Stream.of(first, second)
                .filter(p -> p.haul() != null).count();
        assertEquals(1, sent,
                "the second load has nowhere to go, and the field is a safer place to keep it");
    }

    @Test
    void theGrainStillGetsIn() {
        // Batching the trips must not cost the town throughput: the same grain
        // reaches the granary, in fewer and fuller journeys.
        Settlement s = fedTown();
        Building granary = new Building("kingdoms:granary", new SimPos(4, 64, 0), 1, true);
        s.addBuilding(granary);
        field(s, 40, 0);
        farmer(s);
        farmer(s);
        int banked = s.foodStock();

        for (int step = 0; step < 40; step++) {
            FoodPlanner.advance(s, CTX);
            HaulPlanner.advance(s, CTX);
        }

        assertTrue(s.foodStock() >= banked + FoodPlanner.WORTH_LEAVING_THE_ROWS,
                "loads are still arriving, they are just whole ones");
    }

    // --- checked, and sound ---

    @Test
    void aFarmerIsNotConscriptedAsACourier() {
        // Was already true and is now true for a reason that survives: the
        // farmer's trade has work in front of it while the town has a field.
        Settlement s = fedTown();
        field(s, 40, 0);
        Building near = new Building("kingdoms:storehouse", new SimPos(0, 64, 0), 1, true);
        s.addBuilding(near);
        Building far = new Building("kingdoms:storehouse", new SimPos(300, 64, 0), 1, true);
        far.stores().set(TownStores.WOOD, SupplyPlanner.SHORTAGE + SupplyPlanner.LOAD);
        s.addBuilding(far);
        s.enqueueBuild(new BuildTask("kingdoms:house", new SimPos(10, 64, 0), 40));
        Person farmer = farmer(s);

        SupplyPlanner.advance(s, CTX);

        assertNull(farmer.haul(), "the fields are not a labor pool for the timber");
    }

    @Test
    void aFieldThatWasPulledDownTakesNobodyWithIt() {
        // Nothing stores a farmer's field assignment, so a demolished farm
        // simply drops out of the roster and the next one is dealt in its place.
        Settlement s = fedTown();
        Building first = field(s, 40, 0);
        Building second = field(s, 80, 0);
        Person farmer = farmer(s);

        Building was = FieldRoster.fieldFor(s, farmer);
        assertNotNull(was);
        s.removeBuilding(was, 1, "pulled down in the test");

        Building now = FieldRoster.fieldFor(s, farmer);
        assertNotNull(now, "the survivor is dealt to him at once");
        assertSame(was == first ? second : first, now);
    }

    @Test
    void aFarmerWithNoFieldAtAllIsNotLeftStandingAround() {
        // The one case where a farmer genuinely has nothing to farm. There is no
        // field, so there is no field work, so the hands are better spent
        // walking than waiting for a farm that has not been built yet.
        Settlement s = fedTown();
        Building near = new Building("kingdoms:storehouse", new SimPos(0, 64, 0), 1, true);
        s.addBuilding(near);
        Building far = new Building("kingdoms:storehouse", new SimPos(300, 64, 0), 1, true);
        far.stores().set(TownStores.WOOD, SupplyPlanner.SHORTAGE + SupplyPlanner.LOAD);
        s.addBuilding(far);
        s.enqueueBuild(new BuildTask("kingdoms:house", new SimPos(10, 64, 0), 40));
        Person farmer = farmer(s);

        assertSame(farmer, HaulPlanner.courierFor(s));
    }

    @Test
    void anUndrawnFieldStillFeedsTheTown() {
        // A farm that exists in the simulation but has not been painted into the
        // world yet is off the roster — FieldRoster wants a materialized
        // building, because a farmer cannot walk rows that are not there. The
        // clock keeps the field producing regardless, which is what stops an
        // unwatched town starving on a farm it has not seen yet.
        Settlement s = fedTown();
        Building undrawn = new Building("kingdoms:farm", new SimPos(40, 64, 0), 1);
        s.addBuilding(undrawn);
        Person farmer = farmer(s);

        assertTrue(FieldRoster.fields(s).isEmpty(), "nothing to walk to yet");
        FoodPlanner.advance(s, CTX);

        assertTrue(undrawn.foodStored() > 0, "and the harvest happens anyway");
        assertNull(FieldRoster.fieldFor(s, farmer));
    }
}
