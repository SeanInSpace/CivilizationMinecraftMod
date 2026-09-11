package com.civilization.sim.settlement;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;

/**
 * The wood a lumber camp actually stands in, as a number the clock can work.
 *
 * <p>Timber used to be the second thing a town could conjure. A yield table said
 * what percentage of an imagined felling to credit, and at the shipped zero an
 * unwatched camp brought in nothing at all — so a town nobody visited jammed its
 * build queue on a cottage it had no timber for and never raised another
 * building as long as it stood. {@code Field} took that knob away from food; this
 * takes it away from timber, and for the same reason. A stand is not an
 * abstraction with a dial on it. It is a countable number of trunks inside the
 * camp's {@link WorkArea}, felled by however many lumberjacks the town actually
 * has, growing back out of the saplings they put in the ground.
 *
 * <p>Every camp carries the ledger: how much timber is standing, and how much is
 * coming up. Two things advance it — {@link #grow}, the saplings maturing, and
 * {@link #plant}, a sapling going in — and one thing spends it, {@link #fell}. It
 * is the same ledger whether a player is watching or not. Where somebody is
 * watching, {@code LumberjackWorker}'s real axe does the felling and debits it
 * through {@link #fell}, its real planting credits it through {@link #plant},
 * and the clock credits nothing at all.
 *
 * <p><strong>The trees are their own truth.</strong> A field has to be told what
 * the ledger says when a player walks up to it, because a wheat block's age is
 * invisible bookkeeping; a tree either stands or it does not. So the flip runs
 * the other way here: on the step somebody arrives the camp <em>re-counts</em>
 * the real trunks and takes the world's number — see {@link #recount}.
 *
 * <p>Held in thousandths of a log, for the reason {@code Field} is held in
 * hundredths of a block: a sapling matures over two hundred and forty steps, and
 * a ledger that could only count whole logs would round that to nothing forever.
 */
public final class Stand {

    /**
     * Logs in one tree: <strong>six</strong>.
     *
     * <p>A vanilla oak, averaged, because oak is what a temperate camp is
     * standing in and what {@code LumberjackWorker} replants. An ordinary oak
     * generates with a trunk of four, five or six logs, evenly — five on
     * average. About one oak in ten comes up as a fancy oak instead, which
     * carries its trunk plus branches, twenty-odd logs. Nine ordinary at five
     * and one fancy at twenty is six and a half; six is that, rounded to the
     * side that does not flatter the camp.
     *
     * <p>Species differences are ignored on purpose. A spruce is taller and a
     * birch is not, and a camp in a taiga therefore does slightly better than
     * this says. Modelling that means the ledger has to know what the biome
     * grows, which is a world question, and the answer would move the town's
     * timber by a fifth at most.
     */
    public static final int LOGS_PER_TREE = 6;

    /** Thousandths of a log, the unit the ledger is kept in. */
    public static final int PER_LOG = 1000;

    /** What one standing tree is worth in the ledger's own unit. */
    public static final int PER_TREE = LOGS_PER_TREE * PER_LOG;

    /**
     * A ledger nobody has ever counted.
     *
     * <p>Not zero, and the difference is the whole of it. Zero means "somebody
     * looked and there is nothing standing"; this means "no chunk under this
     * camp has ever been loaded while anybody asked". A camp founded in a wood
     * and saved before a player walked back to it must not wake up believing its
     * trees were felled — see {@code LumberPlanner.reckonStand}, which counts the
     * moment the ground can answer and not before.
     */
    public static final int UNCOUNTED = -1;

    /**
     * Trees a camp is credited with when the ground cannot be counted: 72.
     *
     * <p>Every town in the world is unwatched nearly all of the time, and most
     * of them are unloaded as well — so if "nobody can look" meant "no trees",
     * the honest ledger would have quietly reinstated the very fault it was
     * written to fix. A camp is sited for its woodedness, so the town chose to
     * put it where the trees are, and this is the stand that choice implies.
     *
     * <p>The number, so it can be argued with: a default claim is 24 blocks out,
     * which is about 1,800 squares of ground, and a tree at
     * {@link ForesterStand#SPACING} takes one square in twenty-five — so a claim
     * grown full holds seventy-two of them. Full is the right guess and not a
     * generous one: a camp raised on unsurveyed ground is a camp on ground
     * nobody has ever cut, and it is the only felling in the world that has
     * never happened there.
     *
     * <p>It is a placeholder and never a gift: the moment the camp's chunks load
     * the ground is counted for real and the guess is thrown away, whether that
     * means a hundred trees or none.
     */
    public static final int UNSURVEYED = 72;

