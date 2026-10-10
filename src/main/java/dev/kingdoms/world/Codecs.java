package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;
import java.util.UUID;

/** Small codecs shared by the domain records. */
public final class Codecs {
    /** UUIDs are stored as their canonical string so saves stay readable when debugging. */
    public static final Codec<UUID> UUID_STRING = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Invalid UUID: " + s);
        }
    }, UUID::toString);

    private Codecs() {}

    public static <E extends Enum<E>> Codec<E> enumByName(Class<E> type) {
        return Codec.STRING.comapFlatMap(s -> {
            try {
                return DataResult.success(Enum.valueOf(type, s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + s);
            }
        }, e -> e.name().toLowerCase(Locale.ROOT));
    }
}
