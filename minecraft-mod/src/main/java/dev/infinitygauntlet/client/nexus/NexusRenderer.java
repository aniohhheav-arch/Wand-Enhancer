package dev.infinitygauntlet.client.nexus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.Keybinds;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.nexus.ControlProfiles;
import dev.infinitygauntlet.nexus.NexusBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.List;
import java.util.Locale;

/**
 * Renders the animated Nexus model, its holographic screen and the floating interface around it.
 * <p>
 * Screen states: STANDBY (dark, rotating Mind glyph), CONNECTING (CRT power-on: a line opens into the live picture
 * through a flash and fading static), LIVE (feed with scanlines and vignette; golden interference grows as stability
 * drops, with a blinking warning below 30%), DISCONNECTING (golden interference swamps the picture, the signal is
 * lost to static, then the screen collapses to a line and goes dark).
 * <p>
 * Interface: left panel with entity, health, distance, stability, link duration and coordinates; secondary feed
 * monitors on the right (multi-feed); ability status and the emergency disconnect control along the bottom.
 */
public class NexusRenderer extends GeoBlockRenderer<NexusBlockEntity> {
    private static final float SCREEN_W = 4f, SCREEN_H = 2.25f;
    private static final float SCREEN_Y = 54 / 16f, SCREEN_Z = -6 / 16f + 0.03f;
    private static final float TEXT = 1 / 110f;
    private static final int GOLD = 0xFFD84A, BLUE = 0x7FB8FF, RED = 0xFF4040, DIM = 0x8C9AB8;

