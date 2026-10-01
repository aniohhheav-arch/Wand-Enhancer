package com.infinitemultiverse;

import com.infinitemultiverse.abilities.core.ModAbilities;
import com.infinitemultiverse.abilities.stand.StandAbilities;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.network.MultiverseNetwork;
import com.infinitemultiverse.core.registry.ModAttachments;
import com.infinitemultiverse.core.registry.ModEntities;
import com.infinitemultiverse.core.registry.ModItems;
import com.infinitemultiverse.core.registry.ModParticles;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.slf4j.Logger;

@Mod(InfiniteMultiverse.MOD_ID)
public final class InfiniteMultiverse {
    public static final String MOD_ID = "infinitemultiverse";
    public static final Logger LOGGER = LogUtils.getLogger();

    public InfiniteMultiverse(IEventBus modBus, ModContainer container) {
        modBus.addListener(NewRegistryEvent.class, MultiverseRegistries::onNewRegistry);
        modBus.addListener(RegisterPayloadHandlersEvent.class, MultiverseNetwork::register);

        ModAbilities.ABILITIES.register(modBus);
        StandAbilities.ABILITIES.register(modBus);
        StandAbilities.STAND_TYPES.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.CREATIVE_TABS.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLE_TYPES.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, MultiverseConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, MultiverseConfig.CLIENT_SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
