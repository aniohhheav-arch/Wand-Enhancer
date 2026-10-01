package dev.riftverse.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.ClientUniverseState;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.TimeMode;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Sky, fog and lighting for the Expanse and the Nexus, all driven by the current universe spec. */
public class UniverseDimensionEffects extends DimensionSpecialEffects {
    public UniverseDimensionEffects() {
        super(Float.NaN, true, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        UniverseSpec s = ClientUniverseState.spec();
        if (s == null) return fogColor;
        float day = s.time == TimeMode.CYCLE ? 0.25f + 0.75f * brightness : 1f;
        return new Vec3(ColorUtil.r(s.fogColor) * day, ColorUtil.g(s.fogColor) * day, ColorUtil.b(s.fogColor) * day);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        UniverseSpec s = ClientUniverseState.spec();
        return s != null && s.fogDensity > 0.65f;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        return SkyRenderer.render(modelViewMatrix, projectionMatrix, partialTick);
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors) {
        UniverseSpec s = ClientUniverseState.spec();
        if (s == null) return;
        float day = ClientUniverseState.dayFactor(s, partialTicks);
        float night = 0.22f + 0.78f * day;
        float skyWeight = (pixelY / 15f) * (1f - pixelX / 15f * 0.85f);
        float factor = 1f - (1f - night) * skyWeight;
        colors.mul(factor);
        float tint = 0.1f * skyWeight;
        colors.set(colors.x() * (1f - tint) + ColorUtil.r(s.skyHorizon) * tint * colors.x() * 1.5f,
                colors.y() * (1f - tint) + ColorUtil.g(s.skyHorizon) * tint * colors.y() * 1.5f,
                colors.z() * (1f - tint) + ColorUtil.b(s.skyHorizon) * tint * colors.z() * 1.5f);
    }
}
