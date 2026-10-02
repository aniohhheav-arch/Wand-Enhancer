package dev.riftverse;

import com.mojang.logging.LogUtils;
import dev.riftverse.command.RiftverseCommand;
import dev.riftverse.event.CommonEvents;
import dev.riftverse.network.RvNetwork;
import dev.riftverse.registry.RvArmorMaterials;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvBlockEntities;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvCreativeTabs;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.registry.RvWorldgen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Riftverse.MODID)
public final class Riftverse {
    public static final String MODID = "riftverse";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Riftverse(IEventBus modBus, ModContainer container) {
        RvSounds.SOUNDS.register(modBus);
        RvParticles.PARTICLES.register(modBus);
        RvBlocks.BLOCKS.register(modBus);
        RvArmorMaterials.MATERIALS.register(modBus);
        RvItems.ITEMS.register(modBus);
        RvBlockEntities.BLOCK_ENTITIES.register(modBus);
        RvEntities.ENTITIES.register(modBus);
        RvCreativeTabs.TABS.register(modBus);
        RvAttachments.ATTACHMENTS.register(modBus);
        RvWorldgen.CHUNK_GENERATORS.register(modBus);
        RvWorldgen.BIOME_SOURCES.register(modBus);
        RvWorldgen.FEATURES.register(modBus);

        modBus.addListener(RvEntities::registerAttributes);
        modBus.addListener(RvNetwork::register);

        container.registerConfig(ModConfig.Type.COMMON, RiftverseConfig.SPEC);

        CommonEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(RiftverseCommand::register);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) -> dev.riftverse.command.MultiverseCommand.register(e.getDispatcher()));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
