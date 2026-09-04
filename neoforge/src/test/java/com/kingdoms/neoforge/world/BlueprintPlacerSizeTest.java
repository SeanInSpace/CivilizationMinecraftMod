package com.kingdoms.neoforge.world;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the placer draws, against what the catalogue reserves ground for.
 *
 * <p>This is the check the goals list has been asking for since a town was found
 * with its buildings standing through each other. {@link BuildingSizes} is one
 * table and {@code BuildingSizesTest} already pins the catalogue to it, so a plot
 * is the size the table says. Nothing pinned the other half — the drawing methods
 * in {@link BlueprintPlacer} — because drawing wanted a running level, and the
 * only guard was a {@code SIZE MISMATCH} line in the log of a live game.
 *
 * <p><strong>That guard was not enough, and it is not a hypothetical.</strong>
 * The market was converted to read its size from the table and kept a literal
 * {@code return new int[]{5, 5, 4}} in its return statement, so it went on being
 * drawn five wide where the table declares nine — through a whole in-world run,
 * until somebody diffed that run's log against the table. A test that runs on
 * every build would have caught it in seconds.
 *
 * <p><strong>Why it can run without a world.</strong> The width and depth a
 * builder reports depend on the declared size and its own static geometry, and on
 * nothing else. What the ground is doing decides how much cobble goes in
 * underneath and how far a flight of steps runs; it never moves a wall. So
 * {@link BlueprintPlacer#draw} takes a {@link BlueprintPlacer.Site} rather than a
 * {@code ServerLevel}, and a flat fake site satisfies it. The one shape whose size
 * is not purely static is the compound, which is as many pens as its people keep
 * beasts — so the site carries a culture too, and that case is tested across every
 * culture there is rather than exempted.
 *
 * <p>The log line stays in {@code procedural}. It sees the one thing this cannot:
 * a size that came out wrong because of where the building was put rather than
 * because of what is written in the switch.
 */
class BlueprintPlacerSizeTest {

    /**
     * A plot on ground whose first air block is 64, which is where a floor goes.
     *
     * <p>Any position would do — the point of the seam is that none of this
     * reaches the drawn size — but a plausible one keeps the fixture honest.
     */
    private static final BlockPos BASE = new BlockPos(0, 63, 0);

    /** Level ground under everything, and a people who keep whatever they keep. */
    private static BlueprintPlacer.Site flatFor(Culture culture) {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= 64; }
            @Override public Culture culture() { return culture; }
            @Override public int groundLevel(int x, int z) { return 64; }
        };
    }

    /**
     * Nothing underfoot at all: a plot hanging over a void.
     *
     * <p>The surface is the world floor rather than {@code Integer.MIN_VALUE},
     * which reads as the same thing and is not: {@code accessStairs} works in
     * {@code groundLevel(x, z) - 1}, and one below the smallest int is the
     * largest one, so a fixture saying "no ground anywhere" would have told the
     * steps they had already met the hillside.
     */
    private static BlueprintPlacer.Site emptyAir() {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return true; }
            @Override public Culture culture() { return Culture.DEFAULT; }
            @Override public int groundLevel(int x, int z) { return -64; }
        };
    }

    /** The blueprint path a catalogue id is drawn by: {@code kingdoms:mill} to {@code mill}. */
    private static String pathOf(BuildingType type) {
        String id = type.id();
        return id.substring(id.indexOf(':') + 1);
    }

    private static int[] drawnOn(BlueprintPlacer.Site site, String path) {
        return BlueprintPlacer.draw(site, new ArrayList<>(), path, BASE);
    }

    private static int[] drawn(String path) {
        return drawnOn(flatFor(Culture.DEFAULT), path);
    }

    // --- the check the whole file exists for --------------------------------

    @Test
    void everyBuildingIsDrawnTheSizeItsPlotWasReservedFor() {
        for (BuildingType type : BuildCatalogue.DEFAULT) {
            if (BuildingSizes.variesWithCulture(type.id())) {
                continue;   // the compound, which has its own test below
            }
            String path = pathOf(type);
            BuildingSizes.Size declared = BuildingSizes.of(type.id());
            int[] dims = drawn(path);

            assertEquals(declared.width(), dims[0],
                    path + " is declared " + declared.width() + " across and drawn "
                            + dims[0] + ". The plot reserved for it comes from the"
                            + " declared number, so this is a building standing in"
                            + " ground that was never set aside for it");
            assertEquals(declared.depth(), dims[1],
                    path + " is declared " + declared.depth() + " deep and drawn "
                            + dims[1] + ", against a plot reserved for the declared one");
        }
    }

    @Test
    void everyBuildingStandsItsOwnPostRatherThanSomebodyElsesShape() {
        // Sizes alone cannot tell these apart, and two ways of getting the wrong
        // building are invisible without this. An id with no case in the switch
        // is drawn as the five-by-five marker, which is exactly the size of a
        // hearth and of a watchtower. And six kinds -- cottage, mill, carpentry,
        // lumber_camp, mine, granary -- are all seven by seven, so drawing a mill
        // where a mine belongs measures perfectly.
        //
        // The post is what actually names a building to the player and to the
        // simulation, so a shape carrying the wrong one is the wrong building
        // whatever it measures.
        for (BuildingType type : BuildCatalogue.DEFAULT) {
            String path = pathOf(type);
            Block post = BlueprintPlacer.postFor(path);
            assertNotNull(post, path + " is in the catalogue and has no post block");

            List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
            BlueprintPlacer.draw(flatFor(Culture.DEFAULT), blocks, path, BASE);

            assertTrue(blocks.stream().anyMatch(b -> b.state().is(post)),
                    path + " draws no " + post + ", so either nothing in the switch"
                            + " answers to it -- and it is being built as the"
                            + " unknown-blueprint marker -- or it is being drawn as"
                            + " some other building of the same size");
        }
    }

    @Test
    void whatIsDrawnDoesNotDependOnWhatIsUnderneathIt() {
        // The premise the seam rests on, asserted rather than assumed. If a shape
        // ever did size itself off the ground, every number above would be a
        // measurement of the fixture instead of of the building.
        for (BuildingType type : BuildCatalogue.DEFAULT) {
            String path = pathOf(type);
            int[] onGround = drawnOn(flatFor(Culture.DEFAULT), path);
            int[] overAVoid = drawnOn(emptyAir(), path);

            assertEquals(onGround[0], overAVoid[0], path + " changes width over a drop");
            assertEquals(onGround[1], overAVoid[1], path + " changes depth over a drop");
            assertEquals(onGround[2], overAVoid[2], path + " changes height over a drop");
        }
    }

    // --- the compound, which is the one size a culture has a say in ----------

    @Test
    void theCompoundIsNeverBiggerThanTheGroundStakedForIt() {
        BuildingSizes.Size declared = BuildingSizes.of("kingdoms:animal_farm");
        for (Culture culture : Culture.all()) {
            int[] dims = drawnOn(flatFor(culture), "animal_farm");

            assertEquals(declared.width(), dims[0],
                    culture.id() + " draws a compound of a different width; only the"
                            + " depth is a culture's business");
            assertTrue(dims[1] <= declared.depth(),
                    culture.id() + " keeps " + culture.penCount() + " beasts and draws "
                            + dims[1] + " deep on ground staked at " + declared.depth()
                            + ", so a pen runs through the neighbour's wall");
        }
    }

    @Test
    void aPeopleWhoKeepFourBeastsFillTheCompoundExactly() {
        // Seventeen deep is four pens of three with a fence between each and one
        // at either end, and it is why the table says seventeen. Every culture
        // defined today keeps four beasts, so this is the compound actually built
        // in a game rather than a corner of the arithmetic -- which is why the
        // exact equality is asserted here and only an upper bound above.
        BuildingSizes.Size declared = BuildingSizes.of("kingdoms:animal_farm");
        Culture fourBeasts = new Culture("kingdoms:test_four",
                List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig",
                        "minecraft:chicken"),
                Culture.LAYOUT_RING);

        assertEquals(declared.depth(), drawnOn(flatFor(fourBeasts), "animal_farm")[1]);
    }

    @Test
    void aPeopleWhoKeepFewerBeastsGetAShorterCompound() {
        // The stated exemption in BuildingSizes.variesWithCulture, exercised. A
        // strip of two pens is genuinely shorter than the plot, and the SIZE
        // MISMATCH check must not read that as a fault.
        Culture twoBeasts = new Culture("kingdoms:test_two",
                List.of("minecraft:cow", "minecraft:chicken"), Culture.LAYOUT_RING);

        assertEquals(9, drawnOn(flatFor(twoBeasts), "animal_farm")[1],
                "two pens of three, a fence between them and one at each end");
    }

    @Test
    void aPeopleWhoKeepMoreBeastsThanTheGroundHoldsAreClamped() {
        // The other half of the exemption, and the one that would stack a town:
        // the plot is staked before anybody asks how many beasts these people
        // keep, so a sixth pen is a fence through next door rather than a bigger
        // farm. Widening the reservation is what a sixth pen costs.
        BuildingSizes.Size declared = BuildingSizes.of("kingdoms:animal_farm");
        Culture sixBeasts = new Culture("kingdoms:test_six",
                List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig",
                        "minecraft:chicken", "minecraft:goat", "minecraft:rabbit"),
                Culture.LAYOUT_RING);

        assertEquals(declared.depth(), drawnOn(flatFor(sixBeasts), "animal_farm")[1],
                "a greedy people gets the compound the ground allows, not the one"
                        + " they asked for");
    }

    // --- the one shape that is deliberately not its own footprint ------------

    @Test
    void aFlightOfStepsReportsOneByOneOnPurpose() {
        // Stairs are not a building and have no plot: they are drawn from a
        // doorway outward, and the shared site-clearing pass squares off a box
        // around whatever footprint is reported. A real one would chew through
        // the house the steps serve.
        assertNull(BuildingSizes.of("kingdoms:stairs"),
                "stairs are not a building and must not be given a plot");

        int[] dims = drawn("stairs");

        assertEquals(1, dims[0]);
        assertEquals(1, dims[1]);
        // The one shape that genuinely reads the ground: the run is as long as
        // the drop demands. What it reports is one by one either way, which is
        // the whole claim the seam makes about every other shape as well.
        assertEquals(1, drawnOn(emptyAir(), "stairs")[0]);
        assertEquals(1, drawnOn(emptyAir(), "stairs")[1]);
    }

    @Test
    void aStyledIdIsStillTheBuildingItNames() {
        // draw() is reached today only through procedural, which strips the
        // culture folder first. If it were ever called with one left on, an
        // unstripped path would miss every case in the switch and be built as
        // the marker -- a five-by-five slab where a house belongs.
        BuildingSizes.Size declared = BuildingSizes.of("kingdoms:house");
        int[] dims = drawn("norman/house");

        assertEquals(declared.width(), dims[0]);
        assertEquals(declared.depth(), dims[1]);
    }
}
