package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.quest.Quest;
import com.civilization.sim.quest.QuestBoard;
import com.civilization.sim.quest.QuestKind;
import com.civilization.sim.quest.QuestPlanner;
import com.civilization.sim.quest.QuestState;
import com.civilization.sim.quest.Reputation;
import com.civilization.sim.settlement.Alarm;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a town asks for, and what it pays.
 *
 * <p>The rule every one of these circles: <strong>an ask is a reading, not an
 * invention.</strong> A town offering bread is a town whose granary is empty and
 * you can walk in and see that it is; a town offering a walk to the mine is a
 * town whose seam is cut out. If the board could ask for something that was not
 * true of the place, the board would be a menu and the town would be scenery.
 *
 * <p>The other rule, and the one that makes the first testable at all: the same
 * town stepped to the same number posts the same notice. There is no
 * {@code Random} in the planner, so {@code /civ step} reproduces a board exactly
 * — which is the difference between a fault seen once and a fault that can be
 * looked at.
 */
class QuestPlannerTest {

    private static final SimPos HERE = new SimPos(0, 64, 0);

    /** Nobody about and nothing to look at. The ordinary unwatched town. */
    private static final class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    /** One player, standing exactly where the test puts them. */
    private static final class StandingThere implements WorldBridge {
        private final UUID who;
        private SimPos at;

        StandingThere(UUID who, SimPos at) {
            this.who = who;
            this.at = at;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }

        @Override
        public List<UUID> playersWithin(SimPos pos, double radius) {
            return at != null && pos.horizontalDistanceSq(at) <= radius * radius
                    ? List.of(who) : List.of();
        }
    }

    private static SimContext at(long step) {
        return new SimContext(new QuietBridge(), step, SimSettings.SANDBOX);
    }

    /**
     * A town with a roof, a full larder, full sheds and nothing to worry about.
     *
     * <p>Deliberately bare: no buildings, no queue, no threat. Everything these
     * tests measure is added one trouble at a time, so a quest that appears is a
     * quest the trouble caused.
     *
     * <p>The stocked sheds are load-bearing and were the first thing this
     * fixture got wrong. A settlement out of timber is a settlement that wants
     * timber — quite correctly — so a "calm" town holding nothing at all asked
     * for logs and nothing here about hunger or threat could be read.
     */
    private static Settlement calm() {
        return stocked(new Settlement(Settlement.Id.random(), "Testburg", HERE, 64));
    }

    /** A settlement whose id is fixed, so two of them roll the same board. */
    private static Settlement twin(UUID id, String name) {
        return stocked(new Settlement(new Settlement.Id(id), name, HERE, 64));
    }

    private static Settlement stocked(Settlement town) {
        town.addResident(new Person(Person.Id.random(), "Ada", Profession.FARMER, HERE));
        town.setStock(TownStores.FOOD, 400);
        town.setStock(TownStores.WOOD, 400);
        town.setStock(TownStores.STONE, 400);
        town.setFedStreak(999);
        return town;
    }

    private static Building storehouseAt(int x, int z) {
        return new Building("civilization:storehouse", new SimPos(x, 64, z), 1, true);
    }

    // --- generation comes off the town's own troubles ---

    @Test
    void aStarvingTownAsksForBread() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        assertTrue(town.isStarving(), "the fixture has to actually be starving");

        QuestPlanner.advance(town, at(0));

