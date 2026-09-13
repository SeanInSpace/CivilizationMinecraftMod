package com.civilization.sim.settlement;

import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Goblin camps going out, which is where their living comes from.
 *
 * <p>Every raid in the mod until now came from nowhere. {@code RaidPlanner} hashes
 * a settlement's id against the step number, a strength falls out, and the town
 * either holds or does not — and nothing is ever on the other end of it. That is
 * the right model for "the woods are dangerous"; it is no model at all for a
 * people whose whole economy is taking other people's things, because there is
 * nowhere for the things to go.
 *
 * <p>So this is the same arithmetic run backwards. A camp counts who it can spare,
 * walks them at the nearest settlement that is not goblin, and hands the strength
 * to that settlement's own {@code RaidPlanner} — see
 * {@link RaidPlanner#raidBy}, which is deliberately where all of it resolves. The
 * grace a new town gets, the cap that keeps an early raid a probe, the margin under
 * which nobody dies and the switch to real bodies when a player is watching are all
 * rules about being raided, and this class does not know any of them. What it knows
 * is what a camp can send, what comes home, and who did not.
 *
 * <h2>Why it runs from the world</h2>
 *
 * <p>Everything else in this package is a pass over one settlement, because a
 * settlement is all a {@code SimContext} can reach. This is the one thing in the
 * simulation that needs two of them at once, so it runs from {@code SimWorld.step}
 * with every settlement in hand rather than from {@code Settlement.step} with one.
 * That is also why it takes a list rather than a world: the class stays pure
 * arithmetic over settlements and needs no world at all to test.
 */
public final class GoblinRaids {

    private GoblinRaids() {
    }

    /**
     * Steps between one camp's raids.
     *
     * <p>Three hundred, which is half again the raid interval a town is hit on and
     * about twenty-five minutes of play at the shipped cadence. Deliberately slow:
     * a camp of eight that went out every fifty steps would be a permanent siege
     * of whatever village it was nearest, and the point of a scavenger is that they
     * turn up occasionally and take something.
     *
     * <p>Offset per camp by a hash of its id, exactly as {@link RaidPlanner#raidDue}
     * offsets a town's, so two camps near one village do not arrive together on
     * every multiple of three hundred.
     */
    public static final int RAID_PERIOD_STEPS = 300;

    /**
     * How far a camp will walk for somebody else's food.
     *
     * <p>Five hundred and twelve blocks, which is one worldgen region: a camp raids
     * the neighbors it was generated among and not the other side of the map. Far
     * enough that most camps have a target at all — sites are one per region — and
     * near enough that a raid is a thing happening in the part of the world a player
     * is in rather than an event two kilometres away.
     */
    public static final int REACH = 512;

    /**
     * The smallest party a camp will send.
     *
     * <p>Three. Two goblins walking up to a walled village is not a raid, it is a
     * pair of casualties, and a camp small enough that three is more than half of it
     * stays at home — see {@link #partyFrom}, which never sends the last goblin.
     */
    public static final int MIN_PARTY = 3;

    /**
     * The share of a raided town's stores that comes home in the loot pile.
     *
     * <p>A quarter, and it applies to each of the three things worth carrying:
     * food, iron and coin. Timber and stone are not in it — a goblin party is not
     * carrying logs home across five hundred blocks — which is also the answer to
     * why this does not gut a town: the things it loses are the things it can grow
     * or mine again, and it loses three quarters of nothing if its stores are
     * already empty.
     *
     * <p>A quarter rather than everything because a camp that emptied a town's
     * granary in one night would end the town, and a dead neighbor is a camp with
     * nothing to raid. Scavenging is a rent, not a conquest.
     */
    public static final int LOOT_SHARE = 4;

    /** What a party carries home, in the order a camp values it. */
    public static final List<String> SPOILS =
            List.of(TownStores.FOOD, TownStores.IRON);

    /**
     * One pass over every settlement in the world.
     *
     * <p>Camps only, and each on its own clock. Run after the kingdoms have
     * stepped, so a camp that just crowned a chieftain raids with him and a town
     * that just recruited a guard is defended by him.
     */
    public static void advance(List<Settlement> everywhere, SimContext ctx) {
        if (!ctx.settings().raidsEnabled()) {
            return;
        }
        for (Settlement camp : everywhere) {
            if (!GoblinCamp.isCamp(camp) || !camp.hasLivingResidents()) {
                continue;
            }
            if (GoblinCamp.isScattering(camp)) {
                continue;   // a camp coming apart is not a camp going out
            }
            if (!dueOut(camp, ctx)) {
                continue;
            }
            Settlement target = nearestVictim(camp, everywhere);
            if (target == null) {
                continue;   // nobody within reach; the camp forages instead
            }
            send(camp, target, ctx);
        }
    }

    /**
     * Whether this camp's party goes out this step.
     *
     * <p>The same trick {@link RaidPlanner#raidDue} uses and for the same reason:
     * an offset hashed from the camp's own id, so camps raid on their own clocks
     * rather than all in the same step, and the whole thing replays identically on
     * a reload because nothing is written down.
     */
    public static boolean dueOut(Settlement camp, SimContext ctx) {
        long offset = Math.floorMod(
                mix(camp.id().value().hashCode(), SALT), RAID_PERIOD_STEPS);
        return Math.floorMod(ctx.step() - offset, RAID_PERIOD_STEPS) == 0;
    }

    /**
     * A stream separator, so a camp's going-out clock and its own raid clock are
     * not the same clock.
     *
     * <p>Deliberately not {@code RaidPlanner}'s {@code 0x9E3779B9}. A camp is a
     * settlement and is itself raided by the woods, and a shared salt would have
     * meant every camp going out on precisely the step something arrived at it.
     */
    private static final long SALT = 0x6081_1200_0000_0001L;

    /**
     * The nearest settlement worth raiding, or null when there is none in reach.
     *
     * <p>Nearest rather than richest, and not a choice so much as a description:
     * a scavenger raids what it can walk to. Goblin camps are skipped — goblins
     * fight each other in every story ever told about them and they do not do it
     * here, because a camp raiding a camp is two camps' stores moving back and
     * forth forever and nothing a player would ever see.
     *
     * <p>Empty towns are skipped too. There is nobody to fight, and the honest
     * reading of an abandoned village is that a goblin walks in and helps himself
     * rather than that a party assaults it — which is worth having one day and is
     * not a raid.
     */
    public static Settlement nearestVictim(Settlement camp, List<Settlement> everywhere) {
        long limit = (long) REACH * REACH;
        return everywhere.stream()
                .filter(other -> other != camp)
                .filter(other -> !GoblinCamp.isCamp(other))
                .filter(Settlement::hasLivingResidents)
                .filter(other -> other.center().horizontalDistanceSq(camp.center()) <= limit)
                // By distance, then by id, so a tie between two villages equally
                // far off is settled the same way on every reload.
                .min(Comparator
                        .comparingLong((Settlement other) ->
                                other.center().horizontalDistanceSq(camp.center()))
                        .thenComparing(other -> other.id().value().toString()))
                .orElse(null);
    }

    /**
     * Everybody the camp can spare, which is never everybody.
     *
     * <p>Half the goblins fit to walk, rounded up, and at least {@link #MIN_PARTY}
     * — but never so many that nobody is left, because a camp that emptied itself
     * into a raid would come home to a camp anybody had walked into. The shaman
     * stays: he keeps the camp, and the camp is what the party is coming back to.
     *
     * <p>The chieftain goes. He is the reason the party holds together and he is
     * the reason it is worth {@link KingPlanner#KING_GUARD_BONUS} more than its
     * head count — the same morale the garrison counts him for, spent on the
     * attack instead of the wall.
     */
    public static List<Person> partyFrom(Settlement camp) {
        List<Person> fit = new ArrayList<>();
        for (Person goblin : camp.residents()) {
            if (goblin.profession() == Profession.SHAMAN || goblin.isTooWeakToWork()) {
                continue;
            }
            fit.add(goblin);
        }
        if (fit.size() < MIN_PARTY) {
            return List.of();
        }
        int walking = Math.max(MIN_PARTY, (fit.size() + 1) / 2);
        walking = Math.min(walking, fit.size() - 1);   // somebody always stays
        return walking < MIN_PARTY ? List.of() : List.copyOf(fit.subList(0, walking));
    }

    /**
     * What a party is worth to the settlement it walks up to.
     *
     * <p>One point a body, plus the chieftain's morale if he is in it. Deliberately
     * the same currency as {@link RaidPlanner#raidStrength}, because the town's
     * defense is measured against that currency and a second scale would be a
     * camp whose raids were secretly twice as hard as the woods'.
     *
     * <p>Capped at {@link RaidPlanner#MAX_RAID_STRENGTH} like everything else that
     * produces a strength, so a camp that somehow grew to fifty does not spawn a
     * horde at a village.
     */
    public static int strengthOf(Settlement camp, List<Person> party) {
        if (party.isEmpty()) {
            return 0;
        }
        int morale = party.stream().anyMatch(p -> p.profession() == Profession.KING)
                ? KingPlanner.KING_GUARD_BONUS : 0;
        return Math.min(RaidPlanner.MAX_RAID_STRENGTH, party.size() + morale);
    }

    /**
     * One raid, start to finish: out, the fight, the loot, and who did not come
     * back.
     */
    private static void send(Settlement camp, Settlement target, SimContext ctx) {
        List<Person> party = partyFrom(camp);
        int strength = strengthOf(camp, party);
        if (strength <= 0) {
            return;   // too few goblins fit to be worth the walk
        }
        RaidPlanner.Outcome outcome = RaidPlanner.raidBy(target, ctx, strength, camp);
        if (!outcome.happened()) {
            return;   // the town's grace turned them round; nothing to record
        }
        camp.logEvent(ctx.step(), "A party of " + party.size()
                + " went out at " + target.name());
        if (outcome.fought()) {
            // Real bodies at the target and a real fight. What the party brings
            // home is the world's to decide and this arithmetic must not write a
            // second answer over it -- see the note in docs/GOBLINS.md about the
            // carry-home a watched raid does not yet do.
            return;
        }
        if (outcome.brokeIn()) {
            carryHome(camp, target, ctx);
        }
        buryTheirOwn(camp, party, outcome, ctx);
    }

    /**
     * A share of the target's stores moves to the loot pile.
     *
     * <p>Taken with {@code takeUpTo} rather than {@code take}, so a town with less
     * than the share gives what it has and the party is not told it failed. Both
     * sides' ledgers move in the same breath, which is the whole of why the loot
     * pile is a real store: what is in it was somebody else's an hour ago and the
     * event log on both ends says so.
     */
    private static void carryHome(Settlement camp, Settlement target, SimContext ctx) {
        StringBuilder took = new StringBuilder();
        for (String spoil : SPOILS) {
            int share = target.stores().get(spoil) / LOOT_SHARE;
            if (share <= 0) {
                continue;
            }
            int got = target.stores().takeUpTo(spoil, share);
            if (got <= 0) {
                continue;
            }
            camp.stores().add(spoil, got);
            if (!took.isEmpty()) {
                took.append(", ");
            }
            took.append(got).append(' ').append(spoil);
        }
        int purse = target.treasury() / LOOT_SHARE;
        if (purse > 0 && target.spend(purse)) {
            camp.bank(purse);
            if (!took.isEmpty()) {
                took.append(", ");
            }
            took.append(purse).append(" coin");
        }
        if (took.isEmpty()) {
            camp.logEvent(ctx.step(),
                    "The party got into " + target.name() + " and found nothing worth taking");
            target.logEvent(ctx.step(), "Raiders got in and found nothing to take");
            return;
        }
        camp.logEvent(ctx.step(), "The loot pile takes " + took + " out of " + target.name());
        target.logEvent(ctx.step(), "Raiders carried off " + took);
    }

    /**
     * What the target's defense cost the camp.
     *
     * <p>One goblin for every {@link RaidPlanner#GUARD_POWER} of defense the town
     * fielded, capped at half the party — which is the same arithmetic the town's
     * side uses, read the other way round, and the cap is what stops a camp being
     * wiped out by one raid on a walled town. A party that walks at a garrison of
     * six loses three; a party that walks at an undefended hamlet loses nobody.
     *
     * <p>Removed rather than killed for the same reason a scatter is: these are
     * goblins who did not come back, and the camp is one short either way.
     * Embodied goblins are spared, as everywhere: what a player can see must never
     * die invisibly.
     */
    private static void buryTheirOwn(Settlement camp, List<Person> party,
                                     RaidPlanner.Outcome outcome, SimContext ctx) {
        int toll = Math.min(party.size() / 2, outcome.defense() / RaidPlanner.GUARD_POWER);
        if (toll <= 0) {
            return;
        }
        List<Person> lost = new ArrayList<>();
        for (Person goblin : party) {
            if (lost.size() >= toll) {
                break;
            }
            // The chieftain is the last one out of a fight, not the first. He is
            // skipped here and the succession is left to KingPlanner, which is the
            // one place allowed to have an opinion about a leaderless camp.
            if (goblin.profession() == Profession.KING || goblin.isEmbodied()) {
                continue;
            }
            lost.add(goblin);
        }
        for (Person goblin : lost) {
            camp.removePerson(goblin.id());
        }
        if (!lost.isEmpty()) {
            camp.logEvent(ctx.step(), lost.size()
                    + " of the party did not come back");
        }
    }

    /** Deterministic 64-bit mixer, the same one every clock in this package uses. */
    private static long mix(long a, long b) {
        long x = a * 0x9E3779B97F4A7C15L + b;
        x ^= x >>> 27;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31;
        return x;
    }
}
