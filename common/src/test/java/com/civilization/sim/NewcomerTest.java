package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Race;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.kingdom.Kingdom;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.Newcomer;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimWorld;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who a spawn egg's settler joins, and who they are when they get there.
 *
 * <p>This is the whole of the interesting behavior behind the two settler eggs,
 * and none of it needs a world: an egg is a right-click that asks "is there a
 * town of this race on this ground", and the answer is arithmetic over claims
 * and culture ids. The right-click itself — the chat line, the stack shrinking,
 * the body appearing half a second later — is a playtest check and is written up
 * as one.
 */
class NewcomerTest {

    private static Settlement town(String cultureId, SimPos center, int claim) {
        Settlement town = new Settlement(Settlement.Id.random(), "Somewhere", center, claim);
        town.setCultureId(cultureId);
        return town;
    }

    /**
     * A world holding those towns, each in a kingdom of its own people.
     *
     * <p>One kingdom per town rather than one kingdom for all of them, because
     * {@code Kingdom.addSettlement} stamps the kingdom's culture onto whatever it
     * is handed — so a single realm would quietly turn every town in this test
     * Norman and the race filter would have nothing to filter.
     */
    private static SimWorld worldOf(Settlement... towns) {
        SimWorld world = new SimWorld(new TerrainFake(1));
        for (Settlement town : towns) {
            Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Realm", town.cultureId());
            kingdom.addSettlement(town);
            world.addKingdom(kingdom);
        }
        return world;
    }

    @Test
    @DisplayName("an empty world has nowhere to join, and that is not an exception")
    void anEmptyWorldHasNowhereToJoin() {
        assertNull(Newcomer.townFor(worldOf(), Race.ORC, new SimPos(0, 64, 0)));
        assertNull(Newcomer.townFor(new SimWorld(new TerrainFake(1)), Race.HUMAN,
                new SimPos(0, 64, 0)));
    }

    @Test
    @DisplayName("a town only takes its own race, however close the egg is used")
    void aTownOnlyTakesItsOwnRace() {
        Settlement humans = town(Culture.NORMAN.id(), new SimPos(0, 64, 0), 64);
        SimWorld world = worldOf(humans);
        SimPos here = new SimPos(4, 64, 4);

        assertSame(humans, Newcomer.townFor(world, Race.HUMAN, here));
        // Standing in the middle of a Norman town with an orc egg: an orc has no
        // more business joining it than a Norman has joining a warband.
        assertNull(Newcomer.townFor(world, Race.ORC, here));
        assertNull(Newcomer.townFor(world, Race.GOBLIN, here));
    }

    @Test
    @DisplayName("an orc town takes orcs, whatever culture of orc it is")
    void anOrcTownTakesOrcs() {
        Settlement warhost = town(Culture.ORC.id(), new SimPos(1000, 70, -1000), 48);
        SimWorld world = worldOf(warhost);
        assertSame(warhost, Newcomer.townFor(world, Race.ORC, new SimPos(1010, 70, -1010)));
        assertNull(Newcomer.townFor(world, Race.HUMAN, new SimPos(1010, 70, -1010)));
    }

    @Test
    @DisplayName("outside the claim is outside the town, by one block as much as by a thousand")
    void outsideTheClaimIsOutsideTheTown() {
        Settlement camp = town(Culture.ORC.id(), new SimPos(0, 64, 0), 40);
        SimWorld world = worldOf(camp);
        // The claim is a horizontal radius, so height must not enter into it: an
        // orc on a tower forty blocks up over the camp is still in the camp.
        assertSame(camp, Newcomer.townFor(world, Race.ORC, new SimPos(39, 200, 0)));
        assertSame(camp, Newcomer.townFor(world, Race.ORC, new SimPos(0, -40, 39)));
        assertNull(Newcomer.townFor(world, Race.ORC, new SimPos(41, 64, 0)));
        assertNull(Newcomer.townFor(world, Race.ORC, new SimPos(500, 64, 500)));
    }

    @Test
    @DisplayName("where two claims overlap, the nearer center wins and keeps winning")
    void whereTwoClaimsOverlapTheNearerCenterWins() {
        Settlement near = town(Culture.ORC.id(), new SimPos(0, 64, 0), 100);
        Settlement far = town(Culture.ORC.id(), new SimPos(60, 64, 0), 100);
        SimWorld world = worldOf(near, far);

        assertSame(near, Newcomer.townFor(world, Race.ORC, new SimPos(10, 64, 0)));
        assertSame(far, Newcomer.townFor(world, Race.ORC, new SimPos(50, 64, 0)));
        // And the same spot answers the same way every time it is asked, which a
        // first-match over a map's values would not promise.
        for (int i = 0; i < 20; i++) {
            assertSame(near, Newcomer.townFor(world, Race.ORC, new SimPos(10, 64, 0)));
        }
    }

    @Test
    @DisplayName("an arrival is a resident of that town, idle, unhoused, and standing where they were put")
    void anArrivalIsAResidentOfThatTown() {
        Settlement camp = town(Culture.ORC.id(), new SimPos(0, 64, 0), 48);
        SimPos spot = new SimPos(12, 71, -5);

        Person arrival = Newcomer.arrive(camp, spot, 37);

        assertNotNull(arrival);
        assertSame(arrival, camp.resident(arrival.id()));
        assertEquals(1, camp.population());
        assertEquals(spot, arrival.position());
        // A profession is the town's decision, made by JobPlanner on the next
        // step out of what the town is short of.
        assertEquals(Profession.IDLER, arrival.profession());
        // And a bed is PopulationPlanner's. A household of one with a house
        // nobody assigned is a state nothing maintains.
        assertTrue(camp.households().isEmpty(), "an arrival brought a family with them");
        assertFalse(arrival.isTooWeakToWork(), "an arrival turned up starving");
        assertFalse(camp.events().isEmpty(), "nothing in the town's log says anybody came");
        assertEquals(37, camp.events().getLast().step());
    }

    @Test
    @DisplayName("arrivals are named by the town's own people, and not all the same name")
    void arrivalsAreNamedByTheTownsOwnPeople() {
        Settlement camp = town(Culture.ORC.id(), new SimPos(0, 64, 0), 48);
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            names.add(Newcomer.arrive(camp, new SimPos(i, 64, 0), i).name());
        }
        assertEquals(10, names.size(), "ten arrivals shared a name: " + names);
        for (String name : names) {
            String given = name.split(" ")[0];
            assertTrue(Culture.ORC.givenNames().contains(given),
                    given + " is not a name the warhost uses: " + name);
        }
    }

    @Test
    @DisplayName("the sentinel culture reads as human, so an unnamed town takes human eggs")
    void theSentinelCultureReadsAsHuman() {
        // Every town founded before cultures had names carries no culture id, and
        // Culture.of hands those the sentinel. A human egg has to work on one, or
        // the eggs would be useless in exactly the worlds people already have.
        Settlement old = new Settlement(Settlement.Id.random(), "Oldtown",
                new SimPos(0, 64, 0), 64);
        SimWorld world = worldOf(old);
        assertSame(old, Newcomer.townFor(world, Race.HUMAN, new SimPos(1, 64, 1)));
    }
}
