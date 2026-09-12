package com.civilization.sim.settlement;

import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.Sighting;
import com.civilization.sim.world.SimContext;

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
 * <p><strong>A new town is not raided.</strong> Three rules, in the order they
 * apply, and all three exist because world generation stands nine villages
 * around the spawn point before the player has finished loading in:
 * {@link #RAID_GRACE_STEPS} of nothing at all, then {@link #EARLY_CAP_STEPS} of
 * raids held down to {@link #earlyStrengthCap} — something the town's own watch
 * turns back — and, for a raid resolved out of sight forever after,
 * {@link #UNWATCHED_CASUALTY_MARGIN}: a margin of one costs no lives.
 *
 * <p><strong>No randomness.</strong> Schedules and strengths hash the settlement's
 * id with the step number, so the same world replays identically — the property
 * the whole test suite leans on. Plain-English write-up: {@code DEFENSE.md}.
 */
public final class RaidPlanner {

    /** Settlements below this population are beneath raiders' notice. */
    public static final int MIN_POPULATION_FOR_RAIDS = 6;

    /**
     * How long a new settlement is left entirely alone.
     *
     * <p>Two hundred steps — four whole raid intervals at the shipped cadence,
     * or about seventeen minutes of play — during which no raid fires at all,
     * counted from the settlement's own first step rather than the world's. A
     * chartered camp and a town world generation stood before anybody looked
     * both get the same four intervals from the moment they start living.
     *
     * <p><strong>Why it exists.</strong> The nine towns around the world spawn
     * are raised as villages of twelve with one guard and a defense of two.
     * Measured unwatched over twelve trials, such a village lost an average of
     * 3.75 people to raids by step 200, 8.42 by step 400 and 20.67 by step 1000
     * — it is losing its founding population roughly every five hundred steps
     * and only standing at all because it can outbreed the arithmetic. "Raid of
     * 4 overran the defenses (2) — 2 lost: Ada Baker, Bren Baker" at step 8 is
     * not a difficulty setting; it is a town being eaten in its cradle, before
     * the player has met it.
     *
     * <p><strong>Why not "until the wall is up".</strong> That was the obvious
     * anchor and it does not survive measurement. A seeded village stakes its
     * ring on step 532 and does not pay for the last post until step 1256,
     * because nothing is staked before TOWN and nothing is staked before the
     * stage's own program stands. A grace running to 1257 is twenty-five raid
     * intervals — an hour and a half of play in which a raid cannot happen —
     * which is not a grace period, it is turning the feature off. So the grace
     * covers the first four intervals outright and {@link #EARLY_CAP_STEPS}
     * carries the town from there with raids it can actually turn back.
     */
    public static final int RAID_GRACE_STEPS = 200;

    /**
     * How long a raid is held down to something the town can plausibly repel.
     *
     * <p>Five hundred steps, the founding ladder's own length — the measured run
     * from four settlers in a field to a chartered town with a hall. Inside it a
     * raid is a probe rather than a massacre: see {@link #earlyStrengthCap}.
     */
    public static final int EARLY_CAP_STEPS = 500;

    /**
     * By how much a raid must beat an unwatched town's defense before anybody
     * dies.
     *
     * <p>Two. A raid that gets over the line by one is a bad night, not a
     * bereavement, and the difference matters because of <em>who</em> is dying:
     * a town nobody has ever visited resolves its raids as arithmetic, so a
     * person lost to a margin of one is a person lost to a hash of the
     * settlement's id and the step number, with no fight, no body and nobody
     * watching. Somebody's name goes into the history and there is nothing
     * anywhere that could have changed it.
     *
     * <p>A margin of two is a line the defense was genuinely short of holding,
     * and losing people to that reads as a defeat rather than a die roll. Above
     * the margin the arithmetic is exactly what it always was — the deficit is
     * the toll — so the towns that were already dying badly still die badly.
     */
    public static final int UNWATCHED_CASUALTY_MARGIN = 2;

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
        Sighting seen = ctx.bridge().hostilesSeen(settlement.center(), settlement.claimRadius());
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
        if (withinGrace(settlement, ctx.step())) {
            return;
        }
        if (!raidDue(settlement, ctx)) {
            return;
        }
        execute(settlement, ctx,
                Math.min(raidStrength(settlement, ctx.step()),
                        earlyStrengthCap(settlement, ctx.step())));
    }

    /**
     * Whether this town is still too new to have been noticed.
     *
     * <p>Asked of the settlement's own age rather than the world's step count,
     * so a daughter colony budded off at step nine thousand gets the same start
     * the world's own towns got at step zero.
     *
     * <p>A settlement that has somehow never taken a step is not in its grace —
     * it has no birthday to count from, and the only caller stamps one at the
     * top of every step anyway, so the question can only be reached by a test
     * driving {@link #advance} directly. Answering "no" there keeps those tests
     * measuring what they were written to measure.
     */
    public static boolean withinGrace(Settlement settlement, long step) {
        return settlement.firstStep() != Settlement.NOT_YET_LIVED
                && settlement.ageInSteps(step) < RAID_GRACE_STEPS;
    }

    /**
     * The most a raid may be while the town is still young.
     *
     * <p>{@code guards + structures + 1}, where guards is the head count
     * {@link Garrison#guardStrength} keeps and structures is every standing
     * building's defense bonus. Unbounded once the town is past
     * {@link #EARLY_CAP_STEPS}.
     *
     * <p>The arithmetic is the point. A town's defense is
     * {@code guards * GUARD_POWER + structures}, and GUARD_POWER is two, so a
     * capped raid against a town with even one guard is repelled outright:
     * {@code 2g + s >= g + s + 1} for every {@code g >= 1}. A town with no
     * guards at all is over its defense by exactly one, which is under
     * {@link #UNWATCHED_CASUALTY_MARGIN} — so it takes the scare and keeps its
     * people. That is what "a probe rather than a massacre" means here: for the
     * first five hundred steps a raid is a thing that happens to a town, gets
     * written into its history, raises its threat and makes it recruit, and does
     * not kill anybody who was standing where they were told to stand.
     *
     * <p>The <em>strength</em> is capped rather than the casualties, so a watched
     * town sees a smaller warband walk out of the trees rather than a full one
     * that mysteriously loses. Both fidelities have to tell the same story.
     */
    public static int earlyStrengthCap(Settlement settlement, long step) {
        if (settlement.ageInSteps(step) >= EARLY_CAP_STEPS) {
            return MAX_RAID_STRENGTH;
        }
        return Garrison.guardStrength(settlement) + structureDefense(settlement) + 1;
    }

    /** Every standing building's defense bonus, added up. */
    public static int structureDefense(Settlement settlement) {
        return settlement.buildings().stream()
                .mapToInt(b -> defenseBonusOf(settlement, b.blueprintId()))
                .sum();
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
     * <p>{@link Danger#FULL_ATTENTION}, and the same number by definition rather
     * than by coincidence: the scale's unit <em>is</em> one guard's whole
     * attention, so a guard handles three zombies, or one witch, but not a
     * creeper and a skeleton at once.
     */
    public static final int GUARD_CAPACITY = Danger.FULL_ATTENTION;

    /**
     * Below this much danger the bell stays quiet however thin the watch is.
     *
     * <p>Without a floor, a town whose only guard was hungry would ring over two
     * zombies. {@link Danger#DANGEROUS} — more than one guard should meet alone
     * — which sits below {@link Alarm#ALARMED_AT} on purpose: the bell's job is
     * to panic a badly defended town <em>earlier</em> than the tiers would, not
     * to panic it over nothing.
     */
    public static final int BELL_FLOOR = Danger.DANGEROUS;

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
        int structures = structureDefense(settlement);
        // And the king, who is worth a guard to a warband that can see him. The
        // same bonus Garrison recruits by, so what the town thinks it can field
        // and what it actually fields with are one number -- see Garrison for
        // why a king belongs in both and a watchtower in only one.
        int crown = KingPlanner.hasKing(settlement)
                ? KingPlanner.KING_GUARD_BONUS * GUARD_POWER : 0;
        return guards * GUARD_POWER + structures + crown;
    }

    public static int defenseBonusOf(Settlement settlement, String blueprintId) {
        return settlement.catalog().stream()
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

        if (ctx.bridge().playerWithin(settlement.center(), ctx.settings().observedRadius())) {
            // Someone is watching: make it real and let entity combat decide.
            ctx.bridge().spawnHostiles(strength, settlement.center());
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
        if (strength - defense < UNWATCHED_CASUALTY_MARGIN) {
            // Over the line, but only just, and nobody was there to see it: see
            // UNWATCHED_CASUALTY_MARGIN for why a margin of one costs no lives.
            // Counted as repelled, because from the town's side it was — the
            // raiders got in, took what they could carry and left.
            settlement.logEvent(ctx.step(),
                    "Raid of " + strength + " broke through the defenses (" + defense
                            + ") and was driven off — nobody lost");
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
     * center is unobserved anyway.
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
