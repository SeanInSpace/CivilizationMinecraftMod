package com.kingdoms.neoforge.world;

import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks a settlement's buildings and reports what is built wrong.
 *
 * <p>This exists because every fault it checks for is silent. A farm placed in a
 * lake, a hall perched a storey above its own door, a settler-proof fence, crops
 * popped into item drops — none of them throws, none of them logs, and all of
 * them are obvious within thirty seconds of walking through the town. The audit
 * turns that walk into something a headless run can do, so regressions in world
 * geometry get caught by the harness instead of by the player.
 *
 * <p>Everything here is read-only and judged against the same rules the builders
 * work to: floors sit at grade ({@link BlueprintPlacer#floorFor}), the cleared
 * shelf around a building is part of its plot, and every building needs a way in
 * at ground level.
 */
public final class TownAuditor {

    /** One thing wrong with one building. */
    public record Fault(String blueprintId, BlockPos at, String problem) {

        public String describe() {
            return blueprintId + " @ " + at.toShortString() + ": " + problem;
        }
    }

    /** A course of slack either way before a shelf reads as banked or hanging. */
    private static final int SHELF_TOLERANCE = 1;

    /** Sample every other column on rings, so a big plot stays cheap to judge. */
    private static final int RING_STEP = 2;

    /** Fewer loose items than this is dropped tools and clutter, not a fault. */
    private static final int LOOSE_ITEM_THRESHOLD = 5;

    /** A field this small is still being cleared; do not judge its planting. */
    private static final int MIN_FIELD_TO_JUDGE = 8;

    /** Vanished-crop reports per farm per sweep, so one bad night stays readable. */
    private static final int MAX_VANISHED_REPORTED = 4;

    /**
     * Where crops stood last sweep, per farm.
     *
     * <p>The whole point of remembering: when a crop disappears, the next sweep
     * can say <em>which block</em> and <em>what stands there now</em> — soil
     * intact means the crop broke off it (a survival check failed), soil turned
     * to dirt means something trampled it, a path block means it was paved over.
     * Counting losses never identified the mechanism; naming them does.
     */
    private static final java.util.Map<BlockPos, java.util.Set<BlockPos>> LAST_PLANTED =
            new java.util.HashMap<>();

    private TownAuditor() {
    }

    /**
     * Audits every building of a settlement that stands in a loaded chunk.
     *
     * <p>Unloaded buildings are skipped, not judged: there is nothing there to
     * look at, and guessing would report ghosts.
     */
    public static List<Fault> audit(ServerLevel level, Settlement settlement) {
        List<Fault> faults = new ArrayList<>();
        List<Building> present = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            if (isPath(building.blueprintId())) {
                continue;   // steps are a path, not a building with an inside
            }
            BlockPos origin = new BlockPos(building.origin().x(),
                    building.origin().y(), building.origin().z());
            if (!level.isLoaded(origin)) {
                continue;
            }
            present.add(building);
            auditOne(level, building, origin, faults);
        }
        auditOverlaps(present, faults);
        return faults;
    }

    /** How many of a settlement's buildings the audit could actually see. */
    public static int visibleCount(ServerLevel level, Settlement settlement) {
        int seen = 0;
        for (Building building : settlement.buildings()) {
            if (isPath(building.blueprintId())) {
                continue;
            }
            if (level.isLoaded(new BlockPos(building.origin().x(),
                    building.origin().y(), building.origin().z()))) {
                seen++;
            }
        }
        return seen;
    }

    // --- the checks ---

    private static void auditOne(ServerLevel level, Building building, BlockPos origin,
                                 List<Fault> faults) {
        if (!building.isMaterialized()) {
            // The chunk is loaded and the simulation says this building exists,
            // yet nothing has been drawn. materializePending should have caught
            // it on the next step — a building stuck like this is exactly the
            // "stepping did not create everything" fault.
            faults.add(new Fault(building.blueprintId(), origin,
                    "recorded here, but nothing stands on the ground"));
            return;
        }
        Footprint plot = building.footprint();
        if (!plot.isKnown()) {
            return;   // an old record with no measurements; nothing to judge against
        }
        int floor = plot.y();
        int wallHalfW = Math.max(1, plot.width() / 2 - BlueprintPlacer.APRON_MARGIN);
        int wallHalfD = Math.max(1, plot.depth() / 2 - BlueprintPlacer.APRON_MARGIN);

        checkShelf(level, building, origin, floor, wallHalfW, wallHalfD, faults);
        checkFluid(level, building, origin, floor, wallHalfW, wallHalfD, faults);
        checkDoorway(level, building, origin, floor, wallHalfW, wallHalfD, faults);
        if (isCropFarm(building.blueprintId())) {
            checkField(level, building, origin, floor, wallHalfW, wallHalfD, faults);
        }
    }

    /**
     * The cleared shelf around the walls must sit at the floor's own level.
     *
     * <p>That shelf is what makes a building enterable: the door opens onto it.
     * If the ground one step out from the walls stands above the floor on every
     * side, the building is at the bottom of a pit; if it falls short on every
     * side, the building is perched with its doorway in the air. Either way
     * nobody is walking in. Partial banking is left alone — a hillside build is
     * banked uphill by nature, and one open side is all an entrance needs.
     */
    private static void checkShelf(ServerLevel level, Building building, BlockPos origin,
                                   int floor, int wallHalfW, int wallHalfD,
                                   List<Fault> faults) {
        int samples = 0;
        int banked = 0;
        int hanging = 0;
        int worstAbove = 0;
        int worstBelow = 0;
        for (BlockPos spot : ring(origin, wallHalfW + 1, wallHalfD + 1, RING_STEP)) {
            if (!level.isLoaded(spot)) {
                continue;
            }
            int grade = BlueprintPlacer.groundLevel(level, spot.getX(), spot.getZ()) - 1;
            samples++;
            if (grade > floor + SHELF_TOLERANCE) {
                banked++;
                worstAbove = Math.max(worstAbove, grade - floor);
            } else if (grade < floor - SHELF_TOLERANCE) {
                hanging++;
                worstBelow = Math.max(worstBelow, floor - grade);
            }
        }
        if (samples == 0) {
            return;
        }
        if (banked == samples) {
            faults.add(new Fault(building.blueprintId(), origin,
                    "buried — the ground stands up to " + worstAbove
                            + " above its floor on every side"));
        } else if (hanging == samples) {
            faults.add(new Fault(building.blueprintId(), origin,
                    "perched — its floor hangs up to " + worstBelow
                            + " above the ground on every side"));
        }
    }

    /** Water or lava standing in the rooms is a building in a lake. */
    private static void checkFluid(ServerLevel level, Building building, BlockPos origin,
                                   int floor, int wallHalfW, int wallHalfD,
                                   List<Fault> faults) {
        int wet = 0;
        for (int dx = -wallHalfW; dx <= wallHalfW; dx++) {
            for (int dz = -wallHalfD; dz <= wallHalfD; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos pos = new BlockPos(origin.getX() + dx, floor + dy,
                            origin.getZ() + dz);
                    if (level.isLoaded(pos) && !level.getFluidState(pos).isEmpty()) {
                        wet++;
                    }
                }
            }
        }
        if (wet > 0) {
            faults.add(new Fault(building.blueprintId(), origin,
                    "standing fluid inside it (" + wet + " blocks)"));
        }
    }

    /**
     * Somewhere along the walls there must be a way in at ground level.
     *
     * <p>A doorway is a two-high gap in the wall ring with standable ground just
     * outside it. A fence gate counts whether open or shut — it is an intended
     * access point even where it is kept closed to pen animals. No gap on any
     * side is the town hall with its door a storey up, or a doorway the terrain
     * has swallowed.
     */
    private static void checkDoorway(ServerLevel level, Building building, BlockPos origin,
                                     int floor, int wallHalfW, int wallHalfD,
                                     List<Fault> faults) {
        // Every column, no sampling. A doorway is one column wide, and stepping
        // by two walked straight past the door on two of the four sides — which
        // reported half the town as having no way in when it plainly did.
        for (BlockPos wall : ring(origin, wallHalfW, wallHalfD, 1)) {
            BlockPos feet = new BlockPos(wall.getX(), floor + 1, wall.getZ());
            BlockState atFeet = level.getBlockState(feet);
            if (atFeet.getBlock() instanceof FenceGateBlock) {
                return;   // a gate is a way in, even one kept shut on purpose
            }
            if (!passable(level, feet) || !passable(level, feet.above())) {
                continue;   // solid wall here
            }
            // A gap. Is there ground to stand on just outside it?
            Direction out = outward(origin, wall);
            BlockPos outside = feet.relative(out);
            BlockPos footing = outside.below();
            if (level.getBlockState(footing).isFaceSturdy(level, footing, Direction.UP)
                    && passable(level, outside) && passable(level, outside.above())) {
                return;
            }
        }
        faults.add(new Fault(building.blueprintId(), origin,
                "no way in — no doorway at grade on any side"));
    }

    /**
     * A farm should be mostly planted, and not strewn with popped seed items.
     *
     * <p>Bare farmland at scale means the crops were destroyed after placement —
     * trampled, flooded, or updated off their soil — which the player sees as a
     * field of floating seeds.
     */
    private static void checkField(ServerLevel level, Building building, BlockPos origin,
                                   int floor, int wallHalfW, int wallHalfD,
                                   List<Fault> faults) {
        int farmland = 0;
        int planted = 0;
        java.util.Set<BlockPos> nowPlanted = new java.util.HashSet<>();
        for (int dx = -wallHalfW; dx <= wallHalfW; dx++) {
            for (int dz = -wallHalfD; dz <= wallHalfD; dz++) {
                BlockPos soil = new BlockPos(origin.getX() + dx, floor - 1, origin.getZ() + dz);
                if (!level.isLoaded(soil)) {
                    continue;
                }
                if (level.getBlockState(soil).is(Blocks.FARMLAND)) {
                    farmland++;
                    if (level.getBlockState(soil.above()).is(BlockTags.CROPS)) {
                        planted++;
                        nowPlanted.add(soil.above());
                    }
                }
            }
        }

        // Name what vanished since last sweep, and what stands in its place.
        java.util.Set<BlockPos> before = LAST_PLANTED.put(origin, nowPlanted);
        if (before != null) {
            int reported = 0;
            for (BlockPos was : before) {
                if (nowPlanted.contains(was) || !level.isLoaded(was)) {
                    continue;
                }
                if (reported++ >= MAX_VANISHED_REPORTED) {
                    break;
                }
                faults.add(new Fault(building.blueprintId(), origin,
                        "crop vanished at " + was.toShortString()
                                + " — there now: " + level.getBlockState(was).getBlock()
                                + ", soil: " + level.getBlockState(was.below()).getBlock()));
            }
        }
        if (farmland >= MIN_FIELD_TO_JUDGE && planted * 2 < farmland) {
            faults.add(new Fault(building.blueprintId(), origin,
                    "half the field is bare — " + farmland + " farmland, "
                            + planted + " planted"));
        }

        AABB box = new AABB(
                origin.getX() - wallHalfW, floor - 1, origin.getZ() - wallHalfD,
                origin.getX() + wallHalfW + 1, floor + 3, origin.getZ() + wallHalfD + 1);
        int loose = level.getEntities((Entity) null, box,
                entity -> entity instanceof ItemEntity && entity.isAlive()).size();
        if (loose >= LOOSE_ITEM_THRESHOLD) {
            faults.add(new Fault(building.blueprintId(), origin,
                    "items scattered over the field (" + loose
                            + ") — something is popping the crops"));
        }
    }

    /**
     * Two buildings whose walls share ground have been built through each other.
     *
     * <p>Judged on walls rather than plots: neighbouring cleared shelves may meet
     * by design, but masonry inside masonry is always the destructive case.
     */
    private static void auditOverlaps(List<Building> present, List<Fault> faults) {
        for (int i = 0; i < present.size(); i++) {
            for (int j = i + 1; j < present.size(); j++) {
                Building a = present.get(i);
                Building b = present.get(j);
                if (!a.footprint().isKnown() || !b.footprint().isKnown()) {
                    continue;
                }
                int reachX = wallHalf(a.footprint().width()) + wallHalf(b.footprint().width());
                int reachZ = wallHalf(a.footprint().depth()) + wallHalf(b.footprint().depth());
                if (Math.abs(a.origin().x() - b.origin().x()) <= reachX
                        && Math.abs(a.origin().z() - b.origin().z()) <= reachZ) {
                    faults.add(new Fault(a.blueprintId(),
                            new BlockPos(a.origin().x(), a.origin().y(), a.origin().z()),
                            "its walls run through " + b.blueprintId()
                                    + " at " + b.origin()));
                }
            }
        }
    }

    // --- small helpers ---

    private static int wallHalf(int plotSpan) {
        return Math.max(1, plotSpan / 2 - BlueprintPlacer.APRON_MARGIN);
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Which way is out, from a spot on a building's wall ring. */
    private static Direction outward(BlockPos origin, BlockPos wall) {
        int dx = wall.getX() - origin.getX();
        int dz = wall.getZ() - origin.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** The rectangle of columns at these half-extents around the origin. */
    private static List<BlockPos> ring(BlockPos origin, int halfW, int halfD, int step) {
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -halfW; dx <= halfW; dx += step) {
            spots.add(origin.offset(dx, 0, -halfD));
            spots.add(origin.offset(dx, 0, halfD));
        }
        for (int dz = -halfD + 1; dz < halfD; dz += step) {
            spots.add(origin.offset(-halfW, 0, dz));
            spots.add(origin.offset(halfW, 0, dz));
        }
        return spots;
    }

    private static boolean isPath(String blueprintId) {
        return !BuildPlanner.holdsGround(blueprintId);
    }

    /** A crop farm, as opposed to the animal one. Levels and styles both allowed for. */
    private static boolean isCropFarm(String blueprintId) {
        String path = Identifier.parse(BuildPlanner.baseIdOf(blueprintId)).getPath();
        return path.equals("farm") || path.endsWith("/farm");
    }
}
