package com.civilization.neoforge.world;

import com.keystone.api.LoadedBlueprint;
import com.keystone.api.PlannedBlock;
import com.civilization.neoforge.block.BuildingPostBlock;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Beds;
import com.civilization.sim.settlement.BlueprintCheck;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingSizes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading a hand-authored building: what is actually in the file.
 *
 * <p>The bridge between a pile of block states and the two things the rest of
 * the mod wants from one — the facts a settlement has to remember about this
 * building ({@link Building.Authored}) and the counts the validator judges it by
 * ({@link BlueprintCheck.Survey}). Both come out of one pass, because they are
 * answers to the same question and two passes would eventually disagree about
 * how many beds there are.
 *
 * <p><strong>Everything is measured about the cell that lands on the plot</strong>,
 * which is the middle of the file's own box and not the cell the file names as
 * its anchor: see {@link #plotCell}, and {@code BlueprintPlacer.fromBlueprint},
 * which lays the structure about the same cell. The two have to agree or the beds
 * this finds are in the wrong place.
 *
 * <p><strong>Everything is worked out in the un-turned frame</strong> — the one
 * where the front wall is at {@code +z}, which is the frame {@link Beds} and
 * every drawing method in {@link BlueprintPlacer} work in. A blueprint arrives
 * already turned to face its street, so the first thing done to each cell is to
 * turn it back. That is why there is not a single {@code switch (facing)} in the
 * rules below: the door is at maximum {@code z} and that is the end of it.
 * Positions handed back to the simulation are turned forward again at the last
 * moment, because the record they go on is read against a building that is
 * standing.
 */
public final class AuthoredReading {

    /**
     * The course a head is at: two above the floor.
     *
     * <p>{@link Beds#FLOOR_COURSE} is where feet stand, so this is where a
     * person's head is and where a doorway has to be open for them to walk
     * through it. A wall with a hole at knee height is not a door.
     */
    public static final int HEAD_COURSE = Beds.FLOOR_COURSE + 1;

    private AuthoredReading() {
    }

    /** One pass over a file: what the town remembers, and what the check judges. */
    public record Reading(Building.Authored facts, BlueprintCheck.Survey survey) {
    }

    /**
     * Reads a loaded blueprint that is about to be placed as {@code blueprintId}.
     *
     * @param blueprint already turned so that its own front faces {@code facing}
     * @param facing    quarter turns clockwise the building stands at
     */
    public static Reading read(String blueprintId, LoadedBlueprint blueprint, int facing) {
        Vec3i size = blueprint.size();
        // Measured about the cell that actually lands on the plot, which is the
        // middle of the file's own box and not whatever cell the file names. See
        // plotCell: getting this wrong would measure every rule below -- what is
        // outside the footprint, where the beds are, where the doorstep is --
        // against a point the building is not standing on.
        BlockPos anchor = plotCell(size);

        // The declared shape, in its own frame. A file that is a different size
        // from the declared one is refused elsewhere; here it only decides which
        // cells count as outside, and the bounding box is the honest fallback.
        BuildingSizes.Size declared = BuildingSizes.of(blueprintId);

        // Every cell, un-turned, keyed by its offset from the origin so the
        // second pass can ask whether a neighbour is filled.
        Map<BlockPos, BlockState> local = new HashMap<>();
        int maxZ = Integer.MIN_VALUE;
        int crops = 0;
        boolean post = false;

        record Bed(BlockPos foot, Direction heading) {
        }
        List<Bed> beds = new ArrayList<>();

        for (PlannedBlock block : blueprint.all()) {
            BlockPos turned = block.offset().subtract(anchor);
            BlockPos cell = unturn(turned, facing);
            local.put(cell, block.state());
            maxZ = Math.max(maxZ, cell.getZ());

            BlockState state = block.state();
            if (state.getBlock() instanceof CropBlock) {
                crops++;
            }
            if (state.getBlock() instanceof BuildingPostBlock) {
                post = true;
            }
            // Counted at the foot only. A bed is two blocks and one place to
            // sleep, and counting halves is how a three-bed cottage comes out as
            // six and fails a check it should have passed.
            if (state.getBlock() instanceof BedBlock
                    && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                beds.add(new Bed(cell, state.getValue(BedBlock.FACING)));
            }
        }

        // Beds in a fixed order, because the order is who gets which one. Sorted
        // by position rather than left in whatever order the file's palette
        // happened to walk, so the same building hands out the same beds on the
        // next load — the same reason Beds sorts its sleepers by identity.
        beds.sort((a, b) -> {
            int byZ = Integer.compare(a.foot().getZ(), b.foot().getZ());
            if (byZ != 0) {
                return byZ;
            }
            int byX = Integer.compare(a.foot().getX(), b.foot().getX());
            return byX != 0 ? byX : Integer.compare(a.foot().getY(), b.foot().getY());
        });

        List<SimPos> feet = new ArrayList<>(beds.size());
        List<SimPos> heads = new ArrayList<>(beds.size());
        for (Bed bed : beds) {
            BlockPos head = bed.foot().relative(bed.heading());
            feet.add(sim(turn(bed.foot(), facing)));
            heads.add(sim(turn(head, facing)));
        }

        BlockPos doorstep = doorstepOf(local, maxZ);
        Outside stray = strayOf(local, declared, size);

        Building.Authored facts = new Building.Authored(feet, heads,
                doorstep == null ? null : sim(turn(doorstep, facing)),
                crops > 0 ? crops : Building.UNCOUNTED);

        // The declared spans, which is what the size table is written in. A file
        // is measured in its own frame, never in the one it happens to be turned
        // to, or half the buildings in a town would be compared against the
        // table with their width and depth swapped.
        boolean quarter = Math.floorMod(facing, 2) == 1;
        BlueprintCheck.Survey survey = new BlueprintCheck.Survey(blueprintId,
                quarter ? size.getZ() : size.getX(),
                quarter ? size.getX() : size.getZ(),
                size.getY(),
                feet.size(), post, doorstep != null, crops,
                stray.stray(), stray.low(), stray.high(),
                anchorOffMiddle(blueprint));

        return new Reading(facts, survey);
    }

    /**
     * The cell of a file that lands on the build plot: the middle of its own box.
     *
     * <p><strong>The middle, and never the cell the file names as its anchor.</strong>
     * Honoring the stated one was the obvious reading — Structurize records a
     * {@code primary_offset}, usually the hut block, and honoring it is what puts
     * an imported building on its plot rather than beside it — and it is wrong,
     * for a reason that is nothing to do with the file. A plot in this mod is a
     * point; {@link com.civilization.sim.settlement.Footprint} is a width and a
     * depth measured about that point; the excavation, the apron, the foundation
     * and every overlap check in the simulation are all squared off around it.
     * Laying the structure so that a corner cell lands on the point displaces the
     * building by four blocks while the town goes on recording it centered — so
     * the ground four blocks the other way reads as free, and the plan puts a
     * cottage in it.
     *
     * <p>Nothing is lost by centering. A file whose blocks are not where its own
     * box says they are is already refused by {@code strayOf} and the size rules,
     * so the box's middle <em>is</em> the building's middle for every file that
     * passes at all. What the author meant by naming another cell is reported
     * rather than obeyed: see {@code BlueprintCheck.checkAnchor}.
     *
     * <p>Both spans of a placeable file are odd — that is its own rule — so the
     * middle is a cell and not a seam.
     */
    public static BlockPos plotCell(Vec3i size) {
        return new BlockPos((size.getX() - 1) / 2, 0, (size.getZ() - 1) / 2);
    }

    /**
     * How far the cell the file names is from the one that will actually land on
     * the plot, in blocks: the larger of the two axes.
     *
     * <p>The larger rather than the sum, because it is the number that answers
     * "how far would this building have been displaced" — a corner anchor on a
     * nine-by-nine is four, which is what a reader wants to hear rather than
     * eight.
     */
    private static int anchorOffMiddle(LoadedBlueprint blueprint) {
        BlockPos stated = blueprint.anchor();
        BlockPos middle = plotCell(blueprint.size());
        return Math.max(Math.abs(stated.getX() - middle.getX()),
                Math.abs(stated.getZ() - middle.getZ()));
    }

    /**
     * Where somebody stands to walk in, one block out from the front wall.
     *
     * <p>Found rather than assumed. Every drawing in this mod cuts its doorway
     * in the middle of the front wall, and the doorstep is therefore arithmetic;
     * a building somebody else made has its door wherever they wanted it, and a
     * worker sent to the middle of the wall walks into brickwork.
     *
     * <p>A doorway is a cell on the front plane that a person's head can pass
     * through, which is the test rather than "is it a door block": plenty of
     * perfectly good buildings have an open archway, a trapdoor, or a gate, and
     * nothing but a solid wall should count as no way in. Where more than one
     * opening is found the middle-most is taken, because a front with three
     * arches has a middle one and that is the one the street should run to.
     */
    private static BlockPos doorstepOf(Map<BlockPos, BlockState> local, int maxZ) {
        if (maxZ == Integer.MIN_VALUE) {
            return null;
        }
        List<Integer> openings = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : local.entrySet()) {
            BlockPos cell = entry.getKey();
            if (cell.getZ() != maxZ || cell.getY() != HEAD_COURSE) {
                continue;
            }
            if (!isSolid(entry.getValue())) {
                openings.add(cell.getX());
            }
        }
        // A cell the file simply does not mention is air, and air in a wall is a
        // doorway as much as a door block is. Anything on the front plane whose
        // head course was never written at all counts.
        for (BlockPos cell : local.keySet()) {
            if (cell.getZ() != maxZ) {
                continue;
            }
            BlockPos head = new BlockPos(cell.getX(), HEAD_COURSE, maxZ);
            if (!local.containsKey(head) && !openings.contains(cell.getX())) {
                openings.add(cell.getX());
            }
        }
        if (openings.isEmpty()) {
            return null;
        }
        openings.sort(Integer::compareTo);
        // Nearest the middle of the wall, ties going to the lower side so the
        // answer does not depend on which way a map iterated.
        int best = openings.get(0);
        for (int x : openings) {
            if (Math.abs(x) < Math.abs(best) || (Math.abs(x) == Math.abs(best) && x < best)) {
                best = x;
            }
        }
        // Past the doorstep ring, not merely past the wall. A drawn building's
        // doorstep is worked out from its recorded footprint, which is the walls
        // plus the apron -- so a cottage seven deep has its doorstep five blocks
        // out from the middle and not four. An authored building measured to the
        // wall would put every path, every worker and every delivery one block
        // nearer than the drawn one, which is inside the ring that was cleared
        // for exactly this.
        return new BlockPos(best, 0, maxZ + 1 + BuildingSizes.APRON);
    }

    /**
     * The three ways a cell can fall outside the footprint.
     *
     * <p>Named for what it counts rather than for the shape, deliberately: this
     * is not a {@link com.civilization.sim.settlement.Footprint}, and a reader who
     * assumed it was would look for a width on it.
     */
    private record Outside(int stray, int low, int high) {
    }

    /**
     * How far past its own footprint the file reaches.
     *
     * <p>Split three ways because the three mean completely different things:
     * an eave is fine, a wall standing on the neighbour's doorstep is not, and
     * anything two blocks out is a building that was scanned with its garden.
     * See {@link BlueprintCheck#OVERHANG_MIN_COURSE}.
     */
    private static Outside strayOf(Map<BlockPos, BlockState> local,
                                     BuildingSizes.Size declared, Vec3i size) {
        if (declared == null) {
            return new Outside(0, 0, 0);
        }
        int stray = 0;
        int low = 0;
        int high = 0;
        for (Map.Entry<BlockPos, BlockState> entry : local.entrySet()) {
            BlockPos cell = entry.getKey();
            if (entry.getValue().isAir() || declared.covers(cell.getX(), cell.getZ())) {
                continue;
            }
            if (!withinReach(declared, cell.getX(), cell.getZ())) {
                stray++;
            } else if (cell.getY() < BlueprintCheck.OVERHANG_MIN_COURSE) {
                low++;
            } else {
                high++;
            }
        }
        return new Outside(stray, low, high);
    }

    /** Whether a cell is at most one block outside the covered shape. */
    private static boolean withinReach(BuildingSizes.Size declared, int dx, int dz) {
        int reach = BlueprintCheck.OVERHANG_REACH;
        for (int ox = -reach; ox <= reach; ox++) {
            for (int oz = -reach; oz <= reach; oz++) {
                if (declared.covers(dx + ox, dz + oz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a block stops a person walking through the cell.
     *
     * <p>Measured against {@link EmptyBlockGetter} rather than a level, so a
     * file can be checked without one — the same trick {@code LoadedBlueprint}
     * uses to put a structure in build order without a world.
     */
    private static boolean isSolid(BlockState state) {
        return !state.isAir()
                && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /**
     * Turns a cell back into the frame the tables are written in.
     *
     * <p>The exact inverse of {@link Beds#turned}, which sends {@code (x, z)} to
     * {@code (-z, x)} on a clockwise quarter.
     */
    static BlockPos unturn(BlockPos cell, int facing) {
        int x = cell.getX();
        int z = cell.getZ();
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> new BlockPos(z, cell.getY(), -x);
            case 2 -> new BlockPos(-x, cell.getY(), -z);
            case 3 -> new BlockPos(-z, cell.getY(), x);
            default -> cell;
        };
    }

    /** And forward again, the way the placer turns its blocks. */
    static BlockPos turn(BlockPos cell, int facing) {
        int x = cell.getX();
        int z = cell.getZ();
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> new BlockPos(-z, cell.getY(), x);
            case 2 -> new BlockPos(-x, cell.getY(), -z);
            case 3 -> new BlockPos(z, cell.getY(), -x);
            default -> cell;
        };
    }

    private static SimPos sim(BlockPos pos) {
        return new SimPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
