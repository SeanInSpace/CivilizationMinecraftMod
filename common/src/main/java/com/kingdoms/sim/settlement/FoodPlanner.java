package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.HaulTask;
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
    public static final int STARVATION_GRACE_STEPS = 10;

    // the chain's carrying numbers
    public static final int FOOD_PER_FARMER_PER_STEP = 1;

    /**
     * Steps a watched farm may go without a real harvest before the clock
     * resumes crediting it.
     *
     * <p>The floor under real farming, in the same spirit as the haul floor: a
     * watched farm produces through actual hands on actual wheat, but if those
     * hands cannot reach the field — a cliff, a fence, a pathing failure — the
     * town must not starve for being looked at. Generous enough that a working
     * farmer always suppresses it, short enough that a stuck one costs at most
     * a minute of production.
     */
    public static final int WATCHED_HARVEST_GRACE_STEPS = 12;
    public static final int FARMERS_PER_FARM = 2;
    public static final int FARM_STORE_CAP = 40;
    /**
     * What a farmer shoulders in one trip from the fields.
     *
     * <p>Was four, which could not keep up: a playtest ended with a hundred and
     * fifty-six of harvest banked in the fields, the granary at four, and somebody
     * starving in the middle of it. The food was there — four at a time simply
     * could not move it. A builder carries sixteen; a sack of grain is no heavier.
     */
    public static final int FARMER_CARRY = 12;
    /** What a market hand shoulders from the granary. Matched to the farmer's load. */
    public static final int TRADER_CARRY = 12;
    public static final int MARKET_STOCK_CAP = 150;
    public static final int PANTRY_PER_MEMBER = 3;
    public static final int FETCH_MAX = 8;
    /** How many provisions somebody takes from the family larder at a time. */
    public static final int CARRY_WHEN_EATING = 2;

    // granary pool capacity
    public static final int BASE_GRANARY = 200;
    public static final int GRANARY_PER_BUILDING = 400;

    /** Births need this many steps of food banked per resident (including the newborn). */
    public static final int BIRTH_FOOD_BUFFER_STEPS = 5;

    /**
     * Loaves per resident, counted everywhere in town, below which the place is
     * starving.
     *
     * <p>Two is about thirty steps of eating with nothing coming in — late
     * enough that an ordinary lean spell is not a crisis, early enough that the
     * fields can still be got going before anybody is on the death clock. A town
     * under this is not economising, it is running out.
     */
    public static final int CRISIS_FOOD_PER_RESIDENT = 2;

    /** What a starving town raises ahead of everything else, in the order it wants them. */
    public static final List<String> SURVIVAL_ROLES = List.of("farm", "granary");

    private FoodPlanner() {
    }

    // --- the crisis ---

    /**
     * Whether this town is starving: the state in which its ordinary rules stop
     * serving it.
     *
     * <p>Worked out from hunger and the larder each time it is asked, and
     * deliberately not a saved field — a crisis is a fact about the town right
     * now, and one that outlived a reload without still being true would be
     * worse than having none at all.
     *
     * <p>The town that died in a playtest is the specification. It had nobody
     * farming, a build queue frozen on a hall it could not pay for, and
     * thirty-two loaves; every rule that produced that was individually
     * sensible. This is the flag that suspends them.
     */
    public static boolean isStarving(Settlement settlement) {
        int mouths = settlement.population();
        if (mouths == 0) {
            return false;
        }
        for (Person person : settlement.residents()) {
            if (person.starvingSteps() > 0) {
                return true;   // somebody is already on the death clock
            }
        }
        int floor = mouths * CRISIS_FOOD_PER_RESIDENT;
        // The granary settles it on its own for any town with stock in it, which
        // keeps the fields, the stalls and every family's larder out of the
        // answer for every town that is not in trouble.
        return settlement.foodStock() < floor && totalFood(settlement) < floor;
    }

    /** Everything edible the town owns, wherever it happens to be sitting. */
    public static int totalFood(Settlement settlement) {
        return settlement.foodStock() + farmStock(settlement) + marketStock(settlement)
                + pantryTotal(settlement);
    }

    /**
     * Whether hunger keeps this person from working.
     *
     * <p>It normally does, and should — the weak stop farming, hauling and
     * building. That rule is also precisely how a town dies: weak hands bring in
     * no food, so more hands go weak. While the town is starving it is suspended,
     * and the hungry are allowed to go and fetch their own dinner.
     */
    static boolean heldBackByHunger(Person person, boolean starving) {
        return person.isTooWeakToWork() && !starving;
    }

    /**
     * Whether a blueprint id names this role.
     *
     * <p>Compares the last segment rather than the whole tail, so an animal farm
     * is not mistaken for the thing that grows the town's bread.
     */
    public static boolean namesRole(String blueprintId, String role) {
        String base = BuildPlanner.baseIdOf(blueprintId);
        int cut = Math.max(base.lastIndexOf(':'), base.lastIndexOf('/'));
        return base.substring(cut + 1).equals(role);
    }

    /** Whether this is one of the buildings a starving town puts before all others. */
    public static boolean isSurvivalBuilding(String blueprintId) {
        for (String role : SURVIVAL_ROLES) {
            if (namesRole(blueprintId, role)) {
                return true;
            }
        }
        return false;
    }

    /** One step of the chain, source to mouth, then hunger and its consequences. */
    public static void advance(Settlement settlement, SimContext ctx) {
        int poolBefore = settlement.foodStock();
        // Asked once, so the whole step agrees about what sort of day the town is
        // having. A harvest that lifts it back over the floor must not leave the
        // fields reading one answer and the haulers behind them reading another.
        boolean starving = settlement.isStarving();

        growHarvest(settlement, ctx, starving);
        assignHauls(settlement, starving);
        eatAndHunger(settlement, ctx);

        if (poolBefore > 0 && settlement.foodStock() == 0) {
            settlement.logEvent(ctx.step(),
                    "The granary is empty — growth halts until the fields catch up");
        }
    }

    // --- who fetches what ---

    /**
     * Hands errands to anyone free to run them. Nothing moves here — the goods
     * travel on the hauler's back in {@link HaulPlanner}.
     */
    private static void assignHauls(Settlement settlement, boolean starving) {
        int granarySpace = granaryCapacity(settlement) - settlement.foodStock();
        int granaryStock = settlement.foodStock();
        SimPos granary = granaryPos(settlement);

        for (Person person : settlement.residents()) {
            if (person.haul() != null || heldBackByHunger(person, starving)) {
                continue;
            }
            // A pioneer takes the farmer's errands while generalists labour --
            // same arm, same granary budget, no second copy of the rules.
            Profession arm = person.profession() == Profession.PIONEER
                    && settlement.laboursAs(person, Profession.FARMER)
                    ? Profession.FARMER : person.profession();
            switch (arm) {
                case FARMER -> {
                    if (granarySpace < FARMER_CARRY) {
                        continue;
                    }
                    Building field = fullestWithStock(settlement, "farm", 1);
                    if (field != null) {
                        person.setHaul(new HaulTask(HaulTask.Store.FARM, field.origin(),
                                HaulTask.Store.GRANARY, granary, FARMER_CARRY));
                        granarySpace -= FARMER_CARRY;
                    }
                }
                case TRADER -> {
                    if (granaryStock < TRADER_CARRY) {
                        continue;
                    }
                    Building stall = emptiestBelowCap(settlement, "market", MARKET_STOCK_CAP);
                    if (stall != null) {
                        person.setHaul(new HaulTask(HaulTask.Store.GRANARY, granary,
                                HaulTask.Store.MARKET, stall.origin(), TRADER_CARRY));
                        granaryStock -= TRADER_CARRY;
                    }
                }
                default -> {
                }
            }
        }
        assignPantryRuns(settlement, granary, starving);
    }

    /** One member of each hungry household goes shopping — market first, granary otherwise. */
    private static void assignPantryRuns(Settlement settlement, SimPos granary, boolean starving) {
        for (Household household : settlement.households()) {
            if (household.size() == 0 || !household.isHoused()) {
                continue;
            }
            int target = household.size() * PANTRY_PER_MEMBER;
            if (household.pantry() >= target) {
                continue;
            }
            if (anyMemberHauling(settlement, household)) {
                continue;
            }
            Person shopper = freeMember(settlement, household, starving);
            if (shopper == null) {
                continue;
            }
            int want = Math.min(FETCH_MAX, target - household.pantry());

            Building stall = fullestWithStock(settlement, "market", 1);
            if (stall != null) {
                shopper.setHaul(new HaulTask(HaulTask.Store.MARKET, stall.origin(),
                        HaulTask.Store.HOME, household.home(), want));
            } else if (settlement.foodStock() > 0) {
                shopper.setHaul(new HaulTask(HaulTask.Store.GRANARY, granary,
                        HaulTask.Store.HOME, household.home(), want));
            }
        }
    }

    private static boolean anyMemberHauling(Settlement settlement, Household household) {
        for (Person.Id id : household.members()) {
            Person member = settlement.resident(id);
            if (member != null && member.haul() != null) {
                return true;
            }
        }
        return false;
    }

    /** Somebody in the family with free hands — never a builder mid-course, never a guard. */
    private static Person freeMember(Settlement settlement, Household household, boolean starving) {
        Person fallback = null;
        for (Person.Id id : household.members()) {
            Person member = settlement.resident(id);
            if (member == null || member.haul() != null
                    || heldBackByHunger(member, starving)) {
                continue;
            }
            if (member.profession() == Profession.IDLER) {
                return member;   // idle hands first
            }
            if (member.profession() != Profession.GUARD && member.profession() != Profession.BUILDER) {
                fallback = member;
            }
        }
        return fallback;
    }

    // --- stores ---

    /** Takes goods out of a store. Returns how much was actually there. */
    static int withdraw(Settlement settlement, HaulTask.Store store, SimPos pos, int amount) {
        switch (store) {
            case GRANARY -> {
                int take = Math.min(amount, settlement.foodStock());
                settlement.setFoodStock(settlement.foodStock() - take);
                return take;
            }
            case FARM, MARKET -> {
                Building building = buildingAt(settlement, pos);
                if (building == null) {
                    return 0;
                }
                int take = Math.min(amount, building.foodStored());
                building.setFoodStored(building.foodStored() - take);
                return take;
            }
            default -> {
                return 0;   // homes are never a source
            }
        }
    }

    /** Puts goods into a store. Anything that will not fit is spoiled rather than duplicated. */
    static void deposit(Settlement settlement, HaulTask.Store store, SimPos pos, int amount) {
        switch (store) {
            case GRANARY -> settlement.setFoodStock(
                    Math.min(granaryCapacity(settlement), settlement.foodStock() + amount));
            case FARM, MARKET -> {
                Building building = buildingAt(settlement, pos);
                if (building != null) {
                    building.setFoodStored(building.foodStored() + amount);
                }
            }
            case HOME -> {
                for (Household household : settlement.households()) {
                    if (pos.equals(household.home())) {
                        household.setPantry(household.pantry() + amount);
                        return;
                    }
                }
            }
        }
    }

    /** Where haulers meet the town's bulk store: a granary building, else the centre. */
    public static SimPos granaryPos(Settlement settlement) {
        List<Building> granaries = buildingsOf(settlement, "granary");
        if (!granaries.isEmpty()) {
            return granaries.getFirst().origin();
        }
        List<Building> stores = buildingsOf(settlement, "storehouse");
        return stores.isEmpty() ? settlement.centre() : stores.getFirst().origin();
    }

    private static Building buildingAt(Settlement settlement, SimPos pos) {
        for (Building building : settlement.buildings()) {
            if (building.origin().equals(pos)) {
                return building;
            }
        }
        return null;
    }

    private static Building fullestWithStock(Settlement settlement, String suffix, int minimum) {
        Building best = null;
        for (Building building : buildingsOf(settlement, suffix)) {
            if (building.foodStored() >= minimum
                    && (best == null || building.foodStored() > best.foodStored())) {
                best = building;
            }
        }
        return best;
    }

    private static Building emptiestBelowCap(Settlement settlement, String suffix, int cap) {
        Building best = null;
        for (Building building : buildingsOf(settlement, suffix)) {
            if (building.foodStored() < cap
                    && (best == null || building.foodStored() < best.foodStored())) {
                best = building;
            }
        }
        return best;
    }

    /**
     * Fields produce into their own stores, worked by healthy farmers.
     *
     * <p>Two fidelities, one field, following the lumber camp's rule exactly:
     * where somebody is watching, the real hands are the harvest — a farmer
     * cutting actual wheat credits the farm and the clock stands aside. The
     * clock works every unwatched farm, and floors a watched one whose farmers
     * have not managed a real harvest in {@link #WATCHED_HARVEST_GRACE_STEPS},
     * because being watched must never starve a town.
     */
    private static void growHarvest(Settlement settlement, SimContext ctx, boolean starving) {
        List<Building> farms = buildingsOf(settlement, "farm");
        if (farms.isEmpty()) {
            return;
        }
        int working = Math.min(countFarmHands(settlement, starving), farms.size() * FARMERS_PER_FARM);
        int harvest = working * FOOD_PER_FARMER_PER_STEP;
        for (int i = 0; harvest > 0 && i < farms.size() * FARM_STORE_CAP; i++) {
            Building farm = farms.get(i % farms.size());
            if (farm.foodStored() >= FARM_STORE_CAP) {
                continue;
            }
            boolean watched = ctx.bridge().playerWithin(
                    farm.origin(), ctx.settings().observedRadius());
            if (watched && farm.harvestedWithin(ctx.step(), WATCHED_HARVEST_GRACE_STEPS)) {
                harvest--;   // real hands are working this one; their share is theirs
                continue;
            }
            farm.setFoodStored(farm.foodStored() + 1);
            harvest--;
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
                // Out of food? Take a couple of loaves from the family larder.
                if (person.inventory().bestFood() == null) {
                    Household family = families.get(person.id());
                    if (family != null && family.pantry() > 0) {
                        int take = Math.min(CARRY_WHEN_EATING, family.pantry());
                        int taken = person.inventory().add(Foods.PROVISION, take);
                        family.setPantry(family.pantry() - taken);
                    }
                }
                // Last resort: go to the granary yourself.
                //
                // The chain above is how food is *meant* to move, and it is worth
                // keeping — but it is a queue, and a queue can be slower than a
                // stomach. Without this a settler starves to death standing next
                // to a full granary because their family's shopper had not got
                // back yet, which is a logistics bug wearing a famine costume.
                if (person.inventory().bestFood() == null
                        && person.hunger() >= Person.HUNGER_SEVERE) {
                    if (settlement.foodStock() > 0) {
                        int take = Math.min(CARRY_WHEN_EATING, settlement.foodStock());
                        int taken = person.inventory().add(Foods.PROVISION, take);
                        settlement.stores().take(TownStores.FOOD, taken);
                    } else {
                        // The granary is not the only place food sits. A town can be
                        // empty at the granary and have a hundred and fifty loaves on
                        // the market stall it just stocked, and somebody was starving
                        // to death in the square in front of it.
                        Building stall = fullestWithStock(settlement, "market", 1);
                        if (stall == null) {
                            // And past the stalls, the fields themselves. Watched
                            // towns can jam with every store empty and hundreds of
                            // food capped at the farms — hauling is the bottleneck,
                            // not growing — and nobody starves in sight of a full
                            // field. The desperate eat straight from the rows.
                            stall = fullestWithStock(settlement, "farm", 1);
                        }
                        if (stall != null) {
                            int take = Math.min(CARRY_WHEN_EATING, stall.foodStored());
                            int taken = person.inventory().add(Foods.PROVISION, take);
                            stall.setFoodStored(stall.foodStored() - taken);
                        }
                    }
                }
                // Eat the best thing actually carried — including anything a
                // player has handed over.
                String meal = person.inventory().bestFood();
                if (meal != null) {
                    person.inventory().remove(meal, 1);
                    person.setHunger(person.hunger() - Foods.nutrition(meal));
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

    /**
     * Farmers who will actually go out to the rows.
     *
     * <p>Hunger takes people off the fields, which is the rule that turns a bad
     * season into a death spiral: fewer hands, less food, more weak hands. A
     * starving town gets its farmers back, because nobody else is going to feed
     * them.
     */
    private static int countFarmHands(Settlement settlement, boolean starving) {
        // laboursAs, not the raw profession: below VILLAGE the pioneers ARE the
        // farmers, and a camp that counted only crystallized ones would harvest
        // nothing until the staffing table woke up.
        return (int) settlement.residents().stream()
                .filter(p -> settlement.laboursAs(p, Profession.FARMER)
                        && !heldBackByHunger(p, starving))
                .count();
    }

    /**
     * Matched by blueprint-path suffix so any catalogue's "farm" counts. A
     * placeholder for datapack-declared building roles, like the catalogue itself.
     */
    private static List<Building> buildingsOf(Settlement settlement, String pathSuffix) {
        List<Building> result = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            // Strip the level first. An improved farm is still a farm — missing that
            // drops every levelled building out of the food chain, out of the
            // workplace lookup, and off the end of a path.
            if (BuildPlanner.baseIdOf(building.blueprintId()).endsWith(pathSuffix)) {
                result.add(building);
            }
        }
        return result;
    }
}
