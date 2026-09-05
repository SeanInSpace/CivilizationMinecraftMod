package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A second people, which is what the culture type was a promise about.
 *
 * <p>The claim was that adding one is filling in a table rather than threading
 * a new idea through the simulation. These are the parts of that claim which
 * can be checked without a world: that a second entry exists, that it is
 * genuinely different, and that everything asking for a culture by name gets
 * the one it asked for rather than quietly getting the default.
 */
class CultureTest {

    @Test
    void thePeopleEveryTownHasBeenAllAlongNowHaveAName() {
        // Settlements were stamped kingdoms:norman and the blueprint loader was
        // looking for kingdoms:norman/house long before anything defined a
        // culture by that name, so every lookup fell through to the default and
        // nobody noticed — because the default was the only thing to fall
        // through to.
        assertSame(Culture.NORMAN, Culture.of("kingdoms:norman"),
                "asking for the normans should not quietly hand back the default");
        assertEquals("kingdoms:norman", Culture.NORMAN.id());
    }

    @Test
    void aSecondPeopleKeepDifferentBeasts() {
        assertNotEquals(Culture.NORMAN.pennedAnimals(), Culture.HIGHLAND.pennedAnimals(),
                "a culture that keeps the same animals in the same pens is a rename");
        assertTrue(Culture.HIGHLAND.pennedAnimals().contains("minecraft:goat"));
        assertTrue(Culture.NORMAN.pennedAnimals().contains("minecraft:cow"));
    }

    @Test
    void everyCultureFitsThePlotReservedForItsPens() {
        // The animal farm's ground is reserved in the catalog at a fixed size,
        // so a culture cannot quietly outgrow the plot set aside for it. A fifth
        // pen is a catalog change, not a table entry — which is worth failing
        // loudly here rather than discovering as a compound built through its
        // neighbor's wall.
        for (Culture culture : Culture.all()) {
            assertTrue(culture.penCount() <= 4,
                    culture.id() + " keeps " + culture.penCount()
                            + " kinds of beast, and the reserved plot holds four");
            assertTrue(culture.penCount() >= 1, culture.id() + " must keep something");
        }
    }

    @Test
    void anUnknownCultureFallsBackRatherThanFailing() {
        // A datapack naming a culture nobody shipped must give a plain town, not
        // a crash — the whole point of the id being a string.
        assertSame(Culture.DEFAULT, Culture.of("someone:nonexistent"));
        assertSame(Culture.DEFAULT, Culture.of(null));
    }

    @Test
    void everyCultureIsFindableByItsOwnName() {
        for (Culture culture : Culture.all()) {
            assertSame(culture, Culture.of(culture.id()),
                    culture.id() + " does not answer to its own name");
        }
    }

    @Test
    void everyCultureLaysItselfOutSomeWayItKnowsAbout() {
        // This used to insist on rings, which was the only honest thing to say
        // while rings were the only arrangement that existed — the field was a
        // string nothing read. Now the question is the one that was always
        // meant: does the name a culture asks for resolve to something real.
        //
        // Every name, not just the first. Layouts.of falls back to rings rather
        // than throwing, so a typo in the second entry of a list would be a
        // people who quietly build villages half the time.
        for (Culture culture : Culture.all()) {
            for (String layout : culture.layouts()) {
                assertEquals(layout, Layouts.of(layout).id(),
                        culture.id() + " asks for a layout nothing implements: " + layout);
            }
        }
    }

    @Test
    void thePeoplesDoNotAllBuildTheSameTown() {
        // The claim the culture type has been making since it was written, and
        // could not back up while every settlement was laid out in rings
        // whatever it called itself.
        assertNotEquals(Culture.NORMAN.layouts(), Culture.GOBLIN.layouts());
        assertNotEquals(Culture.GOBLIN.layouts(), Culture.ORC.layouts());
    }

