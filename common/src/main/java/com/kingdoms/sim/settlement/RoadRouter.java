package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.geom.TerrainSense;
import com.kingdoms.sim.geom.Ways;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bends a planned street onto ground somebody could walk.
 *
 * <p>Everything before this could only <em>refuse</em>. A plan is a flat
 * drawing; the ground is not; and the three places that checked — laying,
 * opening, paving — could each say no and none could say "not there, here".
 * Refusal alone leaves holes: streets that never appear, doors stranded from
 * roads that were planned and never built, a network that shrinks every time
 * the rules get stricter. A town on rough ground ended up with fewer roads the
 * better its judgment got, which is precisely backwards.
 *
 * <p>So a street keeps its <em>intent</em> — where it starts, where it ends,
 * what it is for — and gives up its exact line. The router searches a corridor
 * around the drawn line for the cheapest way through, where cheap means level,
 * dry, out of everybody's plot, and close to what was drawn.
 *
 * <p><strong>The corridor is what makes this safe.</strong> Its half-width is
 * less than the setback the plan keeps between a street and the plots along it,
 * so a road may wander without ever wandering into somebody's frontage — and
 * plots stay exactly where the plan put them. The plan does not need to know
 * this happened.
 *
 * <p>Deterministic by construction: integer costs, a fixed neighbor order, and
 * ties broken on coordinates. The same street on the same ground routes the same
 * way on every machine and after every reload — which matters because the answer
 * is persisted, and a road that re-derived differently would be laid twice.
 */
public final class RoadRouter {

    /**
     * How far from the drawn line a road may stray.
     *
     * <p>Under the plan's setback of thirteen, which is the whole safety
     * argument: the plots along a street stand that far back from where the
     * street was drawn, so a road that stays inside this can never be routed
     * onto one. Wider would buy better routes and start taking gardens.
     */
    public static final int CORRIDOR_HALF = 12;

    /**
     * The lattice the search runs on.
     *
     * <p>The oracle remembers ground on a four-block grid and answers repeats
     * from memory, so searching on the same grid turns a corridor's worth of
     * queries into a handful of real reads. A finer search would ask questions
     * nobody has answers for and pay for each one.
     */
    public static final int GRAIN = 4;

    /**
     * The longest stretch of water one road may cross.
     *
     * <p>Water used to be refused outright, everywhere, by both the search and
     * the fine check that follows it — so a river severed a town's network
     * absolutely and a settlement that happened to be founded on two banks was
     * two settlements that shared a name. That was never a decision; it was what
     * you get when the only answer available is no.
     *
     * <p>Twenty-four blocks is a bridge somebody could build: a vanilla river is
     * five to twelve across at its widest and a lake inlet a good deal more, so
     * this crosses every river and refuses the open water it would be silly to
     * span. It is a bound rather than a budget — a road takes the narrowest
     * crossing it can find long before it reaches this, because every wet block
     * is priced.
     */
    public static final int LONGEST_BRIDGE = 24;

    /** Ground this route may not cross at all. */
    public interface Keepout {
        boolean blocked(int x, int z);

        Keepout NOTHING = (x, z) -> false;
    }

    private RoadRouter() {
    }

