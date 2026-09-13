package com.civilization.sim.culture;

import java.util.List;
import java.util.Set;

/**
 * Choosing a town's name so that no two towns share one.
 *
 * <p>Naming used to be one line: hash the site position into the culture's pool
 * and take whatever came out. Two of the nine spawn towns were both called
 * Bellbrook, which is not a coincidence the world can be asked to accept — the
 * hash has no idea what has already been settled, and with nine towns drawn
 * from a pool of a dozen names a collision is the likely outcome rather than
 * the unlucky one.
 *
 * <p>So: the hash still chooses, because the hash is what makes a seed give the
 * same world twice. It just does not get the last word. The hashed name is the
 * first one tried and the pool is walked from there until a free one is found,
 * which keeps a town's name near the one its position asked for while
 * guaranteeing it is its own.
 *
 * <p>Lives in the simulation rather than beside world generation because it is
 * arithmetic over a list of strings and a set of strings, and because the next
 * thing that needs a name — a daughter colony, a renamed capital — has no
 * business reaching into a worldgen class for it.
 *
 * <p>The same doctrine {@code Names} applies to people and families, arrived at
 * from the same kind of report — "Bren Smith" died twice and three families were
 * all "the Turners". An index says where to start looking; what is handed out is
 * the first thing from there that nothing is already called. Two classes rather
 * than one because the pools, the indices and the overflow all differ, and
 * nothing is shared but the idea.
 */
public final class TownNames {

    /**
     * What goes in front of a name when every name in the pool is spoken for.
     *
     * <p>One scheme, and it is the English place-name one: a qualifier in front
     * of the name it distinguishes. Upper and Lower Slaughter, Great and Little
     * Missenden, New and Old Romney — these are real pairs and they read as
     * places rather than as a program running out of ideas, which "Bellbrook 2"
     * plainly does.
     *
     * <p>Ordered, and the order is load-bearing: it is what makes the choice
     * deterministic. The same seed settles the same regions in the same order,
     * so the same town gets the same qualifier every time the world is made.
     */
    private static final List<String> QUALIFIERS = List.of(
            "Upper", "Lower", "Little", "Great", "New", "Old",
            "East", "West", "North", "South");

    private TownNames() {
    }

    /**
     * A name for a town at this site: the hashed one if it is free, the nearest
     * free one along the pool if it is not.
     *
     * <p>The walk is round the pool from the hashed index, so a town whose first
     * choice is taken is named the next thing its own people would have called
     * it. Only when the whole pool is spoken for does a qualifier go in front —
     * and then the walk begins again with every name qualified, so the town
     * still ends up named after the site it stands on.
     *
     * <p>Always terminates and always answers something nobody is using:
     * qualifiers compound once the list of them runs out, which cannot be
     * reached by any world this mod generates (ten qualifiers against a pool of
     * a dozen is a hundred and twenty towns of one culture) and is written down
     * rather than left to an exception nobody would ever see.
     *
     * @param pool  the culture's own town names, in its own order
     * @param hash  the site's number — whatever the caller hashes its position to
     * @param taken every name already standing in the world, of any culture
     */
    public static String pick(List<String> pool, int hash, Set<String> taken) {
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("a people with no names for its towns");
        }
        int start = Math.floorMod(hash, pool.size());
        for (int round = 0; ; round++) {
            for (int step = 0; step < pool.size(); step++) {
                String candidate = qualify(pool.get((start + step) % pool.size()), round);
                if (!taken.contains(candidate)) {
                    return candidate;
                }
            }
        }
    }

    /**
     * A name with {@code round} qualifiers in front of it.
     *
     * <p>Round zero is the bare name. Past that the round is read as a numeral in
     * base {@link #QUALIFIERS}{@code .size()}, least significant digit first, so
     * every round gives a different string and the early ones give the ones a
     * person would have chosen: Upper Bellbrook, then Lower Bellbrook, and so on
     * down the list before any of them is ever used twice.
     */
    private static String qualify(String name, int round) {
        if (round == 0) {
            return name;
        }
        int digit = (round - 1) % QUALIFIERS.size();
        return QUALIFIERS.get(digit) + " "
                + qualify(name, (round - 1) / QUALIFIERS.size());
    }
}
