package com.civilization.sim.geom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wrapping a scatter of points in a loop, tightly or loosely.
 *
 * <p>Plain planar geometry on the x/z plane — heights are carried along but
 * never compared, because a wall's shape is a question about the ground plan
 * and its height is a question about the terrain it crosses.
 *
 * <p>A convex hull around a town's buildings is a poor wall: one outlying farm
 * drags the whole line out around it and the town ends up fortifying a large
 * empty field. The concave hull is the fix, and the single number that controls
 * it is how long an edge is allowed to be before the loop is expected to come
 * in and follow the buildings more closely.
 */
public final class Hull {

    private Hull() {
    }

    /**
     * A square of ground the loop may not be drawn across — a building's plot.
     *
     * <p>A half-width about an origin rather than four corners, because that is
     * the shape the town reserves ground in: {@code BuildPlanner.plotSpanOf}
     * gives a span and a building stands in the middle of it.
     */
    public record Keepout(int x, int z, double half) {
    }

    /**
     * How far inside a keepout's edge the line has to come before it counts as
     * crossing the plot rather than running along it.
     *
     * <p>A whole block, and it is not a fudge. The points this hull wraps
     * <em>are</em> the plots' corners, so a loop drawn through them touches
     * every square it is built from — testing for contact would refuse the
     * hull its own vertices. What a wall must not do is pass through the ground
     * a building stands on, and that is a crossing of the interior.
     *
     * <p>A block rather than half of one because of where those corners sit. A
     * plot is an odd span about an origin, so its true half-width is something
     * and a half, while the corner offered to the hull is the whole number
     * below — half a block of slack lands exactly on the corner ring and
     * {@code Ways.distanceToSquare} counts a run along a face as entering it,
     * which is the refusal this constant exists to avoid. The block of ground
     * given up is the outermost ring of the plot, where a wall running along a
     * building's edge is a wall along a building's edge and not a fence through
     * its floor.
     */
    private static final double KEEPOUT_SLACK = 1.0;

    /**
     * The tightest loop containing every point, corners only.
     *
     * <p>Andrew's monotone chain: sort by x then z, sweep once for the lower
     * boundary and once for the upper, dropping any point that turns the wrong
     * way. Runs in one sort and two passes and has no tuning in it at all.
     *
     * <p>Collinear points are dropped — a wall gains nothing from a vertex in
     * the middle of a straight run, and {@code ringPositions} walks the segment
     * either way.
     */
    public static List<SimPos> convex(List<SimPos> points) {
        List<SimPos> sorted = new ArrayList<>(dedupe(points));
        if (sorted.size() < 3) {
            return sorted;
        }
        sorted.sort(Comparator.comparingInt(SimPos::x).thenComparingInt(SimPos::z));

        List<SimPos> lower = sweep(sorted);
        List<SimPos> reversed = new ArrayList<>(sorted);
        java.util.Collections.reverse(reversed);
        List<SimPos> upper = sweep(reversed);

        // Each sweep ends where the other begins, so drop the shared ends.
        lower.removeLast();
        upper.removeLast();
        lower.addAll(upper);
        return lower;
    }

    /**
     * A loop that follows the points more closely than the convex hull does.
     *
     * <p>Starts from the convex hull and digs in: any edge longer than
     * {@code maxEdge} looks for the point nearest its middle that is not
     * already on the loop, and splits the edge around it — provided doing so
     * genuinely shortens the way, which is what stops the loop folding back
     * through itself in a spiral. Repeats until no edge can be improved.
     *
     * <p>This is the α-shape family's practical cousin. A true α-shape wants a
     * Delaunay triangulation to be exact about which edges survive; digging in
     * from the convex hull reaches the same shape for the case that matters —
     * a scatter of building plots with a few outliers — in a fraction of the
     * code, and the parameter means the same thing.
     *
     * @param maxEdge how long a straight run may be before the loop is expected
     *                to come in and follow the points; smaller is tighter
     */
    public static List<SimPos> concave(List<SimPos> points, int maxEdge) {
        return concave(points, maxEdge, List.of());
    }

