package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import org.junit.jupiter.api.Test;

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
        // The animal farm's ground is reserved in the catalogue at a fixed size,
        // so a culture cannot quietly outgrow the plot set aside for it. A fifth
        // pen is a catalogue change, not a table entry — which is worth failing
        // loudly here rather than discovering as a compound built through its
        // neighbour's wall.
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
        for (Culture culture : Culture.all()) {
            assertEquals(Culture.LAYOUT_RING, culture.layout(),
                    culture.id() + " asks for a layout nothing implements");
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
