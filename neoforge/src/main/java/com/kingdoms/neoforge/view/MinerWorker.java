package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Tallies;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.settlement.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A miner's turn of work: cut stone inside the mine's claim, and stop when the
 * stores are full rather than hollowing out the hillside.
 *
 * <p>The woodland twin of this is {@link LumberjackWorker}, and it works the same
 * way — walk to the nearest block worth taking, swing at it, credit the town.
 * The differences are the ones that matter underground: miners cut <em>downward
 * and inward</em> from the mine head rather than across a surface, they will not
 * touch the last layer above bedrock, and they leave a floor under themselves so
 * the shaft stays walkable.
 */
public final class MinerWorker {

    /** How close a miner has to be to swing at a block. */
    private static final double WORK_REACH = 4.5;

    /** Positions probed per turn, so a big claim cannot stall a tick. */
    private static final int MAX_PROBES = 900;

    /** Stone credited per block cut. */
    private static final int STONE_PER_BLOCK = 1;

    /** Iron credited per vein cut through. */
    private static final int IRON_PER_ORE = 2;

    /** Never cut below this, so nobody digs into the void or strips bedrock. */
    private static final int FLOOR_MARGIN = 6;

    /** How far below the mine head the workings may reach. */
    private static final int MAX_DEPTH = 20;

    private MinerWorker() {
    }

    /**
     * One miner's turn of work.
     *
     * @return true if the world or the town's stores changed
     */
    public static boolean work(ServerLevel level, Settlement settlement, PersonEntity worker) {
        WorkArea area = settlement.mineArea();
        if (area == null || !MinePlanner.wantsMoreStone(settlement)) {
            return false;
        }
        BlockPos face = findFace(level, area, worker.blockPosition());
        if (face == null) {
            return false;
        }
        if (!withinReach(worker, face)) {
            walkTo(worker, face);
            return false;
        }
        worker.getLookControl().setLookAt(face.getX() + 0.5, face.getY() + 0.5, face.getZ() + 0.5);
        worker.swing(InteractionHand.MAIN_HAND);

        boolean ore = level.getBlockState(face).is(BlockTags.IRON_ORES);
        level.destroyBlock(face, false);
        if (ore) {
            // Iron is the one thing a town cannot cut out of a hillside, and the
            // forge runs on it. Ore found while cutting is where it all comes from.
            settlement.stores().add(TownStores.IRON, IRON_PER_ORE);
            settlement.tallies().record(Tallies.STONE_CUT);
        } else {
            settlement.stores().addCapped(TownStores.STONE, STONE_PER_BLOCK,
                    MinePlanner.stoneCapacity(settlement));
            settlement.tallies().record(Tallies.STONE_CUT);
        }
        return true;
    }

    /**
     * The nearest block of stone worth cutting, or null when there is none.
     *
     * <p>Searched from the mine head downward, so the workings deepen rather than
     * spreading across the surface and eating the village green.
     */
    private static BlockPos findFace(ServerLevel level, WorkArea area, BlockPos from) {
        SimPos centre = area.centre();
        int radius = area.radius();
        int minY = Math.max(level.getMinY() + FLOOR_MARGIN, centre.y() - MAX_DEPTH);

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int probed = 0;

        for (int y = centre.y(); y >= minY && probed < MAX_PROBES; y--) {
            for (int dx = -radius; dx <= radius && probed < MAX_PROBES; dx++) {
                for (int dz = -radius; dz <= radius && probed < MAX_PROBES; dz++) {
                    probed++;
                    BlockPos pos = new BlockPos(centre.x() + dx, y, centre.z() + dz);
                    if (!level.isLoaded(pos) || !isWorkableStone(level, pos)) {
                        continue;
                    }
                    // Only a face somebody can actually get at: reaching into
                    // solid rock from inside solid rock is not mining.
                    if (!isExposed(level, pos)) {
                        continue;
                    }
                    double distance = pos.distSqr(from);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
            if (best != null) {
                return best;   // finish this layer before starting the next
            }
        }
        return best;
    }

    /** Stone and its kin only — never ore, never the ground somebody is standing on. */
    private static boolean isWorkableStone(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_BRICKS)
                || state.is(BlockTags.IRON_ORES);
    }

    /** Whether at least one side of this block is open, so it can be reached. */
    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (BlockPos neighbour : new BlockPos[]{
                pos.above(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (!level.getBlockState(neighbour).isSolidRender()) {
                return true;
            }
        }
        return false;
    }

    private static boolean withinReach(PersonEntity worker, BlockPos pos) {
        return worker.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= WORK_REACH * WORK_REACH;
    }

    private static void walkTo(PersonEntity worker, BlockPos pos) {
        worker.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.7);
    }
}
