package com.civilization.neoforge.item;

import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.net.TownMaps;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

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
        SimWorld world = CivilizationMod.simulationFor(level);
        if (world == null) {
            return false;
        }

        SimPos here = new SimPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        Settlement nearest = TownMaps.nearest(world, here);
        if (nearest == null) {
            player.sendSystemMessage(Component.literal("The parchment is blank — no town nearby."));
            return false;
        }
        if (!TownMaps.open(server, nearest)) {
            player.sendSystemMessage(Component.literal(
                    "The parchment stays blank — this client cannot draw it."));
            return false;
        }
        return true;
    }
}
