package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "I still have that town's map open — tell me how it is now."
 *
 * <p>The second thing this mod's client ever says back, and the reason the town
 * map is a screen rather than a photograph. A settlement steps every few
 * seconds; a plan that froze the moment it was opened would show builders who
 * had finished, queues that had emptied and settlers standing where they were a
 * minute ago, which is worse than not drawing them at all.
 *
 * <p>The client sends one of these a second while the screen is up and stops the
 * moment it closes. There is no subscription on the server: nothing is
 * remembered between requests, so a client that disconnects, crashes, or simply
 * stops asking costs the server nothing and leaves nothing behind.
 *
 * <p><strong>The center is a claim, not evidence.</strong> It says which town the
 * screen believes it is showing. The server matches it against the settlements
 * it actually has, and refuses one further than {@link #REACH} from the player —
 * a screen left open across a teleport must not turn into a telescope. The
 * answer is built from the settlement, never from anything in this message.
 */
public record TownMapRequestPayload(BlockPos center) implements CustomPacketPayload {

    public static final Type<TownMapRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "town_map_request"));

    /**
     * How far from a town its map keeps refreshing, in blocks.
     *
     * <p>The same reach the wayfinder's directory calls earshot. Generous — a
     * player who walks out to the woodline and back should not watch the screen
     * go stale — and finite, which is the point.
     */
    public static final int REACH = 1024;

    /**
     * How far the claimed center may be from a real town's and still match it.
     *
     * <p>Not zero, because a settlement's center carries a height and the client
     * is repeating back a position it was given. Two blocks of slack costs
     * nothing and no two towns are ever this close.
     */
    private static final int SLACK = 2;

    public static final StreamCodec<RegistryFriendlyByteBuf, TownMapRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TownMapRequestPayload::center,
                    TownMapRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TownMapRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            return;
        }
        Settlement asked = TownMaps.matching(world, payload.center(), SLACK);
        if (asked == null || !TownMaps.within(asked, player, REACH)) {
            // Nothing is sent back. A refusal would have to be a payload of its
            // own and the screen has a perfectly good answer already: it keeps
            // the last reading and stops being told it is current.
            return;
        }
        PacketDistributor.sendToPlayer(player,
                TownMapPayload.of(asked, world.stepsElapsed(), false));
    }
}
