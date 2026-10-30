package dev.infinitygauntlet.registry;

import com.mojang.serialization.MapCodec;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.loot.AddItemModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, InfinityGauntlet.MOD_ID);

    public static final Supplier<MapCodec<AddItemModifier>> ADD_ITEM = REGISTER.register("add_item", () -> AddItemModifier.CODEC);

    private ModLootModifiers() {
    }
}
