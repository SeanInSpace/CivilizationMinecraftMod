package com.civilization.sim.work;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Field;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.RoadUpkeep;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a town puts its street lights.
 *
 * <p><strong>What this is for.</strong> One Normal-difficulty night in a burgher
 * town of seventeen killed seventeen people — a creeper, seven zombies, two
 * skeletons and four spiders — at outlying fields and houses a hundred and thirty
 * to a hundred and sixty-five blocks out and on the unlit ring road. The farm
 * lanterns were the only light in the whole claim. Nothing about that night was a
 * combat problem: the guards killed thirteen, which is a working watch. The town
 * was dark, so the town was a spawner, and the people walking home through it
 * were walking through the mobs it had made.
 *
 * <p>So lighting is a public work, exactly like the roads and the wall: planned
 * here without a world, raised post by post by a builder where somebody is
 * watching, and by the clock where nobody is. {@code LightLayer} is its platform
 * half and {@link LightStyle} is what each people's lamp looks like.
 *
 * <p><strong>Why it is derived rather than stored.</strong> A light's position is
 * a function of the streets that are open and the buildings that stand, and both
 * of those are already saved. Storing the list would be storing a second copy of
 * the town's own shape, which is the thing {@code PathNetwork} learned not to do.
 * What <em>is</em> stored is one number — how many of them the town has raised —
 * and it is an index into this list for exactly the same reason the perimeter
 * keeps one: a work raised in the order it was planned closes as a line of
 * lights, and one raised nearest-first closes as a scatter.
 *
 * <p><strong>The order is center-outward</strong>, and that is load-bearing. The
 * index has to mean the same lamp from one step to the next, and a town grows
 * outward — new streets and new houses are farther from the middle than the old
 * ones — so sorting by distance from the center appends rather than inserts. It
 * is not a guarantee: an infill house inside the ring does shift every index past
 * it by one. That is survivable rather than ignored, because a shifted index
 * sends a builder to a lamp that is already standing, and both halves of the work
 * check the ground before they charge for anything — see {@code LightLayer.owed}.
 * The cost of the worst case is one plank.
 */
public final class LightPlanner {

    private LightPlanner() {
    }

    /**
     * Blocks between lights along a street: eight.
     *
     * <p>Picked off the light itself rather than off taste. A lantern is light
     * level 15 and a torch 14, and light falls off a level a block, so a lamp
     * holds a street above level 8 out to about six blocks and above zero — which
     * is the level a hostile actually needs to spawn — much further than that.
     * Eight puts every column of a carriageway within {@link #REACH} of a lamp
     * with the lamps standing on the verge rather than in the road, which is the
     * geometry {@code LightPlannerTest} pins down.
     */
    public static final int SPACING = 8;

    /**
     * How far a light has to reach: eight blocks.
     *
     * <p>Stated as its own number because it is the promise, and {@link #SPACING}
     * is only the means. A planner that spaced its lamps differently — round a
     * bend, along a short spur — still has to keep this.
     */
    public static final int REACH = 8;

    /** How far past the paved edge a lamp stands, so nothing is ever in the road. */
    public static final int VERGE = 1;

    /**
     * How close two lamps may stand before the second is not worth raising.
     *
     * <p>Only ever applied to the lamps that are <em>not</em> the street
     * lighting. A lamp at a junction, beside a door or on a field corner is
     * there to cover one particular dark spot, and two of them a pace apart
     * cover it once; a lamp on a verge is part of a run whose spacing is the
     * whole promise of the work, so it is never dropped for being near
     * something. Getting that the wrong way round would let a crowded doorway
     * quietly delete the street lighting past it.
     */
    public static final int MIN_SEPARATION = 3;

    /**
     * Lamps the clock raises in one step: two.
     *
     * <p>The same pace the wall runs at per pair of hands
     * ({@code PerimeterPlanner.POSTS_PER_HAND}), and for the same reason: an
     * unwatched town should light its streets at about the rate a watched one
     * does, or coming home from a journey would mean finding a town that had
     * either done nothing or finished everything.
     */
    public static final int LIGHTS_PER_STEP = 2;

