package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.List;

/**
 * A town laid along a street, with the street known first.
 *
 * <p>The first arrangement here that was a <em>plan</em> rather than a sequence.
 * A lattice answers "where does the nth building go" and has no opinion about
 * roads, because it has nowhere to keep one. This describes a spine, a market
 * widening on it, side lanes, and a back lane behind the western frontage — and
 * then hands out the plots that front them. {@link #plotFor} is a <em>view</em>
 * of that plan, instead of the plan being an afterthought of {@code plotFor}.
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
 * and a cart-track village at eleven. The bend is in the <em>centreline</em>,
 * which the frontage is measured from.
 *
 * <p><strong>Not burgage plots yet.</strong> A real street town is narrow
 * frontages packed shoulder to shoulder, and that needs a plot which is a
 * rectangle with an orientation — the siting code still measures a square. So
 * this is a street town at square-plot spacing: the arrangement is right, the
 * density is not, and the density waits on the plot model.
 */
public final class StreetLayout extends PlannedLayout {

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
     * apart and foul. That sum is {@link #BACK_TO_BACK}: twice the setback and a
     * separation, which is where the forty this held came from and where the
     * thirty-seven it holds now comes from. It was written as a literal with the
     * sum in its own comment, which is a number that goes stale silently the day
     * the separation moves.
     */
    private static final int LANE_SPACING = BACK_TO_BACK;

    /**
     * How far west of the spine the back lane runs.
     *
     * <p>The same sum again, and not a coincidence that it was the same number:
     * the back lane's own frontage stands a setback west of it and the spine's
     * western rank stands a setback east of it, so the two ranks are back to
     * back across exactly the gap {@link #BACK_TO_BACK} names.
     */
    private static final int BACK_AT = BACK_TO_BACK;

    /** The spine is always the plan's first street. */
    private static final int SPINE = 0;

    /** Lanes occupy every index from here; the back lane is appended after them. */
    private static final int LANE_FIRST = 1;

    private final String id;
    private final Wander wander;

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
    protected void design(SimPos centre, int wanted,
                          List<TownPlan.Street> streets, List<Offer> offers) {
        int reach = MARKET_REACH + PITCH * (wanted / 2 + 6);
        int lanes = lanesFor(wanted);
        int backIndex = LANE_FIRST + lanes;
        int depth = laneDepthFor(wanted);

        // The spine, running north-south, bending as it goes.
        Wander spine = wanderFor(wander, centre, SPINE);
        streets.add(northSouth(centre, spine, 0, -reach / 3, reach,
                ROAD_HALF * 2, TownPlan.Kind.SPINE));

        // Lanes off it, each leaving the spine where the spine actually is
        // rather than where a straight one would have been.
        for (int lane = 0; lane < lanes; lane++) {
            int laneZ = laneZ(lane);
            int side = (lane % 2 == 0) ? 1 : -1;
            streets.add(eastWest(centre, wanderFor(wander, centre, LANE_FIRST + lane),
                    laneZ, spine.blocksAt(laneZ),
                    side * (SETBACK + ROAD_HALF + PITCH * depth),
                    ROAD_HALF * 2, TownPlan.Kind.LANE));
        }

        // The back lane, behind the western frontage.
        Wander back = wanderFor(wander, centre, backIndex);
        streets.add(northSouth(centre, back, -BACK_AT, -reach / 4, reach / 2,
                ROAD_HALF, TownPlan.Kind.BACK));

        // The market widening. The best frontage in the town, and nearest the
        // centre, so the sort takes it first and the buildings that matter end
        // up on it.
        for (int k = 0; k * PITCH <= MARKET_REACH; k++) {
            for (int sign : new int[] {1, -1}) {
                int z = sign * (HALF_PITCH + k * PITCH);
                int bend = spine.blocksAt(z);
                offers.add(new Offer(at(centre, bend - (SETBACK + MARKET_EXTRA), z), SPINE, 1));
                offers.add(new Offer(at(centre, bend + SETBACK + MARKET_EXTRA, z), SPINE, 3));
            }
        }

        // The spine's own frontage, three plots south for every one north.
        //
        // Bounded, then the lanes multiply. The first draft ran the spine out and
        // nothing else, and a hundred buildings on one street is a street a
        // hundred buildings long: 506 blocks end to end, worse than the warren it
        // was meant to improve on. A town does not answer growth by getting
        // longer, it answers it by opening another street.
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
            offers.add(new Offer(at(centre, bend - SETBACK, z), SPINE, 1));
            offers.add(new Offer(at(centre, bend + SETBACK, z), SPINE, 3));
        }

        // Lanes, alternating sides so the town does not grow lopsided.
        //
        // Each lane is its OWN street index. It used to hand every lane plot the
        // index 1, so a plan of nine streets reported all of its lane frontage as
        // fronting the first lane -- which counted as frontage and was, but
        // pointed a third of the town at the wrong road.
        for (int lane = 0; lane < lanes; lane++) {
            int laneZ = laneZ(lane);
            int side = (lane % 2 == 0) ? 1 : -1;
            Wander how = wanderFor(wander, centre, LANE_FIRST + lane);
            for (int k = 0; k < depth; k++) {
                int x = side * (SETBACK + ROAD_HALF + HALF_PITCH + k * PITCH);
                int bend = laneZ + how.blocksAt(x);
                offers.add(new Offer(at(centre, x, bend - SETBACK), LANE_FIRST + lane, 2));
                offers.add(new Offer(at(centre, x, bend + SETBACK), LANE_FIRST + lane, 0));
            }
        }

        // The back lane, far side only: the near side is the backs of the western
        // frontage, which is what a back lane is for. Bounded to the town's own
        // depth, or a town of a hundred takes frontage seventeen hundred blocks
        // down one lane, which measured 451 across and is a road, not a town.
        int along = (lanes / 2 + 1) * LANE_SPACING / PITCH + 3;
        for (int k = -along; k < along; k++) {
            int z = HALF_PITCH + k * PITCH;
            offers.add(new Offer(
                    at(centre, -BACK_AT + back.blocksAt(z) - SETBACK, z), backIndex, 1));
        }
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
     * so the count is the square root rather than a division.
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
