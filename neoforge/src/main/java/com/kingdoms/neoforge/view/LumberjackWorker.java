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
            BlockPos log = findLog(level, settlement, area, standing);
            if (log != null) {
                return approachAndFell(level, settlement, worker, log);
            }
        }
        // No trunks left standing, or the stores are full: give the wood back.
        if (settlement.saplingStock() > 0) {
            BlockPos spot = findPlantingSpot(level, settlement, area, standing);
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
        settlement.tallies().record(com.kingdoms.sim.settlement.Tallies.TREES_FELLED);
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
    /**
     * Whether this column is inside the village proper.
     *
     * <p>Trees growing between the houses block every path the town lays and every
     * door its people are trying to reach, so they are fair game whether or not
     * the woodland claim happens to cover them — and they are never replanted.
     */
    private static boolean insideVillage(Settlement settlement, int x, int z) {
        SimPos centre = settlement.centre();
        long dx = x - centre.x();
        long dz = z - centre.z();
        long reach = settlement.claimRadius();
        return dx * dx + dz * dz <= reach * reach;
    }

    private static BlockPos findLog(ServerLevel level, Settlement settlement,
                                    WorkArea area, BlockPos from) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int examined = 0;

        // Search whichever is wider: the woodland claim, or the village itself.
        // Both are worked — the claim for timber, the village to keep it clear.
        SimPos centre = area.centre();
        SimPos town = settlement.centre();
        int r = Math.max(area.radius(), settlement.claimRadius());
        boolean villageFirst = true;
        for (int dx = -r; dx <= r && examined < MAX_COLUMNS_PER_SCAN; dx++) {
            for (int dz = -r; dz <= r && examined < MAX_COLUMNS_PER_SCAN; dz++) {
                int x = town.x() + dx;
                int z = town.z() + dz;
                boolean inVillage = insideVillage(settlement, x, z);
                if (!inVillage && !area.contains(new SimPos(x, centre.y(), z))) {
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
                    // A trunk standing in the village outranks one in the wood,
                    // however far away it is: that is the one in somebody's way.
                    if (inVillage && villageFirst) {
                        villageFirst = false;
                        bestDistance = Double.MAX_VALUE;
                    }
                    if (inVillage || villageFirst) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** Bare ground in the area with headroom, for putting a sapling back. */
    /**
     * Somewhere to put a sapling — inside the woodland claim, and never in the
     * village. Replanting where the town walks is how the paths got blocked in
     * the first place.
     */
    private static BlockPos findPlantingSpot(ServerLevel level, Settlement settlement,
                                             WorkArea area, BlockPos from) {
        SimPos centre = area.centre();
        int r = area.radius();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int examined = 0;

        for (int dx = -r; dx <= r && examined < MAX_COLUMNS_PER_SCAN; dx++) {
            for (int dz = -r; dz <= r && examined < MAX_COLUMNS_PER_SCAN; dz++) {
                int x = centre.x() + dx;
                int z = centre.z() + dz;
                if (!area.contains(new SimPos(x, centre.y(), z))
                        || insideVillage(settlement, x, z)) {
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
