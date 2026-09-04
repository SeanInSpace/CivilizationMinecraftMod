package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An arrangement that draws its streets first and takes its plots off them.
 *
 * <p>Everything that is true of <em>any</em> planned town lives here, and what
 * is left for a subclass is the only part that differs: where the roads go.
 * That split is worth making because the shared half is where all the faults
 * have been. Taking offers nearest-first, refusing a plot that fouls a
 * neighbour, refusing a plot that stands in the carriageway, and always being
 * able to answer for a plot index past the end of the plan are four rules that
 * every planned arrangement needs and that each one would otherwise get wrong
 * separately — the high street got three of them wrong on its own, one at a
 * time, and each was found by a person looking at a picture.
 *
 * <p>So a subclass says only: here are my streets, and here is the frontage they
 * offer. It cannot forget to check the road, because it is not the thing doing
 * the taking.
 *
 * <h2>Why not every arrangement</h2>
 *
 * <p>A warren has no streets, and that is not an omission waiting to be filled
 * in — knots of huts with open ground between them is what a warren <em>is</em>,
 * and giving it a high street would turn it into a different people. The same
 * goes for the organic scatter. Streets-first is right for towns that were
 * planned or that grew along a route, and honest to leave off the ones that
 * grew around a well.
 */
public abstract class PlannedLayout implements Layout {

    /*
     * On making this plan terrain-aware, which has been tried and is not simply
     * an improvement waiting to be written.
     *
     * The plan is a flat drawing. Everything after it corrects: roads route
     * round hills it never heard of, plots are refused for ground it put them
     * on. The obvious next step is to let the plan look at the ground itself,
     * and the obvious reason to want it is that thirty-one doors of sixty stood
     * off a road in a measured town because a plot can be cut off from its own
     * street by a gully that no router may cross -- the corridor a road bends
     * within is narrower than the setback, deliberately, so a road can never
     * take a garden and can never reach a garden the hillside has isolated.
     *
     * Two versions were built and measured, on recorded ground, against a
     * baseline of four stranded doors in sixty-two:
     *
     *   refuse a plot whose door cannot reach its street      9 stranded
     *   prefer reachable plots, offer the rest afterwards     9 stranded
     *
     * Both worse, and identically so, which is the informative part: the harm
     * does not come from refusing plots or from the order they are offered in.
     * It comes from the plan being drawn differently at all.
     *
     * The first explanation offered for that was wrong and is worth recording
     * as such. It said refused plots fell through to the outskirt fallback and
     * came back fronting nothing. They do not: lay() over-provisions until every
     * plot has frontage, so on a measured plan of two hundred and fifty-six,
     * ZERO plots front nothing and the fallback never runs. Fixing the fallback
     * afterwards changed no measurement at all -- correctly, since it was never
     * the path being taken.
     *
     * So why a differently drawn plan strands more doors is still unknown. What
     * is known is that it is not the fallback, not the refusal, and not the
     * ordering. The next attempt should start by finding out which plots the two
     * plans actually differ on.
     *
     * The seam for it is cheap when it is wanted: TerrainSense already exists,
     * and planFor would take one. The reason this is a comment and not code is
     * that the measurement said so.
     */


    /**
     * Frontage taken by one plot along a street.
     *
     * <p>A separation exactly, because a rank of plots along a straight street
     * differs on one axis only and that axis is the one
     * {@link Layout#farEnoughApart} reads. Anything more is bare grass between
     * houses that the plan has decided on in advance and no building can close
     * up; anything less is an offer {@code fits} refuses on the way past.
     *
     * <p>Fourteen before, against a separation of twelve, so the plan was making
     * its offers two blocks coarser than its own rule wanted — and the rule was
     * itself a block loose. That is the whole of why a measured street stood its
     * houses a median six apart when the siting code would have allowed three.
     */
    protected static final int PITCH = Layout.MIN_PLOT_SEPARATION;

    /**
     * Half a pitch, rounded up, for a pair set either side of a middle.
     *
     * <p>A row of two is pitched about the middle of its block, so each stands
     * half a pitch from it and the two of them a pitch apart. Rounding up is what
     * makes that true of an odd pitch: {@code 2 * (11 / 2)} is ten, inside the
     * separation, and every such pair would be refused by the plan's own
     * {@code fits} — a bastide would lose half its frontage without one number in
     * the file looking wrong. The pitch was even until now and nothing had to
     * notice.
     */
    protected static final int HALF_PITCH = (PITCH + 1) / 2;

