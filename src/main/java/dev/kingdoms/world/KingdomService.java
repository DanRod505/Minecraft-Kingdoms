package dev.kingdoms.world;

import dev.kingdoms.core.KingdomsConfig;
import dev.kingdoms.core.KingdomsLog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Function;

/**
 * The only way game code changes kingdoms state (rule 20.8: the server is the authority).
 * Wraps {@link KingdomState} with the world's save data, marks it dirty and logs every change.
 */
public final class KingdomService {
    private KingdomService() {}

    public static KingdomState state(MinecraftServer server) {
        return KingdomsWorldData.get(server).state();
    }

    /** Applies a change and marks the save dirty. A {@link KingdomException} leaves the state untouched. */
    public static <T> T change(MinecraftServer server, Function<KingdomState, T> action) {
        var data = KingdomsWorldData.get(server);
        var result = action.apply(data.state());
        data.setDirty();
        return result;
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    /** Checks the founding rules without changing anything, so a refused standard is never placed. */
    public static void checkCanFound(ServerPlayer player, String name, BlockPos pos) {
        state(player.level().getServer()).checkFound(player.getUUID(), name, dimension(player.level()), pos.getX(), pos.getZ());
    }

    public static Kingdom found(ServerPlayer player, String name, BlockPos pos) {
        return found(player.level().getServer(), player.getUUID(), name, player.level(), pos);
    }

    public static Kingdom found(MinecraftServer server, UUID ruler, String name, Level level, BlockPos pos) {
        var kingdom = change(server, s -> s.found(ruler, name, dimension(level), pos.getX(), pos.getY(), pos.getZ(),
            level.getGameTime(), KingdomsConfig.rules()));
        KingdomsLog.LOGGER.info("Kingdom {} ({}) founded by {} at {} in {}", kingdom.name(), kingdom.id(), ruler, pos.toShortString(), dimension(level));
        return kingdom;
    }

    /** A refused action as a chat message in the player's language. */
    public static Component message(KingdomException e) {
        return Component.translatable(e.key(), e.args());
    }

    public static String rulerName(MinecraftServer server, UUID ruler) {
        var online = server.getPlayerList().getPlayer(ruler);
        return online != null ? online.getGameProfile().name() : ruler.toString();
    }
}
