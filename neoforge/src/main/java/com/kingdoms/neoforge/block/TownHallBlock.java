package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.net.TownOverviewPayload;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The seat of the settlement, and the way in to its books.
 *
 * <p>Every other post reports in chat. This one opens the overview, because the
 * hall is where a town's accounts belong and because a dozen resources is a
 * table, not a sentence.
 */
public class TownHallBlock extends BuildingPostBlock {

    public TownHallBlock(String role, String explains, Properties properties) {
        super(role, explains, properties);
    }

    @Override
    protected String report(Settlement settlement) {
        return "";   // the screen says it all; no chat line to duplicate it
    }

    @Override
    protected void extraReport(Player player, Settlement settlement) {
        if (player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server, TownOverviewPayload.of(settlement));
        }
    }
}
