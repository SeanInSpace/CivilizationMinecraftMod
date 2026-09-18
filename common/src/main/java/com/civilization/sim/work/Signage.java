package com.civilization.sim.work;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.person.NightRest;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a town writes on its own boards.
 *
 * <p>Every sign the dressing put up was blank, and {@code FurnishingLayer}
 * said so in as many words: writing the town's name on a board meant a sign
 * block entity and a text payload, "a thing this seam does not carry". The
 * seam carries it now, and this is the half that decides <em>what</em> —
 * arithmetic over a name, a stage and a birthday, with nothing in it that
 * needs a world.
 *
 * <p><strong>Derived, never saved.</strong> The dressing is worked out from
 * the buildings standing and the streets opened; the save carries a count of
 * pieces raised and nothing else. Sign text follows the same rule, and has to:
 * a town that grew from a village into a town between two sessions has to have
 * the new word on its board, and a board whose text was written down once would
 * still say village. So the lines below are composed fresh every time a piece
 * is planned, and {@code FurnishingLayer} rewrites a board whose text has
 * drifted on the repair sweep exactly as it puts back a fence post somebody
 * broke.
 *
 * <p><strong>Fifteen characters.</strong> A vanilla sign is ninety pixels
 * across ({@code SignBlockEntity.MAX_TEXT_LINE_WIDTH}) and the default font is
 * six pixels to the glyph, so fifteen is what fits on a line in 26.2 — read off
 * the class rather than remembered. Four lines ({@code SignText.LINES}).
 * Everything here is cut to that, because a line that overflows is not
 * truncated by the game, it is drawn narrower and squeezed, and a board that
 * reads as squeezed reads as a bug.
 */
public final class Signage {

    private Signage() {
    }

    /** Characters that fit on one line of a vanilla board: fifteen. */
    public static final int LINE_WIDTH = 15;

    /** Lines on a board: four, which is what {@code SignText.LINES} says. */
    public static final int LINES = 4;

    /**
     * No board at all: what a piece that is not a sign carries, and what a sign
     * carries when nobody handed it a town.
     *
     * <p>Empty rather than four empty strings, and the difference matters. A
     * course with no lines on it is one {@code FurnishingLayer.inscribe} leaves
     * entirely alone; a course with four blank lines on it is an instruction to
     * scrub whatever is written there. The second would have every size test —
     * which draws a plan with no settlement behind it — quietly telling the
     * world to wipe a board.
     */
    public static final List<String> BLANK = List.of();

    // --- what a town has to say about itself ---------------------------------

    /**
     * Everything the town's boards are written from, read off it once.
     *
     * <p>Gathered into a record rather than passed as four arguments so that
     * {@code FurnishingLayer.plan} — which is pure and level-free and is going
     * to stay that way — takes one more thing rather than four, and so that the
     * composing below can be exercised on a plaque a test wrote by hand without
     * a settlement anywhere near it.
     *
     * @param townName   what the town calls itself
     * @param stageWord  camp, homestead, fortified, village or town
     * @param foundedDay which day of the world it first drew breath, or
     *                   {@link #NEVER} for a town that has not lived a step
     * @param innName    what this people call the inn, chosen for this town
     */
    public record Plaque(String townName, String stageWord, long foundedDay,
                         String innName) {

        public Plaque {
            Objects.requireNonNull(townName, "townName");
            Objects.requireNonNull(stageWord, "stageWord");
            Objects.requireNonNull(innName, "innName");
        }

        /**
         * A plaque with nothing on it.
         *
         * <p>What {@code FurnishingLayer.plan} uses when nobody handed it a
         * town — the size tests, which check what a piece is <em>made</em> of
         * across every culture and have no settlement to ask. A blank plaque
         * writes no lines at all rather than writing empty ones, so those tests
         * go on seeing exactly the blocks they always saw.
         */
        public static final Plaque NONE = new Plaque("", "", NEVER, "");

        /** Whether there is anything here worth writing. */
        public boolean isBlank() {
            return townName.isEmpty();
        }
    }

    /** The founding day of a town that has not taken a step yet. */
    public static final long NEVER = -1L;

    /**
     * Everything one settlement's boards say, read off it.
     *
     * <p>The one place a {@link Settlement} is touched. Everything below takes
     * a plaque.
     *
     * @param simIntervalTicks game ticks between simulation steps, which is what
     *                         turns the town's birthday from a step number into
     *                         a day — see {@link #foundedDay}
     */
    public static Plaque of(Settlement settlement, int simIntervalTicks) {
        if (settlement == null) {
            return Plaque.NONE;
        }
        SettlementStage stage = settlement.stage();
        return new Plaque(settlement.name(),
                stage == null ? "" : stage.pretty(),
                foundedDay(settlement.firstStep(), simIntervalTicks),
                innNameFor(Culture.of(settlement.cultureId()), settlement));
    }

