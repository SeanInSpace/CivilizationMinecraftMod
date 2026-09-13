package com.civilization.neoforge.world;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.work.LightStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a street lamp is made of, for each people who build one.
 *
 * <p>Runs without a world for exactly the reason {@link PerimeterLayerPlanTest}
 * does: the one part of a lamp that has to read the ground is where its foot goes,
 * and that is handed in. Everything above the footing follows from the culture, so
 * a lamp is something that can be counted rather than something that has to be
 * walked to.
 *
 * <p>The test that earns its keep is {@link #noTorchEverStandsOnAFence}. That is
 * not a style question, it is the bug the wall paid an entire drawing budget for: a
 * torch will not stand on a fence post, every one placed pops straight back off,
 * and a sweep that counts each doomed torch as work done spends its whole allowance
 * re-placing the same two dozen for ever while the rest of the town stays dark. It
 * was invisible in play — an unlit street reads as a street nobody has got round
 * to — so the only place it can be caught is here.
 */
class LightLayerPlanTest {

    /** Where a lamp's foot lands, once the ground has been read for it. */
    private static final BlockPos FOOTING = new BlockPos(12, 64, -30);

    @Test
    void aNormanLampIsALanternOnAnOakPost() {
        List<LightLayer.Course> plan = LightLayer.plan(LightStyle.OAK_LANTERN, FOOTING);
        assertEquals(3, plan.size(), "two courses of standard and the light on top");
        assertEquals(FOOTING, plan.get(0).pos());
        assertTrue(plan.get(0).state().is(Blocks.OAK_FENCE));
        assertTrue(plan.get(1).state().is(Blocks.OAK_FENCE));
        assertEquals(FOOTING.above(2), plan.get(2).pos());
        assertTrue(plan.get(2).state().is(Blocks.LANTERN),
                "a lantern sits on a fence post and stays there, which the wall proved");
    }

    @Test
    void aHighlandLampIsATorchOnASprucePost() {
        List<LightLayer.Course> plan = LightLayer.plan(LightStyle.SPRUCE_TORCH, FOOTING);
        assertTrue(plan.get(0).state().is(Blocks.SPRUCE_FENCE));
        assertTrue(plan.get(1).state().is(Blocks.STRIPPED_SPRUCE_LOG),
                "the head is solid because a torch needs something to sit on");
        assertTrue(plan.get(2).state().is(Blocks.TORCH));
    }

    @Test
    void aBurgherLampIsMasoned() {
        List<LightLayer.Course> plan = LightLayer.plan(LightStyle.STONE_LANTERN, FOOTING);
        assertTrue(plan.get(0).state().is(Blocks.STONE_BRICK_WALL));
        assertTrue(plan.get(1).state().is(Blocks.STONE_BRICK_WALL),
                "the burgher masons his street furniture where everybody else nails"
                        + " his together");
        assertTrue(plan.get(2).state().is(Blocks.LANTERN));
    }

    @Test
    void aValeLampIsATorchOnAnOakPost() {
        List<LightLayer.Course> plan = LightLayer.plan(LightStyle.FENCE_TORCH, FOOTING);
        assertTrue(plan.get(0).state().is(Blocks.OAK_FENCE));
        assertTrue(plan.get(1).state().is(Blocks.STRIPPED_OAK_LOG));
        assertTrue(plan.get(2).state().is(Blocks.TORCH));
    }

    @Test
    void aWarhostLightsItsRoadsBlue() {
        List<LightLayer.Course> plan =
                LightLayer.plan(LightStyle.SPRUCE_SOUL_LANTERN, FOOTING);
        assertTrue(plan.get(0).state().is(Blocks.SPRUCE_FENCE));
        assertTrue(plan.get(2).state().is(Blocks.SOUL_LANTERN),
                "which nobody else does, and it costs what an ordinary lantern costs");
    }

    @Test
    void aGoblinLampIsATorchOnAStick() {
        List<LightLayer.Course> plan = LightLayer.plan(LightStyle.STICK_TORCH, FOOTING);
        assertTrue(plan.get(0).state().is(Blocks.STRIPPED_BAMBOO_BLOCK));
        assertTrue(plan.get(2).state().is(Blocks.TORCH),
                "a goblin street is lit because somebody jammed a burning stick in"
                        + " the mud");
    }

    // --- the rules that hold for all of them ---

    /** Every fence a lamp standard could be built out of. */
    private static final List<net.minecraft.world.level.block.Block> FENCES = List.of(
            Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE,
            Blocks.JUNGLE_FENCE, Blocks.ACACIA_FENCE, Blocks.DARK_OAK_FENCE,
            Blocks.MANGROVE_FENCE, Blocks.CHERRY_FENCE, Blocks.BAMBOO_FENCE,
            Blocks.CRIMSON_FENCE, Blocks.WARPED_FENCE, Blocks.NETHER_BRICK_FENCE);

    @Test
    void noTorchEverStandsOnAFence() {
        for (LightStyle style : LightStyle.values()) {
            if (!LightLayer.lampBlockOf(style).defaultBlockState().is(Blocks.TORCH)) {
                continue;
            }
            assertFalse(FENCES.contains(LightLayer.headBlockOf(style)),
                    style + " stands a torch on a fence, which pops straight off and"
                            + " burns the drawing sweep's whole budget re-placing it");
        }
    }

    @Test
    void everyStyleIsAPostAndALightAndNothingElse() {
        for (LightStyle style : LightStyle.values()) {
            List<LightLayer.Course> plan = LightLayer.plan(style, FOOTING);
            assertEquals(3, plan.size(), style + " is not two courses and a lamp");
            assertEquals(FOOTING, plan.get(0).pos(), style + " floats");
            assertEquals(FOOTING.above(style.height()), plan.getLast().pos(),
                    style + " puts its flame somewhere other than its own height");
            for (LightLayer.Course course : plan) {
                assertNotNull(course.state(), style + " has a course of nothing");
            }
        }
    }

    @Test
    void noTwoCoursesOfOneLampWantTheSameBlockPosition() {
        for (LightStyle style : LightStyle.values()) {
            Set<BlockPos> taken = new HashSet<>();
            for (LightLayer.Course course : LightLayer.plan(style, FOOTING)) {
                assertTrue(taken.add(course.pos()),
                        style + " lays two blocks at " + course.pos() + ", so the"
                                + " second would replace the first and the crew would"
                                + " never finish the position");
            }
        }
    }

    @Test
    void everyCultureGetsALampAndTheLayerKnowsWhatItIs() {
        for (Culture culture : Culture.all()) {
            LightStyle style = LightStyle.of(culture);
            assertNotNull(LightLayer.footBlockOf(style), culture.id() + " has no post");
            assertNotNull(LightLayer.headBlockOf(style));
            assertNotNull(LightLayer.lampBlockOf(style), culture.id() + " has no light");
        }
    }

    @Test
    void everyLampIsRecognizedAsStreetFurniture() {
        for (LightStyle style : LightStyle.values()) {
            assertTrue(LightLayer.isStreetFurniture(
                            LightLayer.lampBlockOf(style).defaultBlockState()),
                    style + "'s light would be counted as part of a house by the"
                            + " demolition census");
        }
        assertFalse(LightLayer.isStreetFurniture(Blocks.OAK_PLANKS.defaultBlockState()),
                "and a plank is still a wall");
        assertFalse(LightLayer.isStreetFurniture(Blocks.STONE_BRICKS.defaultBlockState()));
    }
}
