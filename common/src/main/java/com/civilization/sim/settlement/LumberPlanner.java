package com.civilization.sim.settlement;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimContext;
import java.util.List;

/**
 * The timber trade.
 *
 * <p>Lumberjacks fell trees inside the camp's {@link WorkArea}, carry the timber
 * to the town's stores, and replant what they cut so the woodland renews itself.
 * The felling and planting themselves are world work, done by the platform layer;
 * this class owns the parts the simulation is authoritative over — where the camp
 * may work, how much timber the town can hold, and the running totals.
 *
 * <p>A camp claims its own surroundings when built. Players move or resize that
 * claim through the camp block, never through config.
 *
 * <p><strong>What a camp produces is its {@link Stand}.</strong> Timber used to
 * be a percentage of an imagined felling, and at the shipped zero an unwatched
 * camp brought in nothing — which is how a town nobody visited came to jam its
 * build queue on a cottage forever. There is no percentage in this file any
 * more. There are trees, there are saplings, and there are the lumberjacks the
 * town actually has.
 */
public final class LumberPlanner {

    public static final int DEFAULT_RADIUS = 24;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 64;
    public static final int RADIUS_STEP = 8;

    /**
     * Logs a lumberjack gets a usable sapling out of: one in four.
     *
     * <p>Off the crown of the tree they just felled, and it is the world's own
     * number — {@code LumberjackWorker} saves one sapling per four logs it cuts,
     * keyed by position so it never drifts. The clock keeps the same rate for
     * the same felling, because the two fidelities are felling the same trees.
     */
    public static final int LOGS_PER_SAPLING = 4;

    /** Saplings worth keeping on hand for replanting. */
    public static final int MAX_SAPLINGS = 128;

    /**
     * Timber the town can stockpile before further felling is pointless.
     *
     * <p>Sized above what a founding party carries in, and well above it since
     * construction started spending timber rather than merely accruing it.
     *
     * <p>It has to stay above the kit and not merely near it: {@code Founding}
     * clamps the stock it grants to this, so a ceiling below
     * {@link com.civilization.sim.settlement.TownStores#FOUNDING_WOOD} does not
     * refuse the charter, it silently shaves it — and the party finds out by
     * running out of timber on the roof of its own hall.
     */
    public static final int BASE_WOOD_STORAGE = 896;
    public static final int WOOD_PER_STOREHOUSE = 400;

    private LumberPlanner() {
    }

    public static void advance(Settlement settlement, SimContext ctx) {
        SimPos camp = campPos(settlement);
        if (settlement.lumberArea() == null && camp != null) {
            settlement.setLumberArea(new WorkArea(camp, DEFAULT_RADIUS));
            settlement.logEvent(ctx.step(),
                    "The lumber camp claims the woodland around " + camp);
        }
        workTheWood(settlement, ctx);
    }

    /**
     * A step of the timber trade, camp by camp.
     *
     * <p>What a camp brings in is what its {@link Stand} holds, and nothing
     * else. There is no yield table in this method and there is not supposed to
     * be one: {@code YieldPolicy} still lists {@code wood} and {@code saplings},
     * and nothing reads either any more.
     *
     * <p>Felling is world work — {@code LumberjackWorker} swings the axe — but
     * that only runs where a player is close enough to see it, and an unwatched
     * town that produced nothing stopped building forever the moment you walked
     * away. So the clock fells too, off the same ledger, at the same pace, and
     * puts the same saplings back. What it will not do is fell a tree that is
     * not there.
     */
    private static void workTheWood(Settlement settlement, SimContext ctx) {
        List<Building> camps = settlement.buildingsWithRole(BuildingRole.LUMBER_CAMP);
        if (camps.isEmpty()) {
            return;
        }
        int jacks = (int) settlement.residents().stream()
                .filter(p -> p.profession() == Profession.LUMBERJACK && !p.isTooWeakToWork())
                .count();
        boolean wantsTimber = wantsMoreTimber(settlement);
        int places = camps.size();
        for (int i = 0; i < places; i++) {
            Building camp = camps.get(i);
            boolean watched = reckonStand(settlement, camp, ctx);
            // The wood grows whether or not the town wants it and whether or not
            // anybody is there: a loaded chunk grows its own saplings and this is
            // only mirroring them.
            Stand.grow(camp, ctx);
            if (watched) {
                continue;   // where there is a hand there is no clock
            }
            int share = Workforce.shareOf(jacks, i, places);
            if (share <= 0) {
                continue;
            }
            // Put down at the camp that felled it, not into the town at large,
            // and split between the camps rather than credited all to the first.
            // The ceiling is still the whole town's: produceNear measures the
            // room it has left before each drop.
            if (wantsTimber) {
                fell(settlement, camp, share);
            }
            // A jack with nothing left to cut, or a town with nowhere to put
            // what he cuts, goes and puts the wood back — which is exactly what
            // LumberjackWorker does with the same two conditions.
            if (!wantsTimber || Stand.trees(camp) <= 0) {
                replant(settlement, camp, share);
            }
        }
    }

