package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.world.HandDig;
import com.kingdoms.neoforge.world.WallClearing;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.work.PublicWorks;
import com.kingdoms.sim.work.Worksite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;

import java.util.List;

/**
 * Sends a spare builder to whatever public work needs a body next.
 *
 * <p>One loop for all of them. The wall had its own worker for about an hour,
 * and writing a second one for roads made it obvious that the third would need
 * a fourth — so what a public work has in common lives in {@link Worksite} and
 * this consumes any of them. A quarry or a bridge later is a class answering
 * three questions rather than another worker, another tick pass, and another
 * argument about who is free to do it.
 *
 * <p><strong>Priority is the order of the list, and nothing else.</strong> The
 * town offers its works most-important-first and this takes the first one with
 * a job in a loaded chunk. Deliberately crude: a settlement with a half-built
 * wall and a half-built road should finish one of them rather than alternate
 * between the two forever.
 *
 * <p>Buildings still come first, checked before this is called at all. Shelter
 * and stores before roads and walls, which is the same order the abstract clock
 * has always used.
 */
public final class Foreman {

    /** How close somebody has to be to work at a station. */
    private static final double WORK_REACH = 3.0;

    private static final double WALK_SPEED = 0.65;

    private Foreman() {
    }

    /**
     * One pass for one builder.
     *
     * @return true if they were given something to do, walking to it included
     */
    public static boolean work(ServerLevel level, Settlement settlement,
                               PersonEntity builder) {
        List<Worksite> works = PublicWorks.of(settlement);
        for (Worksite work : works) {
            if (!work.isWorthStarting(settlement)) {
                continue;
            }
            SimPos station = work.nextStation(settlement);
            if (station == null) {
                continue;
            }
            BlockPos at = new BlockPos(station.x(), station.y(), station.z());
            if (!level.isLoaded(at)) {
                continue;   // the far side of the town; the clock has that stretch
            }
            if (builder.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)
                    > WORK_REACH * WORK_REACH) {
                builder.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                        WALK_SPEED);
                return true;
            }
            // Ground first. A wall used to be built straight through a wood: its
            // footing is found with a heightmap that steps over leaves and not
            // over logs, so a post whose column held a trunk was founded on top
            // of the trunk, and a canopy reaching across the line gave anything
            // outside a floor to walk in on. A tree in the way is a job before
            // it is an obstacle, and it is felled by hand at the speed an axe
            // takes -- the same order a person would do it in.
            BlockPos growth = WallClearing.inTheWay(level, at);
            if (growth != null) {
                if (builder.distanceToSqr(growth.getX() + 0.5, growth.getY() + 0.5,
                        growth.getZ() + 0.5) > WORK_REACH * WORK_REACH) {
                    builder.getNavigation().moveTo(growth.getX() + 0.5, growth.getY(),
                            growth.getZ() + 0.5, WALK_SPEED);
                    return true;
                }
                if (HandDig.strike(level, builder, growth)) {
                    // Fell the whole tree from the one block that gave, so a
                    // crown fifteen blocks up does not have to be picked at from
                    // a ladder nobody has.
                    fell(level, growth);
                }
                return true;   // still clearing; the post is not due yet
            }

            // Standing at it, and the line is clear. Charged now, with somebody
            // there ready to do the work -- never on the strength of a plan.
            if (!work.pay(settlement)) {
                continue;   // cannot afford this one; see whether the next is cheaper
            }
            builder.getLookControl().setLookAt(
                    at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
            builder.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
            work.completeOne(settlement);
            return true;
        }
        return false;
    }

    /**
     * Brings down the whole tree the given block belongs to.
     *
     * <p>Breadth-first from the block that gave, through logs and leaves only,
     * so a felled trunk takes its own canopy with it rather than leaving a
     * crown floating over the wall it was in the way of. Bounded, because a
     * dark oak in a forest is joined to a great many of its neighbours by
     * touching leaves and a wall builder should not be made to clear a county.
     */
    private static void fell(ServerLevel level, BlockPos from) {
        java.util.Deque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(from);
        seen.add(from);
        int taken = 0;
        while (!queue.isEmpty() && taken < MOST_OF_ONE_TREE) {
            BlockPos at = queue.poll();
            if (!level.isLoaded(at)) {
                continue;
            }
            if (!WallClearing.isGrowth(level.getBlockState(at))) {
                continue;
            }
            level.destroyBlock(at, false, null, 512);
            taken++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = at.offset(dx, dy, dz);
                        if (seen.add(next)) {
                            queue.add(next);
                        }
                    }
                }
            }
        }
    }

    /**
     * Blocks one felling will take at most.
     *
     * <p>Generous for a tree and small for a forest. Whatever is left standing
     * is simply found again on the next pass, so the cap costs a little time
     * and never leaves the wall permanently blocked.
     */
    private static final int MOST_OF_ONE_TREE = 400;
}
