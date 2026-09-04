package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.PathNetwork;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws and mends the ways the settlement has trodden.
 *
 * <p>Where the road runs is decided in the simulation and remembered there
 * ({@code PathNetwork}); this only makes a remembered segment visible, and puts
 * it back when it has grown over. Same two-fidelity split as everything else:
 * the road a town believes it has and the road a player can walk must be the
 * same road.
 *
 * <p>Drawing and mending are one operation, which is what makes the whole thing
 * self-healing. A brand-new segment is nothing but gaps, so it is drawn; a worn
 * one is patched only once enough of it has gone that the road has stopped
 * reading as a road. Re-walking a sound stretch costs a handful of block reads
 * and writes nothing.
 *
 * <p>Only natural ground is ever paved and only foliage above it is cleared, so
 * a road can never eat a wall it happens to run past — and a stretch that a
 * building has since been raised over is left alone rather than fought over.
 */
public final class PathLayer {

    /** Blocks of clearance kept above the track. */
    private static final int HEADROOM = 2;

    /**
     * Half-width of one run, so a way is {@code 2 * half + 1} across.
     *
     * <p>Read off the run rather than fixed. A footpath between two buildings is
     * three across, because one block wide read as a trail of crumbs and two
     * people could not pass on it.
     *
     * <p>A street is paved narrower than the plan reserved, which is the
     * difference between a right of way and a metalled surface. The plan keeps
     * plots a setback back from an eight-wide carriageway so that nothing is
     * ever built in the road; paving all eight of it puts a nine-block ribbon of
     * gravel through the village, which reads as a runway rather than a street.
     * A third of the reservation gives a spine five across and a back lane three,
     * with grass verges between the stones and the doorsteps -- and it is the
     * verges that make it look like somewhere people live.
     */
    private static int halfWidthOf(PathNetwork.Segment segment) {
        return Math.max(1, segment.width() / 3);
    }

    /**
     * How much of a stretch must have gone before it is worth re-laying.
     *
     * <p>Not zero: grass creeps back over a corner of a road constantly, and a
     * layer that repaved every blade would rewrite half the town every sweep
     * for no visible gain. A quarter gone is a road with holes in it.
     */
    private static final double REPAIR_FRACTION = 0.25;

    private PathLayer() {
    }

    /**
     * Lays what is missing from one segment, if enough of it is missing.
     *
     * @return blocks paved — zero for a road that is already sound
     */
    public static int mend(ServerLevel level, PathNetwork.Segment segment) {
        // Before the steepness check and before anything else, because a
        // crossing is the one part of a way that is not paving at all: there is
        // no ground under it to be too steep, and the columns it covers read as
        // OTHER to every count below -- water is not a road with holes in it, it
        // is a road with a river across it.
        int decked = Bridge.span(level, segment);
        if (tooSteepToPave(level, segment)) {
            return decked;
        }
        // Earn the steps before laying anything on them. A road that arrives at
        // a two-block rise and stops is a road with a wall at the end of it; a
        // spadeful of dirt turns that rise into two steps anybody can take, and
        // that is what a road crew would do.
        int moved = grade(level, segment);
        int half = halfWidthOf(segment);
        int intact = 0;
        int broken = 0;
        for (SimPos pos : segment.positions()) {
            for (int ox = -half; ox <= half; ox++) {
                for (int oz = -half; oz <= half; oz++) {
                    switch (state(level, pos.x() + ox, pos.z() + oz)) {
                        case PAVED -> intact++;
                        case BARE -> broken++;
                        default -> { }   // not ours: a floor, a wall, water, a drop
                    }
                }
            }
        }
        int ours = intact + broken;
        if (ours == 0 || (double) broken / ours < REPAIR_FRACTION) {
            return decked;
        }
        int laid = moved + decked;
        for (SimPos pos : segment.positions()) {
            for (int ox = -half; ox <= half; ox++) {
                for (int oz = -half; oz <= half; oz++) {
                    laid += pave(level, pos.x() + ox, pos.z() + oz);
                }
            }
        }
        return laid;
    }

    /**
     * Whether the real ground under this run is too steep to be a road.
     *
     * <p>The last line, and the only one standing on ground that is certainly
     * known. The simulation refuses steep runs when it can, but an unwatched
     * town lays and opens its roads without a single chunk loaded — the terrain
     * oracle answers from the generator's noise, which is smooth where real
     * ground is jagged, and forty-eight opened runs of a measured town still
     * climbed two blocks a step or more, one of them sixteen.
     *
     * <p>Here the blocks are in front of us. A run that climbs more than a block
     * between one column and the next is left unpaved: better a gap in the
     * network, which the town will route around and a player reads as untrodden
     * ground, than a gravel stripe up a cliff face that nothing can walk.
     */
    private static boolean tooSteepToPave(ServerLevel level, PathNetwork.Segment segment) {
        int last = Integer.MIN_VALUE;
        for (SimPos pos : segment.positions()) {
            BlockPos surface = surfaceOf(level, pos.x(), pos.z());
            if (surface == null) {
                last = Integer.MIN_VALUE;   // unloaded: judge the parts we can see
                continue;
            }
            if (last != Integer.MIN_VALUE
                    && Math.abs(surface.getY() - last) > MAX_STEP_UNGRADED) {
                return true;
            }
            last = surface.getY();
        }
        return false;
    }

    /**
     * The most a road may climb between one block and the next, unaided.
     *
     * <p>One block is a step anybody takes without thinking.
     */
    private static final int MAX_STEP = 1;

