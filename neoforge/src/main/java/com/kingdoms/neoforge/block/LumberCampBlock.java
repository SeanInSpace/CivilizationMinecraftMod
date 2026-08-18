package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.WorkArea;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The lumber camp's control post — the town's woodland orders in block form.
 *
 * <p>Modelled on the hut blocks players know from colony mods: the building is a
 * thing in the world you walk up to and instruct, not a config file.
 *
 * <ul>
 *   <li><strong>Right-click</strong> — grow the working radius one step, wrapping
 *       back to the smallest once past the largest.</li>
 *   <li><strong>Sneak right-click</strong> — move the working area to where you
 *       are standing, so you can point the camp at a particular wood.</li>
 * </ul>
 *
 * <p>Every interaction reports the resulting orders in chat, so the block teaches
 * its own controls without a screen.
 */
public class LumberCampBlock extends Block {

    public LumberCampBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        SimWorld world = KingdomsMod.simulationFor(serverLevel);
        if (world == null) {
            return InteractionResult.FAIL;
        }
        Settlement settlement = owningSettlement(world, NeoForgeWorldBridge.toSimPos(pos));
        if (settlement == null) {
            player.sendSystemMessage(Component.literal(
                    "This camp belongs to no settlement — found one nearby first."));
            return InteractionResult.SUCCESS;
        }

        WorkArea area = settlement.lumberArea();
        if (area == null) {
            area = new WorkArea(NeoForgeWorldBridge.toSimPos(pos), LumberPlanner.DEFAULT_RADIUS);
        }

        if (player.isShiftKeyDown()) {
            SimPos here = new SimPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            area = area.withCentre(here);
            player.sendSystemMessage(Component.literal(
                    settlement.name() + "'s lumberjacks will now work around " + here
                            + " (radius " + area.radius() + ")."));
        } else {
            int next = area.radius() + LumberPlanner.RADIUS_STEP;
            if (next > LumberPlanner.MAX_RADIUS) {
                next = LumberPlanner.MIN_RADIUS;
            }
            area = area.withRadius(LumberPlanner.clampRadius(next));
            player.sendSystemMessage(Component.literal(
                    settlement.name() + "'s woodland now reaches " + area.radius()
                            + " blocks from " + area.centre()
                            + ".  Sneak-click to move it here."));
        }

        settlement.setLumberArea(area);
        KingdomsSavedData.get(serverLevel).setDirty();
        return InteractionResult.SUCCESS;
    }

    private static Settlement owningSettlement(SimWorld world, SimPos pos) {
        Settlement nearest = null;
        long best = Long.MAX_VALUE;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                long distance = settlement.centre().horizontalDistanceSq(pos);
                if (distance < best) {
                    best = distance;
                    nearest = settlement;
                }
            }
        }
        // Only claim it if the post actually stands within the town's borders.
        if (nearest != null) {
            long limit = (long) nearest.claimRadius() + 32L;
            if (best <= limit * limit) {
                return nearest;
            }
        }
        return null;
    }
}
