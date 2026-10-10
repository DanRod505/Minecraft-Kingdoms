package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kingdoms.KingdomsMod;
import dev.kingdoms.persistence.SchemaVersion;
import dev.kingdoms.persistence.VersionedCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.UUID;

/**
 * Root of the kingdoms save, stored once per world in {@code <world>/data/kingdoms/world_state.dat}.
 * Holds every kingdom of every dimension, so all lookups go through one server-wide object.
 */
public final class KingdomsWorldData extends SavedData {
    public static final Codec<KingdomsWorldData> CODEC = VersionedCodec.of(RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("world_id").forGetter(KingdomsWorldData::worldId),
        KingdomState.MAP_CODEC.forGetter(KingdomsWorldData::state)
    ).apply(i, KingdomsWorldData::new)));

    // Null data fixer: Forge lets mods keep their own format versioning (see SavedDataStorage.readTagFromDisk).
    public static final SavedDataType<KingdomsWorldData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "world_state"), KingdomsWorldData::new, CODEC, null);

    private final UUID worldId;
    private final KingdomState state;

    private KingdomsWorldData() {
        this(UUID.randomUUID(), new KingdomState());
        setDirty();
    }

    private KingdomsWorldData(UUID worldId, KingdomState state) {
        this.worldId = worldId;
        this.state = state;
    }

    public static KingdomsWorldData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Stable id of this world's kingdoms data; never derived from registration order. */
    public UUID worldId() {
        return worldId;
    }

    public int schemaVersion() {
        return SchemaVersion.CURRENT;
    }

    /** The live state. Callers that change it must call {@link #setDirty()}; {@code KingdomService} does. */
    public KingdomState state() {
        return state;
    }
}
