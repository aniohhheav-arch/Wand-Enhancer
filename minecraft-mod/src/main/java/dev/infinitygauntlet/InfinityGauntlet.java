package dev.infinitygauntlet;

import dev.infinitygauntlet.config.ClientConfig;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.network.ModNetwork;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.registry.ModCreativeTabs;
import dev.infinitygauntlet.registry.ModDataComponents;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModItems;
import dev.infinitygauntlet.registry.ModLootModifiers;
import dev.infinitygauntlet.registry.ModParticles;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.registry.ModTriggers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(InfinityGauntlet.MOD_ID)
public final class InfinityGauntlet {
    public static final String MOD_ID = "infinitygauntlet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public InfinityGauntlet(IEventBus modBus, ModContainer container) {
        ModDataComponents.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModEntities.REGISTER.register(modBus);
        ModParticles.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModAttachments.REGISTER.register(modBus);
        ModTriggers.REGISTER.register(modBus);
        ModLootModifiers.REGISTER.register(modBus);
        ModCreativeTabs.REGISTER.register(modBus);

        modBus.addListener(ModNetwork::register);

        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
