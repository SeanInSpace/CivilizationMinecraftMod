package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.List;

/**
 * A town that grew where two roads met: a market at the crossing, ribs off the arms.
 *
 * <p>Two spines cross at the centre, one north-south and one east-west, and the
 * open ground at the crossing is left open — that is the market, and it is the
 * only part of the plan that exists by <em>not</em> being built on. The frontage
 * begins just outside it, so the first eight doors of the town look onto the
 * square, and then runs out along all four arms.
 *
 * <p>This is a real and extremely common settlement form: the wayside town, the
 * Strassendorf that got a second road, half the market towns in England. It is
 * also the one arrangement here whose shape is a claim about <em>growth</em>
 * rather than about survey. Nobody laid a crossroads town out. Two routes
 * existed, people built where they met because that is where the traffic was,
 * and the town spread along the roads because that is where the frontage was.
 *
 * <h2>Ribs, and why not a grid</h2>
 *
 * <p>A town of this shape answers growth by opening a short lane off a spine —
 * far enough to hold two plots a side and no further. That is the whole
 * identity. Let the ribs run on and they meet the ribs of the other spine, the
 * four quadrants close up, and what is left is {@link GridStreetLayout} with a
 * hole in the middle: a different arrangement, drawn by accident, and the reason
 * for having both quietly gone. So {@link #RIB_REACH} is held below the distance
 * at which the first rib of the other spine stands, and the layout's own test
 * asserts that no plot ever stands far from <em>both</em> spines. From the air
 * this reads as a cross with ribs, and it has to, because that silhouette is the
 * only thing distinguishing it.
 *
 * <p>Laid with a straight edge, and the one thing here that is not free to
 * change. A crossroads is two long-distance routes, and a route that wanders
 * visibly within the length of one town is a route nobody would have taken — but
 * the reason to be careful is arithmetic rather than taste. The constructor
 * accepts a {@link Wander}, and a bent spine slides its doors sideways relative
 * to the ribs that cross it, which costs a rib its innermost frontage where the
 * bend goes the wrong way. Measured: at {@code Wander.gentle(3, seed)} — the
 * gentlest there is — a plan for two hundred and fifty-six comes up short, asks
 * its design again with twice the room, and draws forty-six streets running to
 * five hundred blocks instead of twenty-two running to two hundred and eighty.
 * A wandering crossroads wants {@link #RIB_FIRST} widened first.
 */
public final class CrossroadsLayout extends PlannedLayout {

    /**
     * Half-width of the open ground at the crossing, which nothing is built on.
     *
     * <p>The two spines quarter it, so the middle of the town is four corners of
     * empty ground with roads running out of them, which is what a market at a
     * crossroads is. Stated as a rule the offers respect rather than left as a
     * gap that happens to appear: {@link #FIRST_FRONT} is derived from it, and
     * the layout's own test asserts that nothing stands inside it.
     */
    private static final int MARKET = 20;

    /**
     * How far along an arm the first frontage stands, measured from the crossing.
     *
     * <p>Far enough that the <em>building</em> clears the market, not merely its
     * centre point: a plot centred exactly on the market's edge puts five and a
     * half blocks of wall inside the square, and a market with the corners of
     * four houses in it is not open ground. So half a span past the edge, plus a
     * couple of blocks so the clearance does not depend on rounding.
     */
    private static final int FIRST_FRONT = MARKET + Layout.DEFAULT_SPAN / 2 + 2;

    /**
     * How many frontage slots apart the ribs are set, counted in {@link #PITCH}.
     *
     * <p>As few as clear {@link #BACK_TO_BACK}, below which the frontage on the
     * far face of one rib lands on the frontage of the next. Three at the old
     * pitch and four at this one, which is very nearly the same distance either
     * way — forty-two blocks against forty-four. The constraint is in blocks and
     * the step it has to be counted in is what changed.
     *
     * <p>Counted in slots rather than in blocks, which is worth more than it
     * looks. A road takes ten and a half blocks of clearance either side of its
     * centreline: a rib laid halfway between two of the spine's own doors stands
     * inside that of each and refuses <em>both</em>, while a rib laid exactly on a
     * slot refuses only the one it stands on. Aligning the ribs to the spine's
     * slots is the difference between losing two doors per rib and losing four,
     * and it is why this is a count of slots that has to be rounded up rather than
     * a distance.
     */
    private static final int RIB_EVERY = (BACK_TO_BACK + PITCH - 1) / PITCH;