    /** One camp's felling for one step, and the saplings that come off the crowns. */
    private static void fell(Settlement settlement, Building camp, int jacks) {
        int room = Math.max(0, woodCapacity(settlement) - settlement.woodStock());
        int logs = Stand.fell(camp, Math.min(jacks * Stand.LOGS_PER_JACK_PER_STEP, room));
        if (logs <= 0) {
            return;
        }
        SimPos at = camp.origin();
        settlement.produceNear(at, TownStores.WOOD, logs, woodCapacity(settlement));
        // Saplings off the crowns, counted against the running total of logs
        // rather than against this step's felling. One in four of `logs` is
        // nought for every step a camp cuts fewer than four — which is every
        // step of the long stretch when a claim is living off its own regrowth,
        // and it rounded the woodland's whole future away: the stand fell to
        // nothing, the seed box emptied, and the camp stood idle beside ground
        // it was never going to replant. The tally carries the remainder, and it
        // is the same tally the real axe raises, so the two fidelities save seed
        // at one rate between them.
        //
        // Capped: saplings are for replanting, not a stockpile. A playtest left a
        // town holding a thousand of them, which is noise in the ledger.
        int felledBefore = settlement.tallies().get(Tallies.TREES_FELLED);
        int felledNow = settlement.tallies().record(Tallies.TREES_FELLED, logs);
        settlement.produceNear(at, TownStores.SAPLINGS,
                felledNow / LOGS_PER_SAPLING - felledBefore / LOGS_PER_SAPLING,
                MAX_SAPLINGS);
    }

    /** Saplings out of the town's box and into the ground, at the jacks' own pace. */
    private static void replant(Settlement settlement, Building camp, int jacks) {
        int put = Math.min(jacks * Stand.SAPLINGS_PER_JACK_PER_STEP,
                settlement.saplingStock());
        for (int i = 0; i < put; i++) {
            settlement.stores().takeUpTo(TownStores.SAPLINGS, 1);
            Stand.plant(camp);
        }
    }

    /**
     * Counts the camp's trees when the ground can answer, and says whether
     * anybody is watching.
     *
     * <p>The trees are their own truth, so this is the opposite of what a field
     * does on the same flip. A field has to be <em>told</em> what its ledger
     * says, because the age of a wheat block is invisible bookkeeping; a trunk
     * either stands or it does not, so a camp somebody has just walked up to
     * simply counts what is there and believes it.
     *
     * <p>It also runs once for a camp nobody has ever counted — which is not the
     * same as a camp with nothing standing. A camp saved before a player ever
     * reached it keeps {@link Stand#UNCOUNTED} until the day its chunks load,
     * and only then finds out what it is standing in.
     */
    private static boolean reckonStand(Settlement settlement, Building camp, SimContext ctx) {
        boolean watched = ctx.bridge().playerWithin(
                camp.origin(), ctx.settings().observedRadius());
        boolean arriving = watched && !camp.wasWatched();
        if (arriving || !Stand.isCounted(camp)) {
            if (ctx.bridge().isLoaded(camp.origin())) {
                WorkArea area = settlement.lumberArea();
                int radius = area == null ? DEFAULT_RADIUS : area.radius();
                Stand.recount(camp, ctx.bridge().countTreesNear(camp.origin(), radius));
            } else if (!Stand.isCounted(camp)) {
                // Nobody can look, and nearly nobody ever can: a town on the far
                // side of the world is unloaded almost all of its life. Treating
                // that as "no trees" would have reinstated the fault this file
                // was rewritten to fix — an unwatched town that produces nothing
                // and jams its build queue forever. So the camp is credited with
                // the stand its own siting implies until the day somebody can
                // count it. See Stand.UNSURVEYED.
                Stand.recount(camp, Stand.UNSURVEYED);
            }
        }
        camp.setWatched(watched);
        return watched;
    }

    /** Where the camp stands, or null if the town has not built one. */
    public static SimPos campPos(Settlement settlement) {
        for (Building building : settlement.buildings()) {
            if (building.role() == BuildingRole.LUMBER_CAMP) {
                return building.origin();
            }
        }
        return null;
    }

    public static int woodCapacity(Settlement settlement) {
        return BASE_WOOD_STORAGE
                + BuildPlanner.storeStrength(settlement) * WOOD_PER_STOREHOUSE;
    }

    /** Whether felling is still worth doing, so lumberjacks idle instead of clear-cutting. */
    public static boolean wantsMoreTimber(Settlement settlement) {
        return settlement.woodStock() < woodCapacity(settlement);
    }

    public static int clampRadius(int radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }
}
