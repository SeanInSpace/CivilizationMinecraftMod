package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Ring roads round a green, with lanes running out from it.
 *
 * <p>The streets-first answer to the ring lattice, which places its buildings on
 * concentric circles and has no roads at all — so a ring town's houses stand in
 * rings and its paths are threaded between them afterwards by whatever route is
 * left over. Here the rings <em>are</em> the streets and the houses take frontage
 * on them, inside and out.
 *
 * <p>This is a real settlement form and a common one: a Rundling or Angerdorf is
 * a ring of frontage round a green with the lanes radiating outward, and the
 * medieval towns that grew round a market or a castle bailey ended up the same
 * shape by accretion — a road round the obstacle, and roads leaving it.
 *
 * <p>Wanders by default, and it matters more here than anywhere else: a
 * perfectly circular street is the single most obviously computer-generated
 * thing a town can have. A ring that breathes in and out by a few blocks over
 * its circuit reads as a road that went round something.
 */
public final class RadialStreetLayout extends PlannedLayout {

    /** Where the innermost ring runs, leaving the green inside it. */
    private static final int FIRST_RING = 40;

    /**
     * How far apart the rings run.
     *
     * <p>Twice the setback puts twenty-six blocks between the outer face of one
     * ring and the inner face of the next, and the remainder is the gap those two
     * rows of houses look across. On a straight street twelve would do. On a
     * circle it will not: two plots separated radially are separated on the wider
     * axis by only {@code gap / sqrt(2)} where the ring runs diagonally, so a gap
     * of fourteen is a separation of 9.9 and the whole diagonal quarter of every
     * ring is refused.
     *
     * <p>That is the third place on this one layout where a circle laid out in
     * straight-line distances was quietly wrong — along a face, between the two
     * faces of a ring, and now between neighbouring rings. Each cost about the
     * same: frontage fell to a fifth and the town ran outward hunting for room.
     * The rule is the same every time and worth stating once: on a curve, every
     * clearance must be the square root of two times what a straight street needs.
     */
    private static final int RING_SPACING = 46;

    /** How many lanes strike outward from the green. */
    private static final int SPOKES = 6;

    /**
     * How much of a ring one plot takes, measured along the arc.
     *
     * <p>Wider than the {@link #PITCH} used on a straight street, and it has to
     * be. Separation is measured on the wider axis, so two plots a chord apart on
     * a circle are only {@code chord / sqrt(2)} apart where the ring runs
     * diagonally — at the ordinary pitch of fourteen that is 9.9, inside the
     * twelve the siting code demands, and every plot on the diagonal quarters of
     * every ring was being refused.
     *
     * <p>This is precisely the fault that cost the warren three quarters of its
     * population for months: six huts on a circle of thirteen, comfortably far
     * apart as the crow flies and eleven apart on the wider axis. A circle laid
     * out in straight-line distances will always make it. Eighteen is twelve
     * times the root of two, rounded up, so the worst case on the diagonal still
     * clears.
     *
     * <p>Measured, before and after: the town reached 248 blocks holding a
     * hundred and forty plots, against 199 once the pitch was right.
     */
    private static final int ARC_PITCH = 18;

    private final String id;
    private final Wander wander;
    private final boolean hallOnTheGreen;

    public RadialStreetLayout() {
        this("ring_streets", Wander.gentle(9, 0xC1C1EL), false);
    }

    public RadialStreetLayout(String id, Wander wander) {
        this(id, wander, false);
    }

