package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides what a settlement builds next, and where.
 *
 * <p>Two independent questions, kept separate on purpose:
 * <ul>
 *   <li><strong>What</strong> — the settlement builds the most important thing it
 *       is currently short of. See {@link #chooseNext}.</li>
 *   <li><strong>Where</strong> — plots are handed out along expanding rings around
 *       the centre, in a fixed order. See {@link #plotFor}.</li>
 * </ul>
 *
 * <p>Everything here is deterministic. Same settlement state in, same decision out,
 * every time — which is what lets the tests assert on specific choices, and what
 * will let two players on a server see the same town.
 *
 * <p>Plain-English write-up of these rules: {@code BUILD_DECISIONS.md}.
 */
public final class BuildPlanner {

    /** Minimum plots per ring. */
    static final int MIN_SLOTS_PER_RING = 8;

    /** Distance from the centre to the first ring of plots. */
    static final int FIRST_RING_RADIUS = 12;

    /** Added to the radius for each subsequent ring. */
    static final int RING_SPACING = 10;

    /** Aim for roughly this many blocks between neighbouring plots in a ring. */
    public static final int TARGET_PLOT_SPACING = 10;

    /** Keeps the claim boundary a little beyond the outermost building. */
    static final int CLAIM_MARGIN = 8;

    private BuildPlanner() {
    }

    /**
     * Picks the next building, or empty if the settlement wants nothing right now.
     *
     * <p>A type is a candidate when the settlement is big enough for it and has
     * fewer than it wants. Among candidates, highest priority wins; ties go to the
     * larger shortfall, then to whichever id sorts first so the result is stable.
     */
    public static Optional<BuildingType> chooseNext(Settlement settlement, List<BuildingType> catalogue) {
        int population = settlement.population();

        return catalogue.stream()
                .filter(type -> population >= type.minPopulation())
                .filter(type -> shortfall(settlement, type, population) > 0)
                .max(Comparator
                        .comparingInt(BuildingType::priority)
                        .thenComparingInt((BuildingType type) -> shortfall(settlement, type, population))
                        .thenComparing(BuildingType::id, Comparator.reverseOrder()));
    }

    /** How many more of this type the settlement wants than it has. */
    public static int shortfall(Settlement settlement, BuildingType type, int population) {
        return type.desiredCount(population) - settlement.countBuildings(type.id());
    }

    /**
     * How many plots a ring holds: enough that neighbours sit roughly
     * {@link #TARGET_PLOT_SPACING} apart along the circumference.
     *
     * <p>A constant eight per ring was the first version, and produced an
     * eight-legged star — the same eight angles repeated at every radius, with
     * the gaps between spokes growing forever (found in the first live
     * playtest). Packing by circumference fills the space near the town before
     * stepping outward.
     */
    static int slotsInRing(int ring) {
        int radius = FIRST_RING_RADIUS + ring * RING_SPACING;
        int byCircumference = (int) Math.floor(2 * Math.PI * radius / TARGET_PLOT_SPACING);
        return Math.max(MIN_SLOTS_PER_RING, byCircumference);
    }

    /**
     * Where the nth building goes: rings filled densely from the inside out, with
     * alternate rings staggered by half a slot so plots never line up into spokes.
     * Deterministic, never reuses a plot.
     */
    public static SimPos plotFor(SimPos centre, int index) {
        int ring = 0;
        int slot = index;
        while (slot >= slotsInRing(ring)) {
            slot -= slotsInRing(ring);
            ring++;
        }
        int slots = slotsInRing(ring);
        int radius = FIRST_RING_RADIUS + ring * RING_SPACING;
        double slice = 2 * Math.PI / slots;
        double angle = slot * slice + (ring % 2) * slice / 2;

        int x = centre.x() + (int) Math.round(radius * Math.cos(angle));
        int z = centre.z() + (int) Math.round(radius * Math.sin(angle));
        return new SimPos(x, centre.y(), z);
    }

    /** Claim radius needed to keep the given plot inside the settlement's territory. */
    public static int claimRadiusFor(SimPos centre, SimPos plot) {
        return (int) Math.ceil(centre.horizontalDistance(plot)) + CLAIM_MARGIN;
    }
}
