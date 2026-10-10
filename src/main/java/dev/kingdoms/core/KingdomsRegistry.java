package dev.kingdoms.core;

import dev.kingdoms.KingdomsMod;
import dev.kingdoms.block.RoyalStandardBlock;
import dev.kingdoms.block.RoyalStandardItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Blocks and items. Ids are fixed strings, never derived from registration order. */
public final class KingdomsRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomsMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomsMod.MOD_ID);

    public static final RegistryObject<Block> ROYAL_STANDARD = BLOCKS.register("royal_standard",
        () -> new RoyalStandardBlock(BlockBehaviour.Properties.of()
            .setId(BLOCKS.key("royal_standard"))
            .mapColor(MapColor.GOLD)
            .strength(2.0f, 6.0f)
            .sound(SoundType.WOOD)));

    public static final RegistryObject<Item> ROYAL_STANDARD_ITEM = ITEMS.register("royal_standard",
        () -> new RoyalStandardItem(ROYAL_STANDARD.get(), new Item.Properties().setId(ITEMS.key("royal_standard")).stacksTo(1)));

    private KingdomsRegistry() {}

    public static void register(BusGroup modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BuildCreativeModeTabContentsEvent.BUS.addListener(event -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS)
                event.accept(ROYAL_STANDARD_ITEM);
        });
    }
}
