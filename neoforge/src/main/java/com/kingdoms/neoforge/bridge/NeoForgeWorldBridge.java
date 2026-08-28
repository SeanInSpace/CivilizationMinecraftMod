package com.kingdoms.neoforge.bridge;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.neoforge.world.TerrainOracle;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import net.minecraft.world.level.block.state.BlockState;
import com.kingdoms.sim.platform.Sighting;
import com.kingdoms.sim.platform.WorldBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
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
    private boolean slopeWithin(SimPos plot, int radius) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += SAMPLE_STEP) {
            for (int dz = -radius; dz <= radius; dz += SAMPLE_STEP) {
                int x = plot.x() + dx;
                int z = plot.z() + dz;
                BlockPos column = new BlockPos(x, plot.y(), z);
                if (!level.isLoaded(column)) {
                    continue;
                }
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                lowest = Math.min(lowest, surface);
                highest = Math.max(highest, surface);
            }
        }
        if (lowest == Integer.MAX_VALUE) {
            return true;   // the whole plot was unloaded
        }
        return highest - lowest <= MAX_SLOPE;
    }

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
        for (Monster hostile : level.getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive)) {
            for (PersonEntity citizen : citizens) {
                if (citizen.distanceToSqr(hostile) <= CITIZEN_SIGHT * CITIZEN_SIGHT
                        && citizen.hasLineOfSight(hostile)) {
                    seen++;
                    danger += Menace.of(hostile);
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
        int wide = Math.max(radius, WATER_REACH);
        if (oracle.anyWet(plot.x(), plot.z(), wide, TerrainOracle.GRAIN)) {
            return false;
        }
        return oracle.roughness(plot.x(), plot.z(), radius, TerrainOracle.GRAIN)
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
