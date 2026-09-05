package com.kingdoms.neoforge.view;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Getting the crew down off the roof they just finished.
 *
 * <p>There are two ways to be stuck aloft and only one of them was ever noticed.
 * Over a hole, the first solid thing is a long way below and the straight-down
 * search finds it. On a roof it is directly underfoot: the drop reads nought
 * against the three courses that count as stranded, so the old test called it a
 * curb and threw the case away — and the builder who had laid the last course of
 * a cottage stood on it until something else happened to them.
 *
 * <p>So the search now also goes sideways, and this is that part of it. Geometry
 * and an ordering, with what a square must be like arriving as a predicate, so
 * a roof here is a set of the squares a body fits in and nothing has to be walked
 * to. The same seam {@code Excavation.standCandidates} cuts, for the same reason.
 *
 * <p>The other half is here too: deciding that somebody is on a roof at all. That
 * question is asked of the town's books rather than of the blocks — a roof is
 * solid ground and the world's own heightmap counts it as the surface — so it too
 * needs no world, only a settlement with buildings recorded in it.
 */
class StrandedLedgeTest {

    /** Standing on the ridge of a cottage, four courses over the grass. */
    private static final BlockPos PERCH = new BlockPos(0, 68, 0);

    /** A world where a body fits in exactly these squares and nowhere else. */
    private static Set<BlockPos> nowhere() {
        return new HashSet<>();
    }

    private static BlockPos ledgeIn(Set<BlockPos> footing) {
        return PersonEntityManager.ledgeNear(PERCH, footing::contains);
    }

    @Test
    void aRoofWithNothingWithinReachOffersNoWayDown() {
        // The fork this exists to name. Nowhere to step to and the grass four
        // courses below, so the honest answer is "none" — and the caller's answer
        // to none is a flight of steps, not a shove off the edge.
        assertNull(ledgeIn(nowhere()),
                "a settler on a roof with clear air round it has nowhere to go");
    }

    @Test
    void anEavesCourseOneStepDownIsFound() {
        Set<BlockPos> footing = nowhere();
        BlockPos eaves = PERCH.offset(2, -1, 0);
        footing.add(eaves);

        assertEquals(eaves, ledgeIn(footing), "the lean-to roof beside the ridge");
    }

    @Test
    void theNearestOneWinsBeforeTheLowestOne() {
        // A settler goes to the edge they are standing beside rather than across
        // the whole roof to the far one. Sorting on the drop first would send
        // somebody three blocks sideways to save two courses of climbing — and a
        // walk like that is exactly what outlasts the patience that ordered it.
        Set<BlockPos> footing = nowhere();
        BlockPos beside = PERCH.offset(1, -3, 0);
        BlockPos across = PERCH.offset(3, -1, 0);
        footing.add(beside);
        footing.add(across);

        assertEquals(beside, ledgeIn(footing));
    }

    @Test
    void theShallowerStepWinsAmongSquaresEquallyNear() {
        Set<BlockPos> footing = nowhere();
        BlockPos shallow = PERCH.offset(0, -1, 2);
        BlockPos deep = PERCH.offset(2, -3, 0);
        footing.add(shallow);
        footing.add(deep);

        assertEquals(shallow, ledgeIn(footing),
                "both are two squares out; the one that is one course down is the"
                        + " step and the other is a fall");
    }

    @Test
    void nothingAboveOrLevelWithThePerchIsOfferedAsAWayDown() {
        // Down is the whole point. A square level with the feet is somewhere the
        // settler's own navigation has already declined to walk to, and a square
        // above it is a climb rather than a way off.
        Set<BlockPos> footing = nowhere();
        footing.add(PERCH.offset(1, 0, 0));
        footing.add(PERCH.offset(1, 1, 0));
        footing.add(PERCH.offset(0, 2, 1));

        assertNull(ledgeIn(footing), "none of those is a way down");
    }

    @Test
    void theRoomUnderTheRoofIsNotAWayDown() {
        // Their own column is straight down through whatever they are standing on,
        // which is the question the plain descent has already asked and answered.
        // Offered here it scores nothing for distance and beats every real ledge —
        // and what it finds under a roof is the room inside, with no route to it,
        // so the walk fails and the settler is dropped through their own ceiling
        // into a sealed loft.
        Set<BlockPos> footing = nowhere();
        footing.add(PERCH.offset(0, -2, 0));
        footing.add(PERCH.offset(0, -3, 0));

        assertNull(ledgeIn(footing), "the inside of the house is not a ledge");
    }

