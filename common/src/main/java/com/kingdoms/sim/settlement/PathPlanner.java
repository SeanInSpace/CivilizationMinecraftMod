package com.kingdoms.sim.settlement;

import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.work.PublicWorks;
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
     * The most a way may climb between blocks and still be worth opening.
     *
     * <p>Two rather than one, because the paving layer earns the difference: a
     * two-block rise is one spadeful from being two one-block steps, and that is
     * what a road crew does with it. Refusing them instead was measured and it
     * is the wrong trade — the network shrank, doors were stranded from roads
     * that were merely a little steep, and the town got worse the stricter its
     * judgement became.
     *
     * <p>Three is still refused, here and at the layer, because no single block
     * moved makes it walkable and a crew that moved more would be terracing.
     */
    private static final int GRADABLE_ROAD_STEP = 2;

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
            if (Math.abs(here - last) > GRADABLE_ROAD_STEP) {
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
        TownPlan plan = settlement.arrangement().planFor(centre, 1);
        com.kingdoms.sim.geom.TerrainSense ground = groundUnder(ctx);
        RoadRouter.Keepout held = heldGround(settlement, plan);

        for (int i = 0; i < plan.streets().size(); i++) {
            TownPlan.Street street = plan.streets().get(i);
            List<SimPos> drawn = street.path();
            for (int piece = 1; piece < drawn.size(); piece++) {
                int key = pieceKey(i, piece);
                if (network.isStreetSettled(key)) {
                    continue;
                }
                SimPos from = drawn.get(piece - 1);
                SimPos to = drawn.get(piece);
                SimPos middle = new SimPos((from.x() + to.x()) / 2, from.y(),
                        (from.z() + to.z()) / 2);
                if (!within(middle, centre, FIRST_STREETS)
                        && !nearAny(middle, standing, STREET_NEAR)) {
                    continue;   // the town has not grown out to this stretch yet
                }
                // A stretch at a time, not a street at a time.
                //
                // Routing a whole street as one thing sounds tidier and is much
                // worse: a spine runs a thousand blocks, so one ravine anywhere
                // along it condemns the lot. Measured that way, nine streets of
                // twelve were refused and the town fell from sixty-two buildings
                // to thirty-nine -- the roads got better judgement and the town
                // got smaller, which is precisely backwards.
                //
                // Each stretch begins and ends on the drawn line, so neighbours
                // meet exactly whatever either of them did in between, and a
                // cliff costs the town one stretch instead of one street.
                List<SimPos> routed = RoadRouter.route(List.of(from, to), ground, held);
                if (routed == null) {
                    network.markStreetRefused(key);
                    continue;
                }
                for (int step = 1; step < routed.size(); step++) {
                    network.add(new PathNetwork.Segment(
                            routed.get(step - 1), routed.get(step), street.width()));
                }
                network.markStreetRouted(key);
            }
        }
        network.setStreetsLaidFor(standing.size());
    }

    /**
     * One stretch of one street, as a single number the network can remember.
     *
     * <p>Stretches rather than streets, because that is the unit a town builds
     * and gives up in. Four thousand and ninety-six of them to a street is far
     * more than any plan draws.
     */
    private static int pieceKey(int street, int piece) {
        return street * PIECES_TO_A_STREET + piece;
    }

    /** Which street a remembered stretch belongs to. */
    static int streetOfPiece(int key) {
        return key / PIECES_TO_A_STREET;
    }

    private static final int PIECES_TO_A_STREET = 4096;

    /**
     * The ground, as the router wants to be asked about it.
     *
     * <p>{@code groundHeight} rather than {@code surfaceHeight}: the second
     * hands back the caller's own y for a column nobody has loaded, and every
     * point of a planned street carries the town centre's y — so an entire
     * hillside reads as a table top and a router asking that question would
     * cheerfully route across a cliff.
     */
    private static com.kingdoms.sim.geom.TerrainSense groundUnder(SimContext ctx) {
        return new com.kingdoms.sim.geom.TerrainSense() {
            @Override
            public int heightAt(int x, int z) {
                return ctx.bridge().groundHeight(new SimPos(x, 0, z));
            }

            @Override
            public boolean wetAt(int x, int z) {
                return ctx.bridge().standsInWater(new SimPos(x, 0, z), 0);
            }
        };
    }

    /**
     * Ground a road may not have: everything standing, and everything ordered.
     *
     * <p>The queue counts, as it does everywhere else that asks this question. A
     * building is ordered onto clear ground and takes many steps to go up, and a
     * road routed through that gap completes underneath it.
     */
    /**
     * How far a road's CENTRELINE must stay from a plot.
     *
     * <p>The plot's own half-width, a kerb, and — the part that was missing —
     * half the road. A keepout that only holds the centreline out of the plot
     * lets an eight-wide street centred five blocks away pave the garden anyway,
     * and the routed roads promptly did: a farm came back standing on a
     * carriageway that had bent politely around its middle.
     */
    public static int keepoutRound(int span) {
        return span / 2 + KERB + WIDEST_ROAD_HALF;
    }

    /** Half the widest carriageway a plan draws, which the keepout must clear. */
    private static final int WIDEST_ROAD_HALF = 4;

    private static RoadRouter.Keepout heldGround(Settlement settlement, TownPlan plan) {
        // Marked onto the router's own lattice once, rather than asked building
        // by building at every step of the search. A corridor is examined
        // thousands of times and a town has hundreds of claims; a set lookup is
        // the difference between routing a street and stalling a tick.
        java.util.Set<Long> blocked = new java.util.HashSet<>();
        for (Building building : settlement.buildings()) {
            if (BuildPlanner.holdsGround(building.blueprintId())) {
                claim(blocked, building.origin(), keepoutRound(BuildPlanner.plotSpanOf(
                        building.blueprintId(), settlement.catalogue())));
            }
        }
        for (BuildTask queued : settlement.queued()) {
            if (BuildPlanner.holdsGround(queued.blueprintId())) {
                claim(blocked, queued.origin(), keepoutRound(BuildPlanner.plotSpanOf(
                        queued.blueprintId(), settlement.catalogue())));
            }
        }
        // And every plot the plan MIGHT still use, not only the ones standing.
        //
        // Ordering was the hole. A road routed politely around the houses that
        // existed, and a house raised afterwards on the plot the plan had always
        // meant for it found the road already bent across its garden. Siting
        // refuses such ground and simply built elsewhere, so the town lost the
        // plot and kept the bad road. The plan's plots do not move, so the road
        // can be kept off all of them from the start.
        for (TownPlan.Plot plot : plan.plots()) {
            claim(blocked, plot.at(), keepoutRound(plot.span()));
        }
        return (x, z) -> blocked.contains(cell(x, z));
    }

    /** Marks every lattice cell within reach of a claim. */
    private static void claim(java.util.Set<Long> blocked, SimPos at, int reach) {
        int grain = RoadRouter.GRAIN;
        for (int dx = -reach - grain; dx <= reach + grain; dx += grain) {
            for (int dz = -reach - grain; dz <= reach + grain; dz += grain) {
                int x = at.x() + dx;
                int z = at.z() + dz;
                if (Math.abs(x - at.x()) <= reach && Math.abs(z - at.z()) <= reach) {
                    blocked.add(cell(x, z));
                }
            }
        }
    }

    /** A lattice cell, rounded the way the router rounds. */
    private static long cell(int x, int z) {
        int grain = RoadRouter.GRAIN;
        long cx = x - Math.floorMod(x, grain);
        long cz = z - Math.floorMod(z, grain);
        return (cx << 32) ^ (cz & 0xFFFFFFFFL);
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
                    join(network, building, hub, groundUnder(ctx));
                }
                network.markJoined(building.origin());
                return;
            }
            if (join(network, building, hub, groundUnder(ctx))) {
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
            // Hands on the ROADS, or hands that are coming back to them. The
            // question used to be "is anybody here a builder", which was the
            // same question while the roads were the first work a crew was
            // offered. They are below the wall now, so a town with a ring still
            // going up has builders who will not reach the streets for as long
            // as the wall takes — and a clock that stood aside for them would
            // leave a street opened by nobody at all. A builder raising a house
            // is a different case and still stands the clock down: a build queue
            // is finite and they are on their way. See
            // PublicWorks.leaveItToTheCrew.
            if (PublicWorks.leaveItToTheCrew(settlement, ctx.bridge(),
                    new PublicWorks.RoadWork())) {
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
    private static boolean join(PathNetwork network, Building building, SimPos hub,
                                com.kingdoms.sim.geom.TerrainSense ground) {
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
        // The right angle, always. Routing the door track instead was tried
        // twice -- once before the layer could grade a two-block step and once
        // after -- and measured worse both times: six stranded doors became nine,
        // then seven. The reason is not the line. Those doors stand on ground
        // where no track under two blocks a step exists at all, so a router has
        // nothing better to find and its longer answer only spends the town's
        // one-stretch-a-step opening budget.
        //
        // What would actually reach them is levelling the ground they stand on,
        // which is what a town does when it builds somewhere awkward. That is
        // the plot terraforming, not the road.
        network.add(new PathNetwork.Segment(door, corner));
        network.add(new PathNetwork.Segment(corner, target));
        return true;
    }

    /**
     * Whether a run of way is too steep to be worth opening.
     *
     * <p>Asked at opening, which is the moment the ground is most likely to be
     * known: laying is decided long before anybody stands there. A stretch
     * refused here keeps its place in the network and can be asked again, so
     * ground that was merely unread today gets another hearing.
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
