package com.kingdoms.sim.settlement;

import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

import java.util.List;

/**
 * Decides where the town's roads run.
 *
 * <p>Three rules, and between them they are the whole difference from the star
 * of overlapping lines this replaced:
 *
 * <ol>
 *   <li><strong>Join the nearest way, not the centre.</strong> A new building
 *       branches off whatever road already passes closest — only the first one
 *       runs to the hub — so the network grows outward like a village's does
 *       rather than putting one more spoke through the middle of town.</li>
 *   <li><strong>Right angles.</strong> Every road is axis-aligned and a corner
 *       is two runs, which leaves square ground between them for buildings to
 *       sit against. Diagonal tracks left nothing square to build on.</li>
 *   <li><strong>Out of the door first.</strong> The first run leaves along the
 *       way the door actually faces, then turns. The old layer aimed at a fixed
 *       point due south of every building while the placer rotated three
 *       quarters of them to face the centre, so most roads began at a blank
 *       wall.</li>
 * </ol>
 *
 * <p>The hub is the hall when there is one and the camp post before that. That
 * matters more than it sounds: the hall is the TOWN capstone now, so a hub that
 * insisted on it meant no camp, homestead, fortified settlement or village had
 * any roads at all.
 */
public final class PathPlanner {

    /**
     * Longest road planned for one building.
     *
     * <p>A route over this is not refused for good — the building simply stays
     * unjoined and is tried again as the network spreads toward it. The layer
     * this replaced marked such buildings done before it discovered they were
     * out of range, so they were dropped permanently and in silence.
     */
    public static final int MAX_ROUTE = 192;

    private PathPlanner() {
    }

    /**
     * How near a building a planned street has to pass to be worth laying.
     *
     * <p>A setback plus a plot pitch: far enough that the stretch in front of a
     * house is laid along with it and the stretch between two houses joins up,
     * near enough that a street nobody has built on yet stays a line on a plan.
     *
     * <p>Laying the whole plan instead was the first attempt, and it is worth
     * recording why it was wrong. The plan describes a town of two hundred and
     * fifty-six; a village has sixty. Taking every street inside the village own
     * reach gave a settlement of sixty-two buildings <strong>405 stretches of
     * carriageway and thirty thousand paved columns</strong> — a market town
     * street grid around a hamlet, most of it running past nothing, and a very
     * large number of block writes for a town nobody was watching.
     */
    private static final int STREET_NEAR = 28;

    /**
     * Bare ground between a wall and a carriageway, so a door has a doorstep.
     *
     * <p>The same kerb {@code Settlement.isPlotFree} keeps, and it has to be.
     * Siting refuses a plot within {@code span/2 + KERB} of a street; this
     * refused to lay a street within {@code span/2} of a plot. One block of
     * disagreement, and it is enough: a street laid exactly on the kerb line is
     * ground siting would never have built on, so the survey reports a building
     * in the road that the building never chose. Whichever rule runs second
     * wins, and the two must mean the same thing by "clear".
     */
    private static final int KERB = 1;

    /**
     * How far round its own centre a town lays streets before it has anything.
     *
     * <p>A founding camp has one post and nothing to measure from, and a plan
     * whose streets are all judged against buildings that do not exist would
     * never lay any. This is the market and the first of the spine, which is
     * where a town starts and what it builds its first houses along — the route
     * being there first is the whole point.
     */
    private static final int FIRST_STREETS = 40;

