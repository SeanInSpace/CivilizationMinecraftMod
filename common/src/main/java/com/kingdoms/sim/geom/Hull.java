package com.kingdoms.sim.geom;

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

        boolean dug = true;
        while (dug && !spare.isEmpty()) {
            dug = false;
            for (int i = 0; i < loop.size() && !spare.isEmpty(); i++) {
                SimPos from = loop.get(i);
                SimPos to = loop.get((i + 1) % loop.size());
                if (distance(from, to) <= maxEdge) {
                    continue;
                }
                SimPos best = nearestTo(spare, from, to);
                if (best == null) {
                    continue;
                }
                // Only if going by way of this point is shorter than the two
                // legs it replaces would otherwise justify. This keeps the loop
                // from reaching halfway across the town for one stray plot; it
                // does NOT keep the loop from crossing itself, which it was
                // once claimed to do and never did.
                double direct = distance(from, to);
                double detour = distance(from, best) + distance(best, to);
                if (detour >= direct * 2.0) {
                    continue;
                }
                // And only if the two new legs cross nothing. A length ratio
                // says how far the detour goes, never where it goes through --
                // so a point in the middle of town passed the old test happily,
                // the line dug in to reach it, and crossed its own far side on
                // the way. One measured ring: 68 vertices, 2758 posts round a
                // 289x285 town, drawn as nested boxes, blind corridors and two
                // full-width walls straight through the middle. The town was
                // not walled. It was partitioned.
                if (wouldCross(loop, i, best)) {
                    continue;
                }
                // And only if neither leg is drawn through somebody's plot.
                // See the overload's javadoc: the two rules above are about the
                // loop and about the points, and a building is neither -- its
                // corners can sit happily inside a line that runs across its
                // floor.
                if (crossesKeepout(from, best, keepouts)
                        || crossesKeepout(best, to, keepouts)) {
                    continue;
                }
                // And only if nothing ends up outside. This is the rule that
                // makes the loop a wall rather than a tracing of the plots: a
                // point already inside the line does not want visiting, and
                // reaching in to touch it drags the line through the town and
                // leaves the plots on either side of the new leg out in the
                // open. Digging into an empty bay excludes nobody and is
                // exactly what the concave hull is for; digging into the middle
                // of a town excludes its neighbors, and is now refused.
                //
                // Without it the loop stayed simple and still went wrong: a
                // measured ring came back with a corridor of wall running deep
                // into the town between the houses and back out again.
                loop.add(i + 1, best);
                if (excludesAny(loop, all)) {
                    loop.remove(i + 1);
                    continue;
                }
                spare.remove(best);
                dug = true;
                break;
            }
        }
        return loop;
    }

    /**
     * Whether this leg is drawn across the ground any of these plots stands on.
     *
     * <p>Visible beyond the dig loop because this property is asserted of a
     * finished line as well as enforced while one is drawn, and a rule with two
     * spellings is a rule with a gap between them.
     */
    public static boolean crossesKeepout(SimPos from, SimPos to,
                                         List<Keepout> keepouts) {
        for (Keepout square : keepouts) {
            double half = square.half() - KEEPOUT_SLACK;
            if (half <= 0) {
                continue;   // a plot narrower than a block cannot be crossed
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

    /**
     * Whether splitting this edge around a point would put a leg across the
     * loop.
     *
     * <p>Every edge is checked but the one being replaced, and legs that merely
     * share an endpoint with an edge are joins rather than crossings.
     */
    private static boolean wouldCross(List<SimPos> loop, int edge, SimPos best) {
        SimPos from = loop.get(edge);
        SimPos to = loop.get((edge + 1) % loop.size());
        for (int j = 0; j < loop.size(); j++) {
            if (j == edge) {
                continue;   // this is the edge the two new legs replace
            }
            SimPos p = loop.get(j);
            SimPos q = loop.get((j + 1) % loop.size());
            if (crosses(from, best, p, q) || crosses(best, to, p, q)) {
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

    private static SimPos nearestTo(List<SimPos> candidates, SimPos from, SimPos to) {
        SimPos best = null;
        double closest = Double.MAX_VALUE;
        double midX = (from.x() + to.x()) / 2.0;
        double midZ = (from.z() + to.z()) / 2.0;
        for (SimPos candidate : candidates) {
            double dx = candidate.x() - midX;
            double dz = candidate.z() - midZ;
            double away = dx * dx + dz * dz;
            if (away < closest) {
                closest = away;
                best = candidate;
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
