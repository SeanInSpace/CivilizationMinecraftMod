package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers a town improving what it has, once it has everything it wants. */
class UpgradeTest {

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

    private static final BuildingType HOUSE =
            new BuildingType("civilization:house", 20, 1, 1, 0, 80, 4);
    private static final List<BuildingType> CATALOG = List.of(HOUSE);

    private static Settlement town() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
    }

    private static Building standing(String id, int level) {
        Building b = new Building(id, new SimPos(8, 64, 8), 1, true);
        b.setLevel(level);
        return b;
    }

    @Test
    void alevelLivesInTheIdSoADatapackCanSupplyOne() {
        assertEquals("civilization:house", BuildPlanner.leveledId("civilization:house", 1));
        assertEquals("civilization:house_l2", BuildPlanner.leveledId("civilization:house", 2));

        assertEquals(2, BuildPlanner.levelOf("civilization:house_l2"));
        assertEquals(1, BuildPlanner.levelOf("civilization:house"));
        assertEquals("civilization:house", BuildPlanner.baseIdOf("civilization:house_l2"));
        assertEquals("civilization:house", BuildPlanner.baseIdOf("civilization:house"));
    }

    @Test
    void anIdThatMerelyContainsTheMarkerIsNotALevel() {
        assertEquals(1, BuildPlanner.levelOf("civilization:wool_lodge"));
        assertEquals("civilization:wool_lodge", BuildPlanner.baseIdOf("civilization:wool_lodge"));
    }

    @Test
    void aleveledBuildingStillCountsAsWhatItIs() {
        Settlement s = town();
        s.addBuilding(standing("civilization:house_l2", 2));

        assertEquals(1, s.countBuildings("civilization:house"),
                "an improved house is still a house the town owns");
    }

    @Test
    void theLowestLevelIsImprovedFirst() {
        Settlement s = town();
        Building grand = standing("civilization:house_l2", 2);
        Building plain = new Building("civilization:house", new SimPos(-8, 64, -8), 1, true);
        s.addBuilding(grand);
        s.addBuilding(plain);

        assertEquals(plain, BuildPlanner.chooseUpgrade(s, CATALOG).orElseThrow(),
                "a town improves evenly rather than raising one showpiece");
    }

    @Test
    void nothingIsImprovedPastTheTop() {
        Settlement s = town();
        s.addBuilding(standing("civilization:house_l3", BuildPlanner.MAX_LEVEL));

        assertTrue(BuildPlanner.chooseUpgrade(s, CATALOG).isEmpty());
    }

    @Test
    void thingsTheCatalogNeverAskedForAreLeftAlone() {
        Settlement s = town();
        s.addBuilding(new Building("civilization:stairs", new SimPos(4, 64, 4), 1, true));

        assertTrue(BuildPlanner.chooseUpgrade(s, CATALOG).isEmpty(),
                "a repair flight is not a building to be made grander");
    }

    @Test
    void aleveledBuildingIsStillFoundByItsRole() {
        // Everything that looks a building up does it by name suffix — the food
        // chain, the workplace lookup, the path layer. An improved farm that
        // stopped answering to "farm" would quietly drop out of all three, and a
        // town whose granary got better would starve beside it.
        assertEquals("civilization:farm", BuildPlanner.baseIdOf("civilization:farm_l2"));
        assertTrue(BuildPlanner.baseIdOf("civilization:farm_l3").endsWith("farm"));
        assertTrue(BuildPlanner.baseIdOf("civilization:animal_farm_l2").endsWith("animal_farm"));
        assertTrue(BuildPlanner.baseIdOf("civilization:town_hall_l3").endsWith("town_hall"));
    }

    @Test
    void improvingCostsMoreEachTime() {
        assertTrue(BuildPlanner.upgradeWork(HOUSE, 3) > BuildPlanner.upgradeWork(HOUSE, 2));
        assertTrue(BuildPlanner.upgradeWork(HOUSE, 2) > HOUSE.workCost());
    }

    @Test
    void anUpgradeFindsItsBuildingEvenAfterTheGroundMovedUnderIt() {
        // The failure this guards. setUpgradeOf records the target's origin when
        // the work is ordered; setOriginY writes that origin again when the
        // structure is finally placed and the ground turns out to be at a
        // different height. Matching on the whole origin meant the finished
        // upgrade found nothing, fell out of the loop, and threw away every unit
        // of work — leaving a town certain it had improved a building it had
        // never touched.
        Settlement s = town();
        Building house = standing("civilization:house", 1);
        s.addBuilding(house);
        // Hands to do the work and stock to pay for it, or the queue never moves.
        for (int i = 0; i < 4; i++) {
            s.addResident(new com.civilization.sim.person.Person(
                    com.civilization.sim.person.Person.Id.random(), "Hand " + i,
                    com.civilization.sim.person.Profession.BUILDER, s.center()));
        }
        s.setStock(com.civilization.sim.settlement.TownStores.WOOD, 5000);
        s.setStock(com.civilization.sim.settlement.TownStores.STONE, 5000);
        s.setStock(com.civilization.sim.settlement.TownStores.FOOD, 5000);

        BuildTask work = new BuildTask("civilization:house_l2", house.origin(), 1);
        work.setUpgradeOf(house.origin());
        s.enqueueBuild(work);

        // The ground under it turns out to be four blocks higher than surveyed.
        house.setOriginY(house.origin().y() + 4);

        for (int step = 0; step < 40 && !s.buildQueue().isEmpty(); step++) {
            s.step(CTX);
        }

        assertEquals(1, s.buildings().size(),
                "one building, improved — not a second one stacked on the first");
        assertEquals("civilization:house_l2", s.buildings().getFirst().blueprintId());
        assertEquals(2, s.buildings().getFirst().level(), "and the work was not wasted");
    }
}
