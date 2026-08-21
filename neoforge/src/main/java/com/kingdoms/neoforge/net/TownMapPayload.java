package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.client.KingdomsScreens;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A plan of the town: where its buildings stand and how big they are.
 *
 * <p>Only buildings whose size is actually known travel. A building raised before
 * footprints were recorded, or one that has never been placed, has nothing
 * truthful to draw — so it is left out rather than guessed at with a default
 * square.
 *
 * <p>The player's own position is deliberately absent: the client already knows
 * where it is standing, and sending it would only be a staler copy.
 */
public record TownMapPayload(String town, int centreX, int centreZ, int claimRadius,
                             List<Mark> marks) implements CustomPacketPayload {

    /** One building, as a rectangle on the plan. */
    public record Mark(int x, int z, int width, int depth) {
    }

    public static final Type<TownMapPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "town_map"));

    private static final int MAX_NAME = 96;

    private static final StreamCodec<RegistryFriendlyByteBuf, Mark> MARK_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Mark::x,
                    ByteBufCodecs.VAR_INT, Mark::z,
                    ByteBufCodecs.VAR_INT, Mark::width,
                    ByteBufCodecs.VAR_INT, Mark::depth,
                    Mark::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, TownMapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), TownMapPayload::town,
                    ByteBufCodecs.VAR_INT, TownMapPayload::centreX,
                    ByteBufCodecs.VAR_INT, TownMapPayload::centreZ,
                    ByteBufCodecs.VAR_INT, TownMapPayload::claimRadius,
                    MARK_CODEC.apply(ByteBufCodecs.list()), TownMapPayload::marks,
                    TownMapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static TownMapPayload of(Settlement settlement) {
        List<Mark> marks = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            Footprint footprint = building.footprint();
            if (!footprint.isKnown()) {
                continue;
            }
            marks.add(new Mark(building.origin().x(), building.origin().z(),
                    footprint.width(), footprint.depth()));
        }
        return new TownMapPayload(settlement.name(), settlement.centre().x(),
                settlement.centre().z(), settlement.claimRadius(), marks);
    }

    public static void handle(TownMapPayload payload, IPayloadContext context) {
        KingdomsScreens.openTownMap(payload);
    }
}
