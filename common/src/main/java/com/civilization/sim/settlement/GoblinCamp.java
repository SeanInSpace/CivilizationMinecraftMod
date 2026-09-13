package com.civilization.sim.settlement;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Race;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimContext;

import java.util.ArrayList;
import java.util.List;

/**
 * What a goblin camp is, in one place.
 *
 * <p>The mod already had goblins: a {@link Race} row, a culture, a skin and a
 * warren to live in. What it did not have was any sense that a goblin settlement
 * is a different <em>kind</em> of settlement from a village — so a camp of
 * scavengers was a village that happened to be green, farming its fields and
 * selling at its market like anybody else.
 *
 * <p>Four rules make it a camp instead, and they are gathered here rather than
 * scattered through the planners that enforce them, because each of them is a
 * statement about goblins and none of them is a statement about food, or jobs, or
 * raids.
 *
 * <ol>
 *   <li><strong>They never farm.</strong> {@link Homes} refuses the field, and
 *       what a camp has instead is {@link #foragesForever} — the wild-food path
 *       every young settlement uses, kept switched on for these people at every
 *       stage of their lives. See {@link #FORAGE_CEILING_PER_MOUTH}.</li>
 *   <li><strong>A chieftain and a shaman.</strong> The chieftain is
 *       {@link KingPlanner}'s rule in a smaller roof, crowned once the chieftain
 *       hut stands. The shaman is this class's own: a second title, no seat
 *       needed, worth an extra armful of forage a step.</li>
 *   <li><strong>They scatter.</strong> Lose both and the camp walks away — see
 *       {@link #advance}, which is the whole of it.</li>
 *   <li><strong>They raid.</strong> {@link GoblinRaids}, which is its own class
 *       because it is the only thing in the mod that reaches across settlements
 *       and so runs from the world rather than from a town.</li>
 * </ol>
 */
public final class GoblinCamp {

    private GoblinCamp() {
    }

    /**
     * Whether this settlement is a goblin camp.
     *
     * <p>Asked of the race through {@link Culture#isHostile}, so a second goblin
     * people somebody writes down is a camp by being goblin rather than by being
     * added to a list here.
     */
    public static boolean isCamp(Settlement settlement) {
        return settlement != null && Culture.of(settlement.cultureId()).isHostile();
    }

    /** The same, of a culture id, for callers holding one of those instead. */
    public static boolean isCamp(String cultureId) {
        return Culture.of(cultureId).isHostile();
    }

    // --- the economy: forage, never farm -------------------------------------

    /**
     * Whether these people bring in wild food for as long as they live.
     *
     * <p>{@code FoodPlanner.forage} was gated on {@code pioneersLabor}, which is
     * "below VILLAGE" — right for a founding party, because a party that is still
     * eating berries at eighty residents has not built the farm it was supposed
     * to. A camp never builds that farm, so the gate was a camp that starved the
     * step it graduated.
     */
    public static boolean foragesForever(Settlement settlement) {
        return isCamp(settlement);
    }

    /**
     * How much wild food a camp will hold per mouth before it stops picking.
     *
     * <p>Eleven, and the number is forced rather than chosen. The ordinary ceiling
     * is {@link FoodPlanner#FORAGE_CEILING_PER_MOUTH} — five, deliberately half of
     * what {@link StagePlanner#FED_WINDOW_STEPS} demands, so that no settlement
     * graduates HOMESTEAD on berries and every one of them has to farm.
     *
     * <p>That argument is exactly right and exactly inapplicable here. A camp has
     * no farm to be pushed towards, so the low ceiling did not make it build one:
     * it held the larder permanently under the fed streak and locked the camp in
     * HOMESTEAD forever, with no sentry, no palisade and no chieftain. So a camp
     * forages one loaf per mouth past the streak's own bar, which is the smallest
     * number that lets a camp be a camp.
     *
     * <p>It is not a free lunch, and the thing that keeps it honest is the ground:
     * foraging is capped by {@code Settlement.forageAllowance}, which counts what
     * is actually growing nearby and depletes as it is picked. A camp in a mire
     * eats; a camp on bare rock starves whatever this number says.
     */
    public static final int FORAGE_CEILING_PER_MOUTH = StagePlanner.FED_WINDOW_STEPS + 1;

