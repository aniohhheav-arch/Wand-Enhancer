package dev.riftverse.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseRegistry;
import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Assigns each universe slot the biome of its archetype (sky tint, water colour, ambience). */
public class UniverseBiomeSource extends BiomeSource {
    public static final MapCodec<UniverseBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(i, i.stable(UniverseBiomeSource::new)));

    private final Holder<Biome>[] byArchetype;

    @SuppressWarnings("unchecked")
    public UniverseBiomeSource(HolderGetter<Biome> biomes) {
        Archetype[] all = Archetype.values();
        this.byArchetype = new Holder[all.length];
        for (Archetype a : all) byArchetype[a.ordinal()] = biomes.getOrThrow(a.biome);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Arrays.stream(byArchetype);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return byArchetype[UniverseRegistry.specAt(quartX << 2, quartZ << 2).archetype.ordinal()];
    }
}
