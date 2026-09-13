package com.civilization.sim.person;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What counts as food, and how far it goes.
 *
 * <p>Hunger runs 0–99 and climbs one a step, so a loaf at 15 undoes fifteen
 * steps of appetite. Anything not listed is simply not edible — a settler handed
 * a stone pickaxe will carry it and starve beside it.
 *
 * <p>Ids are plain strings so the simulation stays loader-free. The obvious next
 * step is loading this table from a datapack alongside the building catalog.
 */
public final class Foods {

    /** What the town's stores hand out. */
    public static final String PROVISION = "minecraft:bread";

    /** What a field yields before anyone bakes it. */
    public static final String GRAIN = "minecraft:wheat";

    private static final Map<String, Integer> NUTRITION = new LinkedHashMap<>();

    static {
        NUTRITION.put("minecraft:golden_apple", 30);
        NUTRITION.put("minecraft:cooked_beef", 23);
        NUTRITION.put("minecraft:cooked_porkchop", 23);
        NUTRITION.put("minecraft:cooked_chicken", 18);
        NUTRITION.put("minecraft:bread", 15);
        NUTRITION.put("minecraft:baked_potato", 13);
        NUTRITION.put("minecraft:apple", 10);
        NUTRITION.put("minecraft:carrot", 8);
        NUTRITION.put("minecraft:beetroot", 6);
        NUTRITION.put("minecraft:wheat", 5);
        NUTRITION.put("minecraft:potato", 4);
        NUTRITION.put("minecraft:sweet_berries", 4);
    }

    private Foods() {
    }

    /** How much hunger one of these undoes; 0 for anything inedible. */
    public static int nutrition(String itemId) {
        return NUTRITION.getOrDefault(itemId, 0);
    }

    public static boolean isFood(String itemId) {
        return nutrition(itemId) > 0;
    }

    /** "minecraft:cooked_beef" reads as "cooked beef" in a report. */
    public static String displayName(String itemId) {
        int colon = itemId.indexOf(':');
        String path = colon >= 0 ? itemId.substring(colon + 1) : itemId;
        return path.replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
