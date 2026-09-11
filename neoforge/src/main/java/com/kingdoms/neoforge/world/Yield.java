package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Stand;
import com.kingdoms.sim.work.Spoil;
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
 */
public final class Yield {

    private Yield() {
    }

    /**
     * Gives a broken block to whoever broke it.
     *
     * @param hands the person swinging, or null where the clock did it — in
     *              which case there is nobody to fill and the material goes
     *              straight to the shelves nearest the hole
     */
    public static void keep(Settlement settlement, Person hands,
                            BlockState broken, BlockPos at) {
        if (settlement == null) {
            return;
        }
        Spoil.Kind kind = BlueprintPlacer.spoilOf(broken);
        if (!kind.isSomething()) {
            return;
        }
        SimPos where = new SimPos(at.getX(), at.getY(), at.getZ());
        if (kind == Spoil.Kind.TIMBER) {
            // A trunk out of the camp's wood is a trunk off the camp's books,
            // whoever took it and whatever they took it for. A tree on a house
            // plot in the village is not the forester's and is not counted --
            // and nothing anywhere debits a Seam for a foundation, because the
            // rock under somebody's floor was never part of the workings.
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
