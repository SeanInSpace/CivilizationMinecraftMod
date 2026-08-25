package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.entity.PersonEntity;
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
            // Standing at it. Charged now, with somebody there ready to do the
            // work -- never on the strength of a plan.
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
}
