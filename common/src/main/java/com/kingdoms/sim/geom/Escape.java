package com.kingdoms.sim.geom;

/**
 * The arithmetic of getting away from something on foot.
 *
 * <p>A person who runs faster when frightened is a person with two speeds, and a
 * town full of those reads as a town of athletes who were sandbagging all day.
 * Everybody here walks at one pace. What changes when a creeper appears is not
 * how fast they move but <em>when they start</em> and <em>where they go</em>, and
 * both of those are arithmetic rather than atmosphere — which is why they live
 * here, in the module that cannot see Minecraft, with a test on them.
 *
 * <p>Two questions, and they are the two a frightened person actually asks:
 * <em>how much warning do I need?</em> ({@link #noticeDistance}) and <em>does the
 * way I want to run take me past the thing I am running from?</em>
 * ({@link #runPassesWithin}). Getting the second one wrong is worse than having
 * no plan at all: a settler who sprints for a door on the far side of a creeper
 * has used their whole head start to close the distance.
 */
public final class Escape {

    private Escape() {
    }

    /**
     * How far the point {@code (px, pz)} lies from the straight run
     * {@code (ax, az) → (bx, bz)}.
     *
     * <p>The run is a segment and not an infinite line, which is the whole point:
     * a creeper standing well behind a settler is not "near the way ahead" merely
     * because it sits on the line extended backwards.
     */
    public static double distanceToRun(double ax, double az, double bx, double bz,
                                       double px, double pz) {
        double runX = bx - ax;
        double runZ = bz - az;
        double lengthSq = runX * runX + runZ * runZ;
        double alongX;
        double alongZ;
        if (lengthSq <= 0.0) {
            // A run of no length is a point; the two ends are the same place.
            alongX = ax;
            alongZ = az;
        } else {
            double along = ((px - ax) * runX + (pz - az) * runZ) / lengthSq;
            along = Math.max(0.0, Math.min(1.0, along));
            alongX = ax + along * runX;
            alongZ = az + along * runZ;
        }
        double dx = px - alongX;
        double dz = pz - alongZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Whether running from {@code (ax, az)} to {@code (bx, bz)} would take
     * somebody within {@code radius} of {@code (px, pz)}.
     *
     * <p>Straight-line, deliberately. The real path bends, but it bends
     * <em>around</em> obstacles toward the same destination, so a destination
     * whose straight line runs through the blast is a destination the path will
     * carry them into as well. Refusing that target is cheap; being wrong about
     * it costs the settler.
     */
    public static boolean runPassesWithin(double ax, double az, double bx, double bz,
                                          double px, double pz, double radius) {
        return distanceToRun(ax, az, bx, bz, px, pz) <= radius;
    }

    /**
     * The distance at which somebody walking has to notice a threat that walks,
     * if they are never to come inside {@code hurtRadius} of it.
     *
     * <p>Three terms, and each one is a thing that actually happens:
     *
     * <ol>
     *   <li>{@code hurtRadius} — the margin they must still have when everything
     *       else has been spent.</li>
     *   <li>{@code threatSpeed * reactionTicks} — ground given away for nothing,
     *       between the threat coming into view and the settler being under way.
     *       Somebody mid-swing on a log does not turn on the instant.</li>
     *   <li>the settling term — ground given away while the escape path is still
     *       bending. A flee path leaves at an angle, rounds a fence and only then
     *       points away, so over the first seconds the settler's outward progress
     *       is {@code fleeSpeed * pathEfficiency} rather than {@code fleeSpeed}.
     *       Where that is slower than the threat, the difference is lost for
     *       {@code settlingTicks}; where it is faster, nothing is lost and the
     *       term is zero, because a settler who is already pulling away does not
     *       un-pull-away.</li>
     * </ol>
     *
     * <p>Speeds are blocks per tick — the movement-speed attribute times the
     * navigation modifier, which is the number the game actually walks people at.
     */
    public static double noticeDistance(double hurtRadius, double threatSpeed, double fleeSpeed,
                                        int reactionTicks, int settlingTicks,
                                        double pathEfficiency) {
        double outward = fleeSpeed * pathEfficiency;
        double lostPerTick = Math.max(0.0, threatSpeed - outward);
        return hurtRadius + threatSpeed * reactionTicks + lostPerTick * settlingTicks;
    }
}
