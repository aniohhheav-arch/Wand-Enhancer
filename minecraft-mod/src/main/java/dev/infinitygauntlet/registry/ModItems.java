package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.item.InfinityStoneItem;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(InfinityGauntlet.MOD_ID);

    public static final DeferredItem<InfinityGauntletItem> INFINITY_GAUNTLET = REGISTER.register("infinity_gauntlet",
            () -> new InfinityGauntletItem(new Item.Properties()
                    .stacksTo(1)
                    .fireResistant()
                    .rarity(Rarity.EPIC)
                    .component(ModDataComponents.GAUNTLET_STATE, GauntletState.EMPTY)));

    public static final Map<InfinityStone, DeferredItem<InfinityStoneItem>> STONES = new EnumMap<>(InfinityStone.class);

    static {
        for (InfinityStone stone : InfinityStone.values()) {
            STONES.put(stone, REGISTER.register(stone.getSerializedName() + "_stone",
                    () -> new InfinityStoneItem(stone, new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC))));
        }
    }

    public static InfinityStoneItem stone(InfinityStone stone) {
        return STONES.get(stone).get();
    }

    private ModItems() {
    }
}
