package dev.kingdoms.territory;

/** Packs chunk coordinates into a long, using the same layout as vanilla {@code ChunkPos.pack}. */
public final class ChunkKey {
    private ChunkKey() {}

    public static long of(int x, int z) {
        return (x & 0xFFFFFFFFL) | (z & 0xFFFFFFFFL) << 32;
    }

    public static int x(long key) {
        return (int) key;
    }

    public static int z(long key) {
        return (int) (key >>> 32);
    }

    public static long[] neighbours(long key) {
        int x = x(key), z = z(key);
        return new long[] { of(x + 1, z), of(x - 1, z), of(x, z + 1), of(x, z - 1) };
    }
}
