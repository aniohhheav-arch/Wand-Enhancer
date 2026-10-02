package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import dev.mysticarts.world.MysticStructures;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MysticArts.MODID);
    public static final Map<MysticStructures.Kind, DeferredHolder<Feature<?>, MysticStructures.StructureFeature>> STRUCTURES = new EnumMap<>(MysticStructures.Kind.class);

    static {
        for (MysticStructures.Kind kind : MysticStructures.Kind.values()) {
            STRUCTURES.put(kind, FEATURES.register(kind.id, () -> new MysticStructures.StructureFeature(kind, NoneFeatureConfiguration.CODEC)));
        }
    }

    private MaFeatures() {}
}
