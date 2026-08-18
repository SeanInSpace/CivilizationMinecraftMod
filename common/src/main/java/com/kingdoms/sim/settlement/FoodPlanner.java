package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The food chain, from field to mouth:
 *
 * <pre>
 *   fields grow → FARMERS haul → granary pool → TRADERS stock → market
 *                                                                  ↓
 *      mouths ← personal inventory ← family pantry ← a family member fetches
 * </pre>
 *
 * <p>Every link is real, held state: harvest waits at the farm until a farmer
 * carries it, the granary holds the town's bulk, market hands move retail stock,
 * one member per family keeps the pantry filled, and each person carries and
 * eats their own food. Break any link — no farmers, no granary space, no market
 * hands, a housebound family — and hunger arrives downstream.
 *
 * <p><strong>Hunger</strong> rises every step and is scored 0–99:
 * <ul>
 *   <li><b>0–29</b> — fed;</li>
 *   <li><b>30–59</b> — hungry (eats at 30 when there is anything to eat);</li>
 *   <li><b>60–89</b> — weak: stops farming, hauling and building; visibly
 *       debuffed in the world;</li>
 *   <li><b>90–99</b> — severe: heavier debuffs, and after
 *       {@link #STARVATION_GRACE_STEPS} steps held at the cap, death. Starvation
 *       deaths are permanent and enter the town's history.</li>
 * </ul>
 *
 * <p>Young settlements without a market fetch straight from the granary pool;
 * once a market stands, families shop there — the chain grows with the town.
 */
public final class FoodPlanner {

    public static final int STARTING_PROVISIONS = 100;

    // hunger pacing
    public static final int HUNGER_PER_STEP = 2;
    public static final int NUTRITION_PER_FOOD = 30;
    public static final int STARVATION_GRACE_STEPS = 10;

    // the chain's carrying numbers
    public static final int FOOD_PER_FARMER_PER_STEP = 1;
    public static final int FARMERS_PER_FARM = 2;
    public static final int FARM_STORE_CAP = 40;
    public static final int FARMER_CARRY = 4;
    public static final int TRADER_CARRY = 6;
    public static final int MARKET_STOCK_CAP = 150;
    public static final int PANTRY_PER_MEMBER = 3;
    public static final int FETCH_MAX = 8;
    public static final int CARRY_WHEN_EATING = 2;

    // granary pool capacity
    public static final int BASE_GRANARY = 200;
    public static final int GRANARY_PER_BUILDING = 400;

    /** Births need this many steps of food banked per resident (including the newborn). */
    public static final int BIRTH_FOOD_BUFFER_STEPS = 5;

    private FoodPlanner() {
    }

    /** One step of the chain, source to mouth, then hunger and its consequences. */
    public static void advance(Settlement settlement, SimContext ctx) {
        int poolBefore = settlement.foodStock();

        growHarvest(settlement);
        haulHarvestToGranary(settlement);
        stockMarkets(settlement);
        fetchForFamilies(settlement);
        eatAndHunger(settlement, ctx);

        if (poolBefore > 0 && settlement.foodStock() == 0) {
            settlement.logEvent(ctx.step(),
                    "The granary is empty — growth halts until the fields catch up");
        }
    }

    /** Fields produce into their own stores, worked by healthy farmers. */
    private static void growHarvest(Settlement settlement) {
        List<Building> farms = buildingsOf(settlement, "farm");
        if (farms.isEmpty()) {
            return;
        }
        int healthyFarmers = countHealthy(settlement, Profession.FARMER);
        int working = Math.min(healthyFarmers, farms.size() * FARMERS_PER_FARM);
        int harvest = working * FOOD_PER_FARMER_PER_STEP;
        for (int i = 0; harvest > 0 && i < farms.size() * FARM_STORE_CAP; i++) {
            Building farm = farms.get(i % farms.size());
            if (farm.foodStored() < FARM_STORE_CAP) {
                farm.setFoodStored(farm.foodStored() + 1);
                harvest--;
            }
        }
    }

    /** Each healthy farmer carries a load from the fullest field to the granary. */
    private static void haulHarvestToGranary(Settlement settlement) {
        List<Building> farms = buildingsOf(settlement, "farm");
        if (farms.isEmpty()) {
            return;
        }
        int haulers = countHealthy(settlement, Profession.FARMER);
        int space = granaryCapacity(settlement) - settlement.foodStock();
        for (int i = 0; i < haulers && space > 0; i++) {
            Building fullest = null;
            for (Building farm : farms) {
                if (fullest == null || farm.foodStored() > fullest.foodStored()) {
                    fullest = farm;
                }
            }
            if (fullest == null || fullest.foodStored() == 0) {
                break;
            }
            int load = Math.min(FARMER_CARRY, Math.min(fullest.foodStored(), space));
            fullest.setFoodStored(fullest.foodStored() - load);
            settlement.setFoodStock(settlement.foodStock() + load);
            space -= load;
        }
    }

    /** Market hands (traders) carry granary stock to the emptiest market. */
    private static void stockMarkets(Settlement settlement) {
        List<Building> markets = buildingsOf(settlement, "market");
        if (markets.isEmpty()) {
            return;
        }
        int hands = countHealthy(settlement, Profession.TRADER);
        for (int i = 0; i < hands && settlement.foodStock() > 0; i++) {
            Building emptiest = null;
            for (Building market : markets) {
                if (market.foodStored() >= MARKET_STOCK_CAP) {
                    continue;
                }
                if (emptiest == null || market.foodStored() < emptiest.foodStored()) {
                    emptiest = market;
                }
            }
            if (emptiest == null) {
                break;
            }
            int load = Math.min(TRADER_CARRY,
                    Math.min(settlement.foodStock(), MARKET_STOCK_CAP - emptiest.foodStored()));
            settlement.setFoodStock(settlement.foodStock() - load);
            emptiest.setFoodStored(emptiest.foodStored() + load);
        }
    }

    /**
     * One member per family keeps the pantry stocked — from the best-stocked
     * market once one stands, straight from the granary before that.
     */
    private static void fetchForFamilies(Settlement settlement) {
        List<Building> markets = buildingsOf(settlement, "market");
        for (Household household : settlement.households()) {
            int target = household.size() * PANTRY_PER_MEMBER;
            if (household.size() == 0 || household.pantry() >= target) {
                continue;
            }
            int want = Math.min(FETCH_MAX, target - household.pantry());

            if (markets.isEmpty()) {
                int take = Math.min(want, settlement.foodStock());
                settlement.setFoodStock(settlement.foodStock() - take);
                household.setPantry(household.pantry() + take);
                continue;
            }
            Building fullest = null;
            for (Building market : markets) {
                if (fullest == null || market.foodStored() > fullest.foodStored()) {
                    fullest = market;
                }
            }
            int take = Math.min(want, fullest.foodStored());
            fullest.setFoodStored(fullest.foodStored() - take);
            household.setPantry(household.pantry() + take);
        }
    }

    /** Hunger rises; the hungry eat what they carry; the starving die. */
    private static void eatAndHunger(Settlement settlement, SimContext ctx) {
        Map<Person.Id, Household> families = new HashMap<>();
        for (Household household : settlement.households()) {
            for (Person.Id member : household.members()) {
                families.put(member, household);
            }
        }

        List<Person> starved = new ArrayList<>();
        for (Person person : settlement.residents()) {
            person.addHunger(HUNGER_PER_STEP);

            if (person.hunger() >= Person.HUNGER_HUNGRY) {
                if (person.foodCarried() == 0) {
                    Household family = families.get(person.id());
                    if (family != null && family.pantry() > 0) {
                        int take = Math.min(CARRY_WHEN_EATING, family.pantry());
                        family.setPantry(family.pantry() - take);
                        person.setFoodCarried(take);
                    }
                }
                if (person.foodCarried() > 0) {
                    person.setFoodCarried(person.foodCarried() - 1);
                    person.setHunger(person.hunger() - NUTRITION_PER_FOOD);
                }
            }

            if (person.hunger() >= Person.HUNGER_MAX) {
                person.setStarvingSteps(person.starvingSteps() + 1);
                if (person.starvingSteps() >= STARVATION_GRACE_STEPS) {
                    starved.add(person);
                }
            } else {
                person.setStarvingSteps(0);
            }
        }

        for (Person person : starved) {
            settlement.removePerson(person.id());
            settlement.logEvent(ctx.step(), person.name() + " starved");
        }
    }

    /** Whether the town can commit to feeding one more resident. */
    public static boolean canFeedAnotherMouth(Settlement settlement) {
        return settlement.foodStock()
                >= (settlement.population() + 1) * BIRTH_FOOD_BUFFER_STEPS;
    }

    /** Granary buildings and storehouses both extend the town's bulk storage. */
    public static int granaryCapacity(Settlement settlement) {
        int extensions = buildingsOf(settlement, "granary").size()
                + buildingsOf(settlement, "storehouse").size();
        return BASE_GRANARY + extensions * GRANARY_PER_BUILDING;
    }

    public static int marketStock(Settlement settlement) {
        return buildingsOf(settlement, "market").stream().mapToInt(Building::foodStored).sum();
    }

    public static int farmStock(Settlement settlement) {
        return buildingsOf(settlement, "farm").stream().mapToInt(Building::foodStored).sum();
    }

    public static int pantryTotal(Settlement settlement) {
        return settlement.households().stream().mapToInt(Household::pantry).sum();
    }

    private static int countHealthy(Settlement settlement, Profession profession) {
        return (int) settlement.residents().stream()
                .filter(p -> p.profession() == profession && !p.isTooWeakToWork())
                .count();
    }

    /**
     * Matched by blueprint-path suffix so any catalogue's "farm" counts. A
     * placeholder for datapack-declared building roles, like the catalogue itself.
     */
    private static List<Building> buildingsOf(Settlement settlement, String pathSuffix) {
        List<Building> result = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            if (building.blueprintId().endsWith(pathSuffix)) {
                result.add(building);
            }
        }
        return result;
    }
}
