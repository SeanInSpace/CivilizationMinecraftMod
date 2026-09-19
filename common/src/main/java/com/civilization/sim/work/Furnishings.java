package com.civilization.sim.work;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.Homes;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.Perimeter;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownEdge;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.settlement.WorkArea;
import com.civilization.sim.world.SimContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a town puts on the ground between its buildings.
 *
 * <p><strong>What this is for.</strong> A screenshot of a finished ring town —
 * fifteen buildings, fifteen people, the whole radial plan opened and paved —
 * is a wide loop of dirt road around a field of empty grass with a few houses
 * standing on it. Every building in that picture is dressed: {@code Parts.dress}
 * puts a porch and shutters on a cottage, {@code CivicParts} puts an awning on a
 * stall and a belfry on a hall, {@code HouseStyle} decides whose brick it all is.
 * Nothing at all is placed <em>between</em> them, so a town reads as a set of
 * models on a table rather than as a place people live in.
 *
 * <p>So the ground between the buildings is a public work, exactly like the
 * roads, the wall and the lighting: planned here without a world, raised piece by
 * piece by a builder where somebody is watching, and by the clock where nobody
 * is. {@code FurnishingLayer} is its platform half and {@link FurnishingStyle} is
 * what each people's dressing looks like.
 *
 * <p><strong>Why it is derived rather than stored.</strong> Word for word
 * {@link LightPlanner}'s argument. A yard's position is a function of the house
 * it belongs to, a hedge's of the street it runs along, and both of those are
 * already saved; storing the list would be storing a second copy of the town's
 * own shape. What <em>is</em> stored is one number — how many pieces the town has
 * raised — and it is an index into this list for the same reason the perimeter
 * and the lighting each keep one.
 *
 * <p><strong>The order is center-outward</strong>, with the same caveat stated in
 * the same place: a town grows outward, so new pieces mostly append, and an
 * infill house inside the ring does shift every index past it by one. That is
 * survivable rather than ignored, because a shifted index sends a builder to a
 * piece that is already standing and {@code FurnishingLayer.owed} reads the
 * ground before it charges for anything.
 *
 * <p><strong>Dressing is the last thing a town does.</strong> It sits below the
 * lighting in {@link PublicWorks}, and unlike the lighting it does not interleave
 * with building at all: a settlement raises a yard only once its build queue is
 * idle, its streets are opened and its lamps are up. A village that fenced a
 * kitchen garden while its bunkhouse waited would be a village with a very
 * pretty famine.
 */
public final class Furnishings {

    private Furnishings() {
    }

    /**
     * What one piece costs the town: timber, stone and saplings.
     *
     * <p>Three resources, and a rick of hay costs none of them. Charging it in
     * {@code TownStores.GRAIN} was written and then taken back out, because grain
     * is not a building material here — it is the thing a town turns into bread,
     * and the whole of {@code FoodPlanner} is about carrying it from the field to
     * the oven. A dressing work that quietly took nine sheaves off a farm's shelf
     * every time it stood a haystack would be paying for scenery out of the food
     * ledger, and a town would starve prettily. Hay is what is left over after a
     * harvest; the farm has it already. See {@link Piece#HAYSTACK}.
     */
    public record Cost(int wood, int stone, int saplings) {
    }

    /**
     * A kind of thing a town stands on its own free ground.
     *
     * <p>Each carries the two facts the simulation needs and no more: how much
     * ground it takes — as a half-extent, so a piece is a square box a planner can
     * reason about without knowing what is drawn in it — and what it costs. What
     * it is <em>made</em> of is the platform's, exactly as a lamp's blocks are.
     */
    public enum Piece {

        /**
         * The paved square at the heart, with benches round it and a notice board.
         *
         * <p>The one piece whose size is not fixed: see {@code theSquare}, which
         * asks for thirteen clear blocks at the middle of the town, then eleven,
         * then nine, and takes the first that fits. A hillside with a hall already
         * standing on it does not always have thirteen, and a town with no square
         * at all would be a worse answer than a small one.
         */
        SQUARE(6, 3, new Cost(16, 96, 0)),

        /** A ring of stone with water in it, on the heart, where no market stands. */
        WELL(1, 3, new Cost(2, 12, 0)),

        /** A fenced kitchen garden behind a house: crop rows, a flower bed, a gate. */
        YARD(3, 3, new Cost(16, 0, 0)),

        /** Split logs stacked beside a lumber camp or a hearth. */
        WOODPILE(1, 3, new Cost(6, 0, 0)),

        /**
         * A rick of hay by a farm, and the one piece that is free.
         *
         * <p>Free for the reason the clearing is: it is not bought, it is left
         * over. Hay is the straw off a harvest the farm has already taken, so the
         * only thing standing it costs the town is somebody's afternoon. See
         * {@link Cost}, which is where charging it in grain was tried and dropped.
         */
        HAYSTACK(1, 3, new Cost(0, 0, 0)),

        /** Crates and barrels stood outside a storehouse, a warehouse or a market. */
        CRATES(1, 3, new Cost(8, 0, 0)),

        /** A grid of the country's own trees on free ground: one per ten residents. */
        ORCHARD(2, 3, new Cost(0, 0, 9)),

        /** A clipped run of hedge along an opened street, broken at the doors. */
        HEDGE(2, 1, new Cost(4, 0, 0)),

        /** One tree on the outer verge of a ring road, every twelve blocks. */
        AVENUE_TREE(0, 1, new Cost(0, 0, 1)),

        /** A post and a board at a junction, pointing at the hall. */
        SIGNPOST(1, 1, new Cost(4, 0, 0)),

        /**
         * The inn's own board, on a post on its frontage.
         *
         * <p>Shaped exactly like a {@link #SIGNPOST} and costed like one,
         * because it is one: the difference between the two is entirely what is
         * written on the board and which way round it hangs — a signpost faces
         * the middle of the town, and an inn board faces the street, because
         * nobody reads an inn sign from inside the inn.
         *
         * <p>Its own kind rather than a signpost planted by the inn, so that
         * {@code Signage} can tell them apart without having to work out which
         * building a post happens to be standing beside.
         */
        INN_SIGN(1, 1, new Cost(4, 0, 0)),

        /** A ring of stones round a fire, which is what a camp has instead of a hearth. */
        FIRE_PIT(1, 3, new Cost(4, 6, 0)),

        /** A goblin cage: what the mire keeps beside its chieftain's hut. */
        CAGE(1, 3, new Cost(8, 0, 0)),

        /** Sharpened stakes driven into the verge, which is how a warhost decorates. */
        STAKES(1, 1, new Cost(6, 0, 0)),

        /**
         * One stone for one of the town's dead, in a row on the outer verge.
         *
         * <p>The only piece on this list that is not a function of the buildings
         * and the streets alone: there is one for every burial the town has ever
         * made — {@code Settlement.buried}, capped at {@link #GRAVES_PLANNED} —
         * so a town that has never lost anybody has no graveyard and a town that
         * has lost twenty has twenty stones. Counted off the running total and
         * not off {@code Settlement.dead}, which keeps only the last dozen names:
         * pairing the row to that list made a town's thirteenth death recut every
         * board it had. Where the row goes is derived like everything else here —
         * see {@code theGraves} — which is what lets a town bury somebody who
         * starved while nobody was looking and have the grave be there when
         * somebody comes back.
         *
         * <p>A block of stone and a plank for the board, which is what a marker
         * costs, and the clearance of a thing in its own right rather than of a
         * verge piece: a row of graves is a churchyard and a churchyard is not on
         * the pavement.
         */
        GRAVE(1, 3, new Cost(1, 2, 0));

        private final int reach;
        private final int roadClearance;
        private final Cost cost;

        Piece(int reach, int roadClearance, Cost cost) {
            this.reach = reach;
            this.roadClearance = roadClearance;
            this.cost = cost;
        }

        /**
         * Half the ground this takes, in blocks, measured as a square.
         *
         * <p>A square and not a rectangle, though a yard plainly is one. The
         * planner uses this for two things — whether the ground is free and
         * whether two pieces collide — and being generous in both directions is
         * the safe way to be wrong: an over-large box refuses a site that would
         * have fitted, and an under-large one puts a fence through a wall.
         */
        public int reach() {
            return reach;
        }

        /**
         * How far clear of a carriageway this has to stand.
         *
         * <p>Three for everything that is a thing in its own right, so a kitchen
         * garden does not crowd the street. One for the three pieces whose whole
         * point is the verge — a hedge runs along a street, an avenue tree stands
         * on its outer edge, a signpost is at the corner of a junction — which
         * need only to be off the stones.
         */
        public int roadClearance() {
            return roadClearance;
        }

        /** What the town pays for one of these. */
        public Cost cost() {
            return cost;
        }
    }

    /**
     * One planned piece: what it is, where it stands, which way it faces and how
     * big it came out.
     *
     * <p>The reach travels with the piece rather than being read off the kind,
     * because the square's is decided by how much clear ground there was at the
     * middle of the town. Everything else copies its kind's.
     *
     * @param facing 0 south, 1 west, 2 north, 3 east — {@code Building.facing}'s
     *               own convention, so a yard behind a house and the house itself
     *               are described in the same terms.
     */
    public record Furnishing(SimPos at, Piece piece, int facing, int reach) {

        public Furnishing(SimPos at, Piece piece, int facing) {
            this(at, piece, facing, piece.reach());
        }
    }

    // --- the numbers ---------------------------------------------------------

    /**
     * Blocks between the trees of an avenue: twelve.
     *
     * <p>Half again the lighting's {@link LightPlanner#SPACING}, and that gap is
     * the point rather than an accident. Lamps and trees both want the verge, a
     * tree beside a lamp is a lamp inside a crown, and two runs at eight and at
     * twelve fall on the same block only every twenty-four — so most avenue trees
     * are nowhere near a lamp before anything has to be refused.
     */
    public static final int AVENUE_SPACING = 12;

