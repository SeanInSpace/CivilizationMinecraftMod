package com.civilization.sim.settlement;

import com.civilization.sim.person.Profession;

/**
 * How many guards the town's fear says it ought to have, and how many it has.
 *
 * <p>One comparison, in one place. Before this, "is the watch big enough" was
 * asked in three different currencies: the staffing table wanted a guard per
 * eight residents and never looked at the threat at all, {@link RaidPlanner}
 * settled raids with {@link RaidPlanner#GUARD_POWER}, and the bell in
 * {@link RaidPlanner#outmatched} weighed sightings against
 * {@link RaidPlanner#GUARD_CAPACITY}. A town could satisfy any one of them and
 * lose to the raid the other predicted.
 *
 * <h2>The mapping</h2>
 *
 * <p>{@link Settlement#threatLevel()} and the strength of a raid are the same
 * number — {@link RaidPlanner#execute} writes the raid's strength straight into
 * the threat, and {@link Settlement#sighted} writes what the town can see into
 * the same field on the {@link Danger} scale. So "how many guards does this much
 * threat need" is exactly "how many guards repel a raid this strong", and
 * {@link RaidPlanner#execute} answers that with
 * {@code guards * GUARD_POWER >= strength}. Turned round:
 *
 * <pre>{@code neededGuards(threat) = ceil(threat / GUARD_POWER)   // GUARD_POWER = 2}</pre>
 *
 * <p>Per rung of the ladder:
 *
 * <table border="1">
 *   <caption>Threat to guards</caption>
 *   <tr><th>rung</th><th>threat</th><th>guards needed</th></tr>
 *   <tr><td>{@link Danger#NONE}</td><td>0</td><td>0</td></tr>
 *   <tr><td>{@link Danger#ROUTINE}</td><td>1</td><td>1</td></tr>
 *   <tr><td>{@link Danger#AWKWARD}</td><td>2</td><td>1</td></tr>
 *   <tr><td>{@link Danger#FULL_ATTENTION}</td><td>3</td><td>2</td></tr>
 *   <tr><td>{@link Danger#DANGEROUS}</td><td>4</td><td>2</td></tr>
 *   <tr><td>{@link Danger#DIRE}</td><td>5</td><td>3</td></tr>
 *   <tr><td>{@link Danger#OVERMATCH}</td><td>6</td><td>3</td></tr>
 *   <tr><td>{@link Danger#HOPELESS}</td><td>10</td><td>5</td></tr>
 *   <tr><td>{@link RaidPlanner#MAX_RAID_STRENGTH}</td><td>16</td><td>8</td></tr>
 * </table>
 *
 * <p><strong>Why {@link RaidPlanner#GUARD_POWER} and not
 * {@link RaidPlanner#GUARD_CAPACITY}.</strong> They are two honest answers to
 * two different questions: a guard <em>holds</em> three danger's worth of
 * wandering hostiles, but he only <em>counts</em> for two when the raid arrives
 * all at once. Two is the pessimistic one, and it is the one the town actually
 * dies by, so it is the one the town recruits by. A garrison sized on it always
 * satisfies the bell as well; a garrison sized on capacity would look adequate
 * right up to the raid that killed it.
 *
 * <p><strong>Standing defenses are deliberately not counted.</strong>
 * {@link RaidPlanner#defensePower} adds every watchtower's bonus, and this does
 * not, so a town with a tower recruits as though it had none. That is a margin,
 * not an oversight: a tower can be knocked down by the raid before it, and the
 * number a town steers by should be the one it can carry on its own two feet.
 *
 * <p>Plain-English write-up: {@code DEFENSE.md}.
 */
public final class Garrison {

    private Garrison() {
    }

    /**
     * How much of a raid the town's own people can turn back.
     *
     * <p>A head count of guards, times nothing — the strength is the count, and
     * {@link #neededGuards} is already denominated in guards. Weapons and armor
     * are not in it because the forge does not track who is carrying what: it
     * fills a town rack (see {@link SmithPlanner}), and a rack is not a
     * garrison. When a person carries their own sword, this is where the
     * weighting goes.
     *
     * <p>Counted the same way {@link RaidPlanner#defensePower} counts them,
     * including the hungry: a starving guard still stands in the line, and a
     * town that stopped counting him would recruit a replacement it cannot feed
     * either.
     */
    public static int guardStrength(Settlement settlement) {
        return guardHeads(settlement) + kingsWorth(settlement);
    }

    /**
     * How many people in this town are guards. The one guard count.
     *
     * <p>Named, and public, because three lines of {@code /civ info} were
     * counting the watch in three different currencies and a reader could not
     * tell which. This is the head count and nothing else: it is what the jobs
     * line prints, it is the {@code guards} term of
     * {@link RaidPlanner#defensePower}, and it is what {@link #guardStrength}
     * adds the crown to. Anything in the report that says a number of guards says
     * this one.
     */
    public static int guardHeads(Settlement settlement) {
        return JobPlanner.count(settlement, Profession.GUARD);
    }

