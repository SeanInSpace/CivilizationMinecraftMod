package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FieldRoster;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which farmer works which field.
 *
 * <p>Nobody decided this before: every farmer walked to whichever farm was
 * nearest to where they were standing, and a farmer standing on a farm is
 * nearest to that one — so the first field anybody reached became the only one
 * the town ever worked. A live run held a field at {@code 72 farmland, 26
 * planted} for seven minutes in a fully watched town of forty-eight, while
 * other fields in the same town rose and fell.
 */
class FieldRosterTest {

    private static Settlement townWith(int fields, int farmers) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 512);
        for (int i = 0; i < fields; i++) {
            Building farm = new Building(
                    "kingdoms:farm", new SimPos(40 + i * 40, 64, 0), 1, true);
            town.addBuilding(farm);
        }
        for (int i = 0; i < farmers; i++) {
            town.addResident(new Person(
                    Person.Id.random(), "Hand " + i, Profession.FARMER, town.centre()));
        }
        return town;
    }

    private static List<Person> farmersOf(Settlement town) {
        return town.residents().stream()
                .filter(p -> p.profession() == Profession.FARMER)
                .toList();
    }

    @Test
    void everyFieldGetsSomebodyBeforeAnyFieldGetsTwo() {
        // The defect, stated as a rule. Four farmers and four fields must not
        // put all four on one field and leave three standing empty.
        Settlement town = townWith(4, 4);

        Set<SimPos> worked = new HashSet<>();
        for (Person farmer : farmersOf(town)) {
            worked.add(FieldRoster.fieldFor(town, farmer).origin());
        }

        assertEquals(4, worked.size(), "four farmers, four fields, four fields worked");
    }

    @Test
    void moreFarmersThanFieldsStillCoversEveryField() {
        Settlement town = townWith(3, 11);

        Set<SimPos> worked = new HashSet<>();
        for (Person farmer : farmersOf(town)) {
            worked.add(FieldRoster.fieldFor(town, farmer).origin());
        }

        assertEquals(3, worked.size(), "every field has a hand on it");
    }

    @Test
    void fewerFarmersThanFieldsSpreadsThemOut() {
        // Two farmers cannot cover five fields, but they must not both stand in
        // the same one.
        Settlement town = townWith(5, 2);

        Set<SimPos> worked = new HashSet<>();
        for (Person farmer : farmersOf(town)) {
            worked.add(FieldRoster.fieldFor(town, farmer).origin());
        }

        assertEquals(2, worked.size(), "two farmers, two different fields");
    }

    @Test
    void aFarmerKeepsTheSameFieldFromStepToStep() {
        // Otherwise they spend the day walking between two fields and plant
        // nothing in either.
        Settlement town = townWith(3, 3);
        Person farmer = farmersOf(town).getFirst();

        SimPos first = FieldRoster.fieldFor(town, farmer).origin();
        for (int step = 0; step < 20; step++) {
            assertEquals(first, FieldRoster.fieldFor(town, farmer).origin(),
                    "the roster changed its mind on step " + step);
        }
    }

    @Test
    void aTownWithNoFieldsSendsNobodyAnywhere() {
        Settlement town = townWith(0, 3);
        assertNull(FieldRoster.fieldFor(town, farmersOf(town).getFirst()),
                "there is nothing to work, and pretending otherwise strands them");
    }

    @Test
    void theRosterIsAboutFarmersAndNobodyElse() {
        Settlement town = townWith(2, 1);
        Person smith = new Person(
                Person.Id.random(), "Smith", Profession.SMITH, town.centre());
        town.addResident(smith);

        assertNull(FieldRoster.fieldFor(town, smith), "a smith does not appear on it");
        assertNotNull(FieldRoster.fieldFor(town, farmersOf(town).getFirst()));
    }

    @Test
    void anAnimalFarmIsNotAFieldAnybodyPlants() {
        // contains("farm") is the obvious trap and matches a pen full of pigs.
        Settlement town = townWith(1, 1);
        town.addBuilding(new Building(
                "kingdoms:animal_farm", new SimPos(-80, 64, 0), 1, true));

        assertEquals(1, FieldRoster.fields(town).size(), "one crop field, not two");
        assertEquals(new SimPos(40, 64, 0),
                FieldRoster.fieldFor(town, farmersOf(town).getFirst()).origin());
    }

    @Test
    void aFieldNothingHasBeenBuiltOnYetIsNotStaffed() {
        // A farm the simulation has recorded but the world has not drawn has no
        // soil in it. Sending a farmer to stand in a field that is not there is
        // how a town ends up with idle farmers and a hungry granary.
        Settlement town = townWith(1, 1);
        town.addBuilding(new Building(
                "kingdoms:farm", new SimPos(-120, 64, 0), 1, false));

        assertEquals(1, FieldRoster.fields(town).size(), "only the one that exists");
    }

    @Test
    void theSameTownAlwaysDealsTheSameHands() {
        // A reload must not reshuffle the whole workforce between fields.
        Settlement town = townWith(3, 6);
        for (Person farmer : farmersOf(town)) {
            SimPos once = FieldRoster.fieldFor(town, farmer).origin();
            SimPos twice = FieldRoster.fieldFor(town, farmer).origin();
            assertEquals(once, twice);
        }
        assertTrue(FieldRoster.fields(town).get(0).origin().x()
                        < FieldRoster.fields(town).get(1).origin().x(),
                "fields come out in a fixed order, not whatever the list happened to hold");
    }
}
