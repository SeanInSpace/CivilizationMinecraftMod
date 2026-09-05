package com.kingdoms.sim;

import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dictionary that turns the ledger's bare words into things a player can
 * hold — one canonical item out, many acceptable items in.
 */
class ResourcesTest {

    @Test
    void everyResourceTheLedgerNamesCanBeHandedOver() {
        for (String resource : Resources.known()) {
            assertNotNull(Resources.itemFor(resource),
                    resource + " is in the dictionary, so it must name an item");
            assertTrue(Resources.itemFor(resource).contains(":"),
                    resource + " must map to a namespaced item id, not a bare word");
        }
        assertNotNull(Resources.itemFor(TownStores.WOOD), "timber is the whole point");
        assertNotNull(Resources.itemFor(TownStores.STONE));
        assertEquals(Foods.PROVISION, Resources.itemFor(TownStores.FOOD),
                "food pays out in the same loaf the settlers already eat");
    }

    @Test
    void aResourceRoundTripsThroughItsOwnCanonicalItem() {
        // The property that matters: whatever a store hands out, it will take
        // back as the same thing. Without it a town could pay a player in an
        // item it then refuses as a donation.
        for (String resource : Resources.known()) {
            String item = Resources.itemFor(resource);
            String back = Resources.resourceOf(item);
            if (back != null) {
                assertEquals(resource, back,
                        item + " is what " + resource + " pays out, so it must count as "
                                + resource + " coming back");
            }
        }
    }

    @Test
    void anyTimberCountsAsTimber() {
        assertEquals(TownStores.WOOD, Resources.resourceOf("minecraft:oak_log"));
        assertEquals(TownStores.WOOD, Resources.resourceOf("minecraft:spruce_planks"));
        assertEquals(TownStores.WOOD, Resources.resourceOf("minecraft:warped_stem"),
                "nether wood is still wood");
        assertEquals(TownStores.WOOD, Resources.resourceOf("biomesoplenty:hellbark_log"),
                "a modded log is timber too — the rule is the ending, not the namespace");
    }

    @Test
    void aSaplingIsNotALog() {
        // "oak_sapling" would fall to the timber rules if they were tested
        // first, and a town would burn its replanting stock as building material.
        assertEquals(TownStores.SAPLINGS, Resources.resourceOf("minecraft:oak_sapling"));
        assertEquals(TownStores.SAPLINGS, Resources.resourceOf("minecraft:birch_sapling"));
    }

    @Test
    void redstoneIsNotMasonry() {
        // The tempting shortcut is to match any id containing "stone". These
        // are why it is a named family instead: a town that took redstone as
        // building stone would quietly turn a player's circuitry into a wall.
        assertNull(Resources.resourceOf("minecraft:redstone"),
                "redstone is not masonry");
        assertNull(Resources.resourceOf("minecraft:glowstone"),
                "glowstone is not masonry");
        assertEquals(TownStores.STONE, Resources.resourceOf("minecraft:cobblestone"));
        assertEquals(TownStores.STONE, Resources.resourceOf("minecraft:deepslate"));
    }

    @Test
    void foodDefersToTheNutritionTable() {
        // One table, not two. Anything Foods calls edible is food here, so a
        // datapack that adds a food later does not have to be told twice.
        assertEquals(TownStores.FOOD, Resources.resourceOf("minecraft:bread"));
        assertEquals(TownStores.FOOD, Resources.resourceOf("minecraft:cooked_beef"));
        assertEquals(TownStores.FOOD, Resources.resourceOf("minecraft:wheat"),
                "grain feeds people, so the stores count it");
    }

    @Test
    void thingsTheTownHasNoUseForAreRefused() {
        assertNull(Resources.resourceOf("minecraft:diamond"));
        assertNull(Resources.resourceOf("minecraft:jukebox"));
        assertNull(Resources.resourceOf(""));
        assertNull(Resources.resourceOf(null),
                "refusing is the honest answer — swallowing it would lose the player's item");
    }

    @Test
    void gearDoesNotStack() {
        assertEquals(1, Resources.stackSize(TownStores.WEAPONS),
                "a sword is one to a slot");
        assertEquals(1, Resources.stackSize(TownStores.ARMOR));
        assertEquals(64, Resources.stackSize(TownStores.WOOD));
    }
}