    /**
     * The most a road may climb before no amount of digging will help.
     *
     * <p>Two, because two is one spadeful from being one and one: raise the low
     * side a block, or take a block off the high side, and what was a wall
     * becomes a pair of steps. Three cannot be fixed by moving one block, and a
     * road crew that moved three would be terracing the hillside rather than
     * making a way across it.
     */
    private static final int MAX_STEP_UNGRADED = 2;

    /**
     * Cuts and fills the two-block steps along a way into pairs of one.
     *
     * <p>The last piece of the answer to roads nobody can walk. Everything
     * before it could only <em>choose</em> — a better line, or no line at all —
     * and a town on rough ground ran out of good lines. This is the part where
     * the road changes the ground instead of the ground refusing the road.
     *
     * <p>Fill before cut, because filling is what a road crew does: a barrow of
     * earth into the dip is easier than quarrying the rise, and it leaves the
     * hillside as it was. Cutting is the fallback for a step whose low side is
     * something that must not be built on.
     *
     * <p><strong>Bounded by geometry, not by memory.</strong> It only acts where
     * acting turns a two-step into two one-steps, so running it again over a way
     * it has already graded does nothing at all — which matters, because mending
     * re-runs over every opened way constantly and a rule that could act twice
     * would terrace a hillside one sweep at a time.
     */
    private static int grade(ServerLevel level, PathNetwork.Segment segment) {
        int half = halfWidthOf(segment);
        List<SimPos> along = segment.positions();
        int moved = 0;
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                for (int i = 1; i < along.size(); i++) {
                    moved += levelStep(level,
                            along.get(i - 1).x() + ox, along.get(i - 1).z() + oz,
                            along.get(i).x() + ox, along.get(i).z() + oz);
                }
            }
        }
        return moved;
    }

    /** Turns one two-block step between neighbouring columns into two of one. */
    private static int levelStep(ServerLevel level, int ax, int az, int bx, int bz) {
        BlockPos lower = surfaceOf(level, ax, az);
        BlockPos higher = surfaceOf(level, bx, bz);
        if (lower == null || higher == null) {
            return 0;
        }
        if (lower.getY() > higher.getY()) {
            BlockPos swap = lower;
            lower = higher;
            higher = swap;
        }
        if (higher.getY() - lower.getY() != MAX_STEP_UNGRADED) {
            return 0;   // level enough already, or past helping
        }
        // Fill: a block of earth on the low side, if the low side is ours.
        if (isPaveable(level.getBlockState(lower))
                && level.getBlockState(lower.above()).isAir()) {
            level.setBlock(lower.above(), Blocks.DIRT.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            return 1;
        }
        // Cut: take the top off the high side, if there is natural ground under
        // it and nothing standing on it.
        if (isPaveable(level.getBlockState(higher))
                && isPaveable(level.getBlockState(higher.below()))
                && level.getBlockState(higher.above()).isAir()) {
            level.setBlock(higher, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return 1;
        }
        return 0;
    }

    /** How much of a segment is still road, for reports and audits. */
    public static double soundness(ServerLevel level, PathNetwork.Segment segment) {
        int intact = 0;
        int ours = 0;
        for (SimPos pos : segment.positions()) {
            Column column = state(level, pos.x(), pos.z());
            if (column == Column.PAVED) {
                intact++;
                ours++;
            } else if (column == Column.BARE) {
                ours++;
            }
        }
        return ours == 0 ? 1.0 : (double) intact / ours;
    }

    /** What one column of a road currently is. */
    private enum Column {
        /** Already a trodden way. */
        PAVED,
        /** Ground a road could wear into, but has not. */
        BARE,
        /** Nothing to do with us: built on, flooded, or out of the loaded world. */
        OTHER
    }

    private static Column state(ServerLevel level, int x, int z) {
        BlockPos surface = surfaceOf(level, x, z);
        if (surface == null) {
            return Column.OTHER;
        }
        BlockState state = level.getBlockState(surface);
        if (state.is(Blocks.DIRT_PATH)) {
            return Column.PAVED;
        }
        return isPaveable(state) ? Column.BARE : Column.OTHER;
    }

    /**
     * Paves one column: the surface block becomes a path, and the air above it
     * is opened up.
     *
     * @return 1 if anything was laid
     */
    private static int pave(ServerLevel level, int x, int z) {
        BlockPos surface = surfaceOf(level, x, z);
        if (surface == null) {
            return 0;
        }
        BlockState state = level.getBlockState(surface);
        if (!isPaveable(state)) {
            return 0;   // a floor, a roof, or water: not ours to pave
        }
        level.setBlock(surface, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_CLIENTS);

        for (int dy = 1; dy <= HEADROOM; dy++) {
            BlockPos above = surface.above(dy);
            BlockState overhead = level.getBlockState(above);
            // Only foliage is cleared. Anything built stays exactly where it is.
            if (overhead.is(BlockTags.LEAVES) || overhead.is(BlockTags.FLOWERS)
                    || overhead.is(Blocks.SHORT_GRASS) || overhead.is(Blocks.TALL_GRASS)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return 1;
    }

    /** The walkable block of a column, or null where the world cannot answer. */
    private static BlockPos surfaceOf(ServerLevel level, int x, int z) {
        // Ground, not canopy: a track through a wood must follow the floor of it.
        BlockPos surface = new BlockPos(x, BlueprintPlacer.groundLevel(level, x, z) - 1, z);
        return level.isLoaded(surface) ? surface : null;
    }

    /** Grass and dirt only — the things a track actually wears into. */
    private static boolean isPaveable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT);
    }
}
