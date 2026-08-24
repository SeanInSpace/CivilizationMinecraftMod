package com.kingdoms.sim.platform;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Footprint;

/**
 * The one seam between the simulation and the game.
 *
 * <p>Everything the simulation needs from Minecraft is declared here in terms of
 * plain Java and {@link SimPos}. The NeoForge module implements it; a Fabric
 * module could implement it later without the simulation changing at all.
 *
 * <p>If you are tempted to import {@code net.minecraft.*} into the sim, add a
 * method here instead.
 */
public interface WorldBridge {



    /**
     * Whether any living player is within {@code radius} blocks of this position.
     *
     * <p>This is the switch between the two fidelities. Far from every player,
     * people are records, travel is a timer, and combat resolves statistically.
     * Near one, the platform layer materializes the same state as entities and
     * blocks. The radius comes from settings so the caller decides the cutoff —
     * and can use a larger one for release than for spawn (hysteresis).
     */
    boolean playerWithin(SimPos pos, double radius);

    /** Whether the chunk containing this position is currently loaded. */
    boolean isLoaded(SimPos pos);

    /**
     * The ground level at this column: the y of the first air block above the
     * terrain, ignoring foliage. Returns {@code pos.y()} unchanged when the chunk
     * is not available — callers treat the result as best-effort, and the world
     * snaps to real terrain again at materialization time.
     */
    int surfaceHeight(SimPos pos);

    /**
     * Place a completed building into the world.
     *
     * <p>Safe to call when unobserved: implementations should record the change
     * and apply it when the chunk next loads, so that settlements which grew
     * while the player was away appear already built.
     *
     * <p>Returns where it was actually placed and how big it turned out, or
     * {@link Footprint#UNKNOWN} if nothing was placed. An unsurveyed origin carries a guess, and the caller
     * needs the real answer back — otherwise workers keep walking to a height the
     * building is not at.
     *
     * <p>{@code surveyed} says whether the origin's height was measured against
     * real terrain. When it was, the implementation must place at exactly that
     * height and not re-measure — otherwise a structure the builders had already
     * started gets stamped in a second time at a different level, and you get two
     * of it. When it was not, the height is a guess and re-measuring is the whole
     * point.
     */
    Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed,
                                   int facing);

    /**
     * Whether a plot is fit to build on.
     *
     * <p>Plots are handed out by geometry alone — rings around the centre — which
     * is why a settlement will otherwise cheerfully put a house in a lake or across
     * a ravine. This is the veto: too much slope, standing water, or a drop, and
     * the town takes the next plot instead.
     *
     * <p>Answers {@code true} when the chunk is not loaded and nothing can be
     * judged. A guess would be worse than deferring: the site is surveyed again for
     * real before a single block is laid.
     *
     * <p>Default {@code true} so test doubles stay small.
     */
    default boolean isSiteSuitable(SimPos plot, int radius) {
        return true;
    }

    /**
     * How wooded this ground is, from 0 to 100.
     *
     * <p>The one thing siting a lumber camp needs and the simulation cannot
     * know: it has no notion of where trees are. The work area was derived
     * *from* the camp rather than the camp from the trees, which is the wrong
     * way round — a camp put on open grass claims a circle of open grass and
     * then has nothing to fell.
     *
     * <p>Zero when nothing is loaded, which reads as "no reason to prefer this
     * spot" rather than "definitely bare". A guess would be worse: the plot is
     * surveyed again for real before a block is laid.
     *
     * <p>Default zero so test doubles stay small; real platforms should answer.
     */
    default int woodedness(SimPos centre, int radius) {
        return 0;
    }

    /** Structured logging that does not depend on a specific logging backend. */
    void log(String message);

    /**
     * How many hostile creatures are currently near this position.
     *
     * <p>Zero when nothing is loaded — which is exactly right: abstract fidelity
     * has no real hostiles, so threat there comes from the raid system instead.
     * Default provided so test doubles stay small; real platforms must override.
     */
    default int hostilesNear(SimPos centre, double radius) {
        return 0;
    }

    /**
     * Spawn a hostile raiding party around this position — the observed-fidelity
     * half of a raid. Called only when a player is close enough to watch; entity
     * combat decides the outcome from there. Default no-op for test doubles;
     * real platforms must override.
     */
    default void spawnHostiles(int count, SimPos around) {
    }
}
