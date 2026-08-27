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

    /** What the wall is made of. Two of these stand three high to anything jumping. */
    private static final Block POST = Blocks.OAK_FENCE;

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
     * One post: fence two high on the surface, a torch on every eighth.
     *
     * <p>Fence rather than log, and two of them. A fence is a block and a half
     * to anything trying to get over it, so two courses stand three high to a
     * mob and cannot be jumped — where two stacked logs were exactly two blocks
     * and a zombie could climb the slope beside them and step in. It also reads
     * as a wall somebody built rather than a row of trees somebody left.
     *
     * @return 1 if anything was placed, 0 if the post already stood
     */
    private static int drawPost(ServerLevel level, SimPos pos, int index) {
        BlockPos ground = surface(level, pos);
        if (ground == null) {
            return 0;
        }
        clearGrowth(level, ground);
        takeDownWhatIsHanging(level, ground);
        boolean placed = false;
        placed |= put(level, ground, POST);
        placed |= put(level, ground.above(), POST);
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
        BlockPos ground = surface(level, pos);
        if (ground == null) {
            return 0;
        }
        for (SimPos gate : perimeter.gates()) {
            if (pos.x() == gate.x() && pos.z() == gate.z()) {
                if (level.getBlockState(ground).getBlock() == Blocks.OAK_FENCE_GATE) {
                    return 0;
                }
                // The wall runs along one axis here; the gate swings across it.
                Direction facing = gateFacing(perimeter, gate);
                BlockState state = Blocks.OAK_FENCE_GATE.defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, facing);
                if (replaceable(level, ground) || isOurPost(level, ground)) {
                    level.setBlock(ground, state, Block.UPDATE_ALL);
                    return 1;
                }
                return 0;
            }
        }
        // A flanking opening. It is left clear on purpose — but gates move
        // while the wall is going up, following the streets as they appear, so
        // a post may already be standing where the opening now is. Our own
        // posts give way to the gateway; nothing else is touched.
        return clearPost(level, ground) ? 1 : 0;
    }

    /**
     * Whether this block is a palisade post we put there.
     *
     * <p>Logs are still recognised: towns walled before the fence was adopted
     * have log posts standing, and a gate that could not clear one would sit
     * blocked by a wall the town no longer builds.
     */
    private static boolean isOurPost(ServerLevel level, BlockPos pos) {
        return isPostBlock(level.getBlockState(pos));
    }

    /** Takes down a post standing in a gateway, and whatever we stacked on it. */
    private static boolean clearPost(ServerLevel level, BlockPos ground) {
        if (!isOurPost(level, ground)) {
            return false;
        }
        level.setBlock(ground, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos above = ground.above(dy);
            BlockState state = level.getBlockState(above);
            if (isPostBlock(state) || state.is(Blocks.TORCH)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return true;
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

    /**
     * The foot of the post: the first free block above the real ground.
     *
     * <p>The subtlety that matters, and the bug that proved it: the heightmap
     * counts our own wall. Asking it for the surface and planting two logs
     * there raises the heightmap by two, so the next sweep plants two more on
     * top of those, and the palisade grows into the sky at two blocks a second
     * for as long as the town is loaded. A playtest screenshot of hundred-block
     * towers with a ladder of torches up the side is what this comment is for.
     *
     * <p>So step back down through anything the wall itself put here before
     * calling it ground. That makes {@link #put} genuinely idempotent — the
     * second sweep finds its own logs already standing and writes nothing.
     */
    /** Where a post's foot belongs, for anybody wanting to inspect the line. */
    public static BlockPos footingFor(ServerLevel level, SimPos pos) {
        return surface(level, pos);
    }

    /** Whether a post is actually standing at this footing. */
    public static boolean postStands(ServerLevel level, BlockPos ground) {
        return isPostBlock(level.getBlockState(ground));
    }

    private static BlockPos surface(ServerLevel level, SimPos pos) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(pos.x(), pos.y(), pos.z()));
        // Down through the wall's own work, and down through anything growing.
        // MOTION_BLOCKING_NO_LEAVES steps over leaves and NOT over logs, so a
        // post whose column held a trunk was founded on top of the trunk and
        // the wall climbed the tree. Ground is under the wood, not on it.
        while (top.getY() > level.getMinY() + 1
                && (isOurs(level, top.below())
                        || WallClearing.isGrowth(level.getBlockState(top.below())))) {
            top = top.below();
        }
        // And down through nothing at all. A post founded on a trunk before the
        // heightmap was taught to see through wood is left hanging the moment a
        // lumberjack fells that tree — a fence in mid-air over a stump-hole,
        // which is exactly what a walled town in a wood looked like. Falling to
        // whatever is actually holding the column up re-founds those on the next
        // sweep instead of leaving them floating for good.
        while (top.getY() > level.getMinY() + 1
                && level.getBlockState(top.below()).isAir()) {
            top = top.below();
        }
        // Refuse water: a palisade post in a pond reads as a mistake, and the
        // planner will have routed the useful part of the ring on land anyway.
        if (!level.getFluidState(top.below()).isEmpty()) {
            return null;
        }
        return top;
    }

    /**
     * Whether this block is one the wall itself laid, rather than the world's.
     *
     * <p><strong>Every block the wall can place must be listed here.</strong>
     * This is what {@link #surface} walks down through to find the real ground,
     * and a post the wall does not recognise as its own is a post the heightmap
     * reports as the surface — so the next sweep lays another two on top of it,
     * and the sweep after that another two, and the wall climbs to the sky.
     *
     * <p>That is not hypothetical. Changing the post from a log to a fence
     * updated the gateway's idea of a post and missed this one, and the sky
     * walls came straight back. The two predicates now read the same
     * {@link #POST} constant so they cannot drift apart again — which is the
     * actual fix; adding one block id would only have postponed it.
     */
    private static boolean isOurs(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isPostBlock(state) || state.is(Blocks.TORCH)
                || state.is(Blocks.OAK_FENCE_GATE);
    }

    /**
     * Whether this is a palisade post, of any vintage.
     *
     * <p>Logs are still recognised: towns walled before the fence was adopted
     * have log posts standing, and neither the gateways nor the ground-finding
     * may stop seeing them.
     */
    private static boolean isPostBlock(BlockState state) {
        return state.is(POST) || state.is(Blocks.OAK_LOG);
    }

    /**
     * Takes down any of our own posts left hanging above the real footing.
     *
     * <p>The wall used to be founded on whatever the heightmap called the
     * surface, and the heightmap steps over leaves but not over logs — so a post
     * in a tree's column was planted on top of the trunk. Fell that tree later,
     * as a lumberjack eventually does, and the fence stays where it was: a line
     * of posts floating two blocks over a clearing.
     *
     * <p>Founding correctly stops it happening again and does nothing about the
     * ones already up there, because they are above the new footing and out of
     * the way of everything the drawing touches. So they are swept on the pass
     * that re-founds the post, which is the only moment anything knows both
     * where the post is and where it should have been.
     *
     * <p>Only our own blocks, and only directly overhead. A wall that pulled
     * down whatever happened to be above it would demolish the branch it was
     * built under.
     */
    private static void takeDownWhatIsHanging(ServerLevel level, BlockPos ground) {
        for (int dy = 2; dy <= HANGING_REACH; dy++) {
            BlockPos above = ground.above(dy);
            if (!level.isLoaded(above)) {
                return;
            }
            BlockState state = level.getBlockState(above);
            if (isPostBlock(state) || state.is(Blocks.TORCH)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * How far above a footing to look for posts the ground has left.
     *
     * <p>A tree is the reason they are up there, so this reaches about as high
     * as a trunk a post could have been planted on.
     */
    private static final int HANGING_REACH = 12;

    /**
     * Takes the wood out of a stretch of line before a post goes into it.
     *
     * <p>The unwatched half of the same job a builder does by hand — see
     * {@code WallClearing} and {@code Foreman}. Without it a post whose column
     * holds a trunk is simply never placed: a log is neither air nor
     * replaceable, so {@link #put} refuses and says nothing, while the laid
     * count has already moved on. The result is a hole in the wall that the
     * town believes it has built.
     *
     * <p>Our own posts are stepped over. Legacy walls are made of oak logs and
     * the growth test cannot tell one of those from a tree, so clearing without
     * this guard would have each post quietly demolish its neighbours.
     */
    private static void clearGrowth(ServerLevel level, BlockPos footing) {
        for (int dy = 0; dy < WallClearing.CLEAR_UP; dy++) {
            for (int dx = -WallClearing.CLEAR_SIDEWAYS; dx <= WallClearing.CLEAR_SIDEWAYS; dx++) {
                for (int dz = -WallClearing.CLEAR_SIDEWAYS; dz <= WallClearing.CLEAR_SIDEWAYS; dz++) {
                    BlockPos at = footing.offset(dx, dy, dz);
                    if (!level.isLoaded(at) || isOurs(level, at)) {
                        continue;
                    }
                    if (WallClearing.isGrowth(level.getBlockState(at))) {
                        level.destroyBlock(at, false, null, 512);
                    }
                }
            }
        }
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