    /** How far a plot's middle sits from the street's middle. */
    protected static final int SETBACK = 13;

    /**
     * The tightest two parallel streets may run with two ranks back to back.
     *
     * <p>Rule R1 as three arrangements had each worked it out for themselves: a
     * rank stands {@link #SETBACK} off each street, so what is left between their
     * backs is {@code spacing - 2 * SETBACK} and that has to be a separation. The
     * bastide called it thirty-eight and said "the first spacing that clears", the
     * grid called it forty, the high street called it forty and said "the tightest
     * that holds", and the green already wrote it as a sum. Four spellings of one
     * sum, three of them holding a number that stops being right the moment the
     * separation moves.
     *
     * <p>An arrangement whose streets curve wants {@code 2 * SETBACK} plus its own
     * arc pitch instead, which is this same sum with the curve's separation in it.
     */
    protected static final int BACK_TO_BACK = 2 * SETBACK + Layout.MIN_PLOT_SEPARATION;

    /**
     * A pitch that holds at any bearing, for a run whose direction is not known.
     *
     * <p>{@link #PITCH} is a separation exactly, which is right for a street laid
     * along an axis and wrong for one laid at an angle: two plots a pitch apart
     * along a run leaning at {@code theta} are only {@code pitch·cos(theta)} apart
     * on the wider axis, and the worst case over all bearings is the full
     * diagonal. So {@link Layout#onACurve} of a separation, and a block for the
     * rounding of each end to whole blocks.
     *
     * <p>For the fallbacks in this file, which carry a street onward in whatever
     * direction it happened to be running and then ring the town in whatever
     * direction is left. Both were pitched by the straight-street PITCH, which
     * tolerated a lean of about thirty-five degrees while the pitch stood two
     * blocks over the separation and tolerates none now.
     */
    protected static final int PITCH_ANY_BEARING =
            Layout.onACurve(Layout.MIN_PLOT_SEPARATION) + 1;

    /** Half-width of an ordinary carriageway. */
    protected static final int ROAD_HALF = 4;

    /** Bare ground between a wall and a carriageway, so a door has a doorstep. */
    protected static final int KERB = 1;

    /** The least an outskirt ring stands out, before the town's own reach. */
    private static final int OUTSKIRT_START = 60;

    /** Plans kept. More than a handful is a server's worth of towns. */
    private static final int TOWNS_REMEMBERED = 8;

    /** How far apart the points of a bending street are set. */
    protected static final int SEGMENT = 8;

    private final Map<String, TownPlan> planned = new LinkedHashMap<>();

    /**
     * One piece of frontage on offer, before anybody has checked whether it fits.
     *
     * @param at     where the building would stand
     * @param street index into the plan's streets, or -1 for frontage on nothing
     * @param facing quarter turns clockwise, pointing at that street
     */
    protected record Offer(SimPos at, int street, int facing) {
    }

    /**
     * Where this arrangement's roads go, and what frontage they open up.
     *
     * <p>The offers are given in whatever order suits the subclass; the order
     * they are <em>taken</em> in is decided here.
     */
    protected abstract void design(SimPos centre, int wanted,
                                   List<TownPlan.Street> streets, List<Offer> offers);

    @Override
    public SimPos plotFor(SimPos centre, int index) {
        int at = Math.max(0, index);
        return planFor(centre, at + 1).plot(at).at();
    }

    /**
     * How big a plan is laid, whatever size is asked for.
     *
     * <p>Fixed, and it has to be. Every number in a plan is derived from the
     * count — how far the spine runs, how many lanes open, how deep they go — so
     * laying at the size asked meant the <em>same town</em> had different
     * geometry depending on how much of it anybody had asked about yet. A
     * settlement that grew past its cached plan had the plan re-laid underneath
     * it, and plot five moved.
     *
     * <p>Nothing had noticed because the determinism rule is checked by asking
     * twice in a row, which passes: the answer only changes after the town grows.
     * It surfaced when the streets became something to pave, because a road laid
     * along a plan that shifts under the houses is a road through the houses.
     *
     * <p>So the plan is laid once at a size no settlement reaches and handed out
     * as a prefix. Two hundred and fifty-six plots is about eight times the
     * largest town measured, and laying it costs a few milliseconds once per
     * town.
     */
    private static final int PLAN_SIZE = Layout.WHOLE_PLAN;

