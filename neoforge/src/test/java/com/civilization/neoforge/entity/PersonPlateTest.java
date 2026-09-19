package com.civilization.neoforge.entity;

import com.civilization.neoforge.net.PersonInventoryPayload;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A settler's plate says the trade the settler has, not the one they had.
 *
 * <p><strong>The report.</strong> A person panel whose title read {@code Farmer 6
 * — Farmer} over a body line reading {@code Carpenter · hunger 58/99}. The
 * greetings showed the same drift — {@code Farmer 8 — Guard}, {@code Farmer 6 —
 * Miller}. Both lines were about the same settler, read off the same screen, one
 * step apart, and they disagreed.
 *
 * <p>The cause was two vintages of one fact. The plate was written once, at
 * {@code PersonEntityManager.embody}, and nothing ever rewrote it; the panel's
 * title read the plate and its subtitle read the simulation. Jobs are reassigned
 * constantly — {@code JobPlanner.retrainOne} moves somebody most steps a town is
 * short-handed, and one playtest run moved guards 7 → 10 → 12 → 14 inside four
 * minutes — so the plate a player reads was routinely a trade the settler no
 * longer had.
 *
 * <p>These are the pure halves of the rule. The wiring that calls them — the
 * routine's per-pass refresh and {@code mobInteract}'s title — needs a level and
 * is not reachable here; what is reachable, and what the fault actually was, is
 * that the string is derived from the person rather than stored.
 */
class PersonPlateTest {

    private static Person settler(Profession trade) {
        return new Person(Person.Id.random(), "Farmer 6", trade, new SimPos(0, 64, 0));
    }

    @Test
    void thePlateFollowsTheTradeWhenTheTownRetrainsSomebody() {
        Person person = settler(Profession.FARMER);
        String atEmbody = PersonEntity.plateFor(person);
        assertEquals("Farmer 6 — Farmer", atEmbody,
                "the plate is the settler's name and their trade, in that order");

        // The town needs a carpenter. This is the one line of JobPlanner that
        // matters here; everything else about the retraining is the simulation's.
        person.setProfession(Profession.CARPENTER);

        assertNotEquals(atEmbody, PersonEntity.plateFor(person),
                "the plate still says Farmer after the town made them a carpenter,"
                        + " which is the report word for word");
        assertEquals("Farmer 6 — Carpenter", PersonEntity.plateFor(person));
    }

    /**
     * A settler's <em>name</em> is theirs and is not the trade.
     *
     * <p>Worth pinning because the report's example makes it easy to conflate the
     * two: the settler was called "Farmer 6" — {@code /civ populate}'s doing —
     * and happened to also be a farmer. Renaming somebody must not follow the
     * trade and retraining must not follow the name.
     */
    @Test
    void theNameIsNotTheTrade() {
        Person person = settler(Profession.GUARD);
        assertEquals("Farmer 6 — Guard", PersonEntity.plateFor(person),
                "a settler called Farmer 6 who is a guard reads as a guard");
    }

    /**
     * Every trade reads as a word rather than a constant.
     *
     * <p>The enum grows, and a plate that fell back on {@code MASTER_BUILDER}
     * would be caught here rather than by somebody reading it in-game.
     */
    @Test
    void everyTradeHasAPlateAPlayerCanRead() {
        for (Profession trade : Profession.values()) {
            String plate = PersonEntity.plateFor(settler(trade));
            String word = plate.substring(plate.indexOf(" — ") + 3);
            assertTrue(Character.isUpperCase(word.charAt(0)),
                    trade + " reads as " + word);
            assertEquals(trade.name().toLowerCase(Locale.ROOT).substring(1),
                    word.substring(1),
                    trade + " lost something between the enum and the plate");
        }
    }

    /**
     * The title and the body of the person panel cannot disagree.
     *
     * <p>The panel's title is the payload's name and its subtitle is the
     * payload's profession, and this is the screenshot: build the payload the way
     * the entity builds it, after a retrain, and the two have to name the same
     * trade.
     */
    @Test
    void thePanelsTitleAndItsBodyNameTheSameTrade() {
        Person person = settler(Profession.FARMER);
        person.setHunger(58);
        person.setProfession(Profession.CARPENTER);

        PersonInventoryPayload panel =
                PersonInventoryPayload.of(PersonEntity.plateFor(person), person);

        String titleTrade = panel.name().substring(panel.name().indexOf(" — ") + 3);
        String bodyTrade = panel.profession();
        assertEquals(bodyTrade.toLowerCase(Locale.ROOT), titleTrade.toLowerCase(Locale.ROOT),
                "the panel says " + titleTrade + " in its title and " + bodyTrade
                        + " in its body, which is what the playtest photographed");
    }
}
