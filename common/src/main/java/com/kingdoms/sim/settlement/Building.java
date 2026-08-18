package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;

import java.util.Objects;

/**
 * A finished building. This is the settlement's memory of what it has built.
 *
 * <p>The simulation is the authority on what exists, not the blocks in the world.
 * A building is recorded here the moment its {@link BuildTask} completes, whether
 * or not any chunk was loaded at the time — and {@link #materialized} tracks
 * separately whether it has since been drawn into the world as actual blocks.
 *
 * <p>That split is what makes "the settlement grew while you were away" work: the
 * building exists in data immediately, and gets painted in later, once someone is
 * around to see it.
 */
public final class Building {

    private final String blueprintId;
    private final SimPos origin;
    private final long completedOnStep;
    private boolean materialized;

    /** Food held at this building — harvest waiting at a farm, stock at a market. */
    private int foodStored;

    public Building(String blueprintId, SimPos origin, long completedOnStep) {
        this(blueprintId, origin, completedOnStep, false);
    }

    public Building(String blueprintId, SimPos origin, long completedOnStep, boolean materialized) {
        this.blueprintId = Objects.requireNonNull(blueprintId, "blueprintId");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.completedOnStep = completedOnStep;
        this.materialized = materialized;
    }

    public String blueprintId() {
        return blueprintId;
    }

    public SimPos origin() {
        return origin;
    }

    /** Which simulation step this was finished on. Useful for debugging and for "built N steps ago". */
    public long completedOnStep() {
        return completedOnStep;
    }

    /** Whether this has been drawn into the world as blocks yet. */
    public boolean isMaterialized() {
        return materialized;
    }

    public void setMaterialized(boolean materialized) {
        this.materialized = materialized;
    }

    public int foodStored() {
        return foodStored;
    }

    public void setFoodStored(int foodStored) {
        this.foodStored = Math.max(0, foodStored);
    }

    @Override
    public String toString() {
        return blueprintId + " @ " + origin + (materialized ? "" : " (pending)");
    }
}
