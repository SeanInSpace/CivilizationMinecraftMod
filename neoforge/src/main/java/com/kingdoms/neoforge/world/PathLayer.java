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
     * The columns one step of paving covers: the one underfoot and the width
     * either side of it.
     *
     * <p>What a crew does at one station. A road is not laid a block at a time
     * and never was — somebody standing on the line of it works across the way,
     * shoulder to shoulder with the verge — so the unit of the work is a
     * cross-section rather than a column. That is also what keeps a stretch a
     * walk rather than an afternoon: a run of thirty is thirty steps, not the
     * two hundred and seventy blocks those thirty steps put down.
     *
     * <p>Pure geometry, so what a paving stretch comes to can be counted without
     * a world; see {@code PathLayerPlanTest}. {@link #mend} covers the same
     * columns by walking every index of the run, which is what makes the sweep
     * that keeps a road clear and the crew that opens one lay the same road.
     */
    public static List<SimPos> crossSectionAt(PathNetwork.Segment segment, int index) {
        List<SimPos> along = segment.positions();
        if (index < 0 || index >= along.size()) {
            return List.of();
        }
        SimPos at = along.get(index);
        int half = halfWidthOf(segment);
        List<SimPos> columns = new java.util.ArrayList<>((2 * half + 1) * (2 * half + 1));
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                columns.add(new SimPos(at.x() + ox, at.y(), at.z() + oz));
            }
        }
        return List.copyOf(columns);
    }

    /**
     * Paves one cross-section of a run, by hand.
     *
     * <p>The crew's half of {@link #mend}, and deliberately the same work in the
     * same order: earn the step up to this column, then lay the surface across
     * it. What it does not do is the repair test — a stretch being opened is
     * broken by definition, and a builder standing on it has already decided
     * there is a road to make here.
     *
     * <p>The steepness of the step behind is asked here as well as of the whole
     * run in {@link #canBePaved}, and the reason is that the run check happens
     * once, when a crew picks a stretch up, and most of that stretch may be in
     * chunks nobody has loaded — {@link #tooSteepToPave} judges only the parts it
     * can see, quite deliberately. So a run whose far half turns out to be a
     * cliff passes it, and without this the crew would gravel the cliff and
     * {@link #mend} would then refuse the run for ever and never clean it up.
     * Column by column, the walkable part of such a run is paved and the wall at
     * the end of it is left as bare ground, which is what a road crew would leave
     * and what a player reads as the way stopping.
     *
     * @return blocks laid, which is zero for a column that is already a road, is
     *         somebody's floor, or stands a wall above the one behind it
     */
    public static int paveAt(ServerLevel level, PathNetwork.Segment segment, int index) {
        List<SimPos> along = segment.positions();
        if (index < 0 || index >= along.size()) {
            return 0;
        }
        int laid = index > 0 ? gradeAt(level, segment, index) : 0;
        if (index > 0 && stepBehindIsAWall(level, along, index)) {
            return laid;
        }
        for (SimPos column : crossSectionAt(segment, index)) {
            laid += pave(level, column.x(), column.z());
        }
        return laid;
    }

    /** Whether the way climbs more between the last column and this one than a road may. */
    private static boolean stepBehindIsAWall(ServerLevel level, List<SimPos> along, int index) {
        BlockPos behind = surfaceOf(level, along.get(index - 1).x(), along.get(index - 1).z());
        BlockPos here = surfaceOf(level, along.get(index).x(), along.get(index).z());
        return behind != null && here != null
                && Math.abs(here.getY() - behind.getY()) > MAX_STEP_UNGRADED;
    }

    /**
     * Whether a run is one a road can be made of at all.
     *
     * <p>Asked once, when a crew takes a stretch on, rather than at every column
     * — it reads the whole run, and asking it thirty times over would be thirty
     * runs' worth of block reads for one run of road. A stretch that fails it is
     * opened without being paved, which is exactly what the sweep did with one
     * before roads were a job: better a gap in the network, which the town routes
     * around and a player reads as untrodden ground, than a gravel stripe up a
     * cliff face that nothing can walk.
     */
    public static boolean canBePaved(ServerLevel level, PathNetwork.Segment segment) {
        return !tooSteepToPave(level, segment);
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
        int moved = 0;
        for (int i = 1; i < segment.positions().size(); i++) {
            moved += gradeAt(level, segment, i);
        }
        return moved;
    }

    /** The one step of a run a crew standing at this column can earn. */
    private static int gradeAt(ServerLevel level, PathNetwork.Segment segment, int index) {
        int half = halfWidthOf(segment);
        List<SimPos> along = segment.positions();
        SimPos behind = along.get(index - 1);
        SimPos here = along.get(index);
        int moved = 0;
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                moved += levelStep(level,
                        behind.x() + ox, behind.z() + oz, here.x() + ox, here.z() + oz);
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
