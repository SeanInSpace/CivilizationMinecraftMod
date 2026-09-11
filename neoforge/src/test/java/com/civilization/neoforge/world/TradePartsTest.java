package com.civilization.neoforge.world;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trade buildings, each asked the things a signature must not cost.
 *
 * <p>{@link TradeParts} exists so that a smithy does not look like a mill. The
 * risk in that is entirely on the other side of the ledger: a forge bay is a hole
 * cut in a wall, a headframe is four posts driven up through a roof, a windmill's
 * sweeps reach a block past the building, and every one of those is a way to
 * break something that used to be safe by accident. So the claims here are the
 * boring ones.
 *
 * <ul>
 *   <li><strong>It stays on its own ground.</strong> The fault
 *       {@link BuildingSizes} was written to end. A sail wheel is the furthest
 *       anything in the mod reaches from its own origin, so it is the newest way
 *       to put a building through the neighbor's wall.</li>
 *   <li><strong>It does not leak.</strong> Everything roofed is roofed over every
 *       cell it stands on, air written into a wall included.</li>
 *   <li><strong>You can still get in, and the post is still there.</strong> The
 *       post is the block that names a building to the player and to the
 *       simulation, and a post walled in behind a log pile is a building nobody
 *       can read or work.</li>
 *   <li><strong>The field and the pens are untouched.</strong> Seventy-one crop
 *       blocks is what an unwatched town's whole harvest is proportional to, and
 *       a pen is what keeps the cows out of the chicken run.</li>
 *   <li><strong>No log is left standing at the top of a column.</strong> Not
 *       decoration: {@code LumberjackWorker} fells whatever log tops a column
 *       inside the village, so a woodpile in the open or a headframe capped in
 *       timber is a building the town's own axemen carry away.</li>
 * </ul>
 *
 * <p>Runs without a world for the reason {@code BlueprintPlacerSizeTest} gives:
 * what a builder draws is decided by the declared shape and the people building
 * it, never by the hillside, so a flat fake {@link BlueprintPlacer.Site} is
 * enough.
 */
class TradePartsTest {

    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    /**
     * Every trade building, and the course its roof plate sits on.
     *
     * <p>The wall height is the one number about a building that lives in its
     * drawing method rather than in {@link BuildingSizes}, so it is written down
     * again here — a leak has to be asserted over the plate, and a check that
     * only looked above the floor would be satisfied by the walls themselves.
     */
    private static final Map<String, Integer> ROOFED = Map.of(
            "smith", 4, "mill", 7, "carpentry", 4, "workshop", 4,
            "lumber_camp", 4, "mine", 4, "granary", 4, "storehouse", 4,
            "warehouse", 8);

    /** The two that are ground rather than building, and have no roof at all. */
    private static final List<String> OPEN = List.of("farm", "animal_farm");

    /** Where somebody walks in. The camp has no door because it has no walls. */
    private static int wayIn(String path) {
        return path.equals("lumber_camp") ? 1 : 0;
    }

    /** Blocks a person walks through rather than into. */
    private static boolean walkable(BlockState state) {
        return state == null || state.isAir() || state.is(Blocks.LANTERN)
                || state.is(Blocks.RAIL) || state.is(Blocks.LADDER);
    }

