package com.kingdoms.neoforge.entity;

import com.kingdoms.sim.geom.Escape;
import net.minecraft.core.BlockPos;
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
 *
 * <p><b>Nobody flees faster than they work.</b> This goal used to hand out 0.9
 * and then 1.3, against an ordinary working pace of 0.6 — a settler who spotted a
 * creeper became, visibly, twice the walker they had been all morning, and
 * players read that for exactly what it was. The speeds are {@link Pace#WALK}
 * now, both of them, so a fleeing settler is indistinguishable from a settler
 * walking to a field. What danger buys them instead is the two things below:
 * more warning, and somewhere to go.
 */
public final class FleeCreepersGoal extends AvoidEntityGoal<Creeper> {

    /**
     * How close a creeper has to get before a civilian abandons what they are
     * doing.
     *
     * <p>Ten blocks, which is what this was, is not enough for somebody who
     * walks. The arithmetic is {@link Escape#noticeDistance} and it comes out at
     * 16.5:
     *
     * <ul>
     *   <li>{@code 7} — the hurt radius. A creeper is lethal to about three
     *       blocks and hurts to about seven, and seven is the number that has to
     *       survive everything below it.</li>
     *   <li>{@code + 5} — the creeper's own 0.25 blocks per tick for the twenty
     *       ticks before the settler is actually under way. A miner mid-swing
     *       does not turn on the instant, and the manager that stops steering
     *       them at their workplace runs once a second.</li>
     *   <li>{@code + 4.5} — three seconds of a bending path. The settler walks
     *       at 0.5 attribute times {@link Pace#WALK} = 0.35 blocks per tick,
     *       which beats the creeper's 0.25 on open ground; but a flee path leaves
     *       at an angle and rounds a fence before it points away, so call the
     *       outward half of it real. 0.25 − 0.175 = 0.075 lost per tick, for
     *       sixty of them.</li>
     * </ul>
     *
     * <p>Rounded up to eighteen: one and a half blocks of slack for a creeper
     * that is already walking when it comes into view rather than politely
     * standing still, and because a whole number is a thing a person can hold in
     * their head. {@code EscapeTest} keeps the 16.5 honest.
     */
    public static final float NOTICE = 18.0F;

    /**
     * How near the blast reaches. Somebody who starts at seven is still outside
     * it at three.
     */
    public static final double HURT = 7.0;

    /** Walking away, once there is distance in hand. */
    public static final double RETREAT_SPEED = Pace.WALK;

    /** Getting out of the blast, while there is not. The same walk. */
    public static final double PANIC_SPEED = Pace.WALK;

    /**
     * How far a door is still worth running to.
     *
     * <p>Beyond this, "go home" stops being shelter and becomes a cross-town
     * errand with a creeper in it; the away-vector is the better answer. Twice
     * the notice radius, so the shelter a settler is offered is always somewhere
     * they could plausibly be walking to already.
     */
    private static final double SHELTER_REACH = 36.0;

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
        runForShelter();
    }

    @Override
    public void stop() {
        super.stop();
        settler.setFleeing(false);
    }

    /**
     * Prefers a door to a direction.
     *
     * <p>{@code AvoidEntityGoal} runs people at a random point sixteen blocks
     * away from the thing, which is a fine instinct and a poor plan: it puts a
     * settler in a field with their back to the town, and the next creeper finds
     * them there. Where the manager has told this body where its house is, and
     * the house is close enough to be shelter rather than a journey, the settler
     * heads for the door instead — the same door the alarm sends them to, so a
     * frightened town moves as one thing.
     *
     * <p>With one refusal, which is the point of doing this at all: if the
     * straight run to that door passes within {@link #HURT} of the creeper, the
     * door is not shelter, it is the far side of a bomb. A settler who sprints
     * past a creeper to get home has spent their entire head start closing the
     * distance. That case keeps the away-vector {@code super.start()} already
     * set — the plan is only ever an improvement on the fallback, never a
     * replacement for having one.
     */
    private void runForShelter() {
        BlockPos shelter = settler.shelter();
        if (shelter == null || toAvoid == null) {
            return;
        }
        double toDoor = Math.sqrt(settler.distanceToSqr(
                shelter.getX() + 0.5, shelter.getY(), shelter.getZ() + 0.5));
        if (toDoor > SHELTER_REACH) {
            return;   // too far to be shelter; it is a journey with a creeper in it
        }
        if (Escape.runPassesWithin(settler.getX(), settler.getZ(),
                shelter.getX() + 0.5, shelter.getZ() + 0.5,
                toAvoid.getX(), toAvoid.getZ(), HURT)) {
            return;   // home is on the far side of the blast
        }
        // Built before it is committed, never handed to moveTo unchecked: a null
        // path clears the navigation outright, which would throw away the
        // away-vector super.start() just set and leave the settler standing in
        // the blast with no plan at all.
        net.minecraft.world.level.pathfinder.Path toShelter = pathNav.createPath(shelter, 0);
        if (toShelter != null) {
            pathNav.moveTo(toShelter, Pace.WALK);
        }
    }
}
