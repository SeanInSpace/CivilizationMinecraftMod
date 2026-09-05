package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
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

    // --- a camp goes where the trees are ---

    /** A world with a wood to the east of the origin and open grass to the west. */
    private static SimContext woodTo(int easternEdge) {
        WorldBridge bridge = new WorldBridge() {
            @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
            @Override public boolean isLoaded(SimPos pos) { return true; }
            @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
            @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                            boolean surveyed, int facing) {
                return new Footprint(origin.y(), 3, 3, 3);
            }
            @Override public int woodedness(SimPos center, int radius) {
                return center.x() >= easternEdge ? 100 : 0;
            }
            @Override public void log(String message) { }
        };
        return new SimContext(bridge, 0, SimSettings.SANDBOX);
    }

    @Test
    void aLumberCampIsDrawnToTheTrees() {
        Settlement town = town();
        SimContext ctx = woodTo(50);

        double inTheWood = town.siteCost(at(60, 0), BuildingRole.LUMBER_CAMP, ctx);
        double onTheGrass = town.siteCost(at(10, 0), BuildingRole.LUMBER_CAMP, ctx);

        assertTrue(inTheWood < onTheGrass,
                "a camp on open grass has nothing to fell, however convenient it is");
    }

    @Test
    void nothingElseCaresAboutTheTrees() {
        // Only the camp asks. A granary sited by woodedness would wander off
        // into the forest away from the fields it exists to serve.
        Settlement town = town();
        SimContext ctx = woodTo(50);

        assertEquals(town.siteCost(at(60, 0), BuildingRole.OTHER, ctx),
                town.siteCost(at(10, 0), BuildingRole.OTHER, ctx),
                "a house is indifferent to the canopy");
    }

    @Test
    void aWorldThatCannotSeeTheTreesFallsBackRatherThanGuessing() {
        // The default bridge answers zero, which reads as "no reason to prefer
        // this spot" rather than "definitely bare" — so a town with no streets
        // and no partner still takes the first plot that fits.
        Settlement town = town();

        assertEquals(-1, town.siteCost(at(0, 0), BuildingRole.LUMBER_CAMP, null));
    }
}
