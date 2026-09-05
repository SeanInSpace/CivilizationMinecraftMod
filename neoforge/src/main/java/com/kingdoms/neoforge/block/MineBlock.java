package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.WorkArea;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The mine's control post — the town's stone orders in block form.
 *
 * <p>The lumber camp's counterpart, and deliberately the same controls, so
 * learning one teaches the other:
 *
 * <ul>
 *   <li><strong>Right-click</strong> — grow the working radius one step, wrapping
 *       back to the smallest once past the largest.</li>
 *   <li><strong>Sneak right-click</strong> — move the workings to where you are
 *       standing, so you can point the miners at a particular outcrop.</li>
 * </ul>
 */
public class MineBlock extends BuildingPostBlock {

    public MineBlock(String role, String explains, Properties properties) {
        super(role, explains, properties);
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
                    "This mine belongs to no settlement — found one nearby first."));
            return InteractionResult.SUCCESS;
        }

        WorkArea area = settlement.mineArea();
        if (area == null) {
            area = new WorkArea(NeoForgeWorldBridge.toSimPos(pos), MinePlanner.DEFAULT_RADIUS);
        }

        if (player.isShiftKeyDown()) {
            SimPos here = new SimPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            area = area.withCenter(here);
            player.sendSystemMessage(Component.literal(
                    settlement.name() + "'s miners will now cut around " + here
                            + " (radius " + area.radius() + ")."));
        } else {
            int next = area.radius() + MinePlanner.RADIUS_STEP;
            if (next > MinePlanner.MAX_RADIUS) {
                next = MinePlanner.MIN_RADIUS;
            }
            area = area.withRadius(MinePlanner.clampRadius(next));
            player.sendSystemMessage(Component.literal(
                    settlement.name() + "'s workings now reach " + area.radius()
                            + " blocks from " + area.center()
                            + ".  Sneak-click to move them here."));
        }

        settlement.setMineArea(area);
        player.sendSystemMessage(Component.literal(
                "Stone " + settlement.stoneStock() + " / " + MinePlanner.stoneCapacity(settlement)));
        KingdomsSavedData.get(serverLevel).setDirty();
        return InteractionResult.SUCCESS;
    }
}
