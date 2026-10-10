package dev.kingdoms.persistence;

/**
 * Version of every persisted kingdoms document (GDD 14.6, rule 20.7).
 * Bump {@link #CURRENT} only together with a step in {@link Migrations}.
 */
public final class SchemaVersion {
    public static final int CURRENT = 3;
    public static final String FIELD = "schema_version";

    private SchemaVersion() {}
}
