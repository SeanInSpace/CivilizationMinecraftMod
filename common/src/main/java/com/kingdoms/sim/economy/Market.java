package com.kingdoms.sim.economy;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
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
 * <p><strong>Every deal carries its reason.</strong> {@link Reason} is a
 * component of {@link Deal} rather than something a screen can work out for
 * itself, because a price and an explanation derived separately are two things
 * that can disagree — and the explanation is the whole reason the town has a
 * screen of its own instead of a merchant's.
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

    /**
     * Why a price is what it is.
     *
     * <p>The whole argument for the town having a screen of its own rather than
     * a merchant's. A merchant screen can show that grain costs six; it has
     * nowhere to put <em>they are starving</em>, and the reason is the town's
     * entire character. Decided here, where it can be tested, and worded by
     * whatever is drawing it — the simulation has never known what language the
     * player reads.
     */
    public enum Reason {

        /** It is in real trouble and paying accordingly. {@link #DESPERATE}. */
        DESPERATE,

        /** Short of this, and paying over the odds to fix that. {@link #SHORT}. */
        SHORT,

        /** Neither short nor overflowing. The price is the plain one. */
        ORDINARY,

        /**
         * It has nowhere left to put this, which is why it will not buy any and
         * is glad to see the back of what it has. The same arithmetic answers
         * both: no room is exactly the condition {@link #buyOffer} refuses on.
         */
        GLUT
    }

    /** One thing the town is willing to do, in one direction, at one price. */
    public record Deal(String resource, boolean townBuys, int unitPrice, int lots,
                       Reason reason) {

        /** Coin for one lot, which is what a single press of a button costs. */
        public int lotPrice() {
            return unitPrice * LOT;
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
        if (!TRADED.contains(resource)) {
            return null;
        }
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
        return new Deal(resource, true, price, Math.min(lots, 8),
                reasonFor(settlement, resource, true));
    }

    /**
     * What the town will sell this for, or null if it will not part with any.
     *
     * <p>A reserve is always kept, so no player with deep pockets can strip a
     * settlement bare and leave it to starve. What is offered is genuine
     * surplus and nothing else.
     */
    public static Deal sellOffer(Settlement settlement, String resource) {
        // The list is checked here and not only where the board is built,
        // because the board is not what a request is answered against. A
        // settlement's ledger takes any word at all: the smith stocks "weapons"
        // and "armour", they have no base price, and a sell price of "at least
        // base plus one" and a reserve of "nothing" would have sold a town's
        // whole armoury at a coin an ingot to anyone who could name it.
        if (!TRADED.contains(resource)) {
            return null;
        }
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
        return new Deal(resource, false, price, Math.min(spare / LOT, 8),
                reasonFor(settlement, resource, false));
    }

    /**
     * What the town would say about this price if it could talk.
     *
     * <p>Read off the same two rules the offers themselves are built from — the
     * need factor and the room left on the shelves — so the reason and the
     * price can never tell a player different stories.
     *
     * <p>A glut deliberately does not make the goods cheaper. {@code TRADE.md}
     * would have a full town sell at a discount, and it cannot: the sell price
     * has to stay strictly above the base or a player buys a lot cheap, the
     * shelves come down by exactly that lot, the town wants it again at base
     * and the treasury is a fountain — and with stone's base at one there is no
     * room below "base plus one" to discount into. So a glut shows up in what
     * the town says rather than in what it charges, which is the part of the
     * design that was actually load-bearing.
     */
    public static Reason reasonFor(Settlement settlement, String resource,
                                   boolean townBuys) {
        if (!townBuys) {
            return isGlutted(settlement, resource) ? Reason.GLUT : Reason.ORDINARY;
        }
        int need = needFactor(settlement, resource);
        if (need >= DESPERATE) {
            return Reason.DESPERATE;
        }
        return need >= SHORT ? Reason.SHORT : Reason.ORDINARY;
    }

    /**
     * Whether the town has run out of room for this.
     *
     * <p>Stated as "one more lot would not fit" rather than as a fraction of the
     * ceiling, because that is precisely the test {@link #buyOffer} refuses on.
     * Two thresholds for one idea would let a town say it was overflowing while
     * still buying, which is the kind of disagreement a player reads as a lie.
     */
    public static boolean isGlutted(Settlement settlement, String resource) {
        return ceilingFor(settlement, resource) - held(settlement, resource) < LOT;
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
    public static int townBuys(Settlement settlement, SimPos at,
                               String resource, int units) {
        Deal deal = buyOffer(settlement, resource);
        if (deal == null || units <= 0 || units > deal.lots() * LOT) {
            return 0;
        }
        int cost = deal.unitPrice() * units;
        if (!settlement.spend(cost)) {
            return 0;
        }
        // No ceiling check here, and none is missing: the offer's lots were cut
        // to the room the town had, so anything this deal covers fits.
        settlement.storeNear(at).add(resource, units);
        return cost;
    }

    /**
     * Sells a player goods out of the town's surplus.
     *
     * @return coin taken, or zero if the deal could not be honoured
     */
    public static int townSells(Settlement settlement, SimPos at,
                                String resource, int units) {
        Deal deal = sellOffer(settlement, resource);
        if (deal == null || units <= 0 || units > deal.lots() * LOT) {
            return 0;
        }
        if (settlement.stores().get(resource)
                - units < reserveFor(settlement, resource)) {
            return 0;   // that would eat into the reserve
        }
        int taken = drawNearest(settlement, at, resource, units);
        if (taken < units) {
            // Unreachable while a reserve cannot be negative: passing the check
            // above means the town owns at least this much somewhere, and the
            // draw walks every holder it has. Kept because the alternative to
            // an impossible branch is a silent short delivery.
            settlement.storeNear(at).add(resource, taken);   // put back; all or nothing
            return 0;
        }
        int price = deal.unitPrice() * units;
        settlement.bank(price);
        return price;
    }

    /**
     * Takes goods off the shelves that actually hold them, nearest first.
     *
     * <p>The store nearest the counter is not necessarily a store with anything
     * in it. Asking only that one and giving up was a town refusing to sell
     * timber it demonstrably owned because the market happened to be built
     * beside the granary — and the refusal was silent, because a deal the town
     * declines and a deal it cannot reach look the same from outside.
     *
     * <p>So locality decides the <em>order</em> the shelves come down in, not
     * whether the sale happens at all. That is the same rule
     * {@link Settlement#nearestStore(SimPos, String)} already gives a builder
     * who would otherwise be stranded by an empty store underfoot.
     *
     * <p>The loose pile is drawn last because a town with a storehouse should
     * be selling out of it, and a camp with none keeps everything it owns lying
     * in the open — where it is still the town's to sell.
     */
    private static int drawNearest(Settlement settlement, SimPos at,
                                   String resource, int units) {
        int drawn = 0;
        while (drawn < units) {
            Building holder = settlement.nearestStore(at, resource);
            if (holder == null) {
                break;
            }
            int took = holder.stores().takeUpTo(resource, units - drawn);
            if (took <= 0) {
                break;   // nearestStore only answers holders, and a loop trusting that could hang
            }
            drawn += took;
        }
        if (drawn < units) {
            drawn += settlement.loosePile().takeUpTo(resource, units - drawn);
        }
        return drawn;
    }
}
