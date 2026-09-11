package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.block.QuestBoardBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "I will take that one." — or give it back, or collect for it.
 *
 * <p>The second thing this mod's client ever tells its server, after the
 * market's deal, and it is built the same way: everything it carries is a
 * <em>claim</em> and none of it is evidence. Which board, which notice, which
 * button. The server finds the quest again from the settlement, checks whose it
 * is and what state it is actually in, and checks the player is standing at the
 * post they say they are — a screen can be left open across half a kingdom, and
 * a client that is not ours can say anything at all.
 *
 * <p>The action travels as a word rather than an ordinal, the way a profession
 * and a market reason do. An unknown word is refused rather than guessed at:
 * the one thing worse than a button that does nothing is a button that does
 * something else.
 */
public record QuestActionPayload(BlockPos post, String questId, String action)
        implements CustomPacketPayload {

    public static final String ACCEPT = "accept";
    public static final String ABANDON = "abandon";
    public static final String CLAIM = "claim";

    public static final Type<QuestActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "quest_action"));

    /** An id and an action are both single short tokens. */
    private static final int MAX_WORD = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestActionPayload>
            STREAM_CODEC = StreamCodec.composite(
                    BlockPos.STREAM_CODEC, QuestActionPayload::post,
                    ByteBufCodecs.stringUtf8(MAX_WORD), QuestActionPayload::questId,
                    ByteBufCodecs.stringUtf8(MAX_WORD), QuestActionPayload::action,
                    QuestActionPayload::new);

    /** Clipped in the constructor, so nothing can build one the encoder would refuse. */
    public QuestActionPayload {
        questId = clip(questId);
        action = clip(action);
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_WORD ? text : text.substring(0, MAX_WORD);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(QuestActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            QuestBoardBlock.takeAction(player, payload.post(), payload.questId(),
                    payload.action());
        }
    }
}
