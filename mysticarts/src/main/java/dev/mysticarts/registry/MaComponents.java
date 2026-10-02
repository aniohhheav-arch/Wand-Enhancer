package dev.mysticarts.registry;

import com.mojang.serialization.Codec;
import dev.mysticarts.MysticArts;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MysticArts.MODID);

    /** Bitmask of the Infinity Stones socketed into a gauntlet (bit order follows {@code Source.STONES}). */
    public static final Supplier<DataComponentType<Integer>> STONES = COMPONENTS.register("stones",
            () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    private MaComponents() {}
}
