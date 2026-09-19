package com.civilization.sim.settlement;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How a building meets the ground, as arithmetic both halves of the mod share.
 *
 * <p><strong>The report this exists for.</strong> The in-game audit on arrival at
 * a seeded town said the hearth, lumber camp and mine of Millbrook were "buried —
 * the ground stands up to 3 above its floor on every side", Stonebridge's hearth
 * and mine the same, and a cottage "no way in — outside the gap is air". Those are
 * the auditor's words, and the seeded siting had passed every one of those plots.
 *
 * <p>It passed them because it was asking a different question. Siting asks
 * {@code WorldBridge.siteFault}: how far does the ground fall across the bulk of
 * this plot. The auditor asks something narrower and much less forgiving: once the
 * floor is set, can the ring of ground one step outside the walls be made level
 * with it? Those are not the same question and a plot can pass the first and fail
 * the second on any real hillside — a plot in a shallow bowl falls hardly at all
 * across its bulk and is surrounded on every side by ground three courses up.
 *
 * <h2>What the builders can actually do about it</h2>
 *
 * <p>A crew is not helpless in front of a slope. Where the ground stands over the
 * floor line the apron cut takes it off, and where it falls short the doorstep
 * course packs it back up — so the real question is not "is this ground level" but
 * "can this ground be MADE level, by the two things the placer does". Both have a
 * reach, and the reach is the whole of the rule: {@link #CUT_REACH} courses off,
 * {@link #FILL_REACH} courses on. Ground outside that band on every side is ground
 * the auditor will condemn, because nothing in the placement pass can reach it.
 *
 * <h2>Why it lives in the simulation</h2>
 *
 * <p>Because the siting is in the simulation and the auditor is in the platform,
 * and the two were free to disagree for exactly as long as the geometry was
 * written down twice. It is pure arithmetic over a height field — no blocks, no
 * level — so there is nothing platform-shaped about it, and having one copy is the
 * only thing that makes "the seeded siting applies the same test the auditor
 * applies" a fact rather than an intention.
 */
public final class Grade {

    private Grade() {
    }

    /**
     * How far above a floor the apron cut can take the ground away.
     *
     * <p>Mirrors {@code BlueprintPlacer.APRON_HEADROOM}, which is the headroom
     * cleared over the doorstep ring — enough to walk the whole way round. Ground
     * standing higher than this is left exactly where it is, and the auditor then
     * reports the building as buried under it.
     *
     * <p><strong>There used to be a second number.</strong> {@code Grade} also
     * declared a {@code SHELF_TOLERANCE} of one, documented as mirroring
     * {@code TownAuditor.SHELF_TOLERANCE}, and read by nothing at all; the
     * auditor's own copy was read and was one, over the same ring this is
     * measured on. So the siting accepted ground three courses proud and the
     * auditor called the building that went up on it buried, and both were
     * behaving exactly as written. The playtest report is the shape of that
     * disagreement: "buried — the ground stands up to <strong>3</strong> above
     * its floor on every side", three being this number and not a coincidence.
     *
     * <p>Three is the right number and one was wrong, and the placer settles it.
     * {@code BlueprintPlacer.finish} cuts the apron ring — the columns at
     * {@code rx + APRON_MARGIN}, which with {@code BuildingSizes.APRON} at one is
     * exactly the ring one step outside the walls that the auditor measures — for
     * {@code APRON_HEADROOM} courses above the floor line. The crew really does
     * take three courses off that exact ring. An auditor that tolerated one was
     * condemning buildings its own colleagues had already levelled.
     */
    public static final int CUT_REACH = 3;

    /**
     * How far below a floor the underpinning can pack the ground back up.
     *
     * <p>Mirrors {@code BlueprintPlacer.FOUNDATION_DEPTH}. A real cost rather
     * than free leveling — each course is masonry somebody lays and stone the
     * town pays for — which is what keeps "build it up" from being the cheap
     * answer to every slope, and what bounds it here.
     *
     * <p>Measured on the same ring as {@link #CUT_REACH} and by the same pass:
     * {@code BlueprintPlacer.foundation} lays a doorstep on every apron column,
     * dropping up to {@code FOUNDATION_DEPTH} to find something to stand it on
     * and laying nothing at all if the ground is further down than that. Cut
     * above, fill below, one block out — the placer's own words for it — and this
     * is the "below" half of that sentence.
     */
    public static final int FILL_REACH = 3;

    /** The share of a plot's columns taken as its low ground, not its lowest point. */
    public static final int LOW_GROUND_QUANTILE = 5;

    /** How coarsely a plot's own columns are sampled when choosing its floor. */
    private static final int FLOOR_SAMPLE_STEP = 2;

    /** How coarsely the ring outside the walls is sampled. Matches the auditor's. */
    private static final int RING_STEP = 2;

    /**
     * Where a structure's floor course sits, given the first free block in each
     * of its columns.
     *
     * <p>The placer's own rule, moved here so it can be asked without a world.
     * {@code BlueprintPlacer.baseAcross} delegates to it, and so does the
     * unwatched placement path, which used to take the floor from the origin
     * column alone — a single column in a rabbit hole set the floor for the whole
     * building and the plot stood three courses over it on every side.
     *
     * <p>The median rather than the lowest, then held down to what the
     * underpinning can reach. Both halves are load-bearing and both are argued in
     * {@code BlueprintPlacer.baseAcross}; this is the same three lines.
     *
     * @param firstAir the y of the first free block in each column of the plot
     * @param field    a crop field, whose ground layer is drawn one BELOW its
     *                 base, so its base belongs at the first air block
     */
    public static int floorAcross(int[] firstAir, boolean field) {
        if (firstAir.length == 0) {
            return 0;
        }
        int[] sorted = firstAir.clone();
        Arrays.sort(sorted);
        int low = sorted[Math.min(sorted.length - 1, sorted.length / LOW_GROUND_QUANTILE)];
        int median = sorted[sorted.length / 2];
        return Math.min(field ? median : median - 1, low + FILL_REACH);
    }

    /** What the auditor will say about the shelf of ground around a building. */
    public enum Shelf {
        /** At least one side can be made level with the floor. A way in. */
        LEVEL,
        /** The ground stands over the floor on every side, beyond the cut's reach. */
        BURIED,
        /** The floor hangs over the ground on every side, beyond the fill's reach. */
        PERCHED
    }

    /**
     * The verdict on a plot, judged exactly as the auditor judges the building
     * that will stand on it.
     *
     * <p>One rule, and it is the one the three separate fault messages come down
     * to: <strong>a building needs one side whose ground can be brought to its
     * floor.</strong> Buried is every side too high for the cut; perched is every
     * side too low for the fill; "no way in — outside the gap is air" is the same
     * fact read at the doorway rather than round the whole ring, because the
     * doorstep course is laid on the same ring and reaches the same distance.
     *
     * <p>Partial banking is deliberately fine, and that is the auditor's own rule:
     * a hillside build is banked uphill by nature and one open side is all an
     * entrance needs.
     *
     * @param ground where the columns are read from
     * @param plot   the plot's middle; its own y is ignored, the ground is asked
     * @param span   the plot's width, walls plus the doorstep ring
     * @param field  whether this is a crop field; see {@link #floorAcross}
     */
    public static Shelf shelf(WorldBridge ground, SimPos plot, int span, boolean field) {
        return shelf(ground, plot, span, floorFor(ground, plot, span, field));
    }

    /**
     * The same, against a floor somebody else chose.
     *
     * <p>For asking what the auditor would say about a building whose floor was
     * picked by a rule other than {@link #floorFor} — which is exactly the fault
     * in the report. The crew survey a whole plot and take its median; the
     * unwatched placement pass took the origin column and nothing else, so a
     * building whose middle happened to sit in a dip had its floor set in that dip
     * and the plot stood three courses over it on every side. "Buried — the ground
     * stands up to 3 above its floor on every side" is that sentence in the
     * auditor's words.
     */
    public static Shelf shelf(WorldBridge ground, SimPos plot, int span, int floor) {
        int wallHalf = Math.max(1, span / 2 - BuildingSizes.APRON);
        return around(column(ground, plot.y()), plot, wallHalf, wallHalf, floor).shelf();
    }

    /** How far off the floor the ring is: the verdict, and the worst of it. */
    public record Reading(Shelf shelf, int worstAbove, int worstBelow) {
    }

    /**
     * A height field somebody can read one column off, however they hold it.
     *
     * <p>The siting holds a {@code WorldBridge} and the auditor holds a level; the
     * rule below needs neither, only the first free block over a column. Narrowing
     * it to that is what lets one piece of arithmetic serve both, which is the
     * whole reason this class exists — see the note at the top about geometry
     * written down twice.
     */
    @FunctionalInterface
    public interface Columns {

        /** The y of the first free block here, or {@link #UNREAD}. */
        int firstFreeAt(int x, int z);
    }

    /** What a column nobody has loaded reads as. Not a fault; not knowing. */
    public static final int UNREAD = Integer.MIN_VALUE;

    /**
     * A bridge read one column at a time, at the height the caller was working at.
     *
     * <p>The {@code y} is not decoration and leaving it at nought was a live fault
     * for one afternoon: a {@code WorldBridge} is free to answer from the position
     * it is handed — the test fixtures that model flat ground answer
     * {@code pos.y()} outright — so a column asked at nought comes back at nought
     * and every plot on such a world reads as perched over a void.
     */
    private static Columns column(WorldBridge ground, int y) {
        return (x, z) -> ground.groundHeight(new SimPos(x, y, z));
    }

    /**
     * The rule itself, over the rectangle one step outside a building's walls.
     *
     * <p><strong>Both readers come through here.</strong> {@link #shelf} above is
     * the siting asking before it builds, with a square plot and a floor it works
     * out from {@link #floorFor}; {@code TownAuditor.checkShelf} is the audit
     * asking afterwards, with the footprint the placer actually reported and the
     * floor it actually wrote. They used to be two copies of this arithmetic with
     * two different tolerances — three courses here and one there — so a plot the
     * siting had just accepted was a building the auditor called buried on the
     * same ground the same afternoon. One copy now, and the tolerances are
     * {@link #CUT_REACH} and {@link #FILL_REACH} because those are what the crew
     * can reach.
     *
     * <p>Rectangular rather than square, which is the auditor's geometry and not
     * the siting's: a building is thirteen by eleven as often as not, and a square
     * ring round a rectangular building reads the ground at the corners of a box
     * nobody built.
     *
     * <p>Partial banking is deliberately fine, and that is the auditor's own rule:
     * a hillside build is banked uphill by nature and one open side is all an
     * entrance needs.
     */
    public static Reading around(Columns ground, SimPos plot, int wallHalfW,
                                 int wallHalfD, int floor) {
        int banked = 0;
        int hanging = 0;
        int samples = 0;
        int worstAbove = 0;
        int worstBelow = 0;
        for (SimPos at : ring(plot, wallHalfW + 1, wallHalfD + 1)) {
            int free = ground.firstFreeAt(at.x(), at.z());
            if (free == UNREAD) {
                continue;
            }
            int grade = free - 1;
            samples++;
            if (grade > floor + CUT_REACH) {
                banked++;
                worstAbove = Math.max(worstAbove, grade - floor);
            } else if (grade < floor - FILL_REACH) {
                hanging++;
                worstBelow = Math.max(worstBelow, floor - grade);
            }
        }
        if (samples == 0) {
            // Nothing known; not knowing is not a fault.
            return new Reading(Shelf.LEVEL, 0, 0);
        }
        if (banked == samples) {
            return new Reading(Shelf.BURIED, worstAbove, worstBelow);
        }
        if (hanging == samples) {
            return new Reading(Shelf.PERCHED, worstAbove, worstBelow);
        }
        return new Reading(Shelf.LEVEL, worstAbove, worstBelow);
    }

    /** Whether a building on this plot would have somewhere to open its door. */
    public static boolean canBeMadeLevel(WorldBridge ground, SimPos plot, int span,
                                         boolean field) {
        return shelf(ground, plot, span, field) == Shelf.LEVEL;
    }

    /**
     * Everything the ground has to say about a plot, as one number.
     *
     * <p>Two questions, and until this existed only ever one of them was asked
     * outside the two siting loops. {@code WorldBridge.siteFault} measures how far
     * the ground falls across the bulk of a plot; {@link #shelf} asks whether the
     * ring one step outside the walls can be brought to the floor. A plot on a
     * terrace passes the first — it is flat — and fails the second, because the
     * terrace above it stands seven courses proud on every side.
     *
     * <p><strong>Why it had to be one number.</strong> {@code Settlement.chooseSite}
     * and {@code Founding.roomFor} both ask both questions and both rank the answer
     * this same way. Every <em>other</em> path that judges ground — the two
     * relocations, the plan walk they share, and the test of whether a relocation
     * may take a plot at all — asked {@code siteFault} alone. That is the whole of
     * the second playtest's buried town: the seeded plan is laid in chunks nobody
     * has loaded, where the bridge answers from the generator's estimate and is
     * wrong by courses, and the one moment the real ground is finally readable —
     * {@code Settlement.relocatePending}, called from {@code materializePending} a
     * line before the blueprint is drawn — asked the question that terraces pass.
     * So the shelf rule was applied to fiction and never re-applied to the world,
     * and eleven of sixteen buildings went up in pits.
     *
     * <p>Ranked rather than refused outright, in {@code chooseSite}'s own idiom: a
     * town with nothing but terraces to build on must still build, and the ranking
     * is what decides which terrace. Anything the shelf condemns scores worse than
     * every gradable slope, because a slope is ground the crew can cut into and a
     * pit is ground the auditor will condemn however gently it falls.
     *
     * @param span  the plot's width, walls plus the doorstep ring
     * @param field whether this is a crop field; see {@link #floorAcross}
     */
    public static int groundFault(WorldBridge ground, SimPos plot, int span,
                                  boolean field) {
        int fault = ground.siteFault(plot, BuildPlanner.PLOT_PROBE_RADIUS);
        if (fault == WorldBridge.SITE_FAULT_OPEN_WATER) {
            return fault;   // nothing ranks worse, and the shelf of a lake is moot
        }
        Shelf shelf = shelf(ground, plot, span, field);
        if (shelf != Shelf.LEVEL) {
            fault = Math.max(fault, BuildPlanner.LEVELABLE_FALL + shelf.ordinal());
        }
        return fault;
    }

    /** Whether the ground will take this plot on both counts. See {@link #groundFault}. */
    public static boolean willTake(WorldBridge ground, SimPos plot, int span,
                                   boolean field) {
        return groundFault(ground, plot, span, field) == WorldBridge.SITE_FAULT_NONE;
    }

    /**
     * The floor a building on this plot will be given, read off the ground.
     *
     * <p>The plot's own columns, sampled as the survey samples them, put through
     * {@link #floorAcross}. This is the number the auditor will compare the
     * surrounding ground against, because it is the number the placer will write
     * into the footprint.
     */
    public static int floorFor(WorldBridge ground, SimPos plot, int span, boolean field) {
        int half = Math.max(1, span / 2 - BuildingSizes.APRON);
        List<Integer> columns = new ArrayList<>();
        for (int dx = -half; dx <= half; dx += FLOOR_SAMPLE_STEP) {
            for (int dz = -half; dz <= half; dz += FLOOR_SAMPLE_STEP) {
                columns.add(ground.groundHeight(new SimPos(plot.x() + dx, plot.y(),
                        plot.z() + dz)));
            }
        }
        int[] firstAir = new int[columns.size()];
        for (int i = 0; i < firstAir.length; i++) {
            firstAir[i] = columns.get(i);
        }
        return floorAcross(firstAir, field);
    }

    /** Whether this blueprint is a crop field, whose base sits a course lower. */
    public static boolean isField(String blueprintId) {
        return BuildingRole.of(blueprintId) == BuildingRole.CROP_FARM;
    }

    /**
     * The rectangle of columns at these half-extents around a plot's middle.
     *
     * <p>The same walk {@code TownAuditor.ring} makes, at the same
     * {@link #RING_STEP}: the two long sides whole, then the two short sides with
     * the corners left off so no column is read twice.
     */
    private static List<SimPos> ring(SimPos plot, int halfW, int halfD) {
        List<SimPos> spots = new ArrayList<>();
        for (int dx = -halfW; dx <= halfW; dx += RING_STEP) {
            spots.add(new SimPos(plot.x() + dx, plot.y(), plot.z() - halfD));
            spots.add(new SimPos(plot.x() + dx, plot.y(), plot.z() + halfD));
        }
        for (int dz = -halfD + 1; dz < halfD; dz += RING_STEP) {
            spots.add(new SimPos(plot.x() - halfW, plot.y(), plot.z() + dz));
            spots.add(new SimPos(plot.x() + halfW, plot.y(), plot.z() + dz));
        }
        return spots;
    }
}
