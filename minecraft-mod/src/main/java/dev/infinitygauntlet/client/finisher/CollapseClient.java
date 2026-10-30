package dev.infinitygauntlet.client.finisher;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.power.CinematicCamera;
import dev.infinitygauntlet.client.time.Afterimages;
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

import java.util.concurrent.ThreadLocalRandom;

import static dev.infinitygauntlet.finisher.Timelines.Collapse.*;

/**
 * Time: Temporal Collapse. The gauntlet rises and the whole scene stops. Around the target stand its past and
 * future selves: some walking backwards through what it did, some racing ahead at impossible speed. Clock rings stack
 * up and spin faster; the copies are dragged into the target, and time snaps back all at once, ageing it to dust.
 */
final class CollapseClient extends StationaryClient {
    private static final int GREEN = InfinityStone.TIME.color();
    private static final int PALE = InfinityStone.TIME.highlight();
    /** Animation speed of each temporal copy: negative ones rewind, large ones are its future racing past. */
    private static final float[] SPEEDS = {-1.6f, -0.5f, 0.35f, 2.2f, 4.5f};

    CollapseClient(ClientFinishers.Active a) {
        super(a);
    }

    private float converge(float t) {
        float u = window(t, CONVERGE, RELEASE);
        return u * u;
    }

    private float appear(float t) {
        return smooth(window(t, COPIES, COPIES + 12));
    }

    /** Where copy {@code i} stands at {@code t}, relative to the target's feet. */
    private Vec3 copyOffset(int i, float t) {
        double ang = (i - 2) * 0.62 + Math.PI + (t - COPIES) * 0.012 * (i % 2 == 0 ? 1 : -1);
        double dist = (1.5 + 0.35 * (i % 3)) * Math.max(1, hw * 2.2) * (1 - converge(t));
        // fanned out around the side away from the caster
        Vec3 dir = d.scale(-Math.cos(ang)).add(right.scale(Math.sin(ang)));
        return dir.scale(-dist);
    }

    @Override
    public void poseCaster(HumanoidModel<?> m, LivingEntity caster, HumanoidArm arm, HumanoidArm g, float pt) {
        float t = a.time(pt);
        float w = bodyWeight(t, pt);
        float raise = smooth(window(t, 0, FREEZE));
        float thrust = smooth(window(t, RELEASE - 3, RELEASE + 1)) * (1 - smooth(window(t, RELEASE + 10, RELEASE + 24)));
        // turning time back and forth like a dial, faster as the collapse nears
        float dial = t > COPIES && t < RELEASE ? Mth.sin((t - COPIES) * (0.12f + 0.5f * converge(t))) * 0.5f : 0;
        if (arm == gauntletArm) {
            float[] aimed = aim(arm, core.add(0, 0.6, 0));
            float x = Mth.lerp(raise, 0, aimed[0] - 0.35f);
            x = Mth.lerp(thrust, x, aimed[0]);
            blend(part(m, arm), w, x, aimed[1] * raise, side * dial * (1 - thrust));
        } else {
            blend(part(m, arm), w, -0.4f * raise * (1 - thrust) + 0.4f * thrust, 0, -side * 0.3f * raise);
        }
        if (arm == HumanoidArm.RIGHT) {
            stance(m, w, 1 + thrust, core);
        }
    }

