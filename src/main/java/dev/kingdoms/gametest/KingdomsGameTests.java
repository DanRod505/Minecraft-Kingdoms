package dev.kingdoms.gametest;

import dev.kingdoms.KingdomsMod;
import dev.kingdoms.world.KingdomsWorldData;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/**
 * Game tests, run by {@code ./gradlew runGameTestServer}. Each function here needs a matching
 * {@code data/kingdoms/test_instance/<name>.json}. Forge 65.1.0 ships the {@code @GameTest}
 * annotation but nothing registers annotated methods, so tests use the vanilla registry directly.
 */
public final class KingdomsGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, KingdomsMod.MOD_ID);

    public static final RegistryObject<Consumer<GameTestHelper>> WORLD_STATE_ROUND_TRIP =
        TEST_FUNCTIONS.register("world_state_round_trip", () -> KingdomsGameTests::worldStateRoundTrip);

    private KingdomsGameTests() {}

    /** Writes the world state to disk and reads it back through a fresh storage, as a world reload would. */
    private static void worldStateRoundTrip(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var saved = KingdomsWorldData.get(server);
        server.getDataStorage().saveAndJoin();

        try (var fresh = new SavedDataStorage(server.getWorldPath(LevelResource.DATA), server.getFixerUpper(), server.registryAccess())) {
            var loaded = fresh.get(KingdomsWorldData.TYPE);
            helper.assertTrue(loaded != null, "world_state.dat was not written or could not be read");
            helper.assertValueEqual(loaded.worldId(), saved.worldId(), "world id after reload");
            helper.assertValueEqual(loaded.schemaVersion(), saved.schemaVersion(), "schema version after reload");
        }
        helper.succeed();
    }
}
