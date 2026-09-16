package com.civilization.sim.settlement;

import com.civilization.sim.culture.Layouts;
import com.civilization.sim.culture.TownPlan;
import com.civilization.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Which stretches of a town's network the town actually owes.
 *
 * <p>A plan is a claim about a town of two hundred and fifty-six. A settlement
 * of fifteen buildings is not that town and must not read as it: a complete ring
 * road with empty grass inside and out is a road around nothing, and that is
 * exactly what a seeded village came out as, because seeding paid for
 * <em>every</em> stretch the plan drew and a founded town opened them by the
 * clock in index order regardless of whether anything stood beside them.
 *
 * <p>So a street is opened when it is needed, and "needed" is three rules:
 *
 * <ol>
 *   <li><strong>It fronts something.</strong> A standing building, or a plot
 *       already in the build queue — ordered ground is ground the town has
 *       spoken for, the same rule the road keepout already keeps. A lane counts
 *       always: a track exists only because a door needed joining.</li>
 *   <li><strong>It is on the way to the heart.</strong> The shortest route
 *       along the network from a fronted stretch to the middle of town. A
 *       street nobody fronts but everybody walks is a street.</li>
 *   <li><strong>It closes a ring that has filled.</strong> When every stretch
 *       of a street that fronts a plot of the plan has been opened, the gaps
 *       between them are opened too — the circuit completes as the circuit
 *       fills, rather than being drawn on day one and waited for.</li>
 * </ol>
 *
 * <p><strong>This is about opening, not about planning.</strong> The network is
 * routed exactly as it was and stretches keep their indices, because an index is
 * how the save, the paving layer and the lamps all name a stretch. What changes
 * is only which of them the town has paid for.
 *
 * <p>Held per network and memoized, because the answer is asked several times a
 * step — the clock asks, the foreman asks, and the crew asks again when it
 * finishes — and the route half of it is a shortest-path search over the whole
 * town.
 */
final class StreetDemand {

    /**
     * How near a stretch a claim has to sit to be fronting it.
     *
     * <p>A setback and half a plot, near enough: {@code PlannedLayout.SETBACK}
     * stands a plot's middle thirteen blocks off its street's middle and the
     * plot reaches five either side, so a building on its own frontage is
     * eighteen from the centerline at the furthest. Twenty leaves the rounding
     * of a bending street some room and no more — this is deliberately not
     * {@code PathPlanner.STREET_NEAR}, which asks the much looser question of
     * whether the town has grown out as far as a stretch at all.
     */
    static final int FRONTS = 20;

    /**
     * How close two ways have to pass to be walkable between.
     *
     * <p>A lane joins a road at a point <em>along</em> it rather than at either
     * end, so a graph built from endpoints alone is a graph in which no track in
     * the town connects to anything. Splicing at a track's width is what makes
     * the junctions real; wider and two streets passing each other at a corner
     * would be treated as meeting.
     */
    private static final int JOIN = PathNetwork.TRACK_WIDTH;

    /** Mirrors {@code PathPlanner}'s stretch numbering; see its pieceKey. */
    private static final int PIECES_TO_A_STREET = 4096;

    private long signature = Long.MIN_VALUE;

    /** Stretches owed for fronting something, and the ways to the heart. */
    private Set<Integer> walked = Set.of();

    /** Every routed stretch of each street, so a filled circuit can close. */
    private Map<Integer, List<Integer>> ofStreet = Map.of();

    /** The stretches of each street that front a plot the plan drew. */
    private Map<Integer, List<Integer>> frontingOfStreet = Map.of();

