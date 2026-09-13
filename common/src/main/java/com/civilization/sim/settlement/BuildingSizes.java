package com.civilization.sim.settlement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How big each building actually is.
 *
 * <p>One table, read by the two halves that have to agree about it: the placer
 * draws a building this size, and the catalog reserves ground for a building
 * this size. They used to be two separate sets of numbers — the size was a
 * literal in each drawing method and the span was a column in
 * {@link BuildCatalog} — and they had drifted a long way apart. A cottage was
 * drawn five blocks across and given a plot nine wide; a house was drawn five
 * and given eleven. Every street in the mod was spaced for buildings twice the
 * size of the ones standing in it, which is the whole of why a village read as
 * huts in a field.
 *
 * <p>The drift is not a thing to be fixed once. {@code procedural} carries a
 * comment recording that buildings used to grow two blocks per level while the
 * reserved plot did not, so a fourth-level house grew through its neighbor —
 * the same fault, from the same cause, and it was answered by deleting the
 * growth rather than by making the two agree. This is the making-them-agree.
 *
 * <p><strong>The span is derived, never declared.</strong> A building's plot is
 * its own footprint plus the doorstep ring cleared round it, and that is all it
 * is. A few kinds want more ground than their walls — a field wants its fence
 * clear of the crop, a compound wants room for another pen — and those say so
 * explicitly, with the reason, rather than by quietly padding the number.
 */
public final class BuildingSizes {

    private BuildingSizes() {
    }

    /**
     * The doorstep ring cleared round every building.
     *
     * <p>Must match {@code BlueprintPlacer.APRON_MARGIN}, which reads it from
     * here so it cannot fall out of step.
     */
    public static final int APRON = 1;

    /**
     * A rectangular bite taken out of one corner of a building.
     *
     * <p>This is how a shape that is not a rectangle is said, and one corner is
     * deliberately as far as it goes. A single notch gives the L, which is the
     * shape that actually earns its keep: a house wrapped round its own yard, a
     * hall with a wing, a smithy with the forge in the crook. Two notches give a
     * T or a U, three give an H, and all of them are the same arithmetic — but
     * nothing wants them yet, and a shape language nobody uses is a shape
     * language nobody checks.
     *
     * <p>Corners are named by direction rather than by compass, because a
     * building is turned to face its street and a notch turns with it.
     *
     * @param width   how far the bite reaches across, in blocks
     * @param depth   how far it reaches back
     * @param towardX 1 for the +x corner, -1 for the -x corner
     * @param towardZ 1 for the +z corner, -1 for the -z corner
     */
    public record Notch(int width, int depth, int towardX, int towardZ) {

        /** A building that is simply a rectangle, which is most of them. */
        public static final Notch NONE = new Notch(0, 0, 1, 1);

        public boolean isCut() {
            return width > 0 && depth > 0;
        }
    }

    /**
     * How much ground one building covers, walls only — and how much air over it.
     *
     * <p>Both spans odd, always. Everything is drawn symmetrically about its
     * origin, so an even span would put the origin off center and a quarter turn
     * would move the building half a block. The height is under no such rule: it
     * is measured from the floor course, and there is no turn that moves a roof.
     *
     * <p><strong>The height is a ceiling, not an equality.</strong> Width and
     * depth are exact — a plot is reserved from them and a building drawn a block
     * wider stands in its neighbor's ground — but a roof's pitch is the culture's
     * business: the same cottage comes out ten courses tall in the highlands and
     * six in a goblin mire, off the same declared footprint. So this is the
     * tallest any people draws the kind, and a shorter roof is not drift. That is
     * exactly how {@code animal_farm}'s depth already works, for the same reason
     * and with the same test shape behind it.
     */
    public record Size(int width, int depth, int height, Notch notch, int chamfer) {

        public Size(int width, int depth, int height) {
            this(width, depth, height, Notch.NONE, 0);
        }

        public Size(int width, int depth, int height, Notch notch) {
            this(width, depth, height, notch, 0);
        }

