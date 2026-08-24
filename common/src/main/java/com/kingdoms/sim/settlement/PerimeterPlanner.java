package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.geom.Hull;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Stakes and raises the settlement's first ring — the palisade.
 *
 * <p>V1 by design: an axis-aligned rectangle around everything the town has
 * built, with a margin to move in and a gate on every road out. Gates
 * belong where the roads cross the ring, and the roads are remembered now --
 * the side midpoints only stand in for a town whose paths never reach its
 * wall. The α-shape wall in the GOALS replaces {@link #stake} and nothing
 * else — laying, closing, gates and patrol all read
 * the {@link Perimeter} it returns.
 *
 * <p>Raising is paid work on the abstract clock: posts cost timber and go up as
 * fast as the labouring hands can plant them, pausing whenever the build queue
 * has a real building in it — walls matter, but shelter and stores matter more.
 */
public final class PerimeterPlanner {

    /** Clear ground kept between the buildings and the ring. */
    public static final int MARGIN = 4;

    /** Shortest side a ring may have, so a lone post is still a yard. */
    public static final int MIN_HALF_SIDE = 8;

    /** Timber per ring position — one log, split into posts. */
    public static final int WOOD_PER_POST = 1;

    /** Positions one pair of hands raises in a step. */
    public static final int POSTS_PER_HAND = 2;

    private PerimeterPlanner() {
    }

    /**
     * One step of perimeter work: stake it when the stage calls for it, then
     * raise it as timber and hands allow.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        if (settlement.stage().before(SettlementStage.FORTIFIED)) {
            return;
        }
        if (settlement.perimeter() == null) {
            // Staked only once the stage's own buildings stand, so the ring
            // encloses the storehouse rather than being outgrown by it.
            if (!StagePlanner.programComplete(settlement)) {
                return;
            }
            Perimeter staked = stake(settlement, ctx);
            settlement.setPerimeter(staked);
            settlement.logEvent(ctx.step(), "The palisade is staked out — "
                    + staked.length() + " posts will ring " + settlement.name());
            return;
        }
        resiteGates(settlement);
        raise(settlement, ctx);
    }

    /**
     * Keeps the gates on the roads while the wall is still going up.
     *
     * <p>The ring is staked at FORTIFIED, when a town has usually drawn few of
     * its streets — so the gates it is staked with are provisional, and follow
     * the network until the wall closes over them.
     */
    private static void resiteGates(Settlement settlement) {
        Perimeter perimeter = settlement.perimeter();
        if (perimeter.closed() || settlement.paths().isEmpty()) {
            return;
        }
        int west = Integer.MAX_VALUE;
        int east = Integer.MIN_VALUE;
        int north = Integer.MAX_VALUE;
        int south = Integer.MIN_VALUE;
        for (SimPos vertex : perimeter.vertices()) {
            west = Math.min(west, vertex.x());
            east = Math.max(east, vertex.x());
            north = Math.min(north, vertex.z());
            south = Math.max(south, vertex.z());
        }
        perimeter.setGates(gatesFor(settlement, west, east, north, south,
                settlement.centre().y()));
    }

    /**
     * How long a straight run of wall may be before the line is expected to come
     * in and follow the buildings.
     *
     * <p>The one number that decides how tightly the wall hugs the town. Too
     * small and the line frets around every shed; too large and it is a
     * rectangle again, fortifying whatever empty field happens to lie between
     * two outlying farms.
     */
    private static final int MAX_STRAIGHT_RUN = 24;

    /** Sweeps of the contour before the line is taken as settled. */
    private static final int RELAX_PASSES = 4;

    /** How far a vertex may shift in one sweep, looking for better ground. */
    private static final int RELAX_REACH = 3;

    /**
     * What a vertex weighs the ground against the shape.
     *
     * <p>Higher and the wall chases every flat shelf into a wandering line;
     * lower and it ignores the terrain and marches over a ravine. These are the
     * two halves of the contour's energy: {@code GROUND} rewards standing on
     * even ground, {@code TAUT} rewards a short line that does not zigzag.
     */
    private static final double GROUND_WEIGHT = 1.0;
    private static final double TAUT_WEIGHT = 0.35;

    /**
     * Stakes the ring: a loop that follows the town, settled onto the ground.
     *
     * <p>Four steps, each of which can be read on its own. The plots give a
     * scatter of corners; {@link Hull#concave} wraps them in a line that comes
     * in where the buildings do rather than bulging out to the convex hull; the
     * line is pushed clear by {@link #MARGIN}; and then it is relaxed over the
     * terrain, each vertex looking a little way around itself for ground that
     * is flatter than where it stands, without ever letting a building out.
     *
     * <p>The relaxation is a greedy active contour — the vertex-at-a-time kind
     * rather than the matrix kind — because a wall is a few dozen vertices on
     * an integer grid and the expensive version buys nothing at that size.
     * Every candidate move is checked against containment before it is taken,
     * so the terrain can never talk the line into abandoning a farm.
     */
    public static Perimeter stake(Settlement settlement, SimContext ctx) {
        SimPos centre = settlement.centre();
        List<SimPos> plots = plotCorners(settlement);
        List<SimPos> loop = Hull.concave(plots, MAX_STRAIGHT_RUN);
        if (loop.size() < 3) {
            loop = boxAround(centre, MIN_HALF_SIDE);
        }
        loop = pushOut(loop, centre, MARGIN);
        loop = relax(loop, plots, settlement, ctx);

        int west = Integer.MAX_VALUE;
        int east = Integer.MIN_VALUE;
        int north = Integer.MAX_VALUE;
        int south = Integer.MIN_VALUE;
        for (SimPos vertex : loop) {
            west = Math.min(west, vertex.x());
            east = Math.max(east, vertex.x());
            north = Math.min(north, vertex.z());
            south = Math.max(south, vertex.z());
        }
        return new Perimeter(loop,
                gatesFor(settlement, west, east, north, south, centre.y()), 0);
    }

    /**
     * The corners of every plot the town has taken, plus a minimum yard.
     *
     * <p>Corners rather than origins: a wall staked around the middles of
     * buildings runs through their walls. The minimum box is what gives a town
     * with one hut a ring worth the name.
     */
    private static List<SimPos> plotCorners(Settlement settlement) {
        SimPos centre = settlement.centre();
        List<SimPos> points = new ArrayList<>(boxAround(centre, MIN_HALF_SIDE));
        for (Building building : settlement.buildings()) {
            int half = BuildPlanner.plotSpanOf(building.blueprintId(),
                    settlement.catalogue()) / 2;
            SimPos at = building.origin();
            points.add(new SimPos(at.x() - half, centre.y(), at.z() - half));
            points.add(new SimPos(at.x() + half, centre.y(), at.z() - half));
            points.add(new SimPos(at.x() + half, centre.y(), at.z() + half));
            points.add(new SimPos(at.x() - half, centre.y(), at.z() + half));
        }
        return points;
    }

    private static List<SimPos> boxAround(SimPos centre, int half) {
        return List.of(
                new SimPos(centre.x() - half, centre.y(), centre.z() - half),
                new SimPos(centre.x() + half, centre.y(), centre.z() - half),
                new SimPos(centre.x() + half, centre.y(), centre.z() + half),
                new SimPos(centre.x() - half, centre.y(), centre.z() + half));
    }

    /** Moves every vertex directly away from the middle, to clear the buildings. */
    private static List<SimPos> pushOut(List<SimPos> loop, SimPos centre, int margin) {
        List<SimPos> out = new ArrayList<>(loop.size());
        for (SimPos vertex : loop) {
            double dx = vertex.x() - centre.x();
            double dz = vertex.z() - centre.z();
            double away = Math.sqrt(dx * dx + dz * dz);
            if (away < 1e-6) {
                out.add(vertex);
                continue;
            }
            out.add(new SimPos(
                    vertex.x() + (int) Math.round(dx / away * margin),
                    vertex.y(),
                    vertex.z() + (int) Math.round(dz / away * margin)));
        }
        return out;
    }

    /**
     * Settles the line onto the ground, without letting anything out.
     *
     * <p>Each vertex looks at the squares within {@link #RELAX_REACH} of itself
     * and takes the one with the least energy — flatter ground, and a shorter
     * straighter line through its two neighbours. A move that would leave a
     * plot corner outside the loop is refused however good the ground is,
     * which is the one rule a wall cannot bend.
     */
    private static List<SimPos> relax(List<SimPos> loop, List<SimPos> plots,
                                      Settlement settlement, SimContext ctx) {
        List<SimPos> line = new ArrayList<>(loop);
        for (int pass = 0; pass < RELAX_PASSES; pass++) {
            boolean moved = false;
            for (int i = 0; i < line.size(); i++) {
                SimPos before = line.get((i - 1 + line.size()) % line.size());
                SimPos after = line.get((i + 1) % line.size());
                SimPos here = line.get(i);
                SimPos best = here;
                double least = energy(here, before, after, ctx);
                for (int dx = -RELAX_REACH; dx <= RELAX_REACH; dx++) {
                    for (int dz = -RELAX_REACH; dz <= RELAX_REACH; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        SimPos candidate = new SimPos(here.x() + dx, here.y(), here.z() + dz);
                        double cost = energy(candidate, before, after, ctx);
                        if (cost >= least) {
                            continue;
                        }
                        line.set(i, candidate);
                        boolean holds = holdsEverything(line, plots);
                        line.set(i, here);
                        if (holds) {
                            least = cost;
                            best = candidate;
                        }
                    }
                }
                if (!best.equals(here)) {
                    line.set(i, best);
                    moved = true;
                }
            }
            if (!moved) {
                break;   // settled
            }
        }
        return line;
    }

    /**
     * What standing here costs.
     *
     * <p>The ground term is how uneven the four squares around this one are: a
     * wall likes a shelf and dislikes a slope, which is what makes it drift
     * along a contour rather than straight up one. The taut term is the length
     * of the two segments through this vertex, which keeps the line from
     * wandering off after every flat patch it can see.
     */
    private static double energy(SimPos at, SimPos before, SimPos after, SimContext ctx) {
        int here = ctx.bridge().surfaceHeight(at);
        int roughness = 0;
        roughness += Math.abs(ctx.bridge().surfaceHeight(
                new SimPos(at.x() + 1, at.y(), at.z())) - here);
        roughness += Math.abs(ctx.bridge().surfaceHeight(
                new SimPos(at.x() - 1, at.y(), at.z())) - here);
        roughness += Math.abs(ctx.bridge().surfaceHeight(
                new SimPos(at.x(), at.y(), at.z() + 1)) - here);
        roughness += Math.abs(ctx.bridge().surfaceHeight(
                new SimPos(at.x(), at.y(), at.z() - 1)) - here);

        double taut = at.horizontalDistance(before) + at.horizontalDistance(after);
        return GROUND_WEIGHT * roughness + TAUT_WEIGHT * taut;
    }

    /** Whether every plot corner is still inside the line. */
    private static boolean holdsEverything(List<SimPos> line, List<SimPos> plots) {
        for (SimPos plot : plots) {
            if (!Hull.contains(line, plot)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gates go where the roads reach.
     *
     * <p>One to a side, sited on whichever street pushes furthest that way, so
     * the ways out of town line up with the ways through it. A settlement whose
     * roads have not been drawn yet gets the side midpoints, and is re-sited as
     * soon as they are.
     */
    private static List<SimPos> gatesFor(Settlement settlement, int west, int east,
                                         int north, int south, int y) {
        PathNetwork paths = settlement.paths();
        SimPos toNorth = paths.reachToward(0, -1);
        SimPos toSouth = paths.reachToward(0, 1);
        SimPos toWest = paths.reachToward(-1, 0);
        SimPos toEast = paths.reachToward(1, 0);
        if (toNorth == null) {
            return List.of(
                    new SimPos((west + east) / 2, y, north),
                    new SimPos(east, y, (north + south) / 2),
                    new SimPos((west + east) / 2, y, south),
                    new SimPos(west, y, (north + south) / 2));
        }
        // Held off the corners: a gate cut into the turn of a wall is a gap in
        // two walls at once.
        return List.of(
                new SimPos(Math.clamp(toNorth.x(), west + 2, east - 2), y, north),
                new SimPos(east, y, Math.clamp(toEast.z(), north + 2, south - 2)),
                new SimPos(Math.clamp(toSouth.x(), west + 2, east - 2), y, south),
                new SimPos(west, y, Math.clamp(toWest.z(), north + 2, south - 2)));
    }


    /** Posts go up while the timber lasts and no building is waiting on the crew. */
    private static void raise(Settlement settlement, SimContext ctx) {
        Perimeter perimeter = settlement.perimeter();
        if (perimeter.closed()) {
            if (!settlement.perimeterClosed()) {
                settlement.setPerimeterClosed(true);
                settlement.logEvent(ctx.step(), "The palisade closes around "
                        + settlement.name() + " — " + perimeter.gates().size()
                        + " gates and a sentry walk");
            }
            return;
        }
        if (!settlement.buildQueue().isEmpty()) {
            return;   // shelter and stores before walls
        }
        int hands = (int) settlement.residents().stream()
                .filter(p -> settlement.laboursAs(p, Profession.BUILDER)
                        && !p.isTooWeakToWork())
                .count();
        if (hands <= 0) {
            return;
        }
        int want = Math.min(hands * POSTS_PER_HAND,
                perimeter.length() - perimeter.laid());
        int affordable = Math.min(want,
                settlement.woodStock() / Math.max(1, WOOD_PER_POST));
        if (affordable <= 0) {
            // Same rule as any other build that runs dry: go make more timber.
            BuildPlanner.requestProducer(settlement, TownStores.WOOD, ctx.step());
            return;
        }
        settlement.stores().take(TownStores.WOOD, affordable * WOOD_PER_POST);
        perimeter.setLaid(perimeter.laid() + affordable);
    }
}
