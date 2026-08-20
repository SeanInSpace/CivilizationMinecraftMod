package com.kingdoms.neoforge.block;

import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Tallies;
import com.kingdoms.sim.settlement.TownStores;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * The town's noticeboard: what it has seen done, and what it still wants.
 *
 * <p>Reads whatever counters the settlement happens to be keeping rather than a
 * fixed list, so a stat can start being tallied — by this mod, a datapack, or an
 * addon — and appear here without the board being told about it. Counters that
 * have never been raised are simply absent, which is why "hideouts cleared" can
 * exist as an idea before hideouts do.
 */
public class QuestBoardBlock extends BuildingPostBlock {

    public QuestBoardBlock(String role, String explains, Properties properties) {
        super(role, explains, properties);
    }

    @Override
    protected String report(Settlement settlement) {
        return "Population " + settlement.population()
                + ", " + settlement.buildings().size() + " buildings";
    }

    @Override
    protected void extraReport(Player player, Settlement settlement) {
        Map<String, Integer> tallies = settlement.tallies().all();
        if (tallies.isEmpty()) {
            player.sendSystemMessage(Component.literal("  Nothing of note has happened yet.")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.literal("  Deeds recorded:")
                    .withStyle(ChatFormatting.GOLD));
            tallies.forEach((stat, count) -> player.sendSystemMessage(Component.literal(
                    "    " + Tallies.pretty(stat) + ": " + count).withStyle(ChatFormatting.GRAY)));
        }

        player.sendSystemMessage(Component.literal("  Wanted:").withStyle(ChatFormatting.GOLD));
        boolean anyWant = false;
        for (String resource : new String[]{TownStores.FOOD, TownStores.WOOD,
                TownStores.STONE, TownStores.IRON, TownStores.TOOLS}) {
            int held = settlement.stores().get(resource);
            int want = wanted(resource);
            if (held < want) {
                anyWant = true;
                player.sendSystemMessage(Component.literal(
                        "    " + Tallies.pretty(resource) + ": " + held + " of " + want)
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        if (!anyWant) {
            player.sendSystemMessage(Component.literal("    Nothing. The town wants for nothing.")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** A comfortable holding for each resource — what the town would like on hand. */
    private static int wanted(String resource) {
        return switch (resource) {
            case TownStores.FOOD -> 200;
            case TownStores.WOOD, TownStores.STONE -> 256;
            case TownStores.IRON -> 64;
            case TownStores.TOOLS -> 16;
            default -> 0;
        };
    }
}
