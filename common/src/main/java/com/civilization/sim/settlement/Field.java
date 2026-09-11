package com.civilization.sim.settlement;

import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;

/**
 * The crop field a farm actually stands on, as a number the clock can work.
 *
 * <p>Food used to be the one thing a town could conjure: a yield table said what
 * percentage of an imagined harvest to credit, and at the shipped zero an
 * unwatched town simply starved. The percentage was the wrong knob. A field is
 * not an abstraction with a dial on it — it is eleven by eleven of farmland with
 * a countable number of wheat blocks in it, ripening on Minecraft's own
 * schedule, cut by however many farmers the town actually has. So that is what
 * this models, and {@code YieldPolicy} has no say in food any more.
 *
 * <p>Every farm carries a ripeness ledger: how much of its field is standing
 * ready. Two things advance it — the weather, at {@link #ripen}, and the hands
 * working the rows, at {@link #tend} — and one thing spends it, a cut. It is the
 * same ledger whether a player is watching or not. Where somebody is watching,
 * {@code FarmWorker}'s real hands do the cutting and debit it through
 * {@link #cut}, and the clock credits nothing at all.
 *
 * <p>Held in hundredths of a block, for the reason {@link Settlement#abstractYield}
 * carries its own remainder: the weather alone brings on about a fifth of a
 * block per step, and a ledger that could only count whole blocks would round
 * that to nothing forever.
 *
 * <p>What a field is worth, at the shipped hundred ticks a step and two farmers
 * on it: about nine tenths of a <em>sheaf</em> a step, once cutting and tending
 * have found their balance. A sheaf is not a loaf — see {@link #deliver} — and
 * what the town gets for it depends on where it bakes: one loaf at a hearth,
 * one and a half at a working mill. That is the number a town's whole food
 * supply is now made of, and there is no setting anywhere that changes it.
 */
public final class Field {

    /**
     * Wheat blocks in one farm's field: <strong>71</strong>.
     *
     * <p>Counted off the drawing rather than guessed. {@code BlueprintPlacer.farm}
     * lays an eleven-by-eleven plot ({@code r = 5}): the rim is grass and fence,
     * the nine-by-nine inside it is soil — eighty-one cells — with an irrigation
     * channel down the middle row taking nine of them, leaving seventy-two of
     * farmland-and-wheat, and the farm post standing on one of those. Seventy-one
     * wheat blocks. No culture varies it: the farm is drawn from one method with
     * no culture in it, unlike the compound.
     *
     * <p>{@code BlueprintPlacerFieldTest} counts the blocks the placer actually
     * draws and asserts this number, so the two cannot drift apart.
     */
    public static final int CROP_BLOCKS = 71;

    /**
     * Game ticks for one wheat block to go from planted to mature: 37,200,
     * about thirty-one minutes.
     *
     * <p>The derivation, so the number can be argued with:
     * <ul>
     *   <li>Wheat has eight age states, so seven growth steps from seed to
     *       mature.</li>
     *   <li>A block only grows when it gets a random tick. Java Edition rolls
     *       three random ticks per 16×16×16 section per game tick, so any one
     *       block is picked with probability 3/4096 — one random tick per 1,365
     *       game ticks on average.</li>
     *   <li>On hydrated farmland a crop's growth points come out around ten,
     *       and the growth roll is {@code 1/(floor(25/points)+1)} — about one in
     *       three per random tick, so three random ticks per age step.</li>
     * </ul>
     * That is 7 × 3 × 1365 ≈ 28,700 ticks, or roughly twenty-four minutes of
     * pure growing. Real fields are slower than pure growing, and the commonly
     * cited figure for wheat is about thirty-one minutes: crops need light 9 to
     * advance and our field's lanterns give 8, so nothing ripens after dark, and
     * a field planted solid rather than in rows halves its own growth points
     * wherever the neighbors crowd it. Thirty-one minutes is the observed
     * average those pull the clean figure toward, and it is what ships.
     *
     * <p>Deliberately a tick count rather than a step count: steps are a setting
     * and minutes of wheat are not.
     */
    public static final int RIPENING_TICKS = 37_200;

