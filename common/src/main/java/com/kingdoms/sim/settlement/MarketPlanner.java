package com.kingdoms.sim.settlement;

/**
 * When the market is open.
 *
 * <p>A town that trades at all hours is a vending machine. Hours give the day a
 * shape: stalls are staffed in the morning and through the afternoon, the square
 * is busy while they are, and a player who turns up at midnight is told to come
 * back — which is worth more than the convenience it costs.
 *
 * <p>Times are vanilla day-time ticks: 0 is dawn, 6000 noon, 12000 dusk, 24000
 * the next dawn.
 */
public final class MarketPlanner {

    /** Stalls open a little after dawn. */
    public static final long OPENS_AT = 1000L;

    /** And close before dusk, so everyone is home before the light goes. */
    public static final long CLOSES_AT = 11000L;

    /** Food handed over per emerald paid. */
    public static final int FOOD_PER_EMERALD = 8;

    /** The town keeps this much back whatever the price — nobody sells the seed corn. */
    public static final int RESERVE_FOOD = 40;

    private MarketPlanner() {
    }

    /** Whether the stalls are staffed at this time of day. */
    public static boolean isOpen(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        return time >= OPENS_AT && time < CLOSES_AT;
    }

    /** How long until opening, in ticks, or zero if the market is already open. */
    public static long untilOpen(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        if (isOpen(time)) {
            return 0L;
        }
        return time < OPENS_AT ? OPENS_AT - time : 24000L - time + OPENS_AT;
    }

    public static boolean hasMarket(Settlement settlement) {
        return settlement.buildings().stream()
                .anyMatch(b -> b.role() == BuildingRole.MARKET);
    }

    /** What the town is willing to part with right now. */
    public static int foodForSale(Settlement settlement) {
        return Math.max(0, settlement.foodStock() - RESERVE_FOOD);
    }

    /**
     * Sells food for emeralds.
     *
     * @return how much food actually changed hands, which is zero if the town
     *         has none to spare
     */
    public static int sellFood(Settlement settlement, int emeralds) {
        int wanted = Math.max(0, emeralds) * FOOD_PER_EMERALD;
        int available = Math.min(wanted, foodForSale(settlement));
        if (available <= 0) {
            return 0;
        }
        settlement.stores().take(TownStores.FOOD, available);
        settlement.tallies().record(Tallies.GOODS_TRADED, available);
        return available;
    }
}
