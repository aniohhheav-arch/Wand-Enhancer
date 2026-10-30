package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InfinityGauntlet.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.infinitygauntlet"))
            .icon(InfinityGauntletItem::fullGauntlet)
            .displayItems((params, output) -> {
                output.accept(ModItems.INFINITY_GAUNTLET.get());
                output.accept(InfinityGauntletItem.fullGauntlet());
                output.accept(ModBlocks.CONSCIOUSNESS_NEXUS_ITEM.get());
                for (InfinityStone stone : InfinityStone.values()) {
                    output.accept(ModItems.stone(stone));
                }
            })
            .build());

    private ModCreativeTabs() {
    }
}
