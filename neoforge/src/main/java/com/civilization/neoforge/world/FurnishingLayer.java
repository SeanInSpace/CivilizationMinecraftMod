package com.civilization.neoforge.world;

import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.world.BlueprintPlacer.Placement;
import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.work.FurnishingStyle;
import com.civilization.sim.work.Furnishings;
import com.civilization.sim.work.Signage;
import com.civilization.sim.world.SimWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Puts a town's dressing in the ground.
 *
 * <p>{@code Furnishings} decides where the yards, hedges, woodpiles and the
 * square go and {@code FurnishingStyle} decides whose they are; this is the one
 * place either of those becomes blocks. Deliberately the same shape as
 * {@link LightLayer}, down to the names: a {@link Course} is one block of one
 * piece, {@link #plan} says what a piece is made of without needing a world,
 * {@link #owed} reads the ground to find the course that is missing, and
 * {@link #draw} is the clock's sweep over the prefix the town has raised.
 *
 * <p><strong>Every course has to survive being placed, and go on surviving.</strong>
 * That is the lesson the wall paid for with a torch on a fence post, and dressing
 * has two more ways to lose it than a lamp does. Leaves placed without
 * {@link LeavesBlock#PERSISTENT} decay within a minute of being laid, so a hedge
 * would be re-planted by every sweep for ever and a town would spend its whole
 * drawing budget on the same twenty blocks. And a sapling that takes is no longer
 * a sapling: it is a trunk and a crown, and a plan that insisted on the sapling
 * would cut its own orchard down to re-plant it. So {@link #stands} accepts a
 * grown tree where a sapling was planned, and the leaves are persistent.
 *
 * <p><strong>The plan is level-free.</strong> Every drawing method here takes a
 * {@link BlueprintPlacer.Site} — the same three-question seam a blueprint is drawn
 * against — rather than a {@code ServerLevel}, so what each people's yard is made
 * of can be checked on every culture without a running game. See
 * {@code FurnishingLayerPlanTest}, which is {@code BlueprintPlacerSizeTest}'s
 * argument applied to the ground between the buildings.
 */
public final class FurnishingLayer {

    private FurnishingLayer() {
    }

    /**
     * Pieces the clock stamps in per second: four.
     *
     * <p>A third of {@link LightLayer}'s rate, because a piece is thirty blocks
     * where a lamp is three. A grown town's whole dressing therefore goes in over
     * about a minute of being walked into, which is the same promise the lighting
     * makes and for the same reason: "the town tidied itself while you were away"
     * has to look like something that happened before you got there.
     */
    static final int PIECES_PER_SECOND = 4;

    /** How many of the raised prefix one sweep will look at. */
    private static final int SCAN = 24;

    /** How far behind the sweeps may fall before the catch-up is capped. */
    private static final int CATCH_UP_SECONDS = 10;

    /**
     * One block of one piece.
     *
     * @param soil  whether this course replaces the ground rather than standing on
     *              it. Paving and tilled earth do; a fence post does not. The
     *              distinction has to be here rather than in {@link #put} because
     *              the two want opposite answers to the same question: a fence may
     *              never be driven through a wall somebody built, and a paving slab
     *              has to be able to replace the grass it is laid over.
     * @param lines what is written on this course, empty for the forty-nine blocks
     *              out of fifty that are not a board. This is the whole of the
     *              widening the class comment used to refuse: a course already
     *              carries a position and a state, so the text that goes with a
     *              sign block travels beside the sign block rather than in a
     *              second structure that could fall out of step with it. See
     *              {@link #inscribe}, which is the only reader.
     */
    public record Course(BlockPos pos, BlockState state, boolean soil,
                         List<String> lines) {

        public Course {
            lines = List.copyOf(lines);
        }

        public Course(BlockPos pos, BlockState state) {
            this(pos, state, false, List.of());
        }

        public Course(BlockPos pos, BlockState state, boolean soil) {
            this(pos, state, soil, List.of());
        }
    }

    // --- what a people's dressing is made of ---------------------------------

    /**
     * The blocks one people dress their ground with.
     *
     * <p>{@link HouseStyle}'s vocabulary, narrowed to the eleven things anything
     * out here is made of. Read off the same culture, so a burgher's hedge stands
     * between brick houses on stone paving and a goblin's woodpile is the dark oak
     * his hut is: the one rule {@code CivicParts} states about civic buildings —
     * never a new palette, only new furniture — said again about the ground.
     */
    record Palette(Block fence, Block gate, Block post, Block paving, Block pavingStairs,
                   Block hedge, Block log, Block crop, Block flower, Block sapling,
                   Block rim, Block sign, Block headstone, Block board) {
    }

    /** What this people build their dressing out of. */
    static Palette paletteOf(FurnishingStyle style) {
        return switch (style) {
            // Norman: oak, cobble, and a carrot patch.
            case NORMAN -> new Palette(Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE,
                    Blocks.OAK_LOG, Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS,
                    Blocks.OAK_LEAVES, Blocks.OAK_LOG, Blocks.CARROTS, Blocks.POPPY,
                    Blocks.OAK_SAPLING, Blocks.COBBLESTONE, Blocks.OAK_WALL_SIGN,
                    Blocks.MOSSY_COBBLESTONE_WALL, Blocks.OAK_SIGN);
            // Highland: spruce on stone, and potatoes, which is what grows up there.
            case HIGHLAND -> new Palette(Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE,
                    Blocks.STRIPPED_SPRUCE_LOG, Blocks.STONE, Blocks.STONE_STAIRS,
                    Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_LOG, Blocks.POTATOES,
                    Blocks.OXEYE_DAISY, Blocks.SPRUCE_SAPLING, Blocks.STONE,
                    Blocks.SPRUCE_WALL_SIGN, Blocks.ANDESITE_WALL, Blocks.SPRUCE_SIGN);
            // Burgher: everything masoned, and beetroot, which is a town crop.
            case BURGHER -> new Palette(Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE_GATE,
                    Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS,
                    Blocks.OAK_LEAVES, Blocks.DARK_OAK_LOG, Blocks.BEETROOTS,
                    Blocks.ALLIUM, Blocks.OAK_SAPLING, Blocks.STONE_BRICKS,
                    Blocks.DARK_OAK_WALL_SIGN, Blocks.STONE_BRICK_WALL,
                    Blocks.DARK_OAK_SIGN);
            // Vale: pale oak, wheat, and cornflowers on the green.
            case VALE -> new Palette(Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE,
                    Blocks.STRIPPED_OAK_LOG, Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS,
                    Blocks.OAK_LEAVES, Blocks.OAK_LOG, Blocks.WHEAT, Blocks.CORNFLOWER,
                    Blocks.OAK_SAPLING, Blocks.MOSSY_COBBLESTONE, Blocks.OAK_WALL_SIGN,
                    Blocks.MOSSY_COBBLESTONE_WALL, Blocks.OAK_SIGN);
            // Warhost: stone and spruce, and nothing that grows.
            case WARHOST -> new Palette(Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE,
                    Blocks.STONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS,
                    Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_LOG, Blocks.WHEAT,
                    Blocks.DANDELION, Blocks.SPRUCE_SAPLING, Blocks.COBBLESTONE,
                    Blocks.SPRUCE_WALL_SIGN, Blocks.COBBLESTONE_WALL, Blocks.SPRUCE_SIGN);
            // Mire: dark oak and mud, which is what a goblin has.
            case MIRE -> new Palette(Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE_GATE,
                    Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS,
                    Blocks.DARK_OAK_LEAVES, Blocks.DARK_OAK_LOG, Blocks.WHEAT,
                    Blocks.BROWN_MUSHROOM, Blocks.DARK_OAK_SAPLING, Blocks.MUD_BRICKS,
                    Blocks.DARK_OAK_WALL_SIGN, Blocks.MUD_BRICK_WALL, Blocks.DARK_OAK_SIGN);
        };
    }

    // --- the plan ------------------------------------------------------------

    /**
     * What one piece is made of, in the order it goes up.
     *
     * <p>Pure, and reads the ground only through the site's {@code groundLevel},
     * which is what lets a yard on a hillside step with the hill instead of
     * floating over it: every column is placed against its own surface rather than
     * against the middle of the piece.
     */
    public static List<Course> plan(BlueprintPlacer.Site site,
                                    Furnishings.Furnishing piece) {
        return plan(site, piece, Signage.Plaque.NONE);
    }

    /**
     * The same, with the town's own words for whatever board this piece carries.
     *
     * <p>The plaque is handed in rather than read out of a settlement here,
     * because the whole argument of the method above is that a piece can be
     * checked on every culture without a running game — and a settlement is not
     * a world but it is one more thing a size test would have to build. So
     * {@link Signage} reads the town once, this turns three strings into four
     * lines on a board, and the arithmetic of the two halves stays apart.
     */
    public static List<Course> plan(BlueprintPlacer.Site site,
                                    Furnishings.Furnishing piece,
                                    Signage.Plaque plaque) {
        return plan(site, piece, plaque, null);
    }

    /**
     * The same, for a headstone, which is the one board whose words are not a
     * fact about the town.
     *
     * <p>A notice board says the town's name and a signpost says which way the
     * hall is; both come out of the one {@link Signage.Plaque} read off the
     * settlement. A grave says a person's name, and which name depends on which
     * stone — so whose grave this is travels beside the plaque rather than in it,
     * and is null for every piece that is not one. {@link #graveAt} is what works
     * it out, and it works it out from the two lists being in the same order
     * rather than from anything written down.
     */
    public static List<Course> plan(BlueprintPlacer.Site site,
                                    Furnishings.Furnishing piece,
                                    Signage.Plaque plaque,
                                    Settlement.Grave whose) {
        FurnishingStyle style = FurnishingStyle.of(site.culture());
        Palette palette = paletteOf(style);
        List<String> lines = Signage.linesFor(piece.piece(), plaque, piece.facing(), whose);
        List<Course> courses = new ArrayList<>();
        switch (piece.piece()) {
            case YARD -> yard(site, courses, piece, palette);
            case WOODPILE -> woodpile(site, courses, piece, palette);
            case HAYSTACK -> haystack(site, courses, piece);
            case CRATES -> crates(site, courses, piece, palette);
            case WELL -> well(site, courses, piece, palette);
            case SQUARE -> square(site, courses, piece, palette, lines);
            case ORCHARD -> orchard(site, courses, piece, palette);
            case HEDGE -> hedge(site, courses, piece, palette);
            case AVENUE_TREE -> avenueTree(site, courses, piece, palette);
            case SIGNPOST -> signpost(site, courses, piece, palette, lines, true);
            case INN_SIGN -> signpost(site, courses, piece, palette, lines, false);
            case FIRE_PIT -> firePit(site, courses, piece, palette);
            case CAGE -> cage(site, courses, piece, palette);
            case STAKES -> stakes(site, courses, piece, palette);
            case GRAVE -> grave(site, courses, piece, palette, lines);
        }
        return List.copyOf(courses);
    }

    /**
     * A fenced kitchen garden: a rail of fence with a gate in it, rows of the
     * people's own crop, and a bed of flowers along the back.
     *
     * <p>Five across and seven deep, with the gate in the middle of the side the
     * house is on — which is what {@code Furnishings.Furnishing.facing} carries,
     * and the reason it carries the direction back to the building rather than the
     * building's own facing. A garden you have to climb into is a garden nobody
     * planted.
     *
     * <p>The crop is laid over tilled earth in the same order the farm's own field
     * is, soil first, because a crop written before the ground under it pops
     * straight off — which {@code BlueprintPlacer} logs as {@code CROPLAY} and had
     * to learn once already.
     */
    private static void yard(BlueprintPlacer.Site site, List<Course> out,
                             Furnishings.Furnishing piece, Palette palette) {
        int rx = 2;
        int rz = 3;
        SimPos at = piece.at();
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                int[] world = turn(dx, dz, piece.facing());
                int x = at.x() + world[0];
                int z = at.z() + world[1];
                int ground = site.groundLevel(x, z);
                boolean rail = Math.abs(dx) == rx || Math.abs(dz) == rz;
                if (rail) {
                    boolean gate = dz == rz && dx == 0;
                    out.add(new Course(new BlockPos(x, ground, z), gate
                            ? palette.gate().defaultBlockState()
                                    .setValue(FenceGateBlock.OPEN, true)
                                    .setValue(HorizontalDirectionalBlock.FACING,
                                            towardTheGate(piece.facing()))
                            : palette.fence().defaultBlockState()));
                    continue;
                }
                if (dz == -rz + 1) {
                    // The flower bed, along the back rail where nobody walks.
                    out.add(new Course(new BlockPos(x, ground, z),
                            palette.flower().defaultBlockState()));
                    continue;
                }
                out.add(new Course(new BlockPos(x, ground - 1, z),
                        Blocks.FARMLAND.defaultBlockState(), true));
                out.add(new Course(new BlockPos(x, ground, z),
                        palette.crop().defaultBlockState()));
            }
        }
    }

    /** Split logs stacked two courses high, three long and two deep. */
    private static void woodpile(BlueprintPlacer.Site site, List<Course> out,
                                 Furnishings.Furnishing piece, Palette palette) {
        SimPos at = piece.at();
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int[] world = turn(dx, dz, piece.facing());
                int x = at.x() + world[0];
                int z = at.z() + world[1];
                int ground = site.groundLevel(x, z);
                // Laid on their sides along the pile, which is how anybody who has
                // ever stacked firewood stacks it.
                BlockState log = palette.log().defaultBlockState().setValue(
                        RotatedPillarBlock.AXIS,
                        acrossX(piece.facing()) ? Direction.Axis.X : Direction.Axis.Z);
                out.add(new Course(new BlockPos(x, ground, z), log));
                if (dx == -1) {
                    out.add(new Course(new BlockPos(x, ground + 1, z), log));
                }
            }
        }
    }

    /** A rick: four bales and one on top, which is what a farm does with its straw. */
    private static void haystack(BlueprintPlacer.Site site, List<Course> out,
                                 Furnishings.Furnishing piece) {
        SimPos at = piece.at();
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int x = at.x() + dx;
                int z = at.z() + dz;
                out.add(new Course(new BlockPos(x, site.groundLevel(x, z), z),
                        Blocks.HAY_BLOCK.defaultBlockState()));
            }
        }
        out.add(new Course(new BlockPos(at.x(), site.groundLevel(at.x(), at.z()) + 1,
                at.z()), Blocks.HAY_BLOCK.defaultBlockState()));
    }

    /** Barrels and crates stood outside a store, which is what a store overflows with. */
    private static void crates(BlueprintPlacer.Site site, List<Course> out,
                               Furnishings.Furnishing piece, Palette palette) {
        SimPos at = piece.at();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int[] world = turn(dx, dz, piece.facing());
                int x = at.x() + world[0];
                int z = at.z() + world[1];
                int ground = site.groundLevel(x, z);
                boolean barrel = Math.floorMod(dx + dz, 2) == 0;
                out.add(new Course(new BlockPos(x, ground, z), barrel
                        ? Blocks.BARREL.defaultBlockState()
                        : palette.log().defaultBlockState()));
            }
        }
        out.add(new Course(new BlockPos(at.x(), site.groundLevel(at.x(), at.z()) + 1,
                at.z()), Blocks.BARREL.defaultBlockState()));
    }

    /**
     * The civic well, reused rather than redrawn.
     *
     * <p>{@code CivicParts.well} is the shape the market square already has, down
     * to the filled cauldron standing in for a water source so that the auditor
     * does not read the middle of the village as a flooded building. A second
     * drawing of the same thing would be a second chance to get that wrong.
     */
    private static void well(BlueprintPlacer.Site site, List<Course> out,
                             Furnishings.Furnishing piece, Palette palette) {
        SimPos at = piece.at();
        BlockPos base = new BlockPos(at.x(), site.groundLevel(at.x(), at.z()) - 1, at.z());
        List<Placement> drawn = new ArrayList<>();
        CivicParts.well(drawn, base, 0, 0, palette.rim(), palette.post());
        for (Placement placement : drawn) {
            out.add(new Course(placement.pos(), placement.state()));
        }
    }

    /**
     * The paved square: flags underfoot, stair benches facing in on two sides, a
     * flower box at each corner and a board on a post to read.
     *
     * <p>The paving is a soil course, so it replaces the turf rather than standing
     * a course proud of it. A square you step up onto is a plinth.
     *
     * <p>The board reads the town's name, what it has grown into and the day it
     * began. See {@link Signage#board}, which composes it, and {@link #inscribe},
     * which writes it and keeps writing it: the stage on that board changes under
     * the town as it grows, so a board written once would be a board that lies
     * about a village that became a town.
     */
    private static void square(BlueprintPlacer.Site site, List<Course> out,
                               Furnishings.Furnishing piece, Palette palette,
                               List<String> lines) {
        SimPos at = piece.at();
        int reach = piece.reach();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = at.x() + dx;
                int z = at.z() + dz;
                int ground = site.groundLevel(x, z);
                boolean edge = Math.abs(dx) == reach || Math.abs(dz) == reach;
                out.add(new Course(new BlockPos(x, ground - 1, z),
                        (edge ? palette.rim() : palette.paving()).defaultBlockState(),
                        true));
                boolean corner = Math.abs(dx) == reach && Math.abs(dz) == reach;
                if (corner) {
                    out.add(new Course(new BlockPos(x, ground, z),
                            palette.flower().defaultBlockState()));
                }
            }
        }
        // Benches: two runs of stairs facing the middle, a pace in from the edge,
        // with the ends left open so nobody has to vault a corner to sit down.
        for (int side = -1; side <= 1; side += 2) {
            int dz = side * (reach - 1);
            for (int dx = -reach + 2; dx <= reach - 2; dx++) {
                int x = at.x() + dx;
                int z = at.z() + dz;
                out.add(new Course(new BlockPos(x, site.groundLevel(x, z), z),
                        CivicParts.stair(palette.pavingStairs(),
                                side > 0 ? Direction.NORTH : Direction.SOUTH)));
            }
        }
        // And the board, on the far side from the benches so there is room to
        // stand in front of it.
        int boardX = at.x() + reach - 1;
        int boardZ = at.z();
        int ground = site.groundLevel(boardX, boardZ);
        out.add(new Course(new BlockPos(boardX, ground, boardZ),
                palette.post().defaultBlockState()));
        out.add(new Course(new BlockPos(boardX, ground + 1, boardZ),
                palette.post().defaultBlockState()));
        out.add(new Course(new BlockPos(boardX - 1, ground + 1, boardZ),
                palette.sign().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST),
                false, lines));
    }

    /**
     * A grid of the country's own saplings, two apart, on whatever ground is left.
     *
     * <p>Two apart and not one: a tree wants room for its crown, and a nine-block
     * thicket of saplings grows into one canopy, which is the very thing
     * {@code InteriorClearing} exists to take out of the middle of a town.
     */
    private static void orchard(BlueprintPlacer.Site site, List<Course> out,
                                Furnishings.Furnishing piece, Palette palette) {
        SimPos at = piece.at();
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                int x = at.x() + dx;
                int z = at.z() + dz;
                out.add(new Course(new BlockPos(x, site.groundLevel(x, z), z),
                        palette.sapling().defaultBlockState()));
            }
        }
    }

    /**
     * A clipped run of hedge along a street, a course high with a taller middle.
     *
     * <p>Persistent leaves, and that is not a detail: ordinary leaves check their
     * distance from a log and vanish, so a hedge laid without it is gone inside a
     * minute and the sweep spends the rest of the town's life re-planting it. It
     * is the torch-on-a-fence fault in a slower form and would have been just as
     * invisible.
     */
    private static void hedge(BlueprintPlacer.Site site, List<Course> out,
                              Furnishings.Furnishing piece, Palette palette) {
        SimPos at = piece.at();
        BlockState leaf = palette.hedge().defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true);
        boolean alongX = !acrossX(piece.facing());
        for (int step = -2; step <= 2; step++) {
            int x = at.x() + (alongX ? step : 0);
            int z = at.z() + (alongX ? 0 : step);
            int ground = site.groundLevel(x, z);
            out.add(new Course(new BlockPos(x, ground, z), leaf));
            if (Math.abs(step) <= 1) {
                out.add(new Course(new BlockPos(x, ground + 1, z), leaf));
            }
        }
    }

    /** One sapling on the verge, with the turf under it turned so it reads as planted. */
    private static void avenueTree(BlueprintPlacer.Site site, List<Course> out,
                                   Furnishings.Furnishing piece, Palette palette) {
        SimPos at = piece.at();
        int ground = site.groundLevel(at.x(), at.z());
        out.add(new Course(new BlockPos(at.x(), ground - 1, at.z()),
                Blocks.COARSE_DIRT.defaultBlockState(), true));
        out.add(new Course(new BlockPos(at.x(), ground, at.z()),
                palette.sapling().defaultBlockState()));
    }

    /**
     * A post with a board on it, at the corner of a junction, facing the middle of
     * the town.
     *
     * <p>A solid post and a wall sign rather than a fence and a standing sign, for
     * the reason the lamps have a solid head: a sign wants a face to hang on, and
     * a fence post has not got one.
     *
     * <p>The board used to be left blank, and the note here said why: writing the
     * town's name on it meant a sign block entity and a text payload, which was a
     * thing this seam did not carry. It carries one now — a {@link Course} has
     * lines on it — so a post at a crossroads says whose town this is and which
     * way the hall lies, which is the question somebody standing at a crossroads
     * is actually asking. The argument that replaced the old one is unchanged in
     * kind: a <em>wrong</em> board would still read as a bug, which is why the
     * text is derived on every sweep from the town rather than written down once.
     *
     * <p>The inn's own board is this shape and this cost, turned round.
     * {@code towardTheGate} gives the way the thing the piece belongs to lies, and
     * a signpost hangs its board on that face because the thing it belongs to is
     * the middle of the town. An inn board hangs on the opposite face, because
     * nobody reads an inn sign from inside the inn.
     *
     * @param inward whether the board faces the thing this piece belongs to
     */
    private static void signpost(BlueprintPlacer.Site site, List<Course> out,
                                 Furnishings.Furnishing piece, Palette palette,
                                 List<String> lines, boolean inward) {
        SimPos at = piece.at();
        int ground = site.groundLevel(at.x(), at.z());
        out.add(new Course(new BlockPos(at.x(), ground, at.z()),
                palette.post().defaultBlockState()));
        out.add(new Course(new BlockPos(at.x(), ground + 1, at.z()),
                palette.post().defaultBlockState()));
        Direction looking = towardTheGate(inward ? piece.facing() : piece.facing() + 2);
        out.add(new Course(new BlockPos(at.x(), ground + 1, at.z())
                        .relative(looking),
                palette.sign().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, looking),
                false, lines));
    }

    /** A ring of stones round a fire, and two logs to sit on. */
    private static void firePit(BlueprintPlacer.Site site, List<Course> out,
                                Furnishings.Furnishing piece, Palette palette) {
        SimPos at = piece.at();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = at.x() + dx;
                int z = at.z() + dz;
                int ground = site.groundLevel(x, z);
                if (dx == 0 && dz == 0) {
                    out.add(new Course(new BlockPos(x, ground, z),
                            Blocks.CAMPFIRE.defaultBlockState()
                                    .setValue(CampfireBlock.LIT, true)));
                    continue;
                }
                // Both courses stand on the surface rather than replacing it. A
                // ring of hearthstones set flush with the grass is a patio; what a
                // camp fire looks like is stones laid round it on the ground.
                boolean seat = Math.abs(dx) == 1 && dz == 0;
                out.add(new Course(new BlockPos(x, ground, z), seat
                        ? palette.log().defaultBlockState()
                        : palette.rim().defaultBlockState()));
            }
        }
    }

    /** A goblin cage: a box of fence with a gate hung open, because it is empty. */
    private static void cage(BlueprintPlacer.Site site, List<Course> out,
                             Furnishings.Furnishing piece, Palette palette) {
        SimPos at = piece.at();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int[] world = turn(dx, dz, piece.facing());
                int x = at.x() + world[0];
                int z = at.z() + world[1];
                int ground = site.groundLevel(x, z);
                boolean gate = dx == 0 && dz == 1;
                out.add(new Course(new BlockPos(x, ground, z), gate
                        ? palette.gate().defaultBlockState()
                                .setValue(FenceGateBlock.OPEN, true)
                                .setValue(HorizontalDirectionalBlock.FACING,
                                        towardTheGate(piece.facing()))
                        : palette.fence().defaultBlockState()));
                if (!gate) {
                    out.add(new Course(new BlockPos(x, ground + 1, z),
                            palette.fence().defaultBlockState()));
                }
            }
        }
    }

    /** Three sharpened stakes driven into the verge, which is how a warhost decorates. */
    private static void stakes(BlueprintPlacer.Site site, List<Course> out,
                               Furnishings.Furnishing piece, Palette palette) {
        SimPos at = piece.at();
        boolean alongX = !acrossX(piece.facing());
        for (int step = -1; step <= 1; step++) {
            int x = at.x() + (alongX ? step : 0);
            int z = at.z() + (alongX ? 0 : step);
            int ground = site.groundLevel(x, z);
            out.add(new Course(new BlockPos(x, ground, z),
                    palette.fence().defaultBlockState()));
            out.add(new Course(new BlockPos(x, ground + 1, z),
                    palette.fence().defaultBlockState()));
        }
    }

    /**
     * One grave: turned earth, a stone, and a board at the foot of it.
     *
     * <p>Three blocks, and the restraint is the point. A churchyard of twelve of
     * these is what a town that lost half its people to a winter looks like from
     * the road, and twelve of anything more elaborate would be a monument rather
     * than a village burying somebody. The stone is a low wall block — a
     * headstone is exactly the shape of one — in whatever masonry this people
     * already builds with, so the mire's dead get mud brick and the burghers' get
     * dressed stone off the same palette their houses come from.
     *
     * <p>A <em>standing</em> sign at the foot rather than a board hung on the
     * stone, and that is not a style choice. A wall sign needs a face to hang on
     * and a wall block has not got one — {@code WallSignBlock.canSurvive} refuses
     * it — so a board nailed to the headstone would pop off the moment it was
     * placed, and {@link #put} would report the course not done and try again
     * every sweep for ever. The signpost gets away with a wall sign because it
     * has a solid post; this has not.
     *
     * <p><strong>The board says who lies here.</strong> The name, the trade and
     * the day, which is {@code Settlement.Grave.epitaph} set across a board
     * rather than onto one — see {@code Signage.headstone} for why seventeen
     * characters do not go on a fifteen-character line. Which grave this stone is
     * for is {@link #graveAt}'s answer, and it comes from
     * {@code Furnishings.graves} and {@code Settlement.dead} being in the same
     * order rather than from anything written down beside the stone.
     */
    private static void grave(BlueprintPlacer.Site site, List<Course> out,
                              Furnishings.Furnishing piece, Palette palette,
                              List<String> lines) {
        SimPos at = piece.at();
        int ground = site.groundLevel(at.x(), at.z());
        // The turned earth under the stone, laid as soil so it takes the place of
        // the grass rather than standing on it.
        out.add(new Course(new BlockPos(at.x(), ground - 1, at.z()),
                Blocks.COARSE_DIRT.defaultBlockState(), true));
        out.add(new Course(new BlockPos(at.x(), ground, at.z()),
                palette.headstone().defaultBlockState()));
        Direction foot = towardTheGate(piece.facing());
        BlockPos board = new BlockPos(at.x(), ground, at.z()).relative(foot);
        out.add(new Course(board.below(), Blocks.COARSE_DIRT.defaultBlockState(), true));
        out.add(new Course(board, palette.board().defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, signRotation(piece.facing())),
                false, lines));
    }

    /**
     * A standing sign's rotation for one of the four facings.
     *
     * <p>Sixteen positions round the circle against {@code Building.facing}'s
     * four, so each quarter turn is four steps: 0 south, 4 west, 8 north, 12
     * east, which is vanilla's own numbering of the same circle. A grave faces
     * the middle of the town — see {@code Furnishings.theGraves} — so the board
     * reads to somebody walking out of it.
     */
    static int signRotation(int facing) {
        return Math.floorMod(facing, 4) * 4;
    }

    // --- turning a piece to face its building --------------------------------

    /**
     * A local offset turned to the piece's facing.
     *
     * <p>{@code Building.facing}'s convention: 0 means the thing this piece
     * belongs to lies toward +z, 1 toward -x, 2 toward -z, 3 toward +x. So a yard
     * drawn with its gate at local +z has its gate toward its house whichever way
     * round the house ended up.
     */
    static int[] turn(int dx, int dz, int facing) {
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> new int[] {-dz, dx};
            case 2 -> new int[] {-dx, -dz};
            case 3 -> new int[] {dz, -dx};
            default -> new int[] {dx, dz};
        };
    }

    /** Which way the thing this piece belongs to lies. */
    static Direction towardTheGate(int facing) {
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            case 3 -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }

    /** Whether this facing runs along x rather than along z. */
    private static boolean acrossX(int facing) {
        int f = Math.floorMod(facing, 4);
        return f == 1 || f == 3;
    }

    // --- the world -----------------------------------------------------------

    /** What this town's next piece is made of, read against the real ground. */
    public static List<Course> planAt(ServerLevel level, Settlement settlement, int index) {
        List<Furnishings.Furnishing> pieces = Furnishings.pieces(settlement);
        if (index < 0 || index >= pieces.size()) {
            return List.of();
        }
        return plan(siteFor(level, settlement), pieces.get(index),
                plaqueFor(level, settlement),
                graveAt(settlement, pieces.get(index)));
    }

    /**
     * Whose grave this stone is, or null when the piece is not one.
     *
     * <p>Worked out rather than written down, and it is worked out from one
     * fact: {@code Furnishings.graves} lists the planned stones in the order
     * {@code Settlement.dead} lists the people they are for. That is a property
     * the siting is written to have — the row is dug outward from its first
     * stone in exactly the order the center-outward sort will put it in — so the
     * <em>i</em>th stone is for the <em>i</em>th of the dead and nothing has to
     * carry a name through the plan to say so.
     *
     * <p>Null for a stone the plan has and the roster has not, which is the state
     * between a town burying its twelfth person and the oldest falling off the
     * list. A board with nothing on it is left exactly as it stands — see
     * {@link #inscribe}, which does nothing at all with an empty line list — so a
     * stone whose name has aged out keeps the name it was cut with rather than
     * being scrubbed blank.
     */
    static Settlement.Grave graveAt(Settlement settlement, Furnishings.Furnishing piece) {
        if (settlement == null || piece == null
                || piece.piece() != Furnishings.Piece.GRAVE) {
            return null;
        }
        int stone = Furnishings.graves(settlement).indexOf(piece);
        List<Settlement.Grave> dead = settlement.dead();
        return stone < 0 || stone >= dead.size() ? null : dead.get(stone);
    }

    /**
     * What this town's boards say, read off it now.
     *
     * <p>Now, and not once: the name never changes but the stage does, and a
     * village whose hall went up is a town the same step. Composing this on every
     * call is a handful of string operations against a sweep that reads two dozen
     * block states, and it is what makes the text obey the same rule as the
     * dressing it is written on — derived from what is standing, never saved.
     */
    static Signage.Plaque plaqueFor(ServerLevel level, Settlement settlement) {
        return Signage.of(settlement, simIntervalOf(level));
    }

    /**
     * How many game ticks the simulation takes between steps, here.
     *
     * <p>Asked of the running world rather than of the default, because a server
     * whose owner slowed the simulation down has longer days in steps and a board
     * reading a founding day off the default would be out by whatever they
     * changed. The default is the fallback for a level with no simulation on it
     * yet, which is what a test fixture is.
     */
    private static int simIntervalOf(ServerLevel level) {
        SimWorld world = CivilizationMod.simulationFor(level);
        return world == null ? SimWorld.SIM_INTERVAL_TICKS
                : world.settings().simIntervalTicks();
    }

    /**
     * The real world, asked the three questions a piece is allowed to ask it.
     *
     * <p>The culture comes off the settlement rather than being scanned for the
     * way {@code BlueprintPlacer.siteAt} has to scan for it: this caller already
     * knows whose town it is, and a second search of every kingdom for an answer
     * already in hand is a search too many.
     */
    static BlueprintPlacer.Site siteFor(ServerLevel level, Settlement settlement) {
        Culture culture = Culture.of(settlement.cultureId());
        return new BlueprintPlacer.Site() {
            @Override
            public boolean loaded(BlockPos pos) {
                return level.isLoaded(pos);
            }

            @Override
            public boolean unsupported(BlockPos pos) {
                return level.getBlockState(pos).isAir()
                        || !level.getFluidState(pos).isEmpty();
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

    /**
     * The first block of a piece's plan that is not standing yet, or null when
     * there is nothing left to do there.
     *
     * <p>{@link LightLayer#owed}, and the same reason for existing: a crew keeps
     * its place in the ground rather than in a cursor, so somebody killed halfway
     * round a garden fence resumes at the post that is missing.
     */
    public static Course owed(ServerLevel level, List<Course> plan) {
        for (Course course : plan) {
            if (!level.isLoaded(course.pos())) {
                continue;   // unread ground is not work owed; it is work unseen
            }
            if (stands(level.getBlockState(course.pos()), course)) {
                continue;
            }
            if (!replaceable(level, course)) {
                // Nothing can go there — the corner of the yard is somebody's
                // wall, or a pond. Skipped rather than reported owed, and this is
                // where a piece differs from a lamp: a lamp is one column, so a
                // column that refuses it is the whole work lost and the crew may
                // as well walk away. A garden is forty blocks, and abandoning the
                // other thirty-nine because one of them is a boulder would leave a
                // town of half-fenced gardens. The crew lays what it can.
                continue;
            }
            return course;
        }
        return null;
    }

    /**
     * Whether what is standing here counts as this course being done.
     *
     * <p>Usually "is it the block we asked for". The exception is the one that
     * makes an orchard possible at all: a sapling that took is a trunk with a
     * crown on it, and a plan that demanded the sapling back would have the town
     * fell its own orchard every sweep to replant it. A grown tree is the course
     * done, more done than it was when it was planted.
     */
    static boolean stands(BlockState state, Course course) {
        if (state.is(course.state().getBlock())) {
            return true;
        }
        if (!isSapling(course.state())) {
            return false;
        }
        // Asked of the block's own type rather than of {@code BlockTags.LOGS} and
        // {@code BlockTags.LEAVES}. A tag is a datapack's opinion and is not loaded
        // until a world is, so the tag version of this answered "no, that is not a
        // tree" to every question anybody could ask it without a running game —
        // which is to say it could only be tested by the very thing it was written
        // to avoid needing. A log is a pillar and a leaf is a leaf, in this
        // version and in every version before it.
        Block grown = state.getBlock();
        return grown instanceof LeavesBlock
                || grown instanceof RotatedPillarBlock;
    }

    /**
     * Whether what was planned here was a sapling.
     *
     * <p>Asked of the block's own type rather than of a tag, because there is no
     * sapling tag to ask — and asked of the <em>plan</em> rather than of the
     * ground, so a wild tree that happens to have grown where a fence was planned
     * is still a fence that is missing.
     */
    private static boolean isSapling(BlockState state) {
        return state.getBlock() instanceof SaplingBlock;
    }

    /** One block of a piece, put down by hand. */
    public static boolean layByHand(ServerLevel level, Course course) {
        return put(level, course);
    }

    /**
     * Whether anything of this piece is standing yet.
     *
     * <p>The charge-once question {@code LightLayer.oursStandsAt} asks, and asked
     * for the same reason: a piece's position is derived from a town that grows
     * under it, so the piece a builder is sent to may be one that is already
     * there, and charging for it twice would take two gardens' worth of fence out
     * of the stores for one garden.
     */
    public static boolean oursStandsAt(ServerLevel level, Settlement settlement, int index) {
        for (Course course : planAt(level, settlement, index)) {
            if (level.isLoaded(course.pos())
                    && stands(level.getBlockState(course.pos()), course)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stamps the raised prefix of the dressing into the world, and mends what has
     * been broken out of it.
     *
     * <p>{@link LightLayer#draw} in every particular, including the one thing it
     * deliberately does not do: it cannot tell a piece drawn for the first time
     * from one put back, because it wraps round the town and skips unloaded
     * ground, so a dead town's dressing is left exactly as its wall and its lamps
     * are. The gardens a town kept while it lived stay; the ones it never got to
     * never appear.
     *
     * @param handsOnTheDressing whether a builder is out raising pieces himself,
     *                           in which case the sweep stands aside
     */
    public static void draw(ServerLevel level, Settlement settlement,
                            boolean handsOnTheDressing) {
        if (!settlement.hasLivingResidents() || handsOnTheDressing) {
            return;
        }
        int raised = settlement.piecesRaised();
        if (raised <= 0) {
            return;
        }
        List<Furnishings.Furnishing> pieces = Furnishings.pieces(settlement);
        int limit = Math.min(raised, pieces.size());
        if (limit <= 0) {
            return;
        }
        BlueprintPlacer.Site site = siteFor(level, settlement);
        Signage.Plaque plaque = plaqueFor(level, settlement);
        long now = System.nanoTime();
        Long previous = LAST_DRAW.put(settlement.id(), now);
        long elapsed = previous == null ? DrawBudget.NANOS_PER_SECOND : now - previous;
        int budget = DrawBudget.forElapsed(elapsed, PIECES_PER_SECOND, CATCH_UP_SECONDS);
        int start = CURSOR.getOrDefault(settlement.id(), 0);
        if (start >= limit) {
            start = 0;
        }
        int placed = 0;
        int looked = 0;
        int examined = 0;
        int i = start;
        while (looked < SCAN && examined < limit && placed < budget) {
            Furnishings.Furnishing piece = pieces.get(i);
            BlockPos column = new BlockPos(piece.at().x(), piece.at().y(), piece.at().z());
            if (level.isLoaded(column)) {
                placed += raise(level, site, settlement, piece, plaque) ? 1 : 0;
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

    /** Where each town's sweep stopped, so the budget travels round the dressing. */
    private static final Map<Settlement.Id, Integer> CURSOR = new java.util.HashMap<>();

    /** When each town's dressing was last swept, so the budget is real time. */
    private static final Map<Settlement.Id, Long> LAST_DRAW = new java.util.HashMap<>();

    /**
     * Drops what the sweep remembers about a world that is closing.
     *
     * <p>{@code LightLayer.forget}'s housekeeping, and it matters for the same
     * reason: a timestamp from the last session would have the first sweep of the
     * next one reading an elapsed time measured in minutes.
     */
    public static void forget() {
        CURSOR.clear();
        LAST_DRAW.clear();
    }

    /** Stands one whole piece, and says whether anything went in. */
    private static boolean raise(ServerLevel level, BlueprintPlacer.Site site,
                                 Settlement settlement, Furnishings.Furnishing piece,
                                 Signage.Plaque plaque) {
        boolean placed = false;
        for (Course course : plan(site, piece, plaque, graveAt(settlement, piece))) {
            placed |= put(level, course);
        }
        return placed;
    }

    /**
     * Whether this block is part of a town's dressing rather than part of a
     * building.
     *
     * <p>Asked by anything that counts solid blocks inside a plot and has no way
     * of knowing a garden fence is not a wall. {@code Furnishings} already refuses
     * to plan a piece inside anybody's footprint and keeps a block of clearance
     * besides — so this should never fire, and it is here because "should never"
     * is how the wall came to count a hundred and eighty-one tree trunks as
     * palisade.
     */
    public static boolean isDressing(BlockState state) {
        for (FurnishingStyle style : FurnishingStyle.values()) {
            Palette palette = paletteOf(style);
            if (state.is(palette.fence()) || state.is(palette.gate())
                    || state.is(palette.hedge()) || state.is(palette.sapling())
                    || state.is(palette.flower()) || state.is(palette.sign())
                    // A headstone is a wall block standing on open ground, which
                    // is exactly what anything counting masonry inside a plot is
                    // looking for. The graveyard is out past the last house so it
                    // should never be inside one — "should never" being how the
                    // wall came to count tree trunks as palisade.
                    || state.is(palette.headstone()) || state.is(palette.board())) {
                return true;
            }
        }
        return state.is(Blocks.HAY_BLOCK) || state.is(Blocks.BARREL)
                || state.is(Blocks.CAMPFIRE);
    }

    private static boolean put(ServerLevel level, Course course) {
        if (!level.isLoaded(course.pos())) {
            return false;
        }
        if (stands(level.getBlockState(course.pos()), course)) {
            // Standing, but not necessarily saying the right thing. A board is
            // the one course whose correctness is not settled by the block being
            // there, so the repair sweep looks at the words as well.
            inscribe(level, course);
            return false;
        }
        if (!replaceable(level, course)) {
            return false;
        }
        level.setBlock(course.pos(), course.state(), Block.UPDATE_ALL);
        // Only if it survived. A block that pops off the moment it is set is not
        // work done, and counting it as work is what let one bad choice of block
        // halt an entire wall -- see the class comment.
        boolean took = level.getBlockState(course.pos()).is(course.state().getBlock());
        if (took) {
            inscribe(level, course);
        }
        return took;
    }

    /**
     * Writes a course's words on the board that is standing at it.
     *
     * <p>Three things this deliberately is not. It is not a placement: a board
     * that had to be rewritten has cost the town nothing and is not counted
     * against the drawing budget, or a town with three signs in it would spend
     * every sweep on them and never finish a garden. It is not a save: nothing
     * here is written down, and the words are composed again from the standing
     * town on the next sweep. And it is not unconditional — the text is compared
     * first, because {@code SignBlockEntity.setText} marks the block entity
     * changed and re-sends it to every client in range, and doing that four times
     * a second for every board in a town is a packet storm for no picture.
     *
     * <p>Front face only. Every board the dressing puts up is a wall sign hung on
     * a post, so its back is inside the post.
     */
    private static void inscribe(ServerLevel level, Course course) {
        if (course.lines().isEmpty()) {
            return;
        }
        if (!(level.getBlockEntity(course.pos()) instanceof SignBlockEntity board)) {
            return;   // somebody replaced the board with something that is not one
        }
        if (reads(board.getFrontText(), course.lines())) {
            return;
        }
        SignText written = new SignText();
        for (int i = 0; i < course.lines().size() && i < SignText.LINES; i++) {
            written = written.setMessage(i, Component.literal(course.lines().get(i)));
        }
        board.setText(written, true);
    }

    /** Whether the board already says exactly this. */
    private static boolean reads(SignText standing, List<String> lines) {
        Component[] on = standing.getMessages(false);
        for (int i = 0; i < SignText.LINES; i++) {
            String wanted = i < lines.size() ? lines.get(i) : "";
            if (!on[i].getString().equals(wanted)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this course may take the cell it wants.
     *
     * <p>A soil course may replace the ground it is laid over — that is what makes
     * a paved square flush with the grass instead of a plinth standing on it — but
     * never a fluid, because paving a pond is how a village comes to have a
     * cobblestone lake in the middle of it. Everything else takes only air, what
     * vanilla itself calls replaceable, and growth.
     */
    private static boolean replaceable(ServerLevel level, Course course) {
        BlockState state = level.getBlockState(course.pos());
        if (course.soil()) {
            return state.getFluidState().isEmpty() && !state.isAir()
                    && state.getDestroySpeed(level, course.pos()) >= 0;
        }
        return state.isAir() || state.canBeReplaced() || WallClearing.isPlant(state);
    }
}
