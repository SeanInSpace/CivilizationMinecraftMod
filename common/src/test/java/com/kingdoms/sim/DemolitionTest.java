package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Founding;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.Tallies;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.settlement.WorkArea;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A building can be destroyed, and the town notices.
 *
 * <p>Nothing removed a {@link Building} from a settlement until now, and it was
 * never a decision: it simply did not come up while nothing in the mod could
 * destroy one. So a cottage blown flat by a creeper went on housing a family,
 * counting toward the town's beds, having roads routed to its door and being
 * audited for a way in it no longer had.
 *
 * <p>Every case below is one of the ways a town could go on believing in
 * something that is gone. The last one is the only one that could have been
 * written before any of the rest: a town that has lost its hall, its granary and
 * a full storehouse has to keep running, and the way to find out is to run it.
 */
class DemolitionTest {

    /** Unwatched and unloaded, which is how a town spends most of its life. */
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

    private static Settlement town() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        town.setCatalog(BuildCatalog.DEFAULT);
        return town;
    }

    private static Building built(String blueprintId, int x, int z) {
        return new Building(blueprintId, new SimPos(x, 64, z), 1, true);
    }

    /** A family of two, living at this address. */
    private static Household familyAt(Settlement town, String name, SimPos home) {
        Household family = new Household(Household.Id.random(), name);
        for (int i = 0; i < 2; i++) {
            Person person = new Person(
                    Person.Id.random(), name + " " + i, Profession.BUILDER, home);
            town.addResident(person);
            family.addMember(person.id());
        }
        family.setHome(home);
        town.addHousehold(family);
        return family;
    }

    // --- the family ---

    @Test
    void aFamilyWhoseHouseIsGoneMovesIntoAnEmptyOne() {
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        Building spare = built("kingdoms:house", 60, 60);
        town.addBuilding(lost);
        town.addBuilding(spare);
        Household family = familyAt(town, "Mason", lost.origin());

        town.removeBuilding(lost, 7, "a creeper");

        assertEquals(spare.origin(), family.home(),
                "there was a house standing empty, so they are in it");
    }

    @Test
    void andGoesOnTheHousingQueueWhenThereIsNowhereToMove() {
        // Homeless is not a failure state. An unhoused family is what housing
        // demand is made of, so a town that loses a cottage wants another one —
        // which is the whole behavior a demolition ought to produce.
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        town.addBuilding(lost);
        Household family = familyAt(town, "Mason", lost.origin());

        town.removeBuilding(lost, 7, "a creeper");

        assertFalse(family.isHoused(), "they have nowhere to live");
        assertTrue(town.households().contains(family),
                "but they are still a family, with their name and their pantry");
        assertEquals(2, town.population(), "and nobody has died of it");
        assertTrue(town.unhousedHouseholds().contains(family),
                "so the town wants another house");
    }

    @Test
    void aHouseholdThatHadAlreadyDiedOutIsRetiredWithItsHouse() {
        // The one case where the family really does end here rather than move.
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        town.addBuilding(lost);
        Household ghosts = new Household(Household.Id.random(), "Nobody");
        ghosts.setHome(lost.origin());
        town.addHousehold(ghosts);

        town.removeBuilding(lost, 7, "a creeper");

        assertFalse(town.households().contains(ghosts),
                "a household with nobody in it is not a household to rehouse");
    }

    @Test
    void twoFamiliesOutOfOneLonghouseDoNotBothClaimTheSameCottage() {
        Settlement town = town();
        Building lost = built("kingdoms:longhouse", 20, 20);
        town.addBuilding(lost);
        town.addBuilding(built("kingdoms:house", 60, 60));
        town.addBuilding(built("kingdoms:house", 100, 100));
        Household first = familyAt(town, "Mason", lost.origin());
        Household second = familyAt(town, "Turner", lost.origin());

        town.removeBuilding(lost, 7, "a creeper");

        assertTrue(first.isHoused() && second.isHoused(), "both found somewhere");
        assertFalse(first.home().equals(second.home()),
                "and not the same somewhere — a house holds one family");
    }

    @Test
    void theFamilyItselfMovesAndNotJustItsAddress() {
        // Every other move in the population planner walks the people too. A
        // family recorded across town while its members stand in the crater is
        // where the view layer would put their bodies back.
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        Building spare = built("kingdoms:house", 60, 60);
        town.addBuilding(lost);
        town.addBuilding(spare);
        Household family = familyAt(town, "Mason", lost.origin());

        town.removeBuilding(lost, 7, "a creeper");

        for (Person.Id member : family.members()) {
            assertEquals(spare.origin(), town.resident(member).position(),
                    "they are standing at the house they now live in");
        }
    }

    @Test
    void aFamilyIsEvictedEvenWhenTheirHouseHasSettledAtADifferentHeight() {
        // A house finished in an unloaded chunk carries an estimated height, a
        // family can be housed in it before anybody has been near, and drawing
        // it writes the real height back. Matching an address on all three
        // numbers would quietly fail to evict exactly those families.
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        town.addBuilding(lost);
        Household family = familyAt(town, "Mason", lost.origin());
        lost.setOriginY(71);   // the ground turned out to be seven blocks up

        town.removeBuilding(lost, 7, "a creeper");

        assertFalse(family.isHoused(),
                "the plot is what a family lives on; the height is where it ended up");
    }

    @Test
    void anEvictedFamilyIsNotMovedInOnTopOfAnotherOne() {
        // The trap the plot rule opens if only half of it is applied. The
        // Turners are housed at the height their cottage was estimated at; the
        // cottage has since settled a few blocks up. Asked in full, their house
        // reads as standing empty — so the Masons are moved into it, and the
        // Turners' own capacity then reads zero, which reads as permanently
        // overcrowded and stops them growing.
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        Building theirs = built("kingdoms:house", 60, 60);
        town.addBuilding(lost);
        town.addBuilding(theirs);
        Household turners = familyAt(town, "Turner", theirs.origin());
        theirs.setOriginY(71);
        Household masons = familyAt(town, "Mason", lost.origin());

        town.removeBuilding(lost, 7, "a creeper");

        assertFalse(masons.isHoused(),
                "every house in this town is lived in, so the Masons have nowhere");
        assertTrue(turners.isHoused(), "and nobody has been moved in on top of the Turners");
    }

    // --- the ground and the road ---

    @Test
    void thePlotIsFreeToBuildOnAgain() {
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        lost.setFootprint(new Footprint(64, 9, 9, 4));
        town.addBuilding(lost);
        assertFalse(town.isPlotFree(lost.origin(), 9, null),
                "occupied while it stands, or this test proves nothing");

        town.removeBuilding(lost, 7, "a creeper");

        assertTrue(town.isPlotFree(lost.origin(), 9, null),
                "the ground is nobody's again, so something can stand here");
    }

    @Test
    void theRoadForgetsTheDoorItLedTo() {
        // PathNetwork.forget has existed with no caller since roads did, and its
        // own comment says what for: a demolished plot's road can be re-planned.
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        town.addBuilding(lost);
        PathNetwork roads = new PathNetwork();
        roads.markJoined(lost.origin());
        town.setPaths(roads);

        town.removeBuilding(lost, 7, "a creeper");

        assertFalse(town.paths().hasJoined(lost.origin()),
                "the town no longer believes it has run a way to this door");
    }

    @Test
    void andForgetsItEvenWhenTheRoadWasJoinedAtAnotherHeight() {
        // Roads are planned before a building is drawn, so the height in the
        // joined set is routinely the estimate rather than the answer.
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        town.addBuilding(lost);
        PathNetwork roads = new PathNetwork();
        roads.markJoined(new SimPos(20, 71, 20));
        town.setPaths(roads);

        town.removeBuilding(lost, 7, "a creeper");

        assertTrue(town.paths().joined().isEmpty(),
                "a plot is joined or it is not; the height is not part of the address");
    }

    // --- work booked against a building that has gone ---

    @Test
    void theRepairQueuedForItIsCanceledWithIt() {
        // RepairPlanner will always have queued one: anything hurt badly enough
        // to be written off passed SEVERE_DAMAGE on the way down, and a repair
        // goes to the HEAD of the queue. The queue is head-blocking, so a repair
        // the town cannot pay for would stop it ever ordering the replacement
        // house the eviction has just made it want.
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        town.addBuilding(lost);
        BuildTask repair = new BuildTask("kingdoms:house", lost.origin(), 30);
        repair.setUpgradeOf(lost.origin());
        town.enqueueUrgent(repair);

        town.removeBuilding(lost, 7, "a creeper");

        assertTrue(town.buildQueue().isEmpty(),
                "there is nothing left here to repair");
        assertTrue(town.isPlotFree(lost.origin(), 11, null),
                "and the ground the repair was holding is free to build on");
    }

    @Test
    void aFreshBuildOrderedOntoTheSamePlotIsSomebodysPlanAndStays() {
        // The other half. Only work aimed at the building that has gone is
        // canceled; a new building ordered onto the ground is a plan for the
        // ground.
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        town.addBuilding(lost);
        town.enqueueBuild(new BuildTask("kingdoms:granary", lost.origin(), 30));

        town.removeBuilding(lost, 7, "a creeper");

        assertEquals(1, town.buildQueue().size(), "somebody still means to build here");
    }

    // --- the goods ---

    @Test
    void theGoodsInAStoreAreNotDestroyedWithIt() {
        // A store's ledger is part of the town's stock rather than a copy of a
        // town-wide figure, so dropping it with the building would take a
        // storehouse's worth of timber out of the books.
        Settlement town = town();
        Building store = built("kingdoms:storehouse", 20, 20);
        town.addBuilding(store);
        store.stores().add(TownStores.WOOD, 300);
        store.stores().add(TownStores.STONE, 120);
        int wood = town.stores().get(TownStores.WOOD);
        // The founding kit is already lying in the open — see putAwayLoosePile —
        // so what is asserted below is the change, not the total.
        int lyingAbout = town.loosePile().get(TownStores.WOOD);

        town.removeBuilding(store, 7, "a creeper");

        assertEquals(wood, town.stores().get(TownStores.WOOD),
                "the town is no poorer for having lost the shelves");
        assertEquals(lyingAbout + 300, town.loosePile().get(TownStores.WOOD),
                "the timber is lying in the open, which is where goods with "
                        + "nowhere to be belong");
        assertEquals(TownStores.FOUNDING_STONE + 120, town.loosePile().get(TownStores.STONE),
                "and so is the stone");
    }

    @Test
    void andArePutAwayAgainAsSoonAsThereIsSomewhereToPutThem() {
        Settlement town = town();
        Building store = built("kingdoms:storehouse", 20, 20);
        Building spare = built("kingdoms:warehouse", 60, 60);
        town.addBuilding(store);
        town.addBuilding(spare);
        store.stores().add(TownStores.WOOD, 300);
        int owed = town.stores().get(TownStores.WOOD);

        town.removeBuilding(store, 7, "a creeper");
        town.putAwayLoosePile();

        assertEquals(owed, spare.stores().get(TownStores.WOOD),
                "carried into the store that is still standing — the salvage and "
                        + "the founding kit alike");
        assertEquals(0, town.loosePile().get(TownStores.WOOD), "and no longer lying about");
    }

    @Test
    void theHarvestWaitingAtAFarmStaysInTheTown() {
        // Food a farm or a stall is holding is counted in its own column, so it
        // has to move to keep the total the same either side of the demolition.
        Settlement town = town();
        Building farm = built("kingdoms:farm", 20, 20);
        town.addBuilding(farm);
        farm.setFoodStored(40);
        int larder = FoodPlanner.totalFood(town);
        int banked = town.loosePile().get(TownStores.FOOD);

        town.removeBuilding(farm, 7, "a creeper");

        assertEquals(larder, FoodPlanner.totalFood(town),
                "not a loaf was lost with the field");
        assertEquals(banked + 40, town.loosePile().get(TownStores.FOOD),
                "it is on the ground with the provisions the party arrived carrying");
        assertEquals(0, farm.foodStored(), "and not still on the books of a field that is gone");
    }

    // --- the ground a trade was working ---

    @Test
    void theWoodlandGoesBackWithTheLastCamp() {
        // A work area is a claim staked on behalf of a camp. Left behind, it is
        // a license to fell trees for a camp that burned down.
        Settlement town = town();
        Building camp = built("kingdoms:lumber_camp", 20, 20);
        town.addBuilding(camp);
        town.setLumberArea(new WorkArea(camp.origin(), LumberPlanner.DEFAULT_RADIUS));

        town.removeBuilding(camp, 7, "a creeper");

        assertNull(town.lumberArea(), "nothing is felling for a camp that is not there");
    }

    @Test
    void aTownWithTwoCampsStopsWorkingTheWoodTheLostOneClaimed() {
        // "Was it the last of its kind" is the wrong question. The claim was
        // staked around this camp, and the lumberjacks who are left would go on
        // walking to woodland held for a building that is not there. Cleared,
        // the planner re-stakes on the camp that is still standing.
        Settlement town = town();
        Building camp = built("kingdoms:lumber_camp", 20, 20);
        town.addBuilding(camp);
        town.addBuilding(built("kingdoms:lumber_camp", 60, 60));
        town.setLumberArea(new WorkArea(camp.origin(), LumberPlanner.DEFAULT_RADIUS));

        town.removeBuilding(camp, 7, "a creeper");

        assertNull(town.lumberArea(), "the claim went with the camp that made it");
    }

    @Test
    void groundSomebodyElseClaimedIsLeftWhereItIs() {
        // The other half. A player can point the camp block at the wood they
        // want felled, and a camp burning down somewhere else must not throw
        // that choice away.
        Settlement town = town();
        Building camp = built("kingdoms:lumber_camp", 20, 20);
        town.addBuilding(camp);
        Building elsewhere = built("kingdoms:lumber_camp", 60, 60);
        town.addBuilding(elsewhere);
        WorkArea chosen = new WorkArea(new SimPos(200, 64, 200), LumberPlanner.DEFAULT_RADIUS);
        town.setLumberArea(chosen);

        town.removeBuilding(camp, 7, "a creeper");

        assertEquals(chosen, town.lumberArea(), "nobody asked for that wood to be given up");
    }

    // --- the books ---

    @Test
    void theTownWritesDownThatItLostSomething() {
        Settlement town = town();
        Building lost = built("kingdoms:house", 20, 20);
        town.addBuilding(lost);

        town.removeBuilding(lost, 7, "a creeper");

        assertEquals(1, town.tallies().get(Tallies.BUILDINGS_LOST),
                "raised on its own reads as a town's size; lost is the other half");
        assertTrue(town.events().stream().anyMatch(e -> e.message().contains("a creeper")),
                "and its history says what happened: " + town.events());
    }

    @Test
    void removingSomethingThatWasNeverOursChangesNothing() {
        Settlement town = town();
        town.addBuilding(built("kingdoms:house", 20, 20));

        assertFalse(town.removeBuilding(built("kingdoms:house", 90, 90), 7, "a creeper"),
                "a building this town never had is not this town's to lose");
        assertEquals(1, town.buildings().size(), "and nothing else moved");
        assertEquals(0, town.tallies().get(Tallies.BUILDINGS_LOST), "nor was anything counted");
    }

    // --- and then it has to go on living ---

    @Test
    void aTownGoesOnRunningAfterLosingItsHallGranaryAndStore() {
        // The three worst losses at once, in one afternoon: the building the
        // stage program is built around, the larder every food planner reads,
        // and a full storehouse. Two hundred steps afterwards is long enough for
        // the population, food, hauling, job, road, perimeter and repair passes
        // to have run over the hole several times each.
        Settlement town = Founding.seeded(new SimPos(0, 72, 0), "Ruinhead",
                SettlementStage.TOWN, BuildCatalog.DEFAULT, Culture.NORMAN.id());
        Building hall = town.buildingWithRole(BuildingRole.HALL);
        Building granary = town.buildingWithRole(BuildingRole.GRANARY);
        Building store = town.buildingWithRole(BuildingRole.STORE);
        assertTrue(hall != null && granary != null && store != null,
                "a seeded town stands the whole program it climbed through");
        store.stores().add(TownStores.WOOD, 200);

        town.removeBuilding(hall, 0, "a creeper");
        town.removeBuilding(granary, 0, "a creeper");
        town.removeBuilding(store, 0, "a creeper");

        assertDoesNotThrow(() -> {
            for (int step = 1; step <= 200; step++) {
                town.step(new SimContext(new QuietBridge(), step, SimSettings.SANDBOX));
            }
        }, "a town that has lost its middle is still a town");
        assertTrue(town.population() > 0, "and there is somebody left in it");
    }
}
