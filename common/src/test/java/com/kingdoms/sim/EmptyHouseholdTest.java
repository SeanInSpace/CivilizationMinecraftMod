package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.kingdoms.sim.settlement.PopulationPlanner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A household with nobody left in it must not take the server down.
 *
 * <p>Found by a creeper probe, of all things — it crashed the integrated server
 * on the tick and on every tick after, so the client fell out of the world.
 *
 * <p>The shape of it. A household whose members have all died still has a home,
 * and a home the catalogue has no matching building for reports a capacity of
 * zero. Zero capacity reads as <em>full</em> — {@code size() < capacity} is
 * {@code 0 < 0} — so the planner decides the house is overcrowded and sends
 * "the most recently added member" out to found a family in the first vacant
 * house. There is no most recently added member. {@code List.getLast()} threw.
 *
 * <p>And the house it lived in was reserved for it forever. An empty household
 * still reported {@code isHoused()}, so {@code firstVacantHome} counted its home
 * as taken — a house nobody lived in and nobody could move into. Empty
 * households are retired now, at the top of the population step, so the house is
 * back on the market the same step it falls vacant.
 *
 * <p>Every gate before the crash has to be open for it to fire, which is why it
 * took a running world to find and why this fixture is as furnished as it is:
 * the ghost household must be housed, its home must count as a family home, the
 * town must be under its population cap and hold enough food to feed another
 * mouth, and — the one that hides the bug from a simpler fixture — there must be
 * a <em>vacant</em> house standing for it to try to split into. Without one the
 * family just holds its progress and nothing ever throws.
 */
class EmptyHouseholdTest {

    /** Capacity four, so a standing one is somewhere a family could move to. */
    private static final BuildingType COTTAGE =
            new BuildingType("test:cottage", 20, 9999, 0, 0, 80, 4);

    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /**
     * A town holding one living family, one household that has died out, and an
     * empty cottage for somebody to move into.
     */
    private static Settlement townWithAGhostFamily() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        town.setCatalogue(List.of(COTTAGE));
        town.stores().add(TownStores.FOOD, 400);

        // The Smiths' own cottage. It matters that this one exists: without it
        // their home reads as capacity zero too, so *they* count as overcrowded
        // and take the vacant house first — and the ghosts, reached second, find
        // nowhere to split into and never trip the bug.
        town.addBuilding(new Building(COTTAGE.id(), new SimPos(8, 64, 8), 0, true));

        // And an empty one. Nobody lives here, so it is the vacant home the
        // planner reaches for on behalf of the household that has died out.
        town.addBuilding(new Building(COTTAGE.id(), new SimPos(20, 64, 20), 0, true));

        Person alice = new Person(
                Person.Id.random(), "Alice Smith", Profession.FARMER, town.centre());
        town.addResident(alice);
        Household living = new Household(Household.Id.random(), "Smith");
        living.setHome(new SimPos(8, 64, 8));
        living.addMember(alice.id());
        town.addHousehold(living);

        // The ghosts. Housed, and at a spot with no building record — so their
        // home's capacity reads zero, which reads as full.
        Household ghosts = new Household(Household.Id.random(), "Nobody");
        ghosts.setHome(new SimPos(4, 64, 4));
        town.addHousehold(ghosts);
        return town;
    }

    @Test
    void aHouseholdThatHasDiedOutDoesNotCrashTheTick() {
        Settlement town = townWithAGhostFamily();

        assertDoesNotThrow(() -> {
            for (int step = 0; step < 30; step++) {
                town.step(new SimContext(new QuietBridge(), step, SimSettings.SANDBOX));
            }
        }, "an empty household is the record of a family that died out, not a family");
    }

    @Test
    void aHouseWhoseFamilyDiedOutGoesBackOnTheMarket() {
        // The point of retiring the household rather than merely skipping it.
        Settlement town = townWithAGhostFamily();
        Household ghosts = town.households().stream()
                .filter(household -> household.members().isEmpty())
                .findFirst()
                .orElseThrow();
        SimPos emptyHouse = ghosts.home();

        town.step(new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX));

        assertFalse(town.households().contains(ghosts),
                "a household with nobody in it is not a household");
        assertTrue(PopulationPlanner.firstVacantHome(town) != null,
                "and there is somewhere for a new family to live");
        assertTrue(town.households().stream()
                        .noneMatch(household -> emptyHouse.equals(household.home())
                                && household.members().isEmpty()),
                "nobody is holding the deeds to an empty house");
    }

    @Test
    void aFamilyThatShedsItsLastMemberDoesNotLeaveAReservedHouse() {
        // splitFamilyInto takes a member straight off a household, which is the
        // one path that can empty one without going through removePerson. A home
        // reporting zero capacity reads as permanently overcrowded, so the family
        // sheds somebody every birth cycle until there is nobody left.
        Settlement town = townWithAGhostFamily();

        for (int step = 0; step < 120; step++) {
            town.step(new SimContext(new QuietBridge(), step, SimSettings.SANDBOX));
        }

        assertTrue(town.households().stream().noneMatch(h -> h.members().isEmpty()),
                "no household should outlive the last person in it");
    }

    @Test
    void theLivingFamilyIsStillAllowedToGrow() {
        // The guard must skip the empty household, not the whole pass. A fix that
        // stopped every town growing would also make the crash go away.
        Settlement town = townWithAGhostFamily();
        int before = town.population();

        for (int step = 0; step < 30; step++) {
            town.step(new SimContext(new QuietBridge(), step, SimSettings.SANDBOX));
        }

        assertTrue(town.population() > before,
                "the Smiths have a house, food and room to grow into");
    }
}
