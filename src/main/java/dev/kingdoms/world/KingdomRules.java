package dev.kingdoms.world;

/** Tunable founding and territory limits, read from the server config by the service layer. */
public record KingdomRules(int founderCount, int initialClaimRadius, int maxClaims) {
    public static final KingdomRules DEFAULT = new KingdomRules(5, 1, 64);

    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 32;
}
