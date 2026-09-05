package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
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

    /**
     * Who carries a load that belongs to no trade in particular, or null.
     *
     * <p>A player watched a carpenter shoulder a stack of stone and walk it
     * across the village while the carpentry stood empty behind him, and he was
     * right to complain: the old rule passed over builders and farmers and took
     * the next person it found, which on any village past VILLAGE is a
     * craftsman. The town paid for a load of stone with a workshop.
     *
     * <p>So the errand is offered in tiers.
     *
     * <ul>
     *   <li><strong>Idlers first.</strong> Somebody with nothing to do is the
     *       one person a walk costs the town nothing.</li>
     *   <li><strong>Then slack.</strong> A trade whose work is not there today —
     *       a lumberjack at the timber ceiling, a miner at the stone ceiling, a
     *       smith with no iron, a farmer in a town with no field — is a pair of
     *       hands standing still, and a standing hauler is worth less than a
     *       walking one. See {@link #hasWorkInFront}.</li>
     *   <li><strong>Never a builder and never a guard.</strong> The demand this
     *       answers to <em>is</em> a build, so sending the person raising it to
     *       fetch its own materials stops the work in order to supply it. And
     *       the watch is the watch: nothing takes a load off a guard's back when
     *       the bell rings, so a guard carrying stone is a guard who is not
     *       there when a raid arrives.</li>
     * </ul>
     *
     * <p><strong>And if nobody qualifies, the load waits.</strong> That is the
     * whole rule, and it is worth stating why the null return is the right
     * answer rather than a missing case. A waiting haul costs the town a walk it
     * will make later; a stopped workshop costs it everything that workshop
     * would have made in the meantime, and the workshop is the reason the goods
     * were wanted in the first place. Goods are patient — they sit on a shelf
     * and are still there next step, and the trade that wanted them will fetch
     * its own load a stack at a time in the meantime. Craftsmen are not: an hour
     * a carpenter spends on the road is an hour of pre-cut components the build
     * crew never gets, and the build is what the delivery was for. Deliveries
     * that wait are a slow town. Deliveries that empty the workshops are a town
     * that stops.
     */
    public static Person courierFor(Settlement settlement) {
        Person slack = null;
        for (Person person : settlement.residents()) {
            if (person.haul() != null || person.isTooWeakToWork()) {
                continue;
            }
            if (settlement.laboursAs(person, Profession.BUILDER)
                    || person.profession() == Profession.GUARD) {
                continue;
            }
            if (person.profession() == Profession.IDLER) {
                return person;
            }
            if (slack == null && !hasWorkInFront(settlement, person)) {
                slack = person;
            }
        }
        return slack;
    }

    /**
     * Whether this person's trade has anything queued for them here, today.
     *
     * <p>Asked of the town rather than of the person: none of these trades holds
     * a work list, so the question is always whether the thing they work still
     * stands and still wants working. A lumberjack at the timber ceiling has
     * nothing to fell; one below it has a day's felling ahead whether or not an
     * axe is in his hand this second.
     *
     * <p>The carpenter is the case the player found, and it is the strictest of
     * them. {@link SupplyPlanner} only ever moves goods toward a build, so it
     * only ever asks for a courier while something is being built — and a
     * carpenter's whole contribution is the pre-cut components that make that
     * build go faster (see {@code Settlement.advanceBuildQueue}). Which means a
     * carpenter in a town with a carpentry is never free at the exact moment
     * this question gets asked. That is not a coincidence to be worked around;
     * it is the answer.
     *
     * <p>Worth being honest about what that costs today, because the arithmetic
     * does not yet charge for it: the carpentry bonus is a headcount — a
     * carpentry standing and one carpenter alive — and it never looks at whether
     * that carpenter is holding anything, so a hauling carpenter presently
     * yields the same components as one at his bench. The exemption is therefore
     * not buying throughput back this month. It is buying the promise that a
     * craftsman with a job stays at it, which is what the player was actually
     * complaining about — {@code workplaceFor} ranks an outstanding haul above
     * every day job, so a conscripted carpenter visibly walks out of his
     * workshop — and it is buying the freedom to make that headcount into real
     * work later without the courier rule having to be re-argued. A rule that
     * only holds while a placeholder stays a placeholder is the wrong way round.
     *
     * <p>The trades that are <em>not</em> exempt for the same reason are the
     * ones with nothing to walk away from. A miller's whole effect is likewise a
     * headcount ({@link FoodPlanner#millRuns}) and there is no watched miller
     * either — but a miller has no bench: unwatched he is a term in a
     * multiplier, and watched he stands beside the mill doing nothing anybody
     * can see. Nothing is lost when he carries, so he is never claimed here.
     */
    static boolean hasWorkInFront(Settlement settlement, Person person) {
        return switch (person.profession()) {
            // A pioneer past VILLAGE is a generalist the staffing table has not
            // caught up with; while generalists still labour they are builders
            // and farmers, and courierFor has already passed them over.
            case IDLER, PIONEER -> false;
            case BUILDER, GUARD -> true;
            // Deliberately coarse: any field, every farmer, even past the
            // FARMERS_PER_FARM the clock will actually pay for. Watched, the
            // roster deals every farmer to a field (place % fields.size()) and
            // all of them cut real wheat, so the "surplus" hands the clock caps
            // are the same hands a player watches working -- conscripting them
            // is the reported bug, not a saving. Erring the other way has cost
            // this project a town already.
            case FARMER -> settlement.buildingWithRole(BuildingRole.CROP_FARM) != null;
            case LUMBERJACK -> settlement.buildingWithRole(BuildingRole.LUMBER_CAMP) != null
                    && LumberPlanner.wantsMoreTimber(settlement);
            case MINER -> settlement.buildingWithRole(BuildingRole.MINE) != null
                    && MinePlanner.wantsMoreStone(settlement);
            case SMITH -> SmithPlanner.hasWorkInFront(settlement);
            // Never busy. The mill's whole effect is millRuns, a headcount that
            // does not care what the miller is holding, and there is no watched
            // miller to pull off anything -- so a mill town's miller is the one
            // pair of hands a walk costs literally nothing, and refusing to ask
            // him would leave a load waiting for no gain at all.
            case MILLER -> false;
            case CARPENTER -> settlement.buildingWithRole(BuildingRole.CARPENTRY) != null
                    && !settlement.buildQueue().isEmpty();
            case SHEPHERD -> settlement.buildingWithRole(BuildingRole.ANIMAL_FARM) != null;
            // The trader's own errand is granary to stall, and FoodPlanner hands
            // it out before this is ever asked -- so a trader still free here is
            // one with an empty granary behind them or no stall to fill. Asked
            // of FoodPlanner rather than restated, because "a market stands and
            // the granary has twelve" is not the same question: a lone stall at
            // MARKET_STOCK_CAP passes it and still has nothing to be carried.
            case TRADER -> FoodPlanner.hasStallToStock(settlement);
        };
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
