package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A town laid out at once, round a market place, inside a circuit road.
 *
 * <p>A bastide is a grid somebody was <em>paid</em> to survey: a founder's men
 * pegged out the streets, left one whole block open for the market, and ran a
 * road round the outside to say where the town stopped. Monpazier, Aigues-Mortes
 * and a few hundred others in south-west France are this, and so is most of
 * colonial Spanish America. From the air it reads as a rectangle with a hole in
 * the middle and a hard border.
 *
 * <h2>Why this is not {@code stronghold_streets} with a different name</h2>
 *
 * <p>Both are grids, and that is the whole risk of this arrangement existing. A
 * second grid earns its place on three differences, none of them cosmetic:
 *
 * <ul>
 *   <li><strong>The place.</strong> The grid is offset by half a block, so the
 *       middle of the town is the middle of a <em>block</em> rather than a
 *       crossroads, and that block is offered to nothing. The stronghold puts
 *       its busiest junction where this one puts its market.</li>
 *   <li><strong>The circuit.</strong> A rectangular road round the whole grid,
 *       with the outermost houses fronting its inner face. The stronghold's
 *       streets simply stop, and the town frays at the edge.</li>
 *   <li><strong>The block.</strong> The tightest a block can be, where the
 *       stronghold leaves its blocks room to have backs — see {@link #BLOCK}.</li>
 * </ul>
 *
 * <p>Measured on the first forty plots of each, the two arrangements share none
 * of them; the test says so, because "it looks different" is not a thing anybody
 * can check twice.
 */
public final class BastideLayout extends PlannedLayout {

    /**
     * How far apart the streets are pegged.
     *
     * <p>The floor is thirty-four — twice the {@link #SETBACK} plus the width of
     * a road — and it is not the binding constraint. What binds is that the two
     * rows of houses in a block back onto each other at {@code BLOCK - 2 *
     * SETBACK} apart on the wider axis, and {@link Layout#farEnoughApart} wants a
     * whole separation of it. That is {@link #BACK_TO_BACK}, and this takes it
     * exactly bar the parity below: a block tighter and the backs would stand
     * inside the separation, every second house in every block would be refused by
     * the overlap check, and the town would run outward looking for room while the
     * plan swore it had laid enough streets — which is precisely how the warren
     * lost three quarters of its people.
     *
     * <p>It was written as the thirty-eight that sum came to. The sum has since
     * moved — the block between two claims is gone and a plot is walls plus a
     * doorstep — and a literal would now be four blocks of grass in the middle of
     * every block in every bastide, with nothing in the file looking wrong.
     *
     * <p>The useful floor being this rather than thirty-four is also what makes
     * the window between this and the stronghold's block a two-block one rather
     * than a six-block one. Two blocks is still worth taking: it puts the whole
     * town closer together, which is what a surveyed town paying for its wall
     * does.
     *
     * <p><strong>Even, because {@link #HALF_BLOCK} halves it.</strong> A block has
     * to have a middle: its two rows stand {@link #ROW_OFF} either side of one, so
     * what actually separates them is {@code 2 * (BLOCK / 2 - SETBACK)} and not
     * {@code BLOCK - 2 * SETBACK}. Those are the same number only while the block
     * is even, and the odd case loses a whole block to the floor division — which
     * is a block inside the separation, so every second house in every block is
     * refused, the plan asks its design for twice the grid, and the circuit ends
     * up so far out that the rim carries no frontage at all. Measured when the
     * sum first came out odd: the plan of two hundred and fifty-six reached 229
     * blocks against 170, and eight houses fronted the circuit against sixty-eight
     * offered.
     *
     * <p>Taking the floor exactly means <strong>no slack for a wander</strong>. A
     * bent street would slide its frontage sideways and close the gap, so this
     * arrangement is straight by construction rather than by preference — which
     * is also what a bastide is: a town laid out with a rope and a right angle.
     */
    private static final int BLOCK = BACK_TO_BACK + (BACK_TO_BACK % 2);

    /** Half a block, which is where the streets run relative to a block's middle. */
    private static final int HALF_BLOCK = BLOCK / 2;

    /**
     * How far a row of houses stands from the middle of its own block.
     *
     * <p>Not a choice: a row stands {@link #SETBACK} back from the street it
     * fronts, and the street is {@link #HALF_BLOCK} from the middle. Named
     * because it turns up in both families of frontage and reads as a magic six
     * otherwise.
     */
    private static final int ROW_OFF = HALF_BLOCK - SETBACK;

    /**
     * How many plots a block carries: two rows of two.
     *
     * <p>The block is {@link #BLOCK} across and a house takes
     * {@link #PITCH}, so three to a row would put one within a carriageway of the
     * cross street and be refused for standing in it. Two to a row stands a dozen
     * blocks clear either side, and two rows is all the depth there is. Used only
     * to work out how many streets to peg —
     * {@code lay()} asks again if the estimate is short, so being wrong here is
     * slow rather than broken.
     */
    private static final int PER_BLOCK = 4;

    private final String id;

    public BastideLayout() {
        this("bastide");
    }

    public BastideLayout(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    protected void design(SimPos centre, int wanted,
                          List<TownPlan.Street> streets, List<Offer> offers) {
        int rings = ringsFor(wanted);
        int reach = rings * BLOCK + HALF_BLOCK;

        // North-south streets first, then east-west, then the circuit. A plot
        // records the street it fronts by index, so the order has to be a rule
        // rather than whatever the loops happened to do.
        //
        // Every index below is a function of `rings`, which is a function of the
        // size the plan is laid at -- and that size is fixed at PLAN_SIZE
        // precisely so it never changes under a town that is still growing.
        // Putting the circuit first instead would be the same story with the
        // numbers moved around: what actually keeps a door pointing at its own
        // street is that the plan is laid once, at a size no settlement reaches.
        for (int line = -rings; line < rings; line++) {
            streets.add(northSouth(centre, Wander.STRAIGHT, line * BLOCK + HALF_BLOCK,
                    -reach, reach, ROAD_HALF * 2, TownPlan.Kind.LANE));
        }
        int firstEastWest = streets.size();
        for (int line = -rings; line < rings; line++) {
            streets.add(eastWest(centre, Wander.STRAIGHT, line * BLOCK + HALF_BLOCK,
                    -reach, reach, ROAD_HALF * 2, TownPlan.Kind.LANE));
        }
        int circuit = streets.size();
        streets.add(circuitRoad(centre, reach));

        // A block at a time, because every decision here -- which streets a
        // block fronts, whether it turns to face the market -- is a property of
        // the block and not of the street. Walking the streets instead means
        // asking "is this the bit of this road that runs past the place", which
        // is the same question phrased so it can be got wrong.
        for (int col = -rings; col <= rings; col++) {
            for (int row = -rings; row <= rings; row++) {
                if (col == 0 && row == 0) {
                    continue;   // the place: a whole block, offered to nothing
                }
                frontage(centre, col, row, rings, firstEastWest, circuit, offers);
            }
        }
    }

    /**
     * The frontage one block offers, on each of its two sides.
     *
     * <p>A block ordinarily fronts the streets above and below it, as any grid
     * does — two rows of two, with the block's east and west edges left as the
     * backs of the houses. That is the default because it is what makes a street
     * of houses rather than a courtyard.
     *
     * <p>A side <em>turns</em> — the row swings round to face the cross street
     * instead — when there is nothing behind it worth backing onto: the market
     * place, or the world outside the circuit. That is the whole trick of this
     * layout. It buys the two things a bastide needs and cannot otherwise have,
     * because at this block size the four setbacks meet in the middle and a block
     * physically cannot front all four of its streets:
     *
     * <ul>
     *   <li>the place is looked at from all four sides, not merely walked past on
     *       two of them;</li>
     *   <li>the east and west runs of the circuit carry frontage, so the circuit
     *       is a street and not a fence. A bare road costs twice over — it
     *       refuses every plot it passes <em>and</em> gives nothing back, which
     *       held a ring town to 62% frontage with six lanes earning their keep as
     *       hedges.</li>
     * </ul>
     */
    private static void frontage(SimPos centre, int col, int row, int rings,
                                 int firstEastWest, int circuit, List<Offer> offers) {
        int blockX = col * BLOCK;
        int blockZ = row * BLOCK;
        for (int side : new int[] {-1, 1}) {
            int beyond = col + side;
            boolean turned = (beyond == 0 && row == 0)
                    || beyond > rings || beyond < -rings;
            if (turned) {
                // Facing the cross street: the row runs along the block's own
                // edge, pitched down the street rather than across it.
                int line = side > 0 ? col : col - 1;
                int road = line * BLOCK + HALF_BLOCK;
                int street = crossStreet(line, rings, circuit);
                for (int end : new int[] {-1, 1}) {
                    int z = blockZ + end * HALF_PITCH;
                    add(offers, centre, blockX + side * ROW_OFF, z, road, z, street);
                }
            } else {
                // The ordinary case: a house at each end of the block's two rows.
                int x = blockX + side * HALF_PITCH;
                for (int end : new int[] {-1, 1}) {
                    int line = end > 0 ? row : row - 1;
                    int road = line * BLOCK + HALF_BLOCK;
                    add(offers, centre, x, blockZ + end * ROW_OFF, x, road,
                            alongStreet(line, rings, firstEastWest, circuit));
                }
            }
        }
    }

    /** One offer, facing the point on the road it takes its frontage from. */
    private static void add(List<Offer> offers, SimPos centre, int x, int z,
                            int roadX, int roadZ, int street) {
        SimPos plot = at(centre, x, z);
        offers.add(new Offer(plot, street, Layout.facingToward(plot, at(centre, roadX, roadZ))));
    }

    /**
     * The index of the north-south street on this line, or the circuit's.
     *
     * <p>The circuit runs exactly where the next grid street would have gone —
     * one block out from the last one — so the two outermost lines of each family
     * are simply the circuit seen edge-on, and a block on the town's rim fronts
     * it with no special case at all.
     */
    private static int crossStreet(int line, int rings, int circuit) {
        return line < -rings || line >= rings ? circuit : line + rings;
    }

    /** The index of the east-west street on this line, or the circuit's. */
    private static int alongStreet(int line, int rings, int firstEastWest, int circuit) {
        return line < -rings || line >= rings ? circuit : firstEastWest + line + rings;
    }

    /**
     * How many blocks out from the place the grid is pegged.
     *
     * <p>A town of {@code n} wants {@code n / PER_BLOCK} blocks and one more for
     * the place, laid in a square that is an odd number of blocks across so the
     * place sits in the middle of it.
     */
    private static int ringsFor(int wanted) {
        int blocks = Math.max(2, (Math.max(1, wanted) + PER_BLOCK - 1) / PER_BLOCK + 1);
        return Math.max(1, (int) Math.ceil((Math.sqrt(blocks) - 1) / 2));
    }

    /**
     * The road round the outside, as one closed rectangle.
     *
     * <p>One street rather than four, because a plot records the street it fronts
     * by index and a circuit chopped into sides would have the houses on the north
     * range fronting a different road from the houses on the east — which is
     * false, and would show up the first time anything asked how long the town's
     * boundary was.
     *
     * <p><strong>It closes, and that is a shape the shared fallback has never
     * met.</strong> {@code PlannedLayout.finish} answers a design that came up
     * short by carrying every street further along its last two points; on a
     * closed loop those two points aim back into the town's own corner, so the
     * boundary would grow a tail and offer frontage outside itself. Not reachable
     * today — measured at 256, 400, 1000 and 3000 plots, this design always
     * over-provisions and the fallback never runs — but it is the one street here
     * that cannot be lengthened, and the day a design does come up short is the
     * day somebody finds that out. The ring roads have the same shape and the
     * same exposure.
     */
    private static TownPlan.Street circuitRoad(SimPos centre, int reach) {
        List<SimPos> path = new ArrayList<>();
        for (int x = -reach; x < reach; x += SEGMENT) {
            path.add(at(centre, x, -reach));
        }
        for (int z = -reach; z < reach; z += SEGMENT) {
            path.add(at(centre, reach, z));
        }
        for (int x = reach; x > -reach; x -= SEGMENT) {
            path.add(at(centre, x, reach));
        }
        for (int z = reach; z > -reach; z -= SEGMENT) {
            path.add(at(centre, -reach, z));
        }
        path.add(at(centre, -reach, -reach));
        return new TownPlan.Street(path, ROAD_HALF * 2, TownPlan.Kind.SPINE);
    }

    private static SimPos at(SimPos centre, int dx, int dz) {
        return new SimPos(centre.x() + dx, centre.y(), centre.z() + dz);
    }
}
