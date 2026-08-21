package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers a town improving what it has, once it has everything it wants. */
class UpgradeTest {

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
}
