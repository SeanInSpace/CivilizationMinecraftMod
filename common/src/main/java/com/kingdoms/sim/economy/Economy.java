package com.kingdoms.sim.economy;

import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.settlement.Settlement;

/**
 * The town's money, and the fact that only the town has any.
 *
 * <p><strong>A settler owns nothing and wants for nothing.</strong> They belong
 * to the town and the town belongs to them: what they cut, grow, mine or find
 * goes to the common stores, and what they need comes back out of it at no
 * charge. A farmer carrying bread home from the market is not shopping, they
 * are being fed. There are no wages, no purses, and no prices between one
 * settler and another.
 *
 * <p>This is a deliberate retreat from a more detailed arrangement that did
 * have personal purses and a payday. It ran correctly and added nothing anybody
 * could see: purses were invisible, {@code Person.spend} was never called by a
 * single caller, and a settler with four hundred coin behaved exactly like one
 * with none. Detail earns its place when it produces stories rather than
 * numbers, and that detail produced numbers.
 *
 * <p><strong>Money exists for exactly one relationship: the town and an
 * outsider.</strong> A settlement is founded holding {@link Settlement#FOUNDING_TREASURY}
 * and that is the whole supply until somebody trades with it. Nothing mints
 * more. Coin leaves when the town buys, and arrives when it sells, and the only
 * party on the far side of either is a player — see {@code TRADE.md}.
 *
 * <p>Which means the treasury is a real constraint rather than a growing
 * number: a town that spends its endowment on walls has spent it, and getting
 * more is something somebody has to come and do.
 */
public final class Economy {

    private Economy() {
    }

    /**
     * Hands whatever a settler is carrying over to the town, for nothing.
     *
     * <p>They found it while working for the town, so it was the town's before
     * they picked it up. This is the internal half of the economy in its
     * entirety: goods move, coin does not.
     *
     * <p>Food is kept. A settler carrying their dinner is carrying their dinner,
     * and stripping it into the granary the moment they walk past would have
     * them starve in front of a full larder.
     *
     * @return how many items were handed over
     */
    public static int handIn(Settlement settlement, Person person, java.util.function.
            BiConsumer<String, Integer> intoStores) {
        int given = 0;
        for (var slot : java.util.List.copyOf(person.inventory().slots())) {
            if (com.kingdoms.sim.person.Foods.nutrition(slot.itemId()) > 0) {
                continue;   // their dinner is their dinner
            }
            int taken = person.inventory().remove(slot.itemId(), slot.count());
            if (taken > 0) {
                intoStores.accept(slot.itemId(), taken);
                given += taken;
            }
        }
        return given;
    }

    /**
     * Whether this person is carrying anything the town would want put away.
     *
     * <p>Deliberately not urgent. A settler who dropped a day's work the instant
     * they picked something up would be a worse settler than one who finishes
     * the row and takes it in afterwards. So this is consulted after the town's
     * own errands and before the day job, and only once somebody is carrying
     * enough to be worth the walk.
     *
     * <p>Pockets full counts on its own: somebody with nowhere left to put
     * anything has stopped being able to pick things up, which is the one case
     * where the trip really is the useful thing to do.
     */
    public static boolean wantsToUnload(Person person) {
        if (person.haul() != null || person.isTooWeakToWork()) {
            return false;   // already carrying the town's errand, or too weak to care
        }
        int worth = 0;
        int carried = 0;
        for (var slot : person.inventory().slots()) {
            if (com.kingdoms.sim.person.Foods.nutrition(slot.itemId()) > 0) {
                continue;
            }
            carried += slot.count();
            worth += Valuation.priceOf(slot.itemId()) * slot.count();
        }
        if (carried <= 0) {
            return false;
        }
        return worth >= WORTH_THE_WALK || pocketsFull(person);
    }

    /**
     * What a load has to be worth before it is worth walking in with.
     *
     * <p>Priced by {@link Valuation} even though nobody is paid for it: what a
     * thing is worth is still the best measure of whether it is worth crossing
     * the village carrying, and it is the same table the market will charge a
     * player from.
     */
    public static final int WORTH_THE_WALK = 20;

    /** Whether somebody has run out of room to pick anything else up. */
    public static boolean pocketsFull(Person person) {
        return person.inventory().slots().size() >= com.kingdoms.sim.person.Inventory.SLOTS;
    }
}
