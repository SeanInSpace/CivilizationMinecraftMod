package com.kingdoms.neoforge.world;

import com.keystone.api.Blueprints;
import com.keystone.api.LoadedBlueprint;
import com.keystone.api.PlannedBlock;
import com.kingdoms.neoforge.KingdomsBlocks;
import com.kingdoms.neoforge.KingdomsConfig;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.block.BuildingPostBlock;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.world.SimWorld;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.BuildPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a blueprint id into actual blocks — all at once, or brick by brick.
 *
 * <p>Every structure is expressed as an ordered <em>plan</em>: a list of block
 * placements sorted the way a mason would work — <strong>bottom layer first;
 * within each layer, full blocks before partial blocks</strong> (lanterns,
 * fences, crops, water); never more than one layer under way, and the next layer
 * only once the one below is satisfied. Supplies are assumed for now — the sort
 * order is exactly where a supply gate slots in later.
 *
 * <p>Plans come from one of two places, in this order:
 * <ol>
 *   <li><strong>Keystone blueprints</strong> — a file authored in-game or shipped
 *       in a datapack. These carry real block states, so a blueprint's stairs,
 *       doors and fences arrive facing the way they were drawn.</li>
 *   <li><strong>Procedural shapes</strong> — the built-in fallback, so a fresh
 *       install with no blueprint files still builds a whole village.</li>
 * </ol>
 *
 * <p>Both paths produce the same ordered plan and both build course by course.
 * That is the point: an authored building is no longer stamped into existence
 * whole while only the generated ones get to be built by hand.
 *
 * <p>Two ways to consume a plan:
 * <ul>
 *   <li>{@link #place} — the whole structure at once. Used when a finished
 *       building materializes in a freshly loaded chunk ("it grew while you were
 *       away"), and as the idempotent finishing pass that guarantees a completed
 *       building is whole regardless of how construction went.</li>
 *   <li>{@link #nextBlock} / {@link #placeNextBlock} — the visible path: lays the
 *       plan in proportion to the build task's progress, so watchers see the
 *       structure rise while the builders stand at the site.</li>
 * </ul>
 */
public final class BlueprintPlacer {

    /** One block placement in a plan. */
    record Placement(BlockPos pos, BlockState state, CompoundTag nbt) {
    }

    /**
     * One block a builder puts down.
     *
     * <p>Masonry only. Excavation is no longer part of this list: taking ground
     * out is a job several people do at once, in parallel, at real break speed,
     * and a single ordered queue could express none of that. See {@link Excavation}.
     */
    private record Step(BlockPos pos, BlockState state, CompoundTag nbt,
                        int cost, String material) {
    }

    /** What a builder should be doing right now, and what they need in hand for it. */
    public record NextStep(BlockPos pos, int cost) {
    }

    /** A build that cannot go on because the town has run out of something. */
    public record Shortage(String resource, int needed) {
    }

    /**
     * A structure as an ordered sequence of digs and placements.
     *
     * <p>{@code blocked} means something in the footprint cannot be shifted at all
     * — bedrock, in practice. Obsidian is merely slow and does not count.
     */
    private record StructurePlan(int width, int depth, int height, List<Step> steps,
                                 List<BlockPos> digTargets, boolean blocked) {

        int placeWork() {
            int total = 0;
            for (Step step : steps) {
                total += step.cost();
            }
            return total;
        }

        /**
         * Ground and masonry together, for reporting how far along a build is.
         *
         * <p>One unit per block either way. Digging used to be weighted by how
         * hard the block was, which double-charged it: a stone block both took
         * longer to break and ate more of the build budget. Time is now measured
         * in ticks by {@link Excavation} and progress is measured in blocks here,
         * and neither pretends to be the other.
         */
        int totalWork() {
            return digTargets.size() + placeWork();
        }
    }

    /** How far above a doomed block to look for somewhere its occupant can stand. */
    private static final int EVICT_SEARCH_HEIGHT = 8;

    /** Work units to lay one block. Everything else is measured against this. */
    private static final int PLACE_COST = 1;

    /**
     * How far past the walls a building's plot reaches: one block of doorstep.
     *
     * <p>It was two, and two blocks of ground taken on every side of every
     * building is what a player called unnatural — a village of huts each
     * standing in the middle of its own scraped pad, held that far apart from
     * each other because the recorded plot is what keeps the next building off.
     *
     * <p>One is the least that still works. The doorway wants somewhere to stand
     * immediately outside the wall, and that ring is met from both sides now:
     * {@link #foundation} packs it up to the floor line where the ground falls
     * short, and the apron cut below takes it back down where it stands proud.
     * Zero would leave the door opening onto whatever the hillside happened to be.
     */
    public static final int APRON_MARGIN = 1;

    /** Headroom cleared over the apron — enough to walk the whole way round. */
    private static final int APRON_HEADROOM = 3;

    /**
     * How many courses of cobble may be laid under a floor to reach the ground.
     *
     * <p>A real cost rather than free levelling: each course is masonry somebody
     * lays and stone the town pays for, which is what keeps "build it up" from
     * being the cheap answer to every slope. It also bounds the survey — a floor
     * is never chosen higher than this above the lowest column of its own plot,
     * because a floor the fill cannot reach is a floor with a hole under it.
     */
    static final int FOUNDATION_DEPTH = 3;

    /**
     * Which part of a plot counts as its low ground, for the foundation cap.
     *
     * <p>A fifth from the bottom, so a handful of freak columns — a cave mouth,
     * a ravine clipping one corner, a rabbit hole — cannot pull a whole building
     * down after them. The absolute minimum was the obvious thing to measure and
     * was wrong for the same reason the mean was wrong for the median: one
     * unlucky column should not decide where a building sits.
     *
     * <p>The cost is that a plot genuinely straddling a cliff can now be perched
     * rather than sunk. That is the better failure — it is visible, the auditor
     * reports it as "perched", and a plot like that should have been refused by
     * the site check before it ever got here.
     */
    static final int LOW_GROUND_QUANTILE = 5;

    private BlueprintPlacer() {
    }

    // --- the instant path ---

    /** Places a whole structure and reports where it went and how big it is. */
    public static Footprint place(ServerLevel level, String blueprintId, BlockPos base,
                                 int facing) {
        StructurePlan plan = planFor(level, blueprintId, base, facing);
        for (BlockPos dig : plan.digTargets()) {
            if (!level.getBlockState(dig).isAir()) {
                // The plant on top goes first, silently, or it pops off as an
                // item the moment its support vanishes under it.
                BlockPos above = dig.above();
                BlockState overhead = level.getBlockState(above);
                if (!overhead.isAir() && overhead.canBeReplaced()) {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
                level.destroyBlock(dig, false, null, 512);
            }
        }
        for (Step step : plan.steps()) {
            lay(level, new Placement(step.pos(), step.state(), step.nbt()));
        }
        return plotOf(base.getY(), plan);
    }

    /**
     * Measures a structure without placing anything.
     *
     * <p>For buildings that were raised before their size was recorded: the plan
     * is rebuilt from the same blueprint at the same spot, and only its bounds
     * are taken. Nothing is written to the world.
     */
    public static Footprint measure(ServerLevel level, String blueprintId, BlockPos base) {
        if (!level.isLoaded(base)) {
            return Footprint.UNKNOWN;
        }
        StructurePlan plan = planFor(level, blueprintId, base, 0);
        return plotOf(base.getY(), plan);
    }

    /**
     * The plot a structure occupies: the building plus the ground cleared around it.
     *
     * <p>Wider than the walls on purpose. The cleared shelf is part of what the
     * town has taken for this building — it is where its door opens onto and where
     * nothing else may be planted — so the map and the lamp draw the plot, not
     * just the roof.
     *
     * <p>Deliberately not the same as the excavation box: that stays the building's
     * own size, because the margin is only cleared where something is actually in
     * the way. Reporting a wider plot must not widen what gets dug.
     *
     * <p>Footprints are saved as they were measured and never migrated, so a town
     * from before the margin shrank keeps its old wider plots. Nothing reads them
     * wrongly — everything that wants the walls back takes {@link #APRON_MARGIN}
     * off the recorded span — an old town simply stays as spread out as it was
     * built, and only new buildings are packed at the new spacing.
     */
    private static Footprint plotOf(int y, StructurePlan plan) {
        return new Footprint(y,
                plan.width() + 2 * APRON_MARGIN,
                plan.depth() + 2 * APRON_MARGIN,
                plan.height());
    }

    /** How far down a column may be stripped of growth before we call it ground. */
    private static final int OVERBURDEN_SEARCH = 32;

    /**
     * The first free block above actual ground in this column.
     *
     * <p>Not the raw heightmap. {@code MOTION_BLOCKING_NO_LEAVES} counts a tree
     * trunk as the surface, so a plot with an oak standing on it surveyed its
     * floor at the top of the tree — the building was pitched into the branches
     * and the excavation started somewhere no one could stand, in mid-air, with
     * the real ground six blocks below and never touched.
     *
     * <p>Growth is walked through instead: logs, leaves, and anything a block can
     * simply be placed into. What is left underneath is what the town builds on.
     *
     * <p>Water is the one thing the walk stops at rather than passes through. It
     * is replaceable, so the descent used to carry on down to the bed of the pond
     * — and a plot with its toe in the water surveyed its floor several blocks
     * under the surface, dug a bathtub, and filled it with the lake. The grade of
     * a flooded column is the surface, because that is where a builder would put
     * the fill in; {@link #foundation} then displaces the water with it.
     */
    public static int groundLevel(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int floor = level.getMinY() + 1;
        for (int stripped = 0; stripped < OVERBURDEN_SEARCH && y > floor; stripped++) {
            BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
            if (!isOverburden(below)) {
                break;
            }
            y--;
        }
        return y;
    }

    /** Growth and loose cover, as opposed to the ground a building sits on. */
    private static boolean isOverburden(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            // Water and lava are replaceable and would otherwise be walked
            // straight through. They are not cover over the ground, they are the
            // level the ground has to be brought up to.
            return false;
        }
        // Growth and loose cover only. Air is deliberately not on this list: the
        // heightmap has already put us on the first free block, so anything air
        // below that is a cave or an overhang, and walking down into one sinks the
        // building into the hillside for no reason at all.
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.canBeReplaced();
    }

    /**
     * Where a structure's base sits, given the first air block in its column.
     *
     * <p>Almost everything uses {@link #floorFor}: the floor course replaces the
     * topsoil so the door opens at grade. A crop field is the one exception —
     * its ground layer (farmland, irrigation) is drawn one BELOW its base, so
     * its base belongs at the first air block, putting the farmland exactly
     * where the natural surface block was, the way a player tills the ground.
     *
     * <p>Getting this wrong sank every farm a block into the earth. Worse than
     * cosmetic: sunk one block, the crops sit level with the surrounding grade,
     * which is exactly where any pond beside the plot holds its water — so
     * fields flooded from the rim and the crops washed off their soil as a
     * scatter of seed items, over and over, while the farmland underneath
     * stayed perfectly intact.
     */
    public static int baseFor(String blueprintId, int firstAirY) {
        return isField(blueprintId) ? firstAirY : floorFor(firstAirY);
    }

    /**
     * The culture of the settlement whose claim this ground falls in.
     *
     * <p>Resolved here rather than passed down, because the whole placement
     * chain from {@code materializeBlueprint} takes a position and no
     * settlement — and widening that seam to carry a culture would change an
     * interface that six test doubles implement, to tell it something it can
     * work out from where it is standing.
     */
    private static Culture cultureAt(ServerLevel level, BlockPos base) {
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            return Culture.DEFAULT;
        }
        SimPos at = new SimPos(base.getX(), base.getY(), base.getZ());
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                if (settlement.contains(at)) {
                    return Culture.of(settlement.cultureId());
                }
            }
        }
        return Culture.DEFAULT;
    }

    /** A crop field, whatever its level or style. The animal farm is not one. */
    private static boolean isField(String blueprintId) {
        String path = Identifier.parse(BuildPlanner.baseIdOf(blueprintId)).getPath();
        return path.equals("farm") || path.endsWith("/farm");
    }

    /** Where a structure floor sits, given the first air block in that column. */
    public static int floorFor(int firstAirY) {
        // One below, so the floor course replaces the top of the soil instead of
        // sitting on it. Otherwise the building stands a block proud of the ground
        // and its doorway opens at chest height, which is no doorway at all.
        return firstAirY - 1;
    }

    /**
     * The floor height for a plot, from the first free block in each of its columns.
     *
     * <p>Pure arithmetic, and deliberately the whole of the decision: everything
     * about how a building meets sloping ground is these three lines, so they can
     * be read and argued with without a world to run them in — and, since the
     * module grew a test source set, actually pinned. See
     * {@code BlueprintPlacerFloorTest}, which exists because this javadoc used
     * to end by regretting that it could not.
     *
     * <p>The median rather than the lowest. Taking the lowest — which is what
     * surveying one arbitrary column amounted to, whenever that column happened to
     * be the low one — meant the entire plot was cut down to meet it, and every
     * building on anything but a billiard table ended up sitting in a squared-off
     * pit of its own making. The median cuts half the plot down and packs the other
     * half up, which is how a real building sits on a slope. The median rather than
     * the mean because one boulder or one rabbit hole must not drag the floor with it.
     *
     * <p>Then held down to what the underpinning can reach: see
     * {@link #FOUNDATION_DEPTH}. That cap used to measure from the single
     * lowest column, which quietly undid the median it had just been at pains
     * to compute — one rabbit hole or one cave mouth in a corner of the plot
     * dragged the entire building down to within three blocks of it. A house
     * sunk eleven blocks into a hillside has its doorway underground, which is
     * precisely what "no doorway at grade on any side" looks like from the
     * audit. The cap now measures from the low ground rather than the lowest
     * point: see {@link #LOW_GROUND_QUANTILE}.
     */
    static int baseAcross(String blueprintId, int[] firstAir) {
        int[] sorted = firstAir.clone();
        Arrays.sort(sorted);
        int low = sorted[Math.min(sorted.length - 1, sorted.length / LOW_GROUND_QUANTILE)];
        return Math.min(baseFor(blueprintId, sorted[sorted.length / 2]),
                low + FOUNDATION_DEPTH);
    }

    // --- the visible path ---

    /**
     * Surveys the terrain once and records what the job is worth.
     *
     * <p>The site is no longer cleared here. Ground standing in the way is part of
     * the plan now, dug out block by block by somebody holding the right tool.
     *
     * @return true if this call changed anything worth saving
     */
    public static boolean prepareSite(ServerLevel level, BuildTask task) {
        if (!isBuildableByHand(level, task)) {
            return false;
        }
        boolean changed = false;
        if (task.siteY() == BuildTask.UNSET_SITE_Y) {
            // Two jobs already know the height they belong at and must not go
            // looking for another.
            //
            // A flight of steps starts at the doorway it serves, full stop.
            // Surveying the column instead returns the top of the house that
            // doorway is set into, so the steps got built across the roof and
            // buried the door.
            //
            // An improvement is raised in place, on the floor the old building
            // already stands on. Surveying that column finds the roof of the very
            // building being replaced, so the new one was pitched a storey up and
            // built on top of the old — and the excavation, measured from up there,
            // was digging air.
            //
            // Everything else is set to the grade of its own plot, so you can walk
            // in through it.
            boolean inPlace = isStairs(task) || task.isUpgrade();
            task.setSiteY(inPlace ? task.origin().y() : surveyBase(level, task));
            changed = true;
        }
        StructurePlan plan = planOf(level, task);
        if (plan == null) {
            return changed;
        }
        if (task.planWork() != plan.totalWork()) {
            task.setPlan(plan.totalWork(), plan.placeWork());
            changed = true;
        }
        // The size is known the moment the plan is, and the finished building
        // keeps it — that is what lets anything draw a building's bounds.
        Footprint measured = plotOf(task.siteY(), plan);
        if (!measured.equals(task.footprint())) {
            task.setFootprint(measured);
            changed = true;
        }
        if (!task.isSitePrepared()) {
            // The site announces itself the moment it is surveyed: the post
            // stands at its final spot while the ground is still being cut.
            layPosts(level, plan);
            task.setSitePrepared(true);
            changed = true;
        }
        return changed;
    }

    /**
     * Reads the lie of the land across a whole plot and picks the floor for it.
     *
     * <p>Only the origin column used to be looked at, and the site height is
     * write-once, so whatever that one column happened to be became the floor for
     * the whole building — and everything else in the footprint was then cut down
     * to meet it. That is the two-tile shelf of scraped ground, seen side on.
     *
     * <p>The plot is laid out once here purely to learn how wide the building is,
     * and thrown away. Nothing knows a blueprint's size until it has been laid
     * out, and the survey has to know before it can choose a height — so this is
     * one extra layout per building, at the moment its site opens, and never again.
     * The dimensions do not depend on the base, so a provisional one will do.
     */
    private static int surveyBase(ServerLevel level, BuildTask task) {
        int x = task.origin().x();
        int z = task.origin().z();
        StructurePlan shape = planFor(level, task.blueprintId(),
                new BlockPos(x, groundLevel(level, x, z), z));
        // A square of the wider half-span. Buildings are turned to face the town
        // centre, which swaps width and depth, and a survey that sampled the
        // rectangle as drawn would read different ground on opposite sides of town.
        int half = Math.max(shape.width(), shape.depth()) / 2;

        int[] columns = new int[(2 * half + 1) * (2 * half + 1)];
        // The origin is the one column known to be loaded — that is what
        // isBuildableByHand asked — so it is read outright and the survey can
        // never come back with nothing to take a median of.
        columns[0] = groundLevel(level, x, z);
        int read = 1;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                // A plot may straddle a chunk nobody has loaded. Ask first:
                // reading the heightmap of an absent chunk is what drags one in.
                if (!level.isLoaded(new BlockPos(x + dx, task.origin().y(), z + dz))) {
                    continue;
                }
                columns[read++] = groundLevel(level, x + dx, z + dz);
            }
        }
        return baseAcross(task.blueprintId(), Arrays.copyOf(columns, read));
    }

    /**
     * What the builders should be doing now, or null when they have done
     * everything this step cleared them for.
     *
     * <p>The gate is the granted work budget, not a fraction of a separate clock.
     * Progress IS the digging and the masonry, so no cursor runs ahead of the
     * simulation and no remainder is left for a completion pass to stamp in.
     */
    public static NextStep nextStep(ServerLevel level, Settlement settlement, BuildTask task) {
        Step step = currentStep(level, task);
        if (step == null || !task.canAfford(step.cost()) || !canPayFor(settlement, task, step)) {
            return null;
        }
        return new NextStep(step.pos(), step.cost());
    }

    /**
     * What the town has run out of, if that is what has stopped this build.
     *
     * <p>Null when the build is simply not due more work yet — only a genuine
     * shortage is reported, so the caller can tell "waiting" from "stuck".
     */
    public static Shortage shortageFor(ServerLevel level, Settlement settlement, BuildTask task) {
        Step step = currentStep(level, task);
        if (step == null || !task.canAfford(step.cost()) || canPayFor(settlement, task, step)) {
            return null;
        }
        return new Shortage(step.material(), 1);
    }

    /**
     * Whether the town can pay for this step.
     *
     * <p>Digging is free — it costs sweat, not stores. Laying costs one of
     * whatever the block is made of.
     *
     * <p>The producers are exempt on purpose. A lumber camp that needed timber,
     * a mine that needed stone, or a farm that needed food is a town that can
     * never dig itself out of an empty larder. They are the bootstrap, so they
     * are always payable. Whatever {@code BuildPlanner.PRODUCER_OF} holds is what
     * is exempt — the farm joined when food gained a producer, and this comment
     * counted them until it did.
     */
    private static boolean canPayFor(Settlement settlement, BuildTask task, Step step) {
        if (step.material() == null || isProducer(task)) {
            return true;
        }
        return settlement.stores().has(step.material(), 1);
    }

    private static boolean isProducer(BuildTask task) {
        return BuildPlanner.PRODUCER_OF.containsValue(task.blueprintId());
    }

    /** Charges the town for a step that has just been finished. */
    private static void payFor(Settlement settlement, BuildTask task, Step step) {
        if (step.material() == null || isProducer(task)) {
            return;
        }
        settlement.stores().take(step.material(), 1);
    }

    /**
     * What a block is made of, as far as the town's ledger is concerned.
     *
     * <p>Coarse on purpose, and deliberately keyed off the same tags that decide
     * which tool digs a block — if a pickaxe takes it out, stone puts it back.
     * Glass, crops, soil and lanterns cost nothing: a town does not make those,
     * so charging for them would only stall builds for no gain.
     */
    private static String materialFor(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return TownStores.WOOD;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return TownStores.STONE;
        }
        return null;
    }

    /** What the step in front of the builders is made of, or null if it costs nothing. */
    public static String materialOfStep(ServerLevel level, BuildTask task) {
        Step step = currentStep(level, task);
        return step == null ? null : step.material();
    }

    /** The block a builder should be holding for the course in front of them. */
    public static Item toolFor(ServerLevel level, BuildTask task) {
        Step step = currentStep(level, task);
        return step == null ? null : step.state().getBlock().asItem();
    }

    /**
     * One swing at the step in hand. Returns true when the step actually finished.
     *
     * <p>Laying takes a single swing. Excavation does not come through here at
     * all — see {@link Excavation}, which spends real ticks against real block
     * hardness rather than swings against a budget.
     */
    public static boolean swingAtStep(ServerLevel level, Settlement settlement, BuildTask task) {
        return swingAtStep(level, settlement, task, false);
    }

    /**
     * @param carried whether the builder is paying from a load they fetched, in
     *                which case the stores were already debited at the warehouse
     *                and must not be charged a second time here
     */
    public static boolean swingAtStep(ServerLevel level, Settlement settlement, BuildTask task,
                                      boolean carried) {
        Step step = currentStep(level, task);
        if (step == null || !task.canAfford(step.cost())) {
            return false;
        }
        if (!carried && !canPayFor(settlement, task, step)) {
            return false;
        }
        task.addStepProgress();
        if (task.stepProgress() < step.cost()) {
            return false;   // still working at it
        }
        execute(level, step);
        if (!carried) {
            payFor(settlement, task, step);
        }
        task.recordStepDone(step.cost());
        return true;
    }

    /** Finishes the step in hand outright, for paths with no ticks to spend on it. */
    public static boolean completeStep(ServerLevel level, Settlement settlement, BuildTask task) {
        Step step = currentStep(level, task);
        if (step == null || !task.canAfford(step.cost()) || !canPayFor(settlement, task, step)) {
            return false;
        }
        execute(level, step);
        payFor(settlement, task, step);
        task.recordStepDone(step.cost());
        return true;
    }

    private static Step currentStep(ServerLevel level, BuildTask task) {
        StructurePlan plan = planOf(level, task);
        if (plan == null || task.stepsDone() >= plan.steps().size()) {
            return null;
        }
        return plan.steps().get(task.stepsDone());
    }

    /** Carries out one step: the block goes down. */
    private static void execute(ServerLevel level, Step step) {
        lay(level, new Placement(step.pos(), step.state(), step.nbt()));
    }

    /**
     * Whether this is terrain rather than something somebody built.
     *
     * <p>Soil and living rock only. Deliberately excludes wood and worked stone,
     * so cutting a shelf for one building can never take a bite out of the one
     * beside it — and leaves trees standing for the lumberjacks.
     */
    private static boolean isNaturalGround(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.BASE_STONE_OVERWORLD);
    }

    /**
     * Whether this block needs taking out at all.
     *
     * <p>Anything a block can simply be placed into does not: snow, grass, flowers
     * and the like are overwritten by the course that lands on them, so scheduling
     * a dig for one is pure delay. This is why a builder no longer spends time
     * clearing ground that was never in the way.
     */
    private static boolean needsDigging(BlockState state) {
        return !state.isAir() && !state.canBeReplaced();
    }

    /**
     * Puts one placement into the world.
     *
     * <p>Block states are written as authored, without neighbour updates: a
     * blueprint already stores the connected form of every fence, wall and stair,
     * so asking the world to recompute them could only spoil what was drawn — and
     * a door's lower half, laid on its own, would pop straight back off.
     */
    private static void lay(ServerLevel level, Placement placement) {
        // Direct test of a suspected fault: a crop laid before its soil, or soil
        // laid under a crop that is already standing, pops the crop as an item.
        // If either line ever prints, the placement order is broken exactly as
        // suspected; if neither does while seeds still appear, the culprit is
        // elsewhere and this has ruled the order out for good.
        if (KingdomsConfig.debugCommandsEnabled()) {
            if (placement.state().is(BlockTags.CROPS)
                    && !level.getBlockState(placement.pos().below()).is(Blocks.FARMLAND)) {
                KingdomsMod.LOGGER.warn("CROPLAY crop over {} at {}",
                        level.getBlockState(placement.pos().below()).getBlock(),
                        placement.pos().toShortString());
            }
            if (placement.state().is(Blocks.FARMLAND)
                    && level.getBlockState(placement.pos().above()).is(BlockTags.CROPS)) {
                KingdomsMod.LOGGER.warn("CROPLAY farmland laid under a standing crop at {}",
                        placement.pos().toShortString());
            }
        }
        evict(level, placement.pos(), placement.state());
        level.setBlock(placement.pos(), placement.state(), Block.UPDATE_CLIENTS);
        if (placement.nbt() == null) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(placement.pos());
        if (entity != null) {
            entity.loadWithComponents(TagValueInput.create(
                    ProblemReporter.DISCARDING, level.registryAccess(), placement.nbt()));
        }
    }

    /**
     * Every block that has to come out before the first course can be laid.
     *
     * <p>Handed to {@link Excavation}, which owns the order, the timing and the
     * sharing-out. Read once when a site opens: the yard keeps itself in step with
     * the world after that, and blocks that vanish by other means are noticed when
     * somebody is next sent to one.
     */
    public static List<SimPos> excavationTargets(ServerLevel level, BuildTask task) {
        StructurePlan plan = planOf(level, task);
        if (plan == null) {
            return List.of();
        }
        List<SimPos> targets = new ArrayList<>(plan.digTargets().size());
        for (BlockPos pos : plan.digTargets()) {
            targets.add(new SimPos(pos.getX(), pos.getY(), pos.getZ()));
        }
        return targets;
    }

    /**
     * Whether this site has something in it that no builder can shift.
     *
     * <p>Bedrock only. Obsidian is breakable — slowly — and a town is welcome to
     * spend the time on it.
     */
    public static boolean isSiteBlocked(ServerLevel level, BuildTask task) {
        StructurePlan plan = planOf(level, task);
        return plan != null && plan.blocked();
    }

    /**
     * What is still needed to finish this build, block by block.
     *
     * <p>Counted from the steps not yet done, so it shrinks as the walls go up
     * rather than reciting the whole plan forever. Excavation is not in it: taking
     * ground out costs sweat, not stock.
     *
     * <p>Per item rather than per resource on purpose. The town's own economy runs
     * on coarse timber and stone, but a player looking at a bill wants to know it
     * needs forty oak planks and eight panes of glass — and glass is exactly the
     * sort of thing a town cannot make for itself.
     */
    public static Map<Item, Integer> billOfMaterials(ServerLevel level, BuildTask task) {
        StructurePlan plan = planOf(level, task);
        if (plan == null) {
            return Map.of();
        }
        Map<Item, Integer> bill = new LinkedHashMap<>();
        List<Step> steps = plan.steps();
        for (int i = Math.min(task.stepsDone(), steps.size()); i < steps.size(); i++) {
            Item item = steps.get(i).state().getBlock().asItem();
            if (item == Items.AIR) {
                continue;
            }
            bill.merge(item, 1, Integer::sum);
        }
        return bill;
    }

    /** Whether construction can proceed here at all — the chunk has to be loaded. */
    public static boolean isBuildableByHand(ServerLevel level, BuildTask task) {
        BlockPos approx = new BlockPos(task.origin().x(), task.origin().y(), task.origin().z());
        return level.isLoaded(approx);
    }

    /** Quarter turns clockwise into Minecraft's own rotation. */
    private static Rotation rotationOf(int facing) {
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * Turns a gathered plan about its own base.
     *
     * <p>Both halves matter: the positions swing round the origin, and every block
     * state turns with them. Rotating positions alone would leave a house with its
     * stairs and its door facing the way they were drawn while the walls moved.
     */
    private static void turn(List<Placement> blocks, BlockPos base, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            Placement placement = blocks.get(i);
            BlockPos local = placement.pos().subtract(base);
            BlockPos turned = base.offset(rotate(local, rotation));
            blocks.set(i, new Placement(turned,
                    placement.state().rotate(rotation), placement.nbt()));
        }
    }

    /** A position about the origin. Clockwise sends (x, z) to (-z, x). */
    private static BlockPos rotate(BlockPos local, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-local.getZ(), local.getY(), local.getX());
            case CLOCKWISE_180 -> new BlockPos(-local.getX(), local.getY(), -local.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(local.getZ(), local.getY(), -local.getX());
            default -> local;
        };
    }

    /** Repair flights are the one plan that is a path, not a building. */
    private static boolean isStairs(BuildTask task) {
        return Identifier.parse(task.blueprintId()).getPath().endsWith("stairs");
    }

    private static BlockPos baseOf(BuildTask task) {
        return new BlockPos(task.origin().x(), task.siteY(), task.origin().z());
    }

    // Construction is consulted several times a second per builder, and building
    // a plan reads chunk state for the foundation. Keyed by blueprint and site
    // rather than by task, so two settlements building at once do not evict each
    // other's plan on every pass. Server-thread only, so no synchronization.
    private static final int PLAN_CACHE_LIMIT = 8;

    private record PlanKey(String blueprintId, BlockPos base) {
    }

    private static final Map<PlanKey, StructurePlan> PLAN_CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PlanKey, StructurePlan> eldest) {
                    return size() > PLAN_CACHE_LIMIT;
                }
            };

    private static StructurePlan planOf(ServerLevel level, BuildTask task) {
        if (task.siteY() == BuildTask.UNSET_SITE_Y) {
            return null;
        }
        BlockPos base = baseOf(task);
        PlanKey key = new PlanKey(task.blueprintId(), base);
        StructurePlan cached = PLAN_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        StructurePlan plan = planFor(level, task.blueprintId(), base);
        PLAN_CACHE.put(key, plan);
        return plan;
    }

    /** Drops cached plans. Call when blueprint files or datapacks change. */
    public static void clearPlanCache() {
        PLAN_CACHE.clear();
    }

    // --- plans ---

    private static StructurePlan planFor(ServerLevel level, String blueprintId, BlockPos base) {
        return planFor(level, blueprintId, base, 0);
    }

    /**
     * Builds the plan for a structure, turned to face the way it was told to.
     *
     * <p>Authored blueprints are rotated by Keystone as they load, which already
     * handles every block state properly. The built-in shapes are drawn facing
     * south and turned here, which comes to the same thing.
     */
    private static StructurePlan planFor(ServerLevel level, String blueprintId, BlockPos base,
                                         int facing) {
        Identifier id = Identifier.parse(blueprintId);
        Rotation rotation = rotationOf(facing);

        Optional<LoadedBlueprint> authored = Blueprints.loadFirst(level, base,
                styleCandidates(id, cultureAt(level, base)), rotation, Mirror.NONE);
        if (authored.isPresent()) {
            return fromBlueprint(level, authored.get(), base,
                    BuildPlanner.baseIdOf(id.getPath()));
        }
        // Styles degrade too: with no norman/house drawn, a norman town still
        // gets the built-in house rather than an unknown-blueprint marker. So does
        // a level nobody has drawn — it falls back to the plain shape, grown.
        String path = BuildPlanner.baseIdOf(id.getPath());
        int tier = BuildPlanner.levelOf(id.getPath());
        return procedural(level, path.substring(path.lastIndexOf('/') + 1), base, rotation, tier);
    }

    /**
     * The ids to try for a build, most specific first.
     *
     * <p>A styled id like {@code kingdoms:norman/house} falls back to plain
     * {@code kingdoms:house}, so a culture only has to draw the buildings it
     * wants to differ on and inherits the rest.
     *
     * <p><strong>The style is applied here rather than carried in the id.</strong>
     * That was the whole difficulty. Producing styled ids upstream would have
     * meant every comparison of a blueprint id against a catalogue row —
     * {@code type.id().equals(baseId)}, the upgrade lookup, {@code plotSpanOf} —
     * quietly stopping matching, because they all strip a level suffix and none
     * of them strips a culture folder. Composing the path at the last possible
     * moment, from the culture of the town whose ground this is, leaves every
     * one of them untouched: the id stays plain everywhere it is reasoned
     * about, and only the file lookup knows about styles.
     *
     * <p>A blueprint that already names its own folder is left alone. Nothing
     * produces one today, but a datapack asking for a specific style outright
     * should get it rather than have the local culture imposed on top.
     */
    private static List<Identifier> styleCandidates(Identifier id, Culture culture) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            return List.of(id, id.withPath(path.substring(slash + 1)));
        }
        String style = culture.style();
        if (style.isEmpty()) {
            return List.of(id);
        }
        return List.of(id.withPath(style + "/" + path), id);
    }

    /**
     * Turns an authored blueprint into a plan on this site.
     *
     * <p>Blueprints are held from their minimum corner while build plots are
     * points, so the structure is laid so that the cell it names as its anchor
     * lands on the plot. Most authored files say which cell that is — Structurize
     * records it as {@code primary_offset}, usually the hut block — and a file
     * that says nothing is centred on its own footprint, which is what this
     * always did. Honouring the stated one is what stops an imported building
     * sitting beside its plot instead of on it.
     *
     * <p>The blueprint itself has no foundation — nobody draws one — so the same
     * cobble underpinning the procedural shapes get is laid beneath it, which is
     * what stops an authored building floating over a slope.
     */
    private static StructurePlan fromBlueprint(ServerLevel level, LoadedBlueprint blueprint,
                                               BlockPos base, String path) {
        Vec3i size = blueprint.size();
        // The origin that puts the blueprint's own anchor cell on the plot.
        //
        // Its height is deliberately ignored. A stated anchor names a cell in
        // three dimensions — a real file gives (10, 2, 22) in a 32x16x31
        // structure, so its hut block sits two courses up — but a plot is a
        // floor, and shifting the structure down by two to line that block up
        // would bury its bottom two courses in the ground. The building stands
        // on its plot; the anchor decides where on it.
        BlockPos stated = blueprint.anchor();
        BlockPos anchor = base.offset(-stated.getX(), 0, -stated.getZ());

        List<Placement> blocks = new ArrayList<>(blueprint.blockCount() + 32);
        foundation(level, blocks, base, size.getX(), size.getZ());
        Set<BlockPos> filled = new HashSet<>();
        boolean hasPost = false;
        for (PlannedBlock block : blueprint.sequence()) {
            blocks.add(new Placement(block.at(anchor), block.state(), block.nbt()));
            filled.add(block.offset());
            hasPost |= block.state().getBlock() instanceof BuildingPostBlock;
        }
        if (!hasPost) {
            addPost(blocks, anchor, size, filled, path);
        }
        return finish(level, base, blocks, size.getX(), size.getZ(), size.getY());
    }

    /**
     * Gives an authored building the post that names it.
     *
     * <p>Every procedural shape draws its own post, because a building you cannot
     * walk up to and read is a building the player has to guess at. A blueprint
     * drawn by somebody else has no idea our posts exist — and an imported
     * MineColonies hut arrived mute for exactly that reason — so one is added
     * here unless the author already placed theirs.
     *
     * <p>It goes in the first empty cell a course above the floor, searched
     * outward from the middle, so it lands in the room rather than inside a wall.
     * A structure with no interior at all gets it at the centre regardless:
     * better a post in a wall than a building that answers to nobody.
     */
    private static void addPost(List<Placement> blocks, BlockPos anchor, Vec3i size,
                                Set<BlockPos> filled, String path) {
        Block post = postFor(path);
        if (post == null) {
            return;
        }
        int cx = (size.getX() - 1) / 2;
        int cz = (size.getZ() - 1) / 2;
        BlockPos best = new BlockPos(cx, 1, cz);
        long bestDistance = Long.MAX_VALUE;
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                BlockPos candidate = new BlockPos(x, 1, z);
                if (filled.contains(candidate)) {
                    continue;
                }
                long distance = (long) (x - cx) * (x - cx) + (long) (z - cz) * (z - cz);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        add(blocks, anchor.offset(best), post);
    }

    /** The post that belongs to a building, by its blueprint path. */
    private static Block postFor(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return switch (name) {
            case "town_hall" -> KingdomsBlocks.TOWN_HALL.get();
            case "house" -> KingdomsBlocks.HOUSE.get();
            case "granary" -> KingdomsBlocks.GRANARY.get();
            case "farm" -> KingdomsBlocks.FARM.get();
            case "market" -> KingdomsBlocks.MARKET.get();
            case "lumber_camp" -> KingdomsBlocks.LUMBER_CAMP.get();
            case "mine" -> KingdomsBlocks.MINE.get();
            case "warehouse" -> KingdomsBlocks.WAREHOUSE.get();
            case "smith" -> KingdomsBlocks.SMITH.get();
            case "animal_farm" -> KingdomsBlocks.ANIMAL_FARM.get();
            case "watchtower" -> KingdomsBlocks.WATCHTOWER.get();
            case "storehouse" -> KingdomsBlocks.STOREHOUSE.get();
            case "workshop" -> KingdomsBlocks.WORKSHOP.get();
            case "camp_post" -> KingdomsBlocks.CAMP_POST.get();
            case "cache" -> KingdomsBlocks.CACHE.get();
            case "bunkhouse" -> KingdomsBlocks.BUNKHOUSE.get();
            case "hearth" -> KingdomsBlocks.HEARTH.get();
            case "cottage" -> KingdomsBlocks.COTTAGE.get();
            case "mill" -> KingdomsBlocks.MILL.get();
            case "carpentry" -> KingdomsBlocks.CARPENTRY.get();
            case "inn" -> KingdomsBlocks.INN.get();
            default -> null;   // stairs and anything else that is not a building
        };
    }

    /**
     * The built-in shapes, grown by level.
     *
     * <p>An improved building is the same shape with more room and another course
     * of wall — which is enough to read as an upgrade, and means every level of
     * every building does not have to be drawn by hand before levels work at all.
     */
    private static StructurePlan procedural(ServerLevel level, String path, BlockPos base,
                                            Rotation rotation, int tier) {
        int grow = 2 * (Math.max(1, tier) - 1);
        List<Placement> blocks = new ArrayList<>();
        int[] dims = switch (path) {
            case "town_hall" -> cabin(level, blocks, base, 7 + grow, 7 + grow, 4 + grow / 2, Blocks.STONE_BRICKS, Blocks.SPRUCE_LOG);
            case "house" -> cabin(level, blocks, base, 5 + grow, 5 + grow, 3 + grow / 2, Blocks.OAK_PLANKS, Blocks.OAK_LOG);
            case "granary" -> granary(level, blocks, base);
            case "farm" -> farm(level, blocks, base);
            case "market" -> market(level, blocks, base);
            case "lumber_camp" -> lumberCamp(level, blocks, base);
            case "mine" -> mine(level, blocks, base);
            case "warehouse" -> warehouse(level, blocks, base);
            case "smith" -> smith(level, blocks, base);
            case "animal_farm" -> animalFarm(level, blocks, base);
            case "stairs" -> accessStairs(level, blocks, base);
            case "watchtower" -> watchtower(level, blocks, base);
            case "storehouse" -> storehouse(level, blocks, base);
            case "camp_post" -> campPost(level, blocks, base);
            case "cache" -> cache(level, blocks, base);
            case "bunkhouse" -> bunkhouse(level, blocks, base);
            case "hearth" -> hearth(level, blocks, base);
            case "cottage" -> cottage(level, blocks, base);
            case "mill" -> mill(level, blocks, base);
            case "carpentry" -> carpentry(level, blocks, base);
            case "inn" -> inn(level, blocks, base);
            case "workshop" -> workshop(level, blocks, base);
            default -> marker(blocks, base);
        };
        if (path.equals("town_hall")) {
            add(blocks, base.offset(0, 5, 0), Blocks.GOLD_BLOCK);
            add(blocks, base.offset(0, 1, -1), KingdomsBlocks.TOWN_HALL.get());
            // The board hangs in the hall: one place to read what the town wants.
            add(blocks, base.offset(-2, 1, -2), KingdomsBlocks.QUEST_BOARD.get());
        }
        if (path.equals("house")) {
            add(blocks, base.offset(-1, 1, -1), KingdomsBlocks.HOUSE.get());
        }
        turn(blocks, base, rotation);
        // A quarter turn swaps the footprint's axes along with it.
        boolean quarter = rotation == Rotation.CLOCKWISE_90
                || rotation == Rotation.COUNTERCLOCKWISE_90;
        return finish(level, base, blocks,
                quarter ? dims[1] : dims[0], quarter ? dims[0] : dims[1], dims[2]);
    }

    /**
     * Puts a gathered list of placements into build order, with the digging first.
     *
     * <p>Two phases, in this order:
     * <ol>
     *   <li><strong>Excavation</strong> — every solid block standing inside the
     *       footprint comes out. Because the floor course sits at grade rather
     *       than on top of it, this is real work even on flat ground: the topsoil
     *       under the building has to go. Handed off unordered; see
     *       {@link Excavation} for how a crew shares it out.</li>
     *   <li><strong>Masonry</strong> — bottom layer up; full blocks before partial
     *       blocks within a layer; deterministic within that.</li>
     * </ol>
     *
     * <p>The masonry list IS the construction sequence — a supply gate later
     * simply stops the cursor mid-list.
     */
    private static StructurePlan finish(ServerLevel level, BlockPos base, List<Placement> blocks,
                                        int width, int depth, int height) {
        // A placement of air is not a placement at all; it is a hole somebody has
        // to make. The stair repairs use them to clear headroom.
        List<Placement> solid = new ArrayList<>(blocks.size());
        Set<BlockPos> toDig = new LinkedHashSet<>();
        for (Placement placement : blocks) {
            if (placement.state().isAir()) {
                toDig.add(placement.pos());
            } else {
                solid.add(placement);
            }
        }

        int rx = width / 2;
        int rz = depth / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                for (int dy = 0; dy <= height; dy++) {
                    toDig.add(base.offset(dx, dy, dz));
                }
            }
        }

        // Cut the ground back past the walls as well. Setting the floor at grade
        // is only half of being able to walk in: on anything steeper than a
        // gentle slope the hillside still comes up over the doorway, and the
        // building ends up at the bottom of a hole with its door buried.
        //
        // One ring wide, and only the courses ABOVE the floor line — note dy
        // starting at 1. The block at the floor line is the doorstep itself and
        // is never taken; where the ground falls short of it instead, the
        // doorstep course in foundation() packs it back up. Cut above, fill
        // below, one block out: that is the whole of the shelf now, in place of
        // the two-block pad that used to be scraped flat round everything.
        //
        // Only natural ground is taken. Aprons of neighbouring plots can meet,
        // and a rule that ate anything in reach would quietly chew a hole in the
        // house next door — or eat the doorstep the neighbour just laid.
        int ax = rx + APRON_MARGIN;
        int az = rz + APRON_MARGIN;
        for (int dx = -ax; dx <= ax; dx++) {
            for (int dz = -az; dz <= az; dz++) {
                if (Math.abs(dx) <= rx && Math.abs(dz) <= rz) {
                    continue;   // the footprint proper, already accounted for
                }
                for (int dy = 1; dy <= APRON_HEADROOM; dy++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    // The apron reaches past the footprint, so it can cross into
                    // a chunk nobody has loaded. Ask before reading, rather than
                    // dragging chunks in from a plan pass.
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState standing = level.getBlockState(pos);
                    // Only ground that genuinely stands over the floor line. Snow,
                    // grass and flowers are left exactly where they are — nothing
                    // is gained by clearing what was never blocking anything.
                    if (needsDigging(standing) && isNaturalGround(standing)) {
                        toDig.add(pos);
                    }
                }
            }
        }

        // Excavation is handed over as a bare set of blocks. No order is imposed
        // here at all: the order is a property of the terrain, recomputed as the
        // ground comes away, and it is Excavation that works it out. Ordering the
        // list here is exactly what forced a whole crew through one block at a
        // time and had them digging out from under each other.
        List<BlockPos> digTargets = new ArrayList<>();
        boolean blocked = false;
        for (BlockPos pos : toDig) {
            BlockState standing = level.getBlockState(pos);
            if (!needsDigging(standing)) {
                continue;   // air, or something a block simply covers over
            }
            if (standing.getDestroySpeed(level, pos) < 0) {
                blocked = true;   // bedrock: no amount of digging clears this site
                continue;
            }
            digTargets.add(pos);
        }

        solid.sort(Comparator
                .comparingInt((Placement q) -> q.pos().getY())
                .thenComparing(q -> isFullBlock(level, q) ? 0 : 1)
                .thenComparingInt(q -> q.pos().getX())
                .thenComparingInt(q -> q.pos().getZ()));

        // The post goes down first — before the floor, before everything. It is
        // the flag on the plot: the thing a player walks up to, clicks, and is
        // told what is being built here and how far along it is. Its cell is
        // withheld from the excavation for the same reason, so no digger takes
        // the sign down to level the ground it stands on.
        List<Step> steps = new ArrayList<>(solid.size());
        for (Placement placement : solid) {
            if (isPost(placement.state())) {
                steps.add(new Step(placement.pos(), placement.state(), placement.nbt(),
                        PLACE_COST, materialFor(placement.state())));
                digTargets.removeIf(dig -> dig.equals(placement.pos()));
            }
        }
        for (Placement placement : solid) {
            if (!isPost(placement.state())) {
                steps.add(new Step(placement.pos(), placement.state(), placement.nbt(),
                        PLACE_COST, materialFor(placement.state())));
            }
        }
        return new StructurePlan(width, depth, height, steps, digTargets, blocked);
    }

    private static boolean isPost(BlockState state) {
        return state.getBlock() instanceof BuildingPostBlock;
    }

    /**
     * Stands the building's post up, ahead of everything else.
     *
     * <p>Called the moment a site is surveyed, before any digging. Construction
     * will reach the same steps first anyway and lay them again, which is a
     * harmless overwrite — this only moves the announcement to the start of the
     * job instead of the start of the masonry.
     */
    private static void layPosts(ServerLevel level, StructurePlan plan) {
        for (Step step : plan.steps()) {
            if (!isPost(step.state())) {
                break;   // posts are sorted to the front; the first non-post ends them
            }
            lay(level, new Placement(step.pos(), step.state(), step.nbt()));
        }
    }

    private static boolean isFullBlock(ServerLevel level, Placement placement) {
        return placement.state().isCollisionShapeFullBlock(level, placement.pos());
    }

    private static void add(List<Placement> blocks, BlockPos pos, Block block) {
        blocks.add(new Placement(pos, block.defaultBlockState(), null));
    }

    /** As above, for a block that has to be placed in a particular state. */
    private static void add(List<Placement> blocks, BlockPos pos, BlockState state) {
        blocks.add(new Placement(pos, state, null));
    }

    // --- structures, expressed as plans ---

    /**
     * A mine head: a squat stone hut over the shaft, with the post inside.
     *
     * <p>No shaft is dug at build time — the miners cut where the mine post tells
     * them to, which is the point of the post.
     */
    private static int[] mine(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.COBBLESTONE, Blocks.STONE_BRICKS);
        add(blocks, base.offset(-1, 1, -1), KingdomsBlocks.MINE.get());
        add(blocks, base.offset(1, 1, -1), Blocks.FURNACE);
        add(blocks, base.offset(1, 2, -1), Blocks.COBBLESTONE_SLAB);
        return dims;
    }

    /** A long store shed: the town's ledger made of barrels. */
    private static int[] warehouse(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 7, 7, 4, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG);
        add(blocks, base.offset(0, 1, -2), KingdomsBlocks.WAREHOUSE.get());
        for (int dx = -2; dx <= 2; dx += 2) {
            add(blocks, base.offset(dx, 1, 2), Blocks.BARREL);
            add(blocks, base.offset(dx, 2, 2), Blocks.BARREL);
            add(blocks, base.offset(dx, 1, -1), Blocks.BARREL);
        }
        return dims;
    }

    /** A smithy: forge, anvil, bench. */
    private static int[] smith(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        add(blocks, base.offset(-1, 1, -1), KingdomsBlocks.SMITH.get());
        add(blocks, base.offset(0, 1, -1), Blocks.FURNACE);
        add(blocks, base.offset(1, 1, -1), Blocks.ANVIL);
        add(blocks, base.offset(1, 1, 1), Blocks.SMITHING_TABLE);
        add(blocks, base.offset(-1, 1, 1), Blocks.GRINDSTONE);
        return dims;
    }

    /**
     * A fenced compound split into pens, one per beast the culture keeps.
     *
     * <p>Pens are strips rather than a grid: a strip is trivially separated by a
     * single run of fence, and separation is the whole requirement — cows must
     * not end up in with the chickens.
     */
    private static int[] animalFarm(ServerLevel level, List<Placement> blocks, BlockPos base) {
        // The culture of the town whose ground this is, not the default one.
        // ShepherdWorker has always stocked these pens from the settlement's own
        // culture while this sized them from the default — which agreed only for
        // as long as there was one culture, and would have penned a highland
        // town's goats into a compound built for somebody else's herd.
        int pens = Math.max(1, cultureAt(level, base).penCount());
        int penDepth = 3;
        int width = 9;
        int depth = pens * penDepth + pens + 1;
        int rx = width / 2;
        int rz = depth / 2;

        foundation(level, blocks, base, width, depth);
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                add(blocks, base.offset(dx, 0, dz), Blocks.GRASS_BLOCK);
                boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz;
                // A divider every penDepth+1 rows walls one pen off from the next.
                boolean divider = Math.floorMod(dz + rz, penDepth + 1) == 0;
                if (edge || divider) {
                    add(blocks, base.offset(dx, 1, dz), Blocks.OAK_FENCE);
                }
            }
        }
        // One gate per pen, all down the same side, so every pen can be walked into.
        for (int pen = 0; pen < pens; pen++) {
            int dz = -rz + 1 + pen * (penDepth + 1);
            add(blocks, base.offset(-rx, 1, dz), Blocks.OAK_FENCE_GATE);
        }
        add(blocks, base.offset(0, 1, -rz + 1), KingdomsBlocks.ANIMAL_FARM.get());
        return new int[]{width, depth, 3};
    }

    private static int[] granary(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.GRANARY.get());
        add(blocks, base.offset(-1, 1, -1), Blocks.HAY_BLOCK);
        add(blocks, base.offset(1, 1, -1), Blocks.HAY_BLOCK);
        add(blocks, base.offset(-1, 2, -1), Blocks.HAY_BLOCK);
        add(blocks, base.offset(1, 1, 0), Blocks.BARREL);
        return dims;
    }

    /** A woodcutters hut: the control post stands on the floor, axe-side out. */
    private static int[] lumberCamp(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG);
        add(blocks, base.offset(-1, 1, -1), KingdomsBlocks.LUMBER_CAMP.get());
        add(blocks, base.offset(1, 1, -1), Blocks.OAK_LOG);
        add(blocks, base.offset(1, 2, -1), Blocks.OAK_LOG);
        return dims;
    }

    /** A family's own house: the smallest roof a household can grow under. */
    private static int[] cottage(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.COTTAGE.get());
        add(blocks, base.offset(-1, 1, -1), Blocks.WOOL.white());
        add(blocks, base.offset(1, 1, -1), Blocks.WOOL.white());
        add(blocks, base.offset(-1, 1, 1), Blocks.BARREL);
        return dims;
    }

    /** The mill: a grindstone under a spruce roof, hay in every corner. */
    private static int[] mill(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.MILL.get());
        add(blocks, base.offset(-1, 1, -1), Blocks.GRINDSTONE);
        add(blocks, base.offset(1, 1, -1), Blocks.HAY_BLOCK);
        add(blocks, base.offset(1, 2, -1), Blocks.HAY_BLOCK);
        add(blocks, base.offset(-1, 1, 1), Blocks.BARREL);
        return dims;
    }

    /** The carpentry: benches, a saw pit's worth of planks, stacked stock. */
    private static int[] carpentry(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.CARPENTRY.get());
        add(blocks, base.offset(-1, 1, -1), Blocks.CRAFTING_TABLE);
        add(blocks, base.offset(1, 1, -1), Blocks.STRIPPED_OAK_LOG);
        add(blocks, base.offset(1, 2, -1), Blocks.STRIPPED_OAK_LOG);
        add(blocks, base.offset(-1, 1, 1), Blocks.OAK_PLANKS);
        return dims;
    }

    /** The inn: the village's biggest roof, lanterns lit for the road. */
    private static int[] inn(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 7, 5, 3, Blocks.SPRUCE_PLANKS, Blocks.OAK_LOG);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.INN.get());
        add(blocks, base.offset(-2, 1, -1), Blocks.WOOL.white());
        add(blocks, base.offset(-1, 1, -1), Blocks.WOOL.white());
        add(blocks, base.offset(1, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(2, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(2, 1, 1), Blocks.CRAFTING_TABLE);
        return dims;
    }

    /** The staked claim: a flag of a building, the first thing a founding party raises. */
    private static int[] campPost(ServerLevel level, List<Placement> blocks, BlockPos base) {
        foundation(level, blocks, base, 3, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                // Coarse dirt under the post itself: a path block converts to
                // plain dirt the moment anything solid stands on it.
                boolean centre = dx == 0 && dz == 0;
                add(blocks, base.offset(dx, 0, dz),
                        centre ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH);
            }
        }
        add(blocks, base.offset(0, 1, 0), KingdomsBlocks.CAMP_POST.get());
        add(blocks, base.offset(1, 1, 1), Blocks.OAK_FENCE);
        add(blocks, base.offset(1, 2, 1), Blocks.LANTERN);
        return new int[]{3, 3, 3};
    }

    /** The pooled supplies: barrels on boards, out in the weather. */
    private static int[] cache(ServerLevel level, List<Placement> blocks, BlockPos base) {
        foundation(level, blocks, base, 3, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                add(blocks, base.offset(dx, 0, dz), Blocks.SPRUCE_PLANKS);
            }
        }
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.CACHE.get());
        add(blocks, base.offset(-1, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(1, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(-1, 1, 1), Blocks.COMPOSTER);
        return new int[]{3, 3, 2};
    }

    /** One room the whole party sleeps in — housing before there are families. */
    private static int[] bunkhouse(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 7, 5, 3, Blocks.OAK_PLANKS, Blocks.OAK_LOG);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.BUNKHOUSE.get());
        // Bedrolls in a row along the north wall; the mod cannot place real
        // beds, whose two halves need paired states.
        add(blocks, base.offset(-2, 1, -1), Blocks.WOOL.white());
        add(blocks, base.offset(-1, 1, -1), Blocks.WOOL.white());
        add(blocks, base.offset(1, 1, -1), Blocks.WOOL.white());
        add(blocks, base.offset(2, 1, -1), Blocks.WOOL.white());
        add(blocks, base.offset(2, 1, 1), Blocks.BARREL);
        return dims;
    }

    /** The open fire the camp cooks on: a cobble pad, log seats, nothing overhead. */
    private static int[] hearth(ServerLevel level, List<Placement> blocks, BlockPos base) {
        foundation(level, blocks, base, 5, 5);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean pad = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                add(blocks, base.offset(dx, 0, dz),
                        pad ? Blocks.COBBLESTONE : Blocks.DIRT_PATH);
            }
        }
        add(blocks, base.offset(0, 1, 0), Blocks.CAMPFIRE);
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                add(blocks, base.offset(dx, 1, dz), Blocks.STRIPPED_OAK_LOG);
            }
        }
        add(blocks, base.offset(0, 1, -2), KingdomsBlocks.HEARTH.get());
        return new int[]{5, 5, 2};
    }

    private static int[] storehouse(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.STOREHOUSE.get());
        add(blocks, base.offset(-1, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(1, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(-1, 2, -1), Blocks.BARREL);
        return dims;
    }

    private static int[] workshop(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.WORKSHOP.get());
        add(blocks, base.offset(-1, 1, -1), Blocks.CRAFTING_TABLE);
        add(blocks, base.offset(1, 1, -1), Blocks.SMITHING_TABLE);
        add(blocks, base.offset(0, 1, -1), Blocks.FURNACE);
        return dims;
    }

    private static int[] market(ServerLevel level, List<Placement> blocks, BlockPos base) {
        foundation(level, blocks, base, 5, 5);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.MARKET.get());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                add(blocks, base.offset(dx, 0, dz), Blocks.OAK_PLANKS);
                add(blocks, base.offset(dx, 3, dz), Blocks.SPRUCE_PLANKS);
            }
        }
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                add(blocks, base.offset(dx, 1, dz), Blocks.STRIPPED_OAK_LOG);
                add(blocks, base.offset(dx, 2, dz), Blocks.STRIPPED_OAK_LOG);
            }
        }
        add(blocks, base.offset(-1, 1, 0), Blocks.HAY_BLOCK);
        add(blocks, base.offset(0, 1, 0), Blocks.BARREL);
        add(blocks, base.offset(1, 1, 0), Blocks.HAY_BLOCK);
        add(blocks, base.offset(0, 2, 0), Blocks.LANTERN);
        return new int[]{5, 5, 4};
    }

    private static int[] farm(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int r = 5;
        // A field's soil is its floor, and that is drawn one below the base.
        foundation(level, blocks, base, 2 * r + 1, 2 * r + 1, -1);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                if (edge) {
                    add(blocks, base.offset(dx, -1, dz), Blocks.GRASS_BLOCK);
                    add(blocks, base.offset(dx, 0, dz), Blocks.OAK_FENCE);
                } else if (dz == 0) {
                    add(blocks, base.offset(dx, -1, dz), Blocks.WATER);
                } else {
                    add(blocks, base.offset(dx, -1, dz), Blocks.FARMLAND);
                    add(blocks, base.offset(dx, 0, dz), Blocks.WHEAT);
                }
            }
        }
        // Hung open, and it stays open. Nothing in this mod can work a gate: a
        // closed one is solid to pathfinding, so a fence with a gate in it is a
        // pen, and the farmer who walked in at planting was still in there at
        // harvest wondering how to get out.
        add(blocks, base.offset(0, 0, r),
                Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
        // Lanterns on the fence, enough that every crop sits in light 8 at night.
        // A crop that cannot see the sky — and a field cut into a hillside always
        // has a shaded strip under the overhang — pops off its soil the first
        // night, and the farmers replant it by day, and it pops again: a whole
        // field churned into seed items with nothing in any log. Light was the
        // entire cause. Corners and edge midpoints cover an 11-wide field; the
        // gate keeps its own post clear.
        for (int[] post : new int[][]{
                {-r, -r}, {-r, r}, {r, -r}, {r, r}, {0, -r}, {-r, 0}, {r, 0}, {1, r}}) {
            add(blocks, base.offset(post[0], 1, post[1]), Blocks.LANTERN);
        }
        add(blocks, base.offset(r - 1, 0, r - 1), KingdomsBlocks.FARM.get());
        return new int[]{2 * r + 1, 2 * r + 1, 3};
    }

    private static int[] watchtower(ServerLevel level, List<Placement> blocks, BlockPos base) {
        foundation(level, blocks, base, 3, 3);
        for (int y = 0; y < 7; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    if (y == 0 || y == 6) {
                        add(blocks, base.offset(dx, y, dz), Blocks.COBBLESTONE);
                    } else if (shell && !(dz == 1 && dx == 0 && y <= 2)) {
                        add(blocks, base.offset(dx, y, dz), Blocks.COBBLESTONE);
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                add(blocks, base.offset(dx, 7, dz), Blocks.COBBLESTONE_WALL);
            }
        }
        // The bell goes at the peak, where the watch can reach it and the whole
        // town can hear it. The lantern moves inside, hung from the roof it was
        // sitting on — a tower with a light on top and nothing to ring is a
        // tower that cannot raise the alarm.
        add(blocks, base.offset(0, 7, 0), Blocks.BELL);
        add(blocks, base.offset(0, 5, 0), Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        add(blocks, base.offset(0, 1, 0), KingdomsBlocks.WATCHTOWER.get());
        return new int[]{3, 3, 9};
    }

    /** How far a repair flight may run before giving up on reaching the ground. */
    private static final int MAX_STAIR_RUN = 16;

    /**
     * A flight of steps from a doorway down to whatever ground lies below it.
     *
     * <p>Steps march outward from the door, one block down and one block out at a
     * time, until they meet the terrain — so the run is exactly as long as the
     * drop demands. Each tread is underpinned so it is not a floating stair, and
     * the two blocks above are cleared so somebody can actually walk up.
     *
     * <p>Reports a one-by-one footprint on purpose: the shared site-clearing pass
     * squares off a box around the origin, and around a doorway that box would
     * chew through the house it is meant to serve. The clearing this plan needs
     * it does itself, tread by tread.
     */
    private static int[] accessStairs(ServerLevel level, List<Placement> blocks, BlockPos base) {
        for (int i = 1; i <= MAX_STAIR_RUN; i++) {
            int x = base.getX();
            int z = base.getZ() + i;
            int treadY = base.getY() - i;
            int ground = groundLevel(level, x, z) - 1;
            if (treadY <= ground) {
                break;   // the steps have met the hillside
            }
            add(blocks, new BlockPos(x, treadY, z), Blocks.COBBLESTONE);
            for (int under = 1; under <= 2; under++) {
                int fillY = treadY - under;
                if (fillY > ground) {
                    add(blocks, new BlockPos(x, fillY, z), Blocks.COBBLESTONE);
                }
            }
            add(blocks, new BlockPos(x, treadY + 1, z), Blocks.AIR);
            add(blocks, new BlockPos(x, treadY + 2, z), Blocks.AIR);
        }
        return new int[]{1, 1, 2};
    }

    private static int[] marker(List<Placement> blocks, BlockPos base) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                add(blocks, base.offset(dx, 0, dz), Blocks.STONE_BRICKS);
            }
        }
        add(blocks, base.offset(0, 1, 0), Blocks.GOLD_BLOCK);
        return new int[]{5, 5, 2};
    }

    /** A rectangular building: floor, log corners, walls with door gap and windows, rimmed roof. */
    private static int[] cabin(ServerLevel level, List<Placement> blocks, BlockPos base,
                               int width, int depth, int wallHeight, Block wall, Block frame) {
        int rx = width / 2;
        int rz = depth / 2;
        foundation(level, blocks, base, width, depth);

        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                add(blocks, base.offset(dx, 0, dz), wall);
                add(blocks, base.offset(dx, wallHeight + 1, dz), wall);
            }
        }
        for (int y = 1; y <= wallHeight; y++) {
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    boolean edgeX = Math.abs(dx) == rx;
                    boolean edgeZ = Math.abs(dz) == rz;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    BlockPos p = base.offset(dx, y, dz);
                    if (edgeX && edgeZ) {
                        add(blocks, p, frame);
                    } else if (dz == rz && dx == 0 && y <= 2) {
                        // south door gap
                    } else if (y == 2 && (dx == 0 || dz == 0)) {
                        add(blocks, p, Blocks.GLASS);
                    } else {
                        add(blocks, p, wall);
                    }
                }
            }
        }
        for (int dx = -rx; dx <= rx; dx++) {
            add(blocks, base.offset(dx, wallHeight + 1, -rz), frame);
            add(blocks, base.offset(dx, wallHeight + 1, rz), frame);
        }
        add(blocks, base.offset(0, 1, 0), Blocks.LANTERN);
        return new int[]{width, depth, wallHeight + 2};
    }

    /**
     * Cobble underpinning wherever the ground is missing — the true first course.
     *
     * <p>Two rings, and the difference between them is the whole of building up
     * rather than only digging down:
     *
     * <ul>
     *   <li><strong>Under the walls</strong>, from one below the floor. Filled as
     *       far as it will reach and no further: a stump of cobble under one
     *       corner is still better than the hole it is standing over.</li>
     *   <li><strong>The doorstep</strong> — the apron ring — starting AT the floor
     *       line rather than below it, because out here there is no floor course
     *       to stand on and the top of the fill IS the step. It is the exact
     *       complement of the apron cut in {@link #finish}, which starts one
     *       course higher and takes the hillside back where it stands proud.</li>
     * </ul>
     *
     * <p>A doorstep column goes in whole or not at all. Fill that runs out of
     * courses before it finds the ground is not a step, it is a cobble shelf
     * hanging in mid-air, and one of those outside a door looks far worse than
     * the drop it was trying to hide.
     *
     * <p>On level ground none of this places anything: every cell it would fill
     * already has ground in it. The cost is paid only by the buildings that are
     * actually on a slope, which is the point.
     */
    private static void foundation(ServerLevel level, List<Placement> blocks, BlockPos base,
                                   int width, int depth) {
        foundation(level, blocks, base, width, depth, 0);
    }

    /**
     * @param floorCourse where this structure lays its own walkable surface,
     *                    relative to the base. Zero for everything that has a
     *                    floor. A crop field draws its soil one BELOW its base —
     *                    see {@link #baseFor} — so its doorstep belongs one lower
     *                    too, or every field on flat ground gets a cobble kerb
     *                    standing a block proud of the grass all the way round.
     */
    private static void foundation(ServerLevel level, List<Placement> blocks, BlockPos base,
                                   int width, int depth, int floorCourse) {
        foundation(blocks, base, width, depth, floorCourse, groundOf(level));
    }

    /**
     * What the ground says, for the purpose of underpinning something.
     *
     * <p>Two questions, which is all the whole foundation pass ever asks of a
     * world — so asking them through this makes what it lays testable without
     * one. The cells it fills are where "floating over a slope" and "a cobble
     * kerb standing proud of the grass" both live, and neither was reachable by
     * anything but looking at a hillside.
     */
    interface Ground {

        /** Whether this cell can be judged at all. */
        boolean loaded(BlockPos pos);

        /** Nothing holding this cell up: open air, or water to displace. */
        boolean unsupported(BlockPos pos);
    }

    private static Ground groundOf(ServerLevel level) {
        return new Ground() {
            @Override
            public boolean loaded(BlockPos pos) {
                return level.isLoaded(pos);
            }

            @Override
            public boolean unsupported(BlockPos pos) {
                return isUnsupported(level, pos);
            }
        };
    }

    /** The underpinning and its apron, as a list of blocks and nothing else. */
    static void foundation(List<Placement> blocks, BlockPos base,
                           int width, int depth, int floorCourse, Ground ground) {
        int rx = width / 2;
        int rz = depth / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                for (int dy = 1; dy <= FOUNDATION_DEPTH; dy++) {
                    BlockPos below = base.offset(dx, floorCourse - dy, dz);
                    if (ground.unsupported(below)) {
                        add(blocks, below, Blocks.COBBLESTONE);
                    }
                }
            }
        }
        for (int dx = -rx - APRON_MARGIN; dx <= rx + APRON_MARGIN; dx++) {
            for (int dz = -rz - APRON_MARGIN; dz <= rz + APRON_MARGIN; dz++) {
                if (Math.abs(dx) <= rx && Math.abs(dz) <= rz) {
                    continue;   // the building's own box, underpinned above
                }
                doorstep(blocks, base.offset(dx, floorCourse, dz), ground);
            }
        }
    }

    /** Whether nothing is holding this cell up: open air, or water to displace. */
    private static boolean isUnsupported(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty();
    }

    /** Packs one apron column up to the floor line, if it can reach the ground. */
    static void doorstep(List<Placement> blocks, BlockPos top, Ground ground) {
        // The apron reaches past the footprint and can cross into a chunk nobody
        // has loaded, exactly as the apron cut can.
        if (!ground.loaded(top)) {
            return;
        }
        int drop = 0;
        while (drop < FOUNDATION_DEPTH && ground.unsupported(top.below(drop))) {
            drop++;
        }
        if (drop == 0 || ground.unsupported(top.below(drop))) {
            return;   // already at grade, or the ground is further down than a step
        }
        for (int dy = 0; dy < drop; dy++) {
            add(blocks, top.below(dy), Blocks.COBBLESTONE);
        }
    }


    /**
     * Lifts anything standing where a block is about to appear.
     *
     * <p>Builders lay blocks around their own feet, and a fast-forwarded build
     * can put a wall through someone mid-stride. Without this they are simply
     * entombed and suffocate — so whoever is caught gets moved to the first gap
     * above that actually fits them.
     */
    private static void evict(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
            return;   // nothing solid arriving, nothing to be trapped by
        }
        List<Entity> caught = level.getEntities((Entity) null, new AABB(pos), Entity::isAlive);
        if (caught.isEmpty()) {
            return;
        }
        BlockPos refuge = refugeAbove(level, pos);
        for (Entity entity : caught) {
            entity.teleportTo(refuge.getX() + 0.5, refuge.getY(), refuge.getZ() + 0.5);
        }
    }

    /** The lowest spot above {@code pos} with room for something to stand. */
    private static BlockPos refugeAbove(ServerLevel level, BlockPos pos) {
        for (int dy = 1; dy <= EVICT_SEARCH_HEIGHT; dy++) {
            BlockPos feet = pos.above(dy);
            if (isClear(level, feet) && isClear(level, feet.above())) {
                return feet;
            }
        }
        return pos.above(EVICT_SEARCH_HEIGHT);
    }

    private static boolean isClear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
