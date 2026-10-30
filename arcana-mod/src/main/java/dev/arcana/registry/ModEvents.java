package dev.arcana.registry;

import dev.arcana.ArcanaMod;
import dev.arcana.entity.ArchmageEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = ArcanaMod.MODID)
public final class ModEvents {
    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.ARCHMAGE.get(), ArchmageEntity.createAttributes().build());
    }

    private ModEvents() {}
}
