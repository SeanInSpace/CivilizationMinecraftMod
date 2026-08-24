package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a town builds next.
 *
 * <p>Ranked on the raw shortfall, a town wanting five houses with three
 * standing is two short and beat its very first storehouse, which is only one
 * short — so it built a fourth house while having nowhere to put anything.
 * Measured as a share of what is wanted, the storehouse is missing all of its
 * one and the houses two fifths of their five.
 */
class BuildOrderTest {

    //                                    id            work  minPop  base  perN  priority  cap
    private static final BuildingType HOUSE =
            new BuildingType("kingdoms:house", 20, 0, 0, 1, 50, 4);
    private static final BuildingType STORE =
            new BuildingType("kingdoms:storehouse", 20, 0, 1, 0, 50, 0);
    private static final BuildingType HALL =
            new BuildingType("kingdoms:town_hall", 20, 0, 1, 0, 90, 0);

    private static Settlement townOf(int residents, String... standing) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        for (int i = 0; i < residents; i++) {
            town.addResident(new Person(
                    Person.Id.random(), "Resident " + i, Profession.BUILDER, town.centre()));
        }
        int spread = 0;
        for (String id : standing) {
            town.addBuilding(new Building(id, new SimPos(spread += 12, 64, 0), 1, true));
        }
        return town;
    }

    @Test
    void aTownWithNoStorehouseBuildsOneBeforeAFourthHouse() {
        // Five residents want five houses; three stand. The storehouse wants one
        // and none stands. On raw shortfall the houses win two to one.
        Settlement town = townOf(5, "kingdoms:house", "kingdoms:house", "kingdoms:house");

        Optional<BuildingType> next =
                BuildPlanner.chooseNext(town, List.of(HOUSE, STORE));

        assertTrue(next.isPresent());
        assertEquals("kingdoms:storehouse", next.get().id(),
                "the first of a kind is worth more than the fourth of another");
    }

    @Test
    void priorityStillDecidesEverythingItHasAnOpinionAbout() {
        // The share is only a tiebreak. A hall outranks a storehouse whatever
        // either of them is short by.
        Settlement town = townOf(5, "kingdoms:house", "kingdoms:house", "kingdoms:house");

        Optional<BuildingType> next =
                BuildPlanner.chooseNext(town, List.of(HOUSE, STORE, HALL));

        assertEquals("kingdoms:town_hall", next.get().id());
    }

    @Test
    void aTownThatWantsNothingBuildsNothing() {
        Settlement town = townOf(1, "kingdoms:house", "kingdoms:storehouse");

        assertTrue(BuildPlanner.chooseNext(town, List.of(HOUSE, STORE)).isEmpty(),
                "one resident wants one house and one store, and has both");
    }

    @Test
    void aTownTooSmallForSomethingDoesNotOrderIt() {
        BuildingType grand = new BuildingType("kingdoms:market", 20, 40, 1, 0, 99, 0);
        Settlement town = townOf(3);

        Optional<BuildingType> next = BuildPlanner.chooseNext(town, List.of(HOUSE, grand));

        assertEquals("kingdoms:house", next.get().id(),
                "the market outranks everything and is still years away");
    }

    @Test
    void theShareIsMeasuredAgainstWhatIsWantedNotWhatIsBuilt() {
        Settlement town = townOf(10, "kingdoms:house");

        // Ten residents want ten houses and one stands: nine tenths short.
        assertEquals(90, BuildPlanner.shareShort(town, HOUSE, 10));
        // The storehouse wants one and none stands: all of it.
        assertEquals(100, BuildPlanner.shareShort(town, STORE, 10));
    }

    @Test
    void aTypeNobodyWantsIsNotInfinitelyShort() {
        // desiredCount of zero would be a division by zero dressed up as an
        // urgent need, which is the sort of thing that empties a build queue
        // into one building forever.
        BuildingType unwanted = new BuildingType("kingdoms:folly", 20, 0, 0, 0, 50, 0);
        Settlement town = townOf(5);

        assertEquals(0, BuildPlanner.shareShort(town, unwanted, 5));
        assertTrue(BuildPlanner.chooseNext(town, List.of(unwanted)).isEmpty(),
                "and it is never chosen at all");
    }
}
