package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;
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
            new BuildingType("civilization:house", 20, 0, 0, 1, 50, 4);
    private static final BuildingType STORE =
            new BuildingType("civilization:storehouse", 20, 0, 1, 0, 50, 0);
    private static final BuildingType HALL =
            new BuildingType("civilization:town_hall", 20, 0, 1, 0, 90, 0);

    private static Settlement townOf(int residents, String... standing) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        for (int i = 0; i < residents; i++) {
            town.addResident(new Person(
                    Person.Id.random(), "Resident " + i, Profession.BUILDER, town.center()));
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
        Settlement town = townOf(5, "civilization:house", "civilization:house", "civilization:house");

        Optional<BuildingType> next =
                BuildPlanner.chooseNext(town, List.of(HOUSE, STORE));

        assertTrue(next.isPresent());
        assertEquals("civilization:storehouse", next.get().id(),
                "the first of a kind is worth more than the fourth of another");
    }

    @Test
    void priorityStillDecidesEverythingItHasAnOpinionAbout() {
        // The share is only a tiebreak. A hall outranks a storehouse whatever
        // either of them is short by.
        Settlement town = townOf(5, "civilization:house", "civilization:house", "civilization:house");

        Optional<BuildingType> next =
                BuildPlanner.chooseNext(town, List.of(HOUSE, STORE, HALL));

        assertEquals("civilization:town_hall", next.get().id());
    }

    @Test
    void aTownThatWantsNothingBuildsNothing() {
        Settlement town = townOf(1, "civilization:house", "civilization:storehouse");

        assertTrue(BuildPlanner.chooseNext(town, List.of(HOUSE, STORE)).isEmpty(),
                "one resident wants one house and one store, and has both");
    }

    @Test
    void aTownTooSmallForSomethingDoesNotOrderIt() {
        BuildingType grand = new BuildingType("civilization:market", 20, 40, 1, 0, 99, 0);
        Settlement town = townOf(3);

        Optional<BuildingType> next = BuildPlanner.chooseNext(town, List.of(HOUSE, grand));

        assertEquals("civilization:house", next.get().id(),
                "the market outranks everything and is still years away");
    }

    @Test
    void theShareIsMeasuredAgainstWhatIsWantedNotWhatIsBuilt() {
        Settlement town = townOf(10, "civilization:house");

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
        BuildingType unwanted = new BuildingType("civilization:folly", 20, 0, 0, 0, 50, 0);
        Settlement town = townOf(5);

        assertEquals(0, BuildPlanner.shareShort(town, unwanted, 5));
        assertTrue(BuildPlanner.chooseNext(town, List.of(unwanted)).isEmpty(),
                "and it is never chosen at all");
    }

    // --- reacting to what is running out ---

    private static final BuildingType MINE =
            new BuildingType("civilization:mine", 20, 0, 1, 0, 50, 0);

    @Test
    void aTownRunningOutOfStoneWantsItsMineFirst() {
        // Same priority, same shortfall share: the tiebreak is that one of them
        // makes the thing the town is running out of.
        Settlement town = townOf(5);
        town.setStock(TownStores.STONE, 0);

        Optional<BuildingType> next = BuildPlanner.chooseNext(town, List.of(STORE, MINE));

        assertEquals("civilization:mine", next.get().id());
    }

    @Test
    void aTownSittingOnStoneIsNotToldToBuildAnotherMine() {
        Settlement town = townOf(5);
        town.setStock(TownStores.STONE, 5_000);

        assertEquals(0, BuildPlanner.makesSomethingScarce(town, MINE),
                "nine hundred blocks of stone is not a stone shortage");
    }

    @Test
    void scarcityNeverOutranksPriority() {
        // A town short of stone does not stop building its hall to raise a mine.
        Settlement town = townOf(5);
        town.setStock(TownStores.STONE, 0);

        Optional<BuildingType> next = BuildPlanner.chooseNext(town, List.of(MINE, HALL));

        assertEquals("civilization:town_hall", next.get().id());
    }

    @Test
    void aBuildingThatMakesNothingIsNeverScarce() {
        Settlement town = townOf(5);
        town.setStock(TownStores.STONE, 0);
        town.setStock(TownStores.WOOD, 0);

        assertEquals(0, BuildPlanner.makesSomethingScarce(town, HOUSE),
                "a house produces nothing, so no shortage argues for one");
    }

    @Test
    void animprovedProducerStillCountsAsOne() {
        // baseIdOf strips the level, so a mine raised to level two is still the
        // building a stone shortage is asking for.
        BuildingType improved = new BuildingType("civilization:mine_l2", 20, 0, 1, 0, 50, 0);
        Settlement town = townOf(5);
        town.setStock(TownStores.STONE, 0);

        assertEquals(1, BuildPlanner.makesSomethingScarce(town, improved));
    }
}
