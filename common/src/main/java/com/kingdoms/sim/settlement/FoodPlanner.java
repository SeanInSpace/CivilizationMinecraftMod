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
 * The food chain, from field to mouth. Hunger is the one thing that outranks a
 * job: past {@link Person#HUNGER_WEAK} a settler puts the work down and walks to
 * the nearest food themselves — see {@link #assignMealErrands} — and picks the
 * work back up when they have eaten.
 *
 * <p>The chain that is supposed to make that unnecessary:
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
 *   <li><b>60–89</b> — weak: stops farming, hauling and building and goes to
 *       eat instead; visibly debuffed in the world. Nowhere to go and they keep
 *       working, because a starving idler is worse off than a starving
 *       worker;</li>
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

    /**
     * What a field must hold before a farmer downs tools to carry it in.
     *
     * <p>A full load, which is the whole of the reasoning. This used to be one:
     * any field with a single loaf in it sent every farmer in the town off to
     * collect it, and where anybody is watching, a farmer with an errand is a
     * farmer out of the rows — {@code PersonEntityManager.workFarmers} skips
     * them, correctly, because they are on the road. So a watched field grew one
     * loaf, emptied, grew one loaf, emptied, and the player standing in it saw
     * three farmers walking laps and nobody farming. Worse, two of the three
     * arrived at a field somebody had already cleared and walked home with
     * nothing.
     *
     * <p>Hauling is the interruption and growing is the job, so the interruption
     * has to be worth having: one trip, one full load. The town's throughput is
     * unchanged — the same grain reaches the granary in fewer, fuller journeys —
     * and in between them the farmers are in the field, which is what a farm is
     * supposed to look like.
     *
     * <p>Suspended while the town is starving. A loaf is a life then, and the
     * walk is worth making for any of it.
     */
    public static final int WORTH_LEAVING_THE_ROWS = FARMER_CARRY;
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
     * under this is not economizing, it is running out.
     */
    public static final int CRISIS_FOOD_PER_RESIDENT = 2;

    /** What a starving town raises ahead of everything else, in the order it wants them. */
    public static final List<String> SURVIVAL_ROLES = List.of("farm", "granary");

    /**
     * Pioneers per meal turned up by foraging each step, rounded up — a
     * decimated party of one still eats, which is exactly when it matters.
     */
    public static final int FORAGERS_PER_MEAL = 3;

    /**
     * Foraging downs tools once the larder holds this much per mouth. It is
     * deliberately half of what {@link StagePlanner#FED_WINDOW_STEPS} demands:
     * berries keep a camp alive, and only a farm ever graduates it.
     */
    public static final int FORAGE_CEILING_PER_MOUTH = 5;

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
        // Carried food counts. Without it, everything on a hauler's back is
        // invisible between pickup and delivery, and every planner keyed to
        // this figure -- foraging's ceiling first among them -- overshoots by
        // exactly one basket every trip.
        int carried = settlement.residents().stream()
                .mapToInt(p -> p.inventory().foodCount())
                .sum();
        return settlement.foodStock() + farmStock(settlement) + marketStock(settlement)
                + pantryTotal(settlement) + carried;
    }

    /**
     * Whether hunger keeps this person from working.
     *
     * <p>It normally does, and should — the weak stop farming, hauling and
     * building. That rule is also precisely how a town dies: weak hands bring in
     * no food, so more hands go weak. While the town is starving it is suspended,
     * and the hungry are allowed to go and fetch their own dinner.
     *
     * <p>The second escape is the one a player reported: a settler at hunger 88,
     * reading "weak", standing on a roof doing nothing whatever. Downing tools is
     * only worth anything if there is somewhere to go — so somebody with no meal
     * within reach keeps working. A starving idler is worse off than a starving
     * worker, and the work in question is often the field that would end it.
     *
     * @param starving the town's answer for this whole step, asked once in
     *                 {@link #advance} so a harvest that lifts it back over the
     *                 floor cannot leave the fields and the haulers disagreeing
     */
    public static boolean heldBackByHunger(Settlement settlement, Person person,
                                           boolean starving) {
        if (!person.isTooWeakToWork() || starving) {
            return false;
        }
        return isGoingToEat(person) || nearestMeal(settlement, person) != null;
    }

    /** Whether this person is already on their way to something to eat. */
    public static boolean isGoingToEat(Person person) {
        return person.haul() != null && person.haul().isMeal();
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

        forage(settlement);
        growHarvest(settlement, ctx, starving);
        // Dinner before the day's errands, because that is the whole rule: a
        // person past the weak line is going to eat, and assignHauls skips
        // anybody who already has somewhere to be.
        assignMealErrands(settlement);
        assignHauls(settlement, starving);
        carryItHomeUnwatched(settlement);
        eatAndHunger(settlement, ctx);

        if (poolBefore > 0 && settlement.foodStock() == 0) {
            settlement.logEvent(ctx.step(),
                    "The granary is empty — growth halts until the fields catch up");
        }
    }

    /**
     * The food that moves itself where nobody is watching it move.
     *
     * <p>Out of sight a town should end up in the state hands would have put it
     * in, and the chain that feeds it is long: fields to granary, granary to
     * stall, stall to family larder, larder to mouth. Every link is an errand
     * somebody has to be free, fed and unoccupied to run, and every link can
     * fail on its own. Under {@code /civ step} they fail together — nothing
     * walks anywhere, hunger climbs on its own clock, and a town sits on
     * thousands of loaves with its people starving in front of them.
     *
     * <p>So for a household with nobody embodied, the last two links are done as
     * arithmetic. The stall is stocked from the granary and the larder from the
     * stall, exactly as far as a shopper would have carried, and no further —
     * this fills pantries, it does not conjure food. An empty granary still
     * feeds nobody.
     *
     * <p>Only the unwatched. Where somebody IS there to walk it, they walk it:
     * where there is a hand there is no clock, the same rule construction, the
     * wall and the roads all follow.
     */
    private static void carryItHomeUnwatched(Settlement settlement) {
        if (settlement.foodStock() <= 0) {
            return;   // nothing to distribute; this moves food, it does not make it
        }
        // Only the last link. The granary-to-stall leg is a trader's haul and
        // that already completes out of sight — HaulPlanner walks an unembodied
        // carrier abstractly and sets the load down at the far end. Stocking
        // stalls here as well delivered the same load twice: a test that asserts
        // one trader carries one load to the market found fifty in it.
        //
        // What genuinely fails is the leg after that, and only that one is done
        // here.
        for (Household household : settlement.households()) {
            if (household.size() == 0 || !household.isHoused()) {
                continue;
            }
            if (anyMemberEmbodied(settlement, household)) {
                continue;   // somebody is there to fetch it themselves
            }
            int target = household.size() * PANTRY_PER_MEMBER;
            int want = Math.min(FETCH_MAX, target - household.pantry());
            if (want <= 0) {
                continue;
            }
            Building stall = fullestWithStock(settlement, "market", 1);
            int got = 0;
            if (stall != null) {
                got = Math.min(want, stall.foodStored());
                stall.setFoodStored(stall.foodStored() - got);
            }
            if (got < want) {
                got += settlement.stores().takeUpTo(TownStores.FOOD, want - got);
            }
            if (got > 0) {
                household.setPantry(household.pantry() + got);
            }
        }
    }

    /** Whether anybody in this family is standing in the world right now. */
    private static boolean anyMemberEmbodied(Settlement settlement, Household household) {
        for (Person.Id id : household.members()) {
            Person member = settlement.resident(id);
            if (member != null && member.isEmbodied()) {
                return true;
            }
        }
        return false;
    }

    // --- dinner first ---

    /** A place somebody could walk to and eat at, and which books it comes out of. */
    private record Meal(HaulTask.Store store, SimPos where) {
    }

    /**
     * Sends anybody past the weak line to the nearest thing they can eat.
     *
     * <p>The rule the player asked for, in one sentence: <strong>a person too
     * weak to work stops working and goes to eat, and picks the job back up
     * afterwards.</strong> There is nothing to save and restore — a profession is
     * a standing fact and the build queue belongs to the town — so suspending is
     * an errand and resuming is that errand ending. Every worker loop on both
     * fidelities already stands aside for somebody with a haul, so this reaches
     * builders, haulers, farmers and off-alarm guards through machinery that was
     * already there.
     *
     * <p>Four refusals. Somebody already out on an errand keeps it for the one
     * step it takes to put the load down. Somebody carrying food eats where
     * they stand and needs no walk. A guard leaves the wall for dinner only
     * while the town is calm, because a hungry watch beats no watch. And a
     * settler with nothing to walk to is left on the job: see
     * {@link #heldBackByHunger}.
     */
    private static void assignMealErrands(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (person.hunger() < Person.HUNGER_WEAK) {
                continue;
            }
            if (person.haul() != null) {
                // Somebody already out on an errand is left alone for one more
                // step. A load in transit lives on its carrier's back and
                // nowhere else, so handing them a meal over the top of it would
                // delete the goods -- HaulPlanner runs later in the same step,
                // sets the load down where it came from because they are too
                // weak to carry it, and the meal is given out the step after.
                continue;
            }
            if (person.inventory().bestFood() != null) {
                continue;   // already holding a meal; eatAndHunger serves it
            }
            if (person.profession() == Profession.GUARD
                    && settlement.alarm() != Alarm.CALM) {
                continue;
            }
            Meal meal = nearestMeal(settlement, person);
            if (meal == null) {
                continue;   // nowhere to go, so nothing is gained by stopping
            }
            // Both ends are the food itself: there is no delivery leg, and
            // target() therefore points at the meal for the whole walk.
            person.setHaul(new HaulTask(TownStores.FOOD, meal.store(), meal.where(),
                    HaulTask.Store.SELF, meal.where(), CARRY_WHEN_EATING));
        }
    }

    /**
     * The closest place this person could actually get a mouthful, or null.
     *
     * <p>Their own family larder counts, and is usually the nearest thing there
     * is — but a larder is reached by arithmetic every step in
     * {@link #eatAndHunger}, so somebody weak with a stocked pantry has already
     * eaten and never gets here. It is listed all the same for the case that put
     * this on the report: pockets so full of picked-up weeds that the larder
     * could not hand them anything. Walking to it lets the food be eaten on the
     * spot instead of needing a free slot first.
     */
    private static Meal nearestMeal(Settlement settlement, Person person) {
        Meal best = null;
        long nearest = Long.MAX_VALUE;
        for (Household household : settlement.households()) {
            if (household.isHoused() && household.pantry() > 0
                    && household.contains(person.id())) {
                nearest = person.position().horizontalDistanceSq(household.home());
                best = new Meal(HaulTask.Store.HOME, household.home());
                break;   // nobody belongs to two families
            }
        }
        // Stalls and fields keep their own stock; the granary is the town's pool
        // and is reached at granaryPos, which falls back to the square when
        // nothing has been raised to hold it. Two kinds of book, so two lookups.
        for (HaulTask.Store store : new HaulTask.Store[] {
                HaulTask.Store.MARKET, HaulTask.Store.FARM}) {
            for (Building holder : buildingsOf(settlement,
                    store == HaulTask.Store.MARKET ? "market" : "farm")) {
                long d = person.position().horizontalDistanceSq(holder.origin());
                if (holder.foodStored() > 0 && d < nearest) {
                    nearest = d;
                    best = new Meal(store, holder.origin());
                }
            }
        }
        if (settlement.foodStock() > 0) {
            SimPos granary = granaryPos(settlement);
            if (person.position().horizontalDistanceSq(granary) < nearest) {
                best = new Meal(HaulTask.Store.GRANARY, granary);
            }
        }
        return best;
    }

    /**
     * Somebody has walked to the food their errand was for. Feed them.
     *
     * <p>Into their pockets, so the next few steps are covered by what they
     * carry and the walk was worth making. If their pockets will not take it,
     * one goes down on the spot and the rest goes back on the shelf it came off
     * — for the same reason {@link #eatWhereItStands} exists, and because goods
     * only ever sit in one place.
     */
    static void serveMeal(Settlement settlement, Person person, HaulTask errand) {
        int got = takeMeal(settlement, errand.fromStore(), errand.fromPos(), errand.requested());
        if (got <= 0) {
            return;   // somebody got there first; hunger will send them out again
        }
        int stowed = person.inventory().add(Foods.PROVISION, got);
        int spare = got - stowed;
        if (spare > 0) {
            person.setHunger(person.hunger() - Foods.nutrition(Foods.PROVISION));
            if (--spare > 0) {
                deposit(settlement, errand.fromStore(), errand.fromPos(), spare);
            }
        }
    }

    /**
     * Takes a meal out of whatever holds it, the family larder included.
     *
     * <p>{@link #withdraw} deliberately refuses a home, because a pantry is
     * where a haul ends rather than a place goods are fetched from. Eating is
     * the one exception, and it is kept here rather than loosening that rule.
     */
    private static int takeMeal(Settlement settlement, HaulTask.Store store, SimPos pos,
                                int amount) {
        if (store != HaulTask.Store.HOME) {
            return withdraw(settlement, store, pos, amount);
        }
        for (Household household : settlement.households()) {
            if (pos.equals(household.home())) {
                int take = Math.min(amount, household.pantry());
                household.setPantry(household.pantry() - take);
                return take;
            }
        }
        return 0;
    }

    // --- who fetches what ---

    /**
     * Hands errands to anyone free to run them. Nothing moves here — the goods
     * travel on the hauler's back in {@link HaulPlanner}.
     */
    private static void assignHauls(Settlement settlement, boolean starving) {
        // Minus what is already walking toward it. The granary's stock does not
        // move until a carrier arrives, so without this the same headroom is
        // offered again on every step of a walk that takes several -- and
        // deposit spoils whatever will not fit rather than duplicating it, so
        // the overshoot is destroyed food. It was affordable when a farmer
        // arrived with the one or two loaves a step of growth had put in the
        // field; at a full twelve it is not.
        int granarySpace = granaryCapacity(settlement) - settlement.foodStock()
                - onTheRoadTo(settlement, HaulTask.Store.GRANARY);
        int granaryStock = settlement.foodStock();
        SimPos granary = granaryPos(settlement);
        // What is already promised to somebody, field by field. Without it three
        // farmers are all sent to the fullest field and two of them arrive to
        // find it bare -- a wasted round trip each, and on a watched farm a
        // wasted round trip is a farmer who was not farming. Counted from the
        // errands still outstanding rather than only this step's, because a
        // load is not withdrawn until its carrier reaches the field: for the
        // several steps of that walk the grain is still on the books.
        Map<SimPos, Integer> spokenFor = alreadyPromised(settlement);
        // A loaf is worth the walk while the town is starving; below that, a
        // farmer stays in the rows until there is a load worth carrying.
        int worthTheWalk = starving ? 1 : WORTH_LEAVING_THE_ROWS;

        for (Person person : settlement.residents()) {
            if (person.haul() != null || heldBackByHunger(settlement, person, starving)) {
                continue;
            }
            // A pioneer takes the farmer's errands while generalists labor --
            // same arm, same granary budget, no second copy of the rules.
            Profession arm = person.profession() == Profession.PIONEER
                    && settlement.laboursAs(person, Profession.FARMER)
                    ? Profession.FARMER : person.profession();
            switch (arm) {
                case FARMER -> {
                    if (granarySpace < FARMER_CARRY) {
                        continue;
                    }
                    Building field = fullestUnspoken(settlement, spokenFor, worthTheWalk);
                    if (field != null) {
                        person.setHaul(new HaulTask(HaulTask.Store.FARM, field.origin(),
                                HaulTask.Store.GRANARY, granary, FARMER_CARRY));
                        spokenFor.merge(field.origin(), FARMER_CARRY, Integer::sum);
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
                    || heldBackByHunger(settlement, member, starving)) {
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
                // takeUpTo, not setFoodStock. The setter means "make the town
                // hold exactly this", which empties every building and puts the
                // remainder in one — so spending a loaf used to sweep the whole
                // town's larder into a single store on the way past.
                return settlement.stores().takeUpTo(TownStores.FOOD, amount);
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
            case GRANARY -> settlement.stores()
                    .addCapped(TownStores.FOOD, amount, granaryCapacity(settlement));
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

    /** Where haulers meet the town's bulk store: a granary building, else the center. */
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

    /**
     * Grain that somebody is already walking out to collect, field by field.
     *
     * <p>Only errands that have not been picked up yet: once a load is on a
     * back it has genuinely left the field's books, and counting it twice would
     * make every field look emptier than it is.
     */
    private static Map<SimPos, Integer> alreadyPromised(Settlement settlement) {
        Map<SimPos, Integer> promised = new HashMap<>();
        for (Person person : settlement.residents()) {
            HaulTask errand = person.haul();
            if (errand == null || errand.isLoaded()
                    || errand.fromStore() != HaulTask.Store.FARM) {
                continue;
            }
            promised.merge(errand.fromPos(), errand.requested(), Integer::sum);
        }
        return promised;
    }

    /**
     * Food that is on its way into this store and has not landed yet.
     *
     * <p>Both halves of a walk count, unlike {@link #alreadyPromised}, and the
     * difference is which end of the errand is being asked about. A field's
     * books are settled at pickup — once the grain is on a back it has genuinely
     * left the field. The destination's are not settled until delivery, so a
     * load that has been collected is exactly the one most certain to arrive.
     * Hence {@code carried()} for a loaded errand, which is what will actually
     * be set down, and {@code requested()} for one still walking out, which is
     * what it intends to bring.
     */
    private static int onTheRoadTo(Settlement settlement, HaulTask.Store store) {
        int coming = 0;
        for (Person person : settlement.residents()) {
            HaulTask errand = person.haul();
            if (errand == null || errand.toStore() != store) {
                continue;
            }
            coming += errand.isLoaded() ? errand.carried() : errand.requested();
        }
        return coming;
    }

    /**
     * Whether a market hand has a stall worth walking to.
     *
     * <p>The same two questions {@link #assignHauls} asks before it hands a
     * trader an errand, so {@link HaulPlanner#hasWorkInFront} cannot believe a
     * trader is busy on a step the granary would not have sent them anywhere.
     * A single stall sitting at its cap is the case: the town has food and a
     * market and still nothing for this person to carry.
     */
    static boolean hasStallToStock(Settlement settlement) {
        return settlement.foodStock() >= TRADER_CARRY
                && emptiestBelowCap(settlement, "market", MARKET_STOCK_CAP) != null;
    }

    /**
     * The field with the most grain nobody has been sent for yet, or null.
     *
     * <p>{@code fullestWithStock} asks what a field holds; this asks what is
     * still there to fetch once the errands already out have taken their share.
     * Two farmers dispatched to the same twelve loaves is one delivery and one
     * wasted walk.
     */
    private static Building fullestUnspoken(Settlement settlement,
                                            Map<SimPos, Integer> spokenFor, int minimum) {
        Building best = null;
        int most = 0;
        for (Building field : buildingsOf(settlement, "farm")) {
            int left = field.foodStored() - spokenFor.getOrDefault(field.origin(), 0);
            if (left >= minimum && left > most) {
                most = left;
                best = field;
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
    /**
     * Wild food, gathered by hand — the camp's food source before the fields.
     *
     * <p>Two deliberate limits. A handful of foragers turn up one meal a step,
     * so a party survives on it without prospering; and foraging stops at a
     * hand-to-mouth ceiling well below what the fed streak asks for, so no
     * settlement graduates HOMESTEAD on berries. There is no weakness gate:
     * the weak foraging anyway is precisely what stops the starvation spiral
     * the founding rework exists to prevent.
     */
    private static void forage(Settlement settlement) {
        if (!StagePlanner.pioneersLabour(settlement.stage())) {
            return;
        }
        if (totalFood(settlement)
                >= settlement.population() * FORAGE_CEILING_PER_MOUTH) {
            return;
        }
        int hands = (int) settlement.residents().stream()
                .filter(p -> settlement.laboursAs(p, Profession.FARMER))
                .count();
        int gathered = (hands + FORAGERS_PER_MEAL - 1) / FORAGERS_PER_MEAL;
        if (gathered > 0) {
            settlement.stores().add(TownStores.FOOD, gathered);
        }
    }

    private static void growHarvest(Settlement settlement, SimContext ctx, boolean starving) {
        List<Building> farms = buildingsOf(settlement, "farm");
        if (farms.isEmpty()) {
            return;
        }
        int working = Math.min(countFarmHands(settlement, starving), farms.size() * FARMERS_PER_FARM);
        int harvest = working * FOOD_PER_FARMER_PER_STEP;
        // A working mill grinds the same harvest into half again as much bread.
        // One number, deliberately: the full grain-and-bread economy stays a
        // GOALS entry, but the mill has to be worth building the day it stands.
        if (millRuns(settlement)) {
            harvest += working / 2;
        }
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




    /**
     * A meal taken straight from wherever the town keeps it, carrying nothing.
     *
     * <p>The same places the fetching above reaches for, in the same order, but
     * without the step that can fail: no inventory slot is needed because
     * nothing is picked up. One loaf, eaten on the spot.
     *
     * @return true if they found something to eat
     */
    private static boolean eatWhereItStands(Settlement settlement, Person person) {
        int nutrition = Foods.nutrition(Foods.PROVISION);
        if (settlement.foodStock() > 0 && settlement.stores().take(TownStores.FOOD, 1)) {
            person.setHunger(person.hunger() - nutrition);
            return true;
        }
        for (String role : new String[] {"market", "farm", "granary"}) {
            Building holder = fullestWithStock(settlement, role, 1);
            if (holder != null && holder.foodStored() > 0) {
                holder.setFoodStored(holder.foodStored() - 1);
                person.setHunger(person.hunger() - nutrition);
                return true;
            }
        }
        return false;
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
                } else if (person.hunger() >= Person.HUNGER_SEVERE) {
                    // Nothing carried, and every route above failed to hand them
                    // any. The usual reason is not that the town is empty: it is
                    // that their pockets are full.
                    //
                    // Inventory.add returns nothing when all six slots are taken
                    // and none of them holds bread, and settlers pick things up
                    // off the ground now — wildflowers, seeds, whatever they walk
                    // over. So a settler could stand in front of three thousand
                    // loaves, be handed none of them because there was nowhere to
                    // put one, and starve to death holding a fistful of weeds.
                    //
                    // Eating is not a logistics problem. Somebody at the granary
                    // does not need a free pocket to eat; they need a granary.
                    eatWhereItStands(settlement, person);
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
        // Levels rather than buildings, for the same reason the timber and stone
        // ceilings count them: a granary raised a level should hold more than it
        // did, or improving one is a change of scenery.
        int extensions = 0;
        for (Building standing : buildingsOf(settlement, "granary")) {
            extensions += Math.max(1, standing.level());
        }
        for (Building standing : buildingsOf(settlement, "storehouse")) {
            extensions += Math.max(1, standing.level());
        }
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
    /** Whether a mill stands and a miller works it. */
    public static boolean millRuns(Settlement settlement) {
        return settlement.countBuildings("kingdoms:mill") > 0
                && JobPlanner.count(settlement, Profession.MILLER) > 0;
    }

    private static int countFarmHands(Settlement settlement, boolean starving) {
        // laboursAs, not the raw profession: below VILLAGE the pioneers ARE the
        // farmers, and a camp that counted only crystallized ones would harvest
        // nothing until the staffing table woke up.
        return (int) settlement.residents().stream()
                .filter(p -> settlement.laboursAs(p, Profession.FARMER)
                        && !heldBackByHunger(settlement, p, starving))
                .count();
    }

    /**
     * Matched by blueprint-path suffix so any catalog's "farm" counts. A
     * placeholder for datapack-declared building roles, like the catalog itself.
     */
    private static List<Building> buildingsOf(Settlement settlement, String pathSuffix) {
        List<Building> result = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            // Strip the level first. An improved farm is still a farm — missing that
            // drops every leveled building out of the food chain, out of the
            // workplace lookup, and off the end of a path.
            if (BuildPlanner.baseIdOf(building.blueprintId()).endsWith(pathSuffix)) {
                result.add(building);
            }
        }
        return result;
    }
}
