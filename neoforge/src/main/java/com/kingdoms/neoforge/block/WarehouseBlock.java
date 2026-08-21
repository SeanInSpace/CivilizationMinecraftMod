package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.net.SupplyPayload;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The town's stores, and the one place a player can add to them.
 *
 * <p>Hold nothing and it shows the bill for whatever is being built. Hold a stack
 * and it takes it — this is the whole of the player's hand in a town's economy,
 * and the reason a settlement stuck for timber can be helped rather than merely
 * watched.
 */
public class WarehouseBlock extends BuildingPostBlock {

    public WarehouseBlock(String role, String explains, Properties properties) {
        super(role, explains, properties);
    }

    @Override
    protected String report(Settlement settlement) {
        return "";   // the screen carries it
    }

    @Override
    protected void extraReport(Player player, Settlement settlement) {
        if (!(player instanceof ServerPlayer server)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            donate(player, settlement, held);
            return;
        }
        PacketDistributor.sendToPlayer(server, billFor(level, settlement));
    }

    /** Turns a stack in hand into stock the town can actually spend. */
    private static void donate(Player player, Settlement settlement, ItemStack held) {
        String resource = resourceOf(held);
        if (resource == null) {
            player.sendSystemMessage(Component.literal(
                    "  The storekeeper has no use for that.").withStyle(ChatFormatting.GRAY));
            return;
        }
        int given = held.getCount();
        settlement.stores().add(resource, given);
        held.setCount(0);
        player.sendSystemMessage(Component.literal(
                "  " + given + " " + resource + " into the stores of " + settlement.name()
                        + " (now " + settlement.stores().get(resource) + ").")
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * What a stack counts as, in the town's ledger.
     *
     * <p>Keyed off the same tags the builders use to decide which tool digs a
     * block, so what a player hands over is measured the same way the town spends
     * it. Anything else is refused rather than silently swallowed.
     */
    private static String resourceOf(ItemStack stack) {
        if (stack.is(Items.IRON_INGOT)) {
            return TownStores.IRON;
        }
        if (stack.getItem().components().has(net.minecraft.core.component.DataComponents.FOOD)) {
            return TownStores.FOOD;
        }
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE)) {
                return TownStores.WOOD;
            }
            if (block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                return TownStores.STONE;
            }
        }
        return null;
    }

    private static SupplyPayload billFor(ServerLevel level, Settlement settlement) {
        if (settlement.buildQueue().isEmpty()) {
            return new SupplyPayload("nothing", 0, List.of());
        }
        BuildTask task = settlement.buildQueue().getFirst();
        Map<net.minecraft.world.item.Item, Integer> bill =
                BlueprintPlacer.billOfMaterials(level, task);

        List<SupplyPayload.Need> needs = new ArrayList<>();
        bill.forEach((item, count) -> {
            String resource = resourceOf(new ItemStack(item));
            int held = resource == null ? 0 : settlement.stores().get(resource);
            needs.add(new SupplyPayload.Need(
                    BuiltInRegistries.ITEM.getKey(item).toString(), count, held));
        });
        needs.sort((a, b) -> Integer.compare(b.needed(), a.needed()));

        String name = task.blueprintId();
        name = name.substring(name.indexOf(':') + 1).replace('_', ' ');
        return new SupplyPayload(name,
                (int) Math.round(task.completionFraction() * 100), needs);
    }
}
