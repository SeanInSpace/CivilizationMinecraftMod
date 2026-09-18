package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Faces;
import com.civilization.sim.culture.Race;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which face a settler wears, and whether it is still theirs tomorrow.
 *
 * <p>Two claims, and the first is the one that matters. A body is a disposable
 * view — thrown away when the player walks off and rebuilt with a fresh uuid on
 * the way back — so a face worked out from the body is a face that is reshuffled
 * every time you turn your back. This is keyed on the person, so it is not.
 *
 * <p>The second is that a town does not look like one man copied thirty times,
 * which is what every human settlement in the mod looked like until now.
 */
class FacesTest {

    private static UUID person(int n) {
        return new UUID(n * 2654435761L, n * 40503L + 7);
    }

    @Test
    @DisplayName("a face belongs to the person, not to the body")
    void aFaceBelongsToThePerson() {
        UUID one = UUID.fromString("0b2f7a6e-1c4d-4f8a-9b3e-5d6c7a8b9c0d");
        int face = Faces.faceFor(Race.HUMAN, Culture.NORMAN, one);
        for (int i = 0; i < 50; i++) {
            assertEquals(face, Faces.faceFor(Race.HUMAN, Culture.NORMAN, one),
                    "the same settler was handed two different faces");
        }
        // Nothing but the id is in the seed, so an id built a second time is the
        // same settler -- which is what surviving a reload actually means.
        assertEquals(face, Faces.faceFor(Race.HUMAN, Culture.NORMAN,
                UUID.fromString("0b2f7a6e-1c4d-4f8a-9b3e-5d6c7a8b9c0d")));
    }

    @Test
    @DisplayName("all nine of vanilla's faces are worn across the peoples")
    void allNineAreWorn() {
        assertEquals(9, Faces.HUMAN_FACES.size(), "vanilla ships nine default skins");
        assertEquals(9, new HashSet<>(Faces.HUMAN_FACES).size(), "a face is listed twice");

        Set<Integer> worn = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            for (Culture culture : Culture.humans()) {
                int face = Faces.faceFor(Race.HUMAN, culture, person(i));
                assertTrue(face >= 0 && face < 9, "face " + face + " is off the end of the table");
                worn.add(face);
            }
        }
        assertEquals(9, worn.size(),
                "two thousand settlers of every people wore only " + worn.size()
                        + " of the nine faces: " + worn);
    }

    @Test
    @DisplayName("a town of thirty is thirty faces, not one face thirty times")
    void aTownOfThirtyIsNotOneFace() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            seen.add(Faces.faceFor(Race.HUMAN, Culture.NORMAN, person(i)));
        }
        assertEquals(Faces.paletteOf(Culture.NORMAN).size(), seen.size(),
                "thirty lowlanders showed only " + seen.size() + " faces between them");
    }

    @Test
    @DisplayName("every people has a palette, and keeps to it")
    void everyPeopleHasAPaletteAndKeepsToIt() {
        for (Culture culture : Culture.humans()) {
            List<Integer> palette = Faces.paletteOf(culture);
            assertFalse(palette.isEmpty(), culture.id() + " has no faces at all");
            assertEquals(palette.size(), new HashSet<>(palette).size(),
                    culture.id() + " lists a face twice");
            for (int face : palette) {
                assertTrue(face >= 0 && face < Faces.HUMAN_FACES.size(),
                        culture.id() + " names face " + face + ", which does not exist");
            }
            for (int i = 0; i < 500; i++) {
                assertTrue(palette.contains(Faces.faceFor(Race.HUMAN, culture, person(i))),
                        culture.id() + " handed out a face outside its own palette");
            }
        }

        // Six of the nine each, so a people has a look; and never all nine, or
        // the bias would not be a bias.
        for (Culture culture : List.of(Culture.NORMAN, Culture.HIGHLAND,
                Culture.BURGHER, Culture.VALE)) {
            assertEquals(6, Faces.paletteOf(culture).size(),
                    culture.id() + " draws from " + Faces.paletteOf(culture).size()
                            + " faces; six was the arrangement");
        }

        // Two peoples must not be the same crowd under different names.
        Set<List<Integer>> palettes = new HashSet<>();
        for (Culture culture : Culture.humans()) {
            if (culture != Culture.DEFAULT) {
                palettes.add(Faces.paletteOf(culture));
            }
        }
        assertEquals(4, palettes.size(), "two human peoples share a palette");

        // And an unnamed town draws from everything, because it is nobody.
        assertEquals(9, Faces.paletteOf(Culture.DEFAULT).size());
        assertEquals(9, Faces.paletteOf(null).size());
    }

    @Test
    @DisplayName("orcs and goblins keep the two variants they had")
    void orcsAndGoblinsKeepTheirs() {
        for (Race race : List.of(Race.ORC, Race.GOBLIN)) {
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < 400; i++) {
                int variant = Faces.faceFor(race, Culture.ORC, person(i));
                assertTrue(variant >= 0 && variant < Faces.VARIANTS_PER_RACE,
                        race + " variant " + variant + " is outside the table");
                seen.add(variant);
            }
            assertEquals(Faces.VARIANTS_PER_RACE, seen.size(),
                    "four hundred " + race + "s produced only " + seen + " between them");
        }

        // A culture cannot widen a race that has no art for it. The orcs have two
        // sheets and one people; handing them a human palette must not put an orc
        // in face seven, which is a texture that does not exist for him.
        assertTrue(Faces.faceFor(Race.ORC, Culture.NORMAN, person(5))
                < Faces.VARIANTS_PER_RACE);
    }

    @Test
    @DisplayName("a body with nobody behind it is not a crash")
    void aBodyWithNobodyBehindItIsNotACrash() {
        assertEquals(0, Faces.faceFor(Race.HUMAN, Culture.NORMAN, null));
        assertEquals(0, Faces.faceFor(Race.ORC, null));
        // A raider spawned out of the trees answers to no town and draws from
        // everything, which is the two-argument door.
        assertTrue(Faces.paletteOf(null).contains(Faces.faceFor(Race.HUMAN, person(1))));
    }
}
