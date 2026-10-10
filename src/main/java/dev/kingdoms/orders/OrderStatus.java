package dev.kingdoms.orders;

import com.mojang.serialization.Codec;
import dev.kingdoms.world.Codecs;

import java.util.Locale;

/** Task life cycle (GDD 3.4). Only ACTIVE orders take workers. */
public enum OrderStatus {
    ACTIVE, PAUSED, DONE, CANCELLED;

    public static final Codec<OrderStatus> CODEC = Codecs.enumByName(OrderStatus.class);

    public boolean open() {
        return this == ACTIVE || this == PAUSED;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
