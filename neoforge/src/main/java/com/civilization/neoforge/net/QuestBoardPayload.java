package com.civilization.neoforge.net;

import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.client.CivilizationScreens;
import com.civilization.sim.quest.Quest;
import com.civilization.sim.quest.QuestBoard;
import com.civilization.sim.quest.Reward;
import com.civilization.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the town is asking for, read for one particular player.
 *
 * <p>Unlike the overview and the bill, this board is not the same for two
 * people standing at it: {@code mine} says which rows are that player's to
 * abandon or claim, and the standing in the header is theirs. Everything else
 * is the town's and identical for everybody.
 *
 * <p>Sent again after every button, like the market's board and for the same
 * reason — pressing Accept and going on looking at a row that still says "on
 * offer" is worse than no screen at all. {@code opening} tells the right-click
 * from the refresh, so hitting escape before the reply lands does not put the
 * board back up.
 *
 * <p>Every word here was written by the server. The client turns none of it
 * back into a decision: pressing a button sends an id, and the server finds the
 * quest again from the settlement itself.
 */
public record QuestBoardPayload(String town, BlockPos post, int standing, int treasury,
                                boolean opening, List<Row> rows, List<String> done)
        implements CustomPacketPayload {

    /**
     * What a reward comes to, flattened.
     *
     * <p>Sent as three numbers and a word rather than as a sentence, because the
     * screen draws coin and goods each as their own item — and because a
     * sentence assembled on the server is a sentence in the server's language.
     */
    public record Pay(int coin, String goods, int goodsAmount, int standing) {
        static Pay of(Reward reward) {
            return new Pay(reward.coin(), reward.goods(), reward.goodsAmount(),
                    reward.standing());
        }
    }

    /**
     * One notice.
     *
     * @param kind      the {@link com.civilization.sim.quest.QuestKind} name; a client
     *                  that has never heard of it draws the row and greys it
     * @param target    the ledger word the ask is about, so the row can be drawn
     *                  as the thing it wants rather than guessed at from its
     *                  title. A delivery's ask and its reward are different
     *                  goods, and the one thing worse than no icon is the wrong
     *                  one.
     * @param stepsLeft how long is left before it lapses, in simulation steps
     * @param mine      whether this is the reader's own job
     */
    public record Row(String id, String kind, String title, String detail, String target,
                      int amount, int progress, Pay pay, int stepsLeft, String state,
                      boolean mine) {
    }

    public static final Type<QuestBoardPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CivilizationMod.MOD_ID, "quest_board"));

    /** Generous for a custom-named town, short enough not to be a payload attack. */
    private static final int MAX_NAME = 96;

    /** A quest id, a kind, a state and a ledger word are all single tokens. */
    private static final int MAX_WORD = 32;

    /** A title is a phrase and a detail is a sentence or two. */
    private static final int MAX_TITLE = 64;
    private static final int MAX_DETAIL = 192;

    private static final StreamCodec<RegistryFriendlyByteBuf, Pay> PAY_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Pay::coin,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Pay::goods,
                    ByteBufCodecs.VAR_INT, Pay::goodsAmount,
                    ByteBufCodecs.VAR_INT, Pay::standing,
                    Pay::new);

    // Order is load-bearing: the terminal ::new is the canonical record
    // constructor, so each getter must sit exactly where its component does.
    private static final StreamCodec<RegistryFriendlyByteBuf, Row> ROW_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_WORD), Row::id,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Row::kind,
                    ByteBufCodecs.stringUtf8(MAX_TITLE), Row::title,
                    ByteBufCodecs.stringUtf8(MAX_DETAIL), Row::detail,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Row::target,
                    ByteBufCodecs.VAR_INT, Row::amount,
                    ByteBufCodecs.VAR_INT, Row::progress,
                    PAY_CODEC, Row::pay,
                    ByteBufCodecs.VAR_INT, Row::stepsLeft,
                    ByteBufCodecs.stringUtf8(MAX_WORD), Row::state,
                    ByteBufCodecs.BOOL, Row::mine,
                    Row::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestBoardPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), QuestBoardPayload::town,
                    BlockPos.STREAM_CODEC, QuestBoardPayload::post,
                    ByteBufCodecs.VAR_INT, QuestBoardPayload::standing,
                    ByteBufCodecs.VAR_INT, QuestBoardPayload::treasury,
                    ByteBufCodecs.BOOL, QuestBoardPayload::opening,
                    ROW_CODEC.apply(ByteBufCodecs.list(QuestBoard.BOARD_SIZE)),
                    QuestBoardPayload::rows,
                    ByteBufCodecs.stringUtf8(MAX_TITLE)
                            .apply(ByteBufCodecs.list(QuestBoard.REMEMBERED)),
                    QuestBoardPayload::done,
                    QuestBoardPayload::new);

    /**
     * Clipped on the way in, so no construction path can build a payload the
     * encoder would then refuse to write.
     *
     * <p>A town can be named by command and a quest's words come from the
     * simulation, which has never been told how wide the wire is.
     * {@code ByteBufCodecs.stringUtf8} throws while encoding anything past its
     * cap, and a custom payload that throws in the encoder is not skippable —
     * netty drops the connection rather than the packet.
     */
    public QuestBoardPayload {
        town = clip(town, MAX_NAME);
        rows = rows.stream()
                .limit(QuestBoard.BOARD_SIZE)
                .map(row -> new Row(clip(row.id(), MAX_WORD), clip(row.kind(), MAX_WORD),
                        clip(row.title(), MAX_TITLE), clip(row.detail(), MAX_DETAIL),
                        clip(row.target(), MAX_WORD), row.amount(), row.progress(),
                        new Pay(row.pay().coin(), clip(row.pay().goods(), MAX_WORD),
                                row.pay().goodsAmount(), row.pay().standing()),
                        row.stepsLeft(), clip(row.state(), MAX_WORD), row.mine()))
                .toList();
        done = done.stream()
                .limit(QuestBoard.REMEMBERED)
                .map(title -> clip(title, MAX_TITLE))
                .toList();
    }

    /** Cutting one char short of a surrogate pair keeps a name from ending in half a letter. */
    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
        return text.substring(0, end);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Reads a town's board off the settlement itself, for one reader.
     *
     * @param opening true for the right-click that asked for the board, false
     *                for the board sent back after a button
     */
    public static QuestBoardPayload of(Settlement settlement, BlockPos post,
                                       UUID reader, long step, boolean opening) {
        List<Row> rows = new ArrayList<>();
        for (Quest quest : settlement.quests().offered()) {
            rows.add(new Row(quest.id(), quest.kind().name(), quest.title(),
                    quest.detail(), quest.target(), quest.amount(), quest.progress(),
                    Pay.of(quest.reward()),
                    (int) Math.min(Integer.MAX_VALUE, quest.stepsLeft(step)),
                    quest.state().name(), quest.isHeldBy(reader)));
        }
        List<String> done = new ArrayList<>();
        for (Quest quest : settlement.quests().completed()) {
            done.add(quest.title());
        }
        return new QuestBoardPayload(settlement.name(), post,
                settlement.standing().of(reader), settlement.treasury(), opening,
                rows, done);
    }

    public static void handle(QuestBoardPayload payload, IPayloadContext context) {
        CivilizationScreens.openQuestBoard(payload);
    }
}
