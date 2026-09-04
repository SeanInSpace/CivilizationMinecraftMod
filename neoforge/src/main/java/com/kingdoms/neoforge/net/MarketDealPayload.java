package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.block.MarketBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "I will take one lot of that."
 *
 * <p>The first thing this mod's client ever tells its server. Everything it
 * carries is a <em>claim</em> and none of it is evidence: which counter, which
 * good, which way round. The server re-derives the price, the stock, the
 * reserve and whether the stall is even open from the settlement itself, and
 * checks the player is standing at the block they say they are — a screen can
 * be left open while a town starves, or made to say anything at all by a client
 * that is not ours.
 *
 * <p>One lot per message, deliberately. The alternative is a count the server
 * has to bound anyway, and a button that means exactly one thing is a button
 * whose price the player has already read.
 */
public record MarketDealPayload(BlockPos post, String resource, boolean townBuys)
        implements CustomPacketPayload {

    public static final Type<MarketDealPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "market_deal"));

    /** A resource is one word out of the town's ledger. */
    private static final int MAX_WORD = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketDealPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, MarketDealPayload::post,
                    ByteBufCodecs.stringUtf8(MAX_WORD), MarketDealPayload::resource,
                    ByteBufCodecs.BOOL, MarketDealPayload::townBuys,
                    MarketDealPayload::new);

    /** Clipped in the constructor, so nothing can build one the encoder would refuse. */
    public MarketDealPayload {
        resource = resource.length() <= MAX_WORD ? resource : resource.substring(0, MAX_WORD);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MarketDealPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MarketBlock.takeDeal(player, payload.post(), payload.resource(),
                    payload.townBuys());
        }
    }
}
