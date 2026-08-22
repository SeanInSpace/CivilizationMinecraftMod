package com.kingdoms.sim.settlement;

import com.kingdoms.sim.world.SimContext;

/**
 * The inn and its caravans: the town's window on the wider world.
 *
 * <p>While an inn stands, a trade caravan calls on a steady rhythm and swaps
 * the town's surplus bread for iron — the one resource no early settlement can
 * make for itself in quantity. The market's seed-corn rule applies verbatim:
 * only food above the market reserve is ever on the wagon.
 *
 * <p>V1 trades with an abstract caravan. Wandering traders a player can meet,
 * and caravans between settlements, arrive with the expansion work — the inn's
 * ledger entry is the interface they will settle through.
 */
public final class InnPlanner {

    /** Steps between caravans. */
    public static final int CARAVAN_PERIOD = 48;

    /** The most bread one wagon carries away. */
    public static final int CARAVAN_FOOD = 20;

    /** Loaves per ingot — bread is cheap and iron is not. */
    public static final int FOOD_PER_IRON = 4;

    private InnPlanner() {
    }

    public static void advance(Settlement settlement, SimContext ctx) {
        if (ctx.step() <= 0 || ctx.step() % CARAVAN_PERIOD != 0) {
            return;
        }
        if (settlement.countBuildings("kingdoms:inn") <= 0) {
            return;
        }
        int surplus = Math.max(0, settlement.foodStock() - MarketPlanner.RESERVE_FOOD);
        int sold = Math.min(surplus, CARAVAN_FOOD);
        int iron = sold / FOOD_PER_IRON;
        if (iron <= 0) {
            return;   // the wagon does not stop for crumbs
        }
        sold = iron * FOOD_PER_IRON;
        settlement.stores().take(TownStores.FOOD, sold);
        settlement.stores().add(TownStores.IRON, iron);
        settlement.logEvent(ctx.step(), "A caravan calls at the inn — "
                + sold + " loaves traded for " + iron + " iron");
    }
}
