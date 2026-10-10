package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;
import java.util.UUID;

/**
 * A subject of a kingdom (GDD 4.1). In Phase 1 citizens are data only; the physical NPC that
 * represents one arrives in Phase 2 and will carry this same id.
 */
public record Citizen(UUID id, String name, UUID kingdom, UUID settlement, Profession profession) {
    public enum Profession {
        NONE;

        public static final Codec<Profession> CODEC = Codecs.enumByName(Profession.class);

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<Citizen> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codecs.UUID_STRING.fieldOf("id").forGetter(Citizen::id),
        Codec.STRING.fieldOf("name").forGetter(Citizen::name),
        Codecs.UUID_STRING.fieldOf("kingdom").forGetter(Citizen::kingdom),
        Codecs.UUID_STRING.fieldOf("settlement").forGetter(Citizen::settlement),
        Profession.CODEC.fieldOf("profession").forGetter(Citizen::profession)
    ).apply(i, Citizen::new));
}
