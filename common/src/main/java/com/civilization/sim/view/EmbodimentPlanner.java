package com.civilization.sim.view;

import com.civilization.sim.person.Person;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which people should exist as entities right now.
 *
 * <p>The decision lives here, in the loader-free core, so the state machine can be
 * unit-tested without a game: the platform layer only executes the plan (spawn
 * these, despawn those). This is the discipline that keeps hydration bugs — the
 * classic killer of view layers — findable in a millisecond test instead of a
 * twenty-minute play session.
 *
 * <p>Two rules:
 * <ul>
 *   <li><strong>Embody</strong> a person when a player is within the observed
 *       radius of them, until the settlement's cap is reached.</li>
 *   <li><strong>Release</strong> an embodied person only when every player is
 *       beyond the observed radius <em>plus</em> {@link #RELEASE_MARGIN}. The
 *       margin is hysteresis: without it, a person standing right on the radius
 *       would flicker in and out of existence as the player shifts.</li>
 * </ul>
 *
 * <p>And one exception, which is the price of judging watchedness for the whole
 * claim. A watched town's work is all done by hand — see
 * {@code Settlement.isWatched} — and its claim is wider than the observed
 * radius, so the far half of it is work the clock will not do and distance alone
 * would never put a body near. Both rules therefore bend for the people such a
 * town is actually waiting on ({@code Settlement.needsHandsFrom}): they are
 * embodied anywhere in a watched town and not released while it stays watched.
 * Without that, a far plot inside a watched claim waits on hands that were
 * released the moment they walked out of sight to reach it, which is a deadlock
 * rather than a doctrine. Everybody else is still judged by distance: a child or
 * an idler two hundred blocks off has no reason to be an entity.
 */
public final class EmbodimentPlanner {

    /** Extra distance beyond the observed radius before a shown person is hidden. */
    public static final double RELEASE_MARGIN = 32.0;

    /** Who to spawn and who to despawn. Execution order: releases first, then embodiments. */
    public record Plan(List<Person> toEmbody, List<Person> toRelease) {

        public boolean isEmpty() {
            return toEmbody.isEmpty() && toRelease.isEmpty();
        }
    }

    private EmbodimentPlanner() {
    }

    public static Plan plan(Settlement settlement, WorldBridge bridge, SimSettings settings) {
        List<Person> toEmbody = new ArrayList<>();
        List<Person> toRelease = new ArrayList<>();

        int embodied = (int) settlement.residents().stream().filter(Person::isEmbodied).count();
        boolean townWatched = settlement.claimIsWatched(bridge, settings);

        for (Person person : settlement.residents()) {
            boolean needed = townWatched && settlement.needsHandsFrom(person);
            if (person.isEmbodied()) {
                if (!needed && !bridge.playerWithin(person.position(),
                        settings.observedRadius() + RELEASE_MARGIN)) {
                    toRelease.add(person);
                }
            } else {
                if (embodied + toEmbody.size() < settings.embodyCapPerSettlement()
                        && (needed || bridge.playerWithin(person.position(),
                                settings.observedRadius()))) {
                    toEmbody.add(person);
                }
            }
        }
        return new Plan(toEmbody, toRelease);
    }
}
