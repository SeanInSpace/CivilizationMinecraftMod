package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.world.SimContext;

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

    /** Timber the town can stockpile before further felling is pointless. */
    public static final int BASE_WOOD_STORAGE = 200;
    public static final int WOOD_PER_STOREHOUSE = 400;

    private LumberPlanner() {
    }

    public static void advance(Settlement settlement, SimContext ctx) {
        if (settlement.lumberArea() == null) {
            SimPos camp = campPos(settlement);
            if (camp != null) {
                settlement.setLumberArea(new WorkArea(camp, DEFAULT_RADIUS));
                settlement.logEvent(ctx.step(),
                        "The lumber camp claims the woodland around " + camp);
            }
        }
    }

    /** Where the camp stands, or null if the town has not built one. */
    public static SimPos campPos(Settlement settlement) {
        for (Building building : settlement.buildings()) {
            if (building.blueprintId().endsWith("lumber_camp")) {
                return building.origin();
            }
        }
        return null;
    }

    public static int woodCapacity(Settlement settlement) {
        int stores = (int) settlement.buildings().stream()
                .filter(b -> b.blueprintId().endsWith("storehouse"))
                .count();
        return BASE_WOOD_STORAGE + stores * WOOD_PER_STOREHOUSE;
    }

    /** Whether felling is still worth doing, so lumberjacks idle instead of clear-cutting. */
    public static boolean wantsMoreTimber(Settlement settlement) {
        return settlement.woodStock() < woodCapacity(settlement);
    }

    public static int clampRadius(int radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }
}
