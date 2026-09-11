package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Finding a town to draw, and putting its map on somebody's screen.
 *
 * <p>Four surfaces open this map — the hall post, a town map in hand, the
 * wayfinder, and {@code /civ map} — and a fifth asks it to refresh. All five had
 * the same twelve lines of "walk every kingdom, keep the nearest" in them, which
 * is the kind of duplication that does not announce itself when one copy learns
 * something the others do not.
 */
public final class TownMaps {

    private TownMaps() {
    }

    /**
     * The nearest settlement in this world, however far away it is.
     *
     * <p>Null only when there are no settlements at all. Callers that want one
     * within reach say so themselves, because "nearest" and "near" are different
     * questions and the answer to the first is what you need to explain the
     * second.
     */
    public static Settlement nearest(SimWorld world, SimPos here) {
        Settlement nearest = null;
        long best = Long.MAX_VALUE;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                long distance = settlement.center().horizontalDistanceSq(here);
                if (distance < best) {
                    best = distance;
                    nearest = settlement;
                }
            }
        }
        return nearest;
    }

    /**
     * The settlement whose claim this position falls inside, or null.
     *
     * <p>What "inside a town" means for the wayfinder: the borders the charter
     * draws as a ring of sparks, and nothing beyond them.
     */
    public static Settlement containing(SimWorld world, SimPos here) {
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                if (settlement.contains(here)) {
                    return settlement;
                }
            }
        }
        return null;
    }

    /**
     * The settlement whose center is this one, within a couple of blocks.
     *
     * <p>Used to turn a client's claim about which town it is showing back into
     * a settlement the server actually has. Matched flat: a center's height is
     * the ground it was founded on and is not part of its identity.
     */
    public static Settlement matching(SimWorld world, BlockPos center, int slack) {
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                SimPos at = settlement.center();
                if (Math.abs(at.x() - center.getX()) <= slack
                        && Math.abs(at.z() - center.getZ()) <= slack) {
                    return settlement;
                }
            }
        }
        return null;
    }

    /** Whether a player is close enough to this town to go on being shown it. */
    public static boolean within(Settlement settlement, ServerPlayer player, int reach) {
        double dx = settlement.center().x() - player.getX();
        double dz = settlement.center().z() - player.getZ();
        return dx * dx + dz * dz <= (double) reach * reach;
    }

    /**
     * Puts a town's map on a player's screen.
     *
     * <p>Silently does nothing for a player whose client has no channel — a
     * vanilla client on a Kingdoms server, or one running an older build.
     * Sending anyway is not a dropped packet: the channel check throws.
     *
     * @return whether anything was sent, so a caller can say why not
     */
    public static boolean open(ServerPlayer player, Settlement settlement) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        SimWorld world = KingdomsMod.simulationFor(level);
        if (world == null || !player.connection.hasChannel(TownMapPayload.TYPE)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player,
                TownMapPayload.of(settlement, world.stepsElapsed(), true));
        return true;
    }
}
