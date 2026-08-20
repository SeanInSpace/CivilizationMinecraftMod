package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.world.SimContext;

/**
 * The stone trade — the timber trade's twin, one layer down.
 *
 * <p>Miners cut stone inside the mine's {@link WorkArea} and carry it to the
 * town's stores. As with {@link LumberPlanner}, the cutting itself is world work
 * done by the platform layer; this class owns what the simulation is
 * authoritative over — where the mine may work, how much stone the town can
 * hold, and the running total.
 *
 * <p>Stone matters because building now costs digging as well as laying. A town
 * that cannot cut its own is a town that cannot found the next one.
 *
 * <p>Deliberately mirrors LumberPlanner rather than sharing code with it. The two
 * trades are the same shape today and will not stay that way — ore, depth and
 * tool tiers all belong here and nowhere near the woodland.
 */
public final class MinePlanner {

    public static final int DEFAULT_RADIUS = 16;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 48;
    public static final int RADIUS_STEP = 8;

    /** Stone the town can stockpile before further cutting is pointless. */
    public static final int BASE_STONE_STORAGE = 512;
    public static final int STONE_PER_STOREHOUSE = 400;

    private MinePlanner() {
    }

    public static void advance(Settlement settlement, SimContext ctx) {
        if (settlement.mineArea() == null) {
            SimPos mine = minePos(settlement);
            if (mine != null) {
                settlement.setMineArea(new WorkArea(mine, DEFAULT_RADIUS));
                settlement.logEvent(ctx.step(),
                        "The mine claims the stone around " + mine);
            }
        }
    }

    /** Where the mine stands, or null if the town has not built one. */
    public static SimPos minePos(Settlement settlement) {
        for (Building building : settlement.buildings()) {
            if (building.blueprintId().endsWith("mine")) {
                return building.origin();
            }
        }
        return null;
    }

    public static int stoneCapacity(Settlement settlement) {
        int stores = (int) settlement.buildings().stream()
                .filter(b -> b.blueprintId().endsWith("storehouse")
                        || b.blueprintId().endsWith("warehouse"))
                .count();
        return BASE_STONE_STORAGE + stores * STONE_PER_STOREHOUSE;
    }

    /** Whether cutting is still worth doing, so miners idle instead of hollowing the hill. */
    public static boolean wantsMoreStone(Settlement settlement) {
        return settlement.stoneStock() < stoneCapacity(settlement);
    }

    public static int clampRadius(int radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }
}
