package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

import java.util.List;

/**
 * Stakes and raises the settlement's first ring — the palisade.
 *
 * <p>V1 by design: an axis-aligned rectangle around everything the town has
 * built, with a margin to move in and a gate at each side's midpoint. Gates
 * belong where the paths cross the ring; the paths are not remembered yet, so
 * the midpoints stand in until they are. The α-shape wall in the GOALS replaces
 * {@link #stake} and nothing else — laying, closing, gates and patrol all read
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
        raise(settlement, ctx);
    }

    /** The v1 ring: a rectangle over every plot, margin added, gates at midpoints. */
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
        List<SimPos> gates = List.of(
                new SimPos((west + east) / 2, y, north),
                new SimPos(east, y, (north + south) / 2),
                new SimPos((west + east) / 2, y, south),
                new SimPos(west, y, (north + south) / 2));
        return new Perimeter(corners, gates, 0);
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
