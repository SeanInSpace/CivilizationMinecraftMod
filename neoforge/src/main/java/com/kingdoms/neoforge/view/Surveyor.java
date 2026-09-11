package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.KingdomsItems;
import com.kingdoms.neoforge.net.SurveyPayload;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who is holding a surveyor's lamp, and what they are being shown.
 *
 * <p>The lamp used to draw itself: the settlement manager spat particles along
 * every edge on its own beat, which is a pass every twenty ticks and a survey
 * every fourth pass. Four seconds between repaints of something made of sparks
 * that live for one is a flicker, not a drawing.
 *
 * <p>Now the shape is sent and the client draws it every frame. This is the
 * sending half: a survey a second while the lamp is out, one the instant it
 * appears, and an empty one the instant it goes away so the lines go out with
 * it. A vanilla client gets nothing, which is the price of lines that hold
 * still — the optional channel was always going to decide this.
 */
public final class Surveyor {

    /** Ticks between surveys while the lamp is in hand. */
    public static final int SEND_INTERVAL = 20;

    /** Who was holding a lamp last tick, so picking one up is noticed at once. */
    private static final Set<UUID> HOLDING = new HashSet<>();

    private Surveyor() {
    }

    /** Forgets who was holding what. For a world going away. */
    public static void forget() {
        HOLDING.clear();
    }

    /**
     * One tick's worth of surveying.
     *
     * <p>Runs every tick and nearly always does nothing but read two item
     * stacks per player: the survey itself is built on the interval, and off it
     * the only thing that can happen is somebody's hand changing. That is what
     * makes the lamp answer the moment it is drawn rather than up to a second
     * later, which for an instrument you hold up to look at something is the
     * difference between a tool and a delay.
     */
    public static void tick(ServerLevel level, SimWorld world, long tickCounter) {
        boolean due = tickCounter % SEND_INTERVAL == 0;
        for (ServerPlayer player : level.players()) {
            if (!player.connection.hasChannel(SurveyPayload.TYPE)) {
                // A vanilla client, or one running an older Kingdoms. Sending
                // anyway is not a dropped packet — the channel check throws, and
                // this runs from the server tick every second, so one player
                // without the mod would take the server down with it.
                continue;
            }
            boolean holding = holdingLamp(player);
            boolean was = HOLDING.contains(player.getUUID());
            if (holding) {
                HOLDING.add(player.getUUID());
                if (due || !was) {
                    PacketDistributor.sendToPlayer(player, survey(level, world, player));
                }
            } else if (was) {
                HOLDING.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, SurveyPayload.NONE);
            }
        }
    }

    private static boolean holdingLamp(ServerPlayer player) {
        return player.getMainHandItem().is(KingdomsItems.SURVEYORS_LAMP.get())
                || player.getOffhandItem().is(KingdomsItems.SURVEYORS_LAMP.get());
    }

    /**
     * The nearest settlement's survey, or an empty one when there is no town in
     * reach.
     *
     * <p>Nearest rather than every town in range. Two settlements' streets drawn
     * over each other in the same white is a worse picture than one town's, and
     * a survey is a document about a place.
     */
    private static SurveyPayload survey(ServerLevel level, SimWorld world, ServerPlayer player) {
        SimPos eye = new SimPos(
                (int) Math.floor(player.getX()),
                (int) Math.floor(player.getY()),
                (int) Math.floor(player.getZ()));
        Settlement nearest = null;
        double closest = Double.MAX_VALUE;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                SimPos center = settlement.center();
                double reach = settlement.claimRadius() + SurveyPayload.RANGE;
                double distance = Math.hypot(eye.x() - center.x(), eye.z() - center.z());
                if (distance > reach || distance >= closest) {
                    continue;
                }
                closest = distance;
                nearest = settlement;
            }
        }
        if (nearest == null) {
            return SurveyPayload.NONE;
        }
        measureUnknowns(level, nearest, eye);
        return SurveyPayload.of(nearest, eye,
                (x, z) -> BlueprintPlacer.groundLevel(level, x, z));
    }

    /**
     * Gives a size to any building near enough to be drawn that never recorded
     * one.
     *
     * <p>Buildings raised before footprints were kept have no size, and a survey
     * leaves those out rather than guessing a square. Measuring one needs the
     * world, so it happens here, once, when somebody is standing close enough to
     * look — which means a world that predates the feature lights up the first
     * time a lamp is carried through it instead of staying dark forever.
     */
    private static void measureUnknowns(ServerLevel level, Settlement settlement, SimPos eye) {
        boolean measured = false;
        for (Building building : settlement.buildings()) {
            if (building.footprint().isKnown() || !near(eye, building.origin())) {
                continue;
            }
            Footprint found = BlueprintPlacer.measure(level, building.blueprintId(),
                    new BlockPos(building.origin().x(), building.origin().y(),
                            building.origin().z()));
            if (!found.isKnown()) {
                continue;
            }
            building.setFootprint(found);
            measured = true;
        }
        if (measured) {
            KingdomsSavedData.get(level).setDirty();
        }
    }

    private static boolean near(SimPos eye, SimPos at) {
        double dx = eye.x() - at.x();
        double dz = eye.z() - at.z();
        return dx * dx + dz * dz <= SurveyPayload.RANGE * SurveyPayload.RANGE;
    }
}
