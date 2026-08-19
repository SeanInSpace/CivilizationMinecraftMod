package com.keystone.net;

import com.keystone.KeystoneMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The one message Keystone sends: a name for the region a wand has marked.
 *
 * <p>Optional on purpose. A vanilla client can join a server running Keystone
 * and simply not have the wand's screen; nothing about the world depends on the
 * channel existing.
 */
public final class KeystoneNetwork {

    private static final String VERSION = "1";

    private KeystoneNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToServer(
                SaveBlueprintPayload.TYPE,
                SaveBlueprintPayload.STREAM_CODEC,
                SaveBlueprintPayload::handle);
        KeystoneMod.LOG.debug("Keystone network channel registered");
    }
}
