package com.civilization.sim.combat;

/**
 * How a guard stands to the thing he is fighting: which weapon is up, and
 * whether to close, hold, or give ground.
 *
 * <p>Two fights, and they are not the same fight. A zombie is walked up to and
 * hit. A creeper cannot be — its fuse is shorter than its health, so a guard who
 * stands in reach and swings until it dies is a guard who dies with it. The
 * watch used to answer that with a hit-and-retreat dance: one swing, run out of
 * the blast, wait for the fuse to forget, walk back in. It worked, in the sense
 * that the guard usually lived, and it looked like a man who had never been told
 * what a bow is for.
 *
 * <p>So a creeper is shot. The whole of that decision is a distance and a
 * question — <em>does this thing explode?</em> — which is arithmetic, which is
 * why it lives here in the module that cannot see Minecraft and has a test on
 * it. What the world does with the answer (swap the hands over, spawn an arrow,
 * walk backwards) is the view's business.
 *
 * <p>The band is picked off two numbers that already exist. A creeper's blast
 * hurts out to {@link #HURT_RADIUS} seven blocks, so {@link #BAND_NEAR} is eight
 * — one block of margin, and the guard turns round before he is in it rather
 * than after. {@link #BAND_FAR} fourteen is inside the twenty blocks a guard
 * notices a hostile at and close enough that a loosed arrow arrives while the
 * creeper is still roughly where it was aimed; vanilla's own skeleton holds
 * fifteen. Between the two he stands still and shoots, which is the point of
 * carrying a bow.
 */
public final class GuardStance {

    private GuardStance() {
    }

    /**
     * How far a creeper's blast hurts. Vanilla's explosion radius is 3 and the
     * damage falls off to nothing at rather more than twice that; seven is the
     * figure the flight goal already runs on, and the two must not disagree.
     */
    public static final double HURT_RADIUS = 7.0;

    /** Inside this, a bowman is in the blast and gives ground. One block clear of {@link #HURT_RADIUS}. */
    public static final double BAND_NEAR = 8.0;

    /** Beyond this the shot is not worth loosing, so he closes to it. */
    public static final double BAND_FAR = 14.0;

    /** What is in the leading hand. */
    public enum Weapon {
        /** Everything that does not explode. */
        SWORD,
        /** Everything that does. */
        BOW
    }

    /** What the feet are doing. */
    public enum Move {
        /** Too far to fight. Walk at it. */
        CLOSE_IN,
        /** Where he wants to be. Stand and work. */
        HOLD,
        /** Too close to something about to go off. Walk away from it. */
        BACK_OFF
    }

    /**
     * One guard's answer to one target.
     *
     * @param weapon what to hold up; the other half of the kit goes to the off hand
     * @param move   what the feet do this pass
     * @param shoot  whether an arrow may be loosed — bow up and the target within
     *               {@link #BAND_FAR}. True while backing off as well as while
     *               holding: a bowman walking backwards is still a bowman.
     */
    public record Stance(Weapon weapon, Move move, boolean shoot) {
    }

    /**
     * The stance one guard takes against one target.
     *
     * @param blowsUp  whether the target is the exploding kind
     * @param distance blocks between the guard and it
     * @param reach    how close the guard must be to land a sword blow
     */
    public static Stance against(boolean blowsUp, double distance, double reach) {
        if (!blowsUp) {
            // A zombie, a skeleton, a raider: walked up to and hit. Never backed
            // away from — a guard who gives ground to a zombie is a guard who has
            // let it past him, and the thing behind him is a farmer.
            return new Stance(Weapon.SWORD,
                    distance <= reach ? Move.HOLD : Move.CLOSE_IN, false);
        }
        Move move;
        if (distance < BAND_NEAR) {
            move = Move.BACK_OFF;
        } else if (distance > BAND_FAR) {
            move = Move.CLOSE_IN;
        } else {
            move = Move.HOLD;
        }
        return new Stance(Weapon.BOW, move, distance <= BAND_FAR);
    }
}
