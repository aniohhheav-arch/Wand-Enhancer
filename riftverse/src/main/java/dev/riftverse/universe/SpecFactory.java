package dev.riftverse.universe;

import dev.riftverse.universe.UniverseTraits.CreatureKind;
import dev.riftverse.universe.UniverseTraits.MegaKind;
import dev.riftverse.universe.UniverseTraits.MusicKind;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.universe.UniverseTraits.TimeMode;
import dev.riftverse.universe.UniverseTraits.WeatherKind;
import dev.riftverse.util.ColorUtil;
import dev.riftverse.util.Hash;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds universe specs: canonical "prime" realities, seeded random ones, and archetype defaults. */
public final class SpecFactory {
    private SpecFactory() {}

    public static UniverseSpec prime(Archetype archetype, long worldSeed) {
        UniverseId id = UniverseId.prime(archetype);
        UniverseSpec s = new UniverseSpec();
        s.id = id;
        s.seed = Hash.of(worldSeed, id.pack());
        applyArchetype(s, archetype);
        s.name = "Prime " + archetype.displayName;
        return s;
    }

    public static UniverseSpec derive(UniverseId id, long worldSeed) {
        if (id.isPrime()) return prime(Archetype.byId(id.gx() - 1), worldSeed);
        long seed = Hash.of(worldSeed, id.pack());
        Random r = new Random(seed);
        Archetype a = Archetype.byId(r.nextInt(Archetype.values().length));
        return random(id, seed, a, r);
    }

    public static UniverseSpec random(UniverseId id, long seed, Archetype archetype, Random r) {
        UniverseSpec s = new UniverseSpec();
        s.id = id;
        s.seed = seed;
        applyArchetype(s, archetype);
        mutate(s, r);
        s.name = UniverseNames.generate(seed, archetype);
        return s;
    }

    /** Random but tasteful deviations so no two universes of an archetype look alike. */
    public static void mutate(UniverseSpec s, Random r) {
        float hueShift = (r.nextFloat() - 0.5f) * 0.22f;
        s.skyTop = ColorUtil.shiftHue(s.skyTop, hueShift);
        s.skyHorizon = ColorUtil.shiftHue(s.skyHorizon, hueShift);
        s.fogColor = ColorUtil.shiftHue(s.fogColor, hueShift);
        s.nebulaA = ColorUtil.shiftHue(s.nebulaA, (r.nextFloat() - 0.5f) * 0.4f);
        s.nebulaB = ColorUtil.shiftHue(s.nebulaB, (r.nextFloat() - 0.5f) * 0.4f);
        s.amplitude *= 0.7f + r.nextFloat() * 0.7f;
        s.roughness *= 0.75f + r.nextFloat() * 0.6f;
        s.baseHeight += r.nextInt(17) - 8;
        s.megaDensity = clamp(s.megaDensity * (0.6f + r.nextFloat() * 0.9f), 0f, 1f);
        s.starDensity = clamp(s.starDensity + (r.nextFloat() - 0.5f) * 0.3f, 0f, 1f);
        s.nebulaIntensity = clamp(s.nebulaIntensity + (r.nextFloat() - 0.4f) * 0.4f, 0f, 1f);
        if (r.nextFloat() < 0.25f) s.moons = Math.max(s.moons, 1 + r.nextInt(3));
        if (r.nextFloat() < 0.18f) {
            s.planetSize = Math.max(s.planetSize, 0.25f + r.nextFloat() * 0.6f);
            s.planetRings = r.nextBoolean();
        }
        if (r.nextFloat() < 0.12f) s.auroraIntensity = Math.max(s.auroraIntensity, 0.4f + r.nextFloat() * 0.6f);
        if (r.nextFloat() < 0.10f) s.binarySun = true;
        if (r.nextFloat() < 0.08f) {
            s.skyBlackHole = true;
            s.naturalBlackHoles = true;
        }
        if (r.nextFloat() < 0.15f) s.islandDensity = Math.max(s.islandDensity, 0.25f + r.nextFloat() * 0.4f);
        if (r.nextFloat() < 0.12f) s.gravity = clamp(s.gravity * (0.45f + r.nextFloat() * 0.5f), 0.15f, 2f);
        if (r.nextFloat() < 0.15f) s.mega2 = MegaKind.byId(1 + r.nextInt(MegaKind.values().length - 1));
        if (r.nextFloat() < 0.2f) s.creatureScale = 1.2f + r.nextFloat() * 0.8f;
        if (r.nextFloat() < 0.15f) {
            CreatureKind extra = CreatureKind.byId(r.nextInt(CreatureKind.values().length - 1));
            s.creatures.add(extra);
        }
    }

