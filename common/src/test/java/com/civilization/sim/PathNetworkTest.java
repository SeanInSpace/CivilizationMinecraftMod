package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.ForesterStand;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.PathPlanner;
import com.civilization.sim.settlement.Perimeter;
import com.civilization.sim.settlement.PerimeterPlanner;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remembered road network: doors that are actually where the door is,
 * right angles, and roads that branch off the nearest way rather than driving
 * one more spoke into the middle of town.
 */
class PathNetworkTest {

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(
                String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX = new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    private static Settlement town() {
        Settlement s = new Settlement(Settlement.Id.random(), "Wegholt",
                new SimPos(0, 64, 0), 128);
        s.setCatalog(BuildCatalog.DEFAULT);
        return s;
    }

    /**
     * A town far enough along to have a wall at all.
     *
     * <p>Nothing is staked before TOWN — a settlement walls itself at its
     * charter and not before — so a fixture that wants a ring to hang gates on
     * has to be a town with its hall standing, not a fortified camp.
     */
    private static Settlement chartered() {
        Settlement s = town();
        s.setStage(SettlementStage.TOWN);
        raise(s, "civilization:camp_post", new SimPos(0, 64, 0), 0);
        raise(s, "civilization:lumber_camp", new SimPos(12, 64, 0), 1);
        raise(s, "civilization:storehouse", new SimPos(-12, 64, 0), 3);
        raise(s, "civilization:town_hall", new SimPos(0, 64, 14), 0);
        return s;
    }

    /** A standing, drawn building of known size — the only kind a road runs to. */
    private static Building raise(Settlement s, String blueprintId, SimPos at, int facing) {
        Building building = new Building(blueprintId, at, 0, true);
        building.setFootprint(new Footprint(at.y(), 5, 5, 4));
        building.setFacing(facing);
        s.addBuilding(building);
        return building;
    }

    /**
     * The same, at the size the catalog actually reserves for it.
     *
     * <p>{@link #raise} gives everything a five-by-five footprint, which suits
     * the tests that only care where a doorstep is. It will not do for a test
     * about walls: a five-wide house whose catalog entry says nine has a door
     * standing <em>inside</em> its own walls, which is a shape no builder can
     * produce and which makes nonsense of any keepout drawn round it.
     */
    private static Building asBuilt(Settlement s, String blueprintId, SimPos at,
                                    int facing) {
        int[] half = BuildPlanner.wallsHalfOf(blueprintId, facing, s.catalog());
        Building building = new Building(blueprintId, at, 0, true);
        building.setFootprint(
                new Footprint(at.y(), 2 * half[0] + 1, 2 * half[1] + 1, 4));
        building.setFacing(facing);
        s.addBuilding(building);
        return building;
    }

    @Test
    void aDoorstepSitsOnTheSideTheBuildingActuallyFaces() {
        Settlement s = town();
        SimPos at = new SimPos(20, 64, 20);

        assertEquals(new SimPos(20, 64, 23), raise(s, "civilization:house", at, 0).doorstep(),
                "facing 0 is as drawn: the door is on the south wall");
        assertEquals(new SimPos(17, 64, 20), raise(town(), "civilization:house", at, 1).doorstep(),
                "a quarter turn clockwise puts the door on the west wall");
        assertEquals(new SimPos(20, 64, 17), raise(town(), "civilization:house", at, 2).doorstep(),
                "a half turn puts it north");
        assertEquals(new SimPos(23, 64, 20), raise(town(), "civilization:house", at, 3).doorstep(),
                "three quarters puts it east");
    }

    @Test
    void everyRoadRunsAtRightAngles() {
        Settlement s = town();
        raise(s, "civilization:camp_post", new SimPos(0, 64, 0), 0);
        raise(s, "civilization:house", new SimPos(19, 64, 13), 1);
        raise(s, "civilization:granary", new SimPos(-17, 64, 21), 3);
        raise(s, "civilization:farm", new SimPos(6, 64, -23), 2);

        for (int i = 0; i < 8; i++) {
            PathPlanner.advance(s, CTX);
        }

        assertFalse(s.paths().isEmpty(), "a town with buildings should have roads");
        for (PathNetwork.Segment segment : s.paths().segments()) {
            // The Segment constructor enforces this, so reaching here at all is
            // the proof; asserting it states the promise the diagonal
            // Bresenham tracks could never make.
            assertTrue(segment.from().x() == segment.to().x()
                            || segment.from().z() == segment.to().z(),
                    "every road runs along one axis: " + segment);
        }
    }

    @Test
    void aNewBuildingJoinsTheNearestRoadRatherThanTheCenter() {
        Settlement s = town();
        raise(s, "civilization:camp_post", new SimPos(0, 64, 0), 0);
        // Far out east, so its road is a long run the next building can meet.
        raise(s, "civilization:house", new SimPos(60, 64, 0), 1);
        PathPlanner.advance(s, CTX);   // hub marks itself joined
        PathPlanner.advance(s, CTX);   // the far house runs its road to the hub

        List<PathNetwork.Segment> before = s.paths().segments();
        assertFalse(before.isEmpty(), "the first building lays the first road");

        // A neighbor of the far house: the hub is sixty blocks away, the
        // existing road is a few.
        raise(s, "civilization:cottage", new SimPos(58, 64, 14), 2);
        PathPlanner.advance(s, CTX);

        List<PathNetwork.Segment> added = s.paths().segments().stream()
                .filter(segment -> !before.contains(segment))
                .toList();
        assertFalse(added.isEmpty(), "the neighbor lays a road of its own");

        SimPos end = added.getLast().to();
        boolean meetsExistingRoad = before.stream()
                .anyMatch(segment -> segment.nearestTo(end).equals(end));
        assertTrue(meetsExistingRoad,
                "the neighbor should branch off the road already passing it, "
                        + "not drive its own spoke to the hub — ended at " + end);

        int laid = added.stream().mapToInt(PathNetwork.Segment::length).sum();
        assertTrue(laid < 40,
                "branching should cost a short spur, not a sixty-block run; was " + laid);
    }

    /**
     * A lane goes round a house rather than through it.
     *
     * <p>The fault a player reported, at its smallest. Joining a door used to be
     * two straight runs from the doorstep to the nearest road with nothing
     * consulted in between, so a building standing on that line was gravelled
     * from one wall to the other — and since a lane is three wide and sited
     * ground was only ever kept off <em>carriageways</em>, nothing anywhere
     * objected.
     */
    @Test
    void alaneGoesRoundAHouseRatherThanThroughIt() {
        Settlement s = town();
        asBuilt(s, "civilization:camp_post", new SimPos(0, 64, 0), 0);
        // Squarely on the line from the far house's door to the hub.
        Building between = asBuilt(s, "civilization:house", new SimPos(30, 64, 0), 0);
        asBuilt(s, "civilization:cottage", new SimPos(60, 64, 0), 1);

        for (int i = 0; i < 8; i++) {
            PathPlanner.advance(s, CTX);
        }

        int[] half = BuildPlanner.wallsHalfOf(between.blueprintId(), between.facing(),
                s.catalog());
        for (PathNetwork.Segment run : s.paths().segments()) {
            for (SimPos at : run.positions()) {
                assertFalse(Math.abs(at.x() - between.origin().x()) <= half[0]
                                && Math.abs(at.z() - between.origin().z()) <= half[1],
                        "a way runs through the house at " + between.origin()
                                + ": " + run.from() + " -> " + run.to());
            }
        }
    }

    /**
     * A plot may not be raised on a footpath somebody else is still using.
     *
     * <p>The other half of the same fault. Tracks were exempt from the siting
     * rule outright, on the argument that a lane is a consequence of a building
     * and refusing a plot for standing on one would be circular. That is true of
     * the lane a plot <em>replaces</em> and false of every other lane in town,
     * and the difference is whether the way dead-ends inside the plot.
     */
    @Test
    void aplotMayNotStandOnATrackThatRunsThrough() {
        Settlement s = town();
        raise(s, "civilization:camp_post", new SimPos(0, 64, 0), 0);
        // A way from one side of the settlement to the other: no loose end.
        s.paths().add(new PathNetwork.Segment(new SimPos(-40, 64, 20), new SimPos(40, 64, 20)));

        assertFalse(s.isPlotFree(new SimPos(0, 64, 20), 11, null),
                "a plot squarely on a through footpath is not free ground");
        assertTrue(s.isPlotFree(new SimPos(0, 64, 40), 11, null),
                "and one well clear of it still is");
    }

    /** But a lane that dead-ends on the plot is the plot's own, and goes under it. */
    @Test
    void aplotMayBeRaisedOverALaneToADoorThatIsGone() {
        Settlement s = town();
        raise(s, "civilization:camp_post", new SimPos(0, 64, 0), 0);
        // A spur off nothing, ending where the new building wants to stand --
        // which is what is left behind when the house it served is pulled down.
        s.paths().add(new PathNetwork.Segment(new SimPos(0, 64, 40), new SimPos(0, 64, 60)));

        assertTrue(s.isPlotFree(new SimPos(0, 64, 60), 11, null),
                "the loose end of a lane is ground the plot it served may be rebuilt on");
        assertFalse(s.isPlotFree(new SimPos(0, 64, 48), 11, null),
                "but the middle of the same lane is still a way somebody walks");
    }

    @Test
    void aCampWithNoHallStillGetsItsRoads() {
        Settlement s = town();
        // No town hall anywhere: the hall is the TOWN capstone now, and the old
        // layer used it as the only hub — so every founding below TOWN laid no
        // roads whatsoever, which a playtest confirmed in the world.
        raise(s, "civilization:camp_post", new SimPos(0, 64, 0), 0);
        raise(s, "civilization:bunkhouse", new SimPos(14, 64, 6), 1);

        PathPlanner.advance(s, CTX);
        PathPlanner.advance(s, CTX);

        assertFalse(s.paths().isEmpty(),
                "a camp radiates its roads from the camp post, hall or no hall");
        assertTrue(s.paths().hasJoined(new SimPos(14, 64, 6)),
                "and the bunkhouse is on the network");
    }

    @Test
    void theNetworkRemembersAndDoesNotRelayWhatItHasLaid() {
        Settlement s = town();
        raise(s, "civilization:camp_post", new SimPos(0, 64, 0), 0);
        raise(s, "civilization:house", new SimPos(16, 64, 9), 1);

        for (int i = 0; i < 6; i++) {
            PathPlanner.advance(s, CTX);
        }
        int settled = s.paths().segments().size();

        for (int i = 0; i < 20; i++) {
            PathPlanner.advance(s, CTX);
        }

        assertEquals(settled, s.paths().segments().size(),
                "a joined building is remembered: its road is planned once, not every step");
    }

    @Test
    void theWallPutsItsGatesWhereTheStreetsReach() {
        Settlement s = chartered();

        PerimeterPlanner.advance(s, CTX);   // stakes the ring
        Perimeter ring = s.perimeter();
        assertTrue(ring != null, "a chartered town with its hall standing stakes a ring");

        // A street pushing hard north -- the way out of town on that side.
        s.paths().add(new PathNetwork.Segment(new SimPos(7, 64, 0), new SimPos(7, 64, -40)));
        PerimeterPlanner.advance(s, CTX);

        // This used to assert the gate sat at exactly x=7, the street's own
        // coordinate -- which was the fault rather than the property. Gates were
        // computed on the town's BOUNDING BOX while the ring is a concave hull,
        // so the gate was a point in a field: on a measured town three of four
        // stood 9, 10 and 53 blocks from any wall, and isGateway matched three
        // posts of the twelve four openings should cut.
        //
        // What a gate has to be is a hole in the WALL, at a place somebody wants
        // to walk. So: on the ring, and near the road.
        Perimeter moved = s.perimeter();
        java.util.Set<SimPos> onRing = new java.util.HashSet<>(moved.ringPositions());
        for (SimPos gate : moved.gates()) {
            assertTrue(onRing.contains(gate),
                    "a gate at " + gate + " is not a post on the wall at all");
        }
        assertTrue(moved.gates().stream().anyMatch(
                        gate -> gate.z() < 0 && Math.abs(gate.x() - 7) <= 6),
                "no gate was cut where the northbound street crosses the ring: "
                        + moved.gates());
        assertTrue(moved.gates().size() >= 4 && moved.gates().size() <= 6,
                "a wall wants a few gates, not none and not a fence: "
                        + moved.gates().size());
    }

    @Test
    void everyGateIsAnOpeningInTheWallItBelongsTo() {
        // The regression guard for the whole class of fault. A gate that is not
        // a ring post cuts no opening: Perimeter.isGateway looks for posts
        // within a block of a gate, finds none, and the wall is raised solid
        // across the road while the town believes it has a gate there.
        Settlement s = chartered();
        s.paths().add(new PathNetwork.Segment(new SimPos(7, 64, 0), new SimPos(7, 64, -40)));
        s.paths().add(new PathNetwork.Segment(new SimPos(0, 64, 6), new SimPos(40, 64, 6)));
        PerimeterPlanner.advance(s, CTX);
        PerimeterPlanner.advance(s, CTX);

        Perimeter ring = s.perimeter();
        java.util.Set<SimPos> onRing = new java.util.HashSet<>(ring.ringPositions());
        int openings = 0;
        for (SimPos post : ring.ringPositions()) {
            if (ring.isGateway(post)) {
                openings++;
            }
        }
        for (SimPos gate : ring.gates()) {
            assertTrue(onRing.contains(gate), "gate " + gate + " is off the wall");
        }
        assertTrue(openings >= 2 * ring.gates().size(),
                "only " + openings + " posts are gateways for " + ring.gates().size()
                        + " gates — the wall is solid where it should be open");
    }

    // --- the forester's belt ---

    /** The outlying farm whose lane is the one that used to go through the wood. */
    private static final SimPos OUTLYING = new SimPos(45, 64, 10);

    /**
     * A town small enough to have a countryside, with a camp working it.
     *
     * <p>The default fixture claims 128 blocks, which swallows any lumber claim
     * whole — and the belt is by definition the part of the claim <em>outside</em>
     * the village, so a town that big has no belt to put a stand in and nothing
     * to test.
     *
     * @param camped whether the camp has a woodland claim at all, which is the
     *               difference between the fault and the fix
     * @param spread whether anything else has been built out past the belt, and
     *               so whether there is a road out there to take a lane round
     */
    private static Settlement withAStand(boolean camped, boolean spread) {
        Settlement s = town();
        s.setClaimRadius(20);
        raise(s, "civilization:camp_post", new SimPos(0, 64, 0), 0);
        Building camp = raise(s, "civilization:lumber_camp", new SimPos(10, 64, 0), 1);
        if (camped) {
            s.setLumberArea(ForesterStand.woodlandFor(camp.origin(), s.center(),
                    s.claimRadius()));
        }
        if (spread) {
            raise(s, "civilization:granary", new SimPos(30, 64, 32), 2);
        }
        raise(s, "civilization:farm", OUTLYING, 1);
        for (int i = 0; i < 12; i++) {
            PathPlanner.advance(s, CTX);
        }
        return s;
    }

    /** The tree of the stand some stretch of road gravels, if any does. */
    private static SimPos felledBy(PathNetwork network, List<SimPos> stand) {
        for (PathNetwork.Segment run : network.segments()) {
            int half = run.paveHalf();
            for (SimPos at : run.positions()) {
                for (SimPos trunk : stand) {
                    if (Math.abs(at.x() - trunk.x()) <= half
                            && Math.abs(at.z() - trunk.z()) <= half) {
                        return trunk;
                    }
                }
            }
        }
        return null;
    }

    @Test
    void aLaneIsNeverGravelledOverTheForestersStand() {
        // The complaint, and the same town twice. A camp's stand is planted out
        // past the houses because that is the only ground a lumberjack will
        // replant on; the town then goes on building, and the lane run out to
        // the last farm was laid straight through the belt and felled it.
        Settlement standing = withAStand(true, false);
        List<SimPos> stand = ForesterStand.stand(standing);
        assertFalse(stand.isEmpty(), "a camp with a belt has somewhere to put a tree");

        assertEquals(null, felledBy(standing.paths(), stand),
                "a lane was gravelled over a trunk of the stand");
        // The control this test used to carry is gone, and the reason is the
        // second half of the same fix rather than a weakening. It asserted that
        // the identical town with no wood to protect DID gravel the trunk at
        // (20, 64, 10) -- the fault, still exhibitable, which is what kept the
        // assertion above honest. The belt is now also held off the ground the
        // town's own plan has reserved (ForesterStand.PlannedGround), and on this
        // ring town the plan reserves (20, 10): the stand moved out to (25, 0),
        // (30, 0), (35, 0) and the flanks, the lane to the outlying farm runs
        // nowhere near any of them, and the two towns route identically. There is
        // no ground left in this fixture where the fault can still be shown.
        //
        // What still shows the router's keepout doing work, rather than the belt
        // having moved out of its way: the sibling test below, where a trunk
        // stands between the door and the road and the lane goes round it, and
        // ForesterStandTest.noRoadASeededTownOpensIsLaidThroughTheStand.
        assertEquals(felledBy(withAStand(false, false).paths(), stand),
                felledBy(standing.paths(), stand),
                "the two towns differ, so this fixture can exhibit the fault "
                        + "again and the control above should be restored");
    }

    @Test
    void andTheLaneIsLaidAnywayOnceThereIsARoadToTakeItRound() {
        // The stand is held off exactly as a plot is, which means a door with no
        // clear lane left of it waits rather than being given a bad one -- and,
        // like a plot, it is joined the moment the network spreads far enough to
        // offer a way round. Without this the fix would read as "the farm never
        // gets a road", which is a worse bug than the one it cures.
        Settlement s = withAStand(true, true);

        assertTrue(s.paths().hasJoined(OUTLYING),
                "the outlying farm is still on the network");
        assertEquals(null, felledBy(s.paths(), ForesterStand.stand(s)),
                "and it got there without gravelling the wood");
    }

    @Test
    void aRoadIsFoundAgainAtItsNearestPoint() {
        PathNetwork network = new PathNetwork();
        network.add(new PathNetwork.Segment(new SimPos(0, 64, 0), new SimPos(40, 64, 0)));

        assertEquals(new SimPos(12, 64, 0), network.nearestPoint(new SimPos(12, 64, 25)),
                "a building beside the road joins it square on, at the closest point");
        assertEquals(new SimPos(40, 64, 0), network.nearestPoint(new SimPos(90, 64, 3)),
                "past the end of the road, the end of the road is the nearest point");
    }
}