    /**
     * A way from one end of this street to the other, or null if there is none.
     *
     * @param ideal   the line the plan drew, as its points
     * @param ground  what the terrain is doing
     * @param keepout ground already spoken for
     */
    public static List<SimPos> route(List<SimPos> ideal, TerrainSense ground,
                                     Keepout keepout) {
        if (ideal.size() < 2) {
            return ideal;
        }
        SimPos from = snap(ideal.get(0));
        SimPos to = snap(ideal.get(ideal.size() - 1));
        if (from.equals(to)) {
            return null;
        }
        long startKey = key(from);
        long goalKey = key(to);

        Map<Long, Integer> best = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        PriorityQueue<long[]> open = new PriorityQueue<>(
                Comparator.<long[]>comparingLong(a -> a[0])
                        .thenComparingLong(a -> a[2])
                        .thenComparingLong(a -> a[3]));
        best.put(startKey, 0);
        open.add(new long[] {estimate(from, to), 0, from.x(), from.z()});

        int ceiling = ceilingFor(ideal);
        int examined = 0;
        while (!open.isEmpty()) {
            long[] head = open.poll();
            int x = (int) head[2];
            int z = (int) head[3];
            long here = key(x, z);
            int cost = (int) head[1];
            Integer known = best.get(here);
            if (known == null || cost > known) {
                continue;   // a better way here was already found
            }
            if (here == goalKey) {
                List<SimPos> line = simplify(rebuild(cameFrom, here, ideal.get(0),
                        ideal.get(ideal.size() - 1)), ground, keepout);
                // The search prices a four-block cell; a cliff is one block. The
                // winning line is the only one worth asking the fine question
                // about, and it is asked here rather than left to the caller so
                // that a route handed back is one somebody can actually walk.
                return walkable(line, ground, GRADABLE_ALLOWANCE) ? line : null;
            }
            if (cost > ceiling || ++examined > EXAMINE_CEILING) {
                break;   // past what this street is worth; let the caller refuse
            }
            for (int[] step : NEIGHBOURS) {
                int nx = x + step[0] * GRAIN;
                int nz = z + step[1] * GRAIN;
                if (!withinCorridor(nx, nz, ideal) || keepout.blocked(nx, nz)) {
                    continue;
                }
                // A wet cell is dear rather than forbidden, and a step with
                // water at EITHER end has no climb: what a road does over water
                // is carried on a deck, and a deck is level whatever the
                // riverbed underneath it is doing.
                //
                // Either end, not just the far one. Getting on to the water was
                // the obvious half and getting off it is the half that decides
                // whether a crossing exists at all -- a channel ten blocks deep
                // reads as a ten-block climb the moment the road reaches the
                // far bank, which is over the impassable limit, so every
                // crossing on the map was refused one step from finishing.
                boolean wet = ground.wetAt(nx, nz);
                int climb = wet || ground.wetAt(x, z) ? 0
                        : Math.abs(ground.heightAt(nx, nz) - ground.heightAt(x, z));
                if (climb > IMPASSABLE_CLIMB) {
                    continue;   // no arrangement of blocks makes this walkable
                }
                // The ground BETWEEN the two cells, not only at them. A lattice
                // step is four blocks long, so two perfectly good cells can have
                // a ridge or somebody's plot lying across the run that joins
                // them -- which is how a route that had properly found the gap
                // in a ridge came back crossing it diagonally, and how a road
                // that dodged a plot still clipped its corner.
                if (!clearRun(new SimPos(x, 0, z), new SimPos(nx, 0, nz),
                        ground, keepout)) {
                    continue;
                }
                int move = (step[0] == 0 || step[1] == 0) ? STRAIGHT : DIAGONAL;
                // A grain cell that climbs four may be four honest one-block
                // steps, so this is a price and not a refusal. The line that
                // wins is checked column by column afterwards, where a real
                // step can be seen.
                int next = cost + move + climb * CLIMB_PRICE
                        + (wet ? BRIDGE_PRICE : 0)
                        + (int) Math.round(strayed(nx, nz, ideal)) * STRAY_PRICE;
                long there = key(nx, nz);
                Integer had = best.get(there);
                if (had != null && had <= next) {
                    continue;
                }
                best.put(there, next);
                cameFrom.put(there, here);
                open.add(new long[] {next + estimate(nx, nz, to), next, nx, nz});
            }
        }
        return null;
    }

