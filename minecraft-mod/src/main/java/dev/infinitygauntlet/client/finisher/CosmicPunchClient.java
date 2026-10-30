package dev.infinitygauntlet.client.finisher;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.power.CinematicCamera;
import dev.infinitygauntlet.client.time.Afterimages;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.finisher.CosmicPunchTimeline;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

import static dev.infinitygauntlet.finisher.CosmicPunchTimeline.*;

/**
 * Client side of the Cosmic Punch execution: the caster's whole body throws the punch (wind-up, lunge with torso
 * twist and stride, the gauntlet arm locked straight through the target, recoil, retraction), the target hangs on the
 * fist glowing hotter and brighter pink with cracks of light and heartbeat pulses until it erupts, and the camera
 * cuts between an over-the-shoulder lunge, a low side impact shot, a slow orbit, a push-in and a wide eruption shot.
 */
final class CosmicPunchClient implements ClientFinishers.ClientScript {
    private static final int PINK = 0xFF3FD2;
    private static final int HOT = 0xFFE6FA;

    private final ClientFinishers.Active a;
    private final Vec3 d;
    private final Vec3 right;
    private final float yaw;
    private final float halfWidth;
    private final float height;
    private final Vec3 core;
    private final Vec3 stand;
    private final HumanoidArm gauntletArm;
    private final float side;
    private final Deque<Vec3> fistTrail = new ArrayDeque<>();

    CosmicPunchClient(ClientFinishers.Active a) {
        this.a = a;
        LivingEntity target = a.target();
        this.halfWidth = target == null ? 0.3f : target.getBbWidth() * 0.5f;
        this.height = target == null ? 1.8f : target.getBbHeight();
        this.d = dir(a.casterStart, a.targetPos);
        this.right = new Vec3(-d.z, 0, d.x);
        this.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        this.core = a.targetPos.add(0, height * 0.55, 0);
        this.stand = standPoint(a.casterStart, a.targetPos.add(0, height * 0.5, 0), halfWidth);
        Entity caster = a.caster();
        HumanoidArm arm = HumanoidArm.RIGHT;
        if (caster instanceof Player p) {
            arm = p.getMainHandItem().getItem() instanceof InfinityGauntletItem ? p.getMainArm() : p.getMainArm().getOpposite();
        }
        this.gauntletArm = arm;
        this.side = arm == HumanoidArm.RIGHT ? 1 : -1;
    }

    private static float smooth(float x) {
        return VfxDraw.smooth(x);
    }

    @Override
    public Vec3 casterPos(float t) {
        return CosmicPunchTimeline.casterPos(a.casterStart, stand, t);
    }

    @Override
    public float casterYaw() {
        return yaw;
    }

    // ------------------------------------------------------------------ timeline curves

    private float windup(float t) {
        return smooth(t / LUNGE);
    }

    private float strike(float t) {
        float u = Mth.clamp((t - LUNGE) / (IMPACT - LUNGE), 0, 1);
        return u * u * (2.2f - 1.2f * u); // fast through the end: the fist does not slow before it lands
    }

    private float retract(float t) {
        return smooth((t - RETRACT) / 18f);
    }

    private float recoil(float t) {
        return t < ERUPTION ? 0 : (float) Math.exp(-(t - ERUPTION) * 0.3f);
    }

    /** Jolt of the impact itself, decaying over the hit-stop. */
    private float jolt(float t) {
        return t < IMPACT ? 0 : (float) Math.exp(-(t - IMPACT) * 0.45f);
    }

    /** Downward pitch from the caster's shoulder to the target's core, radians. */
    private float aimPitch() {
        double dy = stand.y + 1.37 - core.y;
        double dist = Math.max(0.4, halfWidth + 0.55);
        return (float) Math.atan2(dy, dist);
    }

    /** Blend weight of the scripted body over the vanilla pose. */
    private float bodyWeight(float t, float pt) {
        return smooth(t / 3f) * (1 - smooth((t - 98) / 12f)) * a.presence(pt);
    }

    // ------------------------------------------------------------------ the caster's body

