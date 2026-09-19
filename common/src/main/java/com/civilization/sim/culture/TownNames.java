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
     * The number a site hashes to, given the world it stands in.
     *
     * <p><strong>The report.</strong> Two worlds, seed 8675309 and seed 20260919,
     * six hundred blocks and one biome apart, and both raised a starter town
     * called <em>Ashmarch</em>. It is the first word a new player reads and it was
     * going to be the same word in every world.
     *
     * <p>The cause was that {@link #pick} had never been told which world it was
     * naming. Its caller hashed the site's position and nothing else — {@code x *
     * 31 + z} — and a starter town's position is chosen by machinery that is
     * itself only weakly a function of the seed: the same region, the same
     * spacing, the same jitter lattice, so two worlds put their starter within a
     * stone's throw of the same column and {@code x * 31 + z} then handed them the
     * same index into the same pool. Every other worldgen decision already takes
     * the seed and a named salt — {@code SettlementSites.hash} — and naming was
     * the one that did not.
     *
     * <p>The seed goes in, and the site stays in, because both facts matter. A
     * name that ignored the seed is this report; a name that ignored the site
     * would give one world's nine towns the same name. Avalanched rather than
     * added, because the coordinates of towns on a spacing grid differ in high
     * bits and a plain sum of them collides along whole diagonals — the same
     * warning {@code Kingdom.nameFor} carries about its own hash.
     *
     * <p>The name is still a pure function of the seed and the place, so a world
     * regenerated from the same seed names its towns exactly as it did before.
     * That is what {@link #pick}'s determinism note means and it is untouched.
     */
    public static int hashFor(long worldSeed, int x, int z) {
        long h = worldSeed * 0x9E3779B97F4A7C15L;
        h = mix(h ^ ((long) x * 0x2545F4914F6CDD1DL));
        h = mix(h ^ ((long) z * 0x8A5CD789635D2DFFL));
        return (int) (h ^ (h >>> 32));
    }

    /** splitmix64's finaliser: the avalanche the note above asks for. */
    private static long mix(long x) {
        long h = x;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
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