    /**
     * Which day of the world a town founded on this step was founded on.
     *
     * <p>The birthday is kept as a step number ({@code Settlement.firstStep})
     * because that is the only clock the simulation has, and a step number is
     * not a thing to write on a board: "founded step 1440" means nothing to
     * anybody standing in front of it. A step is {@code simIntervalTicks} game
     * ticks and a day is {@link NightRest#DAY} of them, so the conversion is
     * exact and is done here rather than in the drawing — which keeps it
     * testable and keeps the one place that knows the interval to one.
     *
     * <p>Day one, not day zero. A town raised before the first tick — the nine
     * around world spawn are — has {@code firstStep} of zero, and "founded day
     * 0" reads as a town that does not know when it was founded.
     */
    public static long foundedDay(long firstStep, int simIntervalTicks) {
        if (firstStep < 0 || simIntervalTicks <= 0) {
            return NEVER;
        }
        long stepsPerDay = Math.max(1L, NightRest.DAY / simIntervalTicks);
        return firstStep / stepsPerDay + 1;
    }

    /**
     * What this town's inn is called.
     *
     * <p>Drawn from the people's own pool by the settlement's own id, so the
     * board says the same thing on every reload and in every test without
     * anything being written down — the argument {@code Leisure.restFor} makes
     * about a pastime, applied to a name that has to outlast one: a sign is not
     * a save format either, and an inn that was the Red Lion this session and
     * the Black Swan the next is not an inn anybody would drink at twice.
     *
     * <p>The id is avalanched rather than read raw for {@code Culture.spread}'s
     * reason: ids allotted in sequence share their high bits, and a town's
     * neighbours would all drink at the same sign.
     */
    public static String innNameFor(Culture culture, Settlement settlement) {
        if (settlement == null) {
            return "";
        }
        return innNameFor(culture, settlement.id().value().getMostSignificantBits()
                ^ settlement.id().value().getLeastSignificantBits() * 31L);
    }

    /** The same, from a seed a test can write down. */
    public static String innNameFor(Culture culture, long seed) {
        List<String> pool = (culture == null ? Culture.DEFAULT : culture).innNames();
        if (pool.isEmpty()) {
            return "";
        }
        return pool.get((int) Math.floorMod(mix(seed), pool.size()));
    }

    // --- the boards ----------------------------------------------------------

    /**
     * The notice board on its post in the paved square: who this is, what it
     * has grown into, and when it began.
     *
     * <p>The three facts a stranger walking into a town wants and cannot get
     * without opening a screen. The stage is on it deliberately — a settlement
     * is a thing that becomes another thing, and "Ashmarch / village" tells a
     * player that the hall is not built yet and why.
     */
    public static List<String> board(Plaque plaque) {
        if (plaque == null || plaque.isBlank()) {
            return BLANK;
        }
        List<String> lines = new ArrayList<>();
        lines.add(fit(plaque.townName()));
        lines.add(fit(plaque.stageWord()));
        lines.add(foundedLine(plaque.foundedDay()));
        return pad(lines);
    }

    /**
     * "founded day 12", or "day 4120" once the number stops fitting beside the
     * word, or nothing at all for a town that has not lived a step.
     *
     * <p>The fallback is not fussiness. A world left running has towns founded
     * in five figures, and "founded day 41203" is nineteen characters — which
     * the game does not truncate, it squeezes, so the whole line goes illegible
     * rather than the tail of it going missing.
     */
    public static String foundedLine(long day) {
        if (day < 0) {
            return "";
        }
        String full = "founded day " + day;
        if (full.length() <= LINE_WIDTH) {
            return full;
        }
        return fit("day " + day);
    }

    /**
     * A post at a crossing: whose town this is and which way its middle lies.
     *
     * <p>The middle and not the gate, and that is the piece's own choice rather
     * than this one's — {@code Furnishings.theSignposts} faces every post
     * toward {@code settlement.center()}, because "which way is the hall" is
     * the question somebody standing at a crossroads is actually asking. So the
     * arrow points along the board's own face and the compass word says the
     * same thing for anybody who has walked round it.
     *
     * @param facing the piece's facing, in {@code Building.facing}'s convention:
     *               0 the middle lies toward +z, 1 toward -x, 2 toward -z, 3
     *               toward +x
     */
    public static List<String> signpost(Plaque plaque, int facing) {
        if (plaque == null || plaque.isBlank()) {
            return BLANK;
        }
        List<String> lines = new ArrayList<>();
        lines.add(fit(plaque.townName()));
        lines.add("^ to the hall");
        lines.add(fit(compassWord(facing)));
        return pad(lines);
    }

