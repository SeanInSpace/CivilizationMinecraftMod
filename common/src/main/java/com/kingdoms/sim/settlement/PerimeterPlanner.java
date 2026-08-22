package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
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
            Perimeter staked = stake(settlement);
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

    /** The v1 ring: a rectangle over every plot, margin added, gates on the roads. */
    private static Perimeter stake(Settlement settlement) {
        SimPos centre = settlement.centre();
        int west = centre.x() - MIN_HALF_SIDE;
        int east = centre.x() + MIN_HALF_SIDE;
        int north = centre.z() - MIN_HALF_SIDE;
        int south = centre.z() + MIN_HALF_SIDE;
        for (Building building : settlement.buildings()) {
            int half = BuildPlanner.plotSpanOf(building.blueprintId(),
                    settlement.catalogue()) / 2;
            west = Math.min(west, building.origin().x() - half);
            east = Math.max(east, building.origin().x() + half);
            north = Math.min(north, building.origin().z() - half);
            south = Math.max(south, building.origin().z() + half);
        }
        west -= MARGIN;
        east += MARGIN;
        north -= MARGIN;
        south += MARGIN;

        int y = centre.y();
        List<SimPos> corners = List.of(
                new SimPos(west, y, north),
                new SimPos(east, y, north),
                new SimPos(east, y, south),
                new SimPos(west, y, south));
        return new Perimeter(corners, gatesFor(settlement, west, east, north, south, y), 0);
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