    public static void applyArchetype(UniverseSpec s, Archetype a) {
        s.archetype = a;
        s.materials = a;
        s.accent = a.signatureColor;
        s.creatures = new ArrayList<>();
        s.mega2 = MegaKind.NONE;
        s.islandDensity = 0f;
        s.planetSize = 0f;
        s.planetRings = false;
        s.moons = 0;
        s.skyBlackHole = false;
        s.binarySun = false;
        s.naturalBlackHoles = false;
        s.vacuum = false;
        s.glitch = 0f;
        s.dreamlike = false;
        s.gravity = 1f;
        s.hostility = 1f;
        s.creatureScale = 1f;
        s.auroraIntensity = 0f;
        s.stormIntensity = 0f;
        s.sunSize = 1f;
        s.decorDensity = 1f;
        s.roughness = 1f;
        s.saturation = 1f;
        s.gradeStrength = 0.08f;
        s.gradeTint = a.signatureColor;
        switch (a) {
            case NEON_SPRAWL -> {
                sky(s, 0x0A0418, 0x5A1A6E, 0x2A1036, 0xFF2BD6, 0x00E5FF, 0xFF6AD5);
                s.starDensity = 0.15f; s.nebulaIntensity = 0.3f; s.galaxyIntensity = 0.1f; s.stormIntensity = 0.85f;
                s.time = TimeMode.ETERNAL_NIGHT; s.weather = WeatherKind.STORM; s.fogDensity = 0.45f;
                s.terrain = TerrainMode.CITY; s.baseHeight = 64; s.amplitude = 3; s.hasSea = false; s.seaLevel = 50;
                s.mega = MegaKind.NEON_MEGATOWER; s.megaDensity = 0.55f;
                creatures(s, CreatureKind.NEON_DRONE, CreatureKind.NEON_DRONE, CreatureKind.GLITCHLING, CreatureKind.RIFT_WRAITH);
                s.music = MusicKind.NEON; s.gradeTint = 0xFF40E0; s.gradeStrength = 0.14f; s.saturation = 1.3f;
            }
            case XENOFLORA -> {
                sky(s, 0x150C33, 0x2E7D6B, 0x1E4F48, 0x7CFFB2, 0xB26BFF, 0xFFE0A0);
                s.starDensity = 0.75f; s.nebulaIntensity = 0.55f; s.galaxyIntensity = 0.4f; s.auroraIntensity = 0.35f; s.moons = 2;
                s.time = TimeMode.ETERNAL_NIGHT; s.weather = WeatherKind.SPORES; s.fogDensity = 0.3f;
                s.terrain = TerrainMode.ROLLING; s.baseHeight = 72; s.amplitude = 24; s.hasSea = true; s.seaLevel = 63;
                s.mega = MegaKind.GIANT_TREE; s.megaDensity = 0.6f; s.decorDensity = 1.6f;
                creatures(s, CreatureKind.LUMEN_STRIDER, CreatureKind.LUMEN_STRIDER, CreatureKind.ASTRAL_JELLY, CreatureKind.ASTRAL_JELLY);
                s.hostility = 0.3f; s.music = MusicKind.DREAM; s.gradeTint = 0x60FFB0; s.gradeStrength = 0.1f; s.saturation = 1.25f;
            }
            case SKYSHATTER -> {
                sky(s, 0x2F7FE0, 0xBEE7FF, 0xCFEFFF, 0xFFFFFF, 0xA0D8FF, 0xFFF6D8);
                s.starDensity = 0.05f; s.nebulaIntensity = 0.05f; s.galaxyIntensity = 0f; s.planetSize = 0.32f; s.planetRings = true;
                s.time = TimeMode.ETERNAL_DAY; s.weather = WeatherKind.CLEAR; s.fogDensity = 0.15f;
                s.terrain = TerrainMode.FLOATING; s.islandDensity = 0.85f; s.hasSea = false; s.baseHeight = 120; s.amplitude = 20;
                s.mega = MegaKind.ARCH; s.megaDensity = 0.25f; s.gravity = 0.8f;
                creatures(s, CreatureKind.SKY_WHALE, CreatureKind.SKY_WHALE, CreatureKind.ASTRAL_JELLY);
                s.hostility = 0.2f; s.creatureScale = 1.4f; s.music = MusicKind.DREAM; s.gradeTint = 0xA0D8FF;
            }
            case THALASSIC -> {
                sky(s, 0x1E4E9A, 0x8FD3FF, 0x6FB7E8, 0x6FFFE0, 0x3070FF, 0xFFF2C0);
                s.starDensity = 0.1f; s.nebulaIntensity = 0.1f; s.planetSize = 0.5f; s.moons = 1; s.stormIntensity = 0.2f;
                s.time = TimeMode.CYCLE; s.weather = WeatherKind.RAIN; s.fogDensity = 0.25f;
                s.terrain = TerrainMode.OCEAN; s.baseHeight = 40; s.amplitude = 30; s.hasSea = true; s.seaLevel = 90; s.islandDensity = 0.25f;
                s.mega = MegaKind.COLOSSAL_PILLARS; s.megaDensity = 0.3f;
                creatures(s, CreatureKind.SKY_WHALE, CreatureKind.ASTRAL_JELLY, CreatureKind.ABYSSAL_LEVIATHAN);
                s.creatureScale = 1.6f; s.music = MusicKind.OCEAN; s.gradeTint = 0x40A0FF;
            }
            case PRISMATIC -> {
                sky(s, 0x24104A, 0xE0A6FF, 0xB98CE6, 0x7DF9FF, 0xFF8AF0, 0xFFE8FF);
                s.starDensity = 0.85f; s.nebulaIntensity = 0.7f; s.galaxyIntensity = 0.6f;
                s.time = TimeMode.ETERNAL_DUSK; s.weather = WeatherKind.STARDUST; s.fogDensity = 0.2f;
                s.terrain = TerrainMode.SPIRES; s.baseHeight = 70; s.amplitude = 28; s.hasSea = true; s.seaLevel = 60;
                s.mega = MegaKind.CRYSTAL_SPIRE; s.megaDensity = 0.8f; s.decorDensity = 1.5f;
                creatures(s, CreatureKind.CRYSTAL_SENTINEL, CreatureKind.ASTRAL_JELLY, CreatureKind.ASTRAL_JELLY);
                s.music = MusicKind.COSMIC; s.gradeTint = 0xE0A0FF; s.gradeStrength = 0.12f; s.saturation = 1.2f;
            }
            case ASHEN -> {
                sky(s, 0x08080C, 0x5A3A2A, 0x3A2A22, 0x804030, 0x302040, 0xFFB070);
                s.starDensity = 0.95f; s.nebulaIntensity = 0.15f; s.galaxyIntensity = 0.5f; s.planetSize = 0.95f; s.planetRings = true; s.sunSize = 0.6f;
                s.time = TimeMode.ETERNAL_DUSK; s.weather = WeatherKind.ASH; s.fogDensity = 0.35f;
                s.terrain = TerrainMode.CRATERS; s.baseHeight = 66; s.amplitude = 14; s.hasSea = false; s.seaLevel = 30;
                s.mega = MegaKind.RIBCAGE; s.mega2 = MegaKind.MONOLITH; s.megaDensity = 0.45f; s.decorDensity = 0.6f;
                s.vacuum = true; s.gravity = 0.6f;
                creatures(s, CreatureKind.VOID_STALKER, CreatureKind.RIFT_WRAITH);
                s.hostility = 1.3f; s.music = MusicKind.VOID; s.gradeTint = 0xC08060; s.saturation = 0.65f;
            }
            case ASTRAL -> {
                sky(s, 0x05010F, 0x2B1250, 0x1A0B33, 0xFF4FD8, 0x3D7BFF, 0xFFF0E0);
                s.starDensity = 1f; s.nebulaIntensity = 1f; s.galaxyIntensity = 1f; s.skyBlackHole = true;
                s.time = TimeMode.ETERNAL_NIGHT; s.weather = WeatherKind.STARDUST; s.fogDensity = 0.12f;
                s.terrain = TerrainMode.MOUNTAINS; s.baseHeight = 78; s.amplitude = 70; s.hasSea = false; s.seaLevel = 30;
                s.mega = MegaKind.HALO_RING; s.mega2 = MegaKind.ARCH; s.megaDensity = 0.5f;
                s.naturalBlackHoles = true; s.gravity = 0.7f;
                creatures(s, CreatureKind.ASTRAL_JELLY, CreatureKind.ASTRAL_JELLY, CreatureKind.RIFT_WRAITH, CreatureKind.VOID_STALKER);
                s.music = MusicKind.COSMIC; s.gradeTint = 0x9070FF; s.gradeStrength = 0.1f; s.saturation = 1.2f;
            }
            case CORRUPTED -> {
                sky(s, 0x000000, 0x003B00, 0x002200, 0x39FF14, 0xFF0055, 0xB0FFB0);
                s.starDensity = 0.4f; s.nebulaIntensity = 0.5f; s.galaxyIntensity = 0f;
                s.time = TimeMode.ETERNAL_NIGHT; s.weather = WeatherKind.DATA_RAIN; s.fogDensity = 0.3f;
                s.terrain = TerrainMode.SHATTERED; s.baseHeight = 70; s.amplitude = 26; s.hasSea = false; s.seaLevel = 30;
                s.mega = MegaKind.GLITCH_CUBES; s.megaDensity = 0.75f; s.glitch = 0.7f;
                creatures(s, CreatureKind.GLITCHLING, CreatureKind.GLITCHLING, CreatureKind.NEON_DRONE);
                s.hostility = 1.4f; s.music = MusicKind.NEON; s.gradeTint = 0x40FF40; s.gradeStrength = 0.15f;
            }
            case ELDER -> {
                sky(s, 0x3B6FB6, 0xFFD9A0, 0xF0C890, 0xFFE0A0, 0xFF9060, 0xFFD27A);
                s.starDensity = 0.2f; s.nebulaIntensity = 0.15f; s.binarySun = true;
                s.time = TimeMode.ETERNAL_DUSK; s.weather = WeatherKind.CLEAR; s.fogDensity = 0.2f;
                s.terrain = TerrainMode.ROLLING; s.baseHeight = 68; s.amplitude = 18; s.hasSea = true; s.seaLevel = 60;
                s.mega = MegaKind.ZIGGURAT; s.mega2 = MegaKind.COLOSSAL_PILLARS; s.megaDensity = 0.55f;
                creatures(s, CreatureKind.CRYSTAL_SENTINEL, CreatureKind.LUMEN_STRIDER, CreatureKind.CRYSTAL_SENTINEL);
                s.music = MusicKind.ANCIENT; s.gradeTint = 0xFFC060; s.gradeStrength = 0.12f;
            }
            case INVERTED -> {
                sky(s, 0x0F2A3A, 0x7AF0D0, 0x3E8C7E, 0x7AF0D0, 0xFFD36B, 0xFFFFE0);
                s.starDensity = 0.6f; s.nebulaIntensity = 0.45f; s.moons = 3;
                s.time = TimeMode.ETERNAL_DUSK; s.weather = WeatherKind.STARDUST; s.fogDensity = 0.2f;
                s.terrain = TerrainMode.INVERTED; s.baseHeight = 50; s.amplitude = 22; s.hasSea = true; s.seaLevel = 44; s.islandDensity = 0.6f;
                s.mega = MegaKind.ARCH; s.megaDensity = 0.35f; s.gravity = 0.35f;
                creatures(s, CreatureKind.SKY_WHALE, CreatureKind.ASTRAL_JELLY, CreatureKind.NEON_DRONE);
                s.music = MusicKind.COSMIC; s.gradeTint = 0x70F0D0;
            }
            case HOLLOW -> {
                sky(s, 0x000000, 0x0A0612, 0x050308, 0x4B2A7A, 0x120020, 0x9060FF);
                s.starDensity = 0.12f; s.nebulaIntensity = 0.3f; s.galaxyIntensity = 0.05f;
                s.time = TimeMode.ETERNAL_NIGHT; s.weather = WeatherKind.CLEAR; s.fogDensity = 0.8f;
                s.terrain = TerrainMode.FRAGMENTS; s.baseHeight = 90; s.amplitude = 30; s.hasSea = false; s.islandDensity = 0.45f;
                s.mega = MegaKind.MONOLITH; s.megaDensity = 0.4f; s.naturalBlackHoles = true;
                creatures(s, CreatureKind.VOID_STALKER, CreatureKind.VOID_STALKER, CreatureKind.RIFT_WRAITH);
                s.hostility = 1.7f; s.music = MusicKind.VOID; s.gradeTint = 0x6040A0; s.saturation = 0.7f;
            }
            case SOMNIUM -> {
                sky(s, 0xFF9AD5, 0xFFE4F2, 0xFFD0EA, 0xA0E7FF, 0xFFF3A0, 0xFFFFFF);
                s.starDensity = 0.35f; s.nebulaIntensity = 0.45f; s.moons = 2; s.planetSize = 0.35f;
                s.time = TimeMode.ETERNAL_DUSK; s.weather = WeatherKind.PETALS; s.fogDensity = 0.25f;
                s.terrain = TerrainMode.ROLLING; s.baseHeight = 70; s.amplitude = 26; s.hasSea = true; s.seaLevel = 62; s.islandDensity = 0.2f;
                s.mega = MegaKind.GIANT_MUSHROOM; s.mega2 = MegaKind.ARCH; s.megaDensity = 0.6f; s.decorDensity = 1.4f;
                s.dreamlike = true; s.gravity = 0.6f;
                creatures(s, CreatureKind.ASTRAL_JELLY, CreatureKind.SKY_WHALE, CreatureKind.LUMEN_STRIDER);
                s.hostility = 0f; s.music = MusicKind.DREAM; s.gradeTint = 0xFFA0E0; s.gradeStrength = 0.12f; s.saturation = 1.15f;
            }
            case CINDER -> {
                sky(s, 0x1A0500, 0xB33A00, 0x5A1A05, 0xFF5A1F, 0xFFC14D, 0xFF3300);
                s.starDensity = 0.3f; s.nebulaIntensity = 0.4f; s.sunSize = 2.2f;
                s.time = TimeMode.ETERNAL_DUSK; s.weather = WeatherKind.EMBERS; s.fogDensity = 0.4f;
                s.terrain = TerrainMode.VOLCANIC; s.baseHeight = 60; s.amplitude = 40; s.hasSea = true; s.seaLevel = 42;
                s.mega = MegaKind.VOLCANO; s.megaDensity = 0.5f;
                creatures(s, CreatureKind.RIFT_WRAITH, CreatureKind.VOID_STALKER);
                s.hostility = 1.4f; s.music = MusicKind.VOID; s.gradeTint = 0xFF6020; s.gradeStrength = 0.14f;
            }
            case RIME -> {
                sky(s, 0x0A1A3A, 0x9FDFFF, 0xBFE8FF, 0x60FFD0, 0x8060FF, 0xE0F4FF);
                s.starDensity = 0.85f; s.nebulaIntensity = 0.25f; s.auroraIntensity = 1f; s.moons = 1;
                s.time = TimeMode.ETERNAL_NIGHT; s.weather = WeatherKind.SNOW; s.fogDensity = 0.3f;
                s.terrain = TerrainMode.GLACIAL; s.baseHeight = 70; s.amplitude = 45; s.hasSea = true; s.seaLevel = 62;
                s.mega = MegaKind.ICE_SPIRE; s.megaDensity = 0.55f;
                creatures(s, CreatureKind.CRYSTAL_SENTINEL, CreatureKind.ASTRAL_JELLY);
                s.music = MusicKind.COSMIC; s.gradeTint = 0xA0E0FF;
            }
        }
    }

    private static void sky(UniverseSpec s, int top, int horizon, int fog, int nebA, int nebB, int sun) {
        s.skyTop = top;
        s.skyHorizon = horizon;
        s.fogColor = fog;
        s.nebulaA = nebA;
        s.nebulaB = nebB;
        s.sunColor = sun;
    }

    private static void creatures(UniverseSpec s, CreatureKind... kinds) {
        s.creatures = new ArrayList<>(List.of(kinds));
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
