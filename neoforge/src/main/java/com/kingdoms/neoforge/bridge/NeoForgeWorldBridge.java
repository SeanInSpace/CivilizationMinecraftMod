package com.kingdoms.neoforge.bridge;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.neoforge.world.TerrainOracle;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Danger;
import com.kingdoms.sim.settlement.Footprint;
import net.minecraft.world.level.block.state.BlockState;
import com.kingdoms.sim.platform.Sighting;
import com.kingdoms.sim.platform.WorldBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Objects;
import com.kingdoms.neoforge.entity.PersonEntity;
import java.util.List;

/**
 * Translates between the simulation's plain data and an actual {@link ServerLevel}.
 *
 * <p>This is the only class that knows about both worlds. Keep it thin — it should
 * translate and delegate, never decide.
 */
public final class NeoForgeWorldBridge implements WorldBridge {

    private final ServerLevel level;

    public NeoForgeWorldBridge(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        this.oracle = new TerrainOracle(level);
    }

    public ServerLevel level() {
        return level;
    }

    public static BlockPos toBlockPos(SimPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    public static SimPos toSimPos(BlockPos pos) {
        return new SimPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean playerWithin(SimPos pos, double radius) {
        return level.hasNearbyAlivePlayer(pos.x(), pos.y(), pos.z(), radius);
    }

    @Override
    public boolean isLoaded(SimPos pos) {
        return level.isLoaded(toBlockPos(pos));
    }

    @Override
    public boolean isSiteLevellable(SimPos plot, int radius) {
        // Dry first: no earthwork drains a lake.
        if (oracle.anyWet(plot.x(), plot.z(), radius, TerrainOracle.GRAIN)) {
            return false;
        }
        // And a dip rather than a hillside, measured on the bulk of the plot as
        // every other rule here measures it.
        return oracle.bulkFall(plot.x(), plot.z(), radius, TerrainOracle.GRAIN)
                <= com.kingdoms.sim.settlement.BuildPlanner.LEVELABLE_FALL;
    }

    @Override
    public int groundHeight(SimPos pos) {
        // The oracle, which answers everywhere. This is the question a route
        // asks, and it must not be answered with the caller's own guess.
        return oracle.height(pos.x(), pos.z());
    }

    @Override
    public int surfaceHeight(SimPos pos) {
        if (!level.isLoaded(toBlockPos(pos))) {
            return pos.y();
        }
        // Real ground, not the top of whatever is growing on it. The plot height
        // chosen here is what the survey later builds the whole excavation around.
        return BlueprintPlacer.groundLevel(level, pos.x(), pos.z());
    }

    /**
     * Draws a finished building: a datapack structure template when one exists for
     * the blueprint id, a procedural structure otherwise. See {@link BlueprintPlacer}.
     *
     * <p>Never force-loads a chunk. If the area is not loaded this does nothing and
     * the building stays pending until a later step finds it available.
     */
    @Override
    public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed,
                                          int facing) {
        if (!level.isLoaded(toBlockPos(origin))) {
            return Footprint.UNKNOWN;
        }
        // A surveyed site keeps its measured height. Re-measuring here is what put
        // a stamped building one course above the same building the builders had
        // begun by hand — two copies of it, a block apart.
        //
        // An unsurveyed origin carries a planning estimate, so it does get snapped
        // — through the same floorFor the builders would have used, not the raw
        // surface, or the two paths disagree again.
        int y = surveyed ? origin.y()
                : BlueprintPlacer.baseFor(blueprintId, surfaceHeight(origin));
        BlockPos base = new BlockPos(origin.x(), y, origin.z());
        Footprint placed = BlueprintPlacer.place(level, blueprintId, base, facing);
        // Logs the base actually used, not the requested origin. A mismatch
        // between the two is precisely the double-placement bug.
        KingdomsMod.LOGGER.info("Materialized {} at {} (origin {}, surveyed {})",
                blueprintId, base, origin, surveyed);
        return placed;
    }

    /**
     * Lays the blocks a standing building is short of, and no others.
     *
     * <p>The origin is taken as given rather than snapped to the surface the way
     * an unsurveyed materialization is. A building that can be repaired has been
     * drawn, so its recorded height is the height it is at; re-measuring would put
     * the patch a course above the hole.
     *
     * <p>Ground nobody has loaded answers {@code -1} rather than "nothing was
     * missing", exactly as {@link #solidBlocksIn} does and for the same reason —
     * and, like that count, it is the whole plot that has to be there and not
     * merely the origin column, which is why {@link BlueprintPlacer#patch} does
     * the second half of the check.
     * The clock can finish paying for a repair on a step when the player has
     * walked out of range, and the two answers are opposite facts: on the first
     * the town's books are squared and the hole is filled, on the second nothing
     * was written at all. Reporting the second as the first is what let a town
     * pay off a repair, clear the damage, re-baseline the census against the
     * shell, and record the hole as the building's proper size forever.
     */
    @Override
    public int repairBlueprint(String blueprintId, SimPos origin, int facing) {
        BlockPos base = toBlockPos(origin);
        if (!level.isLoaded(base)) {
            return -1;
        }
        int mended = BlueprintPlacer.patch(level, blueprintId, base, facing);
        if (mended > 0) {
            KingdomsMod.LOGGER.info("Mended {} at {} — {} blocks put back",
                    blueprintId, base, mended);
        }
        return mended;
    }

    /**
     * How much the ground may rise and fall across a plot before it is refused.
     *
     * <p>Four courses. The builders will cut a shelf for anything up to that; past
     * it the building ends up either buried or on stilts, and the excavation alone
     * costs more than the building.
     */
    private static final int MAX_SLOPE = 4;

    /** Columns sampled for slope across a plot. The corners and centre catch what matters. */
    private static final int SAMPLE_STEP = 2;

    /** Half the widest plot the catalogue asks for, which is the animal farm's. */
    private static final int WIDEST_PLOT_HALF = BuildCatalogue.DEFAULT.stream()
            .mapToInt(BuildingType::plotSpan)
            .max()
            .orElse(BuildPlanner.DEFAULT_PLOT_SPAN) / 2;

    /**
     * One block further out again, for the bank a plot does not stand on.
     *
     * <p>A shore column can be perfectly dry and still flood the room, because the
     * floor course is cut one BELOW the grade it was measured at. Cut level with a
     * lake that starts just outside the walls and the lake is now above the floor
     * with nothing in between.
     */
    private static final int SHORE_MARGIN = 1;

    /**
     * Half-width of ground actually read for standing water.
     *
     * <p>Callers only ever ask for {@link BuildPlanner#PLOT_PROBE_RADIUS} — six —
     * because a plot is judged before the building that goes on it has been
     * chosen, and the signature carries no blueprint id to look a real span up
     * with. So the widest span any of them could turn out to be is assumed here
     * instead, plus the {@link BlueprintPlacer#APRON_MARGIN} cleared beyond the
     * walls. A farm was passed on a thirteen-wide window and then built seventeen
     * wide into the lake beside it: 363 blocks of standing water in the field.
     *
     * <p>Deliberately NOT applied to the slope test, which keeps the radius it was
     * given. Natural ground climbs more than four courses across twenty-seven
     * blocks as a matter of course, so widening that would refuse very nearly
     * every site — and a settlement that can find no site at all gives up looking
     * and takes the next ring slot unexamined, which is how it got into the lake
     * to begin with.
     *
     * <p>Reaching this far does occasionally see a standing farm's own irrigation
     * channel from the plot next door and refuse ground that is perfectly dry.
     * That costs the settlement one of its {@link BuildPlanner#PLOT_ATTEMPTS}
     * tries and no more — a refused candidate does not consume a ring slot — and
     * is the side to err on: a plot wrongly refused is invisible, a building
     * standing in a lake is the first thing a player sees.
     */
    private static final int WATER_REACH =
            WIDEST_PLOT_HALF + BlueprintPlacer.APRON_MARGIN + SHORE_MARGIN;

    /**
     * How far under the measured ground a column is still read for fluid.
     *
     * <p>The floor course replaces the topsoil and the foundation goes in beneath
     * it, so water lying a course or two under a dry-looking bank is inside the
     * building the moment the site is dug.
     */
    private static final int WATER_UNDERCUT = 2;

    /**
     * How much of this ground has trees standing on it, as a percentage.
     *
     * <p>Sampled on a coarse grid rather than every column: this is asked while
     * weighing a dozen candidate plots against each other, and the difference
     * between a wood and a meadow does not need every block counted. A column
     * counts as wooded if there is a log within a few blocks of the surface,
     * which finds trunks and ignores the leaf litter a single sapling drops.
     */
    @Override
    public int woodedness(SimPos centre, int radius) {
        BlockPos at = toBlockPos(centre);
        if (!level.isLoaded(at)) {
            return 0;   // nothing to judge; no reason to prefer this spot
        }
        int sampled = 0;
        int wooded = 0;
        for (int dx = -radius; dx <= radius; dx += WOOD_SAMPLE_STEP) {
            for (int dz = -radius; dz <= radius; dz += WOOD_SAMPLE_STEP) {
                BlockPos column = at.offset(dx, 0, dz);
                if (!level.isLoaded(column)) {
                    continue;
                }
                sampled++;
                if (hasTrunk(column)) {
                    wooded++;
                }
            }
        }
        return sampled == 0 ? 0 : wooded * 100 / sampled;
    }

    /** Whether a trunk stands anywhere near the surface of this column. */
    private boolean hasTrunk(BlockPos column) {
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                column.getX(), column.getZ());
        for (int y = surface; y > surface - WOOD_PROBE_DEPTH; y--) {
            if (level.getBlockState(new BlockPos(column.getX(), y, column.getZ()))
                    .is(net.minecraft.tags.BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    /** Every fourth column: enough to tell a wood from a meadow. */
    private static final int WOOD_SAMPLE_STEP = 4;

    /** How far below the surface a trunk still counts as standing on it. */
    private static final int WOOD_PROBE_DEPTH = 6;

    @Override
    public boolean standsInWater(SimPos plot, int radius) {
        if (level.isLoaded(toBlockPos(plot))) {
            return standsInOpenWater(plot, radius);
        }
        int sea = level.getSeaLevel();
        for (int[] at : new int[][] {
                {0, 0}, {-radius, -radius}, {radius, -radius},
                {-radius, radius}, {radius, radius}}) {
            int x = plot.x() + at[0];
            int z = plot.z() + at[1];
            if (oracle.isWet(x, z) || oracle.height(x, z) < sea) {
                return true;
            }
        }
        return false;
    }

    /**
     * The same judgement as {@link #isSiteSuitable}, scored rather than vetoed.
     *
     * <p>Zero exactly where that returns true, which is what makes the two safe
     * to use together: the settlement asks this once per candidate and reads a
     * zero as the veto passing. Above zero it is a quantity — courses of fall
     * beyond what the builders will cut, plus what a plot under standing water
     * is worth — so a town that has refused every candidate can still say which
     * one it disliked least, instead of walking off to unexamined ground.
     *
     * <p>Costs what the veto costs, plot for plot, which it has to: the
     * settlement asks this of every candidate it weighs, and there are
     * ninety-six of those for one building.
     */
    @Override
    public int siteFault(SimPos plot, int radius) {
        BlockPos at = toBlockPos(plot);
        if (!level.isLoaded(at)) {
            return generatorFault(plot, radius);
        }
        if (standsInOpenWater(plot, radius)) {
            return SITE_FAULT_OPEN_WATER;
        }
        int fault = holdsStandingFluid(plot, Math.max(radius, WATER_REACH))
                ? FLOODED_GROUND : 0;
        return fault + slopeFault(plot, radius);
    }

    /**
     * What standing water in a plot is worth, in courses.
     *
     * <p>Flat rather than proportioned to how much of the plot is under it, and
     * that is a cost decision written down rather than a shrug. Counting the wet
     * columns means walking every column of the reach — about eight hundred and
     * forty, each of them a heightmap read, a walk down to the bed and a fluid
     * scan — where {@link #holdsStandingFluid} stops at the first one it finds.
     * This is asked of up to ninety-six candidates for one building, in one
     * tick, and the last thing here that sampled without counting the cost
     * stalled a tick for sixty seconds and had the watchdog kill the server.
     * With a flat charge, scoring a plot costs exactly what vetoing it costs.
     *
     * <p>Thirty-two: above any slope real ground produces, because the two
     * faults are not alike — a builder cuts a shelf into a hillside and lives
     * with it, and a floor cut level with a pond is a flooded room. Below
     * {@link WorldBridge#SITE_FAULT_OPEN_WATER}, which is not a quantity at all:
     * a pond at the edge of a plot is poor ground, a river is not ground.
     */
    private static final int FLOODED_GROUND = 32;

    /**
     * Judging unread ground by degree, through the same estimate the veto uses.
     *
     * <p>Water is still absolute here. What is <em>not</em> the same is the
     * scale: the estimate refuses at {@link #MAX_SLOPE_UNSEEN} because it is
     * coarse, but a caller ranking one plot against another is comparing this
     * against faults measured past {@link #MAX_SLOPE}, and charging unread
     * ground by the looser allowance would make it read four courses better
     * than identical ground somebody had actually looked at — so a search
     * falling back on the least bad would reliably pick the plot nobody has
     * seen, which is the exact bias the ranking exists to remove. The veto
     * keeps its own allowance; the score is quoted in the other one.
     */
    private int generatorFault(SimPos plot, int radius) {
        int sea = level.getSeaLevel();
        for (int[] at : new int[][] {
                {0, 0}, {-radius, -radius}, {radius, -radius},
                {-radius, radius}, {radius, radius}}) {
            if (oracle.height(plot.x() + at[0], plot.z() + at[1]) < sea) {
                return SITE_FAULT_OPEN_WATER;
            }
        }
        int fall = oracle.bulkFall(plot.x(), plot.z(), radius, TerrainOracle.GRAIN);
        if (fall <= MAX_SLOPE_UNSEEN) {
            return SITE_FAULT_NONE;
        }
        return fall - MAX_SLOPE;
    }

    @Override
    public boolean isSiteSuitable(SimPos plot, int radius) {
        BlockPos centre = toBlockPos(plot);
        if (!level.isLoaded(centre)) {
            // Not "nothing to judge on", which is what this used to say before
            // returning true and letting everything through. A town grows mostly
            // out of sight, so that made the terrain test a no-op for most of
            // every town ever built: past the loaded edge nothing was ever
            // refused, and the layout ran unfiltered into lakes and cliffs until
            // somebody walked out there.
            //
            // The generator knows the shape of ground it has not built yet. It
            // is the same question vanilla asks when it decides where a village
            // may go, and it costs no chunk loads.
            return generatorThinksItBuildable(plot, radius);
        }
        // Water first and on its own terms: it is judged across the whole span a
        // building could occupy, where slope is only judged across the plot the
        // caller named. A town that builds in a lake looks like a bug even when
        // it is working.
        if (holdsStandingFluid(plot, Math.max(radius, WATER_REACH))) {
            return false;
        }
        if (standsInOpenWater(plot, radius)) {
            return false;
        }
        return slopeWithin(plot, radius);
    }

    /**
     * Whether any fluid stands in the ground a building here would take up.
     *
     * <p>Every column, not every second one. Height is a smooth field and samples
     * honestly; water does not, and a step of two stepped clean over inlets a
     * block wide and reported the plot dry.
     *
     * <p>Unloaded columns are still passed over rather than refused. That optimism
     * is load-bearing — an unwatched town has to be able to lay itself out at all
     * — and it is safe because {@code Settlement} asks again the moment the chunk
     * is real and moves the building before a block of it is drawn.
     */
    private boolean holdsStandingFluid(SimPos plot, int reach) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = plot.x() + dx;
                int z = plot.z() + dz;
                if (!level.isLoaded(new BlockPos(x, plot.y(), z))) {
                    continue;
                }
                if (columnHoldsFluid(x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fluid anywhere between the top of a column and the ground under it.
     *
     * <p>One probe at the surface is not enough by itself.
     * {@link BlueprintPlacer#groundLevel} walks down through water to the bed, so a
     * plot in a lake surveys its floor at the bottom of the lake and every block
     * of water above that ends up indoors. A swamp is worse still: the heightmap
     * stops on the tree trunk, which is dry, with the water two blocks beneath it.
     */
    private boolean columnHoldsFluid(int x, int z) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // Open water is settled in a single read, which is worth having: this runs
        // for every column of every candidate plot a settlement considers.
        if (!level.getFluidState(new BlockPos(x, surface - 1, z)).isEmpty()) {
            return true;
        }
        int deepest = Math.max(level.getMinY(),
                BlueprintPlacer.groundLevel(level, x, z) - 1 - WATER_UNDERCUT);
        for (int y = surface - 2; y >= deepest; y--) {
            if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the plot is level enough that a shelf can be cut into it. */
    /**
     * Whether the ground is level enough, allowing for what a builder will fix.
     *
     * <p>It used to take the very highest column against the very lowest and
     * refuse anything over four courses apart. That reads a <em>hole</em> as a
     * <em>cliff</em>: a flat shelf with one rabbit hole, cave mouth or ravine
     * corner clipping it was refused outright, when the thing standing there
     * would have packed the hole with two courses of fill and never noticed.
     * A settlement should not walk past good ground because of a pit it is
     * about to floor over anyway.
     *
     * <p>So the judgement is made on the <em>bulk</em> of the plot rather than
     * on its two most extreme columns, and what falls below is left to
     * {@code BlueprintPlacer.foundation}, which already packs a floor up to its
     * line and is already bounded at {@code FOUNDATION_DEPTH} courses. Anything
     * deeper than the foundation can reach still refuses, because a floor the
     * fill cannot reach is a floor with a hole under it.
     */
    /**
     * Whether this plot stands in a river or the sea, which is never allowed.
     *
     * <p>Separate from {@link #holdsStandingFluid} and stricter, because the two
     * refuse different things for different reasons. That one keeps a floor from
     * being cut level with a pond it would then flood from; this one is about
     * what a town looks like. A building standing in open water at sea level
     * reads as broken however sound its foundation is, and there is always
     * better ground within a plot or two — the settlement has ninety-six
     * candidates and only needs one.
     *
     * <p>Sea level is the test rather than "any fluid" on purpose. A mountain
     * tarn or a small pool above sea level is dealt with by the flooding rule
     * above; it is the river and the ocean that make a town look like it drowned.
     */
    private boolean standsInOpenWater(SimPos plot, int radius) {
        int sea = level.getSeaLevel();
        for (int dx = -radius; dx <= radius; dx += SAMPLE_STEP) {
            for (int dz = -radius; dz <= radius; dz += SAMPLE_STEP) {
                BlockPos at = new BlockPos(plot.x() + dx, sea, plot.z() + dz);
                if (!level.isLoaded(at)) {
                    continue;
                }
                if (!level.getFluidState(at).isEmpty()
                        || !level.getFluidState(at.below()).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean slopeWithin(SimPos plot, int radius) {
        return slopeFault(plot, radius) == SITE_FAULT_NONE;
    }

    /** How many courses past the allowance this ground falls, pit included. */
    private int slopeFault(SimPos plot, int radius) {
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += SAMPLE_STEP) {
            for (int dz = -radius; dz <= radius; dz += SAMPLE_STEP) {
                int x = plot.x() + dx;
                int z = plot.z() + dz;
                if (!level.isLoaded(new BlockPos(x, plot.y(), z))) {
                    continue;
                }
                heights.add(level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
        }
        if (heights.isEmpty()) {
            return SITE_FAULT_NONE;   // the whole plot was unloaded
        }
        java.util.Collections.sort(heights);
        int low = heights.get(heights.size() / 5);              // a fifth from the bottom
        int high = heights.get((heights.size() * 4) / 5);       // a fifth from the top
        // The plot's own fall — a slope, as against a pit — and then the
        // outliers, since a pit is welcome only as deep as a foundation goes.
        // Added rather than taken one at a time: a plot that is both is worse
        // than one that is either, and the caller is comparing them.
        return Math.max(0, high - low - MAX_SLOPE)
                + Math.max(0, low - heights.get(0) - FILLABLE_DEPTH);
    }

    /**
     * How deep a hole may be and still count as ground worth building on.
     *
     * <p>Matched to {@code BlueprintPlacer.FOUNDATION_DEPTH}, because that is
     * literally how many courses the builders will lay to reach the floor line.
     * Refusing shallower than they can fill wastes good ground; accepting deeper
     * than they can fill leaves a hole under a floor.
     */
    private static final int FILLABLE_DEPTH = 3;

    @Override
    public void log(String message) {
        KingdomsMod.LOGGER.info(message);
    }

    /**
     * Counts the solid blocks standing inside a building's footprint.
     *
     * <p>Air, fluids and plants do not count; everything else does. The figure
     * is only ever compared against an earlier figure for the same building, so
     * what matters is that the rule is the same both times, not that it agrees
     * with anybody's idea of what a wall is made of.
     *
     * <p>Counted from the floor up through the building's own height, across its
     * measured width and depth. A negative answer means the chunk is not loaded
     * and the question cannot be answered — which is not the same as an answer
     * of zero, and {@code RepairPlanner} is careful about the difference.
     */
    @Override
    public int solidBlocksIn(SimPos origin, Footprint plot) {
        if (!plot.isKnown()) {
            return -1;
        }
        BlockPos at = new BlockPos(origin.x(), plot.y(), origin.z());
        if (!level.isLoaded(at)) {
            return -1;
        }
        int halfWidth = Math.max(0, plot.width() / 2);
        int halfDepth = Math.max(0, plot.depth() / 2);
        int standing = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfDepth; dz <= halfDepth; dz++) {
                for (int dy = 0; dy < Math.max(1, plot.height()); dy++) {
                    cursor.set(origin.x() + dx, plot.y() + dy, origin.z() + dz);
                    if (!level.isLoaded(cursor)) {
                        return -1;   // half a count is worse than no count
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        standing++;
                    }
                }
            }
        }
        return standing;
    }

    /** How far a settler notices something wrong. */
    private static final double CITIZEN_SIGHT = 24.0;

    /** How far above and below the claim a hostile is worth even considering. */
    private static final int THREAT_REACH_Y = 32;

    /**
     * Hostiles the town's own people can see.
     *
     * <p>Line of sight from a living citizen, within {@link #CITIZEN_SIGHT}. The
     * vertical reach is still generous, but it no longer matters much: a mob in
     * a cave fails the sight test against every settler standing on the ground
     * above it, which is the whole point. What is left is what somebody could
     * point at.
     *
     * <p>Weighted by {@link Menace}, so what comes back is an opinion about
     * danger rather than a head count — four zombies and four creepers read very
     * differently to a town, and now they do here too.
     *
     * <p>Who counts is {@link Menace#inSight} and nothing else, which is why this
     * collects every {@code Mob} and lets the table do the whittling. A town is
     * now wary of phantoms at night and of slimes in a swamp, which it never was,
     * and it is no longer permanently wary of an enderman standing in a field,
     * which it always was.
     *
     * <p>A town with nobody embodied sees nothing, and says so. That is correct
     * rather than a gap — with no citizens loaded there is nobody to be
     * frightened, and the abstract half of the simulation has its own raids.
     */
    @Override
    public Sighting hostilesSeen(SimPos centre, double radius) {
        if (!level.isLoaded(toBlockPos(centre))) {
            return Sighting.NONE;
        }
        AABB box = new AABB(
                centre.x() - radius, centre.y() - THREAT_REACH_Y, centre.z() - radius,
                centre.x() + radius, centre.y() + THREAT_REACH_Y, centre.z() + radius);
        List<PersonEntity> citizens =
                level.getEntitiesOfClass(PersonEntity.class, box, LivingEntity::isAlive);
        if (citizens.isEmpty()) {
            return Sighting.NONE;
        }
        int seen = 0;
        int danger = 0;
        // Every Mob, whittled by what the town thinks of it, rather than every
        // Monster. Monster is a PathfinderMob, so the old collection could not
        // reach a ghast, a phantom, a slime, the dragon or a modded boss in
        // vanilla's own boss shape, however carefully the table graded them.
        // Cows are refused by the table rather than by the collection, and they
        // are refused before anybody looks for them: grading is a chain of class
        // tests and line of sight is a ray through the world, so the cheap
        // question goes first even though it now gets asked about every sheep in
        // the claim.
        for (Mob creature : level.getEntitiesOfClass(Mob.class, box, LivingEntity::isAlive)) {
            int worth = Menace.inSight(creature);
            if (worth <= Danger.NONE) {
                continue;
            }
            for (PersonEntity citizen : citizens) {
                if (citizen.distanceToSqr(creature) <= CITIZEN_SIGHT * CITIZEN_SIGHT
                        && citizen.hasLineOfSight(creature)) {
                    seen++;
                    danger += worth;
                    break;   // one witness is enough; a mob is not scarier for being seen twice
                }
            }
        }
        return new Sighting(danger, seen);
    }

    /**
     * The observed half of a raid: real zombies in a ring at the edge of town,
     * nudged toward the centre so vanilla targeting (zombies already hunt
     * villagers) takes over. Entity combat decides everything from here — every
     * villager death flows through the normal view-death path.
     */
    @Override
    public void spawnHostiles(int count, SimPos around) {
        if (!level.isLoaded(toBlockPos(around))) {
            return;
        }
        double distance = 32.0;
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            int x = around.x() + (int) Math.round(distance * Math.cos(angle));
            int z = around.z() + (int) Math.round(distance * Math.sin(angle));
            SimPos ringPos = new SimPos(x, around.y(), z);
            if (!level.isLoaded(toBlockPos(ringPos))) {
                continue;
            }
            Zombie zombie = new Zombie(level);
            zombie.setPos(x + 0.5, surfaceHeight(ringPos), z + 0.5);
            // Deliberately NOT persistence-required: raiders that outlive the raid
            // despawn like any mob. Persistent raiders once accumulated across
            // sessions into a permanent roaming horde.
            if (level.addFreshEntity(zombie)) {
                zombie.getNavigation().moveTo(around.x() + 0.5, around.y(), around.z() + 0.5, 1.0);
                spawned++;
            }
        }
        KingdomsMod.LOGGER.info("Raid: {} hostiles spawned around {}", spawned, around);
    }

    /**
     * Judging ground nobody has loaded, through the terrain oracle.
     *
     * <p>Was five hand-rolled noise samples here. The oracle does the same thing
     * properly: it remembers what it has already read, it knows water exactly
     * rather than by comparison with sea level, and it upgrades a reading to the
     * real chunk the moment there is one. See {@link TerrainOracle}.
     */
    private boolean generatorThinksItBuildable(SimPos plot, int radius) {
        // Nine samples for the water and nine for the fall, on the corners and
        // edges of the plot. The first version took about a hundred, and since
        // siting weighs up to ninety-six candidates for one building that came
        // to ten thousand noise columns a decision -- which stalled a tick for
        // sixty seconds and had the watchdog kill the server. Nine finds a lake
        // or a cliff; a hundred finds the same lake and the same cliff.
        // Sample AT the oracle's own grain. Nine samples thirteen blocks apart
        // stepped straight over lakes -- twenty-two of a hundred and thirteen
        // buildings went into water, against none for the cruder check this
        // replaced. Finer sampling is not the cost it looks: the oracle
        // remembers on a four-block grid, so the first candidate in an area pays
        // for the readings and every candidate after it is answered from memory.
        // Sea level, and only sea level, for the water. Measured on one seed at
        // equal population and equal spread: this rule alone left NONE of
        // ninety-three judged plots in water; the exact two-heightmap test alone
        // left eight of seventy-five; and -- the part worth remembering --
        // requiring BOTH left fourteen of ninety-eight, which is worse than
        // either. Refusing more cannot put more houses in lakes on its own, so
        // something downstream is taking over when the search runs out, and it
        // is: Settlement.chooseSite, having examined every candidate and refused
        // them all, takes the next slot UNEXAMINED. A stricter test therefore
        // buys more blind placements. Until that is fixed, the least strict
        // adequate rule is the safest one.
        //
        // The exact test is kept in the oracle and is the better instrument; it
        // is simply blind to lakes placed as world features, which arrive after
        // the stage the generator answers from, and no sampling fixes that.
        int sea = level.getSeaLevel();
        for (int[] at : new int[][] {
                {0, 0}, {-radius, -radius}, {radius, -radius},
                {-radius, radius}, {radius, radius}}) {
            if (oracle.height(plot.x() + at[0], plot.z() + at[1]) < sea) {
                return false;   // a river or the sea; never, whatever else is true
            }
        }
        // A hole is not a cliff here either. The worst-step reading refuses a
        // shelf for one pit the builders would floor over, so the estimate reads
        // the bulk of the plot and leaves the rest to the foundation.
        return oracle.bulkFall(plot.x(), plot.z(), radius, TerrainOracle.GRAIN)
                <= MAX_SLOPE_UNSEEN;
    }

    /** What the ground is, wherever it is asked about. */
    private final TerrainOracle oracle;

    /** The oracle this bridge reads, so a survey can ask it too. */
    public TerrainOracle oracle() {
        return oracle;
    }

    /**
     * Slope allowed on ground judged from noise alone.
     *
     * <p>Looser than {@link #MAX_SLOPE}, deliberately. The estimate is coarse
     * and refusing on it is cheap to get wrong in the expensive direction: a
     * settlement that can find no site at all stops examining and takes the next
     * slot unseen, which is how it ended up in a lake in the first place. This
     * is meant to turn away cliffs and open water, not to grade a building
     * plot.
     */
    private static final int MAX_SLOPE_UNSEEN = 8;
}
