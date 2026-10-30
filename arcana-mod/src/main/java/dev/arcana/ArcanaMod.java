package dev.arcana;

import dev.arcana.registry.ModAttachments;
import dev.arcana.registry.ModBlocks;
import dev.arcana.registry.ModComponents;
import dev.arcana.registry.ModEntities;
import dev.arcana.registry.ModItems;
import dev.arcana.registry.ModTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(ArcanaMod.MODID)
public class ArcanaMod {
    public static final String MODID = "arcana";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcanaMod(IEventBus bus, ModContainer container) {
        ModComponents.COMPONENTS.register(bus);
        ModAttachments.ATTACHMENTS.register(bus);
        ModItems.init();
        ModBlocks.init();
        ModItems.ITEMS.register(bus);
        ModBlocks.BLOCKS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModTabs.TABS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, ArcanaConfig.SERVER);
        container.registerConfig(ModConfig.Type.CLIENT, ArcanaConfig.CLIENT);
    }
}
