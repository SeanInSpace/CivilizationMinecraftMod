package com.keystone;

import com.keystone.blueprint.Transforms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blueprint coordinate math.
 *
 * <p>Deliberately tests only {@link Transforms#position} and
 * {@link Transforms#size}, which are pure integer arithmetic — no registries, no
 * bootstrap, no game. Rotating the block <em>states</em> is vanilla's job and is
 * exercised in-world instead.
 *
 * <p>A deliberately non-cubic 5x3x2 volume is used throughout: a cube would pass
 * these tests even with X and Z confused.
 */
class TransformsTest {

    private static final Vec3i SIZE = new Vec3i(5, 3, 2);

    private static List<BlockPos> everyPosition(Vec3i size) {
        List<BlockPos> all = new ArrayList<>();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    all.add(new BlockPos(x, y, z));
                }
            }
        }
        return all;
    }

    @Test
    void quarterTurnSwapsWidthAndDepth() {
        assertEquals(new Vec3i(2, 3, 5), Transforms.size(SIZE, Rotation.CLOCKWISE_90));
        assertEquals(new Vec3i(2, 3, 5), Transforms.size(SIZE, Rotation.COUNTERCLOCKWISE_90));
    }

    @Test
    void halfTurnKeepsTheSameFootprint() {
        assertEquals(SIZE, Transforms.size(SIZE, Rotation.CLOCKWISE_180));
        assertEquals(SIZE, Transforms.size(SIZE, Rotation.NONE));
    }

    @Test
    void fourQuarterTurnsReturnEveryBlockToWhereItStarted() {
        for (BlockPos start : everyPosition(SIZE)) {
            BlockPos pos = start;
            Vec3i size = SIZE;
            for (int turn = 0; turn < 4; turn++) {
                pos = Transforms.position(pos, size, Rotation.CLOCKWISE_90, Mirror.NONE);
                size = Transforms.size(size, Rotation.CLOCKWISE_90);
            }
            assertEquals(start, pos, "four quarter turns should be the identity");
            assertEquals(SIZE, size);
        }
    }

    @Test
    void rotationNeverPushesABlockOutsideTheStructure() {
        for (Rotation rotation : Rotation.values()) {
            Vec3i rotated = Transforms.size(SIZE, rotation);
            for (BlockPos start : everyPosition(SIZE)) {
                BlockPos moved = Transforms.position(start, SIZE, rotation, Mirror.NONE);
                assertTrue(moved.getX() >= 0 && moved.getX() < rotated.getX()
                                && moved.getY() >= 0 && moved.getY() < rotated.getY()
                                && moved.getZ() >= 0 && moved.getZ() < rotated.getZ(),
                        () -> rotation + " sent " + start + " to " + moved
                                + ", outside " + rotated);
            }
        }
    }

    @Test
    void rotationIsAPermutation() {
        // Nothing may collide: two blocks landing on one position would silently
        // delete part of a building.
        for (Rotation rotation : Rotation.values()) {
            List<BlockPos> moved = everyPosition(SIZE).stream()
                    .map(p -> Transforms.position(p, SIZE, rotation, Mirror.NONE))
                    .toList();
            assertEquals(moved.size(), Set.copyOf(moved).size(),
                    rotation + " mapped two blocks onto the same position");
        }
    }

    @Test
    void mirroringTwiceIsTheIdentity() {
        for (Mirror mirror : Mirror.values()) {
            for (BlockPos start : everyPosition(SIZE)) {
                BlockPos once = Transforms.position(start, SIZE, Rotation.NONE, mirror);
                BlockPos twice = Transforms.position(once, SIZE, Rotation.NONE, mirror);
                assertEquals(start, twice, mirror + " applied twice should undo itself");
            }
        }
    }

    @Test
    void mirrorsActOnTheAxisTheyName() {
        BlockPos corner = new BlockPos(0, 0, 0);
        assertEquals(new BlockPos(0, 0, 1),
                Transforms.position(corner, SIZE, Rotation.NONE, Mirror.LEFT_RIGHT));
        assertEquals(new BlockPos(4, 0, 0),
                Transforms.position(corner, SIZE, Rotation.NONE, Mirror.FRONT_BACK));
    }

    @Test
    void aQuarterTurnPutsTheOriginCornerWhereItBelongs() {
        // Turning clockwise, the near-left corner becomes the far-left corner.
        assertEquals(new BlockPos(1, 0, 0),
                Transforms.position(new BlockPos(0, 0, 0), SIZE, Rotation.CLOCKWISE_90, Mirror.NONE));
        assertEquals(new BlockPos(0, 0, 4),
                Transforms.position(new BlockPos(4, 0, 1), SIZE, Rotation.CLOCKWISE_90, Mirror.NONE));
    }
}
