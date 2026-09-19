package com.civilization.sim.settlement;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The beasts an animal farm actually holds, as a number the clock can work.
 *
 * <p>The third of its kind, and the last big hole in the honest economy.
 * {@link Field} counts the wheat standing in a farm's rows, {@link Stand} counts
 * the trees left in a camp's claim, {@link Seam} counts the rock under a mine —
 * and the compound of pens counted nothing at all. It was drawn, it was staffed
 * with a shepherd, and it produced not one thing: {@link BuildingRole#ANIMAL_FARM}
 * sat outside the food chain entirely, {@code Foods} listed cooked beef and pork
 * with nothing anywhere making them, and the only animal that ever appeared in a
 * pen was one the old {@code ShepherdWorker} conjured out of nothing whenever it
 * found the pen short.
 *
 * <p>So the pens get a ledger, and it is the same ledger in both fidelities. A
 * head of stock is a head of stock whether a player is standing at the gate or a
 * thousand blocks away; where somebody is watching, the shepherd's own hands
 * feed and cull and the world's real cows are reconciled <em>to</em> this, and
 * where nobody is, the arithmetic below does exactly what those hands would have
 * done. Where there is a hand there is no clock.
 *
 * <p><strong>Nothing grows unfed.</strong> That is not a balance knob, it is
 * vanilla: two animals breed when somebody feeds them and never otherwise, so a
 * compound with no shepherd, or a town with no grain to spare, keeps the beasts
 * it has and gets no more. It is the sharpest difference from the field, which
 * ripens on the weather whether anybody works it or not.
 *
 * <p><strong>What the ledger is kept in.</strong> The building's own
 * {@link Building#stores()} map, under the prefixes below, for the same reason
 * {@link TownStores#GRAIN} is: the codecs already write that map whole, so the
 * herd saves and loads with everything else the compound holds and nothing in
 * the save layer needed a line. It also means the town map's tooltip for the
 * compound lists the herd without being told how.
 */
public final class Herd {

    /**
     * Depth of one pen, matching what {@code BlueprintPlacer.animalFarm} lays.
     *
     * <p>Three rows of grass between two runs of fence. This used to be written
     * down in three places — the placer, {@code ShepherdWorker} and the drawing's
     * own test — which is exactly how the beasts and the fences come to disagree
     * about where a pen is. It is written down here now and the other three read
     * it.
     */
    public static final int PEN_DEPTH = 3;

    /**
     * Ground one beast needs: <strong>two square blocks</strong>.
     *
     * <p>Tight, and meant to be. It is not a welfare standard, it is the point
     * at which a pen stops reading as a pen and starts reading as a stockyard
     * with a cow's worth of wool sticking out of it — and the ceiling exists at
     * all because vanilla breeding has no ceiling of its own. A pen left to
     * itself fills until the entities are standing inside each other and the
     * server is ticking two hundred mobs in a nine-by-seventeen box, which is
     * the single most reliable way this mod could ruin somebody's framerate.
     */
    public static final int BLOCKS_PER_HEAD = 2;

    /**
     * Head one pen holds: <strong>ten</strong>.
     *
     * <p>Counted off the drawing rather than guessed, the way {@link
     * Field#CROP_BLOCKS} is. The compound is nine wide, so the fence runs at
     * {@code dx = ±4} and the ground inside it is seven columns; a pen is
     * {@link #PEN_DEPTH} rows of that, twenty-one blocks, and at
     * {@link #BLOCKS_PER_HEAD} that is ten head with a block left over for the
     * shepherd to stand on.
     *
     * <p>{@code AnimalPenTest} counts the grass the placer actually lays inside
     * one pen and asserts this number, so the two cannot drift apart.
     */
    public static final int HEAD_PER_PEN = (9 - 2) * PEN_DEPTH / BLOCKS_PER_HEAD;

    /**
     * What a compound is stocked with when it opens: <strong>two of each</strong>.
     *
     * <p>A breeding pair, which is the smallest number that is a herd rather
     * than a pet. One would be a dead end — nothing breeds alone — and this is
     * the whole of what the shepherd's old job description promised: "to bring
     * the first of each kind in". They are driven in off the countryside rather
     * than bought, so they cost the town nothing; the ordinary world is full of
     * cows.
     */
    public static final int STARTER_HEAD = 2;

    /**
     * Head the shepherd will never cull past: <strong>two</strong>.
     *
     * <p>The same pair, kept back. A shepherd who eats his last sheep has no
     * sheep, and a town that ate its way to one head of each can never breed its
     * way back — so the cull stops here whatever the town's hunger. It is the
     * one place in the food chain where the answer to a famine is "no".
     */
    public static final int BREEDING_PAIR = 2;

    /**
     * Game ticks from feeding a pair to having a grown beast out of it:
     * <strong>30,000</strong>, twenty-five minutes.
     *
     * <p>Vanilla's own arithmetic, added up. Two fed adults produce a baby at
     * once and then sit out a five-minute cooldown (6,000 ticks); the baby takes
     * twenty minutes to grow up (24,000 ticks). A head of stock is a grown one —
     * nobody eats a calf and nobody breeds one — so the thing a fed pair is
     * actually worth is one adult per thirty thousand ticks, and that is what
     * this is.
     *
     * <p>Deliberately a tick count and not a step count, for the reason
     * {@link Field#RIPENING_TICKS} is: steps are a setting and minutes of calf
     * are not.
     */
    public static final int BREEDING_TICKS = 30_000;

    /**
     * Sheaves it costs to bring one head into the world: <strong>two</strong>.
     *
     * <p>Vanilla again: one wheat into each of two animals is what breeds them.
     * Charged at the birth rather than at the mouthful, because the ledger has
     * no mouthfuls in it — and charged to the compound's own shelf, so the grain
     * has to be carried out to the pens by somebody. A town whose sacks are all
     * at the oven breeds nothing, which is the honest picture of a place that
     * has decided bread matters more than beef.
     */
    public static final int FEED_PER_HEAD = 2;

    /**
     * Sheaves a compound wants on its shelf: <strong>sixteen</strong>.
     *
     * <p>Eight births' worth. Enough that the pens are not stopped by every
     * round trip, little enough that a compound is never a second granary with
     * the town's whole harvest sitting in it where nobody can bake it.
     */
    public static final int FEED_STOCK = 16;

    /**
     * Useful turns one shepherd gets in per step: <strong>four</strong>.
     *
     * <p>The same count and the same reasoning as
     * {@link Field#BLOCKS_PER_FARMER_PER_STEP}: the view layer runs a pass every
     * twenty ticks and the shepherd takes one action per pass, so a hundred-tick
     * step is five actions and one in five is spent walking to the next pen.
     */
    public static final int TURNS_PER_SHEPHERD_PER_STEP = 4;

    /** Turns it takes to feed a pair: one beast each. */
    public static final int TURNS_PER_PAIR = 2;

    /**
     * Raw meat a compound's shelf holds before the culling stops: forty.
     *
     * <p>{@link FoodPlanner#FARM_STORE_CAP}, deliberately the same number as the
     * field's, because it is the same fact: a working building holds about one
     * load and then somebody has to carry it. A full shelf stops the cull and
     * the beasts go on standing in the pen, which is the hauling bottleneck made
     * visible rather than a loss.
     */
    public static final int FARM_MEAT_CAP = FoodPlanner.FARM_STORE_CAP;

    /** Wool, hides and feathers a compound's shelf holds. */
    public static final int FARM_CLIP_CAP = 64;

    /**
     * Raw meat the oven cooks in one step: <strong>four</strong>.
     *
     * <p>{@link FoodPlanner#BAKE_PER_STEP}, because it is the same fire. A hearth
     * that could bake four loaves and roast forty joints would be a hearth that
     * cared which one it was being asked for.
     */
    public static final int ROAST_PER_STEP = FoodPlanner.BAKE_PER_STEP;

    /**
     * Meals one roasted joint is worth: <strong>one</strong>.
     *
     * <p>The town's larder is counted in servings and handed out as
     * {@code Foods.PROVISION}, so the only question is how many sittings a joint
     * covers, and the answer is one. Cooked beef is worth half as much again as
     * bread in {@code Foods}, and that difference is deliberately <em>not</em>
     * taken here: the pool is fungible and paying one and a half loaves for a
     * joint would quietly make the pens the cheapest food in the mod.
     */
    public static final int MEALS_PER_JOINT = 1;

    /** How much one shepherd carries in a load: a farmer's basket. */
    public static final int SHEPHERD_CARRY = FoodPlanner.FARMER_CARRY;

    /** A load worth leaving the pens for. */
    public static final int WORTH_LEAVING_THE_PENS = SHEPHERD_CARRY;

    /** Thousandths, the unit the growth ledger is kept in. */
    private static final int PER_HEAD = 1_000;

    /** Head of stock, by species: {@code herd:cow}. */
    private static final String HEAD = "herd:";

    /** Thousandths toward the next head, by species: {@code unborn:cow}. */
    private static final String UNBORN = "unborn:";

    /** Set once a compound has had its breeding pairs driven in. */
    private static final String STOCKED = "stocked";

    /**
     * What killing one of these actually gives a town.
     *
     * <p>Vanilla's own loot, averaged, and the averaging is the only liberty
     * taken: a cow drops one to three beef and this says two. It has to be a
     * fixed number because the unwatched cull has no dice to roll and the two
     * fidelities have to agree — where somebody is watching, the shepherd
     * gathers the real drops off the ground and the real drops are what the town
     * gets.
     *
     * <p><strong>A goat gives nothing, and that is not an omission.</strong>
     * Vanilla drops nothing whatever for killing a goat, so a ledger that paid
     * mutton for one would be minting meat that no player could ever get by
     * doing the same thing by hand. The highland people keep goats for the milk
     * and the company. A wolf is the same case for a different reason: the orcs
     * pen wolves, and nobody eats the dog.
     */
    public record Yield(String meat, int meatPerCull, String clip, int clipPerCull) {

        /** Nothing at all — a beast that is kept rather than eaten. */
        public static final Yield NONE = new Yield(null, 0, null, 0);

        public boolean isWorthCulling() {
            return meatPerCull > 0 || clipPerCull > 0;
        }
    }

    private static final Map<String, Yield> YIELDS = new LinkedHashMap<>();

    static {
        YIELDS.put("minecraft:cow",
                new Yield(TownStores.MEAT, 2, TownStores.LEATHER, 1));
        YIELDS.put("minecraft:pig",
                new Yield(TownStores.MEAT, 2, null, 0));
        // One wool, because an unshorn sheep drops its fleece when it dies. It
        // is the whole of the town's wool supply and it is deliberately narrow:
        // shearing a live sheep is a thing a shepherd's hands could do and the
        // clock could not, and a yield only one fidelity can produce is exactly
        // the kind of thing this ledger exists to refuse.
        YIELDS.put("minecraft:sheep",
                new Yield(TownStores.MEAT, 1, TownStores.WOOL, 1));
        YIELDS.put("minecraft:chicken",
                new Yield(TownStores.MEAT, 1, TownStores.FEATHERS, 1));
        YIELDS.put("minecraft:rabbit",
                new Yield(TownStores.MEAT, 1, TownStores.LEATHER, 1));
        YIELDS.put("minecraft:goat", Yield.NONE);
        YIELDS.put("minecraft:wolf", Yield.NONE);
    }

    private Herd() {
    }

    // --- the drawing, which everybody has to agree about -----------------------

    /**
     * How many pens a compound of this culture's actually gets.
     *
     * <p>A people that keeps five beasts wants five pens, but the plot was
     * staked in the catalog before anybody asked — so a sixth pen would be a
     * fence through the neighbor's wall rather than a bigger farm. The clamp is
     * the placer's and is reproduced here rather than the other way round,
     * because everything that wants to know where a pen is has to get the same
     * answer and only one of those callers can see a block.
     */
    public static int penCount(Culture culture) {
        int room = (compoundSize().depth() - 1) / (PEN_DEPTH + 1);
        return Math.min(room, Math.max(1, culture.penCount()));
    }

    /** What the catalog reserves for a compound. */
    public static BuildingSizes.Size compoundSize() {
        return BuildingSizes.of("animal_farm");
    }

    /** How deep the fenced ground actually runs, for {@code pens} pens. */
    public static int compoundDepth(int pens) {
        return pens * PEN_DEPTH + pens + 1;
    }

    /**
     * The rows one pen occupies, as offsets from the compound's own post.
     *
     * <p>Index 0 is the pen nearest the post. The first row is the one just
     * inside the leading fence, and there are {@link #PEN_DEPTH} of them.
     */
    public static int penFirstRow(int pens, int index) {
        return -(compoundDepth(pens) / 2) + 1 + index * (PEN_DEPTH + 1);
    }

    /** The columns inside the fence, as offsets: the ring itself is excluded. */
    public static int penHalfWidth() {
        return compoundSize().width() / 2 - 1;
    }

    /**
     * Which beasts this culture keeps, in pen order.
     *
     * <p>Clipped to the pens that actually fit, so a people who outgrew their
     * own plot lose the last kind rather than getting a pen that is not there.
     */
    public static List<String> kindsOf(Culture culture) {
        List<String> penned = culture.pennedAnimals();
        int pens = penCount(culture);
        return penned.size() <= pens ? penned : List.copyOf(penned.subList(0, pens));
    }

    /** The distinct species a culture keeps, each named once however many pens it has. */
    public static List<String> speciesOf(Culture culture) {
        List<String> out = new ArrayList<>();
        for (String kind : kindsOf(culture)) {
            if (!out.contains(kind)) {
                out.add(kind);
            }
        }
        return out;
    }

    /** How many pens this culture gives one species — the mire goblins keep two of fowl. */
    public static int pensFor(Culture culture, String species) {
        int pens = 0;
        for (String kind : kindsOf(culture)) {
            if (kind.equals(species)) {
                pens++;
            }
        }
        return pens;
    }

    // --- the ledger ------------------------------------------------------------

    /** What killing one of these gives, or {@link Yield#NONE} for anything unlisted. */
    public static Yield yieldOf(String species) {
        return YIELDS.getOrDefault(species, Yield.NONE);
    }

    /** The ledger word for a species' head count: {@code minecraft:cow} is {@code herd:cow}. */
    public static String headKey(String species) {
        return HEAD + bareName(species);
    }

    /** Whether a ledger word is a head count, so a reader can print it as one. */
    public static boolean isHeadKey(String resource) {
        return resource != null && resource.startsWith(HEAD);
    }

    /**
     * Whether a ledger word is bookkeeping nobody should be shown.
     *
     * <p>The unborn remainder and the stocked flag are how the arithmetic
     * remembers where it was, not things the compound holds. A panel that listed
     * "unborn:cow 640" beside four cows would be reporting a fraction of a calf
     * as if it were livestock.
     */
    public static boolean isInternal(String resource) {
        return resource != null && (resource.startsWith(UNBORN) || STOCKED.equals(resource));
    }

    /** "herd:cow" reads as "cow" on a panel. */
    public static String headWord(String resource) {
        return isHeadKey(resource) ? resource.substring(HEAD.length()) : resource;
    }

    /** Head of this species standing in this compound's pens. */
    public static int head(Building farm, String species) {
        return farm.stores().get(headKey(species));
    }

    /** Everything this compound holds on the hoof, species to head, in pen order. */
    public static Map<String, Integer> census(Building farm, Culture culture) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String species : speciesOf(culture)) {
            out.put(species, head(farm, species));
        }
        return out;
    }

    /** Head across every compound in the town. */
    public static int totalHead(Settlement settlement) {
        int total = 0;
        for (Building farm : settlement.buildingsWithRole(BuildingRole.ANIMAL_FARM)) {
            for (Map.Entry<String, Integer> line : farm.stores().all().entrySet()) {
                if (isHeadKey(line.getKey())) {
                    total += line.getValue();
                }
            }
        }
        return total;
    }

    /** The most head of this species the compound's pens will hold. */
    public static int capacity(Culture culture, String species) {
        return pensFor(culture, species) * HEAD_PER_PEN;
    }

    /** Whether the breeding pairs have ever been driven in. */
    public static boolean isStocked(Building farm) {
        return farm.stores().get(STOCKED) > 0;
    }

    /**
     * Drive in the first pair of each kind.
     *
     * <p>Runs once per compound, either at the moment a seeded town is written
     * into existence or on the first step after one is built. Idempotent by the
     * flag rather than by the head count, so a herd that was culled to nothing
     * on purpose stays at nothing — a compound is restocked by a shepherd, not
     * by this.
     */
    public static void stock(Building farm, Culture culture) {
        if (isStocked(farm)) {
            return;
        }
        farm.stores().set(STOCKED, 1);
        for (String species : speciesOf(culture)) {
            farm.stores().set(headKey(species),
                    Math.min(STARTER_HEAD, capacity(culture, species)));
        }
    }

    /**
     * Head this compound could still take of a species.
     *
     * <p>What the pens have room for, and nothing to do with feed: a compound
     * with no grain simply never gets here.
     */
    public static int room(Building farm, Culture culture, String species) {
        return Math.max(0, capacity(culture, species) - head(farm, species));
    }

    /**
     * How far the next head of this species has come, in thousandths.
     *
     * <p>A remainder, for the same reason {@link Field} keeps hundredths: a fed
     * pair is worth three thousandths of a grown beast per step at the shipped
     * settings, and a ledger that could only count whole cows would round that
     * to nothing forever.
     */
    public static int unborn(Building farm, String species) {
        return farm.stores().get(UNBORN + bareName(species));
    }

    /**
     * Feed a species' breeding pairs for one step, and grow the calf.
     *
     * <p>The tending action, and the twin of {@link Field#tend}: this is what a
     * shepherd's hands do, done as arithmetic where there are no hands. Every
     * turn spent here is a beast fed, two turns make a pair, and a fed pair
     * advances at the vanilla pace of {@link #BREEDING_TICKS}.
     *
     * <p>Grain is charged at the birth and not at the mouthful, so a compound
     * that runs dry mid-calf keeps the part-grown remainder and stalls. It does
     * not lose it, and it does not get the beast either.
     *
     * @param turns shepherd-turns spent on this species this step
     * @return head actually born
     */
    public static int feed(Building farm, Culture culture, String species,
                           int turns, SimContext ctx) {
        return feed(farm, culture, species, turns, ctx.settings().simIntervalTicks());
    }

    /**
     * The same feeding, for a caller with no step around it.
     *
     * <p>The view layer runs on the entity tick and has no {@link SimContext} to
     * hand over — and the one thing the context was wanted for is how long a step
     * is, which is a setting the platform layer can read for itself. Taking the
     * interval rather than the context is what lets the shepherd's own hands
     * advance the very same ledger the clock does.
     */
    public static int feed(Building farm, Culture culture, String species,
                           int turns, int simIntervalTicks) {
        int pairsHere = Math.min(head(farm, species) / 2, turns / TURNS_PER_PAIR);
        if (pairsHere <= 0 || room(farm, culture, species) <= 0) {
            return 0;
        }
        if (!farm.stores().has(TownStores.GRAIN, FEED_PER_HEAD)) {
            return 0;   // nothing to feed them with; nothing breeds
        }
        int grown = unborn(farm, species)
                + pairsHere * PER_HEAD * Math.max(1, simIntervalTicks) / BREEDING_TICKS;
        int born = 0;
        while (grown >= PER_HEAD && born < room(farm, culture, species)
                && farm.stores().take(TownStores.GRAIN, FEED_PER_HEAD)) {
            grown -= PER_HEAD;
            born++;
            farm.stores().add(headKey(species), 1);
        }
        farm.stores().set(UNBORN + bareName(species), Math.min(grown, PER_HEAD - 1));
        return born;
    }

    /**
     * Take one beast off the ledger and put what it was worth on the shelf.
     *
     * <p>The hook a real kill calls, at the moment the shepherd swings — and
     * where nobody is watching, what the clock calls instead. One head, whoever
     * counted it, which is what stops the ledger filling up behind a watched pen
     * and paying out the moment the player walks away.
     *
     * <p>The meat is <em>not</em> credited here, and that is the whole of the
     * difference between the two fidelities. Where somebody is watching, the
     * beast drops what vanilla says it drops and the shepherd gathers it off the
     * ground; where nobody is, {@link #cullOnTheClock} pays the table's average
     * in its place. Minting a joint here as well would pay the town twice for
     * the same cow.
     *
     * @return whether there was a beast to take
     */
    public static boolean cull(Building farm, String species) {
        if (head(farm, species) <= BREEDING_PAIR) {
            return false;
        }
        farm.stores().add(headKey(species), -1);
        return true;
    }

    /** A cull nobody saw: the head comes off and the table's average goes on the shelf. */
    public static boolean cullOnTheClock(Building farm, String species) {
        if (!cull(farm, species)) {
            return false;
        }
        credit(farm, yieldOf(species));
        return true;
    }

    /** Put a beast's worth on the compound's shelf, each good against its own ceiling. */
    public static void credit(Building farm, Yield yield) {
        if (yield.meat() != null && yield.meatPerCull() > 0) {
            farm.stores().addCapped(yield.meat(), yield.meatPerCull(), FARM_MEAT_CAP);
        }
        if (yield.clip() != null && yield.clipPerCull() > 0) {
            farm.stores().addCapped(yield.clip(), yield.clipPerCull(), FARM_CLIP_CAP);
        }
    }

    /**
     * Whether the shepherd would take a beast out of this pen right now.
     *
     * <p>Two reasons and no others. The pen is <strong>full</strong>, which is
     * what a shepherd does about a full pen and is the only thing that keeps the
     * entity count off a cliff; or the town is <strong>starving</strong>, which
     * is what a herd is for. Never past {@link #BREEDING_PAIR}, and never for a
     * beast that yields nothing — killing a goat feeds nobody and the ledger
     * will not pretend otherwise.
     */
    public static boolean wantsCulling(Building farm, Culture culture, String species,
                                       boolean starving) {
        if (head(farm, species) <= BREEDING_PAIR || !yieldOf(species).isWorthCulling()) {
            return false;
        }
        return head(farm, species) >= capacity(culture, species) || starving;
    }

    /** Raw meat standing on the town's compounds, waiting for somebody to carry it in. */
    public static int meatOnFarms(Settlement settlement) {
        int total = 0;
        for (Building farm : settlement.buildingsWithRole(BuildingRole.ANIMAL_FARM)) {
            total += farm.stores().get(TownStores.MEAT);
        }
        return total;
    }

    /** Raw meat that has reached the oven and not yet been roasted. */
    public static int meatAtTheOven(Settlement settlement) {
        Building oven = FoodPlanner.bakery(settlement);
        return oven == null ? 0 : oven.stores().get(TownStores.MEAT);
    }

    /**
     * What the town has clipped off its beasts, wherever it happens to be sitting.
     *
     * <p>The town's pool plus the shelves the pool cannot see. {@code
     * Settlement.stores()} sums the stores and the loose pile and nothing else,
     * which is right for goods a builder fetches and wrong for a fleece that is
     * still sitting in the sheep pen — so the compounds are added by hand, and
     * the stores are not added twice.
     */
    public static int clipStock(Settlement settlement, String good) {
        int total = settlement.stores().get(good);
        for (Building building : settlement.buildings()) {
            if (building.hasStores() && !building.isStore()) {
                total += building.stores().get(good);
            }
        }
        return total;
    }

    // --- one step --------------------------------------------------------------

    /**
     * One step of the pens, for the whole town.
     *
     * <p>Called from {@link FoodPlanner#advance} between the harvest and the
     * oven, because that is where it belongs in the chain: the grain the pens
     * eat was cut this morning and the meat they give goes to the same fire the
     * bread does.
     *
     * <p>Every compound is stocked if it never has been, fed by whatever
     * shepherds the town has, and culled where a pen has filled or the town is
     * hungry. A watched compound is fed and culled by hand as well — see
     * {@code ShepherdWorker} — and those turns come off this step's allowance
     * before the clock spends any of it, exactly the way {@code growHarvest}
     * settles up with a watched field.
     *
     * <p>The clock used to stand aside for a watched compound altogether, and
     * that is the pens' share of the fault that starved the 2026-09-19
     * playtest's towns: a shepherd who cannot reach the gate is not a reason for
     * a full pen to stop being full. A pen is worth what it holds, and it holds
     * the same whether or not anybody is leaning on the fence.
     */
    public static void advance(Settlement settlement, SimContext ctx, boolean starving) {
        List<Building> compounds = settlement.buildingsWithRole(BuildingRole.ANIMAL_FARM);
        if (compounds.isEmpty()) {
            return;
        }
        Culture culture = Culture.of(settlement.cultureId());
        int shepherds = JobPlanner.count(settlement, Profession.SHEPHERD);
        int turns = shepherds * TURNS_PER_SHEPHERD_PER_STEP;
        for (Building farm : compounds) {
            stock(farm, culture);
            farm.setWatched(settlement.isWatched(ctx, farm.origin()));
            // Turns the shepherds themselves have already taken at this compound
            // since the last step -- see ShepherdWorker, which culls and feeds
            // through these same two methods and books the turn here.
            int byHand = farm.takeHandYield();
            if (turns <= 0) {
                farm.creditByHand(byHand);   // carried, not written off
                continue;                    // there are no hands at all
            }
            // One compound can only take so many shepherds, however many the
            // town has -- the same rule the fields run under.
            int here = Math.min(turns, TURNS_PER_SHEPHERD_PER_STEP);
            turns -= here;
            // And what the real shepherds already spent comes off it, so the pens
            // are worth the same whether or not anybody is leaning on the gate.
            // Turns taken over the allowance are carried into the next step
            // rather than dropped, the way the fields and the claim carry theirs.
            int spent = Math.min(byHand, here);
            farm.creditByHand(byHand - spent);
            here -= spent;
            List<String> kept = speciesOf(culture);
            // The culling first, across every pen, because a full pen is the one
            // thing at a compound that will not wait: it is what stops a herd
            // doubling until the server is ticking two hundred mobs in a yard,
            // and a shepherd who spent his whole day feeding the cattle while
            // the sheep pen overflowed would be doing the wrong job well.
            for (String species : kept) {
                if (here <= 0) {
                    break;
                }
                if (wantsCulling(farm, culture, species, starving)
                        && farm.stores().get(TownStores.MEAT) < FARM_MEAT_CAP
                        && cullOnTheClock(farm, species)) {
                    here--;   // a kill is a turn like any other
                }
            }
            // Then the feeding, starting at a different pen each step. That is
            // not a nicety either: one shepherd gets four turns and a pair costs
            // two, so a loop that always began at the first pen would feed the
            // cattle and the sheep every step of the town's life and never once
            // feed the fowl. The chickens are at the far end of the compound,
            // not at the end of time.
            for (int i = 0; i < kept.size() && here >= TURNS_PER_PAIR; i++) {
                feed(farm, culture,
                        kept.get((int) Math.floorMod(ctx.step() + i, kept.size())),
                        TURNS_PER_PAIR, ctx);
                here -= TURNS_PER_PAIR;
            }
        }
    }

    /**
     * One step at the fire: raw meat in, meals out.
     *
     * <p>The twin of {@code FoodPlanner.bake} and deliberately a second method
     * rather than a branch inside it: bread and roast are two queues at one
     * hearth, and a town short of one is not short of the other. Only the
     * bakery-of-record roasts, for the same reason only it bakes.
     */
    public static void roast(Settlement settlement) {
        Building oven = FoodPlanner.bakery(settlement);
        if (oven == null) {
            return;
        }
        int meat = oven.stores().get(TownStores.MEAT);
        if (meat <= 0) {
            return;
        }
        int room = FoodPlanner.granaryCapacity(settlement) - settlement.foodStock();
        int cooking = Math.min(Math.min(meat, ROAST_PER_STEP), room / MEALS_PER_JOINT);
        if (cooking <= 0) {
            return;   // a full larder spoils nothing; the joint waits on the shelf
        }
        oven.stores().take(TownStores.MEAT, cooking);
        settlement.stores().add(TownStores.FOOD, cooking * MEALS_PER_JOINT);
    }

    /** {@code minecraft:cow} is {@code cow}; a bare word is left alone. */
    private static String bareName(String species) {
        int colon = species.indexOf(':');
        return colon < 0 ? species : species.substring(colon + 1);
    }
}
