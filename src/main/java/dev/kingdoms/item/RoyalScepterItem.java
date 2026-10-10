package dev.kingdoms.item;

import dev.kingdoms.world.KingdomService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Marks the area of the next order: use on a block for the first corner, sneak and use for the second
 * (GDD 3.2). The order itself is created with {@code /kingdom order}.
 */
public final class RoyalScepterItem extends Item {
    public RoyalScepterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.SUCCESS;
        var pos = context.getClickedPos();
        boolean second = player.isShiftKeyDown();
        Selections.set(player.getUUID(), KingdomService.dimension(player.level()), pos, second);
        player.sendSystemMessage(Component.translatable(second ? "kingdoms.scepter.second" : "kingdoms.scepter.first",
            pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.YELLOW));
        Selections.area(player.getUUID()).ifPresent(area -> player.sendSystemMessage(
            Component.translatable("kingdoms.scepter.area", area.volume()).withStyle(ChatFormatting.GRAY)));
        return InteractionResult.SUCCESS;
    }
}
