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
public final class TownStores {

    /** Well-known ids. Not exhaustive — anything may be stored under any name. */
    public static final String FOOD = "food";
    public static final String WOOD = "wood";
    public static final String STONE = "stone";
    public static final String SAPLINGS = "saplings";
    public static final String IRON = "iron";
    public static final String TOOLS = "tools";
    public static final String WEAPONS = "weapons";
    public static final String ARMOUR = "armour";

    /** What a founding party carries in: enough to raise a hall and get started. */
    public static final int FOUNDING_WOOD = 256;
    public static final int FOUNDING_STONE = 256;

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
