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
    private record Placement(BlockPos pos, BlockState state, CompoundTag nbt) {
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

    /** How far past the walls the ground is cut back, so there is somewhere to stand. */
    public static final int APRON_MARGIN = 2;

    /** Headroom cleared over the apron — enough to walk the whole way round. */
    private static final int APRON_HEADROOM = 3;

    private BlueprintPlacer() {
    }

    // --- the instant path ---

    /** Places a whole structure and reports where it went and how big it is. */
    public static Footprint place(ServerLevel level, String blueprintId, BlockPos base,
                                 int facing) {
        StructurePlan plan = planFor(level, blueprintId, base, facing);
        for (BlockPos dig : plan.digTargets()) {
            if (!level.getBlockState(dig).isAir()) {
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
            int firstAir = groundLevel(level, task.origin().x(), task.origin().z());
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
            // Everything else is sunk to grade, so you can walk in through it.
            boolean inPlace = isStairs(task) || task.isUpgrade();
            task.setSiteY(inPlace ? task.origin().y()
                    : baseFor(task.blueprintId(), firstAir));
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
     * <p>The two producers are exempt on purpose. A lumber camp that needed
     * timber, or a mine that needed stone, is a town that can never dig itself
     * out of an empty larder. They are the bootstrap, so they are always payable.
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

        Optional<LoadedBlueprint> authored =
                Blueprints.loadFirst(level, base, styleCandidates(id), rotation, Mirror.NONE);
        if (authored.isPresent()) {
            return fromBlueprint(level, authored.get(), base);
        }
        // Styles degrade too: with no norman/house drawn, a norman town still
        // gets the built-in house rather than an unknown-blueprint marker.
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
     */
    private static List<Identifier> styleCandidates(Identifier id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        if (slash < 0) {
            return List.of(id);
        }
        return List.of(id, id.withPath(path.substring(slash + 1)));
    }

    /**
     * Turns an authored blueprint into a plan on this site.
     *
     * <p>Blueprints are anchored from their minimum corner, while build plots are
     * points, so the footprint is centred on the plot. The blueprint itself has no
     * foundation — nobody draws one — so the same cobble underpinning the
     * procedural shapes get is laid beneath it, which is what stops an authored
     * building floating over a slope.
     */
    private static StructurePlan fromBlueprint(ServerLevel level, LoadedBlueprint blueprint,
                                               BlockPos base) {
        Vec3i size = blueprint.size();
        BlockPos anchor = base.offset(-(size.getX() - 1) / 2, 0, -(size.getZ() - 1) / 2);

        List<Placement> blocks = new ArrayList<>(blueprint.blockCount() + 32);
        foundation(level, blocks, base, size.getX(), size.getZ());
        for (PlannedBlock block : blueprint.sequence()) {
            blocks.add(new Placement(block.at(anchor), block.state(), block.nbt()));
        }
        return finish(level, base, blocks, size.getX(), size.getZ(), size.getY());
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
        // building ends up at the bottom of a hole with its door buried. This
        // levels a shelf around it instead.
        //
        // Only natural ground is taken. Aprons of neighbouring plots can meet,
        // and a rule that ate anything in reach would quietly chew a hole in the
        // house next door.
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
        int pens = Math.max(1, Culture.DEFAULT.penCount());
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
        foundation(level, blocks, base, 2 * r + 1, 2 * r + 1);
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
        add(blocks, base.offset(0, 7, 0), Blocks.LANTERN);
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

    /** Cobble underpinning wherever the ground is missing — the true first course. */
    private static void foundation(ServerLevel level, List<Placement> blocks, BlockPos base,
                                   int width, int depth) {
        int rx = width / 2;
        int rz = depth / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos below = base.offset(dx, -dy, dz);
                    if (level.getBlockState(below).isAir() || !level.getFluidState(below).isEmpty()) {
                        add(blocks, below, Blocks.COBBLESTONE);
                    }
                }
            }
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
