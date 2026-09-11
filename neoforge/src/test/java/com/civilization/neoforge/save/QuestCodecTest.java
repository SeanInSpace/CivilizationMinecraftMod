package com.civilization.neoforge.save;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.quest.Quest;
import com.civilization.sim.quest.QuestKind;
import com.civilization.sim.quest.QuestState;
import com.civilization.sim.quest.Reputation;
import com.civilization.sim.quest.Reward;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A board survives the game closing, and so does what the town thinks of you.
 *
 * <p>Both halves matter and they fail differently. A lost board is a player who
 * walks back to a town after lunch and finds the job they were halfway through
 * was never asked for — annoying, and recoverable. A lost standing is every
 * errand anyone ever ran for that settlement undone at once, silently, with no
 * way to tell it happened. The second is why standing is in the file at all
 * rather than a number held in a running server.
 *
 * <p>The state and the owner are the two fields whose loss is worst, because
 * neither shows: a reloaded quest that has forgotten it was finished is an
 * offer, and a reloaded quest that has forgotten whose it was is a reward
 * anybody can walk up and take.
 */
class QuestCodecTest {

    private static final SimPos HERE = new SimPos(0, 64, 0);
    private static final SimPos WRECK = new SimPos(-37, 71, 84);
    private static final UUID WORKER = UUID.fromString("2f1b3a7c-0000-4000-8000-00000000abcd");

    private static Settlement townWithABoard() {
        Settlement town = new Settlement(Settlement.Id.random(), "Aldenholt", HERE, 128);

        // An offer nobody has taken: no owner, no progress.
        town.quests().post(Quest.offered("q40D", QuestKind.DELIVER,
                "Timber for the yard", "Every job on the books is waiting on logs.",
                TownStores.WOOD, null, 32, Reward.of(96, 2), 40, 640));

        // A job in hand, part done, at a place — every field loaded at once.
        Quest clearing = Quest.offered("q80C", QuestKind.CLEAR, "Take the place back",
                "Something has moved into the wreck.", "wreck", WRECK, 5,
                new Reward(40, TownStores.STONE, 16, 4), 80, 680)
                .takenBy(WORKER, 1_280)
                .withProgress(3);
        town.quests().post(clearing);

        // Finished and waiting to be paid for.
        town.quests().post(Quest.offered("q90V", QuestKind.VISIT, "The mine is cut out",
                        "The seam is finished and nobody wants to say so.", "mine",
                        new SimPos(12, 40, -9), 1, Reward.of(8, 1), 90, 690)
                .takenBy(WORKER, 1_290)
                .withProgress(1));

        town.quests().recordDone(Quest.offered("q10S", QuestKind.SLAY, "Thin them out",
                "There is too much abroad after dark.", "hostiles", null, 4,
                Reward.of(24, 3), 10, 610));

        town.standing().add(WORKER, 37);
        return town;
    }

    @Test
    void aBoardComesBackAsItWasPinnedUp() {
        Settlement back = decode(encode(townWithABoard()));

        assertEquals(3, back.quests().size());
        Quest timber = back.quests().find("q40D");
        assertNotNull(timber);
        assertEquals(QuestKind.DELIVER, timber.kind());
        assertEquals(TownStores.WOOD, timber.target());
        assertEquals(32, timber.amount());
        assertEquals(96, timber.reward().coin());
        assertEquals(2, timber.reward().standing());
        assertEquals(40, timber.offeredOn());
        assertEquals(640, timber.expiresOn());
        assertEquals(QuestState.OFFERED, timber.state());
        assertNull(timber.acceptedBy(), "nobody had taken it");
        assertNull(timber.place(), "a delivery is not anywhere in particular");
    }

    @Test
    void aJobInHandRemembersWhoseItIsAndHowFarTheyGot() {
        Quest clearing = decode(encode(townWithABoard())).quests().find("q80C");

        assertEquals(QuestState.ACCEPTED, clearing.state());
        assertEquals(WORKER, clearing.acceptedBy(),
                "a quest that forgot whose it was is a reward anybody can take");
        assertEquals(3, clearing.progress());
        assertEquals(WRECK, clearing.place(), "and where the trouble is");
        assertEquals(1_280, clearing.expiresOn(),
                "accepting moved the deadline, and the move has to survive");
        assertEquals(TownStores.STONE, clearing.reward().goods());
        assertEquals(16, clearing.reward().goodsAmount());
    }

    @Test
    void finishedWorkComesBackFinished() {
        Quest look = decode(encode(townWithABoard())).quests().find("q90V");

        assertTrue(look.isDone(),
                "a reloaded quest that forgot it was finished is an offer, and the "
                        + "player has done the work twice");
    }

    @Test
    void theTownRemembersWhatWasDoneForIt() {
        Settlement back = decode(encode(townWithABoard()));

        assertEquals(1, back.quests().completed().size());
        assertEquals("Thin them out", back.quests().completed().getFirst().title());
    }

    @Test
    void standingSurvivesTheWorldClosing() {
        Settlement back = decode(encode(townWithABoard()));

        assertEquals(37, back.standing().of(WORKER));
        assertEquals("Trusted", Reputation.wordFor(back.standing().of(WORKER)));
        assertEquals(0, back.standing().of(UUID.randomUUID()),
                "and nobody else gets the credit for it");
    }

    @Test
    void theWordsTravelWithTheNoticeRatherThanBeingWorkedOutAgain() {
        // The reason this is stored at all: a town asks for grain BECAUSE it is
        // starving, and by the time the save is read it may have eaten. A
        // description re-derived on load would quietly stop describing the ask.
        Quest timber = decode(encode(townWithABoard())).quests().find("q40D");

        assertEquals("Timber for the yard", timber.title());
        assertEquals("Every job on the books is waiting on logs.", timber.detail());
    }

    @Test
    void aTownThatHasNeverBeenAskedAnythingStillSaves() {
        Settlement quiet = new Settlement(
                Settlement.Id.random(), "Quietburg", HERE, 64);

        Settlement back = decode(encode(quiet));

        assertTrue(back.quests().isEmpty());
        assertTrue(back.quests().completed().isEmpty());
        assertTrue(back.standing().isEmpty());
    }

    /**
     * The board is its own group in the file rather than four more fields under
     * the holdings.
     *
     * <p>Standing is not a holding: it is not spent, it cannot run out, and it
     * belongs to a person rather than to the town. Filing it with the timber
     * would be the save format telling the next reader something false.
     */
    @Test
    void theBoardIsItsOwnGroupInTheFile() {
        JsonObject written = encode(townWithABoard()).getAsJsonObject();

        assertTrue(written.has("quests"), "the board has a group of its own");
        JsonObject quests = written.getAsJsonObject("quests");
        assertTrue(quests.has("offered") && quests.has("standing"));
        assertFalse(written.getAsJsonObject("holdings").has("standing"),
                "standing is not something a town holds");
    }

    private static JsonElement encode(Settlement town) {
        return CivilizationCodecs.SETTLEMENT.encodeStart(JsonOps.INSTANCE, town)
                .result().orElseThrow();
    }

    private static Settlement decode(JsonElement written) {
        return CivilizationCodecs.SETTLEMENT.parse(JsonOps.INSTANCE, written)
                .result().orElseThrow();
    }
}
