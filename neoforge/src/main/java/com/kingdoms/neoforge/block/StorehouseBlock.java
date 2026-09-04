package com.kingdoms.neoforge.block;

import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.StorehousePlanner;
import com.kingdoms.sim.settlement.TownStores;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.kingdoms.sim.geom.SimPos;

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
public class StorehouseBlock extends BuildingPostBlock implements EntityBlock {

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StoreChestBlockEntity(pos, state);
    }

    /**
     * An empty hand opens the stores; everything else is the post as it was.
     *
     * <p>Hooked here rather than in {@code extraReport} because opening a
     * container needs the block's position, which the report hook does not
     * carry. Sneaking falls through to the post, so the ledger report and the
     * bill of materials are still one gesture away.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (player.getMainHandItem().isEmpty() && openStores(player, pos)) {
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    /**
     * Opens the stores.
     *
     * <p>An empty hand opens the container, because a building that holds the
     * town's goods should behave like the chest it is. Everything the post used
     * to do on an empty hand — the bill, the ledger report — moves to a
     * sneak-click, so both are still one gesture away.
     */
    private static boolean openStores(Player player, BlockPos pos) {
        if (player.isShiftKeyDown() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof StoreChestBlockEntity stores)) {
            return false;
        }
        player.openMenu(stores);
        return true;
    }

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
        // The same figure the planner banked. Working it out twice is how the
        // town came to be paid a different number of emeralds from the one that
        // left the player's hand.
        int paid = StorehousePlanner.emeraldsFor(sold);
        held.shrink(paid);
        give(player, new ItemStack(Items.OAK_LOG, sold));
        player.sendSystemMessage(Component.literal(
                "  " + sold + " logs for " + paid + " emerald" + (paid == 1 ? "" : "s") + ".")
                .withStyle(ChatFormatting.GREEN));
    }

    private static void donate(Player player, Settlement settlement,
                               ItemStack held, String resource) {
        // The store they are standing at, which is the one they meant.
        BlockPos where = player.blockPosition();
        int taken = StorehousePlanner.donate(settlement,
                new SimPos(where.getX(), where.getY(), where.getZ()),
                resource, held.getCount());
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
