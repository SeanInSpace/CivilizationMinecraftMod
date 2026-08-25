package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.Sighting;
import com.kingdoms.sim.world.SimContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Hostile pressure — the reason guards and watchtowers exist.
 *
 * <p>The two fidelities resolve the same raid differently:
 * <ul>
 *   <li><strong>Observed</strong> (a player can see the settlement): real hostiles
 *       are spawned at the edge of town and entity combat decides. Guards fight,
 *       villagers die if the line breaks, and every death flows through the
 *       existing view-death path.</li>
 *   <li><strong>Unobserved</strong>: no entities exist, so the raid resolves as
 *       arithmetic — defense power versus raid strength — and the outcome is
 *       written into the settlement's event log. Come back later and the history
 *       tells you what happened while you were away.</li>
 * </ul>
 *
 * <p><strong>No randomness.</strong> Schedules and strengths hash the settlement's
 * id with the step number, so the same world replays identically — the property
 * the whole test suite leans on. Plain-English write-up: {@code DEFENSE.md}.
 */
public final class RaidPlanner {

    /** Settlements below this population are beneath raiders' notice. */
    public static final int MIN_POPULATION_FOR_RAIDS = 6;

    /** Defense contributed per guard. Structures add their own defenseBonus. */
    public static final int GUARD_POWER = 2;

    /**
     * No raid exceeds this, whatever the population. Protects against oversized
     * legacy towns — an uncapped formula once spawned 131 zombies at a
     * thousand-person settlement.
     */
    public static final int MAX_RAID_STRENGTH = 16;

    private RaidPlanner() {
    }

    /** Called once per settlement step: track visible hostiles, then check the raid clock. */
    public static void advance(Settlement settlement, SimContext ctx) {
        // Threat mirrors what the town's people can see, whenever anybody is
        // there to see it. Zero when abstract — there are no eyes and no
        // hostiles, so threat there comes from raid events instead.
        Sighting seen = ctx.bridge().hostilesSeen(settlement.centre(), settlement.claimRadius());
        settlement.sighted(seen);
        if (outmatched(settlement, seen)) {
            settlement.soundAlarm();
        }

        if (!ctx.settings().raidsEnabled()) {
            return;
        }
        if (settlement.population() < MIN_POPULATION_FOR_RAIDS) {
            return;
        }
        if (!raidDue(settlement, ctx)) {
            return;
        }
        execute(settlement, ctx, raidStrength(settlement, ctx.step()));
    }

    /**
     * Each settlement's raid fires once per interval, at an offset hashed from its
     * id — so towns are raided on their own clocks, not all in the same step.
     */
    public static boolean raidDue(Settlement settlement, SimContext ctx) {
        int interval = ctx.settings().raidIntervalSteps();
        long offset = Math.floorMod(mix(settlement.id().value().hashCode(), 0x9E3779B9L), interval);
        return Math.floorMod(ctx.step() - offset, interval) == 0;
    }

    /** Bigger towns attract bigger raids: {@code 1 + population/8} plus hashed jitter, capped. */
    public static int raidStrength(Settlement settlement, long step) {
        int jitter = (int) Math.floorMod(mix(settlement.id().value().hashCode(), step), 3);
        return Math.min(MAX_RAID_STRENGTH, 1 + settlement.population() / 8 + jitter);
    }

    /**
     * How much danger one guard is reckoned able to hold.
     *
     * <p>Three: a guard handles three zombies, or one witch, but not a creeper
     * and a skeleton at once.
     */
    public static final int GUARD_CAPACITY = 3;

    /**
     * Below this much danger the bell stays quiet however thin the watch is.
     *
     * <p>Without a floor, a town whose only guard was hungry would ring over two
     * zombies. Set below {@link Alarm#ALARMED_AT} on purpose: the bell's job is
     * to panic a badly defended town <em>earlier</em> than the tiers would, not
     * to panic it over nothing.
     */
    public static final int BELL_FLOOR = 4;

