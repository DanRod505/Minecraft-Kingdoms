package dev.kingdoms.entity;

import dev.kingdoms.core.KingdomsLog;
import dev.kingdoms.world.Citizen;
import dev.kingdoms.world.KingdomService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Turns citizen records into NPCs (GDD 4.1). A citizen is spawned once, at its settlement, when that
 * place is loaded; after that the NPC lives in the world save like any mob. Kingdoms from Phase 1 get
 * their NPCs the first time their capital loads.
 */
public final class Materializer {
    public static final int INTERVAL = 100;

    private Materializer() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL == 0)
            materialize(server, null);
    }

    /** Spawns every pending citizen whose home is loaded, or only those of one kingdom. Returns how many. */
    public static int materialize(MinecraftServer server, UUID kingdom) {
        var state = KingdomService.state(server);
        var pending = new ArrayList<Citizen>();
        for (var c : state.citizens())
            if (!c.materialized() && (kingdom == null || c.kingdom().equals(kingdom)))
                pending.add(c);
        int spawned = 0;
        int index = 0;
        for (var citizen : pending) {
            var home = state.settlement(citizen.settlement()).orElse(null);
            if (home == null)
                continue;
            var id = Identifier.tryParse(home.dimension());
            var level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (level == null)
                continue;
            int offset = index++ % 5 - 2;
            var column = new BlockPos(home.x() + offset, home.y(), home.z() + 2);
            if (!level.isPositionEntityTicking(column))
                continue;
            if (level.getEntity(citizen.id()) == null) {
                var ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                var npc = KingdomsEntities.CITIZEN.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (npc == null)
                    continue;
                npc.setUUID(citizen.id());
                npc.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
                if (!level.addFreshEntity(npc))
                    continue;
                spawned++;
                KingdomsLog.LOGGER.info("Citizen {} ({}) appeared at {}", citizen.name(), citizen.id(), ground.toShortString());
            }
            KingdomService.change(server, s -> {
                s.setMaterialized(citizen.id(), true);
                return null;
            });
        }
        return spawned;
    }
}
