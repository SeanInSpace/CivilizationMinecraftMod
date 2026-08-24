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
        List<SimPos> loop = convex(points);
        if (loop.size() < 3 || maxEdge <= 0) {
            return loop;
        }
        List<SimPos> spare = new ArrayList<>(dedupe(points));
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
                // legs it replaces would otherwise justify. Without this the
                // loop happily doubles back on itself.
                double direct = distance(from, to);
                double detour = distance(from, best) + distance(best, to);
                if (detour >= direct * 2.0) {
                    continue;
                }
                loop.add(i + 1, best);
                spare.remove(best);
                dug = true;
                break;
            }
        }
        return loop;
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
