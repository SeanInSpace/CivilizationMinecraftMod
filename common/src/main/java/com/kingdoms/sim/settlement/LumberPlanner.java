package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;
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
 */
public final class LumberPlanner {

    public static final int DEFAULT_RADIUS = 24;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 64;
    public static final int RADIUS_STEP = 8;

    /** Timber one lumberjack brings in per step when nobody is watching them do it. */
    /**
     * Timber one lumberjack brings in per step when nobody is watching them do it.
     *
     * <p>Twice the miner's rate, because building spends wood twice as fast as
     * stone. A playtest with them equal left a town sitting on 900 stone and
     * sixteen planks, unable to raise anything.
     */
    public static final int WOOD_PER_STEP = 8;

    /** Saplings worth keeping on hand for replanting. */
    public static final int MAX_SAPLINGS = 128;

    /**
     * Timber the town can stockpile before further felling is pointless.
     *
     * <p>Sized above what a founding party carries in, and well above it since
     * construction started spending timber rather than merely accruing it.
     */
    public static final int BASE_WOOD_STORAGE = 512;
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
        fellUnwatched(settlement, ctx, camp);
    }

    /**
     * Timber brought in while nobody is looking.
     *
     * <p>Felling is world work — {@code LumberjackWorker} swings the axe — but that
     * only runs where a player is close enough to see it. Without this an unwatched
     * town produces nothing, and since building now costs materials, that means it
     * stops building forever the moment you walk away. Hauling already works at both
     * fidelities for exactly the same reason; so does this.
     */
    private static void fellUnwatched(Settlement settlement, SimContext ctx, SimPos camp) {
        if (camp == null || !wantsMoreTimber(settlement)) {
            return;
        }
        if (ctx.bridge().playerWithin(camp, ctx.settings().observedRadius())) {
            return;   // somebody is watching; the real axes are swinging
        }
        int jacks = (int) settlement.residents().stream()
                .filter(p -> p.profession() == Profession.LUMBERJACK && !p.isTooWeakToWork())
                .count();
        if (jacks <= 0) {
            return;
        }
        // Put down at the camp that felled it, not into the town at large, and
        // split between the camps rather than credited all to the first — a town
        // with two camps works both, so its timber should pile up at both. The
        // ceiling is still the whole town's: produceNear measures the room it
        // has left before each drop.
        List<Building> camps = settlement.buildingsWithRole(BuildingRole.LUMBER_CAMP);
        int places = Math.max(1, camps.size());
        for (int i = 0; i < places; i++) {
            int share = Workforce.shareOf(jacks, i, places);
            if (share <= 0) {
                continue;
            }
            SimPos at = camps.isEmpty() ? settlement.centre() : camps.get(i).origin();
            settlement.produceNear(at, TownStores.WOOD,
                    share * WOOD_PER_STEP, woodCapacity(settlement));
            // Capped: saplings are for replanting, not a stockpile. A playtest left
            // a town holding a thousand of them, which is noise in the ledger.
            settlement.produceNear(at, TownStores.SAPLINGS, share, MAX_SAPLINGS);
        }
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