    /**
     * @param hallOnTheGreen whether to offer the middle of the green itself
     */
    public RadialStreetLayout(String id, Wander wander, boolean hallOnTheGreen) {
        this.id = id;
        this.wander = wander;
        this.hallOnTheGreen = hallOnTheGreen;
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

    /**
     * Whether this arrangement offers the middle of its green.
     *
     * <p>Readable because it is the whole difference between {@code ring_streets}
     * and {@code radial_concentric} that the {@link #wander()} does not already
     * account for, and anything reconstructing one of these from another has to
     * carry it or it hands back the wrong arrangement wearing the right name.
     */
    public boolean hallOnTheGreen() {
        return hallOnTheGreen;
    }

    @Override
    protected void design(SimPos centre, int wanted,
                          List<TownPlan.Street> streets, List<Offer> offers) {
        // Enough rings to hold the town, counted rather than guessed. A ring at
        // radius r carries about 2*pi*r/ARC_PITCH plots on each of its two faces,
        // and that grows with the radius -- so a formula in the plot count alone
        // either runs out of frontage or draws rings nothing ever fronts.
        int rings = 0;
        int room = 0;
        while (room < wanted && rings < 64) {
            int radius = FIRST_RING + rings * RING_SPACING;
            room += 2 * (int) (2 * Math.PI * radius / ARC_PITCH);
            rings++;
        }
        rings = Math.max(1, rings);
        int outer = FIRST_RING + (rings - 1) * RING_SPACING;

        for (int ring = 0; ring < rings; ring++) {
            int radius = FIRST_RING + ring * RING_SPACING;
            streets.add(ringRoad(centre, wanderFor(wander, centre, ring), radius));
        }
        // Spokes last, so ring indices stay put as a town grows and the doors
        // that face them keep facing them.
        int firstSpoke = streets.size();
        for (int spoke = 0; spoke < SPOKES; spoke++) {
            double angle = spoke * 2 * Math.PI / SPOKES;
            streets.add(spokeRoad(centre, angle, outer + RING_SPACING / 2));
        }

        // The middle of the green, when this arrangement wants one. Offered
        // first because it is nearest the centre, and the nearest offer is the
        // one the first building takes -- so the hall stands in the middle with
        // the lanes running out from it, which is the whole point of drawing a
        // town round a centre rather than along a road.
        //
        // It fronts no street, and deliberately: the spokes begin a PITCH out
        // from the middle, so there is nothing here to face, and naming one
        // would only drag the hall off the centre when a renderer moved it up to
        // that street's kerb. A town of this shape spends one plot's frontage on
        // having a middle, and it is worth it.
        if (hallOnTheGreen) {
            offers.add(new Offer(centre, Layout.NO_STREET,
                    Layout.facingToward(centre, round(centre, PITCH, 0))));
        }

        // Frontage on both faces of every ring: the inner face looks out across
        // the road at the outer face of the ring within, which is what makes a
        // ring town feel enclosed rather than merely circular.
        //
        // Each face is spaced on ITS OWN radius, not the ring's. The inner face
        // is a shorter circle than the road it fronts -- thirteen blocks shorter
        // in radius, so at the innermost ring its circumference is two thirds of
        // the centreline's. Spacing both faces by the centreline packs the inner
        // one at 12.2 along the arc, which fails the separation check on the
        // diagonals exactly as the raw pitch did, and frontage fell to a fifth.
        for (int ring = 0; ring < rings; ring++) {
            int radius = FIRST_RING + ring * RING_SPACING;
            Wander how = wanderFor(wander, centre, ring);
            for (int side : new int[] {-1, 1}) {
                int face = radius + side * SETBACK;
                if (face < PITCH) {
                    continue;   // inside the green, where nothing is offered
                }
                int around = Math.max(8, (int) (2 * Math.PI * face / ARC_PITCH));
                for (int i = 0; i < around; i++) {
                    double angle = i * 2 * Math.PI / around;
                    double bent = radius + how.offsetAt(angle * radius);
                    SimPos where = round(centre, bent + side * SETBACK, angle);
                    SimPos onRoad = round(centre, bent, angle);
                    offers.add(new Offer(where, ring, Layout.facingToward(where, onRoad)));
                }
            }
        }

        // Frontage on the spokes as well, which is what makes them streets
        // rather than obstacles. Left bare they cost twice over: they refuse
        // every ring plot they cross AND offer nothing back, which held a ring
        // town to 62% frontage with six lanes running through it earning their
        // keep as fences.
        for (int spoke = 0; spoke < SPOKES; spoke++) {
            double angle = spoke * 2 * Math.PI / SPOKES;
            double outX = Math.cos(angle);
            double outZ = Math.sin(angle);
            for (int t = FIRST_RING; t < outer + RING_SPACING / 2; t += PITCH) {
                for (int side : new int[] {-1, 1}) {
                    SimPos where = new SimPos(
                            centre.x() + (int) Math.round(t * outX - side * SETBACK * outZ),
                            centre.y(),
                            centre.z() + (int) Math.round(t * outZ + side * SETBACK * outX));
                    SimPos onRoad = new SimPos(
                            centre.x() + (int) Math.round(t * outX), centre.y(),
                            centre.z() + (int) Math.round(t * outZ));
                    offers.add(new Offer(where, firstSpoke + spoke,
                            Layout.facingToward(where, onRoad)));
                }
            }
        }
    }

    /** One ring, as the run of points a road round the green passes through. */
    private static TownPlan.Street ringRoad(SimPos centre, Wander how, int radius) {
        List<SimPos> path = new ArrayList<>();
        int around = Math.max(12, (int) (2 * Math.PI * radius / SEGMENT));
        for (int i = 0; i <= around; i++) {
            double angle = i * 2 * Math.PI / around;
            path.add(round(centre, radius + how.offsetAt(angle * radius), angle));
        }
        return new TownPlan.Street(path, ROAD_HALF * 2, TownPlan.Kind.LANE);
    }

    /** A lane striking outward from the green. */
    private static TownPlan.Street spokeRoad(SimPos centre, double angle, int out) {
        return new TownPlan.Street(
                round(centre, PITCH, angle), round(centre, out, angle),
                ROAD_HALF * 2, TownPlan.Kind.SPINE);
    }

    private static SimPos round(SimPos centre, double radius, double angle) {
        return new SimPos(
                centre.x() + (int) Math.round(radius * Math.cos(angle)), centre.y(),
                centre.z() + (int) Math.round(radius * Math.sin(angle)));
    }
}
