package com.kingdoms.sim.world;

import com.kingdoms.sim.settlement.PopulationPlanner;

/**
 * The tunable knobs of the simulation, in one place.
 *
 * <p>The platform layer fills this from its config file; tests use {@link #DEFAULTS}.
 * Keeping it a plain record means the sim never knows a config system exists.
 *
 * @param simIntervalTicks        game ticks between simulation steps (100 = every 5s)
 * @param stepsPerBirth           simulation steps a housed family needs per child
 * @param observedRadius          distance (blocks) at which people become visible entities
 * @param embodyCapPerSettlement  most view entities one settlement may have at once
 * @param raidIntervalSteps       simulation steps between raid checks per settlement
 * @param raidsEnabled            master switch for hostile pressure
 * @param maxSettlementPopulation births stop at this population; the growth ceiling
 * @param expansionEnabled        master switch for founding daughter settlements
 */
public record SimSettings(
        int simIntervalTicks,
        int stepsPerBirth,
        double observedRadius,
        int embodyCapPerSettlement,
        int raidIntervalSteps,
        boolean raidsEnabled,
        int maxSettlementPopulation,
        boolean expansionEnabled
) {

    /**
     * The old six-field shape, which every caller but the two constants uses.
     *
     * <p>Expansion defaults to off. A full town sending out a founding party is
     * a feature that works; what it also does is turn one settlement into six
     * inside a single run, which makes every other thing being looked at harder
     * to look at. It is off until somebody wants to watch a kingdom rather than
     * a village.
     */
    public SimSettings(int simIntervalTicks, int stepsPerBirth, double observedRadius,
                       int embodyCapPerSettlement, int raidIntervalSteps,
                       boolean raidsEnabled, int maxSettlementPopulation) {
        this(simIntervalTicks, stepsPerBirth, observedRadius, embodyCapPerSettlement,
                raidIntervalSteps, raidsEnabled, maxSettlementPopulation, false);
    }

    public static final int DEFAULT_RAID_INTERVAL_STEPS = 50;

    /**
     * Housing supply scales with population, so without a hard ceiling towns grow
     * exponentially forever — live playtesting produced a thousand-person town
     * drawing 131-zombie raids inside sixteen minutes. Millénaire and MineColonies
     * cap settlement size for exactly this reason; those caps are load-bearing.
     */
    /**
     * No ceiling. A settlement grows for as long as it can house and feed people.
     *
     * <p>There used to be a hard cap of forty-eight, and the reason is worth
     * keeping in view: housing supply scales with population, so a town that can
     * always build another cottage can always fill it, and live playtesting
     * produced a thousand-person settlement drawing 131-zombie raids inside
     * sixteen minutes. The cap was load-bearing.
     *
     * <p>What replaces it is the birth rate. {@link PopulationPlanner#STEPS_PER_BIRTH}
     * is now slow enough that the limit is time and land rather than an
     * arbitrary number, which is what a growing village should be bounded by.
     * The setting remains, so a cap can be put back without new code — see
     * {@link #maxSettlementPopulation}.
     */
    public static final int NO_POPULATION_CAP = Integer.MAX_VALUE;

    public static final int DEFAULT_MAX_SETTLEMENT_POPULATION = NO_POPULATION_CAP;

    public static final SimSettings DEFAULTS = new SimSettings(
            SimWorld.SIM_INTERVAL_TICKS,
            PopulationPlanner.STEPS_PER_BIRTH,
            96.0,
            64,
            DEFAULT_RAID_INTERVAL_STEPS,
            true,
            DEFAULT_MAX_SETTLEMENT_POPULATION);

    /**
     * Defaults with raids off. For tests that assert on growth arithmetic — raid
     * schedules hash the settlement's random id, which would make outcomes vary
     * run to run.
     */
    public static final SimSettings SANDBOX = new SimSettings(
            SimWorld.SIM_INTERVAL_TICKS,
            PopulationPlanner.STEPS_PER_BIRTH,
            96.0,
            64,
            DEFAULT_RAID_INTERVAL_STEPS,
            false,
            DEFAULT_MAX_SETTLEMENT_POPULATION);

    /** Convenience keeping the pre-raid signature; raids on at the default cadence. */
    public SimSettings(int simIntervalTicks, int stepsPerBirth, double observedRadius,
                       int embodyCapPerSettlement) {
        this(simIntervalTicks, stepsPerBirth, observedRadius, embodyCapPerSettlement,
                DEFAULT_RAID_INTERVAL_STEPS, true, DEFAULT_MAX_SETTLEMENT_POPULATION);
    }
}
