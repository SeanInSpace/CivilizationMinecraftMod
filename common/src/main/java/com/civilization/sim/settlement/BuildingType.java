package com.civilization.sim.settlement;

import java.util.Objects;

/**
 * A kind of building a settlement knows how to want.
 *
 * <p>Hardcoded in {@link BuildCatalog} for now. Every field here is a plain
 * number precisely so this can become a datapack JSON entry later without the
 * planner changing.
 *
 * @param id            blueprint identifier, e.g. {@code "civilization:house"}
 * @param workCost      builder-steps required to finish one
 * @param minPopulation settlement will not consider this below this population
 * @param baseCount     how many are wanted regardless of population
 * @param perResidents  one more is wanted per this many residents; 0 disables scaling
 * @param priority      higher wins when the settlement is short of several things
 * @param capacity      how many people can live here; 0 means it is not housing
 * @param defenseBonus  added to the settlement's defense power while this stands
 * @param plotSpan      full width of ground this needs, walls plus the cleared
 *                      shelf around them, and with room for the levels it may be
 *                      raised to later. Square and rotation-proof on purpose: a
 *                      building turned a quarter turn swaps its width and depth,
 *                      and a plot that only fitted one way round is not a plot.
 * @param requires      a blueprint id that must already stand in the settlement
 *                      before this is wanted at all, or {@link #NOTHING} for the
 *                      ordinary case. See the field's own note for why a
 *                      population gate could not say this.
 */
public record BuildingType(
        String id,
        int workCost,
        int minPopulation,
        int baseCount,
        int perResidents,
        int priority,
        int capacity,
        int defenseBonus,
        int plotSpan,
        String requires
) {

    /**
     * No prerequisite, which is every row but one.
     *
     * <p>The empty string rather than null so that {@code requires()} is always
     * safe to read and a datapack entry that omits the field lands on the same
     * value a hardcoded row does.
     */
    public static final String NOTHING = "";

    public BuildingType {
        Objects.requireNonNull(id, "id");
        if (requires == null) {
            requires = NOTHING;
        }
        if (requires.equals(id)) {
            throw new IllegalArgumentException(
                    id + " requires itself, so the first one can never be built");
        }
        if (workCost <= 0) {
            throw new IllegalArgumentException("workCost must be positive");
        }
        if (perResidents < 0) {
            throw new IllegalArgumentException("perResidents must not be negative");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative");
        }
        if (defenseBonus < 0) {
            throw new IllegalArgumentException("defenseBonus must not be negative");
        }
        if (plotSpan <= 0) {
            throw new IllegalArgumentException("plotSpan must be positive");
        }
    }

    /** Half the plot, for the overlap arithmetic that keeps two of these apart. */
    public int plotRadius() {
        return plotSpan / 2;
    }

    /**
     * Whether something else has to stand before this is wanted.
     *
     * <p><strong>Why this is not a population gate.</strong> Every other "not
     * yet" in the catalog is {@link #minPopulation}, and for almost everything
     * that is the honest measure: a hamlet does not want a smithy because a
     * hamlet has nobody to keep one. But "a grand library is what a town builds
     * after its library" is a statement about the town's <em>history</em>, not
     * its size, and a population number that happened to coincide would be a
     * coincidence — a town that lost its library to a raid would go on wanting
     * the grand one, and a town that somehow skipped the small one would raise
     * the great one first and never build the ordinary one at all.
     */
    public boolean hasPrerequisite() {
        return !requires.isEmpty();
    }

    /** Convenience for buildings that contribute nothing to defense. */
    public BuildingType(String id, int workCost, int minPopulation, int baseCount,
                        int perResidents, int priority, int capacity) {
        this(id, workCost, minPopulation, baseCount, perResidents, priority, capacity, 0,
                BuildPlanner.DEFAULT_PLOT_SPAN, NOTHING);
    }

    public BuildingType(String id, int workCost, int minPopulation, int baseCount,
                        int perResidents, int priority, int capacity, int defenseBonus) {
        this(id, workCost, minPopulation, baseCount, perResidents, priority, capacity,
                defenseBonus, BuildPlanner.DEFAULT_PLOT_SPAN, NOTHING);
    }

    /**
     * A row that names its own plot and wants nothing standing first, which is
     * every row in {@link BuildCatalog} but one.
     */
    public BuildingType(String id, int workCost, int minPopulation, int baseCount,
                        int perResidents, int priority, int capacity, int defenseBonus,
                        int plotSpan) {
        this(id, workCost, minPopulation, baseCount, perResidents, priority, capacity,
                defenseBonus, plotSpan, NOTHING);
    }

    /** Whether families can live here. */
    public boolean isHousing() {
        return capacity > 0;
    }

    /** How many of this the settlement wants at the given population. */
    public int desiredCount(int population) {
        int scaled = perResidents > 0 ? population / perResidents : 0;
        return baseCount + scaled;
    }
}
