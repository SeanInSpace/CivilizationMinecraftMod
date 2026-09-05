package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.HaulPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers goods physically traveling on somebody's back. */
class HaulPlannerTest {

    private static final BuildingType MARKET = new BuildingType("test:market", 5, 9999, 0, 0, 65, 0);

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    private static Settlement settlement() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        s.setCatalogue(List.of(MARKET));
        return s;
    }

    private static Person person(Settlement s, SimPos at) {
        Person p = new Person(Person.Id.random(), "Carrier", Profession.IDLER, at);
        s.addResident(p);
        return p;
    }

    @Test
    void beingWatchedMustNeverStarveATown() {
        // An embodied hauler has real legs, and real legs cannot climb every
        // cliff a town builds on. The errand gets a fair spell of walking and is
        // then completed by the clock, exactly as it would have been had nobody
        // been looking -- because the alternative, observed in play, was a
        // parked player watching all twenty-five residents starve to death
        // beside full fields.
        Settlement settlement = new Settlement(
                Settlement.Id.random(), "Cliffside", new SimPos(0, 64, 0), 128);
        Building farm = new Building("kingdoms:farm", new SimPos(40, 96, 0), 1, true);
        settlement.addBuilding(farm);
        farm.setFoodStored(30);

        Person hauler = new Person(Person.Id.random(), "Mab", Profession.FARMER,
                new SimPos(0, 64, 0));
        hauler.setEmbodied(true);   // somebody is watching, so the legs are real
        settlement.addResident(hauler);
        hauler.setHaul(new HaulTask(HaulTask.Store.FARM, farm.origin(),
                HaulTask.Store.GRANARY, new SimPos(0, 64, 4), 10));
        int granaryBefore = settlement.foodStock();

        for (int step = 0; step < HaulPlanner.EMBODIED_STALL_STEPS * 2 + 2; step++) {
            HaulPlanner.advance(settlement, CTX);
            // The entity never gets anywhere: position stays where it is, which
            // is what an impossible path looks like from the record's side.
        }

        assertNull(hauler.haul(), "the errand must finish, walk or no walk");
        assertEquals(20, farm.foodStored(), "the load genuinely left the field");
        assertEquals(granaryBefore + 10, settlement.foodStock(),
                "and genuinely reached the granary");
    }

    @Test
    void travelCoversGroundOneStepAtATime() {
        SimPos from = new SimPos(0, 64, 0);
        SimPos to = new SimPos(100, 64, 0);

        SimPos after = HaulPlanner.stepToward(from, to, HaulPlanner.ABSTRACT_TRAVEL_BLOCKS);

        assertEquals(HaulPlanner.ABSTRACT_TRAVEL_BLOCKS, after.x(), "moved exactly one step");
        assertTrue(after.horizontalDistance(to) < from.horizontalDistance(to), "and got closer");
    }

    @Test
    void travelNeverOvershootsTheDestination() {
        SimPos from = new SimPos(0, 64, 0);
        SimPos to = new SimPos(3, 70, 4);

        assertEquals(to, HaulPlanner.stepToward(from, to, HaulPlanner.ABSTRACT_TRAVEL_BLOCKS),
                "a short hop lands exactly on the destination");
    }

    @Test
    void goodsLeaveTheSourceOnlyWhenCollected() {
        Settlement s = settlement();
        Building stall = new Building(MARKET.id(), new SimPos(40, 64, 0), 0, true);
        stall.setFoodStored(30);
        s.addBuilding(stall);
        Person carrier = person(s, new SimPos(0, 64, 0));
        Household family = new Household(Household.Id.random(), "Family");
        family.addMember(carrier.id());
        family.setHome(new SimPos(0, 64, 0));
        s.addHousehold(family);
        carrier.setHaul(new HaulTask(HaulTask.Store.MARKET, stall.origin(),
                HaulTask.Store.HOME, family.home(), 10));

        HaulPlanner.advance(s, CTX);
        assertEquals(30, stall.foodStored(), "nothing taken while still walking there");

        for (int i = 0; i < 6 && !carrier.haul().isLoaded(); i++) {
            HaulPlanner.advance(s, CTX);
        }
        assertTrue(carrier.haul().isLoaded(), "picked the load up on arrival");
        assertEquals(20, stall.foodStored(), "which is gone from the stall");
        assertEquals(0, family.pantry(), "and is on their back, not yet home");
    }

    @Test
    void deliveryCompletesTheErrand() {
        Settlement s = settlement();
        Building stall = new Building(MARKET.id(), new SimPos(20, 64, 0), 0, true);
        stall.setFoodStored(30);
        s.addBuilding(stall);
        Person carrier = person(s, new SimPos(0, 64, 0));
        Household family = new Household(Household.Id.random(), "Family");
        family.addMember(carrier.id());
        family.setHome(new SimPos(0, 64, 0));
        s.addHousehold(family);
        carrier.setHaul(new HaulTask(HaulTask.Store.MARKET, stall.origin(),
                HaulTask.Store.HOME, family.home(), 10));

        for (int i = 0; i < 10 && carrier.haul() != null; i++) {
            HaulPlanner.advance(s, CTX);
        }

        assertNull(carrier.haul(), "errand finished");
        assertEquals(10, family.pantry(), "the load arrived in the larder");
        assertEquals(20, stall.foodStored());
    }

    @Test
    void aWeakCarrierPutsTheLoadBackWhileTheTownHasFood() {
        // The town's granary is full: this is one hungry carrier, not a famine,
        // and somebody who cannot stand up has no business on the road.
        Settlement s = settlement();
        Building stall = new Building(MARKET.id(), new SimPos(20, 64, 0), 0, true);
        stall.setFoodStored(30);
        s.addBuilding(stall);
        Person carrier = person(s, new SimPos(20, 64, 0));
        carrier.setHaul(new HaulTask(HaulTask.Store.MARKET, stall.origin(),
                HaulTask.Store.GRANARY, new SimPos(0, 64, 0), 10));

        HaulPlanner.advance(s, CTX);
        assertTrue(carrier.haul().isLoaded());
        assertEquals(20, stall.foodStored());

        carrier.setHunger(Person.HUNGER_WEAK);
        HaulPlanner.advance(s, CTX);

        assertNull(carrier.haul(), "too weak to finish the errand");
        assertEquals(30, stall.foodStored(), "and the food is returned, never conjured away");
    }

    @Test
    void aLoadOfGrainIsNotPutBackDownInTheMiddleOfAFamine() {
        // Hunger rises on everybody at once, so in a starving town every carrier
        // crosses the weakness line within a step or two of every other. Each of
        // them setting their load back down at the source is how a town with food
        // in its fields starves anyway.
        Settlement s = settlement();
        s.setFoodStock(0);
        Building stall = new Building(MARKET.id(), new SimPos(20, 64, 0), 0, true);
        stall.setFoodStored(4);
        s.addBuilding(stall);
        Person carrier = person(s, new SimPos(20, 64, 0));
        carrier.setHaul(new HaulTask(HaulTask.Store.MARKET, stall.origin(),
                HaulTask.Store.GRANARY, new SimPos(0, 64, 0), 4));

        HaulPlanner.advance(s, CTX);
        assertTrue(carrier.haul().isLoaded(), "the last of the town's food is on their back");
        assertTrue(s.isStarving());

        carrier.setHunger(Person.HUNGER_WEAK);
        HaulPlanner.advance(s, CTX);

        assertNotNull(carrier.haul(), "the errand carries on; somebody has to move the food");
        assertEquals(0, stall.foodStored(), "and it stays on their back rather than going back");
    }

    @Test
    void anEmptiedSourceCancelsTheErrand() {
        Settlement s = settlement();
        Building stall = new Building(MARKET.id(), new SimPos(0, 64, 0), 0, true);
        s.addBuilding(stall);   // nothing in stock
        Person carrier = person(s, new SimPos(0, 64, 0));
        carrier.setHaul(new HaulTask(HaulTask.Store.MARKET, stall.origin(),
                HaulTask.Store.GRANARY, new SimPos(0, 64, 0), 10));

        HaulPlanner.advance(s, CTX);

        assertNull(carrier.haul(), "somebody got there first, so the errand is dropped");
    }

    @Test
    void watchedCarriersAreNotTeleported() {
        Settlement s = settlement();
        Building stall = new Building(MARKET.id(), new SimPos(60, 64, 0), 0, true);
        stall.setFoodStored(30);
        s.addBuilding(stall);
        Person carrier = person(s, new SimPos(0, 64, 0));
        carrier.setEmbodied(true);   // a player can see them; the entity does the walking

        carrier.setHaul(new HaulTask(HaulTask.Store.MARKET, stall.origin(),
                HaulTask.Store.GRANARY, new SimPos(0, 64, 0), 10));
        HaulPlanner.advance(s, CTX);

        assertEquals(new SimPos(0, 64, 0), carrier.position(),
                "the simulation must not shove someone a player is watching");
        assertNotNull(carrier.haul(), "the errand simply waits for them to walk it");
    }
}