    /**
     * Whether a routed line is walkable column by column.
     *
     * <p>The search runs on a four-block lattice, so it can only price a step it
     * cannot quite see: four blocks of climb between two cells might be four
     * gentle steps or one cliff. This is where that is settled, on the winning
     * line only, so the fine questions are asked once instead of across a whole
     * corridor.
     *
     * @param gradable how many two-block steps to tolerate, which the layer can
     *                 cut or fill into a pair of one-block steps
     */
    public static boolean walkable(List<SimPos> line, TerrainSense ground, int gradable) {
        int steep = 0;
        int wetRun = 0;
        int last = NO_HEIGHT_YET;
        for (int i = 1; i < line.size(); i++) {
            List<SimPos> run = between(line.get(i - 1), line.get(i));
            for (SimPos at : run) {
                // A crossing is judged as a crossing: how far the deck has to
                // reach, not how far the bed falls away under it. The climb
                // either side of the water is still judged, because the deck has
                // to meet a bank somebody can walk up.
                if (ground.wetAt(at.x(), at.z())) {
                    if (++wetRun > LONGEST_BRIDGE) {
                        return false;   // not a river; open water
                    }
                    continue;
                }
                wetRun = 0;
                int height = ground.heightAt(at.x(), at.z());
                if (last != NO_HEIGHT_YET) {
                    int climb = Math.abs(height - last);
                    if (climb > GRADABLE_CLIMB) {
                        return false;
                    }
                    if (climb == GRADABLE_CLIMB && ++steep > gradable) {
                        return false;
                    }
                }
                last = height;
            }
        }
        return true;
    }

