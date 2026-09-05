package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Footprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A world whose ground was recorded out of the world the reports came from.
 *
 * <p>{@link TerrainFake} exists so the siting rules are exercised at all;
 * this exists because they were exercised on the wrong ground. Its surface is
 * three sine waves, and the steepest step between neighboring columns is one
 * block — so a rule refusing ground that climbs more than a block a step can
 * never fire, and three consecutive fixes for roads on unclimbable slopes
 * measured perfectly clean in this suite while the world they were written for
 * had forty-eight opened runs climbing two blocks a step and one climbing
 * sixteen.
 *
 * <p>A test double that cannot exhibit the fault certifies the fault. So the
 * ground here is a {@link HeightField} captured by {@code /civ terrain} with
 * full chunk generation forced — real, jagged, and the same coordinates every
 * survey in this project has used.
 *
 * <p>The rules layered on top of it — what counts as water, what counts as
 * buildable — deliberately mirror the live ones exactly as {@code TerrainFake}
 * mirrors them, so the two fakes answer the same question the same way and only
 * the ground differs.
 */
public final class RecordedTerrain implements WorldBridge {

    /** The field captured round the center every survey in this project uses. */
    public static final String SEED_8675309 = "/terrain/seed8675309_town.hf";

    /** How far a plot may fall across its bulk before it is a slope, not a dip. */
    public static final int MAX_FALL = 4;

    private final HeightField ground;

    public RecordedTerrain(HeightField ground) {
        this.ground = ground;
    }

    public static RecordedTerrain of(String resource) {
        return new RecordedTerrain(HeightField.load(resource));
    }

    public int groundAt(int x, int z) {
        return ground.heightAt(x, z);
    }

    public boolean wetAt(int x, int z) {
        return ground.wetAt(x, z);
    }

    public int seaLevel() {
        return ground.seaLevel();
    }

    public HeightField field() {
        return ground;
    }

    @Override
    public int surfaceHeight(SimPos pos) {
        return groundAt(pos.x(), pos.z());
    }

    /**
     * Everything is known here, which is the point of a recording.
     *
     * <p>The live oracle answers unread ground from the generator's noise and is
     * wrong by about eight courses when it does; that is a separate fault with
     * its own fix. This fixture is for the rules, not for the reading of the
     * ground, so it hands over what was actually there.
     */
    @Override
    public boolean isLoaded(SimPos pos) {
        return true;
    }

    @Override
    public boolean playerWithin(SimPos pos, double radius) {
        return false;   // unwatched, which is how towns actually grow
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
     * The same judgment scored, exactly as {@code TerrainFake} scores it.
     *
     * <p>The two fakes have to answer the same question the same way or the
     * recording stops being the same rules on different ground, which is the
     * only thing it is for.
     */
    @Override
    public int siteFault(SimPos plot, int radius) {
        if (standsInWater(plot, radius)) {
            return SITE_FAULT_OPEN_WATER;
        }
        return Math.max(0, bulkFall(plot, radius) - MAX_FALL);
    }

    /** The middle three fifths of a plot's columns: a pit is not a cliff. */
    private int bulkFall(SimPos plot, int radius) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 3) {
            for (int dz = -radius; dz <= radius; dz += 3) {
                heights.add(groundAt(plot.x() + dx, plot.z() + dz));
            }
        }
        Collections.sort(heights);
        int low = heights.get(heights.size() / 5);
        int high = heights.get((heights.size() * 4) / 5);
        return high - low;
    }

    /**
     * A dip this town could fill, as opposed to a lake or a hillside.
     *
     * <p>Mirrors the live rule: dry, and falling no further across its bulk than
     * an earthwork can make good.
     */
    @Override
    public boolean isSiteLevellable(SimPos plot, int radius) {
        if (standsInWater(plot, radius)) {
            return false;
        }
        return bulkFall(plot, radius) <= BuildPlanner.LEVELABLE_FALL;
    }

    @Override
    public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed,
                                          int facing) {
        int span = BuildPlanner.plotSpanOf(id, BuildCatalogue.DEFAULT);
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