    /**
     * Whether what has been seen is more than the watch can be expected to hold.
     *
     * <p>The bell's own rule, and the reason it is not just another threshold:
     * it weighs what is coming against who is standing, so the same two
     * skeletons are a Tuesday for a town with three guards and an emergency for
     * a town with none.
     *
     * <p>Two things it will not do. It will not ring for a single creature —
     * that is the watch's job and the whole reason a town keeps one. And it will
     * not ring below {@link #BELL_FLOOR}, so a thin watch means panicking sooner
     * rather than panicking always.
     */
    public static boolean outmatched(Settlement settlement, Sighting seen) {
        // One creature is the watch's problem, whatever it is. The bell is for
        // telling a town that something has arrived which the watch cannot hold,
        // and a single creeper is not that — it is a guard's afternoon.
        if (seen.seen() < 2 || seen.danger() < BELL_FLOOR) {
            return false;
        }
        long watch = settlement.residents().stream()
                .filter(person -> person.profession() == Profession.GUARD
                        && !person.isTooWeakToWork())
                .count();
        return seen.danger() > watch * GUARD_CAPACITY;
    }

    /** Guards times {@link #GUARD_POWER}, plus every standing structure's defense bonus. */
    public static int defensePower(Settlement settlement) {
        int guards = JobPlanner.count(settlement, Profession.GUARD);
        int structures = settlement.buildings().stream()
                .mapToInt(b -> defenseBonusOf(settlement, b.blueprintId()))
                .sum();
        return guards * GUARD_POWER + structures;
    }

    public static int defenseBonusOf(Settlement settlement, String blueprintId) {
        return settlement.catalogue().stream()
                .filter(type -> type.id().equals(BuildPlanner.baseIdOf(blueprintId)))
                .mapToInt(BuildingType::defenseBonus)
                .findFirst()
                .orElse(0);
    }

    /**
     * Run a raid of the given strength right now. Public so the debug command can
     * force one.
     */
    public static void execute(Settlement settlement, SimContext ctx, int strength) {
        if (strength > settlement.threatLevel()) {
            settlement.setThreatLevel(strength);
        }

        if (ctx.bridge().playerWithin(settlement.centre(), ctx.settings().observedRadius())) {
            // Someone is watching: make it real and let entity combat decide.
            ctx.bridge().spawnHostiles(strength, settlement.centre());
            settlement.logEvent(ctx.step(),
                    "Raiders sighted — " + strength + " attackers approach " + settlement.name());
            return;
        }

        int defense = defensePower(settlement);
        if (defense >= strength) {
            settlement.logEvent(ctx.step(),
                    "Raid of " + strength + " repelled by the garrison (defense " + defense + "), no losses");
            settlement.tallies().record(Tallies.RAIDS_REPELLED);
            return;
        }

        List<Person> fallen = pickCasualties(settlement, strength - defense);
        for (Person person : fallen) {
            settlement.removePerson(person.id());
        }
        settlement.logEvent(ctx.step(),
                "Raid of " + strength + " overran the defenses (" + defense + ") — "
                        + fallen.size() + " lost: " + names(fallen));
    }

    /**
     * Who falls when the line breaks: guards first — they are the line — then
     * others in roster order. Embodied people are never chosen: what a player can
     * see must never die invisibly, and statistical resolution only runs when the
     * centre is unobserved anyway.
     */
    static List<Person> pickCasualties(Settlement settlement, int deficit) {
        List<Person> fallen = new ArrayList<>();
        for (Person person : settlement.residents()) {
            if (fallen.size() >= deficit) {
                break;
            }
            if (person.profession() == Profession.GUARD && !person.isEmbodied()) {
                fallen.add(person);
            }
        }
        for (Person person : settlement.residents()) {
            if (fallen.size() >= deficit) {
                break;
            }
            if (person.profession() != Profession.GUARD && !person.isEmbodied()) {
                fallen.add(person);
            }
        }
        return fallen;
    }

    private static String names(List<Person> people) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < people.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(people.get(i).name());
        }
        return sb.toString();
    }

    /** Deterministic 64-bit mixer (splitmix-style) — the sim's stand-in for randomness. */
    private static long mix(long a, long b) {
        long x = a * 0x9E3779B97F4A7C15L + b;
        x ^= x >>> 27;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31;
        return x;
    }
}
