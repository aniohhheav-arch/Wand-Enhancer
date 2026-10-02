package dev.mysticarts;

import com.mojang.logging.LogUtils;
import dev.mysticarts.command.MysticCommand;
import dev.mysticarts.event.CommonEvents;
import dev.mysticarts.network.MaNetwork;
import dev.mysticarts.power.Spells;
import dev.mysticarts.registry.MaArmorMaterials;
import dev.mysticarts.registry.MaAttachments;
import dev.mysticarts.registry.MaBlockEntities;
import dev.mysticarts.registry.MaBlocks;
import dev.mysticarts.registry.MaComponents;
import dev.mysticarts.registry.MaCreativeTabs;
import dev.mysticarts.registry.MaEntities;
import dev.mysticarts.registry.MaFeatures;
import dev.mysticarts.registry.MaItems;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(MysticArts.MODID)
public final class MysticArts {
    public static final String MODID = "mysticarts";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MysticArts(IEventBus modBus, ModContainer container) {
        MaSounds.SOUNDS.register(modBus);
        MaParticles.PARTICLES.register(modBus);
        MaComponents.COMPONENTS.register(modBus);
        MaBlocks.BLOCKS.register(modBus);
        MaArmorMaterials.MATERIALS.register(modBus);
        MaItems.ITEMS.register(modBus);
        MaBlockEntities.BLOCK_ENTITIES.register(modBus);
        MaEntities.ENTITIES.register(modBus);
        MaCreativeTabs.TABS.register(modBus);
        MaAttachments.ATTACHMENTS.register(modBus);
        MaFeatures.FEATURES.register(modBus);

        modBus.addListener(MaEntities::registerAttributes);
        modBus.addListener(MaNetwork::register);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(Spells::bootstrap));

        container.registerConfig(ModConfig.Type.COMMON, MaConfig.SPEC);

        CommonEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(MysticCommand::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
