package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.PopulationPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A family's growth never stands above the threshold it is growing toward.
 *
 * <p>{@code /civ info} read "growth 45/24" and "36/24" on Millbrook, which is a
 * counter past its own cap for a counter whose whole documented behavior is to
 * hold at the line. Two separate faults made that one line, and this covers both.
 *
 * <ul>
 *   <li><strong>The report printed the wrong denominator.</strong>
 *       {@link PopulationPlanner#stepsPerBirthIn} stretches the base rate by how
 *       crowded the town is — a town of seventeen wants forty-eight steps, not
 *       twenty-four — and the report printed the base. So a family at step 45 of 48
 *       read as nine-tenths past a cap it had not reached. The report asks
 *       {@link PopulationPlanner#stepsToNextBirth} now, which is the function the
 *       gate asks.</li>
 *   <li><strong>The threshold falls, and progress did not fall with it.</strong>
 *       It is stretched by the population, so seventeen deaths in one night drop it
 *       — and every family holding banked progress against the old, larger
 *       threshold was left standing above the new one. Held from above as well as
 *       from below now.</li>
 * </ul>
 */
class GrowthCapTest {

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

    private static final SimContext CTX =
            new SimContext(new LoadedBridge(), 0, SimSettings.SANDBOX);

    private static final int BASE = SimSettings.SANDBOX.stepsPerBirth();

    /**
     * A town with one full house and a crowd standing outside it.
     *
     * <p>One house of four with four people in it is a family that can neither
     * bear — the house is full — nor split, because there is nowhere to split to.
     * That is the state where progress banks, which is the state this is about. The
     * crowd outside does the crowding: they are unhoused, so they never grow, and
     * they are what makes the threshold large.
     */
    private static Settlement crowded(int extras) {
        Settlement town = new Settlement(Settlement.Id.random(), "Millbrook",
                new SimPos(0, 64, 0), 256);
        town.setCatalog(CATALOG);
        town.setFoodStock(100_000);
        town.addBuilding(new Building(HOUSE.id(), new SimPos(10, 64, 0), 0, true));
        for (int i = 0; i < 4 + extras; i++) {
            town.addResident(new Person(Person.Id.random(), "Nobody " + i,
                    Profession.BUILDER, new SimPos(0, 64, 0)));
        }
        return town;
    }

    private static Household housedFamily(Settlement town) {
        return town.households().stream().filter(Household::isHoused).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("the threshold the report prints is the threshold the gate uses")
    void theReportAndTheGateAskTheSameFunction() {
        Settlement town = crowded(0);
        assertEquals(PopulationPlanner.stepsPerBirthIn(BASE, 4),
                PopulationPlanner.stepsToNextBirth(town, BASE));

        // And it is not the base rate, which is what the report used to print.
        Settlement big = crowded(60);
        assertTrue(PopulationPlanner.stepsToNextBirth(big, BASE) > BASE,
                "a town of sixty-four wants more than the base rate per birth,"
                        + " so printing the base rate is printing the wrong number");
    }

    @Test
    @DisplayName("banked growth holds at the threshold and never climbs past it")
    void growthHoldsAtTheLine() {
        Settlement town = crowded(60);
        for (int step = 0; step < 400; step++) {
            town.step(CTX);
            Household family = housedFamily(town);
            int threshold = PopulationPlanner.stepsToNextBirth(town, BASE);
            assertTrue(family.growthProgress() <= threshold,
                    "growth " + family.growthProgress() + "/" + threshold
                            + " at step " + step + " — the numerator is past its"
                            + " own denominator, which is what the report showed");
        }
        assertEquals(PopulationPlanner.stepsToNextBirth(town, BASE),
                housedFamily(town).growthProgress(),
                "a family with nowhere to put a child should be waiting at the line");
    }

    @Test
    @DisplayName("a bad night drops the threshold, and banked growth comes down with it")
    void aFallInPopulationBringsBankedGrowthDownToTheNewThreshold() {
        // The measured case. Seventeen of Millbrook's people died in one night, so
        // the crowding term fell, so the threshold fell -- under the progress every
        // surviving family had already banked against the larger one.
        Settlement town = crowded(60);
        int high = PopulationPlanner.stepsToNextBirth(town, BASE);
        for (int step = 0; step < high + 20; step++) {
            town.step(CTX);
        }
        assertEquals(high, housedFamily(town).growthProgress(), "the fixture never banked");

        // The night. Everybody standing outside the house dies.
        List<Person.Id> outside = new ArrayList<>();
        Household family = housedFamily(town);
        for (Person person : town.residents()) {
            if (!family.contains(person.id())) {
                outside.add(person.id());
            }
        }
        outside.forEach(town::removePerson);

        int low = PopulationPlanner.stepsToNextBirth(town, BASE);
        assertTrue(low < high, "the fixture did not actually change the threshold");
        assertTrue(housedFamily(town).growthProgress() > low,
                "the fixture is not in the state that produced 'growth 45/24'");

        town.step(CTX);

        assertTrue(housedFamily(town).growthProgress()
                        <= PopulationPlanner.stepsToNextBirth(town, BASE),
                "growth " + housedFamily(town).growthProgress() + "/"
                        + PopulationPlanner.stepsToNextBirth(town, BASE)
                        + " — progress is still standing above a threshold that fell");
    }
}
