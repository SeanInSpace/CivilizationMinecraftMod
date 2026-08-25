package com.kingdoms.sim.settlement;

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

    /** Joins one more building to the network, if any is waiting. */
    public static void advance(Settlement settlement, SimContext ctx) {
        Building hubBuilding = hubBuilding(settlement);
        SimPos hub = hubBuilding != null ? hubBuilding.doorstep() : settlement.centre();
        PathNetwork network = settlement.paths();

        for (Building building : settlement.buildings()) {
            if (!building.footprint().isKnown()) {
                continue;   // never measured, so there is no doorstep to aim at
            }
            if (network.hasJoined(building.origin())) {
                continue;
            }
            if (building == hubBuilding) {
                network.markJoined(building.origin());
                return;
            }
            if (join(network, building, hub)) {
                network.markJoined(building.origin());
            }
            return;   // one road a step: a town lays its network as it grows
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