    /**
     * The board outside the inn: what it is called, what it is, and where.
     *
     * <p>The town's name is on it because an inn board is the one sign in a
     * settlement a traveller reads from outside it — a post on the road that
     * says "The Red Lion / inn" and nothing else has told them nothing they
     * could not see through the door.
     */
    public static List<String> innBoard(Plaque plaque) {
        if (plaque == null || plaque.isBlank() || plaque.innName().isEmpty()) {
            return BLANK;
        }
        List<String> lines = new ArrayList<>();
        lines.add(fit("The " + plaque.innName()));
        lines.add("inn");
        lines.add(fit(plaque.townName()));
        return pad(lines);
    }

    /**
     * The board at the foot of a grave: who lies here, what they did, and when.
     *
     * <p>{@code Settlement.Grave.epitaph} is the same sentence — "Ada Baker,
     * Farmer" — and it is one line where a board has four. Seventeen characters
     * do not fit in {@link #LINE_WIDTH}, and a line that overflows is squeezed
     * rather than cut, so the epitaph is set across the board instead of onto it:
     * the name, then the trade, then the day. A reader gets the same sentence; a
     * board gets lines it can draw.
     *
     * <p>The trade line is left empty for somebody who never had one, which is
     * {@code Grave.epitaph}'s own rule said again: "Ada Baker / Idler" is not an
     * epitaph, it is an insult.
     *
     * <p>Day one, not day zero, for the reason {@link #foundedDay} gives and
     * against the same clock a player reads: the first day of a world is Day 1
     * everywhere it is shown, and a stone saying "died day 0" reads as a stone
     * that does not know.
     */
    public static List<String> headstone(Settlement.Grave whose) {
        if (whose == null) {
            return BLANK;
        }
        List<String> lines = new ArrayList<>();
        lines.add(fit(whose.name()));
        lines.add(fit(whose.profession() == com.civilization.sim.person.Profession.IDLER
                ? "" : whose.profession().pretty()));
        lines.add(whose.day() < 0 ? "" : fit("died day " + (whose.day() + 1)));
        return pad(lines);
    }

    /**
     * What one piece of the dressing has written on it, or nothing.
     *
     * <p>The one entry point the drawing uses, so that adding a piece that
     * carries text is an entry here rather than a branch in the placer.
     */
    public static List<String> linesFor(Furnishings.Piece piece, Plaque plaque,
                                        int facing) {
        return linesFor(piece, plaque, facing, null);
    }

    /**
     * The same, for a town that has buried somebody.
     *
     * <p>A grave is the one board whose words are not a fact about the town. A
     * notice board, a signpost and an inn sign all say the same thing on every
     * stone in the settlement and are composed from one {@link Plaque} read off
     * it once; a headstone says a name, and which name depends on <em>which
     * stone</em>. So the grave travels beside the plaque rather than in it, and
     * it is null for the forty-nine pieces out of fifty that are not one.
     *
     * <p>A headstone asks nothing of the plaque, deliberately: it is legible in a
     * town with no name and it is the one board a size test — which plans against
     * {@link Plaque#NONE} — can still be handed something to write.
     */
    public static List<String> linesFor(Furnishings.Piece piece, Plaque plaque,
                                        int facing, Settlement.Grave whose) {
        if (piece == null) {
            return BLANK;
        }
        if (piece == Furnishings.Piece.GRAVE) {
            return headstone(whose);
        }
        if (plaque == null || plaque.isBlank()) {
            return BLANK;
        }
        return switch (piece) {
            case SQUARE -> board(plaque);
            case SIGNPOST -> signpost(plaque, facing);
            case INN_SIGN -> innBoard(plaque);
            default -> BLANK;
        };
    }

    // --- the arithmetic of a line --------------------------------------------

    /**
     * Which way the thing a piece belongs to lies, in words.
     *
     * <p>{@code FurnishingLayer.towardTheGate}'s table, said in English instead
     * of in {@code Direction}. Here rather than there because {@code common} may
     * not name a Minecraft type and a compass word is not one.
     */
    public static String compassWord(int facing) {
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> "west";
            case 2 -> "north";
            case 3 -> "east";
            default -> "south";
        };
    }

    /** One line, cut to what a board will draw without squeezing it. */
    public static String fit(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.strip();
        return trimmed.length() <= LINE_WIDTH ? trimmed
                : trimmed.substring(0, LINE_WIDTH).strip();
    }

    /** Four lines, however many were written. */
    private static List<String> pad(List<String> lines) {
        List<String> out = new ArrayList<>(lines.subList(0,
                Math.min(lines.size(), LINES)));
        while (out.size() < LINES) {
            out.add("");
        }
        return List.copyOf(out);
    }

    /** SplitMix64's finalizer, the same one the rest of the mod picks with. */
    private static long mix(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L ^ 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