    /**
     * How far a rib runs either side of the spine it crosses.
     *
     * <p>Short on purpose, and the silhouette is entirely this number. It is the
     * <em>reach</em> that draws the cross, not the plot count on it: sixty was
     * tried first and the picture it draws at a hundred and forty plots is a
     * square with a hole in it, the arms reaching 125 blocks and the ribs 54, with
     * the four quarters between the arms filled in by ribs reaching at each other
     * from both spines. Fifty stops ten blocks short of that and the quarters stay
     * open, however many plots the pitch fits into the fifty.
     *
     * <pre>
     *   rib reach   plots per rib   arms at 140   ribs   arms : ribs
     *      60             12            125        54        2.3
     *      50              8            167        40        4.2
     * </pre>
     *
     * <p>It also has to stay under the {@code FIRST_FRONT + RIB_EVERY * PITCH} at
     * which the first rib of the <em>other</em> spine stands, or the two sets of
     * ribs actually cross and the quarters close up for good. That inequality is
     * the whole difference between this arrangement and a grid; if either number
     * is changed, check it still holds.
     */
    private static final int RIB_REACH = 50;

    /**
     * How far out along a rib its first frontage stands.
     *
     * <p>Not the rib's own setback, which would be thirteen. A plot here has a
     * spine door for a neighbour one block away <em>along</em> the rib, and
     * separation is Chebyshev — measured on the wider axis, not as a distance —
     * so this has to clear the spine's frontage column by a whole
     * {@link Layout#MIN_PLOT_SEPARATION} on the other axis. The setback plus a
     * separation is the exact floor; the block on top is slack for the rounding a
     * wander introduces.
     */
    private static final int RIB_FIRST = SETBACK + Layout.MIN_PLOT_SEPARATION + 1;

    /** The four arms of the cross, which is what its frontage is counted in. */
    private static final int ARMS = 4;

    /** How many plots one face of a rib holds on one side of its spine. */
    private static final int PER_RIB_FACE =
            (RIB_REACH - Layout.DEFAULT_SPAN / 2 - RIB_FIRST) / PITCH + 1;

    /**
     * Frontage on one arm before its first rib opens: the doors on the market.
     *
     * <p>{@link #RIB_EVERY} slots with two faces each.
     */
    private static final int BEFORE_THE_FIRST_RIB = RIB_EVERY * 2;

    /**
     * Frontage one rib adds to its arm, counting the spine it comes with.
     *
     * <p>{@link #PER_RIB_FACE} plots on each of two faces on each of two sides of
     * the spine, plus the {@link #RIB_EVERY} further slots of spine the rib brings
     * with it — two doors a slot, less the two standing on the rib itself.
     * Written as the sum rather than as the number the sum came to, because both
     * halves move with the pitch.
     *
     * <p>An estimate, and deliberately not a tuned one. {@code lay()} asks the
     * design again with twice the room whenever too little of it survives, so
     * this only has to be close enough that the first ask usually does; a
     * constant somebody has to keep exact per size of town is precisely the kind
     * of arithmetic that has been wrong repeatedly in this package.
     *
     * <p>It happens to be exact for a straight town, and the margin is thin:
     * a plan for two hundred and fifty-six clears two hundred and sixty-four,
     * so eight offers of slack. That is enough because nothing here is refused
     * by chance — the ribs and the doors are on the same arithmetic — and it is
     * not enough for a bending spine, which is recorded on the class above.
     */
    private static final int PER_RIB = PER_RIB_FACE * 4 + RIB_EVERY * 2 - 2;

    /**
     * How many ribs an arm may open before the plan settles for what it has.
     *
     * <p>A bound rather than a budget. Thirty-two ribs an arm is an arm of
     * thirteen hundred blocks, far past anything a settlement reaches, and it is
     * here so that a wanted count nothing could fill stops rather than draws
     * roads to the horizon.
     */
    private static final int RIBS_ALLOWED = 32;

    private final String id;
    private final Wander wander;

    public CrossroadsLayout() {
        this("crossroads", Wander.STRAIGHT);
    }

