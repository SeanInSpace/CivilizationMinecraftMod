package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a digger can stand, and where a crew gathers.
 *
 * <p>The reach rule is what keeps diggers beside a block rather than inside it,
 * and it has to be answerable before anybody walks anywhere — so it is pure
 * arithmetic, and until this module had a test source set it was pure
 * arithmetic nobody had ever run twice.
 */
class ExcavationReachTest {

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @Test
    void theBlockUnderfootIsInReach() {
        assertTrue(Excavation.reaches(at(0, 64, 0), at(0, 63, 0)));
    }

    @Test
    void theBlockBesideYouIsInReach() {
        assertTrue(Excavation.reaches(at(0, 64, 0), at(1, 64, 0)));
        assertTrue(Excavation.reaches(at(0, 64, 0), at(0, 64, 1)));
        assertTrue(Excavation.reaches(at(0, 64, 0), at(-1, 64, -1)),
                "and diagonally, which is what makes a 3x3 cell workable from one spot");
    }

    @Test
    void aBlockAcrossTheRoomIsNot() {
        assertFalse(Excavation.reaches(at(0, 64, 0), at(6, 64, 0)));
        assertFalse(Excavation.reaches(at(0, 64, 0), at(0, 64, 9)));
    }

    @Test
    void reachIsMeasuredFromTheEyesSoUpIsFurtherThanDown() {
        // Not a quirk — it is why a digger works a face top-down from beside it
        // rather than having to climb. A block four above is within reach from
        // ground level; the same distance straight down is not.
        BlockPos feet = at(0, 64, 0);

        assertTrue(Excavation.reaches(feet, at(0, 68, 0)), "four above, from the eyes");
        assertFalse(Excavation.reaches(feet, at(0, 60, 0)), "four below is further away");
    }

    @Test
    void reachDoesNotDependOnWhichSideYouApproachFrom() {
        BlockPos block = at(0, 64, 0);

        assertEquals(Excavation.reaches(at(3, 64, 0), block),
                Excavation.reaches(at(-3, 64, 0), block));
        assertEquals(Excavation.reaches(at(0, 64, 3), block),
                Excavation.reaches(at(0, 64, -3), block));
    }

    @Test
    void theMiddleOfAJobIsWhereItsBlocksAverageOut() {
        List<SimPos> targets = List.of(
                new SimPos(0, 64, 0), new SimPos(10, 64, 0),
                new SimPos(0, 64, 10), new SimPos(10, 64, 10));

        assertEquals(at(5, 64, 5), Excavation.middleOf(targets));
    }

    @Test
    void aJobOfOneBlockIsCentredOnThatBlock() {
        assertEquals(at(7, 70, -3),
                Excavation.middleOf(List.of(new SimPos(7, 70, -3))));
    }

    @Test
    void aJobWithNothingLeftInItHasNoMiddleRatherThanACrash() {
        // Reached when the last block of a dig is cleared between one pass and
        // the next, which happens constantly with a crew of six.
        assertEquals(BlockPos.ZERO, Excavation.middleOf(List.of()));
    }

    @Test
    void theMiddleIsCarriedInLongsSoALargeDigDoesNotWrap() {
        // Summing coordinates in an int overflows on a big excavation far from
        // the origin, and the crew gathers somewhere on the other side of the
        // world. Far-flung towns are ordinary here — daughters settle hundreds
        // of blocks out and their daughters go further.
        List<SimPos> farAway = List.of(
                new SimPos(2_000_000, 64, 2_000_000),
                new SimPos(2_000_010, 64, 2_000_010));

        assertEquals(at(2_000_005, 64, 2_000_005), Excavation.middleOf(farAway));
    }
}
