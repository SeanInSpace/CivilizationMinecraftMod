package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.world.SimContext;

/**
 * Walks loads across the settlement.
 *
 * <p>Goods do not teleport between stores any more: somebody carries them. A
 * hauler walks to the source, picks the load up, walks it to the destination and
 * sets it down — and the load exists on their back the whole way, so ambushing a
 * carrier genuinely costs the town that food.
 *
 * <p>Both fidelities share one arrival test, because {@link Person#position()} is
 * the same field either way. Watched, it is synced from the walking entity each
 * second; unwatched, this class advances it {@link #ABSTRACT_TRAVEL_BLOCKS} at a
 * step. So a delivery takes about as long whether or not anyone is looking.
 */
public final class HaulPlanner {

    /** How far an unobserved hauler covers per simulation step. */
    public static final int ABSTRACT_TRAVEL_BLOCKS = 12;

    /** Close enough to load or unload. */
    public static final double ARRIVAL_RADIUS = 3.0;

    private HaulPlanner() {
    }

    public static void advance(Settlement settlement, SimContext ctx) {
        for (Person person : settlement.residents()) {
            HaulTask haul = person.haul();
            if (haul == null) {
                continue;
            }
            // Too hungry to carry: drop the errand, keeping whatever is on their
            // back so the food is not conjured away.
            if (person.isTooWeakToWork()) {
                abandon(settlement, person, haul);
                continue;
            }

            SimPos target = haul.target();
            if (!person.isEmbodied()) {
                person.setPosition(stepToward(person.position(), target, ABSTRACT_TRAVEL_BLOCKS));
            }
            if (person.position().horizontalDistance(target) > ARRIVAL_RADIUS) {
                continue;
            }

            if (!haul.isLoaded()) {
                int taken = FoodPlanner.withdraw(settlement, haul.fromStore(), haul.fromPos(), haul.requested());
                if (taken <= 0) {
                    person.setHaul(null);   // somebody beat them to it
                    continue;
                }
                haul.setCarried(taken);
            } else {
                FoodPlanner.deposit(settlement, haul.toStore(), haul.toPos(), haul.carried());
                person.setHaul(null);
            }
        }
    }

    /** Put a carried load back into the nearest sensible store and cancel the errand. */
    private static void abandon(Settlement settlement, Person person, HaulTask haul) {
        if (haul.isLoaded()) {
            FoodPlanner.deposit(settlement, haul.fromStore(), haul.fromPos(), haul.carried());
        }
        person.setHaul(null);
    }

    /** Move at most {@code blocks} from one point toward another, never overshooting. */
    public static SimPos stepToward(SimPos from, SimPos to, int blocks) {
        double distance = from.horizontalDistance(to);
        if (distance <= blocks) {
            return to;
        }
        double fraction = blocks / distance;
        int x = from.x() + (int) Math.round((to.x() - from.x()) * fraction);
        int z = from.z() + (int) Math.round((to.z() - from.z()) * fraction);
        return new SimPos(x, to.y(), z);
    }
}
