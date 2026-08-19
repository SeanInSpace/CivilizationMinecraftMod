package com.kingdoms.neoforge.world;

import com.keystone.api.Blueprints;
import com.keystone.api.LoadedBlueprint;
import com.keystone.api.PlannedBlock;
import com.kingdoms.neoforge.KingdomsBlocks;
import com.kingdoms.sim.settlement.BuildTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** The next block a builder should be carrying, and where it goes. */
    public record NextBlock(BlockPos pos, BlockState state) {

        /** What the builder holds — the item form of whatever is being laid. */
        public Block block() {
            return state.getBlock();
        }
    }

    /** A structure as an ordered build sequence plus the site it needs cleared. */
    private record StructurePlan(int width, int depth, int height, List<Placement> blocks) {
    }

    /** Fraction of the work at which the last block is laid, leaving completion headroom. */
    private static final double VISIBLE_BUILD_DONE_AT = 0.85;

    private BlueprintPlacer() {
    }

    // --- the instant path ---

    public static void place(ServerLevel level, String blueprintId, BlockPos base) {
        StructurePlan plan = planFor(level, blueprintId, base);
        clearSite(level, base, plan.width(), plan.depth(), plan.height());
        for (Placement placement : plan.blocks()) {
            lay(level, placement);
        }
    }

    // --- the visible path ---

    /**
     * Surveys the terrain and clears the volume, once, before the first course is
     * laid. Also records the plan size on the task so the simulation can tell a
     * hand-built structure from one that still needs materializing.
     *
     * @return true if the site was surveyed or cleared by this call
     */
    public static boolean prepareSite(ServerLevel level, BuildTask task) {
        if (!isBuildableByHand(level, task)) {
            return false;
        }
        boolean changed = false;
        if (task.siteY() == BuildTask.UNSET_SITE_Y) {
            task.setSiteY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    task.origin().x(), task.origin().z()));
            changed = true;
        }
        StructurePlan plan = planOf(level, task);
        if (plan == null) {
            return changed;
        }
        if (task.planSize() != plan.blocks().size()) {
            task.setPlanSize(plan.blocks().size());
            changed = true;
        }
        if (!task.isSitePrepared()) {
            BlockPos base = baseOf(task);
            clearSite(level, base, plan.width(), plan.depth(), plan.height());
            task.setSitePrepared(true);
            changed = true;
        }
        return changed;
    }

    /**
     * The next block the builders should lay, or null when the structure is as far
     * along as the current work allows.
     *
     * <p>The cursor is deliberately ahead of the simulation: the last block is due
     * at {@link #VISIBLE_BUILD_DONE_AT} of the work, so the structure is standing
     * before the task completes. Otherwise the task leaves the build queue on the
     * very step it finishes and the completion pass stamps in the remainder.
     */
    public static NextBlock nextBlock(ServerLevel level, BuildTask task) {
        StructurePlan plan = planOf(level, task);
        if (plan == null || task.blocksPlaced() >= plan.blocks().size()) {
            return null;
        }
        double pace = Math.min(1.0, task.completionFraction() / VISIBLE_BUILD_DONE_AT);
        int due = Math.min(plan.blocks().size(), (int) Math.round(plan.blocks().size() * pace));
        if (task.blocksPlaced() >= due) {
            return null;
        }
        Placement placement = plan.blocks().get(task.blocksPlaced());
        return new NextBlock(placement.pos(), placement.state());
    }

    /** Lays the next block of the plan. Returns true if one went down. */
    public static boolean placeNextBlock(ServerLevel level, BuildTask task) {
        StructurePlan plan = planOf(level, task);
        if (plan == null || task.blocksPlaced() >= plan.blocks().size()) {
            return false;
        }
        lay(level, plan.blocks().get(task.blocksPlaced()));
        task.setBlocksPlaced(task.blocksPlaced() + 1);
        return true;
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
        return finish(level, blocks, size.getX(), size.getZ(), size.getY());
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
        return finish(level, blocks, dims[0], dims[1], dims[2]);
    }

    /**
     * Puts a gathered list of placements into build order.
     *
     * <p>The mason's order: bottom layer up; full blocks before partial blocks
     * within a layer; deterministic within that. This IS the construction
     * sequence — a supply gate later simply stops the cursor mid-list.
     */
    private static StructurePlan finish(ServerLevel level, List<Placement> blocks,
                                        int width, int depth, int height) {
        blocks.sort(Comparator
                .comparingInt((Placement p) -> p.pos().getY())
                .thenComparing(p -> isFullBlock(level, p) ? 0 : 1)
                .thenComparingInt(p -> p.pos().getX())
                .thenComparingInt(p -> p.pos().getZ()));
        return new StructurePlan(width, depth, height, blocks);
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

    /** Site preparation: the volume is cleared once, before the first course is laid. */
    private static void clearSite(ServerLevel level, BlockPos base, int width, int depth, int height) {
        int rx = width / 2;
        int rz = depth / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                for (int dy = 0; dy <= height; dy++) {
                    level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }
}