        /**
         * A round building: a rectangle with all four corners cut back.
         *
         * <p>The other way of saying a shape that is not a rectangle, and the
         * only one that gives something round. A {@link Notch} takes a bite out
         * of one corner and makes an L; this takes a slice off all four and
         * makes an octagon, which at seven blocks across is as close to a circle
         * as a world made of cubes gets.
         *
         * @param chamfer how far in from each corner the cut reaches, as a
         *                distance along the two axes together
         */
        public static Size round(int width, int depth, int height, int chamfer) {
            return new Size(width, depth, height, Notch.NONE, chamfer);
        }

        public Size {
            if (width <= 0 || depth <= 0) {
                throw new IllegalArgumentException("a building has to have a size");
            }
            if (height <= 0) {
                throw new IllegalArgumentException(
                        "a building stands at least one course tall: " + height);
            }
            if (width % 2 == 0 || depth % 2 == 0) {
                throw new IllegalArgumentException(
                        "buildings are drawn about their origin, so both spans must be odd: "
                                + width + "x" + depth);
            }
            if (notch == null) {
                notch = Notch.NONE;
            }
            if (notch.isCut()
                    && (notch.width() >= width || notch.depth() >= depth)) {
                throw new IllegalArgumentException(
                        "a notch that reaches the far wall is not a notch, it is two buildings");
            }
            if (chamfer < 0) {
                throw new IllegalArgumentException("a corner is cut back or it is not");
            }
            // Cut past the middle and the four corners meet: the shape closes up
            // into nothing, or into a diamond the door is outside of.
            if (chamfer > 0 && chamfer >= Math.min(width, depth) / 2) {
                throw new IllegalArgumentException(
                        "a chamfer that reaches the middle is not a round building, "
                                + "it is a hole: " + chamfer + " off " + width + "x" + depth);
            }
            if (chamfer > 0 && notch.isCut()) {
                throw new IllegalArgumentException(
                        "a building is round or it is bent round a yard, not both");
            }
        }

        /**
         * The ground it holds: its walls and the doorstep round them.
         *
         * <p>Measured on the bounding box even when a corner is cut away. The
         * plan reserves ground before it knows which way round the building will
         * be turned, and a plot that only fitted at one rotation is not a plot —
         * the same reason the span is a square rather than the true rectangle.
         * What the notch buys is not a smaller plot; it is that the corner is
         * never dug out, never scraped flat, and reads as a yard.
         */
        public int span() {
            return Math.max(width, depth) + 2 * APRON;
        }

        /** Whether the building actually stands on this cell of its own box. */
        public boolean covers(int dx, int dz) {
            int rx = width / 2;
            int rz = depth / 2;
            if (Math.abs(dx) > rx || Math.abs(dz) > rz) {
                return false;
            }
            // A chamfer is one test on the sum of the two distances, which is
            // what makes the cut a straight diagonal and the shape an octagon.
            // It is deliberately measured from the box's own corner rather than
            // from the middle, so the same chamfer reads the same on a square
            // and on an oblong.
            if (chamfer > 0 && Math.abs(dx) + Math.abs(dz) > rx + rz - chamfer) {
                return false;
            }
            if (!notch.isCut()) {
                return true;
            }
            boolean inX = notch.towardX() > 0
                    ? dx > rx - notch.width() : dx < -rx + notch.width();
            boolean inZ = notch.towardZ() > 0
                    ? dz > rz - notch.depth() : dz < -rz + notch.depth();
            return !(inX && inZ);
        }
    }

    /**
     * What is drawn, by blueprint path.
     *
     * <p>Sizes are deliberately varied. A village where every roof is the same
     * five by five is a village of sheds however well the streets are drawn, and
     * the size of a building is the cheapest way there is of saying what it is
     * for: a hall is big because a hall is important, a watchtower is narrow
     * because it is tall, an inn has the widest roof on the street.
     *
     * <p>The third number is the height, and it was missing for a long time with
     * one visible consequence: the surveyor's lamp drew every queued plot as a box
     * six courses tall, because six is what a cottage happens to come out at and
     * nothing else was declared. A watchtower's order read as a shed and a grand
     * library's read as a shed, which is the one question a player carries the lamp
     * up a hill to ask. Each figure below is measured — see
     * {@code BlueprintPlacerSizeTest}, which draws all twenty-seven against every
     * culture there is and holds this column to them.
     */
    private static final Map<String, Size> DRAWN = drawn();

