package dev.arcana.registry;

import dev.arcana.ArcanaMod;
import dev.arcana.magic.School;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArcanaMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.arcana"))
        .icon(() -> new ItemStack(ModItems.WANDS.get(School.ARCANE).get()))
        .displayItems((params, out) -> {
            for (School s : School.values()) {
                for (int stage = 0; stage < (s == School.NONE ? 1 : 3); stage++) {
                    ItemStack st = new ItemStack(ModItems.WANDS.get(s).get());
                    st.set(ModComponents.STAGE.get(), stage);
                    out.accept(st);
                }
            }
            ModItems.CRYSTALS.values().forEach(i -> out.accept(i.get()));
            ModBlocks.CRYSTAL_ITEMS.values().forEach(i -> out.accept(i.get()));
            ModItems.MATERIALS.values().forEach(i -> out.accept(i.get()));
            out.accept(ModItems.ARCHMAGE_EGG.get());
        }).build());

    private ModTabs() {}
}
