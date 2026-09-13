package com.civilization.sim.culture;

import com.civilization.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A goblin camp: a huddle of hovels and tents round a trampled middle.
 *
 * <p>The mire goblins had the warren, which is knots of huts with open ground
 * between the knots — a good shape for a people who dig in wherever the digging
 * is good, and the wrong shape entirely for what the goblins actually are now.
 * A warren sprawls: its first knot sits fifty-two blocks out and a town of
 * thirty reaches ninety-six across. A scavenger camp is the opposite of that.
 * It is small, it is close, and every part of it is inside a shout of the fire,
 * because the whole of a camp's defense is that everybody can be at the
 * palisade before the thing at the palisade is through it.
 *
 * <h2>What makes it a camp and not a village</h2>
 *
 * <ul>
 *   <li><strong>No streets.</strong> None, and that is what it <em>is</em>
 *       rather than something not got round to — the same statement
 *       {@link Layouts#WARREN} makes. Goblins make paths, not roads: the track
 *       the road layer wears between the gate and the loot pile is the only
 *       thing in a camp that could be called a way through it, and it is worn
 *       rather than laid. So this is a plain {@link Layout} and not a
 *       {@link PlannedLayout}, which is also how it sits outside the frontage
 *       invariants — a plan with no streets has no frontage to report, and
 *       {@code LayoutTest} asks about frontage only of the arrangements that
 *       draw roads.</li>
 *   <li><strong>Tight.</strong> The plots pack against each other at the
 *       separation and no further, and the middle is a yard rather than a
 *       reservation — see {@link #RIM}, which is the one number that decides how
 *       big a camp is. Measured, on the wider axis out from the middle: a camp of
 *       six reaches <strong>13</strong> blocks, of eight <strong>14</strong>, of
 *       twelve <strong>25</strong> and a big camp of twenty <strong>28</strong>.
 *       So a camp of the size a world actually seeds — eight — is genuinely inside
 *       a twenty-block walk of its own fire. Twelve is not, and cannot be: twelve
 *       plots at an eleven-block separation do not fit in a twenty-block circle
 *       under any arrangement, because about ten is the ceiling. The warren these
 *       people used to build put its <em>first outlying knot</em> at fifty-two.</li>
 *   <li><strong>Irregular.</strong> The candidates come off a golden-angle
 *       spiral and are accepted or refused one at a time, so no two plots line
 *       up into a row or a ring and the edge of the camp is ragged. Nothing here
 *       is on a pitch, because nobody pegged it out.</li>
 *   <li><strong>Different in every camp.</strong> The spiral is turned by a hash
 *       of the camp's own center, so two goblin camps are not the same camp
 *       twice — see {@link #isSameShapeEverywhere}.</li>
 * </ul>
 *
 * <h2>The middle</h2>
 *
 * <p>Plot zero is the center itself, and the camp's first building stands on it.
 * The honest note {@link RadialStreetLayout} and {@code OrcRingLayout} both make
 * applies here too: what actually lands there is whatever the settlement raises
 * first, which for a camp is its post. That is the right thing to have in the
 * middle of a trampled yard, and the fire pit the camp cooks on — the hearth
 * every stage program raises — goes up beside it on the next plot in.
 *
 * <p>Everything after plot zero keeps clear of {@link #RIM}, so the yard stays
 * open ground rather than being built over: the camp is a ring of roofs round a
 * space, which is the one thing about its shape a player reads from the ground.
 */
public final class GoblinCampLayout implements Layout {

    /**
     * How near the middle a plot may be offered at all.
     *
     * <p>A separation, and no curve factor — which is the opposite of what rule R2
     * usually asks for and is right here for one reason: this arrangement does not
     * <em>space</em> its plots on a circle, it accepts or refuses them one at a
     * time against everything already placed. So a candidate on a diagonal at this
     * radius is refused by {@link Layout#farEnoughApart} on its own, and a
     * candidate on an axis at the same radius is legal and is taken. Rounding the
     * rim up to {@link Layout#onACurve} of the separation instead would have been
     * the layout refusing offers the overlap check would have allowed.
     *
     * <p>What it cost when it was rounded up, measured: a camp of twelve reached
     * <strong>32 blocks</strong> from its middle against 24 now, because a
     * seventeen-block hole in the middle of a cluster pushes every plot in it
     * outward. That is the whole yard, paid for twice.
     *
     * <p>So the middle is left open and it is a yard rather than a reservation:
     * four plots come in on the axes at eleven, the diagonals fill from sixteen,
     * and the fire pit standing on plot zero has a ring of trampled ground round it
     * about six blocks wide. Room for a fire, a cage and a crowd; no room at all
     * for another roof.
     */
    static final int RIM = MIN_PLOT_SEPARATION;

    /**
     * Two fifths of a turn, as the warren uses it and for the same reason.
     *
     * <p>The golden angle never repeats a bearing, so the candidates never stack
     * into spokes. Borrowed rather than re-derived: a second spelling of a
     * constant is a second thing to get wrong.
     */
    private static final double TURN = 2.399963;

    /**
     * How fast the spiral reaches outward, in blocks per root-candidate.
     *
     * <p>A golden-angle spiral at {@code radius = GROWTH * sqrt(k)} has constant
     * density, so the candidates arrive at an even rate however far out they are.
     * This number is the sampling pitch, and it is <strong>how tight the camp
     * comes out</strong> rather than a matter of taste: a candidate is accepted
     * only if it clears everything already placed, so a coarse spiral leaves holes
     * it never comes back to fill and the camp reaches outward to make up the
     * count.
     *
     * <p>Measured, as the wider-axis reach of a camp of twelve: at 6.0 the spiral
     * offers only twenty-five candidates inside thirty blocks and the camp reached
     * <strong>31</strong>; at 2.5 it offers a hundred and forty-four and the camp
     * reaches <strong>25</strong>. A camp of eight came down from 20 to 14 over the
     * same change, which is the size that matters — that is the one a world seeds.
     * Tighter than 2.5 buys nothing, since the packing is against the separation by
     * then, and costs candidates weighed per plot.
     */
    private static final double GROWTH = 2.5;

    /**
     * Candidates weighed per plot placed, before the camp gives up and steps out.
     *
     * <p>A bound rather than a budget: a layout that can spin is worse than one
     * that sprawls, which is the lesson {@code Layouts.ORGANIC} paid for. When
     * this is reached the next plot is put a clear separation beyond everything
     * already placed, which is what a camp does when the good ground by the fire
     * is taken.
     */
    private static final int CANDIDATES = 4096;

    /** Camps whose sequences are kept. More than a handful is a server's worth. */
    private static final int CAMPS_REMEMBERED = 8;

    private final String id;

    public GoblinCampLayout() {
        this(Culture.LAYOUT_GOBLIN_CAMP);
    }

    public GoblinCampLayout(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isSameShapeEverywhere() {
        return false;   // the spiral is turned by the camp's own center
    }

    /**
     * Small, because a camp huddles.
     *
     * <p>The warren asks for sixteen because its knots sprawl and the claim has
     * to cover the outliers. A camp has no outliers: the plots pack against each
     * other from the middle out, so the claim wants only the doorstep ring past
     * the furthest roof. This is the default, said out loud because the temptation
     * with a new arrangement is to copy the last one's number.
     */
    @Override
    public int claimMargin() {
        return 8;
    }

    @Override
    public SimPos plotFor(SimPos center, int index) {
        int at = Math.max(0, index);
        SimPos plot = sequenceFor(center, at + 1).get(at);
        // The height is the center's, and it is re-stamped here rather than taken
        // off the remembered plot. The cache is keyed on x and z — a camp is the
        // same camp however deep the valley it sits in — so a second ask at the
        // same column and a different y would otherwise get back the first ask's
        // height, which is a plan inventing a height and {@link Layout} says
        // plainly that it must not. Caught by
        // {@code LayoutTest.noLayoutAnswersDifferentlyForHavingBeenAskedBefore}
        // only because another test in the suite happened to ask about (0, 72, 0)
        // before this one asked about (0, 64, 0).
        return new SimPos(plot.x(), center.y(), plot.z());
    }

    /**
     * The camp's plots in order, generated as far as asked and remembered.
     *
     * <p>Kept for the reason {@code Layouts.ORGANIC} keeps its scatter: a plot is
     * only knowable by having placed everything before it, so recomputing the
     * sequence for every candidate a settlement weighs would be the same work
     * over and over.
     */
    private List<SimPos> sequenceFor(SimPos center, int wanted) {
        String key = center.x() + ":" + center.z();
        synchronized (remembered) {
            List<SimPos> camp = remembered.get(key);
            if (camp == null) {
                camp = new ArrayList<>();
                remembered.put(key, camp);
                if (remembered.size() > CAMPS_REMEMBERED) {
                    Iterator<String> oldest = remembered.keySet().iterator();
                    oldest.next();
                    oldest.remove();
                }
            }
            extend(center, camp, wanted);
            return camp;
        }
    }

    /**
     * Places plots until there are as many as asked for.
     *
     * <p>Resumable, and it has to be: a camp asked for three plots and then for
     * thirty must give the same first three, or the buildings already standing on
     * them are standing on ground the plan has reassigned.
     */
    private void extend(SimPos center, List<SimPos> camp, int wanted) {
        if (camp.isEmpty() && wanted > 0) {
            camp.add(center);   // the trampled middle
        }
        // Where the spiral had got to. Counted from the plots placed rather than
        // stored, so the sequence is a function of the center and the count and
        // nothing that could go stale.
        int candidate = 1;
        double turn = startingTurn(center);
        while (camp.size() < wanted) {
            SimPos found = null;
            int weighed = 0;
            while (found == null && weighed < CANDIDATES) {
                double radius = GROWTH * Math.sqrt(candidate);
                double angle = candidate * TURN + turn;
                candidate++;
                weighed++;
                if (radius < RIM) {
                    continue;   // the yard, which is left open
                }
                SimPos dart = new SimPos(
                        center.x() + (int) Math.round(radius * Math.cos(angle)),
                        center.y(),
                        center.z() + (int) Math.round(radius * Math.sin(angle)));
                boolean clear = true;
                for (SimPos taken : camp) {
                    if (!Layout.farEnoughApart(dart, taken)) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    found = dart;
                }
            }
            camp.add(found != null ? found : beyond(center, camp));
        }
    }

    /**
     * Which way this camp's spiral starts, so two camps are not one camp twice.
     *
     * <p>SplitMix64's finalizer over the two horizontal coordinates, exactly as
     * {@code Culture.layoutFor} and {@code HouseStyle.Variation} use it: a camp's
     * center lands on a spacing grid, so the low bits of x and z move in lockstep
     * and reading them raw would hand a whole region one camp. The height is left
     * out deliberately — a camp resited a block up the hill is the same camp.
     */
    private static double startingTurn(SimPos center) {
        long h = center.x() * 0x9E3779B97F4A7C15L
                ^ center.z() * 0xC2B2AE3D27D4EB4FL
                ^ 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h >>> 11) / (double) (1L << 53)) * 2 * Math.PI;
    }

    /**
     * Ground beyond everything placed, for a camp that has run out of candidates.
     *
     * <p>Clear by construction — past the furthest plot by a whole separation, on
     * a bearing turned by the count so successive escapes do not stack into a
     * line. The same honest way out {@code Layouts.ORGANIC} takes, and for the
     * same reason: a layout that can spin is worse than one that sprawls.
     */
    private static SimPos beyond(SimPos center, List<SimPos> camp) {
        int furthest = RIM;
        for (SimPos placed : camp) {
            furthest = Math.max(furthest, Math.max(
                    Math.abs(placed.x() - center.x()),
                    Math.abs(placed.z() - center.z())));
        }
        int out = furthest + MIN_PLOT_SEPARATION;
        double angle = camp.size() * TURN + startingTurn(center);
        return new SimPos(
                center.x() + (int) Math.round(out * Math.cos(angle)),
                center.y(),
                center.z() + (int) Math.round(out * Math.sin(angle)));
    }

    private final Map<String, List<SimPos>> remembered = new LinkedHashMap<>();
}
