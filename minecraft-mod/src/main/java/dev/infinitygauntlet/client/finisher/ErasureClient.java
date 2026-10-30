package dev.infinitygauntlet.client.finisher;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.power.CinematicCamera;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.RuptureDraw;
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

import static dev.infinitygauntlet.finisher.Timelines.Erasure.*;

/**
 * Space: Dimensional Erasure. The caster raises an open hand; a rupture tears open around the target and starts to
 * swallow it, stretching it thin and spinning it into the dark. Then the caster's fist closes and the rift is crushed
 * into a single point of light, which winks out, leaving only cracks in the air.
 */
final class ErasureClient extends StationaryClient {
    private static final int BLUE = InfinityStone.SPACE.color();
    private static final int PALE = InfinityStone.SPACE.highlight();
    private final RuptureDraw.Palette palette = RuptureDraw.Palette.of(BLUE, PALE);

    ErasureClient(ClientFinishers.Active a) {
        super(a);
    }

    private float open(float t) {
        return smooth(window(t, OPEN, PULL));
    }

    /** 0..1 how far the rift has been crushed. */
    private float crush(float t) {
        float u = window(t, COLLAPSE, ERASE);
        return u * u * u;
    }

    private float radius(float t) {
        return Math.max(1.1f, h * 0.85f) * open(t) * (1 - crush(t)) * (1 + 0.04f * Mth.sin(t * 0.7f));
    }

    /** 0..1 how far the target has been drawn into the rift. */
    private float pull(float t) {
        return smooth(window(t, PULL, COLLAPSE));
    }

    @Override
    public void poseCaster(HumanoidModel<?> m, LivingEntity caster, HumanoidArm arm, HumanoidArm g, float pt) {
        float t = a.time(pt);
        float w = bodyWeight(t, pt);
        float raise = smooth(window(t, 0, OPEN));
        float yank = smooth(window(t, COLLAPSE, ERASE - 4));
        float release = smooth(window(t, ERASE + 6, ERASE + 22));
        float tremble = Mth.sin(t * 2.9f) * 0.03f * pull(t) * (1 - yank);
        if (arm == gauntletArm) {
            float[] aimed = aim(arm, core);
            // open hand held at the rift, then yanked back into a fist that crushes it
            float x = Mth.lerp(raise, 0, aimed[0]);
            x = Mth.lerp(yank, x, 0.35f);
            x = Mth.lerp(release, x, 0);
            blend(part(m, arm), w, x + tremble, aimed[1] * raise * (1 - yank), side * 0.1f * yank * (1 - release));
        } else {
            blend(part(m, arm), w, Mth.lerp(raise, 0, -0.5f) * (1 - release), 0, -side * 0.35f * raise * (1 - release));
        }
        if (arm == HumanoidArm.RIGHT) {
            m.body.yRot = Mth.lerp(w, m.body.yRot, side * 0.35f * yank * (1 - release));
            stance(m, w, 1, core);
        }
    }

    @Override
    public void transformCaster(PoseStack pose, LivingEntity caster, float pt) {
        float t = a.time(pt);
        float lean = (4 * smooth(window(t, 0, OPEN)) - 10 * smooth(window(t, COLLAPSE, ERASE)) * (1 - smooth(window(t, ERASE + 6, ERASE + 22))))
                * bodyWeight(t, pt);
        pose.mulPose(Axis.of(new org.joml.Vector3f((float) d.z, 0, (float) -d.x)).rotationDegrees(lean));
    }

