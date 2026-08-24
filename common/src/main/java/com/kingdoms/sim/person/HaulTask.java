package com.kingdoms.sim.person;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.TownStores;

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
        HOME,
        /** A building's own bulk shelves — timber, stone, saplings, iron. */
        STORE
    }

    /**
     * What is being carried.
     *
     * <p>Every errand was food once, and the food economy still names its stops
     * by kind — farm, granary, stall, pantry. Bulk goods move between buildings'
     * own shelves instead, so the resource is what decides which set of books
     * the load comes out of and goes into.
     */
    private final String resource;

    private final Store fromStore;
    private final SimPos fromPos;
    private final Store toStore;
    private final SimPos toPos;
    private final int requested;

    /** Zero until the load is picked up; the amount on their back afterwards. */
    private int carried;

    /** A food errand, which is what every errand used to be. */
    public HaulTask(Store fromStore, SimPos fromPos, Store toStore, SimPos toPos, int requested) {
        this(TownStores.FOOD, fromStore, fromPos, toStore, toPos, requested);
    }

    public HaulTask(String resource, Store fromStore, SimPos fromPos,
                    Store toStore, SimPos toPos, int requested) {
        this.resource = Objects.requireNonNull(resource, "resource");
        this.fromStore = Objects.requireNonNull(fromStore, "fromStore");
        this.fromPos = Objects.requireNonNull(fromPos, "fromPos");
        this.toStore = Objects.requireNonNull(toStore, "toStore");
        this.toPos = Objects.requireNonNull(toPos, "toPos");
        this.requested = Math.max(1, requested);
    }

    public String resource() {
        return resource;
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

    /**
     * Steps this errand has dragged on while its carrier was embodied.
     *
     * <p>The stall detector. An embodied hauler must genuinely walk, and mob
     * navigation cannot climb everything a town gets built on — so an errand
     * that has taken far longer than the clock would have is completed by the
     * clock instead. Not persisted: after a reload the walk just gets another
     * fair chance first.
     */
    private int stalledSteps;

    public int addStalledStep() {
        return ++stalledSteps;
    }

    public void resetStalled() {
        stalledSteps = 0;
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
