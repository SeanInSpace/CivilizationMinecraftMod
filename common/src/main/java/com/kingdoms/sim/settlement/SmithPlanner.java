package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

/**
 * The forge.
 *
 * <p>Smiths turn iron into the things a town cannot cut out of a hillside: tools
 * for the trades, weapons and armour for the watch. Iron comes up with the stone
 * — miners credit it when they cut through ore — so the chain runs mine → forge →
 * everyone else, and a town with no mine eventually has no tools either.
 *
 * <p>Charcoal is not modelled; timber stands in as fuel, which keeps the lumber
 * camp load-bearing for more than just walls.
 */
public final class SmithPlanner {

    /** Iron and fuel consumed to make one finished thing. */
    public static final int IRON_PER_ITEM = 2;
    public static final int FUEL_PER_ITEM = 1;

    /** Most a smith finishes in one simulation step. */
    public static final int OUTPUT_PER_SMITH = 1;

    /** Ceilings, so a forge stops rather than burying the town in spare hoes. */
    public static final int MAX_TOOLS = 64;
    public static final int MAX_WEAPONS = 32;
    public static final int MAX_ARMOUR = 32;

    private SmithPlanner() {
    }

    /**
     * One step at the forge for every smith the town has.
     *
     * <p>Tools first: a town that cannot dig cannot do anything else either.
     * Then weapons, then armour — a garrison that can hit back matters more than
     * one that can take a hit.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        if (!hasSmithy(settlement)) {
            return;
        }
        int smiths = (int) settlement.residents().stream()
                .filter(p -> p.profession() == Profession.SMITH && !p.isTooWeakToWork())
                .count();
        for (int i = 0; i < smiths; i++) {
            for (int made = 0; made < OUTPUT_PER_SMITH; made++) {
                if (!forgeOne(settlement)) {
                    return;   // out of iron, or nothing left worth making
                }
            }
        }
    }

    /** @return true if something was made */
    private static boolean forgeOne(Settlement settlement) {
        String wanted = nextWanted(settlement);
        if (wanted == null) {
            return false;
        }
        TownStores stores = settlement.stores();
        if (!stores.has(TownStores.IRON, IRON_PER_ITEM)
                || !stores.has(TownStores.WOOD, FUEL_PER_ITEM)) {
            return false;
        }
        stores.take(TownStores.IRON, IRON_PER_ITEM);
        stores.take(TownStores.WOOD, FUEL_PER_ITEM);
        stores.add(wanted, 1);
        return true;
    }

    /** What the forge should be making, or null when everything is stocked. */
    private static String nextWanted(Settlement settlement) {
        TownStores stores = settlement.stores();
        if (stores.get(TownStores.TOOLS) < MAX_TOOLS) {
            return TownStores.TOOLS;
        }
        if (stores.get(TownStores.WEAPONS) < MAX_WEAPONS) {
            return TownStores.WEAPONS;
        }
        if (stores.get(TownStores.ARMOUR) < MAX_ARMOUR) {
            return TownStores.ARMOUR;
        }
        return null;
    }

    public static boolean hasSmithy(Settlement settlement) {
        return settlement.buildings().stream()
                .anyMatch(b -> b.blueprintId().contains("smith"));
    }

    /**
     * Hands a worker a tool from the town's stores.
     *
     * <p>All-or-nothing, like every other payment: a town with an empty rack
     * cannot equip anybody, which is what makes the forge worth building.
     */
    public static boolean issueTool(Settlement settlement, Person person) {
        if (person.hasTool()) {
            return false;
        }
        if (!settlement.stores().take(TownStores.TOOLS, 1)) {
            return false;
        }
        person.setHasTool(true);
        return true;
    }
}
