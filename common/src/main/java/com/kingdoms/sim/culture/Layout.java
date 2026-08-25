package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

/**
 * How a people arranges a town on the ground.
 *
 * <p>Until now this was a string on {@link Culture} that nothing read. Every
 * settlement, whatever it called itself, laid its buildings in concentric rings
 * — which is fine for one people and nonsense the moment there are two who
 * think differently about what a town is. A goblin warren and a human village
 * are not the same shape with different doors on it.
 *
 * <p>The whole of a layout is one pure function: given a centre and the index
 * of the next plot, where does it go. That is deliberately the narrowest seam
 * that can express a genuinely different town, and it is narrow enough that a
 * new arrangement is a new class and nothing else — no planner changes, no
 * threading a concept through the simulation.
 *
 * <p><strong>Three rules every layout must keep.</strong> They are not style;
 * the siting code assumes all three and a layout that breaks one produces a
 * town that cannot build.
 *
 * <ol>
 *   <li><strong>Deterministic.</strong> The same centre and index always give
 *       the same position. The whole test suite leans on replayability, and a
 *       town that re-planned itself on reload would rebuild its own streets.</li>
 *   <li><strong>Injective.</strong> Two different indices never give the same
 *       position. An index is spent when a plot is taken, and a layout that
 *       handed out the same ground twice would have the town demolish itself to
 *       build on it.</li>
 *   <li><strong>Roomy enough.</strong> Neighbouring plots must sit at least
 *       {@link #MIN_PLOT_SEPARATION} apart, or every candidate is rejected by
 *       the overlap check and the town never builds anything at all.</li>
 * </ol>
 */
public interface Layout {

    /**
     * The closest two plot centres may sit and still both be buildable.
     *
     * <p>Not a matter of taste: a default plot is eleven across and the siting
     * code insists on a block of bare ground between neighbours, so two plot
     * centres closer than twelve fail the overlap check and one of them is
     * refused. A layout that proposes ground this tight is not making a dense
     * town, it is making candidates that get thrown away.
     *
     * <p>{@link Layouts#RING} does not keep this on its innermost ring, which
     * is a defect it has always had — see the test that measures it. New
     * arrangements are expected to keep it.
     */
    int MIN_PLOT_SEPARATION = 12;

    /** This arrangement's identifier, as a culture names it. */
    String id();

    /**
     * Where the nth plot of this town goes.
     *
     * <p>The y is the centre's: what height the ground turns out to be is the
     * survey's business, not the plan's.
     */
    SimPos plotFor(SimPos centre, int index);

    /**
     * How far out the town's claim should reach for a plot at this distance.
     *
     * <p>Layouts that sprawl need more margin than layouts that huddle.
     */
    default int claimMargin() {
        return 8;
    }
}
