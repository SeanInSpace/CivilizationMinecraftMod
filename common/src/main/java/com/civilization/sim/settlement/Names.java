package com.civilization.sim.settlement;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Person;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Who a new person is called, and what a new family is called.
 *
 * <p><strong>Nothing in a town is named twice.</strong> That is the whole of this
 * class, and it is here rather than in the three places that used to decide it
 * because the three places disagreed. {@code PopulationPlanner} indexed given
 * names off the size of the household and family names off the number of
 * households; {@code Newcomer} indexed both off the population. Every one of
 * those counters goes <em>down</em> when somebody dies or a household is retired,
 * so every one of them handed out a name it had handed out before:
 *
 * <ul>
 *   <li>"Bren Smith" died, the population fell back past the index that made
 *       him, and the next arrival was named "Bren Smith".</li>
 *   <li>Three unrelated households were all "the Turners", because a household
 *       retiring put {@code households().size()} back on a number it had already
 *       used.</li>
 * </ul>
 *
 * <p>So a name is not derived from a count any more. The index only says
 * <em>where to start looking</em>; what is handed out is the first name from
 * there that nothing in the town is already using, and that search is what makes
 * two families of one name impossible.
 *
 * <p>Where to start differs for the two, and deliberately. A <strong>family</strong>
 * still starts at the number of households, so a young town walks its pool in order
 * and reads exactly the way it always has. A <strong>person</strong> starts at the
 * simulation step, because the search alone does not save them: a count of people
 * comes back round to a number it has used before, and the name it handed out there
 * is free again precisely because its bearer is the one who died. The step only goes
 * up — and goes on going up across a reload, now that the clock is saved — so the
 * same given name is not reached again until the pool has been walked through.
 *
 * <p><strong>Deterministic, like everything else in the simulation.</strong> No
 * randomness: the same town in the same state always answers the same name, which
 * is what makes any of this testable and what makes two clients agree.
 *
 * <p>Two kinds of collision are refused, and they are not the same kind:
 *
 * <ul>
 *   <li>A <em>person's whole name</em> — given and family together — may not be
 *       borne by a living resident. Two Smiths called Bren in one town are two
 *       people nobody can tell apart in a report or on a nameplate.</li>
 *   <li>A <em>family name</em> may not be in use by a household in the town, nor
 *       borne by a resident who has not been gathered into one yet. "The Turners"
 *       has to mean one family.</li>
 * </ul>
 *
 * <p>Only the living and only this town. A name is free again once its bearer is
 * dead — a village that could never reuse the name of somebody's
 * great-grandmother would exhaust its own language — and two towns are welcome to
 * both hold a Bren Smith, because nothing ever lists them side by side.
 */
public final class Names {

    private Names() {
    }

    /**
     * What a people is called when its own table says nothing.
     *
     * <p>Every culture in {@link Culture} fills both pools in, so this is reached
     * only by a datapack entry that leaves one empty. The lowland pools are the
     * right fallback for the same reason {@link Culture#DEFAULT} is human: a town
     * with no people named is what every town in the mod used to be.
     */
    private static List<String> familyPool(Settlement settlement) {
        List<String> pool = Culture.of(settlement.cultureId()).familyNames();
        return pool == null || pool.isEmpty()
                ? Culture.NORMAN.familyNames() : pool;
    }

    private static List<String> givenPool(Settlement settlement) {
        List<String> pool = Culture.of(settlement.cultureId()).givenNames();
        return pool == null || pool.isEmpty()
                ? Culture.NORMAN.givenNames() : pool;
    }

    // --- families ---

    /**
     * A family name no household in this town is using, in the town's own tongue.
     *
     * <p>The search starts where the old counter pointed — the number of
     * households — so a young town still walks its pool in order and reads the way
     * it always has. Where that name is taken it walks on, which is the entire
     * difference from what this replaces.
     */
    public static String familyFor(Settlement settlement) {
        return firstFree(familyPool(settlement), settlement.households().size(),
                familiesInUse(settlement, null));
    }

