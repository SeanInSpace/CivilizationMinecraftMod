package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.BuildLoad;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A builder cannot build what they are not holding.
 *
 * <p>The rule these cover is the one the view layer consults twice per pass: at
 * the wall, before a swing lays anything, and again in the stall assist, which
 * used to place a block after twenty unproductive passes whoever was or was not
 * carrying one. Both ask {@link BuildLoad#canLay}, so both are tested here.
 *
 * <p>These are the watched fidelity only. Where nobody is looking there are no
 * hands to fill and the clock pays per work unit out of the pooled ledger; see
 * {@code Settlement.payForProgress} and {@code VisibleConstructionTest}.
 */
class BuildLoadTest {

    private static Person builder() {
        return new Person(Person.Id.random(), "Digger", Profession.BUILDER, new SimPos(0, 64, 0));
    }

    // --- what a hand may lay ---

    @Test
    void emptyHandsLayNothingHoweverRichTheTownIs() {
        // The complaint this whole rule answers. Owning a log somewhere across
        // the village used to be enough; the load a builder walked to fetch was
        // decorative, and the block came out of the books regardless.
        Person person = builder();

        assertFalse(BuildLoad.canLay(TownStores.WOOD, person));
        assertFalse(BuildLoad.canLay(TownStores.STONE, person));
    }

    @Test
    void theWrongLoadIsNoLoadAtAll() {
        Person person = builder();
        person.setCarry(TownStores.WOOD, 16);

        assertFalse(BuildLoad.canLay(TownStores.STONE, person),
                "timber does not lay masonry");
        assertTrue(BuildLoad.canLay(TownStores.WOOD, person));
    }

    @Test
    void theRightLoadLaysBlocksUntilItRunsOut() {
        Person person = builder();
        person.setCarry(TownStores.STONE, 2);

        assertTrue(BuildLoad.canLay(TownStores.STONE, person));
        person.spendCarry();
        assertTrue(BuildLoad.canLay(TownStores.STONE, person), "one left, one more block");
        person.spendCarry();
        assertFalse(BuildLoad.canLay(TownStores.STONE, person),
                "and then back to the warehouse");
    }

    @Test
    void astepThatCostsNothingNeedsNothingInHand() {
        // Glass, crops and soil are not things a town makes, and the bootstrap
        // producers are exempt everywhere else too — a watched town out of stone
        // that could not raise its own mine would never have stone again. Both
        // arrive here as a null material.
        assertTrue(BuildLoad.canLay(null, builder()));
        assertTrue(BuildLoad.canLay(null, null), "nor anybody to hold it");
    }

    @Test
    void anEntityWithNoRecordBehindItHasNoHands() {
        assertFalse(BuildLoad.canLay(TownStores.WOOD, null));
    }

    @Test
    void thestallAssistFindsNobodyToLayAStepTheCrewCannotPayFor() {
        // The hole that used to run straight through the rule: after twenty
        // unproductive passes the assist placed a block for whichever builder
        // happened to be standing nearest, carrying nothing. The assist now
        // walks the crew asking this, so a crew with empty hands gets no help.
        List<Person> crew = new ArrayList<>();
        crew.add(builder());
        crew.add(builder());
        crew.get(1).setCarry(TownStores.STONE, 8);

        assertNull(firstWhoCanLay(TownStores.WOOD, crew),
                "one empty pair of hands and one full of the wrong thing");
        assertEquals(crew.get(1), firstWhoCanLay(TownStores.STONE, crew),
                "but the one actually holding stone can lay stone");
    }

    /** The stall assist's choice, stated the way {@code assistStalledSite} walks it. */
    private static Person firstWhoCanLay(String owed, List<Person> crew) {
        for (Person person : crew) {
            if (BuildLoad.canLay(owed, person)) {
                return person;
            }
        }
        return null;
    }

    // --- where the town is actually charged ---

    @Test
    void theLedgerIsChargedAtPickupAndNeverAgainAtTheWall() {
        // Sixteen blocks laid for sixteen timber, not thirty-two. The warehouse
        // is the only till on the watched path; spending a carried block touches
        // the books not at all, which is exactly why the laying path must pass
        // "carried" through to BlueprintPlacer and skip payFor.
        TownStores stores = new TownStores();
        stores.set(TownStores.WOOD, 40);
        Person person = builder();

        assertEquals(16, BuildLoad.pickUp(stores, person, TownStores.WOOD));
        assertEquals(24, stores.get(TownStores.WOOD), "the stores are lighter immediately");

        for (int block = 0; block < 16; block++) {
            assertTrue(BuildLoad.canLay(TownStores.WOOD, person));
            person.spendCarry();
        }

        assertEquals(24, stores.get(TownStores.WOOD),
                "and not one log more came off the books on the way up the wall");
        assertEquals(0, person.carriedLoad());
    }

    @Test
    void anEmptyShelfLoadsNobody() {
        TownStores stores = new TownStores();
        Person person = builder();

        assertEquals(0, BuildLoad.pickUp(stores, person, TownStores.WOOD));
        assertFalse(BuildLoad.canLay(TownStores.WOOD, person));
    }

    @Test
    void aChangeOfCourseReturnsWhatIsLeftRatherThanBurningIt() {
        // A house alternates timber and masonry every few blocks. Overwriting
        // the load each time — which is all setCarry does on its own — threw the
        // remainder away, so the town paid for a full armful of each course and
        // got a few blocks of it.
        TownStores stores = new TownStores();
        stores.set(TownStores.WOOD, 16);
        stores.set(TownStores.STONE, 8);
        Person person = builder();

        BuildLoad.pickUp(stores, person, TownStores.WOOD);
        person.spendCarry();
        person.spendCarry();
        person.spendCarry();
        person.spendCarry();

        assertEquals(8, BuildLoad.pickUp(stores, person, TownStores.STONE));

        assertEquals(12, stores.get(TownStores.WOOD),
                "the twelve unused logs went back on the shelf they came off");
        assertEquals(0, stores.get(TownStores.STONE));
        assertEquals(8, person.carriedLoad());
        assertTrue(BuildLoad.canLay(TownStores.STONE, person));
        assertFalse(BuildLoad.canLay(TownStores.WOOD, person));
    }

    @Test
    void anEmptyShelfLeavesABuilderHoldingWhatTheyHave() {
        // A failed trip must not cost them the load they set out with. Putting
        // the timber back before finding out there is no stone here is the same
        // waste by a longer route.
        TownStores stores = new TownStores();
        Person person = builder();
        person.setCarry(TownStores.WOOD, 9);

        assertEquals(0, BuildLoad.pickUp(stores, person, TownStores.STONE));

        assertEquals(9, person.carriedLoad(), "still carrying the timber");
        assertEquals(TownStores.WOOD, person.carriedMaterial());
        assertEquals(0, stores.get(TownStores.WOOD), "and nothing was tipped out here");
    }

    @Test
    void nothingIsCreatedOrDestroyedByPuttingALoadBack() {
        TownStores stores = new TownStores();
        stores.set(TownStores.WOOD, 20);
        Person person = builder();

        BuildLoad.pickUp(stores, person, TownStores.WOOD);
        BuildLoad.putBack(stores, person);

        assertEquals(20, stores.get(TownStores.WOOD));
        assertEquals(0, person.carriedLoad());
        assertNull(person.carriedMaterial());
    }

    @Test
    void aReleasedBuilderHandsTheirLoadBackToTheTown() {
        // What happens when the player walks away mid-trip. The clock that takes
        // over pays out of the pooled ledger and cannot see a load in somebody's
        // arms, so an armful released into a Person record would be an armful the
        // town no longer counts — every approach and departure costing it one.
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 128);
        town.addBuilding(new Building("kingdoms:storehouse", new SimPos(4, 64, 4), 1, true));
        town.putAwayLoosePile();
        int owned = town.stores().get(TownStores.WOOD);
        Person person = builder();
        BuildLoad.pickUp(town.stores(), person, TownStores.WOOD);
        person.spendCarry();
        assertEquals(owned - 16, town.stores().get(TownStores.WOOD), "the load is off the books");

        BuildLoad.putBack(town.stores(), person);

        assertEquals(owned - 1, town.stores().get(TownStores.WOOD),
                "everything but the block they actually laid is back on them");
        assertEquals(0, person.carriedLoad());
    }

    @Test
    void puttingBackEmptyHandsIsHarmless() {
        TownStores stores = new TownStores();
        stores.set(TownStores.WOOD, 5);

        BuildLoad.putBack(stores, builder());

        assertEquals(5, stores.get(TownStores.WOOD));
    }
}
