package com.civilization.neoforge.world;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.settlement.BuildingSizes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The civic buildings, asked the things that would ruin one.
 *
 * <p>{@code PartsTest} makes these claims about homes. The civic buildings are a
 * harder case for every one of them, because each has something a home does not:
 * a cupola stacked on a ridge, a gallery hung inside a room, a ladder bolted to
 * the outside of a wall, an awning reaching out over the doorstep. Every one of
 * those is a new way to grow off the plot, leak, or stand in a doorway.
 *
 * <p>Three of the claims are the ones {@code PartsTest} already makes — stays on
 * its own ground, keeps the weather out, does not brick up its own door. Two are
 * new and are about the fact that these buildings are <em>used</em> rather than
 * only looked at:
 * <ul>
 *   <li><strong>The post cell is the post's.</strong> Every one of these is a
 *       building a player walks up to and clicks, and the block they click is at
 *       a fixed offset that the drawing method does not own — the market's is
 *       read back by {@code MarketBlock} to open the screen. Anything else
 *       standing in that cell is a building nobody can talk to.</li>
 *   <li><strong>The walk from the door to the post is open.</strong> Not the same
 *       claim: a post can be in its own cell with a bar counter, a well or an
 *       awning post in front of it.</li>
 * </ul>
 *
 * <p>Runs without a world for the reason {@code BlueprintPlacerSizeTest} sets out:
 * a drawing is a pure function of its declared size and the people building it,
 * and a flat fake site answers everything it asks the ground.
 */
class CivicPartsTest {

    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    /**
     * The roofed civic buildings and the course their roof starts on.
     *
     * <p>The plate is one above the wall head, and the wall heights live as
     * constants in {@link BlueprintPlacer} where the drawing methods can read
     * them. Written down again here on purpose, the same way {@code PartsTest}
     * writes down the homes' wall heights: a leak has to be asserted over the
     * plate rather than merely somewhere above the floor, or the walls themselves
     * would satisfy it.
     */
    private static final Map<String, Integer> ROOFED = roofed();

    private static Map<String, Integer> roofed() {
        Map<String, Integer> plates = new LinkedHashMap<>();
        plates.put("town_hall", 7);    // six courses of wall
        plates.put("inn", 7);          // six, two storeys of them
        plates.put("library", 8);      // seven, with a gallery inside
        plates.put("hearth", 4);       // no walls at all: a canopy on four posts
        return plates;
    }

    /** Where each civic building stands its post, and how far in the walk to it runs. */
    private record Way(int postDx, int postDz, int fromDz, int toDz) {
    }

    private static final Map<String, Way> POSTS = posts();

