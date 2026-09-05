package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.HaulPlanner;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SmithPlanner;
import com.kingdoms.sim.settlement.SupplyPlanner;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Who the town sends when a load has to be walked somewhere.
 *
 * <p>The report these exist for: "a carpenter had an errand of moving a stack of
 * supplies". He did, and the rule that sent him was a list of two exemptions —
 * not a builder, not a farmer — which on a village with workshops in it means
 * the next person found is a craftsman. The town bought a delivery with a
 * workshop.
 *
 * <p>So the property under test is an order of preference rather than a
 * blacklist: idle hands, then hands whose trade has nothing for them today, then
 * nobody at all. Several of these are about the third case, because a haul that
 * waits is the answer that had to be made deliberate.
 */
class CourierChoiceTest {

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

    /** An empty store beside the work, a full one across the valley, no demand yet. */
    private static Settlement village() {
        Settlement s = new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 512);
        s.setStock(WOOD, 0);
        s.setStock(TownStores.STONE, 0);
        s.setStock(TownStores.FOOD, 0);
        s.setStock(TownStores.IRON, 0);
        store(s, 0);
        store(s, 300).stores().set(WOOD, SupplyPlanner.SHORTAGE + SupplyPlanner.LOAD);
        return s;
    }

    /** The same village with something being built, which is the only thing that creates demand. */
    private static Settlement townWithAShortage() {
        Settlement s = village();
        s.enqueueBuild(new BuildTask("kingdoms:house", new SimPos(10, 64, 0), 40));
        return s;
    }

    private static Building store(Settlement s, int x) {
        Building store = new Building("kingdoms:storehouse", new SimPos(x, 64, 0), 1, true);
        s.addBuilding(store);
        return store;
    }

    private static Building raise(Settlement s, String blueprintId, int x) {
        Building building = new Building(blueprintId, new SimPos(x, 64, 40), 1, true);
        s.addBuilding(building);
        return building;
    }

    private static Person hire(Settlement s, Profession trade) {
        Person person = new Person(Person.Id.random(),
                trade + " " + s.population(), trade, s.centre());
        s.addResident(person);
        return person;
    }

    // --- the report ---

    @Test
    void theIdlerCarriesAndTheCarpenterStaysAtHisBench() {
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:carpentry", 20);
        Person carpenter = hire(s, Profession.CARPENTER);
        Person idler = hire(s, Profession.IDLER);

        SupplyPlanner.advance(s, CTX);

        assertNotNull(idler.haul(), "idle hands go first, whoever else is standing there");
        assertNull(carpenter.haul(), "and the bench keeps its craftsman");
    }

    @Test
    void withNoIdlerTheLoadWaitsAndTheWorkshopKeepsRunning() {
        // The answer that had to be made deliberate. A waiting haul costs the
        // town a walk it will make later; an empty carpentry costs it every
        // component the build crew was going to get, and the build is the
        // reason the timber was wanted at all.
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:carpentry", 20);
        Person carpenter = hire(s, Profession.CARPENTER);

        SupplyPlanner.advance(s, CTX);

        assertNull(carpenter.haul(), "nobody qualifies, so nobody goes");
        assertNull(HaulPlanner.courierFor(s), "and the town knows it has nobody to send");
        assertEquals(1, s.buildQueue().size(),
                "the build he is cutting components for is still in front of him");
    }

    @Test
    void theWaitingLoadGoesTheMomentSomebodyIsFree() {
        // A haul that waits is not a haul that is lost. The shortage is asked
        // again every step, and the first spare pair of hands answers it.
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:carpentry", 20);
        hire(s, Profession.CARPENTER);

        SupplyPlanner.advance(s, CTX);
        Person idler = hire(s, Profession.IDLER);
        SupplyPlanner.advance(s, CTX);

        assertNotNull(idler.haul(), "the load was waiting, not canceled");
    }

    // --- never ---

    @Test
    void aBuilderIsNeverSent() {
        Settlement s = townWithAShortage();
        Person builder = hire(s, Profession.BUILDER);

        SupplyPlanner.advance(s, CTX);

        assertNull(builder.haul(),
                "the demand is a build; supplying it by stopping it is not supplying it");
    }

    @Test
    void aGuardIsNeverSent() {
        // Nothing takes a load off a guard's back when the bell rings, so a
        // guard carrying stone is a guard who is not there when the raid comes.
        Settlement s = townWithAShortage();
        Person guard = hire(s, Profession.GUARD);

        SupplyPlanner.advance(s, CTX);

        assertNull(guard.haul(), "the watch is the watch");
    }

    @Test
    void aFarmerWithAFieldStaysInIt() {
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:farm", 40);
        Person farmer = hire(s, Profession.FARMER);

        SupplyPlanner.advance(s, CTX);

        assertNull(farmer.haul(), "timber can wait; the town's dinner cannot");
    }

    // --- slack ---

    @Test
    void aLumberjackAtTheTimberCeilingCarries() {
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:lumber_camp", 60);
        s.setStock(WOOD, LumberPlanner.woodCapacity(s));
        Person jack = hire(s, Profession.LUMBERJACK);

        assertSame(jack, HaulPlanner.courierFor(s),
                "there is nothing left worth felling, so the axe can be a shoulder");
    }

    @Test
    void aLumberjackWithTreesLeftToFellIsLeftAlone() {
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:lumber_camp", 60);
        s.setStock(WOOD, 0);
        hire(s, Profession.LUMBERJACK);

        assertNull(HaulPlanner.courierFor(s), "a day's felling is work in front of him");
    }

    @Test
    void aMinerAtTheStoneCeilingCarries() {
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:mine", 80);
        s.setStock(TownStores.STONE, MinePlanner.stoneCapacity(s));
        Person miner = hire(s, Profession.MINER);

        assertSame(miner, HaulPlanner.courierFor(s));
    }

    @Test
    void aSmithWithNoOreCarriesAndOneWithOreDoesNot() {
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:smith", 100);
        Person smith = hire(s, Profession.SMITH);

        assertSame(smith, HaulPlanner.courierFor(s), "a cold forge is a spare pair of hands");

        s.setStock(TownStores.IRON, SmithPlanner.IRON_PER_ITEM);
        s.setStock(WOOD, SmithPlanner.FUEL_PER_ITEM);
        assertNull(HaulPlanner.courierFor(s), "iron in front of him is work in front of him");
    }

    @Test
    void aMillerAlwaysCarries() {
        // The mill's whole effect is a headcount that never asks what the miller
        // is holding, and there is no watched miller to pull off anything, so
        // the walk costs the town nothing whatsoever. Refusing to ask him would
        // leave a load waiting in exchange for a saving that does not exist.
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:mill", 120);
        Person miller = hire(s, Profession.MILLER);

        assertSame(miller, HaulPlanner.courierFor(s));
    }

    @Test
    void aTraderWithNowhereToPutTheStockCarriesInstead() {
        // "A market stands and the granary has twelve" is not the same question
        // as "is there an errand for this trader": a lone stall already at its
        // cap passes the first and fails the second, and a trader believed busy
        // on that step is a courier the town had and did not use.
        Settlement s = townWithAShortage();
        Building stall = raise(s, "kingdoms:market", 140);
        s.setFoodStock(FoodPlanner.TRADER_CARRY * 4);
        Person trader = hire(s, Profession.TRADER);

        stall.setFoodStored(FoodPlanner.MARKET_STOCK_CAP);
        assertSame(trader, HaulPlanner.courierFor(s), "a full stall is nothing to stock");

        stall.setFoodStored(0);
        assertNull(HaulPlanner.courierFor(s), "an empty one is his own errand, and it comes first");
    }

    @Test
    void aCarpenterInATownBuildingNothingIsSpare() {
        // The carpentry's whole contribution is to the build queue, so a town
        // with an empty queue genuinely has an idle carpenter — and SupplyPlanner
        // never asks in that case, which is why this asks the rule directly.
        Settlement s = village();
        raise(s, "kingdoms:carpentry", 20);
        Person carpenter = hire(s, Profession.CARPENTER);

        assertSame(carpenter, HaulPlanner.courierFor(s));
    }

    @Test
    void anIdlerBeatsSlackHandsToIt() {
        Settlement s = townWithAShortage();
        raise(s, "kingdoms:lumber_camp", 60);
        s.setStock(WOOD, LumberPlanner.woodCapacity(s));
        hire(s, Profession.LUMBERJACK);
        Person idler = hire(s, Profession.IDLER);

        assertSame(idler, HaulPlanner.courierFor(s),
                "slack is second best; doing nothing at all is first");
    }

    @Test
    void somebodyAlreadyCarryingIsNotAskedAgain() {
        Settlement s = townWithAShortage();
        Person idler = hire(s, Profession.IDLER);

        SupplyPlanner.advance(s, CTX);
        assertNotNull(idler.haul());

        assertNull(HaulPlanner.courierFor(s), "one load per pair of hands");
    }
}