    @Override
    public void poseCaster(HumanoidModel<?> m, LivingEntity caster, HumanoidArm arm, HumanoidArm gArm, float pt) {
        float t = a.time(pt);
        float w = bodyWeight(t, pt);
        if (w <= 0) {
            return;
        }
        float wu = windup(t), st = strike(t), re = retract(t), rc = recoil(t), c = charge(t);
        float hold = 1 - re;
        float tremble = Mth.sin(t * 3.3f) * (0.008f + 0.05f * c) * hold;
        float twist = Mth.lerp(st, 0.45f * wu, -0.5f) * hold;
        float bodyYaw = side * twist;
        float pitch = aimPitch();

        ModelPart part = arm == HumanoidArm.RIGHT ? m.rightArm : m.leftArm;
        boolean gauntlet = arm == gauntletArm;
        float x, y, z;
        if (gauntlet) {
            float aimed = -Mth.HALF_PI + pitch;
            x = Mth.lerp(st, Mth.lerp(wu, 0f, 0.65f), aimed);
            x = Mth.lerp(re, x, 0f) + tremble - 0.4f * rc * hold;
            y = -side * 0.22f * st * hold + tremble * 0.6f;
            z = side * 0.22f * wu * (1 - st);
        } else {
            x = Mth.lerp(st, Mth.lerp(wu, 0f, -1.05f), 0.6f) * hold;      // guard up during the wind-up, drawn back to the hip on impact
            y = side * 0.5f * wu * (1 - st) * hold;
            z = -side * (0.12f + 0.1f * rc) * st * hold;
        }
        blendRot(part, w, x, y + bodyYaw, z);
        // shoulders follow the torso twist, as in the vanilla swing
        float px = arm == HumanoidArm.RIGHT ? -Mth.cos(bodyYaw) * 5 : Mth.cos(bodyYaw) * 5;
        float pz = arm == HumanoidArm.RIGHT ? Mth.sin(bodyYaw) * 5 : -Mth.sin(bodyYaw) * 5;
        part.x = Mth.lerp(w, part.x, px);
        part.z = Mth.lerp(w, part.z, pz);

        if (arm == HumanoidArm.RIGHT) {
            m.body.yRot = Mth.lerp(w, m.body.yRot, bodyYaw);
            ModelPart back = gauntletArm == HumanoidArm.RIGHT ? m.rightLeg : m.leftLeg;
            ModelPart front = gauntletArm == HumanoidArm.RIGHT ? m.leftLeg : m.rightLeg;
            blendRot(back, w, Mth.lerp(st, 0.3f * wu, 0.6f) * hold, 0, side * 0.06f * st * hold);
            blendRot(front, w, Mth.lerp(st, -0.12f * wu, -0.55f) * hold, 0, -side * 0.08f * st * hold);
            blendRot(m.head, w, pitch * 0.8f * (0.3f + 0.7f * st) * hold, -bodyYaw * 0.5f, 0);
        }
    }

    private static void blendRot(ModelPart p, float w, float x, float y, float z) {
        p.xRot = Mth.lerp(w, p.xRot, x);
        p.yRot = Mth.lerp(w, p.yRot, y);
        p.zRot = Mth.lerp(w, p.zRot, z);
    }

    @Override
    public void transformCaster(PoseStack pose, LivingEntity caster, float pt) {
        float t = a.time(pt);
        float w = bodyWeight(t, pt);
        float lean = (Mth.lerp(strike(t), -5 * windup(t), 17) * (1 - retract(t)) - 14 * recoil(t)) * w;
        pose.mulPose(Axis.of(new Vector3f((float) d.z, 0, (float) -d.x)).rotationDegrees(lean));
    }

    // ------------------------------------------------------------------ the target

    private enum Body {HUMANOID, QUADRUPED, BLOB, CRAWLER, OTHER}

    private static Body body(LivingEntity e) {
        float w = e.getBbWidth(), h = e.getBbHeight();
        if (e instanceof Slime || e instanceof MagmaCube) {
            return Body.BLOB;
        }
        if (h >= 1.2f && w < 1.0f) {
            return Body.HUMANOID;
        }
        if (w >= 1.1f && h < 1.0f) {
            return Body.CRAWLER;
        }
        if (w >= 0.6f && h < 1.6f) {
            return Body.QUADRUPED;
        }
        return Body.OTHER;
    }

