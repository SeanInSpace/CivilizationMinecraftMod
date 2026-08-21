package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.client.KingdomsScreens;
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
 * "Here is the state of your town" — server to client, to open the overview.
 *
 * <p>A snapshot rather than a live view. The screen shows what was true when it
 * was opened and does not update behind the player's back, which is honest: the
 * alternative is a number that changes while you are reading it, for a
 * simulation that steps once every five seconds anyway.
 *
 * <p>The ledger travels as a list of name/amount pairs rather than a typed set of
 * fields, because {@code TownStores} lets anything be stored under any name. A
 * resource this build has never heard of still shows up.
 */
public record TownOverviewPayload(String town, int population, List<Line> lines)
        implements CustomPacketPayload {

    /** One row of the ledger. */
    public record Line(String resource, int amount) {
    }

    public static final Type<TownOverviewPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "town_overview"));

    /** Generous for a town name, short enough not to be a payload attack. */
    private static final int MAX_NAME = 96;
    private static final int MAX_RESOURCE = 64;

    private static final StreamCodec<RegistryFriendlyByteBuf, Line> LINE_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_RESOURCE), Line::resource,
                    ByteBufCodecs.VAR_INT, Line::amount,
                    Line::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, TownOverviewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), TownOverviewPayload::town,
                    ByteBufCodecs.VAR_INT, TownOverviewPayload::population,
                    LINE_CODEC.apply(ByteBufCodecs.list()), TownOverviewPayload::lines,
                    TownOverviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Reads a settlement into a payload, in the ledger's own order. */
    public static TownOverviewPayload of(Settlement settlement) {
        List<Line> lines = new ArrayList<>();
        settlement.stores().all().forEach((resource, amount) -> lines.add(new Line(resource, amount)));
        return new TownOverviewPayload(settlement.name(), settlement.population(), lines);
    }

    public static void handle(TownOverviewPayload payload, IPayloadContext context) {
        // Already on the client's main thread by the time a handler runs.
        KingdomsScreens.openTownOverview(payload);
    }
}
