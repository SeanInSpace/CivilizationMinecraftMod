package com.kingdoms.sim.view;

import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimSettings;

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

        for (Person person : settlement.residents()) {
            if (person.isEmbodied()) {
                if (!bridge.playerWithin(person.position(), settings.observedRadius() + RELEASE_MARGIN)) {
                    toRelease.add(person);
                }
            } else {
                if (embodied + toEmbody.size() < settings.embodyCapPerSettlement()
                        && bridge.playerWithin(person.position(), settings.observedRadius())) {
                    toEmbody.add(person);
                }
            }
        }
        return new Plan(toEmbody, toRelease);
    }
}
