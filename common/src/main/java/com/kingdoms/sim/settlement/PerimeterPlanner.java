package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.geom.Hull;
import com.kingdoms.sim.geom.Ways;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Stakes and raises the settlement's wall.
 *
 * <p>Once, at TOWN, around what the town is on the day it is chartered — and
 * then it stays where it was put. Everything the settlement builds afterwards
 * goes up outside it as suburbs, and the line moves again only when those
 * suburbs have come to outnumber the quarter behind the wall; see
 * {@link #RESTAKE_COOLDOWN} for why that is the rule and not the wall following
 * the town.
 *
 * <p>V1 by design: an axis-aligned rectangle around everything the town has
 * built, with a margin to move in and a gate on every road out. Gates
 * belong where the roads cross the ring, and the roads are remembered now --
 * the side midpoints only stand in for a town whose paths never reach its
 * wall. The α-shape wall in the GOALS replaces {@link #stake} and nothing
 * else — laying, closing, gates and patrol all read
 * the {@link Perimeter} it returns.
 *
 * <p>Raising is paid work on the abstract clock: posts cost timber and go up as
 * fast as the laboring hands can plant them, pausing whenever the build queue
 * has a real building in it — walls matter, but shelter and stores matter more.
 */
public final class PerimeterPlanner {

    /** Clear ground kept between the buildings and the ring. */
    public static final int MARGIN = 4;

    /** Shortest side a ring may have, so a lone post is still a yard. */
    public static final int MIN_HALF_SIDE = 8;

    /** Timber per ring position — one log, split into posts. */
    public static final int WOOD_PER_POST = 1;

    /**
     * Coin a post costs on top of the timber.
     *
     * <p>A wall is the last thing a settlement gets, not the first. Timber alone
     * it has from the day it has a lumberjack, so a town that had cut enough
     * wood started walling itself while it was still half-built. Coin comes only
     * from the levy on production, which takes a working town a good while to
     * accumulate — so paying for the wall in money is what actually puts it
     * late in a settlement's life, where it belongs.
     *
     * <p>It is also the first thing the treasury is spent on that is not wages,
     * which makes the money mean something: a town can now be too poor to
     * defend itself.
     */
    public static final int COIN_PER_POST = 3;

    /**
     * Timber the wall will not touch, so a building is never starved by fencing.
     *
     * <p>The wall is the one build with no queue behind it and no deadline, so
     * it is the one that should give way.
     */
    public static final int TIMBER_KEPT_FOR_BUILDING = 64;

    /**
     * Pays for one post out of the ledger, and says whether it could.
     *
     * <p>Both or neither. A town that has the timber but not the coin does not
     * get a free post, and does not lose the timber pretending otherwise.
     *
     * <p>This is the whole price of a post and it is what the clock pays — a
     * town nobody is looking at has no hands to fill, so the timber comes
     * straight off the books. A watched town pays the same total by a different
     * route: the plank leaves the storehouse in a builder's arms and only
     * {@link #payCoinForPost} is owed at the line. Both are written here so the
     * two paths cannot come to disagree about what a wall costs.
     */
    public static boolean payForPost(Settlement settlement) {
        if (settlement.woodStock() < WOOD_PER_POST
                || settlement.treasury() < COIN_PER_POST) {
            return false;
        }
        settlement.stores().take(TownStores.WOOD, WOOD_PER_POST);
        return payCoinForPost(settlement);
    }

    /**
     * The half of a post's price still owed when the plank is already in hand.
     *
     * <p>A watched town's timber leaves the ledger at the storehouse, when a
     * builder shoulders a load of it, and charging for it again at the line
     * would take two logs out of the stores for one post — the same double
     * charge a carried load exists to prevent everywhere else. What is left is
     * the coin, and the coin is charged where it always was: at the post, with
     * somebody standing there ready to plant it.
     */
    public static boolean payCoinForPost(Settlement settlement) {
        if (settlement.treasury() < COIN_PER_POST) {
            return false;
        }
        settlement.spend(COIN_PER_POST);
        return true;
    }

    /** Positions one pair of hands raises in a step. */
    public static final int POSTS_PER_HAND = 2;

    private PerimeterPlanner() {
    }

    /**
     * One step of perimeter work: stake it when the town is old enough to be
     * worth walling, re-stake it when the suburbs have outgrown it, then raise
     * it as timber and hands allow.
     *
     * <p>Order matters at the top. A settlement below TOWN never gets a FIRST
     * wall, but one that already has a wall is worked on at any stage: a save
     * from before this rule, or a town knocked back down the ladder, keeps the
     * line it paid for and goes on raising it. Walls are not demolished by a
     * change of mind about when they should have been built.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        if (settlement.perimeter() == null) {
            if (settlement.stage().before(SettlementStage.TOWN)) {
                return;
            }
            // Staked only once the stage's own buildings stand, so the ring
            // encloses the hall rather than being outgrown by it.
            if (!StagePlanner.programComplete(settlement)) {
                return;
            }
            Perimeter staked = stake(settlement, ctx);
            settlement.setPerimeter(staked);
            settlement.logEvent(ctx.step(), "The wall is staked out — "
                    + staked.length() + " posts will ring " + settlement.name());
            return;
        }
        restakeIfOutgrown(settlement, ctx);
        resiteGates(settlement, ctx);
        raise(settlement, ctx);
    }

    /**
     * The least a wall may stand before the town is allowed to move it.
     *
     * <p>Five hundred steps is the whole founding ladder — the measured run
     * from four settlers in a field to a chartered town with a hall is a shade
     * under it — so a wall can move at most once per age of the town, and a
     * settlement that lived three ages would have moved it twice. That is the
     * number this rule exists to reproduce.
     *
     * <p>Towns walled themselves once, at their charter, and lived inside that
     * circuit for generations. Growth did not push the wall outward: it went
     * <em>outside</em> it, as suburbs, and the suburbs stayed unwalled — which
     * is why the faubourgs are outside the gates on every medieval plan there
     * is. A second circuit was raised only when the suburbs had come to rival
     * the walled town itself, and even then it was the work of a generation and
     * a special levy. Paris managed three circuits in four hundred and fifty
     * years, Florence three in three hundred, and London, having inherited a
     * Roman wall, never built another at all. A wall that is re-staked every
     * time a shed goes up beyond the gate is not defending anything; it is
     * drawing the town's outline, which is not what a wall is for.
     */
    private static final int RESTAKE_COOLDOWN = 500;

    /**
     * Moves the wall out when the suburbs have outgrown the town inside it.
     *
     * <p>Three conditions, and each of them removes a way for a wall to be
     * moved for no good reason. More ground-holding plots outside the line than
     * inside it: the suburb has become the town and the old circuit is now the
     * old quarter, which is the historical trigger and not "somebody built a
     * shed past the gate". The standing wall complete: an unfinished line is
     * never abandoned for a longer one it can afford even less, and because a
     * re-staked ring carries its raised posts onto a longer loop, this alone
     * makes the next move wait until the new circuit is paid for. And the
     * cooldown above, so the answer is asked of a generation rather than of a
     * season.
     *
     * <p>The review is deliberately answerable without staking anything. It
     * used to stake a candidate ring to find out whether it wanted one — a
     * concave hull over every corner of every plot and four relaxation sweeps
     * reading the terrain, a tenth of a second on a town of eighty-six
     * buildings and a second and a half on one of two hundred, paid on a
     * cadence by every settlement in the dimension on the same step. Counting
     * plots against the standing loop answers the same question for a few
     * thousand comparisons, and {@link #stake} is called only once the town has
     * already decided it wants a new wall.
     *
     * <p>Which is why the cadence went with it. A step is five seconds of game
     * time, so those few thousand comparisons fall five seconds apart per
     * settlement, and only for one whose wall is closed and older than the
     * cooldown — everything younger is turned away by two comparisons.
     * Skipping ninety-nine steps in a hundred to save that would be paying for
     * it in delay and getting nothing back.
     *
     * <p>Inside and outside are judged on the reserved plot's corners rather
     * than its origin, because a wall that clips the back of a farm has not
     * enclosed it. The old line is retired rather than kept: see
     * {@link Perimeter#retired()}.
     */
    private static void restakeIfOutgrown(Settlement settlement, SimContext ctx) {
        Perimeter standing = settlement.perimeter();
        // Age rather than a subtraction: the world's step counter restarts at
        // zero on every reload and the stake step does not, so see
        // Perimeter.ageAt. The two cheap questions come first, because the
        // counting below is the only part of this that costs anything.
        if (standing.ageAt(ctx.step()) < RESTAKE_COOLDOWN || !standing.closed()) {
            return;
        }
        if (!suburbsOutnumberTheTown(settlement, standing)) {
            return;
        }
        Perimeter wider = stake(settlement, ctx);
        List<Perimeter.Retired> retired = new ArrayList<>(standing.retired());
        retired.add(new Perimeter.Retired(standing.vertices(), standing.laid()));
        // The posts raised so far travel with the town. A wall moved outward is
        // the same wall: the timber and the coin already spent bought posts,
        // and a settlement carrying its palisade out to a wider line is
        // re-using them rather than buying a second one. Charging for the whole
        // ring again would fall hardest exactly where it is least affordable --
        // on the grown town that has the most ring to pay for -- and would
        // leave it standing in the open for the hundreds of steps it took to
        // pay the first time.
        settlement.setPerimeter(new Perimeter(wider.vertices(), wider.gates(),
                Math.min(standing.laid(), wider.length()), retired, ctx.step()));
        settlement.logEvent(ctx.step(), settlement.name()
                + " has spread further outside its wall than in — the line is"
                + " re-staked at " + wider.length() + " posts, from "
                + standing.length());
    }

    /**
     * Whether the suburbs have come to outnumber the quarter inside the line.
     *
     * <p>One walk of the buildings rather than two counts, because outside and
     * inside are the two halves of a single filter and a rule that compares
     * them must not be able to count different populations. Anything that holds
     * no ground — a well sunk in a street, a marker — is in neither half: it is
     * not a suburb, and it is not a reason to move a wall.
     */
    private static boolean suburbsOutnumberTheTown(Settlement settlement, Perimeter ring) {
        int outside = 0;
        int inside = 0;
        for (Building building : settlement.buildings()) {
            if (!BuildPlanner.holdsGround(building.blueprintId())) {
                continue;
            }
            int half = BuildPlanner.plotSpanOf(
                    building.blueprintId(), settlement.catalogue()) / 2;
            if (whollyInside(ring, building.origin(), half)) {
                inside++;
            } else {
                outside++;
            }
        }
        return outside > inside;
    }

    /** Whether every corner of this plot is inside the line. */
    private static boolean whollyInside(Perimeter ring, SimPos at, int half) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (!Hull.contains(ring.vertices(), new SimPos(
                        at.x() + sx * half, at.y(), at.z() + sz * half))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether somebody is actually there to raise the next post themselves.
     *
     * <p>The same test construction uses, for the same reason: a clock running
     * alongside a builder would raise the wall twice, and one running instead of
     * a builder standing right there would have posts appear beside somebody
     * doing nothing.
     */
    private static boolean handsAreOnIt(Settlement settlement, SimContext ctx,
                                        Perimeter perimeter) {
        if (perimeter.laid() >= perimeter.length()) {
            return false;
        }
        boolean anyEmbodied = settlement.residents().stream()
                .anyMatch(person -> settlement.laboursAs(person, Profession.BUILDER)
                        && person.isEmbodied() && !person.isTooWeakToWork());
        return anyEmbodied
                && ctx.bridge().isLoaded(perimeter.ringPositions().get(perimeter.laid()));
    }

    /**
     * Keeps the gates on the roads while the wall is still going up.
     *
     * <p>The ring is staked the day the town is chartered, when the streets of
     * the quarters it will fill are not drawn yet — so the gates it is staked
     * with are provisional, and follow the network until the wall closes over
     * them.
     */
    private static void resiteGates(Settlement settlement, SimContext ctx) {
        Perimeter perimeter = settlement.perimeter();
        if (perimeter.closed() || settlement.paths().isEmpty()) {
            return;
        }
        // Reviewed on a cadence rather than every step. Finding where the roads
        // cross the ring is every run against every post -- three hundred by
        // nine hundred on a measured town -- which is nothing once in a while
        // and far too much sixty times a second.
        if (ctx.step() % GATE_REVIEW != 0) {
            return;
        }
        perimeter.setGates(gatesFor(settlement, perimeter));
    }

    /** How often the gates are reconsidered while the wall is going up. */
    private static final int GATE_REVIEW = 20;

    /**
     * How long a straight run of wall may be before the line is expected to come
     * in and follow the buildings.
     *
     * <p>The one number that decides how tightly the wall hugs the town. Too
     * small and the line frets around every shed; too large and it is a
     * rectangle again, fortifying whatever empty field happens to lie between
     * two outlying farms.
     */
    private static final int MAX_STRAIGHT_RUN = 24;

    /** Sweeps of the contour before the line is taken as settled. */
    private static final int RELAX_PASSES = 4;

    /** How far a vertex may shift in one sweep, looking for better ground. */
    private static final int RELAX_REACH = 3;

    /**
     * What a vertex weighs the ground against the shape.
     *
     * <p>Higher and the wall chases every flat shelf into a wandering line;
     * lower and it ignores the terrain and marches over a ravine. These are the
     * two halves of the contour's energy: {@code GROUND} rewards standing on
     * even ground, {@code TAUT} rewards a short line that does not zigzag.
     */
    private static final double GROUND_WEIGHT = 1.0;
    private static final double TAUT_WEIGHT = 0.35;

    /**
     * Stakes the ring: a loop that follows the town, settled onto the ground.
     *
     * <p>Four steps, each of which can be read on its own. The plots give a
     * scatter of corners; {@link Hull#concave} wraps them in a line that comes
     * in where the buildings do rather than bulging out to the convex hull; the
     * line is pushed clear by {@link #MARGIN}; and then it is relaxed over the
     * terrain, each vertex looking a little way around itself for ground that
     * is flatter than where it stands, without ever letting a building out.
     *
     * <p>The relaxation is a greedy active contour — the vertex-at-a-time kind
     * rather than the matrix kind — because a wall is a few dozen vertices on
     * an integer grid and the expensive version buys nothing at that size.
     * Every candidate move is checked against containment before it is taken,
     * so the terrain can never talk the line into abandoning a farm.
     *
     * <p>The plots go to the hull twice over: as corners it must enclose, and
     * as ground it may not be drawn across. The second is not implied by the
     * first — a plot's corners sit happily inside a loop whose line runs over
     * its floor — and it is what stops the wall being staked through a standing
     * house. {@link #relax} asks its own version of that question, off the same
     * list, of every move it considers.
     *
     * <p>The two lists are not the same list, and {@link #plotSquares} says
     * why: ground the town has merely ordered may not be drawn across either,
     * but the wall is under no obligation to enclose it.
     */
    public static Perimeter stake(Settlement settlement, SimContext ctx) {
        SimPos centre = settlement.centre();
        List<SimPos> plots = plotCorners(settlement);
        List<Hull.Keepout> squares = plotSquares(settlement);
        List<SimPos> loop = Hull.concave(plots, MAX_STRAIGHT_RUN, squares);
        if (loop.size() < 3) {
            loop = boxAround(centre, MIN_HALF_SIDE);
        }
        loop = pushOut(loop, centre, MARGIN, plots, squares);
        loop = relax(loop, plots, squares, ctx);

        // The ring first, then its gates -- a gate is a hole in a wall, so it
        // can only be chosen once there is a wall to make a hole in.
        Perimeter ring = new Perimeter(loop, List.of(), 0, List.of(), ctx.step());
        ring.setGates(gatesFor(settlement, ring));
        return ring;
    }

    /**
     * The ground the town has taken, which no stretch of wall may cross.
     *
     * <p><strong>Ordered ground counts.</strong> The line used to be staked
     * against what was standing and nothing else, and a settlement is never
     * only what is standing — it always has a plot or two chosen, paid for and
     * waiting for hands. Nothing re-examines such a site once the wall has
     * moved: {@code Settlement.standsOnTheWall} is asked when a plot is chosen
     * and never again, and {@code relocateIfUnsuitable} moves a task for bad
     * ground, not for a fence across it. So the wall was staked over the plot
     * and the building went up on top of it, days later, with nobody having
     * asked either question at the moment they disagreed.
     *
     * <p>Measured over a hundred and seventeen grown towns: of the ninety-nine
     * buildings left with a wall through them once the posts were laid on the
     * line they were staked on, <strong>ninety-two were sites the town had
     * already ordered</strong> when the ring was staked, seven were standing,
     * and not one arrived afterwards.
     *
     * <p>Deliberately not added to {@link #plotCorners}, which is what the ring
     * must <em>enclose</em>. A town builds beyond its wall on purpose when
     * nothing inside will do, and the ring is meant to answer that on its own
     * cadence — obliging it to swallow every such order the moment it was made
     * would drive a re-staking off a single shed and undo the hysteresis
     * {@link #RESTAKE_GROWTH} exists to provide. Not drawn across is a
     * different promise from taken in, and only the first is owed here.
     */
    private static List<Hull.Keepout> plotSquares(Settlement settlement) {
        List<Hull.Keepout> squares = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            keepOut(squares, settlement, building.blueprintId(), building.origin());
        }
        for (BuildTask queued : settlement.buildQueue()) {
            if (queued.isUpgrade()) {
                // The building it raises is standing on that ground already, so
                // its square is above. Two of them would be paid for on every
                // stretch of every candidate line, four relaxation passes deep.
                continue;
            }
            keepOut(squares, settlement, queued.blueprintId(), queued.origin());
        }
        return squares;
    }

    /** One plot's square, at the span the town reserved it. */
    private static void keepOut(List<Hull.Keepout> squares, Settlement settlement,
                                String blueprintId, SimPos origin) {
        if (!BuildPlanner.holdsGround(blueprintId)) {
            return;
        }
        squares.add(new Hull.Keepout(origin.x(), origin.z(),
                BuildPlanner.plotSpanOf(blueprintId, settlement.catalogue()) / 2.0));
    }

    /**
     * Whether either stretch through this vertex is staked over taken ground.
     *
     * <p>Asked of the same list of squares the hull and the push-out are asked
     * of, rather than of the settlement again. The arithmetic is its own —
     * within a block of the whole plot, where {@link Hull#crossesKeepout} gives
     * up the outermost ring for the reason its own constant explains — because
     * a vertex is free to be moved anywhere and a stretch drawn from one is not
     * a stretch the hull built out of that plot's corners.
     */
    private static boolean overTakenGround(List<SimPos> line, int at,
                                           List<Hull.Keepout> squares) {
        SimPos here = line.get(at);
        SimPos before = line.get((at - 1 + line.size()) % line.size());
        SimPos after = line.get((at + 1) % line.size());
        for (Hull.Keepout square : squares) {
            if (Ways.distanceToSquare(before.x(), before.z(), here.x(), here.z(),
                        square.x(), square.z(), square.half()) < 1
                    || Ways.distanceToSquare(here.x(), here.z(), after.x(), after.z(),
                        square.x(), square.z(), square.half()) < 1) {
                return true;
            }
        }
        return false;
    }


    /**
     * The corners of every plot the town has taken, plus a minimum yard.
     *
     * <p>Corners rather than origins: a wall staked around the middles of
     * buildings runs through their walls. The minimum box is what gives a town
     * with one hut a ring worth the name.
     */
    private static List<SimPos> plotCorners(Settlement settlement) {
        SimPos centre = settlement.centre();
        List<SimPos> points = new ArrayList<>(boxAround(centre, MIN_HALF_SIDE));
        for (Building building : settlement.buildings()) {
            int half = BuildPlanner.plotSpanOf(building.blueprintId(),
                    settlement.catalogue()) / 2;
            SimPos at = building.origin();
            // Corners AND the middle of each edge. Four corners are not enough:
            // a concave hull can hold all four and still cut straight across the
            // plot between two of them, which is how a measured town of sixty
            // came to have the wall through ten of its buildings -- the town hall
            // among them, with fourteen posts inside its plot. Every one of them
            // predated the ring, so refusing to BUILD across the wall did not
            // touch it.
            //
            // Repairing the line afterwards was the first attempt and it does not
            // work: pushing a vertex outward swings both of its stretches, and on
            // a concave line a building sitting in the notch beyond a neighbor
            // falls out of the wall. Guarding each move with containment then
            // rejects almost all of them, and ten stayed ten. Giving the hull
            // more to hold is cheaper and it cannot break containment, because
            // every added point is a point the line now has to enclose.
            // Every corner, pushed a yard further out from the middle of town.
            //
            // The plain corners are not enough and the reason is what a hull is.
            // Hull.concave wraps the OUTERMOST points; a building sitting just
            // inside the edge contributes nothing to the boundary, so a stretch
            // running between two further-out buildings cuts straight across it.
            // That is how a measured town came to have the wall through ten of
            // the sixteen buildings standing when it was raised.
            //
            // Offsetting each corner outward makes a near-edge building an
            // extreme point in its own right, so the line has to go round it
            // rather than over it. It cannot lose anything either: every offset
            // point is further out than the corner it came from, so a line that
            // holds the offsets holds the plots.
            for (int sx = -1; sx <= 1; sx++) {
                for (int sz = -1; sz <= 1; sz++) {
                    if (sx == 0 && sz == 0) {
                        continue;
                    }
                    int cx = at.x() + sx * half;
                    int cz = at.z() + sz * half;
                    points.add(new SimPos(cx, centre.y(), cz));
                    points.add(pushedOut(new SimPos(cx, centre.y(), cz), centre, MARGIN));
                }
            }
        }
        return points;
    }

    /** A point moved this much further from the middle of town. */
    private static SimPos pushedOut(SimPos point, SimPos centre, int by) {
        int dx = point.x() - centre.x();
        int dz = point.z() - centre.z();
        double away = Math.hypot(dx, dz);
        if (away < 1) {
            return point;
        }
        return new SimPos(
                point.x() + (int) Math.round(by * dx / away), point.y(),
                point.z() + (int) Math.round(by * dz / away));
    }

    private static List<SimPos> boxAround(SimPos centre, int half) {
        return List.of(
                new SimPos(centre.x() - half, centre.y(), centre.z() - half),
                new SimPos(centre.x() + half, centre.y(), centre.z() - half),
                new SimPos(centre.x() + half, centre.y(), centre.z() + half),
                new SimPos(centre.x() - half, centre.y(), centre.z() + half));
    }

    /**
     * Moves every vertex it safely can directly away from the middle, to leave
     * clear ground between the wall and the buildings.
     *
     * <p>One vertex at a time and each move checked, which is not fussiness.
     * Away from the middle is only the same direction as away from the town on
     * a round town: on a concave line a bay's vertex is pushed roughly
     * <em>along</em> its own stretches rather than square to them, which swings
     * both of them, and a plot sitting just beyond the next vertex round falls
     * out of the wall. Measured on a re-staked ring of a grown town: a bunkhouse
     * left outside the line the whole town was inside, and a stretch drawn
     * across somebody's floor — both after a concave hull that had put every
     * corner inside and crossed nothing.
     *
     * <p>So the margin is a preference and containment is the rule, exactly as
     * it is in {@link #relax}. A vertex that cannot take its margin without
     * letting a plot out or drawing a stretch through one keeps the ground it
     * has, and the wall runs a little closer to the houses just there.
     */
    private static List<SimPos> pushOut(List<SimPos> loop, SimPos centre, int margin,
                                        List<SimPos> plots, List<Hull.Keepout> squares) {
        List<SimPos> line = new ArrayList<>(loop);
        for (int i = 0; i < line.size(); i++) {
            SimPos vertex = line.get(i);
            double dx = vertex.x() - centre.x();
            double dz = vertex.z() - centre.z();
            double away = Math.sqrt(dx * dx + dz * dz);
            if (away < 1e-6) {
                continue;
            }
            line.set(i, new SimPos(
                    vertex.x() + (int) Math.round(dx / away * margin),
                    vertex.y(),
                    vertex.z() + (int) Math.round(dz / away * margin)));
            if (!holdsEverything(line, plots) || crossesAt(line, i, squares)) {
                line.set(i, vertex);
            }
        }
        return line;
    }

    /** Whether either stretch through this vertex is drawn across a plot. */
    private static boolean crossesAt(List<SimPos> line, int at,
                                     List<Hull.Keepout> squares) {
        SimPos here = line.get(at);
        SimPos before = line.get((at - 1 + line.size()) % line.size());
        SimPos after = line.get((at + 1) % line.size());
        return Hull.crossesKeepout(before, here, squares)
                || Hull.crossesKeepout(here, after, squares);
    }

    /**
     * Settles the line onto the ground, without letting anything out.
     *
     * <p>Each vertex looks at the squares within {@link #RELAX_REACH} of itself
     * and takes the one with the least energy — flatter ground, and a shorter
     * straighter line through its two neighbors. A move that would leave a
     * plot corner outside the loop is refused however good the ground is,
     * which is the one rule a wall cannot bend.
     */
    private static List<SimPos> relax(List<SimPos> loop, List<SimPos> plots,
                                      List<Hull.Keepout> squares, SimContext ctx) {
        List<SimPos> line = new ArrayList<>(loop);
        for (int pass = 0; pass < RELAX_PASSES; pass++) {
            boolean moved = false;
            for (int i = 0; i < line.size(); i++) {
                SimPos before = line.get((i - 1 + line.size()) % line.size());
                SimPos after = line.get((i + 1) % line.size());
                SimPos here = line.get(i);
                SimPos best = here;
                double least = energy(here, before, after, ctx);
                for (int dx = -RELAX_REACH; dx <= RELAX_REACH; dx++) {
                    for (int dz = -RELAX_REACH; dz <= RELAX_REACH; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        SimPos candidate = new SimPos(here.x() + dx, here.y(), here.z() + dz);
                        double cost = energy(candidate, before, after, ctx);
                        if (cost >= least) {
                            continue;
                        }
                        line.set(i, candidate);
                        // Containment is not enough, and this is where the wall
                        // came to be staked through ten buildings. A vertex
                        // hunting for flatter ground may land squarely on a plot
                        // and still hold every corner the town asked it to hold —
                        // enclosing a house and standing on it are different
                        // questions, and only the first was being asked.
                        boolean holds = holdsEverything(line, plots)
                                && !overTakenGround(line, i, squares);
                        line.set(i, here);
                        if (holds) {
                            least = cost;
                            best = candidate;
                        }
                    }
                }
                if (!best.equals(here)) {
                    line.set(i, best);
                    moved = true;
                }
            }
            if (!moved) {
                break;   // settled
            }
        }
        return line;
    }

    /**
     * What standing here costs.
     *
     * <p>The ground term is how uneven the four squares around this one are: a
     * wall likes a shelf and dislikes a slope, which is what makes it drift
     * along a contour rather than straight up one. The taut term is the length
     * of the two segments through this vertex, which keeps the line from
     * wandering off after every flat patch it can see.
     */
    private static double energy(SimPos at, SimPos before, SimPos after, SimContext ctx) {
        int here = ctx.bridge().surfaceHeight(at);
        int roughness = 0;
        roughness += Math.abs(ctx.bridge().surfaceHeight(
                new SimPos(at.x() + 1, at.y(), at.z())) - here);
        roughness += Math.abs(ctx.bridge().surfaceHeight(
                new SimPos(at.x() - 1, at.y(), at.z())) - here);
        roughness += Math.abs(ctx.bridge().surfaceHeight(
                new SimPos(at.x(), at.y(), at.z() + 1)) - here);
        roughness += Math.abs(ctx.bridge().surfaceHeight(
                new SimPos(at.x(), at.y(), at.z() - 1)) - here);

        double taut = at.horizontalDistance(before) + at.horizontalDistance(after);
        return GROUND_WEIGHT * roughness + TAUT_WEIGHT * taut;
    }

    /** Whether every plot corner is still inside the line. */
    private static boolean holdsEverything(List<SimPos> line, List<SimPos> plots) {
        for (SimPos plot : plots) {
            if (!Hull.contains(line, plot)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gates go where the roads reach.
     *
     * <p>One to a side, sited on whichever street pushes furthest that way, so
     * the ways out of town line up with the ways through it. A settlement whose
     * roads have not been drawn yet gets the side midpoints, and is re-sited as
     * soon as they are.
     */
    /**
     * Gates: holes in the wall, where the roads go through it.
     *
     * <p>This used to put a gate on the town's bounding box — the middle of its
     * northern extent, its eastern extent and so on — while the ring itself is
     * staked as a <em>concave hull</em> that comes in wherever the buildings do.
     * The two shapes only touch at the extremes, so three of a measured town's
     * four gates stood 9, 10 and 53 blocks away from any wall at all. They were
     * points in a field. {@code isGateway} matched three posts of the twelve
     * that four openings should cut, the wall was raised solid across every
     * road that left town, and one gate opened onto nothing 29 blocks from the
     * nearest road.
     *
     * <p>So a gate is now chosen from the ring itself: the posts where a road
     * actually crosses the line. That makes it a hole in the wall by
     * construction, and a hole where somebody wants to walk, which are the two
     * things a gate has to be. A town whose roads all stop short still gets
     * gates — the compass extremes of the ring — because a wall with no way
     * through it is worse than a wall with a gate nobody uses.
     */
    private static List<SimPos> gatesFor(Settlement settlement, Perimeter ring) {
        List<SimPos> posts = ring.ringPositions();
        if (posts.isEmpty()) {
            return List.of();
        }
        SimPos centre = settlement.centre();

        // Where the ways cross the line, widest road first: a carriageway
        // deserves a gate more than a footpath worn between two sheds.
        List<int[]> crossings = new ArrayList<>();   // {postIndex, width}
        List<PathNetwork.Segment> runs = settlement.paths().segments();
        for (int i = 0; i < posts.size(); i++) {
            SimPos post = posts.get(i);
            int widest = 0;
            for (PathNetwork.Segment run : runs) {
                if (run.width() > widest && run.touches(post, 1)) {
                    widest = run.width();
                }
            }
            if (widest > 0) {
                crossings.add(new int[] {i, widest});
            }
        }
        crossings.sort((a, b) -> b[1] - a[1]);

        int apart = gatesApartOn(ring);
        List<SimPos> gates = new ArrayList<>();
        for (int[] crossing : crossings) {
            if (gates.size() >= MAX_GATES) {
                break;
            }
            SimPos post = posts.get(crossing[0]);
            if (farFromAll(post, gates, apart)) {
                gates.add(post);
            }
        }

        // Top up from the compass extremes of the RING, so even a roadless town
        // has a way in and every gate is still a post on the wall.
        int[][] compass = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] way : compass) {
            if (gates.size() >= MIN_GATES) {
                break;
            }
            SimPos best = null;
            long bestScore = Long.MIN_VALUE;
            for (SimPos post : posts) {
                long score = (long) (post.x() - centre.x()) * way[0]
                        + (long) (post.z() - centre.z()) * way[1];
                if (score > bestScore && farFromAll(post, gates, apart)) {
                    bestScore = score;
                    best = post;
                }
            }
            if (best != null) {
                gates.add(best);
            }
        }
        return List.copyOf(gates);
    }

    /** Whether a candidate gate stands clear of the ones already chosen. */
    private static boolean farFromAll(SimPos candidate, List<SimPos> chosen, int apart) {
        for (SimPos gate : chosen) {
            if (Math.max(Math.abs(gate.x() - candidate.x()),
                         Math.abs(gate.z() - candidate.z())) < apart) {
                return false;
            }
        }
        return true;
    }

    /**
     * How many gates a wall may have, and the fewest it will settle for.
     *
     * <p>A ring riddled with openings is a fence. Six is enough for the roads
     * that matter on a town of any size measured here; four is what a wall gets
     * even when nothing crosses it.
     */
    private static final int MAX_GATES = 6;

    private static final int MIN_GATES = 4;

    /**
     * How far apart two gates must stand, or they are one wide hole.
     *
     * <p>Scaled to the wall rather than fixed. Twenty-four blocks is right for a
     * town whose ring is nine hundred posts round, and on a hamlet's forty-post
     * ring it is wider than the settlement — the first gate claimed the whole
     * wall and the top-up could not place a second, so a fortified camp came out
     * with one way in. A ring gets openings in proportion to how much wall there
     * is to put them in.
     */
    private static int gatesApartOn(Perimeter ring) {
        return Math.max(6, Math.min(24, ring.length() / 8));
    }


    /** Posts go up while the timber lasts and no building is waiting on the crew. */
    private static void raise(Settlement settlement, SimContext ctx) {
        Perimeter perimeter = settlement.perimeter();
        if (perimeter.closed()) {
            // The settlement's own flag latches, and now that a ring can be
            // superseded that matters: re-staking opens the line again, and a
            // town must not be demoted out of FORTIFIED -- or have its camp post
            // start advertising an unfinished wall -- because it grew. It closed
            // a wall once; what it is doing now is moving it.
            if (!settlement.perimeterClosed()) {
                settlement.setPerimeterClosed(true);
                settlement.logEvent(ctx.step(), "The palisade closes around "
                        + settlement.name() + " — " + perimeter.gates().size()
                        + " gates and a sentry walk");
            }
            return;
        }
        // "Shelter and stores before walls" used to be an empty build queue, and
        // that quietly meant never: a town of any size always has something
        // queued, so the wall was only ever raised during the lull a young
        // settlement had while it waited to be allowed to grow. Take that lull
        // away -- which decoupling the wall from stage progression does -- and
        // the ring stayed at nought of four hundred and twenty for good, with a
        // thousand coin and nine hundred timber sitting in the stores.
        //
        // The rule it is replaced by says the same thing and actually holds: do
        // not take the timber a building is waiting on. Coin does the rest of
        // the gatekeeping now, which is the whole point of a wall costing money.
        if (settlement.woodStock() < TIMBER_KEPT_FOR_BUILDING + WOOD_PER_POST) {
            return;
        }
        int hands = (int) settlement.residents().stream()
                .filter(p -> settlement.laboursAs(p, Profession.BUILDER)
                        && !p.isTooWeakToWork())
                .count();
        if (hands <= 0) {
            return;
        }
        // Where there is a hand there is no clock. A watched town raises its
        // wall post by post, with a builder walking to each one -- see
        // PerimeterWorker. The clock here is for the town nobody is looking at,
        // exactly as it is for construction.
        if (handsAreOnIt(settlement, ctx, perimeter)) {
            return;
        }
        int want = Math.min(hands * POSTS_PER_HAND,
                perimeter.length() - perimeter.laid());
        int affordable = Math.min(want,
                settlement.woodStock() / Math.max(1, WOOD_PER_POST));
        affordable = Math.min(affordable,
                settlement.treasury() / Math.max(1, COIN_PER_POST));
        if (affordable <= 0) {
            if (settlement.woodStock() < WOOD_PER_POST) {
                // Same rule as any other build that runs dry: go make more timber.
                BuildPlanner.requestProducer(settlement, TownStores.WOOD, ctx.step(), ctx.bridge());
            }
            return;   // no coin is not a fault; it is a town that cannot afford a wall yet
        }
        // Post by post through the same method the crew's half of the price is
        // written in, rather than by multiplying the two figures out here. The
        // arithmetic is identical; what it buys is that a wall costs one thing,
        // stated once, and a change to what a post costs cannot land on one path
        // and miss the other.
        int raised = 0;
        while (raised < affordable && payForPost(settlement)) {
            raised++;
        }
        perimeter.setLaid(perimeter.laid() + raised);
    }
}
