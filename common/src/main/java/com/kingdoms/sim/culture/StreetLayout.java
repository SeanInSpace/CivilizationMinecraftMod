package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A town laid along a street, with the street known first.
 *
 * <p>The first arrangement here that is a <em>plan</em> rather than a sequence.
 * Every other one answers "where does the nth building go" and has no opinion
 * about roads, because it has nowhere to keep one. This builds a
 * {@link TownPlan} — a spine, a market widening on it, side lanes, a back lane
 * behind the western frontage — and then hands out the plots that front them.
 * {@link #plotFor} is a <em>view</em> of that plan, instead of the plan being an
 * afterthought of {@code plotFor}.
 *
 * <p>It is also the first arrangement whose doors mean anything. Everywhere else
 * a building turns to face the middle of town on principle, which is why a
 * street in this simulation has houses presenting their backs to it. Here a plot
 * knows which street it fronts and looks at that.
 *
 * <p>Grown unevenly on purpose. Real settlements do not have four equal arms:
 * the side toward the crossing runs for centuries and the side toward the moor
 * stops after six houses. Three plots south for every one north is what that
 * looks like from above.
 *
 * <p><strong>The streets bend.</strong> How much is a {@link Wander} the culture
 * hands over, so the same machinery lays a surveyor's grid at amplitude nought
 * and a cart-track village at seven. Crucially the bend is in the
 * <em>centreline</em>, which the frontage is measured from — curving the drawn
 * road and leaving the plots on the straight line closes the setback and puts
 * houses in the carriageway, which is the fault this layout has already had once.
 *
 * <p><strong>Not burgage plots yet.</strong> A real street town is narrow
 * frontages packed shoulder to shoulder, and that needs a plot which is a
 * rectangle with an orientation — the siting code still measures a square. So
 * this is a street town at square-plot spacing: the arrangement is right, the
 * density is not, and the density waits on the plot model.
 */
public final class StreetLayout implements Layout {

    /** Half-width of the carriageway, so a plot knows how far to stand back. */
    private static final int ROAD_HALF = 4;

    /** How far a plot's middle sits from the street's middle. */
    private static final int SETBACK = 13;

    /** Frontage taken by one plot along a street. */
    private static final int PITCH = 14;

    /** How far the market widening reaches along the spine, either way. */
    private static final int MARKET_REACH = 16;

    /** How much further back the town stands where the market widens it. */
    private static final int MARKET_EXTRA = 12;

    /** How far along the spine plots are offered before lanes take over. */
    private static final int SPINE_ROWS = 9;

    /**
     * How far apart successive lanes leave the spine.
     *
     * <p>Cannot go below twice the setback plus a separation: plots on adjacent
     * lanes face each other across the gap, and at thirty they land four blocks
     * apart and foul. Forty is the tightest that holds.
     */
    private static final int LANE_SPACING = 40;

    /** How far west of the spine the back lane runs. */
    private static final int BACK_AT = 40;

    /** The least an outskirt ring stands out, before the town's own reach. */
    private static final int OUTSKIRT_START = 60;

    /** Plans kept. More than a handful is a server's worth of towns. */
    private static final int TOWNS_REMEMBERED = 8;

    /**
     * How far apart the points of a bending street are set.
     *
     * <p>Eight blocks is short enough that the bend reads as a curve on the
     * ground rather than a dogleg, and long enough that a town does not carry
     * three hundred points it has no use for.
     */
    private static final int SEGMENT = 8;

    /** Bare ground between a wall and a carriageway, so a door has a doorstep. */
    private static final int KERB = 1;

    /** The spine is always the plan's first street. */
    private static final int SPINE = 0;

    /** Lanes occupy every index from here; the back lane is appended after them. */
    private static final int LANE_FIRST = 1;

    private final String id;
    private final Wander wander;
    private final Map<String, TownPlan> planned = new LinkedHashMap<>();

    /** The default: a market town whose streets were cart tracks first. */
    public StreetLayout() {
        this("high_street", Wander.gentle(11, 0x5EED1EL));
    }

    public StreetLayout(String id, Wander wander) {
        this.id = id;
        this.wander = wander;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isSameShapeEverywhere() {
        // A straight-edged plan is; a wandering one takes its bends from the
        // town's own centre so that no two settlements kink alike.
        return wander.amplitude() == 0;
    }

    /** How much this arrangement's streets stray, for tests and for the viewer. */
    public Wander wander() {
        return wander;
    }

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
     * The wander of the nth street of a town, which both the road and its
     * frontage are read from.
     *
     * <p>Phased per street and per town, so no two lanes in a settlement bend in
     * step and no two settlements bend alike. Deterministic in the centre, which
     * is what keeps a reloaded town the same town.
     */
    private Wander wanderFor(SimPos centre, int street) {
        long town = (long) centre.x() * 0x9E3779B97F4A7C15L
                ^ (long) centre.z() * 0xC2B2AE3D27D4EB4FL;
        return new Wander(wander.amplitude(), wander.wavelength(),
                wander.seed() ^ town).forStreet(street);
    }

    /**
     * Streets first, then the frontage on them, in the order a town fills.
     *
     * <p>Candidates are offered in growth order and each is taken only if it
     * clears everything already placed. That is what keeps the three rules
     * without hand-solving the geometry: a candidate that would foul a neighbour
     * is never given a slot, exactly as the siting code would refuse it later.
     * The ring layout's innermost course and the warren's clumps were both
     * arithmetic that looked right and was not; this cannot be wrong in that way
     * because it checks.
     */
    private TownPlan lay(SimPos centre, int wanted) {
        int reach = MARKET_REACH + PITCH * (wanted / 2 + 6);
        int lanes = lanesFor(wanted);
        int backIndex = LANE_FIRST + lanes;

        List<TownPlan.Street> streets = new ArrayList<>();

        // The spine, running north-south, bending as it goes.
        Wander spine = wanderFor(centre, SPINE);
        streets.add(northSouth(centre, spine, 0, -reach / 3, reach,
                ROAD_HALF * 2, TownPlan.Kind.SPINE));

        // Lanes east and west off it, each leaving the spine where the spine
        // actually is rather than where a straight one would have been.
        for (int lane = 0; lane < lanes; lane++) {
            int laneZ = laneZ(lane);
            int side = (lane % 2 == 0) ? 1 : -1;
            int from = spine.blocksAt(laneZ);
            int to = side * (SETBACK + ROAD_HALF + PITCH * laneDepthFor(wanted));
            streets.add(eastWest(centre, wanderFor(centre, LANE_FIRST + lane), laneZ,
                    from, to, ROAD_HALF * 2, TownPlan.Kind.LANE));
        }

        // The back lane, behind the western frontage.
        streets.add(northSouth(centre, wanderFor(centre, backIndex), -BACK_AT,
                -reach / 4, reach / 2, ROAD_HALF, TownPlan.Kind.BACK));

        // Nearest frontage first.
        //
        // The offers come out in the order a town opens its streets -- market,
        // spine, then lane after lane -- and taking them in that order makes the
        // reach grow with the plot COUNT: thirty-four lanes at forty blocks
        // apart is a town seven hundred blocks long, and a settlement that
        // refuses ground for terrain asks for plot four hundred while building a
        // hundred. Sorting by distance makes the reach grow with the square root
        // instead, which is what a town filling out actually does.
        //
        // The character survives it. The market is nearest and still goes first,
        // the spine next, the lanes outward in turn -- and every plot still
        // fronts the street it was offered on.
        List<int[]> ordered = new ArrayList<>(offers(centre, wanted));
        ordered.sort((a, b) -> {
            long da = (long) a[0] * a[0] + (long) a[1] * a[1];
            long db = (long) b[0] * b[0] + (long) b[1] * b[1];
            if (da != db) {
                return Long.compare(da, db);
            }
            return a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
        });

        List<TownPlan.Plot> taken = new ArrayList<>();
        for (int[] offer : ordered) {
            SimPos where = at(centre, offer[0], offer[1]);
            boolean clear = true;
            for (TownPlan.Plot placed : taken) {
                if (!Layout.farEnoughApart(where, placed.at())) {
                    clear = false;
                    break;
                }
            }
            if (!clear || standsOnAStreet(where, Layout.DEFAULT_SPAN, streets)) {
                continue;
            }
            taken.add(new TownPlan.Plot(where, Layout.DEFAULT_SPAN, offer[3], offer[2]));
            if (taken.size() >= wanted) {
                break;
            }
        }
        // A layout must always be able to answer, and the offers are a finite
        // list a town can outgrow -- plotFor once asked for plot 117 of a plan
        // holding 117 and ran off the end. What is added past the plan is added
        // in rings around it rather than along a line: an outskirt, which is
        // what a town that has used up its streets actually grows.
        // Rings that start where the streets actually end, not at a fixed
        // distance. Siting scans up to ninety-six candidates for every building
        // it places, so a town of a hundred asks for plot four hundred -- and an
        // outskirt measured from a constant then marches away from the town it
        // belongs to. Measured from what is already taken, the reach grows with
        // the square root of the count, which is what a town does.
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
                SimPos where = at(centre,
                        (int) Math.round(out * Math.cos(angle)),
                        (int) Math.round(out * Math.sin(angle)));
                boolean clear = true;
                for (TownPlan.Plot placed : taken) {
                    if (!Layout.farEnoughApart(where, placed.at())) {
                        clear = false;
                        break;
                    }
                }
                if (clear && !standsOnAStreet(where, Layout.DEFAULT_SPAN, streets)) {
                    taken.add(new TownPlan.Plot(where, Layout.DEFAULT_SPAN,
                            Layout.facingToward(where, centre), -1));
                }
            }
        }
        return new TownPlan(centre, streets, taken);
    }

    /**
     * A north-south street, as the run of points its wander takes it through.
     *
     * @param base the straight line's x, which the wander is measured off
     */
    private TownPlan.Street northSouth(SimPos centre, Wander how, int base,
                                       int fromZ, int toZ, int width, TownPlan.Kind kind) {
        List<SimPos> path = new ArrayList<>();
        for (int z = fromZ; z < toZ; z += SEGMENT) {
            path.add(at(centre, base + how.blocksAt(z), z));
        }
        path.add(at(centre, base + how.blocksAt(toZ), toZ));
        return new TownPlan.Street(path, width, kind);
    }

    /** An east-west street, likewise. */
    private TownPlan.Street eastWest(SimPos centre, Wander how, int base,
                                     int fromX, int toX, int width, TownPlan.Kind kind) {
        List<SimPos> path = new ArrayList<>();
        int step = fromX <= toX ? SEGMENT : -SEGMENT;
        for (int x = fromX; step > 0 ? x < toX : x > toX; x += step) {
            path.add(at(centre, x, base + how.blocksAt(x)));
        }
        path.add(at(centre, toX, base + how.blocksAt(toX)));
        return new TownPlan.Street(path, width, kind);
    }

    /**
     * Whether a plot would stand on a carriageway.
     *
     * <p>The plan checked plots against each other and never against its own
     * streets, so it laid houses in the road: a spine plot at z=44 sat on a lane
     * running through z=36 to 44, and a west lane plot at x=-38 sat on the back
     * lane at x=-40. Fifteen of a hundred and forty on the largest plan.
     *
     * <p>It is the sort of fault that had nowhere to be seen before there was a
     * plan to look at. A settlement would have built those houses, the path
     * layer would have paved through them, and the only report would have been a
     * player wondering why the street went inside somebody's front room.
     *
     * <p>The geometry belongs to {@link TownPlan.Street#touches} so that this and
     * the invariant that checks the finished plan cannot drift apart.
     */
    private static boolean standsOnAStreet(SimPos at, int span,
                                           List<TownPlan.Street> streets) {
        double half = span / 2.0 + KERB;
        for (TownPlan.Street street : streets) {
            if (street.touches(at, half)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every frontage this plan offers, in the order a town would take them.
     *
     * <p>Each offer is {@code {dx, dz, street, facing}}. The position is read off
     * the same wander the street was drawn from, so the setback is a setback from
     * where the road <em>is</em> rather than from where a straight one would have
     * been.
     */
    private List<int[]> offers(SimPos centre, int wanted) {
        List<int[]> out = new ArrayList<>();
        int lanes = lanesFor(wanted);
        Wander spine = wanderFor(centre, SPINE);

        // The market widening. The best frontage in the town and taken first,
        // which is why the buildings that matter end up on it.
        for (int k = 0; k * PITCH <= MARKET_REACH; k++) {
            for (int sign : new int[] {1, -1}) {
                int z = sign * (PITCH / 2 + k * PITCH);
                int bend = spine.blocksAt(z);
                out.add(new int[] {bend - (SETBACK + MARKET_EXTRA), z, SPINE, 1});
                out.add(new int[] {bend + SETBACK + MARKET_EXTRA, z, SPINE, 3});
            }
        }

        // The spine, and lanes off it as the town needs more frontage.
        //
        // The first draft ran the spine out and nothing else, and a hundred
        // buildings on one street is a street a hundred buildings long: it
        // measured 506 blocks end to end, worse than the warren it was meant to
        // improve on. A real town does not answer growth by getting longer, it
        // answers it by opening another street. So the spine runs a bounded
        // distance and then the lanes multiply.
        int perArm = Math.min(SPINE_ROWS, wanted / 2 + 4);
        int south = 0;
        int north = 0;
        for (int step = 0; step < perArm; step++) {
            int z;
            if (step % 4 == 3) {
                z = -(MARKET_REACH + PITCH + north * PITCH);
                north++;
            } else {
                z = MARKET_REACH + PITCH + south * PITCH;
                south++;
            }
            int bend = spine.blocksAt(z);
            out.add(new int[] {bend - SETBACK, z, SPINE, 1});
            out.add(new int[] {bend + SETBACK, z, SPINE, 3});
        }

        // Lanes east off the spine, opened in turn, each filling before the next
        // is wanted. Alternating sides so the town does not grow lopsided in one
        // direction only.
        //
        // Each lane is its OWN street index. It used to hand every lane plot the
        // index 1, so a plan of nine streets reported all of its lane frontage as
        // fronting the first lane -- which counted as a hundred per cent frontage
        // and was, but pointed a third of the town at the wrong road.
        for (int lane = 0; lane < lanes; lane++) {
            int laneZ = laneZ(lane);
            int side = (lane % 2 == 0) ? 1 : -1;
            Wander how = wanderFor(centre, LANE_FIRST + lane);
            for (int k = 0; k < laneDepthFor(wanted); k++) {
                int x = side * (SETBACK + ROAD_HALF + PITCH / 2 + k * PITCH);
                int bend = laneZ + how.blocksAt(x);
                out.add(new int[] {x, bend - SETBACK, LANE_FIRST + lane, 2});
                out.add(new int[] {x, bend + SETBACK, LANE_FIRST + lane, 0});
            }
        }

        // The back lane, far side only: the near side is the backs of the
        // western frontage, which is what a back lane is for.
        // Bounded to the town's own depth. Running it out as far as the plot
        // count would let a town of a hundred take frontage seventeen hundred
        // blocks down a single lane, which measured 451 across and is a road,
        // not a settlement.
        int backIndex = LANE_FIRST + lanes;
        Wander back = wanderFor(centre, backIndex);
        int depth = (lanes / 2 + 1) * LANE_SPACING / PITCH + 3;
        for (int k = -depth; k < depth; k++) {
            int z = PITCH / 2 + k * PITCH;
            out.add(new int[] {-BACK_AT + back.blocksAt(z) - SETBACK, z, backIndex, 1});
        }
        return out;
    }

    /**
     * How many plots deep a lane runs, each side, for a town of this size.
     *
     * <p>Scaled with the count rather than fixed. At a fixed depth a plan can
     * only grow by opening more lanes, and lanes sit forty blocks apart: four
     * hundred plots wanted thirty-four of them and made a town seven hundred
     * blocks long. Deepening the lanes as they multiply keeps the plan roughly
     * square, which is the difference between a town and a ribbon.
     */
    private static int laneDepthFor(int wanted) {
        return Math.max(4, (int) Math.ceil(1.43 * lanesFor(wanted)));
    }

    /**
     * How many lanes a town of this size opens.
     *
     * <p>Grown so the town stays roughly square rather than running away in one
     * direction. Plots come to about {@code lanes * depth * 2} and a square town
     * wants {@code depth} about {@code LANE_SPACING / PITCH} times {@code lanes},
     * so the count is the square root rather than a division. The first draft
     * opened one lane and let the spine take everything, which measured 506
     * blocks end to end; the second multiplied lanes at a fixed depth and
     * reached 660.
     */
    private static int lanesFor(int wanted) {
        return Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, wanted) / 2.86)));
    }

    /** Where the nth lane leaves the spine, walking outward from the market. */
    private static int laneZ(int lane) {
        int step = (lane / 2 + 1) * LANE_SPACING;
        return (lane % 2 == 0) ? step : -step;
    }

    private static SimPos at(SimPos centre, int dx, int dz) {
        return new SimPos(centre.x() + dx, centre.y(), centre.z() + dz);
    }
}
