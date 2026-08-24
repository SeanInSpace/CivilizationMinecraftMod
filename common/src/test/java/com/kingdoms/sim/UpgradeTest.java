package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Settlement;
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
            new BuildingType("kingdoms:house", 20, 1, 1, 0, 80, 4);
    private static final List<BuildingType> CATALOGUE = List.of(HOUSE);

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
        assertEquals("kingdoms:house", BuildPlanner.levelledId("kingdoms:house", 1));
        assertEquals("kingdoms:house_l2", BuildPlanner.levelledId("kingdoms:house", 2));

        assertEquals(2, BuildPlanner.levelOf("kingdoms:house_l2"));
        assertEquals(1, BuildPlanner.levelOf("kingdoms:house"));
        assertEquals("kingdoms:house", BuildPlanner.baseIdOf("kingdoms:house_l2"));
        assertEquals("kingdoms:house", BuildPlanner.baseIdOf("kingdoms:house"));
    }

    @Test
    void anIdThatMerelyContainsTheMarkerIsNotALevel() {
        assertEquals(1, BuildPlanner.levelOf("kingdoms:wool_lodge"));
        assertEquals("kingdoms:wool_lodge", BuildPlanner.baseIdOf("kingdoms:wool_lodge"));
    }

    @Test
    void alevelledBuildingStillCountsAsWhatItIs() {
        Settlement s = town();
        s.addBuilding(standing("kingdoms:house_l2", 2));

        assertEquals(1, s.countBuildings("kingdoms:house"),
                "an improved house is still a house the town owns");
    }

    @Test
    void theLowestLevelIsImprovedFirst() {
        Settlement s = town();
        Building grand = standing("kingdoms:house_l2", 2);
        Building plain = new Building("kingdoms:house", new SimPos(-8, 64, -8), 1, true);
        s.addBuilding(grand);
        s.addBuilding(plain);

        assertEquals(plain, BuildPlanner.chooseUpgrade(s, CATALOGUE).orElseThrow(),
                "a town improves evenly rather than raising one showpiece");
    }

    @Test
    void nothingIsImprovedPastTheTop() {
        Settlement s = town();
        s.addBuilding(standing("kingdoms:house_l3", BuildPlanner.MAX_LEVEL));

        assertTrue(BuildPlanner.chooseUpgrade(s, CATALOGUE).isEmpty());
    }

    @Test
    void thingsTheCatalogueNeverAskedForAreLeftAlone() {
        Settlement s = town();
        s.addBuilding(new Building("kingdoms:stairs", new SimPos(4, 64, 4), 1, true));

        assertTrue(BuildPlanner.chooseUpgrade(s, CATALOGUE).isEmpty(),
                "a repair flight is not a building to be made grander");
    }

    @Test
    void alevelledBuildingIsStillFoundByItsRole() {
        // Everything that looks a building up does it by name suffix — the food
        // chain, the workplace lookup, the path layer. An improved farm that
        // stopped answering to "farm" would quietly drop out of all three, and a
        // town whose granary got better would starve beside it.
        assertEquals("kingdoms:farm", BuildPlanner.baseIdOf("kingdoms:farm_l2"));
        assertTrue(BuildPlanner.baseIdOf("kingdoms:farm_l3").endsWith("farm"));
        assertTrue(BuildPlanner.baseIdOf("kingdoms:animal_farm_l2").endsWith("animal_farm"));
        assertTrue(BuildPlanner.baseIdOf("kingdoms:town_hall_l3").endsWith("town_hall"));
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
        Building house = standing("kingdoms:house", 1);
        s.addBuilding(house);
        // Hands to do the work and stock to pay for it, or the queue never moves.
        for (int i = 0; i < 4; i++) {
            s.addResident(new com.kingdoms.sim.person.Person(
                    com.kingdoms.sim.person.Person.Id.random(), "Hand " + i,
                    com.kingdoms.sim.person.Profession.BUILDER, s.centre()));
        }
        s.setStock(com.kingdoms.sim.settlement.TownStores.WOOD, 5000);
        s.setStock(com.kingdoms.sim.settlement.TownStores.STONE, 5000);
        s.setStock(com.kingdoms.sim.settlement.TownStores.FOOD, 5000);

        BuildTask work = new BuildTask("kingdoms:house_l2", house.origin(), 1);
        work.setUpgradeOf(house.origin());
        s.enqueueBuild(work);

        // The ground under it turns out to be four blocks higher than surveyed.
        house.setOriginY(house.origin().y() + 4);

        for (int step = 0; step < 40 && !s.buildQueue().isEmpty(); step++) {
            s.step(CTX);
        }

        assertEquals(1, s.buildings().size(),
                "one building, improved — not a second one stacked on the first");
        assertEquals("kingdoms:house_l2", s.buildings().getFirst().blueprintId());
        assertEquals(2, s.buildings().getFirst().level(), "and the work was not wasted");
    }
}
