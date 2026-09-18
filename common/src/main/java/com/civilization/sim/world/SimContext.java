package com.civilization.sim.world;

import com.civilization.sim.platform.WorldBridge;

import java.util.Objects;

/**
 * What a single simulation step is handed.
 *
 * <p>Threaded down through kingdoms and settlements so they can reach the world
 * without holding a reference to it. Add fields here rather than widening every
 * {@code step} signature again.
 */
public record SimContext(WorldBridge bridge, long step, SimSettings settings) {

    public SimContext {
        Objects.requireNonNull(bridge, "bridge");
        Objects.requireNonNull(settings, "settings");
    }

    /** Default settings — the form tests use. */
    public SimContext(WorldBridge bridge, long step) {
        this(bridge, step, SimSettings.DEFAULTS);
    }

    /**
     * Which day of the world it is.
     *
     * <p>Not the same thing as {@link #step}, and the difference matters to
     * exactly one caller so far: a gravestone. A step is the simulation's own
     * heartbeat and means nothing to a player — "died on step 41,288" is a log
     * line, not an epitaph — whereas a day is the number at the top of everyone's
     * screen. Zero in a simulation with no world behind it, which is what
     * {@code WorldBridge.dayTime} answers by default and is the right answer: a
     * test double has no days in it.
     */
    public long day() {
        return Math.floorDiv(bridge.dayTime(), com.civilization.sim.person.NightRest.DAY);
    }
}
