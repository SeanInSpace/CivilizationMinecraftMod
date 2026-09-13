package com.civilization.neoforge.world;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.work.LightPlanner;
import com.civilization.sim.work.LightStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Puts a town's street lamps in the ground.
 *
 * <p>{@code LightPlanner} decides where they go and {@code LightStyle} decides
 * what a people's lamp is; this is the one place either of those becomes blocks.
 * Deliberately the same shape as {@code PerimeterLayer}, down to the names: a
 * {@link Course} is one block of one lamp, {@link #plan} says what a lamp is made
 * of without needing a world, {@link #owed} reads the ground to find the course
 * that is missing, and {@link #draw} is the clock's sweep over the prefix the town
 * has raised. The builder and the clock therefore raise the same lamp by
 * construction rather than by two lists somebody has to keep in step.
 *
 * <p><strong>Every course has to survive being placed.</strong> That is not a
 * nicety, it is the lesson the wall paid for: a torch on a fence post pops off the
 * instant it is set, which is invisible in the world — an unlit street reads as a
 * street nobody has got round to — and fatal in the sweep, which counts each
 * doomed torch as work done and spends its whole budget re-placing the same two
 * dozen every second. So a torch style stands its torch on a solid head and a
 * lantern style stands its lantern on a fence, which is the arrangement the wall
 * already proved.
 */
public final class LightLayer {

    private LightLayer() {
    }

    /**
     * Lamps the clock stamps in per second.
     *
     * <p>Half {@code PerimeterLayer.POSTS_PER_SECOND}, because a lamp is three
     * blocks and a post is two and the lighting is a tenth the size of a ring. A
     * town's whole lighting goes in inside a minute of being walked into, which is
     * what "the town lit itself while you were away" has to look like.
     */
    static final int LAMPS_PER_SECOND = 12;

    /**
     * How many of the raised prefix one sweep will look at.
     *
     * <p>Bounded separately from the placing, exactly as the wall's is: half a
     * town's lamps are usually out of sight, and charging the scan for the ones
     * nobody can see meant a sweep whose cursor sat in that arc did nothing and
     * handed the same arc to the next sweep.
     */
    private static final int SCAN = 64;

    /** How far behind the sweeps may fall before the catch-up is capped. */
    private static final int CATCH_UP_SECONDS = 10;

    /** One block of one lamp, and what it is. */
    public record Course(BlockPos pos, BlockState state) {
    }

    /**
     * What a lamp of this style is made of, in the order it goes up.
     *
     * <p>Pure, and takes the footing rather than finding it, so what the lighting
     * draws can be asked without a running world — the footing is the one part of
     * this that has to read the ground. See {@code LightLayerStyleTest}.
     *
     * <p>Two courses of standard and the light on top, every style. The standard's
     * head is solid wherever the light is a torch, because a torch needs something
     * to sit on; where the light is a lantern the head is the same fence as the
     * foot, which is the wall's own arrangement and is known to hold.
     */
    public static List<Course> plan(LightStyle style, BlockPos footing) {
        Block foot = footBlockOf(style);
        Block head = headBlockOf(style);
        Block lamp = lampBlockOf(style);
        List<Course> courses = new ArrayList<>(3);
        courses.add(new Course(footing, foot.defaultBlockState()));
        courses.add(new Course(footing.above(), head.defaultBlockState()));
        courses.add(new Course(footing.above(style.height()), lamp.defaultBlockState()));
        return List.copyOf(courses);
    }

    /** The foot of the standard: what the lamp stands in the ground on. */
    static Block footBlockOf(LightStyle style) {
        return switch (style) {
            case OAK_LANTERN, FENCE_TORCH -> Blocks.OAK_FENCE;
            case SPRUCE_TORCH, SPRUCE_SOUL_LANTERN -> Blocks.SPRUCE_FENCE;
            case STONE_LANTERN -> Blocks.STONE_BRICK_WALL;
            case STICK_TORCH -> Blocks.STRIPPED_BAMBOO_BLOCK;
        };
    }

    /**
     * The head of the standard: what the light itself sits on.
     *
     * <p>Solid for a torch and the same fence for a lantern. A lantern sits on a
     * fence post and stays there; a torch does not, and the difference between the
     * two is the reason this method exists rather than the foot being used twice.
     */
    static Block headBlockOf(LightStyle style) {
        return switch (style) {
            case OAK_LANTERN -> Blocks.OAK_FENCE;
            case SPRUCE_SOUL_LANTERN -> Blocks.SPRUCE_FENCE;
            case STONE_LANTERN -> Blocks.STONE_BRICK_WALL;
            case SPRUCE_TORCH -> Blocks.STRIPPED_SPRUCE_LOG;
            case FENCE_TORCH -> Blocks.STRIPPED_OAK_LOG;
            case STICK_TORCH -> Blocks.STRIPPED_BAMBOO_BLOCK;
        };
    }

    /** The light. */
    static Block lampBlockOf(LightStyle style) {
        return switch (style) {
            case OAK_LANTERN, STONE_LANTERN -> Blocks.LANTERN;
            case SPRUCE_SOUL_LANTERN -> Blocks.SOUL_LANTERN;
            case SPRUCE_TORCH, FENCE_TORCH, STICK_TORCH -> Blocks.TORCH;
        };
    }

    /** What this town's lamps are made of, at one position on the ground. */
    public static List<Course> planAt(ServerLevel level, Settlement settlement, int index) {
        List<LightPlanner.Lamp> lamps = LightPlanner.lamps(settlement);
        if (index < 0 || index >= lamps.size()) {
            return List.of();
        }
        BlockPos footing = footingFor(level, lamps.get(index).at());
        return footing == null ? List.of()
                : plan(LightPlanner.styleOf(settlement), footing);
    }

    /**
     * The first block of a lamp's plan that is not standing yet, or null when there
     * is nothing left to do there.
     *
     * <p>How a crew keeps its place without keeping a cursor, and how the sweep
     * tells a lamp it has already raised from one that has been broken. Written in
     * the ground rather than in a number, so a builder killed halfway up a standard
     * resumes at the course that is missing.
     */
    public static Course owed(ServerLevel level, List<Course> plan) {
        for (Course course : plan) {
            if (!level.getBlockState(course.pos()).is(course.state().getBlock())) {
                return course;
            }
        }
        return null;
    }

    /** One block of a lamp, put down by hand. */
    public static boolean layByHand(ServerLevel level, Course course) {
        return put(level, course.pos(), course.state());
    }

    /**
     * Whether anything of ours is standing in this lamp's column.
     *
     * <p>The charge-once question, exactly as the wall's {@code oursStandsAt} is:
     * a crew pays for the light when the first course of the standard goes in, so
     * it has to know whether this position has been started.
     */
    public static boolean oursStandsAt(ServerLevel level, Settlement settlement, SimPos pos) {
        BlockPos footing = footingFor(level, pos);
        if (footing == null) {
            return false;
        }
        LightStyle style = LightPlanner.styleOf(settlement);
        for (int dy = 0; dy <= style.height(); dy++) {
            BlockPos at = footing.above(dy);
            if (level.isLoaded(at) && isOurs(level, at, style)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stamps the raised prefix of the lighting into the world, and mends what has
     * been broken out of it.
     *
     * <p>One sweep for both, which is what makes the lighting self-healing, and the
     * distinction between them is {@code RoadUpkeep}'s: a lamp drawn for the first
     * time is a record of work somebody did while they were alive to do it, and a
     * lamp put back is a morning with a ladder. A town with nobody left in it does
     * the first and not the second — but this sweep cannot tell them apart any more
     * than the wall's can, because it wraps round the town and skips unloaded
     * ground, so a dead town's lighting is left exactly as the wall's is. The
     * street stays dark and the village looks like what it is.
     *
     * @param handsOnTheLights whether a builder is out raising lamps himself, in
     *                         which case the sweep stands aside — where there is a
     *                         hand there is no clock
     */
    public static void draw(ServerLevel level, Settlement settlement,
                            boolean handsOnTheLights) {
        if (!settlement.hasLivingResidents() || handsOnTheLights) {
            return;
        }
        int raised = settlement.lightsRaised();
        if (raised <= 0) {
            return;
        }
        List<LightPlanner.Lamp> lamps = LightPlanner.lamps(settlement);
        int limit = Math.min(raised, lamps.size());
        if (limit <= 0) {
            return;
        }
        LightStyle style = LightPlanner.styleOf(settlement);
        long now = System.nanoTime();
        Long previous = LAST_DRAW.put(settlement.id(), now);
        long elapsed = previous == null ? DrawBudget.NANOS_PER_SECOND : now - previous;
        int budget = DrawBudget.forElapsed(elapsed, LAMPS_PER_SECOND, CATCH_UP_SECONDS);
        int start = CURSOR.getOrDefault(settlement.id(), 0);
        if (start >= limit) {
            start = 0;
        }
        int placed = 0;
        int looked = 0;
        int examined = 0;
        int i = start;
        while (looked < SCAN && examined < limit && placed < budget) {
            SimPos at = lamps.get(i).at();
            BlockPos column = new BlockPos(at.x(), at.y(), at.z());
            if (level.isLoaded(column)) {
                placed += raise(level, settlement, style, at) ? 1 : 0;
                looked++;
            }
            examined++;
            i++;
            if (i >= limit) {
                i = 0;
            }
        }
        CURSOR.put(settlement.id(), i);
    }

    /** Where each town's sweep stopped, so the budget travels round the lighting. */
    private static final Map<Settlement.Id, Integer> CURSOR = new java.util.HashMap<>();

    /** When each town's lighting was last swept, so the budget is real time. */
    private static final Map<Settlement.Id, Long> LAST_DRAW = new java.util.HashMap<>();

    /**
     * Drops what the sweep remembers about a world that is closing.
     *
     * <p>The same housekeeping as {@code PerimeterLayer.forget}, and it matters for
     * the same reason: a timestamp left over from the last session would have the
     * first sweep of the next one reading an elapsed time measured in minutes.
     */
    public static void forget() {
        CURSOR.clear();
        LAST_DRAW.clear();
    }

    /** Stands one whole lamp, and says whether anything went in. */
    private static boolean raise(ServerLevel level, Settlement settlement,
                                 LightStyle style, SimPos at) {
        BlockPos footing = footingFor(level, at);
        if (footing == null) {
            return false;
        }
        clearGrowth(level, settlement, footing, style);
        boolean placed = false;
        for (Course course : plan(style, footing)) {
            placed |= put(level, course.pos(), course.state());
        }
        return placed;
    }

    /**
     * The foot of the standard: the first free block above the real ground.
     *
     * <p>Down through anything we ourselves put here, which is the subtlety the
     * wall paid for twice. The heightmap counts our own blocks, so asking it for
     * the surface and standing two courses there raises the heightmap by two — and
     * the next sweep stands two more on top of those, and the lamp climbs into the
     * sky at a dozen blocks a second for as long as the town is loaded. Stepping
     * back down through our own work is what makes {@link #raise} genuinely
     * idempotent: the second sweep finds its own post standing and writes nothing.
     */
    public static BlockPos footingFor(ServerLevel level, SimPos pos) {
        BlockPos column = new BlockPos(pos.x(), pos.y(), pos.z());
        if (!level.isLoaded(column)) {
            return null;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column);
        // Every style's blocks, not only this town's: a culture can change under a
        // standing lamp -- a town conquered, a datapack reloaded -- and a footing
        // that stopped recognizing the old post would found the new one on top of
        // it. Growth comes off in clearGrowth rather than being stepped over,
        // because a lamp in a bush is a lamp nobody can see.
        while (top.getY() > level.getMinY() && isAnyOfOurs(level, top.below())) {
            top = top.below();
        }
        return top;
    }

    /** Whether a lamp of this town's is actually standing here. */
    public static boolean lampStands(ServerLevel level, Settlement settlement, SimPos pos) {
        BlockPos footing = footingFor(level, pos);
        if (footing == null) {
            return false;
        }
        LightStyle style = LightPlanner.styleOf(settlement);
        return level.getBlockState(footing.above(style.height()))
                .is(lampBlockOf(style));
    }

    /**
     * Whether this block is part of a street lamp rather than part of a building.
     *
     * <p>Asked by the demolition sweep, which counts the solid blocks inside a
     * plot and has no way of knowing a lantern is not a wall. {@code LightPlanner}
     * already refuses to plan a lamp inside anybody's footprint, so this should
     * never fire — and it is here because "should never" is how the wall came to
     * count a hundred and eighty-one tree trunks as palisade. A player moves a
     * house; a house is rebuilt bigger; a plot is re-sited onto a lamp that was
     * planned when the ground was empty.
     */
    public static boolean isStreetFurniture(BlockState state) {
        for (LightStyle style : LightStyle.values()) {
            if (state.is(lampBlockOf(style))) {
                return true;
            }
        }
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN);
    }

    private static boolean isAnyOfOurs(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isStreetFurniture(state)) {
            return true;
        }
        for (LightStyle style : LightStyle.values()) {
            if (state.is(footBlockOf(style)) || state.is(headBlockOf(style))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOurs(ServerLevel level, BlockPos pos, LightStyle style) {
        BlockState state = level.getBlockState(pos);
        return state.is(footBlockOf(style)) || state.is(headBlockOf(style))
                || state.is(lampBlockOf(style));
    }

    /**
     * Takes the growth off a lamp's own column, and keeps what it was worth.
     *
     * <p>The lamp's column and nothing either side of it. A standard is entitled to
     * the block it stands in; the verge it stands on is somebody's meadow, and a
     * lighting scheme that scraped a ring of bare earth round every post would be
     * the worst-looking thing in the town.
     */
    private static void clearGrowth(ServerLevel level, Settlement settlement,
                                    BlockPos footing, LightStyle style) {
        for (int dy = 0; dy <= style.height(); dy++) {
            BlockPos at = footing.above(dy);
            if (!level.isLoaded(at)) {
                return;
            }
            BlockState state = level.getBlockState(at);
            if (state.isAir() || !WallClearing.isPlant(state)) {
                continue;
            }
            level.destroyBlock(at, false, null, 512);
            // Clearing, never felling. A tuft of grass pulled up to stand a lamp
            // post in is spoil; the forester's ledger has no opinion about it, and
            // a call that said otherwise is the whole of what Yield.Cause exists
            // to prevent.
            Yield.keep(settlement, null, state, at, Yield.Cause.CLEARING);
        }
    }

    private static boolean put(ServerLevel level, BlockPos pos, BlockState want) {
        if (level.getBlockState(pos).is(want.getBlock())) {
            return false;
        }
        if (!replaceable(level, pos)) {
            return false;
        }
        level.setBlock(pos, want, Block.UPDATE_ALL);
        // Only if it survived. A block that pops off the moment it is set is not
        // work done, and counting it as work is what let one bad choice of block
        // halt an entire wall -- see the class comment.
        return level.getBlockState(pos).is(want.getBlock());
    }

    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || WallClearing.isPlant(state);
    }
}
