package com.kingdoms.sim.combat;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a bowman should stand when the thing he is shooting at is behind a wall.
 *
 * <p>{@link GuardStance} answers <em>how far away</em> to be. It cannot answer
 * <em>where</em>, because every point at the right distance is the same point to
 * arithmetic and they are emphatically not the same point in a world with a hill
 * in it. A guard holding the band with a barn between him and the creeper is
 * standing exactly where he was told to stand and is of no use to anybody.
 *
 * <p>So: a ring of candidate stands around the creeper at the band's radius,
 * tried nearest-first, and the first one the caller says is usable wins. What
 * "usable" means is entirely the caller's business — it is a
 * {@link Predicate} over positions, and in the world that predicate is a line-of-sight
 * clip and an arrow path with nobody's farmer in it. None of that can be seen
 * from here, which is the point: the geometry is arithmetic, it is testable
 * without a world, and it lives here.
 *
 * <p>The search is bounded on purpose — a fixed ring, a fixed count, evaluated
 * lazily in nearest-first order, so a guard whose first candidate works has cost
 * one query and a guard with nowhere to go has cost {@link #CANDIDATES} of them
 * and then given up. There is no widening second pass and no recursion: a guard
 * who cannot find a firing point in one ring is a guard the view falls back to
 * closing on, not one who spends a tick searching harder.
 */
public final class FiringPoint {

    private FiringPoint() {
    }

    /**
     * How far from the creeper the ring is drawn: the middle of the band.
     *
     * <p>The middle rather than either edge, because both edges are one step
     * from being wrong. Stand him at the near edge and the creeper walking one
     * block toward him puts him inside the blast and straight back into
     * {@link GuardStance.Move#BACK_OFF}; stand him at the far edge and one block
     * the other way is out of range and he closes again. Eleven leaves three
     * blocks of slack either side, so a firing point stays a firing point while
     * the creeper mills about.
     */
    public static final double RING_RADIUS = (GuardStance.BAND_NEAR + GuardStance.BAND_FAR) / 2.0;

    /**
     * How many stands are tried, spaced evenly round the ring.
     *
     * <p>Twelve is every thirty degrees, which at eleven blocks out is a
     * candidate every five and a half blocks of arc — fine enough to find the
     * way round the end of an ordinary wall or barn, coarse enough that the
     * whole search is a dozen queries and no more. The number is a budget, not a
     * resolution: doubling it would find slightly better stands and cost twice
     * as much every second for every guard fighting a creeper.
     */
    public static final int CANDIDATES = 12;

    /**
     * The nearest stand on the ring that the caller can use, if there is one.
     *
     * <p>Candidates are ordered by how far the guard has to walk and handed to
     * {@code clear} one at a time until one is accepted, so the predicate — which
     * is the expensive half — runs as few times as the answer allows.
     *
     * @param guard  where the guard is standing now
     * @param target what he is shooting at
     * @param clear  whether a stand is one he could actually shoot from; asked at
     *               most once per candidate, and not at all for candidates ruled
     *               out by the blast
     * @return the stand to walk to, or empty if the ring offers nothing
     */
    public static Optional<SimPos> nearest(SimPos guard, SimPos target, Predicate<SimPos> clear) {
        return nearest(guard, target, RING_RADIUS, CANDIDATES, clear);
    }

    /**
     * As {@link #nearest(SimPos, SimPos, Predicate)}, with the ring spelled out.
     *
     * <p>No candidate inside {@link GuardStance#HURT_RADIUS} of the target is
     * ever returned, whatever radius is asked for. That is checked here rather
     * than left to the caller's arithmetic: the one thing this function must
     * never do is send a bowman to stand in a blast, and a radius wide enough to
     * make that impossible is an assumption about a parameter rather than a
     * guarantee about a result.
     *
     * @param radius  how far from the target to draw the ring
     * @param samples how many stands to space round it
     */
    public static Optional<SimPos> nearest(SimPos guard, SimPos target, double radius,
                                           int samples, Predicate<SimPos> clear) {
        if (samples <= 0 || radius <= 0) {
            return Optional.empty();
        }
        // A set, because rounding to whole blocks collides: a small ring has
        // fewer distinct standing blocks on it than it has sample angles, and
        // asking the world the same question twice is the one waste worth
        // spending a hash on.
        Set<SimPos> ring = new LinkedHashSet<>();
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i / samples;
            int x = target.x() + (int) Math.round(radius * Math.cos(angle));
            int z = target.z() + (int) Math.round(radius * Math.sin(angle));
            ring.add(new SimPos(x, target.y(), z));
        }

        List<SimPos> reachable = new ArrayList<>();
        for (SimPos stand : ring) {
            // Rounding to blocks can pull a candidate inboard of the radius that
            // was asked for, so the blast is checked against the position and not
            // against the parameter.
            if (stand.horizontalDistance(target) > GuardStance.HURT_RADIUS) {
                reachable.add(stand);
            }
        }
        reachable.sort(Comparator.comparingLong(stand -> stand.horizontalDistanceSq(guard)));

        for (SimPos stand : reachable) {
            if (clear.test(stand)) {
                return Optional.of(stand);
            }
        }
        return Optional.empty();
    }
}
