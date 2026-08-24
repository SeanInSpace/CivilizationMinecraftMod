package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The auditor's geometry, asked about worlds built by hand.
 *
 * <p>These checks were the whole reason for the {@link WorldView} seam. They
 * are the ones that report faults in play, and until now the only way to run
 * one was to found a town, grow it, and read a log — so "four buildings with no
 * way in" could be argued about but not interrogated. Each case here is a claim
 * the doorway check's own javadoc makes, now actually asked.
 */
class TownAuditorGeometryTest {

    private static final int FLOOR = 64;

    /** Span 7 gives wall half-extents of 2, so the ring sits at x,z = +/-2. */
    private static final int SPAN = 7;
    private static final int HALF = 2;

    private static Settlement townWith(Building building) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, FLOOR, 0), 64);
        town.addBuilding(building);
        return town;
    }

    private static Building house() {
        Building house = new Building("kingdoms:house", new SimPos(0, FLOOR, 0), 1, true);
        house.setFootprint(new Footprint(FLOOR, SPAN, SPAN, 4));
        return house;
    }

    /** A walled house on a flat plain, with the ground reading level with the floor. */
    private static FakeWorld sealedHouse() {
        return new FakeWorld(FLOOR + 1)
                .plain(FLOOR, 12)
                .walls(HALF, FLOOR);
    }

    private static List<String> faultsOf(FakeWorld world) {
        return TownAuditor.audit(world, townWith(house())).stream()
                .map(TownAuditor.Fault::describe)
                .toList();
    }

    private static boolean reportsNoWayIn(FakeWorld world) {
        return faultsOf(world).stream().anyMatch(f -> f.contains("no way in"));
    }

    @Test
    void aHouseWalledAllRoundHasNoWayIn() {
        assertTrue(reportsNoWayIn(sealedHouse()),
                "no gap on any side is the fault this check exists for");
    }

    @Test
    void aDoorwayOntoLevelGroundIsAWayIn() {
        assertFalse(reportsNoWayIn(sealedHouse().doorway(HALF, 0, FLOOR)),
                "a two-high gap with ground outside it is a door");
    }

    @Test
    void oneBlockOfGapIsNotADoorway() {
        // Head height matters: a hole at knee level is a window.
        FakeWorld world = sealedHouse();
        world.empty(HALF, FLOOR + 1, 0);

        assertTrue(reportsNoWayIn(world),
                "a person needs two blocks; one is a window, not a door");
    }

    @Test
    void aDoorOntoTheTownsOwnPathIsAWayIn() {
        // The false positive a player caught by walking through a door the
        // auditor had just called impassable. A dirt path is fifteen-sixteenths
        // of a block and fails a sturdy-face test, so demanding one reported the
        // best-connected houses in town — the ones with a track to the door — as
        // unenterable. Anything with collision counts, which is what a path has.
        assertFalse(reportsNoWayIn(sealedHouse().doorway(HALF, 0, FLOOR)),
                "standable means standable, not sturdy");
    }

    @Test
    void aDoorAtTheHeadOfItsOwnStairIsAWayIn() {
        // The top tread sits one below the floor.
        FakeWorld world = sealedHouse().doorway(HALF, 0, FLOOR);
        world.empty(HALF + 1, FLOOR, 0);
        world.solid(HALF + 1, FLOOR - 1, 0);

        assertFalse(reportsNoWayIn(world), "one step down is still a way in");
    }

    @Test
    void aDoorOntoAShelfOneBlockProudIsAWayIn() {
        FakeWorld world = sealedHouse().doorway(HALF, 0, FLOOR);
        world.solid(HALF + 1, FLOOR + 1, 0);

        assertFalse(reportsNoWayIn(world), "one hop up is still a way in");
    }

    @Test
    void aDoorOpeningOntoAPitIsNotAWayIn() {
        // The genuine version of the fault: a doorway with nothing to step onto
        // within a stride either way.
        FakeWorld world = sealedHouse().doorway(HALF, 0, FLOOR);
        for (int y = FLOOR - 1; y <= FLOOR + 1; y++) {
            world.empty(HALF + 1, y, 0);
        }

        assertTrue(reportsNoWayIn(world),
                "a door onto a drop is a door nobody can use");
    }

    @Test
    void aDoorBuriedByTheTerrainOutsideIsNotAWayIn() {
        // Ground to stand on, but no headroom above it: the hillside the
        // building was cut into has closed over the doorstep.
        FakeWorld world = sealedHouse().doorway(HALF, 0, FLOOR);
        world.solid(HALF + 1, FLOOR + 1, 0);
        world.solid(HALF + 1, FLOOR + 2, 0);
        world.solid(HALF + 1, FLOOR + 3, 0);

        assertTrue(reportsNoWayIn(world),
                "somewhere to put your feet is not enough if there is nowhere to put your head");
    }

    @Test
    void aFenceGateIsAWayInEvenKeptShut() {
        // Pens are gated on purpose. A shut gate is an intended way in, and the
        // check must not report the animal farm every time the gate swings to.
        FakeWorld world = sealedHouse().gate(HALF, FLOOR + 1, 0);

        assertFalse(reportsNoWayIn(world), "a gate is a door somebody chose to close");
    }

    @Test
    void aDoorwayOnAnySideCounts() {
        // All four, because stepping the ring by two once walked straight past
        // the door on two sides and reported half a town as unenterable.
        assertFalse(reportsNoWayIn(sealedHouse().doorway(HALF, 0, FLOOR)), "east");
        assertFalse(reportsNoWayIn(sealedHouse().doorway(-HALF, 0, FLOOR)), "west");
        assertFalse(reportsNoWayIn(sealedHouse().doorway(0, HALF, FLOOR)), "south");
        assertFalse(reportsNoWayIn(sealedHouse().doorway(0, -HALF, FLOOR)), "north");
    }

    @Test
    void aBuildingTheWorldNeverDrewIsReported() {
        Building unbuilt = new Building("kingdoms:house", new SimPos(0, FLOOR, 0), 1, false);
        List<String> faults = TownAuditor.audit(sealedHouse(), townWith(unbuilt)).stream()
                .map(TownAuditor.Fault::describe)
                .toList();

        assertTrue(faults.stream().anyMatch(f -> f.contains("nothing stands on the ground")),
                "the simulation says it is there and the world disagrees");
    }

    @Test
    void standingFluidInTheRoomsIsReportedAndCounted() {
        FakeWorld world = sealedHouse().doorway(HALF, 0, FLOOR)
                .fluid(0, FLOOR + 1, 0)
                .fluid(1, FLOOR + 1, 0);

        assertTrue(faultsOf(world).stream()
                        .anyMatch(f -> f.contains("standing fluid inside it (2 blocks)")),
                "a flooded room, and how much of it — the count is what made the "
                        + "difference between a puddle and a building in a lake");
    }

    @Test
    void adryHouseIsNotReportedAsFlooded() {
        assertFalse(faultsOf(sealedHouse().doorway(HALF, 0, FLOOR)).stream()
                        .anyMatch(f -> f.contains("standing fluid")),
                "an audit that cries wolf is an audit nobody reads");
    }
}