    @Override
    public void transformTarget(PoseStack pose, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < OPEN) {
            return;
        }
        float hc = h * 0.55f;
        float p = pull(t), c = crush(t);
        pose.translate(0, hc, 0);
        pose.translate(0, 0.25f * open(t), 0);
        pose.mulPose(Axis.YP.rotationDegrees(p * p * 540 + c * 900));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.4f) * 12 * p));
        // stretched thin as space folds around it
        float thin = 1 - 0.55f * p, tall = 1 + 0.5f * p;
        float s = 1 - c;
        pose.scale(thin * s, tall * s, thin * s);
        pose.translate(0, -hc, 0);
    }

    @Override
    public void overlayTarget(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < OPEN || t >= ERASE) {
            return;
        }
        float p = pull(t);
        ghost(pose, buffers, e, pt, 0.2f + 0.7f * p, p > 0.6f ? PALE : BLUE, 1.03f);
    }

    @Override
    public void tick() {
        float t = a.age;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t >= OPEN && t < ERASE) {
            float rad = radius(t);
            for (int i = 0; i < Particles.scaled(4 + 6 * pull(t)); i++) {
                Vec3 from = core.add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(rad * 2.5 + 1));
                Particles.inward(from, core, i % 2 == 0 ? BLUE : PALE, 0.09f);
            }
        }
        if (t >= ERASE && t < ERASE + 18) {
            for (int i = 0; i < Particles.scaled(3); i++) {
                Particles.mote(core, new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(0.06), PALE, 0.06f, 20);
            }
        }
    }

    @Override
    public void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        float t = a.time(pt);
        Vec3 camPos = camera.getPosition();
        Quaternionf camRot = camera.rotation();
        pose.pushPose();
        pose.translate(core.x - camPos.x, core.y - camPos.y + 0.25 * open(t), core.z - camPos.z);
        Vec3 cam = camPos.subtract(core);
        if (t >= OPEN && t < ERASE) {
            float rad = radius(t);
            RuptureDraw.draw(pose, buffers, cam, rad, open(t), crush(t) * 0.6f, t, a.targetId * 31L, palette, 1f, false);
            // the bond between the open hand and the rift
            Vec3 hand = gauntletHand(core).subtract(core);
            VfxDraw.beam(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM)), hand, Vec3.ZERO, cam, 0.12f + 0.1f * pull(t),
                    -t * 0.3f, VfxDraw.r(BLUE), VfxDraw.g(BLUE), VfxDraw.b(BLUE), 0.6f * open(t));
            if (crush(t) > 0) {
                // the crush: space pinched inward from every side
                VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
                for (int i = 0; i < 16; i++) {
                    Vec3 dir = VfxDraw.unit(a.targetId * 7L, i);
                    VfxDraw.line(pose.last(), lines, dir.scale(rad * 3 * (1 - crush(t)) + 0.3), dir.scale(0.05), cam, 0.05f, 0.75f, 0.9f, 1f, crush(t));
                }
            }
        }
        if (t >= ERASE - 1 && t < ERASE + 26) {
            // a single point of light, then cracks left in the air where the rift was
            float u = window(t, ERASE - 1, ERASE + 26);
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(pose.last(), glow, camRot, 0, 0, 0, 0.35f + 2.5f * (1 - u) * (1 - u), 0, 0.85f, 0.95f, 1f, 1 - u);
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            for (int i = 0; i < 9; i++) {
                Vec3 dir = VfxDraw.unit(a.targetId * 13L, i);
                VfxDraw.lightning(pose.last(), lines, Vec3.ZERO, dir.scale(1.2 + 1.4 * VfxDraw.hash(a.targetId, i, 3)), cam, a.targetId * 5L + i, 0.6f, 0.03f,
                        0.6f, 0.85f, 1f, (1 - u) * 0.9f);
            }
        }
        pose.popPose();
    }

    @Override
    public CinematicCamera.Shot shot(float pt) {
        float t = a.time(pt);
        Vec3 look = core.add(0, 0.25 * open(t), 0);
        if (t < OPEN) {
            return shot(eye().subtract(d.scale(2.4)).add(right.scale(-side * 0.9)).add(0, 0.25, 0), look, 0, 1f);
        }
        if (t < 30) {
            float u = window(t, OPEN, 30);
            return shot(look.add(right.scale(side * (6.5 - u))).subtract(d.scale(2 + u)).add(0, 0.6, 0), look, -3, 0.95f);
        }
        if (t < COLLAPSE) {
            // slow push across the face of the rift
            float u = window(t, 30, COLLAPSE);
            double ang = Mth.lerp(u, 0.9f, 0.25f);
            Vec3 around = right.scale(side * Math.cos(ang)).subtract(d.scale(Math.sin(ang)));
            return shot(look.add(around.scale(5.5 - 2.6 * u)).add(0, 0.4 - 0.3 * u, 0), look, 5 * u, 0.9f - 0.1f * u);
        }
        if (t < ERASE + 3) {
            // extreme close-up on the crush, from the caster's side
            return shot(look.subtract(d.scale(1.7)).add(right.scale(side * 0.5)).add(0, 0.15, 0), look, -8, 0.7f);
        }
        float u = window(t, ERASE + 3, a.type.duration);
        return shot(look.subtract(d.scale(4 + 2 * u)).add(right.scale(side * (3 + u))).add(0, 1.4 + u, 0), look, 0, 1f);
    }

    @Override
    public int grade(float pt) {
        float t = a.time(pt);
        float alpha = t < ERASE ? 0.08f + 0.22f * pull(t) + 0.1f * crush(t) : 0.3f * (1 - window(t, ERASE, ERASE + 24));
        return grade(0x1A3AA0, alpha);
    }

    @Override
    public int impactFrame(float pt) {
        float t = a.time(pt);
        if (t >= ERASE && t < ERASE + 1) {
            return 0xF0F0FAFF;
        }
        if (t >= ERASE + 1 && t < ERASE + 2.5f) {
            return 0xC0000818;
        }
        return 0;
    }
}
