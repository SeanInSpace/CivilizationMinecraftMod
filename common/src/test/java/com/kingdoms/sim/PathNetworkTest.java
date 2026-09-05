package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.PathPlanner;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
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
        s.setCatalogue(BuildCatalogue.DEFAULT);
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
        raise(s, "kingdoms:camp_post", new SimPos(0, 64, 0), 0);
        raise(s, "kingdoms:lumber_camp", new SimPos(12, 64, 0), 1);
        raise(s, "kingdoms:storehouse", new SimPos(-12, 64, 0), 3);
        raise(s, "kingdoms:town_hall", new SimPos(0, 64, 14), 0);
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

    @Test
    void aDoorstepSitsOnTheSideTheBuildingActuallyFaces() {
        Settlement s = town();
        SimPos at = new SimPos(20, 64, 20);

        assertEquals(new SimPos(20, 64, 23), raise(s, "kingdoms:house", at, 0).doorstep(),
                "facing 0 is as drawn: the door is on the south wall");
        assertEquals(new SimPos(17, 64, 20), raise(town(), "kingdoms:house", at, 1).doorstep(),
                "a quarter turn clockwise puts the door on the west wall");
        assertEquals(new SimPos(20, 64, 17), raise(town(), "kingdoms:house", at, 2).doorstep(),
                "a half turn puts it north");
        assertEquals(new SimPos(23, 64, 20), raise(town(), "kingdoms:house", at, 3).doorstep(),
                "three quarters puts it east");
    }

    @Test
    void everyRoadRunsAtRightAngles() {
        Settlement s = town();
        raise(s, "kingdoms:camp_post", new SimPos(0, 64, 0), 0);
        raise(s, "kingdoms:house", new SimPos(19, 64, 13), 1);
        raise(s, "kingdoms:granary", new SimPos(-17, 64, 21), 3);
        raise(s, "kingdoms:farm", new SimPos(6, 64, -23), 2);

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
    void aNewBuildingJoinsTheNearestRoadRatherThanTheCentre() {
        Settlement s = town();
        raise(s, "kingdoms:camp_post", new SimPos(0, 64, 0), 0);
        // Far out east, so its road is a long run the next building can meet.
        raise(s, "kingdoms:house", new SimPos(60, 64, 0), 1);
        PathPlanner.advance(s, CTX);   // hub marks itself joined
        PathPlanner.advance(s, CTX);   // the far house runs its road to the hub

        List<PathNetwork.Segment> before = s.paths().segments();
        assertFalse(before.isEmpty(), "the first building lays the first road");

        // A neighbour of the far house: the hub is sixty blocks away, the
        // existing road is a few.
        raise(s, "kingdoms:cottage", new SimPos(58, 64, 14), 2);
        PathPlanner.advance(s, CTX);

        List<PathNetwork.Segment> added = s.paths().segments().stream()
                .filter(segment -> !before.contains(segment))
                .toList();
        assertFalse(added.isEmpty(), "the neighbour lays a road of its own");

        SimPos end = added.getLast().to();
        boolean meetsExistingRoad = before.stream()
                .anyMatch(segment -> segment.nearestTo(end).equals(end));
        assertTrue(meetsExistingRoad,
                "the neighbour should branch off the road already passing it, "
                        + "not drive its own spoke to the hub — ended at " + end);

        int laid = added.stream().mapToInt(PathNetwork.Segment::length).sum();
        assertTrue(laid < 40,
                "branching should cost a short spur, not a sixty-block run; was " + laid);
    }

    @Test
    void aCampWithNoHallStillGetsItsRoads() {
        Settlement s = town();
        // No town hall anywhere: the hall is the TOWN capstone now, and the old
        // layer used it as the only hub — so every founding below TOWN laid no
        // roads whatsoever, which a playtest confirmed in the world.
        raise(s, "kingdoms:camp_post", new SimPos(0, 64, 0), 0);
        raise(s, "kingdoms:bunkhouse", new SimPos(14, 64, 6), 1);

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
        raise(s, "kingdoms:camp_post", new SimPos(0, 64, 0), 0);
        raise(s, "kingdoms:house", new SimPos(16, 64, 9), 1);

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