    /**
     * Every stretch this town has earned, by index into the network.
     *
     * <p>Rules one and two are settled by the town's shape and are remembered
     * until it changes. Rule three depends on what is open and so is answered
     * afresh, which costs a walk of the streets and nothing more.
     */
    Set<Integer> owed(Settlement town) {
        PathNetwork network = town.paths();
        if (network == null) {
            return Set.of();
        }
        settle(town, network);
        int settledNow = network.openedCount() + network.unwalkableCount();
        if (closed != null && closedFor == settledNow) {
            return closed;
        }
        Set<Integer> out = new LinkedHashSet<>(walked);
        for (Map.Entry<Integer, List<Integer>> street : ofStreet.entrySet()) {
            List<Integer> fronting = frontingOfStreet.get(street.getKey());
            if (fronting == null || fronting.isEmpty()) {
                continue;   // nothing was ever going to be built along it
            }
            boolean filled = true;
            for (int index : fronting) {
                if (!network.isOpened(index) && !network.isUnwalkable(index)) {
                    filled = false;
                    break;
                }
            }
            if (filled) {
                out.addAll(street.getValue());
            }
        }
        closed = out;
        closedFor = settledNow;
        return out;
    }

    /**
     * The answer with the third rule folded in, and what it was folded in at.
     *
     * <p>Rule three is the only one that reads what is settled — opened, or
     * given up on as too steep — and that changes on the step a crew finishes a
     * stretch and on no other. Kept separately from {@link #walked} so that a
     * town paving a road does not
     * re-run a shortest-path search over its whole network every step, and so
     * that the several askings within one step — the clock, the foreman, the
     * crew — cost one answer between them rather than three.
     */
    private Set<Integer> closed;

    private int closedFor = -1;

    /** Whether this stretch is one the town owes. */
    boolean owes(Settlement town, int index) {
        return owed(town).contains(index);
    }