    public NexusRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(InfinityGauntlet.id("consciousness_nexus")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(NexusBlockEntity nexus) {
        return new AABB(nexus.getBlockPos()).inflate(4, 1, 4).expandTowards(0, 4, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(NexusBlockEntity nexus) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(NexusBlockEntity nexus, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(nexus, pt, pose, buffers, light, overlay);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-nexus.facing().toYRot()));
        drawInterface(nexus, pt, pose, buffers);
        pose.popPose();
    }

    // ------------------------------------------------------------------ screen

    private void drawInterface(NexusBlockEntity nexus, float pt, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        float t = (nexus.getLevel() == null ? 0 : nexus.getLevel().getGameTime()) + pt;
        byte state = nexus.state();
        float age = nexus.stateAge(pt);
        NexusBlockEntity.Link link = nexus.activeLink();
        Entity mob = link == null ? null : mc.level.getEntity(link.entityId());
        float stability = link == null ? 1 : link.stability();

        float sx = 1f, sy = 1f;
        float flash = 0, staticAmount = 0, gold = 0;
        boolean picture = state == NexusBlockEntity.LIVE || state == NexusBlockEntity.CONNECTING;
        String overlayText = null;
        switch (state) {
            case NexusBlockEntity.CONNECTING -> {
                sy = Math.max(0.012f, VfxDraw.smooth((age - 2) / 9f));
                sx = VfxDraw.smooth(age / 4f);
                flash = Math.max(0, 1 - Math.abs(age - 9) / 5f);
                staticAmount = 1 - VfxDraw.smooth((age - 8) / 18f);
            }
            case NexusBlockEntity.LIVE -> {
                staticAmount = (1 - stability) * 0.55f;
                gold = (1 - stability) * 0.4f;
            }
            case NexusBlockEntity.DISCONNECTING -> {
                picture = age < 18;
                gold = VfxDraw.smooth(age / 14f);
                staticAmount = VfxDraw.smooth((age - 8) / 10f);
                if (age >= 16 && age < 32) {
                    overlayText = nexus.lastSignal().isEmpty() ? "NO SIGNAL" : nexus.lastSignal();
                }
                if (age >= 30) {
                    float off = VfxDraw.smooth((age - 30) / 10f);
                    sy = Math.max(0.01f, 1 - off);
                    sx = 1 - VfxDraw.smooth((age - 38) / 6f);
                    flash = off * (1 - off) * 2;
                }
            }
            default -> {
            }
        }

        float w = SCREEN_W * sx, h = SCREEN_H * sy;
        PoseStack.Pose p = pose.last();
        // backplate
        quad(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), -SCREEN_W / 2, SCREEN_Y - SCREEN_H / 2, SCREEN_W, SCREEN_H,
                SCREEN_Z - 0.01f, 0, 0, 1, 1, 0.01f, 0.015f, 0.03f, 0.92f);
        if (w > 0.01f) {
            float x0 = -w / 2, y0 = SCREEN_Y - h / 2;
            if (state == NexusBlockEntity.STANDBY) {
                drawStandby(nexus, p, pose, buffers, t);
            } else {
                RenderType feed = mob == null || FeedRenderer.isRendering() ? null : FeedRenderer.feedType(mob.getId());
                if (picture && feed != null) {
                    float[] uv = crop(SCREEN_W / SCREEN_H);
                    quad(p, buffers.getBuffer(feed), x0, y0, w, h, SCREEN_Z, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
                } else if (picture) {
                    staticAmount = Math.max(staticAmount, 0.7f);
                }
                float jitter = VfxDraw.hash((long) t, 1, 2);
                if (staticAmount > 0.01f) {
                    float u = jitter, v = VfxDraw.hash((long) t, 3, 4);
                    float sr = 0.6f + 0.4f * gold, sg = 0.6f + 0.25f * gold, sb = 0.6f - 0.4f * gold;
                    quad(p, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.STATIC)), x0, y0, w, h, SCREEN_Z + 0.004f, u, v, u + 1.6f,
                            v + 0.9f, sr, sg, sb, staticAmount);
                }
                if (gold > 0.01f) {
                    // golden interference: bright horizontal tearing bands rolling down the picture
                    VertexConsumer vc = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE));
                    for (int k = 0; k < 6; k++) {
                        float band = (t * 0.07f + VfxDraw.hash((long) (t / 6) * 31 + k, 7, 0)) % 1f;
                        float bh = h * (0.02f + 0.06f * VfxDraw.hash((long) (t / 3) * 31 + k, 8, 0));
                        float off = (VfxDraw.hash((long) t * 31 + k, 9, 0) - 0.5f) * 0.15f * gold;
                        quad(p, vc, x0 + off, y0 + band * (h - bh), w, bh, SCREEN_Z + 0.006f, 0, 0, 1, 1, 1f, 0.75f, 0.2f, 0.55f * gold);
                    }
                }
                quad(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.SCANLINES)), x0, y0, w, h, SCREEN_Z + 0.008f, 0, (t * 0.01f) % 1f,
                        8, 4.5f + (t * 0.01f) % 1f, 0, 0.02f, 0.05f, 1f);
                quad(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.VIGNETTE)), x0, y0, w, h, SCREEN_Z + 0.009f, 0, 0, 1, 1,
                        0, 0, 0, 0.85f);
                if (flash > 0.01f) {
                    quad(p, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE)), x0, y0, w, h, SCREEN_Z + 0.012f, 0, 0, 1, 1,
                            0.85f, 0.95f, 1f, flash);
                }
                if (state == NexusBlockEntity.LIVE || state == NexusBlockEntity.CONNECTING && age > 14) {
                    drawFeedOverlay(pose, buffers, mob, stability, t);
                }
                if (overlayText != null) {
                    text(pose, buffers, Component.literal(overlayText), 0, SCREEN_Y + 0.12f, 2.2f, 0xFFE070, true);
                    text(pose, buffers, Component.translatable("nexus.infinitygauntlet.connection_terminated"), 0, SCREEN_Y - 0.15f, 1.1f, 0xC8D0E8, true);
                }
            }
        }
        if (state != NexusBlockEntity.STANDBY) {
            drawPanels(nexus, pose, buffers, mob, link, stability, t);
        }
    }

    private void drawStandby(NexusBlockEntity nexus, PoseStack.Pose p, PoseStack pose, MultiBufferSource buffers, float t) {
        pose.pushPose();
        pose.translate(0, SCREEN_Y, SCREEN_Z + 0.004f);
        pose.mulPose(Axis.ZP.rotation(t * 0.01f));
        quad(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_MIND)), -0.95f, -0.95f, 1.9f, 1.9f, 0, 0, 0, 1, 1,
                1f, 0.8f, 0.25f, 0.35f + 0.1f * Mth.sin(t * 0.08f));
        pose.popPose();
        pose.pushPose();
        pose.translate(0, SCREEN_Y, SCREEN_Z + 0.005f);
        pose.mulPose(Axis.ZP.rotation(-t * 0.016f));
        quad(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_SPACE)), -0.6f, -0.6f, 1.2f, 1.2f, 0, 0, 0, 1, 1,
                0.2f, 0.45f, 1f, 0.4f);
        pose.popPose();
        text(pose, buffers, Component.translatable("nexus.infinitygauntlet.standby"), 0, SCREEN_Y - 0.06f, 2.0f, GOLD, true);
        Component owner = nexus.owner() == null ? Component.translatable("nexus.infinitygauntlet.unbound")
                : Component.translatable("nexus.infinitygauntlet.operator", nexus.ownerName());
        text(pose, buffers, owner, 0, SCREEN_Y - 0.4f, 1.0f, DIM, true);
    }

    private void drawFeedOverlay(PoseStack pose, MultiBufferSource buffers, Entity mob, float stability, float t) {
        float left = -SCREEN_W / 2 + 0.12f, top = SCREEN_Y + SCREEN_H / 2 - 0.1f;
        boolean blink = ((int) (t / 10)) % 2 == 0;
        textLeft(pose, buffers, Component.literal(blink ? "● LIVE FEED" : "  LIVE FEED"), left, top, 1.15f, RED);
        if (mob != null) {
            textRight(pose, buffers, mob.getDisplayName(), SCREEN_W / 2 - 0.12f, top, 1.15f, GOLD);
        }
        if (stability < 0.3f && blink) {
            text(pose, buffers, Component.translatable("nexus.infinitygauntlet.unstable"), 0, SCREEN_Y - SCREEN_H / 2 + 0.25f, 1.5f, RED, true);
        }
        // reticle
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE));
        PoseStack.Pose p = pose.last();
        quad(p, vc, -0.09f, SCREEN_Y - 0.005f, 0.06f, 0.01f, SCREEN_Z + 0.01f, 0, 0, 1, 1, 1, 1, 1, 0.6f);
        quad(p, vc, 0.03f, SCREEN_Y - 0.005f, 0.06f, 0.01f, SCREEN_Z + 0.01f, 0, 0, 1, 1, 1, 1, 1, 0.6f);
        quad(p, vc, -0.005f, SCREEN_Y + 0.03f, 0.01f, 0.06f, SCREEN_Z + 0.01f, 0, 0, 1, 1, 1, 1, 1, 0.6f);
        quad(p, vc, -0.005f, SCREEN_Y - 0.09f, 0.01f, 0.06f, SCREEN_Z + 0.01f, 0, 0, 1, 1, 1, 1, 1, 0.6f);
    }

    // ------------------------------------------------------------------ side panels

    private void drawPanels(NexusBlockEntity nexus, PoseStack pose, MultiBufferSource buffers, Entity mob, NexusBlockEntity.Link link, float stability,
                            float t) {
        PoseStack.Pose p = pose.last();
        float top = SCREEN_Y + SCREEN_H / 2, bottom = SCREEN_Y - SCREEN_H / 2;
        float lx = -3.6f, lw = 1.45f;
        panel(p, buffers, lx, bottom, lw, SCREEN_H, stability < 0.3f && ((int) (t / 8)) % 2 == 0);
        float y = top - 0.12f;
        float gap = 0.135f;
        textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.link"), lx + 0.08f, y, 1.0f, GOLD);
        y -= gap * 1.2f;
        if (mob != null && link != null) {
            long now = nexus.getLevel().getGameTime();
            textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.entity", mob.getDisplayName()), lx + 0.08f, y, 0.9f, 0xFFFFFF);
            y -= gap;
            if (mob instanceof LivingEntity living) {
                textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.health",
                        String.format(Locale.ROOT, "%.0f/%.0f", living.getHealth(), living.getMaxHealth())), lx + 0.08f, y, 0.9f, 0xFFFFFF);
                y -= gap * 0.75f;
                bar(p, buffers, lx + 0.08f, y, lw - 0.16f, living.getHealth() / living.getMaxHealth(), 0.9f, 0.2f, 0.2f);
                y -= gap * 0.6f;
            }
            double dist = mob.position().distanceTo(nexus.screenCenter());
            textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.distance", String.format(Locale.ROOT, "%.1f", dist)),
                    lx + 0.08f, y, 0.9f, 0xFFFFFF);
            y -= gap;
            int sc = stability > 0.6f ? 0x60FF80 : stability > 0.3f ? 0xFFD040 : RED;
            textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.stability", Math.round(stability * 100)), lx + 0.08f, y, 0.9f, sc);
            y -= gap * 0.75f;
            bar(p, buffers, lx + 0.08f, y, lw - 0.16f, stability, (sc >> 16 & 255) / 255f, (sc >> 8 & 255) / 255f, (sc & 255) / 255f);
            y -= gap * 0.6f;
            long secs = Math.max(0, (now - link.started()) / 20);
            textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.duration", String.format(Locale.ROOT, "%02d:%02d", secs / 60, secs % 60)),
                    lx + 0.08f, y, 0.9f, 0xFFFFFF);
            y -= gap;
            textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.coords", mob.getBlockX(), mob.getBlockY(), mob.getBlockZ()),
                    lx + 0.08f, y, 0.8f, DIM);
            y -= gap;
            textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.controls",
                    Component.translatable("control.infinitygauntlet." + ControlProfiles.attackKey(mob)),
                    Component.translatable("control.infinitygauntlet." + ControlProfiles.specialKey(mob))), lx + 0.08f, y, 0.75f, BLUE);
        } else {
            textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.no_entity"), lx + 0.08f, y, 0.9f, DIM);
        }

        // multi-feed monitors
        float rx = 2.15f, rw = 1.45f, th = rw / (SCREEN_W / SCREEN_H);
        List<NexusBlockEntity.Link> links = nexus.links();
        int slot = 0;
        for (int i = 0; i < links.size() && slot < 2; i++) {
            if (i == nexus.activeIndex()) {
                continue;
            }
            float ty = top - (slot + 1) * (th + 0.08f) + 0.08f - 0.12f;
            Entity e = Minecraft.getInstance().level.getEntity(links.get(i).entityId());
            panel(p, buffers, rx - 0.03f, ty - 0.03f, rw + 0.06f, th + 0.18f, false);
            RenderType feed = e == null || FeedRenderer.isRendering() ? null : FeedRenderer.feedType(e.getId());
            if (feed != null) {
                float[] uv = crop(SCREEN_W / SCREEN_H);
                quad(p, buffers.getBuffer(feed), rx, ty, rw, th, SCREEN_Z, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
            } else {
                float u = VfxDraw.hash((long) t, i, 5);
                quad(p, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.STATIC)), rx, ty, rw, th, SCREEN_Z, u, 0, u + 1, 0.6f, 0.5f, 0.5f, 0.5f, 0.6f);
            }
            quad(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.SCANLINES)), rx, ty, rw, th, SCREEN_Z + 0.004f, 0, 0, 4, 2.2f, 0, 0.02f, 0.05f, 1f);
            textLeft(pose, buffers, Component.literal("FEED " + (i + 1) + "  ").append(e == null ? Component.literal("—") : e.getDisplayName()),
                    rx + 0.03f, ty + th + 0.13f, 0.75f, GOLD);
            slot++;
        }
        if (slot == 0) {
            float ty = top - th - 0.12f;
            panel(p, buffers, rx - 0.03f, ty - 0.03f, rw + 0.06f, th + 0.18f, false);
            text(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.feed_free"), rx + rw / 2, ty + th / 2, 0.8f, DIM, true);
        }
        textLeft(pose, buffers, Component.translatable("nexus.infinitygauntlet.panel.feeds", links.size(), nexus.activeIndex() + 1),
                rx, bottom + 0.12f, 0.8f, BLUE);

        // ability strip and emergency disconnect
        float by = bottom - 0.46f, bw = 7.2f, bx = -3.6f;
        panel(p, buffers, bx, by, bw, 0.38f, false);
        long now = nexus.getLevel().getGameTime();
        String[] names = {"blink", "override", "extract"};
        long[] ready = {nexus.blinkReady(), nexus.overrideReady(), nexus.extractReady()};
        Component[] keys = {Keybinds.REMOTE_BLINK.getTranslatedKeyMessage(), Keybinds.NEURAL_OVERRIDE.getTranslatedKeyMessage(),
                Keybinds.PORTAL_EXTRACTION.getTranslatedKeyMessage()};
        for (int k = 0; k < 3; k++) {
            float cx = bx + 0.15f + k * 1.55f;
            long left = ready[k] - now;
            boolean active = k == 1 && now < nexus.overrideUntil();
            Component status = active ? Component.translatable("nexus.infinitygauntlet.ability.active")
                    : left > 0 ? Component.literal(String.format(Locale.ROOT, "%.1fs", left / 20f))
                    : Component.translatable("nexus.infinitygauntlet.ability.ready");
            int color = active ? GOLD : left > 0 ? DIM : 0x60FF80;
            textLeft(pose, buffers, Component.literal("[").append(keys[k]).append("] ")
                    .append(Component.translatable("nexus.infinitygauntlet.ability." + names[k])), cx, by + 0.29f, 0.8f, BLUE);
            textLeft(pose, buffers, status, cx, by + 0.15f, 0.8f, color);
        }
        float ex = bx + bw - 2.35f;
        boolean pulse = ((int) (t / 12)) % 2 == 0;
        quad(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), ex, by + 0.05f, 2.25f, 0.28f, SCREEN_Z + 0.004f, 0, 0, 1, 1,
                0.55f, 0.04f, 0.04f, pulse ? 0.85f : 0.65f);
        text(pose, buffers, Component.translatable("nexus.infinitygauntlet.emergency", Keybinds.EMERGENCY_DISCONNECT.getTranslatedKeyMessage()),
                ex + 1.125f, by + 0.19f, 0.85f, 0xFFFFFF, true);
    }

    private static void panel(PoseStack.Pose p, MultiBufferSource buffers, float x, float y, float w, float h, boolean alarm) {
        quad(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), x, y, w, h, SCREEN_Z - 0.005f, 0, 0, 1, 1,
                alarm ? 0.25f : 0.02f, 0.03f, alarm ? 0.03f : 0.07f, 0.72f);
        VertexConsumer edge = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE));
        float r = alarm ? 1f : 0.85f, g = alarm ? 0.25f : 0.65f, b = alarm ? 0.2f : 0.2f;
        quad(p, edge, x, y + h - 0.012f, w, 0.012f, SCREEN_Z, 0, 0, 1, 1, r, g, b, 0.9f);
        quad(p, edge, x, y, w, 0.012f, SCREEN_Z, 0, 0, 1, 1, r, g, b, 0.9f);
        quad(p, edge, x, y, 0.012f, h, SCREEN_Z, 0, 0, 1, 1, 0.2f, 0.45f, 1f, 0.9f);
        quad(p, edge, x + w - 0.012f, y, 0.012f, h, SCREEN_Z, 0, 0, 1, 1, 0.2f, 0.45f, 1f, 0.9f);
    }

    private static void bar(PoseStack.Pose p, MultiBufferSource buffers, float x, float y, float w, float fraction, float r, float g, float b) {
        quad(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), x, y, w, 0.035f, SCREEN_Z + 0.002f, 0, 0, 1, 1, 0.1f, 0.1f, 0.12f, 0.9f);
        quad(p, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE)), x, y, w * Mth.clamp(fraction, 0, 1), 0.035f, SCREEN_Z + 0.004f, 0, 0, 1, 1,
                r, g, b, 0.95f);
    }

    // ------------------------------------------------------------------ primitives

    /** Feed pictures follow the window aspect; crop them to the screen aspect. Returns {u0, vTop, u1, vBottom}. */
    private static float[] crop(float screenAspect) {
        float a = FeedRenderer.aspect();
        float u0 = 0, u1 = 1, vb = 0, vt = 1;
        if (a > screenAspect) {
            float f = screenAspect / a;
            u0 = (1 - f) / 2;
            u1 = 1 - u0;
        } else {
            float f = a / screenAspect;
            vb = (1 - f) / 2;
            vt = 1 - vb;
        }
        // framebuffer textures are stored bottom-up
        return new float[]{u0, vt, u1, vb};
    }

    /** Axis-aligned quad in the interface plane; (u0, v0) at the top-left corner. */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, float x, float y, float w, float h, float z, float u0, float v0, float u1, float v1,
                             float r, float g, float b, float a) {
        Matrix4f m = p.pose();
        vc.addVertex(m, x, y, z).setUv(u0, v1).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y, z).setUv(u1, v1).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y + h, z).setUv(u1, v0).setColor(r, g, b, a);
        vc.addVertex(m, x, y + h, z).setUv(u0, v0).setColor(r, g, b, a);
    }

    private static void text(PoseStack pose, MultiBufferSource buffers, Component c, float cx, float cy, float scale, int rgb, boolean centered) {
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence seq = c.getVisualOrderText();
        float width = font.width(seq);
        pose.pushPose();
        pose.translate(cx, cy, SCREEN_Z + 0.015f);
        pose.scale(TEXT * scale, -TEXT * scale, TEXT * scale);
        font.drawInBatch(seq, centered ? -width / 2 : 0, -4, 0xFF000000 | rgb, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void textLeft(PoseStack pose, MultiBufferSource buffers, Component c, float x, float y, float scale, int rgb) {
        text(pose, buffers, c, x, y, scale, rgb, false);
    }

    private static void textRight(PoseStack pose, MultiBufferSource buffers, Component c, float x, float y, float scale, int rgb) {
        float width = Minecraft.getInstance().font.width(c) * TEXT * scale;
        text(pose, buffers, c, x - width, y, scale, rgb, false);
    }
}