    @Override
    public void transformTarget(PoseStack pose, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t >= CONVERGE && t < RELEASE) {
            float c = converge(t);
            pose.translate(Mth.sin(t * 6.1f) * 0.06f * c, 0, Mth.cos(t * 5.3f) * 0.06f * c);
        }
    }

    @Override
    public void overlayTarget(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < FREEZE || t >= RELEASE) {
            return;
        }
        ghost(pose, buffers, e, pt, 0.25f + 0.6f * converge(t), GREEN, 1.03f);
        if (t < COPIES) {
            return;
        }
        float body = Mth.lerp(pt, e.yBodyRotO, e.yBodyRot), head = Mth.lerp(pt, e.yHeadRotO, e.yHeadRot);
        float since = t - COPIES;
        for (int i = 0; i < SPEEDS.length; i++) {
            Vec3 off = copyOffset(i, t);
            float speed = SPEEDS[i];
            // each copy plays the target's motion at its own speed and direction
            float limb = e.walkAnimation.position(pt) + since * speed * 0.35f;
            float age = e.tickCount + since * speed;
            float turn = speed < 0 ? 180 : 0;
            float alpha = appear(t) * (0.35f + 0.35f * converge(t)) * (0.75f + 0.25f * Mth.sin(t * 0.8f + i));
            Afterimages.draw(pose, buffers, e, off.x, off.y, off.z, body + turn + i * 9, head + turn + i * 9, e.getXRot(), limb, 0.8f, age, alpha,
                    speed < 0 ? PALE : GREEN, 1f);
            // speed trails on the racing future
            if (speed > 2) {
                for (int k = 1; k <= 3; k++) {
                    Vec3 back = off.add(d.scale(0.25 * k));
                    Afterimages.draw(pose, buffers, e, back.x, back.y, back.z, body + i * 9, head + i * 9, e.getXRot(), limb - k * 0.6f, 0.8f, age - k * 2,
                            alpha * (0.5f - k * 0.12f), GREEN, 1f);
                }
            }
        }
    }

    @Override
    public void tick() {
        float t = a.age;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t >= FREEZE && t < RELEASE) {
            for (int i = 0; i < Particles.scaled(3); i++) {
                Vec3 at = core.add(r.nextGaussian() * 3, r.nextGaussian() * 1.5, r.nextGaussian() * 3);
                Particles.suspended(at, i % 2 == 0 ? GREEN : PALE, 0.05f, 40);
            }
        }
        if ((int) t == RELEASE) {
            for (int i = 0; i < Particles.scaled(50); i++) {
                Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5, r.nextGaussian()).normalize();
                Particles.spark(core, dir.scale(0.4 + r.nextDouble() * 0.4), i % 2 == 0 ? GREEN : 0xFFFFFF, 0.12f);
            }
        }
    }

    @Override
    public void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        float t = a.time(pt);
        if (t < FREEZE || t > RELEASE + 10) {
            return;
        }
        Vec3 camPos = camera.getPosition();
        pose.pushPose();
        pose.translate(core.x - camPos.x, core.y - camPos.y, core.z - camPos.z);
        float c = converge(t);
        float fade = t > RELEASE ? 1 - window(t, RELEASE, RELEASE + 10) : smooth(window(t, FREEZE, FREEZE + 10));
        // stacked clock rings, spinning faster and pulling tight as time collapses
        for (int k = -2; k <= 2; k++) {
            float rr = Math.max(1.1f, h * 0.7f) * (1.6f - Math.abs(k) * 0.22f) * (1 - 0.45f * c);
            pose.pushPose();
            pose.translate(0, k * 0.45 * (1 - 0.6 * c), 0);
            float spin = (t - FREEZE) * (0.02f + 0.35f * c) * (k % 2 == 0 ? 1 : -1);
            pose.mulPose(Axis.YP.rotation(spin));
            VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GEAR_RING)), new Vec3(1, 0, 0), new Vec3(0, 0, 1), rr * 0.86f, rr,
                    48, 0, k == 0 ? VfxDraw.r(PALE) : VfxDraw.r(GREEN), k == 0 ? VfxDraw.g(PALE) : VfxDraw.g(GREEN), k == 0 ? VfxDraw.b(PALE) : VfxDraw.b(GREEN),
                    0.75f * fade);
            pose.popPose();
        }
        Quaternionf camRot = camera.rotation();
        VfxDraw.billboard(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW)), camRot, 0, 0, 0, h * (1.2f + 3 * c), 0, 0.2f, 1f, 0.5f,
                0.25f + 0.55f * c * fade);
        pose.popPose();
    }

    @Override
    public CinematicCamera.Shot shot(float pt) {
        float t = a.time(pt);
        if (t < FREEZE) {
            return shot(eye().subtract(d.scale(2.3)).add(right.scale(-side)).add(0, 0.3, 0), core, 0, 1f);
        }
        if (t < CONVERGE) {
            // a slow, high arc through the stopped moment
            float u = window(t, FREEZE, CONVERGE);
            double ang = Mth.lerp(u, -0.3f, 2.2f);
            Vec3 around = right.scale(side * Math.cos(ang)).subtract(d.scale(Math.sin(ang)));
            return shot(core.add(around.scale(6 - 1.2 * u)).add(0, 2.6 - 1.4 * u, 0), core, 0, 0.95f);
        }
        if (t < RELEASE + 2) {
            float u = window(t, CONVERGE, RELEASE);
            return shot(core.subtract(d.scale(2.6 - 0.9 * u)).add(right.scale(side * 1.1)).add(0, 0.3, 0), core, -6 * u, 0.85f - 0.1f * u);
        }
        float u = window(t, RELEASE + 2, a.type.duration);
        return shot(core.subtract(d.scale(5 + 2 * u)).add(right.scale(side * 3.5)).add(0, 1.5 + u, 0), core, 0, 1.05f);
    }

    @Override
    public int grade(float pt) {
        float t = a.time(pt);
        float alpha = t < FREEZE ? 0.04f : t < RELEASE ? 0.2f + 0.15f * converge(t) : 0.32f * (1 - window(t, RELEASE, RELEASE + 22));
        return grade(0x0E6A34, alpha);
    }

    @Override
    public int impactFrame(float pt) {
        float t = a.time(pt);
        if (t >= FREEZE && t < FREEZE + 1) {
            return 0xC8E8FFF0;
        }
        if (t >= RELEASE && t < RELEASE + 1.5f) {
            return 0xF0F0FFF4;
        }
        return 0;
    }
}
