package com.kingdoms.sim.settlement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Everything a town owns, by name.
 *
 * <p>Replaces the handful of hardcoded {@code int} fields the settlement used to
 * carry. A map rather than fields because the list of things a town can hold is
 * exactly the sort of thing that belongs in a datapack later — adding leather, or
 * a culture that trades in salt, should not be a code change.
 *
 * <p>Amounts never go negative, and {@link #take} is all-or-nothing: a town either
 * pays for something or does not get it. That is the whole basis of supply being
 * limited rather than decorative.
 */
public final class TownStores implements Stock {

    /** Well-known ids. Not exhaustive — anything may be stored under any name. */
    public static final String FOOD = "food";
    public static final String WOOD = "wood";
    public static final String STONE = "stone";
    public static final String SAPLINGS = "saplings";

    /**
     * Earth, dug and kept.
     *
     * <p>A town that levels the ground it builds on has to get the earth from
     * somewhere and put the spoil somewhere, and until now both ends were
     * imaginary: foundations were dug and the dirt vanished. Making it a stock
     * is what turns "flatten the site" from a wish into something a settlement
     * can afford or not afford — which is the whole difference between a rule
     * and a decision.
     */
    public static final String EARTH = "earth";
    public static final String IRON = "iron";
    public static final String TOOLS = "tools";
    public static final String WEAPONS = "weapons";
    public static final String ARMOUR = "armour";

    /**
     * What a founding party carries in: the settlers, the loaves in their pockets,
     * and the materials for the first buildings.
     *
     * <p>All four together because they are one kit and only mean anything sized
     * against each other. A playtest watched a town spend its timber on the hall
     * and starve idle long before it was big enough to want a farm, and nothing
     * anywhere said what the kit was supposed to cover. {@code FoundingEconomicsTest}
     * now holds that arithmetic; the charter item hands the kit out.
     *
     * <p><strong>Why the timber outweighs the stone.</strong> The kit used to be
     * 256 of each, sized when a town's first act was a hall and a house. The
     * founding rework replaced that with the stage programs, and a later playtest
     * watched the party build its cache, start its bunkhouse, run dry and divert
     * into a mine it could not finish either. The kit was 1:1 and building burns
     * 2:1 — {@link BuildPlanner#WOOD_PER_WORK} against
     * {@link BuildPlanner#STONE_PER_WORK} — so a party carrying equal parts spends
     * its last log with half its stone untouched, and every log short of the
     * programme is a building nobody finishes.
     *
     * <p>The programme is what these have to cover: the paid entries of CAMP,
     * HOMESTEAD and FORTIFIED, which is everything {@code StagePlanner.PROGRAMS}
     * names except the farm and the lumber camp (free, because
     * {@link BuildPlanner#PRODUCER_OF} exempts a town's way out of a shortage from
     * the shortage). That runs to 105 builder-steps, and a party of four is billed
     * 116 — the last step of every building is charged for the whole crew whether
     * or not it needed one — so 464 timber and 232 stone.
     *
     * <p>The timber's reserve on top of that is thin, and not by choice.
     * {@link LumberPlanner#BASE_WOOD_STORAGE} is 512: that is every log a town
     * without a storehouse can hold, so a kit sized much above this one arrives at
     * its own ceiling and the lumber camp built at FORTIFIED fells into a full
     * store — {@code LumberPlanner.wantsMoreTimber} is false and the axes stop
     * before they start. The founding programme costs 464 of a possible 512, so
     * the whole design has forty-eight of slack in it and this kit takes sixteen
     * of that as its reserve, leaving thirty-two of headroom for the first felled
     * log. Widening the cushion means widening the ceiling, not the kit.
     *
     * <p>The total is up from 512 to 768, and deliberately: the old kit was not
     * badly divided, it was too small, and no reshuffle of 512 pays for 696.
     *
     * <p><strong>Why the stone keeps the fatter reserve</strong> even though it is
     * the cheaper half. Timber has a rescue inside the founding itself — FORTIFIED
     * raises the lumber camp first and free, so a town that runs low on logs is
     * already felling, and any timber the estimate failed to foresee is felled
     * rather than carried. Stone has no such rescue: no stage programme orders a
     * mine at all, so the only mine a founding party ever gets is the one
     * {@link BuildPlanner#requestProducer} shoves to the head of the queue the
     * moment the stores run dry — the whole party diverted into an unplanned
     * build, which is precisely what the playtest watched. Stone is also what the
     * second fidelity spends fastest: a build somebody is standing over is billed
     * by the platform layer at one unit per block laid, and a wall is masonry
     * however the estimate prices it. Only the estimate can be checked from this
     * module, which has never heard of a block, so the hedge is carried here
     * instead.
     *
     * <p>The provisions are left alone, and the arithmetic for that is
     * {@code whatTheSettlersPackedOutlastsTheRoadToTheProgramsFirstFarm}: the
     * programme puts the farm fourth, so a lone builder reaches a standing field
     * in 95 steps and the loaves in one settler's pockets feed them for 120. The
     * old road — hall, three houses, granary, then a field — ran to some two
     * hundred, and is the reason the larder looks as generous as it does.
     *
     * <p><strong>The charter is not the only thing that pays this.</strong>
     * {@code ExpansionPlanner.found} makes the kit the dowry a parent town owes
     * every daughter, and gates the whole expansion on having it banked — so
     * these figures are also the price of a second settlement, up here by nearly
     * half. A TOWN has long since built its storehouse and can hold plenty, but a
     * town that loses one is a town that can no longer afford to expand, and the
     * next retune should weigh that as well as the founding.
     *
     * <p>The party is four for two reasons that pull against each other: enough
     * hands to build, and few enough to stay under
     * {@link RaidPlanner#MIN_POPULATION_FOR_RAIDS} — a party that arrived already
     * worth raiding would meet a war band before it had walls. A daughter party
     * may be up to {@code ExpansionPlanner.FOUNDING_PARTY_MAX}, and the kit is
     * priced against that crew too.
     */
    /*
     * Up again, from 480/288 to 704/384, and for one reason: the buildings the
     * kit pays for got bigger.
     *
     * A hall went from seven by seven to thirteen by eleven and a house from
     * five to nine, because every street in the mod was spaced for buildings
     * roughly twice the size of the ones being put on it. The programme those
     * two head is what the kit exists to buy, and its bill went from 446 timber
     * to 624 -- so the old kit bought about three quarters of a founding and
     * then stopped, with the party standing on an unfinished hall.
     *
     * Nothing here was reasoned differently; the same reasoning was applied to
     * a bigger number. Everything the paragraphs above say about the shape of
     * the kit -- why stone keeps the fatter reserve, why the provisions are
     * priced against the road to the first field -- still holds.
     *
     * The ceilings moved with it. LumberPlanner.BASE_WOOD_STORAGE was 512 and
     * Founding clamps the kit to it, so a kit of 704 would have been quietly
     * cut to 512 on arrival and the party would have been short without
     * anything saying so.
     */
    public static final int FOUNDING_WOOD = 704;
    public static final int FOUNDING_STONE = 384;
    public static final int FOUNDING_SETTLERS = 4;
    /*
     * Nine loaves, not eight. One provision feeds a settler fifteen steps, and
     * the hall and the first house now want a hundred and twenty-five of them
     * from a lone builder -- five more than eight loaves last. A settler who
     * runs out of food while raising the roof they will live under falls back
     * on the granary, which at that point holds what the charter granted and
     * nothing else.
     */
    public static final int FOUNDING_PROVISIONS_EACH = 9;

    private final Map<String, Integer> amounts = new LinkedHashMap<>();

    /**
     * The stock a brand-new settlement starts with.
     *
     * <p>Not generosity — a bootstrap. Building costs materials now, and a town
     * with nothing could not raise the very buildings that let it produce any.
     * The rest of the way out of that hole is {@code requestProducer}: a town that
     * runs dry goes and builds the camp or the mine that fixes it.
     */
    public static TownStores founding(int food) {
        TownStores out = new TownStores();
        out.set(FOOD, food);
        out.set(WOOD, FOUNDING_WOOD);
        out.set(STONE, FOUNDING_STONE);
        return out;
    }

    public int get(String resource) {
        return amounts.getOrDefault(resource, 0);
    }

    public void set(String resource, int amount) {
        Objects.requireNonNull(resource, "resource");
        if (amount <= 0) {
            amounts.remove(resource);
        } else {
            amounts.put(resource, amount);
        }
    }

    /** Adds to a store, never below zero. Returns the new total. */
    public int add(String resource, int amount) {
        int now = Math.max(0, get(resource) + amount);
        set(resource, now);
        return now;
    }

    /** Adds up to a ceiling, and reports how much actually fit. */
    public int addCapped(String resource, int amount, int ceiling) {
        int room = Math.max(0, ceiling - get(resource));
        int taken = Math.min(Math.max(0, amount), room);
        add(resource, taken);
        return taken;
    }

    public boolean has(String resource, int amount) {
        return get(resource) >= amount;
    }

    /**
     * Spends from a store, all or nothing.
     *
     * @return true if the town could pay
     */
    public boolean take(String resource, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (!has(resource, amount)) {
            return false;
        }
        set(resource, get(resource) - amount);
        return true;
    }

    /** Spends as much as is there, and reports how much that was. */
    public int takeUpTo(String resource, int amount) {
        int taken = Math.min(Math.max(0, amount), get(resource));
        set(resource, get(resource) - taken);
        return taken;
    }

    public Map<String, Integer> all() {
        return Collections.unmodifiableMap(amounts);
    }

    public void restore(Map<String, Integer> saved) {
        amounts.clear();
        saved.forEach(this::set);
    }

    @Override
    public String toString() {
        return amounts.toString();
    }
}
