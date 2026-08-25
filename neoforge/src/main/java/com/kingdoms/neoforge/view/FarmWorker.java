package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.FieldRoster;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A farmer actually farming.
 *
 * <p>Until this existed, the wheat in every field was scenery: food was a number
 * credited by the clock, and the crops a farmer stood beside all day were never
 * once touched by them. Now the field IS the farm where anyone is watching —
 * mature wheat is harvested into the farm's stores and replanted in the same
 * swing, growing crops are tended forward (vanilla's own growth rate would feed
 * nobody), and bare soil is planted. The clock still runs the unwatched farms,
 * and floors a watched one whose farmers cannot reach the field — see
 * {@link FoodPlanner}, which is where the two fidelities meet.
 *
 * <p>One action per pass, like the lumberjacks: the work is meant to be watched.
 */
public final class FarmWorker {

    /** Arm's reach for working a crop, matching the other trades. */
    private static final double WORK_REACH = 4.5;

    /** How far afield a farmer looks for their farm before giving up. */
    private static final double FARM_SEARCH = 32.0;

    private static final double WALK_SPEED = 0.6;

    /** Field cells examined per pass, so an 11-wide farm stays cheap to scan. */
    private static final int MAX_CELLS_PER_SCAN = 200;

    private FarmWorker() {
    }

    /**
     * One pass of one farmer.
     *
     * @return true if they are engaged with field work, walking to it included
     */
    public static boolean work(ServerLevel level, Settlement settlement, long step,
                               PersonEntity farmer) {
        return work(level, settlement, step, farmer, null);
    }

    /** The same, told which person this body stands for so the roster can be read. */
    public static boolean work(ServerLevel level, Settlement settlement, long step,
                               PersonEntity farmer, Person person) {
        // The rostered field first: the farmer has been walked here on purpose,
        // and picking "nearest" again would let two farmers standing between two
        // fields each adopt the other's.
        Building farm = FieldRoster.fieldFor(settlement, person);
        if (farm == null) {
            farm = nearestCropFarm(settlement, farmer);
        }
        if (farm == null) {
            return false;
        }
        Footprint plot = farm.footprint();
        if (!plot.isKnown()) {
            return false;
        }
        // The field, not the plot it was recorded as: the apron comes back off to
        // find the fence line. This was a hardcoded two, which was right only for
        // as long as the placer's margin also happened to be two — the sort of
        // agreement that stays correct right up until somebody changes one of them.
        int half = Math.max(2, plot.width() / 2 - BlueprintPlacer.APRON_MARGIN);
        int floor = plot.y();
        SimPos origin = farm.origin();

        // The best thing to do right now, nearest first — see nextJob for why
        // planting beats tending, which is not the order this used to use.
        BlockPos harvest = null;
        BlockPos tend = null;
        BlockPos plant = null;
        int examined = 0;
        for (int dx = -half; dx <= half && examined < MAX_CELLS_PER_SCAN; dx++) {
            for (int dz = -half; dz <= half && examined < MAX_CELLS_PER_SCAN; dz++) {
                examined++;
                BlockPos soil = new BlockPos(origin.x() + dx, floor - 1, origin.z() + dz);
                if (!level.isLoaded(soil) || !level.getBlockState(soil).is(Blocks.FARMLAND)) {
                    continue;
                }
                BlockPos cell = soil.above();
                BlockState standing = level.getBlockState(cell);
                if (standing.getBlock() instanceof CropBlock crop) {
                    if (crop.isMaxAge(standing)) {
                        harvest = closer(farmer, harvest, cell);
                    } else {
                        tend = closer(farmer, tend, cell);
                    }
                } else if (standing.isAir()) {
                    plant = closer(farmer, plant, cell);
                }
            }
        }

        BlockPos target = nextJob(harvest, plant, tend);
        if (target == null) {
            return false;   // a full field of growing crops needs nothing yet
        }
        if (farmer.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5,
                target.getZ() + 0.5) > WORK_REACH * WORK_REACH) {
            farmer.getNavigation().moveTo(target.getX() + 0.5, target.getY(),
                    target.getZ() + 0.5, WALK_SPEED);
            return true;
        }

        farmer.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5,
                target.getZ() + 0.5);
        farmer.swing(InteractionHand.MAIN_HAND);

        if (target.equals(harvest)) {
            // Harvest and replant in the same motion. No drops: the yield goes
            // straight onto the farm's stores, which is the whole point — this
            // is the visible form of the number the clock used to conjure.
            level.setBlock(target, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (farm.foodStored() < FoodPlanner.FARM_STORE_CAP) {
                farm.setFoodStored(farm.foodStored() + 1);
            }
            farm.touchRealHarvest(step);
        } else if (target.equals(tend)) {
            BlockState standing = level.getBlockState(target);
            level.setBlock(target,
                    standing.setValue(CropBlock.AGE,
                            standing.getValue(CropBlock.AGE) + 1),
                    Block.UPDATE_CLIENTS);
        } else {
            level.setBlock(target, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /**
     * What a farmer does next, given the nearest of each job available.
     *
     * <p>Harvest first: a mature crop is food standing out in the weather, and
     * harvesting replants the cell in the same motion.
     *
     * <p>Then planting, and this is the part that was wrong. Tending used to
     * come second, which sounds harmless and starves a field to death. Tending
     * nudges one crop's age up by one; a field with any growing crop in it
     * therefore always has something to tend, so the planting branch was only
     * ever reached in the instant every single crop was simultaneously ripe.
     * A field would fill to whatever it happened to have planted early on and
     * then sit there — the audit reported the same "72 farmland, 34 planted"
     * three sweeps running, with the vanished-crop tracker never firing once,
     * which is what a field that is not losing crops but never gaining them
     * looks like. It read as something destroying the wheat. Nothing was.
     *
     * <p>Planting adds a cell that then grows on its own; tending only hurries
     * one that already is. Fill the field, then optimise it.
     */
    static BlockPos nextJob(BlockPos harvest, BlockPos plant, BlockPos tend) {
        if (harvest != null) {
            return harvest;
        }
        return plant != null ? plant : tend;
    }

    private static BlockPos closer(PersonEntity farmer, BlockPos held, BlockPos candidate) {
        if (held == null) {
            return candidate;
        }
        return farmer.blockPosition().distSqr(candidate)
                < farmer.blockPosition().distSqr(held) ? candidate : held;
    }

    /** The crop farm this farmer works: the nearest one, animal pens excluded. */
    private static Building nearestCropFarm(Settlement settlement, PersonEntity farmer) {
        Building best = null;
        double bestDistance = FARM_SEARCH * FARM_SEARCH;
        for (Building building : settlement.buildings()) {
            String path = Identifier.parse(
                    BuildPlanner.baseIdOf(building.blueprintId())).getPath();
            if (!path.equals("farm") && !path.endsWith("/farm")) {
                continue;
            }
            double distance = farmer.distanceToSqr(building.origin().x() + 0.5,
                    building.origin().y(), building.origin().z() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = building;
            }
        }
        return best;
    }
}
