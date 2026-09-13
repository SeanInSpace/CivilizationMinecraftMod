package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Names;
import com.civilization.sim.settlement.Newcomer;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nobody in a town is named twice, and a newcomer takes the name of the family
 * that takes them in.
 *
 * <p>What this replaces derived every name from a count of something — the
 * population, the number of households, the size of a family — and every one of
 * those counts goes <em>down</em>. So each of them came back round to a number it
 * had already used, and handed out the name it had handed out there. Measured in
 * the world on 2026-09-12: "Bren Smith" born, buried and born again as a second
 * settler, and three unrelated families all called "the Turners" at once.
 *
 * <p>The pools were eight names deep, which is the other half of it: a policy that
 * refuses a name in use has to have somewhere to fall through to, and a town of
 * seventeen had exhausted eight before it was a village.
 */
class NamesTest {

    //                                             id             work  minPop  base  per  priority  capacity
    private static final BuildingType HOUSE = new BuildingType("test:house",  5,  9999,    0,   2,       80,       4);
    private static final List<BuildingType> CATALOG = List.of(HOUSE);

    private static final class LoadedBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** SANDBOX, so the raid schedule does not kill people mid-measurement. */
    private static SimContext at(long step) {
        return new SimContext(new LoadedBridge(), step, SimSettings.SANDBOX);
    }

    private static Settlement town(String cultureId, int houses) {
        Settlement town = new Settlement(Settlement.Id.random(), "Namesbury",
                new SimPos(0, 64, 0), 256);
        town.setCultureId(cultureId);
        town.setCatalog(CATALOG);
        town.setFoodStock(100_000);
        for (int i = 0; i < houses; i++) {
            town.addBuilding(new Building(HOUSE.id(), new SimPos(10 + i * 8, 64, 0), 0, true));
        }
        return town;
    }

    // --- (a) the pools are deep enough for the policy to work at all ---

    @Test
    @DisplayName("every people has forty given names and thirty family names of its own")
    void everyPeopleHasEnoughNamesToGoRound() {
        for (Culture culture : Culture.all()) {
            assertTrue(culture.givenNames().size() >= 40,
                    culture.id() + " has only " + culture.givenNames().size()
                            + " given names; a town of forty would start repeating");
            assertTrue(culture.familyNames().size() >= 30,
                    culture.id() + " has only " + culture.familyNames().size()
                            + " family names; a town of thirty households would");
            assertEquals(culture.givenNames().size(),
                    Set.copyOf(culture.givenNames()).size(),
                    culture.id() + " lists a given name twice, which is a pool"
                            + " that is shallower than it looks");
            assertEquals(culture.familyNames().size(),
                    Set.copyOf(culture.familyNames()).size(),
                    culture.id() + " lists a family name twice");
        }
    }

    @Test
    @DisplayName("an orc is never called what a human is called, and neither is a goblin")
    void thePeoplesOutsideTheHumansShareNoNamesWithThem() {
        Set<String> humanGiven = new HashSet<>();
        Set<String> humanFamilies = new HashSet<>();
        for (Culture culture : Culture.humans()) {
            humanGiven.addAll(culture.givenNames());
            humanFamilies.addAll(culture.familyNames());
        }
        for (Culture culture : List.of(Culture.ORC, Culture.GOBLIN)) {
            for (String given : culture.givenNames()) {
                assertFalse(humanGiven.contains(given),
                        culture.id() + " names its children " + given + ", which is a human name");
            }
            for (String family : culture.familyNames()) {
                assertFalse(humanFamilies.contains(family),
                        culture.id() + " has a family called " + family + ", which is human");
            }
        }
        // And the two of them are not each other either.
        for (String given : Culture.ORC.givenNames()) {
            assertFalse(Culture.GOBLIN.givenNames().contains(given),
                    "orcs and goblins share the name " + given);
        }
    }

    // --- (b) nothing living is named twice ---

    @Test
    @DisplayName("no two living residents of a town share a name, however many arrive")
    void twoArrivalsNeverShareAName() {
        Settlement town = town(Culture.NORMAN.id(), 0);
        List<String> names = new ArrayList<>();
        for (int step = 0; step < 60; step++) {
            names.add(Newcomer.arrive(town, new SimPos(step, 64, 0), step).name());
        }
        assertEquals(names.size(), Set.copyOf(names).size(),
                "two arrivals were given the same name: " + names);
    }

    @Test
    @DisplayName("a newcomer is not named after the settler who died a moment ago")
    void aDeadSettlersNameIsNotHandedStraightToTheNextArrival() {
        // The measured fault, exactly: "Bren Smith" died and a later newcomer was
        // named "Bren Smith". Every namer indexed off the population, and the
        // population fell when he did, so the very next arrival landed on the
        // index that had made him.
        Settlement town = town(Culture.NORMAN.id(), 0);
        List<Person> arrived = new ArrayList<>();
        for (int step = 0; step < 12; step++) {
            arrived.add(Newcomer.arrive(town, new SimPos(step, 64, 0), step));
        }
        Person doomed = arrived.get(5);
        String dead = doomed.name();
        town.removePerson(doomed.id());

        Person next = Newcomer.arrive(town, new SimPos(99, 64, 0), 12);

        assertNotEquals(dead, next.name(),
                "the next arrival was given the name of the settler who just died");
    }

