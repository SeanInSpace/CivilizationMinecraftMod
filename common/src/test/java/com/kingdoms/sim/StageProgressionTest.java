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

import java.util.List;

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

    /**
     * The same quiet world, told which step it is.
     *
     * <p>{@link #CTX} says step zero for ever, which is harmless for the tests
     * that take one or two steps and quietly wrong for the ones that run a
     * founding. Several planners do work on a cadence — {@code n % 20} for the
     * gates, among others — and a clock stuck at zero satisfies every one of
     * them on every step, so a test that never advances it measures a
     * settlement doing its periodic work far more often than any world would.
     * The wall's own clock now cares about more than a cadence: the cooldown
     * that keeps a town from moving its line twice in a generation is measured
     * against this step, and at step zero for ever no wall is ever old enough
     * to move at all.
     */
    private static SimContext at(int step) {
        return new SimContext(new QuietBridge(), step, SimSettings.SANDBOX);
    }

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

        // The full catalog is on the table and the hall — priority 100, the
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
        // An empty catalog so nothing can ever rise — with the DEFAULT one a
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

        // Five hundred and sixty rather than four hundred and fifty, for the
        // same reason it was four hundred and fifty rather than three hundred:
        // the clock got slower, not the ladder. Buildings are drawn at the size
        // the catalog reserves for them now — a hall is thirteen by eleven
        // where it was seven by seven — so every one of them is more work and a
        // town reaches the size that finishes its wall later. Measured at step
        // 463 for the closing on this ground, against 373 before.
        //
        // 453 now, and holding a wall back to TOWN is not why. This fixture
        // already staked after its charter: it reaches TOWN at step 255, raises
        // the hall the stage asks for, and stakes 386 posts at 284 — exactly
        // where it staked when FORTIFIED was the gate, because the ring waits
        // on the stage's own program either way. Closed at 453 with a hundred
        // and seven steps of headroom in the budget.
        //
        // What this run no longer does is move the wall. The line staked at 284
        // is the line the town still has at 560, and at 700, and the suburbs
        // beyond it stay outside — a second circuit needs the cooldown to run
        // out first, and the whole ladder is shorter than the cooldown.
        for (int i = 1; i <= 560; i++) {
            camp.step(at(i));
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

    /**
     * Every building standing when the wall was staked is inside it, with room
     * to walk.
     *
     * <p>The claim used to be that the ring encloses every building, pinned to
     * the four the program raises exactly one of so that growth could not
     * falsify it. It is stated properly now, because buildings standing outside
     * the wall are no longer a leak to be worked around: they are <em>the
     * design</em>. A town walls itself at its charter and everything it builds
     * afterwards is a suburb, unwalled, on ground the wall never claimed —
     * which is what a faubourg is and where every medieval town put its growth.
     * Measured on this ground: the wall is staked at step 284 around the sixteen
     * buildings standing then, and the seventeenth, raised in the sixteen steps
     * this run has left, goes up outside it.
     *
     * <p>So the moment matters and the buildings are taken at it. What the
     * staking promises is containment of what stood <em>then</em>; what it
     * stands beside afterwards is somebody else's business.
     */
    @Test
    void everyBuildingStandingWhenTheWallWasStakedIsInsideItWithRoomToWalk() {
        Settlement camp = foundingParty();

        List<Building> whenStaked = List.of();
        for (int i = 1; i <= 300; i++) {
            List<Building> stood = List.copyOf(camp.buildings());
            camp.step(at(i));
            if (whenStaked.isEmpty() && camp.perimeter() != null) {
                whenStaked = stood;
            }
        }

        Perimeter ring = camp.perimeter();
        assertTrue(ring != null, "three hundred steps is plenty to stake the ring");
        assertFalse(whenStaked.isEmpty(),
                "the wall was staked around nothing at all");
        int west = ring.vertices().stream().mapToInt(v -> v.x()).min().orElseThrow();
        int east = ring.vertices().stream().mapToInt(v -> v.x()).max().orElseThrow();
        int north = ring.vertices().stream().mapToInt(v -> v.z()).min().orElseThrow();
        int south = ring.vertices().stream().mapToInt(v -> v.z()).max().orElseThrow();
        for (Building building : whenStaked) {
            assertTrue(building.origin().x() > west && building.origin().x() < east
                            && building.origin().z() > north && building.origin().z() < south,
                    building.blueprintId() + " at " + building.origin()
                            + " predates the staking and must stand inside the wall");
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

    /**
     * A fortification is a watch, not a wall.
     *
     * <p>The stage used to be gated on a closed perimeter, and could not be:
     * the wall is paid for in coin, coin comes from a levy on production, and a
     * settlement that must wall itself before it may grow never grows rich
     * enough to wall itself — it locked at FORTIFIED for good. The wall waits
     * for TOWN now, where a chartered town stakes its circuit, so what the
     * stage names is exactly what a frontier post had: its own stores and
     * shelter, and somebody standing guard over them.
     */
    @Test
    void fortificationNeedsItsStoresAndSomebodyStandingWatch() {
        Settlement s = foundingParty();
        s.setStage(SettlementStage.FORTIFIED);

        raise(s, "kingdoms:lumber_camp");
        assertFalse(StagePlanner.readyToAdvance(s, CTX),
                "an axe without a storehouse behind it is not a fortification");

        raise(s, "kingdoms:storehouse");
        assertFalse(StagePlanner.readyToAdvance(s, CTX),
                "and stores nobody watches are not a fortification either");

        s.setPerimeterClosed(true);
        assertFalse(StagePlanner.readyToAdvance(s, CTX),
                "a wall is not what this stage is asking for, so it cannot answer it");

        s.residents().iterator().next().setProfession(Profession.GUARD);
        assertTrue(StagePlanner.readyToAdvance(s, CTX),
                "the stage's own buildings and a sentry over them complete it");
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
                "the rest of the party keeps laboring as pioneers");
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
                "an established town still runs the full catalog");
        assertEquals(SettlementStage.TOWN, SettlementStage.parse("", SettlementStage.TOWN),
                "an absent stage field parses to the fallback, not an exception");
    }
}
