package dev.riftverse.client;

import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.TimeMode;
import dev.riftverse.util.ColorUtil;
import dev.riftverse.util.Hash;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** The client's knowledge of the reality the player currently stands in. */
public final class ClientUniverseState {
    public static final int MODE_VANILLA = 0;
    public static final int MODE_UNIVERSE = 1;
    public static final int MODE_NEXUS = 2;

    private static int mode = MODE_VANILLA;
    @Nullable
    private static UniverseSpec spec;
    private static UniverseSpec nexusSpec;

    private ClientUniverseState() {}

    public static void update(int newMode, CompoundTag tag) {
        mode = newMode;
        spec = newMode == MODE_UNIVERSE && !tag.isEmpty() ? UniverseSpec.load(tag) : null;
        UniverseAmbience.onUniverseChanged();
    }

    public static void reset() {
        mode = MODE_VANILLA;
        spec = null;
    }

    public static int mode() {
        return mode;
    }

    public static boolean active() {
        return mode != MODE_VANILLA;
    }

    /** Spec driving the sky and atmosphere: the current universe, or the Nexus preset. */
    @Nullable
    public static UniverseSpec spec() {
        if (mode == MODE_UNIVERSE) return spec;
        if (mode == MODE_NEXUS) return nexus();
        return null;
    }

    private static UniverseSpec nexus() {
        if (nexusSpec == null) {
            UniverseSpec s = new UniverseSpec();
            s.name = "The Multiverse Nexus";
            s.archetype = Archetype.ASTRAL;
            s.skyTop = 0x120820;
            s.skyHorizon = 0x4A2A10;
            s.fogColor = 0x2A1A30;
            s.nebulaA = 0xFFC14D;
            s.nebulaB = 0x8F6BFF;
            s.sunColor = 0xFFE0A0;
            s.accent = 0xFFC14D;
            s.starDensity = 0.8f;
            s.nebulaIntensity = 0.55f;
            s.galaxyIntensity = 0.35f;
            s.time = TimeMode.ETERNAL_NIGHT;
            s.fogDensity = 0.1f;
            s.gradeTint = 0xFFC060;
            s.gradeStrength = 0.06f;
            s.saturation = 1.1f;
            s.seed = 7;
            nexusSpec = s;
        }
        return nexusSpec;
    }

    public static boolean isNexus() {
        return mode == MODE_NEXUS;
    }

    /** Direction of the sun for this universe's time mode. */
    public static Vector3f sunDirection(UniverseSpec s, float partialTick) {
        float azimuth = Hash.unit(Hash.of(s.seed, 99)) * (float) Math.PI * 2f;
        float elevation;
        switch (s.time) {
            case ETERNAL_DAY -> elevation = 1.05f;
            case ETERNAL_DUSK -> elevation = 0.09f;
            case ETERNAL_NIGHT -> elevation = -0.6f;
            default -> {
                var level = Minecraft.getInstance().level;
                float t = level == null ? 0f : ((level.getGameTime() % 24000L) + partialTick) / 24000f;
                elevation = (float) Math.sin(t * Math.PI * 2.0) * 1.2f;
            }
        }
        float cosE = (float) Math.cos(elevation);
        return new Vector3f((float) Math.cos(azimuth) * cosE, (float) Math.sin(elevation), (float) Math.sin(azimuth) * cosE).normalize();
    }

    /** 0 at night, 1 at noon. */
    public static float dayFactor(UniverseSpec s, float partialTick) {
        return switch (s.time) {
            case ETERNAL_DAY -> 1f;
            case ETERNAL_DUSK -> 0.45f;
            case ETERNAL_NIGHT -> 0f;
            default -> Math.max(0f, Math.min(1f, sunDirection(s, partialTick).y() * 1.6f + 0.25f));
        };
    }

    /** Sky colours adjusted for time of day in cycling universes. */
    public static int skyTop(UniverseSpec s, float partial) {
        if (s.time != TimeMode.CYCLE) return s.skyTop;
        return ColorUtil.lerp(ColorUtil.scale(s.skyTop, 0.12f), s.skyTop, dayFactor(s, partial));
    }

    public static int skyHorizon(UniverseSpec s, float partial) {
        if (s.time != TimeMode.CYCLE) return s.skyHorizon;
        return ColorUtil.lerp(ColorUtil.scale(s.skyHorizon, 0.2f), s.skyHorizon, dayFactor(s, partial));
    }
}
