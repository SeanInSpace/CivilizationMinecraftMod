package com.civilization.neoforge.world;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.settlement.BuildingSizes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one people's houses are made of, and which of {@link Parts} they use.
 *
 * <p>{@link Culture} already says how a people lay a town out, what beasts they
 * keep and what they call their children. It said nothing at all about what
 * their houses look like, so every town in the mod was built out of the same oak
 * planks in the same shape and the arrangements were the only thing telling one
 * people from another — which is invisible from the ground, where a player
 * stands.
 *
 * <p>This is the missing column. A style is a palette and a list of parts, and
 * nothing else: no geometry lives here, because geometry is {@link Parts}, and
 * no culture-picking lives in {@link Parts}, because that is here. A people who
 * have never been drawn fall through to the lowland style exactly as
 * {@code Culture.of} falls through to the default — a town built by somebody
 * nobody has styled is a town of ordinary houses, not a town of markers.
 *
 * <p><strong>Deliberately not in {@code common}.</strong> Every field is a
 * {@link Block}, which {@code common} has never seen and must never see. When
 * these become datapack entries the ids will live in {@code common} and the
 * blocks they resolve to will still live here.
 *
 * @param wall       the plain wall the building is mostly made of
 * @param frame      the corner posts, and the merlons of a flat roof
 * @param roofStairs what the slopes are laid in
 * @param roofRidge  a full block of the same stuff, for the ridge and the
 *                   valleys and the lintel of a porch
 * @param plinth     the stone course at the foot of the wall, and the chimney
 * @param gable      what the triangular ends of a pitched roof are closed with
 * @param timber     the posts of a half-timbered wall
 * @param timberEvery blocks of wall between posts, or zero for a plain wall
 * @param roofCap    the most courses a hip may rise, or zero for a full one
 * @param post       what a porch stands on
 * @param trapdoor   what a shutter is made of
 */
