package com.civilization.sim.combat;

import com.civilization.sim.settlement.Alarm;

/**
 * Who the watch answers, and who a settler who is not of the watch answers.
 *
 * <p>{@link GuardStance} says how a guard stands to something he has already
 * decided to fight. This is the decision before that one — <em>is this my
 * business at all?</em> — and it is the half that was wrong in play.
 *
 * <p><strong>What was reported.</strong> A creeper was put down thirteen blocks
 * from an orc farmer on flat ground, with a guard about twenty blocks off and a
 * clear view of everything. Twelve seconds later the creeper was untouched at
 * full health and no arrow had ever been in the air. Nothing in the watch's own
 * code refuses a fight — there is no alarm gate, no threat-level threshold, and
 * the danger table has always scored a creeper at {@code Danger.DANGEROUS} — so
 * the only thing that could have declined it was the reach, and the reach was
 * twenty blocks measured as a <em>box</em>: twenty on each axis, which is wide
 * on a diagonal and exactly twenty where it matters, straight at the thing.
 *
 * <p>Here, in {@code :common}, for the same reason {@code GuardStance} is: it is
 * arithmetic on a distance, none of it needs a world, and a number that decides
 * whether a town is defended should not be one that can only be checked by
 * playing.
 */
public final class Watch {

    private Watch() {
    }

    /**
     * How far a guard looks for something to fight, in blocks.
     *
     * <p>Twenty-six, and it is not a round number picked for feeling roomy. It
     * is what the rest of the town already forces:
     *
     * <ul>
     *   <li><strong>18</strong> — the distance at which a civilian notices a
     *       creeper and stops work to run from it ({@code FleeCreepersGoal.NOTICE},
     *       itself derived in {@code Escape.noticeDistance}). The watch cannot
     *       see less far than the people it is posted over. A farmer running
     *       from something no guard has even looked at is the town telling the
     *       player, plainly, that the watch does not work.</li>
     *   <li><strong>+ 7</strong> — {@link GuardStance#HURT_RADIUS}, the ground
     *       that farmer is running <em>across</em>. He notices at eighteen and
     *       puts the blast radius between himself and it; the guard has to have
     *       been walking before that, not after, or he arrives at an explosion.</li>
     *   <li><strong>+ 1</strong> — so the two thresholds cannot be equal. A
     *       creeper exactly on the line is a creeper that either is or is not
     *       somebody's problem depending on floating-point luck.</li>
     * </ul>
     *
     * <p>And it is a <em>radius</em> now rather than the half-width of a box.
     * The old twenty was spent by the entity sweep, which inflates a bounding
     * box: a creeper twenty blocks north and twenty blocks east — thirty-four
     * away in a straight line — was engaged, and one twenty-one blocks due east
     * was not. A guard's eye is round.
     */
    public static final double ENGAGE_RANGE = 26.0;

    /**
     * Whether the watch answers this creature.
     *
     * <p>Two questions and no third: is the town afraid of it ({@code Menace}
     * decides, and the guard's list is the same list the bell is rung over), and
     * is it within {@link #ENGAGE_RANGE}. A hostile inside the claim and inside
     * that range is fought, full stop.
     *
     * <p><strong>{@code townAlarm} is taken and deliberately never read.</strong>
     * An argument nobody looks at is ordinarily a mistake; this one is the rule
     * written down. The playtest that produced all of this reported the town's
     * threat level at zero with a creeper standing inside the wall, and the
     * first guess anybody makes is that the watch was waiting to be told. It was
     * not, and it must never be: the alarm is how a town moves its <em>civilians</em>
     * — indoors, off the outlying fields — and a watch that needed the bell rung
     * before it would fight would be a watch that cannot ring it. The test that
     * walks every tier through this is what stops that gate from being added.
     */
    public static boolean answers(boolean threatens, double range, Alarm townAlarm) {
        return threatens && range <= ENGAGE_RANGE;
    }

    /**
     * Whether a settler who is not of the watch hits back.
     *
     * <p>The three refusals {@code PersonEntityManager.civilianDefense} is
     * written around, as one expression that can be checked without a world:
     *
     * <ul>
     *   <li><strong>He does not go looking.</strong> {@code struckByIt} is the
     *       whole of his reason to swing — vanilla's own {@code lastHurtByMob}
     *       and not the nearest hostile. A zombie standing next to a miller is
     *       not the miller's fight; the same zombie that has just bitten him
     *       is. A farmer who picks fights is a guard, and the town did not post
     *       him.</li>
     *   <li><strong>He does not stand up to a creeper.</strong> Whatever is in
     *       his hand, a fuse is not something a cleaver answers.</li>
     *   <li><strong>He does not chase.</strong> Two bounds, and the tighter one
     *       binds: the watch's own melee reach, which he must never out-reach,
     *       and {@link Weaponry#CIVILIAN_REACH}, past which the thing has walked
     *       off and stopped being his quarrel.</li>
     * </ul>
     *
     * @param armed      whether there is a weapon in his fist rather than a sack
     * @param struckByIt whether this is the creature that last hurt him
     * @param itBlowsUp  whether it is the exploding kind
     * @param range      blocks between the two of them
     * @param meleeReach how close the watch has to be to land a blow
     */
    public static boolean strikesBack(boolean armed, boolean struckByIt, boolean itBlowsUp,
                                      double range, double meleeReach) {
        return armed && struckByIt && !itBlowsUp
                && range <= Math.min(meleeReach, Weaponry.CIVILIAN_REACH);
    }
}
