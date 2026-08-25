package com.kingdoms.sim.work;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Settlement;

/**
 * A public work with a physical job somewhere in it.
 *
 * <p>Buildings have always been raised by hands. Everything else a town made —
 * its wall, its roads — appeared: the simulation decided how much of it existed
 * and a layer stamped that much into the world, so a village grew a hundred
 * feet of palisade and a street between two houses while every builder in it
 * stood somewhere else entirely.
 *
 * <p>Rather than write a worker for each of those and another for whatever
 * comes next, this is the shape they have in common. A public work knows three
 * things and nothing else:
 *
 * <ul>
 *   <li><strong>Where the next job is.</strong> One position, the one a settler
 *       has to actually stand at. Null when there is nothing to do.</li>
 *   <li><strong>What it costs.</strong> Materials, coin, or both, for that one
 *       station — charged when somebody is standing there ready to do it, never
 *       before.</li>
 *   <li><strong>How to record it done.</strong> Sim-side bookkeeping only. What
 *       blocks get laid is the platform's business, not this.</li>
 * </ul>
 *
 * <p>One worker loop consumes all of them, so adding a public work later — a
 * quarry, a bridge, a drained field — is a class that answers those three
 * questions rather than another worker, another tick pass, and another set of
 * rules about who is free to do it.
 *
 * <p><strong>Order matters.</strong> A settlement offers its works in the order
 * it thinks they matter, and the foreman takes the first one with a job in a
 * loaded chunk. That is the whole of the priority system, and it is deliberately
 * this crude: a town with a half-built wall and a half-built road should finish
 * one of them, not alternate between the two forever.
 */
public interface Worksite {

    /** What this is, for the event log and for anybody reading a report. */
    String name();

    /**
     * Where somebody has to stand to advance this, or null if nothing is
     * outstanding.
     *
     * <p>Always the <em>next</em> one rather than the nearest: a wall raised in
     * the order it was staked closes as a line, and one raised nearest-first
     * closes as a scatter of disconnected posts.
     */
    SimPos nextStation(Settlement settlement);

    /**
     * Takes what one station costs, and says whether the town could pay.
     *
     * <p>All or nothing. A town that has the timber but not the coin must not
     * lose the timber pretending otherwise.
     */
    boolean pay(Settlement settlement);

    /** Records one station done. Called only after {@link #pay} succeeded. */
    void completeOne(Settlement settlement);

    /**
     * Whether this work is worth pulling a builder off idling for at all.
     *
     * <p>Separate from having a station, because a work can have plenty left to
     * do and still be something the town should not be spending on yet.
     */
    default boolean isWorthStarting(Settlement settlement) {
        return nextStation(settlement) != null;
    }
}
