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

    /**
     * Bumped whenever a payload's shape changes — "2" when the town overview
     * gained its distress reading, "3" when a settler's pockets got a screen of
     * their own, "4" when the market got a board and, with it, the first thing
     * this mod's client ever says back. An optional channel that silently
     * mismatches does not refuse; it decodes the new bytes with the old codec
     * and shows nonsense, which is worse than not having the screen at all.
     */
    private static final String VERSION = "4";

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
        registrar.playToClient(
                SupplyPayload.TYPE,
                SupplyPayload.STREAM_CODEC,
                SupplyPayload::handle);
        registrar.playToClient(
                PersonInventoryPayload.TYPE,
                PersonInventoryPayload.STREAM_CODEC,
                PersonInventoryPayload::handle);
        registrar.playToClient(
                MarketPayload.TYPE,
                MarketPayload.STREAM_CODEC,
                MarketPayload::handle);
        // The only thing that travels the other way. Everything else this mod
        // sends is a report; a market is the one screen a player can press.
        registrar.playToServer(
                MarketDealPayload.TYPE,
                MarketDealPayload.STREAM_CODEC,
                MarketDealPayload::handle);
        KingdomsMod.LOGGER.debug("Kingdoms network channel registered");
    }
}
