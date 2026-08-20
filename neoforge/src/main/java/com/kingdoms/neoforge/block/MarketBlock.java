package com.kingdoms.neoforge.block;

import com.kingdoms.sim.settlement.MarketPlanner;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The market stall: the one place the town will trade with a player.
 *
 * <p>Open during {@link MarketPlanner} hours and shut outside them. Bring
 * emeralds and it sells bread, keeping a reserve back so a town can never be
 * bought into starvation — the seed corn is not for sale at any price.
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

        int forSale = MarketPlanner.foodForSale(settlement);
        if (forSale <= 0) {
            player.sendSystemMessage(Component.literal(
                    "  Open, but there is nothing to spare today.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.EMERALD)) {
            player.sendSystemMessage(Component.literal(
                    "  Open. " + forSale + " loaves to spare — "
                            + MarketPlanner.FOOD_PER_EMERALD + " for an emerald.")
                    .withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.literal(
                    "  Hold emeralds and use the stall to buy.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        // Only charge for what the town can actually hand over.
        int affordable = Math.min(held.getCount(),
                Math.max(1, forSale / MarketPlanner.FOOD_PER_EMERALD));
        int sold = MarketPlanner.sellFood(settlement, affordable);
        if (sold <= 0) {
            player.sendSystemMessage(Component.literal("  Nothing to spare today.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int paid = Math.max(1, sold / MarketPlanner.FOOD_PER_EMERALD);
        held.shrink(paid);
        if (!player.getInventory().add(new ItemStack(Items.BREAD, sold))) {
            player.drop(new ItemStack(Items.BREAD, sold), false);
        }
        player.sendSystemMessage(Component.literal(
                "  " + sold + " bread for " + paid + " emerald" + (paid == 1 ? "" : "s") + ".")
                .withStyle(ChatFormatting.GREEN));
    }
}