    /** Why a lamp is where it is. Reports read this; the layer does not care. */
    public enum Post {
        /** On a verge, part of a run's regular spacing. The backbone. */
        STREET,
        /** Where two ways meet, which is where somebody stands and looks about. */
        JUNCTION,
        /** Beside a door, so the last five steps home are lit. */
        DOOR,
        /** A corner of a field or a plot: the outlying dark the deaths happened in. */
        CORNER
    }

    /** One planned lamp: where it stands and what it is for. */
    public record Lamp(SimPos at, Post why) {
    }

    /**
     * Every lamp this town's streets and plots call for, in the order it raises
     * them.
     *
     * <p>Only <em>opened</em> streets. A planned stretch nobody has walked out is
     * not a street yet, and lighting one would be lighting a line on paper — the
     * same mistake paving a stretch ahead of the crew was.
     */
    public static List<Lamp> lamps(Settlement settlement) {
        List<Lamp> out = new ArrayList<>();
        PathNetwork paths = settlement.paths();
        if (paths != null) {
            // The street lighting first, so that nothing else can displace it:
            // see MIN_SEPARATION.
            alongTheStreets(settlement, paths, out);
            atTheJunctions(settlement, paths, out);
        }
        besideTheDoors(settlement, out);
        onThePlotCorners(settlement, out);
        return accept(settlement, out);
    }

    /** How many lamps the town's shape currently calls for. */
    public static int wanted(Settlement settlement) {
        return lamps(settlement).size();
    }

    /** Whether every lamp the town's shape calls for is standing. */
    public static boolean isLit(Settlement settlement) {
        return settlement.lightsRaised() >= wanted(settlement);
    }

    /** The next lamp to raise, or null when the streets are all lit. */
    public static Lamp next(Settlement settlement) {
        List<Lamp> all = lamps(settlement);
        int raised = settlement.lightsRaised();
        return raised >= 0 && raised < all.size() ? all.get(raised) : null;
    }

    /** How this people light their streets. */
    public static LightStyle styleOf(Settlement settlement) {
        return LightStyle.of(Culture.of(settlement.cultureId()));
    }

    // --- the price ---

    /**
     * Pays for one whole lamp — the standard and the light on it.
     *
     * <p>What the clock pays, and both or neither: a town with the timber for the
     * post but no iron for the lantern does not get a free lantern and does not
     * lose the timber pretending otherwise. The same rule, and the same shape,
     * as {@code PerimeterPlanner.payForPost}.
     */
    public static boolean payForLamp(Settlement settlement) {
        LightStyle style = styleOf(settlement);
        String post = style.postResource();
        String light = style.lightResource();
        if (post.equals(light)) {
            // A torch on a wooden post: one resource, two of it, taken in one
            // go so a town with exactly one plank cannot buy half a lamp.
            return settlement.stores().take(post, 2);
        }
        if (!settlement.stores().has(post, 1) || !settlement.stores().has(light, 1)) {
            return false;
        }
        return settlement.stores().take(post, 1) && settlement.stores().take(light, 1);
    }

    /**
     * The half of a lamp's price still owed when the post is already in hand.
     *
     * <p>A watched town's builder carries the standard out of the storehouse, so
     * it has left the books there; charging for it again at the verge would take
     * two planks out of the town for one lamp. What is left is the light, and it
     * is charged where the wall's coin is charged: at the lamp, with somebody
     * standing there ready to hang it.
     */
    public static boolean payForTheLightOnly(Settlement settlement) {
        return settlement.stores().take(styleOf(settlement).lightResource(), 1);
    }

