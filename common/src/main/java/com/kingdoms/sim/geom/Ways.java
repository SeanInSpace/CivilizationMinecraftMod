package com.kingdoms.sim.geom;

/**
 * How far a run of way passes from a square of ground.
 *
 * <p>One definition, because there are now three things that have to agree about
 * it: the layout that refuses to offer a plot standing in the road, the
 * invariant that checks no plan contains one, and the siting code that refuses a
 * building on a street the town has already laid. A rule with two definitions
 * has none — this codebase has paid for that once already, when
 * {@code MIN_PLOT_SEPARATION} was documented as a distance and enforced as a
 * box, so a layout could keep the stated rule, pass the test that checked it,
 * and still have its plots thrown away.
 *
 * <p>It is a real distance, not a box against a box. Expanding the plot's square
 * by the road's half-width and asking whether the run crosses it is the cheap
 * version and it is wrong on a curve: the corners of that square reach a factor
 * of root two further than its sides, so a ring road passing at radius forty
 * clipped a plot centred at radius twenty-seven. On a grid that costs a block;
 * on a circle it refused a hundred and twenty-five of a hundred and
 * seventy-seven offers.
 */
public final class Ways {

    private Ways() {
    }

    /**
     * Distance from the run {@code a→b} to the axis-aligned square of this
     * half-width about {@code (cx, cz)}. Zero when the run crosses the square.
     */
    public static double distanceToSquare(int ax, int az, int bx, int bz,
                                          int cx, int cz, double half) {
        double x1 = cx - half;
        double x2 = cx + half;
        double z1 = cz - half;
        double z2 = cz + half;
        if (crosses(ax, az, bx, bz, x1, x2, z1, z2)) {
            return 0;
        }
        double nearest = Math.min(
                Math.min(pointToSegment(x1, z1, ax, az, bx, bz),
                         pointToSegment(x2, z1, ax, az, bx, bz)),
                Math.min(pointToSegment(x2, z2, ax, az, bx, bz),
                         pointToSegment(x1, z2, ax, az, bx, bz)));
        nearest = Math.min(nearest, pointToBox(ax, az, x1, x2, z1, z2));
        return Math.min(nearest, pointToBox(bx, bz, x1, x2, z1, z2));
    }

    /** Whether a run enters a box at all: the slab method. */
    private static boolean crosses(int ax, int az, int bx, int bz,
                                   double x1, double x2, double z1, double z2) {
        double enter = 0;
        double leave = 1;
        double[][] slabs = {
                {bx - ax, x1 - ax, x2 - ax},
                {bz - az, z1 - az, z2 - az},
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

    /**
     * How far a point lies from the run {@code a→b}.
     *
     * <p>Public because it had begun to be written out again elsewhere, which
     * is the fault this class's own comment describes: the wall has to be able
     * to ask whether a post it planted stands on the stretch it was staked on,
     * and that question deserves the same answer as every other distance here.
     */
    public static double distanceToSegment(double px, double pz,
                                           int ax, int az, int bx, int bz) {
        return pointToSegment(px, pz, ax, az, bx, bz);
    }

    private static double pointToSegment(double px, double pz,
                                         int ax, int az, int bx, int bz) {
        double vx = bx - ax;
        double vz = bz - az;
        double len = vx * vx + vz * vz;
        if (len == 0) {
            return Math.hypot(px - ax, pz - az);
        }
        double t = ((px - ax) * vx + (pz - az) * vz) / len;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (ax + t * vx), pz - (az + t * vz));
    }

    private static double pointToBox(int px, int pz,
                                     double x1, double x2, double z1, double z2) {
        double dx = Math.max(Math.max(x1 - px, 0), px - x2);
        double dz = Math.max(Math.max(z1 - pz, 0), pz - z2);
        return Math.hypot(dx, dz);
    }
}
