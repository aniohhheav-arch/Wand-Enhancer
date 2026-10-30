package dev.infinitygauntlet.client.nexus;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.nexus.NexusBlockEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Live camera feeds for the Consciousness Nexus. Before the main frame is drawn, the level is rendered a second time
 * from each linked creature's eyes into its own offscreen framebuffer, by temporarily swapping the main render target
 * and camera entity. The Nexus renderer then samples those framebuffers on its holographic screens.
 * <p>
 * Cost control: only feeds of nexuses near the player are drawn, at reduced resolution; the operator's active feed
 * renders every frame, the active feed of other nexuses every third frame and secondary feeds every tenth.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class FeedRenderer {
    private static final int WIDTH = 512;
    private static final double RANGE = 40;
    private static final int MAX_FEEDS = 4;

    private static final class Feed {
        TextureTarget target;
        RenderType type;
        long lastRendered = -1;
    }

    private static final Map<Integer, Feed> FEEDS = new HashMap<>();
    private static boolean rendering;
    private static int renderingEntity = -1;
    private static long frame;

    /** True while a feed is being drawn; renderers must not sample feed textures then (it would read the bound target). */
    public static boolean isRendering() {
        return rendering;
    }

    /** The entity whose eyes the current feed is rendered from, or -1. */
    public static int renderingEntity() {
        return rendering ? renderingEntity : -1;
    }

    /** Render type that samples the feed of {@code entityId}, or null if that feed has no picture yet. */
    @Nullable
    public static RenderType feedType(int entityId) {
        Feed f = FEEDS.get(entityId);
        return f == null || f.lastRendered < 0 ? null : f.type;
    }

    /** Width / height of the feed pictures (follows the window aspect, since the projection does). */
    public static float aspect() {
        var w = Minecraft.getInstance().getWindow();
        return w.getHeight() == 0 ? 16 / 9f : (float) w.getWidth() / w.getHeight();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) {
            return;
        }
        frame++;
        List<int[]> wanted = new ArrayList<>(); // {entityId, interval}
        for (NexusBlockEntity nexus : NexusBlockEntity.CLIENT_LOADED) {
            if (nexus.getLevel() != mc.level || nexus.state() == NexusBlockEntity.STANDBY
                    || nexus.getBlockPos().getCenter().distanceTo(mc.gameRenderer.getMainCamera().getPosition()) > RANGE) {
                continue;
            }
            List<NexusBlockEntity.Link> links = nexus.links();
            for (int i = 0; i < links.size(); i++) {
                int id = links.get(i).entityId();
                boolean operatorFeed = RemoteControlClient.isControlling() && id == RemoteControlClient.activeEntity();
                int interval = operatorFeed ? 1 : i == nexus.activeIndex() ? 3 : 10;
                wanted.add(new int[]{id, interval});
            }
        }
        wanted.sort((a, b) -> Integer.compare(a[1], b[1]));
        Set<Integer> keep = new HashSet<>();
        for (int k = 0; k < Math.min(MAX_FEEDS, wanted.size()); k++) {
            int id = wanted.get(k)[0];
            int interval = wanted.get(k)[1];
            keep.add(id);
            Entity e = mc.level.getEntity(id);
            if (e == null) {
                continue;
            }
            Feed feed = FEEDS.computeIfAbsent(id, i -> new Feed());
            if (feed.lastRendered >= 0 && (frame + id) % interval != 0) {
                continue;
            }
            ensureTarget(feed);
            render(mc, e, feed.target, event.getPartialTick());
            feed.lastRendered = frame;
        }
        FEEDS.entrySet().removeIf(en -> {
            if (!keep.contains(en.getKey())) {
                if (en.getValue().target != null) {
                    en.getValue().target.destroyBuffers();
                }
                return true;
            }
            return false;
        });
    }

    private static void ensureTarget(Feed feed) {
        int height = Math.max(64, Math.round(WIDTH / aspect()));
        if (feed.target != null && feed.target.height == height) {
            return;
        }
        if (feed.target != null) {
            feed.target.destroyBuffers();
        }
        TextureTarget target = new TextureTarget(WIDTH, height, true, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
        feed.target = target;
        feed.type = RenderType.create(InfinityGauntlet.MOD_ID + "_feed", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.EmptyTextureStateShard(
                                () -> RenderSystem.setShaderTexture(0, target.getColorTextureId()), () -> {
                                }))
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                        .createCompositeState(false));
    }

    private static void render(Minecraft mc, Entity e, TextureTarget target, DeltaTracker delta) {
        RenderTarget main = mc.mainRenderTarget;
        Entity oldCamera = mc.cameraEntity;
        CameraType oldType = mc.options.getCameraType();
        boolean oldBob = mc.options.bobView().get();
        boolean oldHideGui = mc.options.hideGui;
        float yRot = e.getYRot(), yRotO = e.yRotO, xRot = e.getXRot(), xRotO = e.xRotO;
        float headRot = 0, headRotO = 0;
        LivingEntity living = e instanceof LivingEntity l ? l : null;
        if (living != null) {
            headRot = living.yHeadRot;
            headRotO = living.yHeadRotO;
        }
        rendering = true;
        renderingEntity = e.getId();
        try {
            mc.mainRenderTarget = target;
            target.bindWrite(true);
            RenderSystem.clearColor(0.02f, 0.03f, 0.06f, 1f);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            mc.cameraEntity = e;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.bobView().set(false);
            mc.options.hideGui = true;
            mc.gameRenderer.setRenderHand(false);
            if (RemoteControlClient.isControlling() && e.getId() == RemoteControlClient.activeEntity()) {
                // predicted view: the operator's latest look, not the last rotation echoed back by the server
                float yaw = RemoteControlClient.yaw(), pitch = RemoteControlClient.pitch();
                e.setYRot(yaw);
                e.yRotO = yaw;
                e.setXRot(pitch);
                e.xRotO = pitch;
                if (living != null) {
                    living.yHeadRot = yaw;
                    living.yHeadRotO = yaw;
                }
            }
            mc.gameRenderer.renderLevel(delta);
        } catch (RuntimeException ex) {
            InfinityGauntlet.LOGGER.error("Nexus feed render failed", ex);
        } finally {
            e.setYRot(yRot);
            e.yRotO = yRotO;
            e.setXRot(xRot);
            e.xRotO = xRotO;
            if (living != null) {
                living.yHeadRot = headRot;
                living.yHeadRotO = headRotO;
            }
            mc.gameRenderer.setRenderHand(true);
            mc.options.hideGui = oldHideGui;
            mc.options.bobView().set(oldBob);
            mc.options.setCameraType(oldType);
            mc.cameraEntity = oldCamera;
            mc.mainRenderTarget = main;
            main.bindWrite(true);
            rendering = false;
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        FEEDS.values().forEach(f -> {
            if (f.target != null) {
                f.target.destroyBuffers();
            }
        });
        FEEDS.clear();
        NexusBlockEntity.CLIENT_LOADED.clear();
    }

    private FeedRenderer() {
    }
}
