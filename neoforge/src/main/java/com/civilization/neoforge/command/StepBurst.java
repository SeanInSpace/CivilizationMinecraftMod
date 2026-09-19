package com.civilization.neoforge.command;

import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.view.PersonEntityManager;
import com.civilization.neoforge.save.CivilizationSavedData;
import com.civilization.sim.world.SimWorld;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * A run of simulation steps, spread over ticks instead of poured into one.
 *
 * <p>{@code /civ step 800} used to run eight hundred steps inside the command,
 * which is inside a tick. A playtest did exactly that against a dedicated server
 * holding a forty-seven person town and the watchdog killed the server —
 * "a single server tick took 60.00 seconds". The steps themselves were far too
 * expensive, and that is fixed where it lives (see
 * {@code LightPlanner.lamps} and {@code PlannedLayout.planFor}); but a command
 * that takes a count up to ten thousand and runs all of it before returning is a
 * loaded gun whatever a step costs. Ten thousand steps of a <em>free</em>
 * simulation would still be a tick nobody survives.
 *
 * <p>So the count is a request rather than a loop. {@link #MOST_PER_TICK} of it
 * runs on the tick the command was typed and the rest is owed, drained at the
 * same rate from the server tick until it is gone. A burst of eight hundred is
 * sixteen ticks — under a second of wall clock, which is what somebody typing it
 * wanted — and not one of those ticks is long enough for the watchdog to notice.
 *
 * <p>Per level, because the simulation is. Owed steps are dropped when the level
 * goes away: they belong to a world that has stopped existing, and a burst is a
 * debugging convenience rather than anything a save should remember.
 */
public final class StepBurst {

    private StepBurst() {
    }

    /**
     * Simulation steps run in one server tick, at most.
     *
     * <p>Fifty. A settled town's step measures about four milliseconds on
     * recorded ground, so fifty is a fifth of a second — noticeable as a stutter
     * and nowhere near the watchdog's sixty, with room for the several towns a
     * grown world steps at once. The watchdog is the thing being stayed clear of
     * and it is three hundred times this.
     */
    public static final int MOST_PER_TICK = 50;

    /** Steps still owed to each level, from a burst too big for one tick. */
    private static final Map<ResourceKey<Level>, Integer> OWED = new HashMap<>();

    /** Drops what a closing world was owed. */
    public static void forget() {
        OWED.clear();
    }

    /** How many steps this level still has coming. */
    public static int owed(ServerLevel level) {
        return OWED.getOrDefault(level.dimension(), 0);
    }

    /**
     * Runs what fits now and remembers the rest.
     *
     * @return how many steps ran on this tick
     */
    public static int request(ServerLevel level, int count) {
        int now = Math.min(Math.max(0, count), MOST_PER_TICK);
        int later = Math.max(0, count - now);
        if (later > 0) {
            OWED.merge(level.dimension(), later, Integer::sum);
        }
        return run(level, now);
    }

    /** Drains a tick's worth of whatever every level is still owed. */
    public static void tick(MinecraftServer server) {
        if (OWED.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            int left = OWED.getOrDefault(level.dimension(), 0);
            if (left <= 0) {
                continue;
            }
            int now = Math.min(left, MOST_PER_TICK);
            int ran = run(level, now);
            if (left - ran <= 0) {
                OWED.remove(level.dimension());
            } else {
                OWED.put(level.dimension(), left - ran);
            }
        }
    }

    /**
     * The stepping itself, and the view pumped after each one.
     *
     * <p>Stepping passes no game ticks, so the view layer never runs on its own.
     * Pump it after each step or the builders would be granted blocks they never
     * lay, and the finished building would be stamped in whole on top of the
     * half-built one standing at the site.
     */
    private static int run(ServerLevel level, int count) {
        SimWorld world = CivilizationMod.simulationFor(level);
        if (world == null) {
            return 0;
        }
        PersonEntityManager manager = CivilizationMod.managerFor(level);
        for (int i = 0; i < count; i++) {
            world.step();
            if (manager != null) {
                manager.flushConstruction();
            }
        }
        if (count > 0) {
            CivilizationSavedData.get(level).setStepsElapsed(world.stepsElapsed());
            CivilizationSavedData.get(level).setDirty();
        }
        return count;
    }
}
