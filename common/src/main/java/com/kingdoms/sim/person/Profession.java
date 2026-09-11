package com.kingdoms.sim.person;

/**
 * Placeholder professions.
 *
 * <p>This is deliberately a small enum for now. At Millenaire scale professions
 * should become <em>data-driven</em> — loaded from datapacks so a new culture can
 * add its own roles without a code change. Treat this enum as scaffolding to be
 * replaced by a registry backed by JSON, not as the final design.
 */
public enum Profession {
    /**
     * A founding generalist with no fixed trade: builds, farms, forages and
     * hauls, whichever the camp needs. Professions crystallize out of pioneers
     * as the stages demand them — the sentry at FORTIFIED, the specialists at
     * VILLAGE — which is what makes early reassignment a stage event rather
     * than a table lookup that wants zero farmers below population five.
     */
    PIONEER,
    IDLER,
    FARMER,
    BUILDER,
    GUARD,
    TRADER,
    LUMBERJACK,
    MINER,
    SMITH,

    /** Works the mill: grinding the harvest gets more bread out of the grain. */
    MILLER,

    /** Works the carpentry: pre-cut components speed every build crew. */
    CARPENTER,
    SHEPHERD,

    /**
     * The one settler who does no work at all.
     *
     * <p>Every other entry on this list is a job, counted by a staffing table
     * that asks how many the town is short of. This one is not: there is exactly
     * one of him in a warband and none anywhere else, he is chosen by a rule
     * about the settlement rather than by a shortfall, and he is deliberately
     * absent from {@code JobPlanner.DEFAULT_NEEDS} — which is what makes
     * "a king never works" true by construction rather than by a special case in
     * every planner that hands out labor.
     *
     * <p>See {@code KingPlanner} for who gets it, when, and what the town gets
     * back for it.
     */
    KING;

    /** Whether this settler is exempt from labor entirely. */
    public boolean isIdleByRight() {
        return this == KING;
    }

    /**
     * Whether this trade works out past the town's walls.
     *
     * <p>The woods and the mine head are where the claim runs out, and they are
     * what a hostile reaches first. Everything else on this list works on a ring
     * plot — behind the palisade, or where the palisade will be — so a town that
     * has seen something out there calls in the woodcutters and the miners and
     * lets the rest get on with it.
     */
    public boolean worksBeyondTheWalls() {
        return this == LUMBERJACK || this == MINER;
    }
}
