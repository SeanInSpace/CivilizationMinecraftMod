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

    /** Frontage taken by one plot along a street. */
    protected static final int PITCH = 14;

    /** How far a plot's middle sits from the street's middle. */
    protected static final int SETBACK = 13;

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

    @Override
    public TownPlan planFor(SimPos centre, int wanted) {
        int want = Math.max(1, wanted);
        synchronized (planned) {
            String key = centre.x() + ":" + centre.z();
            TownPlan held = planned.get(key);
            if (held != null && held.size() >= want) {
                return held;
            }
            // Laid whole and laid again when the town outgrows it. A plan is
            // cheap and a settlement that has to ask for plot four hundred has
            // asked for the three hundred and ninety-nine before it anyway.
            TownPlan made = lay(centre, Math.max(want, 32));
            if (planned.size() > TOWNS_REMEMBERED) {
                planned.clear();
            }
            planned.put(key, made);
            return made;
        }
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
        List<TownPlan.Street> streets = new ArrayList<>();
        List<Offer> offers = new ArrayList<>();
        design(centre, wanted, streets, offers);

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

        // A layout must always be able to answer, and the offers are a finite
        // list a town can outgrow -- plotFor once asked for plot 117 of a plan
        // holding 117 and ran off the end. What is added past the plan is added
        // in rings around it rather than along a line: an outskirt, which is what
        // a town that has used up its streets actually grows. Measured from what
        // is already taken rather than from a constant, or a town of a hundred
        // puts its outskirt somewhere it will never reach.
        int edge = OUTSKIRT_START;
        for (TownPlan.Plot placed : taken) {
            edge = Math.max(edge, Math.max(
                    Math.abs(placed.at().x() - centre.x()),
                    Math.abs(placed.at().z() - centre.z())));
        }
        for (int ring = 1; taken.size() < wanted; ring++) {
            int out = edge + ring * PITCH;
            int around = Math.max(8, (int) (2 * Math.PI * out / PITCH));
            for (int i = 0; i < around && taken.size() < wanted; i++) {
                double angle = i * 2 * Math.PI / around + ring * 0.7;
                SimPos where = new SimPos(
                        centre.x() + (int) Math.round(out * Math.cos(angle)), centre.y(),
                        centre.z() + (int) Math.round(out * Math.sin(angle)));
                if (fits(where, taken, streets)) {
                    taken.add(new TownPlan.Plot(where, Layout.DEFAULT_SPAN,
                            Layout.facingToward(where, centre), -1));
                }
            }
        }
        return new TownPlan(centre, streets, taken);
    }

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
