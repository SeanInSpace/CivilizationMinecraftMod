package com.civilization.sim.settlement;

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
 * level suffix all removed — so {@code civilization:storehouse},
 * {@code civilization:storehouse_l2} and {@code civilization:norman/storehouse} are one
 * building at three addresses. Anything unrecognized is {@link #OTHER}, which
 * is a fine thing to be: most buildings have no special behavior and this
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
    /**
     * The camp's fire, and its oven.
     *
     * <p>Where a settlement without a mill turns grain into bread. It is the
     * first thing the HOMESTEAD program raises after a roof, which is why a
     * town has somewhere to bake from the same stage it has somewhere to farm.
     */
    HEARTH,
    MARKET,
    SMITH,
    HALL,
    INN,
    MILL,
    CARPENTRY,
    /** No special behavior, which is most of them. */
    OTHER;

    private static final Map<String, BuildingRole> BY_NAME = Map.ofEntries(
            Map.entry("storehouse", STORE),
            Map.entry("warehouse", STORE),
            // The goblin camp's store. It is a heap of stolen goods with barrels
            // in it rather than a building with shelves, and it is a STORE for
            // exactly the same reason a warehouse is: everything that asks "where
            // does this town keep its things" has to find one answer. A camp with
            // a loot pile the store machinery could not see would hold its goods
            // in a ledger nothing reads and show them in no chest.
            Map.entry("loot_pile", STORE),
            Map.entry("lumber_camp", LUMBER_CAMP),
            Map.entry("mine", MINE),
            Map.entry("farm", CROP_FARM),
            Map.entry("animal_farm", ANIMAL_FARM),
            Map.entry("granary", GRANARY),
            Map.entry("hearth", HEARTH),
            Map.entry("market", MARKET),
            Map.entry("smith", SMITH),
            Map.entry("town_hall", HALL),
            // The orcs' hall, and not a second kind of thing. The chief sits in
            // the great hut, it stands on the muster yard in the middle of the
            // camp, and KingPlanner names it as the seat — so every rule written
            // about "the hall" has always meant it and could not see it. A war
            // camp raised a town_hall beside its own great hut for exactly that
            // reason: see Homes, which is the other half of the same fix.
            Map.entry("great_hut", HALL),
            // And the goblins' own, which is the same argument in a smaller roof:
            // the chieftain's hut is where the chieftain sits, and until it stands
            // nobody is crowned at all. See GoblinCamp.
            Map.entry("chieftain_hut", HALL),
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
     * <p>{@code civilization:norman/storehouse_l2} is {@code storehouse}. Kept
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