    @Override
    public TownPlan planFor(SimPos centre, int wanted) {
        int want = Math.max(1, wanted);
        synchronized (planned) {
            String key = centre.x() + ":" + centre.z();
            TownPlan held = planned.get(key);
            if (held == null || held.size() < want) {
                held = lay(centre, Math.max(want, PLAN_SIZE));
                if (planned.size() > TOWNS_REMEMBERED) {
                    planned.clear();
                }
                planned.put(key, held);
            }
            if (held.size() == want) {
                return held;
            }
            // A prefix, not a smaller plan. The streets are the town's streets
            // whether or not it has filled them yet, and the plots come in the
            // order the town takes them, so the first n of them ARE the plan for
            // a town of n -- with the same geometry it will still have later.
            return new TownPlan(centre, held.streets(),
                    held.plots().subList(0, Math.min(want, held.size())));
        }
    }

    /** The whole plan this town will ever have, however little of it is built. */
    public TownPlan fullPlan(SimPos centre) {
        return planFor(centre, PLAN_SIZE);
    }

    /**
     * The way the plan says a door on this plot should look.
     *
     * <p>At the street it fronts, which is the whole difference between a street
     * of houses and a row of buildings that happen to be beside a road. Falls
     * back to the centre for ground the plan never offered — a farm sited by its
     * own planner, or an outskirt plot fronting nothing.
     */
    @Override
    public int facingFor(SimPos centre, SimPos plot) {
        for (TownPlan.Plot offered : fullPlan(centre).plots()) {
            if (offered.at().x() == plot.x() && offered.at().z() == plot.z()) {
                return offered.frontsAStreet()
                        ? offered.facing() : Layout.facingToward(plot, centre);
            }
        }
        return Layout.facingToward(plot, centre);
    }

    /**
     * Streets first, then the frontage on them, in the order a town fills.
     *
     * <p>Candidates are taken nearest-first and each only if it clears everything
     * already placed. That is what keeps the three rules without hand-solving the
     * geometry: a candidate that would foul a neighbour is never given a slot,
     * exactly as the siting code would refuse it later. The ring layout's
     * innermost course and the warren's clumps were both arithmetic that looked
     * right and was not; this cannot be wrong in that way because it checks.
     *
     * <p>Nearest-first matters more than it sounds. Taken in the order the
     * streets open instead, the town's reach grows with the plot <em>count</em> —
     * and siting scans up to ninety-six candidates per building, so a town of a
     * hundred asks for plot four hundred and marches away from itself. Sorted by
     * distance the reach grows with the square root, which is what a town filling
     * out actually does.
     */
    private TownPlan lay(SimPos centre, int wanted) {
        // Asked for more frontage until enough of it survives.
        //
        // A design works out how many streets it needs by estimating how much
        // frontage they carry, and that estimate is always optimistic: it cannot
        // know how many of its own offers will be refused for fouling a
        // neighbour or for standing on one of its other streets. The ring roads
        // estimated well and still came up 28% short, so a town of a hundred and
        // forty took a hundred and one plots off its streets and put the other
        // thirty-nine in the outskirts, fronting nothing.
        //
        // Tuning the estimate is the obvious fix and the wrong one -- it is a
        // constant somebody has to keep right, per arrangement, for every size of
        // town, which is exactly the kind of arithmetic that has been wrong three
        // times already in this file's history. Asking again for more needs no
        // constant and cannot be wrong: the only measure of how much frontage a
        // plan has is how much of it survives.
        List<TownPlan.Street> streets = new ArrayList<>();
        List<TownPlan.Plot> taken = new ArrayList<>();
        int ask = wanted;
        for (int attempt = 0; attempt < ENOUGH_TRIES; attempt++) {
            streets = new ArrayList<>();
            taken = take(centre, wanted, ask, streets);
            if (taken.size() >= wanted) {
                break;
            }
            ask *= 2;
        }
        return finish(centre, wanted, streets, taken);
    }

    /**
     * How many times a plan may ask its design for more room before settling.
     *
     * <p>Four doublings is sixteen times the frontage, which no arrangement here
     * has ever needed more than two of. It is a bound rather than a budget: a
     * design that cannot fill a town is allowed to give up and let the outskirts
     * take the remainder, because a layout that spins is worse than one that
     * spreads.
     */
    private static final int ENOUGH_TRIES = 4;

