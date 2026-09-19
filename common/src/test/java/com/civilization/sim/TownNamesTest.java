package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.TownNames;
import com.civilization.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No two towns share a name, however small the pool is.
 *
 * <p>The report: two of the nine spawn towns were both called Bellbrook. The
 * name was a hash of the site position into the culture's pool and nothing
 * asked what was already settled, so with nine towns drawn from a pool of a
 * dozen a collision was the likely outcome and not the unlucky one.
 *
 * <p>The interesting cases are all past the end of the pool, which is why the
 * headline test raises more sites than there are names — the fault is invisible
 * while there is room, and every English county has an Upper and a Lower
 * something for exactly this reason.
 */
class TownNamesTest {

    /** Three names, so the pool runs out on the fourth town and stays out. */
    private static final List<String> POOL = List.of("Bellbrook", "Stonebridge", "Millbrook");

    @Test
    void theHashStillChoosesWhenNothingIsTaken() {
        // The old behavior, unchanged and asserted: the hash is what makes a seed
        // reproduce, so it must still pick when it can.
        for (int hash = 0; hash < 30; hash++) {
            assertEquals(POOL.get(Math.floorMod(hash, POOL.size())),
                    TownNames.pick(POOL, hash, Set.of()),
                    "the hashed name is the one a free pool hands back");
        }
    }

    @Test
    void aTakenNameIsWalkedPastRatherThanHandedOutTwice() {
        // The fault itself, at its smallest. Both towns hash to Bellbrook.
        String first = TownNames.pick(POOL, 0, Set.of());
        String second = TownNames.pick(POOL, 0, Set.of(first));

        assertEquals("Bellbrook", first);
        assertNotEquals(first, second, "the second town cannot also be Bellbrook");
        assertTrue(POOL.contains(second),
                "and while there is room it is still one of this people's own names: "
                        + second);
    }

    @Test
    void moreSitesThanNamesStillGetsOneEach() {
        // The measurement the fix is for. Thirty towns out of a pool of three,
        // each hashed from its own position, all of them named something nobody
        // else is called.
        Set<String> taken = new HashSet<>();
        List<String> named = new ArrayList<>();
        for (int site = 0; site < 30; site++) {
            String name = TownNames.pick(POOL, site * 31 + site * site, taken);
            assertTrue(taken.add(name), "two towns are both called " + name);
            named.add(name);
        }
        assertEquals(30, taken.size(), "thirty towns, thirty names: " + named);
        // And they read as places rather than as a counter. The first ten past
        // the pool are the pool qualified, which is what an English map looks
        // like: Upper and Lower Slaughter, Great and Little Missenden.
        assertTrue(named.stream().anyMatch(n -> n.startsWith("Upper ")),
                "the overflow scheme is a place-name qualifier: " + named);
        assertTrue(named.stream().noneMatch(n -> n.matches(".*\\d.*")),
                "and never a number: " + named);
    }

    @Test
    void theSameWorldNamesTheSameTownsTheSameWay() {
        // Determinism, which is the whole reason the hash still leads. Two runs
        // of the same sites in the same order must agree name for name.
        List<String> first = new ArrayList<>();
        Set<String> takenA = new HashSet<>();
        for (int site = 0; site < 12; site++) {
            String name = TownNames.pick(POOL, site * 7, takenA);
            takenA.add(name);
            first.add(name);
        }
        List<String> again = new ArrayList<>();
        Set<String> takenB = new HashSet<>();
        for (int site = 0; site < 12; site++) {
            String name = TownNames.pick(POOL, site * 7, takenB);
            takenB.add(name);
            again.add(name);
        }
        assertEquals(first, again, "the same seed has to give the same world");
    }

    /**
     * How many worlds are made before the starter's name is believed.
     *
     * <p>A hundred, which is the number the report earned: two worlds is an
     * anecdote and the fault was that <em>every</em> world said Ashmarch.
     */
    private static final int WORLDS = 100;

