package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A long green with a street down each side of it: the Angerdorf.
 *
 * <p>From the air it is an eye, or a leaf. A lens of open ground runs down the
 * middle; a street bows away from the axis on each side of it and the two meet
 * at a point at either end; houses stand along both streets, the rank between
 * the street and the green looking across the green and the rank behind the
 * street looking over it at them; and behind each of those a back lane takes the
 * growth. It is a real and very common form — most of the green villages of the
 * North German plain and a good many English ones are this shape, because a
 * green wants to be long (it is a droveway as much as a common) and the road has
 * to get past it on both sides.
 *
 * <p><strong>It is not a ring town.</strong> {@link RadialStreetLayout} draws
 * circles round a center and both of its arrangements read as a wheel whatever
 * size they are. The whole plan here reaches 243 blocks along the green and 85
 * across it, with nothing at all standing on the middle.
 *
 * <p>Every measurement below is a reach from the middle of the town rather than
 * a footprint, because that is what the siting code and the sprawl bars are in:
 * the plan that reaches 243 by 85 covers 487 by 171 on the ground. Lengths of
 * the green itself are given end to end and said to be.
 *
 * <h2>What a village of this shape actually looks like as it fills</h2>
 *
 * <p>Not like the plan, until it is fairly big, and that is worth being straight
 * about because it is the one claim about this arrangement a person could check
 * and find wanting.
 *
 * <p>{@link PlannedLayout} takes its offers <em>nearest the center first</em>,
 * which is right and is what stops a town marching outward as it grows. What it
 * means for a shape is that a village of {@code n} plots is the plan intersected
 * with a disc, so the built ground is round until the disc is wider than the
 * plan's widest rank. Measured, laid at the plan's own two hundred and fifty-six
 * and read off as a prefix:
 *
 * <pre>
 *   plots     16   24   32   48   64   80   96  112  128  140  180  220  256
 *   along     35   49   49   63   77   91   91  105  119  126  160  209  243
 *   across    45   45   59   59   59   85   85   85   85   85   85   85   85
 * </pre>
 *
 * <p>The across column is the whole of it. The village widens in three steps as
 * the disc reaches each rank in turn, stops at eighty-five once it has reached
 * the last one — the back lane's outer rank — and every plot after about eighty
 * goes into length. So a hamlet of twenty on a green is a knot, which is what a
 * hamlet on a green is; the leaf appears at around a hundred and ten and is
 * unmistakable by two hundred.
 *
 * <h2>Why the green is five hundred and sixty blocks long</h2>
 *
 * <p>Because the width stops where the last rank is, and the number of ranks is
 * what the plan has to trade against the length of the green. Frontage comes in
 * courses forty blocks apart. Two courses on a green this long carry the whole
 * plan; three courses carry it on a green half as long and put the last rank a
 * hundred and thirty-three blocks out, which is as wide as the disc ever gets.
 * Measured at a hundred and forty plots:
 *
 * <pre>
 *   half-length   courses   built ground   reach
 *      160          3         111 x  99     119
 *      280          2         126 x  85     132
 * </pre>
 *
 * <p>So the green is drawn for the whole plan and is long. A village of sixty
 * stands in the middle third of it and the rest is a road through a common,
 * which is exactly what these places look like before they fill.
 *
 * <h2>The flank</h2>
 *
 * <p>Each half of the lens is the curve {@code x = A·u, z = B·cos(pi·u/2)} for
 * {@code u} in [-1, 1], and the two halves meet on the axis at {@code u = ±1}.
 * Chosen over the circular arc through the same three points for its shape
 * rather than its arithmetic: the cosine holds the green very nearly
 * parallel-sided over its middle and spends all of its turning at the ends,
 * which is a droveway green, where a circular arc is uniformly curved and reads
 * as a very long roundabout. It is also 10.2 degrees at its steepest against the
 * arc's 13.0, though at these proportions that buys nothing — both clear the
 * ordinary plot pitch. It would start to matter on a fatter green.
 */