    /** Residents to an orchard: ten. */
    public static final int RESIDENTS_PER_ORCHARD = 10;

    /** How far apart two hedges along the same street stand, so a run has gaps. */
    public static final int HEDGE_SPACING = 7;

    /** Pieces the clock raises in one step: one. */
    public static final int PIECES_PER_STEP = 1;

    /**
     * Timber the dressing will not touch.
     *
     * <p>The same number the lighting keeps back, and for a stronger reason: a
     * lamp keeps people alive and a garden fence does not, so if either is going
     * to starve a bunkhouse it had better not be this one. In practice the
     * reserve almost never bites, because {@link #worthStarting} already refuses
     * to dress a town whose build queue has anything in it at all.
     */
    public static final int TIMBER_KEPT_FOR_BUILDING = LightPlanner.TIMBER_KEPT_FOR_BUILDING;

    /**
     * How far a piece stays clear of a planned lamp: one block past its own edge.
     *
     * <p>One rather than three, and the number was arrived at from both ends. It
     * has to be at least one, because a piece whose box merely excluded the lamp
     * column would let an avenue tree stand a pace from a lamp and grow a crown
     * over it — which is a lamp that lights the underside of a canopy. And it may
     * not be much more than one, because a lamp stands every eight blocks along
     * the same verge a hedge runs down: at three, more than half the hedges in a
     * town were refused by the lighting and a street had a rail of hedge in the
     * one stretch between lamps where nobody looks.
     */
    public static final int LAMP_CLEARANCE = 1;

    /** How far a piece stays from the staked wall line. */
    public static final int WALL_CLEARANCE = 2;

    /**
     * How far a piece stays from a doorstep, so nobody has to climb their fence
     * to get in.
     *
     * <p>Three, which is the doorstep itself and the two paces in front of it.
     * Less than that and a builder's own path home runs through a flower bed;
     * more and a terraced street has nowhere for a hedge at all.
     */
    public static final int DOOR_CLEARANCE = 3;

    // --- the list ------------------------------------------------------------

    /**
     * Every piece this town's buildings and streets call for, in the order it
     * raises them.
     *
     * <p>A function of the settlement and of nothing else: no clock, no die, no
     * world. It is called from both fidelities and from the drawing sweep, so it
     * has to stay that way — a plan that changed its mind would have a crew
     * forever undoing itself, which is the rule every part in {@code Parts} obeys
     * for the same reason.
     *
     * <p>The one thing it writes is the answer, kept on the town against a number
     * naming the town's own shape: {@link #shapeOf}, and see
     * {@code Settlement.cachedDressing} for why the memo exists and why it is a
     * field on the town rather than a table somebody has to empty. It is not a
     * cache in the sense that matters — ask for the plan twice without touching
     * the town and you get the same list, which is what you would have got anyway.
     *
     * <p>The passes run in the order a town would care about them — the square
     * and the well at the middle, then the things that belong to a particular
     * building, then the things that fill in what is left — because an earlier
     * candidate wins any ground a later one wanted. The list that comes out is
     * sorted center-outward regardless; the pass order is about <em>which</em>
     * piece gets a contested spot, not about when it is built.
     */
    public static List<Furnishing> pieces(Settlement settlement) {
        long stamp = shapeOf(settlement);
        List<Furnishing> known = settlement.cachedDressing(stamp);
        if (known != null) {
            return known;
        }
        List<Furnishing> planned = workOutThePlan(settlement);
        settlement.cacheDressing(stamp, planned);
        return planned;
    }

    /**
     * A number that changes whenever anything this plan reads changes.
     *
     * <p>The memo's whole correctness, so it is written to be over-inclusive on
     * purpose: every field any pass below touches goes in, and a few that only
     * {@code LightPlanner} touches, because the lamps are part of what the siting
     * consults. What it costs is a walk of the buildings, the queue, the streets
     * and the wall — a couple of hundred multiplications on a grown town, against
     * sixteen and a half milliseconds for the plan it saves.
     *
     * <p>The one thing deliberately left out is the build catalog, which
     * {@code BuildPlanner.plotSpanOf} reads: a catalog is loaded once and does not
     * change under a running world. If datapack reloading ever changes that, this
     * is the line that has to change with it.
     */
    private static long shapeOf(Settlement settlement) {
        long h = mix(settlement.center().x() * 31L + settlement.center().z());
        h = mix(h ^ settlement.claimRadius());
        h = mix(h ^ (settlement.cultureId() == null ? 0 : settlement.cultureId().hashCode()));
        h = mix(h ^ settlement.residents().size());
        // The one input to this plan that is not the town's shape. A grave is
        // planned per burial the town has ever made, so a death has to move the
        // memo or a town would bury somebody and grow no churchyard until the
        // next house went up. See Piece.GRAVE.
        //
        // The running total and not the roster's size, which stops moving at
        // twelve: a town's thirteenth death would otherwise leave the memo
        // unchanged and the thirteenth stone unplanned for ever.
        h = mix(h ^ settlement.buried());
        for (Building building : settlement.buildings()) {
            h = mix(h ^ building.origin().x());
            h = mix(h ^ building.origin().z());
            h = mix(h ^ building.origin().y());
            h = mix(h ^ building.facing());
            h = mix(h ^ building.footprint().width());
            h = mix(h ^ building.footprint().depth());
            h = mix(h ^ (building.footprint().isKnown() ? 1 : 0));
            h = mix(h ^ (building.blueprintId() == null ? 0 : building.blueprintId().hashCode()));
        }
        for (BuildTask task : settlement.buildQueue()) {
            h = mix(h ^ task.origin().x());
            h = mix(h ^ task.origin().z());
            h = mix(h ^ (task.isUpgrade() ? 1 : 0));
            h = mix(h ^ (task.blueprintId() == null ? 0 : task.blueprintId().hashCode()));
        }
        PathNetwork paths = settlement.paths();
        if (paths != null) {
            List<PathNetwork.Segment> runs = paths.segments();
            for (int i = 0; i < runs.size(); i++) {
                PathNetwork.Segment run = runs.get(i);
                h = mix(h ^ run.from().x());
                h = mix(h ^ run.from().z());
                h = mix(h ^ run.to().x());
                h = mix(h ^ run.to().z());
                h = mix(h ^ run.width());
                h = mix(h ^ (paths.isOpened(i) ? 2 : 0) ^ (paths.isUnwalkable(i) ? 1 : 0));
            }
        }
        Perimeter perimeter = settlement.perimeter();
        if (perimeter != null) {
            h = mix(h ^ perimeter.laid());
            h = mix(h ^ perimeter.pulled());
            for (SimPos vertex : perimeter.vertices()) {
                h = mix(h ^ vertex.x());
                h = mix(h ^ vertex.z());
            }
            for (Perimeter.Retired retired : perimeter.retired()) {
                h = mix(h ^ retired.laid());
                for (SimPos vertex : retired.vertices()) {
                    h = mix(h ^ vertex.x());
                    h = mix(h ^ vertex.z());
                }
            }
        }
        WorkArea belt = settlement.lumberArea();
        if (belt != null) {
            h = mix(h ^ belt.center().x());
            h = mix(h ^ belt.center().z());
            h = mix(h ^ belt.radius());
        }
        return h;
    }

