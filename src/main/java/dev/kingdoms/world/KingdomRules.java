package dev.kingdoms.world;

/** Tunable founding, territory and order limits, read from the server config by the service layer. */
public record KingdomRules(int founderCount, int initialClaimRadius, int maxClaims, int maxOrderVolume) {
    public static final KingdomRules DEFAULT = new KingdomRules(5, 1, 64, 32768);

    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 32;
}
