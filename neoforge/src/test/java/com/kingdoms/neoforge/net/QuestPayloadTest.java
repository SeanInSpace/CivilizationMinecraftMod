package com.kingdoms.neoforge.net;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.quest.Quest;
import com.kingdoms.sim.quest.QuestBoard;
import com.kingdoms.sim.quest.QuestKind;
import com.kingdoms.sim.quest.QuestState;
import com.kingdoms.sim.quest.Reward;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The board's wire, both ways.
 *
 * <p>A stream codec is a pair of functions nothing else checks, and this one has
 * the exact shape that transposes silently: a row carries four numbers and two
 * short words, and {@code StreamCodec.composite} takes the canonical constructor
 * last, so every getter has to sit exactly where its component does. A progress
 * where an amount belongs still compiles, still sends, and tells a player they
 * have delivered thirty-two of three.
 *
 * <p>None of it needs a client.
 */
class QuestPayloadTest {

    private static final BlockPos POST = new BlockPos(-1234, 71, 5678);
    private static final UUID READER = UUID.fromString("11111111-2222-4333-8444-555555555555");

    /**
     * Registries are irrelevant to these payloads — strings, varints, a boolean
     * and a packed position — so the empty access is honest rather than a
     * shortcut. The connection type has to be named: a payload only this mod
     * sends is only ever read by a NeoForge client.
     */
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static QuestBoardPayload roundTrip(QuestBoardPayload sent) {
        RegistryFriendlyByteBuf buf = buffer();
        QuestBoardPayload.STREAM_CODEC.encode(buf, sent);
        QuestBoardPayload read = QuestBoardPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(),
                "the codec read back fewer bytes than it wrote");
        return read;
    }

    private static QuestActionPayload roundTrip(QuestActionPayload sent) {
        RegistryFriendlyByteBuf buf = buffer();
        QuestActionPayload.STREAM_CODEC.encode(buf, sent);
        QuestActionPayload read = QuestActionPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(),
                "the codec read back fewer bytes than it wrote");
        return read;
    }

    @Test
    void aFullBoardSurvivesTheWire() {
        // Every number deliberately different. With the same figure in two
        // adjacent slots a transposition round-trips clean and is still wrong.
        List<QuestBoardPayload.Row> rows = List.of(
                new QuestBoardPayload.Row("q40D", QuestKind.DELIVER.name(),
                        "Timber for the yard", "Every job is waiting on logs.",
                        TownStores.WOOD, 32, 7,
                        new QuestBoardPayload.Pay(96, "", 0, 2), 411,
                        QuestState.ACCEPTED.name(), true),
                new QuestBoardPayload.Row("q80C", QuestKind.CLEAR.name(),
                        "Take the place back", "Something has moved into the wreck.",
                        "wreck", 5, 3,
                        new QuestBoardPayload.Pay(40, TownStores.STONE, 16, 4), 158,
                        QuestState.OFFERED.name(), false));

        QuestBoardPayload sent = new QuestBoardPayload("Aldenholt", POST, 37, 1873,
                true, rows, List.of("Thin them out", "Bread for the winter"));
        QuestBoardPayload read = roundTrip(sent);

        assertEquals("Aldenholt", read.town());
        assertEquals(POST, read.post());
        assertEquals(37, read.standing());
        assertEquals(1873, read.treasury());
        assertTrue(read.opening());
        assertEquals(rows, read.rows());
        assertEquals(List.of("Thin them out", "Bread for the winter"), read.done());
        assertEquals(sent, read);
    }

    @Test
    void anEmptyBoardSurvivesTheWire() {
        // A town asking for nothing still opens its board, and the screen says
        // so. A refresh has to stay a refresh, or a closed board reopens itself.
        QuestBoardPayload sent = new QuestBoardPayload("Quietburg", BlockPos.ZERO, 0, 0,
                false, List.of(), List.of());

        QuestBoardPayload read = roundTrip(sent);

        assertTrue(read.rows().isEmpty());
        assertTrue(read.done().isEmpty());
        assertFalse(read.opening());
        assertEquals(sent, read);
    }

    @Test
    void everyButtonSurvivesTheWire() {
        for (String action : List.of(QuestActionPayload.ACCEPT,
                QuestActionPayload.ABANDON, QuestActionPayload.CLAIM)) {
            QuestActionPayload sent = new QuestActionPayload(POST, "q40D", action);
            QuestActionPayload read = roundTrip(sent);
            assertEquals(POST, read.post());
            assertEquals("q40D", read.questId());
            assertEquals(action, read.action(),
                    "the action is the one field that changes what a press means");
            assertEquals(sent, read);
        }
    }

    /**
     * The disconnect these could have caused.
     *
     * <p>A town can be named by command and a quest's words come out of the
     * simulation, which has never been told how wide the wire is.
     * {@code ByteBufCodecs.stringUtf8} throws while encoding anything past its
     * cap, and a custom payload that throws in the encoder is not skippable —
     * netty drops the connection rather than the packet.
     */
    @Test
    void absurdWordsAreClippedRatherThanRefusedByTheEncoder() {
        QuestBoardPayload board = new QuestBoardPayload("N".repeat(500), POST, 1, 1, true,
                List.of(new QuestBoardPayload.Row("i".repeat(500), "K".repeat(500),
                        "T".repeat(500), "D".repeat(500), "g".repeat(500), 1, 0,
                        new QuestBoardPayload.Pay(1, "r".repeat(500), 1, 1), 1,
                        "S".repeat(500), false)),
                List.of("W".repeat(500)));

        assertTrue(board.town().length() < 500, "the town name was not clipped");
        assertTrue(board.rows().getFirst().detail().length() < 500);
        assertTrue(board.done().getFirst().length() < 500);
        assertEquals(board, roundTrip(board));

        QuestActionPayload press = new QuestActionPayload(POST, "q".repeat(500),
                "a".repeat(500));
        assertTrue(press.questId().length() < 500);
        assertEquals(press, roundTrip(press));
    }

    /** More rows than a board can hold never reach the wire in the first place. */
    @Test
    void aBoardIsNeverWiderThanTheBoard() {
        List<QuestBoardPayload.Row> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < QuestBoard.BOARD_SIZE + 4; i++) {
            tooMany.add(new QuestBoardPayload.Row("q" + i, QuestKind.VISIT.name(),
                    "Come and see", "Have a look.", "mark", 1, 0,
                    new QuestBoardPayload.Pay(8, "", 0, 1), 100,
                    QuestState.OFFERED.name(), false));
        }

        QuestBoardPayload board = new QuestBoardPayload("Testburg", POST, 0, 0, true,
                tooMany, List.of());

        assertEquals(QuestBoard.BOARD_SIZE, board.rows().size());
        assertEquals(board, roundTrip(board));
    }

    /**
     * The board is read straight off the settlement, and read for one reader:
     * {@code mine} is the difference between a row you can abandon and a row
     * somebody else is already working on.
     */
    @Test
    void theBoardIsReadOffTheTownForWhoeverIsStandingAtIt() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        town.quests().post(Quest.offered("q40D", QuestKind.DELIVER,
                        "Timber for the yard", "Waiting on logs.", TownStores.WOOD,
                        null, 32, Reward.of(96, 2), 40, 640)
                .takenBy(READER, 1_240)
                .withProgress(5));
        town.quests().post(Quest.offered("q41S", QuestKind.SLAY, "Thin them out",
                "Too much abroad after dark.", "hostiles", null, 4,
                Reward.of(24, 3), 41, 641));
        town.standing().add(READER, 37);

        QuestBoardPayload board = roundTrip(
                QuestBoardPayload.of(town, POST, READER, 100, true));

        assertEquals("Testburg", board.town());
        assertEquals(37, board.standing());
        assertEquals(town.treasury(), board.treasury());
        assertEquals(2, board.rows().size());

        QuestBoardPayload.Row mine = board.rows().getFirst();
        assertTrue(mine.mine(), "that one is yours");
        assertEquals(5, mine.progress());
        assertEquals(32, mine.amount());
        assertEquals(TownStores.WOOD, mine.target(),
                "so the row can be drawn as the thing it wants");
        assertEquals(1_140, mine.stepsLeft(), "and how long is left to do it in");
        assertFalse(board.rows().get(1).mine());
    }

    @Test
    void aStrangerSeesTheSameBoardAndNoneOfItAsTheirs() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        town.quests().post(Quest.offered("q40D", QuestKind.DELIVER, "Timber",
                        "Waiting on logs.", TownStores.WOOD, null, 32,
                        Reward.of(96, 2), 40, 640)
                .takenBy(READER, 1_240));

        QuestBoardPayload board = QuestBoardPayload.of(
                town, POST, UUID.randomUUID(), 100, true);

        assertEquals(1, board.rows().size());
        assertFalse(board.rows().getFirst().mine());
        assertEquals(0, board.standing(), "and no credit they did not earn");
    }
}