    /**
     * The same loop, forbidden to be drawn across any of these squares.
     *
     * <p>The third rule of the wall, and the one the other two only nearly
     * imply. "Nothing may cross" is about the loop against itself and "nothing
     * may end up outside" is about the points; between them a leg may still be
     * dug straight through a building, because the plot's corners stay inside
     * the loop while its middle is under the line. A wall staked through
     * somebody's house is not a wall with a thick bit in it — it is a house
     * with a fence in the kitchen, and the only reason it has ever read as
     * closed is that a building's own wall stops people walking through it.
     *
     * <p><strong>And a crossing is repaired, not merely refused.</strong> Every
     * other rule about keepouts — here, in {@code PerimeterPlanner.pushOut}, in
     * {@code relax} — turns down a <em>move</em> that would cross a plot, and
     * none of them undoes a crossing the convex hull started with. So a leg is
     * split when it is drawn across a plot as well as when it is too long: by
     * digging in to a point the town owns, and failing that by taking the line
     * out round the crossed plot's own corners. That second way is the only one
     * available for ground the town has merely <em>ordered</em>, which is a
     * keepout and deliberately not a point the ring must enclose, and so offers
     * no corner to dig in to; two thirds of the crossings measured over the grown
     * fixtures were exactly that.
     *
     * @param keepouts ground the line may not cross — the plots the town has
     *                 reserved, at the spans it reserved them
     */
    public static List<SimPos> concave(List<SimPos> points, int maxEdge,
                                       List<Keepout> keepouts) {
        List<SimPos> loop = convex(points);
        if (loop.size() < 3 || maxEdge <= 0) {
            return loop;
        }
        List<SimPos> all = dedupe(points);
        List<SimPos> spare = new ArrayList<>(all);
        spare.removeAll(loop);

        // What each leg answered about the plots, so that a line whose vertices
        // are shuffled thirty times over is not scanned against every plot in
        // town thirty times over. The keepouts do not change while this runs, so
        // a leg's answer cannot go stale; only the indices move, which is why the
        // two endpoints are the key rather than the position in the loop.
        java.util.Map<Leg, Boolean> answered = new java.util.HashMap<>();
        int repairs = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < loop.size(); i++) {
                SimPos from = loop.get(i);
                SimPos to = loop.get((i + 1) % loop.size());
                // Two reasons to split a leg, and they are not the same reason.
                // A leg longer than maxEdge is a leg that could follow the town
                // more closely; a leg drawn across somebody's plot is a leg that
                // is WRONG, at any length at all.
                //
                // Only the first used to be asked, and the second was the fault
                // this loop could not see: every rule about keepouts here and in
                // PerimeterPlanner refuses a MOVE across a plot, and none of them
                // repairs a crossing the convex hull started with. So a building
                // — or, far more often, a plot the town had merely ordered, since
                // those are keepouts and not hull points — lying under a starting
                // leg shorter than maxEdge was crossed with nothing to correct it.
                // Measured on the grown fixtures at 36 plots with a post inside
                // them, every one of them ordered; 1 after this.
                boolean fouled = repairs < REPAIR_CAP
                        && fouled(from, to, keepouts, answered);
                boolean overlong = distance(from, to) > maxEdge;
                if (!fouled && !overlong) {
                    continue;
                }
                if (!fouled && spare.isEmpty()) {
                    continue;   // nothing left to dig in to, and nothing wrong
                }
                for (List<SimPos> insertion
                        : waysRound(loop, i, spare, keepouts, fouled, maxEdge)) {
                    if (!take(loop, i, insertion, all, keepouts)) {
                        continue;
                    }
                    insertion.forEach(spare::remove);
                    if (fouled) {
                        repairs++;
                    }
                    changed = true;
                    break;
                }
                if (changed) {
                    break;
                }
            }
        }
        return loop;
    }

    /** One straight run of the line, as something that can be remembered. */
    private record Leg(SimPos from, SimPos to) {
    }

    /** Whether this leg is drawn across a plot, remembering what it answered. */
    private static boolean fouled(SimPos from, SimPos to, List<Keepout> keepouts,
                                  java.util.Map<Leg, Boolean> answered) {
        if (keepouts.isEmpty()) {
            return false;
        }
        return answered.computeIfAbsent(new Leg(from, to),
                leg -> crossesKeepout(leg.from(), leg.to(), keepouts));
    }

    /**
     * Inserts these points into the leg after {@code at} if the result is a line
     * a wall could be built on, and says whether it did.
     *
     * <p>Three rules, and every one of them has been learned from a ring that was
     * measured rather than from first principles.
     *
     * <p><strong>Nothing crosses the loop.</strong> A length ratio says how far a
     * detour goes, never where it goes through — so a point in the middle of town
     * passed the old test happily, the line dug in to reach it, and crossed its
     * own far side on the way. One measured ring: 68 vertices, 2758 posts round a
     * 289x285 town, drawn as nested boxes, blind corridors and two full-width
     * walls straight through the middle. The town was not walled. It was
     * partitioned.
     *
     * <p><strong>No new leg is drawn through a plot.</strong> The two other rules
     * are about the loop and about the points, and a building is neither — its
     * corners can sit happily inside a line that runs across its floor. This is
     * also what makes a repair a repair: a split that left the crossing where it
     * was would be vertices bought for nothing.
     *
     * <p><strong>Nothing ends up outside.</strong> The rule that makes the loop a
     * wall rather than a tracing of the plots: a point already inside the line
     * does not want visiting, and reaching in to touch it drags the line through
     * the town and leaves the plots on either side of the new leg out in the open.
     * Digging into an empty bay excludes nobody and is exactly what the concave
     * hull is for; digging into the middle of a town excludes its neighbors.
     * Without it the loop stayed simple and still went wrong: a measured ring came
     * back with a corridor of wall running deep into the town between the houses
     * and back out again.
     */
    private static boolean take(List<SimPos> loop, int at, List<SimPos> insertion,
                                List<SimPos> all, List<Keepout> keepouts) {
        loop.addAll(at + 1, insertion);
        boolean sound = legsStayOffTheLoop(loop, at, insertion.size())
                && legsAreClear(loop, at, insertion.size(), keepouts)
                && !excludesAny(loop, all);
        if (!sound) {
            loop.subList(at + 1, at + 1 + insertion.size()).clear();
        }
        return sound;
    }

    /** Whether every leg this insertion made is clear of every plot. */
    private static boolean legsAreClear(List<SimPos> loop, int at, int added,
                                        List<Keepout> keepouts) {
        for (int leg = at; leg <= at + added; leg++) {
            if (crossesKeepout(loop.get(leg % loop.size()),
                    loop.get((leg + 1) % loop.size()), keepouts)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The ways this leg might be split, best first.
     *
     * <p>Two kinds, and a leg that is merely long is only ever offered the first.
     *
     * <p><strong>Digging in</strong> — a point the town already owns, nearest the
     * middle of the leg, which is what a concave hull is. One candidate for a long
     * leg, because a long leg is a preference and the loop is under no obligation
     * to come in; several for a fouled one, because a fouled leg that cannot be
     * split stays across somebody's floor.
     *
     * <p><strong>Going round</strong> — the corners of the crossed plot itself,
     * pushed a block clear, taken in the order the leg passes them. This is the
     * repair the dig-in cannot make: the points a hull may dig in to are the plots
     * it has to enclose, and the ground a town has merely <em>ordered</em> offers
     * no such point at all, so there is very often nothing inside the line to go
     * by way of. Two thirds of the crossings measured were exactly that case.
     * Bulging the line out round the plot cannot exclude anything — every corner
     * offered here lies outside the loop already — and it encloses the order into
     * the bargain, which is the outcome a town would want anyway.
     */
    private static List<List<SimPos>> waysRound(List<SimPos> loop, int at,
                                                List<SimPos> spare,
                                                List<Keepout> keepouts,
                                                boolean fouled, int maxEdge) {
        SimPos from = loop.get(at);
        SimPos to = loop.get((at + 1) % loop.size());
        double direct = distance(from, to);
        List<List<SimPos>> ways = new ArrayList<>();
        for (SimPos best : nearestTo(spare, from, to, fouled ? REPAIR_TRIES : 1)) {
            // Only if going by way of this point is shorter than the two legs it
            // replaces would otherwise justify. This keeps the loop from reaching
            // halfway across the town for one stray plot.
            //
            // A ratio alone is no use to a short fouled leg: twice a leg of six is
            // twelve, and the way round a nine-wide plot is longer than that. So a
            // repair may also spend a plain maxEdge of extra walking, which is the
            // length this hull already considers ordinary for one leg.
            double detour = distance(from, best) + distance(best, to);
            if (detour >= direct * 2.0 && !(fouled && detour <= direct + maxEdge)) {
                continue;
            }
            ways.add(new ArrayList<>(List.of(best)));
        }
        if (!fouled) {
            return ways;
        }
        for (Keepout square : keepouts) {
            double half = square.half() - KEEPOUT_SLACK;
            if (half <= 0 || !crossesKeepout(from, to, List.of(square))) {
                continue;
            }
            List<SimPos> round = new ArrayList<>();
            int clear = (int) Math.ceil(square.half() + 1);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    SimPos corner = new SimPos(square.x() + sx * clear, from.y(),
                            square.z() + sz * clear);
                    if (!contains(loop, corner)) {
                        round.add(corner);
                    }
                }
            }
            // In the order the leg passes them, or the line doubles back on itself
            // between two of its own new vertices.
            round.sort(Comparator.comparingDouble(
                    corner -> along(from, to, corner)));
            if (!round.isEmpty()) {
                ways.add(round);
            }
        }
        return ways;
    }

    /** How far along the leg a point falls, for ordering vertices on it. */
    private static double along(SimPos from, SimPos to, SimPos point) {
        return (double) (to.x() - from.x()) * (point.x() - from.x())
                + (double) (to.z() - from.z()) * (point.z() - from.z());
    }

    /**
     * Whether the legs this insertion made keep off the rest of the loop.
     *
     * <p>Only the new legs against everything else, rather than the whole loop
     * against itself: what was simple before an insertion stays simple except
     * where the insertion touches it, and this is called on every attempt at
     * every leg, so the difference is a walk of the loop against a walk of its
     * square. Legs that merely share an endpoint are joins rather than crossings,
     * which {@link #crosses} already knows.
     */
    private static boolean legsStayOffTheLoop(List<SimPos> loop, int at, int added) {
        for (int leg = at; leg <= at + added; leg++) {
            SimPos a = loop.get(leg % loop.size());
            SimPos b = loop.get((leg + 1) % loop.size());
            for (int j = 0; j < loop.size(); j++) {
                if (j >= at && j <= at + added) {
                    continue;   // one of the new legs; consecutive ones are joins
                }
                if (crosses(a, b, loop.get(j), loop.get((j + 1) % loop.size()))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * How many points a fouled leg may be offered before it is left crossed.
     *
     * <p>Four. The nearest point to the middle of the leg is very often a corner
     * of the very plot being crossed, which is exactly the right place to go
     * round by; when it is not — the point is on the wrong side, or going by way
     * of it would leave a neighbor outside — the next few round are worth asking,
     * and after that the plot's own corners are offered instead.
     *
     * <p>Kept small because this is inside the loop {@code PerimeterPlanner.stake}
     * runs, and a repair that cost a scan of every spare point per leg would pay
     * for a rare fault on every town that never had it.
     */
    private static final int REPAIR_TRIES = 4;

    /**
     * How many crossings one line may be repaired of.
     *
     * <p>A bound rather than a judgment. Every other insertion this loop makes
     * spends a point out of {@code spare}, so the work is finite by construction;
     * a repair may instead add corners of its own, and two dozen of those is far
     * more than any measured ring has wanted — the worst was three — so a line
     * asking for more has gone wrong in a way that wants looking at rather than
     * grinding on.
     */
    private static final int REPAIR_CAP = 24;

    /**
     * Whether this leg is drawn across the ground any of these plots stands on.
     *
     * <p>Visible beyond the dig loop because this property is asserted of a
     * finished line as well as enforced while one is drawn, and a rule with two
     * spellings is a rule with a gap between them.
     */
    public static boolean crossesKeepout(SimPos from, SimPos to,
                                         List<Keepout> keepouts) {
        // The leg's own box, worked out once rather than per square. This scan is
        // now asked of every edge of every candidate line -- a crossing is
        // repaired at any length, not only on a leg long enough to want digging
        // in -- so the arithmetic per square is what the staking costs. A grown
        // town's plots are spread over hundreds of blocks and a leg is a few
        // dozen long, so nearly every square is settled by four comparisons
        // instead of a slab clip and four point-to-segment distances.
        int lowX = Math.min(from.x(), to.x());
        int highX = Math.max(from.x(), to.x());
        int lowZ = Math.min(from.z(), to.z());
        int highZ = Math.max(from.z(), to.z());
        for (Keepout square : keepouts) {
            double half = square.half() - KEEPOUT_SLACK;
            if (half <= 0) {
                continue;   // a plot narrower than a block cannot be crossed
            }
            if (square.x() + half < lowX || square.x() - half > highX
                    || square.z() + half < lowZ || square.z() - half > highZ) {
                continue;   // the leg never comes near this plot at all
            }
            if (Ways.distanceToSquare(from.x(), from.z(), to.x(), to.z(),
                    square.x(), square.z(), half) == 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether a point lies inside the loop, edges counting as inside. */
    public static boolean contains(List<SimPos> loop, SimPos point) {
        if (loop.size() < 3) {
            return false;
        }
        boolean in = false;
        for (int i = 0, j = loop.size() - 1; i < loop.size(); j = i++) {
            SimPos a = loop.get(i);
            SimPos b = loop.get(j);
            if (onSegment(a, b, point)) {
                return true;
            }
            if ((a.z() > point.z()) != (b.z() > point.z())) {
                double crossX = (double) (b.x() - a.x()) * (point.z() - a.z())
                        / (b.z() - a.z()) + a.x();
                if (point.x() < crossX) {
                    in = !in;
                }
            }
        }
        return in;
    }

    /** Whether any of the points has ended up outside the loop. */
    private static boolean excludesAny(List<SimPos> loop, List<SimPos> points) {
        for (SimPos point : points) {
            if (!contains(loop, point)) {
                return true;
            }
        }
        return false;
    }

    /** Whether two segments meet anywhere other than at a shared endpoint. */
    private static boolean crosses(SimPos a, SimPos b, SimPos c, SimPos d) {
        if (same(a, c) || same(a, d) || same(b, c) || same(b, d)) {
            return false;   // consecutive edges of a loop always share a corner
        }
        long d1 = turn(c, d, a);
        long d2 = turn(c, d, b);
        long d3 = turn(a, b, c);
        long d4 = turn(a, b, d);
        if (d1 != 0 && d2 != 0 && d3 != 0 && d4 != 0) {
            return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0));
        }
        // Collinear or touching. A corner resting on somebody else's edge is a
        // crossing here even though it is not a proper intersection: a wall
        // that grazes its own line is still a wall with a seam in it.
        return (d1 == 0 && onSegment(c, d, a))
                || (d2 == 0 && onSegment(c, d, b))
                || (d3 == 0 && onSegment(a, b, c))
                || (d4 == 0 && onSegment(a, b, d));
    }

    private static boolean same(SimPos a, SimPos b) {
        return a.x() == b.x() && a.z() == b.z();
    }

    // --- the small print ---

    private static List<SimPos> sweep(List<SimPos> sorted) {
        List<SimPos> chain = new ArrayList<>();
        for (SimPos point : sorted) {
            while (chain.size() >= 2
                    && turn(chain.get(chain.size() - 2), chain.getLast(), point) <= 0) {
                chain.removeLast();
            }
            chain.add(point);
        }
        return chain;
    }

    /** Positive when a-b-c turns left, negative right, zero collinear. */
    private static long turn(SimPos a, SimPos b, SimPos c) {
        return (long) (b.x() - a.x()) * (c.z() - a.z())
                - (long) (b.z() - a.z()) * (c.x() - a.x());
    }

    /**
     * The {@code want} points nearest the middle of this leg, nearest first.
     *
     * <p>One pass and a list of {@code want} entries rather than a sort, because
     * {@code want} is one or four and the candidates are every plot corner in
     * town — a few thousand on a grown one. Sorting them to read the front of the
     * list would be the expensive way round.
     */
    private static List<SimPos> nearestTo(List<SimPos> candidates, SimPos from, SimPos to,
                                          int want) {
        List<SimPos> best = new ArrayList<>(want);
        List<Double> away = new ArrayList<>(want);
        double midX = (from.x() + to.x()) / 2.0;
        double midZ = (from.z() + to.z()) / 2.0;
        for (SimPos candidate : candidates) {
            double dx = candidate.x() - midX;
            double dz = candidate.z() - midZ;
            double distance = dx * dx + dz * dz;
            int at = 0;
            while (at < away.size() && away.get(at) <= distance) {
                at++;
            }
            if (at >= want) {
                continue;   // further off than everything already held
            }
            away.add(at, distance);
            best.add(at, candidate);
            if (best.size() > want) {
                away.removeLast();
                best.removeLast();
            }
        }
        return best;
    }

    private static boolean onSegment(SimPos a, SimPos b, SimPos point) {
        if (turn(a, b, point) != 0) {
            return false;
        }
        return Math.min(a.x(), b.x()) <= point.x() && point.x() <= Math.max(a.x(), b.x())
                && Math.min(a.z(), b.z()) <= point.z() && point.z() <= Math.max(a.z(), b.z());
    }

    private static double distance(SimPos a, SimPos b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** One point per column: two buildings at the same spot are one corner. */
    private static List<SimPos> dedupe(List<SimPos> points) {
        List<SimPos> out = new ArrayList<>();
        for (SimPos point : points) {
            boolean seen = false;
            for (SimPos kept : out) {
                if (kept.x() == point.x() && kept.z() == point.z()) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                out.add(point);
            }
        }
        return out;
    }
}
