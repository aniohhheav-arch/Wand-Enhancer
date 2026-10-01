package dev.riftverse.registry;

import dev.riftverse.Riftverse;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RvCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Riftverse.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.riftverse"))
            .icon(() -> new ItemStack(RvItems.PORTAL_GUN.get()))
            .displayItems((params, output) -> {
                RvItems.TAB_ORDER.forEach(item -> output.accept(item.get()));
                RvBlocks.WITH_ITEMS.forEach(block -> output.accept(block.get()));
                RvItems.SPAWN_EGGS.forEach(item -> output.accept(item.get()));
            })
            .build());

    private RvCreativeTabs() {}
}
