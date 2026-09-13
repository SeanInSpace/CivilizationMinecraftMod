package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.WorkArea;
import com.civilization.sim.work.InteriorClearing;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wood a town takes down inside its own streets.
 *
 * <p>Two invariants carry the weight, and both of them are about what must
 * <em>not</em> be felled. The forester's belt is the town's timber supply, and a
 * clearing that took it would be a town that cleared its market square and then
 * could not build anything. A plot is somebody's house.
 */
class InteriorClearingTest {

    private static class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX =
            new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    /** A ring road of four opened sides, which is what the measured town had. */
    private static Settlement ringTown() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Millbrook", new SimPos(0, 64, 0), 96);
        PathNetwork paths = new PathNetwork();
        List<SimPos> corners = List.of(
                new SimPos(-40, 64, -40), new SimPos(40, 64, -40),
                new SimPos(40, 64, 40), new SimPos(-40, 64, 40));
        for (int i = 0; i < corners.size(); i++) {
            paths.add(new PathNetwork.Segment(
                    corners.get(i), corners.get((i + 1) % corners.size()), 8));
        }
        for (int i = 0; i < paths.segments().size(); i++) {
            paths.markOpened(i);
        }
        town.setPaths(paths);
        town.addResident(new Person(
                Person.Id.random(), "Woodcutter", Profession.BUILDER, town.center()));
        return town;
    }

    @Test
    void theWoodInsideTheRingRoadsIsTheWorkToBeDone() {
        Settlement town = ringTown();
        List<SimPos> cells = InteriorClearing.cells(town);
        assertFalse(cells.isEmpty(),
                "the forest inside the ring roads stood untouched, which is what the "
                        + "measurement said and what this exists to fix");
        for (SimPos cell : cells) {
            assertTrue(Math.abs(cell.x()) <= 40 + InteriorClearing.CELL
                            && Math.abs(cell.z()) <= 40 + InteriorClearing.CELL,
                    "a cell at " + cell + " is outside the ring, and the wood out "
                            + "there is the wood the village stands in");
        }
    }

    @Test
    void nothingIsClearedBeforeAnyStreetIsOpened() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Camp", new SimPos(0, 64, 0), 96);
        assertTrue(InteriorClearing.cells(town).isEmpty());
        assertTrue(InteriorClearing.outline(town).isEmpty());
        assertNull(InteriorClearing.next(town));
        assertTrue(InteriorClearing.isClear(town), "no streets, nothing enclosed");
    }

    @Test
    void theForestersBeltIsSpared() {
        Settlement town = ringTown();
        // A belt sited where it should never be: inside the claim and inside the
        // ring. The point of the assertion is that the clearing spares it anyway.
        town.setLumberArea(new WorkArea(new SimPos(20, 64, 20), 16));

        assertTrue(InteriorClearing.sparesTheBelt(town),
                "a town that felled its own stand would clear its market square and "
                        + "then have no timber to build anything with");
        for (SimPos cell : InteriorClearing.cells(town)) {
            assertTrue(cell.horizontalDistance(new SimPos(20, cell.y(), 20))
                            > 16,
                    "a cell at " + cell + " is in the belt");
        }
    }

    @Test
    void everyPlotIsSpared() {
        Settlement town = ringTown();
        Building hall = new Building("civilization:hall", new SimPos(0, 64, 0), 1, true);
        hall.setFootprint(new Footprint(64, 15, 15, 6));
        town.addBuilding(hall);
        Building house = new Building("civilization:house", new SimPos(24, 64, -16), 1, true);
        house.setFootprint(new Footprint(64, 9, 9, 4));
        town.addBuilding(house);

        assertTrue(InteriorClearing.sparesThePlots(town),
                "a crew sent to a cell that is somebody's front room would fell a wall");
        for (SimPos cell : InteriorClearing.cells(town)) {
            for (Building building : List.of(hall, house)) {
                assertFalse(building.footprint().covers(building.origin().x(),
                                building.origin().z(), cell.x(), cell.z()),
                        "a cell at " + cell + " is inside " + building.blueprintId());
            }
        }
    }

    @Test
    void cellsAreClearedFromTheMiddleOutward() {
        Settlement town = ringTown();
        long previous = -1;
        for (SimPos cell : InteriorClearing.cells(town)) {
            long distance = cell.horizontalDistanceSq(town.center());
            assertTrue(distance >= previous,
                    "the order has to grow at its end, or the saved count means a "
                            + "different cell after the town grows");
            previous = distance;
        }
    }

    @Test
    void growingTheTownNeverLosesACell() {
        Settlement town = ringTown();
        List<SimPos> before = InteriorClearing.cells(town);

        PathNetwork paths = town.paths();
        paths.add(new PathNetwork.Segment(
                new SimPos(-40, 64, 60), new SimPos(40, 64, 60), 8));
        paths.markOpened(paths.segments().size() - 1);

        List<SimPos> after = InteriorClearing.cells(town);
        assertTrue(after.size() > before.size(), "a wider town encloses more ground");
        assertTrue(after.containsAll(before),
                "every cell the town already knew about is still in the list; losing "
                        + "one would leave a patch of wood nothing ever felled");
    }

    @Test
    void aLopsidedGrowthDoesShiftTheIndexes() {
        // Stated rather than wished away. A band added to one side of the hull is
        // nearer the middle than the far corners of the old hull, so a
        // center-outward order inserts rather than appends and the cleared count
        // stops naming the same cells. What catches it is Woodcut's drawing sweep,
        // which walks the whole cleared prefix continuously and fells whatever it
        // finds standing in it -- so an inserted cell is cleared on the sweep that
        // reaches it rather than being lost. The alternative was persisting a set of
        // cell coordinates instead of one number, which is a save-format cost for a
        // work whose stations are free and idempotent.
        Settlement town = ringTown();
        List<SimPos> before = InteriorClearing.cells(town);

        PathNetwork paths = town.paths();
        paths.add(new PathNetwork.Segment(
                new SimPos(-40, 64, 60), new SimPos(40, 64, 60), 8));
        paths.markOpened(paths.segments().size() - 1);

        List<SimPos> after = InteriorClearing.cells(town);
        int firstMoved = -1;
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).equals(after.get(i))) {
                firstMoved = i;
                break;
            }
        }
        assertTrue(firstMoved > 0,
                "the near cells at least have to keep their places, or a town would "
                        + "re-clear its own market square every time it opened a lane");
    }

    // --- the two fidelities ---

    @Test
    void theClockClearsAtItsCappedPace() {
        Settlement town = ringTown();
        InteriorClearing.advance(town, CTX);
        assertEquals(InteriorClearing.CELLS_PER_STEP, town.interiorCleared(),
                "a cell a step is a town clearing its ground over a season rather "
                        + "than a village that flattens a forest between two glances");
    }

    @Test
    void theClockStandsAsideForAPlayerWhoCanSeeTheCell() {
        Settlement town = ringTown();
        SimContext watched = new SimContext(new QuietBridge() {
            @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        }, 0, SimSettings.SANDBOX);

        InteriorClearing.advance(town, watched);

        assertEquals(0, town.interiorCleared(),
                "a wood that fells itself in front of somebody is the one thing this "
                        + "must never look like");
    }

    @Test
    void aDeadTownClearsNothing() {
        Settlement town = ringTown();
        for (Person person : List.copyOf(town.residents())) {
            town.removeResident(person.id());
        }
        InteriorClearing.advance(town, CTX);
        assertEquals(0, town.interiorCleared(), "nobody is left to hold the axe");
    }

    @Test
    void theWorkFinishesWhenTheGroundIsOpen() {
        Settlement town = ringTown();
        assertNotNull(InteriorClearing.next(town));
        town.setInteriorCleared(InteriorClearing.cells(town).size());
        assertNull(InteriorClearing.next(town));
        assertTrue(InteriorClearing.isClear(town));
    }
}
