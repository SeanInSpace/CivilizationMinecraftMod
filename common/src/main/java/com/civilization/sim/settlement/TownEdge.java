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
 * the end of an opened street, which is the same thing said by a town that has
 * not built a wall: a road out of a village is the road to somewhere else.
 *
 * <p><strong>And of those, the one that is actually near the inn.</strong> This
 * used to take the gate nearest <em>any</em> road and the far end of the
 * <em>longest</em> street, and both of those are answers to a question nobody
 * asked. A playtest measured the result: a town whose inn stood at
 * (261, 64, 511) and whose caravan arrived, correctly and on time, at
 * (169, 69, 256) — <strong>two hundred and seventy-seven blocks away</strong>,
 * at the far end of the longest street of a town four hundred blocks across. A
 * player told a caravan had called, standing in the inn yard where it was
 * calling, watched two entire visit windows and saw nothing. The longest street
 * of a sprawling town is by construction the one running furthest from
 * everything, so "longest" selects hardest against the thing this is for.
 *
 * <p>So the edge is measured by {@link #walkToTheInn}: the shortest way along the
 * town's own opened streets from the edge to the inn's doorstep. Under
 * {@link #WALK_TO_THE_INN} it is a front door; over it, it is a place on the map
 * that happens to be in the claim. Of the ones that qualify the town takes the
 * <em>furthest out</em>, because an arrival is somebody coming in from outside
 * and the nearest street end to the middle is the square. The candidates are
 * still gates first and street ends only when there are none, because a wall with
 * an opening in it has said where people come in and that has not stopped being
 * true — what has changed is which of several gates, and which of several
 * streets.
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
        long stamp = shapeOf(settlement, paths);
        if (settlement.hasCachedEdge(stamp)) {
            return settlement.cachedEdge();
        }
        SimPos edge = workItOut(settlement, paths);
        settlement.cacheEdge(stamp, edge);
        return edge;
    }

    /**
     * A number that changes whenever anything {@link #of} reads changes.
     *
     * <p>{@code Furnishings.shapeOf}'s trick and it is needed for the same
     * reason, more sharply. The front door used to be a walk of the gates
     * against the runs; measuring the way to the inn made it a shortest path,
     * and {@code Caravans.tend} asks this of every town in the world on every
     * pass. So the answer is worked out when the streets, the wall or the
     * buildings change and read off the town the rest of the time — which is
     * nearly always, because a network is a finite thing a town finishes.
     *
     * <p>Over-inclusive on purpose, and cheap on purpose: every input is a size
     * or a coordinate the settlement already has in hand, so the stamp costs
     * nothing to take even when it turns out to be the same one.
     */
    private static long shapeOf(Settlement settlement, PathNetwork paths) {
        long h = settlement.center().x() * 31L + settlement.center().z();
        h = h * 31L + paths.segments().size();
        h = h * 31L + paths.openedCount();
        h = h * 31L + paths.unwalkableCount();
        h = h * 31L + settlement.buildings().size();
        Perimeter wall = settlement.perimeter();
        h = h * 31L + (wall == null ? 0 : wall.gates().size());
        return h;
    }

    private static SimPos workItOut(Settlement settlement, PathNetwork paths) {
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
        // Gates first and street ends only when there are none, exactly as
        // before. What is new is which one of them: see nearestTheInn.
        List<SimPos> ways = theGates(settlement, runs, open);
        if (ways.isEmpty()) {
            ways = theEndsOfTheStreets(settlement, runs, open);
        }
        return nearestTheInn(settlement, runs, open, ways);
    }

    /**
     * How far an arrival may be from the inn and still be its front door: 120.
     *
     * <p>A walk rather than a sight line, and the number is the walk a player
     * will make rather than one a wagon minds. A hundred and twenty blocks is
     * something over a minute at walking pace: far enough that an ordinary town
     * has its gate inside it — the second playtest's run B measured fifteen —
     * and near enough that somebody told a caravan has called can get to it while
     * it is still there. Past that the arrival is not a front door, it is a
     * coordinate in the claim, which is what two hundred and seventy-seven
     * blocks of run A bought.
     *
     * <p>It is a preference and not a veto. A town really can be shaped so that
     * nothing it has is within it, and the answer then is the nearest thing it
     * has rather than no way in at all — a settlement with opened streets is a
     * settlement a wagon can reach, and {@link #of} promising otherwise would
     * stop caravans calling on exactly the towns this was meant to fix.
     */
    public static final int WALK_TO_THE_INN = 120;

    /**
     * Which of these ways in the town's front door is.
     *
     * <p>The outermost one that is still within {@link #WALK_TO_THE_INN} of the
     * inn, and the two halves of that sentence are pulling against each other on
     * purpose. <em>Outermost</em> is the old rule and it was not wrong: an
     * arrival is somebody coming in from outside, so it belongs at the edge of
     * the town, and a wagon put down at the nearest street end to the middle
     * materializes in the square. <em>Within reach of the inn</em> is what the
     * old rule had no opinion about at all, which is how a wagon calling at an
     * inn came to arrive two hundred and seventy-seven blocks from it. A town
     * gets the furthest out it can be while still being somewhere a player in the
     * inn yard can walk to.
     *
     * <p>When nothing is within reach — a town four hundred blocks across whose
     * every edge is a hike — the cap gives way rather than the caravan, and the
     * shortest walk of a bad set wins. And when the network joins none of them to
     * the inn at all, two halves of a town with no road between them, the nearest
     * as the crow flies, which is at least an answer.
     *
     * <p>Ties break on {@link #earlier} throughout, because an entry point that
     * moved between two passes would have a caravan arriving at one gate and
     * leaving by another, which reads as two caravans.
     */
    private static SimPos nearestTheInn(Settlement settlement,
                                        List<PathNetwork.Segment> runs,
                                        List<Integer> open, List<SimPos> ways) {
        if (ways.isEmpty()) {
            return null;
        }
        SimPos inn = theInnDoorstep(settlement);
        SimPos center = settlement.center();
        SimPos outermost = null;
        long furthest = -1L;
        SimPos closest = null;
        double shortest = Double.POSITIVE_INFINITY;
        long asTheCrowFlies = (long) WALK_TO_THE_INN * WALK_TO_THE_INN;
        for (SimPos way : ways) {
            // A walk is never shorter than the straight line, so a candidate
            // already further than the cap in a straight line cannot be inside
            // it on foot -- and a shortest path over a network of hundreds of
            // runs is much the dearest thing this class does. It is still
            // measured, because the straight line is no use for choosing
            // between the ones that pass.
            if (way.horizontalDistanceSq(inn) > asTheCrowFlies && outermost != null) {
                continue;
            }
            double walk = walkToTheInn(settlement, runs, open, way, inn);
            if (walk < shortest || (walk == shortest && earlier(way, closest))) {
                shortest = walk;
                closest = way;
            }
            if (walk > WALK_TO_THE_INN) {
                continue;
            }
            long out = way.horizontalDistanceSq(center);
            if (out > furthest || (out == furthest && earlier(way, outermost))) {
                furthest = out;
                outermost = way;
            }
        }
        if (outermost != null) {
            return outermost;
        }
        if (closest != null && shortest < Double.POSITIVE_INFINITY) {
            return closest;
        }
        // Nothing on the network reaches the inn. Straight-line, then, which is
        // the only remaining sense in which one of these is nearer than another.
        SimPos best = null;
        long nearest = Long.MAX_VALUE;
        for (SimPos way : ways) {
            long away = way.horizontalDistanceSq(inn);
            if (away < nearest || (away == nearest && earlier(way, best))) {
                nearest = away;
                best = way;
            }
        }
        return best;
    }

    /**
     * Where the wagon is headed: the inn's doorstep, the market's, or the middle.
     *
     * <p>{@code Caravan.standsAt}'s own order, and it has to be — an edge chosen
     * for being near one building while the wagon walks to another would be the
     * same fault with a shorter number. The middle is the fallback rather than
     * null because a town with no inn still has newcomers walking into it, and
     * the middle is where anything with nowhere better to be belongs.
     */
    private static SimPos theInnDoorstep(Settlement settlement) {
        Building inn = settlement.buildingWithRole(BuildingRole.INN);
        if (inn != null) {
            return inn.doorstep();
        }
        Building market = settlement.buildingWithRole(BuildingRole.MARKET);
        return market != null ? market.doorstep() : settlement.center();
    }

    /** Whether anybody can walk into this town at all. */
    public static boolean hasAWayIn(Settlement settlement) {
        return of(settlement) != null;
    }

    /**
     * Every gate this town has, each snapped onto the road it lets onto.
     *
     * <p>A wall commonly has several gates and they are not equally useful: the
     * plan cuts them on the compass points, and a town whose streets all leave to
     * the south has three gates opening onto nothing. A gate is therefore snapped
     * to the nearest column of an opened run — the position returned is the point
     * on the run, not the opening in the wall — and a gate whose nearest road is
     * further than {@link #GATE_ONTO_A_ROAD} is not a way in at all, because a
     * gate a hundred blocks from the nearest street is an opening onto grass.
     *
     * <p>All of them rather than one, because which gate is the front door is
     * {@link #nearestTheInn}'s question and it cannot answer it from a single
     * candidate. This used to pick the gate nearest a road and hand that back,
     * which meant a town's front door was decided by where its wall happened to
     * graze a lane.
     */
    private static List<SimPos> theGates(Settlement settlement,
                                         List<PathNetwork.Segment> runs,
                                         List<Integer> open) {
        List<SimPos> ways = new java.util.ArrayList<>();
        Perimeter wall = settlement.perimeter();
        if (wall == null) {
            return ways;
        }
        for (SimPos gate : wall.gates()) {
            SimPos best = null;
            long nearest = Long.MAX_VALUE;
            for (int i : open) {
                SimPos onTheRoad = runs.get(i).nearestTo(gate);
                long away = onTheRoad.horizontalDistanceSq(gate);
                if (away < nearest || (away == nearest && earlier(onTheRoad, best))) {
                    nearest = away;
                    best = onTheRoad;
                }
            }
            if (best != null && nearest <= (long) GATE_ONTO_A_ROAD * GATE_ONTO_A_ROAD) {
                ways.add(best);
            }
        }
        return ways;
    }

    /**
     * How far a gate's own road may be before the gate opens onto nothing: 24.
     *
     * <p>Snapping a gate onto the nearest opened run is what keeps an arrival off
     * the grass, and past a couple of dozen blocks the snap stops being a
     * correction and becomes a different place entirely. A gate that far from any
     * street is one the roads never reached, and it is not where anybody comes in.
     */
    private static final int GATE_ONTO_A_ROAD = 24;

    /**
     * The far end of every opened run: a village's roads to somewhere else.
     *
     * <p><em>Far</em> end meaning the end further from the middle of the town,
     * which is the end that points away from everybody's houses — a caravan that
     * came in at the near end would materialize in the square. Every run rather
     * than only the longest, because the longest street of a sprawling town is by
     * construction the one that runs furthest from the inn, and choosing it is
     * how an arrival came to be two hundred and seventy-seven blocks from the
     * building it was calling at.
     */
    private static List<SimPos> theEndsOfTheStreets(Settlement settlement,
                                                    List<PathNetwork.Segment> runs,
                                                    List<Integer> open) {
        SimPos center = settlement.center();
        List<SimPos> ends = new java.util.ArrayList<>();
        for (int i : open) {
            List<SimPos> along = runs.get(i).positions();
            SimPos head = along.getFirst();
            SimPos tail = along.getLast();
            long headOut = head.horizontalDistanceSq(center);
            long tailOut = tail.horizontalDistanceSq(center);
            if (headOut > tailOut) {
                ends.add(head);
            } else if (tailOut > headOut) {
                ends.add(tail);
            } else {
                ends.add(earlier(head, tail) ? head : tail);
            }
        }
        return ends;
    }

    /**
     * The shortest walk from a column on the streets to the inn's doorstep,
     * along the streets, or infinity when they do not join up.
     *
     * <p>Along the network and not as the crow flies, because the question is
     * whether somebody told a caravan has called can <em>get</em> to it. A town
     * built round a bluff or split by a river has edges that are sixty blocks
     * from the inn and four hundred blocks' walk from it, and an arrival chosen
     * on the straight line would put the wagon at one of them.
     *
     * <p>The graph is the opened runs, joined at their ends: each run is an edge
     * of its own length between its two endpoints, and two endpoints that land on
     * the same spot — or on each other's carriageway, which is the T-junction a
     * lane makes into a street — are the same node. Dijkstra over a few hundred
     * of those is nothing, and it is asked once per caravan window rather than
     * per step.
     *
     * <p>The last leg is the path from the road to the door, straight, because
     * that is exactly what it is: a building is joined to the network at the
     * nearest point on it and the walk up its own path is not a street.
     */
    public static double walkToTheInn(Settlement settlement,
                                      List<PathNetwork.Segment> runs,
                                      List<Integer> open, SimPos from, SimPos to) {
        if (from == null || to == null) {
            return Double.POSITIVE_INFINITY;
        }
        // Where the inn meets the streets, and how far it stands off them.
        SimPos onTheRoad = null;
        double offTheRoad = Double.POSITIVE_INFINITY;
        for (int i : open) {
            SimPos candidate = runs.get(i).nearestTo(to);
            double away = candidate.horizontalDistance(to);
            if (away < offTheRoad) {
                offTheRoad = away;
                onTheRoad = candidate;
            }
        }
        if (onTheRoad == null) {
            return Double.POSITIVE_INFINITY;
        }
        // Two nodes to a run -- its ends -- so node 2k and 2k+1 are the ends of
        // the k'th opened run.
        int n = open.size() * 2;
        double[] best = new double[n];
        java.util.Arrays.fill(best, Double.POSITIVE_INFINITY);
        SimPos[] at = new SimPos[n];
        for (int k = 0; k < open.size(); k++) {
            PathNetwork.Segment run = runs.get(open.get(k));
            at[2 * k] = run.from();
            at[2 * k + 1] = run.to();
            // Seeded from wherever the walk starts, on every run it stands on:
            // a snapped gate is a point part way along one, and a street end is
            // an end of one, and this covers both without asking which.
            double onto = run.nearestTo(from).horizontalDistance(from);
            if (onto <= run.paveHalf()) {
                best[2 * k] = Math.min(best[2 * k], from.horizontalDistance(run.from()));
                best[2 * k + 1] = Math.min(best[2 * k + 1], from.horizontalDistance(run.to()));
            }
        }
        boolean[] settled = new boolean[n];
        for (int done = 0; done < n; done++) {
            int here = -1;
            for (int i = 0; i < n; i++) {
                if (!settled[i] && best[i] < Double.POSITIVE_INFINITY
                        && (here < 0 || best[i] < best[here])) {
                    here = i;
                }
            }
            if (here < 0) {
                break;
            }
            settled[here] = true;
            PathNetwork.Segment run = runs.get(open.get(here / 2));
            // Along this run to its other end.
            int other = here % 2 == 0 ? here + 1 : here - 1;
            relax(best, here, other, run.length());
            // And across to anything standing on the same spot. Zero, because a
            // junction is a place two streets share rather than a walk between
            // them.
            for (int i = 0; i < n; i++) {
                if (i / 2 == here / 2) {
                    continue;
                }
                PathNetwork.Segment neighbour = runs.get(open.get(i / 2));
                if (at[i].horizontalDistance(at[here])
                        <= Math.max(run.paveHalf(), neighbour.paveHalf())) {
                    relax(best, here, i, 0.0);
                }
            }
        }
        // And in from whichever end of whichever run the inn's own path leaves.
        double shortest = Double.POSITIVE_INFINITY;
        for (int k = 0; k < open.size(); k++) {
            PathNetwork.Segment run = runs.get(open.get(k));
            if (run.nearestTo(onTheRoad).horizontalDistance(onTheRoad) > run.paveHalf()) {
                continue;
            }
            shortest = Math.min(shortest,
                    best[2 * k] + onTheRoad.horizontalDistance(run.from()));
            shortest = Math.min(shortest,
                    best[2 * k + 1] + onTheRoad.horizontalDistance(run.to()));
            // Straight down the street when both ends of the walk are on the same
            // one. The graph above is the runs joined at their ends, so without
            // this a gate and an inn a few paces apart on one long street are
            // walked out to a junction and back -- which is not a longer route,
            // it is a route nobody would take, and on a ring road it doubles the
            // answer.
            if (run.nearestTo(from).horizontalDistance(from) <= run.paveHalf()) {
                shortest = Math.min(shortest, from.horizontalDistance(onTheRoad));
            }
        }
        return shortest + offTheRoad;
    }

    private static void relax(double[] best, int from, int to, double weight) {
        if (best[from] + weight < best[to]) {
            best[to] = best[from] + weight;
        }
    }

    /**
     * How far it is from a column on this town's streets to the inn, on foot.
     *
     * <p>{@link #walkToTheInn(Settlement, List, List, SimPos, SimPos)} for a
     * caller that has a settlement and nothing else — the tests, and anything
     * wanting to check the promise {@link #WALK_TO_THE_INN} makes rather than
     * take it on trust.
     */
    public static double walkToTheInn(Settlement settlement, SimPos from) {
        if (settlement == null || settlement.paths() == null) {
            return Double.POSITIVE_INFINITY;
        }
        PathNetwork paths = settlement.paths();
        List<PathNetwork.Segment> runs = paths.segments();
        List<Integer> open = new java.util.ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            if (paths.isOpened(i) && !paths.isUnwalkable(i)) {
                open.add(i);
            }
        }
        return walkToTheInn(settlement, runs, open, from, theInnDoorstep(settlement));
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
