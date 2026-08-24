package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

/**
 * Carries bulk goods to the store that is about to need them.
 *
 * <p>Produce lands where it was made, which is right and is also why a town can
 * end up with its timber by the woods and a build going up on the other side of
 * the village. A builder will walk to whichever store actually holds what they
 * need, so nothing deadlocks — but they will walk a long way, over and over,
 * and the goods never come any closer.
 *
 * <p><strong>The signal is a build, not a difference.</strong> An "even the
 * stores out" rule oscillates: every move it makes creates the imbalance that
 * justifies moving something back. This asks a narrower question — what is the
 * town building, which store is nearest to that, and is it short — so goods
 * only ever move toward work that is actually waiting on them. It is
 * MineColonies' request system in miniature, with the build queue standing in
 * for a worker filing a request.
 *
 * <p><strong>And a source is never drained into a shortage of its own.</strong>
 * A store must hold a full load <em>above</em> the shortage line before it will
 * give any of it away, which is the hysteresis that stops two stores passing
 * the same timber back and forth for the rest of the game.
 */
public final class SupplyPlanner {

    /**
     * Below this, a store counts as short of something.
     *
     * <p>Two stacks: enough that a builder fetching a load of sixteen has
     * several trips in hand before anybody needs to think about it again.
     */
    public static final int SHORTAGE = 32;

    /** What a courier shoulders in one trip. */
    public static final int LOAD = 64;

    private SupplyPlanner() {
    }

    /**
     * Sends at most one courier a step.
     *
     * <p>One, deliberately. A town with four idle hands and three empty stores
     * would otherwise dispatch everybody on the same afternoon and strip the
     * source before any of them arrived — the arithmetic is applied when a load
     * is picked up, not when the errand is set, so several errands against the
     * same shelves all look affordable at the moment they are given out.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        Building destination = wantsGoods(settlement);
        if (destination == null) {
            return;
        }
        for (String resource : Resources.STORED) {
            if (destination.stores().get(resource) >= SHORTAGE) {
                continue;
            }
            Building source = sparest(settlement, destination, resource);
            if (source == null) {
                continue;
            }
            Person carrier = freeHand(settlement);
            if (carrier == null) {
                return;   // everybody is busy; the shortage keeps until somebody is not
            }
            carrier.setHaul(new HaulTask(resource,
                    HaulTask.Store.STORE, source.origin(),
                    HaulTask.Store.STORE, destination.origin(), LOAD));
            return;
        }
    }

    /**
     * The store nearest whatever the town is building, or null.
     *
     * <p>The head of the queue rather than the whole of it: that is the work
     * that will actually draw on a store next, and picking one destination is
     * what keeps goods moving in a single direction.
     */
    static Building wantsGoods(Settlement settlement) {
        if (settlement.buildQueue().isEmpty()) {
            return null;   // nothing being built, so nothing is waiting on goods
        }
        return settlement.nearestStore(settlement.buildQueue().getFirst().site());
    }

    /**
     * The store best able to spare a load of this, or null if none can.
     *
     * <p>"Able to spare" is a full load above the shortage line, so giving one
     * away cannot put the source below where the destination is now. Among
     * those that qualify, the fullest goes first — a town evens out from the
     * top rather than passing its last stack around.
     */
    static Building sparest(Settlement settlement, Building destination, String resource) {
        Building best = null;
        int most = 0;
        for (Building building : settlement.buildingsWithRole(BuildingRole.STORE)) {
            if (building == destination) {
                continue;
            }
            int held = building.stores().get(resource);
            if (held < SHORTAGE + LOAD) {
                continue;   // it would only be creating the next shortage
            }
            if (held > most) {
                most = held;
                best = building;
            }
        }
        return best;
    }

    /**
     * Somebody who can be sent, or null.
     *
     * <p>Anybody not already carrying something and not too weak to lift it —
     * deliberately not a dedicated profession, because a town that had to staff
     * a carrier before its stores could talk to each other would spend one of
     * its four founding settlers on walking.
     *
     * <p>Builders are passed over, and that is not politeness. The demand this
     * planner answers to <em>is</em> a build, so sending the person raising it
     * to fetch its own materials would stop the work in order to supply it —
     * and on a town with one builder, would stop it every time the store beside
     * the site ran low. Anyone else goes instead; if the town is nothing but
     * builders, the shortage waits, which is the right answer because they are
     * already fetching their own loads a stack at a time.
     *
     * <p>Farmers too, for a harder reason. {@code FoodPlanner} runs first and
     * gives them their errands, so one already carrying grain is safe — but one
     * merely idle this step is not, and a farmer sent off with a load of timber
     * is a farmer not in the field when the granary next wants filling. This
     * project has already killed one town by making a walk take priority over
     * eating. Timber can wait; nothing else here can.
     */
    static Person freeHand(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (person.haul() != null || person.isTooWeakToWork()) {
                continue;
            }
            if (settlement.laboursAs(person, Profession.BUILDER)
                    || settlement.laboursAs(person, Profession.FARMER)) {
                continue;
            }
            return person;
        }
        return null;
    }
}
