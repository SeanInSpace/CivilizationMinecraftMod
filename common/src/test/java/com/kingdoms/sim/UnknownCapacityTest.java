package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A household in a house the catalog cannot size is left alone.
 *
 * <p>The fault, measured rather than supposed. {@code capacityOfHome} answered
 * zero for a home whose blueprint matched no catalog entry, and every caller
 * read zero as <em>full</em>, because the test is {@code size() < capacity}. So
 * a family of three in a renamed cottage — or one from a mod no longer loaded,
 * or a save older than the entry — read as permanently overcrowded and shed a
 * member into every vacancy that appeared, every birth cycle, until there was
 * nobody left. On this fixture: three members to none.
 *
 * <p>Zero and unknown are different claims. Zero is a shed: the catalog has
 * been asked and has answered that nobody lives there. Unknown is the catalog
 * declining to answer about a building that is standing, and the only honest
 * thing to do with it is nothing. A household with no home, or one homed where
 * no building stands, is neither — there is no house, which is a fact, and it is
 * answered zero exactly as it always was.
 */
class UnknownCapacityTest {

    private static final BuildingType HOUSE =
            new BuildingType("test:house", 5, 9999, 0, 0, 80, 4);
    private static final BuildingType SHED =
            new BuildingType("test:shed", 5, 9999, 0, 0, 10, 0);

    /** Where the family lives. Deliberately absent from the catalog below. */
    private static final String FORGOTTEN = "test:cottage_by_another_name";

    private static final SimPos FORGOTTEN_HOME = new SimPos(4, 64, 4);