    /**
     * Every family name this town has spoken for: a household's name, and the
     * surname of anybody not yet gathered into one.
     *
     * <p>The second half matters for exactly one case, and it is the case a spawn
     * egg makes: an arrival exists as a resident for at least one step before
     * {@code PopulationPlanner} decides which family they belong to. Ignoring
     * them would let two arrivals in the same step be given the same surname and
     * then found two households of that one name.
     */
    private static Set<String> familiesInUse(Settlement settlement, Person.Id except) {
        Set<String> taken = new HashSet<>();
        for (Household household : settlement.households()) {
            taken.add(household.name());
        }
        for (Person resident : settlement.residents()) {
            if (except != null && except.equals(resident.id())) {
                continue;
            }
            String family = familyPartOf(resident.name());
            if (!family.isEmpty()) {
                taken.add(family);
            }
        }
        return taken;
    }

    // --- people ---

    /**
     * A newborn's name: the family's, and a given name nobody living shares.
     *
     * <p>Children take the family name — that is what a family name is — so the
     * only choice here is the given one, and it is made against the whole town
     * rather than against the household. Two Brens in one house is the obvious
     * fault; two Bren Smiths in one town who happen to live apart is the same
     * fault a step later, when one of them moves out into a cottage.
     */
    public static String childOf(Settlement settlement, Household household, long step) {
        return inFamily(settlement, household.name(), step, null);
    }

    /**
     * A whole name for somebody walking into a town that already stands.
     *
     * <p>A family of their own, because an arrival is not anybody's child. If
     * {@code PopulationPlanner} then gathers them into a household that already
     * exists they are renamed into it by {@link #joining} — one person cannot be
     * a Palfreyman living with the Coopers — and if it founds them a household
     * instead, the household takes the surname they arrived under.
     */
    public static String forNewcomer(Settlement settlement, long step) {
        String family = firstFree(familyPool(settlement), settlement.households().size(),
                familiesInUse(settlement, null));
        return inFamily(settlement, family, step, null);
    }

    /**
     * What this person is called once the household that took them in is known.
     *
     * <p><strong>A newcomer joining a family takes the family's name.</strong>
     * They used to keep whichever surname they arrived under, so a household
     * called the Coopers held a Bren Palfreyman and a report of the town listed a
     * family whose members were not of it. Their given name is kept wherever it
     * is free in that family, which is nearly always, so the rename is a change
     * of surname and not of person.
     *
     * @return their new name, or the one they already have when nothing need change
     */
    public static String joining(Settlement settlement, Person person, Household household,
                                 long step) {
        String family = familyPartOf(person.name());
        if (family.isEmpty() || family.equals(household.name())
                || !isOneOfOurs(settlement, family)) {
            return person.name();
        }
        // Their own id is excluded from what counts as taken, and the search
        // starts at their own given name, so the rename is a change of surname
        // and comes back with their own given name wherever it is free.
        return inFamily(settlement, household.name(),
                givenIndexOf(settlement, person, step), person.id());
    }

    /**
     * A family name for a household founded around somebody who has no family
     * yet: the surname they arrived under, where it is theirs to give.
     *
     * <p>An arrival already carries a surname nothing in the town was using, so
     * founding them a household under a <em>different</em> name is how a family
     * of one comes to be called the Coopers while its only member is a
     * Palfreyman. Two independent pickers, one town, two answers.
     */
    public static String familyFoundedBy(Settlement settlement, Person person) {
        String family = familyPartOf(person.name());
        if (!family.isEmpty() && isOneOfOurs(settlement, family)
                && !familiesInUse(settlement, person.id()).contains(family)) {
            return family;
        }
        return familyFor(settlement);
    }

    /**
     * Whether this surname is one this policy would have handed out.
     *
     * <p>The line between a name the simulation chose and a name something else
     * did, and it decides whether anybody is renamed at all. A person called
     * "Brak", or "Settler 3", or anything a test fixture or a command or a mod
     * put there, is left exactly as they are: nothing downstream keys off a name,
     * so there is no reason to overwrite one somebody meant.
     *
     * <p>The numbered form counts — "Turner 2" is the Turner pool name a town that
     * outgrew its pool was given — so a member of the second Turner family is
     * renamed into the family that takes them in like anybody else.
     */
    private static boolean isOneOfOurs(Settlement settlement, String family) {
        List<String> pool = familyPool(settlement);
        if (pool.contains(family)) {
            return true;
        }
        int space = family.lastIndexOf(' ');
        return space > 0 && pool.contains(family.substring(0, space));
    }

