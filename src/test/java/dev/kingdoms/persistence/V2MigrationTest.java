package dev.kingdoms.persistence;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.kingdoms.world.KingdomRules;
import dev.kingdoms.world.KingdomState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2MigrationTest {
    /** A Phase 1 save: the current encoding with every Phase 2 field removed and schema v2. */
    private static JsonObject phase1Save() {
        var state = new KingdomState();
        long[] next = { 1 };
        state.setIdSource(() -> new UUID(9, next[0]++));
        state.found(new UUID(0, 1), "Avalon", "minecraft:overworld", 8, 64, 8, 0, KingdomRules.DEFAULT);
        var json = KingdomState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
        json.remove("orders");
        json.getAsJsonArray("citizens").forEach(c -> {
            c.getAsJsonObject().remove("materialized");
            c.getAsJsonObject().remove("order");
        });
        json.getAsJsonArray("kingdoms").forEach(k -> k.getAsJsonObject().remove("stockpile"));
        json.addProperty(SchemaVersion.FIELD, 2);
        return json;
    }

    @Test
    void v2SaveOpensWithCitizensWaitingForTheirNpcs() {
        var codec = VersionedCodec.of(KingdomState.CODEC);
        var state = codec.parse(JsonOps.INSTANCE, phase1Save()).getOrThrow();
        assertEquals(1, state.kingdoms().size());
        assertEquals(5, state.citizens().size());
        assertTrue(state.citizens().stream().noneMatch(c -> c.materialized()));
        assertTrue(state.citizens().stream().allMatch(c -> c.order().isEmpty()));
        assertTrue(state.kingdoms().getFirst().stockpile().isEmpty());
        assertTrue(state.validate().isEmpty());
    }

    @Test
    void v2MigrationAddsOrdersAndKeepsIds() {
        var save = phase1Save();
        var upgraded = Migrations.upgrade(new Dynamic<>(JsonOps.INSTANCE, save), 2);
        assertEquals(SchemaVersion.CURRENT, upgraded.get(SchemaVersion.FIELD).asInt(-1));
        assertEquals(0, upgraded.get("orders").asList(d -> d).size());
        assertEquals(save.getAsJsonArray("kingdoms").get(0).getAsJsonObject().get("id"),
            upgraded.get("kingdoms").asList(d -> d).getFirst().get("id").result().orElseThrow().getValue());
        assertFalse(upgraded.get("citizens").asList(d -> d.get("materialized").asBoolean(true)).contains(true));
    }
}
