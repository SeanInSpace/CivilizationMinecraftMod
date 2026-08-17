package com.kingdoms.sim.kingdom;

import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A political entity owning several settlements.
 *
 * <p>Kingdom state outlives every entity in it, so it lives here and is persisted
 * through the platform layer's save data — never on a mob.
 */
public final class Kingdom {

    public record Id(UUID value) {
        public Id {
            Objects.requireNonNull(value, "value");
        }

        public static Id random() {
            return new Id(UUID.randomUUID());
        }
    }

    private final Id id;
    private final String name;

    /** Identifies a datapack-defined culture, e.g. {@code "kingdoms:norman"}. */
    private final String cultureId;

    private final Map<Settlement.Id, Settlement> settlements = new LinkedHashMap<>();
    private final Map<Id, Standing> diplomacy = new LinkedHashMap<>();

    public Kingdom(Id id, String name, String cultureId) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.cultureId = Objects.requireNonNull(cultureId, "cultureId");
    }

    public Id id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String cultureId() {
        return cultureId;
    }

    public void addSettlement(Settlement settlement) {
        settlements.put(settlement.id(), settlement);
    }

    public Collection<Settlement> settlements() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    public int totalPopulation() {
        return settlements.values().stream().mapToInt(Settlement::population).sum();
    }

    public Standing standingWith(Id other) {
        return diplomacy.getOrDefault(other, Standing.NEUTRAL);
    }

    /** Exposed so the platform layer can serialize it. */
    public Map<Id, Standing> diplomacy() {
        return Collections.unmodifiableMap(diplomacy);
    }

    public void setStandingWith(Id other, Standing standing) {
        diplomacy.put(Objects.requireNonNull(other, "other"), Objects.requireNonNull(standing, "standing"));
    }

    /** Advance every settlement by one simulation step, then consider expanding. */
    public void step(SimContext ctx) {
        settlements.values().forEach(settlement -> settlement.step(ctx));
        ExpansionPlanner.advance(this, ctx);
    }

    @Override
    public String toString() {
        return name + " [" + cultureId + ", " + settlements.size() + " settlements, pop " + totalPopulation() + "]";
    }
}
