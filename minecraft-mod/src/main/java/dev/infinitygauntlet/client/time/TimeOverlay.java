package dev.infinitygauntlet.client.time;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Marks every time-dilated entity: a clock ring that turns at the entity's own time rate (still when frozen), a
 * flickering green ghost on frozen creatures, and emerald motes hanging around them.
 */
public final class TimeOverlay {
    private static final int TIME = InfinityStone.TIME.color();
    private static final int TIME_HI = InfinityStone.TIME.highlight();
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static float clock;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientTime.isEmpty()) {
            return;
        }
        clock++;
        ClientTime.forEach((id, scale) -> {
            Entity e = mc.level.getEntity(id);
            if (e != null && Particles.chance(scale <= 0 ? 0.35f : 0.15f)) {
                Vec3 at = e.getBoundingBox().getCenter().add(VfxDraw.unit(id * 31L + (long) clock, 0).scale(e.getBbWidth() + 0.4));
                Particles.suspended(at, TIME_HI, 0.05f, 30);
            }
        });
    }

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientTime.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        float t = clock + pt;
        ClientTime.forEach((id, scale) -> {
            Entity e = mc.level.getEntity(id);
            if (e == null || e.position().distanceToSqr(cam) > 96 * 96) {
                return;
            }
            Vec3 p = e.getPosition(pt);
            pose.pushPose();
            pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            float w = Math.max(0.35f, e.getBbWidth() * 0.85f + 0.25f);
            pose.pushPose();
            pose.translate(0, e.getBbHeight() * 0.5, 0);
            pose.mulPose(com.mojang.math.Axis.YP.rotation(t * 0.08f * scale + id));
            VertexConsumer ring = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GEAR_RING));
            float pulse = scale <= 0 ? 0.75f + 0.25f * (float) Math.sin(t * 0.25f + id) : 0.7f;
            VfxDraw.ring(pose.last(), ring, X, Z, w * 0.85f, w * 1.15f, 32, 0, 0.12f, 0.86f, 0.35f, pulse);
            VertexConsumer glyph = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_TIME));
            VfxDraw.plane(pose.last(), glyph, X, Z, w * 2.6f, 0.12f, 0.86f, 0.35f, 0.35f * pulse);
            pose.popPose();
            if (scale <= 0 && e instanceof LivingEntity living) {
                double jx = Math.sin(t * 1.7 + id) * 0.04, jz = Math.cos(t * 1.3 + id) * 0.04;
                Afterimages.draw(pose, buffers, living, jx, 0, jz, living.yBodyRot, living.yHeadRot, living.getXRot(),
                        living.walkAnimation.position(), living.walkAnimation.speed(), living.tickCount, 0.28f + 0.1f * (float) Math.sin(t * 0.4f + id),
                        TIME, 1.05f);
            }
            pose.popPose();
        });
        buffers.endBatch();
    }

    public static void clear() {
        clock = 0;
    }

    private TimeOverlay() {
    }
}
