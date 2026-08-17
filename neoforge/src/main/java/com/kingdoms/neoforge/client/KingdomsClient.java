package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.KingdomsEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

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
}
