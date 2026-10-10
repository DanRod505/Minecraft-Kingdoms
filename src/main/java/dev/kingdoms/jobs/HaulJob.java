package dev.kingdoms.jobs;

import dev.kingdoms.entity.CitizenEntity;
import dev.kingdoms.entity.Containers;
import dev.kingdoms.orders.Order;
import dev.kingdoms.world.KingdomService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * The carrier brings loose items and the contents of containers in the area to the stockpile. The
 * stockpile itself is never a source, even when it lies in the area.
 */
public final class HaulJob implements Job {
    public static final HaulJob INSTANCE = new HaulJob();

    private HaulJob() {}

    @Override
    public Layer layer() {
        return Layer.ANY;
    }

    private static AABB box(Order order) {
        var a = order.area();
        return new AABB(a.minX(), a.minY(), a.minZ(), a.maxX() + 1, a.maxY() + 1, a.maxZ() + 1);
    }

    private static List<ItemEntity> itemsAt(ServerLevel level, Order order, AABB around) {
        var area = order.area();
        var dimension = KingdomService.dimension(level);
        return level.getEntitiesOfClass(ItemEntity.class, around, e -> e.isAlive() && !e.getItem().isEmpty()
            && area.contains(dimension, e.getBlockX(), e.getBlockY(), e.getBlockZ()));
    }

    private static boolean isStockpile(ServerLevel level, Order order, BlockPos pos) {
        return KingdomService.state(level.getServer()).kingdom(order.kingdom()).flatMap(k -> k.stockpile())
            .filter(s -> s.dimension().equals(KingdomService.dimension(level)) && s.x() == pos.getX() && s.y() == pos.getY() && s.z() == pos.getZ())
            .isPresent();
    }

    @Override
    public List<BlockPos> candidates(ServerLevel level, Order order) {
        var found = new LinkedHashSet<BlockPos>();
        for (var item : itemsAt(level, order, box(order)))
            found.add(item.blockPosition());
        found.addAll(Job.super.candidates(level, order));
        return List.copyOf(found);
    }

    @Override
    public boolean isValid(ServerLevel level, Order order, BlockPos pos) {
        if (!itemsAt(level, order, new AABB(pos)).isEmpty())
            return true;
        if (!level.getBlockState(pos).hasBlockEntity() || isStockpile(level, order, pos))
            return false;
        var container = Containers.at(level, pos);
        return container != null && Containers.hasAny(container);
    }

    @Override
    public int workTicks(ServerLevel level, BlockPos pos) {
        return 10;
    }

    @Override
    public int perform(ServerLevel level, Order order, CitizenEntity worker, BlockPos pos) {
        int moved = 0;
        for (var item : itemsAt(level, order, new AABB(pos).inflate(1))) {
            var stack = item.getItem();
            var rest = worker.getInventory().addItem(stack.copy());
            moved += stack.getCount() - rest.getCount();
            if (rest.isEmpty())
                item.discard();
            else
                item.setItem(rest);
        }
        if (level.getBlockState(pos).hasBlockEntity() && !isStockpile(level, order, pos)) {
            var container = Containers.at(level, pos);
            if (container != null)
                moved += Containers.takeAny(container, worker.getInventory());
        }
        return moved;
    }
}
