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

    /** Work units finished, and the plan's total. One unit is one block, either way. */
    private int workDone;
    private int planWork;

    /**
     * The excavation half of {@link #workDone}.
     *
     * <p>Tracked apart from the rest because the two are paced by completely
     * different clocks. Masonry is rationed a step at a time by
     * {@link #grantWork}; digging is not rationed at all, because a block takes
     * as long as its hardness says it takes and no budget should be able to
     * hurry it or hold it up. Keeping the figures separate is what lets progress
     * count both while only one of them draws on the budget.
     */
    private int digDone;

    /**
     * The placing half of {@link #planWork}, which sets the pace.
     *
     * <p>A building is spread over {@code requiredWork} builder-steps' worth of
     * <em>laying</em>. Excavation is charged on top at the same rate, so a house
     * cut into a hillside genuinely takes longer than the same house on the flat
     * rather than being quietly compressed to fit the catalog's number.
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

    /** Quarter turns clockwise from the drawn orientation; see {@code BuildPlanner.facingToward}. */
    private int facing;

    /**
     * The building this replaces, if it is an improvement rather than a new one.
     *
     * <p>Null for ordinary construction. When set, finishing raises that building's
     * level in place instead of recording another one — and the excavation clears
     * the old walls on the way, because they stand inside the new plan's footprint.
     */
    private SimPos upgradeOf;

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

    /**
     * Steps this build has been watched without a block actually going down.
     *
     * <p>Not persisted: it is a measure of what is happening right now, and a
     * reload has by definition interrupted whatever that was.
     */
    private int idleWatchedSteps;

    /** A block went down by hand. */
    public void touchVisibleProgress() {
        idleWatchedSteps = 0;
    }

    /**
     * A watched step passed with nothing laid.
     *
     * @return how many such steps have run together
     */
    public int noteWatchedIdleStep() {
        return ++idleWatchedSteps;
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

    public SimPos upgradeOf() {
        return upgradeOf;
    }

    public void setUpgradeOf(SimPos upgradeOf) {
        this.upgradeOf = upgradeOf;
    }

    public boolean isUpgrade() {
        return upgradeOf != null;
    }

    /**
     * Whether this job puts a standing building back the way it was.
     *
     * <p>Set alongside {@link #upgradeOf} rather than instead of it: both are work
     * booked against a building already on the books, and the completion path
     * needs to know that either way. What this adds is the one thing that
     * separates them. An upgrade replaces a building with a bigger one and takes
     * the old walls out on the way, because they stand inside the new plan's
     * footprint; a repair is the missing blocks and nothing else. A repair that
     * could not say so was handed a plan for the whole structure with the whole
     * structure marked for excavation — a crew sent to mend a roof would begin by
     * pulling the cottage down around itself.
     */
    private boolean repair;

    public boolean isRepair() {
        return repair;
    }

    public void setRepair(boolean repair) {
        this.repair = repair;
    }

    public int facing() {
        return facing;
    }

    public void setFacing(int facing) {
        this.facing = Math.floorMod(facing, 4);
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
        if (amount <= 0 || planPlaceWork <= 0) {
            return;
        }
        // Against the masonry only. Capping against the whole plan meant every
        // block of ground taken out shrank the allowance for the walls, so a
        // building on a hillside ran out of budget with courses still to lay.
        int remaining = Math.max(0, planPlaceWork - placeWorkDone() - pendingWork);
        pendingWork += Math.min(amount, remaining);
    }

    /** Work units of actual masonry done, as opposed to ground shifted. */
    public int placeWorkDone() {
        return Math.max(0, workDone - digDone);
    }

    public int digDone() {
        return digDone;
    }

    public void setDigDone(int digDone) {
        this.digDone = Math.max(0, digDone);
    }

    /** Records blocks taken out of the ground. Costs nothing but time. */
    public void recordDigDone(int blocks) {
        int dug = Math.max(0, blocks);
        digDone += dug;
        workDone += dug;
        syncProgressToWork();
    }

    /**
     * Squares the books when the hole is finished.
     *
     * <p>The plan counts the blocks that stood in the way when the site was
     * surveyed. Fewer may actually be dug — a player clears part of it, a tree
     * burns down, gravel slides away — and without this the difference would sit
     * in {@code workDone} forever and the building would never read as finished.
     */
    public void creditExcavation() {
        int digTotal = Math.max(0, planWork - planPlaceWork);
        if (digDone >= digTotal) {
            return;
        }
        workDone += digTotal - digDone;
        digDone = digTotal;
        syncProgressToWork();
    }

    /** Records one step finished — a block dug or laid — and spends its cost. */
    public void recordStepDone(int cost) {
        int spent = Math.max(1, cost);
        // A block going down is the only proof that hands are working. Granted
        // work is not: the simulation goes on clearing a crew that never lays
        // anything, which is exactly the state this distinguishes.
        touchVisibleProgress();
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
     * {@code requiredWork} builder-steps to raise. Excavation is not in this
     * budget at all: it runs on real block-break times, so cutting a site out of
     * a hillside costs whatever the hillside costs and the masonry allowance is
     * untouched by it.
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
