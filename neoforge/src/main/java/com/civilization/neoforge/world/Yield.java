package com.civilization.neoforge.world;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.Stand;
import com.civilization.sim.work.Spoil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What happens the instant a citizen's tool takes a block out.
 *
 * <p><strong>One rule with no exceptions, in one place.</strong> Every block a
 * citizen breaks yields its material to that citizen, and what a citizen carries
 * goes into the town's supplies. It does not matter why the block was broken: a
 * site being cleared, a trunk leaning on a plot, the wall line, a road driven
 * through a wood, the earth out from under a floor. There is no such thing as
 * spoil, and there never was — a town that fells six oaks to stand a cottage on
 * their stumps has six oaks' worth of timber.
 *
 * <p>Every path that breaks a block by hand goes through here so that the rule
 * is stated once. What it is worth is {@code Spoil}'s table, which lives in the
 * simulation so both fidelities read one list; which {@code Spoil.Kind} a given
 * block state is comes from {@link BlueprintPlacer#spoilOf}.
 *
 * <p><strong>The town's supplies and the forester's ledger are two different
 * books, and that is the one distinction this class has to make.</strong> The
 * rule above is about the first: every block goes to the town, no exceptions,
 * whatever it was broken for. The second is a record of a wood being
 * <em>worked</em>, and a crew clearing a plot is not working the wood — see
 * {@link Cause}.
 */
public final class Yield {

    /**
     * Why the block came out, which is the one thing the spoil rule does not
     * settle on its own.
     *
     * <p>Every block goes to the town whatever the reason — that is the rule
     * above and it has no exceptions. What the reason decides is whether a
     * felled trunk also comes off the forester's {@link Stand}, and the two
     * questions were being answered by one call for as long as there was only
     * one caller.
     *
     * <p>Measured, which is why it is here: 57 trees standing in Millbrook
     * became 4 in 218 steps with the camp's timber swinging from 1072 to 1. The
     * town was clearing its own plots, and every trunk it took off one was
     * charged to the camp's ledger — so the clock, reading a stand it believed
     * had been felled, stopped paying for the wood the camp actually had.
     */
    public enum Cause {
        /**
         * A site being levelled, a wall line, a road driven through.
         *
         * <p>Spoil, and nothing more. A tree the town felled to stand a cottage
         * on is six oaks' worth of timber in the store and <strong>not</strong>
         * a tree cut from the forester's stand: the crew that took it was not
         * working the wood, and the camp's ledger is a record of the wood being
         * worked. The same argument the mine has always had — nothing anywhere
         * debits a {@code Seam} for a foundation, because the rock under
         * somebody's floor was never part of the workings.
         */
        CLEARING,
        /**
         * Somebody working the wood, whose felling the camp's books follow.
         *
         * <p>{@code Stand.fellInArea} decides whether the trunk actually stood
         * in the camp's claim; a tree felled for timber in the middle of the
         * village is still nobody's stand.
         */
        FELLING
    }

    private Yield() {
    }

    /**
     * Gives a broken block to whoever broke it.
     *
     * @param hands the person swinging, or null where the clock did it — in
     *              which case there is nobody to fill and the material goes
     *              straight to the shelves nearest the hole
     * @param why   whether this was clearing or felling; see {@link Cause}.
     *              Deliberately not defaulted — the whole of the stand-stripping
     *              bug was one call site that had no way to say.
     */
    public static void keep(Settlement settlement, Person hands,
                            BlockState broken, BlockPos at, Cause why) {
        if (settlement == null) {
            return;
        }
        Spoil.Kind kind = BlueprintPlacer.spoilOf(broken);
        if (!kind.isSomething()) {
            return;
        }
        SimPos where = new SimPos(at.getX(), at.getY(), at.getZ());
        if (kind == Spoil.Kind.TIMBER && why == Cause.FELLING) {
            // A trunk out of the camp's wood is a trunk off the camp's books
            // when it was the wood being worked. A tree on a house plot in the
            // village is not the forester's and is not counted.
            Stand.fellInArea(settlement, where);
        }
        // What will not fit in an armful is set down where it was cut rather
        // than destroyed: a whole tree comes away in one stroke and is more than
        // anybody can hold, and a rule that dropped the overflow would make a
        // felled oak worth less than the same six logs dug one at a time.
        int over = kind.perBlock() - Spoil.gain(hands, kind);
        if (over > 0) {
            Spoil.credit(settlement, where, kind.resource(), over);
        }
    }
}
