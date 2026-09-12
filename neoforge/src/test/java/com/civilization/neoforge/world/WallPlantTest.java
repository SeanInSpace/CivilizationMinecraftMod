package com.civilization.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flowers a palisade post has to tread on to get into the ground.
 *
 * <p><strong>What was reported.</strong> {@code /civ wall} on Millbrook, seed
 * 8675309: "laid 566 of 916 … GENUINELY MISSING: 3 — 3 x Lilac". Three positions
 * of the ring had no post in them and a lilac standing where each post belonged.
 *
 * <p>The wall's clearing takes wood and only wood — {@code WallClearing.isGrowth},
 * logs and leaves — and the placing accepts only what {@code canBeReplaced()}
 * says will give way. Short grass gives way. Tall grass gives way. The four tall
 * flowers do not: lilac, sunflower, peony and rose bush are in vanilla's
 * {@code #flowers} list and in none of its replaceable ones, so the post was
 * refused, silently, while the laid count walked on past it. A hole in a wall the
 * town believed it had built.
 *
 * <p><strong>Why it runs without a world.</strong> Block tags are bound when a
 * server loads its datapacks and a JUnit run has no server, so
 * {@code state.is(BlockTags.FLOWERS)} is false here for a lilac itself — which is
 * precisely why {@link WallClearing#isPlant} asks whether the block is a
 * {@link DoublePlantBlock} before it asks any tag. The half a test can reach is
 * the half the fault was in. Same argument {@code OvergrowthPlanTest} makes.
 */
class WallPlantTest {

    /** Millbrook's own footing, near enough: one of the three that stayed empty. */
    private static final BlockPos FOOTING = new BlockPos(70, 69, 111);

    /** A column you stack block by block, which is all the rules ask about. */
    private static final class FakeColumn implements WallClearing.Standing {

        private final Map<BlockPos, BlockState> cells = new HashMap<>();

        FakeColumn put(BlockPos at, BlockState state) {
            cells.put(at, state);
            return this;
        }

        /** A two-tall plant rooted at this cell, both halves, as vanilla builds one. */
        FakeColumn doublePlant(BlockPos lower, net.minecraft.world.level.block.Block plant) {
            put(lower, plant.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            put(lower.above(), plant.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            return this;
        }

        @Override
        public BlockState at(BlockPos pos) {
            return cells.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
    }

    @Test
    void aLilacIsSomethingAPostTreadsOn() {
        // The block that was named in the report, and the three beside it in the
        // same family. Every one of these was refused by canBeReplaced() and is
        // the reason this predicate is not simply that flag.
        for (net.minecraft.world.level.block.Block flower : List.of(
                Blocks.LILAC, Blocks.SUNFLOWER, Blocks.PEONY, Blocks.ROSE_BUSH)) {
            BlockState state = flower.defaultBlockState();
            assertFalse(state.canBeReplaced(),
                    flower + " is replaceable after all; this test has lost its point");
            assertTrue(WallClearing.isPlant(state),
                    flower + " stopped a post from being placed and must not again");
        }
    }

    @Test
    void theTwoTallGrassesWereAlreadyFineAndStayFine() {
        // These carry the replaceable flag, which is why the fault showed up as
        // lilacs and not as every meadow in the world. They are plants all the
        // same, so the same clearing takes them.
        for (net.minecraft.world.level.block.Block grass :
                List.of(Blocks.TALL_GRASS, Blocks.LARGE_FERN)) {
            assertTrue(WallClearing.isPlant(grass.defaultBlockState()),
                    grass + " is growth a post displaces");
        }
    }

    @Test
    void nothingABuildingIsMadeOfIsAPlant() {
        // The one thing this rule must never widen into. A post that uprooted
        // masonry is a wall that eats a storehouse.
        assertFalse(WallClearing.isPlant(Blocks.STONE.defaultBlockState()));
        assertFalse(WallClearing.isPlant(Blocks.OAK_PLANKS.defaultBlockState()));
        assertFalse(WallClearing.isPlant(Blocks.OAK_FENCE.defaultBlockState()),
                "the wall's own post is not growth to be pulled up");
        assertFalse(WallClearing.isPlant(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void waterIsNotAPlant() {
        // The ring crosses streams and a post stands at the waterline. A rule
        // that uprooted water would dig a trench through every pond the line
        // touches -- a hundred and fifty positions of one measured ring were
        // open water.
        assertFalse(WallClearing.isPlant(Blocks.WATER.defaultBlockState()));
    }

    @Test
    void aLilacRootedAtTheFootingComesUpWholeAndTheLowerHalfGoesFirst() {
        // The ordering the world actually produces: a lilac is not motion
        // blocking, so MOTION_BLOCKING_NO_LEAVES puts the footing on its lower
        // half, with the upper half standing in the post's second course.
        FakeColumn site = new FakeColumn().doublePlant(FOOTING, Blocks.LILAC);

        List<BlockPos> pull = WallClearing.uproot(site, FOOTING, WallClearing.POST_COLUMN);

        assertEquals(List.of(FOOTING, FOOTING.above()), pull,
                "both halves, and the lower one first so vanilla's own update"
                        + " carries the upper away with it");
    }

    @Test
    void aLilacTheFootingLandedOnTheTopOfComesUpTooAndTakesTheStemWithIt() {
        // The other ordering, and the one vanilla does not clean up for us. A
        // lower half's survival test asks about the ground beneath it and nothing
        // above, so taking the top out on its own leaves a stem standing under
        // the post -- a fence on a flower. Named here rather than assumed.
        FakeColumn site = new FakeColumn().doublePlant(FOOTING.below(), Blocks.LILAC);

        List<BlockPos> pull = WallClearing.uproot(site, FOOTING, WallClearing.POST_COLUMN);

        assertEquals(List.of(FOOTING, FOOTING.below()), pull,
                "the half at the footing and the half beneath it");
    }

    @Test
    void theWholePostColumnIsCleared() {
        // Two courses of fence and the cell a lantern stands in. A flower in the
        // third one is a flower the lamp cannot be set in.
        FakeColumn site = new FakeColumn()
                .put(FOOTING, Blocks.SHORT_GRASS.defaultBlockState())
                .put(FOOTING.above(2), Blocks.LILAC.defaultBlockState());

        List<BlockPos> pull = WallClearing.uproot(site, FOOTING, WallClearing.POST_COLUMN);

        assertTrue(pull.contains(FOOTING), "the ground cover at the foot");
        assertTrue(pull.contains(FOOTING.above(2)), "and the flower in the lamp's cell");
    }

    @Test
    void theVergeIsLeftStandingEitherSideOfTheLine() {
        // The post's own column and no wider. Taking the flowers two blocks out
        // as well -- which is how far the wood clearing reaches -- would leave a
        // five-wide bald strip round the whole town.
        FakeColumn site = new FakeColumn()
                .doublePlant(FOOTING.east(), Blocks.LILAC)
                .doublePlant(FOOTING.north(2), Blocks.ROSE_BUSH);

        assertEquals(List.of(),
                WallClearing.uproot(site, FOOTING, WallClearing.POST_COLUMN),
                "a palisade through a meadow should read as a palisade"
                        + " through a meadow");
    }

    @Test
    void aClearColumnIsPulledUpNotAtAll() {
        assertEquals(List.of(),
                WallClearing.uproot(new FakeColumn(), FOOTING, WallClearing.POST_COLUMN));
    }

    @Test
    void halfAPlantWithNothingToMatchItIsTakenOnItsOwn() {
        // A lower half whose top has already gone, which is what the world looks
        // like a tick after anything at all has happened to it. The other cell is
        // air and is not named as something to pull up.
        FakeColumn site = new FakeColumn().put(FOOTING,
                Blocks.LILAC.defaultBlockState()
                        .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));

        assertEquals(List.of(FOOTING),
                WallClearing.uproot(site, FOOTING, WallClearing.POST_COLUMN));
    }

    @Test
    void aDoorIsNotHalfOfAPlantHoweverMuchItsPropertiesLookLikeOne() {
        // Doors carry the very same DOUBLE_BLOCK_HALF property, and a rule that
        // read the property without asking what the block was would have a post
        // pull the top out of somebody's front door.
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        assertFalse(WallClearing.isPlant(door));
        assertEquals(null, WallClearing.otherHalf(door, FOOTING));
    }
}
