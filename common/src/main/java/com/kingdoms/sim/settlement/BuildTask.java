package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;

import java.util.Objects;

/**
 * A building under construction.
 *
 * <p>Progress accrues in the data model regardless of whether the chunk is
 * loaded. The platform layer materializes actual blocks only when someone is
 * present to see them — a settlement that grew while you were away should look
 * grown when you return, not start building on arrival.
 */
public final class BuildTask {

    private final String blueprintId;
    private final SimPos origin;
    private final int requiredWork;
    private int progress;

    public BuildTask(String blueprintId, SimPos origin, int requiredWork) {
        this.blueprintId = Objects.requireNonNull(blueprintId, "blueprintId");
        this.origin = Objects.requireNonNull(origin, "origin");
        if (requiredWork <= 0) {
            throw new IllegalArgumentException("requiredWork must be positive");
        }
        this.requiredWork = requiredWork;
    }

    /** Identifies a datapack-defined blueprint, e.g. {@code "kingdoms:norman/bakery"}. */
    public String blueprintId() {
        return blueprintId;
    }

    public SimPos origin() {
        return origin;
    }

    public int requiredWork() {
        return requiredWork;
    }

    public int progress() {
        return progress;
    }

    public void addProgress(int amount) {
        progress = Math.min(requiredWork, progress + Math.max(0, amount));
    }

    public boolean isComplete() {
        return progress >= requiredWork;
    }

    public double completionFraction() {
        return (double) progress / requiredWork;
    }

    @Override
    public String toString() {
        return blueprintId + " @ " + origin + " (" + progress + "/" + requiredWork + ")";
    }
}
