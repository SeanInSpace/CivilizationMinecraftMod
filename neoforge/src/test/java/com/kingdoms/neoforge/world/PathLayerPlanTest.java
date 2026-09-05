package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.PathNetwork;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a crew paves at one station of a run.
 *
 * <p>A street used to be opened by one swing at its middle, after which the
 * whole run was stamped into the ground by a sweep. A crew walks it now, and the
 * unit of the walking is a cross-section rather than a block: somebody standing
 * on the line of a road works across the way, shoulder to shoulder with the
 * verge, which is both what a road crew does and what keeps a run of thirty
 * paces a walk instead of an afternoon.
 *
 * <p><strong>Why it can run without a world.</strong> Which columns a station
 * covers follows from the run and its width and from nothing else — what the
 * ground is doing decides whether a column takes a path block, never which
 * column it is. Same argument {@code BlueprintPlacerSizeTest} makes about a
 * building's drawn size.
 */
class PathLayerPlanTest {

    private static PathNetwork.Segment run(int length, int width) {
        return new PathNetwork.Segment(
                new SimPos(0, 64, 0), new SimPos(length, 64, 0), width);
    }

    @Test
    void aStationIsTheWidthOfTheWayAndNotOneBlockOfIt() {
        // A track is three across, so a station is three by three: the column
        // underfoot, the one ahead, the one behind, and the verges either side.
        PathNetwork.Segment track = run(8, PathNetwork.TRACK_WIDTH);

        List<SimPos> section = PathLayer.crossSectionAt(track, 3);

        assertEquals(9, section.size(), "a three-wide way is three by three at a station");
        assertTrue(section.contains(track.positions().get(3)),
                "and the builder is standing in the middle of it");
    }

    @Test
    void aCarriagewayIsPavedWiderThanATrack() {
        // The paved surface is a third of the reservation -- the plan keeps
        // plots back from an eight-wide right of way so nothing is built in the
        // road, and paving all eight of that is a runway rather than a street.
        PathNetwork.Segment street = run(8, 8);

        assertEquals(25, PathLayer.crossSectionAt(street, 3).size(),
                "a street is five across, so a station is five by five");
    }

    @Test
    void aRunIsWalkedFromOneEndToTheOther() {
        // The stations are the run's own columns in the run's own order, which
        // is what makes a crew's walk the line of the road rather than a tour.
        PathNetwork.Segment track = run(8, PathNetwork.TRACK_WIDTH);
        List<SimPos> along = track.positions();

        for (int i = 0; i < along.size(); i++) {
            assertTrue(PathLayer.crossSectionAt(track, i).contains(along.get(i)),
                    "station " + i + " is not on the run");
        }
    }

    /**
     * Every column the sweep would pave is a column some station covers.
     *
     * <p>The claim that keeps the two halves honest. The sweep that mends a road
     * grown over walks every index of the run and lays the width at each; a crew
     * opening one walks the same indices and lays the same width. If they
     * disagreed, a road would be a different shape depending on whether anybody
     * watched it go in — which is the whole fault this work was about, arrived at
     * from the other side.
     */
    @Test
    void theStationsOfARunCoverExactlyWhatTheSweepWouldPave() {
        PathNetwork.Segment street = run(12, 8);
        Set<SimPos> byStation = new HashSet<>();
        for (int i = 0; i < street.positions().size(); i++) {
            byStation.addAll(PathLayer.crossSectionAt(street, i));
        }

        Set<SimPos> bySweep = new HashSet<>();
        int half = Math.max(1, street.width() / 3);
        for (SimPos pos : street.positions()) {
            for (int ox = -half; ox <= half; ox++) {
                for (int oz = -half; oz <= half; oz++) {
                    bySweep.add(new SimPos(pos.x() + ox, pos.y(), pos.z() + oz));
                }
            }
        }

        assertEquals(bySweep, byStation,
                "the crew and the sweep must lay the same road");
    }

    @Test
    void aStationOffTheEndOfARunIsNoWork() {
        PathNetwork.Segment track = run(4, PathNetwork.TRACK_WIDTH);

        assertTrue(PathLayer.crossSectionAt(track, track.positions().size()).isEmpty());
        assertTrue(PathLayer.crossSectionAt(track, -1).isEmpty());
    }
}
