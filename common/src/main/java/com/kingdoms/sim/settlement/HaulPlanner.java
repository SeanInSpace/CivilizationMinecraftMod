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

    /**
     * Steps an embodied hauler may take over one leg before the clock delivers.
     *
     * <p>Being watched must never starve a town. An embodied hauler has to
     * genuinely walk — that is the show — but mob navigation cannot climb every
     * cliff a town builds on, and an errand that cannot arrive used to simply
     * never complete. With hunger on its own clock, a player who stood and
     * watched a steep town for half an hour watched all twenty-five of its
     * people starve with the fields full. Now the walk gets a fair spell, and
     * then the goods arrive the way they would have if nobody had been looking.
     */
    public static final int EMBODIED_STALL_STEPS = 12;

    private HaulPlanner() {
    }

    public static void advance(Settlement settlement, SimContext ctx) {
        boolean starving = settlement.isStarving();
        for (Person person : settlement.residents()) {
            HaulTask haul = person.haul();
            if (haul == null) {
                continue;
            }
            // Too hungry to carry: drop the errand, keeping whatever is on their
            // back so the food is not conjured away.
            //
            // Not while the town is starving, though. Hunger rises on everybody
            // at once, so every hauler crosses the weakness line within a step or
            // two of every other — and a town whose carriers all put their grain
            // back down at the farm gate on the same afternoon never eats again.
            if (FoodPlanner.heldBackByHunger(person, starving)) {
                abandon(settlement, person, haul);
                continue;
            }

            SimPos target = haul.target();
            boolean overdue = false;
            if (!person.isEmbodied()) {
                person.setPosition(stepToward(person.position(), target, ABSTRACT_TRAVEL_BLOCKS));
            } else {
                overdue = haul.addStalledStep() >= EMBODIED_STALL_STEPS;
            }
            if (person.position().horizontalDistance(target) > ARRIVAL_RADIUS && !overdue) {
                continue;
            }
            haul.resetStalled();

            if (!haul.isLoaded()) {
                int taken = pickUp(settlement, haul);
                if (taken <= 0) {
                    person.setHaul(null);   // somebody beat them to it
                    continue;
                }
                haul.setCarried(taken);
            } else {
                setDown(settlement, haul.resource(), haul.toStore(), haul.toPos(), haul.carried());
                person.setHaul(null);
            }
        }
    }

    /** Put a carried load back where it came from and cancel the errand. */
    private static void abandon(Settlement settlement, Person person, HaulTask haul) {
        if (haul.isLoaded()) {
            setDown(settlement, haul.resource(), haul.fromStore(), haul.fromPos(), haul.carried());
        }
        person.setHaul(null);
    }

    /**
     * Takes the load out of the books it is leaving.
     *
     * <p>Food keeps its own economy — granary, field, stall, pantry — and bulk
     * goods come off the shelves of the building they were fetched from.
     */
    private static int pickUp(Settlement settlement, HaulTask haul) {
        if (TownStores.FOOD.equals(haul.resource())) {
            return FoodPlanner.withdraw(settlement, haul.fromStore(), haul.fromPos(),
                    haul.requested());
        }
        Building from = settlement.buildingAt(haul.fromPos());
        return from == null ? 0 : from.stores().takeUpTo(haul.resource(), haul.requested());
    }

    /**
     * Puts the load into the books it has arrived at.
     *
     * <p>A load whose destination has been pulled down since it set out goes on
     * the ground rather than nowhere: goods only ever exist in one place, and
     * losing them because a building did is the one outcome worse than a wasted
     * walk.
     */
    private static void setDown(Settlement settlement, String resource,
                                HaulTask.Store store, SimPos pos, int amount) {
        if (TownStores.FOOD.equals(resource)) {
            FoodPlanner.deposit(settlement, store, pos, amount);
            return;
        }
        Building to = settlement.buildingAt(pos);
        if (to != null) {
            to.stores().add(resource, amount);
        } else {
            settlement.loosePile().add(resource, amount);
        }
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
