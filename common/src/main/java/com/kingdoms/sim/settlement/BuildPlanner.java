package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    /**
     * Materials an unwatched build spends per builder-step.
     *
     * <p>The observed path charges per block laid, which it can do because it has
     * the block plan. Away from a watcher there is no plan — the chunk is not even
     * loaded — so the cost is estimated from the work instead. The numbers are set
     * to land near what a cabin actually costs, so a town cannot out-build its
     * stores simply by being unobserved.
     */
    public static final int WOOD_PER_WORK = 4;
    public static final int STONE_PER_WORK = 2;

    /**
     * How many plots a settlement will look at before it settles for one.
     *
     * <p>Bounded on purpose: a town ringed by water or cliffs must still build
     * something rather than searching forever. Past this it takes what it can get,
     * and the excavation deals with whatever that turns out to be.
     */
    public static final int PLOT_ATTEMPTS = 12;

    /** Half-span probed when judging a plot, before the real building is chosen. */
    public static final int PLOT_PROBE_RADIUS = 6;

    /** Which building lets a town make a thing it has run out of. */
    public static final Map<String, String> PRODUCER_OF = Map.of(
            TownStores.WOOD, "kingdoms:lumber_camp",
            TownStores.STONE, "kingdoms:mine");

    /** Builder-steps for a producer ordered out of turn; the catalogue cost is used when known. */
    public static final int PRODUCER_WORK = 30;

    /** Blueprint for a run of steps up to a doorway nobody can reach. */
    public static final String ACCESS_STAIRS = "kingdoms:stairs";

    /** Builder-steps per block of climb, so a taller flight is a longer job. */
    public static final int STAIR_WORK_PER_BLOCK = 3;

    /**
     * Orders the building that would fix a shortage, ahead of everything else.
     *
     * <p>This is what stops limited supply becoming a deadlock. A town short of
     * timber wants a house it cannot pay for; the house is the highest-priority
     * thing it lacks, so it would sit on that job forever. Noticing the shortage
     * and going to build a lumber camp instead is both the way out and the
     * obviously sensible thing for a town to do.
     *
     * @return true if a producer was ordered by this call
     */
    public static boolean requestProducer(Settlement settlement, String resource, long step) {
        String producer = PRODUCER_OF.get(resource);
        if (producer == null) {
            return false;
        }
        for (Building standing : settlement.buildings()) {
            if (standing.blueprintId().equals(producer)) {
                return false;   // already have one; the shortage is a real shortage
            }
        }
        for (BuildTask queued : settlement.buildQueue()) {
            if (queued.blueprintId().equals(producer)) {
                return false;
            }
        }
        int work = settlement.catalogue().stream()
                .filter(type -> type.id().equals(producer))
                .mapToInt(BuildingType::workCost)
                .findFirst()
                .orElse(PRODUCER_WORK);
        // A real plot, not the centre. Two producers ordered this way both landed
        // on the town square and were built on top of one another.
        SimPos plot = settlement.takeNextPlot();
        settlement.enqueueUrgent(new BuildTask(producer, plot, work));
        settlement.logEvent(step, "Out of " + resource + " — work starts on a "
                + producer.substring(producer.indexOf(':') + 1).replace('_', ' '));
        return true;
    }

    private BuildPlanner() {
    }

    /**
     * Orders steps up to a doorway a resident cannot reach.
     *
     * <p>Houses are planted by geometry, not by surveyors: on a slope, or where
     * the foundation had to pillar up out of a dip, a door can end up above
     * anything a person can climb. Rather than leaving somebody stranded outside
     * their own home forever, the settlement notices and builds them a way up.
     *
     * <p>The job jumps the queue — a family locked out is more pressing than the
     * next workshop — and is refused if a flight is already ordered or standing,
     * so a stuck resident cannot flood the queue with duplicates.
     *
     * @return true if a new order was placed
     */
    public static boolean requestAccessStairs(Settlement settlement, SimPos doorway,
                                              int climb, long step) {
        for (BuildTask queued : settlement.buildQueue()) {
            if (queued.blueprintId().equals(ACCESS_STAIRS) && queued.origin().equals(doorway)) {
                return false;
            }
        }
        for (Building standing : settlement.buildings()) {
            if (standing.blueprintId().equals(ACCESS_STAIRS) && standing.origin().equals(doorway)) {
                return false;
            }
        }
        int work = Math.max(4, climb * STAIR_WORK_PER_BLOCK);
        settlement.enqueueUrgent(new BuildTask(ACCESS_STAIRS, doorway, work));
        settlement.logEvent(step, "Steps ordered up to a door nobody could reach, at " + doorway);
        return true;
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
