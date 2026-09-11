package com.civilization.sim.culture;

import com.civilization.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A war camp: a great hut on the middle, a yard round it, huts facing in.
 *
 * <p>The orcs had two arrangements and both of them were rectangles — a lattice
 * on a counted pitch and the same discipline with the carriageways ruled in.
 * That is a fine thing for one people to build and a strange thing for the
 * <em>only</em> thing a people builds, because it says an orc settlement is a
 * garrison town and nothing else. This is what they build when they are living
 * somewhere rather than holding it: a ring of huts drawn round their chief.
 *
 * <h2>What makes it this shape rather than {@code ring_streets}</h2>
 *
 * <p>The vale folk's ring town is a Rundling — a road round a green with lanes
 * striking out <em>through</em> the green from the middle. Three things are
 * different here, and each of them is the difference between a village and a
 * camp:
 *
 * <ul>
 *   <li><strong>The yard is whole.</strong> No lane crosses it. The spokes start
 *       at the first ring road and run outward, so the ground inside the ring is
 *       one unbroken round of trampled earth with the great hut standing on it —
 *       which is what a muster ground is and what a green is not.</li>
 *   <li><strong>The middle is taken.</strong> The plan reserves the center, and
 *       offers are taken nearest-first, so the first thing an orc town builds
 *       stands on it. See {@link RadialStreetLayout} for the one arrangement
 *       that already did this and the honest note about which building actually
 *       lands there in a grown town.</li>
 *   <li><strong>It is tight.</strong> The first ring runs at
 *       {@link #FIRST_RING} rather than the forty a village leaves round its
 *       green, which is as close in as the separation rules will allow the huts
 *       on its inner face to stand. A camp is drawn round what it is defending.</li>
 * </ul>
 *
 * <p>Four spokes, because four is a gate on each quarter and the wall these
 * people build wants gates rather than junctions.
 *
 * <h2>The rules a curve costs you</h2>
 *
 * <p>Every constant below that is a distance on a circle is
 * {@link Layout#onACurve} of the straight-line one. That is rule R2 and it is
 * the single mistake that has cost this project most — see
 * {@link RadialStreetLayout#ARC_PITCH}'s neighbours for the three separate
 * times one file made it. Nothing here is a number somebody measured and liked;
 * the arc pitch, the ring spacing and the spoke start are all borrowed from the
 * arrangement that has already paid for them.
 */
public final class OrcRingLayout extends PlannedLayout {

    /**
     * Where the ring road runs, leaving the muster yard inside it.
     *
     * <p>Thirty-four rather than the village's forty, and it is about as tight
     * as this can be. The huts on the yard rim stand a {@link #RING_SETBACK} in
     * from the road, at twenty, and that rim has to clear two things: the
     * {@link #SPOKE_START} clearance a road keeps off a plot's corner, and the
     * great hut standing on the middle. Twenty clears both, the second with room
     * to spare — a great hut reserves fifteen blocks of ground and a hut nine,
     * so their claims meet at twelve and the nearest hut is eight blocks further
     * out than that.
     */
    private static final int FIRST_RING = 34;

    /**
     * How much of a ring one hut takes, measured along the arc.
     *
     * <p>{@link Layout#onACurve} of a separation and two blocks for the rounding
     * of each of the two plot centers to whole blocks. Identical to the village
     * ring's, and deliberately not re-derived: a second spelling of a sum is a
     * second thing to get wrong, and the two blocks on the end of this one are
     * measured rather than derived — see {@link RadialStreetLayout} for what
     * tightening them costs on real ground.
     */
    private static final int ARC_PITCH = Layout.onACurve(MIN_PLOT_SEPARATION) + 2;

    /**
     * How far a hut's middle stands from the ring road it fronts.
     *
     * <p>A block deeper than the {@link #SETBACK} a straight street wants, and
     * the block is rule R2 again in the one place nobody had applied it: not to
     * the spacing <em>along</em> the arc, but to the depth <em>across</em> it.
     *
     * <p>A plot is refused when its square comes within a half-carriageway of
     * the road, and a square reaches {@link Layout#onACurve} of its half-width
     * at the corners. On a straight street the corner points along the road and
     * costs nothing. On a ring it points at the road — radially, on every
     * diagonal — so the clearance a plot actually needs is
     * {@code ROAD_HALF + onACurve(DEFAULT_SPAN / 2 + CURB)}, which comes to
     * thirteen exactly, and a setback of thirteen therefore clears by nothing at
     * all. One block more.
     *
     * <p>It is not a tuning. Measured on the yard rim at the ordinary setback,
     * <strong>four huts of eight stood</strong> — the four on the axes, where the
     * square's corner points along the road, and none of the four on the
     * diagonals. Half of every ring face in the camp, quietly, with the plan
     * reporting full frontage the whole time: frontage counts the plots that
     * were <em>taken</em>, so a refused offer is invisible to it. The camp made
     * up the difference by opening another ring further out and reaching a
     * hundred and seventeen blocks to hold sixty-four huts.
     */
    private static final int RING_SETBACK =
            ROAD_HALF + Layout.onACurve(Layout.DEFAULT_SPAN / 2 + CURB) + 1;

    /** How far apart the rings run: two setbacks and what the curve wants between. */
    private static final int RING_SPACING = 2 * RING_SETBACK + ARC_PITCH + 2;

    /** How many lanes strike outward from the ring road. */
    private static final int SPOKES = 4;

    /**
     * How many huts stand round the yard.
     *
     * <p>The floor every ring face is counted up from, and on the first ring it
     * is the count: a circle of {@code FIRST_RING - RING_SETBACK} has room for
     * seven at the {@link #ARC_PITCH} and eight is what actually fits, because
     * the pitch is a bound on the arc and the separation is measured on the
     * wider axis. Eight is also what the gates are placed against — see
     * {@link #bearingOf}.
     */
    private static final int YARD_HUTS = 8;

    /**
     * How far out from the middle a road may come, with the hall on the middle.
     *
     * <p>The plan refuses a plot within {@code DEFAULT_SPAN / 2 + CURB} of a
     * carriageway and that clearance is a <em>square</em>, so a lane leaving on a
     * diagonal has to clear a corner standing root two further out than a face.
     * Nothing here actually comes that close — the spokes start at the ring road —
     * but the yard's inner frontage is checked against it, because a face inside
     * this is a face the middle plot's own keepout would eat.
     */
    private static final int SPOKE_START =
            Layout.onACurve(Layout.DEFAULT_SPAN / 2 + CURB) + ROAD_HALF + 1;

    /**
     * How far the ring breathes in and out over its circuit.
     *
     * <p>Small, and it is not decoration. A perfectly circular road is the most
     * obviously computer-generated thing a town can have, and it is worse here
     * than anywhere because the wall is drawn round the hull of these plots — a
     * true circle of huts gives a true circle of palisade, which reads as a
     * fairground. Five blocks over a wavelength the {@link Wander#gentle} rule
     * picks keeps every plot fronting its road (the slope bar is 0.25 and this
     * is exactly that) and makes a stockade that went round something.
     *
     * <p>It is also what stops two orc camps being the same camp twice: the
     * wander is re-seeded from the town's own center by
     * {@link PlannedLayout#wanderFor}.
     */
    private static final Wander PALISADE = Wander.gentle(5, 0xC0DEFACEL);

    private final String id;
    private final Wander wander;

    public OrcRingLayout() {
        this(Culture.LAYOUT_ORC_RING, PALISADE);
    }

    public OrcRingLayout(String id) {
        this(id, PALISADE);
    }

    public OrcRingLayout(String id, Wander wander) {
        this.id = id;
        this.wander = wander;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isSameShapeEverywhere() {
        return wander.amplitude() == 0;
    }

    public Wander wander() {
        return wander;
    }

    @Override
    protected void design(SimPos center, int wanted,
                          List<TownPlan.Street> streets, List<Offer> offers) {
        // Enough rings to hold the camp, counted rather than guessed. A ring at
        // radius r carries about 2*pi*r/ARC_PITCH huts on each of its two faces,
        // and that grows with the radius -- so a formula in the plot count alone
        // either runs out of frontage or draws rings nothing ever fronts.
        int rings = 0;
        int room = 1;   // the great hut on the middle
        while (room < wanted && rings < 64) {
            int radius = FIRST_RING + rings * RING_SPACING;
            room += 2 * (int) (2 * Math.PI * radius / ARC_PITCH);
            rings++;
        }
        rings = Math.max(1, rings);
        int outer = FIRST_RING + (rings - 1) * RING_SPACING;

        for (int ring = 0; ring < rings; ring++) {
            streets.add(ringRoad(center, wanderFor(wander, center, ring),
                    FIRST_RING + ring * RING_SPACING));
        }
        // Spokes last, so ring indices stay put as a camp grows and the doors
        // that face them keep facing them.
        int firstSpoke = streets.size();
        for (int spoke = 0; spoke < SPOKES; spoke++) {
            streets.add(spokeRoad(center, bearingOf(spoke), outer + RING_SPACING / 2));
        }

        // The middle of the yard. Offered first because it is nearest the
        // center, and the nearest offer is the one the first building takes.
        //
        // It fronts no street and deliberately: the nearest carriageway is the
        // ring road a whole yard away, so there is nothing here to face, and
        // naming one would only drag the great hut off the middle when a
        // renderer brought it up to that street's curb. A camp drawn round a
        // chief spends one plot's frontage on having a chief.
        offers.add(new Offer(center, Layout.NO_STREET,
                Layout.facingToward(center, round(center, FIRST_RING, 0))));

        // Frontage on both faces of every ring. The inner faces are nearer the
        // middle than anything else on their own ring, so the nearest-first sort
        // in PlannedLayout.take fills them first without being told to -- which
        // is the whole of "the huts face in". Only once the yard is ringed does
        // the camp start building on the outside of its own road.
        //
        // Each face is spaced on ITS OWN radius, not the ring's. The inner face
        // is a shorter circle than the road it fronts -- thirteen blocks shorter
        // in radius, so at the first ring its circumference is five eighths of
        // the centerline's. Spacing both faces by the centerline packs the inner
        // one below the separation on the diagonals, which is the fault that
        // took a village's frontage to a fifth.
        for (int ring = 0; ring < rings; ring++) {
            int radius = FIRST_RING + ring * RING_SPACING;
            Wander how = wanderFor(wander, center, ring);
            for (int side : new int[] {-1, 1}) {
                int face = radius + side * RING_SETBACK;
                if (face < SPOKE_START) {
                    continue;   // inside the yard, where nothing is offered
                }
                int around = Math.max(YARD_HUTS,
                        (int) (2 * Math.PI * face / ARC_PITCH));
                for (int i = 0; i < around; i++) {
                    double angle = i * 2 * Math.PI / around;
                    double bent = radius + how.offsetAt(angle * radius);
                    SimPos where = round(center, bent + side * RING_SETBACK, angle);
                    offers.add(new Offer(where, ring,
                            Layout.facingToward(where, round(center, bent, angle))));
                }
            }
        }

        // And frontage along the spokes, which is what makes them streets rather
        // than obstacles. Left bare they cost twice over: they refuse every ring
        // plot they cross AND offer nothing back.
        //
        // Pitched for the spoke's own bearing rather than by the straight-street
        // PITCH. A spoke IS straight, and straight is not the same as
        // axis-aligned: two offers a pitch apart along a lane leaving at sixty
        // degrees are only pitch times the cosine apart on the wider axis.
        for (int spoke = 0; spoke < SPOKES; spoke++) {
            double angle = bearingOf(spoke);
            double outX = Math.cos(angle);
            double outZ = Math.sin(angle);
            int along = (int) Math.ceil(
                    MIN_PLOT_SEPARATION / Math.max(Math.abs(outX), Math.abs(outZ)));
            for (int t = FIRST_RING; t < outer + RING_SPACING / 2; t += along) {
                for (int side : new int[] {-1, 1}) {
                    SimPos where = new SimPos(
                            center.x() + (int) Math.round(t * outX - side * SETBACK * outZ),
                            center.y(),
                            center.z() + (int) Math.round(t * outZ + side * SETBACK * outX));
                    SimPos onRoad = new SimPos(
                            center.x() + (int) Math.round(t * outX), center.y(),
                            center.z() + (int) Math.round(t * outZ));
                    offers.add(new Offer(where, firstSpoke + spoke,
                            Layout.facingToward(where, onRoad)));
                }
            }
        }
    }

    /**
     * Which way the nth gate faces.
     *
     * <p>Turned half a hut off the axes, and the half-hut is load bearing rather
     * than stylish. The yard rim carries {@link #YARD_HUTS} of them, so they
     * stand on the axes <em>and</em> on the diagonals; four gates on the axes
     * would butt into four of those eight and four on the diagonals into the
     * other four — which is exactly what the first cut of this did, and it read
     * as a camp that had mislaid half its huts. Half of the rim's own angular
     * step puts every gate in a gap.
     *
     * <p>So it is derived from {@link #YARD_HUTS} rather than from
     * {@link #SPOKES}: the thing a gate has to miss is a hut, not another gate.
     */
    private static double bearingOf(int spoke) {
        return spoke * 2 * Math.PI / SPOKES + Math.PI / YARD_HUTS;
    }

    /** One ring, as the run of points the road round the yard passes through. */
    private static TownPlan.Street ringRoad(SimPos center, Wander how, int radius) {
        List<SimPos> path = new ArrayList<>();
        int around = Math.max(12, (int) (2 * Math.PI * radius / SEGMENT));
        for (int i = 0; i <= around; i++) {
            double angle = i * 2 * Math.PI / around;
            path.add(round(center, radius + how.offsetAt(angle * radius), angle));
        }
        return new TownPlan.Street(path, ROAD_HALF * 2, TownPlan.Kind.LANE);
    }

    /**
     * A lane striking outward from the ring road.
     *
     * <p>Begins at the ring rather than at the middle, which is the one geometric
     * difference that makes this a camp and not a Rundling: the yard is left
     * whole, and the great hut on it is reached across open ground rather than
     * down a street.
     */
    private static TownPlan.Street spokeRoad(SimPos center, double angle, int out) {
        return new TownPlan.Street(
                round(center, FIRST_RING, angle), round(center, out, angle),
                ROAD_HALF * 2, TownPlan.Kind.SPINE);
    }

    private static SimPos round(SimPos center, double radius, double angle) {
        return new SimPos(
                center.x() + (int) Math.round(radius * Math.cos(angle)), center.y(),
                center.z() + (int) Math.round(radius * Math.sin(angle)));
    }
}
