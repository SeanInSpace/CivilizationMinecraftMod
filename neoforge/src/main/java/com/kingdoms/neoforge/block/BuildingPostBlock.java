package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.Settlement;
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
 * The post that marks what a building is for.
 *
 * <p>Every building the town raises gets one, standing on its floor — the same
 * idea colony mods use, where a building is a thing you walk up to and read
 * rather than a name in a menu. Right-click and it tells you which town it
 * belongs to, what it is, and whatever that building actually keeps track of.
 *
 * <p>Posts with orders to give, like the lumber camp, subclass this and add them;
 * see {@link LumberCampBlock}. A plain post is an informational sign that happens
 * to be structural.
 */
public class BuildingPostBlock extends Block {

    private final String role;
    private final String explains;

    /**
     * @param role     what shows up in the report, e.g. {@code "Granary"}
     * @param explains one line on what the building does for the town
     */
    public BuildingPostBlock(String role, String explains, Properties properties) {
        super(properties);
        this.role = role;
        this.explains = explains;
    }

    public String role() {
        return role;
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
                    role + " — belongs to no settlement. Found one nearby first."));
            return InteractionResult.SUCCESS;
        }

        player.sendSystemMessage(Component.literal(
                role + " of " + settlement.name() + " — " + explains));
        player.sendSystemMessage(Component.literal(report(settlement)));
        return InteractionResult.SUCCESS;
    }

    /** What this particular post is worth saying beyond its name. Overridable. */
    protected String report(Settlement settlement) {
        return "Population " + settlement.population()
                + ", " + settlement.buildings().size() + " buildings"
                + ", food " + settlement.foodStock()
                + ", timber " + settlement.woodStock()
                + ", stone " + settlement.stoneStock();
    }

    /**
     * The town this post stands in, or null if it stands outside every claim.
     *
     * <p>Nearest centre wins, but only when the post is actually inside that
     * town's borders plus a little slack — otherwise a post dropped in the wild
     * would report on a settlement half a world away.
     */
    protected static Settlement owningSettlement(SimWorld world, SimPos pos) {
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
        if (nearest != null) {
            long limit = (long) nearest.claimRadius() + 32L;
            if (best <= limit * limit) {
                return nearest;
            }
        }
        return null;
    }
}
