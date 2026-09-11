package com.civilization.neoforge.block;

import com.civilization.neoforge.net.TownMaps;
import com.civilization.sim.settlement.Settlement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The seat of the settlement, and the way in to everything it knows.
 *
 * <p>Every other post reports in chat. This one opens the town map, because the
 * hall is where a town's accounts belong and because a town is a plan and a
 * dozen tables, neither of which is a sentence.
 *
 * <p>It used to open the plain ledger — a list of resources and a population
 * count — and that screen still exists behind {@code /civ overview} for anybody
 * who only wants the books. The map's own overview tab is a superset of it, so
 * walking to the hall now gets you strictly more than it used to.
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
            TownMaps.open(server, settlement);
        }
    }
}