public final class GreenLayout extends PlannedLayout {

    /**
     * Half the length of the green, from the middle to the point the streets meet.
     *
     * <p>Five hundred and sixty blocks end to end, for the reason set out above:
     * a back lane stands off its street at a fixed distance and so widens the
     * village faster than it lengthens it, and the only way to carry two hundred
     * and fifty-six plots on two courses is to have somewhere along the green to
     * put them.
     *
     * <p>At two hundred and fifty-five the plan still fills on two courses and at
     * a hundred and sixty it does not. Two hundred and eighty is clear of that
     * edge, so a later block on the pitch or the setback cannot quietly tip the
     * village into a third course and a round shape.
     */
    private static final int GREEN_HALF_LENGTH = 280;

    /**
     * How far the street's centerline bows from the axis at the middle.
     *
     * <p>The open middle is this less {@link #SETBACK} on each side, so
     * thirty-eight blocks of common at the widest and three hundred and eighty of
     * it between the points where the two inner ranks converge on the axis.
     *
     * <p>Wider would be better to look at and costs the shape: every block of it
     * pushes the back lane's outer rank a block further out, and that rank is
     * what the village's width settles at. At forty-four the built ground at a
     * hundred and forty plots goes from 126 by 85 to 125 by 97.
     */
    private static final int GREEN_HALF_WIDTH = 32;

    /**
     * The steepest the flank ever leans, in radians.
     *
     * <p>Every spacing constant below is derived from this one number, so it is
     * worth being exact about. The flank is {@code z = B·cos(pi·u/2)} over
     * {@code x = A·u}, so {@code dz/dx = -(B·pi/2A)·sin(pi·u/2)} and the steepest
     * point is the tip, where the sine is one. With a half-length of 280 and a
     * half-width of 32 that gradient is {@code 32·pi/560 = 0.180}, or 10.2
     * degrees.
     *
     * <p>Nowhere in the plan does anything lean further — not a back lane, which
     * is this same curve pushed sideways along its own normal, and not any rank,
     * for the same reason. A normal offset has the tangent angles of the curve it
     * came from.
     */
    private static final double STEEPEST_LEAN =
            Math.atan(Math.PI * GREEN_HALF_WIDTH / (2.0 * GREEN_HALF_LENGTH));

    /**
     * How much of a flank one plot takes, measured along the flank.
     *
     * <p>Separation is Chebyshev, so two plots a chord apart along a run leaning
     * at {@code theta} are only {@code chord·cos(theta)} apart on the wider axis.
     * The ring roads answer that with a blanket root two — {@link Layout#onACurve}
     * and a block — because a circle runs at 45 degrees somewhere on every quarter
     * and the worst case is the full diagonal.
     *
     * <p>A lens never reaches 45 degrees, and this one never reaches eleven. The
     * worst case on the wider axis is a separation over {@code cos(10.2)}, which
     * is a fiftieth more than a separation — so this sits barely over the ordinary
     * straight-street {@link #PITCH}, and the block of margin is for the two
     * roundings to whole blocks between one plot's center and the next rather than
     * for the curve.
     *
     * <p>Carrying the circle's eighteen over is not merely wasteful, which is
     * what it looks like. It thins every flank by a fifth, the plan then needs a
     * third course of frontage to make the count up, and the third course puts a
     * rank a hundred and thirty-three blocks out. Measured, at a hundred and
     * forty plots:
     *
     * <pre>
     *   pitch   streets   built ground   reach
     *     14       4        126 x  85     132
     *     18       6        135 x 133     137
     * </pre>
     *
     * <p>Eighteen does not make the village worse at anything a test was
     * watching — the frontage stays at a hundred per cent either way. It just
     * stops it being a lens.
     */
    private static final int ARC_PITCH = Math.max(PITCH,
            (int) Math.ceil(MIN_PLOT_SEPARATION / Math.cos(STEEPEST_LEAN)) + 1);

