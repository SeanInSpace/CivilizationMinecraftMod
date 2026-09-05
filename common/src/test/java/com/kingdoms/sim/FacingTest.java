package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildPlanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Covers buildings turning to face the town. Structures are drawn with the door
 * to the south, so zero means "as drawn" and everything else is quarter turns
 * clockwise.
 */
class FacingTest {

    private static final SimPos CENTER = new SimPos(0, 64, 0);

    private static int facing(int x, int z) {
        return BuildPlanner.facingToward(new SimPos(x, 64, z), CENTER);
    }

    @Test
    void abuildingNorthOfTheCenterFacesSouthAsDrawn() {
        assertEquals(0, facing(0, -20), "center is to the south, so no turn is needed");
    }

    @Test
    void abuildingSouthOfTheCenterTurnsRightAround() {
        assertEquals(2, facing(0, 20));
    }

    @Test
    void theEastAndWestSidesTurnOppositeWays() {
        assertEquals(1, facing(20, 0), "center to the west: a quarter clockwise");
        assertEquals(3, facing(-20, 0), "center to the east: three");
        assertNotEquals(facing(20, 0), facing(-20, 0));
    }

    @Test
    void thedominantAxisDecidesOnADiagonal() {
        // Further east than north, so it faces along the east-west axis.
        assertEquals(1, facing(30, -10));
        // Further north than east, so it faces along north-south.
        assertEquals(0, facing(10, -30));
    }

    @Test
    void aringOfBuildingsDoesNotAllFaceOneWay() {
        // The whole point: four houses around a center must not be four identical
        // sheds facing the same direction.
        int north = facing(0, -20);
        int south = facing(0, 20);
        int east = facing(20, 0);
        int west = facing(-20, 0);
        assertEquals(4, java.util.Set.of(north, south, east, west).size(),
                "each side of the town faces its own way");
    }

    @Test
    void abuildingOnTheCenterHasSomethingSaneToDo() {
        assertEquals(0, facing(0, 0), "no direction to face, so it stays as drawn");
    }
}
