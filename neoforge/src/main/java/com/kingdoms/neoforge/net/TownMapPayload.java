package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.client.KingdomsScreens;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
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
public record TownMapPayload(String town, int centerX, int centerZ, int claimRadius,
                             List<Mark> marks, List<Road> roads)
        implements CustomPacketPayload {

    /** One building, as a rectangle on the plan. Unfinished ones draw hollow. */
    public record Mark(int x, int z, int width, int depth, boolean finished) {
    }

    /**
     * One stretch of road, as a straight run on the plan.
     *
     * <p>The network is what tells you a plan is a settlement rather than a
     * scatter of sheds, so it travels with the buildings. Segments are always
     * axis-aligned, which is why two corners are enough to draw one.
     */
    public record Road(int x1, int z1, int x2, int z2) {
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
                    ByteBufCodecs.BOOL, Mark::finished,
                    Mark::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Road> ROAD_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Road::x1,
                    ByteBufCodecs.VAR_INT, Road::z1,
                    ByteBufCodecs.VAR_INT, Road::x2,
                    ByteBufCodecs.VAR_INT, Road::z2,
                    Road::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, TownMapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), TownMapPayload::town,
                    ByteBufCodecs.VAR_INT, TownMapPayload::centerX,
                    ByteBufCodecs.VAR_INT, TownMapPayload::centerZ,
                    ByteBufCodecs.VAR_INT, TownMapPayload::claimRadius,
                    MARK_CODEC.apply(ByteBufCodecs.list()), TownMapPayload::marks,
                    ROAD_CODEC.apply(ByteBufCodecs.list()), TownMapPayload::roads,
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
                    footprint.width(), footprint.depth(), true));
        }
        // Work that is merely planned is on the map too, drawn hollow. A town is
        // its intentions as much as its walls, and the plan is where you look to
        // see where the next building is going before anybody has dug anything.
        for (BuildTask task : settlement.buildQueue()) {
            if (!BuildPlanner.holdsGround(task.blueprintId())) {
                continue;   // repair flights are a path, not a plot worth drawing
            }
            Footprint footprint = task.footprint();
            int width = footprint.isKnown() ? footprint.width()
                    : BuildPlanner.plotSpanOf(task.blueprintId(), settlement.catalog());
            int depth = footprint.isKnown() ? footprint.depth() : width;
            marks.add(new Mark(task.origin().x(), task.origin().z(), width, depth, false));
        }
        List<Road> roads = new ArrayList<>();
        for (PathNetwork.Segment segment : settlement.paths().segments()) {
            roads.add(new Road(segment.from().x(), segment.from().z(),
                    segment.to().x(), segment.to().z()));
        }
        return new TownMapPayload(settlement.name(), settlement.center().x(),
                settlement.center().z(), settlement.claimRadius(), marks, roads);
    }

    public static void handle(TownMapPayload payload, IPayloadContext context) {
        KingdomsScreens.openTownMap(payload);
    }
}