    /**
     * The ceiling this settlement forages up to, per mouth.
     *
     * <p>One method rather than a branch at the call site, so the two ceilings
     * cannot be applied to the wrong settlement.
     */
    public static int forageCeilingPerMouth(Settlement settlement) {
        return foragesForever(settlement)
                ? FORAGE_CEILING_PER_MOUTH : FoodPlanner.FORAGE_CEILING_PER_MOUTH;
    }

    /**
     * Extra meals a step a living shaman turns up.
     *
     * <p>One, and it is the whole of what he is worth. A camp's foraging is a
     * headcount divided by {@link FoodPlanner#FORAGERS_PER_MEAL}, so a shaman is
     * worth three pairs of hands in the bushes — which in a camp of eight is
     * about a third more food. Small enough that a camp without one is not a camp
     * that dies; large enough that it is worth keeping him alive, which is the
     * point, because the same one keeps the camp from walking away.
     *
     * <p>Still bounded by the ground. A shaman on bare rock forages nothing extra,
     * because {@code forageAllowance} caps the whole take and he does not conjure
     * berries any more than anybody else does.
     */
    public static final int SHAMAN_FORAGE_BONUS = 1;

    // --- the two titles ------------------------------------------------------

    /**
     * The smallest camp that names a shaman.
     *
     * <p>Four. Below that the camp is three goblins and a fire, and taking one of
     * them out of the work to mutter over it is a camp that does no work — the
     * same reason the staffing table wants no guards below eight residents. Four
     * is also the charter party's size, so the smallest camp that can exist at all
     * is the smallest one that has a shaman.
     */
    public static final int SHAMAN_FROM = 4;