    /** Lays a design of this size and takes what fits, nearest frontage first. */
    private List<TownPlan.Plot> take(SimPos centre, int wanted, int ask,
                                     List<TownPlan.Street> streets) {
        List<Offer> offers = new ArrayList<>();
        design(centre, ask, streets, offers);

        offers.sort((a, b) -> {
            long da = away(a.at(), centre);
            long db = away(b.at(), centre);
            if (da != db) {
                return Long.compare(da, db);
            }
            return a.at().x() != b.at().x()
                    ? Integer.compare(a.at().x(), b.at().x())
                    : Integer.compare(a.at().z(), b.at().z());
        });

        List<TownPlan.Plot> taken = new ArrayList<>();
        for (Offer offer : offers) {
            if (fits(offer.at(), taken, streets)) {
                taken.add(new TownPlan.Plot(
                        offer.at(), Layout.DEFAULT_SPAN, offer.facing(), offer.street()));
                if (taken.size() >= wanted) {
                    break;
                }
            }
        }
        return taken;
    }

    /** Fills out whatever the streets could not, and settles the plan. */
    private TownPlan finish(SimPos centre, int wanted, List<TownPlan.Street> streets,
                            List<TownPlan.Plot> from) {
        List<TownPlan.Plot> taken = new ArrayList<>(from);
        if (taken.size() >= wanted) {
            return new TownPlan(centre, streets, taken);
        }

        // A town that has filled its streets makes more street.
        //
        // It used to make rings: leftover plots scattered in circles around the
        // town, fronting nothing, facing the middle on principle -- a ring of
        // cottages in a field, which looks like nothing anybody built.
        //
        // In practice this path almost never runs. lay() asks its design for
        // more frontage until every plot has some, so a measured plan of two
        // hundred and fifty-six has NO plots fronting nothing and the fallback
        // is never reached. It was changed on the theory that refused plots fell
        // through to here and came back roadless; that theory was wrong, and
        // changing this moved no measurement -- which is what a path nobody
        // takes should do.
        //
        // It is still worth having right, for the day a design cannot fill a
        // town: on that day it should hand back a street rather than a field.
        //
        // A real town does not answer growth by ringing itself with cottages. It
        // carries its high street further out and builds along the new length.
        List<List<SimPos>> paths = new ArrayList<>();
        for (TownPlan.Street street : streets) {
            paths.add(new ArrayList<>(street.path()));
        }
        List<Integer> frontage = new ArrayList<>();
        for (int i = 0; i < streets.size(); i++) {
            frontage.add(i);
        }

        int guard = 0;
        boolean grew = true;
        while (taken.size() < wanted && grew && guard++ < EXTENSIONS_ALLOWED) {
            grew = false;
            for (int i = 0; i < paths.size() && taken.size() < wanted; i++) {
                List<SimPos> path = paths.get(i);
                if (path.size() < 2) {
                    continue;
                }
                SimPos end = path.get(path.size() - 1);
                SimPos before = path.get(path.size() - 2);
                double dx = end.x() - before.x();
                double dz = end.z() - before.z();
                double run = Math.hypot(dx, dz);
                if (run < 1) {
                    continue;
                }
                dx /= run;
                dz /= run;
                SimPos onward = new SimPos(
                        end.x() + (int) Math.round(dx * PITCH_ANY_BEARING), end.y(),
                        end.z() + (int) Math.round(dz * PITCH_ANY_BEARING));
                path.add(onward);
                grew = true;

                // Frontage either side of the new length, as anywhere else.
                for (int side : new int[] {-1, 1}) {
                    SimPos plot = new SimPos(
                            onward.x() + (int) Math.round(-dz * SETBACK * side), onward.y(),
                            onward.z() + (int) Math.round(dx * SETBACK * side));
                    if (taken.size() < wanted && fits(plot, taken, streets)) {
                        taken.add(new TownPlan.Plot(plot, Layout.DEFAULT_SPAN,
                                Layout.facingToward(plot, onward), frontage.get(i)));
                    }
                }
            }
        }

        // The streets, now longer. A plot that fronts an extension fronts a real
        // street with a real index, so everything downstream -- the router, the
        // refusal bookkeeping, the door that knows which way to look -- treats it
        // exactly as it treats the rest of the town.
        List<TownPlan.Street> grown = new ArrayList<>();
        for (int i = 0; i < streets.size(); i++) {
            TownPlan.Street was = streets.get(i);
            grown.add(paths.get(i).size() == was.path().size() ? was
                    : new TownPlan.Street(paths.get(i), was.width(), was.kind()));
        }

        // And if lengthening every street still will not do it, rings after all.
        // A layout must always be able to answer: plotFor once asked for plot
        // 117 of a plan holding 117 and ran off the end.
        int edge = OUTSKIRT_START;
        for (TownPlan.Plot placed : taken) {
            edge = Math.max(edge, Math.max(
                    Math.abs(placed.at().x() - centre.x()),
                    Math.abs(placed.at().z() - centre.z())));
        }
        for (int ring = 1; taken.size() < wanted; ring++) {
            int out = edge + ring * PITCH_ANY_BEARING;
            int around = Math.max(8, (int) (2 * Math.PI * out / PITCH_ANY_BEARING));
            for (int i = 0; i < around && taken.size() < wanted; i++) {
                double angle = i * 2 * Math.PI / around + ring * 0.7;
                SimPos where = new SimPos(
                        centre.x() + (int) Math.round(out * Math.cos(angle)), centre.y(),
                        centre.z() + (int) Math.round(out * Math.sin(angle)));
                if (fits(where, taken, grown)) {
                    taken.add(new TownPlan.Plot(where, Layout.DEFAULT_SPAN,
                            Layout.facingToward(where, centre), Layout.NO_STREET));
                }
            }
        }
        return new TownPlan(centre, grown, taken);
    }

