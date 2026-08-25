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
    void aDoorwayWithATreeInItIsNoWayIn() {
        // What every "no way in" report in a live audit turned out to be. The
        // doorway is cut, the doorstep is laid, there is solid ground to stand
        // on — and the block where a person's head goes is oak leaves. Leaves
        // have full collision, so this is a door you cannot walk through, and
        // the auditor was right about it for as long as it has been complaining.
        FakeWorld blocked = sealedHouse().doorway(HALF, 0, FLOOR);
        // Head height, one block out from the doorway, over standable ground.
        blocked.solid(HALF + 1, FLOOR + 2, 0);

        assertTrue(reportsNoWayIn(blocked),
                "a person cannot walk through a tree, whatever the ground is doing");
    }

    @Test
    void theSameDoorwayIsFineOnceTheTreeIsGone() {
        // The other half of the claim, so the test above cannot pass by the
        // check simply being broken.
        assertFalse(reportsNoWayIn(sealedHouse().doorway(HALF, 0, FLOOR)),
                "clear the head height and it is a door again");
    }

    @Test
    void aTrunkStandingInTheDoorwayIsNoWayIn() {
        // A whole trunk, not one block: a single block outside the door at
        // chest height is a step UP, which the check tolerates on purpose for
        // doors at the head of their own stair flight. A tree fills the column.
        FakeWorld blocked = sealedHouse().doorway(HALF, 0, FLOOR);
        blocked.solid(HALF + 1, FLOOR + 1, 0);
        blocked.solid(HALF + 1, FLOOR + 2, 0);
        blocked.solid(HALF + 1, FLOOR + 3, 0);

        assertTrue(reportsNoWayIn(blocked), "a trunk in the doorway is not a doorway");
    }

    @Test
    void aSingleBlockOutsideTheDoorIsAStepAndNotAnObstruction() {
        // The tolerance the case above had to work around, stated so nobody
        // later reads it as a bug: a door at the top of its own stair flight
        // has exactly this shape.
        FakeWorld stepped = sealedHouse().doorway(HALF, 0, FLOOR);
        stepped.solid(HALF + 1, FLOOR + 1, 0);

        assertFalse(reportsNoWayIn(stepped), "you can step up into a doorway");
    }

    @Test
    void aRefusedDoorwayNamesWhatWasInTheWay() {
        // The report is the instrument. "No way in" on its own was argued about
        // for as long as it was reported and never once settled, because it said
        // what the auditor concluded and nothing about what it saw.
        FakeWorld blocked = sealedHouse().doorway(HALF, 0, FLOOR);
        blocked.solid(HALF + 1, FLOOR + 2, 0);

        String fault = faultsOf(blocked).stream()
                .filter(f -> f.contains("no way in"))
                .findFirst()
                .orElseThrow();

        assertTrue(fault.contains("gap(s)"), "how many ways in it found: " + fault);
        assertTrue(fault.contains("head hits") || fault.contains("blocked by"),
                "and what stopped the one it found: " + fault);
    }

    // --- a building with nothing drawn: caught mid-step, or genuinely stuck ---

    /** Recorded by the simulation, with no blocks laid for it yet. */
    private static Building undrawn() {
        Building pending = new Building("kingdoms:house", new SimPos(0, FLOOR, 0), 1, false);
        pending.setFootprint(new Footprint(FLOOR, SPAN, SPAN, 4));
        return pending;
    }

    private static List<String> sweep(FakeWorld world, Building building) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, FLOOR, 0), 64);
        town.addBuilding(building);
        return TownAuditor.audit(world, town).stream()
                .map(TownAuditor.Fault::describe)
                .toList();
    }

    private static boolean reportsNothingStanding(List<String> faults) {
        return faults.stream().anyMatch(f -> f.contains("nothing stands on the ground"));
    }

    @Test
    void aBuildingCaughtBetweenBeingRecordedAndBeingDrawnIsNotAFault() {
        // The state every building finished out of sight passes through. The
        // record exists, the chunk is loaded, and the next settlement step will
        // draw it. Complaining here reported 61 of these in one seven-minute
        // run, every one of which was drawn thirty seconds later.
        TownAuditor.forget();

        assertFalse(reportsNothingStanding(sweep(sealedHouse(), undrawn())),
                "one sweep is not evidence; it has had no chance to be drawn yet");
    }

    @Test
    void aBuildingStillNotDrawnNextSweepIsAFault() {
        // The genuine article, reproduced with a cause I control: the same
        // building, never materialized, looked at twice. Anything actually
        // stuck stays stuck, and this is what it looks like.
        TownAuditor.forget();
        Building stuck = undrawn();

        sweep(sealedHouse(), stuck);
        List<String> second = sweep(sealedHouse(), stuck);

        assertTrue(reportsNothingStanding(second),
                "twice running is a building that is not going to be drawn");
    }

    @Test
    void aBuildingDrawnBetweenSweepsIsNeverReported() {
        // The race, run the way it actually goes.
        TownAuditor.forget();
        Building pending = undrawn();

        sweep(sealedHouse(), pending);
        pending.setMaterialized(true);
        List<String> second = sweep(sealedHouse(), pending);

        assertFalse(reportsNothingStanding(second),
                "it was drawn, which is what was supposed to happen");
    }

    @Test
    void theTwoSweepCountRestartsWhenNobodyIsLookingAtIt() {
        // A building nobody has walked past for an hour has not been stuck for
        // an hour -- there was no sweep to observe it. Unloaded ground must not
        // accumulate evidence.
        TownAuditor.forget();
        Building pending = undrawn();
        sweep(sealedHouse(), pending);

        FakeWorld away = sealedHouse();
        away.unloaded(0, FLOOR, 0);
        sweep(away, pending);

        assertFalse(reportsNothingStanding(sweep(sealedHouse(), pending)),
                "the count starts again from the first sweep that could see it");
    }

    // --- fields, and the difference between bare and frozen ---

    private static Building farm() {
        Building farm = new Building("kingdoms:farm", new SimPos(0, FLOOR, 0), 1, true);
        farm.setFootprint(new Footprint(FLOOR, SPAN, SPAN, 4));
        return farm;
    }

    /** A field of twenty-five tilled cells with only four things growing in it. */
    private static FakeWorld halfBareField() {
        FakeWorld world = new FakeWorld(FLOOR + 1).plain(FLOOR, 12);
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                world.farmland(dx, FLOOR - 1, dz);
            }
        }
        world.crop(-HALF, FLOOR, -HALF).crop(-HALF, FLOOR, HALF)
             .crop(HALF, FLOOR, -HALF).crop(HALF, FLOOR, HALF);
        return world;
    }

    private static List<String> fieldFaults(FakeWorld world) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, FLOOR, 0), 64);
        town.addBuilding(farm());
        return TownAuditor.audit(world, town).stream()
                .map(TownAuditor.Fault::describe)
                .toList();
    }

    @Test
    void aBareFieldOnLivingGroundIsStillReported() {
        // The check must keep working where it can actually mean something.
        assertTrue(fieldFaults(halfBareField()).stream()
                        .anyMatch(fault -> fault.contains("half the field is bare")),
                "twenty-one tilled cells with nothing in them is a real complaint");
    }

    @Test
    void aFieldOnGroundThatIsNotRunningIsNotAccused() {
        // Loaded but not ticking: crops do not grow, farmers are not asked to
        // work, and dropped items never despawn. The field is frozen exactly as
        // the last person left it. Reporting that as "something is destroying
        // the crops" is an accusation with no evidence behind it — and it is
        // what a live audit was doing, reporting the identical "72 farmland, 35
        // planted" sixteen times in seven minutes while nothing whatsoever
        // happened on that ground.
        FakeWorld frozen = halfBareField();
        frozen.ticking = false;

        assertTrue(fieldFaults(frozen).stream()
                        .noneMatch(fault -> fault.contains("half the field is bare")),
                "not knowing is not a fault; the auditor says nothing instead");
    }

    @Test
    void frozenGroundStillHasItsGeometryJudged() {
        // Only claims about processes are withdrawn. A wall is a wall whether or
        // not the chunk is ticking, and a building with no door still has no door.
        FakeWorld frozen = sealedHouse();
        frozen.ticking = false;

        assertTrue(reportsNoWayIn(frozen),
                "geometry does not move, so it can always be judged");
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
        // Still reported — but on the second sweep, not the first. This test
        // used to look once and complain, which is what produced 61 false
        // alarms in a seven-minute run: every building finished out of sight is
        // a record with nothing drawn until the next settlement step reaches
        // it, and the auditor kept catching them mid-stride. The claim it was
        // always making, and now actually makes, is that a building the world
        // never draws is reported.
        TownAuditor.forget();
        Building unbuilt = new Building("kingdoms:house", new SimPos(0, FLOOR, 0), 1, false);

        TownAuditor.audit(sealedHouse(), townWith(unbuilt));
        List<String> faults = TownAuditor.audit(sealedHouse(), townWith(unbuilt)).stream()
                .map(TownAuditor.Fault::describe)
                .toList();

        assertTrue(faults.stream().anyMatch(f -> f.contains("nothing stands on the ground")),
                "the simulation says it is there and the world still disagrees");
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
