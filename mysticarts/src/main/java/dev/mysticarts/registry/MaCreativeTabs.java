package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import dev.mysticarts.item.InfinityGauntletItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MysticArts.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mysticarts"))
            .icon(() -> InfinityGauntletItem.full(MaItems.INFINITY_GAUNTLET.get()))
            .displayItems((params, output) -> {
                output.accept(InfinityGauntletItem.full(MaItems.INFINITY_GAUNTLET.get()));
                MaItems.TAB_ORDER.forEach(item -> output.accept(item.get()));
                MaBlocks.WITH_ITEMS.forEach(block -> output.accept(block.get()));
                MaItems.SPAWN_EGGS.forEach(item -> output.accept(item.get()));
            })
            .build());

    private MaCreativeTabs() {}
}
