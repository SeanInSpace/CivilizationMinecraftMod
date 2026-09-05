package com.kingdoms.sim.economy;

import com.kingdoms.sim.person.Foods;

import java.util.Map;

/**
 * What a thing is worth in coin.
 *
 * <p>The town's whole opinion about value, in one table, for the same reason the
 * danger table is one table: the numbers are arguable and ought to be arguable
 * in one place rather than scattered through the planners that read them.
 *
 * <p><strong>Only finds have a price here.</strong> A settlement's own produce —
 * timber, stone, iron, grain — is not bought and sold, because under this
 * culture's arrangement the work belongs to the town already. Nobody sells the
 * town a log it paid them to cut. What a person owns is what they came across:
 * a sword dropped by something the guards killed, armor off a skeleton, a
 * diamond turned up while digging a cellar. That is the property this table
 * prices, and the market is where it changes hands.
 *
 * <p>Rations are deliberately worth nothing. Food issued from the granary is the
 * town's, handed out to be eaten; a settler who could sell their dinner back to
 * the town at a profit would do nothing else.
 */
public final class Valuation {

    private Valuation() {
    }

    /** Worth nothing to the market — most things, including anything issued. */
    public static final int WORTHLESS = 0;

    /**
     * What each kind of find is worth.
     *
     * <p>Scale: <b>1</b> is a day's wage for one settler. <b>10</b> is worth
     * walking across town for. <b>60</b> and up is the kind of thing a whole
     * settlement talks about.
     */
    private static final Map<String, Integer> PRICES = Map.ofEntries(
            // Metal a town cannot make, in the form it is hardest to make.
            Map.entry("minecraft:diamond", 90),
            Map.entry("minecraft:diamond_sword", 140),
            Map.entry("minecraft:diamond_pickaxe", 140),
            Map.entry("minecraft:diamond_axe", 130),
            Map.entry("minecraft:diamond_helmet", 120),
            Map.entry("minecraft:diamond_chestplate", 180),
            Map.entry("minecraft:diamond_leggings", 160),
            Map.entry("minecraft:diamond_boots", 110),
            Map.entry("minecraft:netherite_ingot", 260),
            Map.entry("minecraft:emerald", 40),
            Map.entry("minecraft:gold_ingot", 24),
            Map.entry("minecraft:iron_ingot", 12),

            // Arms and armor, which a settlement can eventually forge but is
            // always glad to be handed.
            Map.entry("minecraft:iron_sword", 30),
            Map.entry("minecraft:iron_helmet", 26),
            Map.entry("minecraft:iron_chestplate", 44),
            Map.entry("minecraft:iron_leggings", 38),
            Map.entry("minecraft:iron_boots", 24),
            Map.entry("minecraft:shield", 18),
            Map.entry("minecraft:bow", 16),
            Map.entry("minecraft:crossbow", 22),

            // Oddments worth something to somebody.
            Map.entry("minecraft:ender_pearl", 35),
            Map.entry("minecraft:blaze_rod", 45),
            Map.entry("minecraft:experience_bottle", 20),
            Map.entry("minecraft:enchanted_book", 70),
            Map.entry("minecraft:golden_apple", 55),
            Map.entry("minecraft:name_tag", 30),
            Map.entry("minecraft:saddle", 28),
            Map.entry("minecraft:music_disc_cat", 25),
            Map.entry("minecraft:totem_of_undying", 200));

    /**
     * What one of these fetches, or {@link #WORTHLESS} for anything the town
     * would not pay for.
     *
     * <p>Food is excluded before the table is even consulted, so a golden apple
     * priced above is still refused if the town counts it as a ration. Two rules
     * that could disagree, made to agree by ordering them.
     */
    public static int priceOf(String itemId) {
        if (itemId == null || Foods.nutrition(itemId) > 0) {
            return WORTHLESS;
        }
        return PRICES.getOrDefault(itemId, WORTHLESS);
    }

    /** Whether the town would buy this at all. */
    public static boolean isWorthSelling(String itemId) {
        return priceOf(itemId) > WORTHLESS;
    }

    /** Every priced item, for tests and for anything that wants to show the list. */
    public static Map<String, Integer> prices() {
        return PRICES;
    }
}
