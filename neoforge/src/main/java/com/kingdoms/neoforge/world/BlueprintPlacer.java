package com.kingdoms.neoforge.world;

import com.keystone.api.Blueprints;
import com.keystone.api.LoadedBlueprint;
import com.keystone.api.PlannedBlock;
import com.kingdoms.neoforge.KingdomsBlocks;
import com.kingdoms.neoforge.KingdomsConfig;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.block.BuildingPostBlock;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Beds;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.work.Spoil;
import com.kingdoms.sim.world.SimWorld;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildingSizes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.HashMap;
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

    /**
     * Something a plan writes at one named cell.
     *
     * <p>Exists so the one rule both a drawing and a build sequence obey — a
     * later write at a cell is what actually stands there afterwards — can be
     * written once and asked of either. See {@link #supersededIn}.
     */
    interface Cell {

        BlockPos pos();
    }

    /** One block placement in a plan. */
    record Placement(BlockPos pos, BlockState state, CompoundTag nbt) implements Cell {
    }

    /**
     * One block a builder puts down.
     *
     * <p>Masonry only. Excavation is no longer part of this list: taking ground
     * out is a job several people do at once, in parallel, at real break speed,
     * and a single ordered queue could express none of that. See {@link Excavation}.
     */
    private record Step(BlockPos pos, BlockState state, CompoundTag nbt,
                        int cost, String material) implements Cell {
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
                                 List<BlockPos> digTargets, List<BlockPos> strip,
                                 boolean blocked, BuildingSizes.Notch notch) {

        StructurePlan(int width, int depth, int height, List<Step> steps,
                      List<BlockPos> digTargets, boolean blocked) {
            this(width, depth, height, steps, digTargets, List.of(), blocked,
                    BuildingSizes.Notch.NONE);
        }

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
    public static final int APRON_MARGIN = BuildingSizes.APRON;

    /** Headroom cleared over the apron — enough to walk the whole way round. */
    private static final int APRON_HEADROOM = 3;

    /**
     * How many courses of cobble may be laid under a floor to reach the ground.
     *
     * <p>A real cost rather than free leveling: each course is masonry somebody
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
        return place(level, blueprintId, base, facing, (resource, amount) -> { });
    }

    /**
     * The same, telling the caller what clearing the plot was worth.
     *
     * <p>The unwatched half of one rule: every block a citizen breaks yields its
     * material to the town. Nobody is holding anything here — the building is
     * being drawn because there was no one to build it — so what a crew would
     * have carried out of the hole is summed as the hole is emptied and handed
     * back for the settlement to credit. Blocks that are already gone yield
     * nothing, which is what stops a site a crew half-dug by hand before the
     * player walked away from being paid for twice.
     */
    public static Footprint place(ServerLevel level, String blueprintId, BlockPos base,
                                 int facing, java.util.function.ObjIntConsumer<String> spoil) {
        StructurePlan plan = planFor(level, blueprintId, base, facing);
        strip(level, plan);
        Map<String, Integer> yielded = new LinkedHashMap<>();
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
                Spoil.Kind kind = spoilOf(level.getBlockState(dig));
                if (kind.isSomething()) {
                    yielded.merge(kind.resource(), kind.perBlock(), Integer::sum);
                }
                level.destroyBlock(dig, false, null, 512);
            }
        }
        yielded.forEach(spoil::accept);
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
        return plotOf(y, plan.width(), plan.depth(), plan.height(), plan.notch());
    }

    /** The same, from the parts, for the plan pass that has no plan yet. */
    private static Footprint plotOf(int y, int width, int depth, int height,
                                    BuildingSizes.Notch notch) {
        return new Footprint(y,
                width + 2 * APRON_MARGIN,
                depth + 2 * APRON_MARGIN,
                height,
                // The bite grows with the box. A footprint is the walls plus the
                // doorstep ring, and a doorstep is laid round the inside of the
                // crook as much as round the outside -- so the corner that is
                // NOT the building shrinks by the margin the rest of it gains.
                notch.isCut()
                        ? new BuildingSizes.Notch(
                                Math.max(1, notch.width() - APRON_MARGIN),
                                Math.max(1, notch.depth() - APRON_MARGIN),
                                notch.towardX(), notch.towardZ())
                        : BuildingSizes.Notch.NONE);
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
            // building being replaced, so the new one was pitched a story up and
            // built on top of the old — and the excavation, measured from up there,
            // was digging air.
            //
            // Everything else is set to the grade of its own plot, so you can walk
            // in through it.
            boolean inPlace = isStairs(task) || task.isUpgrade();
            task.setSiteY(inPlace ? task.origin().y() : surveyBase(level, task));
            changed = true;
        }
        if (task.isRepair() && !task.isSitePrepared()) {
            // Which way round the building actually stands, settled before a
            // single block is compared against it and then carried on the job.
            // Everything a repair does is the difference between the plan and the
            // wall, and a plan drawn the wrong way round makes the whole building
            // read as missing.
            task.setFacing(fittedFacing(level, task.blueprintId(), baseOf(task),
                    task.facing()));
            changed = true;
        }
        StructurePlan plan = planOf(level, task);
        if (plan == null) {
            return changed;
        }
        if (task.isRepair()) {
            // What is still owed, plus what has already been laid: the size of the
            // hole as it was when the crew arrived, held steady while they fill
            // it. Reading only what is owed would shrink the plan exactly as fast
            // as the work filled it and the job would read as finished halfway
            // through. Nothing is dug either, so both halves are the same figure —
            // a repair is masonry from end to end.
            //
            // Raised and never lowered. A plot can straddle a chunk nobody had
            // loaded when the job opened, and the blocks over there are invisible
            // rather than sound; when that ground arrives the bill grows to match.
            // Letting it fall would hand the same building back half mended every
            // time a player walked away mid-repair.
            int bill = owedWork(level, plan) + task.workDone();
            if (bill == 0 && allVisible(plan, standingIn(level))) {
                // A crew that arrives to find the building whole. Not an exotic
                // case: the census that books a repair counts every solid block in
                // the plot box, which is a good deal more than the blueprint, so a
                // felled tree in the yard or a scraped apron reads as a hole in the
                // house. There is no masonry in that job at all.
                //
                // Booked as one unit already done rather than as no plan at all,
                // because a task with no plan can never read complete: it would sit
                // at the head of a head-blocking queue doing nothing until the
                // watched-build grace timed it out, and while it sat there
                // TownAuditor would go on sparing the building from demolition on
                // the strength of a repair nobody was working.
                task.setPlan(1, 1);
                task.setWorkDone(1);
                task.syncProgressToWork();
                changed = true;
            } else if (bill > task.planWork()) {
                task.setPlan(bill, bill);
                changed = true;
            }
        } else if (task.planWork() != plan.totalWork()) {
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
            // And the branches over it come down with the same stroke. Not the
            // crew's work, however much it ought to be: a crown eight blocks over
            // the plot has no square beside it anybody can stand on, so a leaf
            // handed to the diggers is searched for footing three dozen times and
            // then abandoned — and the canopy is still lying across the roof when
            // the building is finished, which is the whole complaint. The trunks
            // are the crew's; the foliage is scenery, and it is simply taken.
            strip(level, plan);
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
        // center, which swaps width and depth, and a survey that sampled the
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
        return nextStep(level, settlement, task, null);
    }

    /**
     * @param inHand what the builder asking is carrying, or null for anybody
     *               carrying nothing. A step whose material is already in a
     *               builder's hands is payable however empty the ledger is: that
     *               stock left the books at the warehouse when it was picked up,
     *               and asking the town to own it a second time is what stranded
     *               a loaded builder beside the last wall they could have
     *               finished — a store emptied by the very trip that filled them.
     */
    public static NextStep nextStep(ServerLevel level, Settlement settlement, BuildTask task,
                                    String inHand) {
        Step step = currentStep(level, task);
        if (step == null || !task.canAfford(step.cost())) {
            return null;
        }
        if (!canPayFor(settlement, task, step) && !isHeld(step, inHand)) {
            return null;
        }
        return new NextStep(step.pos(), step.cost());
    }

    /** Whether what a builder is carrying is what this step is made of. */
    private static boolean isHeld(Step step, String inHand) {
        return inHand != null && inHand.equals(step.material());
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

    /**
     * What breaking this block is worth to the town.
     *
     * <p>The other direction of {@link #materialFor}, and kept beside it so that
     * building and breaking are read off one list. They are deliberately not the
     * same function: laying asks what a course <em>costs</em> and is happy to
     * charge a whole category for it, while breaking asks what actually came away
     * in somebody's hands, which has to be narrow or a town would mine stone out
     * of every furnace it pulled down. See {@code Spoil} for the table itself.
     *
     * <p><strong>Tags first, names second.</strong> Tags are how a modded log
     * gets recognized as a log, and they are bound when a server loads its
     * datapacks — so in a unit test, with no server, every tag test is false and
     * the name table is the whole classifier. Both halves therefore have to be
     * right, and the name half is the one that can be asserted.
     */
    public static Spoil.Kind spoilOf(BlockState state) {
        if (state == null || state.isAir()) {
            return Spoil.Kind.NOTHING;
        }
        if (state.is(BlockTags.LOGS)) {
            return Spoil.Kind.TIMBER;
        }
        if (state.is(BlockTags.LEAVES)) {
            // A crown is not a harvest. Vanilla gives a player sticks and the odd
            // sapling for one, and a town has no column for either.
            return Spoil.Kind.NOTHING;
        }
        if (state.is(BlockTags.IRON_ORES)) {
            return Spoil.Kind.ORE;
        }
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)) {
            return Spoil.Kind.SOIL;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return Spoil.Kind.ROCK;
        }
        return Spoil.ofBlockName(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }

    /**
     * What a builder must have in hand for the step in front of them, or null if
     * the step costs nothing.
     *
     * <p>Null covers both exemptions the ledger already grants, and covers them
     * for the same reason: a step with no material at all — glass, crops, soil,
     * things a town does not make — and the bootstrap producers, which are free
     * everywhere. A step nobody is charged for is a step nobody has to be
     * holding. Charging the carry rule where the ledger charges nothing would
     * leave a watched town out of stone unable to raise the mine that fixes it,
     * because its builders would stand at an empty warehouse waiting for stone
     * to arrive by no means at all.
     */
    public static String materialOwedForStep(ServerLevel level, BuildTask task) {
        Step step = currentStep(level, task);
        if (step == null || isProducer(task)) {
            return null;
        }
        return step.material();
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
     *
     * <p>There is deliberately no overload that omits {@code carried}. It would
     * be the one remaining way to lay a block on the watched path and charge the
     * ledger at the wall, which is exactly the double-charge the carried load
     * exists to prevent.
     *
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

    /**
     * Finishes the step in hand outright, for paths with no ticks to spend on it.
     *
     * <p>As with {@link #swingAtStep}, there is deliberately no overload that
     * omits {@code carried}: one would be a way to lay a block on the watched
     * path and charge the ledger at the wall, which is the double-charge a
     * carried load exists to prevent.
     *
     * @param carried the block came out of a builder's load, which the stores
     *                were charged for at the warehouse and must not be charged
     *                for again here
     */
    public static boolean completeStep(ServerLevel level, Settlement settlement, BuildTask task,
                                       boolean carried) {
        Step step = currentStep(level, task);
        if (step == null || !task.canAfford(step.cost())) {
            return false;
        }
        if (!carried && !canPayFor(settlement, task, step)) {
            return false;
        }
        execute(level, step);
        if (!carried) {
            payFor(settlement, task, step);
        }
        task.recordStepDone(step.cost());
        return true;
    }

    private static Step currentStep(ServerLevel level, BuildTask task) {
        StructurePlan plan = planOf(level, task);
        if (plan == null) {
            return null;
        }
        if (task.isRepair()) {
            skipWhatStillStands(level, task, plan.steps());
        }
        if (task.stepsDone() >= plan.steps().size()) {
            return null;
        }
        return plan.steps().get(task.stepsDone());
    }

    /**
     * Runs a repair's cursor past every course the building has not lost.
     *
     * <p>A repair walks the whole blueprint in the same mason's order as a build,
     * because that is the only order the crew knows and the only one that puts a
     * floor back before the wall that stands on it. What makes it a repair rather
     * than a rebuild is here: a step whose block is already there is not work at
     * all. It is skipped outright — no swing, no budget spent, no charge to the
     * stores — so the town pays for the dozen blocks a creeper took and not for
     * the four hundred it did not.
     *
     * <p>Spending a granted work unit to look at each sound block was the
     * alternative and it is wrong twice over: mending a roof would take as long as
     * raising the cottage, and the pace would come off the size of the building
     * instead of the size of the hole.
     *
     * <p>This is why the plan a repair holds must be the whole structure rather
     * than a list of the gaps. The cursor is an index into that list and is
     * written to disk; a list that shrank as the work was done would leave a
     * reloaded repair pointing at somebody else's block.
     *
     * <p>The cursor stops at a cell nobody can see rather than running past it,
     * which is the opposite of what {@link #isOwed} does and is right for the
     * opposite reason. Judging damage on unread ground invents work; skipping
     * unread ground <em>loses</em> it — the cursor only ever goes forward and it
     * is saved to disk, so one pass while a chunk was out would step over those
     * blocks for good and the repair would report itself finished with the hole
     * still open.
     */
    private static void skipWhatStillStands(ServerLevel level, BuildTask task,
                                            List<Step> steps) {
        Standing world = standingIn(level);
        int at = task.stepsDone();
        while (at < steps.size()) {
            Step step = steps.get(at);
            BlockState there = world.at(step.pos());
            if (there == null || !there.is(step.state().getBlock())) {
                break;   // owed, or nobody can see whether it is
            }
            at++;
        }
        if (at != task.stepsDone()) {
            task.setStepsDone(at);
            task.setStepProgress(0);
        }
    }

    /**
     * Takes the foliage off a plot, once, when its site opens.
     *
     * <p>Silently and with no drops, the way the excavation takes everything
     * else: a site knee-deep in loose leaf items is worse than a site with
     * leaves on it. Idempotent, because a cell that has already been cleared
     * reads as air and is skipped — which matters, since {@link #place} is also
     * the finishing pass that runs over a building that is already whole.
     */
    private static void strip(ServerLevel level, StructurePlan plan) {
        for (BlockPos leaf : plan.strip()) {
            if (level.isLoaded(leaf) && !level.getBlockState(leaf).isAir()) {
                level.setBlock(leaf, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
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
    /**
     * Whether this is something growing rather than something built.
     *
     * <p>The two things a doorway is blocked by that no amount of leveling
     * will shift. Kept apart from {@link #isNaturalGround} because the apron
     * treats them differently in principle even though it now takes both: earth
     * is cut back to make a shelf, and a tree is felled because it is in the
     * way.
     */
    private static boolean isGrowth(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

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
     * <p>Block states are written as authored, without neighbor updates: a
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
     *
     * <p>A repair is billed for the blocks it is short of rather than for the
     * courses still ahead of the cursor, which for a repair is most of the
     * building. A player reading a warehouse sign wants the price of the hole,
     * and the price of the hole is what the town is actually going to pay.
     */
    public static Map<Item, Integer> billOfMaterials(ServerLevel level, BuildTask task) {
        StructurePlan plan = planOf(level, task);
        if (plan == null) {
            return Map.of();
        }
        Standing world = task.isRepair() ? standingIn(level) : null;
        Map<Item, Integer> bill = new LinkedHashMap<>();
        List<Step> steps = plan.steps();
        for (int i = Math.min(task.stepsDone(), steps.size()); i < steps.size(); i++) {
            Step step = steps.get(i);
            if (world != null && !isOwed(step, world)) {
                continue;
            }
            Item item = step.state().getBlock().asItem();
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
    static Rotation rotationOf(int facing) {
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
     *
     * <p>The state half is {@code BlockState.rotate}, which every block answers
     * for itself — a stair turns its {@code FACING} and keeps its {@code SHAPE},
     * because left and right are measured from the facing and the facing is what
     * moved. That was already true before anything in this file drew a pitched
     * roof and nothing had ever depended on it; {@code PartsTest} now does,
     * which is why this is reachable from a test at all.
     */
    static void turn(List<Placement> blocks, BlockPos base, Rotation rotation) {
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

    private record PlanKey(String blueprintId, BlockPos base, int facing, boolean repair) {
    }

    private static final Map<PlanKey, StructurePlan> PLAN_CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PlanKey, StructurePlan> eldest) {
                    return size() > PLAN_CACHE_LIMIT;
                }
            };

    /**
     * The plan the builders are working to.
     *
     * <p>Ordinary construction is drawn unturned, as it always has been, and that
     * is a defect rather than a decision: {@link #place} honors the facing it is
     * given, so a building that grew unwatched faces the town center, while a
     * hand-built one comes out facing south with its door on whichever side that
     * puts it. Correcting it here is a one-word change and a save migration —
     * every structure already standing was laid unturned while its record says
     * otherwise, and every half-built one would resume in a new orientation on top
     * of the courses already laid — so it is left alone and written down.
     *
     * <p>A repair cannot wait for that, because it compares the plan against what
     * is actually standing and a plan turned the wrong way makes every block of
     * the building read as wrong: the "repair" would rotate the cottage. So a
     * repair carries the facing that <em>fits</em> — see {@link #fittedFacing},
     * which asks the wall rather than the record — and this uses it.
     */
    private static StructurePlan planOf(ServerLevel level, BuildTask task) {
        if (task.siteY() == BuildTask.UNSET_SITE_Y) {
            return null;
        }
        BlockPos base = baseOf(task);
        int facing = task.isRepair() ? task.facing() : 0;
        PlanKey key = new PlanKey(task.blueprintId(), base, facing, task.isRepair());
        StructurePlan cached = PLAN_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        StructurePlan plan = planFor(level, task.blueprintId(), base, facing);
        if (task.isRepair()) {
            plan = asRepair(plan);
        }
        PLAN_CACHE.put(key, plan);
        return plan;
    }

    // --- the difference between a plan and the world ---

    /**
     * What is actually standing where a plan says a block should be.
     *
     * <p>One question, and the whole of a repair follows from the answers. Asking
     * it through a seam rather than of a {@code ServerLevel} is what lets the diff
     * be checked without a running game — see {@code BlueprintPlacerDiffTest} —
     * for the same reason {@link Site} exists.
     */
    interface Standing {

        /** The block at this cell, or null where nobody can see it. */
        BlockState at(BlockPos pos);
    }

    /** The real world, asked the one question a repair puts to it. */
    private static Standing standingIn(ServerLevel level) {
        return pos -> level.isLoaded(pos) ? level.getBlockState(pos) : null;
    }

    /**
     * Whether a planned block still has to be laid.
     *
     * <p>Not "is this cell empty": a block that is not the block the plan names is
     * owed whatever is there instead, so a wall somebody swapped for wool is put
     * back in stone. That makes the test cheap and total — there is no list of the
     * ways a block can go missing to keep up to date, which is the same argument
     * the block census makes about damage.
     *
     * <p><strong>The block, not the block state.</strong> A blueprint's state is
     * what was laid on the day and very little of it stays that way: a crop ages,
     * farmland dries and wets, leaves recompute their distance, a fence or a wall
     * or a pane re-joins the moment anything is placed beside it, water fills a
     * waterloggable slot. Comparing states would call every one of those a missing
     * block — inflating the bill for a repair, sending a crew to re-lay a sound
     * wall, and having the unwatched patch reset a field's grown wheat to seed.
     * What a repair is about is blocks that are gone or made of the wrong thing.
     * A stair that a player turned around is left turned around, which is the
     * right trade: it is somebody's decision, not damage.
     *
     * <p>A cell nobody can see is never owed. Half a reading is worse than none:
     * a repair judged across the edge of the loaded area would find the far half
     * of the building absent and set about rebuilding it out of chunks that simply
     * were not there to be looked at.
     */
    static boolean isOwed(Placement planned, Standing world) {
        BlockState there = world.at(planned.pos());
        return there != null && !there.is(planned.state().getBlock());
    }

    /**
     * Which entries of a plan a later entry writes over.
     *
     * <p><strong>A plan is not a set of cells and never was.</strong> Nearly
     * every shape in this file lays a course across a wall and then drops the
     * corner post into the ends of it: the cottage writes oak planks and then an
     * oak log into the same fourteen cells, the town hall into twenty-six, the
     * library into forty-six. Laid in order that is correct and invisible — the
     * log is what stands there afterwards, which is what the shape meant.
     *
     * <p>Read as a diff it is a disaster, and every part of a repair reads the
     * plan as a diff. The planks step can never be satisfied: a log stands in its
     * cell the moment the building is finished, so a sound cottage owes fourteen
     * blocks forever. That inflated the price of every repair by more than the
     * damage that triggered it, sent the crew to knock the corner logs out and
     * put planks in their place before laying the logs back, and left the job's
     * bill permanently a few blocks ahead of the work it was possible to do —
     * so a watched repair could only ever end by timing out against
     * {@code Settlement.WATCHED_BUILD_GRACE_STEPS}.
     *
     * <p>So the rule the world already obeys is applied to the plan before the
     * plan is compared with the world: at any cell written more than once, only
     * the last write counts. It is asked of {@link Cell} rather than of a step or
     * a placement so the drawing and the build sequence answer it the same way.
     */
    private static boolean[] supersededIn(List<? extends Cell> cells) {
        Map<BlockPos, Integer> lastAt = new HashMap<>(cells.size() * 2);
        for (int i = 0; i < cells.size(); i++) {
            lastAt.put(cells.get(i).pos(), i);
        }
        boolean[] overwritten = new boolean[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            overwritten[i] = lastAt.get(cells.get(i).pos()) != i;
        }
        return overwritten;
    }

    /** The blocks of a drawn plan the world does not already hold: the repair diff. */
    static List<Placement> owedOf(List<Placement> planned, Standing world) {
        boolean[] overwritten = supersededIn(planned);
        List<Placement> owed = new ArrayList<>();
        for (int i = 0; i < planned.size(); i++) {
            if (!overwritten[i] && isOwed(planned.get(i), world)) {
                owed.add(planned.get(i));
            }
        }
        return owed;
    }

    private static boolean isOwed(Step step, Standing world) {
        return isOwed(new Placement(step.pos(), step.state(), step.nbt()), world);
    }

    /**
     * A plan as a repair reads it: one write per cell, and no digging at all.
     *
     * <p>Both halves are the difference between mending a building and building
     * it. The superseded writes go because a repair is a diff and a cell written
     * twice can never match a world that holds one block — see
     * {@link #supersededIn}. The excavation goes because it is every cell of the
     * footprint that holds something, and for a building still standing that is
     * the building: a crew sent to mend one wall would open by taking out the
     * other three, the floor and the roof.
     *
     * <p>Collapsed here, once, where the plan is cached, rather than at each of
     * the half-dozen places that read it. The cursor a repair saves to disk is an
     * index into this list, so it has to be the same list every time it is asked
     * for — which is also why it is keyed as its own cache entry.
     */
    private static StructurePlan asRepair(StructurePlan plan) {
        boolean[] overwritten = supersededIn(plan.steps());
        List<Step> once = new ArrayList<>(plan.steps().size());
        for (int i = 0; i < plan.steps().size(); i++) {
            if (!overwritten[i]) {
                once.add(plan.steps().get(i));
            }
        }
        // No digs and no stripping. A repair puts back what a building has lost;
        // it does not re-open the site, and a branch that has grown back over a
        // roof in the years since is scenery rather than damage.
        return new StructurePlan(plan.width(), plan.depth(), plan.height(),
                List.copyOf(once), List.of(), List.of(), false, plan.notch());
    }

    /**
     * Whether the whole of a plan can be read at all.
     *
     * <p>A cell in a chunk nobody has loaded is never owed — see {@link #isOwed}
     * — which is right for judging a wall and wrong for declaring one finished.
     * "Nothing is missing" and "nothing can be seen" arrive at the same figure by
     * opposite routes, and a repair that acted on the first when the second was
     * true would report a hole as filled and hand the town a shell to re-baseline
     * its census against. So the two are separated wherever a zero is about to be
     * treated as good news.
     */
    private static boolean allVisible(StructurePlan plan, Standing world) {
        for (Step step : plan.steps()) {
            if (world.at(step.pos()) == null) {
                return false;
            }
        }
        return true;
    }

    /** What a repair is worth: the cost of the steps the building is short of. */
    private static int owedWork(ServerLevel level, StructurePlan plan) {
        Standing world = standingIn(level);
        int owed = 0;
        for (Step step : plan.steps()) {
            if (isOwed(step, world)) {
                owed += step.cost();
            }
        }
        return owed;
    }

    /**
     * The turn of this blueprint that best matches what is standing here.
     *
     * <p>A repair is only ever the difference between the plan and the wall, so
     * everything depends on drawing the plan the way the wall actually runs. The
     * town's own record cannot be trusted for that: a building raised by hand was
     * laid unturned while its record says it faces the center — see
     * {@link #planOf} — and any save from before that was noticed holds a mixture
     * of the two.
     *
     * <p>So the wall is asked instead of the books. Four turns, four counts of
     * what would be owed, and the one that owes least is the one the building is
     * standing in; a wrong turn reads as nearly the whole structure missing, so
     * on a building that is mostly there the answer is not close.
     *
     * <p><strong>And only then.</strong> That argument is exactly as good as the
     * wall it is made from, and repairs are booked for buildings with holes in
     * them. At four fifths gone all four turns owe nearly everything and the
     * winner is decided by whichever handful of blocks happened to survive — so a
     * near-razed cottage would be put back rotated, which is worse than any hole.
     * The record is therefore kept unless the wall disagrees with it loudly: half
     * the work or better. Below that the books win, which is also what happens
     * for a symmetrical shape, where the question does not matter at all.
     */
    private static int fittedFacing(ServerLevel level, String blueprintId, BlockPos base,
                                    int recorded) {
        int keep = Math.floorMod(recorded, 4);
        int best = keep;
        int fewest = Integer.MAX_VALUE;
        int owedAsRecorded = 0;
        for (int turn = 0; turn < 4; turn++) {
            int facing = Math.floorMod(recorded + turn, 4);
            int owed = owedWork(level, asRepair(planFor(level, blueprintId, base, facing)));
            if (turn == 0) {
                owedAsRecorded = owed;
            }
            if (owed < fewest) {
                fewest = owed;
                best = facing;
            }
        }
        return fewest * 2 <= owedAsRecorded ? best : keep;
    }

    /**
     * Puts back only the blocks a standing structure is missing.
     *
     * <p>The unwatched half of a repair, and deliberately not {@link #place}:
     * nothing is dug, nothing sound is touched, and a building that turns out to
     * be whole costs a pass of block reads and no writes at all. See
     * {@code WorldBridge.repairBlueprint} for why the distinction is the point.
     *
     * <p><strong>All of the plot or none of it.</strong> A building is eleven or
     * more across and routinely straddles a chunk boundary, and the caller of this
     * is the clock, which runs precisely when nobody is standing there — so half a
     * plot loaded is the ordinary case rather than the exotic one. Laying what can
     * be reached and reporting a number would be the worst possible answer: the
     * caller reads any number as "the repair happened", clears the damage, and
     * retakes the census against a building that is still half open. Whatever is
     * left of the hole then becomes its recorded sound size for good.
     *
     * @return how many blocks went back, or {@code -1} if any part of the plan is
     *         on ground nobody can see, in which case nothing was laid at all
     */
    public static int patch(ServerLevel level, String blueprintId, BlockPos base, int facing) {
        if (!level.isLoaded(base)) {
            return -1;
        }
        StructurePlan plan = asRepair(planFor(level, blueprintId, base,
                fittedFacing(level, blueprintId, base, facing)));
        Standing world = standingIn(level);
        if (!allVisible(plan, world)) {
            return -1;
        }
        int mended = 0;
        for (Step step : plan.steps()) {
            if (!isOwed(step, world)) {
                continue;
            }
            lay(level, new Placement(step.pos(), step.state(), step.nbt()));
            mended++;
        }
        return mended;
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
        Site site = siteAt(level, base);

        Optional<LoadedBlueprint> authored = Blueprints.loadFirst(level, base,
                styleCandidates(id, site.culture()), rotation, Mirror.NONE);
        if (authored.isPresent()) {
            return fromBlueprint(level, site, authored.get(), base,
                    BuildPlanner.baseIdOf(id.getPath()));
        }
        // Styles degrade too: with no norman/house drawn, a norman town still
        // gets the built-in house rather than an unknown-blueprint marker. So does
        // a level nobody has drawn — it falls back to the plain shape, grown.
        String path = BuildPlanner.baseIdOf(id.getPath());
        int tier = BuildPlanner.levelOf(id.getPath());
        return procedural(level, site, path.substring(path.lastIndexOf('/') + 1),
                base, rotation, tier);
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
     * meant every comparison of a blueprint id against a catalog row —
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
     * that says nothing is centered on its own footprint, which is what this
     * always did. Honoring the stated one is what stops an imported building
     * sitting beside its plot instead of on it.
     *
     * <p>The blueprint itself has no foundation — nobody draws one — so the same
     * cobble underpinning the procedural shapes get is laid beneath it, which is
     * what stops an authored building floating over a slope.
     */
    private static StructurePlan fromBlueprint(ServerLevel level, Site site,
                                               LoadedBlueprint blueprint,
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
        foundation(site, blocks, base, size.getX(), size.getZ());
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
     * A structure with no interior at all gets it at the center regardless:
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
    static Block postFor(String path) {
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
            case "hut" -> KingdomsBlocks.HUT.get();
            case "great_hut" -> KingdomsBlocks.GREAT_HUT.get();
            case "longhouse" -> KingdomsBlocks.LONGHOUSE.get();
            case "croft" -> KingdomsBlocks.CROFT.get();
            case "library" -> KingdomsBlocks.LIBRARY.get();
            case "mill" -> KingdomsBlocks.MILL.get();
            case "carpentry" -> KingdomsBlocks.CARPENTRY.get();
            case "inn" -> KingdomsBlocks.INN.get();
            default -> null;   // stairs and anything else that is not a building
        };
    }

    /**
     * Draws one built-in shape, facing south, and says how big it came out.
     *
     * <p>Split out of {@link #procedural} so that the size a shape reports can be
     * had without a world. Everything a builder needs to know about where it is
     * standing goes through {@link Site}, and none of it can move a wall: the
     * ground decides how much cobble is packed in underneath and how far a
     * flight of steps runs, the culture decides how many pens a compound has,
     * and the width and depth come from {@link BuildingSizes} either way. So the
     * numbers this returns against a flat fake site are the numbers it returns
     * on a hillside, which is what {@code BlueprintPlacerSizeTest} relies on to
     * compare all twenty-five of these against the table the catalog reserves
     * ground from.
     *
     * <p>The style folder is stripped the same way {@link #postFor} strips it,
     * rather than being left as a precondition on the caller: an id that reached
     * here as {@code norman/house} would otherwise fall through to the
     * unknown-blueprint marker and be built as a five-by-five slab of stone
     * bricks, silently, while everything else in this file went on calling it a
     * house.
     *
     * @return {@code {width, depth, height}}, before any rotation
     */
    static int[] draw(Site site, List<Placement> blocks, String blueprintPath, BlockPos base) {
        String path = blueprintPath.substring(blueprintPath.lastIndexOf('/') + 1);
        int[] dims = switch (path) {
            case "town_hall" -> townHall(site, blocks, base);
            case "house" -> house(site, blocks, base);
            case "granary" -> granary(site, blocks, base);
            case "farm" -> farm(site, blocks, base);
            case "market" -> market(site, blocks, base);
            case "lumber_camp" -> lumberCamp(site, blocks, base);
            case "mine" -> mine(site, blocks, base);
            case "warehouse" -> warehouse(site, blocks, base);
            case "smith" -> smith(site, blocks, base);
            case "animal_farm" -> animalFarm(site, blocks, base);
            case "stairs" -> accessStairs(site, blocks, base);
            case "watchtower" -> watchtower(site, blocks, base);
            case "storehouse" -> storehouse(site, blocks, base);
            case "camp_post" -> campPost(site, blocks, base);
            case "cache" -> cache(site, blocks, base);
            case "bunkhouse" -> bunkhouse(site, blocks, base);
            case "hearth" -> hearth(site, blocks, base);
            case "cottage" -> cottage(site, blocks, base);
            case "hut" -> hut(site, blocks, base);
            case "great_hut" -> greatHut(site, blocks, base);
            case "longhouse" -> longhouse(site, blocks, base);
            case "croft" -> croft(site, blocks, base);
            case "library" -> library(site, blocks, base);
            case "mill" -> mill(site, blocks, base);
            case "carpentry" -> carpentry(site, blocks, base);
            case "inn" -> inn(site, blocks, base);
            case "workshop" -> workshop(site, blocks, base);
            default -> marker(blocks, base);
        };
        if (path.equals("town_hall")) {
            // The gold used to go here, at the top of the wall course, because a
            // hall had no roof for it to stand on. It now finishes the cupola —
            // see CivicParts#cupola — because a hall with a pitched roof buries a
            // block at that height in its own attic, which is a marker nobody in
            // the world can see.
            add(blocks, base.offset(0, 1, -1), KingdomsBlocks.TOWN_HALL.get());
            // The board hangs in the hall: one place to read what the town wants.
            add(blocks, base.offset(-2, 1, -2), KingdomsBlocks.QUEST_BOARD.get());
        }
        if (path.equals("house")) {
            add(blocks, base.offset(-1, 1, -1), KingdomsBlocks.HOUSE.get());
            // Four along the cold wall, one per head the catalog says a house
            // holds. Here rather than in a shape of its own because a house is
            // drawn as a plain cabin and always has been.
            beds(site, blocks, base, "house");
        }
        return dims;
    }

    /**
     * The built-in shapes. One size each, whatever level the record claims.
     *
     * <p>They used to grow with tier — two blocks broader per level — and that
     * is what stacked a town. A plot is reserved from the catalog's declared
     * span and a neighbor is sited against that span, but the drawn building
     * answered to its own level instead. A house is declared eleven across;
     * drawn it is seven at level one, eleven at level three, and
     * <strong>thirteen at level four</strong>. The fourth one grows straight
     * through whatever was next door, and the town hall does the same at
     * fifteen against a plot of thirteen.
     *
     * <p>Upgrading is gone from the planner, so nothing new reaches tier two.
     * The growth goes with it rather than being left armed: a save that already
     * holds a level-three house draws it at the one size that is known to fit,
     * which is smaller than the ground reserved for it and therefore safe.
     *
     * <p>If levels ever come back, the size drawn here must be checked against
     * {@code BuildPlanner.plotSpanOf} rather than assumed to fit. That is the
     * whole lesson and it is why this comment is longer than the line it guards.
     * The check itself is no longer a hope: {@code BlueprintPlacerSizeTest} draws
     * every one of these against a fake site and compares its width and depth
     * with {@link BuildingSizes}, so a shape that outgrows its plot fails a build
     * rather than waiting to be noticed from the air.
     */
    private static StructurePlan procedural(ServerLevel level, Site site, String path,
                                            BlockPos base, Rotation rotation, int tier) {
        List<Placement> blocks = new ArrayList<>();
        int[] dims = draw(site, blocks, path, base);
        turn(blocks, base, rotation);
        // A quarter turn swaps the footprint's axes along with it.
        boolean quarter = rotation == Rotation.CLOCKWISE_90
                || rotation == Rotation.COUNTERCLOCKWISE_90;
        BuildingSizes.Size declared = BuildingSizes.of(path);
        BuildingSizes.Notch notch = declared == null
                ? BuildingSizes.Notch.NONE : declared.notch();
        // Says so, loudly, when a drawing method and the table disagree.
        //
        // The table is what reserves the ground, so a builder that draws
        // something else is the original fault wearing a new hat. It has already
        // happened once since the table existed: the market was converted to read
        // its size, kept a literal in its return statement, and went on being
        // drawn five wide on nine blocks of reserved ground. Nothing threw. It
        // was found by diffing a run's log against the table.
        //
        // BlueprintPlacerSizeTest now makes the same comparison at build time for
        // every shape in the switch, so this is no longer the only thing standing
        // between that fault and a live world. It stays because it can see one
        // thing the test cannot: a size that came out wrong because of where the
        // building was put -- a culture nobody thought to try, a site that made
        // some future shape answer differently -- rather than because of what
        // was written in the switch.
        boolean over = declared != null
                && (dims[0] > declared.width() || dims[1] > declared.depth());
        boolean under = declared != null && !BuildingSizes.variesWithCulture(path)
                && (dims[0] < declared.width() || dims[1] < declared.depth());
        if (over || under) {
            KingdomsMod.LOGGER.error(
                    "SIZE MISMATCH {} is declared {}x{} and drawn {}x{}: the plan has"
                            + " reserved the wrong amount of ground for it",
                    path, declared.width(), declared.depth(), dims[0], dims[1]);
        }
        return finish(level, base, blocks,
                quarter ? dims[1] : dims[0], quarter ? dims[0] : dims[1], dims[2],
                quarter ? turned(notch, rotation) : notch);
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
        return finish(level, base, blocks, width, depth, height,
                BuildingSizes.Notch.NONE);
    }

    /**
     * The same, for a building with a corner cut out of it.
     *
     * <p>The notch is not decoration and it is not only about where blocks go.
     * The excavation and the apron below square off a box around the origin, and
     * a box is exactly what an L is not: without this the yard in the crook is
     * dug out, scraped level and left as bare ground, and the building reads as
     * a rectangle somebody forgot to finish rather than as a house round a yard.
     */
    private static StructurePlan finish(ServerLevel level, BlockPos base, List<Placement> blocks,
                                        int width, int depth, int height,
                                        BuildingSizes.Notch notch) {
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
        Footprint shape = new Footprint(0, width, depth, height, notch);
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (shape.inNotch(dx, dz)) {
                    continue;   // the yard, which is ground rather than a site
                }
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
        // Only natural ground is taken. Aprons of neighboring plots can meet,
        // and a rule that ate anything in reach would quietly chew a hole in the
        // house next door — or eat the doorstep the neighbor just laid.
        int ax = rx + APRON_MARGIN;
        int az = rz + APRON_MARGIN;
        for (int dx = -ax; dx <= ax; dx++) {
            for (int dz = -az; dz <= az; dz++) {
                if (Math.abs(dx) <= rx && Math.abs(dz) <= rz && !shape.inNotch(dx, dz)) {
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
                    // Ground that stands over the floor line, and growth that
                    // stands in the way of a person. Snow, grass and flowers are
                    // left exactly where they are — nothing is gained by
                    // clearing what was never blocking anything.
                    //
                    // Growth is here because leaving it out was a real defect
                    // rather than an oversight of taste. isNaturalGround is
                    // shovel work and base stone; leaves are hoe work and logs
                    // are axe work, so a tree standing against a wall was never
                    // touched by the apron. Every "no way in" report in a live
                    // audit turned out to be exactly that: the doorway was cut,
                    // the doorstep was laid, there was solid ground to stand on
                    // — and a tree was growing in it. Six of seven read "head
                    // hits oak_leaves" at the one block a person's head goes.
                    //
                    // A log added here does not become a three-block hole in a
                    // trunk: Excavation.reduceTrees swaps any log or leaf for
                    // the stump it stands on, so the whole tree comes down in
                    // one job, as it already does inside the footprint.
                    if (needsDigging(standing)
                            && (isNaturalGround(standing) || isGrowth(standing))) {
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
        Set<BlockPos> digTargets = new LinkedHashSet<>();
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

        // And the sky over the plot, which is the other half of clearing a site
        // and used to be no part of it at all. Everything above only ever cut
        // three blocks of headroom round the doorstep, so a building raised in a
        // forest was finished with the canopy of the next tree across its roof
        // and trunks standing against its walls: the crew had cleared precisely
        // the cells they were going to write in, and a branch six blocks up was
        // in nobody's way by that measure.
        //
        // Added after the test above rather than through it, deliberately. Ground
        // cover is replaceable by definition, so needsDigging says — quite
        // correctly, for a cell a course is about to be laid in — that it does not
        // need digging; over a roof there is no course coming, and litter left on
        // the eaves is exactly the complaint.
        //
        // Growth only. Stone and ore standing over a plot are the hillside and
        // belong to the excavation box, which is the building's own size and stops
        // at its roof; this is a canopy rule, and cutting a shaft to the build
        // limit through a mountain is not what anybody meant by it.
        //
        // Only the half of it somebody can get at is dug: see Overgrowth.Clearing.
        // Wood at any height is reachable, because a log is swapped for the stump
        // of its own trunk and felled from the ground; a crown eight blocks over a
        // roof is reachable from nowhere at all.
        Overgrowth.Clearing clearing = Overgrowth.overPlot(Overgrowth.over(level), base,
                plotOf(base.getY(), width, depth, height, notch),
                Overgrowth.woodlandAround(level, base));
        digTargets.addAll(clearing.dug());

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
        return new StructurePlan(width, depth, height, steps,
                List.copyOf(digTargets), clearing.stripped(), blocked, notch);
    }

    /**
     * The same bite, on the building after it has been turned.
     *
     * <p>Only the two quarter turns reach here, and only they need to: a
     * half turn keeps both axes and merely swaps both signs, which the corner
     * arithmetic already handles, while a quarter turn swaps the axes as well.
     * Getting this wrong puts the yard on the wrong side of the house, which is
     * invisible in a test and obvious from the air.
     */
    private static BuildingSizes.Notch turned(BuildingSizes.Notch notch, Rotation rotation) {
        if (!notch.isCut()) {
            return notch;
        }
        if (rotation == Rotation.CLOCKWISE_90) {
            return new BuildingSizes.Notch(notch.depth(), notch.width(),
                    -notch.towardZ(), notch.towardX());
        }
        return new BuildingSizes.Notch(notch.depth(), notch.width(),
                notch.towardZ(), -notch.towardX());
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
     * A mine head: a squat stone hut with a timber headframe standing over it.
     *
     * <p>No shaft is dug at build time — the miners cut down from where they are
     * standing, which is the point of the post. What this adds is the thing that
     * says so from outside: four legs straddling the origin column, up through
     * the roof and capped with a frame, and a stub of rail running out of the
     * door. The roof is held down to two courses on purpose, whatever the local
     * pitch: a pit head is a lid over a hole, and the headframe has to be the
     * tallest thing on the plot or it is not a headframe.
     *
     * <p><strong>The floor stays cobble and this is not a taste.</strong>
     * {@code MinerWorker} cuts anything in the stone-brick or base-stone tags
     * from the mine's own origin course downward, so a floor laid in the
     * highlander's {@code STONE} or the burgher's {@code STONE_BRICKS} would be a
     * mine whose first act is to quarry its own floor out from under itself.
     * Cobble is in neither tag. Everything above the floor line is out of that
     * search and may be whatever the people build in.
     */
    private static int[] mine(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("mine");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int wallHeight = 3;
        cabin(site, blocks, base, size, wallHeight, style.plinth(), style.frame());
        TradeParts.floorOf(blocks, base, size, Blocks.COBBLESTONE);
        TradeParts.dressLow(blocks, base, size, wallHeight, style, 2);
        // Legs of timber, cap of board. The cap is the top of its own column and
        // a log at the top of a village column is a tree as far as
        // LumberjackWorker is concerned — so a headframe capped in logs is a
        // headframe the town's own axemen take down again.
        TradeParts.headframe(blocks, base, 2, 7,
                TradeParts.logFor(style), style.roofRidge());
        // The way the stone leaves: out of the shaft mouth and through the door.
        for (int dz = 0; dz <= size.depth() / 2 + BuildingSizes.APRON; dz++) {
            add(blocks, base.offset(0, 1, dz), Blocks.RAIL);
        }
        add(blocks, base.offset(-1, 1, -1), KingdomsBlocks.MINE.get());
        add(blocks, base.offset(1, 1, -1), Blocks.FURNACE);
        add(blocks, base.offset(1, 2, -1), Blocks.COBBLESTONE_SLAB);
        add(blocks, base.offset(2, 1, 1), Blocks.LANTERN);
        return measured(blocks, base, size, from);
    }

    /**
     * The warehouse: the storehouse again, twice as tall, with its stair outside.
     *
     * <p>The same building and deliberately so — a wide door under a canopy, and
     * barrels against every wall — but with a second floor over it and the way up
     * running up the outside. An external stair is what tells you a store has an
     * upper floor without your having to go in and find one, and it is how a real
     * warehouse is built, because a flight of steps inside is floor that could
     * have held goods.
     */
    private static int[] warehouse(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("warehouse");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int wallHeight = 7;
        int deckY = 4;
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        cabin(site, blocks, base, size, wallHeight, style.wall(), style.frame());
        TradeParts.widenDoor(blocks, base, size, -1, 1);
        TradeParts.canopy(blocks, base, size, 3, 2, style.post(), style.roofRidge());
        Parts.dress(blocks, base, size, wallHeight, style);
        Parts.windowRow(blocks, base, size, deckY + 2, style.wall(),
                HouseStyle.Variation.forOrigin(base).windowSpacing());

        // The upper floor, and the way onto it. The deck is whole: the stair is
        // outside, so nothing has to be left open in it.
        TradeParts.deck(blocks, base, size, deckY, rx, rz, style.wall());
        int landing = TradeParts.outsideStair(blocks, base, size, -1, deckY,
                style.roofStairs(), style.plinth());
        for (int y = deckY + 1; y <= deckY + 2; y++) {
            add(blocks, base.offset(-rx, y, landing), Blocks.AIR);
        }

        for (int dx = -rx + 1; dx <= rx - 1; dx += 2) {
            add(blocks, base.offset(dx, 1, -rz + 1), Blocks.BARREL);
            add(blocks, base.offset(dx, 2, -rz + 1), Blocks.BARREL);
            add(blocks, base.offset(dx, deckY + 1, -rz + 1), Blocks.BARREL);
            add(blocks, base.offset(dx, deckY + 2, -rz + 1), Blocks.BARREL);
            add(blocks, base.offset(dx, deckY + 1, rz - 1), Blocks.BARREL);
        }
        add(blocks, base.offset(rx - 1, 1, rz - 1), Blocks.CHEST);
        add(blocks, base.offset(0, deckY + 1, 0), Blocks.LANTERN);
        add(blocks, base.offset(0, 1, -2), KingdomsBlocks.WAREHOUSE.get());
        return measured(blocks, base, size, from);
    }

    /**
     * A smithy: an open forge bay under a brick chimney, and a floor of stone.
     *
     * <p>Three things say smithy from the far side of the green, and none of them
     * is the anvil. The <strong>bay</strong> — one whole wall gone, standing on
     * two posts under a lintel — because a forge has to breathe and its fire has
     * to be seen. The <strong>chimney</strong>, in fired brick whatever the
     * people build the rest in, climbing clear of the ridge. And the fact that
     * the whole thing is <strong>masonry</strong>: this is the one trade whose
     * walls are the people's stone rather than their timber, because a hearth in
     * a plank shed is a fire, and because a town that has to find two hundred
     * blocks of stone for its smithy is a town for which the smithy is a
     * decision.
     */
    private static int[] smith(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("smith");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int wallHeight = 3;
        int rx = size.width() / 2;
        cabin(site, blocks, base, size, wallHeight, style.plinth(), style.frame());
        // Before the dressing, so the plinth, the timbering and the window row
        // all find air where the bay is and leave it open.
        TradeParts.openBay(blocks, base, size, -1, wallHeight,
                style.frame(), style.roofRidge());
        Parts.dress(blocks, base, size, wallHeight, style);
        // After it, because a chimney has to know how high the ridge came out.
        Parts.chimney(blocks, base, rx, -1, 1, Parts.topOf(blocks, base, from) + 2,
                Blocks.BRICKS);

        add(blocks, base.offset(-1, 1, -1), KingdomsBlocks.SMITH.get());
        add(blocks, base.offset(rx - 1, 1, -1),
                TradeParts.facing(Blocks.BLAST_FURNACE, Direction.WEST));
        add(blocks, base.offset(2, 1, 1), Blocks.ANVIL);
        // The slack tub. A smith quenches more often than he grinds.
        add(blocks, base.offset(3, 1, 1), Blocks.WATER_CAULDRON);
        add(blocks, base.offset(-2, 1, 1), Blocks.GRINDSTONE);
        return measured(blocks, base, size, from);
    }

    /**
     * A fenced compound split into pens, one per beast the culture keeps.
     *
     * <p>Pens are strips rather than a grid: a strip is trivially separated by a
     * single run of fence, and separation is the whole requirement — cows must
     * not end up in with the chickens.
     */
    private static int[] animalFarm(Site site, List<Placement> blocks, BlockPos base) {
        // The culture of the town whose ground this is, not the default one.
        // ShepherdWorker has always stocked these pens from the settlement's own
        // culture while this sized them from the default — which agreed only for
        // as long as there was one culture, and would have penned a highland
        // town's goats into a compound built for somebody else's herd.
        // Clamped to the ground the catalog reserved. The compound's depth
        // is a culture's business -- a people that keeps five beasts wants five
        // pens -- but the plot it stands on was staked before anybody asked, so
        // a sixth pen would be a fence through the neighbor's wall rather than
        // a bigger farm.
        BuildingSizes.Size size = sized("animal_farm");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        Block fence = style.post();
        int penDepth = 3;
        int room = (size.depth() - 1) / (penDepth + 1);
        int pens = Math.min(room, Math.max(1, site.culture().penCount()));
        int width = size.width();
        int depth = pens * penDepth + pens + 1;
        int rx = width / 2;
        int rz = depth / 2;

        foundation(site, blocks, base, width, depth);
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                add(blocks, base.offset(dx, 0, dz), Blocks.GRASS_BLOCK);
                boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz;
                // A divider every penDepth+1 rows walls one pen off from the next.
                boolean divider = Math.floorMod(dz + rz, penDepth + 1) == 0;
                if (edge || divider) {
                    add(blocks, base.offset(dx, 1, dz), fence);
                }
            }
        }
        // One gate per pen, all down the same side, so every pen can be walked into.
        for (int pen = 0; pen < pens; pen++) {
            int dz = -rz + 1 + pen * (penDepth + 1);
            add(blocks, base.offset(-rx, 1, dz), TradeParts.gateFor(fence));
        }
        add(blocks, base.offset(0, 1, -rz + 1), KingdomsBlocks.ANIMAL_FARM.get());
        // A byre at the head of the first pen: feed, a trough, and a roof over
        // both. Set against the far side from the gates and clear of the middle
        // of the strip, which is where ShepherdWorker puts a beast down.
        TradeParts.byre(blocks, base, rx - 1, -rz + 1,
                TradeParts.logFor(style), TradeParts.boardFor(style), Blocks.HAY_BLOCK);
        return new int[]{width, depth, 4};
    }

    /**
     * A granary: a stone stand, staddle piers under the eaves, and a slatted end.
     *
     * <p>A barn you can see into. The end away from the door is opened out — the
     * top course of wall and the whole gable triangle replaced by slats — so the
     * hay stacked against it is visible from the street, which is the difference
     * between a granary and a shed that happens to hold grain. It is also what a
     * real one does: grain that cannot breathe is grain that rots.
     *
     * <p>The stand is stone, the floor included, and four piers stand out under
     * the eave corners. A granary that sits on the earth is a granary the rats
     * have.
     *
     * <p><strong>It is not raised a whole course, and that is a compromise.</strong>
     * The proper thing is a deck lifted a block clear of the ground on staddle
     * stones with daylight underneath. The granary post stands at the floor
     * course at a fixed offset, and lifting the deck would either bury it or
     * shut it under the building where nobody could read it. So the stand is a
     * stone one at grade with its piers showing, and the post stays where
     * everything that looks for it expects to find it.
     */
    private static int[] granary(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("granary");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int wallHeight = 3;
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        cabin(site, blocks, base, size, wallHeight, style.wall(), style.frame());
        TradeParts.floorOf(blocks, base, size, style.plinth());
        Parts.dress(blocks, base, size, wallHeight, style);
        TradeParts.loftVent(blocks, base, size, 1, wallHeight,
                style.roofFor(size) == HouseStyle.Roof.GABLE, style.post());
        // Staddle piers, out in the doorstep ring under the corners of the eave.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = 1; y <= 2; y++) {
                    add(blocks, base.offset(sx * rx, y,
                            sz * (rz + BuildingSizes.APRON)), style.plinth());
                }
            }
        }
        // The stack, three courses against the slatted end so it shows through.
        for (int dz = -2; dz <= 0; dz++) {
            for (int y = 1; y <= wallHeight; y++) {
                add(blocks, base.offset(rx - 1, y, dz),
                        TradeParts.lying(Blocks.HAY_BLOCK, true));
            }
        }
        TradeParts.ladder(blocks, base, rx - 2, -1, 1, wallHeight, Direction.WEST);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.GRANARY.get());
        add(blocks, base.offset(-rx + 1, 1, 1), Blocks.BARREL);
        add(blocks, base.offset(-rx + 1, 1, -1), Blocks.COMPOSTER);
        return measured(blocks, base, size, from);
    }

    /**
     * A lumber camp: a hip roof on eight posts, open on all four sides.
     *
     * <p>Deliberately not a cabin. Every other trade in the mod works indoors and
     * this one works in the wood — what stands on the plot is where the timber is
     * stacked and where the axes are sharpened, not where anybody sits. So it has
     * no walls at all: a floor of trodden earth and boards, eight uprights, a
     * pyramid of a roof, and under it a log pile, a chopping block and a fire.
     *
     * <p>An open shelter is also the cheapest building in the mod, which is
     * right: the lumber camp is one of the three producers a town raises before
     * it has anything to raise them with — see {@code BuildPlanner.PRODUCER_OF}.
     */
    private static int[] lumberCamp(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("lumber_camp");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        int postHeight = 3;
        Block log = TradeParts.logFor(style);
        foundation(site, blocks, base, size.width(), size.depth());

        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                boolean yard = Math.abs(dx) == rx || Math.abs(dz) == rz;
                add(blocks, base.offset(dx, 0, dz),
                        yard ? Blocks.COARSE_DIRT : style.wall());
            }
        }
        // Corners and the middle of each side. Four posts under a seven-wide
        // roof reads as a lid on stilts; eight reads as a frame.
        for (int dx = -rx; dx <= rx; dx += rx) {
            for (int dz = -rz; dz <= rz; dz += rz) {
                if (dx == 0 && dz == 0) {
                    continue;   // the middle is where the work happens
                }
                for (int y = 1; y <= postHeight; y++) {
                    add(blocks, base.offset(dx, y, dz), TradeParts.upright(log));
                }
            }
        }
        Parts.hipRoof(blocks, base, size, postHeight + 1,
                style.roofStairs(), style.roofRidge(), 0);

        TradeParts.logPile(blocks, base, 2, -2, 3, 2, log);
        add(blocks, base.offset(-2, 1, 2), TradeParts.upright(log));   // the block
        add(blocks, base.offset(-2, 2, 2), Blocks.LANTERN);
        add(blocks, base.offset(1, 1, 2), Blocks.CAMPFIRE);
        add(blocks, base.offset(-1, 1, -1), KingdomsBlocks.LUMBER_CAMP.get());
        return measured(blocks, base, size, from);
    }

    /**
     * A home: the box, and everything its people put on a box.
     *
     * <p>The one door every house goes through, so that a style is applied in
     * one place and a sixth home cannot quietly be built in nobody's idiom.
     * {@link Parts#dress} decides what goes on; {@link HouseStyle} decides what
     * it is made of; this only knows which of the two boxes a footprint wants —
     * {@link #hall} for the one shape with a corner cut out of it, {@link #cabin}
     * for everything else.
     *
     * <p>Returns the declared size rather than the drawn dimensions, because a
     * home's height is no longer a number the box can report: a roof, and a
     * chimney over the roof, are drawn after the walls and by somebody else. See
     * {@link #measured}.
     */
    private static BuildingSizes.Size home(Site site, List<Placement> blocks, BlockPos base,
                                           String path, int wallHeight) {
        BuildingSizes.Size size = sized(path);
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        if (size.notch().isCut()) {
            hall(site, blocks, base, size, wallHeight, style.wall(), style.frame());
        } else {
            cabin(site, blocks, base, size, wallHeight, style.wall(), style.frame());
        }
        Parts.dress(blocks, base, size, wallHeight, style);
        return size;
    }

    /**
     * What a building turned out to be: its declared footprint, and the height
     * actually drawn.
     *
     * <p>The height is measured off the placements rather than added up from the
     * parts. A roof that rises with the depth and a chimney that has to clear
     * the ridge are two more pieces of arithmetic that could disagree with the
     * number reported here, and the site is cleared to that number — so a house
     * that under-reported would be a house with a hillside through its roof.
     *
     * @param from where this building's own placements start in the list
     */
    private static int[] measured(List<Placement> blocks, BlockPos base,
                                  BuildingSizes.Size size, int from) {
        return new int[]{size.width(), size.depth(), Parts.topOf(blocks, base, from) + 1};
    }

    /** A house: two households under one roof, and the commonest thing in a town. */
    private static int[] house(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = home(site, blocks, base, "house", 4);
        return measured(blocks, base, size, from);
    }

    /** A family's own house: the smallest roof a household can grow under. */
    private static int[] cottage(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = home(site, blocks, base, "cottage", 3);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.COTTAGE.get());
        beds(site, blocks, base, "cottage");
        add(blocks, base.offset(-1, 1, 1), Blocks.BARREL);
        return measured(blocks, base, size, from);
    }

    // --- the round ones ------------------------------------------------------

    /*
     * On the orc palette below.
     *
     * These four blocks are PLACEHOLDERS and are named as such in ORCS.md. A
     * hut's cone, its trim and its banding are written here rather than on
     * HouseStyle because HouseStyle is a column per people and a hut is a
     * building only one people raises -- putting a "cone material" on every
     * style would be five blank entries and one real one. When a second people
     * builds something round, or when any of this becomes a datapack entry, that
     * is the moment it moves.
     *
     * Spruce over the orc style's own cobblestone stairs, deliberately: a cone
     * of cobble on a seven-wide hut reads as a cairn. Timber and hide is what a
     * warband's roof is made of, and brown wool is the nearest thing the block
     * list has to hide.
     */

    /** What a hut's cone is laid in. Placeholder — see ORCS.md. */
    private static final Block HUT_ROOF_STAIRS = Blocks.SPRUCE_STAIRS;

    /** The full block at the cone's peak, and wherever two slopes meet. */
    private static final Block HUT_ROOF_RIDGE = Blocks.SPRUCE_PLANKS;

    /** Bone, which is what the warhost trims a roof with. Placeholder. */
    private static final Block HUT_TRIM = Blocks.BONE_BLOCK;

    /** Hide, as near as the block list gets to it. Placeholder. */
    private static final Block HUT_BANDING = Blocks.WOOL.pick(DyeColor.BROWN);

    /** How high a hut's wall stands before the cone starts. */
    private static final int HUT_WALL = 3;

    /** And the chief's, which is two courses taller because it is a hall. */
    private static final int GREAT_HUT_WALL = 5;

    /**
     * A roundhouse: octagonal walls with a cone of stairs over them.
     *
     * <p>{@link #hall} does the walls, because {@link #hall} was already the
     * general shape walker — it lays a wall wherever a covered cell has an
     * uncovered one beside it, which on an octagon gives eight faces and eight
     * corner posts without knowing what an octagon is. That is why the shape
     * language got a chamfer rather than the placer getting a second wall loop:
     * a building that is not a rectangle was already expressible, and only the
     * <em>kind</em> of not-a-rectangle was new.
     *
     * <p>The cone is {@link Parts#hipRoof}, uncapped. A hip takes its rise from
     * how far each cell is from the open air in any direction, so over a square
     * it is a pyramid and over an octagon it is a cone — the chamfered corners
     * are open air, so the courses step in on the diagonals as well as on the
     * faces and the roof comes to a point rather than to a ridge. Nothing in the
     * roof code had to learn about round buildings; it was measuring the shape
     * all along.
     *
     * <p><strong>It does read as a cone and not as a pyramid</strong>, which was
     * the open question: a seven-wide octagon rises four courses in rings of
     * 3-5-7-7-7-5-3, 5-7-7-7-5, 3-5-5-5-3 and a single block, and every course
     * steps in on eight sides. On a bare square the same code gives four courses
     * of nested squares, which is the pyramid this was trying not to be. Drawn
     * ring by ring rather than by the inset measure it would be the same blocks.
     *
     * @return the declared size, for {@link #measured}
     */
    private static BuildingSizes.Size roundhouse(Site site, List<Placement> blocks,
                                                 BlockPos base, String path,
                                                 int wallHeight) {
        BuildingSizes.Size size = sized(path);
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        hall(site, blocks, base, size, wallHeight, style.wall(), style.frame());
        Parts.plinth(blocks, base, size, 1, style.plinth(), style.wall(), style.frame());
        // A band of hide round the wall head, under the eaves. Written after the
        // plinth and before the roof, over the wall course the eave will
        // overhang -- so from outside it is the dark line a cone sits on.
        band(blocks, base, size, wallHeight, HUT_BANDING, style.wall());
        Parts.hipRoof(blocks, base, size, wallHeight + 1,
                HUT_ROOF_STAIRS, HUT_ROOF_RIDGE, 0);
        return size;
    }

    /**
     * One course of the wall, replaced, wherever the wall is actually standing.
     *
     * <p>The same rule every replacing part in {@link Parts} keeps: it writes
     * only over the block the wall already laid, so it can never close a window
     * or brick up a doorway.
     */
    private static void band(List<Placement> blocks, BlockPos base,
                             BuildingSizes.Size size, int y, Block with, Block wall) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        Map<BlockPos, Block> drawn = new HashMap<>();
        for (Placement placement : blocks) {
            drawn.put(placement.pos(), placement.state().getBlock());
        }
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                BlockPos pos = base.offset(dx, y, dz);
                if (drawn.get(pos) == wall) {
                    add(blocks, pos, with);
                }
            }
        }
    }

    /**
     * A hut: the round house a warband lives in, three to a roof.
     *
     * <p>What a cottage is to a village. Seven across with the corners cut, a
     * cone over it, and a fire in the middle of the floor — which is the whole
     * difference between this and a cabin with a pointed hat: a roundhouse has
     * no chimney because the smoke goes out through the thatch.
     */
    private static int[] hut(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = roundhouse(site, blocks, base, "hut", HUT_WALL);
        add(blocks, base.offset(0, 1, -2), KingdomsBlocks.HUT.get());
        beds(site, blocks, base, "hut");
        // A bone finial over the door, which is the one thing on a hut you can
        // see from the yard.
        add(blocks, base.offset(0, HUT_WALL + 1, 3), HUT_TRIM);
        add(blocks, base.offset(-2, 1, 1), Blocks.BARREL);
        return measured(blocks, base, size, from);
    }

    /**
     * The great hut: the chief's own roof, and where a warband's king lives.
     *
     * <p>The same building at thirteen across, which is the point of it. A
     * warband does not put its king in a different kind of house; it puts him in
     * a bigger one, on the middle of the muster yard, with the whole camp drawn
     * round it — see {@code OrcRingLayout}, which reserves the center for
     * exactly this.
     *
     * <p>Six beds and the first of them is the king's, by
     * {@code KingPlanner}'s rule rather than by anything drawn here: a bed is a
     * bed, and which one is his is decided by who lives under the roof.
     */
    private static int[] greatHut(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size =
                roundhouse(site, blocks, base, "great_hut", GREAT_HUT_WALL);
        add(blocks, base.offset(0, 1, -5), KingdomsBlocks.GREAT_HUT.get());
        beds(site, blocks, base, "great_hut");
        // The long fire down the middle of the hall, which is what everybody
        // sits round. Clear of the lantern hall() hangs at the origin.
        for (int dz = -1; dz <= 1; dz += 2) {
            add(blocks, base.offset(0, 1, dz), Blocks.CAMPFIRE);
        }
        // Bone over the door and at the four quarters of the wall head: a
        // trophy rack, which is how you tell the chief's hut from the air.
        add(blocks, base.offset(0, GREAT_HUT_WALL + 1, 6), HUT_TRIM);
        for (int side = -1; side <= 1; side += 2) {
            add(blocks, base.offset(side * 6, GREAT_HUT_WALL + 1, 0), HUT_TRIM);
        }
        add(blocks, base.offset(0, GREAT_HUT_WALL + 1, -6), HUT_TRIM);
        add(blocks, base.offset(-4, 1, 4), Blocks.BARREL);
        add(blocks, base.offset(4, 1, 4), Blocks.BARREL);
        return measured(blocks, base, size, from);
    }

    /**
     * Every bed this home holds, where {@link Beds} says they are.
     *
     * <p>The list is the simulation's, not this file's. A settler is sent to
     * their own bed by a pure function that never reads a block, so the one
     * thing that must never drift is where the placer puts a bed and where the
     * simulation believes it is — and the cheapest way to make two things agree
     * is to have only one of them.
     */
    private static void beds(Site site, List<Placement> blocks, BlockPos base, String path) {
        for (Beds.Slot slot : Beds.layoutOf(path)) {
            bed(site, blocks, base, slot);
        }
    }

    /**
     * Where somebody sleeps: a real bed, both halves, laid the right way round.
     *
     * <p>It used to be two blocks of wool, and the note explaining why said a bed
     * was out of reach because its two halves are block states that have to agree
     * with each other while construction lays one block at a time — the head goes
     * down, has no foot, and pops off before the builder gets to it. That is true
     * of a bed placed the way a <em>player</em> places one, and it is not true
     * here. {@link #lay} writes states as authored with {@code UPDATE_CLIENTS}
     * and nothing else, precisely so a door's lower half survives being laid on
     * its own; a bed half survives for exactly the same reason. Vanilla pops a
     * lone half when the block at its partner cell <em>changes</em>, and the only
     * thing this plan ever puts in that cell is the other half.
     *
     * <p>Free, like glass and crops. Not by exemption but by the same rule
     * everything else follows: {@link #materialFor} charges for what a pickaxe or
     * an axe takes out, and a bed is in neither tag — so it costs a town nothing,
     * which is what the wool it replaces cost.
     *
     * <p>The color is the culture's. It is the one piece of furniture there is
     * one of per person, so it is the cheapest thing in a building to say a
     * people with.
     */
    private static void bed(Site site, List<Placement> blocks, BlockPos base, Beds.Slot slot) {
        BlockState foot = Blocks.BED.pick(bedColor(site.culture())).defaultBlockState()
                .setValue(BedBlock.FACING, headingOf(slot))
                .setValue(BedBlock.PART, BedPart.FOOT);
        add(blocks, base.offset(slot.dx(), Beds.FLOOR_COURSE, slot.dz()), foot);
        add(blocks, base.offset(slot.headX(), Beds.FLOOR_COURSE, slot.headZ()),
                foot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    /**
     * A bed's {@code FACING}, which points from its foot to its head.
     *
     * <p>Held in the simulation as a unit offset rather than as a
     * {@code Direction}, because {@code Direction} is a Minecraft class and the
     * simulation must not know about those. This is the one line that turns the
     * one into the other.
     */
    private static Direction headingOf(Beds.Slot slot) {
        if (slot.headDz() != 0) {
            return slot.headDz() < 0 ? Direction.NORTH : Direction.SOUTH;
        }
        return slot.headDx() < 0 ? Direction.WEST : Direction.EAST;
    }

    /**
     * What color this people's beds are.
     *
     * <p>Kept here rather than on {@link Culture} because a dye is a Minecraft
     * idea and the culture table is pure simulation. A people not named here
     * sleeps under undyed white wool, which is what every bed in the mod was
     * until there were beds.
     */
    private static DyeColor bedColor(Culture culture) {
        return switch (culture == null ? "" : culture.id()) {
            // Townsfolk, who can afford madder and want it seen.
            case "kingdoms:human/burgher" -> DyeColor.RED;
            // Hill people, whose wool is the color the sheep grew it.
            case "kingdoms:human/highland" -> DyeColor.BROWN;
            // Vale folk get woad, the one dye a farming village makes itself.
            case "kingdoms:human/vale" -> DyeColor.LIGHT_BLUE;
            case "kingdoms:goblin/mire" -> DyeColor.GREEN;
            case "kingdoms:orc/warhost" -> DyeColor.BLACK;
            // The lowlanders, and anybody a datapack adds without an opinion.
            default -> DyeColor.WHITE;
        };
    }

    /**
     * A longhouse: one roof, six beds, three households.
     *
     * <p>The answer to a town that has run out of frontage rather than out of
     * ground. Three families in a row along a single hall takes one plot where
     * three cottages take three, and it is what a village with a long green and
     * a short high street actually did.
     */
    private static int[] longhouse(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = home(site, blocks, base, "longhouse", 4);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.LONGHOUSE.get());
        // Six beds down the cold wall, a bay apiece.
        beds(site, blocks, base, "longhouse");
        // A hearth at each end of the hall, because thirteen blocks is too long
        // to light and heat from the middle.
        for (int dx = -5; dx <= 5; dx += 10) {
            add(blocks, base.offset(dx, 1, 2), Blocks.CAMPFIRE);
            add(blocks, base.offset(dx, 4, 2), Blocks.LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        add(blocks, base.offset(3, 1, 2), Blocks.BARREL);
        add(blocks, base.offset(-3, 1, 2), Blocks.CRAFTING_TABLE);
        return measured(blocks, base, size, from);
    }

    /**
     * A croft: a house bent round two sides of its own yard.
     *
     * <p>The one building in the mod that is not a rectangle, and the reason the
     * shape language in {@link BuildingSizes} exists at all. The corner away
     * from the door is cut out and stays as ground — not dug, not scraped, not
     * claimed — so the crook of the L is a yard somebody could keep a pig in
     * rather than a notch in a floor plan.
     */
    private static int[] croft(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = home(site, blocks, base, "croft", 4);
        add(blocks, base.offset(0, 1, 3), KingdomsBlocks.CROFT.get());
        // Three beds up the wing, three along the range: six, and the household
        // is split between the two arms the way the building is.
        beds(site, blocks, base, "croft");
        add(blocks, base.offset(-5, 1, 2), Blocks.CAMPFIRE);
        add(blocks, base.offset(-3, 1, 2), Blocks.BARREL);
        add(blocks, base.offset(-1, 1, 2), Blocks.CRAFTING_TABLE);
        // Fenced across the mouth of the yard, which is what turns a gap between
        // two wings into an enclosure. Hung open for the same reason every other
        // gate in this mod is: nothing here can work one, and a shut gate is a
        // wall to anything trying to path through it.
        for (int dz = -5; dz <= -2; dz++) {
            add(blocks, base.offset(6, 0, dz), Blocks.OAK_FENCE);
        }
        add(blocks, base.offset(6, 0, -5),
                Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
        return measured(blocks, base, size, from);
    }

    /**
     * The library: the building a town raises once it has more than it needs.
     *
     * <p>Twenty-three by seventeen, which is wider than the plan's own plot
     * pitch and nearly three times the span of a cottage. It is here as much to
     * prove the machinery as to be read in: until the sizes were declared in one
     * place, a building this big would have been drawn straight through whatever
     * was next to it, because the ground was reserved from a number in the
     * catalog and the walls were drawn from a literal in this file.
     */
    /** How high a library's walls stand: two storeys, because it has two floors. */
    private static final int LIBRARY_WALL = 7;

    private static int[] library(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("library");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        // Stone, whoever builds it — but this people's stone, and their stairs
        // and their shutters. A capped hip because a gable takes its rise from
        // the depth and this building is seventeen deep: the Norman roof that
        // suits a cottage would rise nine courses here and stand over the hall.
        HouseStyle masonry = CivicParts.walledIn(style, style.plinth(),
                HouseStyle.Roof.HIP, LIBRARY_ROOF_CAP);
        int rz = size.depth() / 2;

        cabin(site, blocks, base, size, LIBRARY_WALL, masonry.wall(), masonry.frame());
        Parts.dress(blocks, base, size, LIBRARY_WALL, masonry);
        CivicParts.archedWindows(blocks, base, size, 3, masonry.roofStairs());

        Set<BlockPos> clear = CivicParts.wayIn(base, size, 0, 0);
        CivicParts.shelves(blocks, base, size, clear);
        CivicParts.gallery(blocks, base, size, LIBRARY_GALLERY, 2,
                masonry.roofRidge(), style.post(), masonry.roofStairs());
        CivicParts.readingTable(blocks, base, 0, -2, CivicParts.slabOf(masonry.wall()));
        // Two heights of light: over the open middle from the roof, and under the
        // gallery from the gallery itself, because the ring beneath a mezzanine
        // is exactly the part of a big room that sits dark enough to spawn in.
        CivicParts.chandeliers(blocks, base, size, LIBRARY_WALL, 5);
        CivicParts.wallLanterns(blocks, base, size, 1, LIBRARY_GALLERY - 1, 4);
        add(blocks, base.offset(0, 1, -rz + 2), Blocks.CHISELED_BOOKSHELF);

        CivicParts.keepClear(blocks, clear);
        add(blocks, base.offset(0, 1, rz - 1), KingdomsBlocks.LIBRARY.get());
        return measured(blocks, base, size, from);
    }

    /** Where the gallery floor sits, and how far the roof over it may rise. */
    private static final int LIBRARY_GALLERY = 4;

    private static final int LIBRARY_ROOF_CAP = 3;

    /** The mill: a grindstone under a spruce roof, hay in every corner. */
    /**
     * The mill: a two-story tower with four sweeps on the wall away from its door.
     *
     * <p>A windmill is a silhouette before it is a building, and the silhouette
     * is height and a wheel. The height is honest — two floors, a deck and a
     * ladder, walls twice a cottage's — and the wheel is mounted flat in the
     * plane of the rear wall rather than on a cap.
     *
     * <p><strong>Why flat against the wall.</strong> A real mill's sweeps are
     * longer than its tower is wide, and a building's ground here is its
     * footprint and one block of doorstep — see {@link BuildingSizes#APRON}. A
     * cap-mounted wheel of any size worth seeing would stand in the next plot.
     * Mounted in the rear wall's own plane it has the whole height of the tower
     * to turn in and reaches no further out than the eave already does, which is
     * the difference between a windmill and a trespass.
     */
    private static int[] mill(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("mill");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int wallHeight = 6;
        cabin(site, blocks, base, size, wallHeight, style.wall(), style.frame());
        Parts.dress(blocks, base, size, wallHeight, style);
        // A second rank of windows, because a tower lit only at head height
        // reads as a very tall shed.
        Parts.windowRow(blocks, base, size, wallHeight - 1, style.wall(),
                HouseStyle.Variation.forOrigin(base).windowSpacing());

        TradeParts.deck(blocks, base, size, 4, -2, -2, style.wall());
        // Up through the hole in the deck and one rung past it, or a climber
        // arrives level with the floor rather than on top of it.
        TradeParts.ladder(blocks, base, -2, -2, 1, 5, Direction.SOUTH);
        TradeParts.sailWheel(blocks, base, size, wallHeight - 1,
                TradeParts.logFor(style), style.post(), Blocks.WOOL.white());

        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.MILL.get());
        add(blocks, base.offset(-1, 1, -1), Blocks.GRINDSTONE);
        // The millstone proper: a bed of smooth stone with the runner on it.
        add(blocks, base.offset(2, 1, 0), Blocks.SMOOTH_STONE);
        add(blocks, base.offset(2, 2, 0), Blocks.SMOOTH_STONE_SLAB);
        add(blocks, base.offset(2, 1, -2), TradeParts.lying(Blocks.HAY_BLOCK, true));
        add(blocks, base.offset(2, 2, -2), TradeParts.lying(Blocks.HAY_BLOCK, true));
        add(blocks, base.offset(-2, 1, 1), Blocks.BARREL);
        return measured(blocks, base, size, from);
    }

    /**
     * The carpentry: a lean-to stacked with logs, a saw bench, planks in the yard.
     *
     * <p>A woodworker's yard is more of the building than the building is. The
     * lean-to is one block deep because the doorstep ring is one block deep, and
     * that happens to be exactly what a lean-to is anyway — a roof pitched off
     * somebody else's wall with the stock kept dry underneath.
     *
     * <p>The timber lies <strong>on its side</strong> under that roof, which is
     * two decisions rather than one. On its side because an upright log is a tree
     * and a log across the grain is stock. Under the roof because a lumberjack
     * fells whatever log stands highest in a village column — see
     * {@code LumberjackWorker} — so a woodpile in the open air is a woodpile the
     * town's own axemen carry off again.
     */
    private static int[] carpentry(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("carpentry");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int wallHeight = 3;
        Block log = TradeParts.logFor(style);
        cabin(site, blocks, base, size, wallHeight, style.wall(), style.frame());
        Parts.dress(blocks, base, size, wallHeight, style);
        // After the dressing: a style's shutters hang in this same ring, and a
        // lean-to would rather have its posts than its neighbor's shutters.
        TradeParts.leanTo(blocks, base, size, 1, 2, wallHeight,
                style.post(), style.roofStairs());
        TradeParts.logPile(blocks, base, size.width() / 2 + BuildingSizes.APRON,
                -1, 3, 2, log);
        // Sawn stock in the yard. Boards rather than the people's own walling,
        // because one people walls in stripped oak wood -- which is in the logs
        // tag, and a log lying in a yard is a log a lumberjack fells.
        Block board = TradeParts.boardFor(style);
        int yard = -(size.width() / 2 + BuildingSizes.APRON);
        add(blocks, base.offset(yard, 1, -1), board);
        add(blocks, base.offset(yard, 1, 0), board);
        add(blocks, base.offset(yard, 2, 0), board);
        add(blocks, base.offset(yard, 1, 1), board);

        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.CARPENTRY.get());
        add(blocks, base.offset(-1, 1, -1),
                TradeParts.facing(Blocks.STONECUTTER, Direction.SOUTH));
        add(blocks, base.offset(1, 1, -1), Blocks.FLETCHING_TABLE);
        add(blocks, base.offset(-2, 1, 1), Blocks.CRAFTING_TABLE);
        return measured(blocks, base, size, from);
    }

    /** How high an inn's walls stand: two storeys of rooms over a taproom. */
    private static final int INN_WALL = 6;

    /**
     * The inn: two storeys, a sign over the door, and a stable against the gable.
     *
     * <p>The one building in a town that is for people who do not live there, so
     * everything that marks it out is aimed at the road — the sign, the lanterns
     * on their posts either side of the door, the lean-to where a traveller puts
     * a beast. Inside it is a taproom with a bar and tables and a flight of stairs
     * to the rooms, which is the only building here with an upstairs at all.
     */
    private static int[] inn(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("inn");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        DyeColor colors = bedColor(site.culture());

        cabin(site, blocks, base, size, INN_WALL, style.wall(), style.frame());
        Parts.dress(blocks, base, size, INN_WALL, style);
        CivicParts.upperFloor(blocks, base, size, INN_UPPER,
                style.roofRidge(), style.roofStairs());
        CivicParts.innSign(blocks, base, size, 3);
        CivicParts.stable(blocks, base, size, -1, style);
        CivicParts.doorLanterns(blocks, base, size, 3, style.post());
        // The bar down the back wall, east of the stair, so the flight and the
        // counter are not fighting over the same corner of the taproom.
        CivicParts.bar(blocks, base, size, 1, 3, CivicParts.slabOf(style.wall()));
        CivicParts.table(blocks, base, -3, 2, style.post(), colors);
        CivicParts.table(blocks, base, 3, 2, style.post(), colors);
        CivicParts.table(blocks, base, -3, -1, style.post(), colors);
        CivicParts.chandeliers(blocks, base, size, INN_UPPER - 1, 4);
        add(blocks, base.offset(2, 1, 1), Blocks.CRAFTING_TABLE);

        CivicParts.keepClear(blocks, CivicParts.wayIn(base, size, 0, -1));
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.INN.get());
        return measured(blocks, base, size, from);
    }

    /** The course the inn's upper floor is laid on. */
    private static final int INN_UPPER = 4;

    /**
     * The staked claim: a flag of a building, the first thing a founding party raises.
     *
     * <p>Now literally a flag. Three by three of trodden ground says nothing about
     * who trod it; the colors on a pole beside the post say which people have
     * claimed this ground, in the one color that people uses for everything else
     * it dyes.
     */
    private static int[] campPost(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("camp_post");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        foundation(site, blocks, base, 3, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                // Coarse dirt under the post itself: a path block converts to
                // plain dirt the moment anything solid stands on it.
                boolean center = dx == 0 && dz == 0;
                add(blocks, base.offset(dx, 0, dz),
                        center ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH);
            }
        }
        add(blocks, base.offset(1, 1, 1), Blocks.OAK_FENCE);
        add(blocks, base.offset(1, 2, 1), Blocks.LANTERN);
        CivicParts.campBanner(blocks, base, -1, -1, style.timber(), bedColor(site.culture()));
        CivicParts.keepClear(blocks, CivicParts.box(base, 0, 0, 0, 0, 1, 2));
        add(blocks, base.offset(0, 1, 0), KingdomsBlocks.CAMP_POST.get());
        return measured(blocks, base, size, from);
    }

    /**
     * The pooled supplies: barrels on boards, under a sheet of cloth.
     *
     * <p>The tarpaulin is the whole of the difference between this and a pile of
     * crates. Two posts at the front and the barrels themselves at the back hold
     * it up — which is why the cache reads as somebody's stores kept out of the
     * rain rather than as three barrels somebody left in a field.
     */
    private static int[] cache(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("cache");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        foundation(site, blocks, base, 3, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                add(blocks, base.offset(dx, 0, dz), style.roofRidge());
            }
        }
        add(blocks, base.offset(-1, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(1, 1, -1), Blocks.BARREL);
        add(blocks, base.offset(-1, 1, 0), Blocks.COMPOSTER);
        CivicParts.tarpaulin(blocks, base, 3, style.post(), bedColor(site.culture()));
        CivicParts.keepClear(blocks, CivicParts.box(base, 0, 0, -1, 1, 1, 2));
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.CACHE.get());
        return measured(blocks, base, size, from);
    }

    /** One room the whole party sleeps in — housing before there are families. */
    private static int[] bunkhouse(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = home(site, blocks, base, "bunkhouse", 3);
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.BUNKHOUSE.get());
        // Six bunks in a rank along the north wall, the post standing in the
        // middle of them. One room, the whole party, no bays.
        beds(site, blocks, base, "bunkhouse");
        add(blocks, base.offset(2, 1, 1), Blocks.BARREL);
        return measured(blocks, base, size, from);
    }

    /**
     * The open fire the camp cooks on, under a roof on four posts.
     *
     * <p>Walls are the one thing a hearth must not have — it is a fire people
     * stand round, and a room with a fire in it is a kitchen. A roof is the one
     * thing it must: an open fire is a fire that goes out. Four corner posts and
     * a hip over them is the oldest answer to that and still the right one, and
     * it is also what makes the hearth read from across a camp as a place rather
     * than as a campfire somebody left burning.
     */
    private static int[] hearth(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("hearth");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        foundation(site, blocks, base, size.width(), size.depth());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean pad = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                add(blocks, base.offset(dx, 0, dz),
                        pad ? style.plinth() : Blocks.DIRT_PATH);
            }
        }
        CivicParts.canopy(blocks, base, size, HEARTH_POST, style.post());
        Parts.hipRoof(blocks, base, size, HEARTH_POST + 1,
                style.roofStairs(), style.roofRidge(), 0);
        add(blocks, base.offset(0, 1, 0), Blocks.CAMPFIRE);
        add(blocks, base.offset(-1, 1, -1), Blocks.CAULDRON);
        // Seats at the middle of three sides. The fourth is the post, which is
        // the right height to sit on anyway.
        add(blocks, base.offset(-2, 1, 0), style.timber());
        add(blocks, base.offset(2, 1, 0), style.timber());
        add(blocks, base.offset(0, 1, 2), style.timber());
        CivicParts.keepClear(blocks, CivicParts.box(base, 0, 0, -2, -1, 1, 2));
        add(blocks, base.offset(0, 1, -2), KingdomsBlocks.HEARTH.get());
        return measured(blocks, base, size, from);
    }

    /** How tall the posts of the hearth's canopy stand, below its roof plate. */
    private static final int HEARTH_POST = 3;

    /**
     * A storehouse: a low shed with a cart-wide door under a canopy, lined with
     * barrels.
     *
     * <p>Two things say store. The <strong>door</strong>, three blocks wide with
     * a board on posts over it, because everything about this building is
     * carrying things through that opening. And the <strong>lining</strong> —
     * every foot of wall that is not the door has a barrel or a chest against it,
     * which is a room you can read the purpose of from the threshold.
     */
    private static int[] storehouse(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("storehouse");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int wallHeight = 3;
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        cabin(site, blocks, base, size, wallHeight, style.wall(), style.frame());
        TradeParts.widenDoor(blocks, base, size, -1, 1);
        // Before the roof, so a pitched eave becomes the canopy's own cover —
        // the same bargain Parts.porch makes, one block wider.
        TradeParts.canopy(blocks, base, size, wallHeight, 2,
                style.post(), style.roofRidge());
        Parts.dress(blocks, base, size, wallHeight, style);

        for (int dx = -rx; dx <= rx; dx++) {
            if (dx == 0) {
                continue;   // the aisle in from the door
            }
            add(blocks, base.offset(dx, 1, -rz + 1),
                    Math.floorMod(dx, 2) == 0 ? Blocks.CHEST : Blocks.BARREL);
        }
        for (int side = -1; side <= 1; side += 2) {
            add(blocks, base.offset(side * (rx - 1), 1, 0), Blocks.BARREL);
            add(blocks, base.offset(side * (rx - 1), 1, rz - 1), Blocks.BARREL);
            add(blocks, base.offset(side * (rx - 1), 2, -rz + 1), Blocks.BARREL);
        }
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.STOREHOUSE.get());
        return measured(blocks, base, size, from);
    }

    /**
     * The workshop: a cart-wide door with a trade board hung over it.
     *
     * <p>The building whose whole business is that things go in and come out
     * again, so the door is the thing that says so — two blocks wide instead of
     * one, with a board swinging under the eave above it. Inside is the one bench
     * of every craft that is not a forge and not a saw: a loom, a smithing table,
     * a cartographer's desk.
     *
     * <p>It also had a genuine bug. The old drawing put a crafting table at the
     * post's own cell and then a furnace on top of both, so the workshop's post —
     * the block that names it to the player and to the simulation — was never
     * actually standing.
     */
    private static int[] workshop(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("workshop");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int wallHeight = 3;
        int rx = size.width() / 2;
        cabin(site, blocks, base, size, wallHeight, style.wall(), style.frame());
        TradeParts.widenDoor(blocks, base, size, 0, 1);
        Parts.dress(blocks, base, size, wallHeight, style);
        add(blocks, base.offset(0, 2, size.depth() / 2 + BuildingSizes.APRON),
                TradeParts.signFor(style.post()));

        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.WORKSHOP.get());
        add(blocks, base.offset(-rx + 1, 1, -1), Blocks.LOOM);
        add(blocks, base.offset(rx - 1, 1, -1), Blocks.SMITHING_TABLE);
        add(blocks, base.offset(-rx + 1, 1, 1), Blocks.CARTOGRAPHY_TABLE);
        return measured(blocks, base, size, from);
    }

    /**
     * The market: a paved square with a well in it and six stalls round the edge.
     *
     * <p>It used to be a nine-by-nine lid of spruce on eight posts, which is a bus
     * shelter — and it is the shape that made a market indistinguishable from
     * every other roofed box in the town. A market is not one roof, it is one
     * <em>open square</em> with a small roof over each trader, and the open part
     * is the half that does the work: the thing a player recognizes is the empty
     * middle with a well in it and striped awnings round the outside.
     *
     * <p>Deliberately has no walls and no roof over the middle. The fault a
     * watertight check would report here is the design.
     */
    private static int[] market(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("market");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        DyeColor colors = bedColor(site.culture());
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        foundation(site, blocks, base, size.width(), size.depth());
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                add(blocks, base.offset(dx, 0, dz), style.plinth());
            }
        }
        // One block south of true center, because true center's northern
        // neighbour is the market post and that is a cell nothing may take.
        CivicParts.well(blocks, base, 0, 1, style.plinth(), style.post());
        int[][] pitches = {{-3, -3}, {3, -3}, {-3, 0}, {3, 0}, {-3, 3}, {3, 3}};
        for (int i = 0; i < pitches.length; i++) {
            CivicParts.stall(blocks, base, pitches[i][0], pitches[i][1],
                    pitches[i][1] > 0 ? -1 : 1, style.post(), colors, i);
        }
        CivicParts.keepClear(blocks, CivicParts.box(base, -1, 1, -2, -1, 1, 2));
        add(blocks, base.offset(0, 1, -1), KingdomsBlocks.MARKET.get());
        return measured(blocks, base, size, from);
    }

    /**
     * A crop field, fenced in the culture's own wood, with a scarecrow on the corner.
     *
     * <p><strong>The field itself is untouchable.</strong> Seventy-one wheat
     * blocks is what {@code Field.CROP_BLOCKS} says an unwatched town's whole
     * harvest is proportional to, and {@code BlueprintPlacerFieldTest} counts
     * them off this drawing. So everything added here stands on the rim or in the
     * doorstep ring: the scarecrow on the far corner, the tool shelter against
     * the fence opposite the gate. Not one soil cell changes hands.
     */
    private static int[] farm(Site site, List<Placement> blocks, BlockPos base) {
        int r = 5;
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        Block fence = style.post();
        // A field's soil is its floor, and that is drawn one below the base.
        foundation(site, blocks, base, 2 * r + 1, 2 * r + 1, -1);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                if (edge) {
                    add(blocks, base.offset(dx, -1, dz), Blocks.GRASS_BLOCK);
                    add(blocks, base.offset(dx, 0, dz), fence);
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
        add(blocks, base.offset(0, 0, r), TradeParts.gateFor(fence).defaultBlockState()
                .setValue(FenceGateBlock.OPEN, true));
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

        // The far corner, out in the doorstep ring where no crop grows.
        int out = r + BuildingSizes.APRON;
        TradeParts.scarecrow(blocks, base, -out, -out, 0, fence);
        // A tool shelter against the fence opposite the gate: two posts, a lean
        // of roof, and a barrel under it. One block deep, because the ring is.
        for (int dz = -1; dz <= 1; dz++) {
            add(blocks, base.offset(out, 2, dz),
                    TradeParts.stair(style.roofStairs(), Direction.WEST));
        }
        for (int side = -1; side <= 1; side += 2) {
            add(blocks, base.offset(out, 0, side), fence);
            add(blocks, base.offset(out, 1, side), fence);
        }
        add(blocks, base.offset(out, 0, 0), Blocks.BARREL);
        return new int[]{2 * r + 1, 2 * r + 1, 3};
    }

    /** The last course of the tower's shaft, below its deck. Two storeys taller
     * than the six it stood at: a watchtower you cannot see over the roofs is a
     * watchtower that is not doing its job. */
    private static final int WATCHTOWER_SHAFT = 12;

    /**
     * The watchtower: the one building in a town that is nothing but height.
     *
     * <p>A plinth, and the tower proper standing on the middle of it — a
     * three-wide shaft straight out of the grass reads as a chimney, and the
     * step-in at the base is what makes it a tower. Above that everything says
     * fortification rather than house: arrow loops instead of windows, a ladder
     * bolted to the outside instead of a stair eating the room, merlons round the
     * top, and a fire burning on it that can be seen from the next valley.
     */
    private static int[] watchtower(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("watchtower");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        foundation(site, blocks, base, size.width(), size.depth());
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                add(blocks, base.offset(dx, 0, dz), style.plinth());
            }
        }
        int deck = CivicParts.shaft(blocks, base, 1, WATCHTOWER_SHAFT, style.plinth());
        // Up the east face, which is the one the loops are kept out of. The top
        // rung is level with the deck, so a climber steps off onto it.
        CivicParts.ladder(blocks, base, 2, 0, Direction.EAST, 1, deck);
        CivicParts.crown(blocks, base, deck, style.plinth());
        CivicParts.hang(blocks, base.offset(0, deck - 1, 0));
        CivicParts.keepClear(blocks, CivicParts.box(base, 0, 0, 0, 1, 1, 2));
        add(blocks, base.offset(0, 1, 0), KingdomsBlocks.WATCHTOWER.get());
        return measured(blocks, base, size, from);
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
    private static int[] accessStairs(Site site, List<Placement> blocks, BlockPos base) {
        for (int i = 1; i <= MAX_STAIR_RUN; i++) {
            int x = base.getX();
            int z = base.getZ() + i;
            int treadY = base.getY() - i;
            int ground = site.groundLevel(x, z) - 1;
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

    /** How high a hall's walls stand, which is one course more than an inn's. */
    private static final int TOWN_HALL_WALL = 6;

    /**
     * The town hall: the tallest roof on the street, and the thing on top of it.
     *
     * <p>It used to be the plain box in stone brick, told apart from a storehouse
     * only by being bigger — and by a gold block sitting at the top of its wall
     * course, which was visible for exactly as long as halls had no roofs.
     *
     * <p>Five things now say hall, and they are aimed at five different distances.
     * From across the valley, the cupola and its gold finial standing clear of
     * every other ridge in the town. From the end of the street, walls a course
     * higher than anything else and a two-course stone base under them. From the
     * far side of the square, banners in the town's own color and a flag on its
     * own pole. From the doorstep, an entrance three blocks wide with a paved
     * landing in front of it. And from under the porch, a bell.
     *
     * <p>The order is the usual one and matters for the usual reason: the base
     * courses go on after the dressing so the windows are already cut and survive
     * them, and the porch goes on after the roof — the one place this departs from
     * {@link Parts#dress} — because a bell needs a solid block to hang from and an
     * eave of stairs is not one.
     */
    private static int[] townHall(Site site, List<Placement> blocks, BlockPos base) {
        int from = blocks.size();
        BuildingSizes.Size size = sized("town_hall");
        HouseStyle style = HouseStyle.forCulture(site.culture().id());
        DyeColor colors = bedColor(site.culture());

        cabin(site, blocks, base, size, TOWN_HALL_WALL, style.wall(), style.frame());
        Parts.dress(blocks, base, size, TOWN_HALL_WALL, CivicParts.porchless(style));
        CivicParts.baseCourses(blocks, base, size, 2, 2, style);
        CivicParts.porchWithBell(blocks, base, size, TOWN_HALL_WALL, style);
        CivicParts.doorBanners(blocks, base, size, 2, 3, colors);
        CivicParts.landing(blocks, base, size, 2, style.plinth());
        CivicParts.flagpole(blocks, base, -4, size.depth() / 2 + 1, 5,
                style.frame(), colors);
        CivicParts.chandeliers(blocks, base, size, TOWN_HALL_WALL, 4);
        // Measured over the middle rather than over the whole plan: a chimney
        // climbs the gable end and stands proud of the ridge, and a cupola put on
        // the tallest thing in the plan would be put on the chimney pot.
        CivicParts.cupola(blocks, base, CivicParts.topOver(blocks, base, from, 1), style);

        Set<BlockPos> clear = CivicParts.wayIn(base, size, 1, -1);
        clear.addAll(CivicParts.box(base, -2, -2, -2, -2, 1, 1));   // the quest board
        CivicParts.keepClear(blocks, clear);
        return measured(blocks, base, size, from);
    }

    /**
     * A building whose walls follow its declared shape rather than a rectangle.
     *
     * <p>{@link #cabin} is this with the outline test always answering yes, and
     * it stays separate because nearly every building IS a rectangle and reading
     * a rectangle out of a general shape walker is harder than reading it out of
     * two nested loops.
     *
     * <p>A cell carries wall if it is covered and something next to it is not.
     * That one rule gives the outer walls, the two walls of the crook, and — by
     * being open on both axes at once — a corner post at each of the six corners
     * of an L, including the inner one, which is the corner a rectangle does not
     * have and the one a hand-written wall loop always forgets.
     */
    private static int[] hall(Site site, List<Placement> blocks, BlockPos base,
                              BuildingSizes.Size size, int wallHeight, Block wall, Block frame) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        foundation(site, blocks, base, size.width(), size.depth());

        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (!size.covers(dx, dz)) {
                    continue;
                }
                add(blocks, base.offset(dx, 0, dz), wall);
                add(blocks, base.offset(dx, wallHeight + 1, dz), wall);
            }
        }
        for (int y = 1; y <= wallHeight; y++) {
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    if (!size.covers(dx, dz)) {
                        continue;
                    }
                    boolean openX = !size.covers(dx - 1, dz) || !size.covers(dx + 1, dz);
                    boolean openZ = !size.covers(dx, dz - 1) || !size.covers(dx, dz + 1);
                    if (!openX && !openZ) {
                        continue;   // indoors
                    }
                    BlockPos p = base.offset(dx, y, dz);
                    if (openX && openZ) {
                        add(blocks, p, frame);
                    } else if (dz == rz && dx == 0 && y <= 2) {
                        // the door, in the same place a cabin puts it
                    } else if (y == 2 && (dx == 0 || dz == 0)) {
                        add(blocks, p, Blocks.GLASS);
                    } else {
                        add(blocks, p, wall);
                    }
                }
            }
        }
        // The roof rim, along whichever edges this shape actually has.
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (!size.covers(dx, dz)) {
                    continue;
                }
                if (!size.covers(dx, dz - 1) || !size.covers(dx, dz + 1)) {
                    add(blocks, base.offset(dx, wallHeight + 1, dz), frame);
                }
            }
        }
        add(blocks, base.offset(0, 1, 0), Blocks.LANTERN);
        return new int[]{size.width(), size.depth(), wallHeight + 2};
    }

    /**
     * What this building is declared to be, which is what ground was reserved.
     *
     * <p>Never a literal in a drawing method again. The sizes here and the plot
     * spans in the catalog were two separate sets of numbers and had drifted
     * to roughly a factor of two apart, so every street in the mod was laid out
     * for buildings twice the size of the ones put on it.
     */
    private static BuildingSizes.Size sized(String path) {
        BuildingSizes.Size size = BuildingSizes.of(path);
        if (size == null) {
            throw new IllegalStateException("nothing declares how big a " + path + " is");
        }
        return size;
    }

    /** A rectangular building: floor, log corners, walls with door gap and windows, rimmed roof. */
    private static int[] cabin(Site site, List<Placement> blocks, BlockPos base,
                               BuildingSizes.Size size, int wallHeight, Block wall, Block frame) {
        return cabin(site, blocks, base, size.width(), size.depth(), wallHeight, wall, frame);
    }

    /** A rectangular building, at a size given outright rather than declared. */
    private static int[] cabin(Site site, List<Placement> blocks, BlockPos base,
                               int width, int depth, int wallHeight, Block wall, Block frame) {
        int rx = width / 2;
        int rz = depth / 2;
        foundation(site, blocks, base, width, depth);

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
    private static void foundation(Site site, List<Placement> blocks, BlockPos base,
                                   int width, int depth) {
        foundation(site, blocks, base, width, depth, 0);
    }

    /**
     * @param floorCourse where this structure lays its own walkable surface,
     *                    relative to the base. Zero for everything that has a
     *                    floor. A crop field draws its soil one BELOW its base —
     *                    see {@link #baseFor} — so its doorstep belongs one lower
     *                    too, or every field on flat ground gets a cobble curb
     *                    standing a block proud of the grass all the way round.
     */
    private static void foundation(Site site, List<Placement> blocks, BlockPos base,
                                   int width, int depth, int floorCourse) {
        foundation(blocks, base, width, depth, floorCourse, site);
    }

    /**
     * What the ground says, for the purpose of underpinning something.
     *
     * <p>Two questions, which is all the whole foundation pass ever asks of a
     * world — so asking them through this makes what it lays testable without
     * one. The cells it fills are where "floating over a slope" and "a cobble
     * curb standing proud of the grass" both live, and neither was reachable by
     * anything but looking at a hillside.
     */
    interface Ground {

        /** Whether this cell can be judged at all. */
        boolean loaded(BlockPos pos);

        /** Nothing holding this cell up: open air, or water to displace. */
        boolean unsupported(BlockPos pos);
    }

    /**
     * Everything a drawing method may ask about where it is standing.
     *
     * <p>Three questions and no more, which is the whole point of the type: the
     * <em>size</em> a builder draws is a property of the declared shape and the
     * people building it, never of the hillside, so a plan drawn against a fake
     * site reports exactly the width and depth a real one would. That is what
     * lets {@code BlueprintPlacerSizeTest} compare what every shape draws against
     * {@link BuildingSizes} without a running game — the comparison the
     * {@code SIZE MISMATCH} log in {@link #procedural} had to make at runtime
     * because there was nowhere else it could be made.
     *
     * <p>{@link Ground} was already most of it. The two additions are the two
     * other things a shape has ever wanted: the culture, because a compound is
     * as many pens as the people keep beasts, and the surface height, because a
     * flight of steps runs until it meets the hill.
     */
    interface Site extends Ground {

        /** The people whose town this is, whose habits some shapes answer to. */
        Culture culture();

        /** The first free block over this column; see {@link #groundLevel}. */
        int groundLevel(int x, int z);
    }

    /**
     * The real world, asked the three questions a shape is allowed to ask it.
     *
     * <p>The culture is resolved once, here, rather than looked up again inside
     * whichever shape wants it: {@link #planFor} already needs it to pick a
     * blueprint style, and scanning every settlement of every kingdom twice for
     * the same answer is a scan too many.
     */
    private static Site siteAt(ServerLevel level, BlockPos base) {
        Culture culture = cultureAt(level, base);
        return new Site() {
            @Override
            public boolean loaded(BlockPos pos) {
                return level.isLoaded(pos);
            }

            @Override
            public boolean unsupported(BlockPos pos) {
                return isUnsupported(level, pos);
            }

            @Override
            public Culture culture() {
                return culture;
            }

            @Override
            public int groundLevel(int x, int z) {
                return BlueprintPlacer.groundLevel(level, x, z);
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