    private static List<SimPos> between(SimPos from, SimPos to) {
        int steps = Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z()));
        List<SimPos> out = new ArrayList<>(steps + 1);
        if (steps == 0) {
            out.add(from);
            return out;
        }
        for (int i = 0; i <= steps; i++) {
            out.add(new SimPos(
                    from.x() + Math.round((float) (to.x() - from.x()) * i / steps),
                    from.y(),
                    from.z() + Math.round((float) (to.z() - from.z()) * i / steps)));
        }
        return out;
    }

    // --- the small print ---

    private static boolean withinCorridor(int x, int z, List<SimPos> ideal) {
        return strayed(x, z, ideal) <= CORRIDOR_HALF;
    }

    /** How far this point stands from the line the plan drew. */
    private static double strayed(int x, int z, List<SimPos> ideal) {
        double nearest = Double.MAX_VALUE;
        for (int i = 1; i < ideal.size(); i++) {
            nearest = Math.min(nearest, Ways.distanceToSquare(
                    ideal.get(i - 1).x(), ideal.get(i - 1).z(),
                    ideal.get(i).x(), ideal.get(i).z(), x, z, 0));
            if (nearest == 0) {
                break;
            }
        }
        return nearest;
    }

    private static List<SimPos> rebuild(Map<Long, Long> cameFrom, long at,
                                        SimPos start, SimPos end) {
        List<SimPos> back = new ArrayList<>();
        Long step = at;
        while (step != null) {
            back.add(new SimPos((int) (step >> 32), start.y(),
                    (int) (step.longValue() & 0xFFFFFFFFL)));
            step = cameFrom.get(step);
        }
        java.util.Collections.reverse(back);
        // The plan's own ends, not the lattice's rounding of them: a street has
        // to still meet what it was drawn to meet.
        back.set(0, start);
        back.set(back.size() - 1, end);
        return back;
    }

    /**
     * Drops the points that say nothing.
     *
     * <p>A lattice walk names a cell every four blocks; a street wants to be a
     * handful of runs. Any point within a block of the line between its
     * neighbors is doing no work and goes.
     */
    private static List<SimPos> simplify(List<SimPos> line, TerrainSense ground,
                                         Keepout keepout) {
        if (line.size() < 3) {
            return line;
        }
        List<SimPos> kept = new ArrayList<>();
        kept.add(line.get(0));
        for (int i = 1; i < line.size() - 1; i++) {
            SimPos previous = kept.get(kept.size() - 1);
            SimPos next = line.get(i + 1);
            // Dropping a point straightens the run through it, and a straighter
            // run crosses different ground. The first version of this checked
            // only that the dropped point was near the new line, and turned a
            // route that went round a ridge into one that went through it --
            // the search had found the gap and the tidying-up threw it away.
            if (!clearRun(previous, next, ground, keepout)) {
                kept.add(line.get(i));
            }
        }
        kept.add(line.get(line.size() - 1));
        return kept;
    }

    /**
     * What a road pays per grain cell of water it crosses.
     *
     * <p>Dear enough that a route takes any dry way round it that is not
     * absurdly longer — a cell costs ten straight or fourteen diagonal, so this
     * is worth about twenty cells of detour per cell of water, which is the
     * right trade for a river a road can walk around the head of. Not so dear
     * that a crossing is never worth it, because a town on two banks with no
     * bridge is two towns.
     */
    private static final int BRIDGE_PRICE = 200;

    /** Whether a straight run between two points crosses only ground a road may have. */
    private static boolean clearRun(SimPos from, SimPos to, TerrainSense ground,
                                    Keepout keepout) {
        // Deliberately not seeded from the first column: it may be water, and
        // the bed of a channel is not a height anything is compared against.
        int last = NO_HEIGHT_YET;
        for (SimPos at : between(from, to)) {
            if (keepout.blocked(at.x(), at.z())) {
                return false;
            }
            if (ground.wetAt(at.x(), at.z())) {
                continue;   // carried over on a deck; the span is checked whole
            }
            int height = ground.heightAt(at.x(), at.z());
            if (last != NO_HEIGHT_YET && Math.abs(height - last) > GRADABLE_CLIMB) {
                return false;
            }
            last = height;
        }
        return true;
    }

    /** No dry column has been seen yet, so there is nothing to compare against. */
    private static final int NO_HEIGHT_YET = Integer.MIN_VALUE;

    /** How many gradable steps a whole street may carry before it is not worth it. */
    private static final int GRADABLE_ALLOWANCE = 4;

    private static int ceilingFor(List<SimPos> ideal) {
        double length = 0;
        for (int i = 1; i < ideal.size(); i++) {
            length += Math.hypot(ideal.get(i).x() - ideal.get(i - 1).x(),
                                 ideal.get(i).z() - ideal.get(i - 1).z());
        }
        return (int) (length / GRAIN * STRAIGHT * WORTH_THE_DETOUR) + STRAIGHT * 8;
    }

    private static SimPos snap(SimPos at) {
        return new SimPos(at.x() - Math.floorMod(at.x(), GRAIN), at.y(),
                at.z() - Math.floorMod(at.z(), GRAIN));
    }

    private static long key(SimPos at) {
        return key(at.x(), at.z());
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static long estimate(SimPos from, SimPos to) {
        return estimate(from.x(), from.z(), to);
    }

    /** Never an overestimate, or the search stops finding the cheapest way. */
    private static long estimate(int x, int z, SimPos to) {
        int dx = Math.abs(to.x() - x) / GRAIN;
        int dz = Math.abs(to.z() - z) / GRAIN;
        return (long) STRAIGHT * Math.abs(dx - dz)
                + (long) DIAGONAL * Math.min(dx, dz);
    }

    /** Clockwise from north, so the order never depends on a hash. */
    private static final int[][] NEIGHBOURS = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    private static final int STRAIGHT = 10;
    private static final int DIAGONAL = 14;

    /** What a course of climb costs, against a block of travel. */
    private static final int CLIMB_PRICE = 12;

    /** What a block of straying from the drawn line costs. */
    private static final int STRAY_PRICE = 1;

    /** Climb between lattice cells past which no arrangement of ground helps. */
    private static final int IMPASSABLE_CLIMB = 4;

    /** The steepest step the paving layer can cut or fill into two. */
    private static final int GRADABLE_CLIMB = 2;

    /** How much dearer than a straight run a street is worth routing. */
    private static final double WORTH_THE_DETOUR = 2.5;

    /** A bound on the search itself, because a bad corridor must not hang. */
    private static final int EXAMINE_CEILING = 20_000;
}
