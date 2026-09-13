package com.civilization.neoforge.world;

import com.civilization.neoforge.world.BlueprintPlacer.Placement;
import com.civilization.sim.settlement.BuildingSizes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What tells one civic building from another.
 *
 * <p>{@link Parts} answers the question every building asks — what goes on a box,
 * and in what order — and it answers it the same way for all of them, because a
 * roof is a roof. This answers the next question down: given that a hall and an
 * inn and a library are all boxes with roofs on them, what does a player standing
 * in the street see that says <em>hall</em> rather than <em>big shed</em>.
 *
 * <p>The answer is never a new block palette. The palette is the culture's and
 * comes out of {@link HouseStyle} exactly as a house's does — a Burgher library
 * is brick and a Goblin one is mud brick, and that must stay true or the civic
 * buildings become the one part of a town that all six peoples build identically.
 * What changes is the <em>furniture of the outside</em>: a belfry, a stall, an
 * arched window, a crenellated parapet, an awning. A signature, not a skin.
 *
 * <p><strong>Same three rules as {@link Parts}.</strong> Every part is a pure
 * function of its arguments — nothing reads the world and nothing rolls a die,
 * because a repair is the difference between the drawing and what is standing
 * and a drawing that changed its mind would have a crew forever undoing itself.
 * Later writes beat earlier ones, so the order parts are applied in is part of
 * their meaning. And nothing leaves the footprint plus its one-block doorstep:
 * an awning, a lean-to and a flagpole all stand on the building's own ground.
 *
 * <p>The one thing here that is not additive is {@link #keepClear}, which takes
 * placements back out of a plan. It exists because a civic building has cells
 * that are promised to something else — the post a player clicks, the quest
 * board, the walk from the door to either — and the general dressing in
 * {@link Parts#dress} has no way to know that. Removing the placement is the
 * honest fix; laying air over it would only have the builder dig a hole and then
 * fill it back in.
 */
final class CivicParts {

    private CivicParts() {
    }

    // --- the cells a building promises to leave alone -------------------------

    /**
     * Takes every placement standing in one of these cells back out of the plan.
     *
     * <p>Called after the dressing and before the post goes down, so that whatever
     * a style decided to hang on a wall cannot end up across a doorway or on top
     * of the block the whole building answers to. The shutters are the live
     * example: they are placed by looking for glass, a pane one block from the
     * door puts a shutter leaf directly in the doorway, and no amount of care in
     * the shutter code can know that this particular wall has a door in it.
     */
    static void keepClear(List<Placement> blocks, Collection<BlockPos> cells) {
        blocks.removeIf(placement -> cells.contains(placement.pos()));
    }

    /** Every cell of a box, for handing to {@link #keepClear}. */
    static Set<BlockPos> box(BlockPos base, int fromDx, int toDx, int fromDz, int toDz,
                             int fromY, int toY) {
        Set<BlockPos> cells = new HashSet<>();
        for (int dx = fromDx; dx <= toDx; dx++) {
            for (int dz = fromDz; dz <= toDz; dz++) {
                for (int y = fromY; y <= toY; y++) {
                    cells.add(base.offset(dx, y, dz));
                }
            }
        }
        return cells;
    }

    /**
     * The doorway and the walk from it to the post, at head height and below.
     *
     * <p>Two blocks tall, because that is what a person is and what the auditor
     * measures a way in by. The doorway half reaches one block past the wall so
     * that the doorstep outside is clear as well: a building nobody can stand in
     * front of is as shut as a building with no door.
     *
     * <p>Wide only where the wall is. The doorstep outside is cleared on the
     * center line alone, because the cells either side of it out there are where
     * a porch stands its posts — and a way in that took the porch down with it
     * would be a cure worse than the disease.
     *
     * @param doorHalfWidth how far either side of center the opening runs — zero
     *                      for an ordinary door, one for the three-wide entrance
     *                      a hall gets
     * @param toDz          how far in the walk goes, normally the post's own row
     */
    static Set<BlockPos> wayIn(BlockPos base, BuildingSizes.Size size,
                               int doorHalfWidth, int toDz) {
        int rz = size.depth() / 2;
        Set<BlockPos> cells = box(base, -doorHalfWidth, doorHalfWidth, rz, rz, 1, 2);
        cells.addAll(box(base, 0, 0, toDz, rz + 1, 1, 2));
        return cells;
    }

    /**
     * The highest course anything reaches over a square about the middle of a plan.
     *
     * <p>{@link Parts#topOf} measures the whole building, which is the right
     * answer for how far up the site has to be cleared and the wrong one for
     * where a belfry goes: a chimney climbs the gable end and stands two courses
     * proud of the ridge, so a cupola stacked on the tallest thing in the plan
     * would be stacked on the chimney pot. This asks only what is over the middle.
     *
     * @param from where this building's own placements start in the list
     */
    static int topOver(List<Placement> blocks, BlockPos base, int from, int radius) {
        int top = 0;
        for (int i = from; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i).pos();
            if (Math.abs(pos.getX() - base.getX()) <= radius
                    && Math.abs(pos.getZ() - base.getZ()) <= radius) {
                top = Math.max(top, pos.getY() - base.getY());
            }
        }
        return top;
    }

    // --- a style, bent to what a building is rather than only who built it ----

    /**
     * The same people's palette, wearing the roof a building's <em>function</em>
     * asks for.
     *
     * <p>A library is twenty-three by seventeen. A gable takes its rise from the
     * depth, so the Norman gable that sits so well on a nine-deep cottage rises
     * nine courses over a library and puts a barn roof in the middle of a town —
     * taller than the hall, which is the one building that is supposed to be the
     * tallest thing on the street. So the big-span civic buildings say what shape
     * of roof they want and how far it may rise, and take everything else —
     * planks, stone, stairs, timbering, shutters — from the culture unchanged.
     *
     * <p>Returned as a {@link HouseStyle} rather than handled with a second copy
     * of {@link Parts#dress} on purpose. The order the parts go on in is hard-won
     * and lives in exactly one place; a civic dressing that reimplemented it would
     * be the second copy that drifts.
     *
     * <p>A people who build flat keep building flat. An orc library with a hip on
     * it would be the one orc building in the town that had a roof, which reads as
     * a mistake rather than as a library.
     */
    static HouseStyle roofedFor(HouseStyle style, HouseStyle.Roof want, int cap) {
        HouseStyle.Roof roof = style.roof() == HouseStyle.Roof.FLAT
                ? HouseStyle.Roof.FLAT : want;
        return new HouseStyle(style.wall(), style.frame(), style.roofStairs(),
                style.roofRidge(), style.plinth(), style.gable(), roof,
                style.timber(), style.timberEvery(), cap, style.chimney(),
                style.dormers(), style.porch(), style.post(), style.trapdoor());
    }

    /**
     * The same, for a building whose walls are not what this people's houses are.
     *
     * <p>A library is stone whoever raises it — that is what a room full of books
     * is kept in — but <em>which</em> stone is still the culture's. Swapping the
     * wall block here rather than at the call site matters because half the parts
     * in {@link Parts} find their work by asking what is already standing at a
     * cell: a window row looks for the wall block to punch through, and one told
     * the wrong block punches no windows at all and says nothing about it.
     */
    static HouseStyle walledIn(HouseStyle style, Block wall, HouseStyle.Roof want, int cap) {
        HouseStyle roofed = roofedFor(style, want, cap);
        return new HouseStyle(wall, roofed.frame(), roofed.roofStairs(),
                roofed.roofRidge(), roofed.plinth(), wall, roofed.roof(),
                roofed.timber(), 0, roofed.roofCap(), roofed.chimney(),
                roofed.dormers(), roofed.porch(), roofed.post(), roofed.trapdoor());
    }

    // --- what a hall has that a house does not --------------------------------

    /**
     * A deeper stone base than a house gets, under a wall of something else.
     *
     * <p>{@link Parts#plinth} lays the one course at the foot of a house, and
     * {@link Parts#dress} has already laid it by the time anything here runs — so
     * this takes the courses <em>above</em> that one, and a two-course base asks
     * for exactly {@code 2} to {@code 2}. Written after the dressing so the
     * windows are already cut and survive it: a plinth only ever replaces wall and
     * frame, never a pane.
     */
    static void baseCourses(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                            int fromY, int toY, HouseStyle style) {
        for (int y = fromY; y <= toY; y++) {
            Parts.plinth(blocks, base, size, y, style.plinth(), style.wall(), style.frame());
        }
    }

    /**
     * The same people's style with the porch taken off it.
     *
     * <p>For a building that draws its own porch afterwards. A hall's has to go on
     * after the roof rather than before — see {@link #porchWithBell} — and a
     * dressing that put one on first would have every block of it laid twice: once
     * by the crew building the porch the style asked for and once by the crew
     * building the porch over the top of it, and the town charged for both.
     */
    static HouseStyle porchless(HouseStyle style) {
        return new HouseStyle(style.wall(), style.frame(), style.roofStairs(),
                style.roofRidge(), style.plinth(), style.gable(), style.roof(),
                style.timber(), style.timberEvery(), style.roofCap(), style.chimney(),
                style.dormers(), false, style.post(), style.trapdoor());
    }

    /**
     * A landing of stone in front of the door, out on the doorstep ring.
     *
     * <p>Cheap and worth every block of it: a wide opening with bare ground in
     * front of it reads as a hole in a wall, and the same opening with a paved
     * apron in front reads as an entrance somebody walks up to.
     */
    static void landing(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                        int half, Block stone) {
        int rz = size.depth() / 2;
        for (int dx = -half; dx <= half; dx++) {
            add(blocks, base.offset(dx, 0, rz + 1), stone);
        }
    }

    /**
     * The porch a hall gets whatever its people build, and the bell under it.
     *
     * <p>Drawn <em>after</em> the roof rather than before it, which is the one
     * place this deliberately departs from {@link Parts#dress}. A house's porch
     * goes first so the eave can overhang it and be its roof; a hall's lintel has
     * to be a solid block because a bell hangs from it, and an eave of stairs
     * overhead is not something a bell can hang on. The three blocks of eave it
     * costs are three blocks over the doorstep, not over any floor.
     */
    static void porchWithBell(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                              int wallHeight, HouseStyle style) {
        Parts.porch(blocks, base, size, wallHeight, style.post(), style.roofRidge());
        int rz = size.depth() / 2;
        add(blocks, base.offset(0, wallHeight - 1, rz + 1),
                Blocks.BELL.defaultBlockState()
                        .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING)
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
    }

    /**
     * A banner either side of the door, in the color this people dyes its wool.
     *
     * <p>Hung on the wall rather than stood on the ground: a wall banner needs the
     * block behind it and nothing else, so it survives on any wall this mod
     * builds, where a standing one needs something solid underneath and would pop
     * off a fence the first time anything updated it.
     */
    static void doorBanners(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                            int out, int y, DyeColor color) {
        int rz = size.depth() / 2;
        for (int side = -1; side <= 1; side += 2) {
            add(blocks, base.offset(side * out, y, rz + 1),
                    Blocks.WALL_BANNER.pick(color).defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        }
    }

    /**
     * A pole with the town's colors on it, standing on its own doorstep.
     *
     * <p>Solid all the way up rather than a run of fence, because the banner on
     * top has to have something under it that counts as ground. A fence would look
     * better for exactly as long as it took anything to update that cell.
     */
    static void flagpole(List<Placement> blocks, BlockPos base, int dx, int dz,
                         int height, Block pole, DyeColor color) {
        for (int y = 1; y <= height; y++) {
            add(blocks, base.offset(dx, y, dz), pole);
        }
        add(blocks, base.offset(dx, height + 1, dz), Blocks.BANNER.pick(color));
    }

    /**
     * The little tower on the ridge, and the gold on top of it.
     *
     * <p>The one part of a hall that can be seen over the other roofs, which is
     * the entire job: a town read from a hillside should say where its middle is.
     * Two courses of open belfry on four posts, a cap, and the gold block that has
     * marked a town hall since before any of these buildings had roofs at all —
     * moved up here from the wall course it used to sit on, where a pitched roof
     * now buries it.
     *
     * @param top the highest course the roof reached, from {@link Parts#topOf}
     * @return the course the gold finial stands on
     */
    static int cupola(List<Placement> blocks, BlockPos base, int top, HouseStyle style) {
        for (int y = top + 1; y <= top + 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = dx != 0 && dz != 0;
                    if (corner) {
                        add(blocks, base.offset(dx, y, dz), style.frame());
                    } else if (dx != 0 || dz != 0) {
                        add(blocks, base.offset(dx, y, dz), Blocks.GLASS);
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                add(blocks, base.offset(dx, top + 3, dz), style.roofRidge());
            }
        }
        add(blocks, base.offset(0, top + 4, 0), Blocks.GOLD_BLOCK);
        return top + 4;
    }

    /**
     * Light hung round the inside of a wall, at a spacing.
     *
     * <p>For a room with something over part of it — a gallery, an upper floor —
     * where a grid of lanterns hung from the ceiling would leave the ring under
     * that something dark. A dark ring inside a building is not a matter of taste:
     * it is where things spawn.
     *
     * @param inset how far in from the wall the lanterns hang, normally one
     */
    static void wallLanterns(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                             int inset, int y, int spacing) {
        int rx = size.width() / 2 - inset;
        int rz = size.depth() / 2 - inset;
        for (int dz = -rz; dz <= rz; dz += spacing) {
            for (int side = -1; side <= 1; side += 2) {
                hang(blocks, base.offset(side * rx, y, dz));
            }
        }
        for (int dx = -rx + spacing; dx <= rx - spacing; dx += spacing) {
            for (int side = -1; side <= 1; side += 2) {
                hang(blocks, base.offset(dx, y, side * rz));
            }
        }
    }

    /** Light hung from the ceiling of a big room, on a grid rather than at its middle. */
    static void chandeliers(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                            int y, int spacing) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx + 2; dx <= rx - 2; dx += spacing) {
            for (int dz = -rz + 2; dz <= rz - 2; dz += spacing) {
                hang(blocks, base.offset(dx, y, dz));
            }
        }
    }

    /** One lantern on a chain rather than standing on the floor. */
    static void hang(List<Placement> blocks, BlockPos pos) {
        add(blocks, pos, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    // --- a market, which is a roof per trader rather than one over everybody ---

    /**
     * One trader's pitch: two posts, a striped awning, and a barrel under it.
     *
     * <p>The market used to be a single nine-by-nine lid on eight posts, which is
     * a bus shelter. Six of these round an open square is a market — and it is
     * also ninety blocks cheaper, because the thing a market is mostly made of
     * turns out to be the empty space traders stand in.
     *
     * <p>The awning alternates the culture's own wool with undyed white, and which
     * one it starts with alternates from stall to stall, so no two neighboring
     * pitches read as the same cloth.
     *
     * @param inward which way the awning leans off its posts, as -1 or 1 along z
     * @param flip   the stall's index, so the stripes start on the other color
     */
    static void stall(List<Placement> blocks, BlockPos base, int cx, int cz, int inward,
                      Block post, DyeColor color, int flip) {
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y <= 2; y++) {
                add(blocks, base.offset(cx + side, y, cz), post);
            }
        }
        add(blocks, base.offset(cx, 1, cz), Blocks.BARREL);
        for (int across = -1; across <= 1; across++) {
            for (int out = 0; out <= 1; out++) {
                Block cloth = Math.floorMod(across + out + flip, 2) == 0
                        ? Blocks.WOOL.pick(color) : Blocks.WOOL.white();
                add(blocks, base.offset(cx + across, 3, cz + inward * out), cloth);
            }
        }
    }

    /**
     * A well: a ring of stone with water in it, and a lantern on a post beside.
     *
     * <p>The middle of a market square is where a well goes, and a square with
     * nothing in the middle of it is a yard. Sited one block off the true center
     * because the true center's northern neighbor is the market post, and the post
     * is a cell nothing may take.
     *
     * <p>A filled cauldron rather than a water source, and not for looks. The
     * auditor sweeps every building for fluid standing in its rooms and reports
     * it as a building in a lake — quite rightly, since that is what a flooded
     * house looks like — and a source block in the middle of a market square is
     * indistinguishable from one to that check. A cauldron holds its water as a
     * block state rather than as a fluid, so it reads wet to a player and dry to
     * everything that has an opinion about flooding.
     */
    static void well(List<Placement> blocks, BlockPos base, int cx, int cz,
                     Block rim, Block post) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                add(blocks, base.offset(cx + dx, 1, cz + dz), rim);
            }
        }
        add(blocks, base.offset(cx, 1, cz), Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        add(blocks, base.offset(cx - 1, 2, cz - 1), post);
        add(blocks, base.offset(cx - 1, 3, cz - 1), Blocks.LANTERN);
    }

    // --- an inn, which is a house with a trade and a spare room ---------------

    /**
     * A second floor, and the flight of stairs that reaches it.
     *
     * <p>What actually makes a building read as two storeys from outside is not
     * the deck — nobody can see that — it is that the walls go up twice as far and
     * the windows come in two rows. The deck is here so that the inside is two
     * rooms rather than one tall one, and so the stairs have somewhere to arrive.
     *
     * <p>The flight climbs one course per block along the back wall and the top
     * tread is written over the deck, which is what leaves the hole somebody
     * climbs through. Drawn in that order deliberately.
     */
    static void upperFloor(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                           int y, Block deck, Block stairs) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx + 1; dx <= rx - 1; dx++) {
            for (int dz = -rz + 1; dz <= rz - 1; dz++) {
                add(blocks, base.offset(dx, y, dz), deck);
            }
        }
        for (int step = 0; step < y; step++) {
            add(blocks, base.offset(-rx + 1 + step, step + 1, -rz + 1),
                    stair(stairs, Direction.EAST));
        }
    }

    /** A sign over the door, which is the whole of how an inn says it is one. */
    static void innSign(List<Placement> blocks, BlockPos base, BuildingSizes.Size size, int y) {
        int rz = size.depth() / 2;
        add(blocks, base.offset(0, y, rz + 1),
                Blocks.OAK_WALL_SIGN.defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
    }

    /**
     * A stable lean-to down one flank: posts, a single slope, and fodder.
     *
     * <p>Stands in the doorstep ring against the gable end, where the main roof's
     * own eave never reaches — a gable overhangs its long walls and not its short
     * ones, so this is the one flank of a pitched building with room for a second
     * roof against it.
     *
     * @param side which flank, as -1 or 1 along x
     */
    static void stable(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                       int side, HouseStyle style) {
        int rx = size.width() / 2;
        int out = side * (rx + 1);
        for (int dz = -2; dz <= 2; dz += 4) {
            for (int y = 1; y <= 2; y++) {
                add(blocks, base.offset(out, y, dz), style.post());
            }
        }
        for (int dz = -2; dz <= 2; dz++) {
            add(blocks, base.offset(out, 3, dz),
                    stair(style.roofStairs(), side > 0 ? Direction.WEST : Direction.EAST));
        }
        add(blocks, base.offset(out, 1, 0), Blocks.HAY_BLOCK);
    }

    /** A table: a post with a cloth over it, which is every table in Minecraft. */
    static void table(List<Placement> blocks, BlockPos base, int dx, int dz,
                      Block post, DyeColor color) {
        table(blocks, base, dx, 1, dz, post, color);
    }

    /**
     * The same, on a floor that is not the ground one.
     *
     * <p>A building with three storeys wants furniture on all three of them, and
     * a part that only knows how to stand on course one is a part that can only
     * furnish the bottom of it.
     *
     * @param floorY the course the table's own foot stands on
     */
    static void table(List<Placement> blocks, BlockPos base, int dx, int floorY, int dz,
                      Block post, DyeColor color) {
        add(blocks, base.offset(dx, floorY, dz), post);
        add(blocks, base.offset(dx, floorY + 1, dz), Blocks.CARPET.pick(color));
    }

    /**
     * A bar: a slab counter with a row of barrels standing behind it.
     *
     * <p>Given a stretch of wall rather than a width about the middle, so it can
     * be put down the half of the back wall the stair is not climbing and so the
     * walk from the door to the post does not end at a wall of oak.
     */
    static void bar(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                    int fromDx, int toDx, Block slab) {
        int rz = size.depth() / 2;
        for (int dx = fromDx; dx <= toDx; dx++) {
            add(blocks, base.offset(dx, 1, -rz + 2), slab);
            add(blocks, base.offset(dx, 1, -rz + 1), Blocks.BARREL);
        }
    }

    /** A lantern on a post either side of a doorway, lit for whoever is still on the road. */
    static void doorLanterns(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                             int out, Block post) {
        int rz = size.depth() / 2;
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y <= 2; y++) {
                add(blocks, base.offset(side * out, y, rz + 1), post);
            }
            add(blocks, base.offset(side * out, 3, rz + 1), Blocks.LANTERN);
        }
    }

    // --- a library, which is the one building made of its own contents --------

    /**
     * Tall windows with an arch over them, cut into a wall already standing.
     *
     * <p>Two courses of pane and a stair for the head. The stair is laid top-half
     * so the solid part of it spans the full width of the wall and only the
     * springing is cut away: an arch that reads as an arch from the street without
     * leaving a hole anything could path through or see daylight along.
     *
     * <p>Written over whatever the dressing left, which is why it runs last. A
     * window row and a course of timbering both want the same cells and both are
     * harmless underneath this.
     */
    static void archedWindows(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                              int sill, Block head) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx : spaced(rx)) {
            for (int side = -1; side <= 1; side += 2) {
                arch(blocks, base, dx, side * rz, sill, head,
                        side > 0 ? Direction.SOUTH : Direction.NORTH);
            }
        }
        for (int dz : spaced(rz)) {
            for (int side = -1; side <= 1; side += 2) {
                arch(blocks, base, side * rx, dz, sill, head,
                        side > 0 ? Direction.EAST : Direction.WEST);
            }
        }
    }

    private static void arch(List<Placement> blocks, BlockPos base, int dx, int dz,
                             int sill, Block head, Direction out) {
        add(blocks, base.offset(dx, sill, dz), Blocks.GLASS_PANE);
        add(blocks, base.offset(dx, sill + 1, dz), Blocks.GLASS_PANE);
        add(blocks, base.offset(dx, sill + 2, dz),
                head.defaultBlockState()
                        .setValue(StairBlock.FACING, out)
                        .setValue(StairBlock.HALF, Half.TOP)
                        .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
    }

    /**
     * Where the windows go along a wall: every third block, clear of the corners
     * and clear of the center line, which is where a door is.
     */
    private static int[] spaced(int radius) {
        List<Integer> along = new ArrayList<>();
        for (int at = 2; at <= radius - 3; at += 3) {
            along.add(at);
            along.add(-at);
        }
        int[] columns = new int[along.size()];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = along.get(i);
        }
        return columns;
    }

    /**
     * Shelves two courses high all the way round the inside of the walls.
     *
     * <p>Round the walls rather than in ranks down the middle, because the middle
     * of this building is now open to the gallery above it — and because a wall of
     * books behind every window is what a reading room looks like, where ranks in
     * the middle of the floor are what a stack room looks like.
     */
    static void shelves(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                        Collection<BlockPos> skip) {
        shelves(blocks, base, size, 2, skip);
    }

    /**
     * The same, as high as a room wants them.
     *
     * <p>Two courses is what a reading room with a gallery over it has room for.
     * Three is a stack that reaches over your head, and it is the difference
     * between a room with books in it and a room made of books — which is worth
     * a parameter, because it is most of what tells a grand library from an
     * ordinary one from inside.
     *
     * @param toY the last course shelved, counting the floor as nought
     */
    static void shelves(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                        int toY, Collection<BlockPos> skip) {
        int rx = size.width() / 2 - 1;
        int rz = size.depth() / 2 - 1;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (Math.abs(dx) != rx && Math.abs(dz) != rz) {
                    continue;
                }
                for (int y = 1; y <= toY; y++) {
                    BlockPos pos = base.offset(dx, y, dz);
                    if (!skip.contains(pos)) {
                        add(blocks, pos, Blocks.BOOKSHELF);
                    }
                }
            }
        }
    }

    /**
     * A gallery: a walkway two blocks wide round an open middle, with a rail.
     *
     * <p>What a second storey is worth in a room this size. A full upper floor
     * would make the library two rooms of ordinary height; a ring round a void
     * makes it one room you can see the whole of from either level, which is the
     * only reason to build something twenty-three blocks across in the first
     * place.
     *
     * <p>The rail is placed by asking which gallery cells have open air beside
     * them, so it follows the hole rather than being drawn again from the same
     * arithmetic that made it.
     */
    static void gallery(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                        int y, int width, Block deck, Block rail, Block stairs) {
        int rx = size.width() / 2 - 1;
        int rz = size.depth() / 2 - 1;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (onGallery(rx, rz, width, dx, dz)) {
                    add(blocks, base.offset(dx, y, dz), deck);
                }
            }
        }
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                if (!onGallery(rx, rz, width, dx, dz) || !besideTheVoid(rx, rz, width, dx, dz)) {
                    continue;
                }
                add(blocks, base.offset(dx, y + 1, dz), rail);
            }
        }
        // The flight runs along the outer row of the walkway, where the rail —
        // which follows the void and therefore hugs the inner row — is not.
        for (int step = 0; step < y; step++) {
            add(blocks, base.offset(-rx + step, step + 1, -rz), stair(stairs, Direction.EAST));
        }
    }

    private static boolean onGallery(int rx, int rz, int width, int dx, int dz) {
        return Math.abs(dx) > rx - width || Math.abs(dz) > rz - width;
    }

    private static boolean besideTheVoid(int rx, int rz, int width, int dx, int dz) {
        for (Direction way : Direction.Plane.HORIZONTAL) {
            if (!onGallery(rx, rz, width, dx + way.getStepX(), dz + way.getStepZ())) {
                return true;
            }
        }
        return false;
    }

    /** A reading desk and the pair of lecterns that flank it. */
    static void readingTable(List<Placement> blocks, BlockPos base, int dx, int dz, Block slab) {
        for (int along = -1; along <= 1; along++) {
            add(blocks, base.offset(dx + along, 1, dz), slab);
        }
        add(blocks, base.offset(dx - 2, 1, dz), Blocks.LECTERN);
        add(blocks, base.offset(dx + 2, 1, dz), Blocks.LECTERN);
    }

    // --- a grand library, which is the one building with three floors ----------

    /*
     * On the five parts below.
     *
     * A library is one room with a walkway round it, and CivicParts#gallery is
     * exactly that: a ring two blocks wide inside the walls with a hole in the
     * middle. It does not scale. On a footprint thirty-one by twenty-five the
     * same ring leaves a hole twenty-five by nineteen — which is to say the whole
     * building is the hole, and the "gallery" is a shelf round the inside of a
     * barn.
     *
     * So the grand library is planned the other way round. The middle is a hall
     * of a stated size, open from the floor to the ceiling; everything either
     * side of it is floored solid on all three levels, and what reads as a
     * gallery is the edge of those floors where they look into the hall. That is
     * a floor with a void in it rather than a ring with a hole in it, and the
     * difference is whether the upper storeys are rooms or ledges.
     */

    /**
     * A floor over the wings of a building, open over the hall in the middle.
     *
     * <p>Railed where it looks into the hall, which is found by asking which
     * decked cells have the void beside them rather than by drawing the same
     * rectangle twice. That is the rule {@link #gallery} already uses and it
     * matters more here: the void is not a centered box in the general case and a
     * second piece of arithmetic saying where its edge is would be the copy that
     * drifts.
     *
     * @param hallRx  how far the open middle reaches either side of center
     * @param hallRz  and how far fore and aft
     * @param skip    cells the floor must leave open anyway — the stairwells,
     *                without which a climber cracks their head on the floor they
     *                are climbing to
     */
    static void wingFloors(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                           int y, int hallRx, int hallRz, Block deck, Block rail,
                           Collection<BlockPos> skip) {
        int rx = size.width() / 2 - 1;
        int rz = size.depth() / 2 - 1;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                BlockPos pos = base.offset(dx, y, dz);
                if (overTheHall(hallRx, hallRz, dx, dz) || skip.contains(pos)) {
                    continue;
                }
                add(blocks, pos, deck);
            }
        }
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                BlockPos pos = base.offset(dx, y, dz);
                if (overTheHall(hallRx, hallRz, dx, dz) || skip.contains(pos)
                        || !overlooksTheHall(hallRx, hallRz, dx, dz)) {
                    continue;
                }
                add(blocks, base.offset(dx, y + 1, dz), rail);
            }
        }
    }

    private static boolean overTheHall(int hallRx, int hallRz, int dx, int dz) {
        return Math.abs(dx) <= hallRx && Math.abs(dz) <= hallRz;
    }

    private static boolean overlooksTheHall(int hallRx, int hallRz, int dx, int dz) {
        for (Direction way : Direction.Plane.HORIZONTAL) {
            if (overTheHall(hallRx, hallRz, dx + way.getStepX(), dz + way.getStepZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A flight of stairs climbing one course per block along a row.
     *
     * <p>The last tread is written at the course of the floor it arrives at, so
     * it replaces the deck there and leaves the hole somebody climbs through —
     * the same trick {@link #upperFloor} and {@link #gallery} both turn, in the
     * one place all three of them can share it.
     *
     * @param toward which way the climb runs, as -1 or 1 along x
     */
    static void flight(List<Placement> blocks, BlockPos base, int fromDx, int dz,
                       int fromY, int courses, int toward, Block stairs) {
        Direction way = toward > 0 ? Direction.EAST : Direction.WEST;
        for (int step = 0; step < courses; step++) {
            add(blocks, base.offset(fromDx + toward * step, fromY + step, dz),
                    stair(stairs, way));
        }
    }

    /**
     * A portico: a rank of pillars out on the doorstep under the building's own
     * eave, on a paved landing, with the entrance stepped between them.
     *
     * <p>Stood in the apron ring rather than inside the walls, which is what
     * makes it a portico rather than an aisle — and which is also the whole of
     * the constraint on it. A doorstep is one block wide (see
     * {@link BuildingSizes#APRON}), so the pillars stand in the only row there
     * is, and the eave the building already throws over that row at the wall
     * head is the roof over them. Nothing here reaches further out than the eave
     * does.
     *
     * <p><strong>The landing is flush, and that is deliberate.</strong> A raised
     * entrance would move the building's floor course up by one, and the block a
     * player clicks sits at a fixed offset on that course — the standing note in
     * {@code GOALS.md} about the granary and the hall's floor is the same
     * question and has the same answer. So the grand approach is made of what a
     * flush entrance can carry: the whole frontage paved, stepped shoulders
     * either side of a three-wide opening, and the pillars standing on the
     * paving. On a slope the foundation pass builds the doorstep up to meet it,
     * which is where the flight of steps actually appears.
     *
     * @param columns  where the pillars stand along x, clear of the entrance
     * @param toY      the last course of pillar; the architrave goes one above
     */
    static void portico(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                        int[] columns, int toY, HouseStyle style) {
        int rx = size.width() / 2;
        int out = size.depth() / 2 + 1;
        for (int dx = -rx + 1; dx <= rx - 1; dx++) {
            add(blocks, base.offset(dx, 0, out), style.plinth());
            add(blocks, base.offset(dx, toY + 1, out), style.roofRidge());
        }
        for (int dx : columns) {
            for (int y = 1; y <= toY; y++) {
                add(blocks, base.offset(dx, y, out), style.frame());
            }
        }
        // The shoulders of the entrance stair, hard against the three-wide way
        // in. Two blocks of stone laid as steps is not much; it is the difference
        // between a doorway and a way up to one.
        for (int side = -1; side <= 1; side += 2) {
            for (int along = 2; along <= 3; along++) {
                add(blocks, base.offset(side * along, 1, out),
                        stair(style.roofStairs(), side > 0 ? Direction.WEST : Direction.EAST));
            }
        }
    }

    /**
     * A lantern on the roof: a glazed drum, a stepped cap, and a finial.
     *
     * <p>{@link #cupola} is this at three blocks across with gold on top, and
     * the gold is the point of the difference. That block has marked the middle
     * of a town since before any of these buildings had roofs, and a second
     * building wearing it would be a second thing a player picks a town's center
     * out by from a hillside — so this one is told what to wear.
     *
     * <p>Standing on a closed roof rather than open over the hall below it, for
     * the reason every roofed building here is closed: a glazed oculus is a hole
     * to everything that asks whether a building keeps the weather out, and the
     * check that asks is right to say so.
     *
     * @param top    the highest course over the middle, from {@link #topOver}
     * @param half   how far the drum reaches either side of center
     * @param courses how many courses of glazing stand before the cap
     * @param finial what goes on the very top, and not gold. Whatever it is must
     *               be a block that does not change on its own: a drawing is
     *               compared against what is standing whenever a repair is
     *               weighed, so a weathering block up here is one the crew
     *               replaces for as long as the building stands.
     * @return the course the finial stands on
     */
    static int lantern(List<Placement> blocks, BlockPos base, int top, int half,
                       int courses, HouseStyle style, Block finial) {
        for (int y = top + 1; y <= top + courses; y++) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean acrossX = Math.abs(dx) == half;
                    boolean acrossZ = Math.abs(dz) == half;
                    if (!acrossX && !acrossZ) {
                        continue;   // the inside of the drum, which is the light
                    }
                    add(blocks, base.offset(dx, y, dz),
                            acrossX && acrossZ ? style.frame() : Blocks.GLASS);
                }
            }
        }
        int y = top + courses;
        for (int reach = half; reach >= 1; reach--) {
            y++;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    add(blocks, base.offset(dx, y, dz), style.roofRidge());
                }
            }
        }
        add(blocks, base.offset(0, y + 1, 0), finial);
        return y + 1;
    }

    /**
     * A lantern on a chain hung from a ceiling, rather than one stuck to it.
     *
     * <p>{@link #hang} is a lantern against the block above it, which is all a
     * room seven courses tall has space for. A hall eleven courses tall lit that
     * way is a hall lit from the ceiling, which is to say not lit at all where
     * anybody is standing — so the light comes down on a chain to where it does
     * some good.
     *
     * @param fromY the ceiling course the chain hangs from
     * @param toY   the course the lantern itself hangs at
     */
    static void chandelier(List<Placement> blocks, BlockPos base, int dx, int dz,
                           int fromY, int toY) {
        for (int y = fromY; y > toY; y--) {
            add(blocks, base.offset(dx, y, dz), Blocks.IRON_CHAIN);
        }
        hang(blocks, base.offset(dx, toY, dz));
    }

    // --- a watchtower, which is the one building that is all height ------------

    /**
     * The shaft of a tower: a hollow three-by-three with slits and a door.
     *
     * <p>Slits rather than windows. A tower with glass in it is a lighthouse; the
     * point of an arrow loop is that it is a gap you can see out of and nothing
     * can get in through, and the cheapest way to draw one is to leave the block
     * out. That also means the tower is the one building here with no glass in it
     * at all, which is a difference a player reads at a glance.
     *
     * @param toY the last course of wall, below the deck
     * @return the course the deck goes on
     */
    static int shaft(List<Placement> blocks, BlockPos base, int fromY, int toY, Block stone) {
        for (int y = fromY; y <= toY; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;   // the room, such as it is
                    }
                    if (dz == 1 && dx == 0 && y <= fromY + 1) {
                        continue;   // the door
                    }
                    if (isSlit(y, fromY, dx, dz)) {
                        continue;
                    }
                    add(blocks, base.offset(dx, y, dz), stone);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                add(blocks, base.offset(dx, toY + 1, dz), stone);
            }
        }
        return toY + 1;
    }

    /**
     * Whether this cell of the shell is left out as an arrow loop.
     *
     * <p>Every fourth course, on the three faces that have no ladder on them. The
     * east face is kept whole because the climb is bolted to the outside of it,
     * and a rung with no wall behind it is a rung that falls off.
     */
    private static boolean isSlit(int y, int fromY, int dx, int dz) {
        if (Math.floorMod(y - fromY, 4) != 3) {
            return false;
        }
        return (dx == 0 && dz != 0) || (dx == -1 && dz == 0);
    }

    /** A ladder up the outside of a wall, which is how a watch gets to the top. */
    static void ladder(List<Placement> blocks, BlockPos base, int dx, int dz,
                       Direction out, int fromY, int toY) {
        for (int y = fromY; y <= toY; y++) {
            add(blocks, base.offset(dx, y, dz), Blocks.LADDER.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, out));
        }
    }

    /**
     * The top of a tower: merlons round the edge, a fire in the middle, the bell
     * beside it.
     *
     * <p>The bell stays in the column directly over the tower's own origin because
     * that is where {@code PersonEntityManager.findBell} looks for it, and a bell
     * the watch cannot find is a town that cannot raise the alarm.
     */
    static void crown(List<Placement> blocks, BlockPos base, int deck, Block stone) {
        // Three by three and one course: an ad-hoc shape for the battlement
        // arithmetic to work in, not a building. The height a Size carries is a
        // declaration about a kind in the table, and a crown has no kind, so this
        // one says the least it is allowed to say.
        Parts.battlements(blocks, base, new BuildingSizes.Size(3, 3, 1), deck + 1, stone);
        add(blocks, base.offset(0, deck + 1, 0), Blocks.BELL.defaultBlockState()
                .setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        add(blocks, base.offset(0, deck + 1, 1), Blocks.CAMPFIRE);
    }

    // --- a hearth and the two things a founding camp puts up ------------------

    /**
     * Four posts at the corners of a footprint, for a roof with no walls under it.
     *
     * <p>A hearth is a fire people stand round, so it cannot have walls; a fire
     * with nothing over it is a fire that goes out in the rain. Four posts and a
     * hip is the oldest answer there is and it is still the right one.
     */
    static void canopy(List<Placement> blocks, BlockPos base, BuildingSizes.Size size,
                       int toY, Block post) {
        int rx = size.width() / 2;
        int rz = size.depth() / 2;
        for (int dx = -rx; dx <= rx; dx += 2 * rx) {
            for (int dz = -rz; dz <= rz; dz += 2 * rz) {
                for (int y = 1; y <= toY; y++) {
                    add(blocks, base.offset(dx, y, dz), post);
                }
            }
        }
    }

    /** A tarpaulin on posts: the cheapest roof there is, over a camp's stores. */
    static void tarpaulin(List<Placement> blocks, BlockPos base, int y, Block post,
                          DyeColor color) {
        for (int dx = -1; dx <= 1; dx += 2) {
            add(blocks, base.offset(dx, y - 1, 1), post);
            add(blocks, base.offset(dx, y - 2, 1), post);
            add(blocks, base.offset(dx, y - 1, -1), post);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                add(blocks, base.offset(dx, y, dz), Blocks.WOOL.pick(color));
            }
        }
    }

    /** The colors on a short pole: what a founding party plants before it builds. */
    static void campBanner(List<Placement> blocks, BlockPos base, int dx, int dz,
                           Block pole, DyeColor color) {
        add(blocks, base.offset(dx, 1, dz), pole);
        add(blocks, base.offset(dx, 2, dz), pole);
        add(blocks, base.offset(dx, 3, dz), Blocks.BANNER.pick(color));
    }

    // --- odds and ends --------------------------------------------------------

    /**
     * The slab that goes with a wall.
     *
     * <p>Small and explicit rather than clever. A counter and a reading desk want
     * a half block of whatever the building is made of, and there is no way to ask
     * a block what its slab is.
     */
    static Block slabOf(Block wall) {
        if (wall == Blocks.SPRUCE_PLANKS) {
            return Blocks.SPRUCE_SLAB;
        }
        if (wall == Blocks.DARK_OAK_PLANKS) {
            return Blocks.DARK_OAK_SLAB;
        }
        if (wall == Blocks.BRICKS) {
            return Blocks.BRICK_SLAB;
        }
        if (wall == Blocks.STONE_BRICKS) {
            return Blocks.STONE_BRICK_SLAB;
        }
        if (wall == Blocks.MUD_BRICKS) {
            return Blocks.MUD_BRICK_SLAB;
        }
        if (wall == Blocks.COBBLESTONE) {
            return Blocks.COBBLESTONE_SLAB;
        }
        if (wall == Blocks.STONE) {
            return Blocks.STONE_SLAB;
        }
        return Blocks.OAK_SLAB;
    }

    /** A plain stair, tall half on the given side, sitting on the floor of its cell. */
    private static BlockState stair(Block stairs, Direction facing) {
        return stairs.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    private static void add(List<Placement> blocks, BlockPos pos, Block block) {
        blocks.add(new Placement(pos, block.defaultBlockState(), null));
    }

    private static void add(List<Placement> blocks, BlockPos pos, BlockState state) {
        blocks.add(new Placement(pos, state, null));
    }
}
