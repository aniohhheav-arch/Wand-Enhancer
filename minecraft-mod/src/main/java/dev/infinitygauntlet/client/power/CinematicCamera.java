package dev.infinitygauntlet.client.power;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.vfx.CameraEffects;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.config.ClientConfig;
import dev.infinitygauntlet.network.CinematicPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/**
 * Takes over the camera for Power Stone finishers. The shot is placed after vanilla camera setup but before frustum
 * culling (from {@link ViewportEvent.ComputeFov}, the first hook in between), eases in from and back out to the
 * player's own view, avoids clipping into terrain, and frames the action with letterbox bars.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class CinematicCamera {
    private static final int BLEND_IN = 5;
    private static final int BLEND_OUT = 7;
    @Nullable
    private static CinematicPayload shot;
    private static int age;
    private static Vec3 lastFocus = Vec3.ZERO;

    public static void start(CinematicPayload payload) {
        if (!ClientConfig.cinematicCamera()) {
            return;
        }
        shot = payload;
        age = 0;
        lastFocus = payload.focus();
    }

    public static void clear() {
        shot = null;
    }

    public static boolean active() {
        return shot != null;
    }

    /** 0..1 how much of the frame belongs to the shot. */
    private static float weight(float pt) {
        if (shot == null) {
            return 0;
        }
        float a = age + pt;
        return VfxDraw.smooth(a / BLEND_IN) * VfxDraw.smooth((shot.ticks() - a) / BLEND_OUT);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (shot != null && !Minecraft.getInstance().isPaused() && ++age > shot.ticks()) {
            shot = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onFov(ViewportEvent.ComputeFov event) {
        if (shot == null || !event.usedConfiguredFov()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float pt = (float) event.getPartialTick();
        float w = weight(pt);
        if (w <= 0) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 focus = shot.focus();
        if (shot.focusEntity() >= 0) {
            Entity e = mc.level.getEntity(shot.focusEntity());
            if (e != null) {
                focus = e.getPosition(pt).add(0, e.getBbHeight() * 0.55, 0);
            }
        }
        lastFocus = focus;
        float t = Mth.clamp((age + pt) / shot.ticks(), 0, 1);
        float ease = t * t * (3 - 2 * t);
        Vec3 wanted = focus.add(shot.from().lerp(shot.to(), ease));
        BlockHitResult hit = mc.level.clip(new ClipContext(focus, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            wanted = hit.getLocation().add(focus.subtract(wanted).normalize().scale(0.35));
        }
        Vec3 pos = camera.getPosition().lerp(wanted, w);
        Vec3 d = focus.subtract(wanted);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        Vec3 shake = CameraEffects.shakeAngles(pt);
        float roll = shot.roll() * Mth.sin(t * Mth.PI) + (float) shake.z;
        float camYaw = camera.getYRot() + Mth.wrapDegrees(yaw + (float) shake.x - camera.getYRot()) * w;
        float camPitch = Mth.lerp(w, camera.getXRot(), pitch + (float) shake.y);
        camera.setPosition(pos);
        camera.setRotation(camYaw, camPitch, roll * w);
        if (w > 0.15f) {
            camera.detached = true;
        }
        event.setFOV(event.getFOV() * Mth.lerp(w * Mth.sin(t * Mth.PI), 1f, shot.fovMul()));
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        if (weight(event.getPartialTick()) > 0.15f) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (shot != null && (event.getName().equals(VanillaGuiLayers.CROSSHAIR) || event.getName().equals(VanillaGuiLayers.HOTBAR)
                || event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR) || event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)
                || event.getName().equals(VanillaGuiLayers.FOOD_LEVEL) || event.getName().equals(VanillaGuiLayers.ARMOR_LEVEL))) {
            event.setCanceled(true);
        }
    }

    /** Letterbox bars, registered as a GUI layer. */
    public static void renderBars(GuiGraphics g, DeltaTracker delta) {
        float w = weight(delta.getGameTimeDeltaPartialTick(true));
        if (w <= 0) {
            return;
        }
        int bar = (int) (g.guiHeight() * 0.1f * w);
        g.fill(0, 0, g.guiWidth(), bar, 0xFF000000);
        g.fill(0, g.guiHeight() - bar, g.guiWidth(), g.guiHeight(), 0xFF000000);
    }

    private CinematicCamera() {
    }
}
