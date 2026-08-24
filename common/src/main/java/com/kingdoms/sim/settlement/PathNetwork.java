package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The ways a settlement has trodden, remembered.
 *
 * <p>Paths used to be drawn and forgotten: each building ran its own straight
 * line to the hall, no track knew another existed, and the whole network was an
 * in-memory set that emptied on restart. Nothing could branch off a path
 * because nothing could see one.
 *
 * <p>So the network is state, and it is stored as <em>segments</em> rather than
 * paved blocks — two endpoints each, always axis-aligned. That is what makes it
 * cheap enough to persist, what lets a new building find the nearest existing
 * way to join, what lets the perimeter cut its gates where the roads actually
 * leave town, and what lets a repair sweep re-walk a stretch and see how much
 * of it has grown over.
 */
public final class PathNetwork {

    /**
     * One straight run of path. Always axis-aligned: a corner is two segments,
     * which is what keeps the network at right angles and leaves square ground
     * between the roads for buildings to sit on.
     */
    public record Segment(SimPos from, SimPos to) {

        public Segment {
            if (from.x() != to.x() && from.z() != to.z()) {
                throw new IllegalArgumentException(
                        "a path segment runs along one axis: " + from + " -> " + to);
            }
        }

        /** Blocks from end to end, inclusive of both. */
        public int length() {
            return Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) + 1;
        }

        /** Every column this run covers, in walk order. */
        public List<SimPos> positions() {
            List<SimPos> out = new ArrayList<>(length());
            int stepX = Integer.signum(to.x() - from.x());
            int stepZ = Integer.signum(to.z() - from.z());
            int x = from.x();
            int z = from.z();
            for (int i = 0; i < length(); i++) {
                out.add(new SimPos(x, from.y(), z));
                x += stepX;
                z += stepZ;
            }
            return out;
        }

        /** The point on this run closest to the given position. */
        public SimPos nearestTo(SimPos pos) {
            if (from.x() == to.x()) {
                int lo = Math.min(from.z(), to.z());
                int hi = Math.max(from.z(), to.z());
                return new SimPos(from.x(), from.y(), Math.clamp(pos.z(), lo, hi));
            }
            int lo = Math.min(from.x(), to.x());
            int hi = Math.max(from.x(), to.x());
            return new SimPos(Math.clamp(pos.x(), lo, hi), from.y(), from.z());
        }
    }

    private final List<Segment> segments = new ArrayList<>();

    /**
     * Building origins already joined to the network.
     *
     * <p>Persisted alongside the segments, so a restart does not re-plan every
     * road in the town — the old in-memory set forgot, and a reload quietly
     * re-laid the lot.
     */
    private final Set<SimPos> joined = new LinkedHashSet<>();

    public PathNetwork() {
    }

    public PathNetwork(List<Segment> segments, List<SimPos> joined) {
        this.segments.addAll(segments);
        this.joined.addAll(joined);
    }

    public List<Segment> segments() {
        return List.copyOf(segments);
    }

    public List<SimPos> joined() {
        return List.copyOf(joined);
    }

    /**
     * How far this spot is from the nearest stretch of road, or -1 with no roads.
     *
     * <p>Used when siting a building, so a town grows along its own streets
     * instead of scattering to whichever ring slot came up next.
     */
    public double distanceToRoad(SimPos pos) {
        if (segments.isEmpty()) {
            return -1;
        }
        double closest = Double.MAX_VALUE;
        for (Segment segment : segments) {
            closest = Math.min(closest, pos.horizontalDistance(segment.nearestTo(pos)));
        }
        return closest;
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /** Total blocks of road, for reports. */
    public int totalLength() {
        return segments.stream().mapToInt(Segment::length).sum();
    }

    public boolean hasJoined(SimPos buildingOrigin) {
        return joined.contains(buildingOrigin);
    }

    public void markJoined(SimPos buildingOrigin) {
        joined.add(buildingOrigin);
    }

    /** Forgets a building, so a demolished plot's road can be re-planned. */
    public void forget(SimPos buildingOrigin) {
        joined.remove(buildingOrigin);
    }

    public void add(Segment segment) {
        if (segment.from().equals(segment.to()) || segments.contains(segment)) {
            return;
        }
        segments.add(segment);
    }

    /**
     * The closest point on any existing path, or null if there are none.
     *
     * <p>This is the whole of "favour extending from a path that is already
     * there": a new building joins the nearest way rather than driving its own
     * line to the centre, so the roads grow as a branching network instead of a
     * star with every spoke overlapping at the hall.
     */
    public SimPos nearestPoint(SimPos pos) {
        SimPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Segment segment : segments) {
            SimPos candidate = segment.nearestTo(pos);
            long distance = candidate.horizontalDistanceSq(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * The point on the network reaching furthest in a direction, or null if
     * there are no roads at all.
     *
     * <p>This is how the palisade decides where to put its gates. Asking for a
     * literal crossing of the ring was the obvious thing and the wrong one: the
     * wall is staked around everything already standing, so the roads inside it
     * cross nothing, and every gate fell back to the middle of its side — a
     * door where nobody walks. Where the roads <em>reach</em> answers for a town
     * whose streets stop short of its wall as well as one whose roads run out
     * through it.
     *
     * @param dx 1 for east, -1 for west, 0 to ignore the axis
     * @param dz 1 for south, -1 for north, 0 to ignore the axis
     */
    public SimPos reachToward(int dx, int dz) {
        SimPos best = null;
        long bestScore = Long.MIN_VALUE;
        for (Segment segment : segments) {
            for (SimPos end : List.of(segment.from(), segment.to())) {
                long score = (long) end.x() * dx + (long) end.z() * dz;
                if (score > bestScore) {
                    bestScore = score;
                    best = end;
                }
            }
        }
        return best;
    }
}