    private static Map<String, Size> drawn() {
        Map<String, Size> table = new LinkedHashMap<>();

        // The founding camp. Small on purpose: these are what four people with
        // nothing put up in their first week, and they are meant to be
        // outgrown.
        table.put("camp_post", new Size(3, 3, 4));
        table.put("cache", new Size(3, 3, 4));
        table.put("hearth", new Size(5, 5, 7));
        table.put("bunkhouse", new Size(9, 7, 10));

        // Homes, smallest to largest. A settlement's housing is most of what
        // anybody sees of it, so this is where the variety has to be.
        //
        // The heights here are the steepest roof any culture puts on them, which
        // in every case is the highland one: the same cottage is ten courses in
        // the hills and six in a mire. See Size, where the ceiling is argued.
        table.put("cottage", new Size(7, 7, 10));
        table.put("house", new Size(9, 9, 12));
        table.put("longhouse", new Size(13, 9, 12));
        // The one L in the mod, and the reason the shape language exists. Six
        // blocks by four are cut out of the corner away from the door, so the
        // house wraps two sides of a yard that is still walkable ground rather
        // than a scraped pad -- which is the whole visible difference between a
        // notch that is declared and a notch that is merely drawn.
        table.put("croft", new Size(13, 11, 12, new Notch(6, 4, 1, -1)));

        // The orc homes, and the only round buildings in the mod. Seven across
        // with two blocks off each corner gives a ring of 3-5-7-7-7-5-3, which
        // is what a circle looks like when the world is made of cubes: a hut,
        // not a shed. Five by five indoors, less the corners, which is room for
        // three and a fire.
        //
        // The great hut is the same shape at thirteen, so the chief's roof reads
        // as the same idea rather than as a different people's building. Three
        // off each corner keeps it as round as the small one -- a chamfer is a
        // count of blocks, so holding it at two on a building twice the size
        // would have made a square with the corners nipped.
        table.put("hut", Size.round(7, 7, 8, 2));
        table.put("great_hut", Size.round(13, 13, 13, 3));

        // Trades. Seven is a workshop with room to swing in; nine is a trade
        // with stock to keep.
        //
        // The mill is the tall one of these by five courses, because the sails
        // are the building: a mill that read as a shed on the lamp was the plan
        // telling a player nothing about the skyline it was promising.
        table.put("mill", new Size(7, 7, 13));
        table.put("carpentry", new Size(7, 7, 10));
        table.put("lumber_camp", new Size(7, 7, 8));
        table.put("mine", new Size(7, 7, 8));
        table.put("granary", new Size(7, 7, 10));
        table.put("smith", new Size(9, 7, 12));
        table.put("storehouse", new Size(9, 7, 10));
        table.put("workshop", new Size(9, 7, 10));

        // Civic. These are the buildings a town is read by from the air.
        //
        // The market is four courses -- a square with stalls round it, and no
        // roof over the middle at all -- which makes it the flattest thing in
        // the town and the clearest case for declaring a height rather than
        // guessing at six.
        table.put("market", new Size(9, 9, 4));
        table.put("inn", new Size(11, 9, 14));
        table.put("warehouse", new Size(11, 9, 15));
        table.put("town_hall", new Size(13, 11, 17));
        table.put("library", new Size(23, 17, 13));
        // The largest thing a settlement ever builds, and the one that has to be
        // asked for twice: a grand library is only wanted once an ordinary
        // library stands. Thirty-one by twenty-five claims thirty-three blocks
        // of ground, which is three of the plan's own frontages — so it is the
        // hardest case there is for the siting loop, and that is deliberate.
        // Anything that fits this fits everything.
        table.put("grand_library", new Size(31, 25, 21));

        // Odd shapes, which are their own footprint and not a cabin. And the two
        // extremes of the height column: a watchtower is fifteen courses on five
        // of ground, and a field is three on eleven.
        table.put("watchtower", new Size(5, 5, 15));
        table.put("farm", new Size(11, 11, 3));
        table.put("animal_farm", new Size(9, 17, 4));

        return Map.copyOf(table);
    }