    private void settle(Settlement town, PathNetwork network) {
        long now = mark(town, network);
        if (now == signature) {
            return;
        }
        signature = now;
        closed = null;
        closedFor = -1;
        List<PathNetwork.Segment> segments = network.segments();

        // Rule one: what the town has actually claimed ground beside.
        List<SimPos> claims = new ArrayList<>();
        for (Building building : town.buildings()) {
            if (BuildPlanner.holdsGround(building.blueprintId())) {
                claims.add(building.origin());
            }
        }
        for (BuildTask queued : town.queued()) {
            if (BuildPlanner.holdsGround(queued.blueprintId())) {
                claims.add(queued.origin());
            }
        }
        Set<Integer> fronted = new LinkedHashSet<>();
        for (int i = 0; i < segments.size(); i++) {
            PathNetwork.Segment run = segments.get(i);
            if (run.width() <= PathNetwork.TRACK_WIDTH) {
                fronted.add(i);   // a track exists because a door needed one
                continue;
            }
            for (SimPos claim : claims) {
                if (claim.horizontalDistance(run.nearestTo(claim)) <= FRONTS) {
                    fronted.add(i);
                    break;
                }
            }
        }

        // Rule two: and the ways from each of them back to the middle of town.
        walked = new LinkedHashSet<>(fronted);
        walked.addAll(waysToTheHeart(town, segments, fronted));

        // Rule three's bookkeeping: which stretches belong to which street, and
        // which of those the plan meant somebody to build beside.
        Map<Integer, List<Integer>> streets = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            int key = network.streetOf(i);
            if (key < 0) {
                continue;
            }
            streets.computeIfAbsent(key / PIECES_TO_A_STREET, s -> new ArrayList<>()).add(i);
        }
        ofStreet = streets;
        frontingOfStreet = frontage(town, network, streets);
    }

    /**
     * How far along each street of the plan somebody was meant to build.
     *
     * <p>Asked of the <em>plan</em> rather than of the town, which is what makes
     * rule three a statement about a circuit filling up: the ring is closed when
     * every piece of frontage it was drawn to carry has a building on it, not
     * when the town happens to have opened a lot of it.
     */
    private static Map<Integer, List<Integer>> frontage(
            Settlement town, PathNetwork network, Map<Integer, List<Integer>> streets) {
        if (streets.isEmpty() || !Layouts.isStreetsFirst(town.arrangement())) {
            return Map.of();
        }
        TownPlan plan = town.arrangement().fullPlan(town.center());
        Map<Integer, List<SimPos>> plots = new HashMap<>();
        for (TownPlan.Plot plot : plan.plots()) {
            if (plot.frontsAStreet()) {
                plots.computeIfAbsent(plot.street(), s -> new ArrayList<>()).add(plot.at());
            }
        }
        List<PathNetwork.Segment> segments = network.segments();
        Map<Integer, List<Integer>> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> street : streets.entrySet()) {
            List<SimPos> along = plots.get(street.getKey());
            if (along == null) {
                continue;
            }
            List<Integer> fronting = new ArrayList<>();
            for (int index : street.getValue()) {
                PathNetwork.Segment run = segments.get(index);
                for (SimPos plot : along) {
                    if (plot.horizontalDistance(run.nearestTo(plot)) <= FRONTS) {
                        fronting.add(index);
                        break;
                    }
                }
            }
            out.put(street.getKey(), fronting);
        }
        return out;
    }

    /**
     * The shortest way along the network from each fronted stretch to the middle.
     *
     * <p>An ordinary shortest-path search, with the one wrinkle that a network
     * of runs is not a graph until the junctions are put in: a lane meets a road
     * at whatever point along it was nearest the door, and that point is not
     * either end of the road. So every endpoint is also joined to any run
     * passing within a track's width of it, at the cost of walking to it.
     */
    private static Set<Integer> waysToTheHeart(Settlement town,
                                               List<PathNetwork.Segment> segments,
                                               Set<Integer> fronted) {
        if (segments.isEmpty() || fronted.isEmpty()) {
            return Set.of();
        }
        List<SimPos> nodes = new ArrayList<>();
        Map<Long, Integer> byColumn = new HashMap<>();
        for (PathNetwork.Segment run : segments) {
            for (SimPos end : List.of(run.from(), run.to())) {
                byColumn.computeIfAbsent(column(end), key -> {
                    nodes.add(end);
                    return nodes.size() - 1;
                });
            }
        }

        record Step(int to, double cost, int run) { }
        List<List<Step>> edges = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            edges.add(new ArrayList<>());
        }
        for (int i = 0; i < segments.size(); i++) {
            PathNetwork.Segment run = segments.get(i);
            int from = byColumn.get(column(run.from()));
            int to = byColumn.get(column(run.to()));
            if (from == to) {
                continue;
            }
            double length = run.length();
            edges.get(from).add(new Step(to, length, i));
            edges.get(to).add(new Step(from, length, i));
        }
        // The junctions. A node standing on a run it is not an end of reaches
        // both of that run's ends by walking along it, and the run is spent
        // either way — a stretch is opened whole or not at all.
        //
        // Through a coarse grid of which runs pass near which ground, rather
        // than by asking every node about every run. A grown town has six
        // hundred runs and twelve hundred ends, that product is most of a
        // million distance tests, and this is computed whenever the town builds
        // anything — which is most steps of its life.
        Map<Long, List<Integer>> passing = new HashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            List<SimPos> along = segments.get(i).positions();
            for (int step = 0; step < along.size(); step += JOIN) {
                mark(passing, along.get(step), i);
            }
            mark(passing, along.get(along.size() - 1), i);
        }
        for (int n = 0; n < nodes.size(); n++) {
            SimPos at = nodes.get(n);
            for (int i : nearby(passing, at)) {
                PathNetwork.Segment run = segments.get(i);
                int from = byColumn.get(column(run.from()));
                int to = byColumn.get(column(run.to()));
                if (n == from || n == to) {
                    continue;
                }
                SimPos meets = run.nearestTo(at);
                if (at.horizontalDistance(meets) > JOIN) {
                    continue;
                }
                edges.get(n).add(new Step(from, meets.horizontalDistance(run.from()), i));
                edges.get(n).add(new Step(to, meets.horizontalDistance(run.to()), i));
            }
        }

        SimPos heart = town.center();
        int start = 0;
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            double away = heart.horizontalDistance(nodes.get(i));
            if (away < nearest) {
                nearest = away;
                start = i;
            }
        }

        double[] cost = new double[nodes.size()];
        int[] cameFrom = new int[nodes.size()];
        int[] cameBy = new int[nodes.size()];
        java.util.Arrays.fill(cost, Double.MAX_VALUE);
        java.util.Arrays.fill(cameFrom, -1);
        java.util.Arrays.fill(cameBy, -1);
        cost[start] = 0;
        // The cost travels IN the queue entry rather than being read back out of
        // the array. A heap ordered by a field the algorithm mutates while the
        // entry is sitting in it is a heap whose invariant is broken every time
        // a shorter way is found — it will still terminate, and it will answer
        // with routes that are not the shortest ones.
        PriorityQueue<double[]> open = new PriorityQueue<>(
                (a, b) -> Double.compare(a[1], b[1]));
        open.add(new double[] {start, 0});
        boolean[] settled = new boolean[nodes.size()];
        while (!open.isEmpty()) {
            int at = (int) open.poll()[0];
            if (settled[at]) {
                continue;
            }
            settled[at] = true;
            for (Step step : edges.get(at)) {
                double through = cost[at] + step.cost();
                if (through < cost[step.to()]) {
                    cost[step.to()] = through;
                    cameFrom[step.to()] = at;
                    cameBy[step.to()] = step.run();
                    open.add(new double[] {step.to(), through});
                }
            }
        }

        Set<Integer> onTheWay = new LinkedHashSet<>();
        for (int index : fronted) {
            PathNetwork.Segment run = segments.get(index);
            int from = byColumn.get(column(run.from()));
            int to = byColumn.get(column(run.to()));
            int at = cost[from] <= cost[to] ? from : to;
            if (cost[at] == Double.MAX_VALUE) {
                continue;   // nothing of this stretch reaches the middle yet
            }
            int guard = 0;
            while (at != start && cameFrom[at] >= 0 && guard++ < segments.size() + 2) {
                onTheWay.add(cameBy[at]);
                at = cameFrom[at];
            }
        }
        return onTheWay;
    }

    private static long column(SimPos at) {
        return ((long) at.x() << 32) ^ (at.z() & 0xFFFFFFFFL);
    }

    /** How coarse the grid of "which runs pass near here" is, in blocks. */
    private static final int CELL = 8;

    private static long cell(int x, int z) {
        return ((long) Math.floorDiv(x, CELL) << 32)
                ^ (Math.floorDiv(z, CELL) & 0xFFFFFFFFL);
    }

    private static void mark(Map<Long, List<Integer>> grid, SimPos at, int run) {
        List<Integer> here = grid.computeIfAbsent(cell(at.x(), at.z()),
                key -> new ArrayList<>());
        if (here.isEmpty() || here.get(here.size() - 1) != run) {
            here.add(run);   // a run crosses one cell many times over
        }
    }

    /** Every run recorded in this cell or any of the eight around it. */
    private static Set<Integer> nearby(Map<Long, List<Integer>> grid, SimPos at) {
        Set<Integer> out = new LinkedHashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<Integer> here = grid.get(
                        cell(at.x() + dx * CELL, at.z() + dz * CELL));
                if (here != null) {
                    out.addAll(here);
                }
            }
        }
        return out;
    }

    /**
     * What the answer depends on, as one number.
     *
     * <p>The network's length and the town's claims. Deliberately not what is
     * <em>opened</em>: that changes every step a crew works, and only rule three
     * reads it — which is answered afresh each time and costs a walk.
     */
    private static long mark(Settlement town, PathNetwork network) {
        long standing = 0;
        for (Building building : town.buildings()) {
            if (BuildPlanner.holdsGround(building.blueprintId())) {
                standing++;
            }
        }
        return ((long) network.segments().size() << 40)
                ^ (standing << 20) ^ town.queued().size();
    }
}