        Quest asked = town.quests().offered().getFirst();
        assertEquals(QuestKind.DELIVER, asked.kind());
        assertEquals(TownStores.FOOD, asked.target(),
                "a starving town asks for food and not for a favor");
        assertTrue(asked.detail().toLowerCase(java.util.Locale.ROOT).contains("storehouse"),
                "and says where to take it");
    }

    /**
     * The weights are not decoration. A starving town with a wall and a wreck
     * still asks for bread first, because forty against twelve is not a close
     * thing — and a town that asked for help with its masonry while its people
     * died would be the board saying the wrong thing at the worst moment.
     */
    @Test
    void hungerOutweighsEverythingElseOnTheBoard() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        town.setThreatLevel(Alarm.ALARMED_AT);

        // Forty towns with fixed ids, not twelve random ones. The roll is a
        // hash of the settlement id, so random ids made this a coin-flip test
        // that failed about one run in eight; fixed ids make the count a fact
        // about the weights, and forty trials at 40 against 12 leave the
        // majority in no doubt.
        int breadFirst = 0;
        int trials = 40;
        for (int run = 0; run < trials; run++) {
            Settlement each = twin(new UUID(0x5EED_F00DL, run), "Hungry " + run);
            each.setStock(TownStores.FOOD, 0);
            each.setFedStreak(0);
            each.setThreatLevel(Alarm.ALARMED_AT);
            QuestPlanner.advance(each, at(0));
            if (each.quests().offered().getFirst().target().equals(TownStores.FOOD)) {
                breadFirst++;
            }
        }
        assertTrue(breadFirst > trials / 2,
                "bread should win most rolls against a threat; won " + breadFirst + " of " + trials);
    }

    @Test
    void aTownUnderThreatAsksForSlaying() {
        Settlement town = calm();
        town.setThreatLevel(Alarm.ALARMED_AT);

        // No buildings at all, so there is nothing to look at and nothing to
        // clear: slaying is the only trouble this town has.
        QuestPlanner.advance(town, at(0));

        Quest asked = town.quests().offered().getFirst();
        assertEquals(QuestKind.SLAY, asked.kind());
        assertTrue(asked.amount() >= 4, "and asks for more than one of them");
        assertTrue(asked.reward().coin() > 0, "and pays for it");
    }

    @Test
    void aTownWithNothingWrongOffersALookOrNothingAtAll() {
        Settlement town = calm();
        town.addBuilding(storehouseAt(20, 0));

        QuestPlanner.advance(town, at(0));

        assertEquals(1, town.quests().size());
        assertEquals(QuestKind.VISIT, town.quests().offered().getFirst().kind(),
                "a town with no troubles has nothing to ask but a favor");
    }

    @Test
    void aTownWithNothingWrongAndNothingStandingAsksForNothing() {
        Settlement town = calm();

        QuestPlanner.advance(town, at(0));

        assertTrue(town.quests().isEmpty(),
                "there is nowhere to send anybody and nothing to carry");
    }

    // --- determinism ---

    @Test
    void theSameTownSteppedTwiceOffersTheSameBoard() {
        UUID id = UUID.randomUUID();
        Settlement first = twin(id, "Testburg");
        Settlement second = twin(id, "Testburg");
        first.setThreatLevel(Alarm.ALARMED_AT);
        second.setThreatLevel(Alarm.ALARMED_AT);
        first.addBuilding(storehouseAt(20, 0));
        second.addBuilding(storehouseAt(20, 0));

        for (long step = 0; step <= QuestPlanner.OFFER_EVERY * 3; step++) {
            QuestPlanner.advance(first, at(step));
            QuestPlanner.advance(second, at(step));
        }

        assertEquals(first.quests().offered(), second.quests().offered(),
                "/civ step has to reproduce a board, or a fault seen once is gone");
    }

    @Test
    void twoDifferentTownsDoNotAskTheSameThingInLockstep() {
        // Not a guarantee about any one step -- two towns with one trouble each
        // will agree -- but over a run the boards have to be able to differ, or
        // the seed is not carrying the settlement at all.
        Settlement one = calm();
        Settlement two = calm();
        for (Settlement town : List.of(one, two)) {
            town.setThreatLevel(Alarm.ALARMED_AT);
            town.setStock(TownStores.FOOD, 0);
            town.addBuilding(storehouseAt(20, 0));
        }

        boolean everDiffered = false;
        for (long step = 0; step <= QuestPlanner.OFFER_EVERY * 8; step++) {
            QuestPlanner.advance(one, at(step));
            QuestPlanner.advance(two, at(step));
            if (!one.quests().offered().equals(two.quests().offered())) {
                everDiffered = true;
            }
        }
        assertTrue(everDiffered, "the roll is not reading the settlement's own id");
    }

    // --- the board is bounded ---

    @Test
    void aTownNeverAsksForMoreThanFiveThingsAtOnce() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        town.setThreatLevel(Alarm.ALARMED_AT);
        town.addBuilding(storehouseAt(20, 0));
        town.addBuilding(storehouseAt(-20, 0));

        for (long step = 0; step <= QuestPlanner.OFFER_EVERY * 40; step += QuestPlanner.OFFER_EVERY) {
            QuestPlanner.advance(town, at(step));
            assertTrue(town.quests().size() <= QuestBoard.BOARD_SIZE,
                    "the board overflowed at step " + step);
        }
    }

    @Test
    void aTownDoesNotAskTwiceForTheSameThing() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);

        for (long step = 0; step <= QuestPlanner.OFFER_EVERY * 6; step += QuestPlanner.OFFER_EVERY) {
            QuestPlanner.advance(town, at(step));
        }

        long breadAsks = town.quests().offered().stream()
                .filter(quest -> TownStores.FOOD.equals(quest.target()))
                .count();
        assertEquals(1, breadAsks, "one notice per want, however long the famine runs");
    }

    // --- expiry ---

    @Test
    void anAskNobodyTakesComesDown() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        assertEquals(1, town.quests().size());

        QuestPlanner.advance(town, at(QuestPlanner.OFFER_LIFETIME));

        assertTrue(town.quests().offered().stream()
                        .noneMatch(quest -> quest.offeredOn() == 0),
                "the first notice should have lapsed");
    }

    @Test
    void takingAJobResetsItsClock() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest offered = town.quests().offered().getFirst();
        UUID player = UUID.randomUUID();

        // Taken on the last step it was up for.
        long late = QuestPlanner.OFFER_LIFETIME - 1;
        assertTrue(QuestPlanner.accept(town, player, offered.id(), late));

        QuestPlanner.advance(town, at(QuestPlanner.OFFER_LIFETIME + 1));

        assertNotNull(town.quests().find(offered.id()),
                "a job in hand must not expire under the player on the way out of town");
    }

    @Test
    void finishedWorkNeverLapses() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest offered = town.quests().offered().getFirst();
        UUID player = UUID.randomUUID();
        QuestPlanner.accept(town, player, offered.id(), 0);
        QuestPlanner.creditDelivery(town, player, offered.target(), offered.amount());
        assertTrue(town.quests().find(offered.id()).isDone());

        QuestPlanner.advance(town, at(QuestPlanner.ACCEPTED_LIFETIME * 4));

        assertNotNull(town.quests().find(offered.id()),
                "a town that took back a reward for work it had already had done "
                        + "would be the worst thing on this board");
    }

    // --- progress ---

    @Test
    void goodsHandedOverCountTowardWhatWasAsked() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        UUID player = UUID.randomUUID();
        QuestPlanner.accept(town, player, ask.id(), 0);

        int counted = QuestPlanner.creditDelivery(town, player, TownStores.FOOD, 3);

        assertEquals(3, counted);
        assertEquals(3, town.quests().find(ask.id()).progress());
        assertFalse(town.quests().find(ask.id()).isDone());
    }

    @Test
    void aDeliveryNobodyAskedYouForCountsForNothing() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        UUID taker = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        QuestPlanner.accept(town, taker, ask.id(), 0);

        assertEquals(0, QuestPlanner.creditDelivery(town, bystander, TownStores.FOOD, 99),
                "somebody else's job is not yours to finish");
        assertEquals(0, town.quests().find(ask.id()).progress());
    }

    @Test
    void enoughHandedOverFinishesTheJob() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        UUID player = UUID.randomUUID();
        QuestPlanner.accept(town, player, ask.id(), 0);

        int counted = QuestPlanner.creditDelivery(town, player, TownStores.FOOD,
                ask.amount() + 500);

        assertEquals(ask.amount(), counted, "the board counts what it asked for and no more");
        assertEquals(QuestState.DONE, town.quests().find(ask.id()).state());
    }

    @Test
    void killsInsideTheClaimCountTowardSlaying() {
        Settlement town = calm();
        town.setThreatLevel(Alarm.ALARMED_AT);
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        assertEquals(QuestKind.SLAY, ask.kind());
        UUID player = UUID.randomUUID();
        QuestPlanner.accept(town, player, ask.id(), 0);

        QuestPlanner.creditKill(town, player, new SimPos(10, 64, 10));
        assertEquals(1, town.quests().find(ask.id()).progress());

        // A mile outside the borders is somebody else's business.
        QuestPlanner.creditKill(town, player, new SimPos(4000, 64, 4000));
        assertEquals(1, town.quests().find(ask.id()).progress(),
                "a kill beyond the claim is not this town's relief");
    }

    @Test
    void standingAtTheMarkFinishesAVisit() {
        Settlement town = calm();
        town.addBuilding(storehouseAt(30, 0));
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        assertEquals(QuestKind.VISIT, ask.kind());
        assertNotNull(ask.place(), "a visit has to be somewhere");

        UUID player = UUID.randomUUID();
        QuestPlanner.accept(town, player, ask.id(), 0);

        // Standing somewhere else entirely.
        StandingThere elsewhere = new StandingThere(player, new SimPos(900, 64, 900));
        QuestPlanner.advance(town, new SimContext(elsewhere, 1, SimSettings.SANDBOX));
        assertFalse(town.quests().find(ask.id()).isDone());

        // And then standing on it.
        StandingThere arrived = new StandingThere(player, ask.place());
        QuestPlanner.advance(town, new SimContext(arrived, 2, SimSettings.SANDBOX));
        assertTrue(town.quests().find(ask.id()).isDone(),
                "you walked to the mark; that was the whole job");
    }

    // --- giving one back ---

    @Test
    void abandoningAJobLosesWhatWasDoneTowardIt() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        UUID player = UUID.randomUUID();
        QuestPlanner.accept(town, player, ask.id(), 0);
        QuestPlanner.creditDelivery(town, player, TownStores.FOOD, 4);

        assertTrue(QuestPlanner.abandon(town, player, ask.id(), 10));

        Quest back = town.quests().find(ask.id());
        assertEquals(QuestState.OFFERED, back.state());
        assertEquals(0, back.progress(),
                "carrying one player's half-finished count to the next is the town paying twice");
        assertNull(back.acceptedBy());
    }

    // --- payment ---

    @Test
    void theTownPaysOutOfItsOwnPurseAndRemembersYouForIt() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        UUID player = UUID.randomUUID();
        QuestPlanner.accept(town, player, ask.id(), 0);
        QuestPlanner.creditDelivery(town, player, ask.target(), ask.amount());
        int before = town.treasury();

        QuestPlanner.Payout paid = QuestPlanner.claim(town, player, ask.id(), 5);

        assertTrue(paid.coin() > 0, "the job was worth something");
        assertEquals(before - paid.coin(), town.treasury(),
                "every coin a town pays out came out of its own books");
        assertTrue(paid.standing() > 0);
        assertEquals(paid.standingNow(), town.standing().of(player));
        assertNull(town.quests().find(ask.id()), "and the notice comes down");
        assertEquals(ask.id(), town.quests().completed().getFirst().id(),
                "into what the town remembers being done for it");
    }

    @Test
    void aBrokeTownPaysWhatItHasAndStillKeepsItsWord() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        UUID player = UUID.randomUUID();
        QuestPlanner.accept(town, player, ask.id(), 0);
        QuestPlanner.creditDelivery(town, player, ask.target(), ask.amount());
        town.setTreasury(1);

        QuestPlanner.Payout paid = QuestPlanner.claim(town, player, ask.id(), 5);

        assertEquals(1, paid.coin(), "it paid what was left");
        assertEquals(0, town.treasury());
        assertEquals(ask.reward().standing(), paid.standing(),
                "regard is the one thing a settlement can always afford");
    }

    @Test
    void unfinishedWorkCollectsNothing() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        UUID player = UUID.randomUUID();
        QuestPlanner.accept(town, player, ask.id(), 0);
        int before = town.treasury();

        assertFalse(QuestPlanner.claim(town, player, ask.id(), 5).any());
        assertEquals(before, town.treasury());
        assertNotNull(town.quests().find(ask.id()), "and the job is still yours to do");
    }

    @Test
    void somebodyElsesFinishedJobIsNotYoursToCollect() {
        Settlement town = calm();
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);
        QuestPlanner.advance(town, at(0));
        Quest ask = town.quests().offered().getFirst();
        UUID worker = UUID.randomUUID();
        UUID thief = UUID.randomUUID();
        QuestPlanner.accept(town, worker, ask.id(), 0);
        QuestPlanner.creditDelivery(town, worker, ask.target(), ask.amount());

        assertFalse(QuestPlanner.claim(town, thief, ask.id(), 5).any());
        assertEquals(0, town.standing().of(thief));
    }

    // --- standing ---

    @Test
    void standingClimbsWithEveryJobAndGatesTheNextOne() {
        Settlement town = calm();
        UUID player = UUID.randomUUID();
        town.standing().add(player, Reputation.SWORN_AT);

        Settlement stranger = calm();
        stranger.setStock(TownStores.FOOD, 0);
        stranger.setFedStreak(0);
        town.setStock(TownStores.FOOD, 0);
        town.setFedStreak(0);

        QuestPlanner.advance(stranger, at(0));
        QuestPlanner.advance(town, at(0));

        assertTrue(town.quests().offered().getFirst().amount()
                        > stranger.quests().offered().getFirst().amount(),
                "a town asks its sworn friends for more than it asks a stranger");
    }

    @Test
    void aTownsRegardIsItsOwnAndNotItsKingdoms() {
        Reputation regard = new Reputation();
        UUID player = UUID.randomUUID();

        assertEquals(0, regard.of(player));
        assertEquals("Stranger", Reputation.wordFor(regard.of(player)));

        regard.add(player, Reputation.TRUSTED_AT);
        assertEquals("Trusted", Reputation.wordFor(regard.of(player)));
        assertEquals(1, Reputation.tierOf(regard.of(player)));
        assertEquals(Reputation.TRUSTED_AT, regard.best());

        regard.add(player, -1000);
        assertEquals(0, regard.of(player), "standing never goes below nothing");
    }
}
