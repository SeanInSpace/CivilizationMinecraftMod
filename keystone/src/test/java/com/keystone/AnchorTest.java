package com.keystone;

import com.keystone.blueprint.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cell a structure lines up by.
 *
 * <p>Structurize files name one, under {@code primary_offset} — usually
 * whatever the author put at the front door. Ignoring it and centring on the
 * bounding box instead is what put an imported building beside its plot rather
 * than on it, and sometimes half into the hillside next to it.
 */
class AnchorTest {

    private static Vec3i size(int x, int y, int z) {
        return new Vec3i(x, y, z);
    }

    @Test
    void aBlueprintThatSaysNothingIsCentredOnItsOwnFloor() {
        // What this always did, kept as the fallback: a plot is a point and the
        // building is drawn around it.
        assertEquals(new BlockPos(3, 0, 3), Blueprint.defaultAnchor(size(7, 5, 7)));
        assertEquals(new BlockPos(0, 0, 0), Blueprint.defaultAnchor(size(1, 1, 1)));
    }

    @Test
    void anEvenSpanLeansTowardTheLowerCorner() {
        // Integer halving, stated rather than left to be discovered: an
        // eight-wide building centres on cell three, not three-and-a-half.
        assertEquals(new BlockPos(3, 0, 3), Blueprint.defaultAnchor(size(8, 5, 8)));
    }

    @Test
    void aBlueprintWithNoAnchorGivenFallsBackRatherThanCarryingNull() {
        Blueprint plain = new Blueprint(size(5, 3, 5), List.of(), null);

        assertEquals(Blueprint.defaultAnchor(size(5, 3, 5)), plain.anchor());
    }

    @Test
    void anAnchorInsideTheBoxFits() {
        assertTrue(Blueprint.anchorFits(new BlockPos(0, 0, 0), size(4, 4, 4)));
        assertTrue(Blueprint.anchorFits(new BlockPos(3, 3, 3), size(4, 4, 4)));
    }

    @Test
    void anAnchorOutsideTheBoxDoesNot() {
        // A file naming a cell it does not contain is describing a different
        // structure, and lining up by it would move the whole thing somewhere
        // nobody asked for — so it is refused rather than honoured.
        assertFalse(Blueprint.anchorFits(new BlockPos(4, 0, 0), size(4, 4, 4)));
        assertFalse(Blueprint.anchorFits(new BlockPos(-1, 0, 0), size(4, 4, 4)));
        assertFalse(Blueprint.anchorFits(new BlockPos(0, 9, 0), size(4, 4, 4)));
    }

    @Test
    void aStatedAnchorSurvivesBeingStored() {
        Blueprint stated = new Blueprint(size(9, 4, 5), List.of(), new BlockPos(1, 0, 4));

        assertEquals(new BlockPos(1, 0, 4), stated.anchor(),
                "the author said where the door is; nothing should quietly recentre it");
    }
}
