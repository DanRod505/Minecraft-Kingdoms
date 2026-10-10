package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

/**
 * A kingdom. Immutable; {@link KingdomState} replaces the record on every change.
 * Settlements and citizens point back to the kingdom by id instead of being nested, so lookups stay flat.
 * {@code stockpile} is the container its workers deliver to and take materials from.
 */
public record Kingdom(UUID id, String name, UUID ruler, Emblem emblem, UUID capital, long treasury, long foundedAt,
                      Optional<BlockLocation> stockpile) {
    public static final Codec<Kingdom> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codecs.UUID_STRING.fieldOf("id").forGetter(Kingdom::id),
        Codec.STRING.fieldOf("name").forGetter(Kingdom::name),
        Codecs.UUID_STRING.fieldOf("ruler").forGetter(Kingdom::ruler),
        Emblem.CODEC.fieldOf("emblem").forGetter(Kingdom::emblem),
        Codecs.UUID_STRING.fieldOf("capital").forGetter(Kingdom::capital),
        Codec.LONG.fieldOf("treasury").forGetter(Kingdom::treasury),
        Codec.LONG.fieldOf("founded_at").forGetter(Kingdom::foundedAt),
        BlockLocation.CODEC.optionalFieldOf("stockpile").forGetter(Kingdom::stockpile)
    ).apply(i, Kingdom::new));

    public Kingdom withName(String name) {
        return new Kingdom(id, name, ruler, emblem, capital, treasury, foundedAt, stockpile);
    }

    public Kingdom withEmblem(Emblem emblem) {
        return new Kingdom(id, name, ruler, emblem, capital, treasury, foundedAt, stockpile);
    }

    public Kingdom withStockpile(Optional<BlockLocation> stockpile) {
        return new Kingdom(id, name, ruler, emblem, capital, treasury, foundedAt, stockpile);
    }

    public Kingdom withTreasury(long treasury) {
        return new Kingdom(id, name, ruler, emblem, capital, treasury, foundedAt, stockpile);
    }
}
