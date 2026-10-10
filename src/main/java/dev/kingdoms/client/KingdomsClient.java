package dev.kingdoms.client;

import dev.kingdoms.entity.KingdomsEntities;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Client-only wiring. Only loaded when the game runs with a screen. */
public final class KingdomsClient {
    private KingdomsClient() {}

    public static void init() {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event ->
            event.registerEntityRenderer(KingdomsEntities.CITIZEN.get(), CitizenRenderer::new));
    }
}
