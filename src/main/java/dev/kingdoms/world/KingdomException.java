package dev.kingdoms.world;

/**
 * A rule refused an action. {@link #key()} is a translation key under {@code kingdoms.error.*}
 * so the command layer can show it in the player's language.
 */
public final class KingdomException extends RuntimeException {
    private final String key;
    private final transient Object[] args;

    public KingdomException(String key, Object... args) {
        super(key);
        this.key = key;
        this.args = args;
    }

    public String key() {
        return key;
    }

    public Object[] args() {
        return args.clone();
    }
}
