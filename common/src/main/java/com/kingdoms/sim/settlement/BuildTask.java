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

    /** Sentinel: the construction site has not been surveyed yet. */
    public static final int UNSET_SITE_Y = Integer.MIN_VALUE;

    private final String blueprintId;
    private final SimPos origin;
    private final int requiredWork;
    private int progress;

    /**
     * Visible-construction bookkeeping, persisted so a half-built wall survives a
     * restart. The terrain height is surveyed once when building starts (so the
     * block plan never shifts mid-build), the site is cleared once, and
     * {@code blocksPlaced} tracks how far up the structure the builders have got.
     */
    private int siteY = UNSET_SITE_Y;
    private boolean sitePrepared;
    private int blocksPlaced;

    /** Blocks in this structure's plan, filled in by the view layer once surveyed. */
    private int planSize;

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

    public int siteY() {
        return siteY;
    }

    public void setSiteY(int siteY) {
        this.siteY = siteY;
    }

    public boolean isSitePrepared() {
        return sitePrepared;
    }

    public void setSitePrepared(boolean sitePrepared) {
        this.sitePrepared = sitePrepared;
    }

    public int blocksPlaced() {
        return blocksPlaced;
    }

    public void setBlocksPlaced(int blocksPlaced) {
        this.blocksPlaced = Math.max(0, blocksPlaced);
    }

    public int planSize() {
        return planSize;
    }

    public void setPlanSize(int planSize) {
        this.planSize = Math.max(0, planSize);
    }

    /**
     * Whether builders have already laid every block of this structure by hand.
     *
     * <p>When true, the finished building needs no materialization pass — it is
     * already standing. This is how visible construction avoids being followed by
     * a redundant instant placement that would wipe and re-stamp the work.
     */
    public boolean isVisuallyComplete() {
        return planSize > 0 && blocksPlaced >= planSize;
    }

    /** Where the structure actually stands once surveyed; the planning estimate before. */
    public SimPos site() {
        return siteY == UNSET_SITE_Y ? origin : new SimPos(origin.x(), siteY, origin.z());
    }

    public double completionFraction() {
        return (double) progress / requiredWork;
    }

    @Override
    public String toString() {
        return blueprintId + " @ " + origin + " (" + progress + "/" + requiredWork + ")";
    }
}
