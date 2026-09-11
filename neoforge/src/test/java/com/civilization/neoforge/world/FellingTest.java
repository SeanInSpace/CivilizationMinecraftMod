package com.civilization.neoforge.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What counts as one tree when somebody swings an axe at it.
 *
 * <p>The fault these are written against: felling used to walk out from the
 * stump through logs <em>and leaves</em>, so in any close-grown wood — where
 * five blocks apart is enough for two oaks to interlock their crowns — chopping
 * one tree took down every tree its canopy touched, and theirs, and theirs. A
 * player who cut one oak watched the horizon fall over.
 *
 * <p>No level here on purpose: the question "is there wood at this position" is
 * all {@link Felling} asks, so a wood can be written down as a set of positions
 * and the shapes that matter — a dark oak's double trunk, a fancy oak's diagonal
 * limbs — checked without a server.
 */
class FellingTest {

    /** A wood you can write out by hand: wood in one pile, foliage in another. */
    private static final class Wood {
        private final Set<BlockPos> logs = new HashSet<>();
        private final Set<BlockPos> leaves = new HashSet<>();

        Wood log(int x, int y, int z) {
            logs.add(new BlockPos(x, y, z));
            return this;
        }

        Wood leaf(int x, int y, int z) {
            leaves.add(new BlockPos(x, y, z));
            return this;
        }

        /** A straight trunk of {@code height} logs standing on the ground here. */
        Wood trunk(int x, int z, int height) {
            for (int dy = 0; dy < height; dy++) {
                log(x, GROUND + dy, z);
            }
            return this;
        }

