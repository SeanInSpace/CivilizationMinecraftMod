package com.kingdoms.sim.settlement;

import java.util.Map;

/**
 * What any holder of goods can be asked.
 *
 * <p>Extracted so that a town's holdings and one building's holdings answer the
 * same questions, because the town's are becoming nothing more than the sum of
 * its buildings'. Everything that spends or produces talks to this; only
 * saving, loading and the founding kit need to know which kind of holder they
 * are actually standing in front of.
 *
 * <p><strong>Why there is no {@code set} or {@code restore} here.</strong> Both
 * mean "make the books read exactly this", which is a coherent thing to ask one
 * ledger and an ambiguous thing to ask a pool of them — told that the town has
 * four hundred timber, which building did the difference come from or go to?
 * They stay on {@link TownStores}, where the caller has already chosen a
 * holder, which is why the codecs and the founding kit still name one.
 */
public interface Stock {

    /** How much of a resource is held. */
    int get(String resource);

    /** Whether at least this much is held. */
    boolean has(String resource, int amount);

    /** Adds to the holding, never below zero. Returns the new total. */
    int add(String resource, int amount);

    /** Adds up to a ceiling, and reports how much actually fit. */
    int addCapped(String resource, int amount, int ceiling);

    /**
     * Spends, all or nothing.
     *
     * @return true if it could be paid
     */
    boolean take(String resource, int amount);

    /** Spends as much as is there, and reports how much that was. */
    int takeUpTo(String resource, int amount);

    /** Everything held, by name. */
    Map<String, Integer> all();
}
