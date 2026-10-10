package dev.kingdoms.jobs;

import dev.kingdoms.entity.CitizenEntity;
import dev.kingdoms.orders.Order;
import dev.kingdoms.orders.OrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What one profession does with one target of an order. A job never moves the NPC: {@link
 * dev.kingdoms.entity.WorkGoal} walks it to the target and calls {@link #perform} when it arrives.
 */
public interface Job {
    /** Which targets come first, besides distance. */
    enum Layer { TOP, BOTTOM, ANY }

    Layer layer();

    /** Whether the target still needs work (the world may have changed while the worker walked). */
    boolean isValid(ServerLevel level, Order order, BlockPos pos);

    /** Ticks of work before {@link #perform}, which is what makes harder blocks slower. */
    int workTicks(ServerLevel level, BlockPos pos);

    /**
     * Does the work, moving items into or out of the worker's inventory in this tick only. Returns the
     * progress made, 0 when nothing happened.
     */
    int perform(ServerLevel level, Order order, CitizenEntity worker, BlockPos pos);

    /** Positions worth checking. By default every loaded block of the area. */
    default List<BlockPos> candidates(ServerLevel level, Order order) {
        var area = order.area();
        var list = new ArrayList<BlockPos>();
        for (var pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ()))
            if (level.isLoaded(pos) && isValid(level, order, pos))
                list.add(pos.immutable());
        return list;
    }

    /** The item a worker must carry before working (the builder's material). */
    default Optional<Item> material(Order order) {
        return Optional.empty();
    }

    /** Whether work fills the inventory, so the worker has to deliver to the stockpile. */
    default boolean collects() {
        return true;
    }

    static Job of(OrderType type) {
        return switch (type) {
            case FARM -> FarmJob.INSTANCE;
            case CHOP -> BreakJob.CHOP;
            case MINE -> BreakJob.MINE;
            case HAUL -> HaulJob.INSTANCE;
            case BUILD -> BuildJob.INSTANCE;
        };
    }
}
