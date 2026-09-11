package com.kingdoms.sim.person;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What somebody has picked up off the ground and not yet put anywhere.
 *
 * <p>Separate from {@link Person#carriedMaterial()}, and it has to be. That is
 * the load a builder <em>fetched</em> — one material, drawn from a store, spent
 * block by block into a wall — and overwriting it with a handful of dirt out of
 * a foundation would have a builder lay a course of earth, or more likely stand
 * at a wall holding the wrong thing forever. Digging fills a different pocket.
 *
 * <p>Several materials at once, unlike the building load, because one hole is
 * several materials: a plot with trees on it and a hillside under it hands its
 * digger timber and earth within the same minute, and a satchel that could only
 * hold one of them would have to throw the other away.
 *
 * <p>Small on purpose. See {@link #CAPACITY}.
 */
public final class Pockets {

    /**
     * How much somebody can carry before they have to go and put it down.
     *
     * <p>Sixteen, the same armful {@code BuildLoad.LOAD_SIZE} is, and for the
     * mirror-image reason: enough that a digger is not walking to the storehouse
     * every other block, and little enough that clearing a plot is visibly a
     * sequence of trips rather than one settler absorbing a hillside.
     *
     * <p>Counted across everything held, not per material. A pair of hands is a
     * pair of hands whatever is in them.
     */
    public static final int CAPACITY = 16;

    /** Insertion-ordered: what went in first is what gets walked away first. */
    private final Map<String, Integer> held = new LinkedHashMap<>();

    /**
     * Puts what will fit into the pockets.
     *
     * @return how much actually went in, which is zero once they are full
     */
    public int put(String resource, int amount) {
        if (resource == null || amount <= 0) {
            return 0;
        }
        int room = Math.max(0, CAPACITY - total());
        int taken = Math.min(amount, room);
        if (taken > 0) {
            held.merge(resource, taken, Integer::sum);
        }
        return taken;
    }

    /** Everything held of one material, leaving none of it behind. */
    public int takeAll(String resource) {
        Integer had = held.remove(resource);
        return had == null ? 0 : had;
    }

    /**
     * The material that has waited longest, or null when the pockets are empty.
     *
     * <p>This is the delivery order, and oldest-first is the point of it: a
     * digger who has been carrying a log around since the first tree does not
     * keep carrying it because they have since picked up some dirt.
     */
    public String first() {
        for (Map.Entry<String, Integer> entry : held.entrySet()) {
            if (entry.getValue() > 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    public int get(String resource) {
        return held.getOrDefault(resource, 0);
    }

    public int total() {
        int sum = 0;
        for (int amount : held.values()) {
            sum += amount;
        }
        return sum;
    }

    public boolean isEmpty() {
        return total() <= 0;
    }

    public boolean isFull() {
        return total() >= CAPACITY;
    }

    /** Everything held, by name, oldest first. For the codecs and for reporting. */
    public Map<String, Integer> all() {
        return Collections.unmodifiableMap(held);
    }

    /** For the codecs, loading a saved armful back onto somebody's back. */
    public void restore(Map<String, Integer> saved) {
        held.clear();
        saved.forEach(this::put);
    }

    public void clear() {
        held.clear();
    }

    @Override
    public String toString() {
        return held.toString();
    }
}
