package dev.kingdoms.persistence;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedCodecTest {
    record Sample(String name, int value) {
        static final Codec<Sample> CODEC = VersionedCodec.of(RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Sample::name),
            Codec.INT.fieldOf("value").forGetter(Sample::value)
        ).apply(i, Sample::new)));
    }

    @Test
    void encodeAddsCurrentSchemaVersion() {
        var json = Sample.CODEC.encodeStart(JsonOps.INSTANCE, new Sample("capital", 7)).getOrThrow().getAsJsonObject();
        assertEquals(SchemaVersion.CURRENT, json.get(SchemaVersion.FIELD).getAsInt());
    }

    @Test
    void roundTripPreservesData() {
        var original = new Sample("capital", 7);
        var json = Sample.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, Sample.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void missingVersionIsRejected() {
        var json = new JsonObject();
        json.addProperty("name", "capital");
        json.addProperty("value", 7);
        assertTrue(Sample.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }

    @Test
    void newerVersionIsRejectedInsteadOfLoaded() {
        var json = Sample.CODEC.encodeStart(JsonOps.INSTANCE, new Sample("capital", 7)).getOrThrow().getAsJsonObject();
        json.addProperty(SchemaVersion.FIELD, SchemaVersion.CURRENT + 1);
        var result = Sample.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("v" + (SchemaVersion.CURRENT + 1)));
    }
}
