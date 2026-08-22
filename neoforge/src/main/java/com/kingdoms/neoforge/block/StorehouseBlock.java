package com.kingdoms.neoforge.block;

import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.StorehousePlanner;
import com.kingdoms.sim.settlement.TownStores;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The town storehouse: the ledger made walkable, and the founding arc's
 * trading post.
 *
 * <p>It stands from FORTIFIED — exactly when the palisade is drinking timber —
 * and deals in the stuff the town is built from, where the market deals in its
 * bread. Walk up empty-handed and it reads the ledger. Bring logs, stone or
 * bread and the town takes them with thanks. Bring emeralds and it sells
 * timber, keeping {@link StorehousePlanner#RESERVE_WOOD} back so a town can
 * never be bought out of its own repairs.
 */
public class StorehouseBlock extends BuildingPostBlock {

    public StorehouseBlock(String role, String explains, Properties properties) {
        super(role, explains, properties);
    }

    @Override
    protected String report(Settlement settlement) {
        return "Timber " + settlement.woodStock()
                + ", stone " + settlement.stoneStock()
                + ", food " + settlement.foodStock()
                + ", saplings " + settlement.stores().get(TownStores.SAPLINGS)
                + ". Population " + settlement.population()
                + ", " + settlement.buildings().size() + " buildings";
    }

    @Override
    protected void extraReport(Player player, Settlement settlement) {
        ItemStack held = player.getMainHandItem();

        if (held.is(Items.EMERALD)) {
            sellTimber(player, settlement, held);
            return;
        }
        String donated = donatable(held);
        if (donated != null) {
            donate(player, settlement, held, donated);
            return;
        }
        player.sendSystemMessage(Component.literal(
                "  Donations taken at the door: logs, cobblestone, bread. "
                        + "Emeralds buy timber, "
                        + StorehousePlanner.WOOD_PER_EMERALD + " to the coin.")
                .withStyle(ChatFormatting.GRAY));
    }

    private static void sellTimber(Player player, Settlement settlement, ItemStack held) {
        int forSale = StorehousePlanner.timberForSale(settlement);
        if (forSale <= 0) {
            player.sendSystemMessage(Component.literal(
                    "  No timber to spare — the reserve is not for sale.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int affordable = Math.min(held.getCount(),
                Math.max(1, forSale / StorehousePlanner.WOOD_PER_EMERALD));
        int sold = StorehousePlanner.sellTimber(settlement, affordable);
        if (sold <= 0) {
            player.sendSystemMessage(Component.literal("  Nothing to spare today.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int paid = Math.max(1, sold / StorehousePlanner.WOOD_PER_EMERALD);
        held.shrink(paid);
        give(player, new ItemStack(Items.OAK_LOG, sold));
        player.sendSystemMessage(Component.literal(
                "  " + sold + " logs for " + paid + " emerald" + (paid == 1 ? "" : "s") + ".")
                .withStyle(ChatFormatting.GREEN));
    }

    private static void donate(Player player, Settlement settlement,
                               ItemStack held, String resource) {
        int taken = StorehousePlanner.donate(settlement, resource, held.getCount());
        if (taken <= 0) {
            player.sendSystemMessage(Component.literal(
                    "  The " + resource + " store is full — nothing more fits.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        held.shrink(taken);
        player.sendSystemMessage(Component.literal(
                "  " + settlement.name() + " takes " + taken + " " + resource
                        + " with thanks.")
                .withStyle(ChatFormatting.GREEN));
    }

    /** Which store this item feeds, or null if the town has no use for it. */
    private static String donatable(ItemStack held) {
        if (held.is(ItemTags.LOGS)) {
            return TownStores.WOOD;
        }
        if (held.is(Items.COBBLESTONE) || held.is(Items.STONE)) {
            return TownStores.STONE;
        }
        if (held.is(Items.BREAD)) {
            return TownStores.FOOD;
        }
        return null;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
