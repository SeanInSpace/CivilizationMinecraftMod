package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers supply actually being limited: a town spends what it has, and when it
 * runs out it goes and builds whatever would make more — with somebody in it.
 */
class SupplyTest {

    private static final BuildingType LUMBER_CAMP =
            new BuildingType("kingdoms:lumber_camp", 30, 1, 1, 0, 68, 0);
    private static final BuildingType FARM =
            new BuildingType("kingdoms:farm", 45, 1, 0, 6, 70, 0);

    /** Nobody is watching, so what building there is runs on the clock. */
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

    private static Settlement bareTown(String name) {
        Settlement s = new Settlement(Settlement.Id.random(), name, new SimPos(0, 64, 0), 128);
        s.setCatalogue(List.of(LUMBER_CAMP, FARM));
        return s;
    }

    /** A founding party, as the charter lands one: two builders and two idlers. */
    private static Settlement town() {
        Settlement s = bareTown("Testburg");
        settle(s, "Ada", Profession.BUILDER);
        settle(s, "Bruno", Profession.BUILDER);
        settle(s, "Cass", Profession.IDLER);
        settle(s, "Dov", Profession.IDLER);
        return s;
    }

    private static Person settle(Settlement s, String name, Profession trade) {
        Person person = new Person(Person.Id.random(), name, trade, new SimPos(0, 64, 0));
        s.addResident(person);
        return person;
    }

