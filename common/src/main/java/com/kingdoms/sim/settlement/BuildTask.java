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
     * {@code stepsDone} tracks how far down the plan the builders have got.
     */
    private int siteY = UNSET_SITE_Y;
    private boolean sitePrepared;

    /**
     * How far down the plan the builders have got: the index of the step they are
     * working on now. A step is one block dug or one block laid.
     */
    private int stepsDone;

    /**
     * Passes spent on the current step. Digging a block is not instant — a builder
     * swings at it several times before it gives, which is the whole visual
     * difference between excavating a hillside and laying a course on top of it.
     */
    private int stepProgress;

    /** Work units finished, and the plan's total. Digging costs more per step than laying. */
    private int workDone;
    private int planWork;

    /**
     * The placing half of {@link #planWork}, which sets the pace.
     *
     * <p>A building is spread over {@code requiredWork} builder-steps' worth of
     * <em>laying</em>. Excavation is charged on top at the same rate, so a house
     * cut into a hillside genuinely takes longer than the same house on the flat
     * rather than being quietly compressed to fit the catalogue's number.
     */
    private int planPlaceWork;

    /**
     * Work the builders have been cleared to do but have not done yet.
     *
     * <p>Granted a step's worth at a time by the simulation and spent by the view
     * layer as builders actually swing, which is what makes a structure rise
     * smoothly between steps instead of a whole course appearing at once.
     */
    private int pendingWork;

    /**
     * The size the plan turned out to be, recorded when the site is surveyed so
     * the finished building can keep it.
     */
    private Footprint footprint = Footprint.UNKNOWN;

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

    public int stepsDone() {
        return stepsDone;
    }

    public void setStepsDone(int stepsDone) {
        this.stepsDone = Math.max(0, stepsDone);
    }

    public int stepProgress() {
        return stepProgress;
    }

    public void setStepProgress(int stepProgress) {
        this.stepProgress = Math.max(0, stepProgress);
    }

    /** One more swing at the step in hand. */
    public void addStepProgress() {
        stepProgress++;
    }

    public int workDone() {
        return workDone;
    }

    public void setWorkDone(int workDone) {
        this.workDone = Math.max(0, workDone);
    }

    public int planWork() {
        return planWork;
    }

    public int planPlaceWork() {
        return planPlaceWork;
    }

    /** Recorded by the view layer once the site is surveyed and the plan built. */
    public void setPlan(int planWork, int planPlaceWork) {
        this.planWork = Math.max(0, planWork);
        this.planPlaceWork = Math.max(0, planPlaceWork);
    }

    public Footprint footprint() {
        return footprint;
    }

    public void setFootprint(Footprint footprint) {
        this.footprint = footprint == null ? Footprint.UNKNOWN : footprint;
    }

    public int pendingWork() {
        return pendingWork;
    }

    /** Restores the granted-but-unspent figure when a half-built task is read back. */
    public void setPendingWork(int pendingWork) {
        this.pendingWork = Math.max(0, pendingWork);
    }

    /**
     * Clears the builders for up to {@code amount} more work, never more than the
     * plan has left. Without this ceiling a paused build would bank credit and then
     * finish itself in one pass the moment somebody walked back into view.
     */
    public void grantWork(int amount) {
        if (amount <= 0 || planWork <= 0) {
            return;
        }
        int remaining = Math.max(0, planWork - workDone - pendingWork);
        pendingWork += Math.min(amount, remaining);
    }

    /** Records one step finished — a block dug or laid — and spends its cost. */
    public void recordStepDone(int cost) {
        int spent = Math.max(1, cost);
        stepsDone++;
        stepProgress = 0;
        workDone += spent;
        pendingWork = Math.max(0, pendingWork - spent);
        syncProgressToWork();
    }

    /** Whether the builders have been cleared for enough work to finish this step. */
    public boolean canAfford(int cost) {
        return pendingWork >= Math.max(1, cost);
    }

    /**
     * How much work this many builders get through in one simulation step.
     *
     * <p>Paced off the <em>laying</em> half of the plan, so a building still takes
     * {@code requiredWork} builder-steps to raise. Excavation is charged on top at
     * the same rate rather than being folded into that budget — cutting a site out
     * of a hillside is extra work, and should read as extra time.
     */
    public int workForStep(int builders) {
        if (planPlaceWork <= 0 || builders <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil((double) planPlaceWork * builders / requiredWork));
    }

    /**
     * Drags the abstract work figure onto whatever has actually been done.
     *
     * <p>The two numbers measure the same build in different units, and only this
     * keeps them honest — so a task that loses its builders mid-build carries on
     * from where the work really got to.
     */
    public void syncProgressToWork() {
        if (planWork <= 0) {
            return;
        }
        double fraction = Math.min(1.0, (double) workDone / planWork);
        progress = (int) Math.round(requiredWork * fraction);
    }

    /**
     * Whether builders have already laid every block of this structure by hand.
     *
     * <p>When true, the finished building needs no materialization pass — it is
     * already standing. This is how visible construction avoids being followed by
     * a redundant instant placement that would wipe and re-stamp the work.
     */
    public boolean isVisuallyComplete() {
        return planWork > 0 && workDone >= planWork;
    }

    /** Where the structure actually stands once surveyed; the planning estimate before. */
    public SimPos site() {
        return siteY == UNSET_SITE_Y ? origin : new SimPos(origin.x(), siteY, origin.z());
    }

    /**
     * How far along this build is, as a fraction.
     *
     * <p>Once a plan exists this is the share of the work actually done — ground
     * dug and blocks laid, both of which a player can watch happen. Only before
     * the site is surveyed, when there is nothing to look at yet, does it fall
     * back to the abstract work figure.
     */
    public double completionFraction() {
        if (planWork > 0) {
            return Math.min(1.0, (double) workDone / planWork);
        }
        return (double) progress / requiredWork;
    }

    @Override
    public String toString() {
        return blueprintId + " @ " + origin + " (" + progress + "/" + requiredWork + ")";
    }
}
