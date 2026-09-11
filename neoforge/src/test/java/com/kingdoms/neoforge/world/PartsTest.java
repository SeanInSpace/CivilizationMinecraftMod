package com.kingdoms.neoforge.world;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.settlement.BuildingSizes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pieces a building is made of, each asked the one thing it must not get wrong.
 *
 * <p>Three claims, and they are the three ways a roof fails:
 * <ul>
 *   <li><strong>It leaks.</strong> A roof is not a decoration — it is the thing
 *       that has to be over every square of floor. A slope drawn one course out
 *       leaves a line of open sky down the middle of a house, which is invisible
 *       in a screenshot taken from the street and obvious the moment it rains.</li>
 *   <li><strong>It grows out of its plot.</strong> The whole reason
 *       {@link BuildingSizes} exists is that buildings used to be drawn bigger
 *       than the ground reserved for them and stood through their neighbors. An
 *       eave that overhangs is the first thing in the mod that deliberately
 *       reaches past a wall, so it is also the first thing that could put that
 *       fault back. One block is all it may have, because the doorstep ring is
 *       one block.</li>
 *   <li><strong>It faces the wrong way once the building is turned.</strong>
 *       Every built-in shape is drawn facing south and turned to face its street,
 *       and until now nothing in a drawing had a direction worth turning. Stairs
 *       do.</li>
 * </ul>
 *
 * <p>Runs without a world for the same reason {@code BlueprintPlacerSizeTest}
 * does: a part is a pure function of the base, the footprint and its parameters,
 * and the fake flat {@link BlueprintPlacer.Site} satisfies everything a home
 * asks the ground.
 */
class PartsTest {

    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    /**
     * Every home there is, and how high its walls stand.
     *
     * <p>The wall height is what says where the roof starts, and it is the one
     * number about a home that lives in the drawing method rather than in
     * {@link BuildingSizes}. Written down again here so a leak can be asserted
     * over the plate rather than merely somewhere above the floor — a check that
     * only looked above the floor would be satisfied by the walls themselves.
     */
    private static final java.util.Map<String, Integer> HOMES = java.util.Map.of(
            "cottage", 3, "house", 4, "longhouse", 4, "croft", 4, "bunkhouse", 3);

