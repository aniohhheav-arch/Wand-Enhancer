package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.component.GauntletState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, InfinityGauntlet.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GauntletState>> GAUNTLET_STATE =
            REGISTER.registerComponentType("gauntlet_state", b -> b
                    .persistent(GauntletState.CODEC)
                    .networkSynchronized(GauntletState.STREAM_CODEC)
                    .cacheEncoding());

    private ModDataComponents() {
    }
}