    /**
     * Where in the pool to start looking for this person's given name, so that the
     * one they already answer to is tried first.
     *
     * <p>A rename ought to change as little as possible: somebody who arrived as
     * Bren and is moving in with the Coopers should come out Bren Cooper, not the
     * first free name in the book. So the search starts at their own given name
     * where the pool holds it, and at the population count — the old behavior —
     * where it does not.
     */
    private static long givenIndexOf(Settlement settlement, Person person, long step) {
        List<String> pool = givenPool(settlement);
        int at = pool.indexOf(givenPartOf(person.name()));
        return at >= 0 ? at : step;
    }

    /**
     * A given name from this town's pool that makes a whole name nobody living
     * already answers to, paired with the family name given.
     *
     * <p>{@code from} is where in the pool to start looking, and for a person it is
     * the <em>simulation step</em> rather than any count of people. That is the
     * other half of the fix. A count of people falls when somebody dies, so it
     * comes back round to a number it has already handed out and the name it
     * handed out there is free again — the bearer is the one who died. That is
     * exactly how "Bren Smith" was born, buried, and born again. The step only ever
     * goes up, and it goes up across a reload now that the clock is saved, so a
     * name is not reached a second time until the whole pool has been walked.
     *
     * <p>When every given name in the pool is already paired with this one family
     * — forty of them, so a single household would have to hold forty living
     * people — the name is numbered instead. Ugly and deterministic, which is the
     * right pair of properties for a case that says the pool is too small.
     */
    private static String inFamily(Settlement settlement, String familyName, long from,
                                   Person.Id except) {
        List<String> pool = givenPool(settlement);
        Set<String> taken = namesInUse(settlement, except);
        int size = pool.size();
        int start = (int) Math.floorMod(from, size);
        for (int i = 0; i < size; i++) {
            String candidate = join(pool.get((start + i) % size), familyName);
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        for (int number = 2; number <= taken.size() + 2; number++) {
            String candidate = join(pool.get(start), familyName) + " " + number;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        // Unreachable: the loop above tries more numbers than there are names in
        // the town. Kept rather than thrown, because a town that cannot name a
        // child must still be able to have one.
        return join(pool.get(start), familyName) + " " + (taken.size() + 3);
    }

    /** Every whole name a living resident answers to, bar the one being renamed. */
    private static Set<String> namesInUse(Settlement settlement, Person.Id except) {
        Set<String> taken = new HashSet<>();
        for (Person resident : settlement.residents()) {
            if (except != null && except.equals(resident.id())) {
                continue;
            }
            taken.add(resident.name());
        }
        return taken;
    }

    // --- odds and ends ---

    /**
     * The first name from the pool, starting at {@code from}, that is not taken;
     * numbered where the whole pool is.
     *
     * <p>The numbering is the shape the old {@code nextFamilyName} used when it
     * wrapped — "Turner 2" — kept so that a town which really does outgrow thirty
     * family names reads the way it used to rather than inventing a second
     * convention.
     */
    private static String firstFree(List<String> pool, int from, Set<String> taken) {
        int size = pool.size();
        int start = Math.floorMod(from, size);
        for (int i = 0; i < size; i++) {
            String candidate = pool.get((start + i) % size);
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        for (int number = 2; number <= taken.size() + 2; number++) {
            for (int i = 0; i < size; i++) {
                String candidate = pool.get((start + i) % size) + " " + number;
                if (!taken.contains(candidate)) {
                    return candidate;
                }
            }
        }
        return pool.get(start) + " " + (taken.size() + 3);
    }

    private static String join(String given, String family) {
        return family == null || family.isEmpty() ? given : given + " " + family;
    }

    /**
     * The given name out of a whole one: everything before the first space.
     *
     * <p>Every pool in {@link Culture} holds single-word given names, so the first
     * word is the given name and the rest is the family. A person with one word
     * for a name is all given name and no family, which is what a culture with an
     * empty family pool produces.
     */
    public static String givenPartOf(String name) {
        int space = name.indexOf(' ');
        return space < 0 ? name : name.substring(0, space);
    }

    /** The family name out of a whole one: everything after the first space. */
    public static String familyPartOf(String name) {
        int space = name.indexOf(' ');
        return space < 0 ? "" : name.substring(space + 1);
    }
}
