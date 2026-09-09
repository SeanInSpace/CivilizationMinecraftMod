package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.Field;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.HaulPlanner;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.YieldPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grain becomes bread, and somebody carries it.
 *
 * <p>A cut block of wheat used to be a loaf of bread on the shelf of the farm it
 * was cut on, which made the sickle the town's bakery. It is a sheaf of
 * {@link TownStores#GRAIN} now, and nobody can eat it. It has to be carried to
 * wherever the settlement bakes — the mill if one stands and a miller works it,
 * the hearth or the granary otherwise — and only there does it become food.
 *
 * <p>Which means a town can be rich in wheat and starving, and this file is
 * mostly about making sure that is <em>visible</em> rather than mysterious: the
 * sacks stack up in the fields where a player walks past them, and
 * {@code /civ info} prints them.
 */
class BreadTest {

    /** Nobody watching, nothing wild to pick. */
    private static final class Alone implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int forageableNear(SimPos center, int radius) { return 0; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 11, 11, 3);
        }
        @Override public void log(String message) { }
    }

    private static SimSettings shipped() {
        return SimSettings.SANDBOX.withYields(YieldPolicy.DEFAULTS);
    }

    private static SimContext ctx(int step) {
        return new SimContext(new Alone(), step, shipped());
    }

    private static Settlement town(String name) {
        Settlement town = new Settlement(
                Settlement.Id.random(), name, new SimPos(0, 64, 0), 256);
        town.setStage(SettlementStage.VILLAGE);
        town.setFoodStock(0);
        return town;
    }

    private static Building raise(Settlement town, String blueprintId, int x) {
        Building building = new Building(blueprintId, new SimPos(x, 64, 0), 0, true);
        town.addBuilding(building);
        return building;
    }

    private static Person hand(Settlement town, String name, Profession trade) {
        Person person = new Person(Person.Id.random(), name, trade, town.center());
        town.addResident(person);
        return person;
    }

    /** One step of everything the food chain does, errands included. */
    private static void chain(Settlement town, int steps, int from) {
        for (int step = from; step < from + steps; step++) {
            FoodPlanner.advance(town, ctx(step));
            HaulPlanner.advance(town, ctx(step));
        }
    }

    // --- the field ---

    @Test
    void aCutBlockIsASheafOfGrainAndNotALoaf() {
        Settlement town = town("Sheaf");
        Building farm = raise(town, "kingdoms:farm", 20);
        farm.setRipeHundredths(Field.CROP_BLOCKS * 100);
        hand(town, "Solitary", Profession.FARMER);

        FoodPlanner.advance(town, ctx(1));

        assertTrue(Field.grainStored(farm) > 0, "the cut wheat is on the farm's shelf");
        assertEquals(0, farm.foodStored(), "and not one crumb of it is bread");
        assertEquals(0, FoodPlanner.totalFood(town),
                "a town with a full field and no oven owns no food whatever");
    }

    @Test
    void grainStacksUpToTheCapAndTheCuttingStops() {
        Settlement town = town("Brimming");
        Building farm = raise(town, "kingdoms:farm", 20);
        hand(town, "Ada", Profession.FARMER);
        hand(town, "Bruno", Profession.FARMER);

        // No oven anywhere, so nothing is ever carried away: the shelf can only
        // fill. Long enough for two farmers to cut far more than it holds.
        chain(town, 400, 1);

        assertEquals(FoodPlanner.FARM_GRAIN_CAP, Field.grainStored(farm),
                "a farm's shelf holds one harvest and no more");
        assertEquals(Field.CROP_BLOCKS, Field.ripeBlocks(farm),
                "and the rest of it is standing in the field, uncut — the hauling"
                        + " is the bottleneck and it shows");
    }

    // --- the walk ---

    @Test
    void aFarmerCarriesTheGrainToTheOvenWithinAFewSteps() {
        Settlement town = town("Carried");
        Building farm = raise(town, "kingdoms:farm", 60);
        Building granary = raise(town, "kingdoms:granary", 4);
        farm.stores().set(TownStores.GRAIN, FoodPlanner.FARM_GRAIN_CAP);
        hand(town, "Ada", Profession.FARMER);

        // Sixty blocks out and back at ABSTRACT_TRAVEL_BLOCKS a step is ten
        // steps of walking; twenty is a fair allowance for one delivery.
        chain(town, 20, 1);

        assertTrue(granary.stores().get(TownStores.GRAIN) > 0
                        || town.foodStock() > 0,
                "the sheaves reached the oven on somebody's back");
        assertTrue(Field.grainStored(farm) < FoodPlanner.FARM_GRAIN_CAP,
                "and they genuinely left the field");
    }

    @Test
    void thereIsNoErrandAtAllWhenTheTownHasNowhereToBake() {
        Settlement town = town("Ovenless");
        Building farm = raise(town, "kingdoms:farm", 60);
        farm.stores().set(TownStores.GRAIN, FoodPlanner.FARM_GRAIN_CAP);
        Person ada = hand(town, "Ada", Profession.FARMER);

        FoodPlanner.advance(town, ctx(1));

        assertNull(FoodPlanner.bakery(town), "the premise: nowhere to bake");
        assertNull(ada.haul(), "so there is nowhere to carry it, and she stays in the rows");
    }

    // --- the oven ---

    @Test
    void theHearthBakesTheGrainItIsGiven() {
        Settlement town = town("Hearthside");
        Building hearth = raise(town, "kingdoms:hearth", 8);
        hearth.stores().set(TownStores.GRAIN, 40);

        FoodPlanner.advance(town, ctx(1));

        assertEquals(FoodPlanner.BAKE_PER_STEP, town.foodStock(),
                "a hearth turns grain into bread one for one, up to its rate");
        assertEquals(40 - FoodPlanner.BAKE_PER_STEP, hearth.stores().get(TownStores.GRAIN),
                "and spends exactly the grain it baked");
    }

    @Test
    void aGranaryIsAnOvenTooWhenThereIsNoMill() {
        Settlement town = town("Larder");
        Building granary = raise(town, "kingdoms:granary", 4);
        granary.stores().set(TownStores.GRAIN, 40);

        assertEquals(granary, FoodPlanner.bakery(town));

        FoodPlanner.advance(town, ctx(1));

        assertEquals(FoodPlanner.BAKE_PER_STEP, town.foodStock());
    }

    @Test
    void theMillOutbakesTheHearthAndDoesItAtTwoForThree() {
        Settlement withMill = town("Millward");
        raise(withMill, "kingdoms:granary", 4);
        Building mill = raise(withMill, "kingdoms:mill", 12);
        hand(withMill, "Miller", Profession.MILLER);
        mill.stores().set(TownStores.GRAIN, 60);

        assertTrue(FoodPlanner.millRuns(withMill), "the premise: a mill and a miller");
        assertEquals(mill, FoodPlanner.bakery(withMill),
                "and the mill is where the town bakes");

        FoodPlanner.advance(withMill, ctx(1));

        int ground = 60 - mill.stores().get(TownStores.GRAIN);
        int loaves = withMill.foodStock();
        assertEquals(JobPlanner.count(withMill, Profession.MILLER)
                        * FoodPlanner.MILL_GRAIN_PER_MILLER, ground,
                "one miller puts his own six sheaves through the stones");
        assertEquals(ground / FoodPlanner.MILL_GRAIN_PER_BATCH * FoodPlanner.MILL_LOAVES_PER_BATCH,
                loaves, "at three loaves for every two sheaves");
        assertTrue(loaves > FoodPlanner.BAKE_PER_STEP,
                "which is more bread than a hearth makes in a step, out of the same"
                        + " wheat — that is what a mill is for");
    }

    @Test
    void aMillWithNoMillerIsJustABuilding() {
        Settlement town = town("Idle stones");
        Building hearth = raise(town, "kingdoms:hearth", 8);
        raise(town, "kingdoms:mill", 12);
        hearth.stores().set(TownStores.GRAIN, 40);

        assertEquals(hearth, FoodPlanner.bakery(town),
                "unmanned stones grind nothing, so the fire is still the bakery");

        FoodPlanner.advance(town, ctx(1));

        assertEquals(FoodPlanner.BAKE_PER_STEP, town.foodStock());
    }

    // --- the visible truth ---

    @Test
    void aTownWithNoBakeryStarvesBesideFullSacks() {
        Settlement town = town("Sackfast");
        Building farm = raise(town, "kingdoms:farm", 20);
        farm.setRipeHundredths(Field.CROP_BLOCKS * 100);
        hand(town, "Ada", Profession.FARMER);
        hand(town, "Bruno", Profession.FARMER);

        chain(town, 40, 1);

        assertTrue(FoodPlanner.farmGrain(town) > 0,
                "the wheat was cut and the sacks are full");
        assertEquals(0, FoodPlanner.totalFood(town), "and there is nothing to eat");
        assertTrue(town.isStarving(),
                "so the town is starving in the middle of its own harvest, which is"
                        + " the truth and is meant to be seen");

        chain(town, 80, 41);

        assertEquals(0, town.population(),
                "and starving is not a mood: they die of it, beside the sacks");
        assertTrue(FoodPlanner.farmGrain(town) > 0, "which are still there");
    }

    @Test
    void theSameTownWithAHearthLives() {
        // The control for the case above, one building apart. Nothing else
        // changes: the same field, the same two farmers, the same hundred and
        // twenty steps.
        Settlement town = town("Sackfast");
        Building farm = raise(town, "kingdoms:farm", 20);
        farm.setRipeHundredths(Field.CROP_BLOCKS * 100);
        raise(town, "kingdoms:hearth", 8);
        hand(town, "Ada", Profession.FARMER);
        hand(town, "Bruno", Profession.FARMER);

        chain(town, 120, 1);

        assertTrue(FoodPlanner.totalFood(town) > 0, "one hearth is the whole difference");
    }

    // --- the compound is not a field ---

    @Test
    void anAnimalFarmCountsForNothingInTheFoodChain() {
        // "farm" as a blueprint-path suffix matches kingdoms:animal_farm, so the
        // sheep pen has been counted as a crop field since the day it was added:
        // handed a seventy-one-block ripeness ledger it has no wheat for, sent
        // farmers, and credited with a harvest out of nowhere. BuildingRole
        // settles it.
        Settlement town = town("Pens");
        Building pen = raise(town, "kingdoms:animal_farm", 20);
        raise(town, "kingdoms:hearth", 8);
        hand(town, "Ada", Profession.FARMER);
        hand(town, "Bruno", Profession.FARMER);

        assertEquals(BuildingRole.ANIMAL_FARM, pen.role(), "the premise");

        chain(town, 200, 1);

        assertEquals(0, Field.ripeBlocks(pen), "no wheat ripens in a sheep pen");
        assertEquals(0, Field.grainStored(pen), "and none is cut there");
        assertEquals(0, FoodPlanner.farmGrain(town),
                "the compound is not one of the town's fields");
        assertEquals(0, FoodPlanner.totalFood(town),
                "and it feeds nobody bread — a town of beasts and no crops has no"
                        + " loaves at all");
    }
}
