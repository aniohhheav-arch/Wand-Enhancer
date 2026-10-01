package dev.riftverse.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.riftverse.client.ClientUniverseState;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Paints each universe's procedural sky with a single full-screen shader pass. */
public final class SkyRenderer {
    private SkyRenderer() {}

    private static void color(ShaderInstance sh, String name, int rgb) {
        sh.safeGetUniform(name).set(ColorUtil.r(rgb), ColorUtil.g(rgb), ColorUtil.b(rgb));
    }

    public static boolean render(Matrix4f modelView, Matrix4f projection, float partialTick) {
        ShaderInstance sh = RvShaders.sky;
        UniverseSpec s = ClientUniverseState.spec();
        if (sh == null || s == null) return false;
        Matrix4f inv = new Matrix4f(projection).mul(modelView).invert();
        sh.safeGetUniform("InvViewProj").set(inv);
        color(sh, "SkyTop", ClientUniverseState.skyTop(s, partialTick));
        color(sh, "SkyHorizon", ClientUniverseState.skyHorizon(s, partialTick));
        color(sh, "SkyGround", ColorUtil.scale(s.fogColor, 0.75f));
        color(sh, "NebulaA", s.nebulaA);
        color(sh, "NebulaB", s.nebulaB);
        color(sh, "SunColor", s.sunColor);
        Vector3f sun = ClientUniverseState.sunDirection(s, partialTick);
        sh.safeGetUniform("SunDir").set(sun.x(), sun.y(), sun.z());
        sh.safeGetUniform("SkyP1").set(s.starDensity, s.nebulaIntensity, s.galaxyIntensity, s.auroraIntensity);
        sh.safeGetUniform("SkyP2").set((float) s.moons, s.planetSize, s.planetRings ? 1f : 0f, s.skyBlackHole ? 1f : 0f);
        sh.safeGetUniform("SkyP3").set(s.sunSize, s.binarySun ? 1f : 0f, s.stormIntensity, s.glitch);
        ClientLevel level = Minecraft.getInstance().level;
        float flash = level != null && level.getSkyFlashTime() > 0 ? 0.55f : UniverseFlash.current();
        sh.safeGetUniform("SkyP4").set(ClientUniverseState.dayFactor(s, partialTick), (float) (Math.floorMod(s.seed, 1000L)) * 0.37f, flash, s.saturation);
        sh.safeGetUniform("SkyP5").set(ClientUniverseState.isNexus() ? 1f : 0f, 0f, 0f, 0f);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        Fullscreen.draw(sh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        return true;
    }

    /** Lightning flicker for storm universes, driven from the client tick. */
    public static final class UniverseFlash {
        private static float flash;

        private UniverseFlash() {}

        public static void tick(UniverseSpec s, net.minecraft.util.RandomSource random) {
            flash *= 0.75f;
            if (s != null && s.stormIntensity > 0.5f && random.nextFloat() < 0.012f * s.stormIntensity) flash = 0.5f + random.nextFloat() * 0.4f;
        }

        public static float current() {
            return flash;
        }
    }
}