    @Test
    void aPeopleAlwaysBuildsTheSameTownInTheSamePlace() {
        // The whole reason the choice is a hash of the center rather than a die
        // roll: nothing is written down until a settlement exists, so the answer
        // has to be reconstructible from the ground the town stands on.
        SimPos centre = new SimPos(1_337, 72, -404);
        String first = Culture.BURGHER.layoutFor(centre);
        for (int again = 0; again < 8; again++) {
            assertEquals(first, Culture.BURGHER.layoutFor(centre),
                    "the same people at the same center changed their minds");
        }
        assertSame(Layouts.of(first), Culture.BURGHER.arrangementFor(centre));
    }

    @Test
    void aPeopleWithSeveralArrangementsUsesAllOfThem() {
        // A picker that technically varies but lands on one entry ninety-nine
        // times in a hundred is the bug this whole unit exists to avoid: the
        // second arrangement would ship and nobody would ever see it.
        for (Culture culture : Culture.all()) {
            if (culture.layouts().size() < 2) {
                continue;
            }
            // A grid rather than a line. The first draft walked one diagonal, so
            // a picker that happened to alternate along it would have passed and
            // a retuned hash could have failed for having sampled the wrong
            // thousand blocks rather than for being wrong.
            Set<String> seen = new HashSet<>();
            for (int x = -15; x <= 15; x++) {
                for (int z = -15; z <= 15; z++) {
                    String picked = culture.layoutFor(new SimPos(x * 617, 72, z * 421));
                    assertTrue(culture.layouts().contains(picked),
                            culture.id() + " lays a town out as " + picked
                                    + ", which is not one of its own");
                    seen.add(picked);
                }
            }
            assertEquals(Set.copyOf(culture.layouts()), seen,
                    culture.id() + " never builds some of the arrangements it names");
        }
    }

    @Test
    void everyPeopleKeepsTheArrangementItAlreadyBuiltInFirst() {
        // A save written before the layout was recorded takes the head of the
        // list, so reordering one of these rearranges every town of that people
        // already standing in somebody's world.
        assertEquals(Culture.LAYOUT_RING, Culture.NORMAN.layouts().get(0));
        assertEquals(Culture.LAYOUT_RING, Culture.DEFAULT.layouts().get(0));
        assertEquals(Culture.LAYOUT_ORGANIC, Culture.HIGHLAND.layouts().get(0));
        assertEquals(Culture.LAYOUT_HIGH_STREET, Culture.BURGHER.layouts().get(0));
        assertEquals(Culture.LAYOUT_RING_STREETS, Culture.VALE.layouts().get(0));
        assertEquals(Culture.LAYOUT_WARREN, Culture.GOBLIN.layouts().get(0));
        assertEquals(Culture.LAYOUT_STRONGHOLD, Culture.ORC.layouts().get(0));
    }

    @Test
    void everyArrangementBelongsToSomebody() {
        // Two of them were registered and named by no culture at all, which made
        // them unreachable outside /civ buildtest — shipped code that no town
        // could ever be.
        Set<String> claimed = new HashSet<>();
        for (Culture culture : Culture.all()) {
            claimed.addAll(culture.layouts());
        }
        for (var layout : Layouts.all()) {
            assertTrue(claimed.contains(layout.id()),
                    layout.id() + " is an arrangement no people builds");
        }
    }

    @Test
    void aCultureDrawsFromItsOwnFolder() {
        // The style is the id's own path, derived rather than stored beside it,
        // so the two can never drift apart.
        assertEquals("norman", Culture.NORMAN.style());
        assertEquals("highland", Culture.HIGHLAND.style());
        assertEquals("default", Culture.DEFAULT.style());
    }

    @Test
    void everyCultureHasAFolderToDrawFrom() {
        // An empty style would make the placer ask for "/house", which resolves
        // to nothing and would silently drop every town of that culture back to
        // the built-in shapes.
        for (Culture culture : Culture.all()) {
            assertTrue(!culture.style().isEmpty(), culture.id() + " has no style folder");
            assertTrue(!culture.style().contains(":"),
                    culture.id() + " kept its namespace in the folder name");
        }
    }
}
