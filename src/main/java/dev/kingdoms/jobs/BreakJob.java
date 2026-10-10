package dev.kingdoms.jobs;

import dev.kingdoms.KingdomsMod;
import dev.kingdoms.entity.CitizenEntity;
import dev.kingdoms.orders.Order;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/** Breaking blocks into the worker's inventory: the lumberjack (logs) and the miner (anything safe). */
public final class BreakJob implements Job {
    public static final BreakJob CHOP = new BreakJob(Layer.BOTTOM, state -> state.is(BlockTags.LOGS));
    public static final BreakJob MINE = new BreakJob(Layer.TOP, state -> true);

    private final Layer layer;
    private final Predicate<BlockState> wanted;

    private BreakJob(Layer layer, Predicate<BlockState> wanted) {
        this.layer = layer;
        this.wanted = wanted;
    }

    @Override
    public Layer layer() {
        return layer;
    }

    @Override
    public boolean isValid(ServerLevel level, Order order, BlockPos pos) {
        var state = level.getBlockState(pos);
        return wanted.test(state) && canBreak(level, pos, state, CitizenEntity.toolFor(order.type().profession()));
    }

    /**
     * Rule 20.9: never break air, fluids, blocks with contents (chests, furnaces...), unbreakable blocks,
     * the mod's own blocks, or blocks the worker's tool would not drop.
     */
    static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity())
            return false;
        if (state.getDestroySpeed(level, pos) < 0)
            return false;
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(KingdomsMod.MOD_ID))
            return false;
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    @Override
    public int workTicks(ServerLevel level, BlockPos pos) {
        return hardnessTicks(level, pos);
    }

    static int hardnessTicks(ServerLevel level, BlockPos pos) {
        var hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        return Math.clamp(Math.round(hardness * 20), 10, 100);
    }

    @Override
    public int perform(ServerLevel level, Order order, CitizenEntity worker, BlockPos pos) {
        var state = level.getBlockState(pos);
        var tool = CitizenEntity.toolFor(order.type().profession());
        if (!wanted.test(state) || !canBreak(level, pos, state, tool))
            return 0;
        var drops = Block.getDrops(state, level, pos, null, worker, tool);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.removeBlock(pos, false);
        drops.forEach(worker::keep);
        return 1;
    }
}