    /**
     * What a living king adds to the line, and nothing when there is none.
     *
     * <p>Morale, and the one thing on this page that is not a head count. A
     * warband with somebody to follow fights harder than the same warband
     * without one, so the king is worth {@link KingPlanner#KING_GUARD_BONUS}
     * guards while he stands and nothing at all the step he falls.
     *
     * <p>Counted here <em>and</em> in {@link RaidPlanner#defensePower}, which is
     * deliberately not how the watchtower is treated. A tower is left out of
     * this page on purpose — it can be knocked down before the raid it was built
     * for, so a town should not recruit as though it had one. A king cannot be
     * knocked down in advance: either he is alive when the raid arrives, in
     * which case both numbers are right, or he is not, in which case both are
     * zero.
     */
    public static int kingsWorth(Settlement settlement) {
        return KingPlanner.hasKing(settlement) ? KingPlanner.KING_GUARD_BONUS : 0;
    }

    /**
     * The smallest watch that survives a raid of this much threat.
     *
     * <p>{@code ceil(threat / GUARD_POWER)} — see the class javadoc for the
     * arithmetic and the table of rungs. Zero threat needs no guards; the
     * staffing table's one-per-eight still applies underneath, so this is a
     * floor a frightened town raises, never a ceiling on an ordinary one.
     */
    public static int neededGuards(int threat) {
        if (threat <= 0) {
            return 0;
        }
        return (threat + RaidPlanner.GUARD_POWER - 1) / RaidPlanner.GUARD_POWER;
    }

    /** {@link #neededGuards} for what this town is presently afraid of. */
    public static int neededGuards(Settlement settlement) {
        return neededGuards(settlement.threatLevel());
    }

    /**
     * Whether the town has more threat than watch.
     *
     * <p>The one condition the rest of the mod asks about: it puts guards at the
     * front of {@link JobPlanner#retrainOne}, the watchtower at the front of
     * {@link BuildPlanner#chooseNext}, and a line in {@code /civ info}. False
     * whenever the threat has decayed back under the watch, at which point every
     * one of those returns to its ordinary behavior on the very next step.
     */
    public static boolean outnumbered(Settlement settlement) {
        return neededGuards(settlement) > guardStrength(settlement);
    }

    /**
     * How many more guards the town wants right now, or zero when it is content.
     *
     * <p>The number {@code /civ info} shows, and the reason the recruiting lane
     * stops rather than turning the whole town into a militia.
     */
    public static int guardsWanted(Settlement settlement) {
        return Math.max(0, neededGuards(settlement) - guardStrength(settlement));
    }

    /**
     * The watch, in words, for anything that shows a player the town's defense.
     *
     * <p>Lives here rather than in the screen that draws it because the screen
     * is not the only thing that says it — {@code /civ info} says it too, and
     * the two used to say it differently — and because a sentence assembled out
     * of two integers is a pure function with three cases in it, which is
     * exactly the kind of thing that should be testable without a client.
     *
     * <p>What it replaces read {@code "6 of 0 guards"} on a peaceful town,
     * which is not a sentence. Three states, three sentences:
     *
     * <ul>
     *   <li><strong>Nothing is needed</strong> — {@code "6 guards, none needed"}.
     *       The commonest state by far: {@link #neededGuards} is derived from
     *       threat, and a town that has not seen anything has no threat, so the
     *       old wording's denominator was zero nearly all the time.</li>
     *   <li><strong>Enough</strong> — {@code "6 guards, 5 needed"}. The town is
     *       afraid of something and is holding the line it reckoned it wanted.</li>
     *   <li><strong>Short</strong> — {@code "2 of 5 guards needed"}. The one
     *       state worth a warning color, and the one the "N of M" shape was
     *       always meant for.</li>
     * </ul>
     *
     * <p>Caller decides the color; {@link #outnumbered} and the plain
     * {@code guards < needed} comparison both answer the same question.
     */
    public static String watchSummary(int guards, int neededGuards) {
        if (guards < neededGuards) {
            return guards + " of " + neededGuards + " guards needed";
        }
        String held = switch (guards) {
            case 0 -> "no guards";
            case 1 -> "1 guard";
            default -> guards + " guards";
        };
        return neededGuards <= 0 ? held + ", none needed"
                : held + ", " + neededGuards + " needed";
    }

    /**
     * The same, with the crown's share of the watch named instead of folded in.
     *
     * <p>{@link #guardStrength} is a head count plus a morale bonus, and the
     * bonus is invisible in the total: a warband of six with a king reads as
     * seven guards, four lines under a jobs line that says six. Naming it is what
     * makes the two lines the same count again — {@code "7 guards, 5 needed (6 on
     * the roster, +1 for the king)"} — and a town with nobody crowned is the
     * commonest case and says nothing extra at all.
     */
    public static String watchSummary(int guardHeads, int kingsWorth, int neededGuards) {
        String summary = watchSummary(guardHeads + kingsWorth, neededGuards);
        return kingsWorth <= 0 ? summary
                : summary + " (" + guardHeads + " on the roster, +" + kingsWorth
                        + " for the king)";
    }

    /** {@link #watchSummary} for a settlement as it presently stands. */
    public static String watchSummary(Settlement settlement) {
        return watchSummary(guardHeads(settlement), kingsWorth(settlement),
                neededGuards(settlement));
    }
}
