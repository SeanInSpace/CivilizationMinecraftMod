package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Founding;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a player actually gets when they use a charter.
 *
 * <p>This was the one path into the game and the one path no test could reach:
 * the party was written out longhand inside the item, where nothing without a
 * world could call it. Meanwhile {@code /civ found} — which every scripted run
 * goes through — quietly made something else, a settlement with a kit and
 * nobody to spend it. Both come through {@link Founding} now, so these assert
 * the real thing.
 */
class FoundingTest {

    private static final SimPos SITE = new SimPos(120, 71, -48);

    private static Settlement chartered() {
        return Founding.party(SITE, "Testburg");
    }

    @Test
    void aCharterRaisesACampAndNotATown() {
        assertEquals(SettlementStage.CAMP, chartered().stage(),
                "a party that has just stepped off the road has earned nothing yet");
    }

    @Test
    void thePartyIsFourPioneers() {
        Settlement s = chartered();

        assertEquals(TownStores.FOUNDING_SETTLERS, s.population(),
                "the party is the size the kit was priced against");
        for (Person settler : s.residents()) {
            assertEquals(Profession.PIONEER, settler.profession(),
                    "generalists, so the camp can put them to whatever it needs");
        }
    }

    @Test
    void everySettlerCarriesTheirOwnRations() {
        // Not banked: until the first house stands there is no larder to fetch
        // from, so what they have on them is what they live on.
        for (Person settler : chartered().residents()) {
            assertEquals(TownStores.FOUNDING_PROVISIONS_EACH,
                    settler.inventory().count(Foods.PROVISION),
                    "what the kit promises is what they are actually carrying");
        }
    }

    @Test
    void theRationsFitInThePacksTheyAreCarriedIn() {
        // add() takes what it can and reports the rest away, so a kit priced
        // past the size of a pack would have settlers arrive quietly short of
        // what the founding economics assumed they had.
        int capacity = com.kingdoms.sim.person.Inventory.SLOTS
                * com.kingdoms.sim.person.Inventory.STACK;
        assertTrue(TownStores.FOUNDING_PROVISIONS_EACH <= capacity,
                "a settler cannot carry more than a settler can carry");
    }

    @Test
    void theKitArrivesOnOpenGroundBecauseThereIsNowhereYetToPutIt() {
        Settlement s = chartered();

        assertEquals(TownStores.FOUNDING_WOOD, s.loosePile().get(TownStores.WOOD),
                "the timber is stacked on the ground they are standing on");
        assertEquals(TownStores.FOUNDING_STONE, s.loosePile().get(TownStores.STONE));
        assertEquals(TownStores.FOUNDING_WOOD, s.stores().get(TownStores.WOOD),
                "and the town owns it either way");
    }

    @Test
    void thePartyStandsWhereTheCharterWasUsed() {
        Settlement s = chartered();

        assertEquals(SITE, s.centre(), "the town is where the charter was laid down");
        for (Person settler : s.residents()) {
            assertEquals(SITE, settler.position(),
                    "and nobody was left behind at the last one");
        }
    }

    @Test
    void twoFoundingsAreSeparateTowns() {
        // Ids are minted per party. Sharing one would have a second charter
        // quietly rename and relocate the first town rather than found a
        // second, which is the sort of thing that only shows up on the day
        // somebody founds twice.
        Settlement first = chartered();
        Settlement second = chartered();

        assertTrue(!first.id().equals(second.id()), "two charters, two towns");
    }
}
