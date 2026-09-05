package com.kingdoms.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What goes under a building, and what goes round it.
 *
 * <p>Two failures live in these cells and neither was reachable except by
 * walking to a hillside: a building floating over a slope with nothing holding
 * it up, and a cobble curb standing a block proud of the grass all the way
 * round something on level ground.
 */
class FoundationTest {

    /**
     * The floor course of a building on ground whose first air block is 64.
     *
     * <p>One below, because that is where {@code floorFor} puts it: the floor
     * replaces the top of the soil rather than sitting on it. Getting this wrong
     * in a fixture reads as the code laying a curb it does not lay.
     */
    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    /** Air from this height up, solid below: an ordinary flat world. */
    private static BlueprintPlacer.Ground airFrom(int height) {
        return new BlueprintPlacer.Ground() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= height; }
        };
    }

    /** Solid to the west, open air to the east: a building on a cliff edge. */
    private static BlueprintPlacer.Ground cliffAt(int height) {
        return new BlueprintPlacer.Ground() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) {
                return pos.getX() > 0 || pos.getY() >= height;
            }
        };
    }

    private static List<BlueprintPlacer.Placement> laid(BlueprintPlacer.Ground ground) {
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BlueprintPlacer.foundation(blocks, BASE, 5, 5, 0, ground);
        return blocks;
    }

    private static boolean filled(List<BlueprintPlacer.Placement> blocks, BlockPos at) {
        return blocks.stream().anyMatch(b -> b.pos().equals(at));
    }

    @Test
    void aBuildingOnLevelGroundNeedsNoUnderpinningAtAll() {
        // The whole cost is meant to be paid by buildings actually on a slope.
        // Every cell under a flat plot already has ground in it.
        List<BlueprintPlacer.Placement> blocks = laid(airFrom(64));

        assertTrue(blocks.isEmpty(),
                "flat ground got " + blocks.size() + " blocks of cobble it did not need");
    }

    @Test
    void aBuildingOverAVoidIsPackedUpFromUnderneath() {
        List<BlueprintPlacer.Placement> blocks = laid(airFrom(Integer.MIN_VALUE));

        assertFalse(blocks.isEmpty(), "something has to hold it up");
        for (BlueprintPlacer.Placement block : blocks) {
            assertEquals(Blocks.COBBLESTONE, block.state().getBlock(),
                    "underpinning is cobble, not whatever the building is made of");
        }
    }

    @Test
    void theUnderpinningSitsBeneathTheFloorAndNeverInIt() {
        List<BlueprintPlacer.Placement> blocks = laid(airFrom(Integer.MIN_VALUE));

        for (BlueprintPlacer.Placement block : blocks) {
            assertTrue(block.pos().getY() < BASE.getY(),
                    block.pos() + " is at or above the floor the building lays itself");
        }
    }

    @Test
    void theUnderpinningReachesNoDeeperThanItIsAllowedTo() {
        List<BlueprintPlacer.Placement> blocks = laid(airFrom(Integer.MIN_VALUE));

        int deepest = blocks.stream().mapToInt(b -> b.pos().getY()).min().orElse(BASE.getY());
        assertEquals(BASE.getY() - BlueprintPlacer.FOUNDATION_DEPTH, deepest,
                "a bottomless hole is not filled in forever");
    }

    @Test
    void onlyTheOpenSideOfACliffIsPackedUp() {
        List<BlueprintPlacer.Placement> blocks = laid(cliffAt(64));

        assertFalse(blocks.isEmpty(), "the open side needs holding up");
        for (BlueprintPlacer.Placement block : blocks) {
            assertTrue(block.pos().getX() > 0,
                    block.pos() + " is on the solid side and needed nothing");
        }
    }

    // --- the apron, which is the curb people notice ---

    @Test
    void anApronColumnAtGradeIsLeftAlone() {
        // The bug this guards: a doorstep laid where the ground already reaches
        // the floor line puts a cobble curb a block proud of the grass.
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BlueprintPlacer.doorstep(blocks, BASE, airFrom(64));

        assertTrue(blocks.isEmpty(), "already at grade; nothing to step up from");
    }

    @Test
    void anApronColumnOverAStepIsPackedUpToTheFloorLine() {
        // Ground two below the floor: the step gets filled so somebody can walk in.
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        // Solid from 61 down, so the floor line at 63 stands two above grade.
        BlueprintPlacer.doorstep(blocks, BASE, airFrom(62));

        assertEquals(2, blocks.size(), "two courses to bring the doorstep up");
        assertTrue(filled(blocks, BASE));
        assertTrue(filled(blocks, BASE.below()));
    }

    @Test
    void anApronColumnOverADropIsNotBridged() {
        // Further down than a step: filling it would build a pier out over the
        // drop it was trying to hide.
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BlueprintPlacer.doorstep(blocks, BASE, airFrom(Integer.MIN_VALUE));

        assertTrue(blocks.isEmpty(), "a cliff is not a doorstep");
    }

    @Test
    void anApronColumnInAnUnloadedChunkIsLeftForLater() {
        // The apron reaches past the footprint and can cross a chunk boundary.
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BlueprintPlacer.doorstep(blocks, BASE, new BlueprintPlacer.Ground() {
            @Override public boolean loaded(BlockPos pos) { return false; }
            @Override public boolean unsupported(BlockPos pos) { return true; }
        });

        assertTrue(blocks.isEmpty(), "nothing is judged about ground nobody has loaded");
    }
}
