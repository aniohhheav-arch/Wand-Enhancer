package dev.infinitygauntlet.client.finisher;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.power.CinematicCamera;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

import static dev.infinitygauntlet.finisher.Timelines.Psychic.*;

/**
 * Mind: Psychic Overload. Two fingers to the temple, the other hand reaching out: the target rises limp into the air
 * inside a slowly closing cage of golden geometry (triangles, squares, hexagons, the eye rune) while a neural filament
 * runs from the caster's head to it. Chimes quicken, the cage tightens and spins, and everything is released at once
 * in a massive psychic pulse that drops the body.
 */
final class PsychicClient extends StationaryClient {
    private static final int GOLD = InfinityStone.MIND.color();
    private static final int PALE = InfinityStone.MIND.highlight();
    private static final float LIFT_HEIGHT = 3f;

    PsychicClient(ClientFinishers.Active a) {
        super(a);
    }

    private float lift(float t) {
        if (t >= PULSE) {
            float fall = window(t, PULSE + 2, PULSE + 9);
            return LIFT_HEIGHT * (1 - fall * fall);
        }
        return LIFT_HEIGHT * smooth(window(t, LIFT, BUILD));
    }

    private float build(float t) {
        float u = window(t, BUILD, PULSE);
        return u * u;
    }

    private Vec3 held(float t) {
        return core.add(0, lift(t), 0);
    }

    @Override
    public void poseCaster(HumanoidModel<?> m, LivingEntity caster, HumanoidArm arm, HumanoidArm g, float pt) {
        float t = a.time(pt);
        float w = bodyWeight(t, pt);
        float in = smooth(window(t, 0, LIFT));
        float push = smooth(window(t, PULSE - 3, PULSE + 1)) * (1 - smooth(window(t, PULSE + 10, PULSE + 24)));
        float strain = Mth.sin(t * 3.1f) * 0.03f * build(t);
        Vec3 target = held(t);
        if (arm == gauntletArm) {
            // fingers to the temple: arm raised and folded in toward the head
            float[] aimed = aim(arm, target);
            float x = Mth.lerp(push, Mth.lerp(in, 0, -2.55f), aimed[0]);
            float y = Mth.lerp(push, -side * 0.62f * in, aimed[1]);
            blend(part(m, arm), w, x + strain, y, side * 0.1f * in * (1 - push));
        } else {
            float[] aimed = aim(arm, target);
            blend(part(m, arm), w, Mth.lerp(in, 0, aimed[0]) + strain, aimed[1] * in, 0);
        }
        if (arm == HumanoidArm.RIGHT) {
            stance(m, w, 1 + push, target);
        }
    }