    private static BlueprintPlacer.Site flatFor(Culture culture) {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= 64; }
            @Override public Culture culture() { return culture; }
            @Override public int groundLevel(int x, int z) { return 64; }
        };
    }

    private static List<BlueprintPlacer.Placement> drawn(Culture culture, String path) {
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BlueprintPlacer.draw(flatFor(culture), blocks, path, BASE);
        return blocks;
    }

    /**
     * What the building actually ends up made of, one entry per cell.
     *
     * <p>Last write wins, because that is what laying blocks into a world does
     * and what {@code BlueprintPlacer.supersededIn} says a plan means. It matters
     * everywhere here: a forge bay is air written over a wall that was already
     * drawn, and reading the raw list would find the wall still standing.
     */
    private static Map<BlockPos, BlockState> world(Culture culture, String path) {
        Map<BlockPos, BlockState> world = new LinkedHashMap<>();
        for (BlueprintPlacer.Placement block : drawn(culture, path)) {
            world.put(block.pos(), block.state());
        }
        return world;
    }

    private static List<String> everything() {
        List<String> all = new ArrayList<>(ROOFED.keySet());
        all.addAll(OPEN);
        return all;
    }

    /** The size a building came out, which for the compound is the culture's. */
    private static int[] dimsOf(Culture culture, String path) {
        return BlueprintPlacer.draw(flatFor(culture), new ArrayList<>(), path, BASE);
    }

    // --- it stays on its own ground -------------------------------------------

    @Test
    void nothingATradeBuildingDrawsLeavesItsOwnPlot() {
        for (Culture culture : Culture.all()) {
            for (String path : everything()) {
                int[] dims = dimsOf(culture, path);
                int rx = dims[0] / 2 + BuildingSizes.APRON;
                int rz = dims[1] / 2 + BuildingSizes.APRON;
                for (BlueprintPlacer.Placement block : drawn(culture, path)) {
                    int dx = block.pos().getX() - BASE.getX();
                    int dz = block.pos().getZ() - BASE.getZ();
                    assertTrue(Math.abs(dx) <= rx && Math.abs(dz) <= rz,
                            culture.id() + "'s " + path + " lays a "
                                    + block.state().getBlock() + " at (" + dx + ", " + dz
                                    + "), outside the ground staked for it: the plot"
                                    + " reaches " + rx + " by " + rz);
                }
            }
        }
    }

    @Test
    void theWindmillsSweepsActuallyReachPastTheWallTheyAreHungOn() {
        // The other half of the bound above, which on its own is satisfied by a
        // mill with no sails at all.
        int rz = BuildingSizes.of("civilization:mill").depth() / 2;
        for (Culture culture : Culture.all()) {
            assertTrue(drawn(culture, "mill").stream().anyMatch(block ->
                            block.pos().getZ() - BASE.getZ() == -(rz + BuildingSizes.APRON)
                                    && block.state().is(Blocks.WOOL.white())),
                    culture.id() + "'s mill has no canvas out past its rear wall,"
                            + " which is a tower and not a windmill");
        }
    }

    // --- it does not leak ------------------------------------------------------

    @Test
    void everyRoofedTradeBuildingIsRoofedOverEveryCellItStandsOn() {
        for (Culture culture : Culture.all()) {
            ROOFED.forEach((path, plate) -> {
                Map<BlockPos, BlockState> world = world(culture, path);
                BuildingSizes.Size size = BuildingSizes.of("civilization:" + path);
                Set<Long> covered = new HashSet<>();
                world.forEach((pos, state) -> {
                    if (pos.getY() >= BASE.getY() + plate && !state.isAir()
                            && !state.is(Blocks.GLASS)) {
                        covered.add(key(pos.getX() - BASE.getX(), pos.getZ() - BASE.getZ()));
                    }
                });
                int rx = size.width() / 2;
                int rz = size.depth() / 2;
                for (int dx = -rx; dx <= rx; dx++) {
                    for (int dz = -rz; dz <= rz; dz++) {
                        assertTrue(covered.contains(key(dx, dz)),
                                culture.id() + "'s " + path + " is open to the sky at ("
                                        + dx + ", " + dz + ")");
                    }
                }
            });
        }
    }

    // --- you can get in, and the post is still there ---------------------------

    @Test
    void everyTradeBuildingStandsThePostThatNamesIt() {
        for (Culture culture : Culture.all()) {
            for (String path : everything()) {
                Block post = BlueprintPlacer.postFor(path);
                assertTrue(world(culture, path).values().stream()
                                .anyMatch(state -> state.is(post)),
                        culture.id() + "'s " + path + " has no " + post + " left"
                                + " standing once the plan is laid: something the"
                                + " drawing does afterwards has written over it");
            }
        }
    }

    @Test
    void thereIsAWayInFromTheDoorToThePost() {
        for (Culture culture : Culture.all()) {
            for (String path : ROOFED.keySet()) {
                Map<BlockPos, BlockState> world = world(culture, path);
                int[] dims = dimsOf(culture, path);
                Block post = BlueprintPlacer.postFor(path);
                BlockPos at = world.entrySet().stream()
                        .filter(cell -> cell.getValue().is(post))
                        .map(Map.Entry::getKey).findFirst().orElseThrow();

                int rx = dims[0] / 2;
                int rz = dims[1] / 2;
                Deque<long[]> queue = new ArrayDeque<>();
                Set<Long> seen = new HashSet<>();
                long[] door = {wayIn(path), rz};
                queue.add(door);
                seen.add(key((int) door[0], (int) door[1]));
                boolean arrived = false;
                while (!queue.isEmpty() && !arrived) {
                    long[] cell = queue.poll();
                    for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int dx = (int) cell[0] + step[0];
                        int dz = (int) cell[1] + step[1];
                        if (Math.abs(dx) > rx || Math.abs(dz) > rz
                                || !seen.add(key(dx, dz))) {
                            continue;
                        }
                        if (BASE.offset(dx, 1, dz).equals(at)) {
                            arrived = true;
                            break;
                        }
                        if (walkable(world.get(BASE.offset(dx, 1, dz)))
                                && walkable(world.get(BASE.offset(dx, 2, dz)))) {
                            queue.add(new long[]{dx, dz});
                        }
                    }
                }
                assertTrue(arrived, culture.id() + "'s " + path + " has its post at "
                        + at.subtract(BASE).toShortString() + " walled off from its own"
                        + " doorway: nobody can walk in and read it");
            }
        }
    }

    // --- the field and the pens ------------------------------------------------

    @Test
    void dressingAFarmsFenceTakesNoCropWithIt() {
        for (Culture culture : Culture.all()) {
            long wheat = world(culture, "farm").values().stream()
                    .filter(state -> state.is(Blocks.WHEAT)).count();

            assertEquals(Field.CROP_BLOCKS, (int) wheat,
                    culture.id() + "'s field has " + wheat + " crop blocks in it."
                            + " Field.CROP_BLOCKS says " + Field.CROP_BLOCKS + ", and"
                            + " that constant is what an unwatched town's whole"
                            + " harvest is proportional to");
        }
    }

    @Test
    void aScarecrowStandsOutsideTheCropRatherThanInIt() {
        int r = BuildingSizes.of("civilization:farm").width() / 2;
        for (Culture culture : Culture.all()) {
            BlockPos head = BASE.offset(-(r + BuildingSizes.APRON), 2,
                    -(r + BuildingSizes.APRON));
            assertTrue(world(culture, "farm").get(head) != null
                            && world(culture, "farm").get(head).is(Blocks.CARVED_PUMPKIN),
                    culture.id() + "'s farm has no scarecrow on its far corner");
        }
    }

    @Test
    void aByreNeverTakesAPenOrTheSpotAShepherdPutsABeastDown() {
        for (Culture culture : Culture.all()) {
            int[] dims = dimsOf(culture, "animal_farm");
            int rx = dims[0] / 2;
            int rz = dims[1] / 2;
            Map<BlockPos, BlockState> world = world(culture, "animal_farm");

            long gates = world.values().stream()
                    .filter(state -> state.getBlock() instanceof
                            net.minecraft.world.level.block.FenceGateBlock)
                    .count();
            int pens = Math.min(4, Math.max(1, culture.penCount()));
            assertEquals(pens, (int) gates, culture.id() + " keeps " + culture.penCount()
                    + " beasts and its compound has " + gates + " gates in it");
            assertEquals(pens * 4 + 1, dims[1],
                    culture.id() + "'s compound is the wrong number of pens deep");

            // ShepherdWorker spawns at the middle of each pen's box. The byre
            // stands at the head of the first one, and must not be there.
            for (int pen = 0; pen < pens; pen++) {
                BlockPos spot = BASE.offset(0, 1, -rz + 2 + pen * 4);
                assertTrue(walkable(world.get(spot)), culture.id() + "'s compound has"
                        + " something standing where a beast is put down in pen " + pen);
            }
            assertTrue(rx == 4, "the compound stopped being nine wide");
        }
    }

    // --- and the lumberjacks leave it alone -------------------------------------

    @Test
    void noTradeBuildingLeavesALogAtTheTopOfAColumn() {
        // LumberjackWorker reads the top of every column inside the village and
        // fells it if it is a log. A woodpile stacked in the open, or a headframe
        // capped in timber, is therefore a building the town takes down again --
        // and it would look exactly like decay rather than like a bug.
        for (Culture culture : Culture.all()) {
            for (String path : everything()) {
                Map<Long, BlockPos> highest = new LinkedHashMap<>();
                world(culture, path).forEach((pos, state) -> {
                    if (state.isAir()) {
                        return;
                    }
                    long column = key(pos.getX() - BASE.getX(), pos.getZ() - BASE.getZ());
                    BlockPos standing = highest.get(column);
                    if (standing == null || pos.getY() > standing.getY()) {
                        highest.put(column, pos);
                    }
                });
                Map<BlockPos, BlockState> world = world(culture, path);
                for (BlockPos pos : highest.values()) {
                    assertFalse(isLog(world.get(pos).getBlock()),
                            culture.id() + "'s " + path + " leaves a "
                                    + world.get(pos).getBlock() + " at the top of the"
                                    + " column " + pos.subtract(BASE).toShortString()
                                    + ", which its own lumberjacks will fell");
                }
            }
        }
    }

    private static boolean isLog(Block block) {
        return block == Blocks.OAK_LOG || block == Blocks.SPRUCE_LOG
                || block == Blocks.DARK_OAK_LOG || block == Blocks.STRIPPED_OAK_LOG
                || block == Blocks.STRIPPED_SPRUCE_LOG
                || block == Blocks.STRIPPED_DARK_OAK_LOG
                || block == Blocks.STRIPPED_OAK_WOOD;
    }

    // --- and it draws the same thing twice --------------------------------------

    @Test
    void adrawingIsTheSameEveryTimeItIsAskedFor() {
        // A repair is the difference between the drawing and what is standing, so
        // a building that came out differently on the second pass would have a
        // crew forever knocking down and relaying whatever changed its mind.
        for (String path : everything()) {
            List<BlueprintPlacer.Placement> once = drawn(Culture.VALE, path);
            List<BlueprintPlacer.Placement> twice = drawn(Culture.VALE, path);
            assertEquals(once, twice, path + " draws something different the second"
                    + " time it is asked");
        }
    }

    @Test
    void twoBuildingsOfTheSamePeopleStandingApartAreStillTheSameShape() {
        // The variation is hashed out of the origin on purpose, and a part that
        // read something else -- a die, a clock -- would show up here as two
        // mills of different sizes.
        for (String path : everything()) {
            int[] here = BlueprintPlacer.draw(flatFor(Culture.BURGHER),
                    new ArrayList<>(), path, BASE);
            int[] there = BlueprintPlacer.draw(flatFor(Culture.BURGHER),
                    new ArrayList<>(), path, BASE.offset(640, 0, -320));

            assertEquals(here[0], there[0], path + " changes width across the map");
            assertEquals(here[1], there[1], path + " changes depth across the map");
        }
    }

    private static long key(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }
}
