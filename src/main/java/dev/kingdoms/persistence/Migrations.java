package dev.kingdoms.persistence;

import com.mojang.serialization.Dynamic;

/**
 * Single entry point for upgrading saved data to {@link SchemaVersion#CURRENT}.
 * Each future version adds one step {@code vN -> vN+1}; steps never skip versions.
 */
public final class Migrations {
    private Migrations() {}

    /** Thrown when a save was written by a newer mod version. Loading it would lose data. */
    public static final class UnsupportedSchemaException extends RuntimeException {
        public UnsupportedSchemaException(int found) {
            super("Saved data has schema v" + found + " but this build only understands up to v" + SchemaVersion.CURRENT);
        }
    }

    public static <T> Dynamic<T> upgrade(Dynamic<T> data, int fromVersion) {
        if (fromVersion < 1)
            throw new IllegalArgumentException("Invalid schema version " + fromVersion);
        if (fromVersion > SchemaVersion.CURRENT)
            throw new UnsupportedSchemaException(fromVersion);

        var current = data;
        for (int version = fromVersion; version < SchemaVersion.CURRENT; version++)
            current = step(current, version);
        return current.set(SchemaVersion.FIELD, current.createInt(SchemaVersion.CURRENT));
    }

    private static <T> Dynamic<T> step(Dynamic<T> data, int fromVersion) {
        // No migrations exist yet: v1 is the first schema.
        throw new IllegalStateException("Missing migration step from v" + fromVersion);
    }
}
