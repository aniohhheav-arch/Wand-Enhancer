package dev.finalframe;

import dev.finalframe.finisher.FinisherManager;
import dev.finalframe.finisher.FinisherRegistry;
import dev.finalframe.network.FFNetwork;
import dev.finalframe.registry.FFRegistry;
import dev.finalframe.sheriff.SheriffFinisher;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * FINAL FRAME — Cinematic Finisher Weapons.
 *
 * <p>The mod is split into a reusable finisher framework ({@code finisher/}), a networked
 * session protocol ({@code network/}), the client cinematic engine ({@code client/}) and the
 * first finisher weapon, The Sheriff's Last Word ({@code sheriff/}).
 */
@Mod(FinalFrame.MOD_ID)
public final class FinalFrame {
    public static final String MOD_ID = "finalframe";

    public FinalFrame(IEventBus modBus, ModContainer container) {
        FFRegistry.register(modBus);
        modBus.addListener(FFNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, FFConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, FFConfig.CLIENT_SPEC);
        FinisherRegistry.register(SheriffFinisher.INSTANCE);
        NeoForge.EVENT_BUS.register(FinisherManager.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