    public CrossroadsLayout(String id, Wander wander) {
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
    protected void design(SimPos centre, int wanted,
                          List<TownPlan.Street> streets, List<Offer> offers) {
        int ribs = ribsFor(wanted);
        // The last slot an arm offers, and the road running one pitch past it so
        // the town does not end on a doorstep.
        int lastSlot = ribs * RIB_EVERY + RIB_EVERY - 1;
        int reach = FIRST_FRONT + (lastSlot + 1) * PITCH;

        Wander alongZ = wanderFor(wander, centre, 0);
        Wander alongX = wanderFor(wander, centre, 1);

        // Both spines before anything else, and they keep indices 0 and 1 for
        // good. A plot records the street it fronts by index, so the two roads
        // the whole town hangs off cannot be allowed to renumber when a rib opens.
        streets.add(northSouth(centre, alongZ, 0, -reach, reach,
                ROAD_HALF * 2, TownPlan.Kind.SPINE));
        streets.add(eastWest(centre, alongX, 0, -reach, reach,
                ROAD_HALF * 2, TownPlan.Kind.SPINE));

        // Frontage along both spines, either side, beginning outside the market.
        // Offered at every slot including the ones a rib stands on: those are
        // refused by the shared machinery, which is the thing that knows where
        // the carriageways are, and a design that tried to skip them itself
        // would be keeping a second copy of that arithmetic.
        for (int slot = 0; slot <= lastSlot; slot++) {
            int along = FIRST_FRONT + slot * PITCH;
            for (int end : new int[] {1, -1}) {
                int out = along * end;
                doors(centre, offers, alongZ.blocksAt(out), out, false, 0);
                doors(centre, offers, out, alongX.blocksAt(out), true, 1);
            }
        }

        // Ribs, a ring of four at a time -- one on each arm -- so that a town
        // wanting more room appends ribs rather than renumbering the ones it has.
        for (int ring = 1; ring <= ribs; ring++) {
            int along = FIRST_FRONT + ring * RIB_EVERY * PITCH;
            for (int end : new int[] {1, -1}) {
                int out = along * end;
                rib(centre, streets, offers, alongZ.blocksAt(out), out, true);
            }
            for (int end : new int[] {1, -1}) {
                int out = along * end;
                rib(centre, streets, offers, out, alongX.blocksAt(out), false);
            }
        }
    }

    /**
     * How many ribs each arm opens to hold a town of this size.
     *
     * <p>Counted rather than guessed from the plot count directly, because an
     * arm's capacity is not linear in its length: the first stretch out of the
     * market carries only the spine's own two faces, and every stretch after
     * that carries a rib as well.
     */
    private static int ribsFor(int wanted) {
        int room = ARMS * BEFORE_THE_FIRST_RIB;
        int ribs = 0;
        while (room < wanted && ribs < RIBS_ALLOWED) {
            ribs++;
            room += ARMS * PER_RIB;
        }
        return Math.max(1, ribs);
    }

    /**
     * One rib: a short lane across a spine, with frontage on both its faces.
     *
     * <p>One street rather than two stubs, because it is one lane — a plot
     * records the street it fronts by index, and a rib chopped in half at the
     * spine would be two roads the town knows by two names for no gain.
     *
     * <p>Drawn through {@link #eastWest} and {@link #northSouth} even though it
     * is dead straight and two points would describe it exactly. Those put a
     * vertex every {@link #SEGMENT} blocks, and the road layer routes a plan
     * <em>a stretch at a time</em>: a rib given as two points is one stretch a
     * hundred blocks long, so a single ravine anywhere along it condemns the
     * whole lane and every plot fronting it is refused with it. That is the
     * measured fault recorded in {@code PathPlanner} — nine streets of twelve
     * refused, and a town of sixty-two buildings down to thirty-nine — and the
     * ribs are where this arrangement is most exposed to it, carrying nearly two
     * thirds of a full town's frontage between them.
     *
     * @param cx    where the rib crosses its spine, east of the town centre
     * @param cz    where the rib crosses its spine, south of the town centre
     * @param acrossTheNorthSouthSpine whether this rib runs east-west, which it
     *              does when it comes off the north-south spine
     */
    private static void rib(SimPos centre, List<TownPlan.Street> streets,
                            List<Offer> offers, int cx, int cz,
                            boolean acrossTheNorthSouthSpine) {
        int index = streets.size();
        streets.add(acrossTheNorthSouthSpine
                ? eastWest(centre, Wander.STRAIGHT, cz, cx - RIB_REACH, cx + RIB_REACH,
                        ROAD_HALF * 2, TownPlan.Kind.LANE)
                : northSouth(centre, Wander.STRAIGHT, cx, cz - RIB_REACH, cz + RIB_REACH,
                        ROAD_HALF * 2, TownPlan.Kind.LANE));

        for (int out = RIB_FIRST;
             out + Layout.DEFAULT_SPAN / 2 <= RIB_REACH; out += PITCH) {
            for (int end : new int[] {1, -1}) {
                int onX = acrossTheNorthSouthSpine ? cx + out * end : cx;
                int onZ = acrossTheNorthSouthSpine ? cz : cz + out * end;
                doors(centre, offers, onX, onZ, acrossTheNorthSouthSpine, index);
            }
        }
    }

    /**
     * The pair of doors that stand back from a point on a street, one either side.
     *
     * @param runsEastWest which way the street runs, and so which way the
     *                     setback is taken: doors stand north and south of an
     *                     east-west street, east and west of a north-south one
     */
    private static void doors(SimPos centre, List<Offer> offers,
                              int x, int z, boolean runsEastWest, int street) {
        SimPos onRoad = at(centre, x, z);
        for (int side : new int[] {1, -1}) {
            SimPos where = runsEastWest
                    ? at(centre, x, z + side * SETBACK)
                    : at(centre, x + side * SETBACK, z);
            offers.add(new Offer(where, street, Layout.facingToward(where, onRoad)));
        }
    }

    private static SimPos at(SimPos centre, int dx, int dz) {
        return new SimPos(centre.x() + dx, centre.y(), centre.z() + dz);
    }
}
