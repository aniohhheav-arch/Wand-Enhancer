package dev.infinitygauntlet.client.breach;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.breach.ChamberDimension;
import dev.infinitygauntlet.breach.DimensionalRiftEntity;
import dev.infinitygauntlet.client.nexus.FeedRenderer;
import dev.infinitygauntlet.config.ClientConfig;
import dev.infinitygauntlet.network.GlitchPayload;
import dev.infinitygauntlet.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Reality Breach's interference with rendering itself: a full-screen post shader (spatial ripples, tearing
 * slices, RGB separation, corrupted blocks, exposure flicker), the chamber's white infinity fog and its unstable
 * light, random glitch bursts while inside, and distortion that swells as the player nears an open rift and
 * radiates from where that rift sits on screen. Strength follows the client's VFX quality setting.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class RealityDistortion {
    private static final double RIFT_RANGE = 18;

    @Nullable
    private static PostChain chain;
    private static boolean failed;
    private static int width, height;
    private static float intensity;
    private static float spike;
    private static float clock;
    private static long tick;
    private static long nextBurst;
    @Nullable
    private static ResourceKey<Level> lastDimension;
    @Nullable
    private static Vec3 focusWorld;
    @Nullable
    private static ChamberHum hum;

    public static float spike() {
        return spike;
    }

    public static boolean inChamber() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && ChamberDimension.isChamber(mc.level);
    }

    public static void glitch(GlitchPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double d = mc.player.position().distanceTo(p.origin());
        spike = Math.max(spike, p.strength() * (float) Mth.clamp(1 - d / Math.max(1, p.radius()), 0, 1));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        tick++;
        if (!mc.isPaused()) {
            clock += 0.05f;
        }
        spike *= 0.84f;
        if (lastDimension != mc.level.dimension()) {
            if (lastDimension != null && (ChamberDimension.isChamber(mc.level) || lastDimension == ChamberDimension.KEY)) {
                spike = 1f; // the crossing itself tears the picture
            }
            lastDimension = mc.level.dimension();
        }
        float target = 0;
        focusWorld = null;
        if (inChamber()) {
            target = 0.32f;
            if (tick >= nextBurst) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                spike = Math.max(spike, 0.45f + r.nextFloat() * 0.55f);
                nextBurst = tick + 70 + r.nextInt(200);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.REALITY_GLITCH.get(), 0.8f + r.nextFloat() * 0.5f, 0.35f));
            }
            if (hum == null || !mc.getSoundManager().isActive(hum)) {
                hum = new ChamberHum();
                mc.getSoundManager().play(hum);
            }
        }
        double best = RIFT_RANGE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof DimensionalRiftEntity rift) {
                double d = rift.position().distanceTo(mc.player.getEyePosition()) - rift.radius();
                if (d < best) {
                    best = d;
                    focusWorld = rift.position();
                    float near = (float) Mth.clamp(1 - Math.max(0, d) / RIFT_RANGE, 0, 1);
                    target = Math.max(target, near * near * 0.9f * rift.openProgress(0) * (1 - rift.closeProgress(0)));
                }
            }
        }
        intensity += (target - intensity) * 0.15f;
    }

    @SubscribeEvent
    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || FeedRenderer.isRendering() || failed) {
            return;
        }
        float quality = Math.min(1f, ClientConfig.quality().density);
        float i = intensity * quality, s = spike * quality;
        if (i + s < 0.01f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!ensureChain(mc)) {
            return;
        }
        float fx = 0.5f, fy = 0.5f;
        if (focusWorld != null) {
            Vec3 rel = focusWorld.subtract(event.getCamera().getPosition());
            Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f);
            event.getModelViewMatrix().transform(v);
            event.getProjectionMatrix().transform(v);
            if (v.w > 0.05f) {
                fx = Mth.clamp(v.x / v.w * 0.5f + 0.5f, -0.5f, 1.5f);
                fy = Mth.clamp(v.y / v.w * 0.5f + 0.5f, -0.5f, 1.5f);
            }
        }
        chain.setUniform("Clock", clock);
        chain.setUniform("Intensity", i);
        chain.setUniform("Spike", s);
        chain.setUniform("FocusX", fx);
        chain.setUniform("FocusY", fy);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(event.getPartialTick().getGameTimeDeltaTicks());
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static boolean ensureChain(Minecraft mc) {
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
                        InfinityGauntlet.id("shaders/post/reality_distortion.json"));
            } catch (Exception e) {
                InfinityGauntlet.LOGGER.error("Reality distortion shader failed to load; continuing without it", e);
                failed = true;
                return false;
            }
            width = height = -1;
        }
        if (w != width || h != height) {
            chain.resize(w, h);
            width = w;
            height = h;
        }
        return true;
    }

    /** White infinity: the chamber's edges dissolve into fog of the same white as the room. */
    @SubscribeEvent
    static void onFog(ViewportEvent.RenderFog event) {
        if (!inChamber() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        event.setNearPlaneDistance(1.5f);
        event.setFarPlaneDistance(44f - 14f * spike);
        event.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inChamber()) {
            return;
        }
        float r = 0.955f, g = 0.962f, b = 0.975f;
        if (spike > 0.2f) {
            // lighting inconsistency: the "white" briefly shifts and dims
            float k = spike * (ThreadLocalRandom.current().nextFloat() < 0.5f ? 1 : 0);
            r -= 0.25f * k;
            g -= 0.32f * k;
            b -= 0.1f * k;
        }
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        failed = false;
        intensity = spike = 0;
        lastDimension = null;
    }

    private static final class ChamberHum extends AbstractTickableSoundInstance {
        ChamberHum() {
            super(ModSounds.CHAMBER_AMBIENT.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.7f;
        }

        @Override
        public void tick() {
            if (!inChamber()) {
                stop();
            }
        }
    }

    private RealityDistortion() {
    }
}
