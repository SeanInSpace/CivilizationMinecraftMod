package com.kingdoms.neoforge.world;

import com.kingdoms.neoforge.world.BlueprintPlacer.Placement;
import com.kingdoms.sim.settlement.BuildingSizes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.List;

/**
 * The pieces that say what a building is <em>for</em>.
 *
 * <p>{@link Parts} is the vocabulary of a dwelling: roofs, plinths, timbering,
 * shutters — the things every house in a people's idiom has. It made a town stop
 * reading as sheds. It did not make a town readable, because a smithy dressed in
 * the same gable as the cottage beside it is still a cottage with an anvil
 * hidden in it, and the only way to tell a mill from a mine was to walk in and
 * click the post.
 *
 * <p>So this is the other half: the parts a trade puts on a box and a home never
 * does. An open forge bay, a sail wheel, a headframe over a shaft, a lean-to
 * stacked with logs, a byre, a scarecrow. Each one is a silhouette rather than a
 * label — something you can name from the far side of the green without reading
 * anything.
 *
 * <p><strong>Everything here obeys the same three rules {@link Parts} does.</strong>
 * A part is a pure function of the base, the footprint and its parameters:
 * nothing reads the world and nothing rolls a die, because a repair is the
 * difference between the drawing and what stands. A cell written twice is
 * settled by the later write, so the order the parts are applied in is part of
 * their meaning. And nothing leaves the plot — the footprint plus the one block
 * of {@link BuildingSizes#APRON} — which is what stops a windmill's sails
 * standing in the neighbor's kitchen. {@code TradePartsTest} asserts all three
 * rather than trusting them.
 *
 * <p><strong>Kept apart from {@link Parts} deliberately.</strong> The house
 * vocabulary is shared by every building in the mod and is the thing a new
 * culture is written against; the trade vocabulary is used by eleven methods and
 * by nothing else. Two files also means the two halves can be worked on at once
 * without either one becoming the place everybody edits.
 */
final class TradeParts {

    private TradeParts() {
    }

    // --- openings -------------------------------------------------------------

    /**
     * An open bay: one whole wall taken out and stood on two posts under a lintel.
     *
     * <p>What a smithy has instead of a front. A forge has to breathe and its
     * fire has to be seen, so the working side of one is a hole with the roof
     * carried over it — which is also, from any distance at all, the single most
     * recognizable thing about the building.
     *
     * <p>The cells that become the opening are written as air rather than left
     * alone, and that is not the same thing: the wall has already been laid by
     * the time this runs, so nothing short of writing over it takes it out
     * again. {@code BlueprintPlacer.finish} sorts an air placement into the
     * excavation and out of the masonry, so the cell ends up genuinely empty and
     * nobody is charged for it.
     *
     * <p>Written before {@link Parts#dress}, so the plinth, the timbering and the
     * window row all find air where the bay is and leave it alone. That is the
     * same rule that keeps a plinth from bricking up a doorway, used on purpose.
     *
     * @param towardX which short wall opens, as -1 or 1
     */
    static void openBay(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                        int towardX, int wallHeight, Block post, Block lintel) {
        int dx = towardX * (size.width() / 2);
        int rz = size.depth() / 2;
        for (int dz = -rz + 1; dz <= rz - 1; dz++) {
            boolean pier = Math.abs(dz) == rz - 1;
            for (int y = 1; y <= wallHeight; y++) {
                if (pier) {
                    add(blocks, base.offset(dx, y, dz), post);
                } else if (y == wallHeight) {
                    add(blocks, base.offset(dx, y, dz), lintel);
                } else {
                    add(blocks, base.offset(dx, y, dz), Blocks.AIR);
                }
            }
        }
    }

    /**
     * A wider doorway: the cabin's one-block gap opened out to a cart's width.
     *
     * <p>A storehouse whose door is the same slot as a cottage's is a storehouse
     * nothing can be carried into. Written as air over the wall the box already
     * laid, and before {@link Parts#dress} for the same reason {@link #openBay}
     * is.
     *
     * @param from the leftmost column of the opening, the door's own being zero
     * @param to   the rightmost, inclusive
     */
    static void widenDoor(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                          int from, int to) {
        int rz = size.depth() / 2;
        for (int dx = from; dx <= to; dx++) {
            for (int y = 1; y <= 2; y++) {
                add(blocks, base.offset(dx, y, rz), Blocks.AIR);
            }
        }
    }

