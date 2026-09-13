package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.TownNames;
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