    /**
     * Timber the lighting will not touch, so a building is never starved by lamps.
     *
     * <p>Half what the wall keeps back. A lamp is one plank and a house is
     * hundreds, but the wall's reserve exists because a ring is a commitment of
     * hundreds of posts made all at once, and the lighting of a town of twenty
     * buildings is tens — and unlike the wall, it is the thing keeping the
     * people who would build the house alive.
     */
    public static final int TIMBER_KEPT_FOR_BUILDING = 32;

    /**
     * Whether the town should be lighting anything at all yet.
     *
     * <p>Three refusals, and the first is the one with a measurement behind it.
     *
     * <p><strong>Not while a street is still waiting to be walked out.</strong> A
     * lamp is planned onto the verge of an opened street, so a town whose network is
     * still arriving is a town whose lighting plan is still changing under it — and
     * more to the point, the timber and iron a lamp costs are the same timber and
     * iron the town is building with. Letting the lighting spend while a village was
     * still filling in changed what it built and how far its lanes reached, which
     * showed up as a seeded forester's stand fouled by a lane the town would not
     * otherwise have laid yet ({@code WorldgenTownTest}). This is also exactly what
     * {@link PublicWorks#availableTo} already says about hands, said again to the
     * clock so the two fidelities agree: paving leads, lighting follows.
     *
     * <p>Then the plain two: there has to be a lamp outstanding, and the timber it
     * costs must not be timber the build queue is owed.
     */
    public static boolean worthStarting(Settlement settlement) {
        if (next(settlement) == null) {
            return false;
        }
        if (new PublicWorks.RoadWork().nextStation(settlement) != null) {
            return false;   // the paving leads; see above
        }
        LightStyle style = styleOf(settlement);
        if (!TownStores.WOOD.equals(style.postResource())) {
            return true;   // a stone post takes nothing the build queue is owed
        }
        return settlement.woodStock() >= TIMBER_KEPT_FOR_BUILDING + 2;
    }

    // --- the clock ---

