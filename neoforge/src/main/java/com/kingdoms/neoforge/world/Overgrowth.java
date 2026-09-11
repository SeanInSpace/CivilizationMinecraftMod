package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.WorkArea;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * What grows over a town's works, and how much of it a crew takes off.
 *
 * <p>Two complaints, one fault. A road through a grass biome came out with leaf
 * litter lying on top of the gravel, so it read as generated after the world
 * rather than as part of it. A building in a forest came out with a canopy
 * across its roof and trunks against its walls. In both cases the town had done
 * exactly what it was told: it cleared the cells it was about to write in, and
 * nothing else. A block that sits <em>on</em> the surface — litter, grass, a
 * fern, a flower, a layer of snow — was never in the way of anything, so nobody
 * ever took it off; and a tree whose trunk stood one block outside a plot was
 * somebody else's problem, even though its crown was over the roof.
 *
 * <p>So the rules live here, in one place, and both the paving and the placing
 * ask them. Three of them:
 *
 * <ul>
 *   <li><strong>Ground cover comes off what the town paves.</strong> Whatever
 *       stands in the cells above a paved block goes; the cells either side of
 *       the carriageway are verge and keep theirs, which is what makes a road
 *       read as a road through a meadow rather than as a strip of bare dirt.</li>
 *   <li><strong>A plot is cleared to the sky.</strong> Growth over the
 *       footprint and its doorstep ring is taken from the floor course upward,
 *       however high it goes. Terrain is not: stone, ore and bedrock standing
 *       over a plot are the hillside, and the excavation decides about those.</li>
 *   <li><strong>A trunk against a wall is felled.</strong> One narrow band past
 *       the plot — see {@link Footprint#inClearanceBand} — and logs only. The
 *       crown left behind decays on vanilla's own leaf-distance rule, which is
 *       what happens to any tree anybody cuts down.</li>
 *   <li><strong>The forester's trees are nobody else's.</strong> A trunk rooted
 *       in a lumber camp's belt is left standing whatever is being built or laid
 *       over it — see {@link Spared}. Both the plot rules and the paving rule ask
 *       it, and for the same reason: those trunks are the stand the camp was
 *       planted to work, and a town that fells them has paid itself out of its
 *       own woodshed.</li>
 * </ul>
 *
 * <p><strong>Why the world is behind a seam.</strong> Block tags are bound when
 * a server loads its datapacks, and a unit test has no server — {@code
 * state.is(BlockTags.LEAVES)} is false in a JUnit run for oak leaves themselves.
 * Every rule below is therefore written against {@link Cover}, which is the
 * whole of what the rules care about, and the one method that reads a real
 * {@link BlockState} is {@link #coverOf}. That is the same argument {@code
 * BlueprintPlacer.Site} and {@code BlueprintPlacer.Standing} make, for the same
 * reason: the part worth pinning is the geometry, and the geometry does not
 * need a running game.
 */
public final class Overgrowth {

    private Overgrowth() {
    }

    /** What one cell is, as far as clearing it goes. */
    public enum Cover {

        /** Nothing there, or nothing anybody can see. */
        NONE,

        /** Something that sits on the surface: litter, grass, fern, flower, snow. */
        GROUND,

        /** Leaves. Cleared over what the town builds, never out in the band. */
        LEAF,

        /** A log. The only thing felled outside a plot. */
        LOG,

        /** Somebody's wall, or the hillside. Never touched by any rule here. */
        KEEP
    }

    /** The two questions the clearing rules put to a world. */
    public interface Sky {

        /** What stands at this cell. */
        Cover at(int x, int y, int z);

        /**
         * One above the highest block in this column, so a sweep upward knows
         * where to stop without walking to the build limit of every column.
         */
        int topOf(int x, int z);
    }

    /** Ground a trade is working, which the town's builders leave standing. */
    public interface Spared {

        boolean covers(int x, int z);
    }

    /** Nothing is spared: the fixture for a town with no lumber camp. */
    public static final Spared NOTHING_SPARED = (x, z) -> false;

    /**
     * How far past a building's outer wall a trunk is still leaning on it.
     *
     * <p>Two, measured from the wall rather than from the plot. The plot already
     * carries a doorstep ring one block wide, so what this actually comes to is
     * the single further ring beyond that — the cells whose tree has branches
     * through the eaves. Three would have every house in a wood standing in a
     * bald ring of its own making, which is the scraped-pad look the doorstep
     * ring was shrunk from two blocks to one to be rid of.
     */
    public static final int TRUNK_CLEARANCE = 2;

    /**
     * The same reach, counted out from the edge of the plot.
     *
     * <p>What {@link Footprint#inClearanceBand} wants, because the footprint a
     * building records is its walls <em>plus</em> its doorstep ring. Reading
     * {@link BuildingSizes#APRON} rather than writing 1 means the band cannot
     * quietly widen if the doorstep ever narrows again.
     */
    private static final int BAND_PAST_THE_PLOT =
            Math.max(0, TRUNK_CLEARANCE - BuildingSizes.APRON);

    /**
     * The tallest trunk the band takes, in blocks.
     *
     * <p>A bound rather than a judgement: a jungle tree is about thirty and a
     * column of logs somebody built is not a tree at all. Stopping at thirty
     * means the worst a runaway can do is thirty blocks in one column.
     */
    public static final int TRUNK_COLUMN = 30;

    /**
     * How far below the floor line the search for a stump starts.
     *
     * <p>A tree beside a plot is rooted in its own ground, which on a slope is
     * lower than the floor the building was surveyed to. Two is the same slack
     * the grading rules allow themselves.
     */
    private static final int TRUNK_ROOT_DEPTH = 2;

    // --- reading a real world ---

    /**
     * What one block state is.
     *
     * <p>{@code canBeReplaced()} is the spine of the ground-cover test and is
     * very nearly the whole of it: short grass, tall grass, ferns, dead bush,
     * bush, dry grass, snow layers, vines and leaf litter all carry the flag,
     * which is exactly why a block placed into one of them simply overwrites it.
     * Three families do not, and they are the ones checked by tag: flowers,
     * pink petals and wildflowers are {@code #flowers}, and the firefly bush is
     * in neither — it is picked up by {@code #replaceable_by_trees}, which is
     * vanilla's own list of what a growing tree pushes aside and is therefore
     * the right list for what a building pushes aside.
     *
     * <p>Fluids are excluded before anything else. Water is replaceable, and a
     * rule that cleared it would punch a hole through the side of every pond a
     * road runs along. Crops and saplings are in none of these, deliberately:
     * the town plants those.
     */
    public static Cover coverOf(BlockState state) {
        if (state.isAir()) {
            return Cover.NONE;
        }
        if (!state.getFluidState().isEmpty()) {
            return Cover.KEEP;
        }
        if (state.is(BlockTags.LOGS)) {
            return Cover.LOG;
        }
        if (state.is(BlockTags.LEAVES)) {
            return Cover.LEAF;
        }
        if (state.canBeReplaced()
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)) {
            return Cover.GROUND;
        }
        return Cover.KEEP;
    }

    /** The real world, asked the two questions. */
    public static Sky over(ServerLevel level) {
        return new Sky() {
            @Override
            public Cover at(int x, int y, int z) {
                BlockPos pos = new BlockPos(x, y, z);
                return level.isLoaded(pos) ? coverOf(level.getBlockState(pos)) : Cover.KEEP;
            }

            @Override
            public int topOf(int x, int z) {
                // WORLD_SURFACE counts anything that is not air, which is what a
                // canopy is: MOTION_BLOCKING_NO_LEAVES would stop under the very
                // leaves this exists to find.
                return level.isLoaded(new BlockPos(x, level.getMinY() + 1, z))
                        ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
                        : Integer.MIN_VALUE;
            }
        };
    }

    /**
     * The woodland this town's foresters are working, if any reaches here.
     *
     * <p>The one thing the trunk band must not touch. A seeded camp has a stand
     * planted for it out past the houses — see {@code ForesterStand.BELT} — and
     * a building raised near the edge of the claim would otherwise fell the very
     * trees the camp exists to cut.
     *
     * <p>Only the part of the claim that lies <em>outside</em> the village is
     * spared, which is the same line {@code LumberjackWorker} already draws: a
     * lumber camp's work area is centered on the camp and widened until it
     * reaches past the houses, so it covers most of the village as well, and
     * sparing all of it would mean no building anywhere in town ever cleared a
     * trunk off its wall. Trees between the houses are fair game and are never
     * replanted; trees in the belt are the stand.
     */
    public static Spared woodlandAround(ServerLevel level, BlockPos near) {
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            return NOTHING_SPARED;
        }
        List<Settlement> towns = new ArrayList<>();
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                if (settlement.lumberArea() != null
                        && settlement.contains(new SimPos(near.getX(), near.getY(),
                                near.getZ()))) {
                    towns.add(settlement);
                }
            }
        }
        if (towns.isEmpty()) {
            return NOTHING_SPARED;
        }
        return beltOf(towns);
    }

    /**
     * The belt of one town, which is the answer where the town is already known.
     *
     * <p>{@link #woodlandAround} has to find the town from a position, and it
     * finds it by asking which claims contain the point — which is the right
     * question for a building, standing inside the village by definition, and
     * the wrong one for a road. The belt a road runs through lies <em>outside</em>
     * the claim, so a lane paved out there matched no town at all and spared
     * nothing. Paving already knows whose road it is laying, so it says.
     */
    public static Spared woodlandOf(Settlement town) {
        if (town == null || town.lumberArea() == null) {
            return NOTHING_SPARED;
        }
        return beltOf(List.of(town));
    }

    private static Spared beltOf(List<Settlement> towns) {
        return (x, z) -> {
            for (Settlement town : towns) {
                WorkArea stand = town.lumberArea();
                SimPos at = new SimPos(x, stand.center().y(), z);
                if (stand.contains(at) && !insideVillage(town, x, z)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static boolean insideVillage(Settlement town, int x, int z) {
        SimPos center = town.center();
        long dx = x - center.x();
        long dz = z - center.z();
        long reach = town.claimRadius();
        return dx * dx + dz * dz <= reach * reach;
    }

    // --- the rules ---

    /**
     * The growth that has to come off a plot, split by who takes it off.
     *
     * <p><strong>Wood is dug and foliage is stripped, and the difference is
     * whether anybody can reach it.</strong> A log anywhere in the column is
     * reachable however high it is, because {@code Excavation} swaps a log for
     * the stump of the trunk it belongs to and prices the whole tree there — one
     * job, at ground level, with an axe. A leaf is not: a crown eight blocks over
     * a roof has no square beside it anybody can stand on, so handing it to the
     * crew buys thirty-six failed searches for footing and then an abandoned
     * cell, and the canopy is still there. So the leaves are simply taken, at the
     * moment the site opens, on both fidelities. They cost nothing, they yield
     * nothing, and nobody was ever going to climb up for them.
     *
     * @param dug      trunks and ground cover: the crew's job list
     * @param stripped leaves over the plot: taken outright when the site opens
     */
    public record Clearing(List<BlockPos> dug, List<BlockPos> stripped) {

        public static final Clearing NOTHING = new Clearing(List.of(), List.of());
    }

    /**
     * Everything that has to come off a plot before a building can stand clear
     * on it: the growth over it, and the trunks leaning on it.
     *
     * <p>The dug half is returned as bare cells in no particular order, because
     * that is what the excavation wants — it works out its own order from the
     * terrain as the ground comes away.
     *
     * @param base  the floor line of the building; the sweep starts here
     * @param plot  the footprint with its doorstep ring, notch and all
     */
    public static Clearing overPlot(Sky sky, BlockPos base, Footprint plot, Spared spared) {
        List<BlockPos> dug = new ArrayList<>();
        List<BlockPos> stripped = new ArrayList<>();
        int reachX = plot.width() / 2 + BAND_PAST_THE_PLOT;
        int reachZ = plot.depth() / 2 + BAND_PAST_THE_PLOT;
        for (int dx = -reachX; dx <= reachX; dx++) {
            for (int dz = -reachZ; dz <= reachZ; dz++) {
                int x = base.getX() + dx;
                int z = base.getZ() + dz;
                if (plot.covers(base.getX(), base.getZ(), x, z)) {
                    clearColumn(sky, dug, stripped, x, z, base.getY());
                } else if (plot.inClearanceBand(base.getX(), base.getZ(), x, z,
                        BAND_PAST_THE_PLOT) && !spared.covers(x, z)) {
                    fellTrunk(sky, dug, x, z, base.getY());
                }
            }
        }
        return new Clearing(dug, stripped);
    }

    /**
     * One column of a plot, from the floor course to whatever is over it.
     *
     * <p>Anything that is not growth is stepped over rather than stopping the
     * sweep. Stopping would be the obvious thing and it is wrong twice: on a
     * finished building the roof is the first thing the walk meets, so the
     * canopy over it would never be reached; and in the doorstep ring the first
     * thing may be the hillside, with a branch above it.
     */
    private static void clearColumn(Sky sky, List<BlockPos> dug, List<BlockPos> stripped,
                                    int x, int z, int floor) {
        int top = sky.topOf(x, z);
        if (top == Integer.MIN_VALUE) {
            return;   // nobody can see this column
        }
        for (int y = floor; y <= top; y++) {
            Cover cover = sky.at(x, y, z);
            if (cover == Cover.GROUND || cover == Cover.LOG) {
                dug.add(new BlockPos(x, y, z));
            } else if (cover == Cover.LEAF) {
                stripped.add(new BlockPos(x, y, z));
            }
        }
    }

    /**
     * The trunk standing in one column of the band, if there is one.
     *
     * <p>Logs and nothing else — no leaves, no ground cover. A tree beside a
     * house is not in the way and is not the town's to take; a tree leaning
     * <em>on</em> the house is, and taking its trunk is what a person with an
     * axe would do. What is left hanging comes down by itself: vanilla decays a
     * leaf that is more than six blocks from any log.
     */
    private static void fellTrunk(Sky sky, List<BlockPos> cleared, int x, int z, int floor) {
        int top = Math.min(sky.topOf(x, z), floor + TRUNK_COLUMN);
        int found = 0;
        for (int y = floor - TRUNK_ROOT_DEPTH; y <= top && found < TRUNK_COLUMN; y++) {
            if (sky.at(x, y, z) == Cover.LOG) {
                cleared.add(new BlockPos(x, y, z));
                found++;
            }
        }
    }

    /**
     * Everything over one paved block that a road does not run under.
     *
     * <p>The litter, the grass and the flowers standing on the stones, and the
     * branches and the trunk of whatever tree the way happens to pass through.
     * The sweep stops at the first thing that is neither growth nor air, because
     * that is a roof or an arch or a bridge and a road is perfectly entitled to
     * run beneath one.
     *
     * <p>This is asked of the paved cells only. The verge — the cells either
     * side of the carriageway — is never handed here, which is the whole of what
     * makes a laid road read as a road through a meadow.
     *
     * <p><strong>Except the forester's trunks, which no road fells.</strong>
     * {@link #overPlot} has always asked {@code spared} before taking a tree off
     * a wall and this never asked at all, so a lane routed through the belt came
     * out having cut down the stand the camp was planted to work. What is spared
     * is the wood and only the wood: the litter on the stones and the branches
     * over the carriageway still come off, because a road under a bough is a road
     * through a wood and reads as one. A trunk standing in the middle of the
     * carriageway is not something to clear round — it means the road should
     * never have been routed there, which is {@code PathPlanner}'s job and is now
     * its rule.
     */
    public static List<BlockPos> overPaving(Sky sky, BlockPos surface, Spared spared) {
        List<BlockPos> cleared = new ArrayList<>();
        // A trunk is a column, so the column being paved is the column it is
        // rooted in: no separate search for a stump is needed or wanted.
        boolean theForesters = spared.covers(surface.getX(), surface.getZ());
        int top = Math.min(sky.topOf(surface.getX(), surface.getZ()),
                surface.getY() + TRUNK_COLUMN);
        for (int y = surface.getY() + 1; y <= top; y++) {
            Cover cover = sky.at(surface.getX(), y, surface.getZ());
            if (cover == Cover.NONE) {
                continue;   // the gap between the road and the branches over it
            }
            if (cover == Cover.KEEP) {
                return cleared;   // somebody's floor: the road goes under it
            }
            if (cover == Cover.LOG && theForesters) {
                continue;   // the camp's tree; the road may pass, not fell
            }
            cleared.add(new BlockPos(surface.getX(), y, surface.getZ()));
        }
        return cleared;
    }
}
