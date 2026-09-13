package com.civilization.sim.world;

import com.civilization.sim.kingdom.Kingdom;
import com.civilization.sim.person.Person;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Settlement;

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

    /**
     * Puts the clock back where the save left it.
     *
     * <p>The one thing about this simulation that was not durable, while four
     * things were compared against it. {@code Perimeter.stakedOn},
     * {@code Building.completedOnStep}, {@code Settlement.firstStep} and the raid
     * schedule all come out of the save carrying step numbers from the last
     * session; the counter they are numbers <em>in</em> restarted at zero every
     * launch. So a wall staked on step 900 was, after a reload, staked nine
     * hundred steps in the future, and a town founded on step 900 was owed its
     * founding grace all over again.
     *
     * <p>Called once, between constructing the world and the first step, by
     * whatever loaded the save. Not a setter: a clock that could be wound at any
     * time is a clock nothing may be compared against, so this refuses to go
     * backwards and refuses to run twice after the simulation has moved.
     *
     * @param steps the count the save carries; a negative or absent one is zero
     */
    public void restoreStepsElapsed(long steps) {
        if (stepsElapsed != 0) {
            throw new IllegalStateException(
                    "the clock has already run to " + stepsElapsed + "; it cannot be restored");
        }
        stepsElapsed = Math.max(0L, steps);
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
        // And then the one pass that needs two settlements at once. A kingdom steps
        // its own towns and a town steps itself, so neither of them can see a
        // neighbor -- and a goblin camp raiding somebody is by construction a
        // thing that happens between two settlements. See GoblinRaids, which is
        // handed the list rather than this world so it stays testable without one.
        //
        // After the kingdoms, so a camp that crowned a chieftain this step raids
        // with him and a town that recruited a guard this step is defended by him.
        com.civilization.sim.settlement.GoblinRaids.advance(settlements(), ctx);
        stepsElapsed++;
    }

    /**
     * Every settlement in the world, in a stable order.
     *
     * <p>Flat, because the one thing that reaches across settlements does not care
     * which kingdom holds which — a goblin camp raids the nearest village and has
     * no notion of a realm. Kingdom order then settlement order, both of which are
     * insertion order, so the list is the same on every step and on every reload.
     */
    public java.util.List<Settlement> settlements() {
        java.util.List<Settlement> all = new java.util.ArrayList<>();
        for (Kingdom kingdom : kingdoms.values()) {
            all.addAll(kingdom.settlements());
        }
        return all;
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