record HouseStyle(Block wall, Block frame, Block roofStairs, Block roofRidge,
                  Block plinth, Block gable, Roof roof, Block timber, int timberEvery,
                  int roofCap, boolean chimney, boolean dormers, boolean porch,
                  Block post, Block trapdoor) {

    /** The three roofs a people can build. */
    enum Roof {

        /** Two slopes and a ridge across the door. */
        GABLE,

        /** Slopes on all four sides, to a point or a short ridge. */
        HIP,

        /** No pitch at all, which is a decision and not an omission. */
        FLAT
    }

    /**
     * What actually goes on this footprint, which is not always what the people
     * would prefer.
     *
     * <p>A gable over an L is two gables and the valley where they meet, and a
     * valley has to be authored stair by stair. The croft is the one L in the
     * mod, and it gets a hip whoever built it — the measured hip in
     * {@link Parts#hipRoof} bends round a corner on its own. Said here rather
     * than inside the drawing so that it is a property of the style rather than
     * a surprise in the middle of a roof.
     */
    Roof roofFor(BuildingSizes.Size size) {
        return roof == Roof.GABLE && size.notch().isCut() ? Roof.HIP : roof;
    }

    /**
     * The three things one house differs from the next by.
     *
     * <p>Every house in a town is built by the same people out of the same
     * stuff, and a street of identical houses is the fault this whole unit is
     * about, one level up. So each building takes a few decisions of its own —
     * but takes them from where it stands, never from a die. A drawing is
     * compared against the world every time a repair is considered
     * ({@code BlueprintPlacer.owedOf}), so a house that shuttered its windows on
     * a whim would be a house whose crew unshuttered them the next time anybody
     * looked.
     *
     * @param shutters      whether the windows have any
     * @param chimneySide   which gable end the chimney climbs, as -1 or 1
     * @param windowSpacing how far apart the extra panes are
     */
    record Variation(boolean shutters, int chimneySide, int windowSpacing) {

        /**
         * The variation belonging to a building standing here.
         *
         * <p>SplitMix64's finalizer over the two horizontal coordinates, for the
         * same reason {@code Culture.layoutFor} uses it: a settlement's
         * buildings are laid out on a spacing grid, so the low bits of x and z
         * move in lockstep and reading them raw would give a whole street one
         * answer. The height is left out on purpose — a building resited a block
         * up the hill is the same building and must not redraw itself.
         */
        static Variation forOrigin(BlockPos origin) {
            long spread = spread(origin.getX(), origin.getZ());
            return new Variation(
                    (spread & 1L) == 0L,
                    (spread & 2L) == 0L ? -1 : 1,
                    2 + (int) ((spread >>> 2) & 1L));
        }

        private static long spread(long x, long z) {
            long h = x * 0x9E3779B97F4A7C15L
                    ^ z * 0xC2B2AE3D27D4EB4FL
                    ^ 0x2545F4914F6CDD1DL;
            h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
            h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
            return h ^ (h >>> 31);
        }
    }

    /**
     * The lowland style, which is what every town in the mod has quietly been.
     *
     * <p>Oak planks with the frame showing through, a cobble course at the foot
     * of the wall where the damp gets in, and a pitched oak roof with the eaves
     * over the door. The porch is theirs: it is the cheapest part in the file
     * and the one that most makes a wall look like a front.
     */
    private static final HouseStyle LOWLAND = new HouseStyle(
            Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.OAK_STAIRS, Blocks.OAK_PLANKS,
            Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Roof.GABLE,
            Blocks.OAK_LOG, 2, 0, false, false, true,
            Blocks.OAK_FENCE, Blocks.OAK_TRAPDOOR);

    /**
     * The hill people: spruce on a stone footing, hipped, and a chimney.
     *
     * <p>A hip sheds weather off every side instead of two, which is what you
     * build where the wind does not agree to come from one direction, and it is
     * the visible opposite of the lowland gable from any angle at all.
     */
    private static final HouseStyle HIGHLAND = new HouseStyle(
            Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_PLANKS, Blocks.STONE, Blocks.SPRUCE_PLANKS, Roof.HIP,
            Blocks.STRIPPED_SPRUCE_LOG, 0, 0, true, false, false,
            Blocks.SPRUCE_FENCE, Blocks.SPRUCE_TRAPDOOR);

    /**
     * The vale folk: pale stripped oak, and dormers in the front slope.
     *
     * <p>They face everything onto their green, so their houses are the ones
     * with something to look out of — a window in the roof as well as in the
     * wall.
     */
    private static final HouseStyle VALE = new HouseStyle(
            Blocks.STRIPPED_OAK_WOOD, Blocks.OAK_LOG, Blocks.OAK_STAIRS,
            Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.STRIPPED_OAK_WOOD, Roof.GABLE,
            Blocks.OAK_LOG, 0, 0, false, true, true,
            Blocks.OAK_FENCE, Blocks.OAK_TRAPDOOR);

    /**
     * The townsfolk: brick on a stone-brick plinth under a dark oak roof.
     *
     * <p>The only people here who build out of something somebody had to fire,
     * which is the point of them — burghers are the ones who lay a plan before
     * they build on it and buy their walls rather than felling them. Every house
     * gets a chimney; a town house has a hearth in it, not a fire in the yard.
     */
    private static final HouseStyle BURGHER = new HouseStyle(
            Blocks.BRICKS, Blocks.STONE_BRICKS, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_PLANKS, Blocks.STONE_BRICKS, Blocks.BRICKS, Roof.GABLE,
            Blocks.STONE_BRICKS, 0, 0, true, false, false,
            Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_TRAPDOOR);

    /**
     * The goblins: dark oak and mud, and a hip that gives up after two courses.
     *
     * <p>The cap is the whole of it. A goblin house is not a small house, it is
     * a low one — dug in rather than raised, wider than it is tall, and roofed
     * as flatly as a roof can be while still being a roof.
     */
    private static final HouseStyle GOBLIN = new HouseStyle(
            Blocks.DARK_OAK_PLANKS, Blocks.PACKED_MUD, Blocks.MUD_BRICK_STAIRS,
            Blocks.MUD_BRICKS, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Roof.HIP,
            Blocks.PACKED_MUD, 0, 2, false, false, false,
            Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_TRAPDOOR);

    /**
     * The orcs, who do not pitch a roof and never did.
     *
     * <p>Flat, with a parapet round it. Deliberately the one people who get no
     * roof out of this at all: a barracks with a gable on it is a cottage, and
     * everything else about an orc settlement — the ruled grid, the counted
     * pitch — says these are people who build a wall and stand on it.
     */
    private static final HouseStyle ORC = new HouseStyle(
            Blocks.SPRUCE_PLANKS, Blocks.STONE, Blocks.COBBLESTONE_STAIRS,
            Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.STONE, Roof.FLAT,
            Blocks.STONE, 0, 0, false, false, true,
            Blocks.SPRUCE_FENCE, Blocks.SPRUCE_TRAPDOOR);

    private static final Map<String, HouseStyle> BY_CULTURE = byCulture();

    private static Map<String, HouseStyle> byCulture() {
        Map<String, HouseStyle> table = new LinkedHashMap<>();
        table.put("civilization:default", LOWLAND);
        table.put("civilization:human/norman", LOWLAND);
        table.put("civilization:human/highland", HIGHLAND);
        table.put("civilization:human/vale", VALE);
        table.put("civilization:human/burgher", BURGHER);
        table.put("civilization:goblin/mire", GOBLIN);
        table.put("civilization:orc/warhost", ORC);
        return Map.copyOf(table);
    }

    /**
     * How this people build, or the lowland way if nobody has said.
     *
     * <p>Null-safe for the same reason {@code Culture.of} is: a settlement saved
     * before cultures had names carries no id, and the one lookup guaranteed to
     * happen on an old world is the one that would otherwise throw.
     */
    static HouseStyle forCulture(String cultureId) {
        return cultureId == null ? LOWLAND : BY_CULTURE.getOrDefault(cultureId, LOWLAND);
    }
}
