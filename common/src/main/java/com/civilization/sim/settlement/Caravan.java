package com.civilization.sim.settlement;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.kingdom.Kingdom;
import com.civilization.sim.world.SimWorld;

/**
 * The wagon the inn trades with, as something you can stand in the road and watch.
 *
 * <p><strong>This decides nothing.</strong> {@link InnPlanner} books the trade —
 * so many loaves off the shelf, so much iron onto it, one line in the log — and
 * it does that on its own clock whether or not anybody is there. Everything in
 * this class is the <em>theatre</em>: which step a body would be walking in on,
 * which column it walks in at, where it stands while it is here, and what the
 * nameplate over its head says. Ask every question here, or ask none of them, and
 * the books come out the same. That is not a happy accident of the implementation;
 * it is the rule the whole mod is built on — a thing that only happens while
 * somebody is watching must not be worth anything — and {@code CaravanTest} states
 * it by running the same town twice.
 *
 * <p><strong>The clock.</strong> The visit is keyed off the trade rather than the
 * other way round, because the trade is the thing that is already certain.
 * {@code InnPlanner.advance} fires on the steps divisible by
 * {@link InnPlanner#CARAVAN_PERIOD}; the wagon is therefore on the ground for the
 * {@link #VISIT_STEPS} steps beginning at each of those, and a player who arrives
 * at the inn between visits sees an empty yard, which is what an inn yard mostly
 * is. Predicting an arrival <em>before</em> the trade was written and taken back
 * out: the trade would then have to either wait for a body that may never be
 * embodied, or happen anyway while the wagon was visibly still on the road — and
 * the second of those is a caravan you watch trade with an empty street.
 */
public final class Caravan {

    private Caravan() {
    }

    /**
     * How long the wagon stays: eight steps.
     *
     * <p>Long enough that a player who walks into town on the step the trade
     * books still finds somebody at the inn, and short enough that two visits
     * never overlap — which they must not, because a town is capped at one
     * caravan and a second one due before the first had left would be a wagon
     * that never leaves. Eight against {@link InnPlanner#CARAVAN_PERIOD}'s
     * forty-eight is a sixth of the time, which is about how often an inn yard in
     * a small town has a stranger in it.
     */
    public static final int VISIT_STEPS = 8;

    /**
     * Whether a wagon is on the ground on this step.
     *
     * <p>A pure function of the planner's clock and of nothing else — no town, no
     * die, no world — which is what lets the visit be predicted identically by
     * the view layer on one side and by a test on the other.
     */
    public static boolean isVisiting(long step) {
        if (step < InnPlanner.CARAVAN_PERIOD) {
            return false;   // the first wagon calls on the first trade, not before
        }
        return Math.floorMod(step, InnPlanner.CARAVAN_PERIOD) < VISIT_STEPS;
    }

    /** The step the wagon on the ground at {@code step} rolled in on. */
    public static long arrivedOn(long step) {
        return step - Math.floorMod(step, InnPlanner.CARAVAN_PERIOD);
    }

    /**
     * Whether a caravan could call here at all.
     *
     * <p>Two refusals and both matter. No inn, no caravan: the inn is what a
     * wagon stops at, and it is also the building {@code InnPlanner} requires
     * before it books anything, so a town without one has nothing to watch
     * <em>and</em> nothing to watch it for. And no way in, no caravan: see
     * {@link TownEdge}, which answers null for a settlement whose streets have
     * never been opened. A wagon put down at a road-less camp would be a trader
     * standing in a field, and the pack animals behind him would be in the trees.
     */
    public static boolean callsAt(Settlement settlement) {
        if (settlement == null || settlement.countBuildings("civilization:inn") <= 0) {
            return false;
        }
        return TownEdge.hasAWayIn(settlement);
    }

    /** Where the wagon comes in, and goes back out by. @see TownEdge */
    public static SimPos entersAt(Settlement settlement) {
        return TownEdge.of(settlement);
    }

    /**
     * Where the wagon stands while it is here: on the inn's doorstep.
     *
     * <p>The inn first, because the inn is what {@code InnPlanner} unloads at and
     * a trader standing anywhere else would be a trader whose goods went into a
     * different building. Then the market, for the town that has both and whose
     * inn is on a back lane. Then the middle of the town, which is where anything
     * with nowhere better to be ends up.
     *
     * <p>The <em>doorstep</em> and not the origin, which is the difference
     * between standing in the yard and standing inside the wall. A building's
     * origin is a point in the middle of it; the doorstep is the block outside
     * the door somebody stands on to walk in, and it is where anybody in this mod
     * who is waiting to be let in is put.
     */
    public static SimPos standsAt(Settlement settlement) {
        if (settlement == null) {
            return null;
        }
        Building inn = settlement.buildingWithRole(BuildingRole.INN);
        if (inn != null) {
            return inn.doorstep();
        }
        Building market = settlement.buildingWithRole(BuildingRole.MARKET);
        return market != null ? market.doorstep() : settlement.center();
    }

    /** What the road is called when nobody can say where the wagon came from. */
    public static final String THE_ROAD = "the road";

    /**
     * What the nameplate says: "Caravan from Millbrook", or from the road.
     *
     * <p>A nameplate rather than a chat line because the thing a player needs to
     * know is <em>what this stranger is</em>, and they need to know it from
     * thirty blocks away without being told. An unnamed body walking into a town
     * with two llamas behind it is an oddity; the same body labelled is the
     * neighbouring town, which is a fact about the world nothing else in the game
     * has ever shown.
     *
     * <p>The neighbour is the nearest settlement of the same kingdom that is not
     * this one, because those are the towns a road could plausibly run between.
     * A town with no neighbours trades with {@link #THE_ROAD} — which is the
     * honest answer, and the same one {@code InnPlanner}'s own doc gives: V1
     * trades with an abstract caravan, and this names the abstraction rather
     * than inventing a town to blame it on.
     */
    public static String nameFor(SimWorld world, Settlement settlement) {
        return "Caravan from " + fromWhere(world, settlement);
    }

    private static String fromWhere(SimWorld world, Settlement settlement) {
        if (world == null || settlement == null) {
            return THE_ROAD;
        }
        Settlement nearest = null;
        long away = Long.MAX_VALUE;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement other : kingdom.settlements()) {
                if (other == settlement || other.id().equals(settlement.id())
                        || !other.hasLivingResidents()) {
                    continue;
                }
                // A hostile people do not send wagons, they send raids. A goblin
                // mire named on the nameplate of a friendly trader would be the
                // single most misleading thing in the game.
                if (Culture.of(other.cultureId()).isHostile()) {
                    continue;
                }
                long distance = other.center().horizontalDistanceSq(settlement.center());
                if (distance < away) {
                    away = distance;
                    nearest = other;
                }
            }
        }
        return nearest == null ? THE_ROAD : nearest.name();
    }
}
