package dev.kingdoms.jobs;

import dev.kingdoms.entity.CitizenEntity;
import dev.kingdoms.orders.Order;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;

/** The farmer harvests mature crops and replants them with one seed from the harvest. */
public final class FarmJob implements Job {
    public static final FarmJob INSTANCE = new FarmJob();

    private FarmJob() {}

    @Override
    public Layer layer() {
        return Layer.ANY;
    }

    @Override
    public boolean isValid(ServerLevel level, Order order, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    @Override
    public int workTicks(ServerLevel level, BlockPos pos) {
        return 10;
    }

    @Override
    public int perform(ServerLevel level, Order order, CitizenEntity worker, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state))
            return 0;
        var drops = Block.getDrops(state, level, pos, null, worker, CitizenEntity.toolFor(order.type().profession()));
        var seed = crop.asItem();
        boolean replanted = false;
        for (ItemStack stack : drops) {
            if (!replanted && stack.is(seed)) {
                stack.shrink(1);
                replanted = true;
            }
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        if (replanted)
            level.setBlockAndUpdate(pos, crop.getStateForAge(0));
        else
            level.removeBlock(pos, false);
        drops.forEach(worker::keep);
        return 1;
    }
}