    private static Map<String, Way> posts() {
        Map<String, Way> where = new LinkedHashMap<>();
        where.put("town_hall", new Way(0, -1, -1, 6));
        where.put("market", new Way(0, -1, -2, -1));
        where.put("inn", new Way(0, -1, -1, 5));
        where.put("library", new Way(0, 7, 0, 9));
        where.put("watchtower", new Way(0, 0, 0, 2));
        where.put("hearth", new Way(0, -2, -2, -1));
        where.put("camp_post", new Way(0, 0, 0, 1));
        where.put("cache", new Way(0, -1, -1, 1));
        return Map.copyOf(where);
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

    private static BuildingSizes.Size sizeOf(String path) {
        return BuildingSizes.of("civilization:" + path);
    }

    // --- it stays on its own ground ------------------------------------------

    @Test
    void nothingACivicBuildingDrawsLeavesItsOwnPlot() {
        // The fault BuildingSizes exists to end, in the form these buildings can
        // newly put it back: an awning, a stable lean-to, a flagpole and a ladder
        // all deliberately reach past a wall, and the doorstep ring is all the
        // room any of them may have.
        for (Culture culture : Culture.all()) {
            for (String path : POSTS.keySet()) {
                BuildingSizes.Size size = sizeOf(path);
                int rx = size.width() / 2 + BuildingSizes.APRON;
                int rz = size.depth() / 2 + BuildingSizes.APRON;

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

    // --- and the roofed ones keep the weather out ----------------------------

    @Test
    void everyRoofedCivicBuildingOfEveryPeopleIsWatertight() {
        for (Culture culture : Culture.all()) {
            ROOFED.forEach((path, plate) -> {
                BuildingSizes.Size size = sizeOf(path);
                Set<Long> covered = columnsAbove(drawn(culture, path), plate);
                int rx = size.width() / 2;
                int rz = size.depth() / 2;
                for (int dx = -rx; dx <= rx; dx++) {
                    for (int dz = -rz; dz <= rz; dz++) {
                        assertTrue(covered.contains(key(dx, dz)),
                                culture.id() + "'s " + path + " leaves (" + dx + ", "
                                        + dz + ") open to the sky");
                    }
                }
            });
        }
    }

    @Test
    void theMarketIsOpenToTheSkyAndThatIsTheWholePoint() {
        // Deliberately exempt from the claim above, and said out loud rather than
        // left as a gap in a table. A market is an open square with a small roof
        // over each trader; the thing that tells it apart from every other box in
        // the town is precisely that you can see the sky from the middle of it.
        // The hearth is NOT exempt — it has no walls either, but it does have a
        // roof, and a fire nothing covers is a fire that goes out.
        for (Culture culture : Culture.all()) {
            // At the course the awnings are on, which is the only thing a market
            // has overhead. The well is below it and does not count as a roof.
            Set<Long> covered = columnsAbove(drawn(culture, "market"), 3);
            assertFalse(covered.contains(key(0, 0)),
                    culture.id() + " roofed over the middle of its market square");
            assertFalse(covered.contains(key(0, -3)),
                    culture.id() + " roofed over the aisle of its market");
        }
    }

    // --- and you can reach the block the whole building answers to -----------

    @Test
    void everyCivicPostStandsAloneInItsOwnCell() {
        for (Culture culture : Culture.all()) {
            POSTS.forEach((path, way) -> {
                Block post = BlueprintPlacer.postFor(path);
                BlockPos cell = BASE.offset(way.postDx(), 1, way.postDz());
                List<BlueprintPlacer.Placement> blocks = drawn(culture, path);

                long there = blocks.stream()
                        .filter(block -> block.pos().equals(cell))
                        .count();
                assertEquals(1, there,
                        culture.id() + "'s " + path + " writes " + there + " blocks at "
                                + "the cell its post stands in; a post sharing its cell"
                                + " is a building nobody can click");
                assertTrue(blocks.stream().anyMatch(
                                block -> block.pos().equals(cell) && block.state().is(post)),
                        culture.id() + "'s " + path + " does not stand its " + post
                                + " at " + cell.subtract(BASE).toShortString());
            });
        }
    }

    @Test
    void theWalkFromTheDoorToThePostIsOpenInEveryCivicBuilding() {
        for (Culture culture : Culture.all()) {
            POSTS.forEach((path, way) -> {
                Block post = BlueprintPlacer.postFor(path);
                Set<BlockPos> filled = new HashSet<>();
                for (BlueprintPlacer.Placement block : drawn(culture, path)) {
                    if (!block.state().is(post)) {
                        filled.add(block.pos());
                    }
                }
                for (int dz = way.fromDz(); dz <= way.toDz(); dz++) {
                    for (int y = 1; y <= 2; y++) {
                        BlockPos step = BASE.offset(way.postDx(), y, dz);
                        assertFalse(filled.contains(step),
                                culture.id() + "'s " + path + " has something standing"
                                        + " at " + step.subtract(BASE).toShortString()
                                        + ", which is on the walk from its door to its"
                                        + " post");
                    }
                }
            });
        }
    }

    @Test
    void theMarketPostHasNotMoved() {
        // MarketBlock reads the clicked block back out of the world to open the
        // screen against the right town, and a live world's markets were all
        // built at this offset. Moving it would leave every standing market with
        // a post the screen does not answer to.
        for (Culture culture : Culture.all()) {
            assertTrue(drawn(culture, "market").stream().anyMatch(
                            block -> block.pos().equals(BASE.offset(0, 1, -1))
                                    && block.state().is(BlueprintPlacer.postFor("market"))),
                    culture.id() + " moved its market post off (0, 1, -1)");
        }
    }

    // --- the two things that have to be somewhere in particular --------------

    @Test
    void theHallsGoldIsTheHighestBlockOnIt() {
        // It used to sit at the top of the wall course, which was fine while a
        // hall had no roof. A pitched roof buries a block at that height in the
        // attic, so the gold moved to the finial of the cupola — and the claim
        // that matters about it is not where it is but that it can be seen: it is
        // the marker a player picks a town's middle out by from a hillside.
        for (Culture culture : Culture.all()) {
            List<BlueprintPlacer.Placement> hall = drawn(culture, "town_hall");
            int gold = hall.stream()
                    .filter(block -> block.state().is(Blocks.GOLD_BLOCK))
                    .mapToInt(block -> block.pos().getY())
                    .max()
                    .orElse(Integer.MIN_VALUE);
            int top = hall.stream()
                    .mapToInt(block -> block.pos().getY())
                    .max()
                    .orElse(0);

            assertEquals(top, gold,
                    culture.id() + "'s hall has something standing over its gold"
                            + " block, so the marker is not the thing you see");
        }
    }

    @Test
    void theHallsRoofIsTallerThanEveryOtherCivicRoof() {
        // A tower is exempt: it is supposed to be the tall thing, and it is a
        // tower rather than a roof. Everything else on the street answers to the
        // hall, which is what the size table and the cupola are both for.
        for (Culture culture : Culture.all()) {
            int hall = topOf(drawn(culture, "town_hall"));
            for (String path : List.of("inn", "library", "market", "hearth")) {
                assertTrue(topOf(drawn(culture, path)) < hall,
                        culture.id() + "'s " + path + " stands over its own town"
                                + " hall, which is the one building that must be"
                                + " the tallest roof on the street");
            }
        }
    }

    @Test
    void theWatchtowersBellIsStillWhereTheWatchLooksForIt() {
        // PersonEntityManager.findBell runs up the tower's own origin column and
        // no further, and the tower just grew two storeys. A bell one course past
        // the end of that search is a town that cannot raise the alarm and says
        // nothing at all about why.
        int search = 24;   // BELL_SEARCH_HEIGHT, which this is pinned against
        for (Culture culture : Culture.all()) {
            assertTrue(drawn(culture, "watchtower").stream().anyMatch(
                            block -> block.state().is(Blocks.BELL)
                                    && block.pos().getX() == BASE.getX()
                                    && block.pos().getZ() == BASE.getZ()
                                    && block.pos().getY() - BASE.getY() <= search),
                    culture.id() + " puts its watchtower bell somewhere the watch"
                            + " cannot find it: it has to be in the origin column,"
                            + " no more than " + search + " up");
        }
    }

    // --- and each one is recognizably itself ---------------------------------

    @Test
    void aWatchtowerHasLoopsRatherThanWindows() {
        // The cheapest difference in the file and one of the loudest: every other
        // building in a town has glass in it, and a tower has gaps. If a pane ever
        // turns up in the shell, the tower has quietly become a lighthouse.
        for (Culture culture : Culture.all()) {
            assertFalse(drawn(culture, "watchtower").stream().anyMatch(
                            block -> block.state().is(Blocks.GLASS)
                                    || block.state().is(Blocks.GLASS_PANE)),
                    culture.id() + " glazed its watchtower");
        }
        // And the loops are really left out, rather than the shell being solid.
        List<BlueprintPlacer.Placement> tower = drawn(Culture.NORMAN, "watchtower");
        Set<BlockPos> filled = new HashSet<>();
        tower.forEach(block -> filled.add(block.pos()));
        assertFalse(filled.contains(BASE.offset(0, 4, -1)),
                "the tower's north wall has no arrow loop in it at all");
    }

    @Test
    void aMarketIsStallsRoundAnEmptyMiddleRatherThanOneBigLid() {
        for (Culture culture : Culture.all()) {
            List<BlueprintPlacer.Placement> market = drawn(culture, "market");
            long overhead = market.stream()
                    .filter(block -> block.pos().getY() - BASE.getY() == 3)
                    .map(BlueprintPlacer.Placement::pos)
                    .distinct()
                    .count();
            assertTrue(overhead >= 24 && overhead <= 45,
                    culture.id() + " draws " + overhead + " blocks overhead; six stalls"
                            + " of six is thirty-six, and eighty-one would be the old"
                            + " lid back again");
            assertTrue(market.stream().anyMatch(
                            block -> block.state().is(Blocks.WATER_CAULDRON)),
                    culture.id() + "'s market square has no well in it");
        }
    }

    @Test
    void nothingACivicBuildingDrawsWouldReadAsAFlood() {
        // The auditor sweeps the rooms of every building for fluid and reports it
        // as a building standing in a lake. The market's well is the one thing
        // here that is meant to hold water, and it holds it as a cauldron for
        // exactly this reason — a source block in the middle of the square would
        // have every market in the kingdom reported as flooded, once a sweep,
        // forever.
        for (Culture culture : Culture.all()) {
            for (String path : POSTS.keySet()) {
                assertFalse(drawn(culture, path).stream().anyMatch(
                                block -> !block.state().getFluidState().isEmpty()),
                        culture.id() + "'s " + path + " lays a fluid inside its own"
                                + " walls, which the auditor reads as a flood");
            }
        }
    }

    @Test
    void anInnHasASignAndSomewhereToPutAHorse() {
        for (Culture culture : Culture.all()) {
            List<BlueprintPlacer.Placement> inn = drawn(culture, "inn");
            assertTrue(inn.stream().anyMatch(block -> block.state().is(Blocks.OAK_WALL_SIGN)),
                    culture.id() + "'s inn has nothing over the door to say it is one");
            assertTrue(inn.stream().anyMatch(block -> block.state().is(Blocks.HAY_BLOCK)),
                    culture.id() + "'s inn has no fodder in its stable");
            // The upper floor, which is what two storeys actually means inside.
            assertTrue(inn.stream().anyMatch(
                            block -> block.pos().getY() - BASE.getY() == 4
                                    && Math.abs(block.pos().getX() - BASE.getX()) <= 4
                                    && Math.abs(block.pos().getZ() - BASE.getZ()) <= 3),
                    culture.id() + "'s inn has no second floor in it");
        }
    }

    @Test
    void aLibraryIsShelvesAndAGalleryRatherThanARoomWithBooksInIt() {
        for (Culture culture : Culture.all()) {
            List<BlueprintPlacer.Placement> library = drawn(culture, "library");
            long shelves = library.stream()
                    .filter(block -> block.state().is(Blocks.BOOKSHELF))
                    .count();
            assertTrue(shelves >= 100,
                    culture.id() + " lines its library with " + shelves + " shelves,"
                            + " which is not a wall of books");
            assertTrue(library.stream().anyMatch(block -> block.state().is(Blocks.LECTERN)),
                    culture.id() + "'s library has nowhere to read");
            assertTrue(library.stream().anyMatch(block -> block.state().is(Blocks.GLASS_PANE)),
                    culture.id() + "'s library has no tall windows in it");
        }
    }

    @Test
    void theOpenAirCampBuildingsCarryTheirPeoplesColors() {
        for (Culture culture : Culture.all()) {
            assertTrue(drawn(culture, "camp_post").stream().anyMatch(
                            block -> block.state().getBlock() instanceof BannerBlock),
                    culture.id() + " plants no colors on its claim");
            long cloth = drawn(culture, "cache").stream()
                    .filter(block -> block.pos().getY() - BASE.getY() == 3)
                    .count();
            assertEquals(9, cloth,
                    culture.id() + "'s cache has no tarpaulin over its barrels");
        }
    }

    // --- helpers -------------------------------------------------------------

    /** Which columns, relative to base, hold a solid block at or above a course. */
    private static Set<Long> columnsAbove(List<BlueprintPlacer.Placement> blocks, int from) {
        Set<Long> columns = new HashSet<>();
        for (BlueprintPlacer.Placement block : blocks) {
            if (block.pos().getY() - BASE.getY() < from || block.state().isAir()
                    || block.state().is(Blocks.GLASS) || block.state().is(Blocks.GLASS_PANE)) {
                continue;
            }
            columns.add(key(block.pos().getX() - BASE.getX(),
                    block.pos().getZ() - BASE.getZ()));
        }
        return columns;
    }

    private static int topOf(List<BlueprintPlacer.Placement> blocks) {
        return blocks.stream()
                .mapToInt(block -> block.pos().getY() - BASE.getY())
                .max()
                .orElse(0);
    }

    private static long key(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }
}
