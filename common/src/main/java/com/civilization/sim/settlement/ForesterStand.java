package com.civilization.sim.settlement;

import com.civilization.sim.culture.TownPlan;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.world.SimContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The wood a settled town has always had.
 *
 * <p>A town that grew raised its lumber camp in a forest and has been cutting it
 * back ever since. A town that was <em>seeded</em> — world generation, or
 * {@code /civ seed} — was written into existence complete, and the ground around
 * its camp is whatever the terrain generator happened to leave there. On a plain
 * that is grass. So a lumberjack walked out of a finished camp, found nothing
 * standing inside the claim, and the town's only timber was the abstract clock's
 * — which is exactly the yield a world can now turn off. A forester with no trees
 * is not a forester.
 *
 * <p>This lays the stand that camp is supposed to have been working: grown trees,
 * of the wood the biome grows, on the free ground of the camp's claim, at the
 * spacing {@code LumberjackWorker} fells and replants at. It runs once, at the
 * moment the camp is first drawn — see {@link Building#isSeeded()} — because that
 * is the first time anybody knows what the ground under the camp actually is.
 *
 * <p>The species and the placing of an actual tree are world work, done by the
 * platform layer. What lives here is the part the simulation is authoritative
 * over: how wide the camp's woodland claim has to be, and which squares of it a
 * tree may stand on.
 */
public final class ForesterStand {

    /**
     * Blocks between one trunk and the next.
     *
     * <p>Wide enough that two vanilla canopies do not grow through each other,
     * and narrow enough that a lumberjack who has felled one is already within
     * {@code LumberjackWorker.WORK_REACH} of walking to the next. Tighter than
     * this and the stand reads as a hedge; wider and a dozen trees no longer fit
     * in a claim.
     */
    public static final int SPACING = 5;

    /**
     * Trees a seeded camp is given, at most.
     *
     * <p>A dozen is a stand somebody has been working, not a forest: enough
     * timber to keep a camp in work for a long while, few enough that the town
     * still stands in open country. The ground decides the real number — a claim
     * that is half lake plants half a stand, and nothing here levels anything to
     * fix that.
     */
    public static final int TREES_WANTED = 12;

    /**
     * Saplings in the camp's own store on the day the town is seeded.
     *
     * <p>Enough to put back what the stand loses before the woodland renews
     * itself, and it is a possession rather than a yield: a working camp has a
     * box of seed in it, the same way it has axes. Deliberately not scaled to
     * {@link #TREES_WANTED} — a camp keeps what it keeps whether or not the
     * ground took every tree.
     */
    public static final int SAPLINGS_ON_HAND = 8;

    /**
     * How far past the edge of the village the woodland claim has to reach.
     *
     * <p>Not decoration. {@code LumberjackWorker} refuses to replant anywhere
     * inside the village — trees between the houses block every path the town
     * lays — so a claim that lies wholly within {@code Settlement.claimRadius}
     * is a claim nothing can ever be planted in, and a stand laid there would be
     * felled once and never come back. A seeded camp therefore claims out past
     * the houses, far enough for three ranks of trees.
     */
    public static final int BELT = 3 * SPACING;

    /**
     * How many candidate squares the stand is actually chosen from.
     *
     * <p>The nearest three dozen, and it is a bound on <em>reading the world</em>
     * rather than on planting. Every square a tree might go on has to be ground
     * somebody can see before any of them is planted — that is what makes the
     * stand the whole dozen nearest the camp rather than whichever half happened
     * to be inside the horizon — and a village that has grown out past its camp
     * has a belt hundreds of squares long. Demanding the whole of one is
     * demanding more loaded ground than a server keeps: the debt would never
     * settle, and a camp owed its wood forever is the same bare field as a camp
     * that was never owed any.
     *
     * <p>Three times what is wanted, for the reason there are more candidates
     * than trees at all: the ground gets a say, and a camp on a lakeshore has to
     * be able to lose two squares in three and still come out with a stand.
     */
    public static final int STAND_SEARCH = 3 * TREES_WANTED;

    /**
     * How many further belts a camp will look out before settling for what it
     * has: four.
     *
     * <p>A bound rather than a budget, and it earns its keep now where it used to
     * be nearly dead code. It was written when the first belt offered between
     * forty-eight and ninety-seven squares and the loop below almost never ran.
     * The belt is now held off the ground the town's own plan has reserved as
     * well — see {@link PlannedGround} — and a closely plotted arrangement has
     * reserved very nearly the whole annulus immediately outside its houses. So
     * looking further out is the <em>ordinary</em> case rather than the exception.
     *
     * <p>Measured on flat ground across all fifteen arrangements, at the size a
     * seeded village stands: the first belt offers between <strong>nought and
     * fifty</strong> squares — a bastide nought, a green two — and the claim the
     * camp settles on after widening runs from 44 to 84 blocks and offers between
     * 37 and 124. Every arrangement gets its stand, and none of them needed more
     * than two further belts of the four.
     */
    public static final int BELTS_OUT = 4;

    private ForesterStand() {
    }

    /**
     * The woodland a seeded camp works.
     *
     * <p>Centered on the camp, like any other, and no smaller than the default —
     * but widened until it reaches {@link #BELT} blocks beyond the village edge,
     * because that annulus is the only ground a lumberjack will replant on.
     *
     * <p><strong>Widened as far as it actually takes, and that is the whole of
     * the second bug this method has had.</strong> It used to stop at
     * {@code LumberPlanner.MAX_RADIUS} — what a player can dial up on the camp
     * block — on the grounds that nothing here should produce a claim they could
     * not have made themselves. That reasoning holds only while a village stays
     * inside sixty-four blocks of its own lumber camp, which the concentric
     * rings did and no other arrangement does: a crossroads town flings its
     * frontage out along four arms, a warren buds knots off knots, and the
     * claim circle that contains the outermost of them is a hundred blocks and
     * more. Clamped, the camp's whole claim then lay <em>inside</em> the village,
     * every square of it was refused as somebody's doorstep, and the camp
     * settled its debt having planted nothing at all. A claim that cannot reach
     * the countryside is not a claim; the reach is the requirement and the dial
     * is the convenience, so the dial gives way.
     */
    public static WorkArea woodlandFor(SimPos camp, SimPos center, int claimRadius) {
        int outFromTheHouses = claimRadius
                - (int) Math.floor(camp.horizontalDistance(center)) + BELT;
        int radius = Math.max(LumberPlanner.DEFAULT_RADIUS, outFromTheHouses);
        return new WorkArea(camp, radius);
    }

    /**
     * Where the trees of the stand stand.
     *
     * <p>A grid on {@link #SPACING}, anchored on the camp so the ranks line up
     * with the camp rather than with the world origin, minus everything a tree
     * has no business being on: the camp's own square, anything outside the
     * claim, anything inside the village, any plot, road or wall the town's own
     * siting rules already refuse — {@code Settlement.isPlotFree} answers those
     * in one question, and answering it the same way is the point — and every
     * plot and street of the town's plan, standing or not, which is
     * {@link PlannedGround} and is the half that was missing.
     *
     * <p>Nearest the camp first, so a claim that only yields a handful of usable
     * squares yields the handful closest to the people who work them, and so the
     * platform layer can simply walk the list until it has planted enough.
     *
     * <p>More positions are returned than {@link #TREES_WANTED}: these are
     * candidates, not a stand. Only the world knows which of them are lake,
     * cliff or bare stone, so it takes the list in order and stops when it has
     * its dozen.
     */
    public static List<SimPos> candidates(Settlement town, WorkArea area) {
        return freeOf(town, grid(town, area), Integer.MAX_VALUE);
    }

    /**
     * The ground the town's own plan has already spoken for, as one set.
     *
     * <p><strong>The plan, and not merely what is standing.</strong> This is the
     * second half of the stand-stripping fault and the half that caused it.
     * {@code Settlement.isPlotFree} asks the buildings that stand, the builds
     * that are queued, the ways that are laid and the wall that is staked — every
     * one of which is a thing the town has <em>done</em>. A town is nineteen
     * buildings inside a plan of two hundred and fifty-six, so a belt chosen
     * against what is standing is a belt planted squarely on the two hundred and
     * thirty-seven plots the town has not reached yet. Every one of those is
     * cleared in its turn, and the camp watches its wood be built on.
     *
     * <p>The plan is known from the moment the town is seeded and its plots do
     * not move — that is the whole point of {@code PlannedLayout}'s fixed plan
     * size — so there is nothing to wait for. The same reasoning
     * {@code PathPlanner.heldGround} already gives for keeping a routed road off
     * every plot the plan might still use: ordering was the hole, and a plan that
     * does not move can be respected from the start.
     *
     * <p>The streets too. A lane the plan has drawn is ground no tree may stand
     * on for the same reason a plot is — and worse, because a road is built
     * first: a trunk on the carriageway is felled by the crew that opens it.
     *
     * <p><strong>{@link Founding#PLOTS_ENOUGH_FOR_ANY_PROGRAM} of them and not the
     * whole two hundred and fifty-six</strong>, and the difference is the whole
     * difference between a rule and a sterilization. A layout will answer for any
     * index it is handed, and a lattice answers by tiling the plane: a ring plan of
     * two hundred and fifty-six reaches a hundred and forty blocks out on a
     * sixteen-block pitch, so treating all of it as reserved leaves a forester
     * nowhere in the county. Measured, before this was bounded: a bastide and a
     * stronghold were left <em>nought</em> squares and a green two.
     *
     * <p>Sixty-four is the number a seeded town actually asks its plan for, and it
     * is documented there as room for the whole fifteen-building program with a
     * wide margin. That is ground the town has reserved. Past it is ground the
     * layout merely has an opinion about, and an opinion is not a claim.
     *
     * <p>Held as a set of the camp's own grid squares rather than asked square by
     * square. A widened claim holds thousands of squares and the plan holds
     * hundreds of plots; the product is a quarter of a million comparisons a
     * step, which is how a siting pass comes to take a second. Marking the plan
     * onto the grid once costs a few thousand.
     */
    private static final class PlannedGround {

        /**
         * How wide a tree is held to be here: one column, its trunk.
         *
         * <p>Not its {@link #SPACING} square and emphatically not its crown, for
         * the reason {@code PathPlanner.TRUNK_SPAN} gives in the same words: a
         * canopy is about as wide as the stand's own pitch, so measuring the
         * keepout at the crown fences off the entire belt. Built that way first
         * and measured: a ring plan's plots sit sixteen apart and a crown-wide box
         * is fifteen, so the belt was left fewer squares than it wanted trees and
         * a seeded camp came out with a hedge.
         *
         * <p>A trunk plus a curb is what clearing a plot actually reaches: the
         * excavation takes the building's footprint and its doorstep ring, and a
         * trunk outside that is a tree in somebody's garden rather than a tree in
         * the way.
         */
        private static final int TRUNK = 1;

        /** Bare ground between a tree and a carriageway, as a plot gets a curb. */
        private static final int CURB = 1;

        private final SimPos anchor;
        private final java.util.Set<Long> taken = new java.util.HashSet<>();

        private PlannedGround(Settlement town, SimPos anchor) {
            this.anchor = anchor;
            TownPlan plan = town.arrangement()
                    .planFor(town.center(), Founding.PLOTS_ENOUGH_FOR_ANY_PROGRAM);
            for (TownPlan.Plot plot : plan.plots()) {
                mark(plot.at(), plot.span() / 2 + TRUNK + CURB);
            }
            for (TownPlan.Street street : plan.streets()) {
                int reach = street.width() / 2 + TRUNK + CURB;
                for (SimPos along : street.path()) {
                    mark(along, reach);
                }
                // Between the drawn points as well. A street is a polyline on
                // SEGMENT pitch and a tree can stand in the middle of a run.
                List<SimPos> path = street.path();
                for (int i = 1; i < path.size(); i++) {
                    SimPos from = path.get(i - 1);
                    SimPos to = path.get(i);
                    int steps = Math.max(Math.abs(to.x() - from.x()),
                                         Math.abs(to.z() - from.z()));
                    for (int s = 1; s < steps; s += SPACING) {
                        mark(new SimPos(
                                from.x() + (to.x() - from.x()) * s / steps, from.y(),
                                from.z() + (to.z() - from.z()) * s / steps), reach);
                    }
                }
            }
        }

        /** Every grid square of the camp's lattice within reach of this claim. */
        private void mark(SimPos at, int reach) {
            int fromX = ceilDiv(at.x() - reach - anchor.x(), SPACING);
            int toX = Math.floorDiv(at.x() + reach - anchor.x(), SPACING);
            int fromZ = ceilDiv(at.z() - reach - anchor.z(), SPACING);
            int toZ = Math.floorDiv(at.z() + reach - anchor.z(), SPACING);
            for (int kx = fromX; kx <= toX; kx++) {
                for (int kz = fromZ; kz <= toZ; kz++) {
                    taken.add(((long) kx << 32) ^ (kz & 0xFFFFFFFFL));
                }
            }
        }

        /**
         * Rounding toward negative infinity throughout, which is the whole of a
         * bug this class had for an afternoon.
         *
         * <p>Java's integer division truncates toward zero, so the cell of a
         * square west or north of the anchor came out one too high and a trunk was
         * looked up in the wrong cell. It showed on one arrangement of fifteen — an
         * orc ring whose camp sits at (-16, -26) — because it only bites on the
         * negative side of the anchor.
         */
        private static int ceilDiv(int a, int b) {
            return -Math.floorDiv(-a, b);
        }

        private boolean holds(SimPos spot) {
            long kx = Math.floorDiv(spot.x() - anchor.x(), SPACING);
            long kz = Math.floorDiv(spot.z() - anchor.z(), SPACING);
            return taken.contains((kx << 32) ^ (kz & 0xFFFFFFFFL));
        }
    }

    /**
     * Every square of the camp's grid a tree could stand on, nearest first, on
     * the geometry alone.
     *
     * <p>Split out from {@link #candidates} so the two questions can be asked at
     * two prices. This half is arithmetic and costs nothing; the other half asks
     * the settlement whether each square is somebody's plot, road or wall, which
     * walks every building and every stretch of way in the town. A claim widened
     * round a long-armed city holds thousands of squares and only the nearest
     * dozen are ever planted, so the dear question is worth asking in order and
     * stopping — see {@link #stand}.
     */
    private static List<SimPos> grid(Settlement town, WorkArea area) {
        SimPos camp = area.center();
        int radius = area.radius();
        int claim = town.claimRadius();
        List<SimPos> spots = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += SPACING) {
            for (int dz = -radius; dz <= radius; dz += SPACING) {
                if (dx == 0 && dz == 0) {
                    continue;   // the camp is standing here
                }
                SimPos spot = camp.offset(dx, 0, dz);
                if (!area.contains(spot)) {
                    continue;
                }
                if (spot.horizontalDistanceSq(town.center()) <= (long) claim * claim) {
                    continue;   // the village; a tree here is in somebody's way
                }
                spots.add(spot);
            }
        }
        spots.sort(Comparator.comparingLong(spot -> spot.horizontalDistanceSq(camp)));
        return spots;
    }

    /**
     * The squares of that grid the town has not already spoken for, at most
     * {@code wanted} of them.
     *
     * <p>Sorting before the filter rather than after it, which is the same list:
     * the sort is stable and keyed on distance alone, so dropping squares from
     * an ordered list and ordering what is left of an unfiltered one come to the
     * same order. What it buys is the stopping — a caller that wants a dozen
     * asks the expensive question a dozen-odd times instead of four thousand.
     */
    private static List<SimPos> freeOf(Settlement town, List<SimPos> grid, int wanted) {
        List<SimPos> free = new ArrayList<>();
        if (grid.isEmpty()) {
            return free;
        }
        // The plan is marked out once, off the camp's own lattice, before a
        // single square is weighed -- see PlannedGround for why asking it per
        // square is not affordable.
        //
        // Phased off a square of the grid rather than off the camp, because the
        // two are not the same thing. grid() walks dx from -radius upward in
        // steps of SPACING, so the ranks are phased on the far edge of the claim
        // and line up with the camp only when the radius happens to divide by
        // five -- which the doc on candidates() has always claimed they do. Any
        // member of the grid carries its true phase, so the first one is used.
        PlannedGround planned = new PlannedGround(town, grid.get(0));
        for (SimPos spot : grid) {
            if (free.size() >= wanted) {
                break;
            }
            if (planned.holds(spot)) {
                continue;   // the town's own plan wants this ground
            }
            if (town.isPlotFree(spot, SPACING, null)) {
                free.add(spot);   // not a plot, not a road, not the wall
            }
        }
        return free;
    }

    /**
     * Where this town's stand actually stands, worked out rather than stored.
     *
     * <p>The camp keeps a count of its trees and never kept their squares, and it
     * does not have to: the stand is the nearest {@link #TREES_WANTED} squares of
     * the camp's own grid that the town has not spoken for, which is a function
     * of the camp, the claim and what is standing — all three of which the
     * settlement already knows. A list on the building would be one more thing to
     * write, to load, and to get out of step with the ground.
     *
     * <p>What it is for is the roads. A town goes on building after its stand is
     * planted, and a lane run out to the last farm on the edge of the claim used
     * to be laid straight through the belt, felling the very trunks the camp
     * exists to cut — see {@code PathPlanner}, which holds a road off these
     * squares exactly as it holds one off a plot.
     *
     * <p>Empty for a town with no lumber camp, and empty before the camp has a
     * woodland claim to work. It stays answerable afterwards whether the stand was
     * seeded or grew: the squares are where a stand goes either way, and keeping a
     * road off a dozen squares of a forester's belt costs the network nothing.
     */
    public static List<SimPos> stand(Settlement town) {
        WorkArea wood = town.lumberArea();
        if (wood == null || town.buildingWithRole(BuildingRole.LUMBER_CAMP) == null) {
            return List.of();
        }
        return freeOf(town, grid(town, wood), TREES_WANTED);
    }

    /**
     * Plants the stand a seeded camp should always have had, once.
     *
     * <p>Called from {@code Settlement.materializePending} the moment the camp is
     * first drawn — the earliest step at which the ground under it is loaded and
     * therefore knowable. The camp's woodland claim is widened first, so the
     * squares chosen are squares a lumberjack will later replant on.
     *
     * <p>Nothing happens for a founded camp: the four pioneers of a charter get
     * the country they walked into, and their lumberjacks plant saplings and wait
     * for them, which is the whole of the early game they are for.
     *
     * <p><strong>The ground has to be readable first, and that is the whole of
     * the bug this guard exists for.</strong> A camp is drawn the instant its own
     * chunk arrives — which is, by definition, the far edge of what the player
     * can see — while the stand it is owed lies a further {@link #BELT} beyond
     * the edge of the village, out in chunks nobody has loaded. The platform
     * plants nothing on ground it cannot read and says so by returning nought,
     * and the debt used to have been cancelled a line earlier: every town raised
     * by world generation lost its wood at exactly the moment it was supposed to
     * get one. So the camp keeps its mark until the stand can be seen, and is
     * asked again on the next step until it can. It terminates because standing
     * at the camp is what loads the belt, and a camp with no candidate squares at
     * all settles on the first ask.
     *
     * <p>The stand, and not the belt. What has to be readable is the
     * {@link #STAND_SEARCH} squares nearest the camp — the ones a tree is
     * actually going to go on — rather than every square of the claim. The
     * difference does not show on a tidy ring town, whose belt is a few dozen
     * squares either way; it is the difference between a stand and a bare field
     * on a town whose arms have carried the village edge a hundred blocks out,
     * because no server keeps that much ground loaded at once and a camp waiting
     * for it would wait forever.
     *
     * @return whether the debt is settled — false means the ground is still
     *         unread and the camp is owed its wood yet
     */
    public static boolean raise(Settlement town, Building camp, SimContext ctx) {
        if (camp.role() != BuildingRole.LUMBER_CAMP) {
            return true;   // nothing is owed, so nothing is outstanding
        }
        WorkArea woodland = woodlandFor(camp.origin(), town.center(), town.claimRadius());
        WorkArea standing = town.lumberArea();
        // Never overrule a claim somebody has moved. A camp block the player has
        // already pointed somewhere else is their decision, and widening it back
        // would undo it silently.
        boolean ours = standing == null || standing.center().equals(woodland.center());
        if (!ours) {
            woodland = standing;
        }
        if (!ctx.bridge().isLoaded(camp.origin())) {
            if (ours) {
                town.setLumberArea(woodland);
            }
            return false;   // the camp's own ground is dark; ask again another step
        }
        List<SimPos> spots = candidates(town, woodland);
        // And further out again while the belt is too thin to hold a stand. The
        // geometry usually settles this on the first ask — a village edge with
        // three ranks of trees outside it offers several dozen squares — but a
        // long-armed arrangement can leave the annulus a sliver: a ring road
        // laid along it, an arm of frontage across it, and the dozen squares
        // wanted are four. A camp does not stop at four. It looks another belt
        // out, and another, which is what anybody working that wood would do.
        for (int wider = 0; ours && wider < BELTS_OUT && spots.size() < STAND_SEARCH;
                wider++) {
            woodland = woodland.withRadius(woodland.radius() + BELT);
            spots = candidates(town, woodland);
        }
        if (ours) {
            town.setLumberArea(woodland);
        }
        if (spots.size() > STAND_SEARCH) {
            spots = spots.subList(0, STAND_SEARCH);
        }
        for (SimPos spot : spots) {
            if (!ctx.bridge().isLoaded(spot)) {
                return false;   // some of the wood is still unread
            }
        }
        int planted = ctx.bridge().plantGrownTrees(spots, TREES_WANTED);
        // The camp's ledger is sized from what actually went in, rather than
        // being made to go and count trees the platform has just this moment
        // planted and knows the number of. Everywhere else a stand is counted
        // off the ground — see {@code LumberPlanner.reckonStand} — and this is
        // the one moment the world can simply say.
        //
        // Only when something went in. A platform that planted nothing has not
        // told us the ground is bare — it may be thick with trees the generator
        // put there — so the camp is left uncounted and counts for itself.
        if (planted > 0) {
            Stand.recount(camp, planted);
            town.logEvent(ctx.step(), "The wood around the lumber camp stands "
                    + planted + " trees deep");
        }
        return true;
    }
}
