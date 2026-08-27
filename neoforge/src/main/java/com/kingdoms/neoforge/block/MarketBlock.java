package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.trade.TownMerchant;
import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.MarketPlanner;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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
 */
public class MarketBlock extends BuildingPostBlock {

    public MarketBlock(String role, String explains, Properties properties) {
        super(role, explains, properties);
    }

    @Override
    protected void extraReport(Player player, Settlement settlement) {
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

        // The stall proper. Anything the town is short of or has spare is a real
        // offer at a price its own shortages set -- see Market -- and the
        // villager trading screen already knows how to show that.
        if (Market.hasTrader(settlement)) {
            SimPos at = new SimPos((int) player.getX(), (int) player.getY(), (int) player.getZ());
            TownMerchant stall = new TownMerchant(settlement, at);
            if (stall.hasAnything()) {
                stall.setTradingPlayer(player);
                stall.openTradingScreen(player,
                        Component.literal(settlement.name() + " market"), 1);
                return;
            }
            player.sendSystemMessage(Component.literal(
                    "  Open, but there is nothing they want or can spare today.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.literal(
                "  Nobody here trades. The town needs somebody in the market.")
                .withStyle(ChatFormatting.GRAY));
    }
}
