package com.civilization.sim.settlement;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Foods;
import com.civilization.sim.person.HaulTask;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Inventory;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The food chain, from field to mouth. Hunger is the one thing that outranks a
 * job: from {@link Person#HUNGER_HUNGRY} a settler with nothing to eat at home
 * walks to the nearest food themselves — see {@link #assignMealErrands} — and
 * past {@link Person#HUNGER_WEAK} they put the work down to do it, picking the
 * work back up when they have eaten.
 *
 * <p>The chain that is supposed to make that unnecessary:
 *
 * <pre>
 *   fields grow → FARMERS haul grain → the bakery bakes → granary pool
 *                                                              ↓
 *                                              TRADERS stock → market
 *                                                              ↓
 *      mouths ← personal inventory ← family pantry ← a family member fetches
 * </pre>
 *
 * <p>Every link is real, held state: cut wheat waits on the farm as
 * {@link TownStores#GRAIN} until a farmer carries it in, the bakery — the mill
 * if one runs, the hearth or the granary otherwise — is the only place grain
 * becomes food at all, the granary holds the town's bread, market hands move
 * retail stock, one member per family keeps the pantry filled, and each person
 * carries and eats their own food. Break any link — no farmers, nowhere to
 * bake, no granary space, no market hands, a housebound family — and hunger
 * arrives downstream.
 *
 * <p><strong>Nobody eats grain.</strong> A settlement with fields and no oven
 * cuts wheat, stacks it and starves beside it, and that is the rule rather than
 * a hole in it: {@code /civ info} prints the sacks so the answer is a building
 * and not a mystery.
 *
 * <p><strong>Hunger</strong> rises {@link #HUNGER_PER_STEP} a step and is scored
 * 0–99, so a settler who never eats goes from fed to dead in ninety-nine steps —
 * something over eight minutes of play. Every rung costs the same as every other
 * one; there is no separate death clock any more.
 * <ul>
 *   <li><b>0–29</b> — fed;</li>
 *   <li><b>30–59</b> — hungry: eats what is in their pockets, refills those from
 *       the family pantry, and walks to the nearest food when there is nothing
 *       at home to refill from. They keep working while they walk;</li>
 *   <li><b>60–89</b> — weak: stops farming, hauling and building and goes to eat
 *       instead. Nowhere to go and they keep working, because a starving idler is
 *       worse off than a starving worker;</li>
 *   <li><b>90–98</b> — starving: the town reaches into the granary, the stalls
 *       and the fields on their behalf. Ten steps, which is what the old grace
 *       period was worth;</li>
 *   <li><b>99</b> — dead, on the step hunger reaches it with nothing eaten.
 *       Starvation deaths are permanent and enter the town's history.</li>
 * </ul>
 *
 * <p><strong>Debuffs belong to a starving town, not to a hungry person.</strong>
 * Slowness and weakness are applied in the world only while
 * {@link #isStarving} holds, so one settler who missed lunch walks at full
 * speed and a town in famine visibly drags. Walking to dinner always comes
 * first: a person is sent to food a full thirty points before anything slows
 * them down.
 *
 * <p>Young settlements without a market fetch straight from the granary pool;
 * once a market stands, families shop there — the chain grows with the town.
 */
public final class FoodPlanner {

    public static final int STARTING_PROVISIONS = 100;

    // hunger pacing
    //
    // One a step, which makes the whole 0-99 scale a hundred steps of honest
    // clock: the severe band is ten steps long on its own, exactly what the old
    // grace period after the cap was worth, and nothing else needs a timer.
    public static final int HUNGER_PER_STEP = 1;

    // the chain's carrying numbers
    //
    // What a farmer brings in per step used to be a flat one loaf, and there
    // used to be a grace period after which a watched farm went back on the
    // clock. Both are gone: how much a field yields is the field's business now
    // — see Field — and a watched field is farmed by hands or it is not farmed.
    public static final int FARMERS_PER_FARM = 2;
    public static final int FARM_STORE_CAP = 40;

    /**
     * Sheaves one farm's shelf holds before the cutting stops: the same forty.
     *
     * <p>Deliberately the same number under a new name rather than a rename,
     * because both goods can sit on a farm at once — a save made before bread
     * was a thing has loaves in {@code foodStored} and grain in the store, and
     * the cap that stops the harvest is about the grain.
     *
     * <p>A full shelf stops the cut and the field stays standing, exactly as it
     * did when the shelf held loaves. Nothing rots; the hauling is the
     * bottleneck and it is meant to show.
     */
    public static final int FARM_GRAIN_CAP = FARM_STORE_CAP;

    /**
     * Sheaves the town's bakery will let pile up before it stops sending for
     * more: two hundred.
     *
     * <p>{@link #BASE_GRANARY}'s number, and for the same reason — this is the
     * bulk shelf beside the oven, and it should hold about what the larder next
     * to it does. It is not a throttle in a town that is eating what it grows: a
     * field brings in roughly one sheaf a step and the hearth alone bakes
     * {@link #BAKE_PER_STEP} of them. What fills it is a town with more wheat
     * than it can store bread — the oven stops when the larder is full, and
     * then the sheaves stack up here and then in the fields. That backlog is
     * the honest picture of a good harvest with nowhere to put it, and the way
     * out of it is a bigger granary rather than a bigger number here.
     */
    public static final int BAKERY_GRAIN_CAP = 200;

    /**
     * Loaves a hearth turns out in a step, one for one from grain: four.
     *
     * <p>Sized off what a settlement eats. Hunger climbs
     * {@link #HUNGER_PER_STEP} a step and a provision is worth thirty of it, so
     * one mouth wants a loaf every fifteen steps and fifteen mouths want one a
     * step. Four is that village fed four times over, which is the slack asked
     * for — and, more to the point, it is comfortably above what the fields can
     * deliver: {@link Field#LOAVES_PER_STEP_TENDED} is one sheaf a step per
     * fully staffed field, so a town needs more than four fields before the
     * oven rather than the ground is what limits it. The field stays the truth.
     *
     * <p>No profession is asked for. A camp bakes at its fire; whoever is there
     * does it, and that is why the hearth the HOMESTEAD program raises is
     * enough to keep a founding party alive without a miller in it.
     */
    public static final int BAKE_PER_STEP = 4;

    /**
     * The mill's bargain: two sheaves in, three loaves out.
     *
     * <p>The old flat "+ working/2" bonus, made honest. A mill used to add half
     * again to the town's whole harvest out of nowhere, wherever the harvest
     * happened to be standing. It now does the same thing by doing a real job in
     * a real place: grain is carried to it and ground at three loaves for every
     * two sheaves. Same fifty per cent, and a mill nobody hauls to grinds
     * nothing.
     */
    public static final int MILL_GRAIN_PER_BATCH = 2;
    public static final int MILL_LOAVES_PER_BATCH = 3;

    /**
     * Sheaves one miller can put through the stones in a step: six.
     *
     * <p>Half again the hearth's four, and then worth half again as much per
     * sheaf — so one miller at a mill is nine loaves a step against a hearth's
     * four. That is what a mill is for, and it is why the VILLAGE program
     * bothers to order one.
     */
    public static final int MILL_GRAIN_PER_MILLER = 6;

    /*
     * There is no second haul, and that is a measured decision rather than an
     * omission.
     *
     * The design this was built to was grain out to the mill and bread back
     * from it: two legs, two errands. The second leg was written, and then run.
     * A mill with one miller grinds nine loaves a step; a carrier shoulders
     * FARMER_CARRY of them and a round trip across a town is the better part of
     * ten steps, so one pair of hands moves about one loaf a step and a town
     * with one spare pair of hands is every town below about forty people.
     * HaulPlanner.courierFor is right to refuse the rest — it will not take a
     * builder or a guard, and a farmer has a field — so the bread stacked up at
     * the mill, the shelf filled, the stones stopped for want of anywhere to put
     * the flour, and the grain backed up behind that. Measured on the rough-
     * ground fixture: a hundred and twenty loaves stuck at the mill, a hundred
     * and seventy-four sheaves stranded beside them, the granary draining, and a
     * town that had reached eighteen people stopped there for good.
     *
     * So the flour goes where flour goes. The grain is carried to the mill —
     * that leg is real, and it is the one that matters, because a mill nobody
     * hauls to still grinds nothing — and what the miller makes joins the
     * town's larder the same way the hearth's bread does. The granary pool was
     * never a building anyway: it is the town's bread wherever the town keeps
     * it, spread across whatever stores are standing.
     */

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

    /**
     * How far a forager will range from the camp for a handful of berries.
     *
     * <p>Forty-eight blocks: about the observed radius, and about as far as
     * somebody would sensibly walk for a meal and be home the same step.
     */
    public static final int FORAGE_RADIUS = 48;

    /**
     * How often the camp actually looks at the ground it is standing on.
     *
     * <p>Counting what is growing nearby is a chunk read, and a camp forages
     * every step. Twenty steps between surveys is cheap and still catches a
     * wood the player has cut down, or a field somebody planted next door.
     */
    public static final int FORAGE_SURVEY_STEPS = 20;

    /**
     * Steps for one picked meal to grow back.
     *
     * <p>The whole of "and not forever". A patch yields what it yields and then
     * has to recover, and it recovers at one meal every four steps — which is
     * about what a founding party of four eats. So a camp in a wood can stand
     * still and just about live off it; a camp that tries to <em>grow</em> on
     * wild food strips the wood, and a camp on bare superflat had nothing to
     * strip. Deliberately small: berries are a reprieve, not an economy.
     */
    public static final int FORAGE_REGROWTH_STEPS = 4;

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
     *
     * <p>Two ways in. Anybody at {@link Person#HUNGER_SEVERE} is nine steps from
     * dead and that is a crisis whatever the granary says — a town with bread on
     * the shelf and a settler about to die in front of it has a distribution
     * emergency, and the rules this flag lifts are the ones standing in the way.
     * Failing that, the larder: a town with less food than
     * {@link #CRISIS_FOOD_PER_RESIDENT} a head, counting every field, stall and
     * family pantry, is starving whether or not anybody has noticed yet.
     */
    public static boolean isStarving(Settlement settlement) {
        int mouths = settlement.population();
        if (mouths == 0) {
            return false;
        }
        for (Person person : settlement.residents()) {
            if (person.hunger() >= Person.HUNGER_SEVERE) {
                return true;   // somebody is nine steps from dead
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
     * <p>Only from {@link Person#HUNGER_WEAK}, which is deliberately thirty
     * points above where a meal errand is handed out: going for dinner and
     * downing tools are separate matters, and between 30 and 59 a person walks
     * to food while still counting as a worker.
     *
     * <p>From 60 it normally does hold people back, and should — the weak stop
     * farming, hauling and building. That rule is also precisely how a town dies:
     * weak hands bring in no food, so more hands go weak. While the town is
     * starving it is suspended, and the hungry are allowed to go and fetch their
     * own dinner.
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

        forage(settlement, ctx);
        growHarvest(settlement, ctx, starving);
        // The oven runs on whatever reached it before today, which is why the
        // grain has to be hauled: cutting wheat this step feeds nobody this
        // step, and a town that has not built an oven is never fed by it at all.
        bake(settlement);
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
            Building stall = fullestWithStock(settlement, BuildingRole.MARKET, 1);
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
     * Sends anybody hungry with no dinner at home to the nearest thing they can
     * eat.
     *
     * <p><strong>Walking to food comes before any penalty for not having
     * eaten.</strong> The errand is handed out from {@link Person#HUNGER_HUNGRY}
     * — a full thirty points, thirty steps, before the weak line and before the
     * world slows anybody down. Between 30 and 59 this costs nothing: a person
     * with a meal errand is simply a person on an errand, and every worker loop
     * on both fidelities already stands aside for somebody with a haul. Past
     * {@link Person#HUNGER_WEAK} the same errand is what downing tools looks
     * like, because by then they are too weak to work anyway.
     *
     * <p>There is nothing to save and restore — a profession is a standing fact
     * and the build queue belongs to the town — so suspending is an errand and
     * resuming is that errand ending.
     *
     * <p>Five refusals. Somebody already out on an errand keeps it for the one
     * step it takes to put the load down. Somebody carrying food eats where they
     * stand and needs no walk. Somebody whose family pantry can hand them a loaf
     * is fed by arithmetic in {@link #eatAndHunger} without going anywhere. A
     * guard leaves the wall for dinner only while the town is calm, because a
     * hungry watch beats no watch. And a settler with nothing to walk to is left
     * on the job: see {@link #heldBackByHunger}.
     */
    private static void assignMealErrands(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (person.hunger() < Person.HUNGER_HUNGRY) {
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
            if (pantryWillFeed(settlement, person)) {
                continue;   // dinner is already at home and nobody has to walk
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
     * Whether this person's own family larder will feed them where they stand.
     *
     * <p>{@link #eatAndHunger} reaches into the pantry by arithmetic every step,
     * so somebody with a stocked larder at home has already eaten and needs no
     * errand. Two ways that fails and the walk is wanted after all: no housed
     * family to have a pantry at all, and a pantry with food in it that cannot
     * hand any over because every pocket is full of picked-up weeds. Walking to
     * the larder lets the loaf be eaten on the spot instead of needing a free
     * slot first.
     */
    private static boolean pantryWillFeed(Settlement settlement, Person person) {
        for (Household household : settlement.households()) {
            if (!household.contains(person.id())) {
                continue;
            }
            // Nobody belongs to two families, so this is the answer either way.
            boolean roomForALoaf = person.inventory().slots().size() < Inventory.SLOTS;
            return household.isHoused() && household.pantry() > 0 && roomForALoaf;
        }
        return false;
    }

    /**
     * The closest place this person could actually get a mouthful, or null.
     *
     * <p>Their own family larder counts, and is usually the nearest thing there
     * is — though a larder that will simply feed them is filtered out one step
     * earlier by {@link #pantryWillFeed}, so in practice this reaches the home
     * shelf only for the case that put it on the report: pockets so full of
     * picked-up weeds that the larder could not hand them anything.
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
                    store == HaulTask.Store.MARKET ? BuildingRole.MARKET : BuildingRole.CROP_FARM)) {
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
        // Where the grain is going. Null is a town with no oven, and then the
        // farmers stay in the rows and the sheaves stack up in the fields —
        // which is the honest picture of a town that has not finished its food
        // chain, and exactly what /civ info reports.
        Building oven = bakery(settlement);
        int ovenSpace = oven == null ? 0
                : BAKERY_GRAIN_CAP - oven.stores().get(TownStores.GRAIN)
                        - grainOnTheRoad(settlement);

        for (Person person : settlement.residents()) {
            if (person.haul() != null || heldBackByHunger(settlement, person, starving)) {
                continue;
            }
            // A pioneer takes the farmer's errands while generalists labor --
            // same arm, same granary budget, no second copy of the rules.
            Profession arm = person.profession() == Profession.PIONEER
                    && settlement.laborsAs(person, Profession.FARMER)
                    ? Profession.FARMER : person.profession();
            switch (arm) {
                case FARMER -> {
                    // The farmer's errand is the same walk it always was — out
                    // to the fullest field, back with a full load — and what is
                    // on their back is grain now rather than bread. It goes to
                    // the oven rather than the granary, because grain in a
                    // larder is grain nobody is baking.
                    if (oven == null || ovenSpace < FARMER_CARRY) {
                        continue;
                    }
                    Building field = fullestUnspoken(settlement, spokenFor, worthTheWalk);
                    if (field != null) {
                        // Store to store, on the generic bulk path: grain lives
                        // on a building's own shelves, which is what makes the
                        // farm's sacks and the oven's sacks two different piles
                        // that somebody has to walk between.
                        person.setHaul(new HaulTask(TownStores.GRAIN,
                                HaulTask.Store.STORE, field.origin(),
                                HaulTask.Store.STORE, oven.origin(), FARMER_CARRY));
                        spokenFor.merge(field.origin(), FARMER_CARRY, Integer::sum);
                        ovenSpace -= FARMER_CARRY;
                    }
                }
                case TRADER -> {
                    if (granaryStock < TRADER_CARRY) {
                        continue;
                    }
                    Building stall = emptiestBelowCap(settlement, BuildingRole.MARKET, MARKET_STOCK_CAP);
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

    /** Sheaves already on somebody's back or promised to a shoulder, town-wide. */
    private static int grainOnTheRoad(Settlement settlement) {
        int moving = 0;
        for (Person person : settlement.residents()) {
            HaulTask errand = person.haul();
            if (errand == null || !TownStores.GRAIN.equals(errand.resource())) {
                continue;
            }
            moving += errand.isLoaded() ? errand.carried() : errand.requested();
        }
        return moving;
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

            Building stall = fullestWithStock(settlement, BuildingRole.MARKET, 1);
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
        List<Building> granaries = buildingsOf(settlement, BuildingRole.GRANARY);
        if (!granaries.isEmpty()) {
            return granaries.getFirst().origin();
        }
        List<Building> stores = buildingsOf(settlement, BuildingRole.STORE);
        return stores.isEmpty() ? settlement.center() : stores.getFirst().origin();
    }

    private static Building buildingAt(Settlement settlement, SimPos pos) {
        for (Building building : settlement.buildings()) {
            if (building.origin().equals(pos)) {
                return building;
            }
        }
        return null;
    }

    private static Building fullestWithStock(Settlement settlement, BuildingRole role, int minimum) {
        Building best = null;
        for (Building building : buildingsOf(settlement, role)) {
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
            // By what is being carried rather than by which kind of store it
            // left, because grain moves on the generic bulk path now: a load
            // walking out of a field and a load walking out of a storehouse are
            // both Store.STORE, and only the resource tells the field's errands
            // apart.
            if (errand == null || errand.isLoaded()
                    || !TownStores.GRAIN.equals(errand.resource())) {
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
                && emptiestBelowCap(settlement, BuildingRole.MARKET, MARKET_STOCK_CAP) != null;
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
        for (Building field : buildingsOf(settlement, BuildingRole.CROP_FARM)) {
            int left = Field.grainStored(field) - spokenFor.getOrDefault(field.origin(), 0);
            if (left >= minimum && left > most) {
                most = left;
                best = field;
            }
        }
        return best;
    }

    private static Building emptiestBelowCap(Settlement settlement, BuildingRole role, int cap) {
        Building best = null;
        for (Building building : buildingsOf(settlement, role)) {
            if (building.foodStored() < cap
                    && (best == null || building.foodStored() < best.foodStored())) {
                best = building;
            }
        }
        return best;
    }

    /**
     * Wild food, gathered by hand — the camp's food source before the fields.
     *
     * <p>Three limits, and the third is the one that matters. A handful of
     * foragers turn up one meal a step, so a party survives on it without
     * prospering. Foraging stops at a hand-to-mouth ceiling well below what the
     * fed streak asks for, so no settlement graduates HOMESTEAD on berries.
     * And — this is new — they can only bring back what is actually growing
     * out there.
     *
     * <p>Foraging used to be a headcount divided by three and nothing else,
     * which meant loaves out of thin air: the same meal every step whether the
     * camp sat in a berry-thick taiga, in the middle of a desert, or on bare
     * superflat with a hundred blocks of stone under it and sky above. If it is
     * a superflat world, what are they foraging? So the ground is asked, through
     * {@link com.civilization.sim.platform.WorldBridge#forageableNear}, and what it
     * says is a hard ceiling on the hands.
     *
     * <p>And it depletes. Every meal is booked against the patch it came from
     * and grows back at {@link #FORAGE_REGROWTH_STEPS}, so a wood feeds a camp
     * for a while and then feeds it only as fast as it recovers. A camp with
     * nothing growing nearby forages nothing at all, forever, and must farm or
     * die — which is the point.
     *
     * <p>No yield table is consulted here and there is nothing left for one to
     * scale. Wild food is not conjured any more; it is picked, out of a
     * countable supply, and the settings that decide how much of a town's
     * income is imaginary have no business with food that is not.
     */
    private static void forage(Settlement settlement, SimContext ctx) {
        // A people who never farm never stop picking. The gate was "below
        // VILLAGE", which is right for a founding party on its way to a field and
        // wrong for a camp that will never have one -- see GoblinCamp.
        if (!StagePlanner.pioneersLabor(settlement.stage())
                && !GoblinCamp.foragesForever(settlement)) {
            return;
        }
        if (totalFood(settlement)
                >= settlement.population() * GoblinCamp.forageCeilingPerMouth(settlement)) {
            return;
        }
        // Foragers as well as farmers. A camp's food supply is a trade of its own
        // -- see Profession.FORAGER -- and while the camp is young its pioneers
        // are still doing it, which is what laborsAs answers.
        int hands = (int) settlement.residents().stream()
                .filter(p -> settlement.laborsAs(p, Profession.FARMER)
                        || p.profession() == Profession.FORAGER)
                .count();
        int byHand = (hands + FORAGERS_PER_MEAL - 1) / FORAGERS_PER_MEAL;
        if (GoblinCamp.hasShaman(settlement)) {
            byHand += GoblinCamp.SHAMAN_FORAGE_BONUS;
        }
        int gathered = Math.min(byHand, settlement.forageAllowance(ctx));
        if (gathered > 0) {
            settlement.recordForaged(gathered);
            settlement.stores().add(TownStores.FOOD, gathered);
        }
    }

    /**
     * Fields ripen, and farmers cut what has ripened.
     *
     * <p>No yield table is consulted here and there is nothing left for one to
     * scale. Food is not conjured any more and it is not a percentage of an
     * imagined harvest either: it is the field that actually stands in the
     * world, growing at Minecraft's own rate, cut by the farmers the town
     * actually has. See {@link Field}, which owns all of that arithmetic.
     *
     * <p>Two fidelities, one ledger. Every field ripens every step, watched or
     * not, because the world grows crops in a loaded chunk and the ledger is
     * only keeping count. Where somebody is watching, {@code FarmWorker}'s real
     * hands do the cutting — they credit the farm and they debit the ledger
     * through {@link Field#cut}, and the clock takes nothing. There is no floor
     * under that any more: a watched farm whose farmers have stopped grows a
     * field of ripe wheat that nobody cuts, and standing in it looking at it is
     * the truthful thing for a player to be able to do.
     *
     * <p>Hands do two things in a field and both count. A swing goes into the
     * harvest if there is anything ripe to take and into the rows if there is
     * not — see {@link Field#tend}, which is what stops the unwatched clock
     * running at a thirtieth of the pace a watched farmer sets.
     *
     * <p>And when a field flips from unwatched to watched, the world's crops are
     * at whatever ages they were left at and the ledger is the authority — so the
     * bridge is asked, once, to make the blocks say what the ledger says.
     */
    private static void growHarvest(Settlement settlement, SimContext ctx, boolean starving) {
        List<Building> farms = buildingsOf(settlement, BuildingRole.CROP_FARM);
        if (farms.isEmpty()) {
            return;
        }
        int hands = Math.min(countFarmHands(settlement, starving),
                farms.size() * FARMERS_PER_FARM);
        // Sickle-strokes to spend this step, across the whole town's fields
        // rather than parcelled out one field at a time. A farmer who runs out
        // of ripe wheat in this field walks into the next one, which is both
        // what a farmer would do and the difference between a village living
        // and dying: one farmer with three fields used to work one of them and
        // watch the other two stand ripe.
        int strokes = hands * Field.BLOCKS_PER_FARMER_PER_STEP;
        for (Building farm : farms) {
            // Asked of the town, not of the field. A player at the square is
            // near enough to the claim to be watching, and a farm inside the
            // ring he cannot make out is still his town's field: the clock does
            // not reap it for him. An outlying field beyond the ring answers for
            // itself — see Settlement.isWatched(ctx, site).
            boolean watched = settlement.isWatched(ctx, farm.origin());
            if (watched && !farm.wasWatched()) {
                ctx.bridge().setFieldRipeness(farm.origin(), farm.footprint(),
                        Field.ripeBlocks(farm));
            }
            farm.setWatched(watched);
            Field.ripen(farm, ctx);
            if (strokes <= 0) {
                continue;   // the hands ran out before the fields did
            }
            // One field can only take so many hands, however many the town has.
            int here = Math.min(strokes, FARMERS_PER_FARM * Field.BLOCKS_PER_FARMER_PER_STEP);
            strokes -= here;
            if (!watched) {
                // A full farm stops the harvest and the field stays ripe. That is
                // the hauling bottleneck made visible rather than a loss: nothing
                // rots, and the moment a farmer carries a load to the granary the
                // field is still there waiting to be cut.
                int room = Math.max(0, FARM_GRAIN_CAP - Field.grainStored(farm));
                int cut = Field.harvest(farm, Math.min(here, room));
                here -= cut;
                if (cut > 0) {
                    Field.deliver(farm, cut);
                }
            }
            // Whatever the hands did not spend cutting, they spent in the rows.
            // See Field.tend: this is what keeps the unwatched field running at
            // the same pace as the one a player is standing in.
            Field.tend(farm, here);
        }
        // The mill's half-again bonus used to be applied right here, as a flat
        // "cutTotal / 2" of extra loaves conjured onto whichever fields had
        // just been reaped. It is gone. A mill is a building somebody carries
        // grain to now, and what it gives back it gives back at the stones —
        // see bake.
    }

    /**
     * Where this town turns grain into bread, or null if it has nowhere.
     *
     * <p>Two answers, in order. A <strong>mill</strong> if one stands and a
     * miller works it, because that is what a mill is for and it pays three
     * loaves for two sheaves. Otherwise the <strong>hearth or the granary</strong>,
     * whichever the town has raised, at one for one and with no profession
     * asked for — a camp bakes at its fire, and whoever is standing by it does
     * the baking.
     *
     * <p><strong>And null is a real answer.</strong> A settlement with fields
     * and no oven cuts wheat, stacks it, and starves beside full sacks. That is
     * not a hole in the rules; it is the rule. Grain is not food, and a town
     * that has not built anywhere to bake has not finished its food chain.
     * {@code /civ info} prints the sacks so the reason is visible rather than
     * mysterious.
     */
    public static Building bakery(Settlement settlement) {
        if (millRuns(settlement)) {
            Building mill = settlement.buildingWithRole(BuildingRole.MILL);
            if (mill != null) {
                return mill;
            }
        }
        Building granary = settlement.buildingWithRole(BuildingRole.GRANARY);
        if (granary != null) {
            return granary;
        }
        return settlement.buildingWithRole(BuildingRole.HEARTH);
    }

    /**
     * One step at the oven: grain in, bread out.
     *
     * <p>Baking happens where the grain is, which is the whole reason grain has
     * to be hauled at all. Only the bakery-of-record bakes — a second granary
     * across town is a shelf, not an oven — so a town has exactly one place its
     * bread comes from and exactly one queue to be short at.
     *
     * <p>A mill's bread lands on the mill's own shelf and waits for a carrier;
     * a hearth's or a granary's goes straight into the town's larder, because
     * that is the building the larder <em>is</em>.
     */
    private static void bake(Settlement settlement) {
        Building oven = bakery(settlement);
        if (oven == null) {
            return;
        }
        int grain = oven.stores().get(TownStores.GRAIN);
        if (grain <= 0) {
            return;
        }
        // The larder's own ceiling, not a second one. Baking into a full larder
        // would spoil the loaf and the sheaf both; leaving the grain on the
        // shelf costs nothing and the oven picks it up again when there is room.
        int room = granaryCapacity(settlement) - settlement.foodStock();
        int spent;
        int baked;
        if (oven.role() == BuildingRole.MILL) {
            int millers = JobPlanner.count(settlement, Profession.MILLER);
            int batches = Math.min(Math.min(grain, millers * MILL_GRAIN_PER_MILLER)
                    / MILL_GRAIN_PER_BATCH, room / MILL_LOAVES_PER_BATCH);
            spent = batches * MILL_GRAIN_PER_BATCH;
            baked = batches * MILL_LOAVES_PER_BATCH;
        } else {
            spent = Math.min(Math.min(grain, BAKE_PER_STEP), room);
            baked = spent;
        }
        if (baked <= 0) {
            return;
        }
        oven.stores().take(TownStores.GRAIN, spent);
        settlement.stores().add(TownStores.FOOD, baked);
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
        for (BuildingRole role : new BuildingRole[] {BuildingRole.MARKET,
                BuildingRole.CROP_FARM, BuildingRole.GRANARY}) {
            Building holder = fullestWithStock(settlement, role, 1);
            if (holder != null && holder.foodStored() > 0) {
                holder.setFoodStored(holder.foodStored() - 1);
                person.setHunger(person.hunger() - nutrition);
                return true;
            }
        }
        return false;
    }

    /** Hunger rises one; the hungry eat what they carry; whoever hits the cap dies. */
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
                        Building stall = fullestWithStock(settlement, BuildingRole.MARKET, 1);
                        if (stall == null) {
                            // And past the stalls, the fields themselves. Watched
                            // towns can jam with every store empty and hundreds of
                            // food capped at the farms — hauling is the bottleneck,
                            // not growing — and nobody starves in sight of a full
                            // field. The desperate eat straight from the rows.
                            stall = fullestWithStock(settlement, BuildingRole.CROP_FARM, 1);
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

            // The top of the scale is the end of it. There is no grace period
            // and no separate counter: hunger climbs one a step, so the ten
            // steps from HUNGER_SEVERE to here are the death clock, spent with
            // every store in town being emptied on this person's behalf. Reach
            // 99 with nothing eaten and that was the last of it.
            //
            // Anything eaten above undoes at least four hunger and setHunger
            // caps at HUNGER_MAX, so somebody who got a mouthful this step
            // cannot still be at the cap when this is asked.
            if (person.hunger() >= Person.HUNGER_MAX) {
                starved.add(person);
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
        for (Building standing : buildingsOf(settlement, BuildingRole.GRANARY)) {
            extensions += Math.max(1, standing.level());
        }
        for (Building standing : buildingsOf(settlement, BuildingRole.STORE)) {
            extensions += Math.max(1, standing.level());
        }
        return BASE_GRANARY + extensions * GRANARY_PER_BUILDING;
    }

    public static int marketStock(Settlement settlement) {
        return buildingsOf(settlement, BuildingRole.MARKET).stream().mapToInt(Building::foodStored).sum();
    }

    /**
     * Loaves sitting on a field's shelf.
     *
     * <p>Nearly always nought now, and kept because it is not always: a save
     * made before bread was a thing has loaves out in the fields, and they are
     * still food somebody can walk to and eat. What a field holds <em>today</em>
     * is grain — {@link #farmGrain} — and nobody eats that.
     */
    public static int farmStock(Settlement settlement) {
        return buildingsOf(settlement, BuildingRole.CROP_FARM).stream()
                .mapToInt(Building::foodStored).sum();
    }

    /** Sheaves standing on the town's fields, waiting for somebody to carry them in. */
    public static int farmGrain(Settlement settlement) {
        return buildingsOf(settlement, BuildingRole.CROP_FARM).stream()
                .mapToInt(Field::grainStored).sum();
    }

    /** Sheaves that have reached the oven and not yet been baked. */
    public static int bakeryGrain(Settlement settlement) {
        Building oven = bakery(settlement);
        return oven == null ? 0 : oven.stores().get(TownStores.GRAIN);
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
        return settlement.countBuildings("civilization:mill") > 0
                && JobPlanner.count(settlement, Profession.MILLER) > 0;
    }

    private static int countFarmHands(Settlement settlement, boolean starving) {
        // laborsAs, not the raw profession: below VILLAGE the pioneers ARE the
        // farmers, and a camp that counted only crystallized ones would harvest
        // nothing until the staffing table woke up.
        return (int) settlement.residents().stream()
                .filter(p -> settlement.laborsAs(p, Profession.FARMER)
                        && !heldBackByHunger(settlement, p, starving))
                .count();
    }

    /**
     * Every standing building of a kind, asked of {@link BuildingRole}.
     *
     * <p>This used to match a blueprint-path <em>suffix</em>, and that was a bug
     * with a long tail: {@code civilization:animal_farm} ends with "farm", so every
     * compound of sheep and cows in the mod has been counted as a crop field
     * since the day the compound was added. It was handed a seventy-one-block
     * ripeness ledger it has no wheat for, farmers were dispatched to it, and
     * the harvest it "brought in" was food out of nowhere. The role table
     * settles what a building is once, on its bare name, so {@code animal_farm}
     * is {@link BuildingRole#ANIMAL_FARM} and nothing in the food chain ever
     * mistakes it again. Levels and culture folders come off with it.
     */
    private static List<Building> buildingsOf(Settlement settlement, BuildingRole role) {
        return settlement.buildingsWithRole(role);
    }
}
