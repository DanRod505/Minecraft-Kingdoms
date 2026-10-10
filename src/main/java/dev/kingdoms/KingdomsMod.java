package dev.kingdoms;

import dev.kingdoms.command.KingdomCommands;
import dev.kingdoms.core.KingdomsConfig;
import dev.kingdoms.core.KingdomsRegistry;
import dev.kingdoms.core.KingdomsLog;
import dev.kingdoms.gametest.KingdomsGameTests;
import dev.kingdoms.network.KingdomsNetwork;
import dev.kingdoms.persistence.SchemaVersion;
import dev.kingdoms.world.KingdomsWorldData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(KingdomsMod.MOD_ID)
public final class KingdomsMod {
    public static final String MOD_ID = "kingdoms";

    public KingdomsMod(FMLJavaModLoadingContext context) {
        var modBus = context.getModBusGroup();

        context.registerConfig(ModConfig.Type.COMMON, KingdomsConfig.COMMON_SPEC);
        context.registerConfig(ModConfig.Type.SERVER, KingdomsConfig.SERVER_SPEC);
        KingdomsConfig.registerListeners(modBus);

        KingdomsRegistry.register(modBus);
        KingdomsGameTests.TEST_FUNCTIONS.register(modBus);
        RegisterCommandsEvent.BUS.addListener(event -> KingdomCommands.register(event.getDispatcher()));

        FMLCommonSetupEvent.getBus(modBus).addListener(KingdomsMod::commonSetup);
        ServerStartedEvent.BUS.addListener(KingdomsMod::serverStarted);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        KingdomsNetwork.init();
        KingdomsLog.LOGGER.info("Common setup done (schema v{}, protocol v{})",
            SchemaVersion.CURRENT, KingdomsNetwork.PROTOCOL_VERSION);
    }

    private static void serverStarted(ServerStartedEvent event) {
        // Loads (or creates) the per-world state so a broken save is reported at startup, not later.
        var data = KingdomsWorldData.get(event.getServer());
        KingdomsLog.LOGGER.info("World state ready: world id {}, schema v{}, {} kingdom(s)",
            data.worldId(), data.schemaVersion(), data.state().kingdoms().size());
        var problems = data.state().validate();
        problems.forEach(problem -> KingdomsLog.LOGGER.warn("Invalid kingdoms state: {}", problem));
    }
}
