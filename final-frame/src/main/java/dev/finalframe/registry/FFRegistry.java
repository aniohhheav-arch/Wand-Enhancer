package dev.finalframe.registry;

import com.mojang.serialization.Codec;
import dev.finalframe.FinalFrame;
import dev.finalframe.sheriff.SheriffsLastWordItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FFRegistry {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FinalFrame.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FinalFrame.MOD_ID);

    /** Rounds left in the cylinder. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROUNDS =
        COMPONENTS.registerComponentType("rounds", b -> b.persistent(Codec.intRange(0, SheriffsLastWordItem.CAPACITY))
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredItem<SheriffsLastWordItem> SHERIFFS_LAST_WORD = ITEMS.register("sheriffs_last_word",
        () -> new SheriffsLastWordItem(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC)
            .fireResistant()
            .component(ROUNDS.get(), SheriffsLastWordItem.CAPACITY)));

    private FFRegistry() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        FFSounds.SOUNDS.register(modBus);
        modBus.addListener(FFRegistry::addToTabs);
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(SHERIFFS_LAST_WORD);
        }
    }
}
