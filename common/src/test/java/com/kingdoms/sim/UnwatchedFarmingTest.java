package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Field;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.YieldPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town nobody is watching, farming honestly, at the shipped defaults.
 *
 * <p>The yield tables ship at zero and zero: nothing anywhere is conjured. That
 * used to mean a town nobody visited starved, because food was a percentage of
 * an imagined harvest and the percentage was nought. Food is not a percentage
 * any more. It is {@link Field} — the crop blocks a farm's blueprint actually
 * lays, ripening at Minecraft's own rate, cut by the farmers the town actually
 * has — and a field goes on being a field whether or not anybody is standing in
 * it.
 *
 * <p>So this file asks the question the old one answered with a death
 * certificate: left entirely alone, on ground with nothing to forage, at 0/0,
 * does a founding party live?
 */
class UnwatchedFarmingTest {

    /** A world with nobody in it and nothing growing wild. */
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

    /** What a town looks like at a moment, for the record the CHANGELOG quotes. */
    private static String census(Settlement town, long step) {
        return "step " + step + ": " + town.stage()
                + " pop " + town.population()
                + ", food " + FoodPlanner.totalFood(town)
                + ", grain " + FoodPlanner.farmGrain(town) + " on "
                + town.countBuildings("kingdoms:farm") + " fields and "
                + FoodPlanner.bakeryGrain(town) + " at the oven"
                + ", farmers " + JobPlanner.count(town, Profession.FARMER);
    }

