package com.civilization.sim.settlement;

import com.civilization.sim.geom.SimPos;

import java.util.List;

/**
 * Where somebody comes into a town from outside, and where they leave by.
 *
 * <p><strong>Why this exists.</strong> Until now the only thing that ever walked
 * into a settlement from the edge was a raider. Everybody else appeared: a
 * newcomer was given a body standing on the spot they were recorded at, and the
 * caravan the inn traded with was a line in the event log. Both of those want the
 * same answer to the same question — <em>which column of this town is its front
 * door</em> — so the question is answered once, here, without a world.
 *
 * <p><strong>The rule, in order.</strong> A gate, when the wall has one: a town
 * that went to the trouble of cutting an opening in its own palisade has said
 * where it expects people to arrive, and anything that walked round the wall to
 * come in over the verge instead would be making that opening a lie. Otherwise
 * the far end of the longest opened street, which is the same thing said by a
 * town that has not built a wall: the longest road out of a village is the road
 * to somewhere else.
 *
 * <p><strong>And in both cases it lies on an opened street.</strong> That is the
 * invariant, not a convenience. A gate is a position on the <em>wall line</em>
 * and the wall is staked before most of the roads exist, so the gate column
 * itself is frequently grass with a palisade either side of it. Somebody put down
 * there has to find their own way onto the road before they can walk anywhere,
 * and the one thing a pathfinder is worst at is the first ten blocks. So a gate
 * is <em>snapped</em> to the nearest column of an opened run, which is the point
 * on the road the gate actually lets onto — and a town with no opened run at all
 * has no edge, which is the answer that keeps a caravan out of a road-less camp.
 */
public final class TownEdge {

    private TownEdge() {
    }

    /**
     * The column an arrival walks in at, or null if this town has no way in.
     *
     * <p>Null is a real answer and callers have to honor it. A settlement whose
     * streets have not been opened yet — a founding party three days old, a
     * goblin camp that will never lay a road — is not a place a wagon can reach,
     * and putting one at the edge of it anyway would be a trader standing in a
     * field.
     */
    public static SimPos of(Settlement settlement) {
        if (settlement == null) {
            return null;
        }
        PathNetwork paths = settlement.paths();
        if (paths == null) {
            return null;
        }
        List<PathNetwork.Segment> runs = paths.segments();
        // Gathered once: every test below walks the whole set, and an opened run
        // that has been found unwalkable is not a road however long it is.
        List<Integer> open = new java.util.ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            if (paths.isOpened(i) && !paths.isUnwalkable(i)) {
                open.add(i);
            }
        }
        if (open.isEmpty()) {
            return null;
        }
        SimPos gate = theGate(settlement, runs, open);
        return gate != null ? gate : theEndOfTheLongestStreet(settlement, runs, open);
    }

    /** Whether anybody can walk into this town at all. */
    public static boolean hasAWayIn(Settlement settlement) {
        return of(settlement) != null;
    }

    /**
     * The gate the road actually runs through, snapped onto that road.
     *
     * <p>A wall commonly has several gates and they are not equally useful: the
     * plan cuts them on the compass points, and a town whose streets all leave to
     * the south has three gates opening onto nothing. So the gate chosen is the
     * one nearest an opened run — the one somebody would use — and the position
     * returned is the point on the run, not the opening in the wall.
     *
     * <p>Ties are broken on the coordinates rather than on the order the wall
     * happened to be staked in, so the same town always has the same front door.
     * An entry point that moved between two passes would have a caravan arriving
     * at one gate and leaving by another, which reads as two caravans.
     */
    private static SimPos theGate(Settlement settlement, List<PathNetwork.Segment> runs,
                                  List<Integer> open) {
        Perimeter wall = settlement.perimeter();
        if (wall == null || wall.gates().isEmpty()) {
            return null;
        }
        SimPos best = null;
        long nearest = Long.MAX_VALUE;
        for (SimPos gate : wall.gates()) {
            for (int i : open) {
                SimPos onTheRoad = runs.get(i).nearestTo(gate);
                long away = onTheRoad.horizontalDistanceSq(gate);
                if (away < nearest || (away == nearest && earlier(onTheRoad, best))) {
                    nearest = away;
                    best = onTheRoad;
                }
            }
        }
        return best;
    }

    /**
     * The far end of the longest opened run: a village's road to somewhere else.
     *
     * <p>Longest by {@code length} rather than by how much of it is paved, and
     * <em>far</em> end meaning the end further from the middle of the town, which
     * is the end that points away from everybody's houses. A caravan that came in
     * at the near end would materialize in the square.
     */
    private static SimPos theEndOfTheLongestStreet(Settlement settlement,
                                                   List<PathNetwork.Segment> runs,
                                                   List<Integer> open) {
        SimPos center = settlement.center();
        PathNetwork.Segment longest = null;
        for (int i : open) {
            PathNetwork.Segment run = runs.get(i);
            if (longest == null || run.length() > longest.length()
                    || (run.length() == longest.length()
                        && earlier(run.from(), longest.from()))) {
                longest = run;
            }
        }
        if (longest == null) {
            return null;
        }
        List<SimPos> along = longest.positions();
        SimPos head = along.getFirst();
        SimPos tail = along.getLast();
        long headOut = head.horizontalDistanceSq(center);
        long tailOut = tail.horizontalDistanceSq(center);
        if (headOut > tailOut) {
            return head;
        }
        if (tailOut > headOut) {
            return tail;
        }
        return earlier(head, tail) ? head : tail;
    }

    /** A total order on positions, so a tie is broken the same way every time. */
    private static boolean earlier(SimPos one, SimPos other) {
        if (other == null) {
            return true;
        }
        if (one.x() != other.x()) {
            return one.x() < other.x();
        }
        return one.z() < other.z();
    }

    /**
     * Whether this column is on one of the town's opened streets.
     *
     * <p>The invariant {@link #of} promises, stated so a test can ask it rather
     * than re-derive it. Within the run's paved half-width, because a road is as
     * wide as it was paved and a position one block off a carriageway's
     * centerline is still on the road.
     */
    public static boolean onAnOpenedStreet(Settlement settlement, SimPos at) {
        if (settlement == null || at == null) {
            return false;
        }
        PathNetwork paths = settlement.paths();
        if (paths == null) {
            return false;
        }
        List<PathNetwork.Segment> runs = paths.segments();
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i) || paths.isUnwalkable(i)) {
                continue;
            }
            PathNetwork.Segment run = runs.get(i);
            if (run.nearestTo(at).horizontalDistance(at) <= run.paveHalf()) {
                return true;
            }
        }
        return false;
    }
}
