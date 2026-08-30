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
     * This arrangement's whole layout, as one thing, for as many plots as asked.
     *
     * <p>{@link #plotFor} answers "where does the nth building go" and is all
     * the siting code has ever needed. It is not enough to lay a street: a road
     * has to be known <em>before</em> the buildings that front it, and a
     * sequence of positions has nowhere to put one.
     *
     * <p>So a layout may also describe itself whole. The default derives a plan
     * from the positions alone — every plot square, facing the centre, fronting
     * nothing — which is exactly what a lattice arrangement is, honestly
     * reported. An arrangement built around streets overrides this, and then
     * {@link #plotFor} is properly a <em>view</em> of the plan rather than the
     * other way about.
     *
     * <p>The three rules still hold and are the plan's to keep: the same centre
     * and count give the same plan, no two plots share ground, and neighbours
     * are {@link #MIN_PLOT_SEPARATION} apart.
     */
    default TownPlan planFor(SimPos centre, int wanted) {
        java.util.List<TownPlan.Plot> plots = new java.util.ArrayList<>();
        for (int i = 0; i < Math.max(0, wanted); i++) {
            SimPos at = plotFor(centre, i);
            plots.add(new TownPlan.Plot(at, DEFAULT_SPAN, facingToward(at, centre), -1));
        }
        return new TownPlan(centre, java.util.List.of(), plots);
    }

    /**
     * Which way a building on this plot looks, in quarter turns clockwise.
     *
     * <p>Toward the centre, for an arrangement with no streets to face. Kept
     * here rather than borrowed from {@code BuildPlanner} so that a layout can
     * describe itself without reaching into the planners that consume it.
     */
    static int facingToward(SimPos plot, SimPos centre) {
        int dx = centre.x() - plot.x();
        int dz = centre.z() - plot.z();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? 1 : 3;
        }
        return dz >= 0 ? 2 : 0;
    }

    /** The claim a plot takes when a layout has no opinion about size. */
    int DEFAULT_SPAN = 11;

    /**
     * How far out the town's claim should reach for a plot at this distance.
     *
     * <p>Layouts that sprawl need more margin than layouts that huddle.
     */
    default int claimMargin() {
        return 8;
    }
}
