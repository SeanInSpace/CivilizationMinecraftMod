package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far a spot is from the town's own streets.
 *
 * <p>Plots come from ring slots, which is why towns used to scatter toward
 * whichever index came up next rather than growing along the roads they had
 * already laid. This is the measure the siting rule weighs candidates by, and
 * it is worth pinning on its own because the rule above it is only as good as
 * this is.
 */
class SitingTest {

    private static SimPos at(int x, int z) {
        return new SimPos(x, 64, z);
    }

    private static PathNetwork road(int fromX, int fromZ, int toX, int toZ) {
        return new PathNetwork(
                List.of(new PathNetwork.Segment(at(fromX, fromZ), at(toX, toZ))),
                List.of());
    }

    @Test
    void aTownWithNoStreetsHasNothingToPrefer() {
        // Answered as -1 rather than a large number, because "no roads" is not
        // "roads a long way off": the caller falls back to taking the first fit,
        // which is what it always did.
        assertEquals(-1, new PathNetwork().distanceToRoad(at(0, 0)));
    }

    @Test
    void aSpotOnTheRoadIsOnTheRoad() {
        assertEquals(0, road(0, 0, 20, 0).distanceToRoad(at(10, 0)), 1e-9);
    }

    @Test
    void aSpotBesideTheRoadIsMeasuredAcrossToIt() {
        assertEquals(5, road(0, 0, 20, 0).distanceToRoad(at(10, 5)), 1e-9);
    }

    @Test
    void aSpotBeyondTheEndIsMeasuredToTheEnd() {
        // Segments are finite. A plot off the end of a street is as far away as
        // the end of that street, not as far as the line it lies on.
        assertTrue(road(0, 0, 20, 0).distanceToRoad(at(40, 0)) >= 20 - 1e-9);
    }

    @Test
    void theNearestOfSeveralStreetsIsTheOneThatCounts() {
        PathNetwork town = new PathNetwork(List.of(
                new PathNetwork.Segment(at(0, 0), at(0, 40)),
                new PathNetwork.Segment(at(100, 0), at(100, 40))), List.of());

        assertEquals(3, town.distanceToRoad(at(3, 20)), 1e-9,
                "the street three blocks away, not the one a hundred out");
    }

    @Test
    void aPlotOnAStreetBeatsOneOutInTheField() {
        // The comparison the siting rule actually makes.
        PathNetwork town = road(0, 0, 40, 0);

        assertTrue(town.distanceToRoad(at(20, 2)) < town.distanceToRoad(at(20, 60)),
                "a spot beside the road must score better than one in the next field");
    }

    // --- standing near the buildings you work with ---

    private static Settlement town() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
    }

    private static void farmAt(Settlement town, int x) {
        town.addBuilding(new Building("kingdoms:farm", new SimPos(x, 64, 0), 1, true));
    }

    @Test
    void aGranaryIsDrawnTowardTheFieldsItFills() {
        Settlement town = town();
        farmAt(town, 100);

        double beside = town.siteCost(at(96, 0), BuildingRole.GRANARY);
        double acrossTown = town.siteCost(at(-100, 0), BuildingRole.GRANARY);

        assertTrue(beside < acrossTown,
                "a granary among the fields must cost less than one the other side of town");
    }

    @Test
    void aBuildingWithNoPartnerHasNoOpinionAboutWhereTheFieldsAre() {
        Settlement town = town();
        farmAt(town, 100);

        assertEquals(-1, town.siteCost(at(0, 0), BuildingRole.OTHER),
                "a house does not care where the corn is, and there are no streets yet");
    }

    @Test
    void aTownWithNothingToPreferSaysSoRatherThanGuessing() {
        // -1 rather than a big number: "no roads and no partner" is a different
        // answer from "everything is far away", and the caller falls back to the
        // first plot that fits.
        assertEquals(-1, town().siteCost(at(0, 0), BuildingRole.GRANARY));
    }

    @Test
    void theNearestPartnerIsTheOneThatCounts() {
        Settlement town = town();
        farmAt(town, 200);
        farmAt(town, 20);

        double byTheNearField = town.siteCost(at(24, 0), BuildingRole.GRANARY);
        double betweenThem = town.siteCost(at(110, 0), BuildingRole.GRANARY);

        assertTrue(byTheNearField < betweenThem,
                "beside one field beats splitting the difference between two");
    }

    @Test
    void aForgeIsDrawnTowardTheOreRatherThanTheCorn() {
        Settlement town = town();
        farmAt(town, 100);
        town.addBuilding(new Building("kingdoms:mine", new SimPos(-100, 64, 0), 1, true));

        double byTheMine = town.siteCost(at(-96, 0), BuildingRole.SMITH);
        double byTheFarm = town.siteCost(at(96, 0), BuildingRole.SMITH);

        assertTrue(byTheMine < byTheFarm, "a smith wants the ore, not the wheat");
    }
}