    /**
     * Game ticks for a planted sapling to come up a tree: 24,000 — one in-game
     * day, on average.
     *
     * <p>The derivation, so the number can be argued with:
     * <ul>
     *   <li>A sapling only grows when it gets a random tick. Java Edition rolls
     *       three random ticks per 16×16×16 section per game tick, so any one
     *       block is picked with probability 3/4096 — one random tick per 1,365
     *       game ticks on average.</li>
     *   <li>On each random tick a sapling advances with probability 1/7, and it
     *       takes two advances to become a tree: the first sets its growth
     *       stage, the second grows the tree. That is fourteen random ticks on
     *       average, or about 19,000 ticks.</li>
     *   <li>Nothing advances in the dark — a sapling needs light 9 — and a
     *       Minecraft day is only half daylight, so a stand out of lantern-light
     *       spends a good part of the clock waiting for morning.</li>
     * </ul>
     * Nineteen thousand ticks of pure growing plus the nights it sleeps through
     * lands on the figure players actually quote for an unbonemealed oak: about
     * a day. Deliberately a tick count rather than a step count, because steps
     * are a setting and days of growing are not.
     *
     * <p>The distribution matters as much as the mean, and it is the reason
     * {@link #grow} works the way it does: sapling growth is memoryless, so of
     * a dozen planted together some are trees within minutes and some are still
     * saplings two days later.
     */
    public static final int GROWING_TICKS = 24_000;

    /**
     * Logs one lumberjack fells per step: four.
     *
     * <p>Counted off the watched lumberjack, who is the reference for what a
     * lumberjack can do, exactly as {@code Field} counts swings off the watched
     * farmer. {@code LumberjackWorker} takes its logs through {@code HandDig},
     * which spends {@code Excavation.digTicks} on every one — vanilla's own
     * break arithmetic, doubled by {@code Excavation.LABOR_FACTOR}. An oak log
     * is hardness 2 against an iron axe's speed 6, which is ten ticks for a
     * player and twenty for a settler. A hundred-tick step is therefore five
     * logs of pure chopping.
     *
     * <p>Call it four. The fifth log is the walk: a trunk is six logs tall and
     * the stand is planted five blocks apart, so a jack who clears one tree
     * spends part of a step getting to the next — the same one-in-five the field
     * gives up to stepping across the rows.
     *
     * <p>It is two thirds of a tree a step, and that is the number to hold on
     * to: a dozen standing trees is about eighteen steps of work for one jack.
     */
    public static final int LOGS_PER_JACK_PER_STEP = 4;

    /**
     * Saplings one lumberjack puts back per step: two.
     *
     * <p>Also off the watched lumberjack. Planting costs no dig time at all —
     * {@code LumberjackWorker} sets the sapling on the tick it arrives — so the
     * whole cost is the walk, and planting spots are a stand's
     * {@link ForesterStand#SPACING} apart. Of the five passes in a step, roughly
     * every other one is spent walking to the next bare square, which is two
     * saplings in the ground.
     */
    public static final int SAPLINGS_PER_JACK_PER_STEP = 2;

    private Stand() {
    }

    /** How many simulation steps a sapling takes to come up, at these settings. */
    public static int growingSteps(SimSettings settings) {
        return Math.max(1, GROWING_TICKS / Math.max(1, settings.simIntervalTicks()));
    }

    /** Whether anybody has ever counted the trees around this camp. */
    public static boolean isCounted(Building camp) {
        return camp.standThousandths() != UNCOUNTED;
    }

    /** Trees standing in the camp's claim right now. */
    public static int trees(Building camp) {
        return Math.max(0, camp.standThousandths()) / PER_TREE;
    }

    /** Logs those trees are worth — what a jack could actually carry off today. */
    public static int logs(Building camp) {
        return Math.max(0, camp.standThousandths()) / PER_LOG;
    }