    @Test
    @DisplayName("three families are three names, even after families have died out")
    void aRetiredFamilysNameIsNotGivenToAFamilyStandingBesideIt() {
        // "Three separate families were all the Turners." households().size() was
        // the index, and it falls every time a household is retired -- so the next
        // family founded took a name the town was already using.
        Settlement town = town(Culture.NORMAN.id(), 8);
        for (int i = 0; i < 12; i++) {
            town.addResident(new Person(Person.Id.random(), "Nobody " + i,
                    Profession.BUILDER, new SimPos(0, 64, 0)));
        }
        for (int step = 0; step < 200; step++) {
            town.step(at(step));
        }

        List<String> families = town.households().stream().map(Household::name).toList();
        assertTrue(families.size() >= 3, "the fixture never grew enough families: " + families);
        assertEquals(families.size(), Set.copyOf(families).size(),
                "two households in one town are called the same thing: " + families);
    }

    @Test
    @DisplayName("a town that has grown for a long while still has no two people alike")
    void aGrownTownHoldsNoTwoPeopleOfOneName() {
        Settlement town = town(Culture.VALE.id(), 12);
        for (int i = 0; i < 8; i++) {
            town.addResident(new Person(Person.Id.random(), "Nobody " + i,
                    Profession.FARMER, new SimPos(0, 64, 0)));
        }
        for (int step = 0; step < 400; step++) {
            town.step(at(step));
        }

        List<String> names = town.residents().stream().map(Person::name).toList();
        assertTrue(names.size() > 8, "the town never bore a child: " + names);
        assertEquals(names.size(), Set.copyOf(names).size(),
                "two living people in one town answer to the same name: " + names);
    }

    @Test
    @DisplayName("a family name already in use is walked past rather than handed out again")
    void familyForSkipsWhatIsTaken() {
        Settlement town = town(Culture.NORMAN.id(), 0);
        String first = Names.familyFor(town);
        town.addHousehold(new Household(Household.Id.random(), first));

        assertNotEquals(first, Names.familyFor(town),
                "the same family name was offered twice running");
    }

    // --- (c) a newcomer takes the family's name ---

    @Test
    @DisplayName("a newcomer gathered into an existing family takes that family's name")
    void aNewcomerJoiningAFamilyTakesItsName() {
        Settlement town = town(Culture.NORMAN.id(), 1);
        // A family already standing, with room in it: lastFamilyWithRoom groups
        // arrivals into the newest household under the largest housing capacity,
        // which for this catalog is four.
        Person founder = Newcomer.arrive(town, new SimPos(0, 64, 0), 0);
        town.step(at(1));
        Household family = town.households().getFirst();
        String familyName = family.name();
        assertEquals(familyName, Names.familyPartOf(founder.name()),
                "the household founded around an arrival was given a different"
                        + " surname from the arrival's own");

        Person joiner = Newcomer.arrive(town, new SimPos(3, 64, 0), 2);
        String arrivedAs = joiner.name();
        town.step(at(3));

        assertTrue(family.contains(joiner.id()), "the newcomer was not taken into the family");
        assertEquals(familyName, Names.familyPartOf(joiner.name()),
                "a newcomer joined the " + familyName + "s and stayed a "
                        + Names.familyPartOf(arrivedAs));
        assertEquals(Names.givenPartOf(arrivedAs), Names.givenPartOf(joiner.name()),
                "the rename took their given name away as well as their surname");
    }

    @Test
    @DisplayName("every member of every family is of that family, in a town left to run")
    void nobodyLivesUnderAFamilyNameThatIsNotTheirs() {
        Settlement town = town(Culture.ORC.id(), 6);
        for (int step = 0; step < 40; step++) {
            Newcomer.arrive(town, new SimPos(step, 64, 0), step);
            town.step(at(step));
        }

        for (Household family : town.households()) {
            for (Person.Id id : family.members()) {
                Person member = town.resident(id);
                assertTrue(member != null && member.name().endsWith(" " + family.name()),
                        (member == null ? "a ghost" : member.name())
                                + " is living with the " + family.name() + "s");
            }
        }
    }

    @Test
    @DisplayName("a name nothing in the culture handed out is left exactly as it is")
    void aHandNamedResidentIsNotRenamed() {
        // Nothing keys off a name, so there is no reason to overwrite one somebody
        // meant. A test fixture's "Nobody 3", a command's chosen name, a mod's --
        // all of them keep what they were given, and only a surname this policy
        // itself issued is ever exchanged for a family's.
        Settlement town = town(Culture.NORMAN.id(), 2);
        Person named = new Person(Person.Id.random(), "Aelfric the Wanderer",
                Profession.BUILDER, new SimPos(0, 64, 0));
        town.addResident(named);

        town.step(at(1));

        assertEquals("Aelfric the Wanderer", named.name(),
                "a name the simulation did not choose was overwritten anyway");
    }
}
