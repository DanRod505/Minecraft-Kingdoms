package dev.kingdoms.jobs;

import dev.kingdoms.entity.CitizenEntity;
import dev.kingdoms.orders.Order;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/** The builder fills every empty block of the area with the order's material, from the bottom up. */
public final class BuildJob implements Job {
    public static final BuildJob INSTANCE = new BuildJob();

    private BuildJob() {}

    public static Optional<Block> block(Order order) {
        return block(order.material());
    }

    /** The block a material id names, if it is a block that exists as an item. */
    public static Optional<Block> block(Optional<String> material) {
        return material.map(Identifier::tryParse).flatMap(BuiltInRegistries.BLOCK::getOptional)
            .filter(b -> b.asItem() != Items.AIR);
    }

    @Override
    public Layer layer() {
        return Layer.BOTTOM;
    }

    @Override
    public Optional<Item> material(Order order) {
        return block(order).map(Block::asItem);
    }

    @Override
    public boolean collects() {
        return false;
    }

    @Override
    public boolean isValid(ServerLevel level, Order order, BlockPos pos) {
        var block = block(order);
        if (block.isEmpty())
            return false;
        var current = level.getBlockState(pos);
        if (!current.canBeReplaced() || !current.getFluidState().isEmpty() || current.is(block.get()))
            return false;
        return block.get().defaultBlockState().canSurvive(level, pos);
    }

    @Override
    public int workTicks(ServerLevel level, BlockPos pos) {
        return 10;
    }

    @Override
    public int perform(ServerLevel level, Order order, CitizenEntity worker, BlockPos pos) {
        var block = block(order);
        if (block.isEmpty() || !isValid(level, order, pos))
            return 0;
        // Never place a block inside someone else. A worker standing on the spot steps up onto the new
        // block, the way players build pillars, if there is room above.
        var box = new AABB(pos);
        if (level.getEntitiesOfClass(LivingEntity.class, box, e -> e != worker).stream().findAny().isPresent())
            return 0;
        boolean climb = worker.getBoundingBox().intersects(box);
        if (climb && !(level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir()))
            return 0;
        if (!worker.consume(block.get().asItem()))
            return 0;
        level.setBlockAndUpdate(pos, block.get().defaultBlockState());
        if (climb)
            worker.teleportTo(worker.getX(), pos.getY() + 1.0, worker.getZ());
        return 1;
    }
}
