package com.civilization.sim.person;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * When the town stops working and starts walking home.
 *
 * <p>{@link NightRest} already sends people to bed once it is dark. That turned
 * out not to be the same thing as getting them home: a farmer on a field a
 * hundred and sixty-five blocks out who downs tools at dusk spends the first
 * eight minutes of the night walking through a wood, which is where the
 * measurement found most of the seventeen bodies. The bed was never the problem.
 * The walk was.
 *
 * <p>So there is a curfew, and its whole content is that the walk happens in
 * daylight. It is a lead time rather than a clock hour, and the lead is
 * <em>per person</em>: the miller over the road leaves at the last minute and the
 * forester on the far belt leaves first, because they have the same arrival to
 * make and different distances to make it in. Ordering the departures that way is
 * the only thing that gets everybody inside by dark, and it is also simply what a
 * village does.
 *
 * <p>Here in {@code common} for the same reason {@link NightRest} is: it is
 * arithmetic on a tick count and a distance, none of it needs a world, and a
 * number that decides whether a town survives the night should not be one that
 * can only be checked by playing.
 */
public final class Curfew {

    private Curfew() {
    }

    /**
     * How long before dark the town starts sending people in: 1,500 ticks.
     *
     * <p>Seventy-five seconds, which is a sixteenth of a day. At a citizen's
     * {@link #BLOCKS_PER_TICK} that is five hundred and twenty-five blocks of
     * walking — comfortably more than the far corner of any claim, which is the
     * point: the lead is the ceiling on how early anybody leaves, not the time
     * everybody leaves. Somebody thirty blocks out keeps working until ninety
     * ticks before dusk.
     *
     * <p>Configurable in the sense that the callers take it as a parameter; this
     * is what they pass when nobody has said otherwise.
     */
    public static final long LEAD_TICKS = 1500L;

    /**
     * How fast a citizen covers ground: 0.35 blocks a tick.
     *
     * <p>{@code Pace.WALK} is a navigation modifier on a 0.5 movement-speed
     * attribute, which comes to this. It lives here as a plain number because the
     * arithmetic that decides when somebody sets off cannot import the platform's
     * pace constant, and the two must not drift — {@code PaceTest} is where that
     * is held to.
     */
    public static final double BLOCKS_PER_TICK = 0.35;

    /**
     * Ticks of slack on every walk home: 200.
     *
     * <p>Ten seconds. A straight-line distance is not a walk: a path bends round
     * houses, a gate has to be opened, and somebody who set off with exactly
     * enough time arrives exactly at dusk, which is the moment the spawning
     * starts. It is deliberately a flat figure rather than a percentage, because
     * what it is paying for — the gate, the door, the last few steps to the
     * mattress — does not get longer with the journey.
     */
    public static final long SLACK_TICKS = 200L;

    /**
     * How long before dusk somebody this far from home has to set off.
     *
     * <p>Clamped to {@link #LEAD_TICKS} at the top, because a curfew that started
     * at noon for somebody two hundred blocks out would be a town that stops
     * working at noon. Anybody beyond the lead's reach leaves as early as the
     * curfew allows and is simply late; that is a fact about the claim being too
     * big rather than something the arithmetic can fix.
     *
     * @param distanceHome blocks from where they are standing to their own door
     * @param lead         the town's curfew lead, normally {@link #LEAD_TICKS}
     */
    public static long leadFor(double distanceHome, long lead) {
        if (distanceHome <= 0) {
            return 0L;
        }
        long walk = (long) Math.ceil(distanceHome / BLOCKS_PER_TICK) + SLACK_TICKS;
        return Math.max(0L, Math.min(lead, walk));
    }

    /** The same, at the default lead. */
    public static long leadFor(double distanceHome) {
        return leadFor(distanceHome, LEAD_TICKS);
    }

