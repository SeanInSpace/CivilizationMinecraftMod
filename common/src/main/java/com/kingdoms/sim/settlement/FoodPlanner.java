package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

/**
 * The subsistence loop — what "self-sustaining" literally means.
 *
 * <p>Every step, farmers working the fields put food into the granary and every
 * mouth takes some out. A village whose fields keep up grows; one that outruns
 * its farms stops growing until they catch up. This replaces "farmers are a
 * decorative job" with the oldest economy there is.
 *
 * <ul>
 *   <li>Each farm employs up to {@link #FARMERS_PER_FARM} farmers; farmers
 *       beyond the fields' capacity produce nothing.</li>
 *   <li>Production is {@link #FOOD_PER_FARMER_PER_STEP} per working farmer;
 *       consumption is {@link #FOOD_EATEN_PER_PERSON_PER_STEP} per resident.</li>
 *   <li>The granary holds {@link #BASE_GRANARY}, plus
 *       {@link #GRANARY_PER_STOREHOUSE} per storehouse — the building finally
 *       earns its name. Surplus beyond capacity is wasted.</li>
 *   <li><strong>Births require banked food</strong> — see
 *       {@link #canFeedAnotherMouth} — so the food supply, not just housing,
 *       paces growth.</li>
 *   <li>Nobody starves to death. An empty granary halts growth and is written
 *       into the town's history; a quaint village goes hungry, it does not
 *       collapse. Starvation deaths can arrive with a harsher difficulty later.</li>
 * </ul>
 *
 * <p>New settlements begin with {@link #STARTING_PROVISIONS} — the founding
 * party packs supplies to survive until the first field is tilled.
 */
public final class FoodPlanner {

    public static final int STARTING_PROVISIONS = 100;
    public static final int FOOD_PER_FARMER_PER_STEP = 6;
    public static final int FARMERS_PER_FARM = 2;
    public static final int FOOD_EATEN_PER_PERSON_PER_STEP = 1;
    public static final int BASE_GRANARY = 200;
    public static final int GRANARY_PER_STOREHOUSE = 400;

    /** Births need this many steps of food banked per resident (including the newborn). */
    public static final int BIRTH_FOOD_BUFFER_STEPS = 5;

    private FoodPlanner() {
    }

    /** One step of the subsistence loop: harvest, eat, and note an empty granary. */
    public static void advance(Settlement settlement, SimContext ctx) {
        int before = settlement.foodStock();

        int farmers = JobPlanner.count(settlement, Profession.FARMER);
        int fields = countBuildings(settlement, "farm");
        int working = Math.min(farmers, fields * FARMERS_PER_FARM);
        int produced = working * FOOD_PER_FARMER_PER_STEP;
        int eaten = settlement.population() * FOOD_EATEN_PER_PERSON_PER_STEP;

        int after = before + produced - eaten;
        // The granary clamps what can be BANKED, never confiscates what is held:
        // a stock above capacity (founding provisions, future imports) drains
        // naturally instead of snapping down.
        int ceiling = Math.max(granaryCapacity(settlement), before);
        settlement.setFoodStock(Math.max(0, Math.min(after, ceiling)));

        if (before > 0 && settlement.foodStock() == 0) {
            settlement.logEvent(ctx.step(),
                    "The granary is empty — growth halts until the fields catch up");
        }
    }

    /** Whether the town can commit to feeding one more resident. */
    public static boolean canFeedAnotherMouth(Settlement settlement) {
        return settlement.foodStock()
                >= (settlement.population() + 1) * BIRTH_FOOD_BUFFER_STEPS;
    }

    public static int granaryCapacity(Settlement settlement) {
        return BASE_GRANARY + countBuildings(settlement, "storehouse") * GRANARY_PER_STOREHOUSE;
    }

    /**
     * Matched by blueprint-path suffix so any catalogue's "farm" counts. A
     * placeholder for datapack-declared building roles, like the catalogue itself.
     */
    private static int countBuildings(Settlement settlement, String pathSuffix) {
        return (int) settlement.buildings().stream()
                .filter(b -> b.blueprintId().endsWith(pathSuffix))
                .count();
    }
}
