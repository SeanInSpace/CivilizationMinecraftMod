package com.kingdoms.sim.economy;

import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;

import java.util.ArrayList;
import java.util.List;

/**
 * What a town will buy and sell today, and for how much.
 *
 * <p>The only place money changes hands. Settlers own nothing and are charged
 * nothing — see {@link Economy} — so every price here is a price to a player,
 * and a town's whole money supply is the endowment it was founded with plus
 * whatever a player has paid it.
 *
 * <p><strong>Prices move with need, and that is the point.</strong> A flat
 * table would be a shop; a price that doubles when the granary is empty is a
 * town telling you it is in trouble. Every signal used here already existed —
 * {@code isStarving}, the store ceilings, {@code wantsMoreTimber} — and was
 * only ever read by the planners. Exposing it is what turns a settlement's
 * problems into a player's opportunities, which is the whole design.
 *
 * <p>Nothing here touches the world. It answers what the deals are; handing
 * over emeralds is the platform's job.
 */
public final class Market {

    private Market() {
    }

    /** Goods a town will deal in at all. Its own produce, in bulk. */
    public static final List<String> TRADED = List.of(
            TownStores.FOOD, TownStores.WOOD, TownStores.STONE, TownStores.IRON);

    /** What one unit is worth to a town in ordinary circumstances. */
    public static int basePrice(String resource) {
        return switch (resource) {
            case TownStores.FOOD -> 2;
            case TownStores.WOOD -> 2;
            case TownStores.STONE -> 1;
            case TownStores.IRON -> 8;
            default -> 0;
        };
    }

    /**
     * The town's margin when selling.
     *
     * <p>It sells for more than it pays, because otherwise a player stands at
     * the stall buying and selling the same stone forever and the treasury is
     * a fountain. The spread is the one thing keeping the market honest.
     */
    public static final int SELL_NUMERATOR = 3;
    public static final int SELL_DENOMINATOR = 2;

    /** Multiplier on what a starving town will pay for food. */
    public static final int DESPERATE = 3;

    /** Multiplier on what a town short of a material will pay for it. */
    public static final int SHORT = 2;

    /** How many units a single deal covers, so a screen shows sane numbers. */
    public static final int LOT = 8;

    /** One thing the town is willing to do, in one direction, at one price. */
    public record Deal(String resource, boolean townBuys, int unitPrice, int lots) {

        /** Total coin for taking every lot of this deal. */
        public int totalPrice() {
            return unitPrice * LOT * lots;
        }
    }

    /**
     * Everything this town will trade right now.
     *
     * <p>Empty when it has no trader: a settlement with nobody whose job it is
     * does not deal, which is what makes the profession worth having and worth
     * losing.
     */
    public static List<Deal> offers(Settlement settlement) {
        List<Deal> deals = new ArrayList<>();
        if (!hasTrader(settlement)) {
            return deals;
        }
        for (String resource : TRADED) {
            Deal buying = buyOffer(settlement, resource);
            if (buying != null) {
                deals.add(buying);
            }
            Deal selling = sellOffer(settlement, resource);
            if (selling != null) {
                deals.add(selling);
            }
        }
        return deals;
    }

    /** Whether anybody here is in the business of trading. */
    public static boolean hasTrader(Settlement settlement) {
        return settlement.residents().stream()
                .anyMatch(person -> person.profession()
                        == com.kingdoms.sim.person.Profession.TRADER
                        && !person.isTooWeakToWork());
    }

    /**
     * What the town will pay a player for this, or null if it will not buy.
     *
     * <p>Bounded three ways, all of which already existed: it buys only up to
     * its storage ceiling, only what it actually wants, and only what its
     * treasury can cover. A poor town says no, which is the point of the
     * treasury being finite.
     */
    public static Deal buyOffer(Settlement settlement, String resource) {
        int room = ceilingFor(settlement, resource) - held(settlement, resource);
        if (room < LOT) {
            return null;   // nowhere to put it; a full town wants nothing at any price
        }
        int price = basePrice(resource) * needFactor(settlement, resource);
        if (price <= 0) {
            return null;
        }
        int affordable = settlement.treasury() / (price * LOT);
        int lots = Math.min(room / LOT, affordable);
        if (lots <= 0) {
            return null;   // it wants this and cannot pay for it, which is its own story
        }
        return new Deal(resource, true, price, Math.min(lots, 8));
    }

