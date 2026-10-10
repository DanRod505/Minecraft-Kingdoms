package dev.kingdoms.block;

import net.minecraft.world.level.block.Block;

/**
 * The royal standard marks a capital. Founding happens in {@link RoyalStandardItem} when it is placed;
 * breaking it does not dissolve the kingdom (losing a capital is handled by later phases, GDD 7.1).
 */
public final class RoyalStandardBlock extends Block {
    public RoyalStandardBlock(Properties properties) {
        super(properties);
    }
}
