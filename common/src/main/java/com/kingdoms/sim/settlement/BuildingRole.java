package com.kingdoms.sim.settlement;

import java.util.Map;

/**
 * What a building is for, decided once instead of guessed everywhere.
 *
 * <p>Eleven places used to sniff a blueprint id for a substring to work out
 * what they were looking at, and they did not all agree. {@code contains("mine")}
 * and {@code contains("farm")} are the obvious traps — the second matches the
 * animal farm as readily as the crop one — but the dangerous case was quieter:
 * a store is a store because its name contains "storehouse", so a store
 * blueprint ever renamed would have stopped counting as one. Its goods would
 * have stayed in a ledger nothing reads, no chest would have shown them, and
 * nothing would have thrown.
 *
 * <p>Matching is on the bare building name — namespace, culture folder and
 * level suffix all removed — so {@code kingdoms:storehouse},
 * {@code kingdoms:storehouse_l2} and {@code kingdoms:norman/storehouse} are one
 * building at three addresses. Anything unrecognised is {@link #OTHER}, which
 * is a fine thing to be: most buildings have no special behaviour and this
 * enum should not grow an entry until one does.
 */
public enum BuildingRole {

    /** Bulk goods: the shelves a builder fetches from. */
    STORE,
    /** Where felled timber is gathered. */
    LUMBER_CAMP,
    /** Where stone and ore come out of the ground. */
    MINE,
    /** Wheat, and the field the auditor judges for bare rows. */
    CROP_FARM,
    /** Beasts and their pens, which is a different thing entirely. */
    ANIMAL_FARM,
    /** The town's larder. */
    GRANARY,
    MARKET,
    SMITH,
    HALL,
    INN,
    MILL,
    CARPENTRY,
    /** No special behaviour, which is most of them. */
    OTHER;

    private static final Map<String, BuildingRole> BY_NAME = Map.ofEntries(
            Map.entry("storehouse", STORE),
            Map.entry("warehouse", STORE),
            Map.entry("lumber_camp", LUMBER_CAMP),
            Map.entry("mine", MINE),
            Map.entry("farm", CROP_FARM),
            Map.entry("animal_farm", ANIMAL_FARM),
            Map.entry("granary", GRANARY),
            Map.entry("market", MARKET),
            Map.entry("smith", SMITH),
            Map.entry("town_hall", HALL),
            Map.entry("inn", INN),
            Map.entry("mill", MILL),
            Map.entry("carpentry", CARPENTRY));

    /** What this blueprint builds, or {@link #OTHER} if nothing in particular. */
    public static BuildingRole of(String blueprintId) {
        if (blueprintId == null) {
            return OTHER;
        }
        return BY_NAME.getOrDefault(bareName(blueprintId), OTHER);
    }

    /**
     * The building's own name: no namespace, no culture folder, no level.
     *
     * <p>{@code kingdoms:norman/storehouse_l2} is {@code storehouse}. Kept
     * package-visible so its own test can reach it — the address shapes are the
     * part most likely to grow a new form nobody thought about.
     */
    static String bareName(String blueprintId) {
        String id = BuildPlanner.baseIdOf(blueprintId);
        int colon = id.indexOf(':');
        String path = colon < 0 ? id : id.substring(colon + 1);
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