    private static Person whoWorks(Settlement s, Profession trade) {
        return s.residents().stream()
                .filter(p -> p.profession() == trade)
                .findFirst()
                .orElse(null);
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
    void theCampIsOrderedWithSomebodyAlreadyTrainedToRunIt() {
        // The whole fault: a town bootstrapped a mine and staffed it with nobody,
        // because the staffing table does not want a miner until a mine stands.
        Settlement s = town();

        assertTrue(BuildPlanner.requestProducer(s, TownStores.WOOD, 5));

        assertNotNull(whoWorks(s, Profession.LUMBERJACK),
                "a camp ordered with nobody to work it is not a rescue");
        assertTrue(s.events().stream().anyMatch(e -> e.message().contains("takes up lumberjack")),
                "and the town names who took the job");
    }

    @Test
    void anIdlerGoesBeforeAnybodyWithAJob() {
        Settlement s = town();

        assertTrue(BuildPlanner.requestProducer(s, TownStores.WOOD, 5));

        assertEquals(2, s.residents().stream()
                        .filter(p -> p.profession() == Profession.BUILDER).count(),
                "an idler costs the town nothing, so no builder is taken off the walls");
    }

    @Test
    void withNoIdlersTheBiggestTradeGivesSomebodyUp() {
        // A town of ninety-seven farmers had no idlers at all, and idler-only
        // retraining left it permanently short. Slack lives in the big cohorts.
        Settlement s = bareTown("Full");
        settle(s, "Ada", Profession.BUILDER);
        settle(s, "Bruno", Profession.GUARD);
        settle(s, "Cass", Profession.FARMER);
        settle(s, "Dov", Profession.FARMER);
        settle(s, "Eri", Profession.FARMER);

        assertTrue(BuildPlanner.requestProducer(s, TownStores.WOOD, 5));

        assertNotNull(whoWorks(s, Profession.LUMBERJACK), "somebody had to be found");
        assertEquals(2, s.residents().stream()
                        .filter(p -> p.profession() == Profession.FARMER).count(),
                "and it came out of the three farmers, not off the lone guard");
    }

    @Test
    void aTownWithNobodyToSpareOrdersNothingAndSaysSo() {
        // Refusing loudly beats ordering uselessly: retraining the only builder
        // means the camp is never raised, and the town has diverted itself into
        // a hole in the ground for nothing.
        Settlement s = bareTown("Thin");
        settle(s, "Ada", Profession.BUILDER);
        settle(s, "Bruno", Profession.FARMER);

        assertFalse(BuildPlanner.requestProducer(s, TownStores.WOOD, 5),
                "the last builder and the last farmer are not there to be spent");
        assertTrue(s.buildQueue().isEmpty(), "so nothing is ordered");
        assertEquals(0, s.nextPlotIndex(),
                "and a refused order costs the town no ground either");
        assertTrue(s.events().stream().anyMatch(e -> e.message().contains("nobody to spare")),
                "the town says why, rather than starving quietly");
    }

    @Test
    void aRefusalIsSaidOnceHoweverOftenItIsAsked() {
        // Callers ask again every step the shortage lasts — several times a second
        // while somebody is watching. A town remembers twenty lines, so a refusal
        // that repeated would scrub out its founding, its raids and everything else.
        Settlement s = bareTown("Thin");
        settle(s, "Ada", Profession.BUILDER);
        settle(s, "Bruno", Profession.FARMER);

        for (long step = 1; step <= Settlement.MAX_EVENTS * 2; step++) {
            assertFalse(BuildPlanner.requestProducer(s, TownStores.WOOD, step));
        }

        assertEquals(1, s.events().size(),
                "a distress signal that erases the evidence is worse than silence");
    }

    @Test
    void theLastHandRunningAStandingCampIsNotTakenOffIt() {
        // Wood production would stop dead, and nothing would ever hire a
        // replacement: an order is refused outright once the building stands, so
        // an emptied camp stays empty and its resource is beyond rescue.
        Settlement s = bareTown("Woodsy");
        settle(s, "Ada", Profession.BUILDER);
        settle(s, "Bruno", Profession.FARMER);
        settle(s, "Cass", Profession.LUMBERJACK);
        s.addBuilding(new Building("kingdoms:lumber_camp", new SimPos(40, 64, 0), 1, true));

        assertFalse(BuildPlanner.requestProducer(s, TownStores.STONE, 5),
                "the mine waits rather than emptying the camp that is already working");
        assertNotNull(whoWorks(s, Profession.LUMBERJACK), "and the camp keeps its hand");

        // The rule is about the standing building, not about the trade: the same
        // lumberjack with no camp behind them is the sparest hand in town.
        Settlement campless = bareTown("Bare");
        settle(campless, "Ada", Profession.BUILDER);
        settle(campless, "Bruno", Profession.FARMER);
        settle(campless, "Cass", Profession.LUMBERJACK);

        assertTrue(BuildPlanner.requestProducer(campless, TownStores.STONE, 5));
        assertNotNull(whoWorks(campless, Profession.MINER),
                "nobody is being kept from the mine by a camp that does not exist");
    }

    @Test
    void aTownThatAlreadyHasTheTradeKeepsEverybodyWhereTheyAre() {
        Settlement s = town();
        settle(s, "Eri", Profession.FARMER);

        assertTrue(BuildPlanner.requestProducer(s, TownStores.FOOD, 5));

        assertEquals("kingdoms:farm", s.buildQueue().getFirst().blueprintId());
        assertEquals(2, s.residents().stream()
                        .filter(p -> p.profession() == Profession.IDLER).count(),
                "somebody already farms, so no idler is spent training a second one");
    }

    @Test
    void aStarvingTownOrdersTheFarmItCouldNotAffordToBuy() {
        // Food had no producer at all, so an empty larder was simply the end.
        Settlement s = town();

        assertTrue(BuildPlanner.requestProducer(s, TownStores.FOOD, 5));

        assertEquals("kingdoms:farm", s.buildQueue().getFirst().blueprintId());
        assertNotNull(whoWorks(s, Profession.FARMER), "with a farmer to work it");
    }

    @Test
    void theFieldThatFeedsThemIsRaisedThoughTheyCannotPayForIt() {
        // A town with nothing has nothing to sell and no way to earn, so a farm
        // it must pay for is a farm it can never have. Being in PRODUCER_OF is
        // what exempts it, and this is the exemption doing its job.
        Settlement s = town();
        s.setStock(TownStores.WOOD, 0);
        s.setStock(TownStores.STONE, 0);
        assertTrue(BuildPlanner.requestProducer(s, TownStores.FOOD, 5));

        s.step(new SimContext(new QuietBridge(), 6, SimSettings.SANDBOX));

        assertEquals("kingdoms:farm", s.buildQueue().getFirst().blueprintId());
        assertTrue(s.buildQueue().getFirst().progress() > 0,
                "empty stores must not be what stops a starving town feeding itself");
    }

    @Test
    void everyProducerNamesTheTradeThatRunsIt() {
        for (String producer : BuildPlanner.PRODUCER_OF.values()) {
            assertNotNull(BuildPlanner.TRADE_OF.get(producer),
                    producer + " would be ordered and then never staffed");
        }
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
