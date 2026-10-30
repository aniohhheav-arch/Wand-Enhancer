package dev.infinitygauntlet.client.nexus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.RuptureDraw;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.nexus.NexusBlockEntity;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * World visuals of every neural link: a miniature blue-black rupture hovering over the possessed creature's head,
 * golden neural tendrils reaching from it into the creature, glowing yellow eyes, and a pulsing blue-and-gold stream
 * running from the rupture to the Nexus screen. Disconnecting collapses the rupture and fades the stream.
 */
public final class NexusLinkRenderer {
    private static final RuptureDraw.Palette PALETTE = RuptureDraw.Palette.of(InfinityStone.SPACE.color(), InfinityStone.MIND.highlight());

    public static Vec3 portalPos(Entity e, float pt) {
        return e.getPosition(pt).add(0, e.getEyeHeight() + 0.55 + e.getBbHeight() * 0.12, 0);
    }

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || NexusBlockEntity.CLIENT_LOADED.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        long now = mc.level.getGameTime();
        float t = now + pt;
        for (NexusBlockEntity nexus : NexusBlockEntity.CLIENT_LOADED) {
            if (nexus.getLevel() != mc.level || nexus.getBlockPos().getCenter().distanceTo(cam) > 160) {
                continue;
            }
            Vec3 screen = nexus.screenCenter();
            for (NexusBlockEntity.Link link : nexus.links()) {
                Entity e = mc.level.getEntity(link.entityId());
                if (e != null && e.getId() != FeedRenderer.renderingEntity()) {
                    float open = Math.min(1f, (now - link.started() + pt) / 24f);
                    drawLink(pose, buffers, cam, e, screen, open, 0f, link.stability(), t, now - link.started());
                }
            }
            if (nexus.state() == NexusBlockEntity.DISCONNECTING && nexus.fadingEntityId() >= 0) {
                Entity e = mc.level.getEntity(nexus.fadingEntityId());
                if (e != null && e.getId() != FeedRenderer.renderingEntity()) {
                    float close = Math.min(1f, nexus.stateAge(pt) / 30f);
                    if (close < 1) {
                        drawLink(pose, buffers, cam, e, screen, 1f, close, 0.4f, t, 1000);
                    }
                }
            }
        }
        buffers.endBatch();
    }

    private static void drawLink(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Entity e, Vec3 screen, float open, float close,
                                 float stability, float t, long age) {
        Vec3 portal = portalPos(e, t - (long) t);
        Vec3 eyes = e.getEyePosition(t - (long) t);
        float scale = Math.max(0.3f, Math.min(0.6f, e.getBbWidth() * 0.5f));
        Quaternionf camRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        long seed = e.getId() * 7919L;

        pose.pushPose();
        pose.translate(portal.x - cam.x, portal.y - cam.y, portal.z - cam.z);
        RuptureDraw.draw(pose, buffers, cam.subtract(portal), scale, open, close, t, seed, PALETTE, 0.8f + 0.4f * stability, false);

        float fade = 1 - close;
        float flicker = stability < 0.3f ? (VfxDraw.hash(seed, (int) t, 3) > 0.35f ? 1f : 0.2f) : 1f;
        VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
        // golden neural tendrils down into the creature's head
        float tendril = (age < 30 ? 1f : 0.45f) * fade * open;
        for (int k = 0; k < 3; k++) {
            Vec3 to = eyes.subtract(portal).add((VfxDraw.hash(seed, k, 1) - 0.5) * e.getBbWidth() * 0.6, -0.05,
                    (VfxDraw.hash(seed, k, 2) - 0.5) * e.getBbWidth() * 0.6);
            VfxDraw.lightning(pose.last(), lines, Vec3.ZERO, to, cam.subtract(portal), seed + k * 31L + (long) (t / 2), 0.45f, 0.025f,
                    1f, 0.82f, 0.25f, tendril * flicker);
        }
        // the stream to the Nexus screen
        Vec3 to = screen.subtract(portal);
        float alpha = (0.35f + 0.45f * stability) * fade * open * flicker;
        VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
        VfxDraw.beam(pose.last(), beam, Vec3.ZERO, to, cam.subtract(portal), 0.32f, -t * 0.12f, 0.16f, 0.43f, 1f, alpha * 0.7f);
        VfxDraw.beam(pose.last(), beam, Vec3.ZERO, to, cam.subtract(portal), 0.11f, -t * 0.2f, 1f, 0.82f, 0.3f, alpha);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        for (int k = 0; k < 4; k++) {
            float f = (t * 0.025f + k / 4f) % 1f;
            Vec3 at = to.scale(f);
            boolean goldPulse = k % 2 == 0;
            VfxDraw.billboard(pose.last(), glow, camRot, (float) at.x, (float) at.y, (float) at.z, 0.45f, 0, goldPulse ? 1f : 0.3f,
                    goldPulse ? 0.8f : 0.6f, goldPulse ? 0.3f : 1f, alpha);
        }
        pose.popPose();

        // glowing yellow eyes
        if (fade > 0.05f) {
            pose.pushPose();
            pose.translate(eyes.x - cam.x, eyes.y - cam.y, eyes.z - cam.z);
            Vec3 look = e.getViewVector(t - (long) t);
            Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(0.12 * e.getBbWidth() / 0.6);
            Vec3 front = look.scale(e.getBbWidth() * 0.5 + 0.02);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 at = front.add(side.scale(s));
                VfxDraw.billboard(pose.last(), glow, camRot, (float) at.x, (float) at.y, (float) at.z, 0.22f, 0, 1f, 0.85f, 0.2f, 0.9f * fade);
            }
            pose.popPose();
        }
        if (Particles.chance(0.15f * fade)) {
            Particles.mote(portal, VfxDraw.unit(seed + (long) t, 0).scale(0.03), InfinityStone.MIND.color(), 0.06f, 16);
        }
    }

    private NexusLinkRenderer() {
    }
}