    /**
     * Extra ground a few kinds want beyond their own walls, and why.
     *
     * <p>Everything absent from here takes exactly its footprint and a doorstep.
     * That is the rule, and these are the stated exceptions rather than slack
     * quietly baked into the sizes above.
     */
    private static final Map<String, Integer> ROOM_TO_SPARE = Map.of(
            // A field's fence is its wall, and a crop pressed against a
            // neighbor's doorstep is a crop somebody walks through.
            "farm", 2,
            // Pens are added a strip at a time as a culture keeps more beasts,
            // and the compound must not have to be resited when one is.
            "animal_farm", 2);

    /**
     * Whether this kind is drawn at whatever size the culture asks for, up to
     * the declared one.
     *
     * <p>Exactly one, and it is worth being explicit rather than letting the
     * check that watches for drift quietly tolerate anything smaller. A
     * compound is a strip of pens and a people that keeps three beasts wants
     * three of them; the ground is reserved for the most any people keeps, so
     * the pens can be added to without the whole farm being resited.
     */
    public static boolean variesWithCulture(String blueprintId) {
        return "animal_farm".equals(pathOf(blueprintId));
    }

    /** What is drawn for this blueprint, or null if nothing here draws it. */
    public static Size of(String blueprintId) {
        return DRAWN.get(pathOf(blueprintId));
    }

    /**
     * How tall a building nobody has a size for is assumed to be: six courses.
     *
     * <p>The sibling of {@link BuildPlanner#DEFAULT_PLOT_SPAN}, and it is here for
     * the same reason: a datapack's blueprint, or a save holding an id this build
     * has never heard of, still has to be drawn as something. Six is what an
     * ordinary cottage came out at, which is why it was the figure the surveyor's
     * lamp used for <em>every</em> plot before the column above existed.
     */
    public static final int DEFAULT_HEIGHT = 6;

    /**
     * How much air this building stands in: its declared height, or
     * {@link #DEFAULT_HEIGHT} for a kind this table has never heard of.
     *
     * <p>A ceiling rather than an exact figure — see {@link Size} — so what reads
     * this is reserving or drawing room for the tallest the kind gets, never
     * asserting a roof is at that course. Anything that needs the exact height of
     * a building that <em>stands</em> has one: {@code Footprint} carries what was
     * measured when it was drawn.
     */
    public static int heightOf(String blueprintId) {
        Size size = of(blueprintId);
        return size == null ? DEFAULT_HEIGHT : size.height();
    }

    /**
     * The ground this building holds: its own size, its doorstep, and any room
     * it has a stated reason to want.
     *
     * <p>Falls back to {@link BuildPlanner#DEFAULT_PLOT_SPAN} for anything this
     * table has never heard of, which is what a plan does with a building it
     * cannot size — reserve the ordinary amount and let the overlap check refuse
     * it if that turns out to be wrong.
     */
    public static int plotSpanOf(String blueprintId) {
        Size size = of(blueprintId);
        if (size == null) {
            return BuildPlanner.DEFAULT_PLOT_SPAN;
        }
        return size.span() + ROOM_TO_SPARE.getOrDefault(pathOf(blueprintId), 0);
    }

    /**
     * The building's own name, however it is addressed.
     *
     * <p>Borrowed from {@link BuildingRole} rather than written again here.
     * That class exists because eleven places once each had their own idea of
     * what {@code civilization:norman/house_l2} was called, and adding a twelfth
     * would be the joke telling itself.
     */
    private static String pathOf(String blueprintId) {
        return blueprintId == null ? "" : BuildingRole.bareName(blueprintId);
    }
}