    /**
     * Useful swings one farmer gets in per step: four.
     *
     * <p>Counted off the watched farmer, who is the reference for what a farmer
     * can do. {@code PersonEntityManager} runs a pass every twenty ticks and
     * {@code FarmWorker} takes exactly one action per pass, so a hundred-tick
     * step is five actions. Most of them land: a farmer works to a reach of 4.5
     * blocks, which from anywhere in a nine-wide field covers sixty-odd cells,
     * and {@code FarmWorker} always picks the nearest job — so a farmer standing
     * in the rows has something in arm's reach nearly every pass. Call it one
     * pass in five spent stepping across to the next patch, and four swings.
     *
     * <p>The estimate matters: it is what a field's output is proportional to
     * once the hands rather than the weather are the limit. Five would mean a
     * farmer who never takes a step; three would mean one who spends two passes
     * in five walking a field they can nearly reach across.
     *
     * <p>A swing is spent cutting if there is anything ripe within reach and
     * tending the rows if there is not — see {@link #tend}.
     */
    public static final int BLOCKS_PER_FARMER_PER_STEP = 4;

    /**
     * Age steps a wheat block climbs from seed to mature: seven.
     *
     * <p>What one act of tending is worth. A farmer bonemealing nothing and
     * simply working the row nudges a crop one age along — that is literally
     * what {@code FarmWorker}'s tending branch does to the block — so a tending
     * action is a seventh of a crop.
     */
    public static final int GROWTH_STAGES = 7;

    /**
     * What a fully staffed field brings in on the clock, sheaves a step.
     *
     * <p>Sheaves rather than loaves, since bread stopped growing on the stalk.
     * The name is left alone because the arithmetic did not change: one cut
     * block is one unit of harvest, whatever it is called on the way to the
     * oven.
     *
     * <p>Two farmers swing {@link #BLOCKS_PER_FARMER_PER_STEP} times each per
     * step, and every swing either cuts a ripe block or tends an unripe one a
     * stage forward. A block takes {@link #GROWTH_STAGES} tendings and one cut,
     * so in the steady state one sheaf costs eight swings: eight swings a step,
     * one sheaf a step. The measured run settles a little under this, at nine
     * in ten, because a full store stops the cutting now and then.
     */
    public static final int LOAVES_PER_STEP_TENDED =
            FoodPlanner.FARMERS_PER_FARM * BLOCKS_PER_FARMER_PER_STEP / (GROWTH_STAGES + 1);

    /** Hundredths of a block, the unit the ledger is kept in. */
    private static final int PER_BLOCK = 100;

    private Field() {
    }

    /** Whether this kind of building is a field at all. */
    public static boolean isField(String blueprintId) {
        return "farm".equals(BuildingRole.bareName(blueprintId));
    }

    /**
     * How many crop blocks <em>this</em> farm actually has.
     *
     * <p>{@link #CROP_BLOCKS} is what the drawn farm has, and for most of this
     * mod's life that was the same thing as what every farm has. It stopped
     * being once a player could hand the town a field they built themselves: a
     * hand-authored farm is however many rows the author laid, and a ledger run
     * off the constant would credit a town for crops that are not in the ground
     * — or starve one whose field is bigger than ours.
     *
     * <p>Counted off the placed building rather than declared in the file. What
     * the file claims is the author's word for it; what was placed is the
     * wheat.
     */
    public static int cropBlocksOf(Building farm) {
        if (farm != null && farm.isAuthored() && farm.authored().cropBlocks() > 0) {
            return farm.authored().cropBlocks();
        }
        return CROP_BLOCKS;
    }

    /** How many simulation steps a block of wheat takes to ripen, at these settings. */
    public static int ripeningSteps(SimSettings settings) {
        return Math.max(1, RIPENING_TICKS / Math.max(1, settings.simIntervalTicks()));
    }

    /**
     * How much of a field comes ready in one step, in hundredths of a block.
     *
     * <p>The whole field over the ripening time, which is what a field being
     * continuously harvested and replanted looks like from a distance: at the
     * shipped hundred ticks a step that is 71 blocks over 372 steps, nineteen
     * hundredths of a block each.
     *
     * <p>Proportional to the field, not to the constant: half the rows ripen half
     * as fast, which is the whole of what a smaller field means.
     */
    public static int ripeningPerStep(Building farm, SimSettings settings) {
        return Math.max(1, cropBlocksOf(farm) * PER_BLOCK / ripeningSteps(settings));
    }

