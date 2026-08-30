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
    void aCampLeftAloneClimbsTheWholeLadderToTown() {
        Settlement camp = foundingParty();

        // Four hundred and fifty rather than three hundred. Growth is no longer
        // capped and a birth now costs more the fuller the town is, so a
        // settlement takes considerably longer to reach the size that finishes
        // its wall — measured at step 373 for the closing, against 300 before.
        // The ladder itself is unchanged; only the clock it runs on is slower.
        for (int i = 0; i < 450; i++) {
            camp.step(CTX);
        }

        // The whole founding on the unwatched clock: camp staked, timber
        // bootstrapped, homestead fed over the streak, palisade closed and
        // walked, cottages raised, couples moved out of the bunks, births
        // unlocked, workshops opened -- and the hall at last, built by a town
        // worth governing instead of four settlers in the open.
        assertTrue(camp.population() >= 4,
                "the founding party survives its own founding and grows");
        assertEquals(SettlementStage.TOWN, camp.stage(),
                "the ladder runs all the way up");
        assertTrue(camp.countBuildings("kingdoms:town_hall") >= 1,
                "the hall is the capstone, and it stands");
        assertTrue(camp.perimeterClosed(),
                "the palisade closed along the way");
        assertTrue(camp.countBuildings("kingdoms:cottage") >= 2,
                "the village raised its family homes");
        assertEquals(1, JobPlanner.count(camp, Profession.GUARD) > 0 ? 1 : 0,
                "the sentry never left the wall");
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
        // Pinned to the program-only buildings: exactly one of each ever
        // stands (catalogue base 0), and all predate the staking, so the ring
        // must hold them forever. Catalogue-scaled kinds -- granaries, houses,
        // even storehouses -- multiply with population and may legitimately
        // spill outside once the interior fills; that spill is the alpha-wall's
        // cue to re-stake, and no assertion of ours.
        for (Building building : camp.buildings()) {
            String id = building.blueprintId();
            if (!id.contains("bunkhouse") && !id.contains("hearth")
                    && !id.contains("cache") && !id.contains("camp_post")) {
                continue;
            }
            assertTrue(building.origin().x() > west && building.origin().x() < east
                            && building.origin().z() > north && building.origin().z() < south,
                    id + " predates the staking and must stand inside the palisade");
        }
        // Between four and six. Gates are now cut where the roads cross the
        // ring rather than at the midpoint of each side, so a town with more
        // ways out gets more gates -- bounded, because a ring riddled with
        // openings is a fence. Every one of them is a post on the wall, which
        // the midpoint version was not.
        assertTrue(ring.gates().size() >= 4 && ring.gates().size() <= 6,
                "a wall wants a few gates: " + ring.gates().size());
        java.util.Set<SimPos> onRing = new java.util.HashSet<>(ring.ringPositions());
        for (SimPos gate : ring.gates()) {
            assertTrue(onRing.contains(gate), "gate " + gate + " is not on the wall");
        }
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
    void aDeadSentryIsReplacedBeforeTheFoundingCanStall() {
        Settlement s = foundingParty();
        s.setStage(SettlementStage.FORTIFIED);
        StagePlanner.crystallize(s, SettlementStage.FORTIFIED);
        Person sentry = s.residents().stream()
                .filter(p -> p.profession() == Profession.GUARD)
                .findFirst().orElseThrow();
        s.removePerson(sentry.id());

        s.step(CTX);

        // The playtest raid that forced this killed the only guard twelve
        // steps after the stage named them; graduation requires a sentry every
        // step, so the post must refill, not stay vacant forever.
        assertEquals(1, JobPlanner.count(s, Profession.GUARD),
                "a raid that kills the only sentry must not stall the founding forever");
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
