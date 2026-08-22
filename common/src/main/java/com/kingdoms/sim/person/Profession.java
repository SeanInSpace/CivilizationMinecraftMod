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
    SHEPHERD
}
