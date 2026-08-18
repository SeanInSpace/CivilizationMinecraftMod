package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;

import java.util.Objects;

/**
 * A patch of ground a trade is allowed to work.
 *
 * <p>Currently only the lumber camp uses one: it marks where lumberjacks may fell
 * and replant, so they clear the woodland you point them at instead of the trees
 * you were keeping. The camp's own block is the control — see
 * {@code LumberCampBlock}.
 */
public record WorkArea(SimPos centre, int radius) {

    public WorkArea {
        Objects.requireNonNull(centre, "centre");
    }

    public boolean contains(SimPos pos) {
        return centre.horizontalDistanceSq(pos) <= (long) radius * radius;
    }

    public WorkArea withCentre(SimPos newCentre) {
        return new WorkArea(newCentre, radius);
    }

    public WorkArea withRadius(int newRadius) {
        return new WorkArea(centre, newRadius);
    }
}
