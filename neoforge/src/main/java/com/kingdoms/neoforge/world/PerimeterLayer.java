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

    /** A light every so many posts, so the wall reads at night. */
    private static final int LAMP_EVERY = 8;

    /**
     * What the light is.
     *
     * <p>It was a torch, and a torch cannot stand on a fence: the top of a
     * post is not a face a torch can be supported on, so every one placed
     * popped straight back off. That was invisible in the world -- an unlit
     * wall reads as a wall nobody has got round to lighting -- and fatal in the
     * sweep, which counted each doomed torch as work done and spent its entire
     * budget re-placing the same two dozen of them every second while four
     * fifths of the ring was never reached. See {@link #CURSOR}.
     *
     * <p>A lantern sits on a fence post and stays there.
     */
    private static final Block LAMP = Blocks.LANTERN;

    private PerimeterLayer() {
    }

    /** Stamps the laid prefix of the ring into the world. */
    public static void draw(ServerLevel level, Settlement settlement) {
        Perimeter perimeter = settlement.perimeter();
        if (perimeter == null) {
            return;
        }
        // Before the new wall, the old one comes down -- and it comes down even
        // when nothing is being raised, so a town that re-stakes and then runs
        // out of timber is not left standing inside two walls indefinitely.
        takeDownSuperseded(level, settlement, perimeter);
        if (perimeter.laid() <= 0) {
            return;
        }
        List<SimPos> ring = perimeter.ringPositions();
        int limit = Math.min(perimeter.laid(), ring.size());
        // Where the last sweep stopped, so the budget travels round the ring
        // instead of being spent on the same opening stretch every second.
        int start = CURSOR.getOrDefault(settlement.id(), 0);
        if (start >= limit) {
            start = 0;
        }
        int drawn = 0;
        int looked = 0;
        int i = start;
        // Only ground somebody could actually draw on counts against the budget.
        // A ring is usually half out of sight -- 254 of 640 positions loaded on a
        // measured town -- and charging the scan for the arc nobody can see meant
        // a sweep whose cursor sat in that arc did nothing at all, then handed the
        // same arc to the next sweep. The wall appeared to have stopped when it
        // had simply spent every look on ground it was never allowed to touch.
        int examined = 0;
        while (looked < SCAN && examined < limit && drawn < SLICE) {
            SimPos pos = ring.get(i);
            if (level.isLoaded(new BlockPos(pos.x(), pos.y(), pos.z()))) {
                drawn += perimeter.isGateway(pos)
                        ? drawGateway(level, perimeter, pos)
                        : drawPost(level, pos, i);
                looked++;
            }
            examined++;
            i++;
            if (i >= limit) {
                i = 0;
            }
        }
        CURSOR.put(settlement.id(), i);
    }


    /**
     * Pulls down a wall the town has superseded.
     *
     * <p>A settlement that outgrows its palisade stakes a wider one, and the
     * two must never both stand. An old ring left up inside a new one is a
     * fence line through the middle of a town — precisely the partition the
     * concave-hull work was done to remove, and no better for having been a
     * wall once. It shuts settlers out of their beds all the same.
     *
     * <p>Swept on the same cursor discipline as the raising — resumed where it
     * stopped, so no stretch can starve another — on a quarter of its budget,
     * because a wall going up is what a town is waiting for and a wall coming
     * down is only untidy. The retired line is forgotten only after one full
     * circuit that found every position loaded and nothing of ours standing:
     * anything less writes off a stretch in an unloaded chunk without anybody
     * having looked at it, and that stretch is precisely where a forgotten
     * wall would sit for the rest of the world's life.
     */
    private static void takeDownSuperseded(ServerLevel level, Settlement settlement,
                                           Perimeter perimeter) {
        List<SimPos> retired = perimeter.retiredPositions();
        if (retired.isEmpty()) {
            DEMOLITION.remove(settlement.id());
            return;
        }
        Demolition state = DEMOLITION.getOrDefault(settlement.id(), new Demolition(0, false));
        int i = state.cursor() >= retired.size() ? 0 : state.cursor();
        // A cursor back at zero is a circuit boundary, so what the last one
        // found is already spent; anywhere else, this circuit is still running.
        boolean outstanding = i != 0 && state.outstanding();
        int taken = 0;
        int looked = 0;
        int examined = 0;
        int reach = Math.min(retired.size(), RETIRED_LOOK);
        while (looked < RETIRED_SCAN && examined < reach && taken < RETIRED_SLICE) {
            SimPos pos = retired.get(i);
            if (!level.isLoaded(new BlockPos(pos.x(), pos.y(), pos.z()))) {
                outstanding = true;   // nobody has looked at this stretch yet
            } else {
                looked++;
                if (pullDownOurs(level, pos)) {
                    taken++;
                    outstanding = true;
                }
            }
            examined++;
            i++;
            if (i >= retired.size()) {
                i = 0;
                if (!outstanding) {
                    perimeter.forgetRetired();
                    DEMOLITION.remove(settlement.id());
                    return;
                }
                outstanding = false;   // a fresh circuit judges itself
            }
        }
        DEMOLITION.put(settlement.id(), new Demolition(i, outstanding));
    }

    /**
     * Where a settlement's demolition had got to, and whether this circuit has
     * found anything at all — a stretch still standing, or one nobody could see.
     */
    private record Demolition(int cursor, boolean outstanding) {
    }

    private static final java.util.Map<com.kingdoms.sim.settlement.Settlement.Id, Demolition>
            DEMOLITION = new java.util.HashMap<>();

    /**
     * Takes a superseded post out of a column, if that is what is standing there.
     *
     * <p>The signature is checked before anything is broken, and this is the
     * whole of what keeps a demolition from being vandalism. A fence is not the
     * wall's private block: a pen is fenced, a field is fenced, a bridge has
     * railings, and every one of those is a single course. What the wall
     * uniquely builds is <em>two</em> courses of fence with its lamp above, or
     * a gate on the line — so a lone fence found on a retired position belongs
     * to somebody and is left exactly where it is.
     */
    private static boolean pullDownOurs(ServerLevel level, SimPos pos) {
        BlockPos ground = surface(level, pos);
        if (ground == null) {
            return false;
        }
        boolean post = isPostBlock(level.getBlockState(ground))
                && isPostBlock(level.getBlockState(ground.above()));
        if (!post && !level.getBlockState(ground).is(Blocks.OAK_FENCE_GATE)) {
            return false;
        }
        boolean took = false;
        for (int dy = 0; dy <= RETIRED_REACH; dy++) {
            BlockPos at = ground.above(dy);
            if (!level.isLoaded(at)) {
                break;
            }
            if (isOurs(level, at)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                took = true;
            }
        }
        return took;
    }

    /**
     * How far up a retired position to pull.
     *
     * <p>A post is two courses with a lamp on the third, so three is the whole
     * of what the wall ever puts in a column. {@code surface} has already
     * walked down through the wall's own work to find the footing, so this
     * starts at the bottom of the stack rather than somewhere in the middle
     * of it.
     */
    private static final int RETIRED_REACH = 2;

    /**
     * What a demolition may do and look at in one sweep.
     *
     * <p>A quarter of the drawing's, for the reason given in
     * {@link #takeDownSuperseded}: a wall going up is what a town is waiting
     * for, and one coming down is only untidy. It also has to keep looking long
     * after it has finished, because a stretch nobody has loaded cannot be
     * called clear — so the cost of the looking is what wants to be small.
     */
    private static final int RETIRED_SLICE = 6;

    private static final int RETIRED_SCAN = 64;

    /**
     * Positions visited per sweep whether or not anybody can see them.
     *
     * <p>{@link #RETIRED_SCAN} counts only the ones that could be looked at, so
     * on its own it is no bound at all for the case this sweep spends most of
     * its life in: a line whose demolition is finished except for a stretch in
     * a chunk nobody loads. That never satisfies the forget rule, so the sweep
     * goes round for ever, and without this it would walk the whole retired
     * line every second doing nothing. A circuit takes a few sweeps instead of
     * one, which costs a demolition nobody is waiting on precisely nothing.
     */
    private static final int RETIRED_LOOK = 128;

    /**
     * How far round the ring each settlement's sweep had got.
     *
     * <p>The sweep used to start at the first post every time and stop once it
     * had placed {@link #SLICE} blocks. That is fine only while every position
     * it touches settles down afterwards, and one that never settles turns the
     * budget into a treadmill: the torches were re-placed every single second,
     * twenty-four of them came up before index 185 of a 666-post ring, and the
     * remaining four hundred and eighty posts were never once reached. The wall
     * did not build slowly. It stopped, at exactly the point the first
     * twenty-four torches had eaten the budget, and no amount of waiting moved
     * it.
     *
     * <p>Starting where the last sweep finished means no stretch of wall can
     * starve any other stretch, whatever goes wrong at a single position.
     */
    private static final java.util.Map<com.kingdoms.sim.settlement.Settlement.Id, Integer>
            CURSOR = new java.util.HashMap<>();

    /**
     * Positions examined per sweep, placed or not.
     *
     * <p>The second half of the same lesson. A budget counted only in blocks
     * laid is not a budget at all when nothing can be laid — a stretch of ring
     * running through a cliff face would spin the whole ring every tick looking
     * for work it cannot do. This bounds the looking as well as the doing.
     */
    private static final int SCAN = 256;

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
        takeDownWhatIsHanging(level, ground, index % LAMP_EVERY == 0);
        boolean placed = false;
        placed |= put(level, ground, POST);
        placed |= put(level, ground.above(), POST);
        if (index % LAMP_EVERY == 0) {
            placed |= put(level, ground.above(2), LAMP);
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
            if (isPostBlock(state) || isLamp(state)) {
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

    /**
     * Whether the line is shut here, by a post or by anything else solid.
     *
     * <p>The ring is a hull thrown round the town, so it runs through whatever
     * the town has already built — and a post cannot be placed inside a
     * storehouse wall. That is not a hole. A building is a better wall than a
     * fence, and counting those positions as gaps made the wall report read far
     * worse than the wall was.
     *
     * <p>This is no longer load-bearing, and it is worth saying so plainly
     * because it was: the {@code shutByBuilding} count in {@code /civ wall} —
     * two or three positions on a measured ring — was the only thing standing
     * between "the wall is complete" and "the wall is staked through somebody's
     * house", and it forgave both alike. The staking now rules that out at
     * every stage that draws a line: {@code Hull.concave} digs no leg across a
     * plot, {@code PerimeterPlanner.pushOut} gives up its margin at a vertex
     * rather than swing a stretch over one, and {@code relax} refuses any move
     * onto one. So a position closed by a building is a position where the line
     * grazes a wall rather than crossing it.
     *
     * <p>What is <em>not</em> claimed: the convex hull the concave loop starts
     * from is not itself checked, and none of the three rules can repair a
     * stretch that was across a plot before they were asked — they refuse moves,
     * they do not undo them. Sixty random towns produce no such stretch and
     * neither does the grown one in {@code WallRestakeTest}, so it is a gap in
     * the proof rather than an observed fault. Which is exactly why this stays
     * as a guard rather than being deleted: a report that calls a solid stretch
     * a hole is a report nobody reads, and the day this count climbs is the day
     * that gap has stopped being theoretical.
     *
     * <p>Two courses, because that is what a post is. A single step somebody can
     * stand on is not a wall, and half of one is a stile.
     */
    public static boolean lineIsClosed(ServerLevel level, BlockPos ground) {
        if (postStands(level, ground)) {
            return true;
        }
        return !isPassable(level, ground) && !isPassable(level, ground.above());
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
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
        // Water is crossed, not refused. This used to return no footing at all
        // on the theory that the planner would have routed the ring onto dry
        // land -- it does not, and on one measured ring a hundred and fifty of
        // two and a half thousand positions were open water. That is a
        // six-per-cent hole in a wall whose entire job is not having holes, and
        // it sat where a stream crossed the line, which is the most ordinary
        // terrain there is.
        //
        // The descent above has already stopped on the first solid thing under
        // the column, so a post over water stands at the waterline: a palisade
        // carried across the stream on its posts, which is both what a town
        // would build and a line nothing can walk through.
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
        return isPostBlock(state) || isLamp(state)
                || state.is(Blocks.OAK_FENCE_GATE);
    }

    /**
     * Whether this is one of the wall's lights, of any vintage.
     *
     * <p>Torches are still recognised. Any that an earlier build managed to
     * leave standing are ours, and a ground-finding that stopped seeing them
     * would found a post on top of one.
     */
    private static boolean isLamp(BlockState state) {
        return state.is(LAMP) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);
    }

    /**
     * Whether this is a palisade post.
     *
     * <p>A fence, and only a fence. It used to accept oak logs too, so that a
     * town walled before the fence was adopted kept its wall — and that
     * generosity was measured in a wood: <strong>181 of 666 positions on one
     * ring were tree trunks being counted as wall.</strong> An oak log in the
     * line satisfied "a post is standing here", so no post was ever placed;
     * {@link #isOurs} then reported the same trunk as the town's own work, so
     * {@link #clearGrowth} stepped over it rather than felling it. The trees
     * were not merely in the way of the wall. They <em>were</em> the wall, as
     * far as everything that inspects it could tell.
     *
     * <p>Nothing can distinguish a post somebody planted from a trunk that grew
     * there when both are oak logs, so the wall stops trying. Any real legacy
     * post is now growth, and gets replaced by a fence on the sweep that
     * reaches it — which is the outcome wanted in both cases anyway.
     */
    private static boolean isPostBlock(BlockState state) {
        return state.is(POST);
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
    private static void takeDownWhatIsHanging(
            ServerLevel level, BlockPos ground, boolean lit) {
        // Start above this post's own top, or it demolishes itself. The sweep
        // used to begin at +2 unconditionally, which is precisely where a lit
        // post's lantern stands: every lamp was torn down and rebuilt every
        // second for as long as the town was loaded. Harmless to look at and
        // the same treadmill that halted the wall -- see {@link #CURSOR}.
        for (int dy = lit ? 3 : 2; dy <= HANGING_REACH; dy++) {
            BlockPos above = ground.above(dy);
            if (!level.isLoaded(above)) {
                return;
            }
            BlockState state = level.getBlockState(above);
            if (isPostBlock(state) || isLamp(state)) {
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
        // Only if it survived being placed. A block that pops off the moment it
        // is set -- a torch with nothing to hold it -- is not work done, and
        // counting it as work is what let one bad choice of block halt the
        // whole wall. Whatever the next mistake of this shape is, the sweep now
        // walks past it instead of grinding on it.
        return level.getBlockState(pos).getBlock() == block;
    }

    /** Only air and soft growth give way; a wall never eats a building. */
    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }
}
