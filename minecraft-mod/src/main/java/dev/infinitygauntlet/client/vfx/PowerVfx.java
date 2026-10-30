package dev.infinitygauntlet.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.config.ClientConfig;
import dev.infinitygauntlet.network.VfxPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import static dev.infinitygauntlet.client.vfx.VfxDraw.hash;
import static dev.infinitygauntlet.client.vfx.VfxDraw.smooth;

/**
 * The Power Stone's effect primitives. Each one has a distinct shape and rhythm so the abilities read differently:
 * fissures that race across the ground, a coiling beam, a churning fireball, lock-on reticles, real block debris,
 * crystal novas, punch impact frames and mushrooming smoke columns.
 */
public final class PowerVfx {
    private static final int MAX = 160;
    private static final List<Effect> EFFECTS = new ArrayList<>();

    public static boolean handles(VfxPayload p) {
        return switch (p.vfx()) {
            case GROUND_CRACKS, HELIX_BEAM, POWER_EXPLOSION, CHARGE, ENTITY_TRAIL, LOCK_RETICLE, DEBRIS, SHARD_NOVA, IMPACT_STAR, SMOKE_COLUMN -> true;
            default -> false;
        };
    }

    public static void spawn(VfxPayload p) {
        if (EFFECTS.size() >= MAX) {
            EFFECTS.removeFirst();
        }
        Effect e = new Effect(p);
        e.onSpawn();
        EFFECTS.add(e);
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

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        if (EFFECTS.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        for (Effect e : EFFECTS) {
            Vec3 origin = e.origin(pt);
            pose.pushPose();
            pose.translate(origin.x - cam.x, origin.y - cam.y, origin.z - cam.z);
            e.render(pose, buffers, cam.subtract(origin), camera.rotation(), pt);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static final class Effect {
        final VfxPayload p;
        final int duration;
        final float cr, cg, cb, hr, hg, hb;
        final long seed;
        int age;
        final Deque<Vec3> trail = new ArrayDeque<>();
        Vec3 follow;
        Vec3 followO;

        Effect(VfxPayload p) {
            this.p = p;
            this.duration = Math.max(1, p.duration());
            this.seed = p.seed();
            cr = VfxDraw.r(p.color());
            cg = VfxDraw.g(p.color());
            cb = VfxDraw.b(p.color());
            hr = VfxDraw.r(p.color2());
            hg = VfxDraw.g(p.color2());
            hb = VfxDraw.b(p.color2());
            follow = p.a();
            followO = p.a();
        }

        float progress(float pt) {
            return Mth.clamp((age + pt) / duration, 0, 1);
        }

        Vec3 origin(float pt) {
            return switch (p.vfx()) {
                case ENTITY_TRAIL, LOCK_RETICLE -> followO.lerp(follow, pt);
                default -> p.a();
            };
        }

        Entity tracked() {
            return Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(p.seed());
        }

        void onSpawn() {
            Vec3 a = p.a();
            switch (p.vfx()) {
                case POWER_EXPLOSION -> {
                    float s = p.scale();
                    for (int i = 0; i < Particles.scaled(30 * Math.min(4, s)); i++) {
                        Vec3 dir = VfxDraw.unit(seed, i);
                        Particles.spark(a, dir.scale(0.25 + 0.35 * hash(seed, i, 3)).scale(Math.max(0.6, s * 0.5)), i % 3 == 0 ? p.color2() : p.color(), 0.14f);
                    }
                    for (int i = 0; i < Particles.scaled(10 * Math.min(4, s)); i++) {
                        Vec3 dir = VfxDraw.unit(seed, i + 300);
                        Particles.smoke(a.add(dir.scale(s * 0.5)), dir.scale(0.06 * s).add(0, 0.03, 0), 0x1E0A30, Math.min(2.5f, s * 0.6f), 50);
                    }
                }
                case SHARD_NOVA -> {
                    for (int i = 0; i < Particles.scaled(26); i++) {
                        Vec3 dir = VfxDraw.unit(seed, i);
                        Particles.shard(a, dir.scale(p.scale() * 0.08), i % 2 == 0 ? p.color() : p.color2(), 0.14f, 22);
                    }
                }
                case DEBRIS -> {
                    BlockState state = Block.stateById(p.color());
                    var level = Minecraft.getInstance().level;
                    if (level != null && !state.isAir()) {
                        for (int i = 0; i < Particles.scaled(p.color2() * 2); i++) {
                            Vec3 dir = new Vec3(hash(seed, i, 1) - 0.5, hash(seed, i, 2), hash(seed, i, 3) - 0.5);
                            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), a.x + dir.x, a.y + 0.2, a.z + dir.z,
                                    dir.x * 0.6, dir.y * 0.6 * p.scale(), dir.z * 0.6);
                        }
                    }
                    Particles.smoke(a.add(0, 0.3, 0), new Vec3(0, 0.03, 0), 0x3A3040, 1.2f * p.scale(), 40);
                }
                case IMPACT_STAR -> {
                    Vec3 d = p.b().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.b().normalize();
                    for (int i = 0; i < Particles.scaled(24); i++) {
                        Vec3 dir = d.add(VfxDraw.unit(seed, i).scale(0.7)).normalize();
                        Particles.spark(a, dir.scale(0.6 + 0.5 * hash(seed, i, 4)), i % 2 == 0 ? 0xFFFFFF : p.color(), 0.12f);
                    }
                }
                case GROUND_CRACKS -> {
                    for (int i = 0; i < Particles.scaled(12 * Math.min(3, p.scale() / 3)); i++) {
                        double ang = hash(seed, i, 5) * Math.PI * 2, rr = hash(seed, i, 6) * p.scale();
                        Particles.smoke(a.add(Math.cos(ang) * rr, 0.1, Math.sin(ang) * rr), new Vec3(0, 0.04, 0), 0x3A3040, 0.7f, 30);
                    }
                }
                default -> {
                }
            }
        }

        void tick() {
            Vec3 a = p.a();
            switch (p.vfx()) {
                case CHARGE -> {
                    float t = (float) age / duration;
                    for (int i = 0; i < Particles.scaled(3 + 6 * t); i++) {
                        Vec3 from = a.add(VfxDraw.unit(seed + age, i).scale(p.scale() * (0.8 + 0.4 * hash(seed, age, i))));
                        Particles.inward(from, a, i % 2 == 0 ? p.color() : p.color2(), 0.08f + 0.04f * t);
                    }
                }
                case ENTITY_TRAIL, LOCK_RETICLE -> {
                    Entity e = tracked();
                    followO = follow;
                    if (e != null) {
                        follow = p.vfx() == dev.infinitygauntlet.vfx.VfxType.ENTITY_TRAIL ? e.position().add(0, e.getBbHeight() * 0.5, 0) : e.position();
                    }
                    if (p.vfx() == dev.infinitygauntlet.vfx.VfxType.ENTITY_TRAIL) {
                        trail.addFirst(follow);
                        while (trail.size() > 14) {
                            trail.removeLast();
                        }
                        if (Particles.chance(0.7f)) {
                            Particles.mote(follow, Vec3.ZERO, p.color2(), 0.1f, 14);
                        }
                    }
                }
                case GROUND_CRACKS -> {
                    if (age < duration * 0.6f && Particles.chance(0.5f * ClientConfig.quality().density)) {
                        double ang = Math.random() * Math.PI * 2, rr = Math.random() * p.scale();
                        Particles.mote(a.add(Math.cos(ang) * rr, 0.05, Math.sin(ang) * rr), new Vec3(0, 0.05, 0), p.color(), 0.06f, 20);
                    }
                }
                case SMOKE_COLUMN -> {
                    float t = (float) age / duration;
                    if (t < 0.7f) {
                        for (int i = 0; i < Particles.scaled(3); i++) {
                            double ang = Math.random() * Math.PI * 2;
                            double rr = p.scale() * 0.12 * Math.random();
                            Particles.smoke(a.add(Math.cos(ang) * rr, 0.3, Math.sin(ang) * rr), new Vec3(0, 0.12 + 0.1 * Math.random(), 0), 0x1E0A30,
                                    1.6f, 70);
                        }
                        if (Particles.chance(0.6f)) {
                            Particles.spark(a.add(0, Math.random() * p.scale() * 0.5, 0), new Vec3(0, 0.1, 0), p.color2(), 0.1f);
                        }
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
                case GROUND_CRACKS -> cracks(last, buffers, t, time);
                case HELIX_BEAM -> helix(last, buffers, cam, camRot, t, time);
                case POWER_EXPLOSION -> explosion(pose, buffers, cam, camRot, t, time);
                case CHARGE -> charge(last, buffers, cam, camRot, t, time);
                case ENTITY_TRAIL -> trail(last, buffers, cam, camRot, t, time, pt);
                case LOCK_RETICLE -> reticle(pose, buffers, cam, camRot, t, time);
                case DEBRIS -> debris(pose, buffers, t, time);
                case SHARD_NOVA -> shards(last, buffers, cam, camRot, t, time);
                case IMPACT_STAR -> star(last, buffers, cam, camRot, t, time);
                case SMOKE_COLUMN -> column(last, buffers, camRot, t, time);
                default -> {
                }
            }
        }

        /** Jagged fissures race out from the centre, glow hot, then cool to dark scars and fade. */
        private void cracks(PoseStack.Pose last, MultiBufferSource buffers, float t, float time) {
            float grow = smooth(t / 0.18f);
            float glow = 1 - smooth((t - 0.15f) / 0.6f);
            float fade = 1 - smooth((t - 0.75f) / 0.25f);
            int branches = 7 + (int) (hash(seed, 0, 0) * 5) + (int) (p.scale() / 3);
            // two passes (dark scars, then hot cores) so each pass writes to one buffer at a time
            for (int pass = 0; pass < 2; pass++) {
                VertexConsumer vc = buffers.getBuffer(pass == 0 ? ModRenderTypes.translucent(ModRenderTypes.WHITE) : ModRenderTypes.ENERGY_LINES);
                for (int b = 0; b < branches; b++) {
                    double ang = (b + hash(seed, b, 1) * 0.6) * Math.PI * 2 / branches;
                    float len = p.scale() * (0.55f + 0.45f * hash(seed, b, 2));
                    crackBranch(last, vc, pass == 0, Vec3.ZERO, ang, len * grow, 0.22f + p.scale() * 0.012f, b, 0, glow, fade);
                }
            }
        }

        private void crackBranch(PoseStack.Pose last, VertexConsumer vc, boolean scar, Vec3 start, double ang, float len, float width, int id, int depth,
                                 float glow, float fade) {
            int segs = 6;
            Vec3 at = start;
            for (int i = 0; i < segs; i++) {
                double a = ang + (hash(seed, id * 31 + depth, i) - 0.5) * 0.9;
                double step = len / segs;
                Vec3 next = at.add(Math.cos(a) * step, 0.03 + depth * 0.004, Math.sin(a) * step);
                float w = width * (1 - (float) i / segs * 0.7f);
                if (scar) {
                    flat(last, vc, at, next, w * 1.05f, 0.05f, 0.02f, 0.07f, 0.7f * fade, true);
                } else {
                    // hot core cools from the highlight colour to the stone colour; components stay in [0, 1]
                    float heat = glow * glow;
                    flat(last, vc, at, next, w * 0.5f, Mth.lerp(heat, cr, hr), Mth.lerp(heat, cg, hg), Mth.lerp(heat, cb, hb), glow * fade, false);
                }
                if (depth < 1 && hash(seed, id * 7 + i, 33) > 0.62f) {
                    crackBranch(last, vc, scar, next, a + (hash(seed, id, i + 40) > 0.5f ? 0.8 : -0.8), len * 0.4f, w * 0.7f, id * 13 + i, depth + 1, glow, fade);
                }
                at = next;
            }
        }

        /** Flat ribbon on the ground plane. */
        private static void flat(PoseStack.Pose last, VertexConsumer vc, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha, boolean textured) {
            if (alpha <= 0.003f) {
                return;
            }
            Vec3 d = b.subtract(a);
            Vec3 side = new Vec3(-d.z, 0, d.x).normalize().scale(width / 2);
            var m = last.pose();
            if (textured) {
                vc.addVertex(m, (float) (a.x - side.x), (float) a.y, (float) (a.z - side.z)).setUv(0, 0).setColor(r, g, bl, alpha);
                vc.addVertex(m, (float) (a.x + side.x), (float) a.y, (float) (a.z + side.z)).setUv(1, 0).setColor(r, g, bl, alpha);
                vc.addVertex(m, (float) (b.x + side.x), (float) b.y, (float) (b.z + side.z)).setUv(1, 1).setColor(r, g, bl, alpha);
                vc.addVertex(m, (float) (b.x - side.x), (float) b.y, (float) (b.z - side.z)).setUv(0, 1).setColor(r, g, bl, alpha);
            } else {
                vc.addVertex(m, (float) (a.x - side.x), (float) a.y + 0.005f, (float) (a.z - side.z)).setColor(r, g, bl, alpha);
                vc.addVertex(m, (float) (a.x + side.x), (float) a.y + 0.005f, (float) (a.z + side.z)).setColor(r, g, bl, alpha);
                vc.addVertex(m, (float) (b.x + side.x), (float) b.y + 0.005f, (float) (b.z + side.z)).setColor(r, g, bl, alpha);
                vc.addVertex(m, (float) (b.x - side.x), (float) b.y + 0.005f, (float) (b.z - side.z)).setColor(r, g, bl, alpha);
            }
        }

        /** A white-hot core with two strands spiralling around it and pulses running down its length. */
        private void helix(PoseStack.Pose last, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t, float time) {
            Vec3 to = p.b().subtract(p.a());
            double len = to.length();
            if (len < 0.1) {
                return;
            }
            Vec3 dir = to.scale(1 / len);
            Vec3 n1 = VfxDraw.perpendicular(dir);
            Vec3 n2 = dir.cross(n1).normalize();
            float alpha = 1 - smooth((t - 0.6f) / 0.4f);
            float w = p.scale();
            VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
            VfxDraw.beam(last, beam, Vec3.ZERO, to, cam, w * 3.2f, -time * 0.5f, cr, cg, cb, 0.55f * alpha);
            VfxDraw.beam(last, beam, Vec3.ZERO, to, cam, w * 1.3f, -time * 0.8f, 1, 0.9f, 1, alpha);
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            int segs = Math.max(12, (int) (len * 3));
            for (int strand = 0; strand < 2; strand++) {
                Vec3 prev = null;
                for (int i = 0; i <= segs; i++) {
                    double s = len * i / segs;
                    double ang = s * 1.4 - time * 0.9 + strand * Math.PI;
                    float rad = w * (1.4f + 0.3f * Mth.sin((float) s * 0.7f + time));
                    Vec3 pt = dir.scale(s).add(n1.scale(Math.cos(ang) * rad)).add(n2.scale(Math.sin(ang) * rad));
                    if (prev != null) {
                        VfxDraw.line(last, lines, prev, pt, cam, w * 0.32f, strand == 0 ? hr : cr, strand == 0 ? hg : cg, strand == 0 ? hb : cb, 0.9f * alpha);
                    }
                    prev = pt;
                }
            }
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            for (int k = 0; k < 4; k++) {
                float f = (time * 0.09f + k / 4f) % 1f;
                Vec3 at = to.scale(f);
                VfxDraw.billboard(last, glow, camRot, (float) at.x, (float) at.y, (float) at.z, w * 6, 0, hr, hg, hb, 0.6f * alpha);
            }
            // kept small: the beam starts at the caster's hand, right in front of their camera
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, w * 2.5f, 0, 1, 0.85f, 1, 0.6f * alpha);
            VfxDraw.billboard(last, glow, camRot, (float) to.x, (float) to.y, (float) to.z, w * 12, time * 0.3f, cr, cg, cb, alpha);
            VertexConsumer swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
            VfxDraw.billboard(last, swirl, camRot, (float) to.x, (float) to.y, (float) to.z, w * 8, time * 0.4f, hr, hg, hb, 0.8f * alpha);
        }

        /** Blinding flash, a churning fireball of purple and pink layers, a dark smoke shell and a ground ring. */
        private void explosion(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t, float time) {
            PoseStack.Pose last = pose.last();
            float s = p.scale();
            float grow = 1 - (float) Math.pow(1 - Math.min(1, t / 0.35f), 3);
            float fire = 1 - smooth((t - 0.2f) / 0.5f);
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            float flash = 1 - smooth(t / 0.12f);
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, s * 9 * (0.5f + flash), 0, 1, 0.95f, 1, flash);
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, s * 4.5f * grow, 0, cr, cg, cb, 0.9f * fire);
            VertexConsumer core = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CORE));
            for (int i = 0; i < 9; i++) {
                Vec3 off = VfxDraw.unit(seed, i).scale(s * 0.55 * grow * hash(seed, i, 9));
                float size = s * (1.1f + 0.8f * hash(seed, i, 10)) * grow;
                boolean hot = i % 3 == 0;
                VfxDraw.billboard(last, core, camRot, (float) off.x, (float) off.y + s * 0.25f * t, (float) off.z, size, time * 0.05f * (i % 2 == 0 ? 1 : -1) + i,
                        hot ? hr : cr, hot ? hg : cg, hot ? hb : cb, fire * (hot ? 0.9f : 0.75f));
            }
            VfxDraw.sphere(last, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CRACKLE)), cam, s * 1.15f * grow, 12, 20, time * 0.05f, cr, cg, cb,
                    0.8f * fire, 0.15f * fire);
            VertexConsumer smoke = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.SMOKE_ATLAS));
            float smokeA = smooth(t / 0.25f) * (1 - smooth((t - 0.55f) / 0.45f));
            for (int i = 0; i < 12; i++) {
                Vec3 off = VfxDraw.unit(seed, i + 50).scale(s * (0.8 + 0.6 * t) * grow);
                VfxDraw.smokePuff(last, smoke, camRot, (float) off.x, (float) off.y + s * 0.6f * t, (float) off.z, s * (1.3f + t), time * 0.01f + i, i % 4,
                        0.12f, 0.05f, 0.18f, 0.75f * smokeA);
            }
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(time * 0.03f));
            float ringR = s * 2.6f * grow;
            VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING)), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                    ringR * 0.75f, ringR, 48, 0, hr, hg, hb, (1 - t) * 0.8f);
            pose.popPose();
        }

        /** Streaks converging on a swelling, flickering core. */
        private void charge(PoseStack.Pose last, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t, float time) {
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            float r = p.scale();
            for (int i = 0; i < 20; i++) {
                float phase = (time * 0.07f + hash(seed, i, 1)) % 1f;
                Vec3 dir = VfxDraw.unit(seed, i);
                double outer = r * (1 - phase);
                Vec3 a = dir.scale(outer);
                Vec3 b = dir.scale(Math.max(0.05, outer - r * 0.3));
                VfxDraw.line(last, lines, a, b, cam, 0.035f, hr, hg, hb, 0.7f * phase);
            }
            float core = 0.2f + t * r * 0.35f;
            float flicker = 0.85f + 0.15f * Mth.sin(time * 2.3f);
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, core * 6 * flicker, 0, cr, cg, cb, 0.7f);
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, core * 2.2f, 0, 1, 1, 1, 0.9f * flicker);
            VertexConsumer swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
            VfxDraw.billboard(last, swirl, camRot, 0, 0, 0, core * 4, time * (0.1f + t * 0.4f), hr, hg, hb, 0.8f);
        }

        private void trail(PoseStack.Pose last, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t, float time, float pt) {
            if (trail.size() < 2) {
                return;
            }
            Vec3 here = followO.lerp(follow, pt);
            float fadeOut = 1 - smooth((t - 0.7f) / 0.3f);
            VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
            Vec3 prev = Vec3.ZERO;
            int k = 0;
            for (Vec3 pos : trail) {
                Vec3 local = pos.subtract(here);
                if (k > 0) {
                    float f = 1 - k / (float) trail.size();
                    VfxDraw.beam(last, beam, prev, local, cam, p.scale() * f, -time * 0.4f, cr, cg, cb, 0.8f * f * fadeOut);
                    VfxDraw.beam(last, beam, prev, local, cam, p.scale() * 0.4f * f, -time * 0.4f, hr, hg, hb, f * fadeOut);
                }
                prev = local;
                k++;
            }
        }

        /** Brackets that spin in and lock around the target, plus a targeting pillar from the sky. */
        private void reticle(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t, float time) {
            Entity e = tracked();
            float h = e == null ? 1.8f : e.getBbHeight();
            float lock = smooth(t / 0.35f);
            float alpha = smooth(t / 0.1f) * (1 - smooth((t - 0.85f) / 0.15f)) * (0.75f + 0.25f * Mth.sin(time * 1.5f));
            pose.pushPose();
            pose.translate(0, h * 0.5f, 0);
            Vector3f right = camRot.transform(new Vector3f(1, 0, 0));
            Vector3f up = camRot.transform(new Vector3f(0, 1, 0));
            Vec3 ax = new Vec3(right.x, right.y, right.z);
            Vec3 ay = new Vec3(up.x, up.y, up.z);
            float size = p.scale() * h * (2.6f - 1.6f * lock);
            float spin = (1 - lock) * 6 + time * 0.03f;
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            Vec3 camAt = cam.subtract(0, h * 0.5f, 0);
            for (int k = 0; k < 4; k++) {
                double a0 = spin + k * Math.PI / 2;
                Vec3 corner = ax.scale(Math.cos(a0) * size).add(ay.scale(Math.sin(a0) * size));
                Vec3 arm1 = ax.scale(Math.cos(a0 + 0.35) * size).add(ay.scale(Math.sin(a0 + 0.35) * size));
                Vec3 arm2 = ax.scale(Math.cos(a0 - 0.35) * size).add(ay.scale(Math.sin(a0 - 0.35) * size));
                VfxDraw.line(pose.last(), lines, corner, arm1, camAt, 0.08f, cr, cg, cb, alpha);
                VfxDraw.line(pose.last(), lines, corner, arm2, camAt, 0.08f, cr, cg, cb, alpha);
            }
            VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING)), ax, ay, size * 0.55f, size * 0.68f, 40, time * 0.05f,
                    hr, hg, hb, alpha * 0.7f);
            VfxDraw.beam(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM)), new Vec3(0, -h * 0.5f, 0), new Vec3(0, 40, 0), camAt,
                    0.25f, -time * 0.2f, cr, cg, cb, alpha * 0.45f * lock);
            pose.popPose();
        }

        /** Real block chunks thrown up on deterministic arcs; they land, settle and sink away. */
        private void debris(PoseStack pose, MultiBufferSource buffers, float t, float time) {
            BlockState state = Block.stateById(p.color());
            if (state.isAir() || Minecraft.getInstance().level == null) {
                return;
            }
            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            int light = LevelRenderer.getLightColor(Minecraft.getInstance().level, BlockPos.containing(p.a()).above());
            int n = p.color2();
            float sink = smooth((t - 0.8f) / 0.2f);
            for (int i = 0; i < n; i++) {
                double ang = hash(seed, i, 1) * Math.PI * 2;
                double out = (0.12 + 0.25 * hash(seed, i, 2)) * p.scale();
                double up = (0.35 + 0.45 * hash(seed, i, 3)) * p.scale();
                float delay = hash(seed, i, 4) * 3;
                float tt = Math.max(0, time - delay);
                double x = Math.cos(ang) * out * tt * 1.4;
                double z = Math.sin(ang) * out * tt * 1.4;
                double y = up * tt - 0.045 * tt * tt;
                boolean landed = y < 0 && tt > 2;
                if (landed) {
                    double land = up / 0.045;
                    x = Math.cos(ang) * out * land * 1.4;
                    z = Math.sin(ang) * out * land * 1.4;
                    y = -0.1;
                }
                float size = (0.2f + 0.35f * hash(seed, i, 5)) * (1 - sink);
                if (size <= 0.01f) {
                    continue;
                }
                pose.pushPose();
                pose.translate(x, y + size * 0.5 - sink * 0.3, z);
                float rot = landed ? (float) (up / 0.045) * 14 * hash(seed, i, 6) : tt * 14 * hash(seed, i, 6);
                pose.mulPose(Axis.of(new Vector3f((float) VfxDraw.unit(seed, i).x, (float) VfxDraw.unit(seed, i).y, (float) VfxDraw.unit(seed, i).z))
                        .rotationDegrees(rot + i * 40));
                pose.scale(size, size, size);
                pose.translate(-0.5, -0.5, -0.5);
                dispatcher.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }

        /** Crystal shards bursting outward, slowing, spinning and winking out. */
        private void shards(PoseStack.Pose last, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t, float time) {
            float travel = 1 - (1 - t) * (1 - t) * (1 - t);
            float alpha = 1 - smooth((t - 0.5f) / 0.5f);
            for (int pass = 0; pass < 2; pass++) {
                VertexConsumer vc = buffers.getBuffer(pass == 0 ? ModRenderTypes.additive(ModRenderTypes.BEAM) : ModRenderTypes.ENERGY_LINES);
                for (int i = 0; i < 28; i++) {
                    Vec3 dir = VfxDraw.unit(seed, i);
                    Vec3 tumble = VfxDraw.unit(seed, i + 100);
                    Vec3 at = dir.scale(p.scale() * travel * (0.6 + 0.4 * hash(seed, i, 2)));
                    float len = 0.4f + 0.6f * hash(seed, i, 3);
                    Vec3 axis = VfxDraw.rotate(dir, tumble, time * 0.3 * hash(seed, i, 4)).scale(len);
                    boolean bright = i % 3 == 0;
                    if (pass == 0) {
                        VfxDraw.beam(last, vc, at.subtract(axis), at.add(axis), cam, 0.22f, 0, bright ? 1 : hr, bright ? 1 : hg, bright ? 1 : hb, alpha);
                    } else {
                        VfxDraw.line(last, vc, at.subtract(axis.scale(0.8)), at.add(axis.scale(0.8)), cam, 0.04f, 1, 1, 1, alpha);
                    }
                }
            }
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, p.scale() * 1.5f * (1 - t), 0, cr, cg, cb, 1 - t);
        }

        /** The impact frame of a punch: inverted flash disc, a white star of spikes and speed lines along the blow. */
        private void star(PoseStack.Pose last, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t, float time) {
            Vec3 d = p.b().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.b().normalize();
            float s = p.scale();
            float pop = smooth(t / 0.2f);
            float alpha = 1 - smooth((t - 0.3f) / 0.7f);
            VertexConsumer dark = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.GLOW));
            VfxDraw.billboard(last, dark, camRot, 0, 0, 0, s * 2.4f * pop, 0, 0.05f, 0, 0.1f, 0.7f * alpha);
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            Vector3f right = camRot.transform(new Vector3f(1, 0, 0));
            Vector3f up = camRot.transform(new Vector3f(0, 1, 0));
            Vec3 ax = new Vec3(right.x, right.y, right.z);
            Vec3 ay = new Vec3(up.x, up.y, up.z);
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI * 2 / 12 + hash(seed, i, 1) * 0.3;
                float len = s * (0.6f + 0.9f * hash(seed, i, 2)) * pop;
                Vec3 tip = ax.scale(Math.cos(a) * len).add(ay.scale(Math.sin(a) * len));
                VfxDraw.line(last, lines, Vec3.ZERO, tip, cam, 0.16f * s * (1 - t), 1, 1, 1, alpha);
                VfxDraw.line(last, lines, Vec3.ZERO, tip.scale(1.2), cam, 0.35f * s * (1 - t), cr, cg, cb, alpha * 0.5f);
            }
            Vec3 n1 = VfxDraw.perpendicular(d);
            Vec3 n2 = d.cross(n1).normalize();
            for (int i = 0; i < 16; i++) {
                double a = hash(seed, i, 5) * Math.PI * 2;
                double r = s * (0.4 + 0.8 * hash(seed, i, 6));
                Vec3 base = n1.scale(Math.cos(a) * r).add(n2.scale(Math.sin(a) * r));
                float shift = (time * 0.6f + hash(seed, i, 7)) % 1f;
                Vec3 from = base.add(d.scale(-s * 1.5 + shift * s * 3));
                VfxDraw.line(last, lines, from, from.add(d.scale(s * 1.2)), cam, 0.05f, hr, hg, hb, alpha * 0.8f);
            }
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, s * 1.6f * (1 - t * 0.5f), 0, 1, 1, 1, alpha);
        }

        /** A rising stem of churning smoke that flattens into a cap, lit purple from below. */
        private void column(PoseStack.Pose last, MultiBufferSource buffers, Quaternionf camRot, float t, float time) {
            float h = p.scale();
            float rise = 1 - (1 - Math.min(1, t / 0.45f)) * (1 - Math.min(1, t / 0.45f));
            float alpha = smooth(t / 0.08f) * (1 - smooth((t - 0.6f) / 0.4f));
            VertexConsumer smoke = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.SMOKE_ATLAS));
            int stem = 10;
            for (int i = 0; i < stem; i++) {
                float f = (float) i / stem;
                float y = h * rise * f;
                float w = h * (0.12f + 0.05f * Mth.sin(f * 9 + time * 0.05f));
                float glow = 1 - f;
                VfxDraw.smokePuff(last, smoke, camRot, (float) (Math.sin(f * 7 + time * 0.03) * w * 0.3), y, 0, w * 2.4f, time * 0.008f + i, i % 4,
                        0.1f + 0.25f * glow * cr, 0.04f + 0.1f * glow * cg, 0.14f + 0.25f * glow * cb, 0.8f * alpha);
            }
            for (int i = 0; i < 9; i++) {
                double a = i * Math.PI * 2 / 9 + time * 0.004;
                float capR = h * 0.35f * rise * (1 + 0.3f * t);
                VfxDraw.smokePuff(last, smoke, camRot, (float) (Math.cos(a) * capR), h * rise + (float) Math.sin(a * 2) * h * 0.04f, (float) (Math.sin(a) * capR),
                        h * 0.4f, time * 0.01f + i, i % 4, 0.14f, 0.06f, 0.2f, 0.8f * alpha);
            }
            VertexConsumer glowVc = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(last, glowVc, camRot, 0, h * 0.08f, 0, h * 0.8f, 0, hr, hg, hb, 0.6f * alpha * (1 - t));
        }
    }

    private PowerVfx() {
    }
}
