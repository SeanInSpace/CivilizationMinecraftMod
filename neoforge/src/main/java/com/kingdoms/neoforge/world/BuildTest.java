package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a town plan onto the ground, with nobody living in it.
 *
 * <p>Not a simulation and deliberately not one. A settlement decides what to
 * build from its people — how many there are, what they do, what they have in
 * store — and every question about the <em>shape</em> of a town has had to be
 * asked through that. Which means a fault in the geometry arrives mixed with
 * whatever the population was doing at the time, on ground that varies, over
 * seven minutes of growth. Three separate faults this month were found only by
 * dumping a grown town and measuring it afterwards.
 *
 * <p>So this renders the plan directly. Same {@link TownPlan} the real code
 * uses — the geometry under test is the production geometry, not a copy — and
 * everything else is gone: no residents, no stores, no build queue, no stages.
 * On flat ground with a fixed centre it draws the same town every time, which
 * makes a difference in the result mean a difference in the code.
 *
 * <p><strong>It proves nothing about the simulation.</strong> A town that
 * renders correctly here can still be built wrongly by a settlement, because
 * the settlement is what is missing. This is an instrument for looking at
 * arrangement, and the arrangement is all it can see.
 */
public final class BuildTest {

    /** Runs in progress, one to a world. */
    private static final Map<ServerLevel, BuildTest> RUNNING = new HashMap<>();

    /**
     * What a town puts up, in the order it puts it up.
     *
     * <p>A settlement would decide this from its people. With nobody to ask, the
     * order is written down: the hall in the middle, the things that make a town
     * a town around it, then houses out to the edge. Fixed on purpose — a
     * programme that varied would make two runs incomparable, which is the one
     * thing this exists to avoid.
     */
    private static final String[] PROGRAMME = {
            "kingdoms:town_hall", "kingdoms:market", "kingdoms:granary",
            "kingdoms:storehouse", "kingdoms:smith", "kingdoms:carpentry",
            "kingdoms:inn", "kingdoms:mill", "kingdoms:workshop",
            "kingdoms:watchtower",
    };

    /** What fills the rest of the grid once the civic buildings are placed. */
    private static final String[] DWELLINGS = {
            "kingdoms:house", "kingdoms:house", "kingdoms:cottage", "kingdoms:bunkhouse",
    };

    private final ServerLevel level;
    private final List<Placement> pending;
    private final int ticksBetween;
    private int waited;
    private int placed;

    private record Placement(String blueprintId, SimPos at, int facing) {
    }

    private BuildTest(ServerLevel level, List<Placement> pending, int ticksBetween) {
        this.level = level;
        this.pending = pending;
        this.ticksBetween = ticksBetween;
    }

    /**
     * Starts drawing a gridiron town at this centre.
     *
     * @param buildingsPerSecond how fast to raise them; one is a building a second
     * @return how many buildings the run will place
     */
    public static int start(ServerLevel level, SimPos centre, int count,
                            int buildingsPerSecond) {
        Layout gridiron = Layouts.of(com.kingdoms.sim.culture.Culture.LAYOUT_STRONGHOLD_STREETS);
        TownPlan plan = gridiron.planFor(centre, count);

        List<Placement> pending = new ArrayList<>();
        for (int i = 0; i < plan.plots().size() && i < count; i++) {
            TownPlan.Plot plot = plan.plots().get(i);
            pending.add(new Placement(blueprintFor(i), plot.at(), plot.facing()));
        }
        // Twenty ticks to the second, so a rate is a wait: one a second waits
        // twenty, five a second waits four. Never less than one, or a rate past
        // twenty would place the whole town in a single tick and the point of
        // watching it appear would be lost.
        int ticks = Math.max(1, 20 / Math.max(1, buildingsPerSecond));
        BuildTest run = new BuildTest(level, pending, ticks);
        synchronized (RUNNING) {
            RUNNING.put(level, run);
        }
        KingdomsMod.LOGGER.info("BUILDTEST start centre={} count={} bps={} streets={}",
                centre, pending.size(), buildingsPerSecond, plan.streets().size());
        return pending.size();
    }

    /** Stops whatever is being drawn here. */
    public static boolean stop(ServerLevel level) {
        synchronized (RUNNING) {
            return RUNNING.remove(level) != null;
        }
    }

    /** Advances every run by one tick. Called from the server tick. */
    public static void tick(ServerLevel level) {
        BuildTest run;
        synchronized (RUNNING) {
            run = RUNNING.get(level);
        }
        if (run != null && run.step()) {
            stop(level);
        }
    }

    /** @return whether the run has finished */
    private boolean step() {
        if (pending.isEmpty()) {
            KingdomsMod.LOGGER.info("BUILDTEST done, {} buildings placed", placed);
            return true;
        }
        if (++waited < ticksBetween) {
            return false;
        }
        waited = 0;
        Placement next = pending.remove(0);
        // The chunk, before the building. materializeBlueprint refuses an
        // unloaded column and a grid spreads well past whatever a player has
        // loaded, so a run left to chance would place its middle and silently
        // drop its edges. One chunk per building at a building a second is a
        // cost this can afford; the simulation cannot, which is why it does not
        // do this and this is not the simulation.
        level.getChunk(next.at().x() >> 4, next.at().z() >> 4);
        int y = BlueprintPlacer.baseFor(next.blueprintId(),
                BlueprintPlacer.groundLevel(level, next.at().x(), next.at().z()));
        BlueprintPlacer.place(level, next.blueprintId(),
                new BlockPos(next.at().x(), y, next.at().z()), next.facing());
        placed++;
        // Logged per building, because BlueprintPlacer says nothing and a run
        // that silently placed nothing looked exactly like a run that silently
        // placed everything -- which cost one wasted verification.
        KingdomsMod.LOGGER.info("BUILDTEST placed {} {} at {},{},{} facing {}",
                placed, next.blueprintId(), next.at().x(), y, next.at().z(),
                next.facing());
        return false;
    }

    /**
     * Which building goes on the nth plot.
     *
     * <p>Plots come out of the plan nearest-centre first, so walking them in
     * order puts the hall and the market in the middle and the houses at the
     * edge without anything having to decide that separately. It is the one
     * thing a settlement's population would otherwise be doing here.
     */
    private static String blueprintFor(int index) {
        if (index < PROGRAMME.length) {
            return PROGRAMME[index];
        }
        return DWELLINGS[(index - PROGRAMME.length) % DWELLINGS.length];
    }

    /** The catalogue span of what this run places, for reports. */
    public static int spanOf(String blueprintId) {
        return BuildPlanner.plotSpanOf(blueprintId, BuildCatalogue.DEFAULT);
    }

}
