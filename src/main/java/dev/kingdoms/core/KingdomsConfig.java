package dev.kingdoms.core;

import dev.kingdoms.world.KingdomRules;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Mod configuration. COMMON is shared by client and server ({@code config/kingdoms-common.toml});
 * SERVER is per world ({@code <world>/serverconfig/kingdoms-server.toml}) and holds gameplay rules.
 */
public final class KingdomsConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.IntValue FOUNDER_COUNT;
    public static final ForgeConfigSpec.IntValue INITIAL_CLAIM_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_CLAIMS;

    static {
        var common = new ForgeConfigSpec.Builder();
        common.push("logging");
        DEBUG_LOGGING = common
            .comment("Enable DEBUG level for the kingdoms logger.")
            .translation("kingdoms.config.logging.debug")
            .define("debug", false);
        common.pop();
        COMMON_SPEC = common.build();

        var server = new ForgeConfigSpec.Builder();
        server.push("founding");
        FOUNDER_COUNT = server
            .comment("Colonists that arrive with a new kingdom (GDD 2.4: a camp has 5-10).")
            .translation("kingdoms.config.founding.founder_count")
            .defineInRange("founderCount", KingdomRules.DEFAULT.founderCount(), 1, 10);
        INITIAL_CLAIM_RADIUS = server
            .comment("Chunks claimed around the capital when founding. 1 means a 3x3 area.")
            .translation("kingdoms.config.founding.initial_claim_radius")
            .defineInRange("initialClaimRadius", KingdomRules.DEFAULT.initialClaimRadius(), 0, 4);
        server.pop();
        server.push("territory");
        MAX_CLAIMS = server
            .comment("Maximum chunks a kingdom can hold.")
            .translation("kingdoms.config.territory.max_claims")
            .defineInRange("maxClaims", KingdomRules.DEFAULT.maxClaims(), 1, 4096);
        server.pop();
        SERVER_SPEC = server.build();
    }

    /** Current rules. Falls back to defaults before the world's server config is loaded. */
    public static KingdomRules rules() {
        if (!SERVER_SPEC.isLoaded())
            return KingdomRules.DEFAULT;
        return new KingdomRules(FOUNDER_COUNT.get(), INITIAL_CLAIM_RADIUS.get(), MAX_CLAIMS.get());
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
