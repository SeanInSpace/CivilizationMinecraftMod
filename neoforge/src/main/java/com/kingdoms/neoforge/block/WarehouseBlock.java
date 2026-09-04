package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.net.SupplyPayload;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
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
import com.kingdoms.sim.geom.SimPos;

/**
 * The town's stores, and the one place a player can add to them.
 *
 * <p>Hold nothing and it shows the bill for whatever is being built. Hold a stack
 * and it takes it — this is the whole of the player's hand in a town's economy,
 * and the reason a settlement stuck for timber can be helped rather than merely
 * watched.
 */
public class WarehouseBlock extends BuildingPostBlock implements EntityBlock {

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
        // The warehouse they are standing at, rather than whichever store the
        // town lists first — a gift left at one door used to turn up at another.
        BlockPos where = player.blockPosition();
        settlement.storeNear(new SimPos(where.getX(), where.getY(), where.getZ()))
                .add(resource, given);
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
     *
     * <p><strong>Deliberately not shared with the market.</strong> It is right
     * for a one-way door and wrong for a counter that pays: any pickaxe-mineable
     * block item reads as stone, which at a warehouse means a player's shulker
     * box is refused as a gift and at a stall would mean it was bought off them
     * for two coin. The stall asks {@code Resources.resourceOf}, which names the
     * items it will take. Two classifiers, because there are genuinely two
     * questions — what will you accept as a gift, and what will you pay for.
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