    @Test
    void arealLedgeIsTakenOverTheRoomUnderfoot() {
        Set<BlockPos> footing = nowhere();
        BlockPos loft = PERCH.offset(0, -2, 0);
        BlockPos eaves = PERCH.offset(3, -2, 0);
        footing.add(loft);
        footing.add(eaves);

        assertEquals(eaves, ledgeIn(footing),
                "three squares out and on the outside of the wall beats nought"
                        + " squares out and on the wrong side of the ceiling");
    }

    @Test
    void theGroundFourCoursesBelowIsOutOfReach() {
        // The line between a step and a fall, stated. Anything past three courses
        // is what the flight of steps is for; reaching further here would be the
        // teleport the whole mechanism exists to avoid.
        Set<BlockPos> footing = nowhere();
        footing.add(PERCH.offset(1, -4, 0));
        footing.add(PERCH.offset(0, -5, 0));

        assertNull(ledgeIn(footing));
    }

    @Test
    void nothingFurtherOutThanThreeSquaresCounts() {
        Set<BlockPos> footing = nowhere();
        footing.add(PERCH.offset(4, -1, 0));
        footing.add(PERCH.offset(0, -1, 4));

        assertNull(ledgeIn(footing), "that is a walk, and they would have walked it");
    }

    // --- and whose roof it is ---

    /**
     * A cottage as the town records one: seven of wall with the doorstep ring
     * counted in, which is what {@code BlueprintPlacer.plotOf} writes down.
     */
    private static Building cottageAt(int x, int z) {
        Building cottage = new Building("kingdoms:cottage", new SimPos(x, 64, z), 1, true);
        cottage.setFootprint(new Footprint(64, 7 + 2 * BuildingSizes.APRON,
                7 + 2 * BuildingSizes.APRON, 4));
        return cottage;
    }

    private static Settlement townOf(Building... standing) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Roofton", new SimPos(0, 64, 0), 64);
        for (Building building : standing) {
            town.addBuilding(building);
        }
        return town;
    }

    @Test
    void somebodyOnTheGrassBesideAHouseIsNotOnAnything() {
        Settlement town = townOf(cottageAt(0, 0));

        assertNull(PersonEntityManager.standingOn(town, new BlockPos(0, 65, 0)),
                "standing on the floor is not standing on the roof");
        assertNull(PersonEntityManager.standingOn(town, new BlockPos(20, 65, 20)));
    }

    @Test
    void somebodyOnTheLastCourseIsOnTheRoof() {
        Building cottage = cottageAt(0, 0);
        Settlement town = townOf(cottage);

        assertEquals(cottage, PersonEntityManager.standingOn(town, new BlockPos(1, 68, 1)),
                "four courses up and over the walls");
    }

    @Test
    void anUpperFloorIndoorsIsNotARoof() {
        // Being three courses over the floor is also true of a watchtower's
        // platform and of anybody's loft, and being indoors is not being
        // stranded. Snapping them to a lower square would drop them through their
        // own ceiling; ordering a flight of steps would be stranger still.
        Settlement town = townOf(cottageAt(0, 0));

        assertNull(PersonEntityManager.standingOn(town, new BlockPos(0, 67, 0)),
                "a course below the roof is inside the house");
    }

    @Test
    void aRoofIsNeverConfusedWithTheNeighborsDoorstep() {
        // Recorded footprints are the walls plus the doorstep ring, and two
        // neighbors' rings are allowed to meet — so the plots overlap where the
        // buildings do not. Asked of the plot, a builder on one roof standing
        // inside the next cottage's apron is attributed to the neighbor, and the
        // town orders a flight of steps onto a roof he is not on.
        // Walls seven across, so west holds x in [-3, 3] and east, packed against
        // it, holds [4, 10]. Their recorded plots are [-4, 4] and [3, 11], which
        // overlap on the two columns between them. West is listed first, so a
        // plot-shaped question answers "west" for both of them.
        Building west = cottageAt(0, 0);
        Building east = cottageAt(7, 0);
        Settlement town = townOf(west, east);

        assertEquals(west, PersonEntityManager.standingOn(town, new BlockPos(3, 68, 0)),
                "west's outermost wall, which is also east's doorstep");
        assertEquals(east, PersonEntityManager.standingOn(town, new BlockPos(4, 68, 0)),
                "and east's outermost wall, which is also west's doorstep");
    }
}
