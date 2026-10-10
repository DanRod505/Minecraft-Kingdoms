package dev.kingdoms.persistence;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

/**
 * Wraps a codec for the current schema so that encoded data always carries {@link SchemaVersion#FIELD}
 * and older data is passed through {@link Migrations} before decoding.
 */
public final class VersionedCodec<A> implements Codec<A> {
    private final Codec<A> current;

    private VersionedCodec(Codec<A> current) {
        this.current = current;
    }

    public static <A> Codec<A> of(Codec<A> current) {
        return new VersionedCodec<>(current);
    }

    @Override
    public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
        return current.encode(input, ops, prefix)
            .flatMap(encoded -> ops.mergeToMap(encoded, ops.createString(SchemaVersion.FIELD), ops.createInt(SchemaVersion.CURRENT)));
    }

    @Override
    public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
        var dynamic = new Dynamic<>(ops, input);
        return dynamic.get(SchemaVersion.FIELD).asNumber().flatMap(version -> {
            try {
                return DataResult.success(Migrations.upgrade(dynamic, version.intValue()));
            } catch (RuntimeException e) {
                return DataResult.error(e::getMessage);
            }
        }).flatMap(upgraded -> current.decode(ops, upgraded.getValue()));
    }
}
