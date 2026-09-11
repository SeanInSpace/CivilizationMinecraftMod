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
 *   <li><strong>Join the nearest way, not the center.</strong> A new building
 *       branches off whatever road already passes closest — only the first one
 *       runs to the hub — so the network grows outward like a village's does
 *       rather than putting one more spoke through the middle of town.</li>
 *   <li><strong>Right angles.</strong> Every road is axis-aligned and a corner
 *       is two runs, which leaves square ground between them for buildings to
 *       sit against. Diagonal tracks left nothing square to build on.</li>
 *   <li><strong>Out of the door first.</strong> The first run leaves along the
 *       way the door actually faces, then turns. The old layer aimed at a fixed
 *       point due south of every building while the placer rotated three
 *       quarters of them to face the center, so most roads began at a blank
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
     * <p>The same curb {@code Settlement.isPlotFree} keeps, and it has to be.
     * Siting refuses a plot within {@code span/2 + CURB} of a street; this
     * refused to lay a street within {@code span/2} of a plot. One block of
     * disagreement, and it is enough: a street laid exactly on the curb line is
     * ground siting would never have built on, so the survey reports a building
     * in the road that the building never chose. Whichever rule runs second
     * wins, and the two must mean the same thing by "clear".
     */
    private static final int CURB = 1;

    /**
     * How far round its own center a town lays streets before it has anything.
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
     * judgment became.
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

    /**
     * The forester's stand, worked out at most once a pass and only if asked.
     *
     * <p>{@code ForesterStand.stand} walks the camp's grid and asks the town
     * about each square it reaches, which is not a question to put at every
     * step of a search — and most passes through here lay no road at all. So the
     * answer is fetched the first time something actually needs it and held for
     * the rest of the pass, which is exactly as long as it can be trusted: the
     * squares move when the town builds.
     */
    private static final class StandGround {

        private final Settlement town;
        private List<SimPos> spots;

        private StandGround(Settlement town) {
            this.town = town;
        }

        private List<SimPos> spots() {
            if (spots == null) {
                spots = ForesterStand.stand(town);
            }
            return spots;
        }
    }

    static void layPlannedStreets(Settlement settlement, PathNetwork network,
                                  SimContext ctx, StandGround stand) {
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
        SimPos center = settlement.center();
        TownPlan plan = settlement.arrangement().planFor(center, 1);
        com.kingdoms.sim.geom.TerrainSense ground = groundUnder(ctx);
        RoadRouter.Keepout held = heldGround(settlement, plan, stand.spots());

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
                if (!within(middle, center, FIRST_STREETS)
                        && !nearAny(middle, standing, STREET_NEAR)) {
                    continue;   // the town has not grown out to this stretch yet
                }
                // A stretch at a time, not a street at a time.
                //
                // Routing a whole street as one thing sounds tidier and is much
                // worse: a spine runs a thousand blocks, so one ravine anywhere
                // along it condemns the lot. Measured that way, nine streets of
                // twelve were refused and the town fell from sixty-two buildings
                // to thirty-nine -- the roads got better judgment and the town
                // got smaller, which is precisely backwards.
                //
                // Each stretch begins and ends on the drawn line, so neighbors
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
     * point of a planned street carries the town center's y — so an entire
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
     * How far a road's CENTERLINE must stay from a plot.
     *
     * <p>The plot's own half-width, a curb, and — the part that was missing —
     * half the road. A keepout that only holds the centerline out of the plot
     * lets an eight-wide street centered five blocks away pave the garden anyway,
     * and the routed roads promptly did: a farm came back standing on a
     * carriageway that had bent politely around its middle.
     */
    public static int keepoutRound(int span) {
        return span / 2 + CURB + WIDEST_ROAD_HALF;
    }

    /** Half the widest carriageway a plan draws, which the keepout must clear. */
    private static final int WIDEST_ROAD_HALF = 4;

    /**
     * How wide a tree of the stand is held to be: one column, its trunk.
     *
     * <p>Not its crown. A road under a bough is a road through a wood and reads
     * as one; a road through a trunk is a felled tree, and it is the trunk the
     * camp is counting. Measuring the keepout at the crown instead would fence
     * off the whole belt — the stand is planted five blocks apart and a canopy is
     * about that wide, so every square out there would be within reach of one and
     * no lane could leave the village at all.
     */
    private static final int TRUNK_SPAN = 1;

    private static RoadRouter.Keepout heldGround(Settlement settlement, TownPlan plan,
                                                 List<SimPos> stand) {
        // Marked onto the router's own lattice once, rather than asked building
        // by building at every step of the search. A corridor is examined
        // thousands of times and a town has hundreds of claims; a set lookup is
        // the difference between routing a street and stalling a tick.
        java.util.Set<Long> blocked = new java.util.HashSet<>();
        for (Building building : settlement.buildings()) {
            if (BuildPlanner.holdsGround(building.blueprintId())) {
                claim(blocked, building.origin(), keepoutRound(BuildPlanner.plotSpanOf(
                        building.blueprintId(), settlement.catalog())));
            }
        }
        for (BuildTask queued : settlement.queued()) {
            if (BuildPlanner.holdsGround(queued.blueprintId())) {
                claim(blocked, queued.origin(), keepoutRound(BuildPlanner.plotSpanOf(
                        queued.blueprintId(), settlement.catalog())));
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
        // And the forester's stand, which is ground the town has spoken for as
        // surely as any plot. The trees were planted out past the houses because
        // that is the only ground a lumberjack will replant on, and a street
        // routed through the belt afterwards came out having felled them.
        for (SimPos trunk : stand) {
            claim(blocked, trunk, keepoutRound(TRUNK_SPAN));
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

    private static boolean within(SimPos pos, SimPos center, int reach) {
        return Math.max(Math.abs(pos.x() - center.x()),
                        Math.abs(pos.z() - center.z())) <= reach;
    }

    /** Joins one more building to the network, if any is waiting. */
    public static void advance(Settlement settlement, SimContext ctx) {
        Building hubBuilding = hubBuilding(settlement);
        SimPos hub = hubBuilding != null ? hubBuilding.doorstep() : settlement.center();
        PathNetwork network = settlement.paths();
        StandGround stand = new StandGround(settlement);

        // The streets come first, which is the whole point of planning them.
        layPlannedStreets(settlement, network, ctx, stand);

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
                    join(settlement, network, building, hub, stand);
                }
                network.markJoined(building.origin());
                return;
            }
            if (join(settlement, network, building, hub, stand)) {
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
     * Walks out, in one pass, the roads a town that was written into existence
     * has always had.
     *
     * <p>Everything {@link #advance} does a stretch at a time, done at once and
     * exactly once — the plan's streets routed, every standing door joined, and
     * every stretch the ground will take marked opened. Nothing else changes:
     * the same router, the same keepouts, the same refusal for ground too steep
     * to walk.
     *
     * <p><strong>This is not a clock.</strong> The rule it might look like it
     * breaks — where there is a player there is no clock — is about <em>work</em>,
     * and none of this is work. A seeded town's roads were walked out before the
     * world had a first step, in the same sense that its houses were built before
     * it: {@code Founding.seeded} stands the buildings and this stands the
     * streets between them, and both are then <em>drawn</em> when somebody first
     * comes near enough to see them. {@code RoadUpkeep.mayDraw} already keeps
     * that distinction, and it is why an away town's whole network appears at
     * once on your arrival rather than unrolling in front of you.
     *
     * <p>What it does <em>not</em> touch is anything the town builds afterwards.
     * The debt is cleared the moment it is paid, so the first cottage a
     * discovered village raises gets its lane walked out by a builder in front of
     * you, one stretch a step, exactly as a chartered town's does.
     *
     * <p>The joining is run to a fixed point rather than once through. A road
     * longer than {@link #MAX_ROUTE} is refused and tried again as the network
     * spreads toward it, which is the whole reason an outlying farm ever gets a
     * track at all; doing one pass would leave exactly those buildings — the far
     * ones, the ones a village is judged by — standing in a field.
     *
     * <p><strong>Called after the buildings have settled where they stand</strong>
     * — see {@code Settlement.step}, which runs it below {@code materializePending}
     * rather than beside the ordinary pass. A seeded plot carries the town
     * center's height as an estimate and is still allowed to be moved off a
     * river, and a road planned to a door that then moves is a track across a
     * field to nowhere. Measured on the recorded ground, three of fourteen
     * buildings moved on the step they were drawn.
     */
    public static void walkOutSeededRoads(Settlement settlement, SimContext ctx) {
        if (!settlement.seededRoadsOwed()) {
            return;
        }
        settlement.setSeededRoadsOwed(false);
        PathNetwork network = settlement.paths();
        // Whatever this step's ordinary pass managed before the buildings had
        // settled is redone rather than kept. It amounts to one building at
        // most, and one of them matters: advance marks the hub joined the first
        // time it sees it and gives it nothing, because the network is empty --
        // so a hub left marked here is a camp post with no way to its door for
        // as long as the town stands. The segments are left where they are; a
        // road planned twice is the same two runs, and the network drops a
        // repeat.
        StandGround stand = new StandGround(settlement);
        for (Building building : settlement.buildings()) {
            network.forget(building.origin());
        }
        layPlannedStreets(settlement, network, ctx, stand);

        Building hubBuilding = hubBuilding(settlement);
        SimPos hub = hubBuilding != null ? hubBuilding.doorstep() : settlement.center();
        com.kingdoms.sim.geom.TerrainSense ground = groundUnder(ctx);

        boolean joinedAny = true;
        while (joinedAny) {
            joinedAny = false;
            for (Building building : settlement.buildings()) {
                if (building == hubBuilding || !building.footprint().isKnown()
                        || network.hasJoined(building.origin())) {
                    continue;
                }
                if (join(settlement, network, building, hub, stand)) {
                    network.markJoined(building.origin());
                    joinedAny = true;
                }
            }
        }
        // The hub last, and for the reason advance gives: roads no longer
        // radiate from it, so a hub joined while the network was still empty is
        // a town whose most important door opens onto a field.
        if (hubBuilding != null && !network.hasJoined(hubBuilding.origin())) {
            if (!network.isEmpty()) {
                join(settlement, network, hubBuilding, hub, stand);
            }
            network.markJoined(hubBuilding.origin());
        }

        for (int i = 0; i < network.segments().size(); i++) {
            if (network.isOpened(i) || network.isUnwalkable(i)) {
                continue;
            }
            if (unwalkable(network.segments().get(i), ctx)) {
                network.markUnwalkable(i);
                continue;
            }
            network.markOpened(i);
        }
        settlement.logEvent(ctx.step(), "The streets of " + settlement.name()
                + " run " + network.segments().size() + " ways deep");
    }

    /**
     * Opens one stretch on the clock, for a town nobody is looking at.
     *
     * <p>Two refusals, for two different reasons. The crew is coming, so a clock
     * would open the road twice; or a player can see the stretch, in which case
     * there is no clock at all — a street that unrolls itself in front of
     * somebody is magic whether or not the town has anybody to send.
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
            // And whether or not anybody is coming, a street does not pave
            // itself in front of a player. Asked at the stretch rather than at
            // the town, so an outlying lane over the hill is still the clock's
            // to open while the square is watched — and asked after the crew
            // question rather than instead of it, because the two refuse for
            // different reasons and both refusals stand.
            if (ctx.bridge().playerWithin(segments.get(i).positions().getFirst(),
                    ctx.settings().observedRadius())) {
                return;   // watched ground: hands or nothing
            }
            network.markOpened(i);
            return;   // one stretch a step, watched or not
        }
    }

    /**
     * Runs a road from this building's door to whatever it should join.
     *
     * @return false if there is no lane to be had yet — too long, or no line to
     *         any road that does not go through somebody's house
     */
    private static boolean join(Settlement settlement, PathNetwork network,
                                Building building, SimPos hub, StandGround stand) {
        SimPos door = building.doorstep();
        if (door.equals(hub)) {
            SimPos onNetwork = network.nearestPoint(door);
            if (onNetwork == null) {
                return false;
            }
            hub = onNetwork;   // the hub joins the streets, not itself
        }

        Walls walls = wallsOf(settlement, building, stand.spots());

        // The nearest existing road wins unless the hub itself is closer, which
        // it only is for the first few buildings — after that the network is
        // always the better answer, and that is what makes it branch.
        //
        // Nearest is now the first ANSWER rather than the only one. A lane is
        // two straight runs and there is no guarantee the pair that reaches the
        // closest road misses every wall between here and there; when it does
        // not, the next road out is asked, and the one after that. A slightly
        // longer lane round the back of a house is a lane. A short one through
        // the middle of it is what this is for.
        for (SimPos target : targetsFor(network, door, hub)) {
            // Straight out of the door first, and then a block or two further
            // out if the turn will not fit. A lane turns in front of the house
            // it leaves, and "in front of" has to clear the gravel as well as
            // the wall -- turn on the threshold itself and the three-wide track
            // lays a stripe down the front of the building for the whole length
            // of the turn. Walking the corner out is a shorter answer than
            // giving up on the door, and the stem it adds runs along the door's
            // own axis, where nothing of the building can possibly be.
            for (int out = 0; out <= STAND_OFF; out++) {
                SimPos from = stepOut(door, building.facing(), out);
                PathNetwork.Segment stem = new PathNetwork.Segment(door, from);
                if (out > 0 && walls.crossedBy(stem, target)) {
                    break;   // cannot even get clear of the door this way
                }
                List<PathNetwork.Segment> lane =
                        layLane(from, building.facing(), target, walls);
                if (lane != null) {
                    // The stem first, so the network reads the way somebody
                    // walks it: out of the door, out to the turn, and away.
                    if (out > 0) {
                        network.add(stem);
                    }
                    lane.forEach(network::add);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * How far a lane may walk its corner out from the door before giving up.
     *
     * <p>Small on purpose. A door that cannot turn within a few blocks of itself
     * is hemmed in by something, and the answer to that is to wait for the
     * network to spread rather than to march a stem across the town.
     */
    private static final int STAND_OFF = 4;

    /** A point this many blocks straight out from a door, on the door's own axis. */
    private static SimPos stepOut(SimPos door, int facing, int blocks) {
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> new SimPos(door.x() - blocks, door.y(), door.z());
            case 2 -> new SimPos(door.x(), door.y(), door.z() - blocks);
            case 3 -> new SimPos(door.x() + blocks, door.y(), door.z());
            default -> new SimPos(door.x(), door.y(), door.z() + blocks);
        };
    }

    /**
     * Roads this door might join, nearest first.
     *
     * <p>The hub is always in the list and always last: it is the answer of last
     * resort for a building whose every neighboring road is walled off from it,
     * and for the first few buildings in a town it is the only road there is.
     */
    private static List<SimPos> targetsFor(PathNetwork network, SimPos door, SimPos hub) {
        record Reach(SimPos at, long away) { }
        List<Reach> reaches = new java.util.ArrayList<>();
        for (PathNetwork.Segment run : network.segments()) {
            SimPos nearest = run.nearestTo(door);
            long away = nearest.horizontalDistanceSq(door);
            if (away > (long) MAX_ROUTE * MAX_ROUTE) {
                continue;   // nothing on this road is within a lane's reach
            }
            reaches.add(new Reach(nearest, away));
            // And further along the same road, which is the whole difference
            // between a door that joins and one that never does. The point on a
            // road CLOSEST to a door is the one most likely to have the door's
            // own neighbors between it and the door; a stretch of the same road
            // a few blocks along is reached by a lane that goes round them. A
            // seeded village left a farm off its network entirely for want of
            // this, its only near road being on the far side of the market.
            List<SimPos> along = run.positions();
            for (int i = 0; i < along.size(); i += ALONG_A_ROAD) {
                SimPos at = along.get(i);
                reaches.add(new Reach(at, at.horizontalDistanceSq(door)));
            }
            SimPos last = along.get(along.size() - 1);
            reaches.add(new Reach(last, last.horizontalDistanceSq(door)));
        }
        reaches.sort(java.util.Comparator.comparingLong(Reach::away));
        List<SimPos> out = new java.util.ArrayList<>();
        for (Reach reach : reaches) {
            if (!out.contains(reach.at())) {
                out.add(reach.at());
            }
            if (out.size() >= TARGETS_TRIED) {
                break;
            }
        }
        // The hub before the far end of the network rather than after it: a road
        // to the middle of town is a road, and one to the far side of a spur is
        // usually a lane the long way round the whole settlement.
        if (hub.horizontalDistanceSq(door) < (out.isEmpty() ? Long.MAX_VALUE
                : out.get(out.size() - 1).horizontalDistanceSq(door))) {
            out.add(0, hub);
        } else {
            out.add(hub);
        }
        return out;
    }

    /**
     * How many roads a door is offered before it is left for another step.
     *
     * <p>Enough that a house hemmed in on one side has somewhere else to look,
     * few enough that joining one door stays a handful of line tests. A door
     * that all of these refuse is nearly always one whose neighbors have not
     * been built yet, and the network spreads toward it on its own.
     */
    private static final int TARGETS_TRIED = 16;

    /** How often a road offers a door another place to meet it, in blocks. */
    private static final int ALONG_A_ROAD = 4;

    /**
     * Lays the two runs of a lane, if either order of them clears every wall.
     *
     * <p>The right angle, always. Routing the door track through
     * {@link RoadRouter} instead was tried twice — once before the layer could
     * grade a two-block step and once after — and measured worse both times: six
     * stranded doors became nine, then seven. The reason is not the line. Those
     * doors stand on ground where no track under two blocks a step exists at
     * all, so a router has nothing better to find and its longer answer only
     * spends the town's one-stretch-a-step opening budget.
     *
     * <p>What is new is the second order. A lane turns once, and which of its
     * two legs comes first used to be settled entirely by the way the door
     * faces — sound, and it is still the order tried first, because a road you
     * walk straight out of the door onto is what a door is for. But when that
     * order drives the second leg back across the building's own kitchen, or
     * through the neighbor's, the other order is very often clear and was never
     * asked for. Measured: 415 of 972 buildings gravelled by a lane were
     * gravelled by their <em>own</em>.
     *
     * @return the two runs, in walking order, or null if neither order is clear
     */
    private static List<PathNetwork.Segment> layLane(SimPos door, int facing,
                                                     SimPos target, Walls walls) {
        SimPos preferred = cornerFor(door, facing, target);
        SimPos other = preferred.x() == door.x()
                ? new SimPos(target.x(), door.y(), door.z())
                : new SimPos(door.x(), door.y(), target.z());
        for (SimPos corner : List.of(preferred, other)) {
            int length = Math.abs(corner.x() - door.x()) + Math.abs(corner.z() - door.z())
                    + Math.abs(target.x() - corner.x()) + Math.abs(target.z() - corner.z());
            if (length > MAX_ROUTE) {
                continue;
            }
            PathNetwork.Segment out = new PathNetwork.Segment(door, corner);
            PathNetwork.Segment on = new PathNetwork.Segment(corner, target);
            if (walls.crossedBy(out, target) || walls.crossedBy(on, target)) {
                continue;
            }
            return List.of(out, on);
        }
        return null;
    }

    /**
     * Every wall a lane must miss, as a question a run can be asked.
     *
     * <p>Standing buildings and ordered ones both, for the reason the street
     * keepout already gives: a building is ordered onto clear ground and takes
     * many steps to go up, and a lane run through that gap completes underneath
     * it.
     *
     * <p>Walls rather than plots, deliberately. A plot is a claim with an apron
     * round it and the apron is the <em>doorstep</em> — a lane that ends on one
     * is a lane doing its job, and a keepout drawn at the plot would leave every
     * door in a close-built town unreachable. So the line is the blocks
     * themselves, plus the block either side that the layer actually gravels.
     *
     * <p>The building being joined is held to the same line as its neighbors,
     * with one thing excused: the way straight out of its own door. A door
     * stands off the wall it is cut into, and the gravel laid on the threshold
     * and on the few blocks leading away from it necessarily laps that wall —
     * which is a doorstep and not a road through a kitchen. Everything else is
     * held off, and that is the case that was actually going wrong: a lane that
     * left the door, turned, and came back down the length of the building's own
     * side wall, gravelling it end to end.
     *
     * <p>Its own door, and nobody else's. Excusing the threshold against every
     * wall in the settlement was tried and is the last six buildings this fault
     * had left: a house standing tight against the market got its doorstep, and
     * the gravel of that doorstep went down the market's side wall.
     */
    private static final class Walls {

        private record Claim(SimPos at, int halfX, int halfZ) {

            boolean covers(int x, int z) {
                return Math.abs(x - at.x()) <= halfX && Math.abs(z - at.z()) <= halfZ;
            }
        }

        private final List<Claim> claims = new java.util.ArrayList<>();
        private Claim own;
        private SimPos doorstep;
        private int facing;

        void add(SimPos at, int[] half, int margin) {
            claims.add(new Claim(at, half[0] + margin, half[1] + margin));
        }

        /** The walls of the building whose door this lane is for, and its door. */
        void ownedBy(SimPos at, int[] half, int margin, SimPos door, int faces) {
            own = new Claim(at, half[0] + margin, half[1] + margin);
            doorstep = door;
            facing = faces;
        }

        /**
         * Whether this column is on the way straight out of the door.
         *
         * <p>The threshold and the line leading away from it, on the door's own
         * axis. It cannot cross the building it belongs to — it points away from
         * it — so excusing it excuses a doorstep and nothing else, and without it
         * a building whose door sits inside its own keepout could never be
         * joined to anything at all.
         */
        private boolean onTheWayOut(int x, int z) {
            if (doorstep == null) {
                return false;
            }
            return switch (facing) {
                case 1 -> z == doorstep.z() && x <= doorstep.x();
                case 2 -> x == doorstep.x() && z <= doorstep.z();
                case 3 -> z == doorstep.z() && x >= doorstep.x();
                default -> x == doorstep.x() && z >= doorstep.z();
            };
        }

        /**
         * Whether any column this run would gravel stands inside somebody's walls.
         *
         * @param arriving where the lane is going, which it is always allowed to
         *                 reach: either a point on a road, which stands in
         *                 nobody's walls by construction, or the hub's own
         *                 threshold, which is a door and is what a road is for
         */
        boolean crossedBy(PathNetwork.Segment run, SimPos arriving) {
            for (SimPos at : run.positions()) {
                if (at.x() == arriving.x() && at.z() == arriving.z()) {
                    continue;
                }
                for (Claim claim : claims) {
                    if (claim.covers(at.x(), at.z())) {
                        return true;
                    }
                }
                if (own != null && own.covers(at.x(), at.z())
                        && !onTheWayOut(at.x(), at.z())) {
                    return true;   // its own wall, anywhere but out of its door
                }
            }
            return false;
        }
    }

    /** How far outside a wall a lane's own gravel reaches; see Segment.paveHalf. */
    private static final int LANE_PAVE_HALF =
            new PathNetwork.Segment(new SimPos(0, 0, 0), new SimPos(1, 0, 0)).paveHalf();

    private static Walls wallsOf(Settlement settlement, Building joining,
                                 List<SimPos> stand) {
        Walls walls = new Walls();
        // A trunk of the forester's stand, held off exactly as a wall is: the
        // lane may pass under its branches and may not gravel the column it
        // grows out of. A lane is the case the street keepout never covered —
        // the streets are drawn from a plan and stop at the village edge, and
        // what actually runs out into the belt is the track to the last farm.
        for (SimPos trunk : stand) {
            walls.add(trunk, new int[] {TRUNK_SPAN / 2, TRUNK_SPAN / 2}, LANE_PAVE_HALF);
        }
        for (Building building : settlement.buildings()) {
            if (!BuildPlanner.holdsGround(building.blueprintId())) {
                continue;
            }
            int[] half = BuildPlanner.wallsHalfOf(building.blueprintId(),
                    building.facing(), settlement.catalog());
            if (building == joining) {
                walls.ownedBy(building.origin(), half, LANE_PAVE_HALF,
                        building.doorstep(), building.facing());
            } else {
                walls.add(building.origin(), half, LANE_PAVE_HALF);
            }
        }
        for (BuildTask queued : settlement.queued()) {
            if (!BuildPlanner.holdsGround(queued.blueprintId())) {
                continue;
            }
            walls.add(queued.origin(),
                    BuildPlanner.wallsHalfOf(queued.blueprintId(), queued.facing(),
                            settlement.catalog()),
                    LANE_PAVE_HALF);   // never drawn, so the catalog is all there is
        }
        return walls;
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
     * in which case the settlement's own center stands in.
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
