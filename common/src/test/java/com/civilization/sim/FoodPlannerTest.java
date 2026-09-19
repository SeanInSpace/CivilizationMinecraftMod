package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Foods;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Field;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.HaulPlanner;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the food chain — field, farmer, granary, market hand, pantry, mouth —
 * and what happens to the people at the end of it when a link breaks.
 */
class FoodPlannerTest {

    private static final BuildingType FARM   = new BuildingType("test:farm",   5, 9999, 0, 0, 70, 0);
    private static final BuildingType MARKET = new BuildingType("test:market", 5, 9999, 0, 0, 65, 0);
    private static final BuildingType HOUSE  = new BuildingType("test:house",  5, 9999, 0, 0, 80, 4);

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
        s.setCatalog(List.of(FARM, MARKET, HOUSE));
        return s;
    }

    private static Person add(Settlement s, Profession profession) {
        Person p = new Person(Person.Id.random(),
                profession + " " + s.population(), profession, new SimPos(0, 64, 0));
        s.addResident(p);
        return p;
    }

    private static Building addBuilding(Settlement s, BuildingType type, int index) {
        Building b = new Building(type.id(), new SimPos(20 + index * 8, 64, 0), 0, true);
        s.addBuilding(b);
        return b;
    }

    /**
     * A field with wheat standing ready on it.
     *
     * <p>Fields ripen at Minecraft's pace now — about a fifth of a block a step —
     * so a test that wants to watch a harvest has to give the farm something to
     * harvest first, exactly as three hundred and seventy steps of growing would
     * have. See {@link Field}.
     */
    private static Building ripe(Building farm, int blocks) {
        farm.setRipeHundredths(blocks * 100);
        return farm;
    }

    /**
     * Runs the food chain and the haulers, without the rest of the settlement
     * step — job retraining would reassign the very people these tests rely on.
     */
    private static void chainSteps(Settlement s, int count) {
        for (int i = 0; i < count; i++) {
            FoodPlanner.advance(s, CTX);
            HaulPlanner.advance(s, CTX);
        }
    }

    private static Household house(Settlement s, Person... members) {
        Household h = new Household(Household.Id.random(), "Family");
        for (Person p : members) {
            h.addMember(p.id());
        }
        h.setHome(new SimPos(5, 64, 5));
        s.addHousehold(h);
        return h;
    }

    // --- the chain, link by link ---

    /** Watching: every farm is observed, so real hands are expected to work it. */
    private static final class WatchingBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** A watched field of standing wheat with one farmer to its name. */
    private static Settlement oneRipeFarm() {
        Settlement s = settlement();
        Building farm = new Building("civilization:farm", new SimPos(10, 64, 0), 1, true);
        s.addBuilding(farm);
        add(s, Profession.FARMER);
        ripe(farm, 20);
        return s;
    }

    private static Building farmOf(Settlement s) {
        return s.buildings().getFirst();
    }

    /**
     * One budget, spent by whichever fidelity is in a position to spend it.
     *
     * <p>This was {@code theClockStandsAsideWhereRealHandsAreFarming}, and it
     * asserted that a watched farm with a fresh real harvest against its name was
     * credited nothing at all by the clock. The intent was right — double-crediting
     * would make watched towns richer than unwatched ones — and the mechanism was
     * a disaster, because "stand aside" is not "settle up". A farmer who cut one
     * sheaf and a farmer who cut none looked identical to the ledger, and so did a
     * farmer who was asleep, unspawned, boxed in by terrain, or simply given no
     * game tick to move in. The playtest of 2026-09-19 measured the bill: the same
     * town over the same three hundred steps, granary 0 to 449 with the player six
     * hundred blocks away against 467 down to 144 and still falling with him
     * standing in the square, and the town dead at step 1213 with sixty people
     * left in it.
     *
     * <p>So the step's harvest is worked out once, the real sickles book what they
     * spend of it through {@code Building.creditByHand}, and the clock credits the
     * remainder — nothing at all where the crew keeps up, all of it where there is
     * no crew. What a player cannot do, by standing anywhere, is change what the
     * field is worth.
     */
    @Test
    void theClockCreditsExactlyWhatTheHandsDidNotCut() {
        // What one step of this field is worth, with no hand having booked any
        // of it. Taken from the run rather than written down, because the number
        // is the farmers' pace and this test is not about the farmers' pace.
        Settlement idle = oneRipeFarm();
        FoodPlanner.advance(idle,
                new SimContext(new WatchingBridge(), 5, SimSettings.SANDBOX));
        int allowance = Field.grainStored(farmOf(idle));
        assertTrue(allowance > 0,
                "a watched field of standing wheat is worth something, and that"
                        + " is the whole of the correction");

        // The crew keeping up: they book the step's whole allowance, so the
        // clock has nothing left to credit and every sheaf on that shelf was cut
        // by somebody the player could watch cutting it.
        Settlement kept = oneRipeFarm();
        farmOf(kept).creditByHand(allowance);
        FoodPlanner.advance(kept,
                new SimContext(new WatchingBridge(), 5, SimSettings.SANDBOX));
        assertEquals(0, Field.grainStored(farmOf(kept)),
                "where the hands did the work, the clock pays for none of it —"
                        + " a watched town must never come out richer");

        // And a crew that managed one swing of it leaves the rest of the budget
        // for the clock, which is the case neither of the two old rules could
        // express.
        Settlement partly = oneRipeFarm();
        farmOf(partly).creditByHand(1);
        FoodPlanner.advance(partly,
                new SimContext(new WatchingBridge(), 5, SimSettings.SANDBOX));
        assertEquals(allowance - 1, Field.grainStored(farmOf(partly)),
                "one sheaf off the budget, and the clock cuts the rest");
    }

    /**
     * A field nobody can reach is still worth what it is worth.
     *
     * <p>This was {@code aWatchedFieldNobodyCanReachJustStandsThereRipe}, and the
     * comment under it argued the case with some conviction: a field of ripe wheat
     * with nobody cutting it is the truth, and it is a truth a player can walk over
     * and see. It is also a town's death certificate. "The farmers cannot get to
     * the field" is the ordinary state of a watched town — out of reach, unspawned,
     * walking, asleep, or given no game tick at all, which is every step of a
     * {@code /civ step} burst — and under the old rule every one of those steps was
     * a step the town ate through and did not earn. Run A of the 2026-09-19
     * playtest starved from 467 loaves to 144 and falling while somebody stood in
     * its square, and died at step 1213 with a population of sixty.
     *
     * <p>What is guaranteed instead: the field pays the same whether or not
     * anybody is looking at it. The wheat that the clock cuts is gone off the
     * ripeness ledger exactly as a sickle would have taken it, so the player who
     * walks out to look at the field sees a field that has been worked.
     */
    @Test
    void aWatchedFieldNobodyCanReachIsStillCutByTheClock() {
        Settlement watched = oneRipeFarm();
        Settlement alone = oneRipeFarm();

        // Watched, and no real harvest for a long while: nobody is cutting here,
        // and nobody has been for ninety-nine steps.
        farmOf(watched).touchRealHarvest(1);   // stale
        FoodPlanner.advance(watched,
                new SimContext(new WatchingBridge(), 100, SimSettings.SANDBOX));
        FoodPlanner.advance(alone, new SimContext(new QuietBridge(), 100, SimSettings.SANDBOX));

        assertTrue(Field.grainStored(farmOf(watched)) > 0,
                "no floor and no standing aside: the field is worked, because"
                        + " being looked at is not a reason for wheat to stop"
                        + " being wheat");
        assertEquals(Field.grainStored(farmOf(alone)), Field.grainStored(farmOf(watched)),
                "and worked to exactly the sheaf an unwatched one would be");
        assertEquals(Field.ripeBlocks(farmOf(alone)), Field.ripeBlocks(farmOf(watched)),
                "with the same wheat left standing in it afterwards — the books"
                        + " balance on both sides, not just the granary's");
    }

    @Test
    void harvestWaitsAtTheFarmUntilSomebodyCarriesIt() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Building farm = ripe(addBuilding(s, FARM, 0), 20);
        // Somewhere to take it. Cut wheat is grain, and grain is not food until
        // an oven has had it -- so a town with no hearth has nothing to send
        // anybody to and nothing would ever reach the larder.
        s.addBuilding(new Building("civilization:hearth", new SimPos(0, 64, 8), 0, true));
        Person farmer = add(s, Profession.FARMER);

        // The field fills, and the farmer is sent to fetch — but nothing has
        // reached the larder while they are still walking out to the field.
        chainSteps(s, 1);
        assertTrue(Field.grainStored(farm) > 0, "harvest sits in the field until collected");
        assertEquals(0, s.foodStock(), "and cannot arrive before the farmer does");
        assertNotNull(farmer.haul(), "the farmer has been sent for it");

        chainSteps(s, 8);
        assertTrue(s.foodStock() > 0,
                "the harvest reached the oven on somebody's back, and was baked");
    }

    @Test
    void fieldsOnlyEmploySoManyHands() {
        Settlement s = settlement();
        s.setFoodStock(0);
        ripe(addBuilding(s, FARM, 0), 40);
        for (int i = 0; i < 6; i++) {
            add(s, Profession.FARMER);
        }

        FoodPlanner.advance(s, CTX);

        assertEquals(FoodPlanner.FARMERS_PER_FARM * Field.BLOCKS_PER_FARMER_PER_STEP,
                FoodPlanner.farmGrain(s),
                "one field feeds work to two farmers; the rest wait for more fields");
    }

    @Test
    void marketHandsStockTheMarketFromTheGranary() {
        Settlement s = settlement();
        s.setFoodStock(50);
        Building market = addBuilding(s, MARKET, 0);
        add(s, Profession.TRADER);

        chainSteps(s, 6);

        assertEquals(FoodPlanner.TRADER_CARRY, market.foodStored(), "one load walked to the stall");
        assertTrue(s.foodStock() < 50, "and it left the granary");
    }

    @Test
    void familiesFetchFromTheMarketToThePantry() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Building market = addBuilding(s, MARKET, 0);
        market.setFoodStored(40);
        Household family = house(s, add(s, Profession.IDLER), add(s, Profession.IDLER));

        chainSteps(s, 8);

        assertEquals(2 * FoodPlanner.PANTRY_PER_MEMBER, family.pantry(),
                "a member shopped the pantry up to its target");
        assertEquals(40 - family.pantry(), market.foodStored(), "and the stall is down by that much");
    }

    @Test
    void youngTownsFetchStraightFromTheGranary() {
        Settlement s = settlement();
        s.setFoodStock(30);
        Household family = house(s, add(s, Profession.IDLER));

        chainSteps(s, 6);

        assertTrue(family.pantry() > 0, "no market yet, so the granary serves families directly");
    }

    @Test
    void aCarriedLoadIsNeverInTwoPlacesAtOnce() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Building market = addBuilding(s, MARKET, 0);
        market.setFoodStored(20);
        Household family = house(s, add(s, Profession.IDLER));
        Person shopper = s.residents().iterator().next();

        // Step until the load is on their back but not yet delivered.
        for (int i = 0; i < 8 && (shopper.haul() == null || !shopper.haul().isLoaded()); i++) {
            chainSteps(s, 1);
        }

        assertNotNull(shopper.haul(), "still running the errand");
        assertTrue(shopper.haul().isLoaded(), "carrying the load");
        assertEquals(20 - shopper.haul().carried(), market.foodStored(),
                "goods left the stall the moment they were picked up");
        assertEquals(0, family.pantry(), "and have not arrived home yet");
    }

    @Test
    void theHungryEatWhatTheyCarry() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Person person = add(s, Profession.BUILDER);
        Household family = house(s, person);
        family.setPantry(10);
        person.setHunger(Person.HUNGER_HUNGRY + 2);

        FoodPlanner.advance(s, CTX);

        assertTrue(person.hunger() < Person.HUNGER_HUNGRY, "eating pushed hunger back down");
        assertEquals(FoodPlanner.CARRY_WHEN_EATING - 1,
                person.inventory().count(Foods.PROVISION),
                "took a couple of loaves from the larder, ate one on the spot");
    }

    // --- when the chain breaks ---

    @Test
    void hungerClimbsWithNothingToEat() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Person person = add(s, Profession.BUILDER);

        for (int i = 0; i < 5; i++) {
            FoodPlanner.advance(s, CTX);
        }

        assertEquals(5 * FoodPlanner.HUNGER_PER_STEP, person.hunger());
    }

    @Test
    void theWeakStopWorkingWhileTheTownCanStillFeedThem() {
        // A full granary, so this is one hungry person and not a starving town.
        // Under those conditions the weakness gate stands: somebody who has not
        // eaten is in no state to work a field.
        Settlement s = settlement();
        s.setFoodStock(100);
        ripe(addBuilding(s, FARM, 0), 20);
        Person farmer = add(s, Profession.FARMER);
        farmer.setHunger(Person.HUNGER_WEAK);

        assertFalse(s.isStarving(), "a town with a hundred loaves is not starving");

        FoodPlanner.advance(s, CTX);

        assertEquals(0, FoodPlanner.farmGrain(s),
                "a farmer too weak to work brings in nothing while there is food to be had");
    }

    @Test
    void theWeakKeepFarmingOnceTheTownIsStarving() {
        // The same weak farmer, in a town with nothing left. The gate above is
        // exactly how a settlement dies — weak hands bring in no food, so more
        // hands go weak — so starvation lifts it and the hungry go and grow
        // their own dinner.
        Settlement s = settlement();
        s.setFoodStock(0);
        ripe(addBuilding(s, FARM, 0), 20);
        Person farmer = add(s, Profession.FARMER);
        farmer.setHunger(Person.HUNGER_WEAK);

        assertTrue(s.isStarving(), "no food anywhere in town is what starving means");

        FoodPlanner.advance(s, CTX);

        assertTrue(FoodPlanner.farmGrain(s) > 0,
                "the hungry must be allowed to feed themselves, or hunger feeds on itself");
    }

    @Test
    void aTownIsOnlyStarvingWhenTheFoodIsGoneFromEverywhere() {
        // The predicate has to be rare enough to mean something. An empty
        // granary is an ordinary afternoon if the stalls and the larders are
        // full — the town is starving only when there is nothing left anywhere.
        Settlement s = settlement();
        s.setFoodStock(0);
        Building market = addBuilding(s, MARKET, 0);
        add(s, Profession.IDLER);
        add(s, Profession.IDLER);

        market.setFoodStored(40);
        assertFalse(s.isStarving(), "a stocked market is food the town still has");

        market.setFoodStored(0);
        assertTrue(s.isStarving(), "and when the stall empties too, it does not");
    }

    /**
     * The whole ladder, walked once, with nothing anywhere to eat.
     *
     * <p>Every rung costs the same as every other one now, so the run from fed to
     * dead is just {@code HUNGER_MAX / HUNGER_PER_STEP} steps of arithmetic and
     * this test is that arithmetic written out. It is deliberately expressed in
     * the constants: change either and the expectation moves with it, and what is
     * actually asserted is that there is no hidden grace period on either end.
     */
    @Test
    void aSettlerWithNothingToEatWalksTheWholeLadderAndDiesAtTheCap() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Person person = add(s, Profession.BUILDER);

        int stepsToDeath = Person.HUNGER_MAX / FoodPlanner.HUNGER_PER_STEP;
        assertEquals(99, stepsToDeath, "ninety-nine steps, about eight minutes of play");

        for (int step = 1; step < stepsToDeath; step++) {
            FoodPlanner.advance(s, CTX);
            assertEquals(1, s.population(),
                    "alive at hunger " + person.hunger() + " after " + step + " steps");
            assertEquals(step * FoodPlanner.HUNGER_PER_STEP, person.hunger(),
                    "hunger climbs one a step and nothing else");
        }

        assertEquals(Person.HUNGER_MAX - FoodPlanner.HUNGER_PER_STEP, person.hunger(),
                "one step short of the cap, and still standing");

        FoodPlanner.advance(s, CTX);

        assertEquals(0, s.population(), "the step hunger reaches the cap is the last one");
        assertTrue(s.events().stream().anyMatch(e -> e.message().contains("starved")),
                "and the town remembers");
    }

    /** Ten steps of severe band, which is exactly what the old grace was worth. */
    @Test
    void theSevereBandIsTheDeathClock() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Person person = add(s, Profession.BUILDER);
        person.setHunger(Person.HUNGER_SEVERE - FoodPlanner.HUNGER_PER_STEP);

        int survived = 0;
        while (s.population() > 0) {
            FoodPlanner.advance(s, CTX);
            survived++;
        }

        assertEquals(10, survived,
                "ten steps from the severe line to the grave, the old grace period over again");
    }

    @Test
    void aMealTheStepBeforeTheCapSavesThem() {
        Settlement s = settlement();
        s.setFoodStock(0);
        Person person = add(s, Profession.BUILDER);
        Household family = house(s, person);
        person.setHunger(Person.HUNGER_MAX - FoodPlanner.HUNGER_PER_STEP);
        family.setPantry(5);

        FoodPlanner.advance(s, CTX);

        assertEquals(1, s.population(), "food arrived in time");
        assertTrue(person.hunger() < Person.HUNGER_SEVERE,
                "and a loaf put them back below the severe line; hunger " + person.hunger());
    }

    // --- dinner before any penalty ---

    @Test
    void theMerelyHungryWalkToFoodWhenThereIsNothingAtHome() {
        // Thirty is where the errand starts now, not sixty. Nothing in their
        // pockets, no pantry to refill from, and a granary across town.
        Settlement s = settlement();
        s.setFoodStock(40);
        Person person = add(s, Profession.BUILDER);
        person.setHunger(Person.HUNGER_HUNGRY);

        FoodPlanner.advance(s, CTX);

        assertNotNull(person.haul(), "a hungry settler with no dinner at home goes for one");
        assertTrue(FoodPlanner.isGoingToEat(person), "and the errand is a meal");
        assertFalse(person.isTooWeakToWork(),
                "while still counting as a worker: going for dinner is not downing tools");
    }

    @Test
    void aStockedPantryMeansNobodyHasToWalk() {
        // The same hunger, the same granary, but a housed family with loaves on
        // the shelf. eatAndHunger reaches the pantry by arithmetic, so an errand
        // would be a walk to nowhere.
        Settlement s = settlement();
        s.setFoodStock(40);
        Person person = add(s, Profession.BUILDER);
        Household family = house(s, person);
        family.setPantry(6);
        person.setHunger(Person.HUNGER_HUNGRY);

        FoodPlanner.advance(s, CTX);

        assertFalse(FoodPlanner.isGoingToEat(person),
                "dinner was already at home; nobody is sent out for it");
        assertTrue(person.hunger() < Person.HUNGER_HUNGRY, "and they ate");
    }

    @Test
    void oneStarvingSettlerIsACrisisWhateverTheGranaryHolds() {
        // The clause that replaced "somebody is on the death clock". A town with
        // bread on the shelf and a settler nine steps from dead in front of it
        // has an emergency, and the flag is what lifts the rules in the way.
        Settlement s = settlement();
        s.setFoodStock(500);
        Person person = add(s, Profession.BUILDER);
        add(s, Profession.IDLER);

        assertFalse(s.isStarving(), "a full granary and nobody starving is an ordinary day");

        person.setHunger(Person.HUNGER_SEVERE);

        assertTrue(s.isStarving(),
                "somebody at the severe line is a starving town however full the granary");
    }

    // --- unchanged foundations ---

    @Test
    void birthsStillRequireBankedFood() {
        Settlement s = settlement();
        s.setFoodStock(0);
        assertTrue(!FoodPlanner.canFeedAnotherMouth(s) || s.population() > 0);
        s.setFoodStock(1000);
        assertTrue(FoodPlanner.canFeedAnotherMouth(s));
    }

    @Test
    void granaryBuildingsExtendCapacity() {
        Settlement s = settlement();
        s.addBuilding(new Building("test:granary", new SimPos(30, 64, 0), 0, true));

        assertEquals(FoodPlanner.BASE_GRANARY + FoodPlanner.GRANARY_PER_BUILDING,
                FoodPlanner.granaryCapacity(s));
    }
}
