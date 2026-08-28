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
     * <p><strong>Measured on the wider axis, not as a distance.</strong> That
     * distinction is the whole of this constant's history. It used to be
     * documented as a distance and tested as one, while
     * {@code BuildPlanner.plotsOverlap} refused a pair when they were within
     * reach on <em>both</em> axes at once — a square box, not a circle. So a
     * layout could keep the stated rule, pass the test that checked it, and
     * still have its plots thrown away by the siting code.
     *
     * <p>{@link Layouts#WARREN} did exactly that. Six huts on a circle of
     * thirteen sit a comfortable 12.5 apart as the crow flies and 11 apart on
     * the wider axis, so a third of every warren's plots were refused, its plot
     * cursor ran away outward, and a measured town reached a quarter of the
     * population of the same seed under a different layout. The rule was never
     * broken. It was written in the wrong units.
     *
     * <p>Hence {@link #farEnoughApart}: one predicate, in the metric the code
     * actually applies, for layouts and tests to share. A rule with two
     * definitions has none.
     */
    int MIN_PLOT_SEPARATION = 12;

    /**
     * Whether two plot centres are far enough apart to both be built on.
     *
     * <p>The wider axis decides it, because that is what the overlap check
     * asks: two plots foul each other only when they are close on <em>both</em>
     * axes. Being far apart on one is enough, which is precisely how a street
     * of houses works and why a distance was always the wrong measure.
     *
     * <p>Stated for two plots of the default span. A larger building reaches
     * further and may still be refused; the siting loop tries the next index
     * when that happens, which is a cost of one attempt and not a fault.
     */
    static boolean farEnoughApart(SimPos a, SimPos b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()))
                >= MIN_PLOT_SEPARATION;
    }

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
