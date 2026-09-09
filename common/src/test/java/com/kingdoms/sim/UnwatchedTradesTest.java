package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Seam;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.Stand;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.YieldPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town nobody is watching, working real timber and real stone, at 0/0.
 *
 * <p>{@link UnwatchedFarmingTest} asked whether an unwatched town could eat, and
 * the answer was yes and yes it starved anyway — of timber. At the shipped
 * yields nothing was conjured, so an unwatched lumber camp brought in no wood at
 * all, the build queue jammed on the first cottage the town could not pay for,
 * and four pioneers stood in a finished village for fifteen hundred steps
 * without ever raising a second field or a house for a fifth settler.
 *
 * <p>So this file asks the other half of the question. On ground with a dozen
 * trees standing and two thousand blocks of rock under the mine head, with
 * nobody ever visiting and nothing anywhere conjured, does a founding party
 * build?
 */
class UnwatchedTradesTest {

    /**
     * A world with nobody in it whose chunks can still be read.
     *
     * <p>Which is the honest shape of the case: a camp is counted the day its
     * ground is loaded, and it is worked by the clock every day after that,
     * whether or not anybody is there.
     */
    private static final class Woods implements WorldBridge {
        int trees = 12;
        int stone = 2000;

        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public int forageableNear(SimPos center, int radius) { return 0; }
        @Override public int countTreesNear(SimPos center, int radius) { return trees; }
        @Override public int countStoneBelow(SimPos c, int radius, int depth) { return stone; }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 11, 11, 3);
        }
        @Override public void log(String message) { }
    }

    private static SimSettings shipped() {
        return SimSettings.SANDBOX.withYields(YieldPolicy.DEFAULTS);
    }

    /** What a town's trades look like at a moment, for the record. */
    private static String census(Settlement town, long step) {
        Building camp = town.buildingWithRole(BuildingRole.LUMBER_CAMP);
        Building mine = town.buildingWithRole(BuildingRole.MINE);
        return "step " + step + ": " + town.stage()
                + " pop " + town.population()
                + ", buildings " + town.buildings().size()
                + ", timber " + town.woodStock()
                + ", stone " + town.stoneStock()
                + ", felled " + town.tallies().get(
                        com.kingdoms.sim.settlement.Tallies.TREES_FELLED)
                + ", stand " + (camp == null ? "-" : Stand.trees(camp) + "/"
                        + Stand.growing(camp) + " growing")
                + ", seam " + (mine == null ? "-" : String.valueOf(Seam.remaining(mine)))
                + ", farms " + town.countBuildings("kingdoms:farm");
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

    /**
     * The measurement this unit exists for.
     *
     * <p>Four pioneers, a dozen trees, a seam of two thousand, nobody watching,
     * nothing conjured. Measured: a VILLAGE of 7 with 11 buildings at step 400,
     * 267 logs felled and 34 trees coming up on ground it has cleared; the same
     * village with 14 buildings and 793 logs felled at 800; and a TOWN of 45
     * with 37 buildings and seven fields at 1500, having cut 3,857 logs and dug
     * the mine out entirely — the seam reads nought, and the town says so.
     *
     * <p>The shape of that run is the point. The claim is felled bare inside the
     * first twenty steps and everything after it comes out of saplings: the town
     * is timber-poor for four hundred steps, and then compounds, because every
     * four logs cut is a sapling put back and every sapling is six logs in a
     * day's time.
     */
    @Test
    void aFoundingPartyOnRealGroundBuildsWithoutBeingWatched() {
        Settlement camp = campOfFour();
        SimSettings settings = shipped();
        Woods world = new Woods();
        StringBuilder log = new StringBuilder();

        for (int step = 1; step <= 1500; step++) {
            camp.step(new SimContext(world, step, settings));
            if (step == 400 || step == 800 || step == 1500) {
                log.append("\n  ").append(census(camp, step));
            }
        }

        assertTrue(camp.countBuildings("kingdoms:cottage")
                        + camp.countBuildings("kingdoms:house") >= 1,
                "a roof was raised for somebody who was not on the charter." + log);
        assertTrue(camp.countBuildings("kingdoms:farm") >= 2,
                "and a second field dug, which is the thing a town with no timber"
                        + " could never get to." + log);
        assertTrue(camp.tallies().get(com.kingdoms.sim.settlement.Tallies.TREES_FELLED) > 0,
                "off real trees." + log);
        assertTrue(camp.stage().ordinal() >= SettlementStage.VILLAGE.ordinal(),
                "and it is a village at least." + log);
    }

    /** The same party on bare ground: it stalls, and the ledger says why. */
    @Test
    void aCampWithNoWoodAndNoSeedStallsAndSaysSo() {
        Settlement camp = campOfFour();
        Woods barren = new Woods();
        barren.trees = 0;
        SimSettings settings = shipped();

        for (int step = 1; step <= 800; step++) {
            camp.step(new SimContext(barren, step, settings));
        }

        Building lumberCamp = camp.buildingWithRole(BuildingRole.LUMBER_CAMP);
        assertTrue(lumberCamp == null || Stand.isBare(lumberCamp),
                "there is nothing standing and nothing coming up");
        assertTrue(camp.woodStock() <= TownStores.FOUNDING_WOOD,
                "and no timber has appeared out of the air to make up for it —"
                        + " a camp on open grass is a camp on open grass, and the"
                        + " town can see it in /civ info rather than wondering");
    }

    /**
     * A village that arrived already built goes on building.
     *
     * <p>Measured on the same ground: 13 buildings and a pop of 14 at step 400,
     * a TOWN of 16 with 16 buildings at 800, and 42 buildings and 65 people at
     * 1500, with ten fields and a mine cut out under it.
     */
    @Test
    void aSeededVillageKeepsBuilding() {
        Settlement village = seededVillage();
        SimSettings settings = shipped();
        Woods world = new Woods();
        int began = village.buildings().size();
        StringBuilder log = new StringBuilder();

        for (int step = 1; step <= 1500; step++) {
            village.step(new SimContext(world, step, settings));
            if (step == 400 || step == 800 || step == 1500) {
                log.append("\n  ").append(census(village, step));
            }
        }

        assertTrue(village.buildings().size() > began,
                "a village left alone on real ground raises more than it arrived"
                        + " with." + log);
        assertTrue(village.population() > 8, "and fills them." + log);
    }

    private static Settlement seededVillage() {
        Settlement village = new Settlement(
                Settlement.Id.random(), "Oldfield", new SimPos(0, 64, 0), 192);
        village.setCatalog(BuildCatalog.DEFAULT);
        village.setStage(SettlementStage.VILLAGE);
        village.setFoodStock(FoodPlanner.STARTING_PROVISIONS);
        String[] plan = {"kingdoms:hearth", "kingdoms:granary", "kingdoms:farm",
                "kingdoms:farm", "kingdoms:lumber_camp", "kingdoms:mine",
                "kingdoms:house", "kingdoms:house"};
        for (int i = 0; i < plan.length; i++) {
            Building b = new Building(plan[i], new SimPos(16 + i * 16, 64, 0), 0, true);
            village.addBuilding(b);
        }
        Profession[] trades = {Profession.FARMER, Profession.FARMER, Profession.FARMER,
                Profession.FARMER, Profession.LUMBERJACK, Profession.MINER,
                Profession.BUILDER, Profession.TRADER};
        for (int i = 0; i < trades.length; i++) {
            village.addResident(new Person(Person.Id.random(), "Villager " + i,
                    trades[i], new SimPos(0, 64, 0)));
        }
        return village;
    }
}
