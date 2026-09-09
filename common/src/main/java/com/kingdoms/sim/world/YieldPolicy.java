package com.kingdoms.sim.world;

import com.kingdoms.sim.settlement.TownStores;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How much of the simulation clock's yield a town is actually credited.
 *
 * <p>The mod has two fidelities and one rule holding them together: where there
 * is a hand there is no clock. A lumberjack a player can see fells an actual
 * tree and the log is real; the same lumberjack out past the observed radius is
 * a number, and the clock credits the timber on his behalf so that walking away
 * does not stop a town dead.
 *
 * <p>That second half is abstraction, and until now it was total and
 * unadjustable. Every log, every block of stone, every loaf of an unwatched
 * town appeared out of arithmetic — and so did the <em>watched floor</em>, the
 * yield credited to a camp a player is standing in when the real axes have not
 * managed a swing in {@code WATCHED_WORK_GRACE_STEPS}. Some players want a
 * world where nothing is conjured; some want the towns they left behind to
 * still be there when they get back. This record is where that is decided.
 *
 * <p>Two tables, both keyed by the {@link TownStores} resource id:
 *
 * <ul>
 *   <li>{@link #unwatchedPercent} — the share credited when nobody is within the
 *       observed radius of the producing building. Default 0: an unwatched town
 *       keeps working — building, hauling, eating, spending what it holds —
 *       and conjures nothing at all while it does. A simulation worth the name
 *       carries on with no player nearby; it does not have to be paid to.</li>
 *   <li>{@link #watchedFloorPercent} — the share credited when a player <em>is</em>
 *       near but the hands have not produced anything real within the grace
 *       steps. Default 0: in front of a player, only real work counts.</li>
 * </ul>
 *
 * <p>A resource missing from a table is credited in full. That is what makes
 * {@link #FULL} — two empty tables — mean exactly the behavior that shipped
 * before this record existed, and it is why a resource the clock learns to
 * credit later is never silently throttled by an old config file.
 *
 * <p>Real harvests by embodied hands are never scaled by anything here. Neither
 * is anything that is not a gain: hauling between stores, a refund from a pulled
 * fence post, the forge turning iron into tools, a caravan's barter at the inn.
 * The knob is for goods the clock conjures, not for goods that change hands.
 */
public record YieldPolicy(
        Map<String, Integer> unwatchedPercent,
        Map<String, Integer> watchedFloorPercent
) {

    /** Everything the simulation clock can credit out of nothing. */
    public static final List<String> SCALED_RESOURCES = List.of(
            TownStores.WOOD,
            TownStores.STONE,
            TownStores.FOOD,
            TownStores.IRON,
            TownStores.SAPLINGS);

    public static final int DEFAULT_UNWATCHED_PERCENT = 0;
    public static final int DEFAULT_WATCHED_FLOOR_PERCENT = 0;

    /**
     * Everything abstract, which is what the mod did before the tables existed.
     *
     * <p>Both tables empty rather than filled with hundreds, so that this stays
     * the meaning of "no policy at all" however the resource list grows.
     */
    public static final YieldPolicy FULL = new YieldPolicy(Map.of(), Map.of());

    /**
     * The shipped defaults: nothing conjured, anywhere, ever.
     *
     * <p>Zero on both tables. A town nobody is watching still runs — it builds,
     * it hauls, it eats, it spends what it holds and its people go on living
     * their lives — but it gains nothing it did not already have. Every log,
     * every block of stone, every loaf has to come from somewhere now: a real
     * hand on a real tree, a harvest somebody could have stood and watched,
     * wild food actually growing on the ground the camp is pitched on.
     *
     * <p>Raise {@code economy.unwatched_yield_percent} if you would rather the
     * towns you left behind kept growing on their own. 70 is what shipped
     * before; 100 is total abstraction.
     */
    public static final YieldPolicy DEFAULTS =
            uniform(DEFAULT_UNWATCHED_PERCENT, DEFAULT_WATCHED_FLOOR_PERCENT);

    public YieldPolicy {
        Objects.requireNonNull(unwatchedPercent, "unwatchedPercent");
        Objects.requireNonNull(watchedFloorPercent, "watchedFloorPercent");
        unwatchedPercent = Collections.unmodifiableMap(new LinkedHashMap<>(unwatchedPercent));
        watchedFloorPercent = Collections.unmodifiableMap(new LinkedHashMap<>(watchedFloorPercent));
    }

    /** One pair of numbers applied to every resource the clock can credit. */
    public static YieldPolicy uniform(int unwatched, int watchedFloor) {
        Map<String, Integer> a = new LinkedHashMap<>();
        Map<String, Integer> b = new LinkedHashMap<>();
        for (String resource : SCALED_RESOURCES) {
            a.put(resource, clamp(unwatched));
            b.put(resource, clamp(watchedFloor));
        }
        return new YieldPolicy(a, b);
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    /** The share of an unwatched building's clock yield the town is credited. */
    public int unwatched(String resource) {
        return clamp(unwatchedPercent.getOrDefault(resource, 100));
    }

    /** The share credited to a watched building whose hands have gone quiet. */
    public int watchedFloor(String resource) {
        return clamp(watchedFloorPercent.getOrDefault(resource, 100));
    }

    /** Whichever of the two applies, given whether anybody is standing there. */
    public int percent(boolean watched, String resource) {
        return watched ? watchedFloor(resource) : unwatched(resource);
    }

    /** True when both tables are 100 across the board — nothing is being held back. */
    public boolean isFull() {
        for (String resource : SCALED_RESOURCES) {
            if (unwatched(resource) != 100 || watchedFloor(resource) != 100) {
                return false;
            }
        }
        return true;
    }

    /**
     * The one number both tables use, or -1 when the resources disagree.
     *
     * <p>For the sake of {@code /civ info}, which would rather print
     * "unwatched 70%" than five identical lines.
     */
    public int uniformUnwatched() {
        return uniformValue(true);
    }

    public int uniformWatchedFloor() {
        return uniformValue(false);
    }

    private int uniformValue(boolean unwatched) {
        int first = unwatched ? unwatched(SCALED_RESOURCES.get(0))
                : watchedFloor(SCALED_RESOURCES.get(0));
        for (String resource : SCALED_RESOURCES) {
            int here = unwatched ? unwatched(resource) : watchedFloor(resource);
            if (here != first) {
                return -1;
            }
        }
        return first;
    }
}
