package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The settlement's defensive ring: an ordered loop of vertices, the gates cut
 * into it, and how much of it has been raised so far.
 *
 * <p>This is the interface the whole perimeter subsystem works through, and it
 * is deliberately shape-agnostic: v1 stakes an axis-aligned rectangle around
 * the buildings ({@link PerimeterPlanner}), and the concave α-shape wall the
 * GOALS describe replaces only the staking — the walk order, the gates, the
 * laying cursor and the patrol all read the same loop either way.
 *
 * <p>The vertices double as the sentry's patrol nodes; the positions along the
 * loop are what the palisade layer stamps into the world.
 */
public final class Perimeter {

    /** Blocks of clear opening cut around each gate's centre. */
    public static final int GATE_HALF_WIDTH = 1;

    private final List<SimPos> vertices;
    private List<SimPos> gates;
    private int laid;

    public Perimeter(List<SimPos> vertices, List<SimPos> gates, int laid) {
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("a perimeter is a loop, not a line");
        }
        this.vertices = List.copyOf(vertices);
        this.gates = List.copyOf(gates);
        this.laid = Math.max(0, laid);
    }

    /** The loop's corners in walk order. Patrol nodes, verbatim. */
    public List<SimPos> vertices() {
        return vertices;
    }

    /** Openings in the ring — where the ways in and out are. */
    public List<SimPos> gates() {
        return gates;
    }

    /**
     * Moves the gates, which is only allowed while the wall is still going up.
     *
     * <p>A ring is staked before most of the town's roads exist, so the gates
     * it starts with are a guess. They are re-sited as the streets appear and
     * fixed the moment the wall closes — after that the posts are in the ground
     * and a gate is where it is.
     */
    public void setGates(List<SimPos> gates) {
        if (!closed()) {
            this.gates = List.copyOf(gates);
        }
    }

    /** Ring positions raised so far, counted along {@link #ringPositions()}. */
    public int laid() {
        return laid;
    }

    public void setLaid(int laid) {
        this.laid = Math.max(0, Math.min(laid, length()));
    }

    /** Whether the ring is complete. */
    public boolean closed() {
        return laid >= length();
    }

    /** Total positions on the loop, gates included (they are laid as openings). */
    public int length() {
        return ringPositions().size();
    }

    /**
     * Every position on the loop, in walk order, corner to corner. Sides are
     * walked axis-aligned; a diagonal between vertices steps x first, then z,
     * which keeps the v1 rectangle exact and degrades gracefully for any
     * polygon a later planner might stake.
     */
    public List<SimPos> ringPositions() {
        List<SimPos> ring = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            SimPos from = vertices.get(i);
            SimPos to = vertices.get((i + 1) % vertices.size());
            int x = from.x();
            int z = from.z();
            while (x != to.x()) {
                ring.add(new SimPos(x, from.y(), z));
                x += Integer.signum(to.x() - x);
            }
            while (z != to.z()) {
                ring.add(new SimPos(x, from.y(), z));
                z += Integer.signum(to.z() - z);
            }
        }
        return ring;
    }

    /** Whether this ring position is inside a gate's opening. */
    public boolean isGateway(SimPos pos) {
        for (SimPos gate : gates) {
            if (Math.abs(pos.x() - gate.x()) <= GATE_HALF_WIDTH
                    && Math.abs(pos.z() - gate.z()) <= GATE_HALF_WIDTH) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Perimeter other
                && vertices.equals(other.vertices)
                && gates.equals(other.gates)
                && laid == other.laid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(vertices, gates, laid);
    }
}
