package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A subject of a kingdom (GDD 4.1). The NPC that represents a citizen uses the same UUID as entity id;
 * {@code materialized} is true once that NPC has been spawned, {@code order} is the order it works on.
 */
public record Citizen(UUID id, String name, UUID kingdom, UUID settlement, Profession profession,
                      Optional<UUID> order, boolean materialized) {
    public enum Profession {
        NONE, FARMER, LUMBERJACK, MINER, CARRIER, BUILDER;

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
        Profession.CODEC.fieldOf("profession").forGetter(Citizen::profession),
        Codecs.UUID_STRING.optionalFieldOf("order").forGetter(Citizen::order),
        Codec.BOOL.fieldOf("materialized").forGetter(Citizen::materialized)
    ).apply(i, Citizen::new));

    public Citizen withProfession(Profession profession) {
        return new Citizen(id, name, kingdom, settlement, profession, Optional.empty(), materialized);
    }

    public Citizen withOrder(Optional<UUID> order) {
        return new Citizen(id, name, kingdom, settlement, profession, order, materialized);
    }

    public Citizen withMaterialized(boolean materialized) {
        return new Citizen(id, name, kingdom, settlement, profession, order, materialized);
    }
}
