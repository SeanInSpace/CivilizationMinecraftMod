package com.kingdoms.sim.person;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What counts as food, and how far it goes.
 *
 * <p>Hunger runs 0–99, so a loaf at 30 undoes about fifteen steps of appetite.
 * Anything not listed is simply not edible — a settler handed a stone pickaxe
 * will carry it and starve beside it.
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
        NUTRITION.put("minecraft:golden_apple", 60);
        NUTRITION.put("minecraft:cooked_beef", 45);
        NUTRITION.put("minecraft:cooked_porkchop", 45);
        NUTRITION.put("minecraft:cooked_chicken", 35);
        NUTRITION.put("minecraft:bread", 30);
        NUTRITION.put("minecraft:baked_potato", 25);
        NUTRITION.put("minecraft:apple", 20);
        NUTRITION.put("minecraft:carrot", 15);
        NUTRITION.put("minecraft:beetroot", 12);
        NUTRITION.put("minecraft:wheat", 10);
        NUTRITION.put("minecraft:potato", 8);
        NUTRITION.put("minecraft:sweet_berries", 8);
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
