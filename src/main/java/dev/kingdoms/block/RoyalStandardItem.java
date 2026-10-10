package dev.kingdoms.block;

import dev.kingdoms.world.KingdomException;
import dev.kingdoms.world.KingdomService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Placing the standard founds a kingdom named after the player, with the capital at the standard. */
public final class RoyalStandardItem extends BlockItem {
    public RoyalStandardItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel().isClientSide())
            return super.place(context);
        if (!(context.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.FAIL;

        var pos = context.getClickedPos();
        var name = player.getGameProfile().name();
        try {
            KingdomService.checkCanFound(player, name, pos);
        } catch (KingdomException e) {
            player.sendSystemMessage(KingdomService.message(e));
            return InteractionResult.FAIL;
        }

        var result = super.place(context);
        if (result.consumesAction()) {
            var kingdom = KingdomService.found(player, name, pos);
            player.sendSystemMessage(Component.translatable("kingdoms.message.founded", kingdom.name()));
        }
        return result;
    }
}
