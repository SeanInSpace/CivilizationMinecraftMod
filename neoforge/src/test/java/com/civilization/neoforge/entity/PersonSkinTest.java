package com.civilization.neoforge.entity;

import com.civilization.sim.culture.Race;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skins exist, are the shape the player model expects, and are picked the
 * same way twice.
 *
 * <p>A texture named by the renderer and missing from the jar is a magenta
 * checkerboard in the game and a green build everywhere else — nothing on the
 * server, in the resource pipeline or in {@code processResources} has any opinion
 * about whether an entity texture the code asks for is actually there. So the
 * table and the files are checked against each other here.
 *
 * <p>The size matters as much as the existence. A player-shaped sheet is read as
 * a 64x64 atlas of cube faces; a 64x32 legacy sheet or a 128x128 one would draw
 * an orc out of whatever happened to lie at those coordinates, which reads as
 * scrambled clothing rather than as a missing file. So the PNG header is parsed
 * rather than trusted.
 *
 * <p>The renderer itself is not touched here — it is a client class, and what it
 * does with the table (copies the race into the render state, hands the sheet
 * back per frame) needs a client. That is written up as a manual check.
 */
class PersonSkinTest {

    @Test
    @DisplayName("every race has an opinion about what it looks like")
    void everyRaceHasAnOpinionAboutWhatItLooksLike() {
        for (Race race : Race.values()) {
            assertTrue(PersonSkins.races().contains(race),
                    race + " is not in the skin table; a new race needs a row in it"
                            + " even if the row is Steve");
            assertFalse(PersonSkins.of(race).isEmpty(), race + " has no sheets at all");
        }
    }

    @Test
    @DisplayName("every sheet the table names is on disk and is a 64x64 skin")
    void everySheetTheTableNamesIsOnDiskAndIsA64x64Skin() throws IOException {
        int ours = 0;
        for (Race race : PersonSkins.races()) {
            for (Identifier sheet : PersonSkins.of(race)) {
                if (sheet.getNamespace().equals("minecraft")) {
                    // Vanilla's own Steve. Shipped by the game, not by us.
                    continue;
                }
                ours++;
                assertEquals("civilization", sheet.getNamespace(),
                        "a settler sheet in somebody else's namespace: " + sheet);
                String path = "/assets/" + sheet.getNamespace() + "/" + sheet.getPath();
                int[] size = pngSize(path);
                assertEquals(64, size[0], path + " is " + size[0] + " wide, not 64");
                assertEquals(64, size[1], path + " is " + size[1] + " tall, not 64");
                // Color type 6 is RGBA. The tusks live on the hat layer and the
                // whole overlay half of the sheet is transparent, so a sheet with
                // no alpha channel would put a solid block in front of the face.
                assertEquals(6, size[2], path + " has no alpha channel");
            }
        }
        assertTrue(ours >= 3, "only " + ours + " sheets of our own; the orcs and goblins want three");
    }

    @Test
    @DisplayName("the orcs have two faces and the goblins one, and every ask lands on a real sheet")
    void theOrcsHaveTwoFacesAndTheGoblinsOne() {
        List<Identifier> orcs = PersonSkins.of(Race.ORC);
        assertEquals(2, orcs.size(), "the orcs were promised plain and war-painted");
        assertEquals(orcs.get(0), PersonSkins.sheetFor(Race.ORC, 0));
        assertEquals(orcs.get(1), PersonSkins.sheetFor(Race.ORC, 1));

        // A race with fewer sheets than the server has variants must reduce into
        // what it has rather than fall off the end -- half of all goblins carry
        // variant one, and every one of them has to be a goblin.
        assertEquals(1, PersonSkins.of(Race.GOBLIN).size());
        assertSame(PersonSkins.sheetFor(Race.GOBLIN, 0), PersonSkins.sheetFor(Race.GOBLIN, 1));

        // And nothing anybody can send produces a null or a vanilla fallback for
        // a race that has its own art.
        for (Race race : Race.values()) {
            for (int variant = 0; variant < 8; variant++) {
                Identifier sheet = PersonSkins.sheetFor(race, variant);
                assertNotNull(sheet);
                assertTrue(PersonSkins.of(race).contains(sheet),
                        race + " variant " + variant + " asked for " + sheet
                                + ", which is not one of its own");
            }
        }
    }

    @Test
    @DisplayName("humans still wear Steve, on purpose")
    void humansStillWearSteve() {
        assertEquals(List.of(PersonSkins.STEVE), PersonSkins.of(Race.HUMAN));
        assertEquals("minecraft", PersonSkins.STEVE.getNamespace());
    }

    @Test
    @DisplayName("a person's skin is stable for their life and spread across the variants")
    void aPersonsSkinIsStableAndSpread() {
        UUID one = UUID.fromString("0b2f7a6e-1c4d-4f8a-9b3e-5d6c7a8b9c0d");
        assertEquals(PersonEntity.skinFor(one), PersonEntity.skinFor(one),
                "the same person got two different faces");

        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            int variant = PersonEntity.skinFor(new UUID(i * 2654435761L, i));
            assertTrue(variant >= 0 && variant < PersonEntity.SKINS_PER_RACE,
                    "variant " + variant + " is outside the table");
            seen.add(variant);
        }
        assertEquals(PersonEntity.SKINS_PER_RACE, seen.size(),
                "four hundred orcs only produced " + seen + " between them");

        // A body with no person behind it is not a crash.
        assertEquals(0, PersonEntity.skinFor(null));
    }

    /** Width, height and color type, read straight out of a PNG's IHDR. */
    private static int[] pngSize(String classpath) throws IOException {
        try (InputStream in = PersonSkinTest.class.getResourceAsStream(classpath)) {
            assertNotNull(in, "no such resource on the test classpath: " + classpath);
            DataInputStream data = new DataInputStream(in);
            byte[] signature = new byte[8];
            data.readFully(signature);
            assertEquals(0x89, signature[0] & 0xFF, classpath + " is not a PNG");
            data.readInt();                     // IHDR length
            assertEquals(0x49484452, data.readInt(), classpath + " does not start with IHDR");
            int width = data.readInt();
            int height = data.readInt();
            data.readByte();                    // bit depth
            int colorType = data.readByte();
            return new int[] {width, height, colorType};
        }
    }
}
