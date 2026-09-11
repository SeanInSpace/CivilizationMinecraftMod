package com.civilization.neoforge.world;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.settlement.Beds;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two round buildings, asked the things a cone can get wrong.
 *
 * <p>{@link PartsTest} holds every rectangular home to these same rules, and it
 * does it over a footprint that is a rectangle — which is exactly why these two
 * need their own file rather than a line in its table. A hut is an octagon, so
 * "watertight" means watertight over the octagon, and the cells the shape cuts
 * away at the corners are ground the roof is allowed to overhang and the walls
 * are required not to stand on.
 *
 * <p>Runs without a world, for the reason {@code BlueprintPlacerSizeTest} spells
 * out: a drawing is a pure function of the base, the declared size and the
 * culture's palette, and a flat fake site satisfies everything it asks the
 * ground.
 */
class RoundHouseTest {

    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    /** The round homes, and how high each one's wall stands under its cone. */
    private static final Map<String, Integer> ROUND = Map.of(
            "hut", 3, "great_hut", 5);

    private static BlueprintPlacer.Site flatFor(Culture culture) {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) {
                return true;
            }

            @Override public boolean unsupported(BlockPos pos) {
                return pos.getY() >= 64;
            }

            @Override public Culture culture() {
                return culture;
            }

            @Override public int groundLevel(int x, int z) {
                return 64;
            }
        };
    }

    private static List<BlueprintPlacer.Placement> drawn(Culture culture, String path) {
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BlueprintPlacer.draw(flatFor(culture), blocks, path, BASE);
        return blocks;
    }

    // --- the shape is actually round ----------------------------------------

    @Test
    void bothRoundHomesAreOctagonsAndNotSquares() {
        // The claim the chamfer exists to make. Said out loud because a Size
        // whose chamfer went to zero would still pass every other test in this
        // file -- it would simply be a square hut, watertight and in its plot.
        for (String round : ROUND.keySet()) {
            BuildingSizes.Size size = BuildingSizes.of("civilization:" + round);
            int rx = size.width() / 2;
            int rz = size.depth() / 2;
            assertFalse(size.covers(rx, rz),
                    round + " covers its own box corner, so it is a square");
            assertTrue(size.covers(rx, 0), round + " has lost its east face");
            assertTrue(size.covers(0, rz), round + " has lost its south face");
            assertTrue(size.covers(0, 0), "the middle is always the building");
        }
    }

    @Test
    void aConeStepsInOnEveryCourseRatherThanStandingOnAWall() {
        // What makes it read as a cone rather than as a hat. Each course of the
        // roof has to cover strictly fewer columns than the one below it, all
        // the way to a single block at the peak -- a course that held its width
        // would be a drum, and two courses the same would be a step somebody
        // could stand on.
        for (Map.Entry<String, Integer> round : ROUND.entrySet()) {
            List<BlueprintPlacer.Placement> blocks =
                    drawn(Culture.ORC, round.getKey());
            int plate = BASE.getY() + round.getValue() + 1;
            int top = 0;
            for (BlueprintPlacer.Placement block : blocks) {
                top = Math.max(top, block.pos().getY());
            }
            assertTrue(top >= plate + 2,
                    round.getKey() + " has a roof only " + (top - plate)
                            + " courses tall, which is a lid rather than a cone");

            int below = Integer.MAX_VALUE;
            for (int y = plate; y <= top; y++) {
                int wide = columnsAt(blocks, y).size();
                assertTrue(wide < below,
                        round.getKey() + " covers " + wide + " columns at course "
                                + (y - plate) + " and " + below + " below it — a cone"
                                + " narrows on every course");
                below = wide;
            }
            assertEquals(1, below,
                    round.getKey() + " comes to a ridge rather than to a point");
        }
    }

    // --- and it is a building -------------------------------------------------

    @Test
    void everyRoundHomeIsRoofedOverEveryCellOfItsOwnFloor() {
        // Watertight over the octagon, which is the footprint, rather than over
        // the box the octagon is cut out of. The corners are open ground.
        for (Culture culture : Culture.all()) {
            for (Map.Entry<String, Integer> round : ROUND.entrySet()) {
                BuildingSizes.Size size =
                        BuildingSizes.of("civilization:" + round.getKey());
                Set<Long> covered = columnsAtOrAbove(
                        drawn(culture, round.getKey()), BASE.getY() + round.getValue() + 1);
                int rx = size.width() / 2;
                int rz = size.depth() / 2;
                for (int dx = -rx; dx <= rx; dx++) {
                    for (int dz = -rz; dz <= rz; dz++) {
                        if (!size.covers(dx, dz)) {
                            continue;
                        }
                        assertTrue(covered.contains(key(dx, dz)),
                                culture.id() + "'s " + round.getKey() + " is open to"
                                        + " the sky at (" + dx + ", " + dz + ")");
                    }
                }
            }
        }
    }

    @Test
    void nothingARoundHomeDrawsLeavesItsOwnPlot() {
        // The fault BuildingSizes exists to end. A cone's eave overhangs by one
        // block, which is the doorstep ring and not a block more.
        for (Culture culture : Culture.all()) {
            for (String round : ROUND.keySet()) {
                BuildingSizes.Size size = BuildingSizes.of("civilization:" + round);
                int rx = size.width() / 2 + BuildingSizes.APRON;
                int rz = size.depth() / 2 + BuildingSizes.APRON;
                for (BlueprintPlacer.Placement block : drawn(culture, round)) {
                    int dx = block.pos().getX() - BASE.getX();
                    int dz = block.pos().getZ() - BASE.getZ();
                    assertTrue(Math.abs(dx) <= rx && Math.abs(dz) <= rz,
                            culture.id() + "'s " + round + " lays a "
                                    + block.state().getBlock() + " at (" + dx + ", "
                                    + dz + "), outside the " + rx + " by " + rz
                                    + " staked for it");
                }
            }
        }
    }

    @Test
    void noWallStandsOnGroundTheShapeCutAway() {
        // The other half of being round. A wall loop that walked the box rather
        // than the shape would put four corner posts out in the open and the
        // hut would be a square with a cone on it.
        for (String round : ROUND.keySet()) {
            BuildingSizes.Size size = BuildingSizes.of("civilization:" + round);
            int rx = size.width() / 2;
            int rz = size.depth() / 2;
            Set<BlockPos> filled = new HashSet<>();
            for (BlueprintPlacer.Placement block : drawn(Culture.ORC, round)) {
                filled.add(block.pos());
            }
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    if (size.covers(dx, dz)) {
                        continue;
                    }
                    assertFalse(filled.contains(BASE.offset(dx, 1, dz)),
                            round + " built a wall at (" + dx + ", " + dz + "), in a"
                                    + " corner its own shape cuts away");
                }
            }
        }
    }

    @Test
    void aDoorwayIsStillADoorwayAndTheFireIsNotInABed() {
        for (Culture culture : Culture.all()) {
            for (String round : ROUND.keySet()) {
                BuildingSizes.Size size = BuildingSizes.of("civilization:" + round);
                int rz = size.depth() / 2;
                Set<BlockPos> filled = new HashSet<>();
                for (BlueprintPlacer.Placement block : drawn(culture, round)) {
                    filled.add(block.pos());
                }
                for (int y = 1; y <= 2; y++) {
                    assertFalse(filled.contains(BASE.offset(0, y, rz)),
                            culture.id() + "'s " + round + " has something standing"
                                    + " in its own doorway at height " + y);
                }
            }
        }
    }

    // --- and the beds are where the simulation believes ----------------------

    @Test
    void everyBedTheCatalogPromisesIsActuallyLaid() {
        // The one drift that would be invisible in the world: a settler is sent
        // to a bed by a pure function that never reads a block, so a home with
        // fewer beds drawn than declared is a person walking to thin air.
        for (BuildingType type : BuildCatalog.DEFAULT) {
            String path = type.id().substring(type.id().indexOf(':') + 1);
            if (!ROUND.containsKey(path)) {
                continue;
            }
            assertEquals(type.capacity(), Beds.countIn(type.id()),
                    path + " holds " + type.capacity() + " and lays "
                            + Beds.countIn(type.id()) + " beds");

            long halves = drawn(Culture.ORC, path).stream()
                    .filter(block -> block.state().is(
                            Blocks.BED.pick(net.minecraft.world.item.DyeColor.BLACK)))
                    .map(BlueprintPlacer.Placement::pos)
                    .distinct()
                    .count();
            assertEquals(2L * type.capacity(), halves,
                    path + " drew " + halves + " bed halves for "
                            + type.capacity() + " heads");
        }
    }

    @Test
    void theBuildingPostHasACellOfItsOwn() {
        // A post sharing a cell with a bed, a barrel or the lantern is a post
        // that is quietly missing from the finished building -- and the post is
        // the block a player right-clicks to find out what they are looking at.
        Map<String, BlockPos> posts = Map.of(
                "hut", BASE.offset(0, 1, -2),
                "great_hut", BASE.offset(0, 1, -5));
        posts.forEach((path, post) -> {
            Map<BlockPos, BlockState> last = new HashMap<>();
            for (BlueprintPlacer.Placement block : drawn(Culture.ORC, path)) {
                last.put(block.pos(), block.state());
            }
            assertEquals(BlueprintPlacer.postFor(path),
                    last.get(post).getBlock(),
                    path + " does not leave its own post standing at "
                            + post.toShortString() + "; something drawn after it"
                            + " took the cell");
        });
    }

    // --- and it survives being turned to face its street ---------------------

    @Test
    void aTurnedConeIsStillWatertight() {
        // Positions and block states are turned by two different mechanisms and
        // a roof that survives one and not the other is a roof with a slot in
        // it. A cone is the worst case: every course of it is corner stairs.
        BuildingSizes.Size size = BuildingSizes.of("civilization:hut");
        for (Rotation rotation : Rotation.values()) {
            List<BlueprintPlacer.Placement> turned =
                    new ArrayList<>(drawn(Culture.ORC, "hut"));
            BlueprintPlacer.turn(turned, BASE, rotation);
            Set<Long> covered = columnsAtOrAbove(turned, BASE.getY() + 4);
            int r = size.width() / 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!size.covers(dx, dz)) {
                        continue;   // a square footprint, so a turn does not move it
                    }
                    assertTrue(covered.contains(key(dx, dz)),
                            "a hut turned " + rotation + " is open at ("
                                    + dx + ", " + dz + ")");
                }
            }
        }
    }

    // --- helpers -------------------------------------------------------------

    private static Set<Long> columnsAt(List<BlueprintPlacer.Placement> blocks, int y) {
        Set<Long> columns = new HashSet<>();
        for (BlueprintPlacer.Placement block : blocks) {
            if (block.pos().getY() != y || block.state().isAir()) {
                continue;
            }
            columns.add(key(block.pos().getX() - BASE.getX(),
                    block.pos().getZ() - BASE.getZ()));
        }
        return columns;
    }

    private static Set<Long> columnsAtOrAbove(List<BlueprintPlacer.Placement> blocks,
                                              int from) {
        Set<Long> columns = new HashSet<>();
        for (BlueprintPlacer.Placement block : blocks) {
            if (block.pos().getY() < from || block.state().isAir()
                    || block.state().is(Blocks.GLASS)) {
                continue;
            }
            columns.add(key(block.pos().getX() - BASE.getX(),
                    block.pos().getZ() - BASE.getZ()));
        }
        return columns;
    }

    private static long key(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }
}
