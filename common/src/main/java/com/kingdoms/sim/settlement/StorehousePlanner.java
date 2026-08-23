package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;

/**
 * The storehouse's dealings with the player: donations in, timber out.
 *
 * <p>This is the founding arc's helping hand. The storehouse arrives at
 * FORTIFIED, exactly when the palisade is drinking the town's timber, and a
 * player who wants their young town walled sooner can carry logs to the door
 * or buy the shortfall away with emeralds. The market sells the town's bread;
 * the storehouse deals in the stuff the town is built from.
 *
 * <p>Same seed-corn rule as the market, applied to timber: a reserve is never
 * for sale, so a town cannot be bought out of its own repairs.
 */
public final class StorehousePlanner {

    /** Logs handed over per emerald. */
    public static final int WOOD_PER_EMERALD = 8;

    /** Timber the town keeps back no matter what is offered for it. */
    public static final int RESERVE_WOOD = 32;

    private StorehousePlanner() {
    }

    /** Timber the town would part with today. */
    public static int timberForSale(Settlement settlement) {
        return Math.max(0, settlement.woodStock() - RESERVE_WOOD);
    }

    /**
     * Sells timber for emeralds, reserve honoured.
     *
     * @return logs actually handed over
     */
    public static int sellTimber(Settlement settlement, int emeralds) {
        int wanted = Math.max(0, emeralds) * WOOD_PER_EMERALD;
        int sold = Math.min(wanted, timberForSale(settlement));
        if (sold > 0) {
            settlement.stores().take(TownStores.WOOD, sold);
        }
        return sold;
    }

    /**
     * Accepts a donation into the named store, up to its capacity.
     *
     * @return how much the town could actually take
     */
    public static int donate(Settlement settlement, SimPos at, String resource, int amount) {
        int ceiling = switch (resource) {
            case TownStores.WOOD -> LumberPlanner.woodCapacity(settlement);
            case TownStores.STONE -> MinePlanner.stoneCapacity(settlement);
            case TownStores.FOOD -> FoodPlanner.granaryCapacity(settlement);
            default -> 0;
        };
        if (ceiling <= 0 || amount <= 0) {
            return 0;
        }
        return settlement.produceNear(at, resource, amount, ceiling);
    }
}
