package com.civilization.sim.person;

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
     * Brings in wild food by hand, forever — what a people who never farm have
     * instead of a farmer.
     *
     * <p>Not a synonym for one. A farmer answers to the ground: the staffing table
     * wants {@code FARMERS_PER_FARM} hands for every field standing, so a people
     * with no fields wants no farmers, and the foraging that carries a camp
     * through its first stages stopped dead the moment the camp graduated out of
     * pioneers. A forager answers to the census instead — so many mouths, so many
     * pairs of hands in the bushes — which is what a scavenger camp's food supply
     * actually is.
     *
     * <p>Only appears in a settlement whose people never farm; see
     * {@code JobPlanner.needsFor}, which is the one place that swaps the row.
     */
    FORAGER,

    /**
     * The goblin camp's second named role: the one who keeps the camp.
     *
     * <p>The chieftain's counterpart, and deliberately not a job either — there is
     * exactly one, he is chosen by a rule about the camp rather than by a
     * shortfall, and he is absent from the staffing table for the same reason the
     * king is. What the camp gets for him is a better day's foraging and, far more
     * importantly, a reason not to walk away: a camp that loses its chieftain
     * <em>and</em> its shaman scatters, and while either of them lives it stays.
     *
     * <p>See {@code GoblinCamp} for who gets it, when, and what it is worth.
     */
    SHAMAN,

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

    /**
     * Whether this settler is exempt from labor entirely.
     *
     * <p>The two titles, and nothing else. A shaman is idle by right for exactly
     * the reason a king is — he is not in the staffing table, so nothing ever
     * wants one and nothing ever retrains one away — and the camp gets what it
     * gets from him by his being alive rather than by his working.
     */
    public boolean isIdleByRight() {
        return this == KING || this == SHAMAN;
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
