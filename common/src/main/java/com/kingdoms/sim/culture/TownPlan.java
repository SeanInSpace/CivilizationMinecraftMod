package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.List;
import java.util.Objects;

/**
 * A town's layout, held as one thing before any of it is built.
 *
 * <p>Until now there was no such object. A settlement's plan existed only as
 * the scattered {@code origin} fields of buildings that already stood, which
 * meant nothing could look at a layout <em>as a layout</em> — could not judge
 * it, could not reject it, could not lay a street along it, could not draw it
 * without first building it. Every question about the shape of a town had to be
 * answered by growing one and flying out to look.
 *
 * <p>So: streets and plots, together, as data. It is deliberately a value and
 * deliberately dumb. It knows where things go and nothing about how they are
 * built, what stands on them, or what the ground is like — a plan is a claim
 * about arrangement, and the survey is still what decides whether the claim
 * survives contact with a hillside.
 *
 * <p><strong>Streets first is the point.</strong> Roads in this simulation are
 * currently a <em>consequence</em>: a building is placed, and afterwards
 * {@code PathPlanner} joins it to whatever road passes nearest. Real settlements
 * work the other way round almost without exception — the route is there first
 * and the buildings take frontage on it. A plan that carries its streets can be
 * generated in that order, and a plot can then know which street it faces
 * instead of every door being turned toward the middle of town on principle.
 */
public record TownPlan(SimPos centre, List<Street> streets, List<Plot> plots) {

    public TownPlan {
        Objects.requireNonNull(centre, "centre");
        streets = List.copyOf(streets);
        plots = List.copyOf(plots);
    }

    /** What a street is for, which is mostly what decides how wide it is. */
    public enum Kind {
        /** The route through. Everything else hangs off it. */
        SPINE,
        /** A street off the spine, opened as the town needs more frontage. */
        LANE,
        /** Behind the frontage: reaches the backs of plots and later becomes a street. */
        BACK
    }

    /**
     * One straight run of street, from one end to the other.
     *
     * <p>Straight because a bend is two runs. That keeps the type trivial and
     * matches what the path layer can actually pave; a curve is a polyline of
     * these and reads as a curve on the ground.
     */
    public record Street(SimPos from, SimPos to, int width, Kind kind) {
        public Street {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }

        /** How long this run is, along the ground. */
        public double length() {
            return Math.hypot(to.x() - from.x(), to.z() - from.z());
        }

        /** Whether this run lies more east-west than north-south. */
        public boolean runsEastWest() {
            return Math.abs(to.x() - from.x()) >= Math.abs(to.z() - from.z());
        }
    }

    /**
     * One piece of ground set aside for one building.
     *
     * <p>{@code span} is the square claim the siting code already understands.
     * {@code facing} is in quarter turns clockwise, as {@code BuildPlanner} has
     * it, and on a planned town it points at the street rather than at the
     * centre — which is most of the difference between a village and a wheel of
     * sheds.
     *
     * @param street index into {@link #streets}, or -1 for a plot that fronts
     *               nothing. Kept as an index rather than a reference so the
     *               whole plan stays a flat value that can be compared, copied
     *               and written down.
     */
    public record Plot(SimPos at, int span, int facing, int street) {
        public Plot {
            Objects.requireNonNull(at, "at");
        }

        public boolean frontsAStreet() {
            return street >= 0;
        }
    }

    /** The plot at this index, which is what a {@code Layout} hands out. */
    public Plot plot(int index) {
        return plots.get(index);
    }

    public int size() {
        return plots.size();
    }

    /** The street a plot fronts, or null when it fronts none. */
    public Street streetOf(Plot plot) {
        return plot.frontsAStreet() ? streets.get(plot.street()) : null;
    }

    /**
     * How much of this plan actually faces a street.
     *
     * <p>The one number worth having about a plan on its own, before any ground
     * is judged: a layout where nothing fronts anything is a scatter, whatever
     * else is true of it. Reported as a percentage so the bar does not move when
     * the town grows.
     */
    public int frontagePercent() {
        if (plots.isEmpty()) {
            return 0;
        }
        int fronting = 0;
        for (Plot plot : plots) {
            if (plot.frontsAStreet()) {
                fronting++;
            }
        }
        return 100 * fronting / plots.size();
    }
}
