package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A block position in a dimension, without Minecraft classes. */
public record BlockLocation(String dimension, int x, int y, int z) {
    public static final Codec<BlockLocation> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("dimension").forGetter(BlockLocation::dimension),
        Codec.INT.fieldOf("x").forGetter(BlockLocation::x),
        Codec.INT.fieldOf("y").forGetter(BlockLocation::y),
        Codec.INT.fieldOf("z").forGetter(BlockLocation::z)
    ).apply(i, BlockLocation::new));
}
