package dev.kingdoms.core;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Mod configuration. COMMON is shared by client and server ({@code config/kingdoms-common.toml}).
 * A SERVER spec (per world, synced to clients) is added with the first gameplay setting in Phase 1.
 */
public final class KingdomsConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        var common = new ForgeConfigSpec.Builder();
        common.push("logging");
        DEBUG_LOGGING = common
            .comment("Enable DEBUG level for the kingdoms logger.")
            .translation("kingdoms.config.logging.debug")
            .define("debug", false);
        common.pop();
        COMMON_SPEC = common.build();
    }

    private KingdomsConfig() {}

    public static void registerListeners(BusGroup modBus) {
        ModConfigEvent.Loading.getBus(modBus).addListener(event -> onLoad(event.getConfig()));
        ModConfigEvent.Reloading.getBus(modBus).addListener(event -> onLoad(event.getConfig()));
    }

    private static void onLoad(ModConfig config) {
        if (config.getSpec() == COMMON_SPEC)
            KingdomsLog.setDebug(DEBUG_LOGGING.get());
    }
}
