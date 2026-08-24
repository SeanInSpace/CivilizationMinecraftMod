package com.kingdoms.neoforge.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a digger stands to take a block out.
 *
 * <p>Beside it, never on it and never in it — which is the whole of why nobody
 * drops themselves down a hole, and it has to be decided before anybody walks
 * anywhere. The ordering matters as much as the filtering: sorting purely on
 * closeness to the block once sent people round the far side of a mound for a
 * square a foot nearer the target, and a walk like that outlasts the patience
 * that hands the cell to somebody else.
 */
class StandCandidatesTest {

    private static final BlockPos BLOCK = new BlockPos(0, 64, 0);

    /** Everywhere is standable, so only the geometry and the ordering show. */
    private static List<BlockPos> around(BlockPos block) {
        return Excavation.standCandidates(block, feet -> true, feet -> 0.0);
    }

    private static List<BlockPos> around(BlockPos block, Set<BlockPos> usable) {
        return Excavation.standCandidates(block, usable::contains, feet -> 0.0);
    }

    @Test
    void nobodyStandsInsideTheBlockTheyAreDigging() {
        assertFalse(around(BLOCK).contains(BLOCK),
                "standing in it is how a digger buries themselves");
    }

    @Test
    void nobodyStandsUnderneathIt() {
        // The block comes out; anyone below it is under a falling roof.
        for (int drop = 1; drop <= 4; drop++) {
            BlockPos under = BLOCK.below(drop);
            assertFalse(around(BLOCK).contains(under), under + " is directly beneath the work");
        }
    }

    @Test
    void standingOnTopOfItIsNotOfferedHere() {
        // standFor adds that square itself as a last resort, deliberately and
        // separately, because it breaks the rule this method keeps.
        assertFalse(around(BLOCK).contains(BLOCK.above()),
                "the square on top is a special case, not an ordinary candidate");
    }

    @Test
    void theSquareBesideAtHeadHeightComesFirst() {
        // Feet one above the block: the digger works at their own feet, which is
        // the stance the whole scheme is arranged around.
        List<BlockPos> found = around(BLOCK);

        assertFalse(found.isEmpty());
        assertEquals(BLOCK.getY() + 1, found.getFirst().getY(),
                "level with the block's top, so the work is at their feet");
        assertEquals(1, Math.max(
                        Math.abs(found.getFirst().getX() - BLOCK.getX()),
                        Math.abs(found.getFirst().getZ() - BLOCK.getZ())),
                "and adjacent to it rather than out across the site");
    }

    @Test
    void aHigherStanceIsPreferredToALowerOne() {
        List<BlockPos> found = around(BLOCK);

        for (int i = 1; i < found.size(); i++) {
            int before = Math.abs(found.get(i - 1).getY() - (BLOCK.getY() + 1));
            int after = Math.abs(found.get(i).getY() - (BLOCK.getY() + 1));
            assertTrue(before <= after, "candidates must run downward, never back up");
        }
    }

    @Test
    void onlySquaresThePredicateAllowsAreOffered() {
        BlockPos onlyOne = new BlockPos(1, 65, 0);

        List<BlockPos> found = around(BLOCK, Set.of(onlyOne));

        assertEquals(List.of(onlyOne), found,
                "reserved, unfooted and body-blocked squares are all refused this way");
    }

    @Test
    void aBlockNobodyCanStandBesideOffersNothing() {
        List<BlockPos> found = Excavation.standCandidates(BLOCK, feet -> false, feet -> 0.0);

        assertTrue(found.isEmpty(), "and the caller falls back to standing on top of it");
    }

    @Test
    void everySquareOfferedIsWithinReachOfTheBlock() {
        for (BlockPos feet : around(BLOCK)) {
            assertTrue(Excavation.reaches(feet, BLOCK),
                    feet + " is offered but could not put a tool on the block");
        }
    }

    @Test
    void theShortestWalkBreaksATieBetweenEqualSquares() {
        // Among squares at the same height and the same distance from the block,
        // the one nearest the digger wins — which is what stops a crew crossing
        // each other to reach identical stances.
        BlockPos east = new BlockPos(1, 65, 0);
        BlockPos west = new BlockPos(-1, 65, 0);

        List<BlockPos> found = Excavation.standCandidates(BLOCK,
                feet -> feet.equals(east) || feet.equals(west),
                feet -> feet.equals(west) ? 1.0 : 99.0);

        assertEquals(west, found.getFirst(), "the nearer walk goes first");
    }
}
