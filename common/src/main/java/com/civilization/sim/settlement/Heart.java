package com.civilization.sim.settlement;

import com.civilization.sim.culture.Layout;
import com.civilization.sim.culture.Layouts;
import com.civilization.sim.culture.TownPlan;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.platform.WorldBridge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The middle of a town: the square the roads run to, and the hall's own ground.
 *
 * <p>A settlement has never had a middle. It had a <em>first plot</em>, and the
 * first thing every town builds is a camp post, so plot zero went to a marker on
 * step one and the hall — the thing the whole arrangement is drawn around — was
 * raised a hundred blocks out on whatever slot the cursor had reached by the
 * time a town could afford one. Worse, in every lattice arrangement that camp
 * post was also what the roads radiated from, so the lanes of the town converged
 * on a signpost and the middle read back as carriageway.
 *
 * <p>Three claims, and they are deliberately separate things:
 *
 * <ul>
 *   <li><strong>The square</strong> is a fixed point at the plan's center. It is
 *       not a plot and never becomes one. It is what a road aims at when it has
 *       nothing nearer to join, which is what makes a town's ways converge on
 *       its middle instead of on whatever happened to be built first.</li>
 *   <li><strong>The hall's ground</strong> is plot zero, held at the hall's own
 *       square rather than the plan's default, and held against everybody until
 *       the hall is ordered. Reserving the <em>index</em> reserves no ground:
 *       {@link Layout#MIN_PLOT_SEPARATION} is stated for two plots of the
 *       default span, so a hall does not fit between plot zero's neighbors in
 *       any arrangement in the mod. It costs the two nearest plots, which is the
 *       price of a town with a hall in the middle of it.</li>
 *   <li><strong>The marker</strong> — the camp post — stands at the square's
 *       edge and on no plot at all, because a post is a sign and not a
 *       building.</li>
 * </ul>
 *
 * <p><strong>Three arrangements have no plot near the middle.</strong>
 * {@code ring_streets}, {@code crossroads} and {@code bastide} draw their
 * innermost frontage thirty to fifty blocks out, by design — a circus, a
 * crossing and a market place are open ground, and filling them in would be
 * deleting the arrangement rather than centering the hall. For those the square
 * is the open middle, the marker stands at its edge, and the hall takes the
 * nearest plot to it, which is what plot zero already is: the plan's offers are
 * taken nearest-first, so plot zero is the closest ground the arrangement has.
 */
public final class Heart {

    private Heart() {
    }

    /**
     * How much ground a hall holds, whichever hall a people raises.
     *
     * <p>The largest of them, and deliberately not the one this particular
     * culture builds. The reserve is made on step one, before a camp knows what
     * it will grow into and long before {@code Homes} has substituted a great
     * hut for a town hall; a reserve that changed size when a culture changed
     * its mind would be ground the town had already built on.
     */
    public static final int SPAN = Math.max(
            BuildingSizes.plotSpanOf("civilization:town_hall"),
            Math.max(BuildingSizes.plotSpanOf("civilization:great_hut"),
                    BuildingSizes.plotSpanOf("civilization:chieftain_hut")));

    /*
     * On backing the hall off plot zero, which was built and taken out again.
     *
     * A plan keeps its plots off its own carriageways at Layout.DEFAULT_SPAN and
     * a hall is four blocks wider, so the obvious worry is that the hall's
     * square overhangs the curb of the street plot zero fronts and
     * Settlement.standsOnAWay refuses the hall the very ground reserved for it.
     * The obvious fix is to back the square two blocks off the street.
     *
     * It is not needed and it costs more than it buys. PlannedLayout offers its
     * frontage at SETBACK -- thirteen blocks from the centerline -- rather than
     * at the ten and a half its own `fits` would tolerate, and thirteen clears a
     * fifteen-wide claim off an eight-wide carriageway with half a block to
     * spare. What backing it off breaks is everything that finds a plot by
     * matching its column against the plan's own offers: facingFor falls through
     * to "face the middle of the town", which is exactly the thing street-first
     * layout exists to stop, and againstTheCurb stops recognising the plot at
     * all.
     *
     * So the hall stands on plot zero, at plot zero. An arrangement whose plot
     * zero is tight enough to the road that a hall will not fit there is
     * reported as one that cannot take the hall -- see OpenStreetsTest -- rather
     * than quietly moved.
     */

    /** How far out from the square a marker is allowed to look for standing room. */
    private static final int MARKER_REACH = 32;

    /** The four ways out of a square, in the order a marker tries them. */
    private static final int[][] SIDES = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /**
     * The square: the fixed point at the plan's center that the roads run to.
     *
     * <p>A method rather than a bare {@code center()} because it is a different
     * claim, made in one place, and because what a road aims for is exactly the
     * thing that used to be a building and should not be one.
     */
    public static SimPos square(Settlement town) {
        SimPos heart = town.center();
        if (!builtOver(town, heart)) {
            return heart;
        }
        // Something is standing on the middle. A hub inside somebody's walls is
        // a hub no lane may arrive at — PathPlanner.Walls refuses every run that
        // would gravel a building — so a town that had built on its own center
        // would have no roads at all. The nearest clear column out, in a fixed
        // order, keeps the hub where it belongs without letting one badly placed
        // marker take the whole network down with it.
        for (int out = 1; out <= MARKER_REACH; out++) {
            for (int[] side : SIDES) {
                SimPos at = new SimPos(heart.x() + side[0] * out, heart.y(),
                        heart.z() + side[1] * out);
                if (!builtOver(town, at)) {
                    return at;
                }
            }
        }
        return heart;
    }

    /** Whether any standing building's walls, and their gravel, cover this column. */
    private static boolean builtOver(Settlement town, SimPos at) {
        for (Building standing : town.buildings()) {
            if (!BuildPlanner.holdsGround(standing.blueprintId())) {
                continue;
            }
            int[] half = BuildPlanner.wallsHalfOf(standing.blueprintId(),
                    standing.facing(), town.catalog());
            if (Math.abs(at.x() - standing.origin().x()) <= half[0] + 1
                    && Math.abs(at.z() - standing.origin().z()) <= half[1] + 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plot zero's ground, as the hall will actually stand on it.
     *
     * <p>Plot zero's middle, backed off the street it fronts by
     * {@link #SETBACK}. The plan draws every plot at the default span; a hall is
     * wider, so leaving it on the offered middle puts its wall over the curb of
     * its own street.
     */
    public static SimPos hallGround(Settlement town) {
        return hallGround(town.arrangement(), town.center());
    }

    /**
     * Middles worked out, kept because the siting loops ask constantly.
     *
     * <p>{@code Settlement.isPlotFree} asks whether a candidate stands on the
     * hall's ground, and the give-up path puts five hundred candidates through
     * it for one building. Working the answer out means asking the layout for a
     * plan, which is a synchronized cache lookup at best. A handful of towns is
     * a server's worth, the same bound {@code PlannedLayout} keeps its plans at.
     */
    private static final Map<String, SimPos> MIDDLES = new LinkedHashMap<>();

    private static final int TOWNS_REMEMBERED = 8;

    /** The same, for a plan nobody has founded a town on yet. */
    public static SimPos hallGround(Layout arrangement, SimPos center) {
        String key = arrangement.id() + "@" + center.x() + ":" + center.y()
                + ":" + center.z();
        synchronized (MIDDLES) {
            SimPos known = MIDDLES.get(key);
            if (known != null) {
                return known;
            }
            SimPos found = middleOf(arrangement, center);
            if (MIDDLES.size() > TOWNS_REMEMBERED) {
                MIDDLES.clear();
            }
            MIDDLES.put(key, found);
            return found;
        }
    }

    private static SimPos middleOf(Layout arrangement, SimPos center) {
        if (!Layouts.isStreetsFirst(arrangement)) {
            return arrangement.plotFor(center, 0);
        }
        TownPlan plan = arrangement.planFor(center, 1);
        if (plan.plots().isEmpty()) {
            return arrangement.plotFor(center, 0);
        }
        return plan.plot(0).at();
    }

    /**
     * Whether a plot of this width would stand on ground the hall is holding.
     *
     * <p>Only until the hall is ordered. From the moment there is one in the
     * queue the hall's own claim is doing this job, and a reserve that outlived
     * it would be a second building's worth of empty ground in the middle of
     * every town forever.
     */
    public static boolean onTheHallsGround(Settlement town, SimPos candidate, int span) {
        if (hallIsSpokenFor(town)) {
            return false;
        }
        return BuildPlanner.plotsOverlap(candidate, span, hallGround(town), SPAN);
    }

    /** Whether the town has its hall, or has at least ordered one. */
    public static boolean hallIsSpokenFor(Settlement town) {
        for (Building standing : town.buildings()) {
            if (standing.role() == BuildingRole.HALL) {
                return true;
            }
        }
        for (BuildTask queued : town.queued()) {
            if (BuildingRole.of(queued.blueprintId()) == BuildingRole.HALL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where a marker stands: at the square's edge, clear of the hall's ground.
     *
     * <p>The four sides in turn at each distance out, so the answer is the same
     * every time the same town is asked and the post ends up as near the middle
     * as there is room for. Null when the middle of the town is so built up that
     * there is nowhere within {@link #MARKER_REACH} — the caller then sites it as
     * an ordinary building, which is what it used to be.
     */
    public static SimPos marker(Settlement town, int span) {
        return marker(town, span, null);
    }

    /**
     * The same, put to the world before it is taken.
     *
     * <p>A marker that the ground refuses is a marker the arrival then moves, and
     * a seeded building that moves on arrival is the fault {@code SeededGroundTest}
     * exists to hold at nought. So the ground gets the same veto here that
     * {@code Founding.roomFor} gives every other seeded building: the site's own
     * fault, and whether the ring one step outside its walls can be brought to
     * its floor.
     *
     * @param ground the world to ask, or null to place on the geometry alone,
     *               which is what {@code /civ seed} and most tests want
     */
    public static SimPos marker(Settlement town, int span, WorldBridge ground) {
        SimPos square = town.center();
        TownPlan plan = Layouts.isStreetsFirst(town.arrangement())
                ? town.arrangement().fullPlan(town.center()) : null;
        int first = SPAN / 2 + span / 2 + 1;
        for (int out = first; out <= first + MARKER_REACH; out++) {
            for (int[] side : SIDES) {
                SimPos at = new SimPos(square.x() + side[0] * out, square.y(),
                        square.z() + side[1] * out);
                if (!town.isPlotFree(at, span, null) || inACarriageway(plan, at, span)) {
                    continue;
                }
                if (!theGroundWillTakeIt(at, span, false, ground)) {
                    continue;
                }
                return at;
            }
        }
        return null;
    }

    /**
     * Whether the world will take a building of this span on this column.
     *
     * <p>Deliberately the same pair of questions {@code Founding.roomFor} asks,
     * and for the reason that class gives at length: a plot in a shallow bowl
     * passes {@code siteFault} and still stands three courses over its own floor
     * on every side, which is the "buried" fault the in-game audit reports.
     */
    public static boolean theGroundWillTakeIt(SimPos at, int span, boolean isField,
                                              WorldBridge ground) {
        if (ground == null) {
            return true;
        }
        SimPos onGround = new SimPos(at.x(), ground.groundHeight(at), at.z());
        return ground.siteFault(onGround, BuildPlanner.PLOT_PROBE_RADIUS)
                        == WorldBridge.SITE_FAULT_NONE
                && Grade.shelf(ground, onGround, span, isField) == Grade.Shelf.LEVEL;
    }

    /**
     * Whether a marker would stand in a street the plan has drawn but not laid.
     *
     * <p>Asked of the plan rather than of the network, because a post goes up on
     * step one and the streets are routed afterwards: {@code isPlotFree} refuses
     * ground that is <em>already</em> road, which on the day a camp is founded is
     * none of it. A post set down in the middle of a carriageway that has not
     * been laid yet is a post in the road a week later.
     */
    private static boolean inACarriageway(TownPlan plan, SimPos at, int span) {
        if (plan == null) {
            return false;
        }
        double half = span / 2.0 + 1;
        for (TownPlan.Street street : plan.streets()) {
            if (street.touches(at, half)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ground the plan is holding for the hall, for a road that must not cross it.
     *
     * <p>{@code PathPlanner.heldGround} keeps a routed street off every plot the
     * plan might still use, at the plot's own span. Plot zero's is wider than the
     * plan says, and a carriageway laid through the middle of town before the
     * hall is built is a hall that can never be built there.
     */
    public static List<SimPos> heldFor(Settlement town) {
        return hallIsSpokenFor(town) ? List.of() : List.of(hallGround(town));
    }
}
