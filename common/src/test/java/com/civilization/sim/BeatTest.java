package com.civilization.sim;

import com.civilization.sim.combat.Beat;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.PathNetwork;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guards' night round.
 *
 * <p>Three things have to hold or the beat is worse than the post it replaces.
 * Every node of the round has to be on somebody's beat, or whichever street was
 * dropped is a street nobody walks. No node may be on two beats, or two guards pace
 * each other down one lane while the rest of the town is empty. And nothing may be
 * outside the claim, because the one guard who died in the measurement died in the
 * outlying dark.
 */
class BeatTest {

    private static final int CLAIM = 64;

    /** A ring road of four sides, all opened, inside a claim of sixty-four. */
    private static PathNetwork ringRoad() {
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
        return paths;
    }

    @Test
    void theRoundCoversTheRing() {
        List<SimPos> loop = Beat.loop(ringRoad(), new SimPos(0, 64, 0), CLAIM);
        assertFalse(loop.isEmpty(), "an opened ring road is a round to walk");
        // The four sides are 81 blocks each; sampled every sixteen that is six
        // nodes a side before the duplicate-corner cull.
        assertTrue(loop.size() >= 12,
                "a ring of three hundred blocks sampled every " + Beat.NODE_SPACING
                        + " should come to more than " + loop.size() + " nodes");
        for (SimPos node : loop) {
            assertTrue(node.horizontalDistance(new SimPos(0, 64, 0)) <= CLAIM,
                    "a node at " + node + " is outside the claim, which is where the "
                            + "guard who died was found");
        }
    }

    @Test
    void twoGuardsSplitTheRingBetweenThem() {
        List<SimPos> loop = Beat.loop(ringRoad(), new SimPos(0, 64, 0), CLAIM);
        List<SimPos> first = Beat.shareOf(loop, 0, 2);
        List<SimPos> second = Beat.shareOf(loop, 1, 2);

        Set<SimPos> covered = new HashSet<>(first);
        covered.addAll(second);
        assertEquals(loop.size(), covered.size(),
                "every node has to be on somebody's beat, or whichever street was "
                        + "dropped is a street nobody walks");
        assertEquals(loop.size(), first.size() + second.size(),
                "and on nobody's twice, or two guards pace each other down one lane");
        assertTrue(first.size() > 0 && second.size() > 0, "both of them walk somewhere");
    }

    @Test
    void eachShareIsOneStretchRatherThanAlternateNodes() {
        List<SimPos> loop = Beat.loop(ringRoad(), new SimPos(0, 64, 0), CLAIM);
        List<SimPos> first = Beat.shareOf(loop, 0, 2);
        for (int i = 0; i < first.size(); i++) {
            assertEquals(loop.get(i), first.get(i),
                    "a share is a contiguous arc; alternate nodes would have both "
                            + "guards walking the whole town");
        }
    }

    @Test
    void theRemainderGoesToTheLastGuard() {
        List<SimPos> loop = List.of(
                new SimPos(0, 0, 0), new SimPos(1, 0, 0), new SimPos(2, 0, 0),
                new SimPos(3, 0, 0), new SimPos(4, 0, 0));
        assertEquals(2, Beat.shareOf(loop, 0, 2).size());
        assertEquals(3, Beat.shareOf(loop, 1, 2).size(),
                "five between two is three and two, not two and two with a node "
                        + "nobody walks");
    }

    @Test
    void oneGuardWalksTheWholeRound() {
        List<SimPos> loop = Beat.loop(ringRoad(), new SimPos(0, 64, 0), CLAIM);
        assertEquals(loop.size(), Beat.shareOf(loop, 0, 1).size());
    }

    @Test
    void aGuardStandingOnANodeWalksToTheNextOne() {
        List<SimPos> loop = Beat.loop(ringRoad(), new SimPos(0, 64, 0), CLAIM);
        SimPos standing = loop.getFirst();
        SimPos next = Beat.nextNode(loop, standing);
        assertNotNull(next);
        assertFalse(standing.equals(next),
                "a guard who was sent to the node he is standing on would never move");
    }

    @Test
    void aGuardBetweenNodesWalksToTheNearest() {
        List<SimPos> loop = List.of(
                new SimPos(0, 0, 0), new SimPos(100, 0, 0), new SimPos(200, 0, 0));
        assertEquals(new SimPos(100, 0, 0),
                Beat.nextNode(loop, new SimPos(90, 0, 0)));
    }

    @Test
    void theRoundIsALoopRatherThanAZigzag() {
        List<SimPos> loop = Beat.loop(ringRoad(), new SimPos(0, 64, 0), CLAIM);
        // A bag of ring-road nodes walked in a bad order crosses the town on every
        // second step, so the round would be several times the ring's own length.
        double ring = 4 * 81;
        assertTrue(Beat.length(loop) < ring * 1.6,
                "the round is " + Beat.length(loop) + " blocks for a ring of " + ring
                        + ", which is a guard crossing the town rather than walking it");
    }

    @Test
    void aTownWithNoOpenedStreetsHasNoBeat() {
        PathNetwork planned = new PathNetwork();
        planned.add(new PathNetwork.Segment(
                new SimPos(-40, 64, 0), new SimPos(40, 64, 0), 8));
        assertTrue(Beat.loop(planned, new SimPos(0, 64, 0), CLAIM).isEmpty(),
                "and falls back to the day post, which is what it always had");
        assertNull(Beat.nextNode(List.of(), new SimPos(0, 64, 0)));
        assertTrue(Beat.loop(null, new SimPos(0, 64, 0), CLAIM).isEmpty());
    }

    @Test
    void aLaneOutPastTheClaimIsNotOnAnybodysBeat() {
        PathNetwork paths = ringRoad();
        paths.add(new PathNetwork.Segment(
                new SimPos(40, 64, 0), new SimPos(300, 64, 0), 3));
        paths.markOpened(paths.segments().size() - 1);

        for (SimPos node : Beat.loop(paths, new SimPos(0, 64, 0), CLAIM)) {
            assertTrue(node.horizontalDistance(new SimPos(0, 64, 0)) <= CLAIM,
                    "the lumber lane is not a beat, it is an errand, and sending the "
                            + "one armed man in the town down it alone is how he died");
        }
    }
}
