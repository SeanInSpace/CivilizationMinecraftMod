package com.civilization.sim.settlement;

/**
 * The stone a mine actually stands on, as a number the clock can work.
 *
 * <p>The timber trade's twin, one layer down, and it exists for the same reason
 * {@link Stand} does: at the shipped yield of zero an unwatched mine cut nothing
 * at all, so a town nobody visited could not pay for the next thing it wanted to
 * build. A mine's stone is not a percentage of an imagined quarry. It is the
 * rock that is actually under the mine head, cut by however many miners the town
 * actually has.
 *
 * <p><strong>A seam does not grow back.</strong> That is the whole difference
 * from the stand, and it is what makes a mine a place rather than a machine: a
 * camp that is felled bare regrows out of its own saplings, and a mine that is
 * cut out is cut out. When the seam is empty the mine is exhausted, the clock
 * stops crediting it, and {@code /civ info} says so — a town that wants more
 * stone after that has to find more ground.
 *
 * <p>Whole blocks, unlike the stand's thousandths: a miner takes six of them a
 * step and there is no slow ripening to carry a remainder for.
 */
public final class Seam {

    /**
     * A seam nobody has ever counted. See {@link Stand#UNCOUNTED}: "nobody has
     * looked" and "there is nothing there" are opposite facts, and a mine saved
     * before a player ever walked to it must not wake up believing it is spent.
     */
    public static final int UNCOUNTED = -1;

    /**
     * How far below the mine head the workings reach.
     *
     * <p>{@code MinerWorker} will not cut deeper than this, so counting deeper
     * than this would credit a mine with rock nobody is ever going to bring up.
     * The two must stay in step, which is why the worker reads this number
     * rather than keeping its own.
     */
    public static final int WORKINGS_DEPTH = 20;

    /**
     * What a mine is credited with when the ground cannot be counted: 2,000
     * blocks.
     *
     * <p>Deliberately conservative, and deliberately stated. The workings of a
     * default mine are thirty-three by thirty-three by twenty — near enough
     * twenty-two thousand blocks, most of it solid rock in ordinary terrain. Two
     * thousand is a tenth of that: about three hundred and thirty steps of work
     * for one miner, enough that a town which had to guess is not stopped dead,
     * and little enough that a mine sunk into a superflat is never credited with
     * a mountain. Where the chunks are loaded nobody guesses — the ground is
     * sampled and the real number used, and a mountain mine reads far above this
     * while a superflat one reads far below it.
     */
    public static final int UNSURVEYED = 2_000;

    /**
     * Stone one miner cuts per step: six blocks.
     *
     * <p>Counted off the watched miner, who is the reference for what a miner
     * can do. {@code MinerWorker} takes its blocks through {@code HandDig},
     * which spends {@code Excavation.digTicks} on each — vanilla's own break
     * arithmetic, doubled by {@code Excavation.LABOR_FACTOR}. Stone is hardness
     * 1.5 against an iron pickaxe's speed 6: eight ticks for a player, sixteen
     * for a settler. A hundred-tick step is six and a quarter blocks, and the
     * quarter is the shuffle to the next face.
     *
     * <p>It happens to be the number the yield table used to conjure, which is a
     * good sign about the old estimate and no reason to keep the table.
     */
    public static final int STONE_PER_MINER_PER_STEP = 6;

    /**
     * Blocks of stone cut per ingot's worth of iron turned up: six.
     *
     * <p>The ratio the mine has always run at — a miner brought in six stone and
     * one iron a step — kept exactly as it was when the seam replaced the table
     * under it. Ore is rarer than rock, and the iron is found while cutting the
     * rock rather than alongside it, so it comes off the same seam and is not
     * charged twice.
     */
    public static final int STONE_PER_IRON = 6;

    private Seam() {
    }

    /** Whether anybody has ever counted the stone under this mine. */
    public static boolean isCounted(Building mine) {
        return mine.stoneSeam() != UNCOUNTED;
    }

    /** Blocks of stone left in the workings. */
    public static int remaining(Building mine) {
        return Math.max(0, mine.stoneSeam());
    }

    /** Whether this mine has been cut out. */
    public static boolean isExhausted(Building mine) {
        return isCounted(mine) && mine.stoneSeam() <= 0;
    }

    /** Take the world's word for what is down there. */
    public static void recount(Building mine, int blocks) {
        mine.setStoneSeam(Math.max(0, blocks));
    }

    /**
     * Cut up to {@code blocks} out of the seam.
     *
     * <p>The hook a real dig calls, one block at a time, at the same moment the
     * stone is credited to the store — an ore block included, because the miner
     * who broke it was cutting the same seam.
     *
     * @return what was actually cut, which is less than asked for on the step
     *         the mine runs out
     */
    public static int cut(Building mine, int blocks) {
        int taken = Math.min(Math.max(0, blocks), remaining(mine));
        if (taken > 0) {
            mine.setStoneSeam(mine.stoneSeam() - taken);
        }
        return taken;
    }
}
