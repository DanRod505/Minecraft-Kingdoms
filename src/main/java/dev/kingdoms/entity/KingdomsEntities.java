package dev.kingdoms.entity;

import dev.kingdoms.KingdomsMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity types. Ids are fixed strings. */
public final class KingdomsEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KingdomsMod.MOD_ID);

    public static final RegistryObject<EntityType<CitizenEntity>> CITIZEN = ENTITY_TYPES.register("citizen",
        () -> EntityType.Builder.<CitizenEntity>of(CitizenEntity::new, MobCategory.MISC)
            .sized(0.6f, 1.95f)
            .clientTrackingRange(10)
            .build(ENTITY_TYPES.key("citizen")));

    private KingdomsEntities() {}

    public static void register(BusGroup modBus) {
        ENTITY_TYPES.register(modBus);
        EntityAttributeCreationEvent.BUS.addListener(event -> event.put(CITIZEN.get(), CitizenEntity.createAttributes().build()));
    }
}
