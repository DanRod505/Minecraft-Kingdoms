package dev.kingdoms.gametest;

import com.mojang.serialization.JsonOps;
import dev.kingdoms.KingdomsMod;
import dev.kingdoms.core.KingdomsRegistry;
import dev.kingdoms.world.KingdomService;
import dev.kingdoms.world.KingdomState;
import dev.kingdoms.world.KingdomsWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game tests, run by {@code ./gradlew runGameTestServer}. Each function here needs a matching
 * {@code data/kingdoms/test_instance/<name>.json}. Forge 65.1.0 ships the {@code @GameTest}
 * annotation but nothing registers annotated methods, so tests use the vanilla registry directly.
 * Tests share one world, so each uses its own ruler and deletes its kingdom at the end.
 */
public final class KingdomsGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, KingdomsMod.MOD_ID);

    public static final RegistryObject<Consumer<GameTestHelper>> WORLD_STATE_ROUND_TRIP =
        TEST_FUNCTIONS.register("world_state_round_trip", () -> KingdomsGameTests::worldStateRoundTrip);
    public static final RegistryObject<Consumer<GameTestHelper>> KINGDOM_SURVIVES_RELOAD =
        TEST_FUNCTIONS.register("kingdom_survives_reload", () -> KingdomsGameTests::kingdomSurvivesReload);
    public static final RegistryObject<Consumer<GameTestHelper>> ROYAL_STANDARD_FOUNDS_KINGDOM =
        TEST_FUNCTIONS.register("royal_standard_founds_kingdom", () -> KingdomsGameTests::royalStandardFoundsKingdom);

    private KingdomsGameTests() {}

    /** Writes the world state to disk and reads it back through a fresh storage, as a world reload would. */
    private static void worldStateRoundTrip(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var saved = KingdomsWorldData.get(server);
        var loaded = reload(helper);
        helper.assertValueEqual(loaded.worldId(), saved.worldId(), "world id after reload");
        helper.assertValueEqual(loaded.schemaVersion(), saved.schemaVersion(), "schema version after reload");
        helper.succeed();
    }

    /** Phase 1 acceptance: founding, then reloading the world keeps borders, citizens and treasury. */
    private static void kingdomSurvivesReload(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var ruler = UUID.randomUUID();
        var kingdom = KingdomService.found(server, ruler, "Test " + ruler.toString().substring(0, 8), helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        try {
            KingdomService.change(server, s -> s.addTreasury(kingdom.id(), 250));
            var loaded = reload(helper).state();
            var live = KingdomService.state(server);

            helper.assertValueEqual(loaded.kingdom(kingdom.id()).orElse(null), live.kingdom(kingdom.id()).orElse(null), "kingdom after reload");
            helper.assertValueEqual(loaded.kingdom(kingdom.id()).map(k -> k.treasury()).orElse(-1L), 250L, "treasury after reload");
            helper.assertValueEqual(loaded.citizensOf(kingdom.id()), live.citizensOf(kingdom.id()), "citizens after reload");
            helper.assertValueEqual(loaded.settlementsOf(kingdom.id()), live.settlementsOf(kingdom.id()), "settlements after reload");
            helper.assertValueEqual(loaded.claimCount(kingdom.id()), live.claimCount(kingdom.id()), "claims after reload");
            helper.assertValueEqual(encode(loaded), encode(live), "whole state after reload");
            helper.assertTrue(loaded.validate().isEmpty(), "reloaded state is valid: " + loaded.validate());
        } finally {
            KingdomService.change(server, s -> { s.delete(kingdom.id()); return null; });
        }
        helper.succeed();
    }

    /** Placing the Royal Standard founds a kingdom ruled by the player, with the capital at the standard. */
    private static void royalStandardFoundsKingdom(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = mockPlayer(helper);
        var floor = new BlockPos(1, 0, 1);
        placeStandard(helper, player, floor);

        var standard = floor.above();
        helper.assertBlockPresent(KingdomsRegistry.ROYAL_STANDARD.get(), standard);
        var kingdom = KingdomService.state(server).kingdomOf(player.getUUID()).orElse(null);
        helper.assertTrue(kingdom != null, "placing the standard founds a kingdom");
        try {
            var capital = KingdomService.state(server).settlement(kingdom.capital()).orElseThrow();
            helper.assertValueEqual(new BlockPos(capital.x(), capital.y(), capital.z()), helper.absolutePos(standard), "capital position");

            // A second standard from the same player is refused and not placed.
            placeStandard(helper, player, BlockPos.ZERO);
            helper.assertBlockNotPresent(KingdomsRegistry.ROYAL_STANDARD.get(), new BlockPos(0, 1, 0));
        } finally {
            KingdomService.change(server, s -> { s.delete(kingdom.id()); return null; });
        }
        helper.succeed();
    }

    /** A survival player that swallows chat, since mock players have no network connection. */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "MockRuler"), ClientInformation.createDefault()) {
            @Override
            public void sendSystemMessage(Component message, boolean overlay) {}

            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        return player;
    }

    /** UseOnContext reads the item from the player's hand, so the stack has to be held, not just passed in. */
    private static void placeStandard(GameTestHelper helper, Player player, BlockPos floor) {
        var stack = new ItemStack(KingdomsRegistry.ROYAL_STANDARD_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.placeAt(player, stack, floor, Direction.UP);
    }

    /** Saves to disk and loads the world state through a new storage, as a server restart would. */
    private static KingdomsWorldData reload(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        KingdomsWorldData.get(server).setDirty();
        server.getDataStorage().saveAndJoin();
        try (var fresh = new SavedDataStorage(server.getWorldPath(LevelResource.DATA), server.getFixerUpper(), server.registryAccess())) {
            var loaded = fresh.get(KingdomsWorldData.TYPE);
            helper.assertTrue(loaded != null, "world_state.dat was not written or could not be read");
            return loaded;
        }
    }

    private static String encode(KingdomState state) {
        return KingdomState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().toString();
    }
}
