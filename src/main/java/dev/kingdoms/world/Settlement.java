package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;
import java.util.UUID;

/** A settlement. The capital is the settlement a kingdom's {@code capital} points to. */
public record Settlement(UUID id, UUID kingdom, String name, String dimension, int x, int y, int z, Stage stage) {
    /** Political stages from GDD 2.4. Only CAMP exists in Phase 1; growth comes with population. */
    public enum Stage {
        CAMP, VILLAGE, TOWN, CITY;

        public static final Codec<Stage> CODEC = Codecs.enumByName(Stage.class);

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codecs.UUID_STRING.fieldOf("id").forGetter(Settlement::id),
        Codecs.UUID_STRING.fieldOf("kingdom").forGetter(Settlement::kingdom),
        Codec.STRING.fieldOf("name").forGetter(Settlement::name),
        Codec.STRING.fieldOf("dimension").forGetter(Settlement::dimension),
        Codec.INT.fieldOf("x").forGetter(Settlement::x),
        Codec.INT.fieldOf("y").forGetter(Settlement::y),
        Codec.INT.fieldOf("z").forGetter(Settlement::z),
        Stage.CODEC.fieldOf("stage").forGetter(Settlement::stage)
    ).apply(i, Settlement::new));
}
