package com.stickyfingers;

import com.mojang.logging.LogUtils;
import com.stickyfingers.config.SFConfig;
import com.stickyfingers.registry.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(StickyFingersMod.MODID)
public final class StickyFingersMod {
    public static final String MODID = "stickyfingers";
    public static final ResourceKey<Level> PARALLEL_DIMENSION =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(MODID, "parallel"));
    public static final Logger LOGGER = LogUtils.getLogger();

    public StickyFingersMod(IEventBus modBus, ModContainer container) {
        ModSounds.REGISTER.register(modBus);
        ModParticles.REGISTER.register(modBus);
        ModEffects.REGISTER.register(modBus);
        ModBlocks.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModEntities.REGISTER.register(modBus);
        ModAttachments.REGISTER.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, SFConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, SFConfig.CLIENT_SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