    /**
     * A hundred worlds do not all call their first town the same thing.
     *
     * <p>The report: seed 8675309 and seed 20260919 raised a {@code human/burgher}
     * town and a {@code human/highland} town, six hundred blocks apart in
     * different worlds, and both were named <strong>Ashmarch</strong>.
     *
     * <p>The starter's column is where the old hash failed. Every other worldgen
     * draw takes the seed and a named salt; the naming took {@code x * 31 + z} and
     * nothing else, and two worlds' starters land near enough to the same column
     * — same region, same spacing, the same jitter lattice — that the weak sum
     * handed them the same index into the same pool.
     *
     * <p>So the column is held fixed here and only the seed is varied, which is
     * the hardest version of the question: if the seed does nothing, every one of
     * the hundred worlds gets the identical name, which is exactly what the old
     * hash did and what the second half of this test records.
     */
    @Test
    void ahundredWorldsDoNotAllNameTheirStarterTheSameThing() {
        List<String> pool = Culture.of("civilization:human/burgher").townNames();
        assertTrue(pool.size() >= 4, "the burghers' pool is too small to measure with");
        SimPos starter = new SimPos(312, 0, -184);   // one world's starter column

        Set<String> named = new HashSet<>();
        for (int world = 0; world < WORLDS; world++) {
            long seed = 8675309L + world * 7919L;
            named.add(TownNames.pick(pool,
                    TownNames.hashFor(seed, starter.x(), starter.z()), Set.of()));
        }
        assertEquals(pool.size(), named.size(),
                "a hundred worlds reached only " + named.size() + " of the burghers'"
                        + " " + pool.size() + " names for one column, so the seed is"
                        + " barely steering the choice: " + named);

        // And the fault itself, stated: the hash the caller used to pass cannot
        // tell one world from another, because it was never given one.
        Set<String> theOldWay = new HashSet<>();
        for (int world = 0; world < WORLDS; world++) {
            theOldWay.add(TownNames.pick(pool, starter.x() * 31 + starter.z(), Set.of()));
        }
        assertEquals(1, theOldWay.size(),
                "the position-only hash somehow varied by world, which would mean"
                        + " this test is no longer measuring what the report was");
    }

    /**
     * The two worlds of the report, by name, and they differ.
     *
     * <p>Kept separate from the sweep because these are the two seeds a person
     * can go and look at, and a sweep that is green on average is no comfort to
     * somebody holding the screenshot.
     */
    @Test
    void theTwoSeedsOfTheReportDoNotBothSayAshmarch() {
        List<String> pool = Culture.of("civilization:human/burgher").townNames();
        SimPos starter = new SimPos(312, 0, -184);
        assertNotEquals(
                TownNames.pick(pool, TownNames.hashFor(8675309L, starter.x(),
                        starter.z()), Set.of()),
                TownNames.pick(pool, TownNames.hashFor(20260919L, starter.x(),
                        starter.z()), Set.of()),
                "the two seeds of the playtest still name their starter the same"
                        + " thing on the same column");
    }

    /**
     * And the same seed still makes the same world, which is the whole reason
     * the choice is a hash and not a random draw.
     */
    @Test
    void theSeededNameIsStillAFunctionOfTheSeedAndThePlace() {
        List<String> pool = Culture.of("civilization:human/burgher").townNames();
        for (int world = 0; world < 20; world++) {
            long seed = 20260919L + world;
            for (int at = -3; at <= 3; at++) {
                int x = at * 407;
                int z = at * -613;
                assertEquals(TownNames.hashFor(seed, x, z), TownNames.hashFor(seed, x, z),
                        "the hash is not a function of its arguments");
                assertEquals(
                        TownNames.pick(pool, TownNames.hashFor(seed, x, z), Set.of()),
                        TownNames.pick(pool, TownNames.hashFor(seed, x, z), Set.of()),
                        "a world regenerated from its seed named a town differently");
            }
        }
    }

    /**
     * Two towns of one world are not named off each other's coordinates.
     *
     * <p>The other half of what the hash has to do. Adding the seed would be no
     * use if the sites within a world then collided, and {@code x * 31 + z}
     * collides along whole diagonals — every site on {@code x * 31 + z == c} got
     * the same index. The taken-names walk hides that, so it is measured here
     * with nothing taken.
     */
    @Test
    void sitesOfOneWorldAreNotHashedOntoEachOther() {
        Set<Integer> hashes = new HashSet<>();
        int sites = 0;
        for (int rx = -4; rx <= 4; rx++) {
            for (int rz = -4; rz <= 4; rz++) {
                sites++;
                // A spacing grid with jitter, which is the lattice towns sit on.
                hashes.add(TownNames.hashFor(20260919L,
                        rx * 1024 + rz * 31, rz * 1024 - rx * 31));
            }
        }
        assertEquals(sites, hashes.size(),
                sites - hashes.size() + " of " + sites + " sites in one world hash"
                        + " onto each other, which is the diagonal collision the"
                        + " plain sum had");
    }

    @Test
    void everyCulturesOwnPoolSurvivesBeingUsedUp() {
        // Across the real pools rather than a fixture's three, because the fault
        // was found in a real world: nine towns, the real cultures, the real
        // names. Each people's pool is drawn down twice over and still hands out
        // nothing twice.
        for (Culture culture : Culture.all()) {
            List<String> pool = culture.townNames();
            if (pool.isEmpty()) {
                continue;   // a people that names nothing; "Wayside" covers it
            }
            Set<String> taken = new HashSet<>();
            for (int site = 0; site < pool.size() * 2 + 1; site++) {
                String name = TownNames.pick(pool, site * 31 - site, taken);
                assertTrue(taken.add(name),
                        culture.id() + " handed out " + name + " twice");
            }
        }
    }
}