    /**
     * How far a back lane stands behind the street in front of it.
     *
     * <p>Two ranks stand back to back in the gap: one at {@link #SETBACK} from
     * the street it fronts, one at {@link #SETBACK} from the lane. So the spacing
     * is twice the setback plus whatever those two ranks need between them, and
     * that gap is measured <em>across</em> the flank rather than along it — which
     * comes to the same number. The normal leans at {@code 90 - theta} and the
     * Chebyshev factor there is {@code max(sin, cos) = cos(theta)}, the same
     * factor as along the tangent, for as long as {@code theta} stays under 45
     * degrees. It does everywhere, by {@link #STEEPEST_LEAN}.
     *
     * <p>Which is {@link #BACK_TO_BACK} with the lens's own pitch in place of the
     * straight one, and it is the sum three other arrangements were each holding
     * as a literal. It clears the arithmetic floor that twice the setback plus a
     * carriageway imposes, so a lane cannot land on the frontage of the street it
     * stands behind.
     */
    private static final int LANE_SPACING = 2 * SETBACK + ARC_PITCH;

    /**
     * How close to the axis of the green an offer may stand.
     *
     * <p>The inner rank is offset thirteen blocks along the inward normal, and
     * towards the tips that normal points substantially <em>along</em> the green
     * — so the inner rank crosses the axis well before the street it fronts
     * reaches the tip, and the north rank would finish up standing on the south
     * rank's ground. Half a separation and a block keeps the two ranks a
     * separation and a block apart where they converge, which is what the siting
     * code wants of them, and it leaves the taper at each end of the green open,
     * which is what a taper is.
     */
    private static final int AXIS_CLEARANCE = MIN_PLOT_SEPARATION / 2 + 1;

    /** North first, then south, so a street's index does not depend on the order. */
    private static final int[] SIDES = {1, -1};

    /**
     * How many back lanes may be opened before the village stops opening them.
     *
     * <p>A bound rather than a budget. The plan of two hundred and fifty-six
     * opens one; sixteen is a village half a kilometer wide and nothing has ever
     * asked for it.
     */
    private static final int LANES_ALLOWED = 16;

    /**
     * How finely the flank is sampled when walking it by arc length.
     *
     * <p>A thousand and twenty-four steps over five hundred and sixty blocks is
     * half a block, and the points are interpolated within a step, so the spacing
     * error is well under the rounding the plan already does by putting things on
     * whole blocks.
     *
     * <p>Sampled rather than solved because the arc length of a cosine has no
     * closed form worth having. The tempting alternative — spacing by the
     * parameter {@code u} instead of by arc length — packs the tips tighter than
     * the middle, which is the R5 fault of spacing a face by somebody else's arc
     * wearing a different hat.
     */
    private static final int FINE = 1024;

    private final String id;

    public GreenLayout() {
        this("green");
    }

