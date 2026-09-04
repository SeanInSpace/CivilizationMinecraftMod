package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.net.MarketPayload;
import com.kingdoms.neoforge.trade.MarketCounter;
import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.MarketPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The market stall: the one place the town will trade with a player.
 *
 * <p>Open during {@link MarketPlanner} hours and shut outside them, and only
 * if somebody here trades for a living. What is on offer is whatever the town
 * is short of or has spare, at prices its own shortages set — see {@code Market}
 * — and a reserve is always kept back, so a settlement can never be bought into
 * starvation. The seed corn is not for sale at any price.
 *
 * <p>It used to sell bread for emeralds at a fixed rate and never touch the
 * treasury, so trading with a town left its books unchanged. Every emerald now
 * comes out of, or goes into, the town's own money.
 *
 * <p><strong>Why the screen is the mod's own and not a merchant's.</strong>
 * Implementing {@code Merchant} bought the villager trading screen for one
 * class, and it was the right first move; what it cannot do is say <em>why</em>.
 * A merchant screen has room for a price and nowhere at all for "they are
 * starving", and that sentence is the entire design — the whole point of prices
 * that move is that a town's trouble should be legible. So the board is sent as
 * a payload and drawn with the same chrome as the mod's other screens, and each
 * row carries its reason.
 */
public class MarketBlock extends BuildingPostBlock {

    /**
     * How far a player may stand from the stall and still be served, squared.
     *
     * <p>Eight blocks, which is what a vanilla container allows before it shuts
     * itself. The screen is a claim by a client and can be kept open across half
     * a kingdom otherwise.
     */
    private static final double REACH_SQ = 64.0;

    public MarketBlock(String role, String explains, Properties properties) {
        super(role, explains, properties);
    }

    @Override
    protected void extraReport(Player player, Settlement settlement, BlockPos pos) {
        Level level = player.level();
        // 26.2 replaced getDayTime() with the world-clock system; this is the
        // dimension's own clock, so a fixed-time dimension still reads sanely.
        long dayTime = level.getDefaultClockTime();

        if (!MarketPlanner.isOpen(dayTime)) {
            long wait = MarketPlanner.untilOpen(dayTime);
            player.sendSystemMessage(Component.literal(
                    "  Closed. The stalls open in about " + (wait / 1000) + " hours.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!Market.hasTrader(settlement)) {
            player.sendSystemMessage(Component.literal(
                    "  Nobody here trades. The town needs somebody in the market.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (player instanceof ServerPlayer server) {
            // An empty board is still worth opening: the screen says the town
            // wants nothing and can spare nothing, and shows the treasury that
            // is usually the reason.
            PacketDistributor.sendToPlayer(server, MarketPayload.of(settlement, pos, true));
        }
    }

    /**
     * One lot, taken at the counter a player says they are standing at.
     *
     * <p>Every claim in the message is re-derived here rather than believed.
     * The block really is a market, the player really is beside it, the stall
     * really is open and staffed, and the price comes from the settlement as it
     * is now — not as it was when the screen was drawn. A screen can be left
     * open while a town starves, and a client that is not ours can say anything
     * at all.
     *
     * <p>The board is sent back afterwards whether or not the trade happened,
     * because the interesting case is that it did: the second lot of grain is
     * not the same price as the first when the first one is what stopped the
     * town starving.
     *
     * <p>Every refusal says something. A stall that does nothing when a button
     * is pressed is indistinguishable from a broken one, and the screen holds no
     * distance check of its own — a player four steps too far back would
     * otherwise press Buy and get silence.
     */
    public static void takeDeal(ServerPlayer player, BlockPos post, String resource,
                                boolean townBuys) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (post.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > REACH_SQ) {
            said(player, "You have stepped away from the stall.");
            return;
        }
        if (!(level.getBlockState(post).getBlock() instanceof MarketBlock)) {
            said(player, "There is no stall there any more.");
            return;
        }
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            return;
        }
        SimPos at = NeoForgeWorldBridge.toSimPos(post);
        Settlement settlement = owningSettlement(world, at);
        if (settlement == null) {
            said(player, "That stall belongs to nobody.");
            return;
        }
        if (taskAt(settlement, post) != null) {
            said(player, "They are rebuilding the stall. Come back when it is up.");
            return;
        }
        if (!MarketPlanner.isOpen(level.getDefaultClockTime())
                || !Market.hasTrader(settlement)) {
            said(player, "The stall has shut.");
            return;
        }
        MarketCounter.take(player, settlement, at, resource, townBuys);
        PacketDistributor.sendToPlayer(player, MarketPayload.of(settlement, post, false));
    }

    private static void said(ServerPlayer player, String what) {
        player.sendSystemMessage(Component.literal("  " + what)
                .withStyle(ChatFormatting.GRAY));
    }
}
