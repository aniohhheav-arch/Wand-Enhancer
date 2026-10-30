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
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

import static dev.infinitygauntlet.finisher.Timelines.Rewrite.*;

/**
 * Reality: Reality Rewrite. An open palm and the target stops dead; then its geometry is rewritten: it warps and
 * twists, becomes a heap of crimson cubes, splits into red and cyan copies of itself, turns to inverted glass. Each
 * rewrite lands with a glitch. Finally the palm closes and the target shatters into fragments of reality that drift
 * up and dissolve.
 */
final class RewriteClient extends StationaryClient {
    private static final int RED = InfinityStone.REALITY.color();
    private static final int PALE = InfinityStone.REALITY.highlight();
    private static final BlockState[] CUBES = {Blocks.RED_STAINED_GLASS.defaultBlockState(), Blocks.CRIMSON_NYLIUM.defaultBlockState(),
            Blocks.RED_CONCRETE.defaultBlockState(), Blocks.MAGENTA_STAINED_GLASS.defaultBlockState()};

    RewriteClient(ClientFinishers.Active a) {
        super(a);
    }

    /** Which surreal state the target is in: -1 before, 0..3 during, 4 after. */
    private int state(float t) {
        if (t < STATES) {
            return -1;
        }
        return Math.min(4, (int) ((t - STATES) / STATE_LENGTH));
    }

    /** 0..1 within the current state. */
    private float stateTime(float t) {
        return ((t - STATES) % STATE_LENGTH) / STATE_LENGTH;
    }

    private boolean glitchFrame(float t) {
        return t >= STATES && t < SHATTER && stateTime(t) < 0.12f;
    }

    @Override
    public void poseCaster(HumanoidModel<?> m, LivingEntity caster, HumanoidArm arm, HumanoidArm g, float pt) {
        float t = a.time(pt);
        float w = bodyWeight(t, pt);
        float raise = smooth(window(t, 0, FREEZE));
        float close = smooth(window(t, SHATTER - 4, SHATTER));
        float release = smooth(window(t, SHATTER + 8, SHATTER + 22));
        // each rewrite twists the wrist like turning a dial
        float twist = state(t) >= 0 && state(t) < 4 ? Mth.sin(stateTime(t) * Mth.PI) * 0.45f : 0;
        if (arm == gauntletArm) {
            float[] aimed = aim(arm, core);
            float x = Mth.lerp(raise, 0, aimed[0]);
            x = Mth.lerp(close, x, aimed[0] + 0.45f);
            blend(part(m, arm), w, Mth.lerp(release, x, 0), aimed[1] * raise * (1 - release), side * twist * (1 - close));
        } else {
            blend(part(m, arm), w, -0.3f * raise * (1 - release), 0, -side * 0.4f * raise * (1 - release));
        }
        if (arm == HumanoidArm.RIGHT) {
            stance(m, w, 1, core);
        }
    }

