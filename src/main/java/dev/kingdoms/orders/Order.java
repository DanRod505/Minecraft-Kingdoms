package dev.kingdoms.orders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kingdoms.world.Codecs;

import java.util.Optional;
import java.util.UUID;

/**
 * A work order of a kingdom (GDD 3.3). {@code blocked} holds a translation key suffix explaining why
 * no work is happening, shown to the player; it is cleared as soon as a worker makes progress.
 * {@code material} is a block id, used by BUILD orders.
 */
public record Order(UUID id, UUID kingdom, OrderType type, Area area, int priority, OrderStatus status,
                    Optional<String> blocked, Optional<String> material, long createdAt, int progress) {
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 5;
    public static final int DEFAULT_PRIORITY = 3;

    public static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codecs.UUID_STRING.fieldOf("id").forGetter(Order::id),
        Codecs.UUID_STRING.fieldOf("kingdom").forGetter(Order::kingdom),
        OrderType.CODEC.fieldOf("type").forGetter(Order::type),
        Area.CODEC.fieldOf("area").forGetter(Order::area),
        Codec.INT.fieldOf("priority").forGetter(Order::priority),
        OrderStatus.CODEC.fieldOf("status").forGetter(Order::status),
        Codec.STRING.optionalFieldOf("blocked").forGetter(Order::blocked),
        Codec.STRING.optionalFieldOf("material").forGetter(Order::material),
        Codec.LONG.fieldOf("created_at").forGetter(Order::createdAt),
        Codec.INT.fieldOf("progress").forGetter(Order::progress)
    ).apply(i, Order::new));

    public Order withStatus(OrderStatus status) {
        return new Order(id, kingdom, type, area, priority, status, status == OrderStatus.ACTIVE ? blocked : Optional.empty(), material, createdAt, progress);
    }

    public Order withPriority(int priority) {
        return new Order(id, kingdom, type, area, priority, status, blocked, material, createdAt, progress);
    }

    public Order withBlocked(Optional<String> blocked) {
        return new Order(id, kingdom, type, area, priority, status, blocked, material, createdAt, progress);
    }

    public Order withProgress(int progress) {
        return new Order(id, kingdom, type, area, priority, status, Optional.empty(), material, createdAt, progress);
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }
}
