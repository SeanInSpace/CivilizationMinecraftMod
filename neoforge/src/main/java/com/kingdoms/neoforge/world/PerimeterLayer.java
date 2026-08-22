package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Draws the palisade the simulation has already paid for.
 *
 * <p>The ring itself — where it runs, how much of it is raised — is decided on
 * the abstract clock in {@code PerimeterPlanner}; this class only makes the
 * laid prefix visible where the world is loaded. Same doctrine as everything
 * else with two fidelities: the wall a town believes it has and the wall a
 * player can walk up to must be the same wall.
 *
 * <p>Idempotent by inspection: a position already carrying its post is skipped,
 * so redrawing every sweep costs almost nothing. Slices cap the work per tick
 * so a freshly loaded town does not stamp two hundred posts in one frame.
 */
public final class PerimeterLayer {

    /** Posts drawn per settlement per tick, at most. */
    private static final int SLICE = 24;

    /** A torch every so many posts, so the wall reads at night. */
    private static final int TORCH_EVERY = 8;

    private PerimeterLayer() {
    }

    /** Stamps the laid prefix of the ring into the world. */
    public static void draw(ServerLevel level, Settlement settlement) {
        Perimeter perimeter = settlement.perimeter();
        if (perimeter == null || perimeter.laid() <= 0) {
            return;
        }
        List<SimPos> ring = perimeter.ringPositions();
        int limit = Math.min(perimeter.laid(), ring.size());
        int drawn = 0;
        for (int i = 0; i < limit && drawn < SLICE; i++) {
            SimPos pos = ring.get(i);
            if (!level.isLoaded(new BlockPos(pos.x(), pos.y(), pos.z()))) {
                continue;
            }
            drawn += perimeter.isGateway(pos)
                    ? drawGateway(level, perimeter, pos)
                    : drawPost(level, pos, i);
        }
    }

    /**
     * One post: two logs on the surface, a torch on every eighth.
     *
     * @return 1 if anything was placed, 0 if the post already stood
     */
    private static int drawPost(ServerLevel level, SimPos pos, int index) {
        BlockPos ground = surface(level, pos);
        if (ground == null) {
            return 0;
        }
        boolean placed = false;
        placed |= put(level, ground, Blocks.OAK_LOG);
        placed |= put(level, ground.above(), Blocks.OAK_LOG);
        if (index % TORCH_EVERY == 0) {
            placed |= put(level, ground.above(2), Blocks.TORCH);
        }
        return placed ? 1 : 0;
    }

    /**
     * A gateway position: the opening stays clear, and the centre block gets a
     * fence gate facing out through the wall — a choke point with a door.
     */
    private static int drawGateway(ServerLevel level, Perimeter perimeter, SimPos pos) {
        for (SimPos gate : perimeter.gates()) {
            if (pos.x() == gate.x() && pos.z() == gate.z()) {
                BlockPos ground = surface(level, pos);
                if (ground == null) {
                    return 0;
                }
                // The wall runs along one axis here; the gate swings across it.
                Direction facing = gateFacing(perimeter, gate);
                BlockState state = Blocks.OAK_FENCE_GATE.defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, facing);
                if (level.getBlockState(ground).getBlock() != Blocks.OAK_FENCE_GATE
                        && replaceable(level, ground)) {
                    level.setBlock(ground, state, Block.UPDATE_ALL);
                    return 1;
                }
                return 0;
            }
        }
        return 0;   // flanking opening: left clear on purpose
    }

    /** The gate swings across the wall's run: side gates face east/west, top and bottom north/south. */
    private static Direction gateFacing(Perimeter perimeter, SimPos gate) {
        List<SimPos> vertices = perimeter.vertices();
        for (int i = 0; i < vertices.size(); i++) {
            SimPos a = vertices.get(i);
            SimPos b = vertices.get((i + 1) % vertices.size());
            if (a.x() == b.x() && gate.x() == a.x()) {
                return Direction.EAST;   // wall runs north-south
            }
        }
        return Direction.SOUTH;
    }

    /** First air-or-plant block standing on solid ground at this column. */
    private static BlockPos surface(ServerLevel level, SimPos pos) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(pos.x(), pos.y(), pos.z()));
        // Refuse water: a palisade post in a pond reads as a mistake, and the
        // planner will have routed the useful part of the ring on land anyway.
        if (!level.getFluidState(top.below()).isEmpty()) {
            return null;
        }
        return top;
    }

    private static boolean put(ServerLevel level, BlockPos pos, Block block) {
        if (level.getBlockState(pos).getBlock() == block) {
            return false;
        }
        if (!replaceable(level, pos)) {
            return false;
        }
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /** Only air and soft growth give way; a wall never eats a building. */
    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }
}
