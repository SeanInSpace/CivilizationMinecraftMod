package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.world.BlueprintPlacer.Placement;
import com.kingdoms.sim.settlement.BuildingSizes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pieces a building is made of, above the box.
 *
 * <p>{@code BlueprintPlacer.cabin} draws a box: a floor, four walls, a doorway
 * and a flat slab on top. That was every building in the mod, and it is why a
 * settlement read as sheds — a village is not told apart by its floor plans, it
 * is told apart by its roofs. Everything here is a piece that goes <em>on</em>
 * that box, so a cottage and a burgher's house can be the same arithmetic
 * underneath and still not look like each other.
 *
 * <p><strong>Every part is a pure function of (base, footprint, parameters).</strong>
 * Nothing reads the world and nothing rolls a die. That is not tidiness: a
 * repair is the difference between the drawing and what is standing
 * ({@code BlueprintPlacer.owedOf}), so a drawing that came out differently the
 * second time would have a crew forever knocking down and relaying the half of
 * the roof that changed its mind. Where a building does need to differ from its
 * neighbor, the difference is hashed out of the building's own origin — see
 * {@link HouseStyle.Variation} — which is a constant for the life of that
 * building.
 *
 * <p><strong>Last write wins.</strong> A plan is a list, not a set, and
 * {@code BlueprintPlacer.supersededIn} settles a cell that is written twice in
 * favour of the later write. Several parts here rely on that: a plinth is a
 * course of stone written over the course of planks the wall already laid, and
 * the roof's eave is written over the flat slab the box put on top. The order
 * the parts are applied in is therefore part of their meaning, and
 * {@link #dress} is where that order is written down once.
 *
 * <p><strong>Nothing here leaves the plot.</strong> The one part that reaches
 * past the walls is the eave, which overhangs by a single block — and a
 * building's plot is its footprint plus {@link BuildingSizes#APRON}, which is
 * also one. So an overhanging roof lands on its own doorstep and never on the
 * neighbor's. {@code PartsTest} asserts it rather than trusting it.
 */
final class Parts {

    private Parts() {
    }

    /** A cell no roof reaches. Distinct from zero, which is the eave. */
    private static final int NO_COURSE = -1;

    // --- roofs ---------------------------------------------------------------

    /**
     * A gable roof: two slopes running up from the long eaves to a ridge.
     *
     * <p>One in one — a course of stairs per block inward — so the ridge stands
     * {@code ceil(depth / 2)} above the wall plate and the pitch is the same on
     * every building whatever its size. The ridge runs along x, which is across
     * the door, because a building is drawn facing south and a house's ridge is
     * parallel to its street.
     *
     * <p>Three things are laid, and the eave is the one worth naming:
     * <ul>
     *   <li>an <strong>eave</strong> one block outside each long wall and one
     *       course below the plate, which is what makes a roof look like it is
     *       sheltering a wall rather than sitting on one;</li>
     *   <li>the <strong>slopes</strong>, stairs facing uphill so each tread
     *       steps down toward the eave;</li>
     *   <li>the <strong>gable ends</strong>, the triangle of wall closing each
     *       short end off — without which the attic is simply open at both ends.</li>
     * </ul>
     *
     * @param y      the course of the wall plate: where the roof's first,
     *               lowest full course goes, directly over the walls
     * @param stairs the roof's stair block, whose {@code FACING} is set here and
     *               turned later by {@code BlueprintPlacer.turn}
     * @param ridge  a full block for the ridge line and for any cell where two
     *               slopes meet rather than climb
     * @param gable  what the triangular ends are filled with, normally the wall
     * @return the topmost course the roof occupies, relative to {@code base}
     */
    static int gableRoof(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                         int y, Block stairs, Block ridge, Block gable) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        int[][] course = emptyCourses(rx, rz);
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                set(course, rx, rz, dx, dz,
                        Math.abs(dz) > rz ? 0 : rz - Math.abs(dz) + 1);
            }
        }
        layRoof(blocks, base, course, rx, rz, y, stairs, ridge);

        // The ends. Each course of the slope leaves the cells between its two
        // stairs open, and at the short walls those cells are the gable.
        for (int side = -1; side <= 1; side += 2) {
            for (int up = 0; up < rz; up++) {
                for (int dz = -(rz - up) + 1; dz <= rz - up - 1; dz++) {
                    add(blocks, base.offset(side * rx, y + up, dz), gable);
                }
            }
        }
        return y + rz;
    }

    /**
     * A hipped roof: slopes on all four sides, meeting in a short ridge or a point.
     *
     * <p>Where the gable takes its rise from the depth alone, this takes it from
     * how far a cell is from the nearest open air in any direction — which is
     * the same thing on the long axis of a rectangle and gives the hip its
     * corners on the short one. A square comes to a point; an oblong comes to a
     * ridge as long as the difference between its spans.
     *
     * <p><strong>It is measured, not derived, so it works on a shape that is not
     * a rectangle.</strong> The croft is an L, and the distance measured here
     * bends round its corner and puts a valley in the crook by itself. That is
     * the reason a hip is what an L gets whatever its people would otherwise
     * build: a gable over an L is two gables and a valley that has to be
     * authored, and a roof nobody can check is worse than a roof in the wrong
     * idiom.
     *
     * @param cap the most courses the roof may rise, or zero for no limit. A
     *            capped hip is flat on top — which is a roof some peoples
     *            genuinely build, and the whole of what makes a goblin house
     *            squat rather than merely brown.
     * @return the topmost course the roof occupies, relative to {@code base}
     */
    static int hipRoof(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                       int y, Block stairs, Block ridge, int cap) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        int[][] course = insetCourses(size, rx, rz, cap);
        layRoof(blocks, base, course, rx, rz, y, stairs, ridge);
        int highest = 0;
        for (int[] column : course) {
            for (int rise : column) {
                highest = Math.max(highest, rise);
            }
        }
        return y + highest - 1;
    }

    /**
     * How far every cell of a footprint is from the open air round it.
     *
     * <p>Measured as steps along the axes rather than as the crow flies, because
     * only that measure never jumps: a roof whose rise changed by two between
     * neighboring cells is a roof with a hole in it. One forward pass and one
     * backward pass over the box is the whole of it.
     *
     * <p>The ring outside the footprint is kept at zero — which is the eave, and
     * the reason a single sweep in {@link #layRoof} lays the overhang and the
     * slope without knowing the difference.
     */
    private static int[][] insetCourses(BuildingSizes.Size size, int rx, int rz, int cap) {
        int far = 4 * (rx + rz + 4);
        int[][] course = new int[2 * rx + 3][2 * rz + 3];
        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                set(course, rx, rz, dx, dz, covers(size, rx, rz, dx, dz) ? far : 0);
            }
        }
        for (int i = 0; i < course.length; i++) {
            for (int j = 0; j < course[i].length; j++) {
                if (i > 0) {
                    course[i][j] = Math.min(course[i][j], course[i - 1][j] + 1);
                }
                if (j > 0) {
                    course[i][j] = Math.min(course[i][j], course[i][j - 1] + 1);
                }
            }
        }
        for (int i = course.length - 1; i >= 0; i--) {
            for (int j = course[i].length - 1; j >= 0; j--) {
                if (i + 1 < course.length) {
                    course[i][j] = Math.min(course[i][j], course[i + 1][j] + 1);
                }
                if (j + 1 < course[i].length) {
                    course[i][j] = Math.min(course[i][j], course[i][j + 1] + 1);
                }
            }
        }
        if (cap > 0) {
            for (int[] column : course) {
                for (int j = 0; j < column.length; j++) {
                    column[j] = Math.min(column[j], cap);
                }
            }
        }
        // A cell of open air only carries an eave if the building is next to it.
        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                if (covers(size, rx, rz, dx, dz) || touchesBuilding(size, rx, rz, dx, dz)) {
                    continue;
                }
                set(course, rx, rz, dx, dz, NO_COURSE);
            }
        }
        return course;
    }

    /** Whether the building stands anywhere in the eight cells round this one. */
    private static boolean touchesBuilding(BuildingSizes.Size size, int rx, int rz,
                                           int dx, int dz) {
        for (int ax = -1; ax <= 1; ax++) {
            for (int az = -1; az <= 1; az++) {
                if ((ax != 0 || az != 0) && covers(size, rx, rz, dx + ax, dz + az)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean covers(BuildingSizes.Size size, int rx, int rz, int dx, int dz) {
        return Math.abs(dx) <= rx && Math.abs(dz) <= rz && size.covers(dx, dz);
    }

    /**
     * Lays a roof from a course map: one block per cell, at the course it names.
     *
     * <p>The whole of a roof's shape is in the map; this only has to decide what
     * kind of block a cell wants, which it reads off its neighbors:
     * <ul>
     *   <li>exactly one neighbor a course higher — a straight stair, facing it,
     *       so the tall half of the tread is the uphill side;</li>
     *   <li>no such neighbor but a higher corner — an outer stair, the shape
     *       that turns a hip round its corner;</li>
     *   <li>nothing higher at all — the top of the roof, which takes a full
     *       block. That is the ridge on a gable, the point or short ridge on a
     *       hip, and the flat deck of a capped one.</li>
     * </ul>
     *
     * <p>Two or more higher neighbors means a valley, which only an L has; it
     * takes the full block too, because a valley of stairs is a valley that
     * leaks.
     */
    private static void layRoof(List<Placement> blocks, BlockPos base, int[][] course,
                                int rx, int rz, int y, Block stairs, Block ridge) {
        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                int rise = at(course, rx, rz, dx, dz);
                if (rise == NO_COURSE) {
                    continue;
                }
                BlockPos pos = base.offset(dx, y + rise - 1, dz);
                Direction uphill = soleUphill(course, rx, rz, dx, dz, rise);
                if (uphill != null) {
                    add(blocks, pos, straightStair(stairs, uphill));
                    continue;
                }
                int[] corner = uphillCorner(course, rx, rz, dx, dz, rise);
                if (corner != null) {
                    add(blocks, pos, cornerStair(stairs, corner[0], corner[1]));
                } else {
                    add(blocks, pos, ridge);
                }
            }
        }
    }

    /** The one neighbor a course higher, or null when there is not exactly one. */
    private static Direction soleUphill(int[][] course, int rx, int rz,
                                        int dx, int dz, int rise) {
        Direction found = null;
        for (Direction way : Direction.Plane.HORIZONTAL) {
            if (at(course, rx, rz, dx + way.getStepX(), dz + way.getStepZ()) == rise + 1) {
                if (found != null) {
                    return null;   // a valley, not a slope
                }
                found = way;
            }
        }
        return found;
    }

    /** The diagonal a course higher, as {@code {stepX, stepZ}}, or null. */
    private static int[] uphillCorner(int[][] course, int rx, int rz,
                                      int dx, int dz, int rise) {
        int[][] diagonals = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] way : diagonals) {
            if (at(course, rx, rz, dx + way[0], dz + way[1]) == rise + 1) {
                return way;
            }
        }
        return null;
    }

    /**
     * A stair whose tall half is on the given side.
     *
     * <p>{@code FACING} names the raised half: the vanilla model puts the upper
     * element on the east side and draws {@code facing=east} unrotated. So a
     * tread that steps down away from the ridge faces the ridge, and a roof
     * built the other way round would be a set of stairs somebody could not walk
     * up.
     */
    private static BlockState straightStair(Block stairs, Direction facing) {
        return stairs.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    /**
     * A stair with its tall quarter on the given diagonal: a hip's corner.
     *
     * <p>{@code OUTER_LEFT} keeps the quarter on the facing side and the side
     * anticlockwise of it, {@code OUTER_RIGHT} the clockwise one — which is what
     * the vanilla shape table says and not a guess: {@code StairBlock.getShape}
     * looks the outer shape up under {@code facing} for the left form and under
     * {@code facing.getClockWise()} for the right.
     *
     * <p>Both forms survive a quarter turn untouched, because left and right are
     * measured from {@code FACING} and {@code FACING} is what a rotation moves.
     */
    private static BlockState cornerStair(Block stairs, int stepX, int stepZ) {
        Direction facing = stepZ < 0 ? Direction.NORTH : Direction.SOUTH;
        Direction across = stepX < 0 ? Direction.WEST : Direction.EAST;
        return stairs.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, across == facing.getCounterClockWise()
                        ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT);
    }

    private static int[][] emptyCourses(int rx, int rz) {
        int[][] course = new int[2 * rx + 3][2 * rz + 3];
        for (int[] column : course) {
            Arrays.fill(column, NO_COURSE);
        }
        return course;
    }

    private static int at(int[][] course, int rx, int rz, int dx, int dz) {
        int i = dx + rx + 1;
        int j = dz + rz + 1;
        if (i < 0 || j < 0 || i >= course.length || j >= course[i].length) {
            return NO_COURSE;
        }
        return course[i][j];
    }

    private static void set(int[][] course, int rx, int rz, int dx, int dz, int rise) {
        course[dx + rx + 1][dz + rz + 1] = rise;
    }

    // --- what goes on a roof, and beside a wall ------------------------------

    /**
     * A chimney: one column of masonry from the floor up through the roof.
     *
     * <p>Drawn in the plane of a wall rather than out in the room, so it takes
     * the wall's own cells and nothing of the floor a household lives on. It is
     * written after the roof on purpose: the course where it crosses the slope
     * is a cell the roof has already filled, and the later write is what stands.
     *
     * @param toY the course of the pot, which wants to clear the ridge or the
     *            smoke blows straight back down it
     */
    static void chimney(List<Placement> blocks, BlockPos base, int dx, int dz,
                        int fromY, int toY, Block masonry) {
        for (int y = fromY; y <= toY; y++) {
            add(blocks, base.offset(dx, y, dz), masonry);
        }
    }

    /**
     * A shed dormer: three cells of the front slope stood up into a window.
     *
     * <p>A window in the middle, a cheek either side, and a small flat roof back
     * into the slope. Deliberately the simple kind — a gabled dormer needs its
     * own ridge and two valleys where it meets the main roof, and a valley of
     * stairs is where roof arithmetic stops being checkable.
     *
     * <p>Wants a roof of at least two courses and a clear block of wall either
     * side of the middle, so the caller places it away from the gable ends.
     *
     * @param y  the course of the wall plate, as {@link #gableRoof} takes it
     * @param dx which column of the front slope stands up
     */
    static void dormer(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                       int dx, int y, Block cheek, Block roof) {
        int rz = size.depth() / 2;
        for (int across = -1; across <= 1; across++) {
            add(blocks, base.offset(dx + across, y + 1, rz),
                    across == 0 ? Blocks.GLASS : cheek);
            add(blocks, base.offset(dx + across, y + 2, rz), roof);
            add(blocks, base.offset(dx + across, y + 2, rz - 1), roof);
        }
    }

    /**
     * Battlements: a merlon, a gap, a merlon, all the way round a flat roof.
     *
     * <p>Here because one people's houses genuinely have no pitch at all, and a
     * flat roof with nothing on it reads as an unfinished one. A parapet is what
     * makes a flat roof a decision.
     *
     * @return the course the merlons stand on
     */
    static int battlements(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                           int y, Block block) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (!covers(size, rx, rz, dx, dz) || !onAnyWall(size, rx, rz, dx, dz)) {
                    continue;
                }
                if (Math.floorMod(dx + dz, 2) == 0) {
                    add(blocks, base.offset(dx, y, dz), block);
                }
            }
        }
        return y;
    }

    /**
     * A porch: a step at the door, two posts, and a lintel over them.
     *
     * <p>No roof of its own. The eave already overhangs the door by exactly one
     * block at exactly this course, so a porch under a pitched roof is the posts
     * that turn that overhang into shelter — which is why this is drawn before
     * the roof and lets the eave write over the lintel. Under a flat roof there
     * is no eave, and the lintel stands as the porch's own little roof.
     */
    static void porch(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                      int wallHeight, Block post, Block lintel) {
        int rz = size.depth() / 2;
        int out = rz + 1;
        add(blocks, base.offset(0, 0, out), lintel);
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y < wallHeight; y++) {
                add(blocks, base.offset(side, y, out), post);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            add(blocks, base.offset(dx, wallHeight, out), lintel);
        }
    }

    // --- what a wall is made of ----------------------------------------------

    /**
     * A plinth: the lowest course of a wall, in stone instead of timber.
     *
     * <p>Written over the course the wall already laid rather than instead of
     * it, and only where that course is actually wall or frame — so the doorway,
     * which is a gap and holds nothing, stays a doorway. That rule is why a
     * plinth cannot be told to seal a house shut.
     */
    static void plinth(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                       int y, Block stone, Block wall, Block frame) {
        Map<BlockPos, Block> drawn = drawnSoFar(blocks);
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                BlockPos pos = base.offset(dx, y, dz);
                Block standing = drawn.get(pos);
                if (standing == wall || standing == frame) {
                    add(blocks, pos, stone);
                }
            }
        }
    }

    /**
     * Half-timbering: a post of timber through the plaster every few blocks.
     *
     * <p>Counted along whichever wall the cell belongs to, so posts read as
     * uprights on every face rather than as a diagonal across the building. Like
     * the plinth it only writes where the wall block is standing, so it can
     * never close a window or a door.
     *
     * @param every how many blocks of wall between posts
     */
    static void halfTimber(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                           int fromY, int toY, Block timber, Block wall, int every) {
        if (every <= 0) {
            return;
        }
        Map<BlockPos, Block> drawn = drawnSoFar(blocks);
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (!covers(size, rx, rz, dx, dz)) {
                    continue;
                }
                int along = onZWall(size, rx, rz, dx, dz) ? dx : dz;
                if (Math.floorMod(along, every) != 0) {
                    continue;
                }
                for (int y = fromY; y <= toY; y++) {
                    BlockPos pos = base.offset(dx, y, dz);
                    if (drawn.get(pos) == wall) {
                        add(blocks, pos, timber);
                    }
                }
            }
        }
    }

    /**
     * More windows: a pane at a spacing along every wall, beside the one the box
     * already puts on each center line.
     *
     * <p>A single window per face is what made every building read as a shed
     * with a hole in it. Glass costs a town nothing — see
     * {@code BlueprintPlacer.materialFor} — so this is the cheapest difference
     * between a hut and a house there is.
     *
     * <p>Counted outward from the center line rather than from the origin, and
     * starting one block off it, so a row is symmetrical about the door and —
     * the part that is not decoration — never lands on a column
     * {@link #halfTimber} wants. Posts sit on even counts and panes on odd ones,
     * which is why a wall comes out post, pane, post, pane instead of a row of
     * windows punched through the uprights. The first cut of this counted from
     * the origin at the same spacing as the posts, and every pane it asked for
     * turned out to be standing in a post already: a nine-wide house came out
     * with the three windows the plain box had always given it.
     *
     * @param spacing blocks between panes, varied per building
     */
    static void windowRow(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                          int y, Block wall, int spacing) {
        if (spacing <= 0) {
            return;
        }
        Map<BlockPos, Block> drawn = drawnSoFar(blocks);
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (!covers(size, rx, rz, dx, dz)) {
                    continue;
                }
                int along = onZWall(size, rx, rz, dx, dz) ? dx : dz;
                if (along == 0 || Math.floorMod(Math.abs(along) - 1, spacing) != 0) {
                    continue;   // the center line already has one
                }
                BlockPos pos = base.offset(dx, y, dz);
                if (drawn.get(pos) == wall) {
                    add(blocks, pos, Blocks.GLASS);
                }
            }
        }
    }

    /**
     * Shutters: a pair of open trapdoors flat against the wall beside a window.
     *
     * <p>Found by looking for the glass rather than by being told where the
     * windows are, so a style that moves its windows keeps its shutters without
     * anybody having to remember to move those too.
     *
     * <p>They stand in the cell outside the wall, which is the doorstep ring and
     * still the building's own ground. An open trapdoor's panel sits on the face
     * away from {@code FACING}, so a shutter on a south wall faces south and
     * ends up pressed against the outside of it.
     */
    static void shutters(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                         int y, Block trapdoor) {
        Map<BlockPos, Block> drawn = drawnSoFar(blocks);
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (!covers(size, rx, rz, dx, dz)
                        || drawn.get(base.offset(dx, y, dz)) != Blocks.GLASS) {
                    continue;
                }
                boolean onZ = onZWall(size, rx, rz, dx, dz);
                Direction out = onZ
                        ? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
                        : (dx > 0 ? Direction.EAST : Direction.WEST);
                BlockState leaf = trapdoor.defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, out)
                        .setValue(TrapDoorBlock.OPEN, true)
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM);
                for (int side = -1; side <= 1; side += 2) {
                    BlockPos beside = base.offset(
                            dx + out.getStepX() + (onZ ? side : 0),
                            y,
                            dz + out.getStepZ() + (onZ ? 0 : side));
                    // A leaf hangs on the wall cell behind it. Where that cell
                    // holds nothing the wall has a gap there, and the only gap
                    // in a wall at this height is a doorway -- a shutter across
                    // the door is a door nobody can use.
                    if (drawn.get(beside.relative(out.getOpposite())) == null) {
                        continue;
                    }
                    add(blocks, beside, leaf);
                }
            }
        }
    }

    /** Whether this cell's wall faces along z — which decides what a post counts. */
    private static boolean onZWall(BuildingSizes.Size size, int rx, int rz, int dx, int dz) {
        return !covers(size, rx, rz, dx, dz - 1) || !covers(size, rx, rz, dx, dz + 1);
    }

    private static boolean onAnyWall(BuildingSizes.Size size, int rx, int rz, int dx, int dz) {
        return onZWall(size, rx, rz, dx, dz)
                || !covers(size, rx, rz, dx - 1, dz) || !covers(size, rx, rz, dx + 1, dz);
    }

    // --- the whole outside of a building, in one call ------------------------

    /**
     * Everything a style puts on a plain box, in the one order that works.
     *
     * <p>The order is the point and it is not arbitrary. The plinth and the
     * timbering write over the wall, so they go before the windows that punch
     * holes in it; the windows go before the shutters that look for them; the
     * porch goes before the roof so the eave can be its roof; and the chimney
     * goes last of all, because it has to know how high the ridge ended up in
     * order to clear it.
     *
     * <p>Written to be called by any building and not only by a house. The trade
     * buildings are the same box underneath, and this is the vocabulary they
     * will be given.
     */
    static void dress(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                      int wallHeight, HouseStyle style) {
        HouseStyle.Variation how = HouseStyle.Variation.forOrigin(base);
        int rx = size.width() / 2;
        HouseStyle.Roof shape = style.roofFor(size);

        plinth(blocks, base, size, 1, style.plinth(), style.wall(), style.frame());
        halfTimber(blocks, base, size, 2, wallHeight,
                style.timber(), style.wall(), style.timberEvery());
        windowRow(blocks, base, size, 2, style.wall(), how.windowSpacing());
        if (style.porch()) {
            porch(blocks, base, size, wallHeight, style.post(), style.roofRidge());
        }

        int top = switch (shape) {
            case GABLE -> gableRoof(blocks, base, size, wallHeight + 1,
                    style.roofStairs(), style.roofRidge(), style.gable());
            case HIP -> hipRoof(blocks, base, size, wallHeight + 1,
                    style.roofStairs(), style.roofRidge(), style.roofCap());
            case FLAT -> battlements(blocks, base, size, wallHeight + 2, style.frame());
        };
        if (style.dormers() && shape == HouseStyle.Roof.GABLE && size.depth() >= 5) {
            for (int dx : dormerColumns(rx)) {
                dormer(blocks, base, size, dx, wallHeight + 1, style.wall(),
                        style.roofRidge());
            }
        }
        if (style.chimney()) {
            chimney(blocks, base, how.chimneySide() * rx, 1, 1, top + 2, style.plinth());
        }
        if (how.shutters()) {
            shutters(blocks, base, size, 2, style.trapdoor());
        }
    }

    /**
     * Which columns of the front slope stand up as dormers.
     *
     * <p>Kept two blocks clear of each gable end, because a dormer's cheeks need
     * a slope either side of them and the end of the roof has none. A narrow
     * house gets the one over its door; anything wider gets a pair.
     */
    private static int[] dormerColumns(int rx) {
        if (rx >= 4) {
            return new int[]{-(rx - 2), rx - 2};
        }
        return rx >= 2 ? new int[]{0} : new int[0];
    }

    // --- reading a plan back --------------------------------------------------

    /**
     * What the plan leaves standing at each cell so far.
     *
     * <p>Last write wins, exactly as {@code BlueprintPlacer.supersededIn} has
     * it, so a part asking "is this cell wall?" gets the same answer the world
     * will give once the building is finished.
     */
    private static Map<BlockPos, Block> drawnSoFar(List<Placement> blocks) {
        Map<BlockPos, Block> drawn = new HashMap<>(blocks.size() * 2);
        for (Placement placement : blocks) {
            drawn.put(placement.pos(), placement.state().getBlock());
        }
        return drawn;
    }

    /**
     * The highest course anything has been drawn at, counted from the base.
     *
     * <p>A building reports its height so the site can be cleared that far up,
     * and a roof — or a chimney over a roof — is now the thing that decides it.
     * Measured off the plan rather than worked out again from the parts, because
     * a second piece of arithmetic saying how tall a house is is the same drift
     * the size table was written to end.
     *
     * @param from where this building's own placements start in the list
     */
    static int topOf(List<Placement> blocks, BlockPos base, int from) {
        int top = 0;
        for (int i = from; i < blocks.size(); i++) {
            top = Math.max(top, blocks.get(i).pos().getY() - base.getY());
        }
        return top;
    }

    private static void add(List<Placement> blocks, BlockPos pos, Block block) {
        blocks.add(new Placement(pos, block.defaultBlockState(), null));
    }

    private static void add(List<Placement> blocks, BlockPos pos, BlockState state) {
        blocks.add(new Placement(pos, state, null));
    }
}
