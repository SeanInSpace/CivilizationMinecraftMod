package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Garrison;
import com.civilization.sim.settlement.HaulPlanner;
import com.civilization.sim.settlement.Homes;
import com.civilization.sim.settlement.JobPlanner;
import com.civilization.sim.settlement.KingPlanner;
import com.civilization.sim.settlement.RaidPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who a warband follows, and the seven things that follow from it.
 *
 * <p>The rules are on {@link KingPlanner} and each of them is here, because
 * every one of them fails quietly: a second king is a settler who stops working
 * for no visible reason, a missing garrison bonus is a raid that goes the wrong
 * way, and a human village that crowns somebody is a bug nobody would think to
 * look for.
 */
class KingTest {

    private static final SimPos CENTER = new SimPos(0, 64, 0);

    private static Settlement camp(String cultureId) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Karrgurd", CENTER, 128);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.VILLAGE);
        town.setCultureId(cultureId);
        return town;
    }

    private static Settlement warband() {
        return camp(Culture.ORC.id());
    }

    private static Person join(Settlement town, String name, Profession trade) {
        Person person = new Person(Person.Id.random(), name, trade, CENTER);
        town.addResident(person);
        return person;
    }

    /** The chief's roof, standing. */
    private static void raiseTheGreatHut(Settlement town) {
        town.addBuilding(new Building(KingPlanner.SEAT, CENTER, 1, true));
    }

    private static void step(Settlement town, long at) {
        KingPlanner.advance(town, new SimContext(new TerrainFake(8675309L), at, SimSettings.SANDBOX));
    }

    // --- when a king appears -------------------------------------------------

    @Test
    void nobodyIsCrownedUntilTheGreatHutStands() {
        // A king with nowhere to sit is a label on a settler. The whole of what
        // makes this readable from the ground is that there is a building in the
        // middle of the camp with him in it.
        Settlement town = warband();
        join(town, "Brak", Profession.GUARD);
        join(town, "Durg", Profession.FARMER);

        step(town, 1);
        assertNull(KingPlanner.king(town), "a camp with no great hut crowned somebody");

        raiseTheGreatHut(town);
        step(town, 2);
        assertNotNull(KingPlanner.king(town), "the great hut stands and nobody took it");
    }

    @Test
    void theOldestGuardTakesItAndTheOldestResidentIfThereIsNoGuard() {
        // "Oldest" is longest-resident: nobody in this simulation has an age,
        // and the order people joined a town is the one thing about seniority it
        // actually knows. See KingPlanner.oldest.
        Settlement withGuards = warband();
        join(withGuards, "Uzga", Profession.FARMER);
        Person firstGuard = join(withGuards, "Brak", Profession.GUARD);
        join(withGuards, "Thok", Profession.GUARD);
        raiseTheGreatHut(withGuards);
        step(withGuards, 1);
        assertSame(firstGuard, KingPlanner.king(withGuards),
                "the warband followed a younger guard over the one who has carried"
                        + " a weapon here longest");

        Settlement unarmed = warband();
        Person eldest = join(unarmed, "Morg", Profession.FARMER);
        join(unarmed, "Ghal", Profession.BUILDER);
        raiseTheGreatHut(unarmed);
        step(unarmed, 1);
        assertSame(eldest, KingPlanner.king(unarmed),
                "with nobody under arms the warband follows whoever has been here longest");
    }

    @Test
    void thereIsExactlyOneHoweverManyTurnUp() {
        // Nothing in the ordinary flow makes two. A save, a migration or a
        // command could, and every one of those is quiet, so the rule is
        // enforced every step rather than assumed once.
        Settlement town = warband();
        raiseTheGreatHut(town);
        join(town, "Brak", Profession.KING);
        join(town, "Durg", Profession.KING);
        join(town, "Ghal", Profession.KING);

        step(town, 1);
        assertEquals(1, JobPlanner.count(town, Profession.KING),
                "three settlers were wearing the same crown");
        assertEquals("Brak", KingPlanner.king(town).name(),
                "the extras should be demoted, not the first one");
    }

    @Test
    void aHumanTownNeverCrownsAnybody() {
        // A shire has a hall and a population. Every human culture there is,
        // because "humans do not do this" is the claim and one of them is not
        // evidence for it.
        for (Culture people : Culture.humans()) {
            Settlement town = camp(people.id());
            raiseTheGreatHut(town);
            join(town, "Ada", Profession.GUARD);
            join(town, "Bren", Profession.FARMER);
            for (int at = 1; at <= 200; at++) {
                step(town, at);
            }
            assertNull(KingPlanner.king(town),
                    people.id() + " crowned a king, which is a warband's idea");
        }
    }

    @Test
    void aTownReBadgedAwayFromTheOrcsPutsItsCrownDown() {
        // /civ culture can do this to a living town, and a settler wearing a
        // title his people do not have is a settler doing no work for a reason
        // nobody can see.
        Settlement town = warband();
        raiseTheGreatHut(town);
        join(town, "Brak", Profession.GUARD);
        step(town, 1);
        assertNotNull(KingPlanner.king(town));

        town.setCultureId(Culture.NORMAN.id());
        step(town, 2);
        assertNull(KingPlanner.king(town), "the crown outlived the people who made it");
    }

    // --- what he is worth ----------------------------------------------------

    @Test
    void aLivingKingIsWorthTwoGuardsToTheWatchAndToTheRaidAlike() {
        // The two numbers are the one number. Garrison is what a town recruits
        // by and RaidPlanner is what it fights by, and a bonus in only one of
        // them is a town that feels safe and dies anyway.
        Settlement town = warband();
        join(town, "Brak", Profession.GUARD);
        join(town, "Durg", Profession.GUARD);

        int guardsAlone = Garrison.guardStrength(town);
        int defenseAlone = RaidPlanner.defensePower(town);

        raiseTheGreatHut(town);
        step(town, 1);
        assertNotNull(KingPlanner.king(town));

        // One of the two guards took the crown, so the head count went down by
        // one and the bonus went up by two: net plus one, which is exactly the
        // claim -- a king is worth more in the line than the guard he was.
        assertEquals(guardsAlone - 1 + KingPlanner.KING_GUARD_BONUS,
                Garrison.guardStrength(town),
                "the crown is not counted where the town recruits");
        assertEquals(defenseAlone
                        + (KingPlanner.KING_GUARD_BONUS - 1) * RaidPlanner.GUARD_POWER,
                RaidPlanner.defensePower(town),
                "the crown is not counted where the town fights");
    }

    @Test
    void theBonusGoesWithHim() {
        Settlement town = warband();
        join(town, "Brak", Profession.GUARD);
        raiseTheGreatHut(town);
        step(town, 1);
        Person crowned = KingPlanner.king(town);
        assertNotNull(crowned);
        int withHim = Garrison.guardStrength(town);

        town.removePerson(crowned.id());
        assertEquals(withHim - KingPlanner.KING_GUARD_BONUS,
                Garrison.guardStrength(town),
                "a dead king was still stiffening the line");
    }

    // --- and that he does nothing --------------------------------------------

    @Test
    void aKingIsNeverPutToWork() {
        // He is absent from DEFAULT_NEEDS, so nothing wants one and nothing
        // retrains one away. The lanes that reach PAST the table are the ones
        // worth checking: the starving town's, which takes whichever trade has
        // the most heads in it, and the courier, which takes whoever has no work
        // in front of them -- and a king has none by definition.
        Settlement town = warband();
        raiseTheGreatHut(town);
        Person king = join(town, "Brak", Profession.KING);
        join(town, "Durg", Profession.BUILDER);
        town.setFoodStock(0);
        assertTrue(town.isStarving(), "the fixture stopped being a famine");

        for (int at = 1; at <= 20; at++) {
            JobPlanner.retrainOne(town);
            step(town, at);
        }
        assertSame(Profession.KING, king.profession(),
                "a starving camp put its king in the fields");
        Person courier = HaulPlanner.courierFor(town);
        assertFalse(courier != null && courier.id().equals(king.id()),
                "the king was sent to carry a sack to the storehouse");
    }

    // --- and that the succession runs ----------------------------------------

    @Test
    void theCampMournsAndThenCrownsAnother() {
        Settlement town = warband();
        raiseTheGreatHut(town);
        Person first = join(town, "Brak", Profession.GUARD);
        join(town, "Durg", Profession.GUARD);
        step(town, 1);
        assertSame(first, KingPlanner.king(town));

        town.removePerson(first.id());
        step(town, 2);
        assertNull(KingPlanner.king(town), "the succession was instant, so nobody mourned");
        assertTrue(town.events().stream()
                        .anyMatch(event -> event.message().contains("king is dead")),
                "the camp lost its king and said nothing about it");

        // Still nobody, all the way to the end of the mourning.
        for (int at = 3; at < 2 + KingPlanner.MOURNING_STEPS; at++) {
            step(town, at);
            assertNull(KingPlanner.king(town),
                    "somebody took the great hut at step " + at + ", mid-mourning");
        }

        step(town, 2 + KingPlanner.MOURNING_STEPS);
        assertNotNull(KingPlanner.king(town),
                "the mourning ran out and the warband still follows nobody");
        assertEquals("Durg", KingPlanner.king(town).name());
    }

    // --- and where the camp musters ------------------------------------------

    @Test
    void theRallyPointIsTheGreatHutAndOtherwiseTheMiddleOfTown() {
        Settlement town = warband();
        assertEquals(CENTER, KingPlanner.rallyPoint(town),
                "a camp with no great hut still has a middle to run to");

        SimPos seat = new SimPos(20, 64, -8);
        town.addBuilding(new Building(KingPlanner.SEAT, seat, 1, true));
        assertEquals(seat, KingPlanner.rallyPoint(town),
                "the warband musters at a point on the map rather than at its"
                        + " chief's door");
    }

    // --- and that the camp builds the hut at all ------------------------------

    @Test
    void theWarbandBuildsHutsAndTheGreatHutAndNobodyElseDoes() {
        // The other half of the king: a rule about a building nobody raises is a
        // rule that never runs. Asserted through Homes, which is the one table
        // that decides whose house is whose.
        assertTrue(Homes.buildableBy(Culture.ORC.id(), "civilization:hut"));
        assertTrue(Homes.buildableBy(Culture.ORC.id(), KingPlanner.SEAT));
        assertFalse(Homes.buildableBy(Culture.ORC.id(), "civilization:cottage"),
                "a war camp wants cottages");
        for (Culture people : Culture.humans()) {
            assertFalse(Homes.buildableBy(people.id(), "civilization:hut"),
                    people.id() + " builds orc huts");
            assertFalse(Homes.buildableBy(people.id(), KingPlanner.SEAT),
                    people.id() + " builds a great hut, and would have nobody to put in it");
            assertTrue(Homes.buildableBy(people.id(), "civilization:cottage"));
        }
        assertEquals("civilization:hut", Homes.instead(Culture.ORC.id(), "civilization:cottage"),
                "the stage program's cottage does not become a hut for the orcs");
        assertEquals("civilization:granary",
                Homes.instead(Culture.ORC.id(), "civilization:granary"),
                "a granary is a granary");
    }
}
