package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.person.Greetings;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.SettlementEvent;
import com.civilization.sim.settlement.SettlementStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a settler says, and whether they mean it twice.
 *
 * <p>The fault this answers was not that the lines were few — it was that the
 * pick was seeded off the <em>body's</em> uuid, and a body is thrown away and
 * rebuilt every time a player walks out of town and back. So the same settler
 * greeted you differently on every visit, which is the one thing that makes a
 * person read as a prop.
 *
 * <p>All of it is arithmetic over a record, a clock and a table, so all of it is
 * here rather than in a world: there is nothing about choosing a line that needs
 * a level, and a feature that could only be checked by standing in front of
 * somebody would not be checked.
 */
class GreetingTest {

    private static final Greetings.Town THORNBURY =
            new Greetings.Town("Thornbury", SettlementStage.TOWN, null);

    /** A settler with nothing going on, which is most settlers most of the time. */
    private static Greetings.Moment passing(Person.Id who, Profession trade) {
        return new Greetings.Moment(who, trade, 0,
                false, false, false, false, false, false, false, false);
    }

    private static Person.Id id(int n) {
        return new Person.Id(new UUID(n * 2654435761L, n * 40503L + 7));
    }

    @Test
    @DisplayName("the same person on the same day says the same thing")
    void theSamePersonOnTheSameDaySaysTheSameThing() {
        Greetings.Moment moment = passing(id(1), Profession.FARMER);
        String first = Greetings.lineFor(moment, THORNBURY, Culture.NORMAN, 12);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, Greetings.lineFor(moment, THORNBURY, Culture.NORMAN, 12),
                    "a settler changed their mind between two right-clicks");
        }

        // The body is not in the seed at all -- which is the whole fix. Two
        // separate Moment objects for the same person are the same person.
        assertEquals(first,
                Greetings.lineFor(passing(id(1), Profession.FARMER), THORNBURY,
                        Culture.NORMAN, 12));
    }

    @Test
    @DisplayName("a settler has something else to say tomorrow")
    void aSettlerHasSomethingElseToSayTomorrow() {
        Greetings.Moment moment = passing(id(3), Profession.MILLER);
        Set<String> overAMonth = new HashSet<>();
        for (long day = 0; day < 30; day++) {
            overAMonth.add(Greetings.lineFor(moment, THORNBURY, Culture.BURGHER, day));
        }
        // Not eleven: the pick is a hash and a month is a small sample, so some
        // days collide. The claim is only that the day is genuinely in the seed.
        assertTrue(overAMonth.size() >= 5,
                "one man said only " + overAMonth.size() + " different things in a month");
    }

    @Test
    @DisplayName("a town of thirty does not speak in unison")
    void aTownOfThirtyDoesNotSpeakInUnison() {
        Set<String> heard = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            heard.add(Greetings.lineFor(passing(id(i), Profession.FARMER),
                    THORNBURY, Culture.NORMAN, 4));
        }
        assertTrue(heard.size() >= 7,
                "thirty lowlanders managed only " + heard.size() + " lines between them: "
                        + heard);
    }

    @Test
    @DisplayName("every people has a voice, and ten things to say with it")
    void everyPeopleHasAVoice() {
        for (Culture culture : Culture.all()) {
            List<String> pool = Greetings.poolFor(culture);
            assertFalse(pool.isEmpty(), culture.id() + " says nothing at all");
            assertTrue(pool.size() >= 10,
                    culture.id() + " has only " + pool.size() + " lines; ten were asked for");
            assertEquals(pool.size(), new HashSet<>(pool).size(),
                    culture.id() + " says the same thing twice in its own pool");
        }

        // The four human peoples are four peoples, not one with four names.
        Set<List<String>> voices = new HashSet<>();
        for (Culture culture : List.of(Culture.NORMAN, Culture.HIGHLAND,
                Culture.BURGHER, Culture.VALE)) {
            voices.add(Greetings.poolFor(culture));
        }
        assertEquals(4, voices.size(), "two human cultures share a pool");

        // And the orcs and the goblins are not the humans.
        assertNotEquals(Greetings.poolFor(Culture.ORC), Greetings.poolFor(Culture.NORMAN));
        assertNotEquals(Greetings.poolFor(Culture.GOBLIN), Greetings.poolFor(Culture.ORC));
    }

    @Test
    @DisplayName("an unnamed town still has a voice")
    void anUnnamedTownStillHasAVoice() {
        assertFalse(Greetings.poolFor(Culture.DEFAULT).isEmpty());
        assertFalse(Greetings.poolFor(null).isEmpty());
        // The sentinel has always been the lowlanders, everywhere else in the mod.
        assertEquals(Greetings.poolFor(Culture.NORMAN), Greetings.poolFor(Culture.DEFAULT));
    }

    @Test
    @DisplayName("the town is called by its own name")
    void theTownIsCalledByItsOwnName() {
        Greetings.Town ashcombe = new Greetings.Town("Ashcombe", SettlementStage.TOWN, null);
        boolean named = false;
        for (int i = 0; i < 200; i++) {
            String line = Greetings.lineFor(passing(id(i), Profession.FARMER),
                    ashcombe, Culture.NORMAN, i);
            assertFalse(line.contains("{town}"), "a token got through unfilled: " + line);
            assertFalse(line.contains("{place}"), "a token got through unfilled: " + line);
            named |= line.contains("Ashcombe");
        }
        assertTrue(named, "two hundred greetings and nobody mentioned Ashcombe");

        // And it is the town's own name, not a name baked into the line.
        Greetings.Town other = new Greetings.Town("Netherby", SettlementStage.TOWN, null);
        for (int i = 0; i < 200; i++) {
            String line = Greetings.lineFor(passing(id(i), Profession.FARMER),
                    other, Culture.NORMAN, i);
            assertFalse(line.contains("Ashcombe"), "the wrong town's name: " + line);
        }
    }

    @Test
    @DisplayName("every situation fires on its own condition and outranks the pool")
    void everySituationFiresAndOutranksThePool() {
        Person.Id who = id(9);
        List<String> pool = new ArrayList<>(Greetings.poolFor(Culture.NORMAN));

        for (Greetings.Situation situation : Greetings.Situation.values()) {
            if (situation == Greetings.Situation.PASSING) {
                continue;
            }
            Greetings.Moment moment = momentFor(who, situation);
            assertEquals(situation, Greetings.situationOf(moment),
                    situation + " did not fire on its own condition");
            String line = Greetings.lineFor(moment, THORNBURY, Culture.NORMAN, 2);
            assertFalse(pool.contains(line),
                    situation + " fell through to the idle pool and said: " + line);
        }

        // Nothing going on is the answer the rest of the time.
        assertEquals(Greetings.Situation.PASSING,
                Greetings.situationOf(passing(who, Profession.FARMER)));
    }

    @Test
    @DisplayName("the given lines are the given lines")
    void theGivenLinesAreTheGivenLines() {
        // Two were named in the brief and are the ones a player will quote back.
        assertTrue(anyLineOf(Greetings.Situation.HUNGRY).contains("Have you any bread?"),
                "a hungry settler no longer asks for bread");
        assertTrue(anyLineOf(Greetings.Situation.ALARM).contains("Get indoors!"),
                "the alarm no longer tells anybody to get indoors");
    }

    /** Every line a situation can produce, gathered over many people and days. */
    private static Set<String> anyLineOf(Greetings.Situation situation) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(Greetings.lineFor(momentFor(id(i), situation), THORNBURY,
                    Culture.NORMAN, i));
        }
        return seen;
    }

    @Test
    @DisplayName("hunger and the alarm outrank every trade")
    void hungerAndTheAlarmOutrankEveryTrade() {
        // A hungry king is a hungry man, and an alarmed one is not talking about
        // his crown. This is the priority order Situation's own declaration
        // promises, checked where it actually matters.
        Greetings.Moment hungryKing = new Greetings.Moment(id(11), Profession.KING,
                Person.HUNGER_HUNGRY, true, false, false, false, false, false, false, false);
        assertEquals(Greetings.Situation.HUNGRY, Greetings.situationOf(hungryKing));

        Greetings.Moment alarmedHungryGuard = new Greetings.Moment(id(12), Profession.GUARD,
                Person.HUNGER_SEVERE, true, false, false, false, false, true, false, false);
        assertEquals(Greetings.Situation.ALARM, Greetings.situationOf(alarmedHungryGuard));

        // And one below the line: a settler at exactly one short of hungry is not.
        Greetings.Moment nearlyHungry = new Greetings.Moment(id(13), Profession.FARMER,
                Person.HUNGER_HUNGRY - 1, false, false, false, false, false, false,
                false, false);
        assertEquals(Greetings.Situation.PASSING, Greetings.situationOf(nearlyHungry));
    }

    @Test
    @DisplayName("a guard off duty is not on post, and a builder with a load is not waiting")
    void theTradeSituationsWantTheirOwnConditions() {
        // Being a guard is not enough; the routine's "at work" is.
        Greetings.Moment restingGuard = new Greetings.Moment(id(21), Profession.GUARD, 0,
                false, true, false, true, false, false, false, false);
        assertNotEquals(Greetings.Situation.ON_POST, Greetings.situationOf(restingGuard));

        // A builder carrying stone is building, not waiting for it.
        Greetings.Moment loadedBuilder = new Greetings.Moment(id(22), Profession.BUILDER, 0,
                true, false, false, false, false, false, false, false);
        assertEquals(Greetings.Situation.PASSING, Greetings.situationOf(loadedBuilder));

        // Ripe fields say nothing to the miller standing next to them.
        Greetings.Moment miller = new Greetings.Moment(id(23), Profession.MILLER, 0,
                true, false, false, false, false, false, false, true);
        assertEquals(Greetings.Situation.PASSING, Greetings.situationOf(miller));
    }

    @Test
    @DisplayName("a young settlement and a settlement with news each get a line for it")
    void theSettlementItselfGetsALineIn() {
        Greetings.Town camp = new Greetings.Town("Stonefold", SettlementStage.CAMP, null);
        assertTrue(anyLine(camp, Culture.NORMAN).stream()
                        .anyMatch(line -> line.contains("barely a camp")),
                "a five-hut camp talks about itself as though it were a town");

        Greetings.Town rumoured = Greetings.Town.of("Stonefold", SettlementStage.TOWN,
                List.of(new SettlementEvent(1, "an early frost"),
                        new SettlementEvent(9, "raiders driven off")));
        assertEquals("raiders driven off", rumoured.lastEvent(),
                "the town gossips about the wrong thing; it should be the latest");
        assertTrue(anyLine(rumoured, Culture.NORMAN).stream()
                        .anyMatch(line -> line.contains("raiders driven off")),
                "nobody in town mentioned the raid");

        // A settled town with nothing to report keeps to its own pool and says
        // neither of the two extra lines. Counted rather than compared, because
        // what comes back has its tokens filled in and the pool still has them.
        Set<String> settled = anyLine(THORNBURY, Culture.NORMAN);
        assertEquals(Greetings.poolFor(Culture.NORMAN).size(), settled.size(),
                "a grown, quiet town said something that is not in its pool: " + settled);
        assertTrue(settled.stream().noneMatch(line -> line.contains("barely a")),
                "a town of eighty buildings called itself new");
        assertTrue(settled.stream().noneMatch(line -> line.startsWith("You'll have heard")),
                "a town with no history gossiped anyway");
    }

    /** Every passing line this town can produce, over enough people to see them all. */
    private static Set<String> anyLine(Greetings.Town town, Culture culture) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 600; i++) {
            seen.add(Greetings.lineFor(passing(id(i), Profession.FARMER), town, culture, i));
        }
        return seen;
    }

    @Test
    @DisplayName("a camp is a camp and a town is a town, whoever is speaking")
    void aCampIsACampAndATownIsATown() {
        // The one line that names what the place is, said by a king, because he
        // is the only one who mentions it. A goblin's is a camp forever;
        // anybody's is a camp until it is a village.
        Greetings.Moment king = momentFor(id(31), Greetings.Situation.KING);
        Greetings.Town young = new Greetings.Town("Stonefold", SettlementStage.HOMESTEAD, null);
        Greetings.Town grown = new Greetings.Town("Stonefold", SettlementStage.TOWN, null);

        assertTrue(everyLine(king, young, Culture.NORMAN).stream()
                        .noneMatch(line -> line.contains(" town")),
                "a homestead of eight huts called itself a town");
        assertTrue(everyLine(king, grown, Culture.GOBLIN).stream()
                        .noneMatch(line -> line.contains(" town")),
                "a goblin called his hole a town");
        assertTrue(everyLine(king, grown, Culture.NORMAN).stream()
                        .anyMatch(line -> line.contains(" town")),
                "a grown human settlement is a town and its king should say so");
    }

    /** One settler's line across many days, which is every line they can say. */
    private static Set<String> everyLine(Greetings.Moment moment, Greetings.Town town,
                                         Culture culture) {
        Set<String> seen = new HashSet<>();
        for (long day = 0; day < 60; day++) {
            seen.add(Greetings.lineFor(moment, town, culture, day));
        }
        return seen;
    }

    /** A settler arranged so that exactly one situation holds. */
    private static Greetings.Moment momentFor(Person.Id who, Greetings.Situation situation) {
        return switch (situation) {
            case ALARM -> new Greetings.Moment(who, Profession.FARMER, 0,
                    false, false, false, false, false, true, false, false);
            case HUNGRY -> new Greetings.Moment(who, Profession.FARMER,
                    Person.HUNGER_HUNGRY, false, false, false, false, false, false,
                    false, false);
            case KING -> new Greetings.Moment(who, Profession.KING, 0,
                    false, false, false, false, false, false, false, false);
            case WAITING_ON_MATERIALS -> new Greetings.Moment(who, Profession.BUILDER, 0,
                    true, false, false, false, false, false, true, false);
            case HARVEST -> new Greetings.Moment(who, Profession.FARMER, 0,
                    true, false, false, false, false, false, false, true);
            case ON_POST -> new Greetings.Moment(who, Profession.GUARD, 0,
                    true, false, false, false, false, false, false, false);
            case INN -> new Greetings.Moment(who, Profession.MILLER, 0,
                    false, true, true, true, false, false, false, false);
            case CURFEW -> new Greetings.Moment(who, Profession.MILLER, 0,
                    false, false, false, true, true, false, false, false);
            case PASSING -> passing(who, Profession.FARMER);
        };
    }
}