    private static Settlement campOfFour() {
        Settlement camp = new Settlement(
                Settlement.Id.random(), "Newholt", new SimPos(0, 64, 0), 128);
        camp.setCatalog(BuildCatalog.DEFAULT);
        camp.setStage(SettlementStage.CAMP);
        camp.setFoodStock(FoodPlanner.STARTING_PROVISIONS);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov"}) {
            camp.addResident(new Person(Person.Id.random(), name, Profession.PIONEER,
                    new SimPos(0, 64, 0)));
        }
        return camp;
    }

    // --- the ledger's own arithmetic ---

    @Test
    void aFieldRipensTheWholeOfItselfOverTheRipeningTime() {
        Building farm = new Building("kingdoms:farm", new SimPos(20, 64, 0), 0, true);
        SimSettings settings = shipped();
        SimContext ctx = new SimContext(new Alone(), 1, settings);

        for (int step = 0; step < Field.ripeningSteps(settings); step++) {
            Field.ripen(farm, ctx);
        }

        assertTrue(Field.ripeBlocks(farm) >= Field.CROP_BLOCKS * 9 / 10,
                "a field left alone for the whole ripening time should be very"
                        + " nearly all ripe, and " + Field.ripeBlocks(farm)
                        + " of " + Field.CROP_BLOCKS + " is not");
    }

    @Test
    void aFieldNeverRipensPastItself() {
        Building farm = new Building("kingdoms:farm", new SimPos(20, 64, 0), 0, true);
        SimContext ctx = new SimContext(new Alone(), 1, shipped());

        for (int step = 0; step < 5000; step++) {
            Field.ripen(farm, ctx);
        }

        assertEquals(Field.CROP_BLOCKS, Field.ripeBlocks(farm),
                "a farm nobody cuts holds a ripe field, not a granary in the soil");
    }

    @Test
    void theFractionOfABlockIsCarriedRatherThanThrownAway() {
        // A field grows about a fifth of a block a step. Rounded down at every
        // step that is nothing, forever, which is exactly the trap the yield
        // carry was written to avoid.
        Building farm = new Building("kingdoms:farm", new SimPos(20, 64, 0), 0, true);
        SimContext ctx = new SimContext(new Alone(), 1, shipped());

        for (int step = 0; step < 5; step++) {
            Field.ripen(farm, ctx);
        }

        assertTrue(farm.ripeHundredths() > 0, "five steps of growing is not nothing");
        assertEquals(0, Field.ripeBlocks(farm), "and it is not a whole block yet either");
    }

    @Test
    void cuttingTakesWholeBlocksAndLeavesTheRemainder() {
        Building farm = new Building("kingdoms:farm", new SimPos(20, 64, 0), 0, true);
        farm.setRipeHundredths(350);   // three and a half blocks

        assertEquals(2, Field.harvest(farm, 2), "only what was asked for");
        assertEquals(150, farm.ripeHundredths(), "and the half block is still standing");

        assertEquals(1, Field.harvest(farm, 9), "only what is ripe");
        assertEquals(50, farm.ripeHundredths());
    }

    @Test
    void aFullFarmStoreStopsTheHarvestAndTheFieldStaysRipe() {
        Settlement town = oneFieldTown();
        Building farm = town.buildings().get(0);
        farm.setRipeHundredths(Field.CROP_BLOCKS * 100);
        farm.stores().set(TownStores.GRAIN, FoodPlanner.FARM_GRAIN_CAP);

        FoodPlanner.advance(town, new SimContext(new Alone(), 1, shipped()));

        assertEquals(FoodPlanner.FARM_GRAIN_CAP, Field.grainStored(farm),
                "a full field cannot hold another sheaf");
        assertTrue(Field.ripeBlocks(farm) >= Field.CROP_BLOCKS - 1,
                "and nothing was cut, so the wheat is still standing — the hauling"
                        + " is the bottleneck, and it shows");
    }

    @Test
    void aRealHarvestSpendsTheSameRipeBlockTheClockWouldHave() {
        // The hook FarmWorker calls. Without it the ledger fills up behind a
        // watched field and pays the whole of it out the instant a player leaves.
        Building farm = new Building("kingdoms:farm", new SimPos(20, 64, 0), 0, true);
        farm.setRipeHundredths(1000);

        Field.cut(farm, 1);

        assertEquals(900, farm.ripeHundredths());
    }

    // --- the two fidelities ---

    @Test
    void aWatchedFieldRipensAndIsNotCutByTheClock() {
        Settlement town = oneFieldTown();
        Building farm = town.buildings().get(0);
        farm.setRipeHundredths(2000);

        for (int step = 1; step <= 20; step++) {
            FoodPlanner.advance(town, new SimContext(new Standing(), step, shipped()));
        }

        assertEquals(0, FoodPlanner.totalFood(town),
                "in front of a player only real hands harvest");
        assertTrue(Field.ripeBlocks(farm) > 20,
                "and the world went on growing the crops while they watched");
    }

    @Test
    void theWorldIsToldOnceWhatTheLedgerSaysWhenSomebodyWalksUp() {
        Settlement town = oneFieldTown();
        Building farm = town.buildings().get(0);
        farm.setFootprint(new Footprint(64, 11, 11, 3));
        Arriving world = new Arriving();

        // Nobody there for a while, then somebody, and they stay.
        for (int step = 1; step <= 5; step++) {
            FoodPlanner.advance(town, new SimContext(world, step, shipped()));
        }
        farm.setRipeHundredths(1700);
        world.here = true;
        for (int step = 6; step <= 20; step++) {
            FoodPlanner.advance(town, new SimContext(world, step, shipped()));
        }

        assertEquals(1, world.reconciled,
                "the crops are set to agree with the ledger on the step somebody"
                        + " arrives, and not again every step they stay");
        assertEquals(17, world.told, "and what they are told is what the ledger held");
    }

    @Test
    void walkingAwayAndBackAsksAgain() {
        Settlement town = oneFieldTown();
        town.buildings().get(0).setFootprint(new Footprint(64, 11, 11, 3));
        Arriving world = new Arriving();

        for (int step = 1; step <= 12; step++) {
            world.here = step % 4 < 2;   // there, gone, there, gone
            FoodPlanner.advance(town, new SimContext(world, step, shipped()));
        }

        assertEquals(4, world.reconciled, "once per arrival, and only on arrival");
    }

    // --- what a town left entirely alone actually does ---

    /**
     * A founding party of four, never visited, on ground with nothing to pick.
     *
     * <p>The measurement this whole unit exists for. Foraging carries a camp to
     * its homestead and then stops; past that everything the town eats comes out
     * of a field, and at the shipped 0/0 no part of it is conjured. The camp used
     * to reach four hundred steps out of bread with two of its four too weak to
     * work, and be gone by eight hundred.
     *
     * <p>It now feeds itself, comfortably, on one field and nothing else.
     * Measured: a VILLAGE of four with 412 loaves at step 400, 756 at 800 and
     * 1012 at 1500 — a surplus that grows until the granary is full, out of a
     * single eleven-by-eleven of wheat that nobody has ever looked at. Every one
     * of those loaves was a sheaf of grain first, cut in the field, carried to
     * the oven and baked there.
     *
     * <p><strong>What it does not do is grow, and that is not the field.</strong>
     * The camp stays four people in one field for the whole run, because at 0/0
     * an unwatched lumber camp brings in no timber, so the build queue jams on a
     * cottage it cannot pay for and no house is ever raised for a fifth settler.
     * Hand it timber and stone and it still only ever orders one farm — eighteen
     * buildings by step five hundred, one of them a field. Neither is fixed by
     * making wheat grow faster.
     * {@link #aCampWithFieldsToWorkProspersUnwatched} is the same camp with
     * fields, and it reaches eighty-one people.
     */
    @Test
    void aCampLeftEntirelyAloneAtTheShippedDefaultsFeedsItselfOffOneField() {
        Settlement camp = campOfFour();
        SimSettings settings = shipped();
        StringBuilder log = new StringBuilder();

        for (int step = 1; step <= 1500; step++) {
            camp.step(new SimContext(new Alone(), step, settings));
            if (step == 400 || step == 800 || step == 1500) {
                log.append("\n  ").append(census(camp, step));
            }
        }

        assertEquals(4, camp.population(),
                "the party that used to be dead by step eight hundred is all here"
                        + " at fifteen hundred." + log);
        assertTrue(FoodPlanner.totalFood(camp) > FoodPlanner.STARTING_PROVISIONS,
                "with more bread than it set out with, and every loaf of it cut"
                        + " off a field somebody would have had to walk to." + log);
        assertTrue(camp.stage().ordinal() >= SettlementStage.VILLAGE.ordinal(),
                "and it got its village out of that one field." + log);
    }

    /**
     * The same camp, with fields to work: it prospers, unwatched, at 0/0.
     *
     * <p>Which is the claim this whole unit is making. Nothing here is conjured —
     * the yield tables are at zero — and the town grows anyway, because the
     * fields are real and the farmers cut them. Timber and stone are handed over
     * so that the one scarce thing is food; what is being measured is whether an
     * honestly farmed field can carry a growing town, and it can.
     */
    @Test
    void aCampWithFieldsToWorkProspersUnwatched() {
        Settlement camp = campOfFour();
        for (int i = 0; i < 3; i++) {
            camp.addBuilding(new Building("kingdoms:farm",
                    new SimPos(40 + i * 16, 64, 0), 0, true));
        }
        SimSettings settings = shipped();
        StringBuilder log = new StringBuilder();

        for (int step = 1; step <= 1500; step++) {
            camp.stores().add(TownStores.WOOD, 4);
            camp.stores().add(TownStores.STONE, 4);
            camp.step(new SimContext(new Alone(), step, settings));
            if (step == 400 || step == 800 || step == 1500) {
                log.append("\n  ").append(census(camp, step));
            }
        }

        assertTrue(camp.stage().ordinal() >= SettlementStage.TOWN.ordinal(),
                "a town nobody ever visits reaches TOWN on its own crops." + log);
        assertTrue(camp.population() > 4, "and more than the party that arrived." + log);
        assertTrue(FoodPlanner.totalFood(camp) > 0, "and it is not out of bread." + log);
    }

    /** A village handed the buildings and hands a village has, and left alone. */
    @Test
    void aSeededVillageLeftAloneHolds() {
        Settlement village = seededVillage();
        SimSettings settings = shipped();
        StringBuilder log = new StringBuilder();
        int began = village.population();

        for (int step = 1; step <= 1000; step++) {
            village.step(new SimContext(new Alone(), step, settings));
            if (step == 400 || step == 800 || step == 1000) {
                log.append("\n  ").append(census(village, step));
            }
        }

        assertTrue(village.population() > began,
                "a village that arrived with its fields already dug does not"
                        + " shrink for want of watching — it fills up." + log);
        assertTrue(FoodPlanner.totalFood(village) > FoodPlanner.STARTING_PROVISIONS,
                "and sits on a granary rather than its last loaf." + log);
    }









    // --- fixtures ---

    /** A player standing in the town, always. */
    private static final class Standing implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int forageableNear(SimPos center, int radius) { return 0; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 11, 11, 3);
        }
        @Override public void log(String message) { }
    }

    /** A world somebody walks into and out of, counting what it is told. */
    private static final class Arriving implements WorldBridge {
        boolean here;
        int reconciled;
        int told = -1;

        @Override public boolean playerWithin(SimPos pos, double radius) { return here; }
        @Override public boolean isLoaded(SimPos pos) { return here; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int forageableNear(SimPos center, int radius) { return 0; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 11, 11, 3);
        }
        @Override public void setFieldRipeness(SimPos origin, Footprint plot, int ripeBlocks) {
            reconciled++;
            told = ripeBlocks;
        }
        @Override public void log(String message) { }
    }

    private static Settlement oneFieldTown() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Onefield", new SimPos(0, 64, 0), 256);
        town.setStage(SettlementStage.VILLAGE);
        town.setFoodStock(0);
        town.addBuilding(new Building("kingdoms:farm", new SimPos(20, 64, 0), 0, true));
        town.addResident(new Person(Person.Id.random(), "Solitary",
                Profession.FARMER, new SimPos(0, 64, 0)));
        return town;
    }

    private static Settlement seededVillage() {
        Settlement village = new Settlement(
                Settlement.Id.random(), "Oldfield", new SimPos(0, 64, 0), 192);
        village.setCatalog(BuildCatalog.DEFAULT);
        village.setStage(SettlementStage.VILLAGE);
        village.setFoodStock(FoodPlanner.STARTING_PROVISIONS);
        String[] plan = {"kingdoms:hearth", "kingdoms:granary", "kingdoms:farm",
                "kingdoms:farm", "kingdoms:farm", "kingdoms:house", "kingdoms:house",
                "kingdoms:house"};
        for (int i = 0; i < plan.length; i++) {
            Building b = new Building(plan[i], new SimPos(16 + i * 16, 64, 0), 0, true);
            b.setSeeded(true);
            village.addBuilding(b);
        }
        Profession[] trades = {Profession.FARMER, Profession.FARMER, Profession.FARMER,
                Profession.FARMER, Profession.FARMER, Profession.FARMER,
                Profession.BUILDER, Profession.TRADER};
        for (int i = 0; i < trades.length; i++) {
            village.addResident(new Person(Person.Id.random(), "Villager " + i,
                    trades[i], new SimPos(0, 64, 0)));
        }
        return village;
    }
}
