package dev.kingdoms.persistence;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationsTest {
    private static Dynamic<?> data() {
        var json = new JsonObject();
        json.addProperty("name", "capital");
        return new Dynamic<>(JsonOps.INSTANCE, json);
    }

    @Test
    void currentVersionPassesThroughUnchanged() {
        var upgraded = Migrations.upgrade(data(), SchemaVersion.CURRENT);
        assertEquals("capital", upgraded.get("name").asString("missing"));
        assertEquals(SchemaVersion.CURRENT, upgraded.get(SchemaVersion.FIELD).asInt(-1));
    }

    @Test
    void invalidVersionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Migrations.upgrade(data(), 0));
    }

    @Test
    void futureVersionIsRejected() {
        assertThrows(Migrations.UnsupportedSchemaException.class, () -> Migrations.upgrade(data(), SchemaVersion.CURRENT + 1));
    }
}
