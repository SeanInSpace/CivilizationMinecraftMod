package com.kingdoms.neoforge.item;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.net.TownMapPayload;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A plan of the nearest town.
 *
 * <p>Opens on use, in hand or against a block — a map you have to aim at
 * something would be a strange map.
 */
public final class TownMapItem extends Item {

    public TownMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return open(player) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return open(player) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Sends the nearest town's plan, or says why there is nothing to draw. */
    private static boolean open(Player player) {
        if (!(player instanceof ServerPlayer server)
                || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null) {
            return false;
        }

        Settlement nearest = null;
        long best = Long.MAX_VALUE;
        SimPos here = new SimPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                long distance = settlement.center().horizontalDistanceSq(here);
                if (distance < best) {
                    best = distance;
                    nearest = settlement;
                }
            }
        }
        if (nearest == null) {
            player.sendSystemMessage(Component.literal("The parchment is blank — no town nearby."));
            return false;
        }
        PacketDistributor.sendToPlayer(server, TownMapPayload.of(nearest));
        return true;
    }
}
