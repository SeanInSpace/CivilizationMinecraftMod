package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.StagePlanner;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the founding progression of FOUNDING.md: no realistic settlement starts
 * by building a government. A charter party is a camp of pioneers that earns
 * its way to a town through conditions — shelter, food, safety, permanence —
 * and the hall arrives last, not first.
 */
class StageProgressionTest {

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

    /** A charter party as the item now lands one: pioneers, staged as a camp. */
    private static Settlement foundingParty() {
        Settlement s = new Settlement(Settlement.Id.random(), "Newholt", new SimPos(0, 64, 0), 128);
        s.setCatalogue(BuildCatalogue.DEFAULT);
        s.setStage(SettlementStage.CAMP);
        s.setFoodStock(FoodPlanner.STARTING_PROVISIONS);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov"}) {
            s.addResident(new Person(Person.Id.random(), name, Profession.PIONEER,
                    new SimPos(0, 64, 0)));
        }
        return s;
    }

    private static void raise(Settlement s, String blueprintId) {
        s.addBuilding(new Building(blueprintId, new SimPos(10, 64, 10), 0, true));
    }

    @Test
    void aFreshCampStakesItsClaimBeforeGovernment() {
        Settlement camp = foundingParty();

        camp.step(CTX);

        // The full catalogue is on the table and the hall — priority 100, the
        // old first act — is not what four settlers reach for: the program is,
        // and the program starts by staking the claim.
        assertFalse(camp.buildQueue().isEmpty(),
                "a founding party should start building something on day one");
        assertEquals("kingdoms:camp_post", camp.buildQueue().getFirst().blueprintId(),
                "the first thing a founding party raises is its own camp post");
    }

    @Test
    void aCampThatCanBuildNothingLivesOnBerriesAndNeverGraduates() {
        Settlement camp = foundingParty();
        // An empty catalogue so nothing can ever rise — with the DEFAULT one a
        // camp bootstraps its own timber economy through requestProducer and
        // honestly farms its way out, which the next test pins. Here the party
        // has only its hands, and foraging is the whole of the food supply.
        camp.setCatalogue(java.util.List.of());
        camp.setFoodStock(10);

        for (int i = 0; i < 60; i++) {
            camp.step(CTX);
        }

        assertEquals(4, camp.population(),
                "foraging pioneers keep a buildingless camp alive");
        assertTrue(FoodPlanner.totalFood(camp)
                        <= 4 * FoodPlanner.FORAGE_CEILING_PER_MOUTH + 2,
                "foraging is hand-to-mouth: it stops at the ceiling instead of filling the larder");
        assertEquals(0, camp.fedStreak(),
                "no settlement graduates on berries — the fed streak needs a farm behind it");
        assertEquals(SettlementStage.HOMESTEAD, camp.stage(),
                "an empty program advances CAMP, and the hunger wall stops everything after");
    }

    @Test
    void aCampLeftAloneRaisesItsPalisadeAndBecomesAVillage() {
        Settlement camp = foundingParty();

        for (int i = 0; i < 300; i++) {
            camp.step(CTX);
        }

        // The whole founding, on the unwatched clock alone: the party stakes
        // the camp, requestProducer bootstraps timber when the first bill goes
        // unpaid, the homestead farms itself over the fed streak, the
        // fortification names its specialists and closes its ring, and the
        // village dissolves the pioneers into the ordinary staffing table. It
        // stops exactly where step 5's cottages pick up: no family homes, so
        // no VILLAGE graduation and no growth.
        assertEquals(4, camp.population(),
                "the founding party survives its own founding");
        assertEquals(SettlementStage.VILLAGE, camp.stage(),
                "a healthy camp climbs to VILLAGE and waits on family housing");
        assertTrue(camp.countBuildings("kingdoms:farm") >= 1,
                "the homestead fed itself from a real farm, not from berries");
        assertTrue(camp.perimeterClosed(),
                "the palisade closed on its own timber");
        assertTrue(camp.perimeter() != null && camp.perimeter().closed(),
                "and the ring geometry agrees with the flag");
        assertEquals(1, JobPlanner.count(camp, Profession.GUARD),
                "the fortification named its sentry");
        assertTrue(JobPlanner.count(camp, Profession.LUMBERJACK) >= 1,
                "and its woodcutter — the palisade drank real timber");
        assertEquals(0, JobPlanner.count(camp, Profession.PIONEER),
                "VILLAGE dissolved the generalists");
    }

    @Test
    void theRingEnclosesEveryBuildingWithRoomToWalk() {
        Settlement camp = foundingParty();

        for (int i = 0; i < 300; i++) {
            camp.step(CTX);
        }

        Perimeter ring = camp.perimeter();
        assertTrue(ring != null, "three hundred steps is plenty to stake the ring");
        int west = ring.vertices().stream().mapToInt(v -> v.x()).min().orElseThrow();
        int east = ring.vertices().stream().mapToInt(v -> v.x()).max().orElseThrow();
        int north = ring.vertices().stream().mapToInt(v -> v.z()).min().orElseThrow();
        int south = ring.vertices().stream().mapToInt(v -> v.z()).max().orElseThrow();
        for (Building building : camp.buildings()) {
            // Extraction stands where the resource is: mines and lumber camps
            // are worked outside the walls in any real settlement, and both can
            // be ordered after the ring is staked. The palisade's promise is
            // narrower and firmer -- everyone sleeps and everything is stored
            // behind it.
            if (building.blueprintId().contains("mine")
                    || building.blueprintId().contains("lumber_camp")) {
                continue;
            }
            assertTrue(building.origin().x() > west && building.origin().x() < east
                            && building.origin().z() > north && building.origin().z() < south,
                    building.blueprintId() + " must stand inside the palisade");
        }
        assertEquals(4, ring.gates().size(),
                "v1 cuts a gate into each side, midpoints standing in for paths");
    }

    @Test
    void theHallWaitsForTheTown() {
        assertFalse(StagePlanner.catalogueAllows(SettlementStage.CAMP, "kingdoms:town_hall"),
                "a camp has no business ordering civic architecture");
        assertFalse(StagePlanner.catalogueAllows(SettlementStage.VILLAGE, "kingdoms:town_hall"),
                "even a village has not earned the hall yet");
        assertTrue(StagePlanner.catalogueAllows(SettlementStage.TOWN, "kingdoms:town_hall"),
                "the hall is the town's capstone, so TOWN may order it");
        assertTrue(StagePlanner.catalogueAllows(SettlementStage.CAMP, "kingdoms:house"),
                "the gate is for the hall alone, not ordinary buildings");
    }

    @Test
    void pioneersLabourEveryTradeUntilTheVillage() {
        Settlement camp = foundingParty();
        Person pioneer = camp.residents().iterator().next();

        assertTrue(camp.laboursAs(pioneer, Profession.BUILDER),
                "below VILLAGE a pioneer builds");
        assertTrue(camp.laboursAs(pioneer, Profession.FARMER),
                "below VILLAGE a pioneer farms");

        camp.setStage(SettlementStage.VILLAGE);
        assertFalse(camp.laboursAs(pioneer, Profession.BUILDER),
                "from VILLAGE the specialists exist and a pioneer is just unassigned");
    }

    @Test
    void aHomesteadGraduatesOnlyOnceItFeedsItself() {
        Settlement s = foundingParty();
        s.setStage(SettlementStage.HOMESTEAD);
        raise(s, "kingdoms:bunkhouse");
        raise(s, "kingdoms:hearth");
        raise(s, "kingdoms:farm");
        raise(s, "kingdoms:granary");

        assertFalse(StagePlanner.readyToAdvance(s, CTX),
                "a homestead with an empty larder has not proven it can feed itself");

        s.setFedStreak(StagePlanner.FED_WINDOW_STEPS);
        assertTrue(StagePlanner.readyToAdvance(s, CTX),
                "a fed streak over the window is the homestead's graduation");
    }

    @Test
    void fortificationNeedsAPerimeterAndSomeoneToWalkIt() {
        Settlement s = foundingParty();
        s.setStage(SettlementStage.FORTIFIED);
        raise(s, "kingdoms:lumber_camp");
        raise(s, "kingdoms:storehouse");

        assertFalse(StagePlanner.readyToAdvance(s, CTX),
                "an open perimeter is not a fortification");

        s.setPerimeterClosed(true);
        assertFalse(StagePlanner.readyToAdvance(s, CTX),
                "a wall nobody watches is not a fortification either");

        s.residents().iterator().next().setProfession(Profession.GUARD);
        assertTrue(StagePlanner.readyToAdvance(s, CTX),
                "a closed perimeter with a sentry on it completes the stage");
    }

    @Test
    void reachingFortifiedNamesTheSentryAndTheWoodcutter() {
        Settlement s = foundingParty();

        StagePlanner.crystallize(s, SettlementStage.FORTIFIED);

        assertEquals(1, JobPlanner.count(s, Profession.GUARD),
                "the fortification converts one pioneer to the watch");
        assertEquals(1, JobPlanner.count(s, Profession.LUMBERJACK),
                "and one to the axe — the palisade drinks more timber than the kit holds");
        assertEquals(2, JobPlanner.count(s, Profession.PIONEER),
                "the rest of the party keeps labouring as pioneers");
    }

    @Test
    void reachingVillageDissolvesThePioneers() {
        Settlement s = foundingParty();
        StagePlanner.crystallize(s, SettlementStage.FORTIFIED);

        StagePlanner.crystallize(s, SettlementStage.VILLAGE);

        assertEquals(0, JobPlanner.count(s, Profession.PIONEER),
                "at VILLAGE the generalists are gone");
        assertEquals(2, JobPlanner.count(s, Profession.IDLER),
                "dissolved pioneers idle until the staffing table places them");
        assertEquals(1, JobPlanner.count(s, Profession.GUARD),
                "crystallized professions are kept, not reshuffled");
        assertEquals(1, JobPlanner.count(s, Profession.LUMBERJACK),
                "the woodcutter keeps the axe too");
    }

    @Test
    void anOldSaveLoadsAsATownAndKeepsItsBehaviour() {
        // Saves from before stages existed carry no stage field; they must come
        // back as TOWN so nothing about an established settlement changes.
        Settlement s = new Settlement(Settlement.Id.random(), "Oldholt",
                new SimPos(0, 64, 0), 128);

        assertEquals(SettlementStage.TOWN, s.stage(),
                "the default stage is TOWN so pre-stage saves behave as they always did");
        assertTrue(StagePlanner.catalogueRuns(s.stage()),
                "an established town still runs the full catalogue");
        assertEquals(SettlementStage.TOWN, SettlementStage.parse("", SettlementStage.TOWN),
                "an absent stage field parses to the fallback, not an exception");
    }
}
