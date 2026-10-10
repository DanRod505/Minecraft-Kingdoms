package dev.kingdoms.entity;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which worker holds which block of an order, so two workers never take the same target (GDD 3.5).
 * Kept in memory only: after a restart every reservation is gone and workers simply pick targets again,
 * which is the reconciliation GDD 14.6 asks for.
 */
public final class Reservations {
    private static final Map<UUID, Map<BlockPos, UUID>> BY_ORDER = new HashMap<>();

    private Reservations() {}

    public static synchronized boolean reserve(UUID order, BlockPos pos, UUID worker) {
        var holder = BY_ORDER.computeIfAbsent(order, o -> new HashMap<>()).putIfAbsent(pos.immutable(), worker);
        return holder == null || holder.equals(worker);
    }

    public static synchronized boolean heldByOther(UUID order, BlockPos pos, UUID worker) {
        var map = BY_ORDER.get(order);
        if (map == null)
            return false;
        var holder = map.get(pos);
        return holder != null && !holder.equals(worker);
    }

    public static synchronized boolean othersWorking(UUID order, UUID worker) {
        var map = BY_ORDER.get(order);
        return map != null && map.values().stream().anyMatch(w -> !w.equals(worker));
    }

    public static synchronized void release(UUID order, BlockPos pos, UUID worker) {
        var map = BY_ORDER.get(order);
        if (map != null && worker.equals(map.get(pos)))
            map.remove(pos);
    }

    public static synchronized void releaseWorker(UUID worker) {
        BY_ORDER.values().forEach(map -> map.values().removeIf(worker::equals));
        BY_ORDER.values().removeIf(Map::isEmpty);
    }

    public static synchronized void clear() {
        BY_ORDER.clear();
    }
}