    /** SplitMix64's finalizer, for the reason {@code Culture.layoutFor} uses it. */
    private static long mix(long value) {
        long h = value * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /** The plan itself, worked out from scratch. See {@link #pieces}. */
    private static List<Furnishing> workOutThePlan(Settlement settlement) {
        FurnishingStyle style = styleOf(settlement);
        Keepouts ground = Keepouts.of(settlement);
        List<Furnishing> kept = new ArrayList<>();
        theSquare(settlement, style, ground, kept);
        theWell(settlement, style, ground, kept);
        theYards(settlement, style, ground, kept);
        theWoodpiles(settlement, style, ground, kept);
        theHaystacks(settlement, style, ground, kept);
        theCrates(settlement, style, ground, kept);
        theCampFires(settlement, style, ground, kept);
        theCages(settlement, style, ground, kept);
        theOrchards(settlement, style, ground, kept);
        theHedges(settlement, style, ground, kept);
        theVergeTrees(settlement, style, ground, kept);
        theSignposts(settlement, style, ground, kept);
        theInnBoard(settlement, style, ground, kept);
        // Last, and it is a pass order rather than a build order: the graveyard
        // stands further out than anything else in the town, so it wins no
        // ground anybody else wanted and loses none it needed.
        theGraves(settlement, style, ground, kept);
        SimPos center = settlement.center();
        // Center-outward, so the index means the same piece as the town grows,
        // with the tie broken on the position rather than left to the order the
        // passes above happened to run in.
        kept.sort(Comparator
                .comparingLong((Furnishing piece) -> piece.at().horizontalDistanceSq(center))
                .thenComparingInt(piece -> piece.at().x())
                .thenComparingInt(piece -> piece.at().z())
                .thenComparing(piece -> piece.piece().name()));
        return List.copyOf(kept);
    }

    /** How many pieces the town's shape currently calls for. */
    public static int wanted(Settlement settlement) {
        return pieces(settlement).size();
    }

    /** Whether every piece the town's shape calls for is standing. */
    public static boolean isDressed(Settlement settlement) {
        return settlement.piecesRaised() >= wanted(settlement);
    }

    /** The next piece to raise, or null when the ground between the houses is dressed. */
    public static Furnishing next(Settlement settlement) {
        List<Furnishing> all = pieces(settlement);
        int raised = settlement.piecesRaised();
        return raised >= 0 && raised < all.size() ? all.get(raised) : null;
    }

    /**
     * The pieces the town has actually raised: the prefix it is drawing.
     *
     * <p>Asked by anything that has to leave the dressing alone rather than draw
     * it — the interior clearing, which would otherwise fell a grown orchard as
     * readily as it fells wild wood. Exposed here rather than derived twice
     * because the prefix rule ("how many, in this order") is this class's and
     * nobody else should be re-deciding it.
     */
    public static List<Furnishing> raised(Settlement settlement) {
        if (settlement.piecesRaised() <= 0) {
            return List.of();   // and, much more to the point, no plan is computed
        }
        List<Furnishing> all = pieces(settlement);
        int raised = Math.max(0, Math.min(settlement.piecesRaised(), all.size()));
        return List.copyOf(all.subList(0, raised));
    }

    /**
     * The trees this town has planted itself, as ground nothing may fell.
     *
     * <p>Both kinds, and leaving either out is the same bug. An orchard is the
     * obvious one; an avenue is the one that would have been missed, because an
     * avenue tree is a single sapling on a verge and looks like nothing at all
     * until it is a trunk — at which point the clearing sweep, which fells
     * whatever wood it finds standing in the ground the town has cleared, takes it
     * down, and the dressing plants it again, for ever.
     *
     * <p>A named list rather than a filter at the call site, because "the town's
     * own trees are not the wood the clearing is for" is a rule about the work and
     * not a detail of how a sweep happens to be written.
     */
    public static List<Furnishing> plantings(Settlement settlement) {
        List<Furnishing> out = new ArrayList<>();
        for (Furnishing piece : raised(settlement)) {
            if (piece.piece() == Piece.ORCHARD || piece.piece() == Piece.AVENUE_TREE) {
                out.add(piece);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Whether this column stands in something the town planted.
     *
     * <p>A block of margin past the piece's own box, because a tree is wider at
     * the top than at the foot and a trunk that leaned a block as it grew is still
     * the tree somebody planted.
     */
    public static boolean inAPlanting(List<Furnishing> plantings, int x, int z) {
        for (Furnishing planted : plantings) {
            if (Math.abs(planted.at().x() - x) <= planted.reach() + 1
                    && Math.abs(planted.at().z() - z) <= planted.reach() + 1) {
                return true;
            }
        }
        return false;
    }

    /** How this people dress their ground. */
    public static FurnishingStyle styleOf(Settlement settlement) {
        return FurnishingStyle.of(Culture.of(settlement.cultureId()));
    }

    // --- the price -----------------------------------------------------------

    /**
     * Pays for one whole piece, and says whether the town could.
     *
     * <p>All three resources or none of them, which is {@code LightPlanner.payForLamp}'s
     * rule widened to a tuple: a town with the fence timber but no saplings for the
     * orchard does not get half an orchard and does not lose the timber pretending
     * otherwise.
     */
    public static boolean payFor(Settlement settlement, Piece piece) {
        Cost cost = piece.cost();
        if (!settlement.stores().has(TownStores.WOOD, cost.wood())
                || !settlement.stores().has(TownStores.STONE, cost.stone())
                || !settlement.stores().has(TownStores.SAPLINGS, cost.saplings())) {
            return false;
        }
        take(settlement, TownStores.WOOD, cost.wood());
        take(settlement, TownStores.STONE, cost.stone());
        take(settlement, TownStores.SAPLINGS, cost.saplings());
        return true;
    }

    private static void take(Settlement settlement, String resource, int amount) {
        if (amount > 0) {
            settlement.stores().take(resource, amount);
        }
    }

    /**
     * Whether the town should be dressing anything at all yet.
     *
     * <p>Five refusals, and the first four are the priority this work has. There
     * has to be a piece outstanding. The build queue has to be empty — dressing is
     * the only public work that waits for that outright, because a fence is the one
     * thing on the list nobody needs. The streets have to be opened, because a
     * hedge runs along a street and an avenue stands on its verge, so a town whose
     * network is still arriving is a town whose dressing plan is still moving under
     * it. And the lamps have to be up, which is {@link PublicWorks#of}'s order said
     * again to the clock so that the two fidelities agree.
     *
     * <p>Then the plain one: the timber a piece costs must not be timber the build
     * queue is owed.
     */
    public static boolean worthStarting(Settlement settlement) {
        return whyNotStarting(settlement) == null;
    }

    /**
     * Why this town is not dressing itself, or null when it is entitled to.
     *
     * <p>{@link #worthStarting}'s refusals, named instead of counted, because a
     * playtest found a town of a hundred and forty-five people with four hundred
     * and sixty-three pieces planned and <em>none</em> raised, and nothing
     * anywhere said which of five gates had been shut for its entire life. The
     * reason now rides on {@code /civ info}'s dressing line, so the same question
     * takes one command instead of a debugger.
     */
    public static String whyNotStarting(Settlement settlement) {
        // The three cheap refusals first, and the order is a performance decision
        // as much as a priority one. Working out where a town's dressing goes is
        // the most expensive planning pass in the mod -- it walks every plot,
        // every street and every planned lamp against a few thousand candidate
        // positions -- and asking it on every step of every town would be paying
        // for a plan almost none of them is entitled to act on.
        //
        // Shelter first, and the queue is a backlog rather than a veto. This was
        // "the queue must be empty", which a growing town never is.
        int queued = settlement.buildQueue().size();
        if (queued > PublicWorks.KEEPING_UP) {
            return "build queue " + queued + " over " + PublicWorks.KEEPING_UP;
        }
        // Then the paving and then the lamps, the same way and for the same
        // reason. See PublicWorks.keepingUp, which holds the whole argument.
        PublicWorks.RoadWork roads = new PublicWorks.RoadWork();
        int owedRuns = roads.owedRuns(settlement);
        int runs = settlement.paths() == null ? 0 : settlement.paths().segments().size();
        if (!PublicWorks.keepingUp(runs - owedRuns, runs)) {
            return "paving " + owedRuns + " runs behind of " + runs;
        }
        int lit = settlement.lightsRaised();
        int lamps = LightPlanner.wanted(settlement);
        if (!PublicWorks.keepingUp(lit, lamps)) {
            return "lamps " + lit + " of " + lamps + " standing";
        }
        Furnishing piece = next(settlement);
        if (piece == null) {
            return "nothing left to raise";
        }
        int owed = TIMBER_KEPT_FOR_BUILDING + piece.piece().cost().wood();
        if (settlement.woodStock() < owed) {
            return "timber " + settlement.woodStock() + " under " + owed;
        }
        return null;
    }


    // --- the clock -----------------------------------------------------------

    /**
     * One step of dressing for a town nobody is looking at.
     *
     * <p>The same four refusals every other public work's clock has, in the same
     * order and for the same reasons: a dead town does nothing, nobody works after
     * dark, hands on the work mean there is no clock, and ground somebody can see
     * is ground where a piece is raised by a hand or not at all. A flower bed that
     * plants itself in front of a player is the one thing this must never look
     * like — and it is a worse offense here than anywhere else on the list,
     * because dressing is the part of a town a player is actually looking at.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        if (!settlement.hasLivingResidents()) {
            return;
        }
        if (com.civilization.sim.person.Curfew.idlesUnwatchedWork(
                ctx.bridge().dayTime(), com.civilization.sim.person.Curfew.LEAD_TICKS)) {
            return;
        }
        for (int raised = 0; raised < PIECES_PER_STEP; raised++) {
            // The gate before the plan, never the other way round: see
            // worthStarting, whose first three questions are the ones that keep
            // this out of the step loop of every growing town in the world.
            if (!worthStarting(settlement)) {
                return;
            }
            Furnishing piece = next(settlement);
            if (piece == null) {
                return;
            }
            if (PublicWorks.leaveItToTheCrew(settlement, ctx.bridge(),
                    new PublicWorks.DressingWork())) {
                return;
            }
            if (ctx.bridge().playerWithin(piece.at(), ctx.settings().observedRadius())) {
                return;
            }
            if (!payFor(settlement, piece.piece())) {
                return;
            }
            settlement.setPiecesRaised(settlement.piecesRaised() + 1);
        }
    }

    /**
     * Whether a piece of the raised prefix may be put back where it is missing.
     *
     * <p>{@code RoadUpkeep}'s distinction and {@code LightPlanner.mayMend}'s
     * wording: drawing a piece the town raised while somebody was alive to raise
     * it is a record, and putting back one that has since been broken is a morning
     * with a spade and needs hands.
     */
    public static boolean mayMend(Settlement settlement, int index) {
        return index >= 0 && index < settlement.piecesRaised()
                && settlement.hasLivingResidents();
    }

    /** Whether a piece may be drawn into the world for the first time. */
    public static boolean mayDraw(Settlement settlement, int index) {
        return index >= 0 && index < settlement.piecesRaised();
    }

    // --- where they go -------------------------------------------------------

    /**
     * The paved square at the heart of the plan.
     *
     * <p>The one piece that negotiates its own size. Thirteen blocks of clear
     * ground in the middle of a town is a lot to ask of a hillside and of a plan
     * that has already put a hall there, so it asks for thirteen, then eleven,
     * then nine, and takes the first that fits. A town that cannot seat nine gets
     * no square, which is the right answer: a five-block square is a gap between
     * two houses.
     *
     * <p>Sited by walking out from the center a ring at a time, so "the heart"
     * means the nearest clear ground to the middle rather than the middle exactly.
     * The middle exactly is where the hall is.
     */
    private static void theSquare(Settlement settlement, FurnishingStyle style,
                                  Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.SQUARE)) {
            return;
        }
        SimPos center = settlement.center();
        for (int reach = Piece.SQUARE.reach(); reach >= SMALLEST_SQUARE; reach--) {
            SimPos at = nearestFree(ground, out, center, Piece.SQUARE, reach,
                    SQUARE_SEARCH);
            if (at != null) {
                out.add(new Furnishing(at, Piece.SQUARE, 0, reach));
                return;
            }
        }
    }

    /** The smallest square worth calling one: nine blocks across. */
    public static final int SMALLEST_SQUARE = 4;

    /** How far from the middle the square may be pushed to find room. */
    private static final int SQUARE_SEARCH = 24;

    /**
     * A well at the heart, when no market stands.
     *
     * <p>The market draws its own — see {@code CivicParts.well}, which puts one in
     * the middle of every market square there is — so a town with a market has a
     * well already and a second one twenty blocks away would be a town that dug
     * twice. A town without one has nowhere to draw water at all, which is the
     * oldest thing missing from the middle of a village.
     */
    private static void theWell(Settlement settlement, FurnishingStyle style,
                                Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.WELL)
                || !settlement.buildingsWithRole(BuildingRole.MARKET).isEmpty()) {
            return;
        }
        SimPos at = nearestFree(ground, out, settlement.center(), Piece.WELL,
                Piece.WELL.reach(), SQUARE_SEARCH);
        if (at != null) {
            out.add(new Furnishing(at, Piece.WELL, 0));
        }
    }

