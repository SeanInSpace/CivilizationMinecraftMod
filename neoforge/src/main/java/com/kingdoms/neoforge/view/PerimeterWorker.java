package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;

/**
 * A builder walking the line, raising the wall one post at a time.
 *
 * <p>The palisade used to appear. The abstract clock advanced the laid count by
 * as many posts as the town could afford that step, and the layer stamped up to
 * twenty-four of them into the world on the next tick — so a watched town grew a
 * wall in blocks of two dozen, out of nowhere, while its builders stood around
 * somewhere else entirely.
 *
 * <p>This is the same doctrine construction already follows, applied to the
 * wall: where there is a hand there is no clock, and where there is no hand the
 * clock is all there is. A town nobody is looking at still raises its wall on
 * the clock and has it standing when you arrive, because that is what "grew
 * while you were away" has to mean. A town somebody is standing in raises it by
 * sending a builder to each post in turn.
 *
 * <p><strong>What this does not do is lay blocks.</strong> It walks a builder to
 * the next unraised position, has them swing at it, pays for the post, and
 * advances the laid count by exactly one. {@code PerimeterLayer} draws whatever
 * has been laid, as it always did, and is idempotent by inspection — so the
 * post appears under the settler who raised it rather than being placed twice
 * by two different pieces of code that would then have to agree forever.
 */
public final class PerimeterWorker {

    /** How close a builder has to be to raise a post. */
    private static final double WORK_REACH = 3.0;

    private static final double WALK_SPEED = 0.65;

    private PerimeterWorker() {
    }

    /**
     * One pass for one builder.
     *
     * @return true if they are engaged with the wall, walking to it included
     */
    public static boolean work(ServerLevel level, Settlement settlement, PersonEntity builder) {
        Perimeter perimeter = settlement.perimeter();
        if (perimeter == null || perimeter.laid() >= perimeter.length()) {
            return false;
        }
        // Always the next one along, so the wall closes as a line rather than
        // appearing in patches -- and so the laid count stays a prefix, which is
        // what the layer draws and what every other reader assumes.
        SimPos next = perimeter.ringPositions().get(perimeter.laid());
        BlockPos at = new BlockPos(next.x(), next.y(), next.z());
        if (!level.isLoaded(at)) {
            return false;   // the far side of the ring; the clock has that stretch
        }

        if (builder.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)
                > WORK_REACH * WORK_REACH) {
            builder.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                    WALK_SPEED);
            return true;
        }

        // Standing at it. Pay for it, or stand there wanting to: a town that
        // cannot afford the post does not get it, and the builder is not
        // pretending otherwise by raising it anyway.
        if (!PerimeterPlanner.payForPost(settlement)) {
            return false;
        }
        builder.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
        builder.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
        perimeter.setLaid(perimeter.laid() + 1);
        return true;
    }
}
