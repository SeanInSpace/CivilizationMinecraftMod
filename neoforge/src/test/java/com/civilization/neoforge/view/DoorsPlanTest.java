package com.civilization.neoforge.view;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the town looks for a door, without a world to look in.
 *
 * <p>The whole of {@link Doors}'s reading is here, because the whole of it is
 * arithmetic on a building's plan: the doorstep the simulation already knows,
 * and the step from it back into the house. Everything else that class does is
 * block states against a level and cannot be checked here.
 *
 * <p>The test that earns its keep is {@link #everyFacingLooksIntoItsOwnHouse}.
 * The bug this kind of code has is always the same one — a facing convention
 * read the wrong way round — and it fails silently: a sweep that probed
 * <em>outward</em> from the doorstep would find nothing, report every house
 * shut, and be indistinguishable from a village whose doors were all closed
 * already. So the assertion is not about coordinates but about direction: the
 * cells have to walk toward the middle of the building, whichever way it faces.
 */
class DoorsPlanTest {

    /** A cottage seven deep, placed at the origin, facing whichever way. */
    private static Building cottage(int facing) {
        Building building = new Building("cottage", new SimPos(0, 64, 0), 0L, true);
        building.setFootprint(new Footprint(64, 7, 7, 5));
        building.setFacing(facing);
        return building;
    }

    @Test
    void theFirstCellIsTheDoorstepItself() {
        Building house = cottage(0);
        assertEquals(house.doorstep(), Doors.leavesOf(house).getFirst(),
                "the sweep starts where the simulation says somebody stands to walk in");
    }

    @Test
    void thereAreThreeCellsAndNoMore() {
        for (int facing = 0; facing < 4; facing++) {
            assertEquals(Doors.LEAF_REACH + 1, Doors.leavesOf(cottage(facing)).size(),
                    "facing " + facing);
        }
    }

    /**
     * The one that matters. Each step has to be nearer the building's origin
     * than the last; a reading that walked the other way would search the street.
     */
    @Test
    void everyFacingLooksIntoItsOwnHouse() {
        for (int facing = 0; facing < 4; facing++) {
            Building house = cottage(facing);
            List<SimPos> cells = Doors.leavesOf(house);
            double last = Double.MAX_VALUE;
            for (SimPos cell : cells) {
                double away = cell.horizontalDistance(house.origin());
                assertTrue(away < last,
                        "facing " + facing + ": " + cell + " is no nearer the house than "
                                + "the cell before it");
                last = away;
            }
        }
    }

    /**
     * A row of cells, not a box. Three block reads a door is the reason this is
     * read off the plan at all rather than scanned for.
     */
    @Test
    void theCellsRunInAStraightLine() {
        for (int facing = 0; facing < 4; facing++) {
            List<SimPos> cells = Doors.leavesOf(cottage(facing));
            SimPos first = cells.get(0);
            SimPos last = cells.get(cells.size() - 1);
            assertTrue(first.x() == last.x() || first.z() == last.z(),
                    "facing " + facing + " walks diagonally");
            assertEquals(first.y(), last.y(), "facing " + facing + " changes storey");
        }
    }

    /**
     * An authored building's door is wherever its author cut it, and the step
     * inward is still the step inward: the recorded offset has already had the
     * building's turn applied to it, so the axis is the same fact for both kinds.
     */
    @Test
    void anAuthoredDoorstepIsReadOffTheFileAndStillWalksInward() {
        Building hall = new Building("hall", new SimPos(100, 70, -40), 0L, true);
        hall.setFootprint(new Footprint(70, 11, 9, 7));
        hall.setFacing(2);
        // Off-centre, which is exactly what an authored door can be and a drawn
        // one cannot: two along the wall from the middle.
        hall.setAuthored(new Building.Authored(List.of(), List.of(),
                new SimPos(2, 0, -5), Building.UNCOUNTED));
        List<SimPos> cells = Doors.leavesOf(hall);
        assertEquals(new SimPos(102, 70, -45), cells.getFirst(),
                "the doorstep is the file's, offset from the origin");
        assertEquals(new SimPos(102, 70, -44), cells.get(1));
        assertEquals(new SimPos(102, 70, -43), cells.get(2));
    }

    /** Facing is taken modulo four wherever it comes from, as the building's is. */
    @Test
    void theInwardStepIsTheOppositeOfTheDoorstepsOwn() {
        assertArrayEqualsish(new int[] {0, -1}, Doors.inward(0));
        assertArrayEqualsish(new int[] {1, 0}, Doors.inward(1));
        assertArrayEqualsish(new int[] {0, 1}, Doors.inward(2));
        assertArrayEqualsish(new int[] {-1, 0}, Doors.inward(3));
        assertArrayEqualsish(Doors.inward(0), Doors.inward(4));
        assertArrayEqualsish(Doors.inward(3), Doors.inward(-1));
    }

    private static void assertArrayEqualsish(int[] want, int[] got) {
        assertEquals(want[0], got[0]);
        assertEquals(want[1], got[1]);
    }
}
