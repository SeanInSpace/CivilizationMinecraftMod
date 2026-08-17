package com.kingdoms.sim.world;

import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Settlement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Root of the simulation. Owns every kingdom in a dimension.
 *
 * <p><strong>The slow tick is the whole point of this class.</strong> Minecraft
 * ticks 20 times a second; this simulation does not need to. Settlements, economies
 * and build queues advance on {@link #SIM_INTERVAL_TICKS}, which buys roughly two
 * orders of magnitude of headroom over per-tick simulation and is imperceptible to
 * players. Raising the interval is the cheapest performance lever you have.
 *
 * <p>Nothing in this class touches Minecraft. Everything that needs the world goes
 * through {@link WorldBridge}.
 */
public final class SimWorld {

    /** Default game ticks between simulation steps (100 ticks = 5 seconds). */
    public static final int SIM_INTERVAL_TICKS = 100;

    private final WorldBridge bridge;
    private final SimSettings settings;
    private final Map<Kingdom.Id, Kingdom> kingdoms = new LinkedHashMap<>();

    private long stepsElapsed;

    public SimWorld(WorldBridge bridge) {
        this(bridge, SimSettings.DEFAULTS);
    }

    public SimWorld(WorldBridge bridge, SimSettings settings) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public SimSettings settings() {
        return settings;
    }

    public void addKingdom(Kingdom kingdom) {
        kingdoms.put(kingdom.id(), kingdom);
    }

    public Kingdom kingdom(Kingdom.Id id) {
        return kingdoms.get(id);
    }

    public Collection<Kingdom> kingdoms() {
        return Collections.unmodifiableCollection(kingdoms.values());
    }

    public long stepsElapsed() {
        return stepsElapsed;
    }

    public WorldBridge bridge() {
        return bridge;
    }

    /**
     * Call this from the platform's server tick hook every game tick. It decides
     * for itself whether this tick is a simulation step.
     *
     * @param gameTime the level's game time
     * @return true if a simulation step actually ran
     */
    private long tickCounter;

    /**
     * Call once per game tick; returns true when a simulation step ran.
     *
     * <p>Counts ticks internally rather than trusting the level's clock. Found the
     * hard way: 26.2's per-dimension clocks can hold {@code getGameTime()} at a
     * fixed value, and a clock frozen on a multiple of the interval stepped the
     * simulation twenty times a second — raids every two seconds, population
     * explosion. The first live playtest caught it within a minute.
     */
    public boolean onGameTick() {
        if (++tickCounter % settings.simIntervalTicks() != 0) {
            return false;
        }
        step();
        return true;
    }

    /** Advance the simulation by exactly one step. Exposed directly so tests need no game. */
    public void step() {
        SimContext ctx = new SimContext(bridge, stepsElapsed, settings);
        for (Kingdom kingdom : kingdoms.values()) {
            kingdom.step(ctx);
        }
        stepsElapsed++;
    }

    /** The settlement this person lives in, if any. */
    public Optional<Settlement> settlementOf(Person.Id personId) {
        for (Kingdom kingdom : kingdoms.values()) {
            for (Settlement settlement : kingdom.settlements()) {
                if (settlement.resident(personId) != null) {
                    return Optional.of(settlement);
                }
            }
        }
        return Optional.empty();
    }

    public int totalPopulation() {
        return kingdoms.values().stream().mapToInt(Kingdom::totalPopulation).sum();
    }
}
