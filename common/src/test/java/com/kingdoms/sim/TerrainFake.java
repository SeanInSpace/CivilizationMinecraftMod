package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Footprint;

/**
 * A world with actual ground in it, for tests that care where a town builds.
 *
 * <p>Every fake bridge in this suite until now answered {@code isSiteSuitable}
 * with yes, because none of them had any terrain to answer from. So the whole
 * siting half of the simulation — slope, water, the give-up path, the
 * difference between one layout and another on real land — was only ever
 * exercised by launching Minecraft and looking. That is a five-minute round trip
 * per question, and it is why a quarter of a town stood in water for months
 * without a red test anywhere.
 *
 * <p>The ground here is synthetic but not trivial: rolling relief, a river
 * running across it, and a lake. It is deterministic, so a test that passes
 * today fails tomorrow only because the code changed.
 *
 * <p>It deliberately mirrors the <em>rules</em> the real bridge applies rather
 * than the real bridge's implementation — bulk fall rather than worst step,
 * water refused outright — so that a change to those rules shows up here as a
 * change in the town, which is the whole point.
 */
public final class TerrainFake implements WorldBridge {

    /** Where the water sits, as vanilla has it. */
    public static final int SEA_LEVEL = 63;

    /** How far a plot may fall across its bulk before it is a slope, not a dip. */
    public static final int MAX_FALL = 4;

    /** Half-width of the river channel, in blocks. */
    private static final int RIVER_HALF = 9;

    private final long seed;

    public TerrainFake(long seed) {
        this.seed = seed;
    }

    /**
     * The ground at a column: rolling relief, cut by a river, dented by a lake.
     *
     * <p>Cheap trigonometry rather than noise, because a test wants the same
     * hill every run and does not care whether it looks like Minecraft. What it
     * has to have is the things that break siting: a fall steep enough to
     * refuse, water low enough to drown a floor, and enough variety that a town
     * has to actually choose.
     */
    public int groundAt(int x, int z) {
        double roll = 9 * Math.sin((x + seed) / 41.0) + 7 * Math.cos((z - seed) / 37.0);
        double ridge = 12 * Math.sin((x + z) / 97.0);
        int height = (int) Math.round(72 + roll + ridge);

        // A river, running north-south, wandering as it goes.
        int bank = (int) Math.round(60 * Math.sin(z / 130.0)) - 40;
        if (Math.abs(x - bank) <= RIVER_HALF) {
            return SEA_LEVEL - 3;
        }
        // A lake off to one side.
        if (Math.hypot(x - 150, z + 90) < 46) {
            return SEA_LEVEL - 4;
        }
        return height;
    }

    /** Whether this column is under water, which here means below the sea. */
    public boolean wetAt(int x, int z) {
        return groundAt(x, z) < SEA_LEVEL;
    }

    @Override
    public int surfaceHeight(SimPos pos) {
        return groundAt(pos.x(), pos.z());
    }

    @Override
    public boolean isLoaded(SimPos pos) {
        return true;   // the point of this fake is that everything IS judged
    }

    @Override
    public boolean playerWithin(SimPos pos, double radius) {
        return false;  // unwatched, which is how towns actually grow
    }

    @Override
    public boolean standsInWater(SimPos pos, int radius) {
        for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                if (wetAt(pos.x() + dx, pos.z() + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isSiteSuitable(SimPos plot, int radius) {
        return siteFault(plot, radius) == SITE_FAULT_NONE;
    }

    /**
     * The same judgement, in courses of fall past what a builder will cut.
     *
     * <p>Written as the scored answer and the veto derived from it rather than
     * the other way round, because the two must agree exactly: a plot the town
     * would accept has to score zero, or a search that falls back on the
     * least-bad candidate treats good ground as a compromise.
     */
    @Override
    public int siteFault(SimPos plot, int radius) {
        if (standsInWater(plot, radius)) {
            return SITE_FAULT_OPEN_WATER;
        }
        return Math.max(0, bulkFall(plot, radius) - MAX_FALL);
    }

    /**
     * How far the bulk of a plot falls: the middle three fifths of its columns.
     *
     * <p>The bulk, not the extremes: a pit the builders would fill is not a
     * reason to walk away from a shelf. Mirrors the live rule.
     */
    private int bulkFall(SimPos plot, int radius) {
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 3) {
            for (int dz = -radius; dz <= radius; dz += 3) {
                heights.add(groundAt(plot.x() + dx, plot.z() + dz));
            }
        }
        java.util.Collections.sort(heights);
        int low = heights.get(heights.size() / 5);
        int high = heights.get((heights.size() * 4) / 5);
        return high - low;
    }

    /**
     * A building comes out the size the catalogue said it would.
     *
     * <p>This used to invent a span — nine, or fifteen for a farm — and that
     * quietly broke every overlap assertion made against it. The settlement
     * measures a standing building's claim from its <em>footprint</em> when it
     * has one, so a fake that builds things smaller than the catalogue reserved
     * lets the town pack them tighter than it ever could in a world, and a test
     * comparing catalogue spans then reports overlaps the code never made.
     *
     * <p>A test double that lies about the thing under test is worse than none.
     */
    @Override
    public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed,
                                          int facing) {
        int span = com.kingdoms.sim.settlement.BuildPlanner.plotSpanOf(
                id, com.kingdoms.sim.settlement.BuildCatalogue.DEFAULT);
        return new Footprint(groundAt(origin.x(), origin.z()), span, span, 5);
    }

    @Override
    public int woodedness(SimPos centre, int radius) {
        return Math.abs((centre.x() * 31 + centre.z() * 17) % 100);
    }

    @Override
    public void log(String message) {
    }
}
