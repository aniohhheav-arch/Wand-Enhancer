package com.infinitemultiverse.core.registry;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.power.PowerSet;
import com.infinitemultiverse.stand.StandType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Custom registries. Synced so client and server agree on ability ids; addons register through DeferredRegister. */
public final class MultiverseRegistries {
    public static final ResourceKey<Registry<Ability>> ABILITY_KEY = ResourceKey.createRegistryKey(InfiniteMultiverse.id("ability"));
    public static final Registry<Ability> ABILITIES = new RegistryBuilder<>(ABILITY_KEY).sync(true).create();
    public static final ResourceKey<Registry<StandType>> STAND_TYPE_KEY = ResourceKey.createRegistryKey(InfiniteMultiverse.id("stand_type"));
    public static final Registry<StandType> STAND_TYPES = new RegistryBuilder<>(STAND_TYPE_KEY).sync(true).create();

    public static final ResourceKey<Registry<PowerSet>> POWER_SET_KEY = ResourceKey.createRegistryKey(InfiniteMultiverse.id("power_set"));
    public static final Registry<PowerSet> POWER_SETS = new RegistryBuilder<>(POWER_SET_KEY).sync(true).create();

    private MultiverseRegistries() {
    }

    public static void onNewRegistry(NewRegistryEvent event) {
        event.register(ABILITIES);
        event.register(STAND_TYPES);
        event.register(POWER_SETS);
    }
}
