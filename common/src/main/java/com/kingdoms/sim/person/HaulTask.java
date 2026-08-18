package com.kingdoms.sim.person;

import com.kingdoms.sim.geom.SimPos;

import java.util.Objects;

/**
 * A load of goods somebody is carrying from one store to another.
 *
 * <p>Two legs: walk to the source empty-handed, pick the load up, walk it to the
 * destination and set it down. {@link #target()} is wherever they are headed
 * right now, so the view layer and the abstract mover share one destination.
 *
 * <p>Goods are only ever in one place — a farm's store, the granary, a market
 * stall, a family pantry, or on somebody's back. Nothing teleports.
 */
public final class HaulTask {

    /** The kinds of store goods move between. */
    public enum Store {
        FARM,
        GRANARY,
        MARKET,
        HOME
    }

    private final Store fromStore;
    private final SimPos fromPos;
    private final Store toStore;
    private final SimPos toPos;
    private final int requested;

    /** Zero until the load is picked up; the amount on their back afterwards. */
    private int carried;

    public HaulTask(Store fromStore, SimPos fromPos, Store toStore, SimPos toPos, int requested) {
        this.fromStore = Objects.requireNonNull(fromStore, "fromStore");
        this.fromPos = Objects.requireNonNull(fromPos, "fromPos");
        this.toStore = Objects.requireNonNull(toStore, "toStore");
        this.toPos = Objects.requireNonNull(toPos, "toPos");
        this.requested = Math.max(1, requested);
    }

    public Store fromStore() {
        return fromStore;
    }

    public SimPos fromPos() {
        return fromPos;
    }

    public Store toStore() {
        return toStore;
    }

    public SimPos toPos() {
        return toPos;
    }

    public int requested() {
        return requested;
    }

    public int carried() {
        return carried;
    }

    public void setCarried(int carried) {
        this.carried = Math.max(0, carried);
    }

    public boolean isLoaded() {
        return carried > 0;
    }

    /** Where this person is walking right now. */
    public SimPos target() {
        return isLoaded() ? toPos : fromPos;
    }

    @Override
    public String toString() {
        return (isLoaded() ? carried + " from " : "empty to ")
                + fromStore + " -> " + toStore;
    }
}
