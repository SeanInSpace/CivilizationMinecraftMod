package com.kingdoms.neoforge.entity;

import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;

/**
 * A settler getting out of the way of a creeper.
 *
 * <p>Citizens have no combat AI at all — they never fight anything, and that is
 * deliberate — but until now they also never <em>fled</em>, which meant a farmer
 * would carry on hoeing a row while a creeper walked up to them and took the row,
 * the farmer and the fence with it. Standing still is not neutrality when the
 * thing approaching kills by arriving.
 *
 * <p>Only creepers, and only civilians. Every other hostile is something the
 * watch walks up to and hits, so a civilian near one is in the ordinary kind of
 * danger the town alarm already answers. And the watch itself does not run — a
 * guard's whole job is to be the one who does not — so guards are excluded here
 * and handle creepers their own way, by hitting and stepping back out of the
 * blast.
 *
 * <p>A settler whose record cannot be found counts as a civilian and runs. If we
 * do not know whose body this is, fleeing is the answer that survives being
 * wrong.
 */
public final class FleeCreepersGoal extends AvoidEntityGoal<Creeper> {

    /**
     * How close a creeper has to get before a civilian abandons what they are
     * doing.
     *
     * <p>Comfortably outside the blast, which is lethal to about three blocks and
     * hurts to about seven. Somebody who starts running at seven is still running
     * at three.
     */
    private static final float NOTICE = 10.0F;

    /** Walking away, once there is distance in hand. */
    private static final double RETREAT_SPEED = 0.9;

    /** Getting out of the blast, while there is not. */
    private static final double PANIC_SPEED = 1.3;

    private final PersonEntity settler;

    public FleeCreepersGoal(PersonEntity settler) {
        super(settler, Creeper.class, NOTICE, RETREAT_SPEED, PANIC_SPEED);
        this.settler = settler;
    }

    @Override
    public boolean canUse() {
        return !settler.isGuard() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !settler.isGuard() && super.canContinueToUse();
    }

    @Override
    public void start() {
        super.start();
        // Tells the manager to stop steering this body toward its workplace for
        // the duration. Two systems both calling moveTo every tick means neither
        // of them wins, and the settler stands in the blast vibrating.
        settler.setFleeing(true);
    }

    @Override
    public void stop() {
        super.stop();
        settler.setFleeing(false);
    }
}
