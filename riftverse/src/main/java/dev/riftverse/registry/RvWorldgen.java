package dev.riftverse.registry;

import com.mojang.serialization.MapCodec;
import dev.riftverse.Riftverse;
import dev.riftverse.world.NaturalRiftFeature;
import dev.riftverse.world.NexusChunkGenerator;
import dev.riftverse.world.UniverseBiomeSource;
import dev.riftverse.world.UniverseChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RvWorldgen {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Riftverse.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, Riftverse.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Riftverse.MODID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<UniverseChunkGenerator>> UNIVERSE_GENERATOR =
            CHUNK_GENERATORS.register("universe", () -> UniverseChunkGenerator.CODEC);
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<NexusChunkGenerator>> NEXUS_GENERATOR =
            CHUNK_GENERATORS.register("nexus", () -> NexusChunkGenerator.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<UniverseBiomeSource>> UNIVERSE_BIOMES =
            BIOME_SOURCES.register("universe", () -> UniverseBiomeSource.CODEC);
    public static final DeferredHolder<Feature<?>, NaturalRiftFeature> NATURAL_RIFT =
            FEATURES.register("natural_rift", () -> new NaturalRiftFeature(NoneFeatureConfiguration.CODEC));

    public static final ResourceKey<Level> EXPANSE = ResourceKey.create(Registries.DIMENSION, Riftverse.id("expanse"));
    public static final ResourceKey<Level> NEXUS = ResourceKey.create(Registries.DIMENSION, Riftverse.id("nexus"));

    private RvWorldgen() {}
}
