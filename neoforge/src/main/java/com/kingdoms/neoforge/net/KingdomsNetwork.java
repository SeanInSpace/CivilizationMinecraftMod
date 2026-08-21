package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's network channel.
 *
 * <p>Optional on purpose, like Keystone's. A vanilla client can join a server
 * running Kingdoms and simply not have the town overview; nothing about the
 * simulation depends on the channel existing.
 */
public final class KingdomsNetwork {

    private static final String VERSION = "1";

    private KingdomsNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToClient(
                TownOverviewPayload.TYPE,
                TownOverviewPayload.STREAM_CODEC,
                TownOverviewPayload::handle);
        registrar.playToClient(
                TownMapPayload.TYPE,
                TownMapPayload.STREAM_CODEC,
                TownMapPayload::handle);
        KingdomsMod.LOGGER.debug("Kingdoms network channel registered");
    }
}
