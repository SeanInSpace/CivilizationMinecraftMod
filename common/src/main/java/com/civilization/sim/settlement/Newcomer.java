package com.civilization.sim.settlement;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Race;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.kingdom.Kingdom;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimWorld;

import java.util.List;

/**
 * One person walking into a town that already stands.
 *
 * <p>Everything else that puts people into the world puts a whole settlement in
 * with them — a charter founds, worldgen seeds, a household breeds. This is the
 * other case: somebody arrives at a town nobody had to build for them, which is
 * what a spawn egg is and what an immigrant will be.
 *
 * <p><strong>An arrival joins or it does nothing.</strong> There is no such thing
 * here as a person with no town: a body the entity manager does not own is
 * culled on the spot, and a person no settlement holds is a record nothing steps.
 * So the two halves are separate on purpose — {@link #townFor} answers whether
 * there is anywhere to arrive <em>at</em>, and only if it says yes does
 * {@link #arrive} make anybody. A caller that finds no town should say so and
 * make nothing; founding one instead is a charter's job.
 *
 * <p>Pure simulation. Nothing here has ever heard of Minecraft, which is the
 * point: the "which town do I join" question is the whole of the interesting
 * behavior and it is answerable in a unit test with no world at all.
 */
public final class Newcomer {

    /**
     * The town of that race this spot belongs to, or null if it belongs to none.
     *
     * <p>Inside the claim rather than merely near it. A settlement's claim is
     * already the radius everything else in the mod means by "this town's
     * ground" — it is what {@code Settlement.contains} tests, what the excavation
     * respects and what a charter refuses to build inside — and a newcomer who
     * could join a town he is standing outside of would be joining by a rule
     * nothing else in the game uses.
     *
     * <p>Nearest center wins where two claims overlap, which they can: claims
     * grow as towns do and nothing shrinks a neighbor's to make room. Nearest is
     * the answer a player expects from "this one", and it is stable — the same
     * spot always picks the same town — which a first-match over a map's values
     * would not be.
     *
     * @param race the race the arrival is of; a town of any other race is not a
     *             candidate however close it is
     */
    public static Settlement townFor(SimWorld world, Race race, SimPos spot) {
        if (world == null || race == null || spot == null) {
            return null;
        }
        Settlement best = null;
        long nearest = Long.MAX_VALUE;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement town : kingdom.settlements()) {
                if (Culture.of(town.cultureId()).race() != race || !town.contains(spot)) {
                    continue;
                }
                long distance = town.center().horizontalDistanceSq(spot);
                if (distance < nearest) {
                    nearest = distance;
                    best = town;
                }
            }
        }
        return best;
    }

    /**
     * Adds one resident to that town, standing where they were put.
     *
     * <p>An idler, and deliberately: a profession is the town's decision, and
     * {@code JobPlanner} makes it on the next step out of what the town is short
     * of. Handing an arrival a trade here would be this class overruling the
     * planner about a town it knows nothing else about.
     *
     * <p>Unhoused, for the same reason. {@code PopulationPlanner} puts people in
     * beds and gathers them into families; a newcomer with a household of one and
     * a house nobody assigned him would be a state the planner cannot produce and
     * therefore a state nothing maintains.
     *
     * <p>Empty pockets, because a town that is standing has a granary and a
     * newcomer starts unhungry — there is a whole day of simulation between
     * arriving and needing a meal. The rations a chartered party carries are for
     * the opposite case: a camp with no larder at all.
     *
     * @param spot where they are standing, which is where the entity manager will
     *             give them a body
     * @param step the simulation step this happened on, for the town's own log
     */
    public static Person arrive(Settlement town, SimPos spot, long step) {
        Person person = new Person(Person.Id.random(), nameIn(town), Profession.IDLER, spot);
        town.addResident(person);
        town.logEvent(step, person.name() + " arrived in " + town.name());
        return person;
    }

    /**
     * A name in the town's own people's words: a given name and a family name.
     *
     * <p>Indexed off the population rather than drawn at random, so the first
     * arrival in a camp is not the fifth given name by luck — and so two eggs in
     * a row produce two different people rather than the same name twice, which
     * random would do often enough to look broken.
     *
     * <p>The two pools are walked at different rates on purpose: the same count
     * over both would pair the nth given name with the nth family name forever,
     * and a people with ten of each would only ever have ten names between them.
     */
    private static String nameIn(Settlement town) {
        Culture culture = Culture.of(town.cultureId());
        List<String> given = culture.givenNames();
        List<String> families = culture.familyNames();
        int count = town.population();
        String first = given.isEmpty() ? "Settler" : given.get(count % given.size());
        if (families.isEmpty()) {
            return first;
        }
        // Advances one family for every full pass through the given names, so the
        // pair does not repeat until both pools have.
        int family = (count / Math.max(1, given.size()) + count) % families.size();
        return first + " " + families.get(family);
    }

    private Newcomer() {
    }
}
