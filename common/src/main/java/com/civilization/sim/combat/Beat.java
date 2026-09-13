package com.civilization.sim.combat;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.PathNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The round a guard walks at night.
 *
 * <p>A guard's day is a post: {@code PersonEntityManager.patrolPost} sends him to
 * the nearest vertex of the ring and to the next one along when he gets there,
 * which is a slow circuit of the wall and is right for daylight — the wall is
 * where somebody coming at the town arrives. It is wrong for the dark, because
 * after dusk the town is not being approached from outside. It is spawning inside
 * itself. The measurement found the creeper and four of the spiders on the ring
 * road and at houses within the claim, not beyond it.
 *
 * <p>So at night the round is the <em>streets</em>. A beat is a loop of nodes
 * sampled off the opened runs of the path network, ordered the way somebody
 * walking would take them — round the town rather than back and forth across it —
 * and split between however many guards the town has posted so that two of them
 * cover opposite halves instead of pacing each other.
 *
 * <p><strong>Inside the claim, always.</strong> A beat that followed a lane out
 * to the lumber belt would send the one armed man in the town into the outlying
 * dark, on his own, which is where the guard who died was found. The claim radius
 * is the fence on this and there is no argument that gets past it.
 *
 * <p>Stateless, like the day post and for the same reason: a guard near a node
 * heads for the next one along and otherwise for the nearest, which resolves into
 * a steady round without a cursor anybody has to save.
 */
public final class Beat {

    private Beat() {
    }

    /**
     * Blocks between nodes on a beat: sixteen.
     *
     * <p>Twice {@code LightPlanner.SPACING}, which is not a coincidence. A guard
     * walking to a node passes two lamps on the way, so the beat is sampled
     * finely enough that nothing between nodes is out of his sight and coarsely
     * enough that he is walking rather than stopping every few paces.
     */
    public static final int NODE_SPACING = 16;

    /** How close counts as standing at a node. */
    public static final double AT_A_NODE = 3.0;

    /**
     * The whole round, as the nodes of it in walking order.
     *
     * <p>Sampled off the opened runs and then sorted by bearing from the middle
     * of town, which is what turns a bag of points into a loop. Sorting by
     * bearing is crude and is deliberately crude: a real traveling-salesman
     * ordering of forty nodes every time a street opens would be a lot of
     * arithmetic to decide which of two lamp posts a guard walks past first, and
     * bearing gets the thing that matters — a circuit rather than a zigzag — for
     * a comparator.
     *
     * <p>Duplicates are dropped by proximity rather than by equality: two runs
     * meeting at a crossroads sample nearly the same column, and a beat with the
     * same corner in it twice is a guard standing still.
     *
     * @param claimRadius the fence. Nodes beyond it are not on anybody's beat.
     */
    public static List<SimPos> loop(PathNetwork paths, SimPos center, int claimRadius) {
        if (paths == null) {
            return List.of();
        }
        List<SimPos> nodes = new ArrayList<>();
        List<PathNetwork.Segment> runs = paths.segments();
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i) || paths.isUnwalkable(i)) {
                continue;
            }
            List<SimPos> along = runs.get(i).positions();
            for (int at = 0; at < along.size(); at += NODE_SPACING) {
                SimPos node = along.get(at);
                if (node.horizontalDistance(center) > claimRadius) {
                    continue;   // out past the fence; not a beat, an errand
                }
                if (crowds(nodes, node)) {
                    continue;
                }
                nodes.add(node);
            }
        }
        nodes.sort(Comparator
                .comparingDouble((SimPos node) -> bearing(center, node))
                .thenComparingLong(node -> node.horizontalDistanceSq(center)));
        return List.copyOf(nodes);
    }

    private static boolean crowds(List<SimPos> nodes, SimPos node) {
        for (SimPos standing : nodes) {
            if (standing.horizontalDistance(node) < NODE_SPACING / 2.0) {
                return true;
            }
        }
        return false;
    }

    /** Clockwise from north, in radians, which is only ever used to sort. */
    private static double bearing(SimPos center, SimPos node) {
        return Math.atan2(node.z() - center.z(), node.x() - center.x());
    }

    /**
     * One guard's share of the round: a contiguous arc of it.
     *
     * <p>Contiguous, not every second node. Two guards taking alternate nodes
     * would walk the whole town each and pass each other at every lamp; two
     * guards taking halves each walk half of it and are never in the same street,
     * which is the entire point of there being two.
     *
     * <p>The last guard takes the remainder, so a loop of twenty-one between two
     * gives eleven and ten rather than ten and ten with a node nobody walks. Every
     * node is on exactly one beat — {@code BeatTest} holds that, because a beat
     * system that lost a node would lose whichever street it was on.
     *
     * @param which  this guard's number, from zero
     * @param guards how many are on the watch tonight
     */
    public static List<SimPos> shareOf(List<SimPos> loop, int which, int guards) {
        if (loop.isEmpty() || guards <= 0) {
            return List.of();
        }
        if (guards == 1 || loop.size() <= guards) {
            return which == 0 || which >= loop.size() ? loop
                    : List.of(loop.get(Math.min(which, loop.size() - 1)));
        }
        int mine = Math.max(0, Math.min(which, guards - 1));
        int each = loop.size() / guards;
        int from = mine * each;
        int to = mine == guards - 1 ? loop.size() : from + each;
        return List.copyOf(loop.subList(from, to));
    }

    /**
     * Where this guard walks next.
     *
     * <p>Nearest node, or the one after it when he is standing on it. The same
     * shape as the day post and stateless for the same reason: what a guard is
     * doing is legible from where he is, so nothing about a round has to survive
     * a restart.
     *
     * @return null when the town has no beat to walk, in which case the caller
     *         falls back to the day post
     */
    public static SimPos nextNode(List<SimPos> beat, SimPos standing) {
        if (beat.isEmpty()) {
            return null;
        }
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < beat.size(); i++) {
            double distance = beat.get(i).horizontalDistance(standing);
            if (distance < best) {
                best = distance;
                nearest = i;
            }
        }
        return best <= AT_A_NODE ? beat.get((nearest + 1) % beat.size()) : beat.get(nearest);
    }

    /** How far round one beat is, end to end and back to the start. */
    public static double length(List<SimPos> beat) {
        if (beat.size() < 2) {
            return 0;
        }
        double total = 0;
        for (int i = 0; i < beat.size(); i++) {
            total += beat.get(i).horizontalDistance(beat.get((i + 1) % beat.size()));
        }
        return total;
    }
}
