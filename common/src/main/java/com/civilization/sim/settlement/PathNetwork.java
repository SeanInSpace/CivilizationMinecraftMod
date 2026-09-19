package com.civilization.sim.settlement;

import com.civilization.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /** How wide a trodden way between two buildings is, in blocks. */
    public static final int TRACK_WIDTH = 3;

    /**
     * One straight run of way, from one end to the other.
     *
     * <p>A run used to be forbidden from going diagonally, and the reason was
     * sound for the runs that existed: a track joining a building to the network
     * that cuts a corner leaves no square ground to put the next building
     * against. That is a rule about how {@code PathPlanner} <em>routes</em>, and
     * it still holds there.
     *
     * <p>It is not a rule about what a way can be. A planned street bends,
     * because a settlement with perfectly straight roads reads as a spreadsheet
     * from the air, and the ground either side of it was reserved by the same
     * plan that bent it. Forbidding the diagonal here would have meant either
     * staircasing every street into hundreds of one-block runs, or keeping
     * streets in a parallel structure that the gates, the siting, the mending
     * and the save all had to learn about separately.
     *
     * <p>Both {@link #length()} and {@link #positions()} give exactly their old
     * answers for an axis-aligned run, so nothing that existed changes.
     *
     * @param width how many blocks across the way is paved; a footpath is
     *              {@value #TRACK_WIDTH} and a carriageway is whatever the plan
     *              said
     */
    public record Segment(SimPos from, SimPos to, int width) {

        public Segment {
            if (width < 1) {
                throw new IllegalArgumentException("a way has to be at least one wide");
            }
        }

        /** An ordinary trodden track, which is what a building joins by. */
        public Segment(SimPos from, SimPos to) {
            this(from, to, TRACK_WIDTH);
        }

        /** Blocks from end to end, inclusive of both. */
        public int length() {
            return Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z())) + 1;
        }

        /**
         * Every column this run covers, in walk order.
         *
         * <p>Interpolated rather than stepped by sign, or a run that is eight
         * east and three south would set off at forty-five degrees and stop
         * short. For an axis-aligned run this is the same walk it always was.
         */
        public List<SimPos> positions() {
            int steps = length() - 1;
            List<SimPos> out = new ArrayList<>(steps + 1);
            if (steps == 0) {
                out.add(from);
                return out;
            }
            int dx = to.x() - from.x();
            int dz = to.z() - from.z();
            for (int i = 0; i <= steps; i++) {
                out.add(new SimPos(
                        from.x() + Math.round((float) dx * i / steps), from.y(),
                        from.z() + Math.round((float) dz * i / steps)));
            }
            return out;
        }

        /**
         * The point on this run closest to the given position.
         *
         * <p>Projected onto the run and clamped to its ends, which for an
         * axis-aligned run is the clamp it always did.
         */
        public SimPos nearestTo(SimPos pos) {
            double dx = to.x() - from.x();
            double dz = to.z() - from.z();
            double lenSq = dx * dx + dz * dz;
            if (lenSq == 0) {
                return from;
            }
            double t = ((pos.x() - from.x()) * dx + (pos.z() - from.z()) * dz) / lenSq;
            t = Math.max(0, Math.min(1, t));
            return new SimPos(
                    from.x() + (int) Math.round(dx * t), from.y(),
                    from.z() + (int) Math.round(dz * t));
        }

        /**
         * How far either side of the centerline this way is actually paved.
         *
         * <p>A way's {@code width} is what the plan called it; this is what the
         * ground ends up covered in, and the two are not the same number. The
         * layer paves a square of this half-width at every column of the run, so
         * a three-wide track lays a three-by-three patch and an eight-wide
         * carriageway lays five across.
         *
         * <p>It lives here because the siting code and the layer have to agree
         * about it. They did not: siting reasoned about the centerline and the
         * layer paved a block either side of it, so a building whose wall stood
         * one block off a lane was reported clear and was gravelled anyway.
         * {@code PathLayer.crossSectionAt} computes exactly this.
         */
        public int paveHalf() {
            return Math.max(1, width / 3);
        }

        /** Whether this run keeps the right-angle rule that routing must. */
        public boolean isAxisAligned() {
            return from.x() == to.x() || from.z() == to.z();
        }

        /**
         * Whether a square of this half-width would stand on this way.
         *
         * <p>The same question {@code TownPlan.Street} answers, and deliberately
         * the same arithmetic: a plot refused by the plan for standing in a road
         * must also be refused by the siting code for standing on the road the
         * town has actually laid, or the two disagree and the second one wins.
         */
        public boolean touches(SimPos at, double half) {
            return com.civilization.sim.geom.Ways.distanceToSquare(
                    from.x(), from.z(), to.x(), to.z(), at.x(), at.z(), half)
                    < width / 2.0;
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

    /**
     * Which stretches somebody has actually walked out and opened.
     *
     * <p>A road used to exist the moment it was planned: the network held the
     * line and the layer paved whatever was bare along it, wherever the town was
     * loaded, with nobody present. So a street between two houses appeared while
     * every builder in the village was somewhere else.
     *
     * <p>Opening a stretch is now a job somebody walks to — see
     * {@code PublicWorks.RoadWork}. Keeping an opened one clear of the grass
     * that grows back over it stays automatic, because that is sweeping a road
     * rather than building one, and is not worth crossing the village for.
     *
     * <p>Indices into {@link #segments}, which only ever grows, so an index
     * means the same stretch for as long as the town stands.
     */
    private final Set<Integer> opened = new LinkedHashSet<>();

    /**
     * How many buildings the town's planned streets were last laid for.
     *
     * <p>Kept so the laying happens when the town has actually grown rather than
     * on every tick. Checking whether four hundred runs are already present costs
     * a scan of four hundred runs each, and a settlement steps constantly.
     *
     * <p>Minus one rather than nought for a town that has never laid any, so
     * that a settlement with nothing built yet still lays the streets round its
     * own market instead of matching nought against nought and never starting.
     */
    private int streetsLaidFor = -1;

    public int streetsLaidFor() {
        return streetsLaidFor;
    }

    public void setStreetsLaidFor(int buildings) {
        streetsLaidFor = buildings;
    }

    public PathNetwork() {
    }

    public PathNetwork(List<Segment> segments, List<SimPos> joined) {
        this.segments.addAll(segments);
        this.joined.addAll(joined);
    }

    /**
     * Stretches the ground turned out to be too steep to open.
     *
     * <p>Judged at opening rather than at laying, because laying is decided long
     * before anybody stands there: the oracle answers from the generator's noise,
     * which is smooth where real ground is jagged, and forty-six streets of a
     * measured town passed as walkable while climbing two blocks a step or more.
     *
     * <p>Remembered rather than re-derived so that both the clock and the builder
     * agree — one of them has the world to ask and the other does not, and a
     * stretch the clock refuses must not be one a settler is still walked out to.
     * Not persisted: a reload asks the ground again, which is the right default
     * for a judgment made about terrain that may since have been read properly.
     */
    private final Set<Integer> unwalkable = new LinkedHashSet<>();

    /** Whether this stretch was found too steep to be worth opening. */
    public boolean isUnwalkable(int index) {
        return unwalkable.contains(index);
    }

    /** Records that this stretch is a stair rather than a street. */
    public void markUnwalkable(int index) {
        if (index >= 0) {
            unwalkable.add(index);
        }
    }

    /** How many stretches were refused for being too steep, for reports. */
    public int unwalkableCount() {
        return unwalkable.size();
    }

    /**
     * Planned streets already routed onto the ground, by their index in the plan.
     *
     * <p>Routing is not cheap and its answer is persisted as segments, so it
     * happens once per street. Remembering which are done is also what keeps a
     * reload from laying every street a second time down a slightly different
     * line — the network is the authority once a road exists, not the plan.
     */
    private final Set<Integer> streetsRouted = new LinkedHashSet<>();

    /**
     * Planned streets the ground refused, by their index in the plan.
     *
     * <p>A street with no walkable way through its corridor is one the town will
     * never have. That has to be remembered rather than rediscovered, because
     * something else depends on it: the plots that were to front that street are
     * fronting a road that will not exist, and siting skips them rather than
     * building a row of houses along a line on paper.
     */
    private final Set<Integer> streetsRefused = new LinkedHashSet<>();

    public boolean isStreetSettled(int street) {
        return streetsRouted.contains(street) || streetsRefused.contains(street);
    }

    public boolean isStreetRefused(int street) {
        return streetsRefused.contains(street);
    }

    /**
     * Whether nothing of this street could be built at all.
     *
     * <p>The question the siting code actually wants. A street with a gap in it
     * is still a street and the houses along the rest of it are still on a road;
     * only a street of which no stretch survived leaves its frontage facing a
     * field. Judging that per stretch rather than per street is the difference
     * between a town losing a road and a town losing a quarter of itself.
     */
    public boolean isWhollyRefused(int street, int piecesToAStreet) {
        boolean anyRefused = false;
        for (int key : streetsRefused) {
            if (key / piecesToAStreet == street) {
                anyRefused = true;
                break;
            }
        }
        if (!anyRefused) {
            return false;
        }
        for (int key : streetsRouted) {
            if (key / piecesToAStreet == street) {
                return false;
            }
        }
        return true;
    }

    public void markStreetRouted(int street) {
        streetsRouted.add(street);
    }

    public void markStreetRefused(int street) {
        streetsRefused.add(street);
    }

    public List<Integer> routedStreets() {
        return List.copyOf(streetsRouted);
    }

    public List<Integer> refusedStreets() {
        return List.copyOf(streetsRefused);
    }

    public void restoreStreets(List<Integer> routed, List<Integer> refused) {
        streetsRouted.clear();
        streetsRefused.clear();
        if (routed != null) {
            streetsRouted.addAll(routed);
        }
        if (refused != null) {
            streetsRefused.addAll(refused);
        }
    }

    /** Whether this stretch has been walked out and opened. */
    public boolean isOpened(int index) {
        return opened.contains(index);
    }

    /** Records a stretch opened. */
    public void markOpened(int index) {
        if (index >= 0) {
            opened.add(index);
        }
    }

    /** Every opened stretch, for saving. */
    public List<Integer> openedSegments() {
        return List.copyOf(opened);
    }

    /** Restores opened stretches from a save. */
    public void restoreOpened(List<Integer> indices) {
        opened.clear();
        if (indices != null) {
            opened.addAll(indices);
        }
    }

    /** How many stretches are open, which is what a town has actually built. */
    public int openedCount() {
        return opened.size();
    }

    /**
     * Which stretches the stones have actually gone down on.
     *
     * <p>Opened is not laid. A town that grows unwatched opens its streets by
     * the clock, with nobody standing on them and no blocks placed; the ground
     * only gets its gravel when somebody is there to see it. So the network
     * carries two records, and the difference between them is the difference
     * between the first drawing of a road and every visit after it.
     *
     * <p>Persisted, which was always the point: this used to live in the drawing
     * code's memory, so every server start forgot that the roads had ever been
     * drawn and laid the whole town again from the first stretch. For a living
     * town that was invisible, because re-laying a sound road writes nothing.
     * For a dead one it was a road crew.
     *
     * <p><strong>A set, and no longer a high-water mark.</strong> The mark was
     * sound while a town opened every stretch of its network in index order: a
     * number said as much as a set, and the sweep that laid them kept one number
     * for the same reason. {@code StreetDemand} ended that — a town opens the
     * streets it fronts, the ways to its square and the rings that have filled,
     * so its opened set has holes in it. Against a mark, the first hole was a
     * wall: everything past it read as un-laid forever, the backlog stopped
     * there, and the stretches beyond were drawn only by the one-a-second
     * round-robin behind it. A town you walked back into drew its streets in
     * around you one at a time for as long as you stood there, which is the
     * exact fault the backlog was written to prevent.
     *
     * <p>Insertion-ordered, so the save reads back in the order the stones went
     * down rather than in whatever order a hash chose.
     */
    private final Set<Integer> laid = new LinkedHashSet<>();

    /** How many stretches have been drawn into the world at least once. */
    public int laidCount() {
        return laid.size();
    }

    /** Whether this stretch has ever been drawn into the world. */
    public boolean isLaid(int index) {
        return laid.contains(index);
    }

    /** Records a stretch drawn. A road once drawn stays drawn. */
    public void markLaid(int index) {
        if (index >= 0 && index < segments.size()) {
            laid.add(index);
        }
    }

    /** Every stretch drawn at least once, for saving. */
    public List<Integer> laidSegments() {
        return List.copyOf(laid);
    }

    /** Restores the drawn stretches from a save. */
    public void restoreLaid(List<Integer> indices) {
        laid.clear();
        if (indices == null) {
            return;
        }
        for (int index : indices) {
            markLaid(index);
        }
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

    /**
     * Whether a road has already been run to this building.
     *
     * <p>The exact position first, which is the answer for nearly every
     * building and is free. Only on a miss is the set walked for the same plot
     * at another height — because a building joined before it was drawn was
     * joined at an estimated height that {@code setOriginY} then corrected, and
     * asking in full would report it unjoined and have the planner spend a
     * second step laying the road it already has.
     */
    public boolean hasJoined(SimPos buildingOrigin) {
        if (joined.contains(buildingOrigin)) {
            return true;
        }
        return joined.stream().anyMatch(
                at -> at.x() == buildingOrigin.x() && at.z() == buildingOrigin.z());
    }

    public void markJoined(SimPos buildingOrigin) {
        joined.add(buildingOrigin);
    }

    /**
     * Forgets a building, so a demolished plot's road can be re-planned.
     *
     * <p>Matched on the plot rather than on the whole position, for the reason
     * the upgrade path already carries: a building's x and z are its plot and
     * never move, while its y is wherever the ground turned out to be and is
     * written again by {@code setOriginY} the first time the structure is
     * actually drawn. A road is joined to a building before then — the planner
     * runs ahead of materialization every step — so the height in this set is
     * routinely the estimate rather than the answer, and forgetting by the whole
     * position would forget nothing at all and leave the town believing forever
     * that it had run a way to a door that is gone.
     */
    public void forget(SimPos buildingOrigin) {
        joined.removeIf(at -> at.x() == buildingOrigin.x() && at.z() == buildingOrigin.z());
    }

    public void add(Segment segment) {
        if (segment.from().equals(segment.to()) || segments.contains(segment)) {
            return;
        }
        segments.add(segment);
    }

    /**
     * Which planned stretch each run was routed for, by its index in the network.
     *
     * <p>A run has always been anonymous once it was added: a street was routed
     * into segments and the segments forgot which street they were. That was
     * enough while every run was opened in turn, and is not enough to say "this
     * circuit has filled up" — a rule about a <em>street</em> cannot be enforced
     * over a list that no longer knows what a street is.
     *
     * <p>The value is {@code PathPlanner}'s stretch key, which carries the street
     * and the piece within it. A run with no entry is a lane: a track run to one
     * door, belonging to no street at all.
     */
    private final Map<Integer, Integer> streetOfRun = new LinkedHashMap<>();

    /** Adds a run laid for a planned stretch, remembering which one. */
    public void addForStreet(Segment segment, int streetKey) {
        int before = segments.size();
        add(segment);
        if (segments.size() > before) {
            streetOfRun.put(segments.size() - 1, streetKey);
        }
    }

    /** The planned stretch this run was laid for, or -1 for a lane. */
    public int streetOf(int index) {
        Integer key = streetOfRun.get(index);
        return key == null ? -1 : key;
    }

    /**
     * Every run that belongs to a street, and which, for saving.
     *
     * <p>A flat run of pairs — index, then key — rather than a map, because a
     * list of numbers is the cheapest thing a save format has and this is one
     * entry per stretch of carriageway in the town.
     */
    public List<Integer> runStreetPairs() {
        List<Integer> out = new ArrayList<>(streetOfRun.size() * 2);
        streetOfRun.forEach((index, key) -> {
            out.add(index);
            out.add(key);
        });
        return out;
    }

    /** Restores the street each run belongs to from a save. */
    public void restoreRunStreets(List<Integer> pairs) {
        streetOfRun.clear();
        if (pairs == null) {
            return;
        }
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            streetOfRun.put(pairs.get(i), pairs.get(i + 1));
        }
    }

    /**
     * What this town owes in road, remembered between askings.
     *
     * <p>Not saved and not meant to be: it is derived from the network, the
     * buildings and the plan, all of which a reload has. Held on the network
     * because the network is the thing it is about, and because the question is
     * asked several times a step — by the clock, by the foreman, and by the crew
     * when it finishes a stretch.
     */
    private StreetDemand demand;

    StreetDemand demand() {
        if (demand == null) {
            demand = new StreetDemand();
        }
        return demand;
    }

    /**
     * The closest point on any existing path, or null if there are none.
     *
     * <p>This is the whole of "favor extending from a path that is already
     * there": a new building joins the nearest way rather than driving its own
     * line to the center, so the roads grow as a branching network instead of a
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