    /**
     * Lays the streets this town has planned, where the town has reached them.
     *
     * <p>Roads in this simulation have always been a <em>consequence</em>: a
     * building went up and afterwards a track was run from its door to whatever
     * passed nearest. Every real settlement works the other way round — the
     * route is there first and the buildings take frontage on it — and until
     * there was a plan carrying streets there was nowhere to keep the route.
     *
     * <p>A planned street enters the network as ordinary runs, so everything
     * downstream needs to know nothing about plans: they are opened by a builder
     * walking out to them, mended when they grow over, counted by the gates when
     * the palisade decides where the roads leave town, and consulted by the
     * siting code so the next building goes up near a street rather than in a
     * field. The only thing that makes them streets is that they are wider and
     * they bend.
     *
     * <p>Arrangements with no streets — the warren, the organic scatter — lay
     * nothing and go on joining doors to tracks exactly as before.
     */
    /**
     * The most a way may climb between one block and the next.
     *
     * <p>One block is a step anybody can take. Two is a jump, and a cart cannot
     * make it at all; more than that is a wall with gravel on it, which is what
     * a planned street becomes when it is laid across a hillside without anybody
     * asking how high the hillside is.
     *
     * <p>That was exactly the state of things: {@code layPlannedStreets} copied
     * the plan's lines onto the ground and never consulted the terrain, because
     * a {@link TownPlan} is a flat drawing and nothing downstream was asking. On
     * a superflat world every one of 292 runs measured perfectly level, which is
     * the proof that the steps come entirely from routing over ground rather
     * than from the geometry.
     */
    private static final int MAX_ROAD_STEP = 1;

    /**
     * Whether the ground under this run is too steep to lay a street along.
     *
     * <p>Refusing is the honest answer rather than terracing it. A town that
     * cannot take its planned frontage on a cliff should grow somewhere else,
     * and the plot on the far side is refused with it — the alternative is a
     * street that arrives at a wall and a house nobody can reach.
     */
    private static boolean tooSteepToWalk(PathNetwork.Segment run, SimContext ctx) {
        List<SimPos> along = run.positions();
        int last = ctx.bridge().groundHeight(along.get(0));
        for (int i = 1; i < along.size(); i++) {
            int here = ctx.bridge().groundHeight(along.get(i));
            if (Math.abs(here - last) > MAX_ROAD_STEP) {
                return true;
            }
            last = here;
        }
        return false;
    }

    static void layPlannedStreets(Settlement settlement, PathNetwork network,
                                  SimContext ctx) {
        if (!Layouts.isStreetsFirst(settlement.arrangement())) {
            return;
        }
        List<SimPos> standing = new java.util.ArrayList<>();
        for (Building building : settlement.buildings()) {
            if (BuildPlanner.holdsGround(building.blueprintId())) {
                standing.add(building.origin());
            }
        }
        if (standing.size() == network.streetsLaidFor()) {
            return;   // nothing new has been built; a town steps every tick
        }
        SimPos centre = settlement.centre();
        // The plan streets are the same whatever slice of its plots is asked
        // for, so this is the cheap call and it is the cached one.
        TownPlan plan = settlement.arrangement().planFor(centre, 1);
        for (TownPlan.Street street : plan.streets()) {
            List<SimPos> path = street.path();
            for (int i = 1; i < path.size(); i++) {
                SimPos from = path.get(i - 1);
                SimPos to = path.get(i);
                SimPos middle = new SimPos((from.x() + to.x()) / 2, from.y(),
                        (from.z() + to.z()) / 2);
                if (!within(middle, centre, FIRST_STREETS)
                        && !nearAny(middle, standing, STREET_NEAR)) {
                    continue;
                }
                PathNetwork.Segment run =
                        new PathNetwork.Segment(from, to, street.width());
                // Not through a house that is already there. The siting code
                // refuses to build on a street, but the two rules have to point
                // both ways or the town simply does it in the other order: two
                // animal farms stood on eight-wide carriageways that were laid
                // straight through them at steps 234 and 435, long after they
                // were built. A plan is not a warrant to pave somebody's floor.
                if (!crossesAnything(settlement, run) && !tooSteepToWalk(run, ctx)) {
                    network.add(run);
                }
            }
        }
        network.setStreetsLaidFor(standing.size());
    }

