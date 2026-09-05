package com.kingdoms.neoforge.item;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.PopulationPlanner;
import com.kingdoms.sim.settlement.RaidPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementEvent;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SettlementNames;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;
import com.kingdoms.sim.settlement.Founding;

/**
 * The legitimate way to start a settlement — no commands, no cheats.
 *
 * <p>Use on the ground: founds a town on that spot, named deterministically from
 * the position, seeded with the founding party and kit described by
 * {@link TownStores}. Consumed on use (outside creative).
 *
 * <p>Sneak-use anywhere: a written report of the nearest settlement — population,
 * jobs, defense, and recent history. The in-game window into the simulation.
 */
public final class FoundingCharterItem extends Item {

    /** New settlements are refused this close to an existing claim's edge. */
    private static final int SPACING = 48;

    // The party's size and its rations sit in TownStores beside the timber and
    // stone, because the four numbers are one kit: they have to be sized against
    // each other, and only there can the simulation's own tests weigh them.

    public FoundingCharterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        SimWorld world = KingdomsMod.simulationFor(level);
        Player player = context.getPlayer();
        if (world == null || player == null) {
            return InteractionResult.FAIL;
        }

        if (context.isSecondaryUseActive()) {
            return report(world, player);
        }
        return found(world, level, player, context);
    }

    private InteractionResult found(SimWorld world, ServerLevel level, Player player, UseOnContext context) {
        BlockPos clicked = context.getClickedPos().above();
        SimPos site = NeoForgeWorldBridge.toSimPos(clicked);

        Settlement neighbor = tooClose(world, site);
        if (neighbor != null) {
            player.sendOverlayMessage(Component.literal(
                    "Too close to " + neighbor.name() + " — settle further away."));
            return InteractionResult.FAIL;
        }

        String name = SettlementNames.forPosition(site);
        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), name, "kingdoms:norman");
        // The party itself is simulation, not right-clicking — see Founding,
        // which /civ found now comes through as well, so a scripted run founds
        // what a player's charter founds.
        // A person chose this spot, so the ground gets a vote and not a veto:
        // a dozen blocks is enough to step off a cliff edge and not enough to
        // overrule somebody who wanted the view.
        site = Founding.bestSiteNear(site, Founding.CHARTER_REACH,
                KingdomsMod.simulationFor(level).bridge());
        Settlement settlement = Founding.party(site, name);
        kingdom.addSettlement(settlement);
        settlement.logEvent(world.stepsElapsed(), name + " was founded");

        KingdomsSavedData.get(level).addKingdom(kingdom);
        world.addKingdom(kingdom);

        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        player.sendSystemMessage(Component.literal(
                name + " is founded! " + TownStores.FOUNDING_SETTLERS
                        + " settlers begin work — they will build, marry, and grow. "
                        + "Sneak-use a charter to check on them."));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult report(SimWorld world, Player player) {
        Settlement nearest = null;
        long best = Long.MAX_VALUE;
        SimPos here = new SimPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement s : kingdom.settlements()) {
                long d = s.center().horizontalDistanceSq(here);
                if (d < best) {
                    best = d;
                    nearest = s;
                }
            }
        }
        if (nearest == null) {
            player.sendSystemMessage(Component.literal(
                    "No settlements anywhere yet. Use the charter on open ground to found one."));
            return InteractionResult.SUCCESS;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(nearest.name()).append(" ===");
        sb.append("\nPopulation ").append(nearest.population())
                .append(" (beds for ").append(PopulationPlanner.totalHousingCapacity(nearest)).append(")")
                .append(", ").append(nearest.households().size()).append(" families");
        sb.append("\nDefense ").append(RaidPlanner.defensePower(nearest))
                .append(", threat ").append(nearest.threatLevel());
        sb.append("\nGranary ").append(nearest.foodStock())
                .append("/").append(FoodPlanner.granaryCapacity(nearest));
        sb.append("\nBuildings: ").append(nearest.buildings().size());
        if (!nearest.buildQueue().isEmpty()) {
            sb.append(" (building ").append(nearest.buildQueue().getFirst().blueprintId())
                    .append(" ").append(Math.round(nearest.buildQueue().getFirst().completionFraction() * 100))
                    .append("%)");
        }
        List<SettlementEvent> events = nearest.events();
        if (!events.isEmpty()) {
            sb.append("\nRecent history:");
            for (int i = Math.max(0, events.size() - 3); i < events.size(); i++) {
                sb.append("\n  ").append(events.get(i).message());
            }
        }
        player.sendSystemMessage(Component.literal(sb.toString()));
        return InteractionResult.SUCCESS;
    }

    private static Settlement tooClose(SimWorld world, SimPos site) {
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement s : kingdom.settlements()) {
                long limit = (long) s.claimRadius() + SPACING;
                if (s.center().horizontalDistanceSq(site) < limit * limit) {
                    return s;
                }
            }
        }
        return null;
    }
}