    /** Trees' worth of sapling in the ground, rounded up: what is coming back. */
    public static int growing(Building camp) {
        return (Math.max(0, camp.growingThousandths()) + PER_TREE - 1) / PER_TREE;
    }

    /** Whether this camp has neither a trunk to fell nor a sapling to wait on. */
    public static boolean isBare(Building camp) {
        return isCounted(camp) && camp.standThousandths() < PER_LOG
                && camp.growingThousandths() <= 0;
    }

    /**
     * Take the world's word for what is standing here.
     *
     * <p>Called when the ground can answer and not otherwise: the first time a
     * camp's chunks are loaded, and again on the step a player walks up to it.
     * Saplings are left alone — a count of trunks does not see them, and the
     * ledger is the only record there is of what has been put in the ground.
     */
    public static void recount(Building camp, int trees) {
        camp.setStandThousandths(Math.max(0, trees) * PER_TREE);
    }

    /**
     * One step of growing back.
     *
     * <p>Runs whether or not anybody is watching, because the world grows its own
     * saplings in a loaded chunk and the ledger is only mirroring them.
     *
     * <p>A share of what is coming up arrives each step rather than each sapling
     * standing up on its own two-hundred-and-fortieth birthday, and that is not
     * a shortcut — it is what {@link #GROWING_TICKS} actually describes. Sapling
     * growth is a coin flipped on random ticks with no memory of the flips
     * before it, so a cohort planted together comes up strung out over days with
     * a mean of one. A pool that delivers a two-hundred-and-fortieth of itself
     * each step has exactly that mean and exactly that spread.
     */
    public static void grow(Building camp, SimContext ctx) {
        int growing = camp.growingThousandths();
        if (growing <= 0) {
            return;
        }
        int come = Math.min(growing, Math.max(1, growing / growingSteps(ctx.settings())));
        camp.setGrowingThousandths(growing - come);
        camp.setStandThousandths(Math.max(0, camp.standThousandths()) + come);
    }

    /**
     * Put a sapling in the ground: one tree's worth of timber, coming.
     *
     * <p>The hook a real planting calls as well as the clock. A lumberjack who
     * walks out and plants a sapling has done exactly this, and the camp's
     * ledger has to know or the woodland renews itself twice.
     */
    public static void plant(Building camp) {
        camp.setGrowingThousandths(camp.growingThousandths() + PER_TREE);
    }

    /**
     * Fell up to {@code logs} logs off the standing timber.
     *
     * <p>The hook a real felling calls, one log at a time, at the same moment
     * the timber is credited to the store. Without it the ledger would stand
     * full behind a watched camp and pay the whole wood out the instant the
     * player walked away.
     *
     * @return the logs actually taken, which is fewer than asked for when the
     *         stand runs out mid-step
     */
    public static int fell(Building camp, int logs) {
        int taken = Math.min(Math.max(0, logs), logs(camp));
        if (taken > 0) {
            camp.setStandThousandths(camp.standThousandths() - taken * PER_LOG);
        }
        return taken;
    }

    /**
     * One log off the camp's ledger, but only if it stood in the camp's wood.
     *
     * <p>For every axe that is not a lumberjack's. A site crew clearing a plot,
     * a wall crew taking a tree off the line and a road crew driving through one
     * all fell real trunks, and a trunk that came out of the camp's
     * {@link WorkArea} is one trunk fewer standing there whoever took it — leave
     * it on the books and the clock pays the town for it again the moment
     * everybody walks away.
     *
     * <p>And only then. A tree on a house plot in the middle of the village is
     * not the forester's, so felling it must not make the camp any poorer: the
     * town gets the timber and the stand is untouched. Same argument for the
     * mine, which is why nothing anywhere debits a {@link Seam} for a foundation
     * — the rock under somebody's floor was never part of the workings.
     *
     * @return the logs actually taken off the ledger, zero when the tree was not
     *         the camp's
     */
    public static int fellInArea(Settlement settlement, SimPos where) {
        if (settlement == null) {
            return 0;
        }
        WorkArea wood = settlement.lumberArea();
        if (wood == null || !wood.contains(new SimPos(where.x(), wood.center().y(), where.z()))) {
            return 0;
        }
        Building camp = settlement.buildingWithRole(BuildingRole.LUMBER_CAMP);
        return camp == null ? 0 : fell(camp, 1);
    }

}
