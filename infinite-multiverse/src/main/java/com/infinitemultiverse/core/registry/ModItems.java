package com.infinitemultiverse.core.registry;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.stand.AwakeningArrowheadItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(InfiniteMultiverse.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InfiniteMultiverse.MOD_ID);

    public static final DeferredItem<AwakeningArrowheadItem> AWAKENING_ARROWHEAD = ITEMS.registerItem("awakening_arrowhead",
            AwakeningArrowheadItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.infinitemultiverse"))
                    .icon(() -> new ItemStack(AWAKENING_ARROWHEAD.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(AWAKENING_ARROWHEAD.get());
                        com.infinitemultiverse.power.gear.ModGear.ALL.forEach(item -> output.accept(item.get()));
                        com.infinitemultiverse.cosmic.CosmicContent.creativeItems().forEach(item -> output.accept(item.get()));
                    })
                    .build());

    private ModItems() {
    }
}