    public GreenLayout(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    protected void design(SimPos center, int wanted,
                          List<TownPlan.Street> streets, List<Offer> offers) {
        int lanes = lanesFor(wanted);

        // The two streets round the green first, north then south, and the back
        // lanes after them in pairs. A plot records the street it fronts by
        // index, so a plan that renumbered its roads as it grew would repoint
        // every door in the village -- and the lanes are the part that grows.
        for (int side : SIDES) {
            streets.add(flank(center, side, 0, TownPlan.Kind.SPINE));
        }
        for (int lane = 1; lane <= lanes; lane++) {
            for (int side : SIDES) {
                streets.add(flank(center, side, lane * LANE_SPACING, TownPlan.Kind.LANE));
            }
        }

        // Frontage on both faces of every street. The inner face of the two
        // streets round the green is the rank that stands on the common, and it
        // is the only frontage anywhere in the village offered inside the lens --
        // everything else is offered outward. Nothing at all is offered on the
        // green itself: an Angerdorf's middle is common land, and a hall in the
        // center of it would make this a ring town with a very long ring.
        //
        // A back lane with no frontage would cost twice over -- it would refuse
        // every plot it crossed and give nothing back -- so both of its faces are
        // offered too, and the two ranks between a street and the lane behind it
        // stand back to back, one facing each way, with the tofts between them.
        for (int lane = 0; lane <= lanes; lane++) {
            double middle = lane * LANE_SPACING;
            for (int s = 0; s < SIDES.length; s++) {
                for (int face : new int[] {-1, 1}) {
                    frontage(center, SIDES[s], lane * SIDES.length + s,
                            middle, middle + face * SETBACK, lane == 0 && face < 0,
                            offers);
                }
            }
        }
    }

    /**
     * How many back lanes this many plots wants.
     *
     * <p>Counted rather than guessed, and counted on each face's own arc. A flank
     * pushed out by a lane spacing is a longer curve than the one it stands
     * behind, so a formula in the plot count alone either runs out of frontage or
     * draws lanes that nothing ever fronts.
     *
     * <p>The count is of offers rather than of plots, and a few offers at each tip
     * are refused where the two streets converge on one another. That shortfall is
     * deliberately not allowed for: the plan carries about a seventh more frontage
     * than it hands out, and allowing for the refusals as well would open a lane
     * the village does not need.
     */
    private static int lanesFor(int wanted) {
        int room = roomAt(0);
        int lanes = 0;
        while (room < wanted && lanes < LANES_ALLOWED) {
            lanes++;
            room += roomAt(lanes * LANE_SPACING);
        }
        return lanes;
    }

    /** How much frontage the two faces of a street at this offset would carry. */
    private static int roomAt(double middle) {
        return roomOn(middle - SETBACK) + roomOn(middle + SETBACK);
    }

    /**
     * How many plots one face carries, on both flanks, once the taper is cut.
     *
     * <p>Walked rather than divided out of a length, because the offers near the
     * tips are dropped for standing too near the axis and a length does not know
     * that. The south flank is the north flank's mirror image, so it is counted
     * rather than walked.
     */
    private static int roomOn(double face) {
        int room = 0;
        for (double[] point : spaced(1, face, ARC_PITCH)) {
            if (point[1] >= AXIS_CLEARANCE) {
                room++;
            }
        }
        return room * SIDES.length;
    }

    /**
     * One rank of frontage: a face of one street, on one side of the green.
     *
     * <p>Every rank turns to look at the street it fronts, which is the whole
     * point of drawing streets before houses, with one exception: the rank
     * standing between the green and its street looks the other way, across the
     * common. That is not decoration. It is the only place in any of these
     * arrangements where a plot has something better than a road to face, and a
     * row of houses presenting their back gardens to the village green would be
     * the arrangement drawn inside out. It still <em>fronts</em> that street and
     * is counted as doing so, because that is where it takes its access from.
     *
     * @param acrossTheGreen whether this is that rank
     */
    private static void frontage(SimPos center, int side, int street, double middle,
                                 double face, boolean acrossTheGreen,
                                 List<Offer> offers) {
        for (double[] point : spaced(side, face, ARC_PITCH)) {
            if (side * point[1] < AXIS_CLEARANCE) {
                continue;   // in the taper, where the green has run out
            }
            SimPos where = block(center, point[0], point[1]);
            SimPos looksAt;
            if (acrossTheGreen) {
                looksAt = block(center, point[0], 0);
            } else {
                double[] onRoad = flankPoint(point[2], side, middle);
                looksAt = block(center, onRoad[0], onRoad[1]);
            }
            offers.add(new Offer(where, street, Layout.facingToward(where, looksAt)));
        }
    }

    /** One flank of the lens, as the run of points a road along it passes through. */
    private static TownPlan.Street flank(SimPos center, int side, double out,
                                         TownPlan.Kind kind) {
        List<SimPos> path = new ArrayList<>();
        double carried = SEGMENT;
        double[] previous = null;
        for (int i = 0; i <= FINE; i++) {
            double[] point = flankPoint(-1 + 2.0 * i / FINE, side, out);
            if (previous != null) {
                carried += Math.hypot(point[0] - previous[0], point[1] - previous[1]);
            }
            previous = point;
            if (carried < SEGMENT && i < FINE) {
                continue;
            }
            carried = 0;
            SimPos at = block(center, point[0], point[1]);
            if (path.isEmpty() || !path.get(path.size() - 1).equals(at)) {
                path.add(at);
            }
        }
        return new TownPlan.Street(path, ROAD_HALF * 2, kind);
    }

    /**
     * Points a fixed distance out from one flank, evenly spaced along their own arc.
     *
     * <p>The R5 rule, done the only way it can be on a curve with no arc length in
     * closed form: sample finely, keep the running length, and put a point
     * wherever that length passes the next multiple of the pitch, interpolating
     * within the sample so the spacing is exact rather than exact to half a block.
     * The inner rank of a street is a shorter curve than the street and the lane
     * behind it is a longer one; spacing all three by the street's own length
     * would pack the inner one below the separation the siting code demands and
     * leave the outer one gappy.
     *
     * <p>Centered on the middle of the green, so the leftover is split between the
     * two tips and the village is symmetrical about both of its axes.
     *
     * @return {@code {x, z, u}} for each point, in the center's local coordinates
     */
    private static List<double[]> spaced(int side, double out, double spacing) {
        double[][] sample = new double[FINE + 1][];
        double[] along = new double[FINE + 1];
        for (int i = 0; i <= FINE; i++) {
            sample[i] = flankPoint(-1 + 2.0 * i / FINE, side, out);
            along[i] = i == 0 ? 0 : along[i - 1] + Math.hypot(
                    sample[i][0] - sample[i - 1][0], sample[i][1] - sample[i - 1][1]);
        }
        double total = along[FINE];
        int gaps = (int) Math.floor(total / spacing);
        double first = (total - gaps * spacing) / 2;

        List<double[]> points = new ArrayList<>();
        int at = 0;
        for (int k = 0; k <= gaps; k++) {
            double want = first + k * spacing;
            // Stops one short of the end, not at it: the step read below is
            // along[at + 1], so a walker allowed to reach FINE reads off the
            // end of its own table. It takes a total that divides by the pitch
            // to within an ulp to get there, which is to say never, but a guard
            // that does not cover the access it guards is not a guard.
            while (at < FINE - 1 && along[at + 1] < want) {
                at++;
            }
            double step = along[at + 1] - along[at];
            double part = step <= 0 ? 0 : (want - along[at]) / step;
            points.add(new double[] {
                    sample[at][0] + part * (sample[at + 1][0] - sample[at][0]),
                    sample[at][1] + part * (sample[at + 1][1] - sample[at][1]),
                    sample[at][2] + part * (sample[at + 1][2] - sample[at][2])});
        }
        return points;
    }

    /**
     * A point on one flank of the lens, pushed {@code out} blocks along its normal.
     *
     * <p>Along the normal and not straight sideways, which is the difference
     * between a rank that stands thirteen blocks from its street everywhere and
     * one that stands thirteen blocks from it in the middle and less at the tips.
     * The second kind puts its end houses on the curb.
     *
     * @param side 1 for the north flank, -1 for the south, which is its mirror
     * @param out  blocks along the outward normal; negative is into the green
     */
    private static double[] flankPoint(double u, int side, double out) {
        double along = Math.PI * u / 2;
        double runZ = -GREEN_HALF_WIDTH * (Math.PI / 2) * Math.sin(along);
        double run = Math.hypot(GREEN_HALF_LENGTH, runZ);
        return new double[] {
                GREEN_HALF_LENGTH * u - out * runZ / run,
                side * (GREEN_HALF_WIDTH * Math.cos(along)
                        + out * GREEN_HALF_LENGTH / run),
                u};
    }

    private static SimPos block(SimPos center, double x, double z) {
        return new SimPos(center.x() + (int) Math.round(x), center.y(),
                center.z() + (int) Math.round(z));
    }
}
