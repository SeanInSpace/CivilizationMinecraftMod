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
    IDLER,
    FARMER,
    BUILDER,
    GUARD,
    TRADER
}