    /**
     * Whether this run would be laid through ground a building already holds.
     *
     * <p>Standing buildings <em>and</em> the build queue, which is the whole
     * difference between working and nearly working. A building is ordered onto
     * clear ground and then takes many steps to go up; check only what stands
     * and the town lays a street across a plot in that gap, and the building
     * completes in the middle of the road. That is exactly how an animal farm
     * came to sit on an eight-wide carriageway at step 234: the road was laid
     * while the farm was still a task rather than a building.
     *
     * <p>{@code Settlement.isPlotFree} has always counted the queue for the same
     * reason. The two rules have to agree about what "occupied" means, or
     * whichever runs second wins.
     */
    private static boolean crossesAnything(Settlement settlement, PathNetwork.Segment run) {
        for (Building building : settlement.buildings()) {
            if (!BuildPlanner.holdsGround(building.blueprintId())) {
                continue;
            }
            int span = BuildPlanner.plotSpanOf(
                    building.blueprintId(), settlement.catalogue());
            if (run.touches(building.origin(), span / 2.0 + KERB)) {
                return true;
            }
        }
        for (BuildTask queued : settlement.queued()) {
            if (!BuildPlanner.holdsGround(queued.blueprintId())) {
                continue;
            }
            int span = BuildPlanner.plotSpanOf(
                    queued.blueprintId(), settlement.catalogue());
            if (run.touches(queued.origin(), span / 2.0 + KERB)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a stretch passes close enough to anything the town has built. */
    private static boolean nearAny(SimPos where, List<SimPos> standing, int reach) {
        for (SimPos origin : standing) {
            if (Math.max(Math.abs(where.x() - origin.x()),
                         Math.abs(where.z() - origin.z())) <= reach) {
                return true;
            }
        }
        return false;
    }

    private static boolean within(SimPos pos, SimPos centre, int reach) {
        return Math.max(Math.abs(pos.x() - centre.x()),
                        Math.abs(pos.z() - centre.z())) <= reach;
    }

    /** Joins one more building to the network, if any is waiting. */
    public static void advance(Settlement settlement, SimContext ctx) {
        Building hubBuilding = hubBuilding(settlement);
        SimPos hub = hubBuilding != null ? hubBuilding.doorstep() : settlement.centre();
        PathNetwork network = settlement.paths();

        // The streets come first, which is the whole point of planning them.
        layPlannedStreets(settlement, network, ctx);

        for (Building building : settlement.buildings()) {
            if (!building.footprint().isKnown()) {
                continue;   // never measured, so there is no doorstep to aim at
            }
            if (network.hasJoined(building.origin())) {
                continue;
            }
            if (building == hubBuilding) {
                // The hub used to be marked joined and given nothing, on the
                // reasoning that roads radiate FROM it so it needs none. That
                // was true when every road ran to the hub and is false now that
                // the streets are drawn from a plan the hub knows nothing about:
                // a measured town left its camp post twenty-five blocks from the
                // nearest road and its town hall fourteen, which is a town whose
                // two most important doors open onto a field.
                if (!network.isEmpty()) {
                    join(network, building, hub);
                }
                network.markJoined(building.origin());
                return;
            }
            if (join(network, building, hub)) {
                network.markJoined(building.origin());
                return;   // one road a step: a town lays its network as it grows
            }
            continue;   // out of range for now -- try the next, not nobody
        }
        // Every road is planned; what is left is opening them. Where there is a
        // hand there is no clock -- a watched town walks somebody out to each
        // stretch (see PublicWorks.RoadWork) and only an unwatched one has its
        // streets appear, which is what "grew while you were away" has to mean.
        openNextUnwatched(settlement, ctx, network);
    }

    /**
     * Opens one stretch on the clock, for a town nobody is looking at.
     *
     * <p>The same test construction and the wall use, for the same reason: a
     * clock running alongside a builder would open the road twice, and one
     * running instead of a builder standing right there would have a street
     * appear beside somebody doing nothing.
     */
    private static void openNextUnwatched(Settlement settlement, SimContext ctx,
                                          PathNetwork network) {
        List<PathNetwork.Segment> segments = network.segments();
        for (int i = 0; i < segments.size(); i++) {
            if (network.isOpened(i)) {
                continue;
            }
            if (network.isUnwalkable(i)) {
                continue;
            }
            if (unwalkable(segments.get(i), ctx)) {
                // A stair, not a street. Recorded so the builder does not walk
                // out to it either, and left in the network rather than removed:
                // the ground may simply be unread today.
                network.markUnwalkable(i);
                continue;
            }
            SimPos where = segments.get(i).from();
            boolean handsThere = settlement.residents().stream()
                    .anyMatch(person -> settlement.laboursAs(person, Profession.BUILDER)
                            && person.isEmbodied() && !person.isTooWeakToWork());
            if (handsThere && ctx.bridge().isLoaded(where)) {
                return;   // somebody is there to walk it out themselves
            }
            network.markOpened(i);
            return;   // one stretch a step, watched or not
        }
    }

    /**
     * Runs a road from this building's door to whatever it should join.
     *
     * @return false if the route is too long to lay yet
     */
    private static boolean join(PathNetwork network, Building building, SimPos hub) {
        SimPos door = building.doorstep();
        if (door.equals(hub)) {
            SimPos onNetwork = network.nearestPoint(door);
            if (onNetwork == null) {
                return false;
            }
            hub = onNetwork;   // the hub joins the streets, not itself
        }

        // The nearest existing road wins unless the hub itself is closer, which
        // it only is for the first few buildings — after that the network is
        // always the better answer, and that is what makes it branch.
        SimPos target = hub;
        SimPos onNetwork = network.nearestPoint(door);
        if (onNetwork != null
                && onNetwork.horizontalDistanceSq(door) < hub.horizontalDistanceSq(door)) {
            target = onNetwork;
        }

        SimPos corner = cornerFor(door, building.facing(), target);
        int length = Math.abs(corner.x() - door.x()) + Math.abs(corner.z() - door.z())
                + Math.abs(target.x() - corner.x()) + Math.abs(target.z() - corner.z());
        if (length > MAX_ROUTE) {
            return false;
        }
        network.add(new PathNetwork.Segment(door, corner));
        network.add(new PathNetwork.Segment(corner, target));
        return true;
    }

    /**
     * Whether a run of way is too steep to be worth opening.
     *
     * <p>Asked at opening, which is the only moment the ground is reliably
     * known: laying happens long before anybody stands there and the oracle
     * answers from noise. A stretch refused here keeps its place in the network
     * and can be asked again.
     */
    static boolean unwalkable(PathNetwork.Segment run, SimContext ctx) {
        List<SimPos> along = run.positions();
        int last = ctx.bridge().groundHeight(along.get(0));
        for (int i = 1; i < along.size(); i++) {
            int here = ctx.bridge().groundHeight(along.get(i));
            if (Math.abs(here - last) > MAX_ROAD_STEP) {
                return true;
            }
            last = here;
        }
        return false;
    }

    /**
     * Where the road turns.
     *
     * <p>It leaves along the door's own axis when the target lies that way, so
     * you walk straight out of the door and then turn. When the target is
     * behind the door instead, the first run goes sideways along the wall — the
     * alternative is a road that sets off straight back through the building it
     * just left.
     */
    static SimPos cornerFor(SimPos door, int facing, SimPos target) {
        boolean alongZ = facing == 0 || facing == 2;
        int outward = (facing == 0 || facing == 3) ? 1 : -1;

        boolean leaveAlongDoorAxis = alongZ
                ? (target.z() - door.z()) * outward >= 0
                : (target.x() - door.x()) * outward >= 0;

        if (alongZ == leaveAlongDoorAxis) {
            return new SimPos(door.x(), door.y(), target.z());
        }
        return new SimPos(target.x(), door.y(), door.z());
    }

    /**
     * What the roads radiate from: the hall, else the camp post, else nothing —
     * in which case the settlement's own centre stands in.
     */
    private static Building hubBuilding(Settlement settlement) {
        Building campPost = null;
        for (Building building : settlement.buildings()) {
            String id = BuildPlanner.baseIdOf(building.blueprintId());
            if (id.endsWith("town_hall")) {
                return building;
            }
            if (campPost == null && id.endsWith("camp_post")) {
                campPost = building;
            }
        }
        return campPost;
    }
}
