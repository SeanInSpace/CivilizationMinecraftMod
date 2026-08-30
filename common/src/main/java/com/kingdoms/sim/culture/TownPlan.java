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
     * One street, as the run of points it passes through.
     *
     * <p>This was a single straight run, on the reasoning that a bend is two
     * runs and a curve is a polyline of them. That is true of the <em>geometry</em>
     * and false of the <em>identity</em>: a plot records the street it fronts as
     * an index, so chopping one road into thirty segments turns one street the
     * town knows by name into thirty a plot cannot pick between. A bending high
     * street would have had its frontage attributed to whichever eight-block
     * fragment happened to be nearest.
     *
     * <p>So a street carries its path. A straight one is two points and costs
     * nothing extra; a wandering one is however many it needs, and either way the
     * road is one thing with one index, which is what the rest of the plan
     * assumes.
     */
    public record Street(List<SimPos> path, int width, Kind kind) {
        public Street {
            path = List.copyOf(path);
            if (path.size() < 2) {
                throw new IllegalArgumentException("a street needs at least two points");
            }
        }

        /** A straight street, which is the common case and reads better. */
        public Street(SimPos from, SimPos to, int width, Kind kind) {
            this(List.of(from, to), width, kind);
        }

        public SimPos from() {
            return path.get(0);
        }

        public SimPos to() {
            return path.get(path.size() - 1);
        }

        /** How long this street is, along the ground, following its bends. */
        public double length() {
            double total = 0;
            for (int i = 1; i < path.size(); i++) {
                total += Math.hypot(path.get(i).x() - path.get(i - 1).x(),
                                    path.get(i).z() - path.get(i - 1).z());
            }
            return total;
        }

        /** Whether this street lies more east-west than north-south, end to end. */
        public boolean runsEastWest() {
            return Math.abs(to().x() - from().x()) >= Math.abs(to().z() - from().z());
        }

        /** Whether it bends at all, which decides how it can be drawn or paved. */
        public boolean isStraight() {
            return path.size() == 2;
        }

        /**
         * Whether a square of this half-width would stand on the carriageway.
         *
         * <p>Lives here, on the street, because the layout that refuses such a
         * plot and the invariant that checks none survived have to mean the same
         * thing by it. This codebase has already paid for a rule with two
         * definitions once: {@code MIN_PLOT_SEPARATION} was documented as a
         * distance and enforced as a box, so a layout could keep the stated rule,
         * pass the test that checked it, and still have its plots thrown away.
         *
         * <p>Measured as a real distance from the road to the plot's square. The
         * two cheaper versions of this were both wrong, and instructively so.
         * Comparing the run's <em>bounding box</em> is exact only while a street
         * is straight: on a road bending nine blocks in eighty, an eight-block
         * run's box stands five and a half blocks wider than the road, which ate
         * the setback and halved the frontage.
         *
         * <p>Expanding the plot's square by the road's half-width and asking
         * whether the run crosses it is better, and still wrong on a curve — the
         * corners of that square reach a factor of root two further than its
         * sides, so a ring road passing at radius forty clips the corner of a box
         * centred at radius twenty-seven. On a grid that costs a block; on a
         * circle it refused a hundred and twenty-five of a hundred and
         * seventy-seven offers and left a ring town with a quarter of its
         * frontage. A square expanded into a square is the wrong shape: the right
         * one is a square expanded by a radius, which has round corners.
         */
        public boolean touches(SimPos at, double half) {
            double edge = width / 2.0;
            for (int i = 1; i < path.size(); i++) {
                if (distanceToSquare(path.get(i - 1), path.get(i), at, half) < edge) {
                    return true;
                }
            }
            return false;
        }

        /**
         * How far a run passes from an axis-aligned square, zero if it crosses it.
         *
         * <p>Either the run enters the square, or the nearest approach is at one
         * of the square's corners or at one of the run's own ends.
         */
        private static double distanceToSquare(SimPos a, SimPos b, SimPos box, double half) {
            double x1 = box.x() - half;
            double x2 = box.x() + half;
            double z1 = box.z() - half;
            double z2 = box.z() + half;
            if (crosses(a, b, x1, x2, z1, z2)) {
                return 0;
            }
            double nearest = Double.MAX_VALUE;
            double[][] corners = {{x1, z1}, {x2, z1}, {x2, z2}, {x1, z2}};
            for (double[] corner : corners) {
                nearest = Math.min(nearest, pointToSegment(corner[0], corner[1], a, b));
            }
            nearest = Math.min(nearest, pointToBox(a, x1, x2, z1, z2));
            nearest = Math.min(nearest, pointToBox(b, x1, x2, z1, z2));
            return nearest;
        }

        /** Whether a run enters a box at all: the slab method. */
        private static boolean crosses(SimPos a, SimPos b,
                                       double x1, double x2, double z1, double z2) {
            double enter = 0;
            double leave = 1;
            double[][] slabs = {
                    {b.x() - a.x(), x1 - a.x(), x2 - a.x()},
                    {b.z() - a.z(), z1 - a.z(), z2 - a.z()},
            };
            for (double[] slab : slabs) {
                double d = slab[0];
                if (d == 0) {
                    // Parallel to this pair: either between them or nowhere near.
                    if (slab[1] > 0 || slab[2] < 0) {
                        return false;
                    }
                    continue;
                }
                double near = slab[1] / d;
                double far = slab[2] / d;
                if (near > far) {
                    double swap = near;
                    near = far;
                    far = swap;
                }
                enter = Math.max(enter, near);
                leave = Math.min(leave, far);
                if (enter > leave) {
                    return false;
                }
            }
            return true;
        }

        private static double pointToSegment(double px, double pz, SimPos a, SimPos b) {
            double vx = b.x() - a.x();
            double vz = b.z() - a.z();
            double len = vx * vx + vz * vz;
            if (len == 0) {
                return Math.hypot(px - a.x(), pz - a.z());
            }
            double t = ((px - a.x()) * vx + (pz - a.z()) * vz) / len;
            t = Math.max(0, Math.min(1, t));
            return Math.hypot(px - (a.x() + t * vx), pz - (a.z() + t * vz));
        }

        private static double pointToBox(SimPos p, double x1, double x2,
                                         double z1, double z2) {
            double dx = Math.max(Math.max(x1 - p.x(), 0), p.x() - x2);
            double dz = Math.max(Math.max(z1 - p.z(), 0), p.z() - z2);
            return Math.hypot(dx, dz);
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