    private static BlueprintPlacer.Site flatFor(Culture culture) {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= 64; }
            @Override public Culture culture() { return culture; }
            @Override public int groundLevel(int x, int z) { return 64; }
        };
    }

    private static List<BlueprintPlacer.Placement> drawn(Culture culture, String path,
                                                         BlockPos base) {
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BlueprintPlacer.draw(flatFor(culture), blocks, path, base);
        return blocks;
    }

    // --- a roof is the thing over the floor ----------------------------------

    @Test
    void agableRoofPutsSomethingOverEveryColumnItSpans() {
        for (String home : HOMES.keySet()) {
            BuildingSizes.Size size = BuildingSizes.of("kingdoms:" + home);
            List<BlueprintPlacer.Placement> roof = new ArrayList<>();
            Parts.gableRoof(roof, BASE, size, 5,
                    Blocks.OAK_STAIRS, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS);

            assertRoofedIn(roof, size, 5, home + " under a gable");
        }
    }

    @Test
    void ahippedRoofPutsSomethingOverEveryColumnItSpansIncludingTheL() {
        for (String home : HOMES.keySet()) {
            BuildingSizes.Size size = BuildingSizes.of("kingdoms:" + home);
            List<BlueprintPlacer.Placement> roof = new ArrayList<>();
            Parts.hipRoof(roof, BASE, size, 5,
                    Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, 0);

            assertRoofedIn(roof, size, 5, home + " under a hip");
        }
        // The croft is the reason the hip is measured rather than derived: it is
        // an L, and a roof worked out from the depth alone would step over the
        // notch and leave the crook open.
        assertTrue(BuildingSizes.of("kingdoms:croft").notch().isCut(),
                "the croft stopped being the L, and this test stopped testing it");
    }

    @Test
    void alowHipIsStillARoofAndNotJustAShorterOne() {
        // The goblin cap. Clamping the rise flattens the top, and the thing that
        // could go wrong is that the flat part stops being laid at all.
        BuildingSizes.Size size = BuildingSizes.of("kingdoms:house");
        List<BlueprintPlacer.Placement> capped = new ArrayList<>();
        int top = Parts.hipRoof(capped, BASE, size, 5,
                Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICKS, 2);

        assertEquals(6, top, "two courses of rise puts the deck one above the plate");
        assertRoofedIn(capped, size, 5, "a capped hip");
    }

    @Test
    void everyHomeOfEveryPeopleIsRoofedOverEveryCellOfItsFloor() {
        // The claim on the real drawings rather than on the parts in isolation.
        // A home is a box plus a roof, and the seam between the two is exactly
        // where a course goes missing.
        for (Culture culture : Culture.all()) {
            HOMES.forEach((home, wallHeight) -> {
                BuildingSizes.Size size = BuildingSizes.of("kingdoms:" + home);
                assertRoofedIn(drawn(culture, home, BASE), size, wallHeight + 1,
                        culture.id() + "'s " + home);
            });
        }
    }

    // --- and it stays on its own ground --------------------------------------

    @Test
    void nothingAHomeDrawsLeavesItsOwnPlot() {
        // The fault BuildingSizes was written to end, in its newest possible
        // form. An eave is allowed exactly the doorstep ring and not a block
        // more; past that it is standing in the neighbor's house.
        for (Culture culture : Culture.all()) {
            for (String home : HOMES.keySet()) {
                BuildingSizes.Size size = BuildingSizes.of("kingdoms:" + home);
                int rx = size.width() / 2 + BuildingSizes.APRON;
                int rz = size.depth() / 2 + BuildingSizes.APRON;

                for (BlueprintPlacer.Placement block : drawn(culture, home, BASE)) {
                    int dx = block.pos().getX() - BASE.getX();
                    int dz = block.pos().getZ() - BASE.getZ();
                    assertTrue(Math.abs(dx) <= rx && Math.abs(dz) <= rz,
                            culture.id() + "'s " + home + " lays a "
                                    + block.state().getBlock() + " at (" + dx + ", "
                                    + dz + "), which is outside the ground staked"
                                    + " for it: the plot reaches " + rx + " by " + rz);
                }
            }
        }
    }

    @Test
    void anEaveActuallyOverhangsRatherThanStoppingAtTheWall() {
        // The other half of the bound above, which on its own would be satisfied
        // by a roof that never left the walls at all.
        BuildingSizes.Size size = BuildingSizes.of("kingdoms:cottage");
        List<BlueprintPlacer.Placement> roof = new ArrayList<>();
        Parts.gableRoof(roof, BASE, size, 5,
                Blocks.OAK_STAIRS, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS);

        int rz = size.depth() / 2;
        assertTrue(roof.stream().anyMatch(
                        block -> Math.abs(block.pos().getZ() - BASE.getZ()) == rz + 1),
                "the eave never leaves the wall it is supposed to shelter");
    }

    // --- and it faces the way the building was turned ------------------------

    @Test
    void aroofsStairsTurnWithTheBuildingThroughEveryQuarter() {
        for (Rotation rotation : Rotation.values()) {
            List<BlueprintPlacer.Placement> drawn = drawn(Culture.NORMAN, "cottage", BASE);
            List<BlueprintPlacer.Placement> turned = new ArrayList<>(drawn);
            BlueprintPlacer.turn(turned, BASE, rotation);

            int stairs = 0;
            for (int i = 0; i < drawn.size(); i++) {
                BlockState before = drawn.get(i).state();
                if (!(before.getBlock() instanceof StairBlock)) {
                    continue;
                }
                stairs++;
                BlockState after = turned.get(i).state();
                assertEquals(rotation.rotate(before.getValue(StairBlock.FACING)),
                        after.getValue(StairBlock.FACING),
                        "a stair kept its old facing through " + rotation
                                + ", so this house's roof slopes across its own ridge");
                assertEquals(before.getValue(StairBlock.SHAPE),
                        after.getValue(StairBlock.SHAPE),
                        "a stair's shape is measured from its facing and must not"
                                + " be turned a second time on top of it");
            }
            assertTrue(stairs > 0, "a cottage with no stairs in it has no roof to turn");
        }
    }

    @Test
    void aquarterTurnReallyMovesAStairRatherThanLeavingItAlone() {
        // The test above passes vacuously if BlockState.rotate is a no-op for
        // stairs, which is exactly what it is for any block that has not
        // implemented it. Said out loud, because the whole roof depends on it.
        List<BlueprintPlacer.Placement> drawn = drawn(Culture.NORMAN, "cottage", BASE);
        List<BlueprintPlacer.Placement> turned = new ArrayList<>(drawn);
        BlueprintPlacer.turn(turned, BASE, Rotation.CLOCKWISE_90);

        boolean moved = false;
        for (int i = 0; i < drawn.size(); i++) {
            if (drawn.get(i).state().getBlock() instanceof StairBlock
                    && drawn.get(i).state().getValue(StairBlock.FACING)
                    != turned.get(i).state().getValue(StairBlock.FACING)) {
                moved = true;
            }
        }
        assertTrue(moved, "no stair changed direction in a quarter turn");
    }

    @Test
    void aturnedRoofIsStillWatertightOverTheTurnedFootprint() {
        // Positions and states are turned by two different mechanisms, and a
        // roof that survives one and not the other is a roof with a slot in it.
        BuildingSizes.Size size = BuildingSizes.of("kingdoms:longhouse");
        List<BlueprintPlacer.Placement> turned =
                new ArrayList<>(drawn(Culture.NORMAN, "longhouse", BASE));
        BlueprintPlacer.turn(turned, BASE, Rotation.CLOCKWISE_90);

        Set<Long> covered = columnsOf(turned, BASE, BASE.getY() + 5);
        int rx = size.depth() / 2;    // a quarter turn swaps the axes
        int rz = size.width() / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                assertTrue(covered.contains(key(dx, dz)),
                        "the turned longhouse is open at (" + dx + ", " + dz + ")");
            }
        }
    }

    // --- the wall parts ------------------------------------------------------

    @Test
    void ashutterNeverHangsAcrossTheDoorway() {
        // A shutter leaf hangs beside a window, one cell outside the wall. A
        // window one block from the door put its leaf in the cell outside the
        // doorway, which reads as a door with a trapdoor nailed across it. The
        // leaf is skipped wherever the wall cell behind it is a gap.
        for (Culture culture : Culture.all()) {
            for (String home : HOMES.keySet()) {
                BuildingSizes.Size size = BuildingSizes.of("kingdoms:" + home);
                int rz = size.depth() / 2;
                Set<BlockPos> filled = new HashSet<>();
                for (BlueprintPlacer.Placement block : drawn(culture, home, BASE)) {
                    filled.add(block.pos());
                }
                for (int y = 1; y <= 2; y++) {
                    assertFalse(filled.contains(BASE.offset(0, y, rz + 1)),
                            culture.id() + "'s " + home + " has something hanging"
                                    + " across the outside of its doorway at height " + y);
                }
            }
        }
    }

    @Test
    void aplinthNeverBricksUpTheDoorway() {
        // The rule that makes the replacing parts safe: they write only where the
        // wall is already standing, and a doorway is a gap that holds nothing. A
        // plinth that ignored that would seal every house in the town.
        for (Culture culture : Culture.all()) {
            for (String home : HOMES.keySet()) {
                BuildingSizes.Size size = BuildingSizes.of("kingdoms:" + home);
                int rz = size.depth() / 2;
                Set<BlockPos> filled = new HashSet<>();
                for (BlueprintPlacer.Placement block : drawn(culture, home, BASE)) {
                    filled.add(block.pos());
                }
                for (int y = 1; y <= 2; y++) {
                    assertFalse(filled.contains(BASE.offset(0, y, rz)),
                            culture.id() + "'s " + home + " has something standing in"
                                    + " its own doorway at head height " + y);
                }
            }
        }
    }

    @Test
    void halfTimberingAndAWindowRowDoNotFightOverTheSameCell() {
        // Both write over the wall block and only over the wall block, so
        // whichever runs second finds a post where a post went and leaves it.
        // The failure mode is a window punched through an upright.
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BuildingSizes.Size size = BuildingSizes.of("kingdoms:house");
        BlueprintPlacer.draw(flatFor(Culture.NORMAN), blocks, "house", BASE);

        long panes = blocks.stream()
                .filter(block -> block.state().is(Blocks.GLASS))
                .map(BlueprintPlacer.Placement::pos)
                .distinct()
                .count();
        assertTrue(panes >= 4,
                "a house with " + panes + " panes in it is still a shed with a hole"
                        + " in the wall; the whole point of a window row is more"
                        + " than one");
        assertEquals(9, size.width(), "the fixture stopped being the house it names");
    }

    @Test
    void aporchStandsOnItsPostsRatherThanInThinAir() {
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BuildingSizes.Size size = BuildingSizes.of("kingdoms:cottage");
        Parts.porch(blocks, BASE, size, 3, Blocks.OAK_FENCE, Blocks.OAK_PLANKS);

        int rz = size.depth() / 2;
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y < 3; y++) {
                BlockPos post = BASE.offset(side, y, rz + 1);
                assertTrue(blocks.stream().anyMatch(
                                block -> block.pos().equals(post)
                                        && block.state().is(Blocks.OAK_FENCE)),
                        "the porch has no post at " + post.toShortString());
            }
        }
    }

    // --- helpers -------------------------------------------------------------

    /** Asserts every cell the footprint covers has something over the plate. */
    private static void assertRoofedIn(List<BlueprintPlacer.Placement> roof,
                                       BuildingSizes.Size size, int plate, String what) {
        Set<Long> covered = columnsOf(roof, BASE, BASE.getY() + plate);
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (!size.covers(dx, dz)) {
                    continue;
                }
                assertTrue(covered.contains(key(dx, dz)),
                        what + " leaves (" + dx + ", " + dz + ") open to the sky");
            }
        }
    }

    /** Which columns, relative to base, hold a solid block at or above {@code from}. */
    private static Set<Long> columnsOf(List<BlueprintPlacer.Placement> blocks,
                                       BlockPos base, int from) {
        Set<Long> columns = new HashSet<>();
        for (BlueprintPlacer.Placement block : blocks) {
            if (block.pos().getY() < from || block.state().isAir()
                    || block.state().is(Blocks.GLASS)) {
                continue;
            }
            columns.add(key(block.pos().getX() - base.getX(),
                    block.pos().getZ() - base.getZ()));
        }
        return columns;
    }

    private static long key(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }
}
