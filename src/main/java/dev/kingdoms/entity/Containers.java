package dev.kingdoms.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Item moves between an NPC and a container. Each method runs within one tick and moves stacks rather
 * than copying them, so an item is always in exactly one place (no dupes, GDD 12.4).
 */
public final class Containers {
    private Containers() {}

    /** The container at a position, joining both halves of a double chest. */
    public static @Nullable Container at(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest)
            return ChestBlock.getContainer(chest, state, level, pos, true);
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
    }

    /** Moves everything it can from the inventory into the container. Returns how many items did not fit. */
    public static int deposit(SimpleContainer from, Container to) {
        int left = 0;
        for (int i = 0; i < from.getContainerSize(); i++) {
            var stack = from.removeItemNoUpdate(i);
            if (stack.isEmpty())
                continue;
            var rest = HopperBlockEntity.addItem(null, to, stack, null);
            from.setItem(i, rest);
            left += rest.getCount();
        }
        to.setChanged();
        from.setChanged();
        return left;
    }

    /** Moves up to {@code max} items of one type from the container into the inventory. Returns the amount moved. */
    public static int take(Container from, SimpleContainer to, Item item, int max) {
        int moved = 0;
        for (int i = 0; i < from.getContainerSize() && moved < max; i++) {
            var stack = from.getItem(i);
            if (stack.isEmpty() || !stack.is(item))
                continue;
            var part = from.removeItem(i, Math.min(max - moved, stack.getCount()));
            var rest = to.addItem(part);
            if (!rest.isEmpty()) {
                // No room: put the remainder back into the same slot and stop.
                var current = from.getItem(i);
                if (current.isEmpty())
                    from.setItem(i, rest);
                else
                    current.grow(rest.getCount());
                moved += part.getCount() - rest.getCount();
                break;
            }
            moved += part.getCount();
        }
        from.setChanged();
        return moved;
    }

    /** Moves whatever fits from the container into the inventory. Returns the amount moved. */
    public static int takeAny(Container from, SimpleContainer to) {
        int moved = 0;
        for (int i = 0; i < from.getContainerSize(); i++) {
            var stack = from.getItem(i);
            if (stack.isEmpty() || !to.canAddItem(stack))
                continue;
            var taken = from.removeItemNoUpdate(i);
            var rest = to.addItem(taken);
            from.setItem(i, rest);
            moved += taken.getCount() - rest.getCount();
        }
        from.setChanged();
        return moved;
    }

    public static boolean hasAny(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++)
            if (!container.getItem(i).isEmpty())
                return true;
        return false;
    }

    public static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item))
                total += stack.getCount();
        }
        return total;
    }

    public static int emptySlots(SimpleContainer container) {
        int empty = 0;
        for (int i = 0; i < container.getContainerSize(); i++)
            if (container.getItem(i).isEmpty())
                empty++;
        return empty;
    }
}
