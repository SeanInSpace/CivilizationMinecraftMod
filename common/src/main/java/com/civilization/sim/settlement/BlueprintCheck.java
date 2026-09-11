package com.civilization.sim.settlement;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a hand-authored building can stand in for the drawn one.
 *
 * <p>An authored file is not a picture. It is a replacement for a drawing that
 * the rest of the mod has a great many standing assumptions about: the plan
 * reserved exactly so much ground for it, the catalog promised it sleeps three
 * people, a worker will walk to the middle of the south wall expecting a door,
 * and a farm's whole food supply is a count of wheat blocks. A file that
 * disagrees with any of those does not look wrong — it looks fine, and the town
 * quietly stops working. The cottage grows through its neighbor, three settlers
 * stand all night in front of a wall where their beds should be, and the farm
 * that fed forty people feeds none.
 *
 * <p>So an authored file is checked against the same tables the drawing is held
 * to, and the findings are said out loud rather than discovered from the air six
 * sessions later. This is the same job the {@code SIZE MISMATCH} line in the
 * placer does for a drawn building, extended to everything else the drawing
 * promised.
 *
 * <p>Deliberately here rather than in the platform layer. Every rule below is
 * arithmetic over {@link BuildingSizes}, {@link Beds} and {@link Field} — the
 * three tables that are the contract — and none of it needs a world. What the
 * platform does is the other half: it reads a file and counts what is in it,
 * which is what a {@link Survey} is.
 */
public final class BlueprintCheck {

    private BlueprintCheck() {
    }

    /**
     * The lowest course a block may hang past the walls on: three.
     *
     * <p>Above the doorway, which is the whole reason for the number. A block
     * one step outside the footprint high up is an eave, and eaves are what stop
     * a roof reading as a lid; the same block at floor level is a wall standing
     * on the ground the plan gave to the building next door, and the plan has no
     * idea it is there. Courses nought to two are where a person walks, so they
     * are where the footprint is the truth.
     */
    public static final int OVERHANG_MIN_COURSE = 3;

    /** How far past its own footprint a building may reach at all: one block. */
    public static final int OVERHANG_REACH = 1;

    /** How badly wrong a finding is. */
    public enum Severity {

        /** The file must not be used. Placing it would break something visible. */
        REFUSED,

        /** It will work, and somebody meant something else. */
        WARNING,

        /** Worth saying, and not a fault. */
        NOTE;

        public boolean isFault() {
            return this == REFUSED;
        }
    }

    /** One thing the check has to say about a file. */
    public record Finding(Severity severity, String message) {

        @Override
        public String toString() {
            return severity + ": " + message;
        }
    }

    /**
     * What somebody who can read a blueprint counted in it.
     *
     * <p>Every field is a count or a flag rather than a position, on purpose:
     * the rules below are about <em>how many</em> and <em>whether</em>, and a
     * survey that carried block positions would tempt this class into opinions
     * about block states, which is the platform's business and not the
     * simulation's.
     *
     * @param blueprintId      what the file is meant to be, e.g.
     *                         {@code civilization:norman/cottage}
     * @param width            span across, in blocks
     * @param depth            span back
     * @param height           courses, floor to the top of the roof
     * @param beds             bed blocks found, counted once per bed rather than
     *                         once per half
     * @param post             whether the author put a building post in it
     * @param door             whether there is a way through the front wall at
     *                         head height
     * @param crops            crop blocks found
     * @param strayCells       cells standing more than {@link #OVERHANG_REACH}
     *                         outside the footprint, at any height
     * @param lowOverhangCells cells one block outside the footprint, below
     *                         {@link #OVERHANG_MIN_COURSE}
     * @param overhangCells    cells one block outside it at or above that course
     */
    public record Survey(String blueprintId, int width, int depth, int height,
                         int beds, boolean post, boolean door, int crops,
                         int strayCells, int lowOverhangCells, int overhangCells) {
    }

    /**
     * Everything wrong with this file, worst first, or an empty list.
     *
     * <p>Never throws and never stops at the first fault. Somebody who has just
     * spent an hour building a cottage wants the whole list, not the first line
     * of it and another hour of guessing.
     */
    public static List<Finding> of(Survey survey) {
        List<Finding> found = new ArrayList<>();
        String id = survey.blueprintId();

        checkSpans(found, survey);
        checkBeds(found, survey, id);
        checkDoorAndPost(found, survey);
        checkCrops(found, survey, id);
        checkFootprint(found, survey);

        found.sort((a, b) -> a.severity().compareTo(b.severity()));
        return List.copyOf(found);
    }

    /** Whether anything here forbids using the file. */
    public static boolean passes(List<Finding> findings) {
        for (Finding finding : findings) {
            if (finding.severity().isFault()) {
                return false;
            }
        }
        return true;
    }

    // --- the rules -----------------------------------------------------------

