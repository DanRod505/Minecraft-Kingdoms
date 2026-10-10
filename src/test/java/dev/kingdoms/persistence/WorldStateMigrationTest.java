package dev.kingdoms.persistence;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.kingdoms.world.KingdomState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStateMigrationTest {
    /** The exact shape Phase 0 wrote (world_id as int array, schema v1). */
    private static final String V1 = "{\"world_id\":[1,2,3,4],\"schema_version\":1}";

    @Test
    void v1SaveOpensAsEmptyV2State() {
        var codec = VersionedCodec.of(KingdomState.CODEC);
        var state = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(V1)).getOrThrow();
        assertTrue(state.kingdoms().isEmpty());
        assertTrue(state.validate().isEmpty());
    }

    @Test
    void v1MigrationKeepsOtherFields() {
        var upgraded = Migrations.upgrade(new com.mojang.serialization.Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(V1)), 1);
        assertEquals(4, upgraded.get("world_id").asIntStream().count());
        assertEquals(SchemaVersion.CURRENT, upgraded.get(SchemaVersion.FIELD).asInt(-1));
        assertEquals(0, upgraded.get("claims").asList(d -> d).size());
    }
}
