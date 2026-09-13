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
     * Courses of slack the shelf check allows before it calls a building buried.
     *
     * <p>Mirrors {@code TownAuditor.SHELF_TOLERANCE}. One: a single course of
     * ground standing proud of a doorstep is a step up, not a pit.
     */
    public static final int SHELF_TOLERANCE = 1;

    /**
     * How far above a floor the apron cut can take the ground away.
     *
     * <p>Mirrors {@code BlueprintPlacer.APRON_HEADROOM}, which is the headroom
     * cleared over the doorstep ring — enough to walk the whole way round. Ground
     * standing higher than this is left exactly where it is, and the auditor then
     * reports the building as buried under it.
     */
    public static final int CUT_REACH = 3;

    /**
     * How far below a floor the underpinning can pack the ground back up.
     *
     * <p>Mirrors {@code BlueprintPlacer.FOUNDATION_DEPTH}. A real cost rather
     * than free leveling — each course is masonry somebody lays and stone the
     * town pays for — which is what keeps "build it up" from being the cheap
     * answer to every slope, and what bounds it here.
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
        int banked = 0;
        int hanging = 0;
        int samples = 0;
        for (SimPos at : ring(plot, wallHalf + 1)) {
            int grade = ground.groundHeight(at) - 1;
            samples++;
            if (grade > floor + CUT_REACH) {
                banked++;
            } else if (grade < floor - FILL_REACH) {
                hanging++;
            }
        }
        if (samples == 0) {
            return Shelf.LEVEL;   // nothing known; not knowing is not a fault
        }
        if (banked == samples) {
            return Shelf.BURIED;
        }
        return hanging == samples ? Shelf.PERCHED : Shelf.LEVEL;
    }

    /** Whether a building on this plot would have somewhere to open its door. */
    public static boolean canBeMadeLevel(WorldBridge ground, SimPos plot, int span,
                                         boolean field) {
        return shelf(ground, plot, span, field) == Shelf.LEVEL;
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

    /** The rectangle of columns at this half-extent around a plot's middle. */
    private static List<SimPos> ring(SimPos plot, int half) {
        List<SimPos> spots = new ArrayList<>();
        for (int dx = -half; dx <= half; dx += RING_STEP) {
            spots.add(new SimPos(plot.x() + dx, plot.y(), plot.z() - half));
            spots.add(new SimPos(plot.x() + dx, plot.y(), plot.z() + half));
        }
        for (int dz = -half + 1; dz < half; dz += RING_STEP) {
            spots.add(new SimPos(plot.x() - half, plot.y(), plot.z() + dz));
            spots.add(new SimPos(plot.x() + half, plot.y(), plot.z() + dz));
        }
        return spots;
    }
}