    /** Blocks standing ready on this farm right now. */
    public static int ripeBlocks(Building farm) {
        return farm.ripeHundredths() / PER_BLOCK;
    }

    /**
     * One step of growing, for one farm.
     *
     * <p>Runs whether or not anybody is watching: the world grows its own crops
     * in a loaded chunk and the ledger is only mirroring them. Capped at the
     * field, because a farm nobody cuts for a year has a ripe field and not a
     * granary hidden in the soil.
     */
    public static void ripen(Building farm, SimContext ctx) {
        int grown = farm.ripeHundredths() + ripeningPerStep(farm, ctx.settings());
        farm.setRipeHundredths(Math.min(cropBlocksOf(farm) * PER_BLOCK, grown));
    }

    /**
     * Hands working the rows, hurrying the crop along.
     *
     * <p>The half of farming that is not cutting, and leaving it out was a
     * thirty-fold hole between the two fidelities. {@code FarmWorker} — which is
     * the reference for what a farmer does, and says so in its own javadoc,
     * "growing crops are tended forward (vanilla's own growth rate would feed
     * nobody)" — spends every action it is not harvesting or planting on nudging
     * a crop one age nearer ripe. A watched field therefore runs at the farmers'
     * pace and not at the weather's. An unwatched one modelled on ripening alone
     * ran at a thirtieth of it, which would have meant a town that quietly
     * collapsed the moment its player walked away and recovered when they came
     * back — the exact thing the two-fidelity rule exists to forbid.
     *
     * <p>So the clock tends too. Every action a farmer did not spend cutting is
     * spent here, and it is worth {@link #GROWTH_STAGES}th of a block, because
     * that is what the swing is worth in the world.
     *
     * @param actions swings not spent on the harvest
     */
    public static void tend(Building farm, int actions) {
        if (actions > 0) {
            farm.setRipeHundredths(Math.min(cropBlocksOf(farm) * PER_BLOCK,
                    farm.ripeHundredths() + actions * (PER_BLOCK / GROWTH_STAGES)));
        }
    }

    /**
     * Cut up to {@code limit} ripe blocks, and put them back to seed.
     *
     * <p>Only whole blocks are taken, so the fraction the field is part-way
     * through keeping is never rounded away.
     *
     * @return the blocks actually cut
     */
    public static int harvest(Building farm, int limit) {
        int cutting = Math.min(Math.max(0, limit), ripeBlocks(farm));
        if (cutting > 0) {
            cut(farm, cutting);
        }
        return cutting;
    }

    /**
     * Strike ripe blocks off the ledger without crediting anything.
     *
     * <p>The hook a real harvest calls. Where somebody is watching, the clock
     * does not cut and {@code FarmWorker} does — and a block a farmer actually
     * swung at is a ripe block gone, whoever counted it. Without this the ledger
     * would fill up behind a watched field and pay out the moment the player
     * walked away.
     */
    public static void cut(Building farm, int blocks) {
        if (blocks > 0) {
            farm.setRipeHundredths(farm.ripeHundredths() - blocks * PER_BLOCK);
        }
    }

    /**
     * Put the cut wheat on the farm it came off.
     *
     * <p>One method on purpose, and the day it warned about has arrived: a cut
     * block is a sheaf of {@link TownStores#GRAIN} on the farm's own shelf, not
     * a loaf. Nobody can eat it there. It has to be carried to wherever the town
     * bakes — {@link FoodPlanner#bakery} — and only then is it bread.
     *
     * <p>Grain rather than {@code foodStored} because they are different goods
     * and a farm may hold both: {@code foodStored} on a field is loaves from an
     * older save, still perfectly edible, and they are left exactly where they
     * are.
     *
     * @return what was actually taken, once the farm's shelf is full
     */
    public static int deliver(Building farm, int amount) {
        return farm.stores().addCapped(TownStores.GRAIN, amount, FoodPlanner.FARM_GRAIN_CAP);
    }

    /** Sheaves waiting on this farm's shelf for somebody to carry them in. */
    public static int grainStored(Building farm) {
        return farm.stores().get(TownStores.GRAIN);
    }
}
