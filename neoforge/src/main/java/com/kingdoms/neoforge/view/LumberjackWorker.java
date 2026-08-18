package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Felling and replanting inside the camp's {@link WorkArea}.
 *
 * <p>The loop a lumberjack runs: find the nearest standing trunk in the area,
 * walk to it, chop it log by log — timber going straight into the town's stores,
 * with the odd sapling saved from the crown — and when the area is clear of
 * trunks, plant one of those saplings on bare ground so the wood grows back.
 *
 * <p>Trunk-finding leans on the motion-blocking-no-leaves heightmap, which
 * reports the top of a column ignoring foliage: over a tree that is the highest
 * log, so one heightmap read per column finds trees without scanning volumes.
 * Columns are searched nearest-first and the scan is capped, so a large work
 * area costs a bounded amount per attempt rather than a spike.
 */
public final class LumberjackWorker {

    /** Reach for felling and planting — an arm's length plus a bit. */
    private static final double WORK_REACH = 4.5;

    /** Columns examined per search before giving up until the next pass. */
    private static final int MAX_COLUMNS_PER_SCAN = 900;

    /** Timber yielded per log. */
    private static final int WOOD_PER_LOG = 1;

    /** One log in four leaves a usable sapling, keyed by position so it never drifts. */
    private static final int SAPLING_EVERY = 4;

    private LumberjackWorker() {
    }

    /**
     * One lumberjack's turn of work.
     *
     * @return true if the world or the town's stores changed
     */
    public static boolean work(ServerLevel level, Settlement settlement, PersonEntity worker) {
        WorkArea area = settlement.lumberArea();
        if (area == null) {
            return false;
        }
        BlockPos standing = worker.blockPosition();

        if (LumberPlanner.wantsMoreTimber(settlement)) {
            BlockPos log = findLog(level, area, standing);
            if (log != null) {
                return approachAndFell(level, settlement, worker, log);
            }
        }
        // No trunks left standing, or the stores are full: give the wood back.
        if (settlement.saplingStock() > 0) {
            BlockPos spot = findPlantingSpot(level, area, standing);
            if (spot != null) {
                return approachAndPlant(level, settlement, worker, spot);
            }
        }
        return false;
    }

    private static boolean approachAndFell(ServerLevel level, Settlement settlement,
                                           PersonEntity worker, BlockPos log) {
        if (!withinReach(worker, log)) {
            walkTo(worker, log);
            return false;
        }
        worker.getLookControl().setLookAt(log.getX() + 0.5, log.getY() + 0.5, log.getZ() + 0.5);
        worker.swing(InteractionHand.MAIN_HAND);

        level.destroyBlock(log, false);
        settlement.setWoodStock(Math.min(
                LumberPlanner.woodCapacity(settlement), settlement.woodStock() + WOOD_PER_LOG));
        if (Math.floorMod(log.asLong(), SAPLING_EVERY) == 0) {
            settlement.setSaplingStock(settlement.saplingStock() + 1);
        }
        return true;
    }

    private static boolean approachAndPlant(ServerLevel level, Settlement settlement,
                                            PersonEntity worker, BlockPos spot) {
        if (!withinReach(worker, spot)) {
            walkTo(worker, spot);
            return false;
        }
        worker.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        worker.swing(InteractionHand.MAIN_HAND);

        level.setBlockAndUpdate(spot, Blocks.OAK_SAPLING.defaultBlockState());
        settlement.setSaplingStock(settlement.saplingStock() - 1);
        return true;
    }

    // --- finding work ---

    /** Nearest standing trunk in the area, or null when the wood is clear. */
    private static BlockPos findLog(ServerLevel level, WorkArea area, BlockPos from) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int examined = 0;

        SimPos centre = area.centre();
        int r = area.radius();
        for (int dx = -r; dx <= r && examined < MAX_COLUMNS_PER_SCAN; dx++) {
            for (int dz = -r; dz <= r && examined < MAX_COLUMNS_PER_SCAN; dz++) {
                int x = centre.x() + dx;
                int z = centre.z() + dz;
                if (!area.contains(new SimPos(x, centre.y(), z))) {
                    continue;
                }
                double distance = from.distSqr(new BlockPos(x, from.getY(), z));
                if (distance >= bestDistance) {
                    continue;   // cannot beat what we have, so skip the chunk read
                }
                if (!level.isLoaded(new BlockPos(x, centre.y(), z))) {
                    continue;
                }
                examined++;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos candidate = new BlockPos(x, top, z);
                BlockState state = level.getBlockState(candidate);
                if (state.is(BlockTags.LOGS)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** Bare ground in the area with headroom, for putting a sapling back. */
    private static BlockPos findPlantingSpot(ServerLevel level, WorkArea area, BlockPos from) {
        SimPos centre = area.centre();
        int r = area.radius();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int examined = 0;

        for (int dx = -r; dx <= r && examined < MAX_COLUMNS_PER_SCAN; dx++) {
            for (int dz = -r; dz <= r && examined < MAX_COLUMNS_PER_SCAN; dz++) {
                int x = centre.x() + dx;
                int z = centre.z() + dz;
                if (!area.contains(new SimPos(x, centre.y(), z))) {
                    continue;
                }
                double distance = from.distSqr(new BlockPos(x, from.getY(), z));
                if (distance >= bestDistance) {
                    continue;
                }
                if (!level.isLoaded(new BlockPos(x, centre.y(), z))) {
                    continue;
                }
                examined++;
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = new BlockPos(x, surface - 1, z);
                BlockPos above = ground.above();
                if (!level.getBlockState(ground).is(BlockTags.DIRT)) {
                    continue;
                }
                if (!level.getBlockState(above).isAir()
                        || !level.getBlockState(above.above()).isAir()) {
                    continue;
                }
                best = above;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean withinReach(PersonEntity worker, BlockPos pos) {
        return worker.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= WORK_REACH * WORK_REACH;
    }

    private static void walkTo(PersonEntity worker, BlockPos pos) {
        worker.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.7);
    }
}