    /**
     * How many times every street may be carried further out.
     *
     * <p>A bound rather than a budget: each pass lengthens every street by one
     * plot pitch, and a town of two hundred and fifty-six wants far fewer passes
     * than this. It is here so a plan that cannot place its last plot lengthens
     * its streets to the horizon rather than forever.
     */
    private static final int EXTENSIONS_ALLOWED = 64;

    /** Whether a plot may stand here: clear of its neighbours and of the road. */
    private static boolean fits(SimPos where, List<TownPlan.Plot> taken,
                                List<TownPlan.Street> streets) {
        for (TownPlan.Plot placed : taken) {
            if (!Layout.farEnoughApart(where, placed.at())) {
                return false;
            }
        }
        double half = Layout.DEFAULT_SPAN / 2.0 + KERB;
        for (TownPlan.Street street : streets) {
            if (street.touches(where, half)) {
                return false;
            }
        }
        return true;
    }

    private static long away(SimPos at, SimPos centre) {
        long dx = at.x() - centre.x();
        long dz = at.z() - centre.z();
        return dx * dx + dz * dz;
    }

    /** A north-south street, as the run of points its wander takes it through. */
    protected static TownPlan.Street northSouth(SimPos centre, Wander how, int base,
                                                int fromZ, int toZ, int width,
                                                TownPlan.Kind kind) {
        List<SimPos> path = new ArrayList<>();
        for (int z = fromZ; z < toZ; z += SEGMENT) {
            path.add(new SimPos(centre.x() + base + how.blocksAt(z), centre.y(),
                    centre.z() + z));
        }
        path.add(new SimPos(centre.x() + base + how.blocksAt(toZ), centre.y(),
                centre.z() + toZ));
        return new TownPlan.Street(path, width, kind);
    }

    /** An east-west street, likewise. */
    protected static TownPlan.Street eastWest(SimPos centre, Wander how, int base,
                                              int fromX, int toX, int width,
                                              TownPlan.Kind kind) {
        List<SimPos> path = new ArrayList<>();
        int step = fromX <= toX ? SEGMENT : -SEGMENT;
        for (int x = fromX; step > 0 ? x < toX : x > toX; x += step) {
            path.add(new SimPos(centre.x() + x, centre.y(),
                    centre.z() + base + how.blocksAt(x)));
        }
        path.add(new SimPos(centre.x() + toX, centre.y(),
                centre.z() + base + how.blocksAt(toX)));
        return new TownPlan.Street(path, width, kind);
    }

    /**
     * The wander of the nth street of a town, phased so nothing bends in step.
     *
     * <p>Seeded from the town's own centre, which is what stops every settlement
     * on the map having the identical kink in the identical place — a repeated
     * asset reads worse than a straight road.
     */
    protected static Wander wanderFor(Wander base, SimPos centre, int street) {
        long town = (long) centre.x() * 0x9E3779B97F4A7C15L
                ^ (long) centre.z() * 0xC2B2AE3D27D4EB4FL;
        return new Wander(base.amplitude(), base.wavelength(),
                base.seed() ^ town).forStreet(street);
    }
}
