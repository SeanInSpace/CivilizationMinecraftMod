package com.kingdoms.sim.economy;

import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Settlement;

/**
 * Money, and the loop it goes round.
 *
 * <p>A settlement of this culture is not a commune. The town owns what its
 * people are paid to produce — a lumberjack's timber is the town's the moment
 * it is cut, and nobody buys it back — but a person owns what they come across,
 * and can sell it. That single distinction is the whole of the arrangement, and
 * everything here follows from it.
 *
 * <p><strong>The loop.</strong> Money has to come from somewhere and go
 * somewhere, or the first rare thing a settler finds empties the treasury for
 * good.
 *
 * <ol>
 *   <li><strong>A levy on production.</strong> The town realises a little coin
 *       from everything its workers produce. This is the only place money is
 *       created, and it is created in proportion to work actually done, so a
 *       town that produces nothing earns nothing.</li>
 *   <li><strong>Wages back out.</strong> Every able worker draws a wage each
 *       payday, from the treasury, and only if the treasury can cover it. A
 *       broke town does not pay, which is a legible failure rather than a
 *       negative number.</li>
 *   <li><strong>Buying finds.</strong> A settler who brings a find to the
 *       market is paid for it out of the same treasury, and the thing itself
 *       becomes the town's.</li>
 * </ol>
 *
 * <p>So coin flows town → worker as wages, worker → town as goods, and the
 * total is anchored to how much the settlement actually makes. A rich town is
 * one that has been producing; a poor one cannot buy the sword somebody found,
 * and has to say so.
 */
public final class Economy {

    private Economy() {
    }

    /**
     * Coin the town realises per unit of goods produced.
     *
     * <p>Deliberately less than one: production is counted in units of timber
     * and stone, of which a working town makes a great many, and a coin apiece
     * would make every settlement rich beyond any use for it. See
     * {@link #LEVY_PER}.
     */
    public static final int LEVY_COIN = 1;

    /** Units of produce that yield {@link #LEVY_COIN}. */
    public static final int LEVY_PER = 4;

    /** A day's pay for one worker. */
    public static final int WAGE = 1;

    /** Steps between paydays. Long enough that wages are an event, not a drip. */
    public static final int PAYDAY_EVERY = 12;

    /**
     * What the town takes from a delivery of goods.
     *
     * <p>Rounded down, so very small deliveries yield nothing at all rather
     * than rounding a single log up into a coin.
     */
    public static int levyOn(int producedUnits) {
        if (producedUnits <= 0) {
            return 0;
        }
        return producedUnits / LEVY_PER * LEVY_COIN;
    }

    /** Whether this step is a payday. */
    public static boolean isPayday(long step) {
        return step > 0 && step % PAYDAY_EVERY == 0;
    }

    /**
     * Whether this person draws a wage.
     *
     * <p>Everybody who works. Idlers do not, and neither does anybody too weak
     * with hunger to have done anything — not as a punishment, but because the
     * wage is for the work and there has not been any.
     */
    public static boolean earnsWage(Person person) {
        return person.profession() != Profession.IDLER && !person.isTooWeakToWork();
    }

    /**
     * Pays everybody who is owed, as far as the treasury reaches.
     *
     * <p>All or nothing per person rather than a part-wage each: half a coin
     * paid to everybody is worse than a full coin paid to as many as the town
     * can afford, and it keeps the arithmetic in whole numbers.
     *
     * @return how much was paid out in total
     */
    public static int payWages(Settlement settlement) {
        int paid = 0;
        for (Person person : settlement.residents()) {
            if (!earnsWage(person)) {
                continue;
            }
            if (settlement.treasury() < WAGE) {
                break;   // the purse is empty; the rest go unpaid and it shows
            }
            settlement.spend(WAGE);
            person.earn(WAGE);
            paid += WAGE;
        }
        return paid;
    }

    /**
     * What the town will pay this person for what they are carrying, and takes it.
     *
     * <p>The find becomes the town's and the coin becomes theirs. Nothing
     * happens at all if the treasury cannot cover it — a town that cannot pay
     * does not get the sword, which is the point of the treasury being finite.
     *
     * @return the item sold and what it fetched, or null if nothing changed
     */
    public static Sale sellOne(Settlement settlement, Person seller) {
        String best = null;
        int bestPrice = 0;
        for (var slot : seller.inventory().slots()) {
            int price = Valuation.priceOf(slot.itemId());
            if (price > bestPrice) {
                best = slot.itemId();
                bestPrice = price;
            }
        }
        if (best == null || bestPrice <= 0 || settlement.treasury() < bestPrice) {
            return null;
        }
        if (seller.inventory().remove(best, 1) <= 0) {
            return null;
        }
        settlement.spend(bestPrice);
        seller.earn(bestPrice);
        return new Sale(best, bestPrice);
    }

    /**
     * Whether this person has reason to walk to the market.
     *
     * <p>Deliberately not urgent. A settler who dropped a day's work the instant
     * they picked up a sword would be a worse settler than one who finishes the
     * row and takes it in afterwards, and a town whose whole workforce downed
     * tools every time a skeleton died would never build anything. So this is
     * consulted after the errands and before the day job, and only once
     * somebody is carrying enough to be worth the walk.
     *
     * <p>Pockets nearly full counts as worth the walk on its own. A settler with
     * nowhere left to put anything has stopped being able to pick things up,
     * which is the one case where the trip really is the useful thing to do.
     */
    public static boolean wantsMarket(Person person) {
        if (person.haul() != null || person.isTooWeakToWork()) {
            return false;   // already carrying the town's errand, or too hungry to care
        }
        int worth = 0;
        for (var slot : person.inventory().slots()) {
            worth += Valuation.priceOf(slot.itemId()) * slot.count();
        }
        if (worth <= 0) {
            return false;
        }
        return worth >= WORTH_THE_WALK || pocketsFull(person);
    }

    /**
     * Coin's worth of finds that makes the trip worth taking on its own.
     *
     * <p>Below this a settler carries it and gets on with the day; the market is
     * not going anywhere.
     */
    public static final int WORTH_THE_WALK = 20;

    /** Whether somebody has run out of room to pick anything else up. */
    public static boolean pocketsFull(Person person) {
        return person.inventory().slots().size() >= com.kingdoms.sim.person.Inventory.SLOTS;
    }

    /** One thing changing hands at the market. */
    public record Sale(String itemId, int price) {
    }
}
