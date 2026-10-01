package com.infinitemultiverse.core.registry;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.ability.Ability;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Custom registries. Synced so client and server agree on ability ids; addons register through DeferredRegister. */
public final class MultiverseRegistries {
    public static final ResourceKey<Registry<Ability>> ABILITY_KEY = ResourceKey.createRegistryKey(InfiniteMultiverse.id("ability"));
    public static final Registry<Ability> ABILITIES = new RegistryBuilder<>(ABILITY_KEY).sync(true).create();

    private MultiverseRegistries() {
    }

    public static void onNewRegistry(NewRegistryEvent event) {
        event.register(ABILITIES);
    }
}