    /**
     * A fenced garden for every family home, on the side away from the street.
     *
     * <p>Behind the house first, which is where a kitchen garden goes in every
     * village that has ever had one: the door faces the town center, so the back
     * of the house faces away from it and that is the ground nothing else wants.
     * Then the two flanks, because a house on a corner has no back — and a house
     * with a garden at its side is still a house with a garden, whereas a house
     * with none is the empty grass this whole work is about.
     */
    private static void theYards(Settlement settlement, FurnishingStyle style,
                                 Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.YARD)) {
            return;
        }
        for (Building building : settlement.buildings()) {
            if (!isFamilyHome(building)) {
                continue;
            }
            SimPos at = besideTheBuilding(ground, out, building, Piece.YARD,
                    Piece.YARD.reach());
            if (at != null) {
                // Facing the house it belongs to rather than facing whichever way
                // the house does, because what the drawing needs to know is which
                // side of the garden the gate goes in. A yard pushed round onto a
                // flank would otherwise have its gate in the back rail.
                out.add(new Furnishing(at, Piece.YARD,
                        facingToward(at, building.origin())));
            }
        }
    }

    /**
     * Whether this building is one household's home rather than a dormitory, a
     * hall or somebody's workplace.
     *
     * <p>{@link Homes#isFamilyHome} rather than a fresh guess at the blueprint id,
     * and rather than either of the two neighbouring questions that look like this
     * one and are not — see that method, which says why. Getting it wrong is not
     * loud: {@code isSomebodysOwnHome} is true only of the orc and goblin homes, so
     * asking it here gave every human village in the world exactly nought kitchen
     * gardens and nothing threw.
     */
    private static boolean isFamilyHome(Building building) {
        return building.footprint().isKnown()
                && Homes.isFamilyHome(building.blueprintId());
    }

    // --- how full a heap stands ------------------------------------------------

    /**
     * How many courses a heap can stand: three.
     *
     * <p>Three and not ten, because what is being said is "the camp is doing
     * well" and there are three answers to that from across a street: a few
     * logs, a stack, a wall of timber. A pile with ten heights is a pile whose
     * height nobody reads.
     */
    public static final int HEAP_STEPS = 3;

    /**
     * Timber in the camp per course of woodpile: 64.
     *
     * <p>A stack of logs. Under one the pile is a few split rounds, at one it is
     * a proper stack, at two it is the wall of timber a camp with a good season
     * behind it has — and the top step is open-ended, so a camp sitting on a
     * thousand logs looks the same as one sitting on two hundred, which is
     * right: past a point a woodpile is just a woodpile.
     */
    public static final int TIMBER_PER_COURSE = 64;

    /** Grain in the granary per course of rick: 32. A rick is smaller than a stack. */
    public static final int GRAIN_PER_COURSE = 32;

    /**
     * How full this piece stands, from one to {@link #HEAP_STEPS}.
     *
     * <p><strong>Derived and never written down</strong>, exactly like where the
     * piece goes — and deliberately <em>outside</em> {@link #pieces}, which is
     * memoized on {@link #shapeOf}. A store changes every step and the shape of
     * a town does not; folding the stock into the memo would throw the most
     * expensive plan in the mod away several times a second to move one log.
     * So the list of pieces is a fact about the buildings and the streets as it
     * always was, and how tall two of them stand is asked separately, by the
     * sweep, at the moment it draws one.
     *
     * <p>Everything that is not a heap answers {@link #HEAP_STEPS}: a fence is
     * a fence at any hour and a headstone does not get shorter.
     */
    public static int heapOf(Settlement settlement, Furnishing piece) {
        if (settlement == null || piece == null) {
            return HEAP_STEPS;
        }
        return switch (piece.piece()) {
            case WOODPILE -> coursesFor(
                    heldIn(settlement, BuildingRole.LUMBER_CAMP, TownStores.WOOD),
                    TIMBER_PER_COURSE);
            case HAYSTACK -> coursesFor(
                    heldIn(settlement, BuildingRole.GRANARY, TownStores.GRAIN),
                    GRAIN_PER_COURSE);
            default -> HEAP_STEPS;
        };
    }

    /** One course to start with, and one more for each {@code per} held. */
    public static int coursesFor(int held, int per) {
        if (per <= 0) {
            return HEAP_STEPS;
        }
        return Math.max(1, Math.min(HEAP_STEPS, 1 + Math.max(0, held) / per));
    }

    /**
     * What the buildings of this role are holding, or what the town holds
     * altogether where it has raised none of them.
     *
     * <p>The building's own store first, because the piece is beside that
     * building and a woodpile is made of the camp's timber rather than of the
     * town's idea of timber. The fallback is for the towns that stand one of
     * these anyway: a woodpile goes beside a hearth as well as beside a camp,
     * and a village whose firewood is in its storehouse should not have a pile
     * that is permanently one log high.
     */
    private static int heldIn(Settlement settlement, BuildingRole role, String good) {
        List<Building> holders = settlement.buildingsWithRole(role);
        if (holders.isEmpty()) {
            return settlement.stores().get(good);
        }
        int held = 0;
        for (Building building : holders) {
            held += building.stores().get(good);
        }
        return held;
    }

    /** A woodpile beside every lumber camp and every hearth. */
    private static void theWoodpiles(Settlement settlement, FurnishingStyle style,
                                     Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.WOODPILE)) {
            return;
        }
        besideEach(settlement, ground, out, Piece.WOODPILE,
                BuildingRole.LUMBER_CAMP, BuildingRole.HEARTH);
    }

    /** A rick of hay by every crop farm. */
    private static void theHaystacks(Settlement settlement, FurnishingStyle style,
                                     Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.HAYSTACK)) {
            return;
        }
        besideEach(settlement, ground, out, Piece.HAYSTACK, BuildingRole.CROP_FARM);
    }

    /** Crates and barrels outside every store and every market. */
    private static void theCrates(Settlement settlement, FurnishingStyle style,
                                  Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.CRATES)) {
            return;
        }
        besideEach(settlement, ground, out, Piece.CRATES,
                BuildingRole.STORE, BuildingRole.MARKET);
    }

    /**
     * A fire pit by the hall, for the peoples who have one instead of a hearth.
     *
     * <p>Only where a hearth does not already stand. A camp that raised a hearth
     * has somewhere to cook and the fire pit would be a second one in the same
     * yard; a camp that has not is a camp that plainly has a fire somewhere, and
     * this is where it is.
     */
    private static void theCampFires(Settlement settlement, FurnishingStyle style,
                                     Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.FIRE_PIT)
                || !settlement.buildingsWithRole(BuildingRole.HEARTH).isEmpty()) {
            return;
        }
        besideEach(settlement, ground, out, Piece.FIRE_PIT, BuildingRole.HALL);
    }

    /** Cages beside the chieftain's hut, which is the mire's whole idea of a garden. */
    private static void theCages(Settlement settlement, FurnishingStyle style,
                                 Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.CAGE)) {
            return;
        }
        besideEach(settlement, ground, out, Piece.CAGE, BuildingRole.HALL);
    }

    private static void besideEach(Settlement settlement, Keepouts ground,
                                   List<Furnishing> out, Piece piece,
                                   BuildingRole... roles) {
        for (Building building : settlement.buildings()) {
            if (!building.footprint().isKnown()) {
                continue;
            }
            BuildingRole role = BuildingRole.of(building.blueprintId());
            boolean wanted = false;
            for (BuildingRole each : roles) {
                wanted |= role == each;
            }
            if (!wanted) {
                continue;
            }
            SimPos at = besideTheBuilding(ground, out, building, piece, piece.reach());
            if (at != null) {
                out.add(new Furnishing(at, piece, facingToward(at, building.origin())));
            }
        }
    }

    /**
     * Free ground beside one building: behind it first, then its two flanks, then
     * in front of it.
     *
     * <p>In front is last and is not a mistake. A woodpile on the street side of a
     * lumber camp is a woodpile people walk past, which is what a woodpile is for;
     * it only ever happens when the other three sides are somebody else's ground,
     * and the alternative to taking it is the bare grass this work exists to fill.
     * The doorway itself is protected separately and absolutely — see
     * {@link #DOOR_CLEARANCE} — so "in front of it" never means "across the door".
     */
    private static SimPos besideTheBuilding(Keepouts ground, List<Furnishing> out,
                                            Building building, Piece piece, int reach) {
        SimPos origin = building.origin();
        // The plot's widest half, not the half on the axis being stepped along,
        // and the gap on top of it is two rather than one. Both of those are
        // the same arithmetic the keepout does: Keepouts.Plot squares a
        // footprint off at its longer side and then keeps a block clear besides,
        // so a piece stood at exactly the narrow half plus its own reach lands on
        // the boundary and is refused by the very rule that sited it. The whole
        // pass then returns null and no building in the town gets anything.
        int half = Math.max(building.footprint().width(),
                building.footprint().depth()) / 2;
        // Behind, then the flanks, then the front: the door faces `facing`, so
        // the back is the opposite quarter-turn and the flanks are the two beside.
        for (int turn : new int[] {2, 1, 3, 0}) {
            int side = Math.floorMod(building.facing() + turn, 4);
            int dx = side == 1 ? -1 : side == 3 ? 1 : 0;
            int dz = side == 0 ? 1 : side == 2 ? -1 : 0;
            int off = half + reach + 2;
            SimPos at = origin.offset(dx * off, 0, dz * off);
            if (isFree(ground, out, at, piece, reach)) {
                return at;
            }
        }
        return null;
    }

    /**
     * An orchard for every ten residents, on whatever free ground is left inside
     * the built area.
     *
     * <p>Counted off the people rather than off the buildings, because an orchard
     * is a thing a town eats from and a town eats in proportion to how many of
     * them there are. Sited on the grid of the ground the streets enclose, so an
     * orchard lands in the open middle of a block rather than in the gap between
     * two houses.
     *
     * <p><strong>An orchard planted where trees already stand is free, and that is
     * allowed.</strong> {@code FurnishingLayer.stands} counts a grown tree as the
     * sapling that was planted — it has to, or a town would fell its own orchard
     * every sweep to replant it — so a patch of standing wood inside the built area
     * reads as an orchard that has already taken, nothing is charged for it, and the
     * clearing then spares it. The town has adopted five blocks square of its own
     * forest and called it an orchard, which is what an orchard is. It is small
     * enough not to be the canopy {@code InteriorClearing} exists to take down, and
     * the streets round it are lit.
     */
    private static void theOrchards(Settlement settlement, FurnishingStyle style,
                                    Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.ORCHARD)) {
            return;
        }
        int wanted = settlement.residents().size() / RESIDENTS_PER_ORCHARD;
        if (wanted <= 0) {
            return;
        }
        int planted = 0;
        for (SimPos cell : InteriorClearing.cells(settlement)) {
            if (planted >= wanted) {
                return;
            }
            if (isFree(ground, out, cell, Piece.ORCHARD, Piece.ORCHARD.reach())) {
                out.add(new Furnishing(cell, Piece.ORCHARD, 0));
                planted++;
            }
        }
    }

    /**
     * Hedges along the opened streets, broken at the doors.
     *
     * <p>"Broken at the doors" is not a flourish in the drawing; it is done here,
     * by refusing any stretch within {@link #DOOR_CLEARANCE} of a doorstep, because
     * a hedge across somebody's front gate is a house nobody can get into and the
     * pathfinder would agree.
     *
     * <p>On the verge, alternating sides the way the lamps do, so a street is not
     * hedged solid down one flank and bare down the other.
     */
    private static void theHedges(Settlement settlement, FurnishingStyle style,
                                  Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.HEDGE)) {
            return;
        }
        alongTheVerges(settlement, ground, out, Piece.HEDGE, HEDGE_SPACING, true, false);
    }

    /**
     * How near a building a stretch of verge has to be to be worth hedging.
     *
     * <p>Sixteen, and the rule it enforces is the brief's own words: hedges go
     * along the street <em>between plots</em>. Without it a town of sixty hedged a
     * hundred and twenty-three stretches, most of them out on the long empty legs
     * of a ring road where there is nothing on either side to divide from anything
     * — which is not a hedge, it is a fence across a field. With it a hedge only
     * appears where a street has frontage, which is the only place a real one ever
     * does.
     */
    public static final int HEDGE_FRONTAGE = 16;

    /**
     * A tree on the outer verge every {@link #AVENUE_SPACING} blocks — or, for a
     * warhost, a stand of stakes in the same places.
     *
     * <p>The same pass for both because they are the same siting problem: a small
     * thing, repeated along a street, standing on the grass rather than on the
     * stones. What differs is entirely what gets drawn, which is the platform's
     * business.
     */
    private static void theVergeTrees(Settlement settlement, FurnishingStyle style,
                                      Keepouts ground, List<Furnishing> out) {
        Piece piece = style.raises(Piece.AVENUE_TREE) ? Piece.AVENUE_TREE
                : style.raises(Piece.STAKES) ? Piece.STAKES : null;
        if (piece == null) {
            return;
        }
        alongTheVerges(settlement, ground, out, piece, AVENUE_SPACING, false, true);
    }

    /**
     * Whatever this is, spaced along the verges of every opened street.
     *
     * <p>Deliberately the plainest possible walk: down the run in steps, and every
     * candidate put through the same free-ground test as everything else. The
     * lighting does something cleverer — see {@code LightPlanner.standingRoom},
     * which nudges a lamp along the run rather than dropping it — and it does that
     * because a gap in a run of lamps is a dark stretch somebody dies in. A gap in
     * a run of hedge is a gate.
     *
     * @param needsFrontage  whether this only belongs where something is built:
     *                       true for a hedge, which divides one plot from the
     *                       next, and false for an avenue, which is the street
     *                       itself
     * @param outerVergeOnly whether it goes on the side away from the middle
     *                       rather than alternating. An avenue does; a hedge
     *                       alternates, so a street is not railed solid down one
     *                       flank and bare down the other
     */
    private static void alongTheVerges(Settlement settlement, Keepouts ground,
                                       List<Furnishing> out, Piece piece, int spacing,
                                       boolean needsFrontage, boolean outerVergeOnly) {
        PathNetwork paths = settlement.paths();
        if (paths == null) {
            return;
        }
        List<PathNetwork.Segment> runs = paths.segments();
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i) || paths.isUnwalkable(i)) {
                continue;
            }
            PathNetwork.Segment run = runs.get(i);
            List<SimPos> along = run.positions();
            int side = 1;
            for (int at = spacing / 2; at < along.size(); at += spacing) {
                int thisSide = outerVergeOnly
                        ? outwardSide(settlement, run, along, at, piece) : side;
                SimPos spot = onTheVerge(settlement, run, along, at, thisSide,
                        piece.reach(), piece.roadClearance());
                side = -side;
                if (spot == null
                        || (needsFrontage && !ground.hasFrontage(spot, HEDGE_FRONTAGE))) {
                    continue;
                }
                if (isFree(ground, out, spot, piece, piece.reach())) {
                    // Facing the street, so a hedge's long axis is the street's
                    // and a run of them reads as one boundary rather than as a
                    // row of blocks set crosswise into the verge.
                    out.add(new Furnishing(spot, piece,
                            facingToward(spot, along.get(Math.min(at, along.size() - 1)))));
                }
            }
        }
    }

    /**
     * Which of a run's two verges faces away from the middle of the town.
     *
     * <p>The brief asked for the avenue on the outer verge of a ring road and it is
     * right twice over. It is what an avenue is — a line of trees you see from
     * outside, with the carriageway and then the town behind it — and it halves
     * what the trees cost: alternating sides put eighty-five saplings round a town
     * of sixty buildings, which is a wood with a road through it.
     *
     * <p>Answered by measuring both verges from the center rather than by any
     * cleverness about which runs are rings. A straight high street has an outer
     * side too; it is just the side the town is not on.
     */
    private static int outwardSide(Settlement settlement, PathNetwork.Segment run,
                                   List<SimPos> along, int index, Piece piece) {
        SimPos center = settlement.center();
        SimPos one = onTheVerge(settlement, run, along, index, 1, piece.reach(),
                piece.roadClearance());
        SimPos other = onTheVerge(settlement, run, along, index, -1, piece.reach(),
                piece.roadClearance());
        return one.horizontalDistanceSq(center) >= other.horizontalDistanceSq(center)
                ? 1 : -1;
    }

    /**
     * The verge position beside one column of a run, far enough out that the piece
     * clears its own siting rule.
     *
     * <p>{@code LightPlanner.onTheVerge} with two things added, and both of them
     * had to be measured rather than reasoned out. The piece's half-extent,
     * because a lamp is one column wide and a hedge is five, so a hedge put where
     * a lamp stands is two blocks of hedge in the road. And then two blocks more —
     * the piece's own road clearance and one over it — because a piece stood at
     * exactly the distance Keepouts.freeFor measures against is refused by
     * the very rule that placed it, and the first run of this produced one hedge
     * in a town of sixty buildings and looked for all the world like a siting
     * problem rather than an off-by-one.
     *
     * <p>The one over the clearance also buys the gap the lamps need: a lamp
     * stands at {@code paveHalf + 1} and every piece here ends up at least two
     * further out than that, so the {@link #LAMP_CLEARANCE} test passes on the
     * perpendicular alone and a run of hedge does not have to be broken at every
     * lamp post along it.
     */
    private static SimPos onTheVerge(Settlement settlement, PathNetwork.Segment run,
                                     List<SimPos> along, int index, int side,
                                     int reach, int roadClearance) {
        SimPos at = along.get(Math.max(0, Math.min(index, along.size() - 1)));
        int dx = run.to().x() - run.from().x();
        int dz = run.to().z() - run.from().z();
        double length = Math.hypot(dx, dz);
        int off = run.paveHalf() + roadClearance + reach + 2;
        if (length == 0) {
            return at.offset(off * side, 0, 0);
        }
        int px = (int) Math.round(-dz / length * off) * side;
        int pz = (int) Math.round(dx / length * off) * side;
        if (px == 0 && pz == 0) {
            px = off * side;
        }
        return at.offset(px, 0, pz);
    }

    /**
     * A post with a board on it where two ways meet.
     *
     * <p>Which is where somebody stands and wonders which way the hall is, and is
     * the same argument {@code LightPlanner.atTheJunctions} makes about wanting a
     * lamp there. The two do not fight, because a lamp is refused within
     * {@link #LAMP_CLEARANCE} of the post and there are four corners at a
     * crossroads.
     */
    private static void theSignposts(Settlement settlement, FurnishingStyle style,
                                     Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.SIGNPOST)) {
            return;
        }
        PathNetwork paths = settlement.paths();
        if (paths == null) {
            return;
        }
        List<PathNetwork.Segment> runs = paths.segments();
        // One post per crossing, not one per street that arrives at it. A network
        // is a chain of short runs and every run's far end is the next run's near
        // end, so asking each run about each of its ends put a signpost at every
        // joint in the plan: eighty-two of them in a town of sixty buildings,
        // which is not a town with signs in it, it is a forest of them. The
        // crossings are gathered first and thinned to one apiece.
        List<SimPos> crossings = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i) || paths.isUnwalkable(i)) {
                continue;
            }
            List<SimPos> along = runs.get(i).positions();
            for (int end : List.of(0, along.size() - 1)) {
                SimPos corner = along.get(end);
                if (meetsAnother(paths, runs, i, corner)
                        && !nearOneAlready(crossings, corner, SIGNPOSTS_APART)) {
                    crossings.add(corner);
                }
            }
        }
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i) || paths.isUnwalkable(i)) {
                continue;
            }
            PathNetwork.Segment run = runs.get(i);
            List<SimPos> along = run.positions();
            for (int end : List.of(0, along.size() - 1)) {
                if (!crossings.contains(along.get(end))) {
                    continue;
                }
                for (int side : new int[] {1, -1}) {
                    SimPos at = onTheVerge(settlement, run, along, end, side,
                            Piece.SIGNPOST.reach(), Piece.SIGNPOST.roadClearance());
                    if (at != null && isFree(ground, out, at, Piece.SIGNPOST,
                            Piece.SIGNPOST.reach())) {
                        out.add(new Furnishing(at, Piece.SIGNPOST,
                                facingToward(at, settlement.center())));
                        break;
                    }
                }
            }
        }
    }

    // --- the graveyard -------------------------------------------------------

    /**
     * Blocks between two stones in the row: three.
     *
     * <p>A grave's own box is one either side, so three is the box plus a pace,
     * which is what {@link #crowds} demands of any two pieces and is also simply
     * what a row of headstones looks like. Two would be refused by the siting
     * rule that placed them; four is a row you have to walk between.
     */
    public static final int GRAVES_APART = 3;

    /**
     * How far past the last house the row begins: eight blocks.
     *
     * <p>Far enough to read as <em>outside</em> the town — the whole point of a
     * graveyard on the verge is that it is past where people live — and near
     * enough that it is still inside the claim of any settlement big enough to
     * have lost anybody. It is measured off the outermost standing building
     * rather than off the claim radius, because a claim is a circle drawn by a
     * charter and the edge of a town is where its last roof is.
     */
    public static final int BEYOND_THE_LAST_HOUSE = 8;

    /**
     * How many stones the row is ever planned to hold: twenty-four.
     *
     * <p>Twice what the town remembers names for, and a hard stop. A settlement
     * that lives long enough buries everybody in it several times over, and a
     * churchyard planned from an unbounded count would be a ring of cobble
     * {@link #GRAVES_APART} blocks a stone right round the outside of the
     * village — a hundred deaths is three hundred blocks of verge, which is
     * further out than the claim goes.
     *
     * <p>What the cap does <em>not</em> do is move anything. Slot <em>i</em> is
     * burial <em>i</em> for ever; the cap only says that burials past the
     * twenty-fourth get no stone of their own, so the churchyard stops growing
     * rather than starting again. That is the right shape for the thing: a
     * village has a graveyard, and then the graveyard is full.
     *
     * <p>The other reading — keep the <em>newest</em> twenty-four and let the
     * oldest stop being redrawn — was written out and rejected. The row's slots
     * are absolute, so the newest twenty-four of a hundred burials sit at slots
     * seventy-six to ninety-nine, which is a hundred and fifty blocks from the
     * head of the row and outside any claim: {@code Keepouts} would refuse every
     * one of them and the churchyard would silently vanish. Finding those slots
     * at all would also mean walking the row from nought every time the plan is
     * made, which is a cost that grows for ever inside the most expensive
     * planning pass in the mod.
     */
    public static final int GRAVES_PLANNED = 24;

    /**
     * A row of stones on the outer verge, one for each of the town's dead.
     *
     * <p><strong>Why it is here at all.</strong> A settler starved, or was killed
     * in a raid resolved as arithmetic while the chunks were unloaded, and the
     * whole of what happened was a line in a log nobody reads. A town that loses
     * a third of its people to a bad winter should look different afterwards, and
     * this is the cheapest true way for it to: the dead are on the settlement,
     * the row is derived from them, and it is drawn where nobody was watching
     * exactly as a hedge is.
     *
     * <p><strong>Where.</strong> Out past the last house, along the way the
     * longest opened street runs — which is a way out of town that already
     * exists, so the churchyard is somewhere people walk past rather than in the
     * middle of a field. The row itself is laid across that bearing so it reads
     * as a row from the road rather than as a line going away from you.
     *
     * <p>Every stone goes through {@link #isFree} like everything else, so a
     * grave is never on a plot, a carriageway, the wall line, a lamp, a doorway
     * or the forester's belt; a stone that cannot be placed is skipped and the
     * row goes on past it, because a churchyard with a gap in it is a churchyard
     * and a churchyard that stopped at the first boulder is nothing.
     */
    private static void theGraves(Settlement settlement, FurnishingStyle style,
                                  Keepouts ground, List<Furnishing> out) {
        // Every burial the town has ever made, not the dozen it still has names
        // for — see Settlement.buried. Planning from the roster made the row
        // stop at twelve stones and, far worse, made the thirteenth death
        // rewrite every board in the churchyard with the next name along.
        int buried = Math.min(settlement.buried(), GRAVES_PLANNED);
        if (buried <= 0 || !style.raises(Piece.GRAVE)) {
            return;
        }
        int[] bearing = outwardBearing(settlement);
        if (bearing == null) {
            return;   // no opened street: no way out of town, and so no verge
        }
        SimPos center = settlement.center();
        SimPos head = theHeadOfTheRow(settlement, ground, out, bearing);
        if (head == null) {
            return;
        }
        // Across the bearing, so the row faces whoever is coming up the road.
        int acrossX = -bearing[1];
        int acrossZ = bearing[0];
        int facing = facingToward(head, center);
        out.add(new Furnishing(head, Piece.GRAVE, facing));
        // Grown outward from the first stone, a slot either side at a time,
        // rather than run off in one direction. A row laid one way runs into
        // whatever is at that end of it -- the verge of the next street, a
        // lamp, the corner of the claim -- and stops there with half its stones
        // unplaced; grown from the middle it has two ends to lose and needs only
        // one of them. A refused slot is skipped and the row goes on past it,
        // because a churchyard with a plot nobody has used yet is a churchyard
        // and one that stopped at the first boulder is nothing.
        // Which side of the row is dug first, and it is decided once rather than
        // fixed. Both slots of a pair are the same distance from the middle of
        // the town, so the center-outward sort that follows breaks the tie on
        // their coordinates — and a row dug in the other order than the one it
        // sorts into would have each new burial insert a stone in the middle of
        // the list rather than append one, which moves the index of every stone
        // past it. Digging in the order the list will end up in keeps the index
        // of a stone that is already standing meaning that stone.
        int[] sides = earlierOfThePair(head, acrossX, acrossZ)
                ? new int[] {1, -1} : new int[] {-1, 1};
        int stood = 1;
        for (int slot = 1; slot <= buried * ROW_TRIES && stood < buried; slot++) {
            for (int side : sides) {
                if (stood >= buried) {
                    break;
                }
                SimPos at = head.offset(acrossX * GRAVES_APART * slot * side, 0,
                        acrossZ * GRAVES_APART * slot * side);
                if (isFree(ground, out, at, Piece.GRAVE, Piece.GRAVE.reach())) {
                    out.add(new Furnishing(at, Piece.GRAVE, facing));
                    stood++;
                }
            }
        }
    }

    /**
     * Whether the slot on the {@code +1} side of a pair sorts before its twin.
     *
     * <p>The plan's own tie-break, asked of one pair and true of every pair: the
     * row runs along a fixed axis, so whichever of the two sides has the smaller
     * coordinate at one slot has it at all of them.
     */
    private static boolean earlierOfThePair(SimPos head, int acrossX, int acrossZ) {
        SimPos plus = head.offset(acrossX * GRAVES_APART, 0, acrossZ * GRAVES_APART);
        SimPos minus = head.offset(-acrossX * GRAVES_APART, 0, -acrossZ * GRAVES_APART);
        return plus.x() != minus.x() ? plus.x() < minus.x() : plus.z() < minus.z();
    }

    /**
     * How many slots each way the row will try per stone it has to stand: three.
     *
     * <p>So a dozen dead get thirty-six slots either side of the first stone to
     * find room in, which is a hundred blocks of verge and is enough for every
     * arrangement anybody builds in on the recorded ground of seed 8675309. A
     * budget rather than an open walk because this runs inside the most expensive
     * planning pass in the mod and an unbounded row on a town hemmed in on both
     * sides would walk to the edge of the claim testing every position on the way.
     */
    private static final int ROW_TRIES = 3;

    /**
     * The first stone: straight out along the bearing until the ground is free.
     *
     * <p>Walked outward rather than searched in rings, and the difference is the
     * whole of whether this works. {@link #nearestFree} takes the <em>nearest</em>
     * free ground to a point, and the nearest free ground to a spot on a ring road
     * is the inside of the ring — so a graveyard sited that way lands back among
     * the houses, on the same verge the hedges are on, and half the row is refused
     * by the hedges. Walking out keeps the one property a churchyard needs: every
     * step is further from the middle of the town than the last, so the first spot
     * that is free is past everything the town has put down.
     */
    private static SimPos theHeadOfTheRow(Settlement settlement, Keepouts ground,
                                          List<Furnishing> out, int[] bearing) {
        SimPos center = settlement.center();
        int edge = settlement.claimRadius() - Piece.GRAVE.reach() - 1;
        for (int away = beyondTheLastHouse(settlement); away <= edge; away++) {
            SimPos at = center.offset(bearing[0] * away, 0, bearing[1] * away);
            if (isFree(ground, out, at, Piece.GRAVE, Piece.GRAVE.reach())) {
                return at;
            }
        }
        return null;
    }

    /**
     * The graves this town's plan calls for, one per entry in its dead and in
     * the same order.
     *
     * <p>So the layer that draws a stone can say whose it is: grave <em>i</em> is
     * for the <em>i</em>th of {@code Settlement.dead}, oldest first. That pairing
     * is not a coincidence and is not re-derived here — it is a property
     * {@code theGraves} is written to have. The row is dug outward from its first
     * stone in exactly the order the center-outward sort will put it in, so
     * filtering the finished plan in plan order recovers the order the stones
     * were dug in, and that is the order the dead are listed in.
     *
     * <p>It follows that a burial appends rather than inserts: a new stone stands
     * further out along the row than every stone already there, so every index
     * already drawn still names the stone it named. That is the same promise the
     * whole dressing list makes about a town growing outward, said again about a
     * churchyard growing along a verge.
     */
    public static List<Furnishing> graves(Settlement settlement) {
        List<Furnishing> row = new ArrayList<>();
        for (Furnishing piece : pieces(settlement)) {
            if (piece.piece() == Piece.GRAVE) {
                row.add(piece);
            }
        }
        return List.copyOf(row);
    }

    /**
     * How far out the graveyard begins: past the outermost standing building.
     *
     * <p>Clamped inside the claim, because a town whose outermost building sits
     * on its own boundary has nowhere further out to bury anybody and a row
     * planned outside the claim would be refused stone by stone by
     * {@code Keepouts.freeFor} — which is a correct refusal and an entirely
     * silent one.
     */
    private static int beyondTheLastHouse(Settlement settlement) {
        SimPos center = settlement.center();
        double furthest = 0;
        for (Building building : settlement.buildings()) {
            double out = building.origin().horizontalDistance(center);
            if (building.footprint().isKnown()) {
                out += Math.max(building.footprint().width(),
                        building.footprint().depth()) / 2.0;
            }
            furthest = Math.max(furthest, out);
        }
        int wanted = (int) Math.ceil(furthest) + BEYOND_THE_LAST_HOUSE;
        return Math.min(wanted, Math.max(0, settlement.claimRadius() - Piece.GRAVE.reach() - 1));
    }

    /**
     * Which way out of town the longest opened street points, as a unit step.
     *
     * <p>{@code TownEdge}'s question with a different answer wanted: the edge
     * wants the column somebody walks in at, and this wants the direction, so the
     * two share the reasoning rather than the code. Snapped to one of the four
     * compass steps, because a row laid along a diagonal is a row the spacing
     * arithmetic has to do trigonometry for and gains nothing by.
     */
    private static int[] outwardBearing(Settlement settlement) {
        SimPos edge = TownEdge.of(settlement);
        if (edge == null) {
            return null;
        }
        SimPos center = settlement.center();
        int dx = edge.x() - center.x();
        int dz = edge.z() - center.z();
        if (dx == 0 && dz == 0) {
            return new int[] {1, 0};
        }
        return Math.abs(dx) >= Math.abs(dz)
                ? new int[] {dx >= 0 ? 1 : -1, 0}
                : new int[] {0, dz >= 0 ? 1 : -1};
    }

    /**
     * A board on a post outside the inn, with the inn's own name on it.
     *
     * <p>{@code besideEach}, and so sited the same way every woodpile and every
     * rick is: behind the building first, then its flanks, then its frontage,
     * with the doorway itself kept absolutely clear. That puts most inn boards
     * round the back, which sounds wrong and is not — "behind" is measured from
     * the door, so the first side it tries is the one away from the street, and
     * it only lands there when the street side is somebody else's ground. Given
     * a choice this piece would rather stand on the frontage, and the ordinary
     * case is that nothing else wants the frontage of an inn.
     *
     * <p>Nobody dresses one but the peoples who build inns. A warhost and a
     * mire camp have no inn to sign, and {@code FurnishingStyle.raises} already
     * says so for both without a word having to be added to it — which is what
     * the table being a shape rather than a flag per piece buys.
     */
    private static void theInnBoard(Settlement settlement, FurnishingStyle style,
                                    Keepouts ground, List<Furnishing> out) {
        if (!style.raises(Piece.INN_SIGN)) {
            return;
        }
        besideEach(settlement, ground, out, Piece.INN_SIGN, BuildingRole.INN);
    }

    /**
     * How far apart two signposts have to be for both to be worth standing.
     *
     * <p>Twenty-four, which is about three plots: far enough that a player walking
     * a street passes one occasionally rather than constantly, and near enough
     * that a real crossroads on the far side of a block still gets its own.
     */
    public static final int SIGNPOSTS_APART = 24;

    private static boolean nearOneAlready(List<SimPos> taken, SimPos at, int apart) {
        for (SimPos standing : taken) {
            if (standing.horizontalDistance(at) < apart) {
                return true;
            }
        }
        return false;
    }

    /** Whether another opened run passes through this end, which makes it a junction. */
    private static boolean meetsAnother(PathNetwork paths, List<PathNetwork.Segment> runs,
                                        int mine, SimPos end) {
        for (int j = 0; j < runs.size(); j++) {
            if (j == mine || !paths.isOpened(j) || paths.isUnwalkable(j)) {
                continue;
            }
            if (runs.get(j).nearestTo(end).horizontalDistance(end) <= PathNetwork.TRACK_WIDTH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which way something at {@code from} has to look to be looking at {@code at}.
     *
     * <p>{@code Building.facing}'s convention, so a signpost and a house describe
     * their facing in the same numbers.
     */
    public static int facingToward(SimPos from, SimPos at) {
        int dx = at.x() - from.x();
        int dz = at.z() - from.z();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? 3 : 1;
        }
        return dz >= 0 ? 0 : 2;
    }

    // --- the ground ----------------------------------------------------------

    /**
     * The nearest free ground to a point, walked out in rings.
     *
     * <p>A ring at a time and two blocks at a step, which is coarse on purpose: a
     * square is thirteen blocks across and a one-block scan of a
     * twenty-four-block radius is two thousand positions tested against every
     * plot in the town, three times over as the square shrinks. The cost of the
     * coarseness is that a spot two blocks off the best one is taken instead,
     * which nobody can see.
     */
    private static SimPos nearestFree(Keepouts ground, List<Furnishing> out, SimPos from,
                                      Piece piece, int reach, int search) {
        if (isFree(ground, out, from, piece, reach)) {
            return from;
        }
        for (int ring = 2; ring <= search; ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                for (int dz = -ring; dz <= ring; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    SimPos at = from.offset(dx, 0, dz);
                    if (isFree(ground, out, at, piece, reach)) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Whether a piece of this size may stand here.
     *
     * <p>The whole siting rule in one place, so that the test which states it
     * about a grown town and the planner that obeys it cannot drift apart. Eight
     * refusals: outside the claim, on a plot standing or planned, on a
     * carriageway, on the wall line, in the forester's belt, on a lamp, across a
     * doorway, or on top of a piece that is already there.
     */
    private static boolean isFree(Keepouts ground, List<Furnishing> out, SimPos at,
                                  Piece piece, int reach) {
        return ground.freeFor(at, piece, reach) && !crowds(out, at, reach);
    }

    /**
     * Whether a piece here would overlap one already accepted.
     *
     * <p>Box against box, with a block of air between them: two fences sharing a
     * line read as one enclosure, and a woodpile flush against a haystack reads
     * as a heap of something nobody can name.
     */
    private static boolean crowds(List<Furnishing> out, SimPos at, int reach) {
        for (Furnishing standing : out) {
            int gap = standing.reach() + reach + 1;
            if (Math.abs(standing.at().x() - at.x()) <= gap
                    && Math.abs(standing.at().z() - at.z()) <= gap) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every piece this town plans stands on ground nothing else has a
     * claim to.
     *
     * <p>An assertion rather than a filter — {@link #isFree} already filters — and
     * it is here for the reason {@code InteriorClearing.sparesTheBelt} is: so a
     * test can state the invariant about a whole grown town on real ground rather
     * than about the one position it happened to check. If it ever comes back
     * false then a pass above has been added that does not go through
     * {@link #isFree}, which is the only way this can break.
     */
    public static boolean standOnFreeGround(Settlement settlement) {
        Keepouts ground = Keepouts.of(settlement);
        List<Furnishing> seen = new ArrayList<>();
        for (Furnishing piece : pieces(settlement)) {
            if (!ground.freeFor(piece.at(), piece.piece(), piece.reach())) {
                return false;
            }
            if (crowds(seen, piece.at(), piece.reach())) {
                return false;
            }
            seen.add(piece);
        }
        return true;
    }

    /**
     * Everything a piece of dressing is not allowed to stand on, gathered once.
     *
     * <p>Gathered rather than asked of the settlement position by position, and
     * that is the difference between a planner that runs and one that does not.
     * {@link #pieces} tests a few thousand candidate positions on a grown town and
     * each test consults every plot, every street, every staked post and every
     * planned lamp; re-deriving the lamp list inside that loop would be
     * {@code LightPlanner.lamps} run a thousand times a step.
     */
    private record Keepouts(SimPos center, int claimRadius, Boxes plots,
                            Boxes roads, Grid wall,
                            Grid lamps, Grid doors, WorkArea belt) {

        /**
         * Points bucketed onto a coarse grid, so a candidate asks about its own
         * neighborhood rather than about the whole town.
         *
         * <p>Written because the honest version was too slow to run. A grown town
         * plans several hundred lamps and several hundred wall posts, and this is
         * asked a few thousand times per plan — so the straight-line version was
         * a million distance comparisons a step, on a work that runs on every
         * settlement in the world. A sixteen-block bucket turns that into a
         * handful of lookups and changes no answer at all: a query reads every
         * bucket its box touches.
         */
        private record Grid(java.util.Map<Long, List<SimPos>> buckets) {

            /** How much ground one bucket covers. Wider than any piece plus its clearance. */
            private static final int CELL = 16;

            static Grid of(List<SimPos> points) {
                java.util.Map<Long, List<SimPos>> buckets = new java.util.HashMap<>();
                for (SimPos point : points) {
                    buckets.computeIfAbsent(key(point.x(), point.z()),
                            unused -> new ArrayList<>()).add(point);
                }
                return new Grid(buckets);
            }

            private static long key(int x, int z) {
                return ((long) Math.floorDiv(x, CELL) << 32)
                        ^ (Math.floorDiv(z, CELL) & 0xFFFFFFFFL);
            }

            /** Whether any point lies within {@code range} of here, as a square. */
            boolean anyWithin(SimPos at, int range) {
                if (buckets.isEmpty()) {
                    return false;
                }
                int fromX = Math.floorDiv(at.x() - range, CELL);
                int toX = Math.floorDiv(at.x() + range, CELL);
                int fromZ = Math.floorDiv(at.z() - range, CELL);
                int toZ = Math.floorDiv(at.z() + range, CELL);
                for (int bx = fromX; bx <= toX; bx++) {
                    for (int bz = fromZ; bz <= toZ; bz++) {
                        List<SimPos> here = buckets.get(
                                ((long) bx << 32) ^ (bz & 0xFFFFFFFFL));
                        if (here == null) {
                            continue;
                        }
                        for (SimPos point : here) {
                            if (Math.abs(point.x() - at.x()) <= range
                                    && Math.abs(point.z() - at.z()) <= range) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        }

        static Keepouts of(Settlement settlement) {
            Boxes plots = new Boxes();
            List<SimPos> doors = new ArrayList<>();
            for (Building building : settlement.buildings()) {
                SimPos at = building.origin();
                if (building.footprint().isKnown()) {
                    plots.add(at.x(), at.z(), at.x(), at.z(),
                            Math.max(building.footprint().width(),
                                    building.footprint().depth()) / 2);
                    doors.add(building.doorstep());
                } else {
                    plots.add(at.x(), at.z(), at.x(), at.z(),
                            BuildPlanner.plotSpanOf(building.blueprintId(),
                                    settlement.catalog()) / 2);
                }
            }
            // The ground the town has already promised to something it has not
            // built yet. Left out, a hedge is planted on the plot a house is
            // about to be raised on and the crew lays a wall through it.
            for (BuildTask task : settlement.buildQueue()) {
                if (BuildPlanner.holdsGround(task.blueprintId()) && !task.isUpgrade()) {
                    plots.add(task.origin().x(), task.origin().z(),
                            task.origin().x(), task.origin().z(),
                            BuildPlanner.plotSpanOf(task.blueprintId(),
                                    settlement.catalog()) / 2);
                }
            }
            Boxes roads = new Boxes();
            PathNetwork paths = settlement.paths();
            if (paths != null) {
                List<PathNetwork.Segment> runs = paths.segments();
                for (int i = 0; i < runs.size(); i++) {
                    if (!paths.isOpened(i)) {
                        continue;
                    }
                    PathNetwork.Segment run = runs.get(i);
                    roads.add(run.from().x(), run.from().z(), run.to().x(), run.to().z(),
                            run.paveHalf());
                }
            }
            List<SimPos> wall = new ArrayList<>();
            Perimeter perimeter = settlement.perimeter();
            if (perimeter != null) {
                wall.addAll(perimeter.ringPositions());
                wall.addAll(perimeter.retiredPositions());
            }
            List<SimPos> lamps = new ArrayList<>();
            for (LightPlanner.Lamp lamp : LightPlanner.lamps(settlement)) {
                lamps.add(lamp.at());
            }
            return new Keepouts(settlement.center(), settlement.claimRadius(), plots, roads,
                    Grid.of(wall), Grid.of(lamps), Grid.of(doors),
                    settlement.lumberArea());
        }

        /**
         * Whether any plot stands near enough for this stretch of verge to be the
         * front of something.
         *
         * <p>Asked of the plots rather than of the doors, because what makes a
         * street a street is being built along, and a building's back wall is as
         * much frontage as its front one when you are standing outside it.
         */
        boolean hasFrontage(SimPos at, int within) {
            return plots.anyWithin(at.x(), at.z(), within);
        }

        boolean freeFor(SimPos at, Piece piece, int reach) {
            long out = (long) (at.x() - center.x()) * (at.x() - center.x())
                    + (long) (at.z() - center.z()) * (at.z() - center.z());
            long held = (long) (claimRadius - reach) * (claimRadius - reach);
            if (claimRadius - reach < 0 || out > held) {
                return false;   // a town does not dress ground it does not hold
            }
            if (plots.anyWithin(at.x(), at.z(), reach + 1)) {
                return false;
            }
            if (roads.anyWithin(at.x(), at.z(), piece.roadClearance() + reach)) {
                return false;
            }
            if (wall.anyWithin(at, reach + WALL_CLEARANCE)
                    || lamps.anyWithin(at, reach + LAMP_CLEARANCE)
                    || doors.anyWithin(at, reach + DOOR_CLEARANCE)) {
                return false;
            }
            if (belt != null) {
                long dx = belt.center().x() - at.x();
                long dz = belt.center().z() - at.z();
                long span = (long) (belt.radius() + reach) * (belt.radius() + reach);
                if (dx * dx + dz * dz <= span) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * How far past its own edge anything in a bucketed index may be asked about.
     *
     * <p>An index registers each thing it holds in every bucket its own box plus
     * this reaches into, which is what lets a query read the one bucket it stands
     * in and nothing else. So it has to be at least the largest {@code extra} any
     * caller ever passes, and the largest is {@link #HEDGE_FRONTAGE} at sixteen —
     * larger than anything the clearances come to, because asking whether a stretch
     * of street has anything built along it reaches further than asking whether
     * something is in the way. A rule added later that asks for more than this
     * would quietly stop seeing things near a bucket edge, which is why the number
     * is stated once, here, rather than guessed at in two places.
     */
    private static final int INDEX_PAD = 20;

    /**
     * Squares of ground, bucketed so a candidate asks about its own neighborhood.
     *
     * <p>The plots and the streets both come to the same question — "is there one
     * of these within so many blocks of here" — and both were first asked as a walk
     * of the whole town, a few thousand times per plan. Each square is registered
     * here in every bucket its own box plus {@link #INDEX_PAD} touches, so a query
     * reads exactly one bucket; and the road test is done in squared integers
     * against the same rounded nearest point
     * {@code PathNetwork.Segment.nearestTo} returns — same answer, no square root
     * and no allocation, where before it allocated a {@code SimPos} and took a
     * square root for every street in the town for every candidate.
     *
     * <p><strong>It was not what made the plan affordable, and the measurement says
     * so.</strong> Bucketing took a town of thirty from 5.6 milliseconds to 5.1: the
     * scans this replaces were never the bill. Two thirds of the bill is
     * {@code LightPlanner.lamps}, which the siting has to ask because a hedge may
     * not swallow a lamp, and there is no version of this that does not ask it. What
     * made the plan affordable is not working it out again unless the town has
     * changed — see {@code Settlement.cachedDressing}. This is kept because it is a
     * real improvement to a hot loop and costs nothing to keep, and it is documented
     * as not being the fix so that nobody reads it as one.
     */
    private static final class Boxes {

        /**
         * How much ground one bucket covers: thirty-two blocks.
         *
         * <p>Bigger than the point grids' sixteen, because what is registered here
         * is boxes rather than points and a long street registers into every bucket
         * it crosses. Thirty-two keeps that registration cheap while still leaving
         * two or three things in the bucket a query lands in.
         */
        private static final int CELL = 32;

        /** What one entry is: a segment's two ends, or a plot's center twice. */
        private record Entry(int ax, int az, int bx, int bz, int half) {
        }

        private final java.util.Map<Long, List<Entry>> buckets = new java.util.HashMap<>();

        void add(int ax, int az, int bx, int bz, int half) {
            Entry entry = new Entry(ax, az, bx, bz, half);
            int pad = half + INDEX_PAD;
            int fromX = Math.floorDiv(Math.min(ax, bx) - pad, CELL);
            int toX = Math.floorDiv(Math.max(ax, bx) + pad, CELL);
            int fromZ = Math.floorDiv(Math.min(az, bz) - pad, CELL);
            int toZ = Math.floorDiv(Math.max(az, bz) + pad, CELL);
            for (int bxk = fromX; bxk <= toX; bxk++) {
                for (int bzk = fromZ; bzk <= toZ; bzk++) {
                    buckets.computeIfAbsent(key(bxk, bzk),
                            unused -> new ArrayList<>()).add(entry);
                }
            }
        }

        private static long key(int bx, int bz) {
            return ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
        }

        /**
         * Whether any entry comes within {@code extra} blocks of its own edge of
         * here.
         *
         * <p>A point entry — a plot — is measured as a square, because a plot is
         * one. A two-ended entry — a street — is measured from the nearest point
         * along it, rounded exactly as {@code Segment.nearestTo} rounds, so this
         * and {@code LightPlanner.standsInTheRoad} cannot disagree about whether
         * something is in the road.
         */
        boolean anyWithin(int x, int z, int extra) {
            List<Entry> here = buckets.get(
                    key(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
            if (here == null) {
                return false;
            }
            for (Entry entry : here) {
                int span = entry.half() + extra;
                if (entry.ax() == entry.bx() && entry.az() == entry.bz()) {
                    if (Math.abs(entry.ax() - x) <= span
                            && Math.abs(entry.az() - z) <= span) {
                        return true;
                    }
                    continue;
                }
                if (distanceSqToRun(entry, x, z) <= (long) span * span) {
                    return true;
                }
            }
            return false;
        }

        /** The squared distance to the nearest column of a run. See {@link #anyWithin}. */
        private static long distanceSqToRun(Entry run, int x, int z) {
            double dx = run.bx() - run.ax();
            double dz = run.bz() - run.az();
            double lenSq = dx * dx + dz * dz;
            double t = lenSq == 0 ? 0
                    : ((x - run.ax()) * dx + (z - run.az()) * dz) / lenSq;
            t = Math.max(0, Math.min(1, t));
            long nx = run.ax() + Math.round(dx * t);
            long nz = run.az() + Math.round(dz * t);
            return (nx - x) * (nx - x) + (nz - z) * (nz - z);
        }
    }
}