    @Override
    public void transformTarget(PoseStack pose, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < LIFT) {
            return;
        }
        float hc = h * 0.5f;
        float b = build(t);
        pose.translate(0, lift(t), 0);
        if (t < PULSE) {
            // hangs limp, turning slowly, convulsing as the overload builds
            pose.translate(0, hc, 0);
            pose.mulPose(Axis.YP.rotationDegrees((t - LIFT) * (1.5f + 6 * b)));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.2f) * 10 + Mth.sin(t * 2.7f) * 6 * b));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.cos(t * 0.17f) * 8));
            pose.translate(0, -hc, 0);
        }
    }

    @Override
    public void overlayTarget(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < LIFT || t >= PULSE) {
            return;
        }
        ghost(pose, buffers, e, pt, 0.2f + 0.7f * build(t), build(t) > 0.7f ? PALE : GOLD, 1.03f);
        // glowing eyes
        Quaternionf camRot = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        float eye = e.getEyeHeight();
        VfxDraw.billboard(pose.last(), glow, camRot, 0, eye, 0, 0.5f + 0.6f * build(t), 0, 1f, 0.85f, 0.3f, 0.6f + 0.4f * build(t));
    }

    @Override
    public void tick() {
        float t = a.age;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t >= LIFT && t < PULSE) {
            Vec3 c = held(t);
            for (int i = 0; i < Particles.scaled(2 + 5 * build(t)); i++) {
                Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                Particles.mote(c.add(dir.scale(1.6)), dir.cross(new Vec3(0, 1, 0)).scale(0.05), i % 2 == 0 ? GOLD : PALE, 0.07f, 20);
            }
            if (t < BUILD) {
                Particles.mote(feet.add(r.nextGaussian() * 0.4, 0.1, r.nextGaussian() * 0.4), new Vec3(0, 0.12, 0), GOLD, 0.06f, 20);
            }
        }
        for (int chime : CHIMES) {
            if ((int) t == chime) {
                Vec3 c = held(t);
                for (int i = 0; i < Particles.scaled(20); i++) {
                    double ang = i * Math.PI * 2 / 20;
                    Particles.spark(c, new Vec3(Math.cos(ang), 0, Math.sin(ang)).scale(0.3), PALE, 0.08f);
                }
            }
        }
    }

    /** One of the floating symbols: a polygon outline with an inner star, in the plane of {@code ax}/{@code ay}. */
    private static void polygon(PoseStack.Pose last, VertexConsumer lines, Vec3 at, Vec3 ax, Vec3 ay, int sides, float r, Vec3 cam, float alpha) {
        Vec3 prev = null, first = null;
        for (int k = 0; k <= sides; k++) {
            double ang = k * Math.PI * 2 / sides;
            Vec3 p = at.add(ax.scale(Math.cos(ang) * r)).add(ay.scale(Math.sin(ang) * r));
            if (prev != null) {
                VfxDraw.line(last, lines, prev, p, cam, 0.05f, 1f, 0.85f, 0.3f, alpha);
            } else {
                first = p;
            }
            prev = p;
        }
        for (int k = 0; k < sides; k++) {
            double a0 = k * Math.PI * 2 / sides, a1 = (k + 2) * Math.PI * 2 / sides;
            Vec3 p0 = at.add(ax.scale(Math.cos(a0) * r)).add(ay.scale(Math.sin(a0) * r));
            Vec3 p1 = at.add(ax.scale(Math.cos(a1) * r)).add(ay.scale(Math.sin(a1) * r));
            VfxDraw.line(last, lines, p0, p1, cam, 0.025f, 1f, 0.95f, 0.6f, alpha * 0.7f);
        }
    }

    @Override
    public void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        float t = a.time(pt);
        if (t < LIFT - 4 || t > PULSE + 20) {
            return;
        }
        Vec3 camPos = camera.getPosition();
        Vec3 c = held(t);
        pose.pushPose();
        pose.translate(c.x - camPos.x, c.y - camPos.y, c.z - camPos.z);
        Vec3 cam = camPos.subtract(c);
        Quaternionf camRot = camera.rotation();
        float appear = smooth(window(t, LIFT - 4, BUILD - 10));
        float b = build(t);
        if (t < PULSE) {
            // the cage of symbols, closing in and spinning faster
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            int count = 7;
            float radius = Mth.lerp(b, 1.9f, 1.0f) * Math.max(1, h * 0.7f);
            for (int i = 0; i < count; i++) {
                double orbit = i * Math.PI * 2 / count + t * (0.03 + 0.12 * b);
                double tilt = Math.sin(i * 1.7) * 0.6;
                Vec3 at = new Vec3(Math.cos(orbit) * radius, Math.sin(orbit * 2 + i) * 0.35 * radius + Math.sin(tilt) * 0.3, Math.sin(orbit) * radius);
                Vec3 out = at.normalize();
                Vec3 ax = out.cross(new Vec3(0, 1, 0)).normalize();
                Vec3 ay = out.cross(ax).normalize();
                Vec3 spinX = VfxDraw.rotate(ax, out, t * 0.08 * (i % 2 == 0 ? 1 : -1));
                Vec3 spinY = VfxDraw.rotate(ay, out, t * 0.08 * (i % 2 == 0 ? 1 : -1));
                polygon(pose.last(), lines, at, spinX, spinY, 3 + i % 4, 0.32f + 0.08f * (i % 3), cam, appear * (0.6f + 0.4f * Mth.sin(t * 0.5f + i)));
            }
            // neural filament from the caster's mind to the target
            Vec3 temple = eye().add(right.scale(side * 0.25)).subtract(c);
            VfxDraw.lightning(pose.last(), lines, temple, Vec3.ZERO, cam, a.targetId * 3L + (long) (t / 2), 0.25f, 0.035f, 1f, 0.8f, 0.25f, appear);
            // rune halo behind the target, facing the camera
            Vector3f rx = camRot.transform(new Vector3f(1, 0, 0)), ry = camRot.transform(new Vector3f(0, 1, 0));
            pose.pushPose();
            VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_MIND)), new Vec3(rx.x, rx.y, rx.z), new Vec3(ry.x, ry.y, ry.z),
                    radius * 0.55f, radius * 0.95f, 32, t * 0.01f, 1f, 0.82f, 0.25f, 0.22f * appear);
            pose.popPose();
        }
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        if (t < PULSE) {
            VfxDraw.billboard(pose.last(), glow, camRot, 0, 0, 0, h * (1.5f + 3 * b), 0, 1f, 0.8f, 0.2f, 0.3f + 0.5f * b);
        } else {
            float u = window(t, PULSE, PULSE + 20);
            VfxDraw.billboard(pose.last(), glow, camRot, 0, 0, 0, 3 + 22 * u, 0, 1f, 0.92f, 0.6f, 1 - u);
        }
        pose.popPose();
    }

    @Override
    public CinematicCamera.Shot shot(float pt) {
        float t = a.time(pt);
        Vec3 c = held(t);
        if (t < LIFT) {
            // close past the caster's head, the temple gesture in frame
            return shot(eye().subtract(d.scale(1.3)).add(right.scale(side * 0.8)).add(0, 0.25, 0), core, 0, 0.9f);
        }
        if (t < BUILD) {
            // low angle looking up as the target rises
            float u = window(t, LIFT, BUILD);
            return shot(feet.add(right.scale(side * 3.6)).subtract(d.scale(1.5)).add(0, 0.35, 0), c, 6 * u, 0.95f);
        }
        if (t < PULSE - 2) {
            float u = window(t, BUILD, PULSE - 2);
            double ang = Mth.lerp(u, 0.4f, 2.1f);
            Vec3 around = right.scale(side * Math.cos(ang)).subtract(d.scale(Math.sin(ang)));
            return shot(c.add(around.scale(4.4 - 1.6 * u)).add(0, 0.8 - 0.6 * u, 0), c, -4 * u, 0.9f - 0.12f * u);
        }
        float u = window(t, PULSE - 2, a.type.duration);
        return shot(c.subtract(d.scale(7 + 3 * u)).add(right.scale(side * 4)).add(0, 1.5 + 2 * u, 0), c.subtract(0, 1.5 * u, 0), 0, 1.05f);
    }

    @Override
    public int grade(float pt) {
        float t = a.time(pt);
        float alpha = t < PULSE ? 0.06f + 0.26f * build(t) : 0.32f * (1 - window(t, PULSE, PULSE + 24));
        return grade(0xC08A10, alpha);
    }

    @Override
    public int impactFrame(float pt) {
        float t = a.time(pt);
        if (t >= PULSE && t < PULSE + 1.5f) {
            return 0xF0FFF6D0;
        }
        return 0;
    }
}