    /**
     * One step of lighting for a town nobody is looking at.
     *
     * <p>The same two refusals the roads have, for the same two reasons. Hands
     * are on the lighting, or coming back to it, so a clock would raise a lamp in
     * front of the very builder walking out to raise it; or a player can see the
     * spot, in which case there is no clock at all — a lantern that lights itself
     * while somebody stands there is magic however dark the street was.
     *
     * <p>A dead town lights nothing. Raising a lamp is work and there is nobody
     * left to do it, which is the {@code RoadUpkeep} distinction exactly: the
     * lights a town raised while it was alive stay standing and stay drawn, and
     * the ones it never got to never appear.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        if (!settlement.hasLivingResidents()) {
            return;
        }
        // And nobody raises a lamp post at night, whoever is watching. A watched
        // town's builders are walked home by the curfew like everybody else, so a
        // clock that went on lighting through the dark would light an unwatched town
        // faster than a watched one -- which is the asymmetry the trades were just
        // squared for, and it would be no better here for being in a good cause.
        if (com.civilization.sim.person.Curfew.idlesUnwatchedWork(
                ctx.bridge().dayTime(), com.civilization.sim.person.Curfew.LEAD_TICKS)) {
            return;
        }
        for (int raised = 0; raised < LIGHTS_PER_STEP; raised++) {
            Lamp lamp = next(settlement);
            if (lamp == null) {
                return;
            }
            if (!worthStarting(settlement)) {
                return;
            }
            if (PublicWorks.leaveItToTheCrew(settlement, ctx.bridge(), new PublicWorks.LightWork())) {
                return;   // somebody is walking out there to do it themselves
            }
            if (ctx.bridge().playerWithin(lamp.at(), ctx.settings().observedRadius())) {
                return;   // watched ground: hands or nothing
            }
            if (!payForLamp(settlement)) {
                return;
            }
            settlement.setLightsRaised(settlement.lightsRaised() + 1);
        }
    }

    // --- where they go ---

    /**
     * A lamp every {@link #SPACING} blocks along each opened run, on alternating
     * verges.
     *
     * <p>Alternating, because a row of lamps all down one side of a street lights
     * one side of it: the shadow under the opposite eaves is where a spider
     * stands. Alternating also halves what the lighting costs per side without
     * changing what a column of carriageway is worth — see {@code
     * LightPlannerTest}, which measures the worst column rather than trusting the
     * arithmetic in this sentence.
     *
     * <p>Both ends are always lit whatever the spacing lands on, because the end
     * of a run is where it meets another one and that is a junction.
     */
    private static void alongTheStreets(Settlement settlement, PathNetwork paths,
                                        List<Lamp> out) {
        List<PathNetwork.Segment> runs = paths.segments();
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i) || paths.isUnwalkable(i)) {
                continue;
            }
            PathNetwork.Segment run = runs.get(i);
            List<SimPos> along = run.positions();
            int side = 1;
            for (int at = 0; at < along.size(); at += SPACING) {
                add(out, standingRoom(settlement, run, along, at, side), Post.STREET);
                side = -side;
            }
            int last = along.size() - 1;
            if (last % SPACING != 0) {
                add(out, standingRoom(settlement, run, along, last, side), Post.STREET);
            }
        }
    }

    private static void add(List<Lamp> out, SimPos at, Post why) {
        if (at != null) {
            out.add(new Lamp(at, why));
        }
    }

    /**
     * Somewhere near this column of a run that a lamp can actually stand.
     *
     * <p>The verge on the wanted side first, then the other verge, then a pace or
     * two along the run and both verges again. Null when none of that finds
     * standing room, which for one column of one street is a gap the neighbouring
     * lamps mostly cover.
     *
     * <p>It exists because of the crossroads. A lamp at the middle of a crossing
     * stands on its own verge and in the <em>crossing street's carriageway</em> —
     * both of its verges do, because the two roads are perpendicular — so the whole
     * junction was refused and the darkest column of a four-way crossing came out
     * eight and a half blocks from a lamp. Stepping a pace off the crossing puts the
     * post on the corner of the junction, which is where a lamp standard goes in
     * every town that has ever had one.
     */
    private static SimPos standingRoom(Settlement settlement, PathNetwork.Segment run,
                                       List<SimPos> along, int index, int side) {
        for (int shift = 0; shift <= SHIFT_ALONG; shift++) {
            for (int step : shift == 0 ? new int[] {0} : new int[] {-shift, shift}) {
                int at = index + step;
                if (at < 0 || at >= along.size()) {
                    continue;
                }
                for (int trySide : new int[] {side, -side}) {
                    SimPos spot = onTheVerge(run, along, at, trySide);
                    if (!insideAPlot(settlement, spot) && !onACarriageway(settlement, spot)) {
                        return spot;
                    }
                }
            }
        }
        return null;
    }

    /**
     * How far along a run a lamp may be nudged to find standing room: three.
     *
     * <p>Enough to clear the widest carriageway the plan draws — eight, so five
     * paved and two of verge either side — and little enough that a lamp is still
     * lighting the column it was planned for. See {@code StreetLightsTest}, which
     * measures the worst column rather than trusting this number.
     */
    private static final int SHIFT_ALONG = 3;

    /** A lamp where ways meet, which is where somebody stops and looks about. */
    private static void atTheJunctions(Settlement settlement, PathNetwork paths,
                                       List<Lamp> out) {
        List<PathNetwork.Segment> runs = paths.segments();
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i) || paths.isUnwalkable(i)) {
                continue;
            }
            PathNetwork.Segment run = runs.get(i);
            List<SimPos> along = run.positions();
            for (int end : List.of(0, along.size() - 1)) {
                if (!meetsAnother(paths, runs, i, along.get(end))) {
                    continue;
                }
                add(out, standingRoom(settlement, run, along, end, 1), Post.JUNCTION);
            }
        }
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
     * The verge position beside one column of a run.
     *
     * <p>Perpendicular to the run's own direction and {@link PathNetwork.Segment#paveHalf()}
     * plus {@link #VERGE} out from the centerline, so a lamp stands on the grass
     * beside the stones rather than in the carriageway. It matters more than it
     * looks: a post in the road is a post the paving sweep fights over for ever,
     * and a post in a gateway is a gate that will not open.
     */
    private static SimPos onTheVerge(PathNetwork.Segment run, List<SimPos> along,
                                     int index, int side) {
        SimPos at = along.get(Math.max(0, Math.min(index, along.size() - 1)));
        int dx = run.to().x() - run.from().x();
        int dz = run.to().z() - run.from().z();
        double length = Math.hypot(dx, dz);
        int off = run.paveHalf() + VERGE;
        if (length == 0) {
            return at.offset(off * side, 0, 0);
        }
        int px = (int) Math.round(-dz / length * off) * side;
        int pz = (int) Math.round(dx / length * off) * side;
        if (px == 0 && pz == 0) {
            px = off * side;   // a run shorter than the offset still has a side
        }
        return at.offset(px, 0, pz);
    }

    /**
     * A lamp beside every door.
     *
     * <p>Beside it and not in front of it. A lamp on the doorstep is a lamp
     * somebody has to walk round to get in, and the doorstep is also where the
     * road ends — so the post goes one block to the side of the door, along the
     * wall, which is where a person would hang one.
     */
    private static void besideTheDoors(Settlement settlement, List<Lamp> out) {
        for (Building building : settlement.buildings()) {
            if (!building.footprint().isKnown()) {
                continue;
            }
            SimPos door = building.doorstep();
            boolean acrossX = building.facing() == 1 || building.facing() == 3;
            out.add(new Lamp(acrossX ? door.offset(0, 0, 1) : door.offset(1, 0, 0),
                    Post.DOOR));
        }
    }

    /**
     * A lamp on each corner of every field.
     *
     * <p>Fields and not every plot, and the distinction is where the people
     * actually died. A house is a lit room with one dark doorstep, and the
     * doorstep gets a lamp above; a field is a hundred square blocks of open
     * ground with nobody in it after dusk and no walls to keep anything out, a
     * hundred and thirty blocks from the middle of town. Four corners is what
     * turns one of those from a spawner into a farm with lights on it.
     *
     * <p>Reading "every farm/plot corner" as every plot was tried on paper and
     * comes to four lamps on every shed in the town — two hundred and forty
     * posts and two hundred and forty iron for a town of sixty buildings, most
     * of them lighting a wall that was already lit from the door. The corners
     * that matter are the ones with a crop between them.
     */
    private static void onThePlotCorners(Settlement settlement, List<Lamp> out) {
        for (Building building : settlement.buildings()) {
            if (!Field.isField(building.blueprintId()) || !building.footprint().isKnown()) {
                continue;
            }
            SimPos at = building.origin();
            int rx = building.footprint().width() / 2 + 1;
            int rz = building.footprint().depth() / 2 + 1;
            for (int sx : List.of(-1, 1)) {
                for (int sz : List.of(-1, 1)) {
                    out.add(new Lamp(at.offset(rx * sx, 0, rz * sz), Post.CORNER));
                }
            }
        }
    }

    /**
     * Throws out the lamps that cannot stand where they were planned, and puts
     * what is left in raising order.
     *
     * <p>Three refusals. A lamp inside somebody's footprint is a lamp in their
     * front room, and it is also — much worse — a block the demolition sweep
     * would read as a standing wall and go on sparing a crater for; see
     * {@code TownAuditor.demolishRuins}, which counts what is inside a plot and
     * has no way of knowing a lantern is not a house. A lamp on a carriageway is
     * a post in the road. And a lamp that is not the street lighting and stands
     * within {@link #MIN_SEPARATION} of one already accepted is a second lamp
     * over the same dark spot.
     */
    private static List<Lamp> accept(Settlement settlement, List<Lamp> candidates) {
        List<Lamp> kept = new ArrayList<>(candidates.size());
        for (Lamp lamp : candidates) {
            if (insideAPlot(settlement, lamp.at()) || onACarriageway(settlement, lamp.at())) {
                continue;
            }
            if (lamp.why() != Post.STREET && crowds(kept, lamp.at())) {
                continue;
            }
            kept.add(lamp);
        }
        // Center-outward, so the index means the same lamp as the town grows.
        // Ties broken on the position itself rather than left to the sort's
        // stability, because the candidate order above is not the order the
        // caller sees and "whichever came first" would be a different answer
        // after any change to the passes above it.
        SimPos center = settlement.center();
        kept.sort(Comparator
                .comparingLong((Lamp lamp) -> lamp.at().horizontalDistanceSq(center))
                .thenComparingInt(lamp -> lamp.at().x())
                .thenComparingInt(lamp -> lamp.at().z()));
        return List.copyOf(kept);
    }

    private static boolean crowds(List<Lamp> kept, SimPos at) {
        for (Lamp standing : kept) {
            if (standing.at().horizontalDistance(at) < MIN_SEPARATION) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideAPlot(Settlement settlement, SimPos at) {
        for (Building building : settlement.buildings()) {
            if (building.footprint().isKnown() && building.footprint().covers(
                    building.origin().x(), building.origin().z(), at.x(), at.z())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this spot is on the stones rather than on the verge beside them.
     *
     * <p>Measured against {@link PathNetwork.Segment#paveHalf()} and deliberately
     * <em>not</em> against {@link PathNetwork.Segment#touches}, which is the test a
     * <em>plot</em> has to pass. A street's reservation is eight blocks wide and
     * its carriageway is five: the ground between the two is the verge, which is
     * exactly where a lamp belongs and exactly where nothing may be built. Asking
     * the plot question here refused every lamp in the town — the verge is inside
     * the reservation by construction, because that is what a verge is.
     */
    private static boolean onACarriageway(Settlement settlement, SimPos at) {
        PathNetwork paths = settlement.paths();
        if (paths == null) {
            return false;
        }
        List<PathNetwork.Segment> runs = paths.segments();
        for (int i = 0; i < runs.size(); i++) {
            if (!paths.isOpened(i)) {
                continue;
            }
            PathNetwork.Segment run = runs.get(i);
            if (run.nearestTo(at).horizontalDistance(at) <= run.paveHalf()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a lamp would stand on the paved surface of any opened street.
     *
     * <p>The same question {@link #onACarriageway} answers, exposed so a test can
     * state the invariant about a whole town rather than re-deriving the paving
     * geometry and getting it wrong in the other direction.
     */
    public static boolean standsInTheRoad(Settlement settlement, SimPos at) {
        return onACarriageway(settlement, at);
    }

    // --- reading the plan ---

    /** How far this spot is from the nearest planned lamp, or -1 when there are none. */
    public static double toNearestLamp(List<Lamp> lamps, SimPos at) {
        double closest = -1;
        for (Lamp lamp : lamps) {
            double distance = lamp.at().horizontalDistance(at);
            if (closest < 0 || distance < closest) {
                closest = distance;
            }
        }
        return closest;
    }

    /**
     * Whether a lamp of the raised prefix may be put back where it is missing.
     *
     * <p>The lighting's half of {@link RoadUpkeep}, and the same distinction:
     * drawing a lamp the town raised while somebody was alive to raise it is a
     * record and happens whether or not anybody is left, and putting back one
     * that has since been broken is a morning with a ladder and needs hands.
     * Written here rather than in the layer because it is a rule, and a rule that
     * only exists inside a block-placing loop is a rule a dead town keeps
     * breaking.
     */
    public static boolean mayMend(Settlement settlement, int index) {
        return index >= 0 && index < settlement.lightsRaised()
                && settlement.hasLivingResidents();
    }

    /** Whether a lamp may be drawn into the world for the first time. */
    public static boolean mayDraw(Settlement settlement, int index) {
        return index >= 0 && index < settlement.lightsRaised();
    }
}
