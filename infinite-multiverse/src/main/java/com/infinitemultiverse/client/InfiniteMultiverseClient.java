package com.infinitemultiverse.client;

import com.infinitemultiverse.InfiniteMultiverse;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = InfiniteMultiverse.MOD_ID, dist = Dist.CLIENT)
public final class InfiniteMultiverseClient {
    public InfiniteMultiverseClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
