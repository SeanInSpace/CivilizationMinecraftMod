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

    /**
     * Logs handed over per emerald.
     *
     * <p><strong>This does not agree with the market, and the gap is a pump.</strong>
     * {@code Market.basePrice(WOOD)} is two coin for one log, so a town holding
     * both buildings — which every town does from FORTIFIED — sells eight logs
     * here for a coin and buys them back at the stall for sixteen. Fifteen coin
     * a click, and a founding endowment of two thousand drains in about a
     * hundred and thirty of them.
     *
     * <p>It is left alone deliberately. The two numbers were chosen for
     * different jobs and either could be the wrong one: this rate is the
     * founding arc's helping hand, sized so that a player can buy a young town's
     * palisade out of a shortfall, and pricing it at the market's three coin a
     * log makes that gesture unaffordable. {@code TRADE.md} has its own answer —
     * the market should be the only counter a town has — and acting on that
     * means deciding what happens to this one, which is not a decision the
     * market's own work should make on the storehouse's behalf.
     */
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
     * What a bundle of logs actually costs at the door.
     *
     * <p>One formula with two readers — this planner banks it and the block
     * shrinks the player's stack by it — because they were separate and could
     * therefore drift into a town being paid a different number of emeralds
     * from the one the player handed over. A part bundle is still charged for:
     * asking for five emeralds' worth and being given the four logs above the
     * reserve costs a coin, not nothing.
     */
    public static int emeraldsFor(int logs) {
        return logs <= 0 ? 0 : Math.max(1, logs / WOOD_PER_EMERALD);
    }

    /**
     * Sells timber for emeralds, reserve honoured.
     *
     * <p>The emeralds go into the treasury. They used to go nowhere: the
     * storehouse consumed a player's emeralds and handed out logs without the
     * town's books changing by a coin, which quietly broke the one invariant
     * the two representations of money have — every emerald in the world came
     * out of a treasury, and every emerald that leaves it goes into one. A
     * counter that destroys money is a counter that can be used to drain the
     * whole supply out of a world.
     *
     * @return logs actually handed over
     */
    public static int sellTimber(Settlement settlement, int emeralds) {
        int wanted = Math.max(0, emeralds) * WOOD_PER_EMERALD;
        int sold = Math.min(wanted, timberForSale(settlement));
        if (sold > 0) {
            settlement.stores().take(TownStores.WOOD, sold);
            settlement.bank(emeraldsFor(sold));
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