    /**
     * The size, against the table that reserved the ground.
     *
     * <p>Three separate things, and only the middle one is the famous mismatch.
     * Odd spans come first because a building is placed about a point, so an
     * even one has no middle to place it about and moves half a block every
     * quarter turn. The plot span comes last because a kind absent from
     * {@link BuildingSizes} still has a plot reserved for it, and a file may
     * outgrow that without outgrowing anything else.
     */
    private static void checkSpans(List<Finding> found, Survey survey) {
        if (survey.width() <= 0 || survey.depth() <= 0 || survey.height() <= 0) {
            found.add(new Finding(Severity.REFUSED, "the file has no volume: "
                    + survey.width() + "x" + survey.height() + "x" + survey.depth()));
            return;
        }
        if (survey.width() % 2 == 0 || survey.depth() % 2 == 0) {
            found.add(new Finding(Severity.REFUSED,
                    "both spans must be odd — a building is placed about its middle cell, and "
                            + survey.width() + "x" + survey.depth()
                            + " has none. Add or take a row."));
        }

        BuildingSizes.Size declared = BuildingSizes.of(survey.blueprintId());
        if (declared == null) {
            found.add(new Finding(Severity.NOTE, "nothing draws a "
                    + BuildingRole.bareName(survey.blueprintId())
                    + ", so there is no declared size to check this against"));
        } else if (survey.width() > declared.width() || survey.depth() > declared.depth()) {
            found.add(new Finding(Severity.REFUSED, "SIZE MISMATCH: "
                    + BuildingRole.bareName(survey.blueprintId()) + " is declared "
                    + declared.width() + "x" + declared.depth() + " and this file is "
                    + survey.width() + "x" + survey.depth()
                    + ". The plan reserves ground for the declared size, so this one would"
                    + " be built through whatever is next door."));
        }

        int span = Math.max(survey.width(), survey.depth()) + 2 * BuildingSizes.APRON;
        int reserved = BuildingSizes.plotSpanOf(survey.blueprintId());
        if (span > reserved) {
            found.add(new Finding(Severity.REFUSED, "this file plus its doorstep needs a plot "
                    + span + " across and the plan reserves " + reserved));
        }
    }

    /**
     * The beds, against what the catalog promised the building sleeps.
     *
     * <p>Exact rather than "at least". Too few and somebody stands in the dark
     * all night; too many and the extra ones are furniture nobody is ever sent
     * to, because how many a home holds is a number the catalog decided long
     * before this file existed.
     */
    private static void checkBeds(List<Finding> found, Survey survey, String id) {
        int wanted = Beds.countIn(id);
        if (wanted == 0) {
            if (survey.beds() > 0) {
                found.add(new Finding(Severity.WARNING, "a "
                        + BuildingRole.bareName(id) + " is not a home, so the "
                        + survey.beds() + " bed(s) in this file are furniture:"
                        + " nobody will ever be sent to sleep in them"));
            }
            return;
        }
        if (survey.beds() != wanted) {
            found.add(new Finding(Severity.REFUSED, "a "
                    + BuildingRole.bareName(id) + " sleeps " + wanted
                    + " and this file has " + survey.beds() + " bed(s)."
                    + " The town houses people by the catalog's number, so the difference"
                    + " is settlers with nowhere to lie down."));
        }
    }

    private static void checkDoorAndPost(List<Finding> found, Survey survey) {
        if (!survey.door()) {
            found.add(new Finding(Severity.REFUSED,
                    "no way through the front wall at head height. Workers walk to the"
                            + " doorstep on the side the building faces and expect to get in."));
        }
        if (!survey.post()) {
            found.add(new Finding(Severity.NOTE,
                    "no building post in the file; one will be put in the first free cell"
                            + " above the floor. Place your own if you want it somewhere"
                            + " particular."));
        }
    }

    /**
     * The crops, for a field.
     *
     * <p>A farm is not a building with a job attached; it is a countable number
     * of wheat blocks, and that count is what an unwatched town eats. A field
     * with none in it is a farm that starves its town while looking perfectly
     * well built, which is the worst kind of fault there is.
     */
    private static void checkCrops(List<Finding> found, Survey survey, String id) {
        if (!Field.isField(id)) {
            return;
        }
        if (survey.crops() <= 0) {
            found.add(new Finding(Severity.REFUSED,
                    "a farm with no crops in it. The town's whole food supply is a count of"
                            + " crop blocks, so this field would feed nobody."));
            return;
        }
        if (survey.crops() < Field.CROP_BLOCKS) {
            found.add(new Finding(Severity.WARNING, "this field holds " + survey.crops()
                    + " crops against the drawn farm's " + Field.CROP_BLOCKS
                    + ", so it feeds about "
                    + (100 * survey.crops() / Field.CROP_BLOCKS)
                    + "% as many people. The town will use the real count."));
        }
    }

    private static void checkFootprint(List<Finding> found, Survey survey) {
        if (survey.strayCells() > 0) {
            found.add(new Finding(Severity.REFUSED, survey.strayCells()
                    + " block(s) stand more than " + OVERHANG_REACH
                    + " outside the footprint. Ground past the footprint belongs to"
                    + " whatever the plan puts there next."));
        }
        if (survey.lowOverhangCells() > 0) {
            found.add(new Finding(Severity.REFUSED, survey.lowOverhangCells()
                    + " block(s) stand outside the footprint below course "
                    + OVERHANG_MIN_COURSE
                    + ". That is a wall on the doorstep, not an eave."));
        }
        if (survey.overhangCells() > 0) {
            found.add(new Finding(Severity.NOTE, survey.overhangCells()
                    + " block(s) of eave hang one past the walls, which is allowed"));
        }
    }
}
