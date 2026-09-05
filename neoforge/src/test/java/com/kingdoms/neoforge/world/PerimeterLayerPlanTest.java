package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Perimeter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the wall lays at one position on the line.
 *
 * <p>The wall used to be stamped into the world by a sweep and nothing else, so
 * what it laid was only ever knowable by walking to it. It is a job now: a
 * builder stands at a position, and the blocks that go in there are this list,
 * one to a swing. The sweep that fills in a wall raised while nobody was looking
 * reads the same list, which is what stops the two from drifting apart into a
 * palisade that looks different depending on whether you watched it go up.
 *
 * <p><strong>Why it can run without a world.</strong> The one part of a post
 * that has to read the ground is where its foot goes, and that is handed in.
 * Everything above the footing follows from the position's place on the ring and
 * from where the gates are — both of which are in the {@link Perimeter} — so a
 * plan is something that can be counted rather than something that has to be
 * walked to. Same argument as {@code BlueprintPlacerSizeTest} makes about a
 * building's drawn size.
 */
class PerimeterLayerPlanTest {

    /** Where a post's foot lands, once the ground has been read for it. */
    private static final BlockPos FOOTING = new BlockPos(-16, 64, -16);

    /** A square ring with no gates in it at all, for the plain cases. */
    private static Perimeter plainRing() {
        return new Perimeter(box(16), List.of(), 0);
    }

    private static List<SimPos> box(int half) {
        return List.of(
                new SimPos(-half, 64, -half), new SimPos(half, 64, -half),
                new SimPos(half, 64, half), new SimPos(-half, 64, half));
    }

    /** The first position of that ring, which is its north-west corner. */
    private static SimPos firstPost(Perimeter ring) {
        return ring.ringPositions().get(0);
    }

    @Test
    void aPostIsTwoCoursesOfFenceOnItsFooting() {
        // Two, and the reason is a mob rather than a look: a fence is a block
        // and a half to anything trying to get over it, so two courses stand
        // three high and cannot be jumped.
        Perimeter ring = plainRing();

        List<PerimeterLayer.Course> plan =
                PerimeterLayer.plan(ring, firstPost(ring), 1, FOOTING);

        assertEquals(2, plan.size(), "an unlit post is two blocks and no more");
        assertEquals(FOOTING, plan.get(0).pos(), "the lower course sits on the footing");
        assertEquals(FOOTING.above(), plan.get(1).pos());
        for (PerimeterLayer.Course course : plan) {
            assertTrue(course.state().is(Blocks.OAK_FENCE),
                    "a palisade post is a fence: " + course.state());
        }
    }

    @Test
    void everyEighthPostCarriesALamp() {
        // So the wall reads at night, and so it is not lit so often that a ring
        // is a string of lanterns.
        Perimeter ring = plainRing();
        SimPos pos = firstPost(ring);

        List<PerimeterLayer.Course> lit = PerimeterLayer.plan(ring, pos, 8, FOOTING);
        assertEquals(3, lit.size());
        assertEquals(FOOTING.above(2), lit.get(2).pos(),
                "the lamp stands on top of the post, not beside it");
        assertTrue(lit.get(2).state().is(Blocks.LANTERN),
                "a lantern rather than a torch: a torch cannot stand on a fence,"
                        + " and every one placed popped straight back off");

        for (int index = 1; index < 8; index++) {
            assertEquals(2, PerimeterLayer.plan(ring, pos, index, FOOTING).size(),
                    "position " + index + " is between lamps and wants none");
        }
    }

    @Test
    void theMiddleOfAGateIsAGate() {
        // A gate is a hole in the wall with a door in it, so it replaces the
        // post rather than standing beside one.
        Perimeter ring = plainRing();
        SimPos gate = ring.ringPositions().get(4);
        ring.setGates(List.of(gate));

        List<PerimeterLayer.Course> plan = PerimeterLayer.plan(ring, gate, 4, FOOTING);

        assertEquals(1, plan.size(), "a gate is one block, at the footing");
        assertEquals(FOOTING, plan.get(0).pos());
        assertTrue(plan.get(0).state().is(Blocks.OAK_FENCE_GATE));
    }

    @Test
    void aGateSwingsAcrossTheRunOfTheWall() {
        // A gate hung along the line is a gate you cannot walk through.
        Perimeter ring = plainRing();
        // The ring is walked from its north-west corner eastward, so its fourth
        // vertex leg runs north-south down the western side.
        SimPos onTheWesternLeg = ring.ringPositions().get(ring.length() - 8);
        ring.setGates(List.of(onTheWesternLeg));

        List<PerimeterLayer.Course> plan =
                PerimeterLayer.plan(ring, onTheWesternLeg, 0, FOOTING);

        assertEquals(Direction.EAST,
                plan.get(0).state().getValue(HorizontalDirectionalBlock.FACING),
                "a gate on a north-south run swings east and west");
    }

    @Test
    void theRestOfAGatewayIsLeftOpen() {
        // The whole point of a gate. A gateway is three positions wide and only
        // its middle carries anything; the two beside it are the opening.
        Perimeter ring = plainRing();
        List<SimPos> posts = ring.ringPositions();
        SimPos gate = posts.get(4);
        ring.setGates(List.of(gate));

        for (SimPos beside : List.of(posts.get(3), posts.get(5))) {
            assertTrue(ring.isGateway(beside), "fixture: " + beside + " is in the opening");
            assertTrue(PerimeterLayer.plan(ring, beside, 3, FOOTING).isEmpty(),
                    "the wall lays nothing at " + beside + ", which is what an opening is");
        }
    }
}
