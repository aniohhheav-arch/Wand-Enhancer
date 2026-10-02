package com.infinitemultiverse.client.cinematic;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.client.vfx.ClientVfx;
import com.infinitemultiverse.client.vfx.VfxSpawner;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.network.ScenePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

/** Runs every rendered scene: ticks, world geometry, cutscene camera, letterbox, flashes, titles and shake. */
@EventBusSubscriber(modid = InfiniteMultiverse.MOD_ID, value = Dist.CLIENT)
public final class SceneManager {
    public static final ResourceLocation OVERLAY_ID = InfiniteMultiverse.id("cinematic_overlay");
    private static final Map<ResourceLocation, Function<ScenePayload, Scene>> FACTORIES = new HashMap<>();
    private static final List<Scene> ACTIVE = new ArrayList<>();
    private static final int MAX_SCENES = 96;
    @Nullable
    private static Scene cutscene;
    private static float letterbox;
    private static float letterboxPrev;

    static {
        CursedScenes.register(FACTORIES);
        HeroScenes.register(FACTORIES);
        MysticScenes.register(FACTORIES);
        CosmicScenes.register(FACTORIES);
    }

    private SceneManager() {
    }

    public static void start(ScenePayload payload) {
        if (payload.duration() <= 0) {
            // Stop request: end every running scene of this id owned by this entity.
            ACTIVE.removeIf(scene -> {
                boolean match = scene.p.scene().equals(payload.scene()) && scene.p.entityId() == payload.entityId();
                if (match && scene == cutscene) {
                    cutscene = null;
                }
                return match;
            });
            return;
        }
        Function<ScenePayload, Scene> factory = FACTORIES.get(payload.scene());
        Minecraft mc = Minecraft.getInstance();
        if (factory == null || mc.level == null) {
            return;
        }
        double max = MultiverseConfig.CLIENT.vfxMaxDistance.get() + payload.param() * 2;
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(payload.origin()) > max * max) {
            return;
        }
        if (ACTIVE.size() >= MAX_SCENES) {
            ACTIVE.remove(0);
        }
        Scene scene = factory.apply(payload);
        ACTIVE.add(scene);
        if (Boolean.getBoolean("infinitemultiverse.debugScenes")) {
            InfiniteMultiverse.LOGGER.info("scene {} dur={} active={}", payload.scene(), payload.duration(), ACTIVE.size());
        }
        if (scene.wantsCamera() && cutscene == null && allowCutscene(scene)) {
            cutscene = scene;
        }
    }

    private static boolean allowCutscene(Scene scene) {
        if (!MultiverseConfig.CLIENT.cutscenes.get()) {
            return false;
        }
        return scene.localIsCaster() || MultiverseConfig.CLIENT.othersCutscenes.get();
    }

    /** A broken effect must never take the game down: log it once and drop the scene. */
    private static void fail(Scene scene, RuntimeException e) {
        InfiniteMultiverse.LOGGER.error("Scene {} failed and was removed", scene.p.scene(), e);
        ACTIVE.remove(scene);
        if (scene == cutscene) {
            cutscene = null;
        }
    }

    @Nullable
    public static Scene cutscene() {
        return cutscene;
    }

    public static boolean inCutscene() {
        return cutscene != null;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        letterboxPrev = letterbox;
        letterbox = Mth.approach(letterbox, cutscene != null ? 1f : 0f, 0.12f);
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        VfxSpawner spawner = ACTIVE.isEmpty() ? null : ClientVfx.spawner();
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Scene scene = ACTIVE.get(i);
            try {
                scene.tick(spawner);
            } catch (RuntimeException e) {
                fail(scene, e);
                continue;
            }
            scene.age++;
            if (scene.done()) {
                ACTIVE.remove(i);
                if (scene == cutscene) {
                    cutscene = null;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        cutscene = null;
        letterbox = letterboxPrev = 0;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean orbit = mc.level != null && mc.level.dimension() == com.infinitemultiverse.cosmic.space.Space.ORBIT;
        if (ACTIVE.isEmpty() && !orbit) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Vec3 look = new Vec3(event.getCamera().getLookVector());
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // Darkness first so light composites over it.
        FxDraw ink = new FxDraw(pose, buffers.getBuffer(RenderType.debugQuads()), true, cam, look);
        if (orbit) {
            OrbitSky.render(ink.glow(), cam, mc.level.getGameTime() + pt);
        }
        for (Scene scene : List.copyOf(ACTIVE)) {
            try {
                scene.render(ink.glow(), pt);
            } catch (RuntimeException e) {
                fail(scene, e);
            }
        }
        buffers.endBatch(RenderType.debugQuads());
        FxDraw glow = new FxDraw(pose, buffers.getBuffer(RenderType.lightning()), false, cam, look);
        if (orbit) {
            OrbitSky.render(glow.glow(), cam, mc.level.getGameTime() + pt);
        }
        for (Scene scene : List.copyOf(ACTIVE)) {
            try {
                scene.render(glow.glow(), pt);
            } catch (RuntimeException e) {
                fail(scene, e);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    // ------------------------------------------------------------- camera

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ACTIVE.isEmpty() || !MultiverseConfig.CLIENT.screenShake.get()) {
            return;
        }
        float pt = (float) event.getPartialTick();
        float shake = 0;
        for (Scene scene : ACTIVE) {
            shake += scene.shake(pt);
        }
        if (shake > 0.01f) {
            shake = Math.min(shake, 6f);
            float time = (Minecraft.getInstance().level.getGameTime() + pt) * 1.7f;
            event.setYaw(event.getYaw() + (float) Math.sin(time * 3.1) * shake);
            event.setPitch(event.getPitch() + (float) Math.cos(time * 2.7) * shake * 0.7f);
            event.setRoll(event.getRoll() + (float) Math.sin(time * 1.9) * shake * 0.5f);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        float pt = (float) event.getPartialTick();
        double fov = event.getFOV();
        for (Scene scene : ACTIVE) {
            fov *= scene.fov(pt);
        }
        event.setFOV(fov);
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (cutscene != null && cutscene.localIsCaster()) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
            event.getInput().up = event.getInput().down = event.getInput().left = event.getInput().right = false;
        }
    }

    /** The shatter flash replaces vanilla's "Loading terrain" screen when a cutscene crosses dimensions (Mirror Dimension). */
    @SubscribeEvent
    public static void onScreen(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen && hasRecent(
                com.infinitemultiverse.core.cinematic.SceneIds.MIRROR_ENTER, com.infinitemultiverse.core.cinematic.SceneIds.MIRROR_EXIT)) {
            event.setCanceled(true);
        }
    }

    private static boolean hasRecent(ResourceLocation... ids) {
        for (Scene scene : ACTIVE) {
            for (ResourceLocation id : ids) {
                if (scene.p.scene().equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The first-person hand must not float in front of a cutscene camera. */
    @SubscribeEvent
    public static void onRenderHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        if (cutscene != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (cutscene != null && !event.getName().equals(OVERLAY_ID)) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------- overlay

    public static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        float flash = 0;
        for (Scene scene : List.copyOf(ACTIVE)) {
            try {
                scene.overlay(g, pt, scene == cutscene);
                flash = Math.max(flash, scene.flash(pt));
            } catch (RuntimeException e) {
                fail(scene, e);
            }
        }
        float bars = Mth.lerp(pt, letterboxPrev, letterbox);
        if (bars > 0.001f) {
            int bh = (int) (h * 0.12f * bars * (2 - bars));
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        if (flash > 0.01f) {
            g.fill(0, 0, w, h, FxDraw.argb(0xFFFFFF, Math.min(1, flash)));
        }
    }

    /** Large centred title card with letter-spaced subtitle; alpha 0..1. */
    public static void title(GuiGraphics g, Component title, @Nullable Component subtitle, int color, float alpha, float scale, float yFrac) {
        if (alpha <= 0.02f) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int a = Mth.clamp((int) (alpha * 255), 4, 255) << 24;
        g.pose().pushPose();
        g.pose().translate(g.guiWidth() / 2f, g.guiHeight() * yFrac, 400);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, title, -font.width(title) / 2, -4, a | (color & 0xFFFFFF), true);
        g.pose().popPose();
        if (subtitle != null) {
            g.pose().pushPose();
            g.pose().translate(g.guiWidth() / 2f, g.guiHeight() * yFrac + scale * 7 + 4, 400);
            float sub = Math.max(1f, scale * 0.45f);
            g.pose().scale(sub, sub, 1);
            g.drawString(font, subtitle, -font.width(subtitle) / 2, 0, a | 0xFFFFFF, true);
            g.pose().popPose();
        }
    }
}
