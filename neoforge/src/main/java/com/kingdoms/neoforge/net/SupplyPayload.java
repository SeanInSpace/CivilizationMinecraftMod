package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.client.KingdomsScreens;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * What the town is building and what it still needs for it.
 *
 * <p>Sent when a player asks the warehouse. A snapshot, like the town overview —
 * the numbers were true when the screen opened and do not crawl while being read.
 */
public record SupplyPayload(String building, int percent, List<Need> needs)
        implements CustomPacketPayload {

    /**
     * One line of the bill.
     *
     * @param item   registry id of the block wanted
     * @param needed how many are still to be laid
     * @param held   how many of the resource that block is made of the town has
     */
    public record Need(String item, int needed, int held) {
    }

    public static final Type<SupplyPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "supply"));

    private static final int MAX_NAME = 96;

    private static final StreamCodec<RegistryFriendlyByteBuf, Need> NEED_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), Need::item,
                    ByteBufCodecs.VAR_INT, Need::needed,
                    ByteBufCodecs.VAR_INT, Need::held,
                    Need::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, SupplyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), SupplyPayload::building,
                    ByteBufCodecs.VAR_INT, SupplyPayload::percent,
                    NEED_CODEC.apply(ByteBufCodecs.list()), SupplyPayload::needs,
                    SupplyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SupplyPayload payload, IPayloadContext context) {
        KingdomsScreens.openSupply(payload);
    }
}