    @Override
    public void transformTarget(PoseStack pose, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < IMPACT || t >= ERUPTION + 2) {
            return;
        }
        float c = charge(t), jolt = jolt(t);
        float h = e.getBbHeight();
        float shake = 0.015f + 0.06f * c;
        pose.translate(Mth.sin(t * 5.1f) * shake, 0, Mth.cos(t * 4.3f) * shake);
        Axis tip = Axis.of(new Vector3f((float) d.z, 0, (float) -d.x));
        switch (body(e)) {
            case HUMANOID -> {
                // impaled on the fist: thrown back at the hips, then lifted and arched as the energy fills it
                pose.translate(0, 0.14f * c, 0);
                pose.translate(0, h * 0.45f, 0);
                pose.mulPose(tip.rotationDegrees(20 * jolt + 16 * c));
                pose.translate(0, -h * 0.45f, 0);
            }
            case QUADRUPED -> {
                // rears up off its front legs
                pose.translate(d.x * -h * 0.4f, 0, d.z * -h * 0.4f);
                pose.mulPose(tip.rotationDegrees(-(14 * jolt + 22 * c)));
                pose.translate(d.x * h * 0.4f, 0, d.z * h * 0.4f);
            }
            case BLOB -> {
                float swell = 1 + 0.4f * c + 0.08f * Mth.sin(t * 1.7f) * c + 0.25f * jolt;
                pose.scale(swell, 1 + 0.3f * c - 0.2f * jolt, swell);
            }
            case CRAWLER -> {
                pose.scale(1 + 0.1f * c, 1 - 0.25f * c, 1 + 0.1f * c); // legs curl in
                pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.9f) * 8 * c));
            }
            case OTHER -> {
                pose.translate(0, 0.3f * c, 0);
                pose.mulPose(Axis.YP.rotationDegrees(t * 4 * c));
            }
        }
    }

    @Override
    public void overlayTarget(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < IMPACT || t >= ERUPTION) {
            return;
        }
        float c = charge(t);
        int color = lerpColor(PINK, HOT, c * c);
        float body = Mth.lerp(pt, e.yBodyRotO, e.yBodyRot);
        float head = Mth.lerp(pt, e.yHeadRotO, e.yHeadRot);
        float limbPos = e.walkAnimation.position(pt), limbSpeed = e.walkAnimation.speed(pt), age = e.tickCount + pt;
        // a glowing skin that grows from a faint flush to blinding
        Afterimages.draw(pose, buffers, e, 0, 0, 0, body, head, e.getXRot(), limbPos, limbSpeed, age, 0.25f + 0.75f * c, color, 1.02f);
        // heartbeat pulses running outward through the body
        for (int hb : HEARTBEATS) {
            float u = (t - hb) / 10f;
            if (u >= 0 && u < 1) {
                Afterimages.draw(pose, buffers, e, 0, 0, 0, body, head, e.getXRot(), limbPos, limbSpeed, age, (1 - u) * (0.4f + 0.6f * c), PINK,
                        1.04f + 0.3f * u);
            }
        }
        // cracks of light splitting across the body
        long seed = e.getId() * 977L;
        int cracks = 2 + (int) (c * 16);
        float hw = e.getBbWidth() * 0.55f, h = e.getBbHeight();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(e.getPosition(pt));
        VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
        for (int i = 0; i < cracks; i++) {
            Vec3 p0 = surface(seed, i * 2, hw, h);
            Vec3 p1 = surface(seed, i * 2 + 1, hw, h).lerp(p0, 0.45);
            float flicker = 0.6f + 0.4f * Mth.sin(t * 0.9f + i);
            VfxDraw.lightning(pose.last(), lines, p0, p1, cam, seed + i * 31L + (long) (t / 3), 0.35f, 0.02f + 0.03f * c,
                    VfxDraw.r(color), VfxDraw.g(color), VfxDraw.b(color), flicker * (0.4f + 0.6f * c));
        }
        // the light it gives off
        Quaternionf camRot = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        VfxDraw.billboard(pose.last(), glow, camRot, 0, h * 0.55f, 0, h * (1.4f + 4.5f * c), 0, 1f, 0.3f, 0.85f, 0.25f + 0.6f * c);
        VfxDraw.billboard(pose.last(), glow, camRot, 0, h * 0.55f, 0, h * (0.6f + 1.6f * c), 0, 1f, 0.9f, 1f, 0.2f + 0.7f * c);
    }

    /** A deterministic point on the surface of the target's box, relative to its feet. */
    private static Vec3 surface(long seed, int i, float hw, float h) {
        Vec3 u = VfxDraw.unit(seed, i);
        double m = Math.max(Math.abs(u.x) / hw, Math.max(Math.abs(u.y) / (h * 0.5), Math.abs(u.z) / hw));
        return new Vec3(u.x / m, h * 0.5 + u.y / m, u.z / m);
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ fist and world effects

    /** Approximate world position of the gauntlet fist, from the same curves that pose the arm. */
    private Vec3 fist(float t) {
        Vec3 base = casterPos(t);
        float st = strike(t), wu = windup(t), re = retract(t);
        float x = Mth.lerp(st, Mth.lerp(wu, 0f, 0.65f), -Mth.HALF_PI + aimPitch());
        x = Mth.lerp(re, x, 0f);
        float lean = (Mth.lerp(st, -5 * wu, 17) * (1 - re)) * Mth.DEG_TO_RAD;
        Vec3 shoulder = base.add(right.scale(0.31 * side)).add(0, 1.37 * Math.cos(lean), 0).add(d.scale(1.37 * Math.sin(lean)));
        Vec3 armDir = d.scale(-Math.sin(x)).add(0, -Math.cos(x), 0);
        if (st > 0.5f && re < 0.5f) {
            armDir = core.add(d.scale(halfWidth * 0.6)).subtract(shoulder).normalize().lerp(armDir, re * 2);
        }
        return shoulder.add(armDir.scale(0.78));
    }

    @Override
    public void tick() {
        float t = a.age;
        Vec3 f = fist(t);
        fistTrail.addFirst(f);
        while (fistTrail.size() > 7) {
            fistTrail.removeLast();
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t < IMPACT) {
            for (int i = 0; i < Particles.scaled(4); i++) {
                Particles.mote(f.add(r.nextGaussian() * 0.08, r.nextGaussian() * 0.08, r.nextGaussian() * 0.08), Vec3.ZERO, i % 2 == 0 ? 0xA032FF : PINK,
                        0.1f, 10);
            }
        } else if (t < ERUPTION) {
            float c = charge(t);
            for (int i = 0; i < Particles.scaled(2 + 10 * c); i++) {
                Vec3 out = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7, r.nextGaussian()).normalize();
                Vec3 at = core.add(out.multiply(halfWidth * 1.1, height * 0.45, halfWidth * 1.1));
                if (i % 3 == 0) {
                    Particles.spark(at, out.scale(0.05 + 0.15 * c), i % 2 == 0 ? PINK : 0xFFFFFF, 0.08f + 0.05f * c);
                } else {
                    Vec3 swirl = out.cross(new Vec3(0, 1, 0)).scale(0.04).add(0, 0.02, 0);
                    Particles.mote(at, swirl, i % 2 == 0 ? PINK : 0xC060FF, 0.07f + 0.05f * c, 18);
                }
            }
            for (int hb : HEARTBEATS) {
                if ((int) t == hb) {
                    for (int i = 0; i < Particles.scaled(24); i++) {
                        double ang = i * Math.PI * 2 / 24;
                        Vec3 ring = right.scale(Math.cos(ang)).add(0, Math.sin(ang), 0);
                        Particles.spark(core, ring.scale(0.25 + 0.3 * c), PINK, 0.09f);
                    }
                }
            }
        } else if (t < ERUPTION + 30) {
            // residual cosmic energy dissolving around the withdrawn fist
            float fade = 1 - (t - ERUPTION) / 30f;
            for (int i = 0; i < Particles.scaled(5 * fade); i++) {
                Vec3 at = f.add(r.nextGaussian() * 0.12, r.nextGaussian() * 0.12, r.nextGaussian() * 0.12);
                if (i % 3 == 0) {
                    Particles.smoke(at, new Vec3(0, 0.03, 0), 0x3A0A48, 0.3f, 24);
                } else {
                    Particles.mote(at, new Vec3(r.nextGaussian() * 0.01, 0.03, r.nextGaussian() * 0.01), i % 2 == 0 ? PINK : 0xA032FF, 0.06f, 22);
                }
            }
        }
    }

    @Override
    public void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        float t = a.time(pt);
        Vec3 camPos = camera.getPosition();
        Vec3 f = fist(t);
        pose.pushPose();
        pose.translate(f.x - camPos.x, f.y - camPos.y, f.z - camPos.z);
        Vec3 cam = camPos.subtract(f);
        Quaternionf camRot = camera.rotation();
        if (t < ERUPTION + 20) {
            // the arm's energy trail during the lunge
            if (t > LUNGE - 1 && t < IMPACT + 4 && fistTrail.size() > 1) {
                VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
                Vec3 prev = Vec3.ZERO;
                int k = 0;
                for (Vec3 p : fistTrail) {
                    Vec3 local = p.subtract(f);
                    if (k > 0) {
                        float fade = 1 - k / (float) fistTrail.size();
                        VfxDraw.beam(pose.last(), beam, prev, local, cam, 0.45f * fade, -t * 0.4f, 0.63f, 0.2f, 1f, 0.9f * fade);
                        VfxDraw.beam(pose.last(), beam, prev, local, cam, 0.18f * fade, -t * 0.4f, 1f, 0.6f, 0.95f, fade);
                    }
                    prev = local;
                    k++;
                }
            }
            float glowA = t < IMPACT ? smooth(t / IMPACT) : t < ERUPTION ? 0.7f + 0.3f * charge(t) : 1 - (t - ERUPTION) / 20f;
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(pose.last(), glow, camRot, 0, 0, 0, 0.9f + 0.6f * charge(t), 0, 1f, 0.35f, 0.9f, 0.8f * glowA);
            // energy pouring from the fist into the body
            if (t >= IMPACT && t < ERUPTION) {
                VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
                float c = charge(t);
                long seed = a.targetId * 131L + (long) (t / 2);
                for (int i = 0; i < 2 + (int) (c * 4); i++) {
                    Vec3 to = core.add(VfxDraw.unit(seed, i).multiply(halfWidth, height * 0.4, halfWidth)).subtract(f);
                    VfxDraw.lightning(pose.last(), lines, Vec3.ZERO, to, cam, seed + i, 0.5f, 0.03f, 1f, 0.4f, 0.9f, 0.5f + 0.5f * c);
                }
            }
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ camera and screen

    @Override
    public CinematicCamera.Shot shot(float pt) {
        float t = a.time(pt);
        Vec3 mid = stand.add(0, 1.2, 0).lerp(core, 0.5);
        if (t < IMPACT) {
            // over the shoulder, riding the lunge
            Vec3 base = casterPos(t);
            Vec3 pos = base.subtract(d.scale(2.4)).add(right.scale(-1.0 * side)).add(0, 1.8, 0);
            return new CinematicCamera.Shot(pos, base.add(0, 1.4, 0).lerp(core, 0.6), 0, 1.0f - 0.1f * strike(t));
        }
        if (t < IMPACT + 5) {
            // hard cut: low, close, from the side, at the moment the fist goes through
            float u = (t - IMPACT) / 5f;
            Vec3 pos = mid.add(right.scale(2.3 * side)).add(d.scale(-0.2)).add(0, -0.45 + 0.1 * u, 0);
            return new CinematicCamera.Shot(pos, mid, 7 * (1 - u), 0.8f);
        }
        if (t < 58) {
            // slow orbit around the frozen pair as the energy builds
            float u = (t - IMPACT - 5) / (58 - IMPACT - 5f);
            double ang = u * 1.15;
            Vec3 around = right.scale(side * Math.cos(ang)).add(d.scale(Math.sin(ang)));
            Vec3 pos = mid.add(around.scale(2.6 + 0.6 * u)).add(0, -0.2 + 0.6 * u, 0);
            return new CinematicCamera.Shot(pos, mid.add(0, 0.1, 0), 4 * Mth.sin(u * Mth.PI), 0.88f);
        }
        if (t < ERUPTION) {
            // push in on the target as it reaches critical
            float u = (t - 58) / (ERUPTION - 58f);
            Vec3 pos = core.add(d.scale(1.9 - 0.7 * u)).add(right.scale(0.8 * side)).add(0, 0.2, 0);
            return new CinematicCamera.Shot(pos, core, -9 * u, 0.82f - 0.1f * u);
        }
        // cut wide for the eruption, holding as control eases back
        float u = Mth.clamp((t - ERUPTION) / 30f, 0, 1);
        Vec3 pos = mid.add(right.scale((7.5 + 2 * u) * side)).subtract(d.scale(2.5)).add(0, 2.4 + 2 * u, 0);
        return new CinematicCamera.Shot(pos, core.add(0, 0.6 * u, 0), 0, 1.05f);
    }

    @Override
    public int grade(float pt) {
        float t = a.time(pt);
        float alpha;
        if (t < IMPACT) {
            alpha = 0.05f * smooth(t / IMPACT);
        } else if (t < ERUPTION) {
            alpha = 0.06f + 0.3f * charge(t);
        } else {
            alpha = 0.36f * (1 - smooth((t - ERUPTION) / 26f));
        }
        return (int) (alpha * 255) << 24 | PINK;
    }

    @Override
    public int impactFrame(float pt) {
        float t = a.time(pt);
        if (t >= IMPACT && t < IMPACT + 1) {
            return 0xF0FFFFFF; // white frame as the fist connects
        }
        if (t >= IMPACT + 1 && t < IMPACT + 2.5f) {
            return 0xB8140020; // then a beat of near-black: the classic impact frame
        }
        if (t >= ERUPTION && t < ERUPTION + 1.5f) {
            return 0xF8FFF0FC;
        }
        return 0;
    }
}
