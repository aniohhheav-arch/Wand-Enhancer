package dev.arcana.registry;

import dev.arcana.ArcanaMod;
import dev.arcana.item.WandStyle;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, ArcanaMod.MODID);

    public static final Supplier<DataComponentType<Integer>> STAGE = COMPONENTS.registerComponentType("stage",
        b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final Supplier<DataComponentType<WandStyle>> STYLE = COMPONENTS.registerComponentType("style",
        b -> b.persistent(WandStyle.CODEC).networkSynchronized(WandStyle.STREAM_CODEC));
    public static final Supplier<DataComponentType<String>> SELECTED = COMPONENTS.registerComponentType("selected",
        b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ModComponents() {}
}
