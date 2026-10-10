package dev.kingdoms.item;

import dev.kingdoms.orders.Area;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The two corners each player marked with the Royal Scepter. Memory only: a selection is a draft. */
public final class Selections {
    private record Selection(String dimension, BlockPos first, BlockPos second) {}

    private static final Map<UUID, Selection> BY_PLAYER = new HashMap<>();

    private Selections() {}

    public static synchronized void set(UUID player, String dimension, BlockPos pos, boolean second) {
        var old = BY_PLAYER.get(player);
        if (old != null && !old.dimension().equals(dimension))
            old = null;
        var first = second ? (old == null ? null : old.first()) : pos.immutable();
        var other = second ? pos.immutable() : (old == null ? null : old.second());
        BY_PLAYER.put(player, new Selection(dimension, first, other));
    }

    /** The marked area, once both corners are set. */
    public static synchronized Optional<Area> area(UUID player) {
        var s = BY_PLAYER.get(player);
        if (s == null || s.first() == null || s.second() == null)
            return Optional.empty();
        return Optional.of(Area.of(s.dimension(), s.first().getX(), s.first().getY(), s.first().getZ(),
            s.second().getX(), s.second().getY(), s.second().getZ()));
    }

    public static synchronized void clear(UUID player) {
        BY_PLAYER.remove(player);
    }
}
