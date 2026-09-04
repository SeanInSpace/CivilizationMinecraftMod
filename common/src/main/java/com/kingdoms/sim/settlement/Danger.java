package com.kingdoms.sim.settlement;

/**
 * The scale a town's fear is measured on.
 *
 * <p>Every number in the alarm system is a quantity of this: what the platform's
 * danger table says each kind of creature is worth, what {@link Alarm} needs to
 * see before it empties the streets, what {@link RaidPlanner} reckons one guard
 * can hold. They were all bare integers in three files, tuned against a sentence
 * of javadoc that only one of them carried, and there was nothing to stop the
 * scale meaning one thing in the table and another in the thresholds.
 *
 * <p>The unit is <b>one guard</b>. {@link #FULL_ATTENTION} is a guard doing
 * nothing else until the thing is dealt with, which is why it is also
 * {@link RaidPlanner#GUARD_CAPACITY}: the watch is counted in the same currency
 * as what is coming for it. Everything else is a fraction or a multiple of that
 * — {@link #OVERMATCH}, the tier that sends the town indoors, is literally two
 * guards' worth — so re-basing the scale moves the table and both thresholds
 * together instead of leaving them arguing.
 *
 * <p>The ladder, worst last:
 *
 * <table border="1">
 *   <caption>Rungs</caption>
 *   <tr><td>{@link #NONE}</td><td>a cow</td></tr>
 *   <tr><td>{@link #ROUTINE}</td><td>a zombie</td></tr>
 *   <tr><td>{@link #AWKWARD}</td><td>a skeleton</td></tr>
 *   <tr><td>{@link #FULL_ATTENTION}</td><td>a witch</td></tr>
 *   <tr><td>{@link #DANGEROUS}</td><td>a creeper</td></tr>
 *   <tr><td>{@link #DIRE}</td><td>a ravager</td></tr>
 *   <tr><td>{@link #OVERMATCH}</td><td>more than a lone guard can hold</td></tr>
 *   <tr><td>{@link #HOPELESS}</td><td>a warden</td></tr>
 * </table>
 *
 * <p>This lives in {@code :common} because both halves need it and only one of
 * them may see Minecraft: the tiers are here and the table that fills them is in
 * the platform layer, which depends on this and not the other way round.
 */
public final class Danger {

    private Danger() {
    }

    // The anchors are declared first because the rungs between them are
    // arithmetic on these, and Java will not let a constant refer to one
    // declared below it. Reading order gives way to the dependency.

    /** Not a threat at all: a sheep, a boat, a wandering trader's llama. */
    public static final int NONE = 0;

    /**
     * One guard's routine afternoon: he walks up to it and hits it.
     *
     * <p>The unit the scale is counted in, and deliberately the smallest thing
     * that registers at all — {@link Alarm#WARY_AT} is exactly this, so one of
     * anything makes a town look up.
     */
    public static final int ROUTINE = 1;

    /**
     * A guard's full attention: he is doing nothing else until it is dealt with.
     *
     * <p>Three routine afternoons, and the same number as
     * {@link RaidPlanner#GUARD_CAPACITY}, which is not a coincidence — it is
     * what makes {@code danger > guards * GUARD_CAPACITY} mean "more than the
     * watch can hold" rather than an arbitrary comparison of two scales.
     */
    public static final int FULL_ATTENTION = 3 * ROUTINE;

    /**
     * Two guards' full attention, which a lone guard by definition has not got.
     *
     * <p>A thing one guard probably loses to, and therefore the tier at which a
     * town stops pretending everything is fine: {@link Alarm#ALARMED_AT}.
     */
    public static final int OVERMATCH = 2 * FULL_ATTENTION;

    /**
     * Nothing a settlement can field is going to stop this. Run.
     *
     * <p>Not a multiple of anything: it is the top of the scale rather than a
     * point on it, set far enough above {@link #OVERMATCH} that no arithmetic
     * involving ordinary creatures can be mistaken for it.
     */
    public static final int HOPELESS = 10 * ROUTINE;

    // --- the rungs in between, so nothing in the table has to be a bare int ---

    /**
     * Still one guard's job, and not a comfortable one: it shoots back, or it is
     * quicker than he is. Halfway between routine and his whole attention.
     */
    public static final int AWKWARD = (ROUTINE + FULL_ATTENTION) / 2;

    /**
     * More than one guard should meet alone — he can lose to this if it goes
     * wrong, and it may take a building with it.
     *
     * <p>Also {@link RaidPlanner#BELL_FLOOR}: below this the bell stays quiet
     * however thin the watch is, because the watch is what a town keeps so it
     * does not have to hide from one of anything.
     */
    public static final int DANGEROUS = (FULL_ATTENTION + OVERMATCH) / 2;

    /**
     * A lone guard holds this only if nothing goes wrong.
     *
     * <p>One routine afternoon short of {@link #OVERMATCH}, and that gap is
     * load-bearing: the worst single creature a raid fields must sit below the
     * tier that empties the streets, or one of it would panic a town on its own.
     */
    public static final int DIRE = OVERMATCH - ROUTINE;
}