    /**
     * How long until the light goes, in ticks. Zero once it already has.
     *
     * <p>Read off {@link NightRest#DUSK} rather than kept here, so the hour the
     * town walks home by and the hour it goes to bed at cannot come to disagree.
     */
    public static long untilDusk(long dayTime) {
        long time = Math.floorMod(dayTime, NightRest.DAY);
        return NightRest.isNight(time) ? 0L : NightRest.DUSK - time;
    }

    /**
     * Whether this person should have stopped working and started walking.
     *
     * <p>True through the night as well as through the curfew, so a caller has
     * one question to ask rather than two — somebody who was out when it got dark
     * is not less under curfew for having missed it.
     *
     * @param dayTime      the level's clock
     * @param distanceHome blocks from where they stand to their own door
     * @param lead         the town's curfew lead
     */
    public static boolean sendsHome(long dayTime, double distanceHome, long lead) {
        if (NightRest.isNight(dayTime)) {
            return true;
        }
        return untilDusk(dayTime) <= leadFor(distanceHome, lead);
    }

    /**
     * Whether the town as a whole is under curfew: the light is going, or gone.
     *
     * <p>The question the <em>work planners</em> ask, as opposed to the one an
     * individual asks. A town under curfew hands out no new errands to civilians
     * — sending somebody on a haul across the village at dusk is the town itself
     * putting them outdoors after dark, which no amount of care about their walk
     * home can undo.
     *
     * <p>Measured at the full lead rather than per person on purpose. An errand
     * is not where somebody already is; it is somewhere else, and how far is not
     * known until it is given out.
     */
    public static boolean isCurfew(long dayTime, long lead) {
        return NightRest.isNight(dayTime) || untilDusk(dayTime) <= lead;
    }

    /** The same, at the default lead. */
    public static boolean isCurfew(long dayTime) {
        return isCurfew(dayTime, LEAD_TICKS);
    }

    /**
     * Whether a civilian may be handed work at all right now.
     *
     * <p>Guards are exempt from everything here — they keep the night, and the
     * whole point of the beat is that somebody is out in it.
     */
    public static boolean maySetToWork(long dayTime, boolean guard, long lead) {
        return guard || !isCurfew(dayTime, lead);
    }

    /**
     * One person's walk home, for ordering the departures.
     *
     * @param who      whatever the caller identifies people by
     * @param distance blocks from where they stand to their own door
     */
    public record Walk<T>(T who, double distance) {
    }

    /**
     * Who leaves first: the longest walk, in front.
     *
     * <p>The ordering is the feature. Everybody is aiming at the same moment —
     * indoors by dusk — so the person with the furthest to go has to set off
     * soonest, and a town that sent its whole workforce home on one bell would
     * put its outlying farmers on the road at exactly the time its millers were
     * already closing their doors. Sorting the departures by distance is what
     * turns one bell into a staggered walk home.
     *
     * <p>Stable on the distance so a caller iterating this gets the same order
     * every pass, which matters: the order decides who is steered and who is left
     * working, and a list that reshuffled would have somebody set off and turn
     * back.
     */
    public static <T> List<Walk<T>> departureOrder(List<Walk<T>> walks) {
        List<Walk<T>> ordered = new ArrayList<>(walks);
        ordered.sort(Comparator.comparingDouble((Walk<T> walk) -> -walk.distance()));
        return List.copyOf(ordered);
    }

    /**
     * Whether the unwatched clock should idle a town's civilian work.
     *
     * <p>Its own name rather than a second call to {@link #isCurfew}, because it
     * is a different decision about the same moment and the two have different
     * consequences. A watched town stopping work at dusk is people walking home.
     * An unwatched one stopping is arithmetic not being done, which is a town
     * producing nine-sixteenths of what it used to — and the doctrine that both
     * fidelities produce the same outcome cuts both ways: a watched town that
     * falls behind an unwatched one is a bug, and a town that starves because it
     * now sleeps is a worse one.
     *
     * <p>See {@code NightEconomyTest}, which measures what this costs over a
     * thousand steps, and the changelog entry, which says what the measurement
     * decided.
     */
    public static boolean idlesUnwatchedWork(long dayTime, long lead) {
        return isCurfew(dayTime, lead);
    }
}