    /**
     * Slats in place of the top course and the gable above it: an open loft.
     *
     * <p>A granary is the one building whose whole point is what is inside it, so
     * the end of it is left open enough to see the hay through — which is what a
     * real one does, because grain that cannot breathe is grain that rots.
     *
     * <p>Only the gable end, and only when the people actually build a gable. A
     * hip has no triangle to open and a flat roof has no end at all; both simply
     * get the slatted top course, which still reads as a vent.
     *
     * @param towardX which end is slatted, as -1 or 1
     */
    static void loftVent(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                         int towardX, int wallHeight, boolean gabled, Block slat) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        int dx = towardX * rx;
        for (int dz = -rz + 1; dz <= rz - 1; dz++) {
            add(blocks, base.offset(dx, wallHeight, dz), slat);
        }
        if (!gabled) {
            return;
        }
        // Exactly the cells Parts.gableRoof fills with the gable block, written
        // over afterwards. Counted the same way it counts them so a change to the
        // pitch cannot leave a slat hanging outside the triangle.
        for (int up = 0; up < rz; up++) {
            for (int dz = -(rz - up) + 1; dz <= rz - up - 1; dz++) {
                add(blocks, base.offset(dx, wallHeight + 1 + up, dz), slat);
            }
        }
    }

    // --- things that stand outside a wall -------------------------------------

    /**
     * A canopy: posts and a board over a wide door.
     *
     * <p>The storehouse's version of a porch, and drawn on the same principle —
     * before the roof, so a pitched eave overhangs it and becomes its cover,
     * while under a flat roof the board stands as the whole of it. See
     * {@link Parts#porch}, which is this one block wide.
     *
     * @param half how far the canopy reaches either side of the door
     */
    static void canopy(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                       int wallHeight, int half, Block post, Block board) {
        int out = size.depth() / 2 + 1;
        for (int side = -1; side <= 1; side += 2) {
            add(blocks, base.offset(side * half, 0, out), board);
            for (int y = 1; y < wallHeight; y++) {
                add(blocks, base.offset(side * half, y, out), post);
            }
        }
        for (int dx = -half; dx <= half; dx++) {
            add(blocks, base.offset(dx, wallHeight, out), board);
        }
    }

    /**
     * A lean-to: a one-block shed roof on posts, against a long wall.
     *
     * <p>Where a carpenter keeps the timber that is not yet furniture. It lives
     * entirely in the doorstep ring, which is the only ground a building has
     * outside its own walls, so it is exactly one block deep — and that is the
     * honest shape of a lean-to anyway.
     *
     * <p>Drawn after {@link Parts#dress}, not before: the shutters a style hangs
     * beside its windows stand in this same ring, and a post is worth more to a
     * lean-to than a shutter is.
     *
     * @param towardX which long wall it leans on, as -1 or 1
     * @param reach   how far it runs either side of the center line
     */
    static void leanTo(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                       int towardX, int reach, int wallHeight, Block post, Block roof) {
        int dx = towardX * (size.width() / 2 + BuildingSizes.APRON);
        Direction uphill = towardX > 0 ? Direction.WEST : Direction.EAST;
        for (int dz = -reach; dz <= reach; dz++) {
            add(blocks, base.offset(dx, wallHeight, dz), stair(roof, uphill));
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y < wallHeight; y++) {
                add(blocks, base.offset(dx, y, side * reach), post);
            }
        }
    }

    /**
     * A flight of steps up the outside of a building, to an upper door.
     *
     * <p>What makes a two-story warehouse read as two stories from the ground.
     * Runs in the doorstep ring along one wall, climbing toward the far end, each
     * tread packed underneath so it is a stair rather than a row of floating
     * treads.
     *
     * @param towardX which wall it climbs, as -1 or 1
     * @param toY     the course of the landing at the top
     * @return the z the landing ends at, which is where the upper door belongs
     */
    static int outsideStair(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                            int towardX, int toY, Block tread, Block pier) {
        int dx = towardX * (size.width() / 2 + BuildingSizes.APRON);
        int rz = size.depth() / 2;
        int dz = rz;
        for (int y = 1; y <= toY && dz >= -rz; y++, dz--) {
            for (int under = 1; under < y; under++) {
                add(blocks, base.offset(dx, under, dz), pier);
            }
            add(blocks, base.offset(dx, y, dz),
                    y == toY ? pier.defaultBlockState() : stair(tread, Direction.NORTH));
        }
        return dz + 1;
    }

    /**
     * A headframe: four legs and a cap frame, straddling the shaft.
     *
     * <p>The one thing that says "mine" from outside rather than from the post.
     * It goes up through the roof on purpose — that is what a pit head looks like
     * — and its legs are set clear of the shaft column so that whatever is cut
     * below stays open to the sky above.
     *
     * @param half how far the legs stand from the shaft
     * @param toY  the course of the cap frame
     */
    static void headframe(List<Placement> blocks, BlockPos base, int half, int toY,
                          Block leg, Block beam) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = 1; y < toY; y++) {
                    add(blocks, base.offset(sx * half, y, sz * half), upright(leg));
                }
            }
        }
        for (int along = -half; along <= half; along++) {
            for (int side = -1; side <= 1; side += 2) {
                add(blocks, base.offset(along, toY, side * half), lying(beam, false));
                add(blocks, base.offset(side * half, toY, along), lying(beam, true));
            }
            add(blocks, base.offset(along, toY, 0), lying(beam, false));
        }
    }

    /**
     * A sail wheel, flat against the wall away from the door.
     *
     * <p>A windmill's cap and its four sweeps are a shape no box can be made to
     * hold: the sweeps of a real one are longer than the tower is wide, and a
     * building's ground here is its footprint and one block of doorstep. So the
     * wheel is mounted in the plane of the rear wall, in the doorstep ring, where
     * it has the whole height of the building to turn in and reaches no further
     * out than a roof's eave already does.
     *
     * <p>Four sweeps of fence with a canvas at each tip, which is what a sweep
     * is: a lattice with cloth stretched over the outer half of it.
     *
     * @param hubY the course the axle runs at
     */
    static void sailWheel(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                          int hubY, Block hub, Block sweep, Block canvas) {
        int dz = -(size.depth() / 2 + BuildingSizes.APRON);
        add(blocks, base.offset(0, hubY, dz), lying(hub, false));
        for (int side = -1; side <= 1; side += 2) {
            add(blocks, base.offset(side, hubY, dz), sweep);
            add(blocks, base.offset(2 * side, hubY, dz), canvas);
            add(blocks, base.offset(0, hubY + side, dz), sweep);
            add(blocks, base.offset(0, hubY + 2 * side, dz), canvas);
        }
    }

    /**
     * A scarecrow: a cross of fence with a carved head on it.
     *
     * <p>Stands outside the crop, in the doorstep ring, because a scarecrow
     * planted in the rows would be a crop block the field no longer has — and the
     * number of crop blocks in a field is what the whole unwatched food economy
     * is proportional to. See {@code Field.CROP_BLOCKS}.
     *
     * @param y the course its feet stand on
     */
    static void scarecrow(List<Placement> blocks, BlockPos base, int dx, int dz, int y,
                          Block stake) {
        add(blocks, base.offset(dx, y, dz), stake);
        add(blocks, base.offset(dx, y + 1, dz), stake);
        add(blocks, base.offset(dx - Integer.signum(dx), y + 1, dz), stake);
        add(blocks, base.offset(dx, y + 1, dz - Integer.signum(dz)), stake);
        add(blocks, base.offset(dx, y + 2, dz), Blocks.CARVED_PUMPKIN.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
    }

    /**
     * A byre: an open shelter at the head of a compound, with feed and a trough.
     *
     * <p>What turns a run of pens into a farm. Two posts and a roof over the
     * corner of the first pen, set clear of the middle so the shepherd's own
     * spot at the center of the strip stays open — see {@code ShepherdWorker},
     * which spawns a beast at the center of each pen's box and would drop it into
     * a wall if this stood there.
     *
     * @param dz the row the byre's back stands on
     */
    static void byre(List<Placement> blocks, BlockPos base, int dx, int dz, Block post,
                     Block roof, Block feed) {
        for (int back = 0; back <= 1; back++) {
            for (int y = 1; y <= 2; y++) {
                add(blocks, base.offset(dx, y, dz + back), upright(post));
            }
            add(blocks, base.offset(dx, 3, dz + back), roof);
            add(blocks, base.offset(dx - 1, 3, dz + back), roof);
        }
        add(blocks, base.offset(dx - 1, 1, dz), lying(feed, true));
        add(blocks, base.offset(dx - 1, 1, dz + 1),
                Blocks.WATER_CAULDRON.defaultBlockState());
    }

    // --- things that stand inside one -----------------------------------------

    /**
     * A pile of logs on their sides, the way timber is actually stacked.
     *
     * <p>An upright log is a tree; a log lying across the grain is stock. The
     * axis is the whole of the difference and it costs nothing to say.
     *
     * <p>Never left as the top of its own column. A lumberjack fells whatever log
     * stands highest in a village column — see {@code LumberjackWorker.findLog} —
     * so a woodpile in the open is a woodpile the town carries away again. Every
     * caller here stacks under a roof.
     */
    static void logPile(List<Placement> blocks, BlockPos base, int dx, int dz,
                        int length, int courses, Block log) {
        for (int along = 0; along < length; along++) {
            for (int up = 0; up < courses - along % 2; up++) {
                add(blocks, base.offset(dx, 1 + up, dz + along), lying(log, false));
            }
        }
    }

    /**
     * A ladder up a wall, and the deck it climbs to.
     *
     * <p>{@code FACING} on a ladder names the side it is climbed from, so the
     * wall it hangs on is the one behind — which is why this takes the direction
     * the climber faces out toward rather than the wall's own.
     */
    static void ladder(List<Placement> blocks, BlockPos base, int dx, int dz,
                       int fromY, int toY, Direction out) {
        BlockState rung = Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, out);
        for (int y = fromY; y <= toY; y++) {
            add(blocks, base.offset(dx, y, dz), rung);
        }
    }

    /**
     * An upper floor across the inside of a building, with a hole for the ladder.
     *
     * <p>A story is a deck somebody can stand on, and without one a tall wall is
     * only a tall room. The hole is not an omission: a deck laid corner to corner
     * is a deck nobody can get onto.
     */
    static void deck(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                     int y, int holeX, int holeZ, Block floor) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx + 1; dx <= rx - 1; dx++) {
            for (int dz = -rz + 1; dz <= rz - 1; dz++) {
                if (dx == holeX && dz == holeZ) {
                    continue;
                }
                add(blocks, base.offset(dx, y, dz), floor);
            }
        }
    }

    /** One course of a building laid in something else: a floor of worked stone. */
    static void floorOf(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                        Block block) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (size.covers(dx, dz)) {
                    add(blocks, base.offset(dx, 0, dz), block);
                }
            }
        }
    }

    /**
     * A mine's low roof, and the wall dressing that goes under it.
     *
     * <p>{@link Parts#dress} is the whole outside of a house in one call and
     * picks its roof from the people who built it. A pit head is the one trade
     * building that does not take the local pitch: it is a lid over a hole, kept
     * deliberately squat so the headframe stands clear above it, and a mine with
     * a cottage's gable on it is a cottage.
     *
     * <p>Everything below the plate is still the people's own, in the same order
     * {@code dress} uses it — which is why this is here rather than a fifth
     * argument to that.
     *
     * @return the topmost course the roof occupies, relative to {@code base}
     */
    static int dressLow(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                        int wallHeight, HouseStyle style, int cap) {
        HouseStyle.Variation how = HouseStyle.Variation.forOrigin(base);
        Parts.plinth(blocks, base, size, 1, style.plinth(), style.wall(), style.frame());
        Parts.halfTimber(blocks, base, size, 2, wallHeight,
                style.timber(), style.wall(), style.timberEvery());
        Parts.windowRow(blocks, base, size, 2, style.wall(), how.windowSpacing());
        int top = Parts.hipRoof(blocks, base, size, wallHeight + 1,
                style.roofStairs(), style.roofRidge(), cap);
        if (how.shutters()) {
            Parts.shutters(blocks, base, size, 2, style.trapdoor());
        }
        return top;
    }

    // --- what a people's trade buildings are made of --------------------------

    /**
     * The log this people's timber comes as.
     *
     * <p>Read off the wall rather than added to {@link HouseStyle}, because a
     * style is a palette for a <em>house</em> and a woodpile is not a wall. The
     * two peoples who build in fired brick and cut stone still fell ordinary
     * trees, and their carpenter's yard says so.
     */
    static Block logFor(HouseStyle style) {
        Block wall = style.wall();
        if (wall == Blocks.SPRUCE_PLANKS) {
            return Blocks.SPRUCE_LOG;
        }
        if (wall == Blocks.DARK_OAK_PLANKS || wall == Blocks.BRICKS) {
            return Blocks.DARK_OAK_LOG;
        }
        return Blocks.OAK_LOG;
    }

    /**
     * The board that log is sawn into.
     *
     * <p>Not {@link HouseStyle#wall()}, and the difference is not cosmetic. One
     * people builds in stripped oak <em>wood</em>, which is in the logs tag —
     * so a stack of their walling left out in the yard is a stack a lumberjack
     * walks over and fells. Sawn boards are in no such tag and never will be.
     */
    static Block boardFor(HouseStyle style) {
        Block log = logFor(style);
        if (log == Blocks.SPRUCE_LOG) {
            return Blocks.SPRUCE_PLANKS;
        }
        return log == Blocks.DARK_OAK_LOG ? Blocks.DARK_OAK_PLANKS : Blocks.OAK_PLANKS;
    }

    /** The gate that hangs in this people's fence. */
    static Block gateFor(Block fence) {
        if (fence == Blocks.SPRUCE_FENCE) {
            return Blocks.SPRUCE_FENCE_GATE;
        }
        if (fence == Blocks.DARK_OAK_FENCE) {
            return Blocks.DARK_OAK_FENCE_GATE;
        }
        return Blocks.OAK_FENCE_GATE;
    }

    /** The board a trade hangs over its own door, in this people's wood. */
    static Block signFor(Block fence) {
        if (fence == Blocks.SPRUCE_FENCE) {
            return Blocks.SPRUCE_HANGING_SIGN;
        }
        if (fence == Blocks.DARK_OAK_FENCE) {
            return Blocks.DARK_OAK_HANGING_SIGN;
        }
        return Blocks.OAK_HANGING_SIGN;
    }

    // --- block states ---------------------------------------------------------

    /** A log standing the way it grew. */
    static BlockState upright(Block log) {
        return axis(log, Direction.Axis.Y);
    }

    /** A log on its side, across the building or along it. */
    static BlockState lying(Block log, boolean alongZ) {
        return axis(log, alongZ ? Direction.Axis.Z : Direction.Axis.X);
    }

    private static BlockState axis(Block log, Direction.Axis along) {
        BlockState state = log.defaultBlockState();
        return state.hasProperty(RotatedPillarBlock.AXIS)
                ? state.setValue(RotatedPillarBlock.AXIS, along) : state;
    }

    /**
     * A stair with its tall half on the given side.
     *
     * <p>The same convention {@link Parts} uses and for the same reason: vanilla
     * draws {@code facing=east} unrotated with the raised element on the east, so
     * a slope that steps down away from something faces that thing.
     */
    static BlockState stair(Block stairs, Direction facing) {
        return stairs.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    /** A block that wants to face into the room rather than out of the wall. */
    static BlockState facing(Block block, Direction way) {
        BlockState state = block.defaultBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.setValue(HorizontalDirectionalBlock.FACING, way) : state;
    }

    private static void add(List<Placement> blocks, BlockPos pos, Block block) {
        blocks.add(new Placement(pos, block.defaultBlockState(), null));
    }

    private static void add(List<Placement> blocks, BlockPos pos, BlockState state) {
        blocks.add(new Placement(pos, state, null));
    }
}
