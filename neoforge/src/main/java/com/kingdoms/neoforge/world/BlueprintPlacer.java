package com.kingdoms.neoforge.world;

import com.keystone.api.Blueprints;
import com.keystone.api.LoadedBlueprint;
import com.keystone.api.PlannedBlock;
import com.kingdoms.neoforge.KingdomsBlocks;
import com.kingdoms.sim.settlement.BuildTask;
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
     * One thing a builder does: take a block out of the ground, or put one down.
     *
     * <p>{@code cost} is in work units. Laying is one; digging is more, scaled by
     * how hard the block is, which is what makes cutting a site out of a hillside
     * visibly slower than raising the walls afterwards.
     */
    private record Step(boolean digging, BlockPos pos, BlockState state, CompoundTag nbt, int cost) {
    }

    /** What a builder should be doing right now, and what they need in hand for it. */
    public record NextStep(boolean digging, BlockPos pos, int cost) {
    }

    /** A structure as an ordered sequence of digs and placements. */
    private record StructurePlan(int width, int depth, int height, List<Step> steps) {

        int placeWork() {
            int total = 0;
            for (Step step : steps) {
                if (!step.digging()) {
                    total += step.cost();
                }
            }
            return total;
        }

        int totalWork() {
            int total = 0;
            for (Step step : steps) {
                total += step.cost();
            }
            return total;
        }
    }

    /** How far above a doomed block to look for somewhere its occupant can stand. */
    private static final int EVICT_SEARCH_HEIGHT = 8;

    /** Work units to lay one block. Everything else is measured against this. */
    private static final int PLACE_COST = 1;

    /** Cheapest a dig can be, so even loose soil is slower to shift than a block is to set. */
    private static final int MIN_DIG_COST = 2;

    /** Hardest a dig can be, so an obsidian outcrop cannot stall a town forever. */
    private static final int MAX_DIG_COST = 8;

    private BlueprintPlacer() {
    }

    // --- the instant path ---

    public static void place(ServerLevel level, String blueprintId, BlockPos base) {
        StructurePlan plan = planFor(level, blueprintId, base);
        for (Step step : plan.steps()) {
            execute(level, step);
        }
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
            int firstAir = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    task.origin().x(), task.origin().z());
            // A flight of steps has no floor to sit flush with, so it is not sunk.
            // Everything else is, so you can walk in through the door.
            task.setSiteY(isStairs(task) ? firstAir : floorFor(firstAir));
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
        if (!task.isSitePrepared()) {
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
    public static NextStep nextStep(ServerLevel level, BuildTask task) {
        Step step = currentStep(level, task);
        if (step == null || !task.canAfford(step.cost())) {
            return null;
        }
        return new NextStep(step.digging(), step.pos(), step.cost());
    }

    /** What a builder needs in hand for the step in front of them. */
    public static Item toolFor(ServerLevel level, BuildTask task) {
        Step step = currentStep(level, task);
        if (step == null) {
            return null;
        }
        return step.digging()
                ? diggingTool(level.getBlockState(step.pos()))
                : step.state().getBlock().asItem();
    }

    /**
     * One swing at the step in hand. Returns true when the step actually finished.
     *
     * <p>Laying takes a single swing. Digging takes as many as the block is worth,
     * so a builder is visibly working at the ground rather than deleting it.
     */
    public static boolean swingAtStep(ServerLevel level, BuildTask task) {
        Step step = currentStep(level, task);
        if (step == null || !task.canAfford(step.cost())) {
            return false;
        }
        task.addStepProgress();
        if (task.stepProgress() < step.cost()) {
            return false;   // still working at it
        }
        execute(level, step);
        task.recordStepDone(step.cost());
        return true;
    }

    /** Finishes the step in hand outright, for paths with no ticks to spend on it. */
    public static boolean completeStep(ServerLevel level, BuildTask task) {
        Step step = currentStep(level, task);
        if (step == null || !task.canAfford(step.cost())) {
            return false;
        }
        execute(level, step);
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

    /** Carries out one step: the ground comes out, or the block goes down. */
    private static void execute(ServerLevel level, Step step) {
        if (step.digging()) {
            if (!level.getBlockState(step.pos()).isAir()) {
                // No drops. There is nowhere for spoil to go yet, and a site
                // knee-deep in dirt items is worse than no spoil at all.
                level.destroyBlock(step.pos(), false, null, 512);
            }
            return;
        }
        lay(level, new Placement(step.pos(), step.state(), step.nbt()));
    }

    /**
     * The right tool for shifting this block.
     *
     * <p>Builders are handed it rather than having to own it — there is no tool
     * economy yet — but the tool in their hand is the correct one for what they
     * are digging, and hardness sets the cost either way.
     */
    private static Item diggingTool(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return Items.IRON_SHOVEL;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return Items.IRON_AXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return Items.IRON_HOE;
        }
        return Items.IRON_PICKAXE;
    }

    /** What one block of ground is worth shifting, from how hard it is. */
    private static int digCost(ServerLevel level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) {
            return MAX_DIG_COST;   // unbreakable; excavation skips these entirely
        }
        return Math.clamp(MIN_DIG_COST + Math.round(hardness), MIN_DIG_COST, MAX_DIG_COST);
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

    /** Whether construction can proceed here at all — the chunk has to be loaded. */
    public static boolean isBuildableByHand(ServerLevel level, BuildTask task) {
        BlockPos approx = new BlockPos(task.origin().x(), task.origin().y(), task.origin().z());
        return level.isLoaded(approx);
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
        Identifier id = Identifier.parse(blueprintId);

        Optional<LoadedBlueprint> authored =
                Blueprints.loadFirst(level, base, styleCandidates(id));
        if (authored.isPresent()) {
            return fromBlueprint(level, authored.get(), base);
        }
        // Styles degrade too: with no norman/house drawn, a norman town still
        // gets the built-in house rather than an unknown-blueprint marker.
        String path = id.getPath();
        return procedural(level, path.substring(path.lastIndexOf('/') + 1), base);
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

    private static StructurePlan procedural(ServerLevel level, String path, BlockPos base) {
        List<Placement> blocks = new ArrayList<>();
        int[] dims = switch (path) {
            case "town_hall" -> cabin(level, blocks, base, 7, 7, 4, Blocks.STONE_BRICKS, Blocks.SPRUCE_LOG);
            case "house" -> cabin(level, blocks, base, 5, 5, 3, Blocks.OAK_PLANKS, Blocks.OAK_LOG);
            case "granary" -> granary(level, blocks, base);
            case "farm" -> farm(level, blocks, base);
            case "market" -> market(level, blocks, base);
            case "lumber_camp" -> lumberCamp(level, blocks, base);
            case "stairs" -> accessStairs(level, blocks, base);
            case "watchtower" -> watchtower(level, blocks, base);
            case "storehouse" -> storehouse(level, blocks, base);
            case "workshop" -> workshop(level, blocks, base);
            default -> marker(blocks, base);
        };
        if (path.equals("town_hall")) {
            add(blocks, base.offset(0, 5, 0), Blocks.GOLD_BLOCK);
        }
        return finish(level, base, blocks, dims[0], dims[1], dims[2]);
    }

    /**
     * Puts a gathered list of placements into build order, with the digging first.
     *
     * <p>Two phases, in this order:
     * <ol>
     *   <li><strong>Excavation</strong> — every solid block standing inside the
     *       footprint comes out, worked from the top down so nobody undermines
     *       what they are standing on. Because the floor course sits at grade
     *       rather than on top of it, this is real work even on flat ground: the
     *       topsoil under the building has to go.</li>
     *   <li><strong>Masonry</strong> — bottom layer up; full blocks before partial
     *       blocks within a layer; deterministic within that.</li>
     * </ol>
     *
     * <p>This IS the construction sequence — a supply gate later simply stops the
     * cursor mid-list.
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

        List<Step> digs = new ArrayList<>();
        for (BlockPos pos : toDig) {
            BlockState standing = level.getBlockState(pos);
            if (standing.isAir() || standing.getDestroySpeed(level, pos) < 0) {
                continue;   // nothing there, or nothing anyone can shift
            }
            digs.add(new Step(true, pos, standing, null, digCost(level, pos, standing)));
        }
        // Top down: you cannot dig out the block you are standing on.
        digs.sort(Comparator
                .comparingInt((Step s) -> -s.pos().getY())
                .thenComparingInt(s -> s.pos().getX())
                .thenComparingInt(s -> s.pos().getZ()));

        solid.sort(Comparator
                .comparingInt((Placement q) -> q.pos().getY())
                .thenComparing(q -> isFullBlock(level, q) ? 0 : 1)
                .thenComparingInt(q -> q.pos().getX())
                .thenComparingInt(q -> q.pos().getZ()));

        List<Step> steps = new ArrayList<>(digs.size() + solid.size());
        steps.addAll(digs);
        for (Placement placement : solid) {
            steps.add(new Step(false, placement.pos(), placement.state(), placement.nbt(), PLACE_COST));
        }
        return new StructurePlan(width, depth, height, steps);
    }

    private static boolean isFullBlock(ServerLevel level, Placement placement) {
        return placement.state().isCollisionShapeFullBlock(level, placement.pos());
    }

    private static void add(List<Placement> blocks, BlockPos pos, Block block) {
        blocks.add(new Placement(pos, block.defaultBlockState(), null));
    }

    // --- structures, expressed as plans ---

    private static int[] granary(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG);
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
        add(blocks, base.offset(-1, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(1, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(-1, 2, -1), Blocks.BARREL);
        return dims;
    }

    private static int[] workshop(ServerLevel level, List<Placement> blocks, BlockPos base) {
        int[] dims = cabin(level, blocks, base, 5, 5, 3, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG);
        add(blocks, base.offset(-1, 1, -1), Blocks.CRAFTING_TABLE);
        add(blocks, base.offset(1, 1, -1), Blocks.SMITHING_TABLE);
        add(blocks, base.offset(0, 1, -1), Blocks.FURNACE);
        return dims;
    }

    private static int[] market(ServerLevel level, List<Placement> blocks, BlockPos base) {
        foundation(level, blocks, base, 5, 5);
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
        int r = 3;
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
        add(blocks, base.offset(0, 0, r), Blocks.OAK_FENCE_GATE);
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
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
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
