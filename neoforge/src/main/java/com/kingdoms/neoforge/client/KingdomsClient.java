package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.KingdomsEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only wiring. Referenced from the mod constructor strictly behind a
 * dist check so a dedicated server never loads client classes.
 */
public final class KingdomsClient {

    private KingdomsClient() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(KingdomsEntities.PERSON.get(), PersonRenderer::new);
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        SurveyLines.registerPipelines(event);
    }

    /**
     * Listeners on the game bus rather than the mod bus.
     *
     * <p>Added here rather than from the mod constructor so the mod constructor
     * never names a class that only exists on a client — it calls this one
     * method, behind its dist check, and everything client-side hangs off it.
     */
    public static void listen() {
        NeoForge.EVENT_BUS.addListener(SurveyLines::render);
    }
}
