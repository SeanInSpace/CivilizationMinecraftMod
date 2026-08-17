package com.kingdoms.sim.world;

import com.kingdoms.sim.platform.WorldBridge;

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
}
