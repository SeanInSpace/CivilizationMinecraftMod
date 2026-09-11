package com.civilization.neoforge.world;

import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.Transforms;
import com.civilization.neoforge.CivilizationBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.List;

/**
 * A seven-by-seven Norman cottage, written out block by block.
 *
 * <p>Stands in for a building somebody made in creative and scanned, because in
 * a test there is nobody to make one. Everything the loader has to get right
 * about an authored file is in here and nowhere else in the mod: the beds are
 * real bed blocks rather than cells in a table, the doorway is a hole somebody
 * cut in a wall, and the post is where the author put it. That is the whole
 * difference being tested — the drawn cottage's beds are three rows in
 * {@code Beds}, and this one's are three pairs of blocks that have to be
 * <em>found</em>.
 *
 * <p>Laid out to agree with the drawn cottage on purpose, so a failure is about
 * the loading and never about the design: same seven by seven, same three beds
 * in the same cells, same post at the back of the room, door in the middle of
 * the south wall. See {@code BuildingSizes} and {@code Beds} for where those
 * numbers come from.
 */
final class ExampleCottage {

    /** Seven across, seven back, five courses from floor to roof. */
    static final int WIDTH = 7;
    static final int DEPTH = 7;
    static final int HEIGHT = 5;

    /** The middle of the floor, which is the cell that lands on the build plot. */
    static final BlockPos ANCHOR = new BlockPos(WIDTH / 2, 0, DEPTH / 2);

    private ExampleCottage() {
    }

    /**
     * The cottage as a file authored by somebody facing {@code facing}.
     *
     * <p>Genuinely turned, rather than drawn southward with a different number
     * written on it. That distinction is the entire point of the fixture: a scan
     * taken by an author looking east has its door in the {@code +x} wall
     * <em>and</em> says three, and a loader that honours one without the other
     * puts a blank wall on the street. A fixture that only stamped the number
     * would let exactly that bug pass.
     *
     * @param facing which way its front points, as the file records it
     */
    static Blueprint blueprint(int facing) {
        return Transforms.apply(southFacing(), Transforms.between(0, facing), Mirror.NONE);
    }

    /** The cottage as drawn: door in the {@code +z} wall, saying so. */
    private static Blueprint southFacing() {
        List<Blueprint.BlueprintBlock> blocks = new ArrayList<>();

        // The floor, at the course a plot is measured from.
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                blocks.add(at(x, 0, z, Blocks.OAK_PLANKS.defaultBlockState()));
            }
        }

        // Three courses of wall round the outside, with a doorway cut in the
        // middle of the south wall: two courses of nothing at all, which is what
        // a scan of a real building records where a door stands open.
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < WIDTH; x++) {
                for (int z = 0; z < DEPTH; z++) {
                    boolean wall = x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
                    if (!wall) {
                        continue;
                    }
                    boolean doorway = z == DEPTH - 1 && x == WIDTH / 2 && y <= 2;
                    if (doorway) {
                        continue;
                    }
                    blocks.add(at(x, y, z, Blocks.COBBLESTONE.defaultBlockState()));
                }
            }
        }

        // A flat roof. A real author would put a gable on it; a gable is four
        // more loops and tests nothing this file is about.
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                blocks.add(at(x, HEIGHT - 1, z, Blocks.OAK_PLANKS.defaultBlockState()));
            }
        }

        // The post that names the building, at the back of the room.
        blocks.add(at(WIDTH / 2, 1, DEPTH / 2 - 1,
                CivilizationBlocks.COTTAGE.get().defaultBlockState()));

        // Three beds, in the cells the drawn cottage puts them in.
        bed(blocks, 2, 2, Direction.NORTH);
        bed(blocks, 4, 2, Direction.NORTH);
        bed(blocks, 4, 4, Direction.EAST);

        return new Blueprint(new Vec3i(WIDTH, HEIGHT, DEPTH), blocks, ANCHOR,
                new Blueprint.Meta(0, Blueprint.UNCOUNTED));
    }

    /** Both halves of one bed, the foot at {@code (x, z)} and the head beyond it. */
    private static void bed(List<Blueprint.BlueprintBlock> blocks, int x, int z,
                            Direction heading) {
        BlockState foot = Blocks.BED.pick(DyeColor.RED).defaultBlockState()
                .setValue(BedBlock.FACING, heading)
                .setValue(BedBlock.PART, BedPart.FOOT);
        blocks.add(at(x, 1, z, foot));
        BlockPos head = new BlockPos(x, 1, z).relative(heading);
        blocks.add(at(head.getX(), head.getY(), head.getZ(),
                foot.setValue(BedBlock.PART, BedPart.HEAD)));
    }

    private static Blueprint.BlueprintBlock at(int x, int y, int z, BlockState state) {
        return new Blueprint.BlueprintBlock(new BlockPos(x, y, z), state, null);
    }
}