    private static final class LoadedBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static Settlement town() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        town.setCatalogue(List.of(HOUSE, SHED));
        town.setFoodStock(100_000);   // this file isolates housing
        return town;
    }

    /**
     * A family of three in the unrecognized house, and empty houses to shed
     * into.
     *
     * <p>The vacancies are the load-bearing part of the fixture. A family with
     * nowhere to move to merely holds its growth progress and nothing happens,
     * so a town without them hides the fault entirely. Four of them, because
     * every member put out founds a household that fills a house of its own.
     */
    private static Settlement townWithAForgottenHouse() {
        Settlement town = town();
        town.addBuilding(new Building(FORGOTTEN, FORGOTTEN_HOME, 0, true));
        for (int i = 0; i < 4; i++) {
            town.addBuilding(new Building(HOUSE.id(), new SimPos(20 + i * 8, 64, 20), 0, true));
        }

        Household family = new Household(Household.Id.random(), "Nameless");
        family.setHome(FORGOTTEN_HOME);
        for (int i = 0; i < 3; i++) {
            Person person = new Person(
                    Person.Id.random(), "Settler " + i, Profession.FARMER, FORGOTTEN_HOME);
            town.addResident(person);
            family.addMember(person.id());
        }
        town.addHousehold(family);
        return town;
    }

    private static Household atForgottenHome(Settlement town) {
        return town.households().stream()
                .filter(household -> FORGOTTEN_HOME.equals(household.home()))
                .findFirst()
                .orElse(null);
    }

    // SANDBOX because it has raids off: a raid's schedule hashes the
    // settlement's random id, which would make the counts below vary run to run.
    private static void steps(Settlement town, int count) {
        for (int i = 0; i < count; i++) {
            town.step(new SimContext(new LoadedBridge(), i, SimSettings.SANDBOX));
        }
    }

    // --- the fault, inverted ---

    @Test
    void aFamilyInAnUnsizeableHouseIsLeftAlone() {
        Settlement town = townWithAForgottenHouse();
        assertEquals(3, atForgottenHome(town).size(), "three to begin with");

        steps(town, 200);

        Household family = atForgottenHome(town);
        assertTrue(family != null, "the family should still exist");
        assertEquals(3, family.size(),
                "an unknown capacity is not an overcrowding; nobody is put out");
    }

    @Test
    void anUnsizeableHouseHostsNoBirthsEither() {
        // The other half of the decision, and the conservative one: a town that
        // cannot count the beds in a house cannot promise there is a spare.
        // Counted on the town rather than on the household, because a household
        // that both bore a child and shed a member would still read three.
        Settlement town = townWithAForgottenHouse();

        steps(town, 200);

        assertEquals(3, town.population(), "nobody was born in a house nobody can size");
        assertEquals(1, town.households().size(), "and no second family was founded");
    }

    @Test
    void anUnsizeableHouseIsNotAVacancy() {
        // Nobody may be moved *into* it either. The house exists; how much of it
        // is bedroom does not.
        Settlement town = town();
        town.addBuilding(new Building(FORGOTTEN, FORGOTTEN_HOME, 0, true));
        town.addResident(new Person(
                Person.Id.random(), "Wanderer", Profession.FARMER, town.centre()));

        steps(town, 10);

        assertEquals(0, PopulationPlanner.totalHousingCapacity(town),
                "a house the catalog cannot size promises no beds");
        assertTrue(town.households().stream().noneMatch(Household::isHoused),
                "and houses nobody");
    }

    // --- the behavior that must not change ---

    @Test
    void aKnownHouseStillShedsWhenOverfull() {
        // Same fixture, a house of four holding four, with somewhere to go. This
        // is the case zero was always the right answer for.
        Settlement town = town();
        town.addBuilding(new Building(HOUSE.id(), new SimPos(4, 64, 4), 0, true));
        town.addBuilding(new Building(HOUSE.id(), new SimPos(20, 64, 20), 0, true));

        Household family = new Household(Household.Id.random(), "Baker");
        family.setHome(new SimPos(4, 64, 4));
        for (int i = 0; i < 4; i++) {
            Person person = new Person(
                    Person.Id.random(), "Settler " + i, Profession.FARMER, new SimPos(4, 64, 4));
            town.addResident(person);
            family.addMember(person.id());
        }
        town.addHousehold(family);

        steps(town, PopulationPlanner.STEPS_PER_BIRTH);

        assertEquals(3, family.size(), "the fourth moves out of a full house");
        assertEquals(2, town.households().size(), "and founds a family in the empty one");
        assertEquals(4, town.population(), "moving out is not a birth");
    }

    // --- what "unknown" means ---

    @Test
    void aShedIsKnownToHoldNobody() {
        assertEquals(OptionalInt.of(0), PopulationPlanner.capacityOf(town(), SHED.id()),
                "the catalog has been asked and has answered");
    }

    @Test
    void anUnmatchedIdIsUnknown() {
        assertEquals(OptionalInt.empty(), PopulationPlanner.capacityOf(town(), FORGOTTEN));
    }

    @Test
    void aLevelledOrStyledIdStillResolves() {
        // The trap this test exists for: unknown must mean genuinely unmatched,
        // not merely addressed differently. A raised house and a culture's own
        // version of one are the same building at three addresses -- which is
        // the rule BuildingRole already states and this now borrows.
        Settlement town = town();
        assertEquals(OptionalInt.of(4), PopulationPlanner.capacityOf(town, "test:house_l2"),
                "a raised house is a house");
        assertEquals(OptionalInt.of(4), PopulationPlanner.capacityOf(town, "test:norman/house"),
                "and so is a Norman one");
        assertEquals(OptionalInt.of(4), PopulationPlanner.capacityOf(town, "test:norman/house_l2"),
                "and so is a raised Norman one");
    }

    @Test
    void aStyledBunkhouseIsStillBunks() {
        // The hole the bare-name fallback opens if only half of it is dug. The
        // catalog now finds kingdoms:norman/bunkhouse and reports its six
        // beds; isFamilyHome had to widen with it, or families would have moved
        // into a bunkhouse and bred in it — the one thing that rule exists to
        // stop, reintroduced by a lookup being made cleverer on its own.
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 256);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.addBuilding(new Building(
                "kingdoms:norman/bunkhouse", new SimPos(4, 64, 4), 0, true));

        assertEquals(OptionalInt.of(6),
                PopulationPlanner.capacityOf(town, "kingdoms:norman/bunkhouse"),
                "a Norman bunkhouse holds what a bunkhouse holds");
        assertFalse(town.isFamilyHome(new SimPos(4, 64, 4)),
                "and is no more somewhere to raise a family than a plain one");
    }

    @Test
    void aFamilyInARaisedHouseGrowsNormally() {
        // The same again through the planner rather than the lookup, because the
        // lookup answering right is no use if the household is still skipped.
        Settlement town = town();
        town.addBuilding(new Building("test:house_l2", new SimPos(4, 64, 4), 0, true));

        Household family = new Household(Household.Id.random(), "Miller");
        family.setHome(new SimPos(4, 64, 4));
        for (int i = 0; i < 2; i++) {
            Person person = new Person(
                    Person.Id.random(), "Settler " + i, Profession.FARMER, new SimPos(4, 64, 4));
            town.addResident(person);
            family.addMember(person.id());
        }
        town.addHousehold(family);

        steps(town, PopulationPlanner.STEPS_PER_BIRTH);

        assertEquals(3, family.size(), "a raised house has room for a child");
    }

    @Test
    void anUnhousedFamilyHasNoBedsRatherThanUnknownBeds() {
        Settlement town = town();
        Household homeless = new Household(Household.Id.random(), "Turner");
        town.addHousehold(homeless);

        assertEquals(OptionalInt.of(0), PopulationPlanner.capacityOfHome(town, homeless),
                "no house is a fact about the beds, not a shrug");
    }

    @Test
    void aFamilyHomedWhereNoBuildingStandsIsStillTreatedAsFull() {
        // Deliberately not changed. A household whose recorded home has no
        // building on it is a different claim from one whose building the
        // catalog cannot size: there is nothing there, which is knowably no
        // beds. Reading it as unknown would be worse than the fault being fixed
        // — Settlement.isFamilyHome answers true for a spot it has no record of,
        // and nothing else ever clears a home, so the family would be frozen at
        // a phantom address forever. Shedding puts its members in houses that
        // exist, one per birth cycle, and retires the household after the last.
        Settlement town = town();
        town.addBuilding(new Building(HOUSE.id(), new SimPos(20, 64, 20), 0, true));

        Household stranded = new Household(Household.Id.random(), "Fletcher");
        stranded.setHome(new SimPos(4, 64, 4));   // no building here
        Person person = new Person(
                Person.Id.random(), "Settler", Profession.FARMER, new SimPos(4, 64, 4));
        town.addResident(person);
        stranded.addMember(person.id());
        town.addHousehold(stranded);

        assertEquals(OptionalInt.of(0), PopulationPlanner.capacityOfHome(town, stranded));

        steps(town, PopulationPlanner.STEPS_PER_BIRTH);

        assertTrue(town.households().stream()
                        .anyMatch(h -> new SimPos(20, 64, 20).equals(h.home())),
                "the stranded settler ends up in a house that exists");
    }
}
