package dev.kingdoms.orders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** An inclusive block box in one dimension. Built from any two corners. */
public record Area(String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final Codec<Area> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("dimension").forGetter(Area::dimension),
        Codec.INT.fieldOf("min_x").forGetter(Area::minX),
        Codec.INT.fieldOf("min_y").forGetter(Area::minY),
        Codec.INT.fieldOf("min_z").forGetter(Area::minZ),
        Codec.INT.fieldOf("max_x").forGetter(Area::maxX),
        Codec.INT.fieldOf("max_y").forGetter(Area::maxY),
        Codec.INT.fieldOf("max_z").forGetter(Area::maxZ)
    ).apply(i, Area::new));

    public static Area of(String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Area(dimension, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean contains(String dimension, int x, int y, int z) {
        return this.dimension.equals(dimension) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int centerX() {
        return (minX + maxX) / 2;
    }

    public int centerZ() {
        return (minZ + maxZ) / 2;
    }
}
