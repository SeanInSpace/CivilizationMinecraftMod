package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
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
    private final List<PathNetwork.Segment> toPave;
    private final Settlement town;
    private final int ticksBetween;
    private int waited;
    private int placed;
    private int paved;

    private record Placement(String blueprintId, SimPos at, int facing,
                             TownPlan.Street fronts) {
    }

    private BuildTest(ServerLevel level, List<Placement> pending,
                      List<PathNetwork.Segment> toPave, Settlement town,
                      int ticksBetween) {
        this.level = level;
        this.pending = pending;
        this.toPave = toPave;
        this.town = town;
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
            pending.add(new Placement(blueprintFor(i), plot.at(), plot.facing(),
                    plan.streetOf(plot)));
        }

        // A real settlement, registered, holding the buildings and the streets --
        // and never stepped. The town map and the lamp find a town by walking the
        // kingdoms, so a rendered town that was not registered was a town those
        // tools could not see: you could stand in the middle of one and survey
        // nothing at all. Registering it costs nothing, because it is marked as a
        // drawing and a drawing does not grow.
        Settlement town = new Settlement(Settlement.Id.random(), "Buildtest",
                centre, Math.max(96, count * 2));
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.TOWN);
        town.setCultureId("kingdoms:orc");
        town.setDrawnOnly(true);
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Buildtest", "kingdoms:orc");
        kingdom.restoreSettlement(town);
        var world = KingdomsMod.simulationFor(level);
        if (world != null) {
            world.addKingdom(kingdom);
            // Nothing else in this class touches saved data, so without this the
            // whole town lives in memory and dies with the session: reopening the
            // world gave back bare streets and "No kingdoms yet", with the map
            // and the lamp blind again. Cost one wasted verification pass.
            KingdomsSavedData.get(level).setDirty();
        }

        // The streets, as the network the map already knows how to draw. Every
        // stretch is opened at once: nobody is walking them out, and a plan being
        // rendered rather than grown has no reason to meter itself.
        List<PathNetwork.Segment> toPave = new ArrayList<>();
        for (TownPlan.Street street : plan.streets()) {
            List<SimPos> path = street.path();
            for (int i = 1; i < path.size(); i++) {
                toPave.add(new PathNetwork.Segment(
                        path.get(i - 1), path.get(i), street.width()));
            }
        }
        for (int i = 0; i < toPave.size(); i++) {
            town.paths().add(toPave.get(i));
            town.paths().markOpened(i);
        }
        // Twenty ticks to the second, so a rate is a wait: one a second waits
        // twenty, five a second waits four. Never less than one, or a rate past
        // twenty would place the whole town in a single tick and the point of
        // watching it appear would be lost.
        int ticks = Math.max(1, 20 / Math.max(1, buildingsPerSecond));
        BuildTest run = new BuildTest(level, pending, toPave, town, ticks);
        synchronized (RUNNING) {
            RUNNING.put(level, run);
        }
        KingdomsMod.LOGGER.info("BUILDTEST start centre={} count={} bps={} streets={}",
                centre, pending.size(), buildingsPerSecond, plan.streets().size());
        KingdomsMod.LOGGER.info("BUILDTEST street runs to pave: {}", toPave.size());
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
        // Streets first, and faster than the buildings. A town with its houses up
        // and no roads between them reads as a scatter of sheds, which is the one
        // thing this instrument exists to be able to see clearly.
        for (int i = 0; i < RUNS_PER_TICK && paved < toPave.size(); i++) {
            PathNetwork.Segment run = toPave.get(paved++);
            level.getChunk(run.from().x() >> 4, run.from().z() >> 4);
            PathLayer.mend(level, run);
        }
        if (pending.isEmpty()) {
            if (paved < toPave.size()) {
                return false;   // the houses are up, the roads are still going in
            }
            KingdomsMod.LOGGER.info("BUILDTEST done, {} buildings and {} stretches",
                    placed, paved);
            KingdomsSavedData.get(level).setDirty();
            return true;
        }
        if (++waited < ticksBetween) {
            return false;
        }
        waited = 0;
        Placement next = pending.remove(0);
        SimPos where = againstTheKerb(next);
        // The chunk, before the building. materializeBlueprint refuses an
        // unloaded column and a grid spreads well past whatever a player has
        // loaded, so a run left to chance would place its middle and silently
        // drop its edges. One chunk per building at a building a second is a
        // cost this can afford; the simulation cannot, which is why it does not
        // do this and this is not the simulation.
        level.getChunk(where.x() >> 4, where.z() >> 4);
        int y = BlueprintPlacer.baseFor(next.blueprintId(),
                BlueprintPlacer.groundLevel(level, where.x(), where.z()));
        Footprint print = BlueprintPlacer.place(level, next.blueprintId(),
                new BlockPos(where.x(), y, where.z()), next.facing());
        placed++;
        // Recorded on the settlement, so the map and the lamp can see it. With
        // the footprint the placer actually laid, not the catalogue guess: a
        // building whose size is unknown never travels to the map.
        Building raised = new Building(next.blueprintId(),
                new SimPos(where.x(), y, where.z()), placed, true);
        raised.setSurveyed(true);
        raised.setFacing(next.facing());
        raised.setFootprint(print);
        town.addBuilding(raised);
        // Logged per building, because BlueprintPlacer says nothing and a run
        // that silently placed nothing looked exactly like a run that silently
        // placed everything -- which cost one wasted verification.
        KingdomsMod.LOGGER.info("BUILDTEST placed {} {} at {},{},{} facing {} size {}x{}",
                placed, next.blueprintId(), where.x(), y, where.z(),
                next.facing(), print.width(), print.depth());
        return false;
    }

    /**
     * Moves a building up to the kerb of the street it fronts.
     *
     * <p>The plan sets a plot back thirteen blocks from the middle of its street,
     * which is the right distance for the eleven-block square the plan reserves.
     * The buildings that actually go there are seven and nine blocks across, and
     * the paved road is five — so a house sat six blocks of bare grass from the
     * kerb, and a street of them read as two rows of sheds facing a gap rather
     * than a street.
     *
     * <p>The plan cannot fix this: it reserves ground before anybody knows what
     * will stand on it, and reserving the largest possible plot is what keeps
     * buildings off each other. The renderer can, because by then the blueprint
     * is known — so the building is measured first and set down with its front
     * wall a fixed verge from the paved edge, whatever its size.
     *
     * <p>It only ever moves a building TOWARD its street, and never past the
     * kerb, so nothing can be pushed onto the road it fronts.
     */
    private SimPos againstTheKerb(Placement placement) {
        if (placement.fronts() == null) {
            return placement.at();
        }
        SimPos plot = placement.at();
        SimPos onStreet = nearestPointOn(placement.fronts(), plot);
        if (onStreet == null) {
            return plot;
        }
        double dx = plot.x() - onStreet.x();
        double dz = plot.z() - onStreet.z();
        double away = Math.hypot(dx, dz);
        if (away < 1) {
            return plot;   // already on the line; leave it where the plan put it
        }
        // What the blueprint actually measures, not what the catalogue reserved.
        Footprint size = BlueprintPlacer.measure(level, placement.blueprintId(),
                new BlockPos(plot.x(), level.getMinY() + 1, plot.z()));
        int across = size.isKnown() ? Math.max(size.width(), size.depth())
                : BuildPlanner.plotSpanOf(placement.blueprintId(), BuildCatalogue.DEFAULT);
        // Half the paved way, a verge, and half the building.
        double wanted = Math.max(1, placement.fronts().width() / 3.0) + VERGE + across / 2.0;
        if (wanted >= away) {
            return plot;   // already at least that far back; do not push it out
        }
        return new SimPos(
                onStreet.x() + (int) Math.round(dx / away * wanted), plot.y(),
                onStreet.z() + (int) Math.round(dz / away * wanted));
    }

    /** The point of a street nearest a plot, along its whole path. */
    private static SimPos nearestPointOn(TownPlan.Street street, SimPos from) {
        SimPos best = null;
        long nearest = Long.MAX_VALUE;
        for (SimPos point : street.path()) {
            long away = (long) (point.x() - from.x()) * (point.x() - from.x())
                    + (long) (point.z() - from.z()) * (point.z() - from.z());
            if (away < nearest) {
                nearest = away;
                best = point;
            }
        }
        return best;
    }

    /** Bare ground between the paved way and a front wall. */
    private static final int VERGE = 1;

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

    /**
     * Stretches of street paved in one tick.
     *
     * <p>Faster than the buildings on purpose: the roads want to be there with
     * the houses rather than long after them, or the town reads as a scatter for
     * most of the time anybody is watching it appear.
     */
    private static final int RUNS_PER_TICK = 4;

    /** The catalogue span of what this run places, for reports. */
    public static int spanOf(String blueprintId) {
        return BuildPlanner.plotSpanOf(blueprintId, BuildCatalogue.DEFAULT);
    }

}
