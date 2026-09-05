package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.RoadRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carries a road over the water it could not go round.
 *
 * <p>Water was impassable to a road, absolutely and in three separate places:
 * the search skipped a wet cell, the fine check between two cells refused one,
 * and the layer had no block to put down over it. So a river cut a town's
 * network in half and a settlement founded on two banks was two settlements
 * that shared a name. None of that was a decision — it is what you get when the
 * only answer available is no.
 *
 * <p>{@link RoadRouter} now prices water instead of refusing it, and refuses
 * only a span longer than {@link RoadRouter#LONGEST_BRIDGE}, so a road takes the
 * narrowest crossing it can find and gives up on open water. This is the other
 * half: where a laid road meets water, it gets a deck.
 *
 * <p>Deliberately a simple structure, and free the way paving is free. A bridge
 * is part of the way, not a building — a village that cannot cross its own
 * stream because it is short of stone is a village with a bug in it, not a
 * village with a supply problem. If bridges ever want to be paid for, they
 * should become a {@code BuildingType} and be sited like one.
 */
public final class Bridge {

    private Bridge() {
    }

    /**
     * How far the middle of a crossing rises above its ends.
     *
     * <p>Two blocks. A flat deck at water level reads as a raft; the arch is
     * what makes it a bridge, and it is also what lets a boat under. Any more
     * than two on the spans these rivers want and the approach stops being a
     * step and starts being a stair.
     */
    private static final int RISE = 2;

    /** Blocks along the deck per block of rise, so the arch is walkable. */
    private static final int RUN_PER_RISE = 4;

    /** Clearance kept over the deck, so a person can walk across it. */
    private static final int HEADROOM = 3;

    /** How often a pier is dropped to the riverbed. */
    private static final int PIER_EVERY = 5;

    /** How far a pier reaches down before it gives up on finding the bed. */
    private static final int PIER_DEPTH = 16;

    /** How far above the heightmap to start looking for the top of the water. */
    private static final int WATER_PROBE = 4;

    private static final int NO_WATER = Integer.MIN_VALUE;

    /**
     * Decks whatever part of this run stands over water.
     *
     * <p>Called from the paving sweep, so it must be cheap when there is nothing
     * to do — which is nearly always, since nearly no run crosses water and a
     * crossing that has already been decked has nothing missing.
     *
     * @return blocks laid; zero for a run on dry land or a bridge already sound
     */
    public static int span(ServerLevel level, PathNetwork.Segment segment) {
        List<SimPos> line = segment.positions();
        int half = Math.max(1, segment.width() / 3);
        int laid = 0;
        int from = -1;
        for (int i = 0; i <= line.size(); i++) {
            boolean wet = i < line.size() && waterTopAt(level, line.get(i)) != NO_WATER;
            if (wet && from < 0) {
                from = i;
            }
            if (!wet && from >= 0) {
                laid += deck(level, line, from, i - 1, half);
                from = -1;
            }
        }
        return laid;
    }

    /**
     * One crossing, from the last dry block on one side to the first on the other.
     *
     * <p>The span is checked again here even though the router already refused
     * anything longer. The router asks the terrain oracle, which answers from the
     * generator's noise for chunks nobody has loaded; this is standing on the
     * blocks themselves, and the two do not always agree about where a lake is.
     * A deck three hundred blocks long laid because of that disagreement would be
     * the most visible bug in the mod.
     */
    private static int deck(ServerLevel level, List<SimPos> line, int first, int last,
                            int half) {
        int length = last - first + 1;
        if (length > RoadRouter.LONGEST_BRIDGE) {
            return 0;   // open water, whatever the oracle thought
        }

        // The deck stands level with whichever is higher: the water it crosses,
        // or the banks it has to meet. A deck below its own bank is a step down
        // into a hole at each end.
        int base = NO_WATER;
        for (int i = first; i <= last; i++) {
            base = Math.max(base, waterTopAt(level, line.get(i)) + 1);
        }
        if (base == NO_WATER) {
            return 0;
        }
        for (int bank : new int[] {first - 1, last + 1}) {
            if (bank >= 0 && bank < line.size()) {
                BlockPos on = groundAt(level, line.get(bank));
                if (on != null) {
                    base = Math.max(base, on.getY());
                }
            }
        }

        // Gathered before anything is laid, because the railings are decided by
        // which columns turned out to have a neighbor and which did not, and
        // that cannot be known one column at a time.
        //
        // Held as real positions rather than as three loose numbers. They were
        // three loose numbers, in the order {x, z, y}, and every one of them was
        // then handed to a method whose parameters read (x, y, z) -- so the
        // entire deck of a measured crossing went in at y=31 and z=64, sixty
        // blocks underground and thirty to one side. It reported laying
        // eighty-one blocks and it had; there was simply nothing over the water.
        // Nothing threw, the arithmetic was right, and a photograph of the
        // river showed a road stopping at the bank exactly as it had before the
        // bridge existed. What found it was asking the world what block was
        // where, one y at a time.
        Map<Long, BlockPos> deck = new LinkedHashMap<>();
        for (int i = first; i <= last; i++) {
            int lift = Math.min(RISE, Math.min(i - first, last - i) / RUN_PER_RISE);
            SimPos at = line.get(i);
            for (int ox = -half; ox <= half; ox++) {
                for (int oz = -half; oz <= half; oz++) {
                    BlockPos on = new BlockPos(at.x() + ox, base + lift, at.z() + oz);
                    long column = column(on.getX(), on.getZ());
                    BlockPos had = deck.get(column);
                    // The highest of the lifts that reach a column, so an arch
                    // shared between two centerline points does not have a step
                    // cut out of it where their square footprints overlap.
                    if (had == null || on.getY() > had.getY()) {
                        deck.put(column, on);
                    }
                }
            }
        }

        int laid = 0;
        for (BlockPos on : deck.values()) {
            laid += lay(level, on, Blocks.OAK_PLANKS.defaultBlockState());
            for (int dy = 1; dy <= HEADROOM; dy++) {
                BlockPos above = on.above(dy);
                if (level.isLoaded(above) && !level.getFluidState(above).isEmpty()) {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    laid++;
                }
            }
        }

        // Railings where the deck ends over water, and nowhere else. A rail
        // across the mouth of the bridge is a fence somebody has to jump.
        for (BlockPos on : deck.values()) {
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = on.getX() + step[0];
                int nz = on.getZ() + step[1];
                if (deck.containsKey(column(nx, nz))) {
                    continue;
                }
                if (waterTopAt(level, nx, nz) == NO_WATER) {
                    continue;   // the bank: this is where people get on and off
                }
                laid += lay(level, on.above(), Blocks.OAK_FENCE.defaultBlockState());
                break;
            }
        }

        // Piers, so the deck is standing on something. Nothing structural
        // depends on them; a bridge that is visibly held up simply reads as one.
        for (int i = first; i <= last; i++) {
            if ((i - first) % PIER_EVERY != 0) {
                continue;
            }
            SimPos at = line.get(i);
            BlockPos on = deck.get(column(at.x(), at.z()));
            if (on == null) {
                continue;
            }
            for (int y = on.getY() - 1; y > on.getY() - 1 - PIER_DEPTH; y--) {
                BlockPos pos = new BlockPos(at.x(), y, at.z());
                if (!level.isLoaded(pos)) {
                    break;
                }
                BlockState standing = level.getBlockState(pos);
                if (standing.isAir() || !standing.getFluidState().isEmpty()) {
                    level.setBlock(pos, Blocks.OAK_FENCE.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                    laid++;
                    continue;
                }
                break;   // the bed, or something already standing on it
            }
        }
        return laid;
    }

    /** Puts one block down, unless it is already there. */
    private static int lay(ServerLevel level, BlockPos pos, BlockState want) {
        if (!level.isLoaded(pos) || level.getBlockState(pos).is(want.getBlock())) {
            return 0;
        }
        level.setBlock(pos, want, Block.UPDATE_CLIENTS);
        return 1;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int waterTopAt(ServerLevel level, SimPos at) {
        return waterTopAt(level, at.x(), at.z());
    }

    /**
     * The topmost water block in this column, or {@link #NO_WATER}.
     *
     * <p>{@code WORLD_SURFACE} counts fluid, so it lands one above the water and
     * the search downward is a block or two. It stops at the first solid block
     * so that a puddle sitting in a hole under an overhang is not mistaken for
     * the surface of a river.
     */
    private static int waterTopAt(ServerLevel level, int x, int z) {
        BlockPos top = level.getHeightmapPos(
                Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
        for (int y = top.getY(); y >= top.getY() - WATER_PROBE; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos)) {
                return NO_WATER;
            }
            if (level.getFluidState(pos).is(FluidTags.WATER)) {
                return y;
            }
            if (!level.getBlockState(pos).isAir()) {
                return NO_WATER;
            }
        }
        return NO_WATER;
    }

    /** The walkable block of a dry column, or null where the world cannot answer. */
    private static BlockPos groundAt(ServerLevel level, SimPos at) {
        BlockPos on = new BlockPos(at.x(),
                BlueprintPlacer.groundLevel(level, at.x(), at.z()) - 1, at.z());
        return level.isLoaded(on) ? on : null;
    }
}
