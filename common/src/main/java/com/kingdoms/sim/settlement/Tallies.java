package com.kingdoms.sim.settlement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Named counters a settlement keeps about the world around it.
 *
 * <p>Deliberately untyped: any name may be counted, and nothing needs to declare
 * a stat before it exists. That is the whole point — "hideouts cleared" can be
 * tallied long before there are hideouts, and a datapack quest can ask after a
 * counter this code has never heard of without a code change.
 *
 * <p>Kept apart from {@link TownStores} even though both are string-to-int maps,
 * because they answer different questions and behave differently: stores are
 * spent and can run out, tallies only ever climb.
 */
public final class Tallies {

    /** Counters the mod itself raises. Nothing stops anyone adding more. */
    public static final String MOBS_SLAIN = "mobs_slain";
    public static final String BOSSES_SLAIN = "bosses_slain";
    public static final String HIDEOUTS_CLEARED = "hideouts_cleared";
    public static final String RAIDS_REPELLED = "raids_repelled";
    public static final String SETTLERS_LOST = "settlers_lost";
    public static final String BUILDINGS_RAISED = "buildings_raised";
    public static final String TREES_FELLED = "trees_felled";
    public static final String STONE_CUT = "stone_cut";
    public static final String GOODS_TRADED = "goods_traded";

    private final Map<String, Integer> counts = new LinkedHashMap<>();

    public int get(String stat) {
        return counts.getOrDefault(stat, 0);
    }

    /** Counts one more of something. Returns the new total. */
    public int record(String stat) {
        return record(stat, 1);
    }

    public int record(String stat, int amount) {
        if (amount <= 0) {
            return get(stat);
        }
        int now = get(stat) + amount;
        counts.put(stat, now);
        return now;
    }

    public Map<String, Integer> all() {
        return Collections.unmodifiableMap(counts);
    }

    public void restore(Map<String, Integer> saved) {
        counts.clear();
        saved.forEach((stat, amount) -> {
            if (amount > 0) {
                counts.put(stat, amount);
            }
        });
    }

    /** A stat's name in prose: {@code mobs_slain} reads as "Mobs slain". */
    public static String pretty(String stat) {
        String spaced = stat.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