    @Override
    public void transformTarget(PoseStack pose, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < FREEZE) {
            return;
        }
        float hc = h * 0.5f;
        pose.translate(0, hc, 0);
        int k = state(t);
        if (k == -1) {
            // the first rewrite: its proportions start to slip
            float u = window(t, FREEZE, STATES);
            pose.scale(1 + 0.18f * u * Mth.sin(t * 0.9f), 1 - 0.12f * u * Mth.sin(t * 1.3f), 1 + 0.18f * u * Mth.cos(t * 0.8f));
        } else if (k == 0) {
            // twisted like taffy
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.45f) * 55));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.3f) * 20));
            pose.scale(1 + 0.3f * Mth.sin(t * 0.7f), 1 - 0.25f * Mth.sin(t * 0.7f), 1);
        } else if (k == 1 || k == 3) {
            pose.scale(0.0001f, 0.0001f, 0.0001f); // replaced by cubes / glass, drawn in the overlay
        } else if (k == 2) {
            pose.translate(Mth.sin(t * 7) * 0.05f, 0, 0);
        } else {
            float s = 1 - window(t, SHATTER - 4, SHATTER);
            pose.scale(s, s, s);
        }
        if (glitchFrame(t)) {
            pose.translate(ThreadLocalRandom.current().nextGaussian() * 0.15, 0, ThreadLocalRandom.current().nextGaussian() * 0.15);
        }
        pose.translate(0, -hc, 0);
    }

    @Override
    public void overlayTarget(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < FREEZE || t >= SHATTER) {
            return;
        }
        int k = state(t);
        float limb = e.walkAnimation.position(pt), age = e.tickCount + pt;
        switch (k) {
            case -1, 0 -> ghost(pose, buffers, e, pt, 0.35f, RED, 1.04f);
            case 1 -> {
                // a heap of crimson cubes holding its silhouette
                var dispatcher = Minecraft.getInstance().getBlockRenderer();
                int nx = Math.max(2, Math.round(hw * 2 / 0.3f)), ny = Math.max(3, Math.round(h / 0.3f));
                float cell = Math.max(hw * 2 / nx, h / ny);
                for (int ix = 0; ix < nx; ix++) {
                    for (int iy = 0; iy < ny; iy++) {
                        for (int iz = 0; iz < nx; iz++) {
                            long sd = (long) ix * 73856093L ^ (long) iy * 19349663L ^ (long) iz * 83492791L ^ e.getId();
                            if (VfxDraw.hash(sd, 1, 2) < 0.35f) {
                                continue;
                            }
                            float jitter = (VfxDraw.hash(sd, (int) (t / 2), 3) - 0.5f) * 0.06f;
                            float s = cell * (0.75f + 0.2f * Mth.sin(t * 0.8f + iy));
                            pose.pushPose();
                            pose.translate(-hw + (ix + 0.5f) * cell + jitter, (iy + 0.5f) * cell, -hw + (iz + 0.5f) * cell);
                            pose.mulPose(Axis.YP.rotationDegrees(VfxDraw.hash(sd, 4, 5) * 40 + t * 3));
                            pose.scale(s, s, s);
                            pose.translate(-0.5, -0.5, -0.5);
                            dispatcher.renderSingleBlock(CUBES[(ix + iy + iz) % CUBES.length], pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
                            pose.popPose();
                        }
                    }
                }
            }
            case 2 -> {
                // chromatic split: red and cyan copies pulling apart
                float split = 0.15f + 0.25f * stateTime(t);
                pose.pushPose();
                pose.translate(right.x * split, 0.03, right.z * split);
                ghost(pose, buffers, e, pt, 0.7f, 0xFF2020, 1f, limb, age);
                pose.popPose();
                pose.pushPose();
                pose.translate(-right.x * split, -0.03, -right.z * split);
                ghost(pose, buffers, e, pt, 0.7f, 0x20FFFF, 1f, limb + 0.5f, age + 3);
                pose.popPose();
            }
            case 3 -> {
                // inverted glass: a pale shell over a dark core, flickering between the two
                boolean flip = ((int) (t * 1.5f)) % 2 == 0;
                ghost(pose, buffers, e, pt, 0.9f, flip ? 0xF0F0FF : 0x101018, 1f);
                ghost(pose, buffers, e, pt, 0.5f, flip ? 0x400008 : PALE, 1.06f);
            }
            default -> {
            }
        }
    }

    /** A fragment's motion after the shatter, deterministic per index. */
    private Vec3 fragment(int i, float u) {
        Vec3 dir = VfxDraw.unit(a.targetId * 11L, i);
        double out = 0.6 + 2.2 * VfxDraw.hash(a.targetId, i, 7);
        return core.add(dir.scale(out * Math.sqrt(u) * Math.max(1, h * 0.6))).add(0, 1.6 * u * u, 0);
    }

    @Override
    public void tick() {
        float t = a.age;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t >= FREEZE && t < SHATTER) {
            for (int i = 0; i < Particles.scaled(3); i++) {
                Vec3 at = core.add(r.nextGaussian() * hw * 1.4, r.nextGaussian() * h * 0.4, r.nextGaussian() * hw * 1.4);
                Particles.suspended(at, i % 2 == 0 ? RED : PALE, 0.05f, 30);
            }
        }
        if ((int) t == SHATTER) {
            for (int i = 0; i < Particles.scaled(40); i++) {
                Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                Particles.shard(core, dir.scale(0.25 + r.nextDouble() * 0.3), i % 2 == 0 ? RED : PALE, 0.14f, 30);
            }
        }
    }

    @Override
    public void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        float t = a.time(pt);
        Vec3 camPos = camera.getPosition();
        Quaternionf camRot = camera.rotation();
        if (t >= FREEZE && t < SHATTER) {
            // ripples of rewritten space around the target
            pose.pushPose();
            pose.translate(core.x - camPos.x, core.y - camPos.y, core.z - camPos.z);
            Vector3f rx = camRot.transform(new Vector3f(1, 0, 0)), ry = camRot.transform(new Vector3f(0, 1, 0));
            for (int i = 0; i < 3; i++) {
                float u = ((t - FREEZE) * 0.04f + i / 3f) % 1f;
                float rr = Math.max(1, h) * (0.6f + 1.8f * u);
                VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING)), new Vec3(rx.x, rx.y, rx.z), new Vec3(ry.x, ry.y, ry.z),
                        rr * 0.9f, rr, 48, 0, VfxDraw.r(RED), VfxDraw.g(RED), VfxDraw.b(RED), 0.5f * (1 - u));
            }
            pose.popPose();
        }
        if (t >= SHATTER && t < a.type.duration) {
            // fragments of reality drifting up and dissolving
            float u = window(t, SHATTER, a.type.duration);
            VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
            for (int i = 0; i < 36; i++) {
                Vec3 p = fragment(i, u);
                Vec3 axis = VfxDraw.rotate(VfxDraw.unit(a.targetId * 17L, i), VfxDraw.unit(a.targetId * 19L, i), t * 0.2).scale(0.18 + 0.15 * VfxDraw.hash(i, 1, 1));
                Vec3 local = p.subtract(camPos);
                pose.pushPose();
                pose.translate(local.x, local.y, local.z);
                boolean pale = i % 3 == 0;
                VfxDraw.beam(pose.last(), beam, axis.scale(-1), axis, camPos.subtract(p), 0.2f, 0, pale ? 1 : VfxDraw.r(RED), pale ? 0.8f : VfxDraw.g(RED),
                        pale ? 0.8f : VfxDraw.b(RED), (1 - u) * (0.6f + 0.4f * Mth.sin(t * 0.7f + i)));
                pose.popPose();
            }
        }
    }

    @Override
    public CinematicCamera.Shot shot(float pt) {
        float t = a.time(pt);
        if (t < FREEZE) {
            return shot(eye().subtract(d.scale(2.2)).add(right.scale(-side)).add(0, 0.2, 0), core, 0, 1f);
        }
        if (t < STATES) {
            return shot(core.add(right.scale(side * 2.4)).subtract(d.scale(0.8)).add(0, -0.3, 0), core, 14, 0.85f);
        }
        if (t < SHATTER) {
            // circling with a rolling, unstable horizon; each rewrite jumps to a new angle
            int k = state(t);
            float u = stateTime(t);
            double ang = k * 1.6 + u * 0.5;
            Vec3 around = right.scale(Math.cos(ang)).subtract(d.scale(Math.sin(ang)));
            return shot(core.add(around.scale(3.2 - 0.6 * u)).add(0, 0.3 + 0.4 * Mth.sin(k), 0), core, Mth.sin(t * 0.15f) * 18, 0.85f);
        }
        float u = window(t, SHATTER, a.type.duration);
        return shot(core.subtract(d.scale(4 + 2 * u)).add(right.scale(side * 3)).add(0, 1 + 1.5 * u, 0), core.add(0, u, 0), 0, 1f);
    }

    @Override
    public int grade(float pt) {
        float t = a.time(pt);
        float alpha = t < FREEZE ? 0.05f : t < SHATTER ? 0.22f + (glitchFrame(t) ? 0.2f : 0) : 0.3f * (1 - window(t, SHATTER, SHATTER + 20));
        return grade(0x8A0A14, alpha);
    }

    @Override
    public int impactFrame(float pt) {
        float t = a.time(pt);
        if (t >= FREEZE && t < FREEZE + 1) {
            return 0xD0FFE0E0;
        }
        if (glitchFrame(t) && stateTime(t) < 0.06f) {
            return 0x90200008;
        }
        if (t >= SHATTER && t < SHATTER + 1.5f) {
            return 0xF0FFF0F0;
        }
        return 0;
    }
}
