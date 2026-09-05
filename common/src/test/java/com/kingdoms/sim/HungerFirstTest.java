package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Alarm;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A starving citizen eats before doing anything else.
 *
 * <p>Reported from a world: a settler reading "weak" at hunger 88 whose
 * priorities were plainly not dinner, and a builder standing perfectly still on
 * top of the house they had just finished. The two turned out to be one fault.
 *
 * <p>The old rule had a hole exactly the width of the weak band. Below 30
 * nobody eats; from 30 a person eats what they carry, or what the family larder
 * hands them; from 90 the town reaches into the granary and puts food in their
 * hands wherever they happen to be standing. But at 60 they stop farming,
 * hauling and building — and the errand that fills the family larder is only
 * ever given to somebody who is <em>not</em> too weak to run it. So between 60
 * and 89, a settler with empty pockets and an empty larder was barred from the
 * work and barred from the shopping at the same time, and simply stood there
 * getting hungrier. Guards and builders were never shoppers at any hunger at
 * all.
 *
 * <p>The rule now: past {@link Person#HUNGER_WEAK} you put the job down and
 * walk to the nearest food yourself, and the job is yours again when you have
 * eaten. With one exception, which is the other half of the same sentence —
 * nowhere to walk to and you stay on the job, because a starving idler is worse
 * off than a starving worker.
 */
class HungerFirstTest {

    /** Nobody is watching, so the clock walks every errand. */
    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(
                String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    /** Where the town's bulk store sits, a good walk from the square. */
    private static final SimPos GRANARY = new SimPos(100, 64, 0);

    /**
     * A town with nothing queued and nothing to build, so every step is about
     * dinner and nothing else.
     */
    private static Settlement town() {
        Settlement s = new Settlement(
                Settlement.Id.random(), "Mealtide", new SimPos(0, 64, 0), 256);
        s.setCatalog(List.of());
        s.setFoodStock(0);
        return s;
    }

    private static Person settle(Settlement s, String name, Profession trade) {
        Person person = new Person(Person.Id.random(), name, trade, s.center());
        s.addResident(person);
        return person;
    }

    /** A granary building and a pool in it, far enough away to be a real walk. */
    private static void stockTheGranary(Settlement s, int loaves) {
        s.addBuilding(new Building("kingdoms:granary", GRANARY, 0, true));
        s.setFoodStock(loaves);
    }

    @Test
    void theWeakPutTheJobDownAndWalkToTheNearestFood() {
        Settlement town = town();
        stockTheGranary(town, 60);
        Person alder = settle(town, "Alder", Profession.BUILDER);
        alder.setHunger(Person.HUNGER_WEAK);

        town.step(CTX);

        assertTrue(FoodPlanner.isGoingToEat(alder),
                "a builder reading weak has somewhere to be, and it is not the roof");
        assertEquals(GRANARY, alder.haul().target(),
                "and where they are going is the food");
        assertEquals(60, town.foodStock(),
                "nothing is eaten at a distance; they have to get there first");
    }

    @Test
    void theNearestFoodWinsRatherThanTheFullest() {
        Settlement town = town();
        stockTheGranary(town, 400);
        // Twenty blocks out: near enough to be the obvious answer, far enough
        // that one step of walking does not finish the errand before it can be
        // looked at.
        Building stall = new Building("kingdoms:market", new SimPos(20, 64, 0), 0, true);
        stall.setFoodStored(3);
        town.addBuilding(stall);
        Person alder = settle(town, "Alder", Profession.BUILDER);
        alder.setHunger(Person.HUNGER_WEAK);

        town.step(CTX);

        assertEquals(stall.origin(), alder.haul().target(),
                "three loaves twenty blocks away beat four hundred a hundred away");
    }

    @Test
    void theJobIsTheirsAgainOnceTheyHaveEaten() {
        Settlement town = town();
        stockTheGranary(town, 60);
        Person alder = settle(town, "Alder", Profession.BUILDER);
        alder.setHunger(Person.HUNGER_WEAK);

        int fedOn = 0;
        for (int step = 1; step <= 40 && fedOn == 0; step++) {
            town.step(CTX);
            if (alder.hunger() < Person.HUNGER_WEAK) {
                fedOn = step;
            }
        }

        assertTrue(fedOn > 0, "they got there and ate; hunger ended at " + alder.hunger());
        assertFalse(FoodPlanner.isGoingToEat(alder), "the errand is over");
        assertEquals(Profession.BUILDER, alder.profession(),
                "and they are a builder again, having never stopped being one");
        assertFalse(FoodPlanner.heldBackByHunger(town, alder, town.isStarving()),
                "so nothing is holding them off the site any more");
        assertEquals(60 - FoodPlanner.CARRY_WHEN_EATING, town.foodStock(),
                "one armful came off the shelf, and only one");
    }

    @Test
    void nothingWithinReachMeansTheyKeepWorking() {
        // The town owns eight loaves, so it is not in a famine and the famine
        // escape does not apply -- but every one of them is on somebody else's
        // back, which is food this person cannot walk to. Standing still would
        // buy them nothing at all.
        Settlement town = town();
        Person hand = settle(town, "Ada", Profession.FARMER);
        settle(town, "Bruno", Profession.BUILDER);
        settle(town, "Cass", Profession.BUILDER).inventory().add(Foods.PROVISION, 8);
        hand.setHunger(Person.HUNGER_WEAK);

        assertFalse(town.isStarving(), "a town holding eight loaves is not in a famine");
        assertFalse(FoodPlanner.heldBackByHunger(town, hand, town.isStarving()),
                "with nowhere to walk to, a weak settler is still a pair of hands");

        town.step(CTX);

        assertFalse(FoodPlanner.isGoingToEat(hand),
                "and no errand is invented to somewhere that holds nothing");
    }

    @Test
    void theSamePersonDownsToolsTheMomentThereIsSomewhereToGo() {
        // The other half of the rule above, on one fixture so the two can never
        // drift apart: what decides it is whether a meal exists, nothing else.
        Settlement town = town();
        Person hand = settle(town, "Ada", Profession.FARMER);
        settle(town, "Bruno", Profession.BUILDER);
        settle(town, "Cass", Profession.BUILDER).inventory().add(Foods.PROVISION, 8);
        hand.setHunger(Person.HUNGER_WEAK);

        stockTheGranary(town, 40);

        assertTrue(FoodPlanner.heldBackByHunger(town, hand, town.isStarving()),
                "a granary they can reach is a reason to stop working");
    }

    @Test
    void aHaulerWhoGoesWeakSetsTheLoadDownRatherThanLosingIt() {
        // A load in transit exists on its carrier's back and in no set of books
        // anywhere. Handing somebody a meal over the top of the errand they were
        // already running would therefore not interrupt it — it would delete
        // twelve of grain, or a courier's sixty-four of timber. The errand they
        // have is left alone for the one step it takes to put it down properly.
        Settlement town = town();
        stockTheGranary(town, 0);
        Building field = new Building("kingdoms:farm", new SimPos(-40, 64, 0), 0, true);
        field.setFoodStored(30);
        town.addBuilding(field);
        Person ada = settle(town, "Ada", Profession.FARMER);
        settle(town, "Bruno", Profession.BUILDER);
        ada.setPosition(field.origin());

        town.step(CTX);

        assertNotNull(ada.haul(), "the fixture is a farmer out on an errand");
        assertTrue(ada.haul().isLoaded(), "with grain actually on their back");
        int carried = ada.haul().carried();
        int onTheShelf = field.foodStored();

        ada.setHunger(Person.HUNGER_WEAK);
        town.step(CTX);

        assertEquals(onTheShelf + carried, field.foodStored(),
                "every grain of it went back where it came from");
        assertNull(ada.haul(), "and the errand is over");

        town.step(CTX);

        // Standing in the rows they were hauling out of, so the errand is given
        // and run inside the one step — which is the point: nothing was lost and
        // nothing was delayed beyond the step it took to set the sacks down.
        assertTrue(ada.inventory().foodCount() > 0,
                "and the meal comes the step after, once their hands are empty");
    }

    @Test
    void theWatchHoldsTheWallWhileTheTownIsAlarmed() {
        Settlement town = town();
        stockTheGranary(town, 60);
        Person sentry = settle(town, "Gest", Profession.GUARD);
        // So the staffing table is satisfied and nobody is retrained mid-test.
        settle(town, "Alder", Profession.BUILDER);
        sentry.setHunger(Person.HUNGER_WEAK);
        town.setThreatLevel(Alarm.ALARMED_AT);
        assertEquals(Alarm.ALARMED, town.alarm(), "the fixture is a town under threat");

        town.step(CTX);

        assertFalse(FoodPlanner.isGoingToEat(sentry),
                "a hungry watch beats no watch; dinner waits for the raid");

        town.setThreatLevel(0);
        town.step(CTX);

        assertTrue(FoodPlanner.isGoingToEat(sentry),
                "and the moment it is quiet they go and eat");
    }

    @Test
    void fullPocketsAreNoLongerAReasonToStarve() {
        // The failure FullPocketsTest names, met on the walk instead of at the
        // granary: six slots of picked-up weeds and nowhere to put a loaf. A
        // person standing at the shelf does not need a free pocket to eat.
        Settlement town = town();
        stockTheGranary(town, 60);
        Person alder = settle(town, "Alder", Profession.BUILDER);
        for (int slot = 0; slot < com.kingdoms.sim.person.Inventory.SLOTS; slot++) {
            alder.inventory().add("minecraft:stone_pickaxe_" + slot, 1);
        }
        alder.setHunger(Person.HUNGER_WEAK);
        alder.setPosition(GRANARY);

        town.step(CTX);

        assertNull(alder.haul(),
                "standing on the errand's own doorstep, it is run and done in one step");
        assertTrue(alder.hunger() < Person.HUNGER_WEAK,
                "and the loaf went down on the spot rather than into a pocket; hunger "
                        + alder.hunger());
        assertEquals(59, town.foodStock(),
                "one loaf eaten, the other put back: an armful nobody can carry is "
                        + "not an armful that vanishes");
    }

    @Test
    void aMealMovesFoodAndNeverMakesAny() {
        Settlement town = town();
        stockTheGranary(town, 60);
        Household family = new Household(Household.Id.random(), "Alder");
        Person alder = settle(town, "Alder", Profession.BUILDER);
        family.addMember(alder.id());
        family.setHome(new SimPos(-20, 64, 0));
        family.setPantry(0);
        town.addHousehold(family);
        alder.setHunger(Person.HUNGER_WEAK);

        int before = FoodPlanner.totalFood(town);
        int steps = 12;
        for (int step = 1; step <= steps; step++) {
            town.step(CTX);
        }

        // A loaf is in exactly one of three places: a store, a pocket, or gone,
        // and a gone one has undone thirty hunger. Twelve steps from sixty stays
        // clear of both ends of the scale, so the arithmetic is exact rather
        // than approximate — which is the whole point of asking it this way.
        int hungerHadNobodyEaten = Person.HUNGER_WEAK + FoodPlanner.HUNGER_PER_STEP * steps;
        int eaten = (hungerHadNobodyEaten - alder.hunger()) / Foods.nutrition(Foods.PROVISION);
        assertTrue(eaten > 0, "the errand fed them at least once");
        assertEquals(before, FoodPlanner.totalFood(town) + eaten,
                "every loaf is either still somewhere in town or was eaten; a walk "
                        + "to the granary makes none and loses none");
    }
}
