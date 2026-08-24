package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What improving a building is actually worth.
 *
 * <p>Capacity used to be a count of stores, so a storehouse raised to level two
 * held exactly what it had held before. Improving one gained the town nothing,
 * and "improve the lowest level first" was an even distribution of no effect —
 * which is why the answer to "choose upgrades more intelligently" turned out to
 * be "make upgrades mean something first".
 */
class StoreStrengthTest {

    private static Settlement town() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
    }

    private static Building store(Settlement town, int x, int level) {
        Building standing = new Building("kingdoms:storehouse", new SimPos(x, 64, 0), 1, true);
        standing.setLevel(level);
        town.addBuilding(standing);
        return standing;
    }

    @Test
    void aTownWithNoStoresHasNoStoreStrength() {
        assertEquals(0, BuildPlanner.storeStrength(town()));
    }

    @Test
    void plainStoresCountOneEachJustAsTheyAlwaysDid() {
        // The migration property: nothing moves until somebody actually improves
        // something, so this cannot quietly reprice every existing town.
        Settlement s = town();
        store(s, 0, 1);
        store(s, 20, 1);
        store(s, 40, 1);

        assertEquals(3, BuildPlanner.storeStrength(s));
    }

    @Test
    void aBiggerStoreHoldsMore() {
        Settlement s = town();
        store(s, 0, 1);
        Building improved = store(s, 20, 1);

        int before = LumberPlanner.woodCapacity(s);
        improved.setLevel(2);

        assertTrue(LumberPlanner.woodCapacity(s) > before,
                "raising a storehouse a level must be worth something");
    }

    @Test
    void theCeilingRisesEvenlyWithEachLevel() {
        // Linear, so the third level is worth exactly what the second was.
        // Anything else and a town would improve one showpiece store rather
        // than several, which is the opposite of what "improve the lowest
        // first" is trying to arrange.
        Settlement s = town();
        Building only = store(s, 0, 1);

        int atOne = MinePlanner.stoneCapacity(s);
        only.setLevel(2);
        int atTwo = MinePlanner.stoneCapacity(s);
        only.setLevel(3);
        int atThree = MinePlanner.stoneCapacity(s);

        assertTrue(atTwo > atOne, "a second level is worth something");
        assertEquals(atTwo - atOne, atThree - atTwo, "and a third is worth the same again");
    }

    @Test
    void timberAndStoneRiseTogether() {
        Settlement s = town();
        Building only = store(s, 0, 1);

        int woodBefore = LumberPlanner.woodCapacity(s);
        int stoneBefore = MinePlanner.stoneCapacity(s);
        only.setLevel(2);

        assertTrue(LumberPlanner.woodCapacity(s) > woodBefore);
        assertTrue(MinePlanner.stoneCapacity(s) > stoneBefore,
                "one store holds both, so improving it must raise both");
    }

    @Test
    void aBuildingWithNoLevelRecordedStillCountsAsOne() {
        // Buildings restored from a save written before levels existed carry
        // zero, and a store that counts for nothing would silently shrink the
        // town's ceiling the moment it loaded.
        Settlement s = town();
        Building ancient = store(s, 0, 0);

        assertEquals(1, BuildPlanner.storeStrength(s));
        assertTrue(ancient.level() <= 1);
    }

    @Test
    void onlyStoresCountTowardTheStoreCeiling() {
        Settlement s = town();
        store(s, 0, 2);
        Building house = new Building("kingdoms:house", new SimPos(40, 64, 0), 1, true);
        house.setLevel(3);
        s.addBuilding(house);

        assertEquals(2, BuildPlanner.storeStrength(s),
                "a grand house is still not somewhere to put timber");
    }
}
