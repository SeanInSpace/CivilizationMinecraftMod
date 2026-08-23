package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Foods;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a resource actually is, as an item.
 *
 * <p>The town's ledger counts bare words — {@code "wood"}, {@code "stone"} —
 * while everything a player can hold is a namespaced id like
 * {@code "minecraft:oak_log"}. Until now nothing joined the two. Settlers
 * already carried real bread ({@link Foods#PROVISION}) because food had been
 * made real; timber and stone stayed numbers, so a charter could announce four
 * hundred and eighty wood and there was not a single log anywhere in the world.
 * This is the dictionary that lets the ledger be spent, stored and handed over
 * as things rather than totals.
 *
 * <p>The mapping is deliberately asymmetric, because the real one is:
 * <ul>
 *   <li><strong>One way out.</strong> A resource has exactly one canonical item
 *       — what the town hands you and what fills its chests. Otherwise a store
 *       holding "wood" would have to decide between logs and planks every time
 *       anything read it.</li>
 *   <li><strong>Many ways in.</strong> Any log, any planks, any of the stone
 *       family counts toward the same resource, so a player donating what they
 *       happen to be carrying is not turned away on a technicality.</li>
 * </ul>
 *
 * <p>Pure strings and no Minecraft imports, which is what lets it live in the
 * simulation and be tested without a game. The platform layer already has a
 * richer, tag-based classifier for blocks in the world
 * ({@code WarehouseBlock.resourceOf}); this is the half of the job that has to
 * work where tags do not exist.
 */
public final class Resources {

    /**
     * What a store building keeps.
     *
     * <p>Bulk building materials only, and the omissions are deliberate. Gear
     * does not stack, so a store of sixty-four tools would want sixty-four
     * slots on its own. Food has its own economy — granary, stalls, pantries
     * and haulers — and belongs there rather than in the timber store. What is
     * left is exactly what a builder walks to a storehouse to fetch.
     *
     * <p>Lives here rather than on the container that shows it, because which
     * goods a store holds is a fact about the settlement, not about a block
     * entity. The chest reads this list; it does not own it.
     */
    public static final List<String> STORED = List.of(
            TownStores.WOOD, TownStores.STONE, TownStores.SAPLINGS, TownStores.IRON);

    /** The item a resource is paid out in, keyed by ledger word. */
    private static final Map<String, String> CANONICAL = new LinkedHashMap<>();

    /** How many of the canonical item sit in one stack. */
    private static final Map<String, Integer> STACK = new LinkedHashMap<>();

    /**
     * The stone family, named rather than matched on substring.
     *
     * <p>A substring test for "stone" is the obvious shortcut and it is wrong:
     * redstone and glowstone are not masonry, and a town that accepted them as
     * building stone would quietly turn a player's redstone into a wall.
     */
    private static final Set<String> STONE_FAMILY = Set.of(
            "minecraft:stone", "minecraft:cobblestone", "minecraft:smooth_stone",
            "minecraft:stone_bricks", "minecraft:mossy_cobblestone",
            "minecraft:deepslate", "minecraft:cobbled_deepslate",
            "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
            "minecraft:tuff", "minecraft:basalt", "minecraft:blackstone",
            "minecraft:sandstone", "minecraft:calcite", "minecraft:dripstone_block");

    /** Endings that make an item timber, whatever wood it was cut from. */
    private static final List<String> WOOD_SUFFIXES =
            List.of("_log", "_wood", "_planks", "_stem", "_hyphae");

    static {
        // These are the items the overview screen already draws for each
        // resource, so the ledger, the icon and the chest all agree. Timber is
        // the one deliberate exception: the screen draws planks, but a lumber
        // camp fells logs and the storehouse already sells logs, so a log is
        // what the store actually holds.
        put(TownStores.WOOD, "minecraft:oak_log", 64);
        put(TownStores.STONE, "minecraft:cobblestone", 64);
        put(TownStores.FOOD, Foods.PROVISION, 64);
        put(TownStores.SAPLINGS, "minecraft:oak_sapling", 64);
        put(TownStores.IRON, "minecraft:iron_ingot", 64);
        // Gear does not stack. A store of thirty-two weapons is thirty-two
        // slots, which is the difference between a chest that fits and one that
        // silently drops the overflow.
        put(TownStores.TOOLS, "minecraft:iron_pickaxe", 1);
        put(TownStores.WEAPONS, "minecraft:iron_sword", 1);
        put(TownStores.ARMOUR, "minecraft:iron_chestplate", 1);
    }

    private static void put(String resource, String itemId, int stack) {
        CANONICAL.put(resource, itemId);
        STACK.put(resource, stack);
    }

    private Resources() {
    }

    /** Every resource this dictionary can turn into items, in a stable order. */
    public static List<String> known() {
        return List.copyOf(CANONICAL.keySet());
    }

    /**
     * The item a resource is paid out in.
     *
     * @return an item id, or null for a resource nothing has taught us to hand over
     */
    public static String itemFor(String resource) {
        return resource == null ? null : CANONICAL.get(resource);
    }

    /** How many of that resource fit in one stack. Gear is one. */
    public static int stackSize(String resource) {
        return STACK.getOrDefault(resource, 64);
    }

    /**
     * Which resource an item counts as, or null if the town has no use for it.
     *
     * <p>Food defers to {@link Foods}, which already owns the question of what
     * is edible and is where a datapack will eventually answer it — asking it
     * here keeps one nutrition table rather than two lists that drift.
     */
    public static String resourceOf(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        if (Foods.isFood(id)) {
            return TownStores.FOOD;
        }
        // Before the timber rules: a sapling is not a log, and "oak_sapling"
        // would otherwise have to be excluded from every one of them.
        if (id.endsWith("_sapling")) {
            return TownStores.SAPLINGS;
        }
        for (String suffix : WOOD_SUFFIXES) {
            if (id.endsWith(suffix)) {
                return TownStores.WOOD;
            }
        }
        if (STONE_FAMILY.contains(id)) {
            return TownStores.STONE;
        }
        if (id.equals("minecraft:iron_ingot")) {
            return TownStores.IRON;
        }
        return null;
    }
}