        /** A crown of foliage, two out and two up from the top of a trunk. */
        Wood crown(int x, int z, int height) {
            int top = GROUND + height - 1;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        leaf(x + dx, top + dy, z + dz);
                    }
                }
            }
            return this;
        }

        /** The only question felling gets to ask. Foliage answers no. */
        Predicate<BlockPos> isLog() {
            return logs::contains;
        }

        Set<BlockPos> felledFrom(int x, int y, int z) {
            return Felling.treeAt(new BlockPos(x, y, z), isLog());
        }
    }

    private static final int GROUND = 64;

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    // --- the bug ---

    @Test
    void twoOaksWhoseCrownsHaveGrownIntoEachOtherAreTwoTrees() {
        // Five apart is the spacing a town's own woodland is planted on, and at
        // that spacing the canopies plainly touch. The trunks do not.
        Wood wood = new Wood().trunk(0, 0, 6).crown(0, 0, 6)
                .trunk(5, 0, 6).crown(5, 0, 6);

        Set<BlockPos> felled = wood.felledFrom(0, GROUND, 0);

        assertEquals(6, felled.size(), "one oak is its own six logs and nothing else");
        for (int dy = 0; dy < 6; dy++) {
            assertFalse(felled.contains(at(5, GROUND + dy, 0)),
                    "the neighbor is still standing");
        }
        assertFalse(felled.contains(at(5, GROUND, 0)), "and its stump is untouched");
    }

    @Test
    void nothingCrossesALeaf() {
        // A single leaf between two trunks used to be a bridge. It is not wood.
        Wood wood = new Wood().trunk(0, 0, 4).leaf(1, GROUND, 0).trunk(2, 0, 4);

        Set<BlockPos> felled = wood.felledFrom(0, GROUND, 0);

        assertEquals(4, felled.size());
        assertFalse(felled.contains(at(2, GROUND, 0)), "leaves join nothing to anything");
    }

    @Test
    void aLeafIsNotATree() {
        Wood wood = new Wood().trunk(0, 0, 4).crown(0, 0, 4);

        assertTrue(wood.felledFrom(0, GROUND + 3, 2).isEmpty(),
                "an axe in the foliage fells no tree; that leaf is one leaf");
    }

    // --- the shapes vanilla actually grows ---

    @Test
    void aDarkOakComesDownAllFourColumns() {
        // DarkOakTrunkPlacer lays a 2x2 trunk: the four columns touch face to
        // face all the way up, and nobody would call that two trees.
        Wood wood = new Wood().trunk(0, 0, 8).trunk(1, 0, 8)
                .trunk(0, 1, 8).trunk(1, 1, 8);

        Set<BlockPos> felled = wood.felledFrom(0, GROUND, 0);

        assertEquals(32, felled.size(), "all four columns, eight courses each");
    }

    @Test
    void aDarkOaksCornerBranchComesWithIt() {
        // The same placer hangs branch columns on the corners of the 2x2 —
        // offsets of -1 and +2 on x and z — so a branch meets the trunk on a
        // diagonal and never on a face.
        Wood wood = new Wood().trunk(0, 0, 8).trunk(1, 0, 8)
                .trunk(0, 1, 8).trunk(1, 1, 8);
        for (int dy = 0; dy < 3; dy++) {
            wood.log(-1, GROUND + 4 + dy, -1);
        }

        Set<BlockPos> felled = wood.felledFrom(0, GROUND, 0);

        assertTrue(felled.contains(at(-1, GROUND + 6, -1)),
                "a corner branch left behind is a log floating in the air");
        assertEquals(35, felled.size());
    }

    @Test
    void aFancyOakKeepsItsDiagonalBranches() {
        // FancyTrunkPlacer.makeLimb rasterizes a branch along a line, one log
        // per step of max(|dx|,|dy|,|dz|). These six positions are what that
        // produces for a limb from the trunk out to (5, +3, 2), and two of the
        // five joints in it are purely horizontal diagonals — dy of zero.
        Wood wood = new Wood().trunk(0, 0, 7);
        int y = GROUND + 4;
        wood.log(1, y + 1, 0).log(2, y + 1, 1).log(3, y + 2, 1)
                .log(4, y + 2, 2).log(5, y + 3, 2);

        Set<BlockPos> felled = wood.felledFrom(0, GROUND, 0);

        assertTrue(felled.contains(at(2, y + 1, 1)), "the first horizontal diagonal");
        assertTrue(felled.contains(at(4, y + 2, 2)), "the second");
        assertTrue(felled.contains(at(5, y + 3, 2)), "and the tip of the limb");
        assertEquals(12, felled.size(), "seven of trunk and five of branch");
    }

    @Test
    void twoSaplingsPushedInSideBySideAreOneTree() {
        // Wood touching wood is one tree. That is the vanilla reality — it is
        // why a dark oak is one tree — and it applies to a player's planting too.
        Wood wood = new Wood().trunk(0, 0, 5).trunk(1, 0, 5);

        assertTrue(Felling.isOneTree(at(0, GROUND, 0), at(1, GROUND + 4, 0), wood.isLog()));
    }

    // --- the bounds ---

    @Test
    void theCapHolds() {
        // A slab of solid wood, well inside the box and far past the cap.
        Wood wood = new Wood();
        for (int dx = 0; dx < 6; dx++) {
            for (int dz = 0; dz < 6; dz++) {
                wood.trunk(dx, dz, 6);
            }
        }

        assertEquals(Felling.MOST_LOGS, wood.felledFrom(0, GROUND, 0).size(),
                "one stroke brings down a tree's worth and stops");
    }

    @Test
    void theBoxHoldsSideways() {
        // A run of wood laid end to end, the way a wood joined trunk to trunk
        // by a fallen log would look. It is cut off at the edge of the box.
        Wood wood = new Wood();
        for (int dx = 0; dx <= 20; dx++) {
            wood.log(dx, GROUND, 0);
        }

        Set<BlockPos> felled = wood.felledFrom(0, GROUND, 0);

        assertEquals(Felling.REACH_SIDEWAYS + 1, felled.size());
        assertTrue(felled.contains(at(Felling.REACH_SIDEWAYS, GROUND, 0)));
        assertFalse(felled.contains(at(Felling.REACH_SIDEWAYS + 1, GROUND, 0)),
                "past the widest tree that grows, it is somebody else's wood");
    }

    @Test
    void theBoxHoldsUpward() {
        Wood wood = new Wood().trunk(0, 0, 50);

        Set<BlockPos> felled = wood.felledFrom(0, GROUND, 0);

        assertEquals(Felling.REACH_UP + 1, felled.size());
        assertFalse(felled.contains(at(0, GROUND + Felling.REACH_UP + 1, 0)),
                "nothing that grows is taller than this");
    }

    @Test
    void aTrunkStruckPartWayUpStillTakesTheStumpUnderIt() {
        // A wall builder is handed the log where the trunk crosses the line,
        // which is wherever the corridor happens to cut it — not the root.
        Wood wood = new Wood().trunk(0, 0, 9);

        Set<BlockPos> felled = wood.felledFrom(0, GROUND + 3, 0);

        assertEquals(9, felled.size());
        assertTrue(felled.contains(at(0, GROUND, 0)), "the stump comes down too");
    }
}
