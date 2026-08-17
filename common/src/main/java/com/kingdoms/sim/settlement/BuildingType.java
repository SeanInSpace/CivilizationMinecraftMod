package com.kingdoms.sim.settlement;

import java.util.Objects;

/**
 * A kind of building a settlement knows how to want.
 *
 * <p>Hardcoded in {@link BuildCatalogue} for now. Every field here is a plain
 * number precisely so this can become a datapack JSON entry later without the
 * planner changing.
 *
 * @param id            blueprint identifier, e.g. {@code "kingdoms:house"}
 * @param workCost      builder-steps required to finish one
 * @param minPopulation settlement will not consider this below this population
 * @param baseCount     how many are wanted regardless of population
 * @param perResidents  one more is wanted per this many residents; 0 disables scaling
 * @param priority      higher wins when the settlement is short of several things
 * @param capacity      how many people can live here; 0 means it is not housing
 * @param defenseBonus  added to the settlement's defense power while this stands
 */
public record BuildingType(
        String id,
        int workCost,
        int minPopulation,
        int baseCount,
        int perResidents,
        int priority,
        int capacity,
        int defenseBonus
) {

    public BuildingType {
        Objects.requireNonNull(id, "id");
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
    }

    /** Convenience for buildings that contribute nothing to defense. */
    public BuildingType(String id, int workCost, int minPopulation, int baseCount,
                        int perResidents, int priority, int capacity) {
        this(id, workCost, minPopulation, baseCount, perResidents, priority, capacity, 0);
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