    /** The shaman, or null when the camp has none. */
    public static Person shaman(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (person.profession() == Profession.SHAMAN) {
                return person;
            }
        }
        return null;
    }

    /** Whether a shaman is alive in this camp right now. */
    public static boolean hasShaman(Settlement settlement) {
        return shaman(settlement) != null;
    }

    /**
     * Whether the camp still has somebody it listens to.
     *
     * <p>Either title will do, which is the scatter rule stated the other way
     * round: a camp holds together while there is a chieftain to lead it
     * <em>or</em> a shaman to keep it, and comes apart only when there is neither.
     */
    public static boolean stillLed(Settlement settlement) {
        return KingPlanner.hasKing(settlement) || hasShaman(settlement);
    }

    // --- the scatter ---------------------------------------------------------

    /**
     * Goblins who walk out of a leaderless camp each step.
     *
     * <p>Two. A camp does not empty in a step — that reads as a bug, and a player
     * standing in one would watch eight bodies blink out together — and it does not
     * take a session either. Two a step empties a camp of eight in four steps,
     * which at the shipped interval is twenty seconds: long enough to see it
     * happening and short enough that the place is quiet by the time you have
     * walked round it.
     */
    public static final int SCATTER_PER_STEP = 2;

    /**
     * Whether this camp is coming apart.
     *
     * <p>Three conditions, and every one of them is read off state the save
     * already holds rather than a flag this feature would have had to add:
     *
     * <ul>
     *   <li>it is a goblin camp;</li>
     *   <li>nobody holds either title — {@link #stillLed} is false;</li>
     *   <li>and it has <em>had</em> a chieftain. {@code Settlement.mourningUntil}
     *       is stamped by {@link KingPlanner} the step a leader falls and by
     *       nothing else, so a nonzero value is the camp's own record that it once
     *       had somebody and lost them.</li>
     * </ul>
     *
     * <p>That third condition is what keeps a camp that has not built its
     * chieftain hut yet from scattering on its first step. A camp with no leader
     * and no history of one is a camp that has not got round to naming one, which
     * is an ordinary state and not a collapse.
     */
    public static boolean isScattering(Settlement settlement) {
        return isCamp(settlement)
                && settlement.mourningUntil() > 0
                && !stillLed(settlement);
    }

    /**
     * One step of a camp's life: name a shaman, or come apart.
     *
     * <p>Run from {@code Settlement.step} after {@link KingPlanner#advance}, so a
     * chieftain crowned this step counts as leadership this step rather than next.
     *
     * <p>The two halves are exclusive on purpose. A camp that is scattering does
     * not name a shaman out of the people walking away from it — that would be a
     * camp that rescues itself from the rule, every step, forever.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        if (!isCamp(settlement)) {
            // A camp re-badged away from the goblins puts its stick down, exactly
            // as a town re-badged away from the orcs puts its crown down: a
            // settler wearing a title his people do not have is a settler who
            // does no work for a reason nobody can see.
            for (Person person : new ArrayList<>(settlement.residents())) {
                if (person.profession() == Profession.SHAMAN) {
                    person.setProfession(Profession.IDLER);
                }
            }
            return;
        }
        if (isScattering(settlement)) {
            scatter(settlement, ctx);
            return;
        }
        keepAShaman(settlement, ctx);
    }

    /**
     * Exactly one shaman, named from the longest-standing goblin who is free.
     *
     * <p>"Longest-standing" for the same reason {@link KingPlanner} means it:
     * nobody in this simulation has an age, and {@code residents} keeps the order
     * people joined, so the first free goblin in the list is the one who has been
     * here longest. It is deterministic, which is what lets the succession survive
     * a reload without anything being written down.
     *
     * <p>Extras become idlers, enforced every step rather than assumed, because
     * the ways a second one could appear are all quiet: a save, a migration, a
     * command.
     */
    private static void keepAShaman(Settlement settlement, SimContext ctx) {
        List<Person> muttering = new ArrayList<>();
        for (Person person : settlement.residents()) {
            if (person.profession() == Profession.SHAMAN) {
                muttering.add(person);
            }
        }
        for (int i = 1; i < muttering.size(); i++) {
            muttering.get(i).setProfession(Profession.IDLER);
        }
        if (!muttering.isEmpty() || settlement.population() < SHAMAN_FROM) {
            return;
        }
        for (Person person : settlement.residents()) {
            if (person.profession().isIdleByRight()) {
                continue;   // the chieftain keeps his own title
            }
            person.setProfession(Profession.SHAMAN);
            settlement.logEvent(ctx.step(),
                    person.name() + " takes up the stick and keeps the camp");
            return;
        }
    }

    /**
     * The camp walks away, a couple of goblins a step.
     *
     * <p>Removed rather than killed, and the difference is the whole of the rule:
     * these goblins are not dead, they have gone somewhere else. What is left is a
     * standing camp with nobody in it, which is a thing a player can walk into and
     * loot — and which {@code Settlement.stepEmpty} already handles, having been
     * written for the plague village. So "marked abandoned" needs no new field: an
     * empty settlement is an abandoned one, and the event log says why.
     *
     * <p>The chieftain hut, the hovels, the palisade and whatever is left in the
     * loot pile all stay exactly where they are.
     */
    private static void scatter(Settlement settlement, SimContext ctx) {
        List<Person> leaving = new ArrayList<>();
        for (Person person : settlement.residents()) {
            if (leaving.size() >= SCATTER_PER_STEP) {
                break;
            }
            leaving.add(person);
        }
        if (leaving.isEmpty()) {
            return;   // already empty; stepEmpty has it from here
        }
        for (Person person : leaving) {
            settlement.removePerson(person.id());
        }
        settlement.logEvent(ctx.step(), "No chieftain and no shaman — "
                + leaving.size() + " goblins slip away from " + settlement.name());
        if (!settlement.hasLivingResidents()) {
            settlement.logEvent(ctx.step(),
                    settlement.name() + " has scattered; the camp stands abandoned");
        }
    }
}
