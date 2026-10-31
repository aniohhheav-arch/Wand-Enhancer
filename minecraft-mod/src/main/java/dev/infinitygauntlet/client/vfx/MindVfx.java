package dev.infinitygauntlet.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.time.Afterimages;
import dev.infinitygauntlet.network.VfxPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static dev.infinitygauntlet.client.vfx.VfxDraw.hash;
import static dev.infinitygauntlet.client.vfx.VfxDraw.smooth;

/**
 * The Mind Stone's visual language: golden geometry and neural filaments. Beams are threaded with branching
 * dendrites, links ripple like nerves, controlled creatures wear a rune halo and burning eyes, marks are eye sigils,
 * bursts throw out spinning polygons, and shields ripple where they are struck.
 */
public final class MindVfx {
    private static final int MAX = 160;
    private static final List<Effect> EFFECTS = new ArrayList<>();

    public static boolean handles(VfxPayload p) {
        return switch (p.vfx()) {
            case NEURAL_BEAM, NEURAL_LINK, MIND_AURA, MIND_MARK, GEOMETRY_BURST, SHIELD_RIPPLE, DAZE, ASTRAL_GHOST -> true;
            default -> false;
        };
    }

    public static void spawn(VfxPayload p) {
        if (EFFECTS.size() >= MAX) {
            EFFECTS.removeFirst();
        }
        EFFECTS.add(new Effect(p));
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
            Vec3 o = e.origin(pt);
            if (o == null) {
                continue;
            }
            pose.pushPose();
            pose.translate(o.x - cam.x, o.y - cam.y, o.z - cam.z);
            e.render(pose, buffers, cam.subtract(o), camera.rotation(), pt);
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** One polygon outline (and its inner star) in the plane of ax/ay. */
    static void polygon(PoseStack.Pose last, VertexConsumer lines, Vec3 at, Vec3 ax, Vec3 ay, int sides, float r, Vec3 cam, float width, float alpha) {
        Vec3 prev = null;
        for (int k = 0; k <= sides; k++) {
            double ang = k * Math.PI * 2 / sides;
            Vec3 p = at.add(ax.scale(Math.cos(ang) * r)).add(ay.scale(Math.sin(ang) * r));
            if (prev != null) {
                VfxDraw.line(last, lines, prev, p, cam, width, 1f, 0.85f, 0.3f, alpha);
            }
            prev = p;
        }
        if (sides >= 5) {
            for (int k = 0; k < sides; k++) {
                double a0 = k * Math.PI * 2 / sides, a1 = (k + 2) * Math.PI * 2 / sides;
                VfxDraw.line(last, lines, at.add(ax.scale(Math.cos(a0) * r)).add(ay.scale(Math.sin(a0) * r)),
                        at.add(ax.scale(Math.cos(a1) * r)).add(ay.scale(Math.sin(a1) * r)), cam, width * 0.5f, 1f, 0.95f, 0.6f, alpha * 0.7f);
            }
        }
    }

    private static final class Effect {
        final VfxPayload p;
        final int duration;
        final long seed;
        final float cr, cg, cb, hr, hg, hb;
        int age;

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
        }

        float progress(float pt) {
            return Mth.clamp((age + pt) / duration, 0, 1);
        }

        Entity entity(int id) {
            return Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
        }

        /** Where the effect is drawn from this frame; null hides it (followed entity gone). */
        Vec3 origin(float pt) {
            return switch (p.vfx()) {
                case NEURAL_LINK -> {
                    Entity from = entity((int) p.b().x);
                    yield from == null ? null : hand(from, pt);
                }
                case MIND_AURA, MIND_MARK, DAZE, ASTRAL_GHOST -> {
                    Entity e = entity((int) seed);
                    yield e == null ? null : e.getPosition(pt);
                }
                default -> p.a();
            };
        }

        static Vec3 hand(Entity e, float pt) {
            if (!(e instanceof net.minecraft.world.entity.player.Player)) {
                return e.getPosition(pt).add(0, e.getBbHeight() * 0.75, 0);
            }
            Vec3 look = e.getViewVector(pt);
            Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(0.38);
            return e.getEyePosition(pt).add(look.scale(0.55)).add(side).add(0, -0.4, 0);
        }

        void tick() {
            switch (p.vfx()) {
                case MIND_MARK -> {
                    Entity e = entity((int) seed);
                    if (e != null) {
                        float t = (float) age / duration;
                        Vec3 c = e.getBoundingBox().getCenter();
                        for (int i = 0; i < Particles.scaled(2 + 5 * t); i++) {
                            Vec3 from = c.add(VfxDraw.unit(seed + age, i).scale(1.8 + e.getBbHeight() * 0.5));
                            Particles.inward(from, c, i % 2 == 0 ? p.color() : p.color2(), 0.08f);
                        }
                    }
                }
                case MIND_AURA -> {
                    Entity e = entity((int) seed);
                    if (e != null && Particles.chance(0.3f)) {
                        Particles.mote(e.getEyePosition().add((Math.random() - 0.5) * 0.6, 0.3, (Math.random() - 0.5) * 0.6), new Vec3(0, 0.02, 0),
                                p.color(), 0.05f, 20);
                    }
                }
                case ASTRAL_GHOST -> {
                    Entity e = entity((int) seed);
                    if (e != null && Particles.chance(0.6f)) {
                        Particles.mote(e.position().add((Math.random() - 0.5) * 0.8, Math.random() * 1.8, (Math.random() - 0.5) * 0.8), Vec3.ZERO, p.color(),
                                0.06f, 25);
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
                case NEURAL_BEAM -> beam(last, buffers, p.b().subtract(p.a()), cam, camRot, t, time, p.scale(), true);
                case NEURAL_LINK -> {
                    Entity target = entity((int) seed);
                    Entity from = entity((int) p.b().x);
                    if (target != null && from != null) {
                        Vec3 to = target.getBoundingBox().getCenter().add(target.getPosition(pt).subtract(target.position())).subtract(hand(from, pt));
                        beam(last, buffers, to, cam, camRot, t, time, 0.1f * p.scale(), false);
                    }
                }
                case MIND_AURA -> aura(pose, buffers, cam, camRot, t, time, pt);
                case MIND_MARK -> mark(pose, buffers, cam, camRot, t, time);
                case GEOMETRY_BURST -> burst(last, buffers, cam, t, time);
                case SHIELD_RIPPLE -> ripple(last, buffers, cam, camRot, t);
                case DAZE -> daze(last, buffers, camRot, t, time);
                case ASTRAL_GHOST -> ghost(pose, buffers, pt, t);
                default -> {
                }
            }
        }

        /** A golden core with neural filaments winding along it, sprouting short dendrites, with pulses running down. */
        private void beam(PoseStack.Pose last, MultiBufferSource buffers, Vec3 to, Vec3 cam, Quaternionf camRot, float t, float time, float w, boolean flare) {
            double len = to.length();
            if (len < 0.1) {
                return;
            }
            Vec3 dir = to.scale(1 / len);
            Vec3 n1 = VfxDraw.perpendicular(dir);
            Vec3 n2 = dir.cross(n1).normalize();
            float alpha = flare ? 1 - smooth((t - 0.5f) / 0.5f) : smooth(t / 0.2f) * (1 - smooth((t - 0.7f) / 0.3f));
            VfxDraw.beam(last, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM)), Vec3.ZERO, to, cam, w * 2.6f, -time * 0.4f, cr, cg, cb,
                    0.55f * alpha);
            VfxDraw.beam(last, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM)), Vec3.ZERO, to, cam, w, -time * 0.6f, 1, 0.97f, 0.85f, alpha);
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            int segs = Math.max(10, (int) (len * 3));
            for (int strand = 0; strand < 4; strand++) {
                Vec3 prev = null;
                for (int i = 0; i <= segs; i++) {
                    double s = len * i / segs;
                    double ph = strand * 1.7 + s * 2.1 + time * 0.25;
                    double rad = w * (1.6 + 0.6 * Math.sin(s * 3 + strand));
                    Vec3 pt = dir.scale(s).add(n1.scale(Math.cos(ph) * rad)).add(n2.scale(Math.sin(ph * 0.8) * rad));
                    if (prev != null) {
                        VfxDraw.line(last, lines, prev, pt, cam, w * 0.22f, hr, hg, hb, 0.85f * alpha);
                        if (hash(seed, strand * 97 + i, 3) > 0.86f) {
                            Vec3 tip = pt.add(n1.scale((hash(seed, i, 5) - 0.5) * w * 6)).add(n2.scale((hash(seed, i, 6) - 0.5) * w * 6));
                            VfxDraw.line(last, lines, pt, tip, cam, w * 0.12f, hr, hg, hb, 0.6f * alpha);
                        }
                    }
                    prev = pt;
                }
            }
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            for (int k = 0; k < 3; k++) {
                float f = (time * 0.07f + k / 3f) % 1f;
                Vec3 at = to.scale(f);
                VfxDraw.billboard(last, glow, camRot, (float) at.x, (float) at.y, (float) at.z, w * 5, 0, 1, 0.9f, 0.5f, 0.7f * alpha);
            }
            if (flare) {
                VfxDraw.billboard(last, glow, camRot, (float) to.x, (float) to.y, (float) to.z, w * 14 * (0.6f + t), 0, cr, cg, cb, alpha);
                Vector3f rx = camRot.transform(new Vector3f(1, 0, 0)), ry = camRot.transform(new Vector3f(0, 1, 0));
                PoseStack ps = new PoseStack();
                ps.last().pose().set(last.pose());
                ps.translate(to.x, to.y, to.z);
                VfxDraw.ring(ps.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_MIND)), new Vec3(rx.x, rx.y, rx.z),
                        new Vec3(ry.x, ry.y, ry.z), 0, 1.4f + 1.6f * t, 32, time * 0.02f, cr, cg, cb, 0.8f * alpha);
            }
        }

        private void aura(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t, float time, float pt) {
            Entity e = entity((int) seed);
            if (!(e instanceof LivingEntity living)) {
                return;
            }
            float alpha = smooth(time / 10f) * (1 - smooth((t - 0.95f) / 0.05f));
            float eye = e.getEyeHeight();
            // a halo of runes turning over the head
            pose.pushPose();
            pose.translate(0, e.getBbHeight() + 0.35, 0);
            pose.mulPose(Axis.YP.rotation(time * 0.06f));
            VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_MIND)), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.15f,
                    0.55f + e.getBbWidth() * 0.4f, 24, 0, cr, cg, cb, 0.8f * alpha);
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                Vec3 at = new Vec3(Math.cos(a) * 0.55, Math.sin(time * 0.1 + i) * 0.05, Math.sin(a) * 0.55);
                polygon(pose.last(), lines, at, new Vec3(Math.cos(a), 0, Math.sin(a)), new Vec3(0, 1, 0), 3, 0.08f, cam.subtract(0, e.getBbHeight() + 0.35, 0),
                        0.02f, alpha);
            }
            pose.popPose();
            // burning yellow eyes, on the front of the face
            float headYaw = Mth.lerp(pt, living.yHeadRotO, living.yHeadRot) * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(headYaw), 0, Mth.cos(headYaw));
            Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
            float spread = Math.max(0.08f, e.getBbWidth() * 0.22f);
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            for (int s = -1; s <= 1; s += 2) {
                Vec3 at = fwd.scale(e.getBbWidth() * 0.5 + 0.02).add(side.scale(spread * s)).add(0, eye + 0.02, 0);
                VfxDraw.billboard(pose.last(), glow, camRot, (float) at.x, (float) at.y, (float) at.z, 0.32f, 0, 1f, 0.85f, 0.2f, alpha);
                VfxDraw.billboard(pose.last(), glow, camRot, (float) at.x, (float) at.y, (float) at.z, 0.12f, 0, 1f, 1f, 0.8f, alpha);
            }
        }

        private void mark(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t, float time) {
            Entity e = entity((int) seed);
            if (e == null) {
                return;
            }
            float h = e.getBbHeight();
            float alpha = smooth(t / 0.15f);
            Vector3f rx = camRot.transform(new Vector3f(1, 0, 0)), ry = camRot.transform(new Vector3f(0, 1, 0));
            Vec3 ax = new Vec3(rx.x, rx.y, rx.z), ay = new Vec3(ry.x, ry.y, ry.z);
            pose.pushPose();
            pose.translate(0, h + 0.8, 0);
            float size = (0.7f + 0.3f * t) * p.scale();
            VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_MIND)), ax, ay, 0, size, 32, time * 0.03f, cr, cg, cb, alpha);
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            Vec3 c = cam.subtract(0, h + 0.8, 0);
            // the eye: an almond outline with a burning pupil
            Vec3 prev = null;
            for (int k = 0; k <= 24; k++) {
                double a = k * Math.PI * 2 / 24;
                Vec3 pnt = ax.scale(Math.cos(a) * size * 0.6).add(ay.scale(Math.sin(a) * Math.abs(Math.sin(a)) * size * 0.3));
                if (prev != null) {
                    VfxDraw.line(pose.last(), lines, prev, pnt, c, 0.035f, 1, 0.9f, 0.4f, alpha);
                }
                prev = pnt;
            }
            pose.popPose();
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(pose.last(), glow, camRot, 0, h + 0.8f, 0, size * (0.4f + 0.4f * t), 0, 1, 0.9f, 0.3f, alpha);
            // spirals winding into the body, tightening as it charges
            VertexConsumer spiral = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            for (int arm = 0; arm < 3; arm++) {
                Vec3 last = null;
                for (int k = 0; k < 20; k++) {
                    float f = k / 19f;
                    double r = (1.8 - 1.4 * t) * (1 - f) + 0.1;
                    double a = arm * Math.PI * 2 / 3 + f * 6 + time * 0.2;
                    Vec3 pnt = new Vec3(Math.cos(a) * r, h * (0.2 + 0.6 * f), Math.sin(a) * r);
                    if (last != null) {
                        VfxDraw.line(pose.last(), spiral, last, pnt, cam, 0.04f, hr, hg, hb, alpha * f);
                    }
                    last = pnt;
                }
            }
        }

        /** Polygons of every kind thrown outward, turning as they go. */
        private void burst(PoseStack.Pose last, MultiBufferSource buffers, Vec3 cam, float t, float time) {
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            float travel = 1 - (1 - t) * (1 - t);
            float alpha = 1 - smooth((t - 0.45f) / 0.55f);
            for (int i = 0; i < 14; i++) {
                Vec3 dir = VfxDraw.unit(seed, i);
                Vec3 at = dir.scale(p.scale() * travel * (0.5 + 0.5 * hash(seed, i, 2)));
                Vec3 normal = VfxDraw.unit(seed, i + 40);
                Vec3 ax = VfxDraw.rotate(VfxDraw.perpendicular(normal), normal, time * 0.15 * (i % 2 == 0 ? 1 : -1));
                Vec3 ay = normal.cross(ax).normalize();
                polygon(last, lines, at, ax, ay, 3 + i % 4, 0.15f + 0.2f * hash(seed, i, 3) * Math.max(1, p.scale() * 0.3f), cam, 0.03f, alpha);
            }
        }

        /** Rings racing across the dome's surface from the point of impact. */
        private void ripple(PoseStack.Pose last, MultiBufferSource buffers, Vec3 cam, Quaternionf camRot, float t) {
            Vec3 centre = p.b().subtract(p.a());
            Vec3 n = p.a().subtract(p.b()).normalize();
            Vec3 u = VfxDraw.perpendicular(n), v = n.cross(u).normalize();
            float r = p.scale();
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            for (int ring = 0; ring < 2; ring++) {
                float tt = Mth.clamp(t - ring * 0.2f, 0, 1);
                double theta = tt * 0.9;
                float alpha = (1 - tt) * (tt > 0 ? 1 : 0);
                Vec3 prev = null;
                for (int k = 0; k <= 32; k++) {
                    double phi = k * Math.PI * 2 / 32;
                    Vec3 onSphere = n.scale(Math.cos(theta)).add(u.scale(Math.sin(theta) * Math.cos(phi))).add(v.scale(Math.sin(theta) * Math.sin(phi))).scale(r);
                    Vec3 pnt = centre.add(onSphere);
                    if (prev != null) {
                        VfxDraw.line(last, lines, prev, pnt, cam, 0.06f, hr, hg, hb, alpha);
                    }
                    prev = pnt;
                }
            }
            VfxDraw.billboard(last, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW)), camRot, 0, 0, 0, 1.4f * (1 - t) + 0.3f, 0, cr, cg, cb, 1 - t);
        }

        private void daze(PoseStack.Pose last, MultiBufferSource buffers, Quaternionf camRot, float t, float time) {
            Entity e = entity((int) seed);
            if (e == null) {
                return;
            }
            float alpha = smooth(time / 6f) * (1 - smooth((t - 0.85f) / 0.15f));
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            float r = 0.35f + e.getBbWidth() * 0.3f;
            for (int i = 0; i < 3; i++) {
                double a = time * 0.25 + i * Math.PI * 2 / 3;
                VfxDraw.billboard(last, glow, camRot, (float) (Math.cos(a) * r), e.getBbHeight() + 0.25f + 0.05f * Mth.sin(time * 0.5f + i), (float) (Math.sin(a) * r),
                        0.22f, (float) a, 1f, 0.9f, 0.35f, alpha);
            }
        }

        private void ghost(PoseStack pose, MultiBufferSource buffers, float pt, float t) {
            Entity e = entity((int) seed);
            Minecraft mc = Minecraft.getInstance();
            if (!(e instanceof LivingEntity living) || e == mc.player) {
                return;
            }
            float alpha = Math.min(1f, (age + pt) / 10f) * (1 - smooth((t - 0.95f) / 0.05f));
            Afterimages.draw(pose, buffers, living, 0, 0, 0, Mth.lerp(pt, living.yBodyRotO, living.yBodyRot), Mth.lerp(pt, living.yHeadRotO, living.yHeadRot),
                    living.getXRot(), 0, 0, living.tickCount + pt, 0.55f * alpha, p.color(), 1f);
        }
    }

    private MindVfx() {
    }
}
