package dev.finalframe.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.finalframe.FFConfig;
import dev.finalframe.FinalFrame;
import dev.finalframe.client.choreo.WeaponState;
import dev.finalframe.client.session.ClientFinisherManager;
import dev.finalframe.client.session.ClientSession;
import dev.finalframe.sheriff.SheriffScript;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** World-space cinematic effects: the airborne revolver, muzzle flash, bullet trail, glint and shockwave. */
public final class WorldFx {
    private static final ResourceLocation FLASH = FinalFrame.id("textures/misc/muzzle_flash.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private WorldFx() {
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientFinisherManager.INSTANCE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack ps = event.getPoseStack();
        for (ClientSession s : ClientFinisherManager.INSTANCE.sessions()) {
            if (!s.isAborting()) {
                s.choreography().renderWorld(s, s.time(partial), ps, buffers, event.getCamera());
            }
        }
        buffers.endBatch();
    }

    /** Local-only per-tick cues for the performing player. */
    public static void onLocalTick(ClientSession s, int tick) {
        if (tick == SheriffScript.FIRE && FFConfig.get(FFConfig.DUCK_WORLD_AUDIO)) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
        }
    }

    public static void renderAirborneRevolver(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 pos, float yaw, float spin,
                                              WeaponState state) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();
        int light = mc.level == null ? FULL_BRIGHT : LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos));
        ps.pushPose();
        ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(-(90f + yaw)));
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        ps.scale(0.8f, 0.8f, 0.8f);
        ps.translate(-0.56f, -0.47f, -0.5f);
        RevolverRenderer.drawParts(ps, buffers, light, OverlayTexture.NO_OVERLAY, state.cylinder(), state.hammer(), ItemStack.EMPTY);
        ps.popPose();
    }

    public static void renderMuzzleFlash(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 muzzle, Vec3 toward, float alpha) {
        Vec3 dir = toward.subtract(muzzle).normalize();
        billboard(ps, buffers, camera, muzzle.add(dir.scale(0.18)), 0.75f * (0.6f + 0.4f * alpha), 0f, 1f, 0.95f, 0.8f, alpha);
        billboard(ps, buffers, camera, muzzle.add(dir.scale(0.5)), 0.45f * alpha, 45f, 1f, 0.8f, 0.45f, alpha * 0.8f);
    }

    public static void renderGlint(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 at, float size, float t) {
        billboard(ps, buffers, camera, at, size, t * 9f, 1f, 0.95f, 0.75f, 0.9f);
    }

    private static void billboard(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 at, float size, float spin,
                                  float r, float g, float b, float a) {
        Vec3 cam = camera.getPosition();
        ps.pushPose();
        ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        ps.mulPose(camera.rotation());
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        PoseStack.Pose pose = ps.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(FLASH));
        float h = size / 2f;
        quadTex(vc, pose, -h, -h, h, h, r, g, b, a);
        ps.popPose();
    }

    private static void quadTex(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        vertex(vc, pose, x0, y0, 0, 1, r, g, b, a);
        vertex(vc, pose, x1, y0, 1, 1, r, g, b, a);
        vertex(vc, pose, x1, y1, 1, 0, r, g, b, a);
        vertex(vc, pose, x0, y1, 0, 0, r, g, b, a);
        vertex(vc, pose, x0, y1, 0, 0, r, g, b, a);
        vertex(vc, pose, x1, y1, 1, 0, r, g, b, a);
        vertex(vc, pose, x1, y0, 1, 1, r, g, b, a);
        vertex(vc, pose, x0, y0, 0, 1, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, 0).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
            .setNormal(pose, 0, 0, 1);
    }

    /** Glowing bullet trail from muzzle to impact, camera-facing so it reads from every shot angle. */
    public static void renderTracer(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 from, Vec3 to, float alpha) {
        Vec3 cam = camera.getPosition();
        Vec3 dir = to.subtract(from);
        Vec3 mid = from.add(dir.scale(0.5));
        Vec3 side = dir.cross(cam.subtract(mid)).normalize();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        float a = alpha * alpha;
        strip(vc, m, cam, from, to, side.scale(0.09 * alpha), 1f, 0.72f, 0.3f, 0.35f * a);
        strip(vc, m, cam, from, to, side.scale(0.025), 1f, 0.97f, 0.85f, 0.95f * a);
    }

    private static void strip(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, Vec3 side, float r, float g, float bl, float al) {
        Vec3[] q = {a.add(side), b.add(side), b.subtract(side), a.subtract(side)};
        int[][] orders = {{0, 1, 2, 3}, {3, 2, 1, 0}};
        for (int[] order : orders) {
            for (int i : order) {
                Vec3 p = q[i].subtract(cam);
                vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, bl, al);
            }
        }
    }

    /** Expanding ground ring at the impact point. */
    public static void renderShockwave(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 center, float radius, float alpha) {
        Vec3 cam = camera.getPosition();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        float inner = Math.max(0, radius - 0.35f);
        int segments = 48;
        double cx = center.x - cam.x;
        double cy = center.y - cam.y;
        double cz = center.z - cam.z;
        for (int i = 0; i < segments; i++) {
            float a0 = i / (float) segments * Mth.TWO_PI;
            float a1 = (i + 1) / (float) segments * Mth.TWO_PI;
            float[][] pts = {
                {inner * Mth.cos(a0), inner * Mth.sin(a0), 0},
                {radius * Mth.cos(a0), radius * Mth.sin(a0), 1},
                {radius * Mth.cos(a1), radius * Mth.sin(a1), 1},
                {inner * Mth.cos(a1), inner * Mth.sin(a1), 0}
            };
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    float[] p = pts[pass == 0 ? k : 3 - k];
                    float edge = p[2];
                    vc.addVertex(m, (float) (cx + p[0]), (float) cy, (float) (cz + p[1]))
                        .setColor(1f, 0.8f, 0.42f, alpha * (0.15f + 0.55f * edge));
                }
            }
        }
    }
}
