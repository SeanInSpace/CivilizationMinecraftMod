package com.keystone;

import com.keystone.blueprint.BlockSubstitutions;
import com.keystone.blueprint.StructurizeNbt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code .blueprint} decoding that can be checked without a game.
 *
 * <p>Same split as {@link TransformsTest}: the integer arithmetic and the string
 * policy are tested here, and turning names into real block states is exercised
 * in-world. That division is not a compromise — the packing and the index order
 * are exactly where a silent bug would live, because getting either wrong still
 * produces a full building, just a transposed or shifted one.
 *
 * <p>The figures come from a real 240-file MineColonies schematic set rather
 * than from imagination.
 */
class StructurizeNbtTest {

    // --- the packed cell array ---

    @Test
    void twoCellsShareAnIntWithTheFirstInTheHighHalf() {
        int[] packed = {(7 << 16) | 9, (1 << 16) | 2};

        assertArrayEquals(new int[]{7, 9, 1, 2},
                StructurizeNbt.unpackIndices(packed, 4),
                "each int carries two palette indices, the earlier one in the high half");
    }

    @Test
    void anOddCellCountIgnoresItsPaddingShort() {
        // waypoint.blueprint really is 1x1x1: one cell, but a whole int on disk.
        int[] packed = {(5 << 16) | 0};

        int[] cells = StructurizeNbt.unpackIndices(packed, 1);

        assertEquals(1, cells.length,
                "the trailing short is padding and must not be read as a block");
        assertEquals(5, cells[0], "the one real cell is the high half");
    }

    @Test
    void paletteIndicesAreUnsigned() {
        // A palette may exceed 32767 entries. Sign-extending the short would
        // index backwards off the front of the palette rather than into it.
        int[] packed = {(0xFFFF << 16) | 0x8000};

        assertArrayEquals(new int[]{65535, 32768},
                StructurizeNbt.unpackIndices(packed, 2),
                "high indices must come back positive");
    }

    @Test
    void aTruncatedArrayIsRefusedRatherThanReadPastItsEnd() {
        assertTrue(StructurizeNbt.holdsEveryCell(new int[1013], 2025),
                "1013 ints hold 2026 shorts, enough for archery1's 2025 cells");
        assertTrue(StructurizeNbt.holdsEveryCell(new int[1452], 2904),
                "baker1's 11x24x11 packs exactly, with no padding");
        assertFalse(StructurizeNbt.holdsEveryCell(new int[10], 2904),
                "a file whose array is short is damaged, and saying so beats reading rubbish");
    }

    // --- the cell ordering ---

    @Test
    void cellsAreWalkedByLayerThenRowThenColumn() {
        // Deliberately non-cubic, 4 wide and 2 deep: a cube would pass this even
        // with x and z confused, which is the one mistake worth catching here.
        int sizeX = 4;
        int sizeZ = 2;

        assertEquals(0, StructurizeNbt.cellIndex(0, 0, 0, sizeX, sizeZ), "origin is first");
        assertEquals(1, StructurizeNbt.cellIndex(1, 0, 0, sizeX, sizeZ), "x moves fastest");
        assertEquals(4, StructurizeNbt.cellIndex(0, 0, 1, sizeX, sizeZ), "then z, a row at a time");
        assertEquals(8, StructurizeNbt.cellIndex(0, 1, 0, sizeX, sizeZ), "then y, a layer at a time");
        assertEquals(11, StructurizeNbt.cellIndex(3, 1, 0, sizeX, sizeZ), "and they compose");
    }

    @Test
    void everyCellOfAVolumeGetsItsOwnIndex() {
        int sizeX = 5;
        int sizeY = 3;
        int sizeZ = 2;
        boolean[] seen = new boolean[sizeX * sizeY * sizeZ];

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int i = StructurizeNbt.cellIndex(x, y, z, sizeX, sizeZ);
                    assertFalse(seen[i], "two cells collided on index " + i);
                    seen[i] = true;
                }
            }
        }
        for (int i = 0; i < seen.length; i++) {
            assertTrue(seen[i], "no cell maps to index " + i);
        }
    }

    // --- the substitution policy ---

    @Test
    void structurizesOwnMarkersAreInstructionsRatherThanBlocks() {
        assertNull(BlockSubstitutions.substituteFor("structurize:blocksubstitution"),
                "a substitution block means leave the world alone — filling it would "
                        + "bury the building in its own foundation");
        assertEquals("minecraft:dirt",
                BlockSubstitutions.substituteFor("structurize:blocksolidsubstitution"),
                "a solid substitution wants ground, not nothing");
        assertEquals("minecraft:water",
                BlockSubstitutions.substituteFor("structurize:blockfluidsubstitution"));
    }

    @Test
    void unknownModBlocksAreGuessedFromTheirSuffix() {
        assertEquals("minecraft:oak_stairs",
                BlockSubstitutions.substituteFor("biomesoplenty:hellbark_stairs"),
                "a mod block named _stairs is a stairs block, named or not");
        assertEquals("minecraft:ladder",
                BlockSubstitutions.substituteFor("quark:crimson_ladder"));
        assertEquals("minecraft:bookshelf",
                BlockSubstitutions.substituteFor("quark:warped_bookshelf"));
        assertEquals("minecraft:oak_trapdoor",
                BlockSubstitutions.substituteFor("biomesoplenty:hellbark_trapdoor"));
        assertEquals("minecraft:oak_planks",
                BlockSubstitutions.substituteFor("biomesoplenty:hellbark_planks"));
    }

    @Test
    void theLongerSuffixIsTestedFirst() {
        // Every one of these ends with a shorter rule's suffix too, so a table
        // walked in the wrong order turns gates into fences and panes into glass.
        assertEquals("minecraft:oak_fence_gate",
                BlockSubstitutions.substituteFor("somemod:birch_fence_gate"),
                "a fence gate must not be read as a fence");
        assertEquals("minecraft:glass_pane",
                BlockSubstitutions.substituteFor("somemod:tinted_glass_pane"),
                "a pane must not be read as a solid glass block");
        assertEquals("minecraft:oak_trapdoor",
                BlockSubstitutions.substituteFor("somemod:iron_trapdoor"),
                "a trapdoor must not be read as a door");
        assertEquals("minecraft:wall_torch",
                BlockSubstitutions.substituteFor("occultism:spirit_wall_torch"),
                "a wall torch must not be read as a standing torch");
    }

    @Test
    void theCommonPackFixturesGetConsideredAnswers() {
        assertEquals("minecraft:cobblestone_stairs",
                BlockSubstitutions.substituteFor("domum_ornamentum:shingle"),
                "shingles are roofing and carry stair properties, so stairs keep the pitch");
        assertEquals("minecraft:cobblestone_slab",
                BlockSubstitutions.substituteFor("domum_ornamentum:shingle_slab"));
        assertEquals("minecraft:barrel",
                BlockSubstitutions.substituteFor("minecolonies:blockminecoloniesrack"),
                "a rack is storage, and so is a barrel");
        assertTrue(BlockSubstitutions.isKnown("minecolonies:blockwaypoint"),
                "fixtures that mean nothing outside their own mod are named, not guessed");
    }

    @Test
    void anythingElseBecomesSomethingSolid() {
        assertEquals(BlockSubstitutions.DEFAULT,
                BlockSubstitutions.substituteFor("nobodysmod:mysterious_thing"),
                "an unknown block is more often part of a wall than a decoration, and a "
                        + "hole reads as broken where a plain patch reads as plain");
        assertFalse(BlockSubstitutions.isKnown("nobodysmod:mysterious_thing"));
    }
}
