package dev.infinitygauntlet.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.config.ClientConfig;
import dev.infinitygauntlet.network.VfxPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static dev.infinitygauntlet.client.vfx.VfxDraw.smooth;

/**
 * Holds and renders transient world-space effects requested by {@link VfxPayload}. Effects are pure client
 * presentation: they never touch game state, are culled by distance, and are capped in number.
 */
public final class ClientVfxManager {
    private static final int MAX_EFFECTS = 256;
    private static final List<Effect> EFFECTS = new ArrayList<>();

    public static void spawn(VfxPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double max = ClientConfig.SPEC.isLoaded() ? ClientConfig.VFX_DISTANCE.get() : 128;
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(payload.a()) > max * max) {
            return;
        }
        if (EFFECTS.size() >= MAX_EFFECTS) {
            EFFECTS.removeFirst();
        }
        Effect effect = new Effect(payload);
        effect.onSpawn();
        EFFECTS.add(effect);
    }

    public static void tick() {
        Iterator<Effect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            if (++e.age > e.duration) {
                it.remove();
            } else {
                e.tick();
            }
        }
    }

    public static void clear() {
        EFFECTS.clear();
    }

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float partialTick) {
        if (EFFECTS.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        for (Effect e : EFFECTS) {
            pose.pushPose();
            pose.translate(e.p.a().x - cam.x, e.p.a().y - cam.y, e.p.a().z - cam.z);
            e.render(pose, buffers, cam.subtract(e.p.a()), camera.rotation(), partialTick);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static final class Effect {
        final VfxPayload p;
        final int duration;
        int age;
        final float cr, cg, cb, hr, hg, hb;

        Effect(VfxPayload p) {
            this.p = p;
            this.duration = Math.max(1, p.duration());
            cr = VfxDraw.r(p.color());
            cg = VfxDraw.g(p.color());
            cb = VfxDraw.b(p.color());
            hr = VfxDraw.r(p.color2());
            hg = VfxDraw.g(p.color2());
            hb = VfxDraw.b(p.color2());
        }

        float progress(float pt) {
            return Mth.clamp((age + pt) / duration, 0, 1);
        }

        void onSpawn() {
            float density = ClientConfig.quality().density;
            Vec3 a = p.a();
            switch (p.vfx()) {
                case SPARK_BURST -> {
                    int n = (int) (40 * density * Math.max(0.6f, p.scale()));
                    for (int i = 0; i < n; i++) {
                        Vec3 dir = VfxDraw.unit(p.seed(), i).scale(0.15 + 0.35 * VfxDraw.hash(p.seed(), i, 4));
                        Particles.spark(a, dir.scale(Math.max(0.6, p.scale())), i % 3 == 0 ? p.color2() : p.color(), 0.12f + 0.1f * VfxDraw.hash(p.seed(), i, 5));
                    }
                    for (int i = 0; i < n / 4; i++) {
                        Particles.smoke(a.add(VfxDraw.unit(p.seed(), i + 99).scale(p.scale() * 0.4)), VfxDraw.unit(p.seed(), i + 199).scale(0.05),
                                darken(p.color()), Math.min(0.6f, p.scale() * 0.4f), 26);
                    }
                }
                case DISSOLVE -> {
                    double w = p.b().x, h = p.b().y;
                    int n = (int) (60 * density * Math.max(0.5, h / 1.8));
                    for (int i = 0; i < n; i++) {
                        Vec3 at = a.add((VfxDraw.hash(p.seed(), i, 1) - 0.5) * w * 1.2, VfxDraw.hash(p.seed(), i, 2) * h,
                                (VfxDraw.hash(p.seed(), i, 3) - 0.5) * w * 1.2);
                        Vec3 vel = at.subtract(a.add(0, h / 2, 0)).normalize().scale(0.04).add(0, 0.02, 0);
                        if (i % 3 == 0) {
                            Particles.smoke(at, vel, darken(p.color()), 0.35f, 18);
                        } else {
                            Particles.mote(at, vel.scale(2), i % 2 == 0 ? p.color2() : p.color(), 0.08f, 20);
                        }
                    }
                }
                case IMPLOSION -> {
                    int n = (int) (36 * density);
                    for (int i = 0; i < n; i++) {
                        Vec3 from = a.add(VfxDraw.unit(p.seed(), i).scale(p.scale() * 2.2));
                        Particles.inward(from, a, p.color2(), 0.1f);
                    }
                }
                case SHOCKWAVE -> {
                    Vec3 n = p.b().lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : p.b().normalize();
                    Vec3 ax = VfxDraw.perpendicular(n);
                    Vec3 ay = n.cross(ax).normalize();
                    int count = (int) (32 * density);
                    for (int i = 0; i < count; i++) {
                        double ang = i * Math.PI * 2 / count;
                        Vec3 dir = ax.scale(Math.cos(ang)).add(ay.scale(Math.sin(ang)));
                        Particles.spark(a.add(dir.scale(0.3)), dir.scale(p.scale() / duration * 1.1), p.color2(), 0.1f);
                    }
                }
                case AFTERIMAGES -> dev.infinitygauntlet.client.time.ClientHistory.keepAlive(duration + 40);
                case SUSPENDED_FIELD -> {
                    int n = (int) (140 * density);
                    for (int i = 0; i < n; i++) {
                        Vec3 at = a.add(VfxDraw.unit(p.seed(), i).scale(p.scale() * Math.cbrt(VfxDraw.hash(p.seed(), i, 9))));
                        Particles.suspended(at, i % 3 == 0 ? p.color2() : p.color(), 0.04f + 0.05f * VfxDraw.hash(p.seed(), i, 10), duration);
                    }
                }
                case GROUND_GLYPH -> {
                    int count = (int) (20 * density);
                    for (int i = 0; i < count; i++) {
                        double ang = i * Math.PI * 2 / count;
                        Particles.mote(a.add(Math.cos(ang) * p.scale(), 0.05, Math.sin(ang) * p.scale()), new Vec3(0, 0.03, 0), p.color(), 0.08f, 30);
                    }
                }
                default -> {
                }
            }
        }

        void tick() {
            float density = ClientConfig.quality().density;
            Vec3 a = p.a();
            switch (p.vfx()) {
                case RUPTURE_BURST -> {
                    if (Particles.chance(0.6f * density)) {
                        Vec3 from = a.add(VfxDraw.unit(p.seed() + age, 0).scale(p.scale() * 2.4));
                        Particles.inward(from, a, p.color2(), 0.09f);
                    }
                }
                case LINK -> {
                    if (Particles.chance(0.8f * density)) {
                        double t = Math.random();
                        Particles.mote(a.add(p.b().subtract(a).scale(t)), VfxDraw.unit(p.seed() + age, 1).scale(0.02), p.color2(), 0.07f, 12);
                    }
                }
                case GROUND_GLYPH -> {
                    if (Particles.chance(0.5f * density)) {
                        double ang = Math.random() * Math.PI * 2;
                        double rr = Math.random() * p.scale();
                        Particles.mote(a.add(Math.cos(ang) * rr, 0.05, Math.sin(ang) * rr), new Vec3(0, 0.05, 0), p.color2(), 0.06f, 20);
                    }
                }
                default -> {
                }
            }
        }

        void render(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float pt) {
            float t = progress(pt);
            float time = age + pt;
            PoseStack.Pose last = pose.last();
            switch (p.vfx()) {
                case SHOCKWAVE -> {
                    Vec3 n = p.b().lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : p.b().normalize();
                    Vec3 ax = VfxDraw.perpendicular(n);
                    Vec3 ay = n.cross(ax).normalize();
                    VertexConsumer vc = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING));
                    float e = 1 - (1 - t) * (1 - t);
                    float radius = p.scale() * e;
                    float width = p.scale() * 0.22f * (1 - t) + 0.15f;
                    float alpha = (float) Math.pow(1 - t, 1.4);
                    VfxDraw.ring(last, vc, ax, ay, Math.max(0, radius - width), radius + width * 0.4f, 64, time * 0.02f, cr, cg, cb, alpha);
                    float t2 = Mth.clamp((t - 0.12f) / 0.88f, 0, 1);
                    float r2 = p.scale() * 0.8f * (1 - (1 - t2) * (1 - t2));
                    VfxDraw.ring(last, vc, ax, ay, Math.max(0, r2 - width * 0.4f), r2 + width * 0.2f, 64, -time * 0.03f, hr, hg, hb, alpha * (t2 > 0 ? 0.9f : 0));
                    VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
                    VfxDraw.billboard(last, glow, camRot, 0, 0, 0, p.scale() * 0.7f * (1 - t), 0, hr, hg, hb, (1 - t) * 0.8f);
                }
                case SPHERE_PULSE -> {
                    float e = 1 - (1 - t) * (1 - t) * (1 - t);
                    VertexConsumer vc = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CRACKLE));
                    VfxDraw.sphere(last, vc, cam, p.scale() * e, 14, 24, time * 0.03f, cr, cg, cb, 0.7f * (1 - t), 0.03f * (1 - t));
                    VertexConsumer shell = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE));
                    VfxDraw.sphere(last, shell, cam, p.scale() * e * 0.98f, 14, 24, 0, hr, hg, hb, 0.22f * (1 - t) * (1 - t), 0.0f);
                }
                case IMPLOSION -> {
                    float shrink = smooth(t / 0.7f);
                    float radius = p.scale() * (1 - shrink);
                    VertexConsumer shell = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE));
                    VfxDraw.sphere(last, shell, cam, radius, 12, 20, 0, cr, cg, cb, 0.7f * (0.4f + shrink), 0.05f);
                    float flash = t > 0.6f ? 1 - Math.abs(t - 0.78f) / 0.22f : 0;
                    VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
                    VfxDraw.billboard(last, glow, camRot, 0, 0, 0, p.scale() * 0.6f * (1 - shrink) + 0.2f, 0, hr, hg, hb, 0.8f);
                    if (flash > 0) {
                        VfxDraw.billboard(last, glow, camRot, 0, 0, 0, p.scale() * 4.5f * flash, 0, hr, hg, hb, flash);
                        VfxDraw.billboard(last, glow, camRot, 0, 0, 0, p.scale() * 1.4f * flash, 0, 1, 1, 1, flash);
                    }
                }
                case BEAM -> {
                    Vec3 to = p.b().subtract(p.a());
                    float alpha = 1 - smooth((t - 0.5f) / 0.5f);
                    float grow = smooth(t / 0.15f);
                    Vec3 end = to.scale(grow);
                    VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
                    VfxDraw.beam(last, beam, Vec3.ZERO, end, cam, p.scale() * 2.5f, -time * 0.25f, cr, cg, cb, 0.6f * alpha);
                    VfxDraw.beam(last, beam, Vec3.ZERO, end, cam, p.scale(), -time * 0.4f, hr, hg, hb, alpha);
                    VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
                    VfxDraw.billboard(last, glow, camRot, 0, 0, 0, p.scale() * 5, 0, hr, hg, hb, alpha);
                    VfxDraw.billboard(last, glow, camRot, (float) end.x, (float) end.y, (float) end.z, p.scale() * 6, 0, cr, cg, cb, alpha);
                }
                case ARC -> {
                    VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
                    long seed = p.seed() * 31L + age / 2;
                    VfxDraw.lightning(last, lines, Vec3.ZERO, p.b().subtract(p.a()), cam, seed, 0.6f * p.scale(), 0.05f, cr, cg, cb, 1 - t);
                }
                case LINK -> {
                    Vec3 to = p.b().subtract(p.a());
                    float alpha = Math.min(1, t * 6) * (1 - smooth((t - 0.6f) / 0.4f));
                    VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
                    VfxDraw.beam(last, beam, Vec3.ZERO, to, cam, p.scale(), -time * 0.3f, cr, cg, cb, 0.7f * alpha);
                    VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
                    for (int k = 0; k < 3; k++) {
                        float f = (time * 0.06f + k / 3f) % 1f;
                        Vec3 at = to.scale(f);
                        VfxDraw.billboard(last, glow, camRot, (float) at.x, (float) at.y, (float) at.z, p.scale() * 4, 0, hr, hg, hb, alpha);
                    }
                    VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
                    VfxDraw.lightning(last, lines, Vec3.ZERO, to, cam, p.seed() * 17L + age / 2, 0.25f, 0.03f, hr, hg, hb, 0.8f * alpha);
                }
                case RUPTURE_BURST -> {
                    float open = Math.min(1f, t / 0.35f);
                    float close = Math.max(0f, (t - 0.65f) / 0.35f);
                    RuptureDraw.draw(pose, buffers, cam, p.scale(), open, close, time, p.seed(), RuptureDraw.Palette.of(p.color(), p.color2()), 1f, false);
                }
                case GROUND_GLYPH -> {
                    float alpha = smooth(t / 0.15f) * (1 - smooth((t - 0.75f) / 0.25f));
                    Vec3 ax = new Vec3(1, 0, 0);
                    Vec3 az = new Vec3(0, 0, 1);
                    pose.pushPose();
                    pose.mulPose(com.mojang.math.Axis.YP.rotation(time * 0.03f));
                    VertexConsumer vc = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_SPACE));
                    VfxDraw.plane(pose.last(), vc, ax, az, p.scale() * 2.2f, cr, cg, cb, alpha);
                    pose.popPose();
                    pose.pushPose();
                    pose.translate(0, 0.02, 0);
                    pose.mulPose(com.mojang.math.Axis.YP.rotation(-time * 0.05f));
                    vc = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_SPACE));
                    VfxDraw.plane(pose.last(), vc, ax, az, p.scale() * 1.3f, hr, hg, hb, alpha * 0.8f);
                    pose.popPose();
                }
                case DISSOLVE -> {
                    float alpha = 1 - t;
                    VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
                    float h = (float) p.b().y;
                    for (int k = 0; k < 3; k++) {
                        VfxDraw.billboard(last, glow, camRot, 0, h * (0.2f + 0.3f * k), 0, (float) Math.max(p.b().x, 0.6) * 2.5f * (1 + t), 0,
                                cr, cg, cb, 0.5f * alpha);
                    }
                }
                case DISTORTION -> {
                    VertexConsumer vc = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE));
                    VfxDraw.sphere(last, vc, cam, p.scale() * (1 + 0.15f * t), 12, 20, 0, cr * 0.3f, cg * 0.3f, cb * 0.4f, 0.4f * (1 - t), 0.02f);
                    VertexConsumer rim = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CRACKLE));
                    VfxDraw.sphere(last, rim, cam, p.scale() * (1.02f + 0.15f * t), 12, 20, time * 0.05f, hr, hg, hb, 0.7f * (1 - t), 0);
                }
                case SPARK_BURST -> {
                    VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
                    float f = 1 - smooth(t / 0.4f);
                    VfxDraw.billboard(last, glow, camRot, 0, 0, 0, p.scale() * 3.5f * (0.5f + t), 0, hr, hg, hb, f);
                }
                case CLOCK_GLYPH -> {
                    float alpha = smooth(t * duration / 10f) * (1 - smooth((t - 1 + 14f / duration) * duration / 14f));
                    float grow = 1 - (1 - smooth(t * duration / 12f)) * 0.6f;
                    Vec3 ax = new Vec3(1, 0, 0);
                    Vec3 az = new Vec3(0, 0, 1);
                    pose.pushPose();
                    pose.mulPose(com.mojang.math.Axis.YP.rotation(time * 0.02f));
                    VfxDraw.plane(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_TIME)), ax, az, p.scale() * 2.1f * grow,
                            cr, cg, cb, 0.85f * alpha);
                    pose.popPose();
                    pose.pushPose();
                    pose.translate(0, 0.02, 0);
                    pose.mulPose(com.mojang.math.Axis.YP.rotation(-time * 0.045f));
                    VfxDraw.plane(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_TIME)), ax, az, p.scale() * 1.1f * grow,
                            hr, hg, hb, 0.7f * alpha);
                    VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GEAR_RING)), ax, az, p.scale() * 1.02f * grow,
                            p.scale() * 1.16f * grow, 64, 0, cr, cg, cb, 0.8f * alpha);
                    pose.popPose();
                }
                case CLOCK_RINGS -> {
                    float alpha = smooth(t / 0.15f) * (1 - smooth((t - 0.6f) / 0.4f));
                    VertexConsumer vc = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GEAR_RING));
                    for (int k = -1; k <= 1; k++) {
                        float rr = p.scale() * (1.1f - Math.abs(k) * 0.25f) * (0.8f + 0.4f * t);
                        pose.pushPose();
                        pose.translate(0, k * p.scale() * 0.55f, 0);
                        pose.mulPose(com.mojang.math.Axis.YP.rotation(time * 0.12f * (k == 0 ? -1 : 1)));
                        VfxDraw.ring(pose.last(), vc, new Vec3(1, 0, 0), new Vec3(0, 0, 1), rr * 0.82f, rr, 40, 0, k == 0 ? hr : cr, k == 0 ? hg : cg,
                                k == 0 ? hb : cb, alpha);
                        pose.popPose();
                    }
                }
                case AFTERIMAGES -> {
                    net.minecraft.world.entity.Entity entity = Minecraft.getInstance().level.getEntity(p.seed());
                    if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
                        break;
                    }
                    float fade = Math.min(1f, (duration - age - pt) / 10f);
                    Vec3 now = entity.getPosition(pt);
                    for (int k = 1; k <= 5; k++) {
                        dev.infinitygauntlet.client.time.ClientHistory.Frame f = dev.infinitygauntlet.client.time.ClientHistory.get(p.seed(), k * 2);
                        if (f == null || now.distanceToSqr(f.x(), f.y(), f.z()) < 0.04) {
                            continue;
                        }
                        dev.infinitygauntlet.client.time.Afterimages.drawFrame(pose, buffers, living, f, p.a().x, p.a().y, p.a().z,
                                0.42f * fade * (1 - k / 6f), p.color());
                    }
                }
                case SUSPENDED_FIELD -> {
                }
            }
        }

        private static int darken(int rgb) {
            int r = (int) ((rgb >> 16 & 255) * 0.18f + 12), g = (int) ((rgb >> 8 & 255) * 0.18f + 14), b = (int) ((rgb & 255) * 0.25f + 20);
            return r << 16 | g << 8 | b;
        }
    }

    private ClientVfxManager() {
    }
}
