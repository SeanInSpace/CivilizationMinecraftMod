package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Profession;

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
        return JobPlanner.count(settlement, Profession.GUARD) + kingsWorth(settlement);
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
    private static int kingsWorth(Settlement settlement) {
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
}