    /**
     * What the town will sell this for, or null if it will not part with any.
     *
     * <p>A reserve is always kept, so no player with deep pockets can strip a
     * settlement bare and leave it to starve. What is offered is genuine
     * surplus and nothing else.
     */
    public static Deal sellOffer(Settlement settlement, String resource) {
        if (needFactor(settlement, resource) > 1) {
            return null;   // it is short of this; it is certainly not selling it
        }
        int spare = held(settlement, resource) - reserveFor(settlement, resource);
        if (spare < LOT) {
            return null;
        }
        // Strictly dearer than the base, never merely equal to it. Integer
        // division is the trap: stone has a base of one, and one times three
        // over two is one, so the town sold at exactly what it paid. Worse than
        // a wash -- a town short of stone pays double for it while still
        // selling at one, and a player can stand at the stall turning the
        // treasury into their own money. The spread is what stops that, so it
        // has to survive cheap goods.
        int price = Math.max(basePrice(resource) + 1,
                basePrice(resource) * SELL_NUMERATOR / SELL_DENOMINATOR);
        return new Deal(resource, false, price, Math.min(spare / LOT, 8));
    }

    /**
     * How badly the town wants this: 1 ordinarily, more when it is short.
     *
     * <p>Zero would mean "will not buy", but a town is never uninterested in
     * principle — it is only ever out of room, which {@link #buyOffer} checks
     * separately. Keeping those two ideas apart is what lets a full granary
     * refuse grain while a starving one pays triple for it.
     */
    public static int needFactor(Settlement settlement, String resource) {
        return switch (resource) {
            case TownStores.FOOD -> FoodPlanner.isStarving(settlement) ? DESPERATE
                    : held(settlement, TownStores.FOOD)
                        < FoodPlanner.STARTING_PROVISIONS ? SHORT : 1;
            case TownStores.WOOD -> LumberPlanner.wantsMoreTimber(settlement)
                    && held(settlement, TownStores.WOOD) < LOT * 8 ? SHORT : 1;
            case TownStores.STONE -> MinePlanner.wantsMoreStone(settlement)
                    && held(settlement, TownStores.STONE) < LOT * 8 ? SHORT : 1;
            case TownStores.IRON -> held(settlement, TownStores.IRON) < 32 ? SHORT : 1;
            default -> 1;
        };
    }

    /** What the town will not sell however much a player offers. */
    public static int reserveFor(Settlement settlement, String resource) {
        return switch (resource) {
            // Enough to eat while somebody grows more. Selling below this is
            // selling the town's own dinner.
            case TownStores.FOOD -> Math.max(FoodPlanner.STARTING_PROVISIONS,
                    settlement.population() * 8);
            // What a build might be waiting on.
            case TownStores.WOOD -> 128;
            case TownStores.STONE -> 128;
            case TownStores.IRON -> 32;
            default -> 0;
        };
    }

    private static int ceilingFor(Settlement settlement, String resource) {
        return switch (resource) {
            case TownStores.FOOD -> FoodPlanner.granaryCapacity(settlement);
            case TownStores.WOOD -> LumberPlanner.woodCapacity(settlement);
            case TownStores.STONE -> MinePlanner.stoneCapacity(settlement);
            case TownStores.IRON -> MinePlanner.MAX_IRON;
            default -> 0;
        };
    }

    private static int held(Settlement settlement, String resource) {
        return settlement.stores().get(resource);
    }

    /**
     * Takes a player's goods and pays for them.
     *
     * <p>All or nothing. The goods land in the stores nearest wherever the
     * trade happened, and the coin leaves the treasury — the one place in the
     * mod where money moves for something other than public works.
     *
     * @return coin paid, or zero if the deal could not be honoured
     */
    public static int townBuys(Settlement settlement, com.kingdoms.sim.geom.SimPos at,
                               String resource, int units) {
        Deal deal = buyOffer(settlement, resource);
        if (deal == null || units <= 0 || units > deal.lots() * LOT) {
            return 0;
        }
        int cost = deal.unitPrice() * units;
        if (!settlement.spend(cost)) {
            return 0;
        }
        settlement.storeNear(at).add(resource, units);
        return cost;
    }

    /**
     * Sells a player goods out of the town's surplus.
     *
     * @return coin taken, or zero if the deal could not be honoured
     */
    public static int townSells(Settlement settlement, com.kingdoms.sim.geom.SimPos at,
                                String resource, int units) {
        Deal deal = sellOffer(settlement, resource);
        if (deal == null || units <= 0 || units > deal.lots() * LOT) {
            return 0;
        }
        if (settlement.stores().get(resource)
                - units < reserveFor(settlement, resource)) {
            return 0;   // that would eat into the reserve
        }
        int taken = settlement.storeNear(at).takeUpTo(resource, units);
        if (taken < units) {
            settlement.storeNear(at).add(resource, taken);   // put back; all or nothing
            return 0;
        }
        int price = deal.unitPrice() * units;
        settlement.bank(price);
        return price;
    }
}
