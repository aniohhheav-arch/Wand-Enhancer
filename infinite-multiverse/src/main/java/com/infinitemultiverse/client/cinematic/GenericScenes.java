package com.infinitemultiverse.client.cinematic;

import static com.infinitemultiverse.client.cinematic.FxDraw.argb;
import static com.infinitemultiverse.client.cinematic.FxDraw.lerp;

import com.infinitemultiverse.client.vfx.VfxSpawner;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.network.ScenePayload;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Reusable scene families: beams, area waves, auras, wards and impacts, each with several looks. */
final class GenericScenes {
    private GenericScenes() {
    }

    static final Vec3 UP = new Vec3(0, 1, 0);

    static void register(Map<ResourceLocation, Function<ScenePayload, Scene>> m) {
        m.put(SceneIds.BEAM, p -> new Beam(p, BeamStyle.PLAIN));
        m.put(SceneIds.BEAM_HELIX, p -> new Beam(p, BeamStyle.HELIX));
        m.put(SceneIds.BEAM_LIGHTNING, p -> new Beam(p, BeamStyle.LIGHTNING));
        m.put(SceneIds.BEAM_TWIN, p -> new Beam(p, BeamStyle.TWIN));
        m.put(SceneIds.BEAM_WHIP, p -> new Beam(p, BeamStyle.WHIP));
        m.put(SceneIds.BEAM_ROPE, p -> new Beam(p, BeamStyle.ROPE));
        m.put(SceneIds.BEAM_SONIC, p -> new Beam(p, BeamStyle.SONIC));
        m.put(SceneIds.BEAM_FIRE, p -> new Beam(p, BeamStyle.FIRE));
        m.put(SceneIds.BEAM_PSYCHIC, p -> new Beam(p, BeamStyle.PSYCHIC));
        for (WaveStyle style : WaveStyle.values()) {
            m.put(style.id, p -> new Wave(p, style));
        }
        for (AuraStyle style : AuraStyle.values()) {
            m.put(style.id, p -> new Aura(p, style));
        }
        for (WardStyle style : WardStyle.values()) {
            m.put(style.id, p -> new Ward(p, style));
        }
        m.put(SceneIds.WARD_HIT, Ripple::new);
        m.put(SceneIds.IMPACT, p -> new Impact(p, false));
        m.put(SceneIds.IMPACT_SLASH, p -> new Impact(p, true));
    }

    // =================================================================== beams

    enum BeamStyle { PLAIN, HELIX, LIGHTNING, TWIN, WHIP, ROPE, SONIC, FIRE, PSYCHIC }

    /** A beam that shoots out over ~3 ticks, holds, and thins away; muzzle flare, impact flare and style detail. */
    static final class Beam extends Scene {
        private final BeamStyle style;
        private final double width;

        Beam(ScenePayload p, BeamStyle style) {
            super(p);
            this.style = style;
            this.width = Math.max(0.15, param);
        }

        private Vec3 end() {
            return origin.add(dir);
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age < 3) {
                Vec3 end = end();
                for (int i = 0; i < s.count(14); i++) {
                    s.spark(end, s.randomUnit().scale(0.25 + s.rand() * 0.3), 0xFFFFFF, color, 0.18f, 10 + (int) (s.rand() * 8), 0.88f);
                }
                if (style == BeamStyle.FIRE) {
                    for (int i = 0; i < s.count(16); i++) {
                        s.mote(origin.add(dir.scale(s.rand())), new Vec3(0, 0.05 + s.rand() * 0.05, 0), 0xFFE080, color, 0.25f, 18, 0.9f);
                    }
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float grow = Mth.clamp((age + pt) / 3f, 0, 1);
            float fade = 1 - Mth.clamp((age + pt - (duration - 6)) / 6f, 0, 1);
            if (fade <= 0) {
                return;
            }
            Vec3 a = origin;
            Vec3 b = origin.add(dir.scale(grow));
            double w = width * (0.6 + 0.4 * fade);
            Vec3 n = dir.normalize();
            double time = age + pt;
            switch (style) {
                case LIGHTNING -> {
                    d.bolt(a, b, seed + age, Math.min(3, dir.length() * 0.12), w * 0.35, color, fade, 2);
                    d.bolt(a, b, seed * 7 + age, Math.min(2, dir.length() * 0.08), w * 0.2, color, fade * 0.7f, 1);
                }
                case TWIN -> {
                    Vec3 side = FxDraw.perp(n).scale(0.18);
                    d.beam(a.add(side), b.add(side), w * 0.5, color, fade);
                    d.beam(a.subtract(side), b.subtract(side), w * 0.5, color, fade);
                }
                case WHIP -> {
                    Vec3 u = FxDraw.perp(n), v = n.cross(u);
                    Vec3 prev = a;
                    int steps = 36;
                    for (int i = 1; i <= steps; i++) {
                        double t = i / (double) steps * grow;
                        double wave = Math.sin(t * 14 - time * 1.6) * (1 - t) * 0.9;
                        Vec3 pnt = a.add(dir.scale(t)).add(u.scale(wave)).add(v.scale(Math.cos(t * 9 - time) * 0.3 * (1 - t)));
                        d.ribbon(prev, pnt, w * (1.2 - t), argb(0xFFF0C0, fade), argb(color, fade));
                        d.ribbon(prev, pnt, w * 3.5 * (1.2 - t), argb(color, fade * 0.3f), argb(color, fade * 0.2f));
                        if (i % 4 == 0) {
                            d.flare(pnt, w * 1.6, color, fade * 0.6f);
                        }
                        prev = pnt;
                    }
                }
                case ROPE -> {
                    d.helix(a, b, w * 0.4, dir.length() * 1.4, 2, time * 0.4, w * 0.35, color, fade);
                    d.ribbon(a, b, w * 0.5, argb(0xFFFFE0, fade), argb(0xFFFFE0, fade));
                    d.ribbon(a, b, w * 2.5, argb(color, fade * 0.25f), argb(color, fade * 0.25f));
                }
                case PSYCHIC -> {
                    d.beam(a, b, w * 0.6, color, fade);
                    for (int i = 0; i < 5; i++) {
                        double t = ((time * 0.25 + i / 5.0) % 1.0) * grow;
                        d.ring(a.add(dir.scale(t)), n, w * (1.2 + t * 2), w * 0.2, color, fade * (float) (1 - t));
                    }
                }
                default -> {
                    d.beam(a, b, w, color, fade);
                    if (style == BeamStyle.HELIX || style == BeamStyle.FIRE) {
                        d.helix(a, b, w * 1.4, dir.length() * 0.35, 3, time * 0.6, w * 0.3, lerp(color, 0xFFFFFF, 0.4f), fade * 0.8f);
                    }
                    if (style == BeamStyle.SONIC || style == BeamStyle.PLAIN) {
                        int rings = style == BeamStyle.SONIC ? 6 : 3;
                        for (int i = 0; i < rings; i++) {
                            double t = (i + 0.5) / rings * grow;
                            d.ring(a.add(dir.scale(t)), n, w * (2.5 - t * 1.5), w * 0.25, color, fade * 0.7f * (float) (1 - t * 0.5));
                        }
                    }
                }
            }
            // muzzle and impact
            d.flare(a, w * 4, color, fade * 0.8f);
            d.ring(a, n, w * 2.2 + (time * 0.4), w * 0.3, color, fade * 0.6f);
            if (grow >= 1) {
                d.flare(b, w * 6 * fade, lerp(color, 0xFFFFFF, 0.5f), fade);
                d.ring(b, d.camera().subtract(b).normalize(), w * 2 + (age + pt) * 0.5, w * 0.4, color, fade);
            }
        }

        @Override
        public float shake(float pt) {
            return age < 4 ? (float) (width * 1.2) * falloff(origin, 12 + width * 8) : 0;
        }
    }

    // =================================================================== waves

    enum WaveStyle {
        PLAIN(SceneIds.WAVE), CONE(SceneIds.WAVE_CONE), FIRE(SceneIds.WAVE_FIRE), FROST(SceneIds.WAVE_FROST), MAGNET(SceneIds.WAVE_MAGNET),
        SMOKE(SceneIds.WAVE_SMOKE), MIND(SceneIds.WAVE_MIND), VOICE(SceneIds.WAVE_VOICE), BLOOD(SceneIds.WAVE_BLOOD), LANDING(SceneIds.WAVE_LANDING);

        final ResourceLocation id;

        WaveStyle(ResourceLocation id) {
            this.id = id;
        }
    }

    /** Expanding ground rings, a light dome and style detail (flames, ice spikes, field lines, smoke, sound rings...). */
    static final class Wave extends Scene {
        private final WaveStyle style;
        private final double radius;

        Wave(ScenePayload p, WaveStyle style) {
            super(p);
            this.style = style;
            this.radius = Math.max(2, param);
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age == 0) {
                for (int i = 0; i < s.count(60); i++) {
                    Vec3 v = s.randomUnit();
                    if (style == WaveStyle.CONE && v.dot(dir.normalize()) < 0.4) {
                        continue;
                    }
                    int c = style == WaveStyle.SMOKE ? 0x303030 : color;
                    s.mote(origin.add(0, 0.6, 0), new Vec3(v.x, Math.abs(v.y) * 0.4, v.z).scale(radius * 0.06), 0xFFFFFF, c, 0.3f, 16 + (int) (s.rand() * 10), 0.86f);
                }
            }
            if (style == WaveStyle.SMOKE && age < 30) {
                for (int i = 0; i < s.count(10); i++) {
                    s.mote(origin.add(s.jitter(radius * 0.6)).add(0, s.rand() * 2, 0), new Vec3(0, 0.01, 0), 0x505050, 0x202020, 0.9f, 40, 0.95f);
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float t = t(pt);
            float ease = 1 - (1 - t) * (1 - t) * (1 - t);
            float fade = 1 - t;
            double r = radius * ease;
            Vec3 c = origin.add(0, 0.08, 0);
            Vec3 look = new Vec3(dir.x, 0, dir.z).normalize();
            boolean cone = style == WaveStyle.CONE || (p.flags() & com.infinitemultiverse.core.cinematic.Cinematics.CONE) != 0;
            double sweep = cone ? Math.PI * 0.8 : Math.PI * 2;
            double a0 = cone ? Math.atan2(look.z, look.x) - sweep / 2 : 0;
            // ground rings — flat in XZ: normal UP, the ring helper uses perp(UP)=(−z..) basis; compute angles in that basis
            Vec3 u = FxDraw.perp(UP), w = UP.cross(u).normalize();
            if (cone) {
                a0 = Math.atan2(look.dot(w), look.dot(u)) - sweep / 2;
            }
            switch (style) {
                case SMOKE -> {
                    d.ink();
                    for (int i = 0; i < 7; i++) {
                        RandomSource rs = RandomSource.create(seed + i);
                        Vec3 pnt = c.add((rs.nextDouble() - 0.5) * radius, 0.5 + rs.nextDouble() * 1.5, (rs.nextDouble() - 0.5) * radius);
                        d.sphere(pnt, radius * 0.35 * ease + 0.5, 0x2A2A2E, 0.55f * fade, 0.0f, 6, 10);
                    }
                    d.glow();
                }
                case FROST -> {
                    RandomSource rs = RandomSource.create(seed);
                    for (int i = 0; i < 18; i++) {
                        double ang = rs.nextDouble() * Math.PI * 2, rr = radius * (0.3 + rs.nextDouble() * 0.7);
                        double h = (0.8 + rs.nextDouble() * 1.6) * Mth.clamp(t * 4 - rr / radius, 0, 1);
                        if (h > 0.01) {
                            spike(d, c.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr), h, 0.25 + rs.nextDouble() * 0.2, rs.nextDouble() * 3, color, Math.min(1, fade * 2));
                        }
                    }
                }
                case FIRE -> {
                    int n = 28;
                    for (int i = 0; i < n; i++) {
                        double ang = a0 + sweep * i / n;
                        Vec3 base = FxDraw.onCircle(c, u, w, r, ang);
                        d.flame(base, 1.6 * fade + 0.3, 0.9, age * 0.5 + i, 0xFFF2B0, color, fade);
                    }
                }
                case MAGNET -> {
                    for (int i = 0; i < 10; i++) {
                        double ang = Math.PI * 2 * i / 10 + (age + pt) * 0.05;
                        Vec3 out = FxDraw.onCircle(c, u, w, r, ang);
                        Vec3 mid = c.add(out.subtract(c).scale(0.5)).add(0, r * 0.5, 0);
                        Vec3 prev = c.add(0, 1, 0);
                        for (int k = 1; k <= 12; k++) {
                            double s = k / 12.0;
                            Vec3 q = c.add(0, 1, 0).scale((1 - s) * (1 - s)).add(mid.scale(2 * s * (1 - s))).add(out.scale(s * s));
                            d.ribbon(prev, q, 0.08, argb(color, fade), argb(0xC0C8FF, fade));
                            prev = q;
                        }
                    }
                }
                case MIND, VOICE -> {
                    for (int i = 0; i < 4; i++) {
                        double rr = (radius * ((t * 1.5 + i * 0.22) % 1.0));
                        d.ring(origin.add(0, 1.2, 0), UP, rr, 0.18, color, fade * (float) (1 - rr / radius));
                        if (style == WaveStyle.VOICE) {
                            d.ring(origin.add(0, 1.5, 0), look, rr * 0.5, 0.1, 0xFFFFFF, fade * 0.6f * (float) (1 - rr / radius));
                        }
                    }
                    if (style == WaveStyle.MIND) {
                        d.star(origin.add(0, 2.4, 0), d.camera().subtract(origin).normalize(), 0.8, 0.45, 6, (age + pt) * 0.1, 0.06, color, fade);
                    }
                }
                case BLOOD -> {
                    for (int i = 0; i < 8; i++) {
                        double ang = Math.PI * 2 * i / 8 + t * 2;
                        d.orb(FxDraw.onCircle(origin.add(0, 1.2, 0), u, w, r * 0.8, ang), 0.35 + 0.2 * (1 - t), 0xFF8080, color, fade);
                    }
                }
                default -> {
                }
            }
            d.glow();
            d.ring(c, UP, r, 0.35 + radius * 0.02, color, fade, 48, a0, sweep);
            d.ring(c, UP, r * 0.7, 0.2, lerp(color, 0xFFFFFF, 0.5f), fade * 0.7f, 40, a0, sweep);
            if (!cone && style != WaveStyle.SMOKE) {
                d.sphere(origin.add(0, 0.5, 0), r * 0.6, color, 0.02f * fade, 0.35f * fade, 10, 20);
                d.flare(origin.add(0, 1, 0), radius * 0.6 * (1 - t), lerp(color, 0xFFFFFF, 0.6f), fade);
            }
            if (style == WaveStyle.LANDING) {
                for (int i = 0; i < 12; i++) {
                    double ang = Math.PI * 2 * i / 12;
                    d.line(c, FxDraw.onCircle(c, u, w, r * 1.1, ang + Math.sin(i) * 0.2), UP, 0.25, 0x302018, fade);
                }
            }
        }

        @Override
        public float shake(float pt) {
            return age < 6 ? (float) (radius * 0.15) * falloff(origin, radius * 2.5) : 0;
        }

        @Override
        public float flash(float pt) {
            return style == WaveStyle.PLAIN && radius > 8 && age < 2 ? 0.25f * falloff(origin, radius) : 0;
        }
    }

    /** Hexagonal ice spike. */
    static void spike(FxDraw d, Vec3 base, double h, double r, double rot, int color, float alpha) {
        Vec3 tip = base.add(Math.sin(rot) * h * 0.2, h, Math.cos(rot) * h * 0.2);
        for (int i = 0; i < 6; i++) {
            double a0 = rot + Math.PI * i / 3, a1 = rot + Math.PI * (i + 1) / 3;
            Vec3 b0 = base.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), b1 = base.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            int side = argb(lerp(color, 0xFFFFFF, (i % 2) * 0.4f), alpha * 0.8f);
            d.quad(b0, b1, tip, tip, side, side, argb(0xFFFFFF, alpha), argb(0xFFFFFF, alpha));
        }
        d.flare(tip, r * 1.5, 0xFFFFFF, alpha * 0.6f);
    }

    // =================================================================== auras

    enum AuraStyle {
        ENERGY(SceneIds.AURA), ELECTRIC(SceneIds.AURA_ELECTRIC), FIRE(SceneIds.AURA_FIRE), HEAL(SceneIds.AURA_HEAL), BLOOD(SceneIds.AURA_BLOOD),
        SPEED(SceneIds.AURA_SPEED), FLIGHT(SceneIds.AURA_FLIGHT), THRUSTERS(SceneIds.AURA_THRUSTERS), CLOAK(SceneIds.AURA_CLOAK),
        GLIDE(SceneIds.AURA_GLIDE), STRENGTH(SceneIds.AURA_STRENGTH);

        final ResourceLocation id;

        AuraStyle(ResourceLocation id) {
            this.id = id;
        }
    }

    /** Body-hugging aura that follows its entity: licking energy flames, arcs, healing spirals, thruster jets... */
    static final class Aura extends Scene {
        private final AuraStyle style;

        Aura(ScenePayload p, AuraStyle style) {
            super(p);
            this.style = style;
        }

        @Override
        public void tick(VfxSpawner s) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            Vec3 at = e.position();
            switch (style) {
                case SPEED -> {
                    for (int i = 0; i < s.count(4); i++) {
                        s.spark(at.add(s.jitter(0.4)).add(0, 0.2 + s.rand() * 1.6, 0), e.getDeltaMovement().scale(-0.2), 0xFFFFFF, color, 0.12f, 6, 0.8f);
                    }
                }
                case THRUSTERS -> {
                    for (int i = 0; i < s.count(5); i++) {
                        s.mote(at.add(s.jitter(0.15)), new Vec3(0, -0.25 - s.rand() * 0.2, 0), 0xFFFFFF, color, 0.22f, 8, 0.9f);
                    }
                }
                default -> {
                    if (s.rand() < 0.6f) {
                        s.mote(at.add(s.jitter(0.5)).add(0, s.rand() * 1.8, 0), new Vec3(0, 0.04, 0), 0xFFFFFF, color, 0.12f, 14, 0.95f);
                    }
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            float fade = envelope(pt, 4, 4) * (style == AuraStyle.SPEED ? 0.8f : 0.6f);
            Vec3 at = e.getPosition(pt);
            double time = (e.tickCount + pt);
            double h = e.getBbHeight();
            switch (style) {
                case ELECTRIC -> {
                    for (int i = 0; i < 3; i++) {
                        double a = time * 0.7 + i * 2.1;
                        Vec3 p0 = at.add(Math.cos(a) * 0.5, h * (0.2 + 0.3 * i), Math.sin(a) * 0.5);
                        Vec3 p1 = at.add(Math.cos(a + 2) * 0.6, h * (0.5 + 0.2 * i), Math.sin(a + 2) * 0.6);
                        d.bolt(p0, p1, seed + e.tickCount * 13L + i, 0.5, 0.04, color, fade * 1.3f, 1);
                    }
                    d.ring(at.add(0, 0.05, 0), UP, 0.9, 0.12, color, fade);
                }
                case HEAL -> {
                    d.helix(at, at.add(0, h + 0.4, 0), 0.7, 1.5, 3, time * 0.15, 0.08, color, fade);
                    d.ring(at.add(0, 0.05, 0), UP, 1.0 + Math.sin(time * 0.2) * 0.1, 0.15, color, fade);
                    for (int i = 0; i < 3; i++) {
                        Vec3 c = at.add(Math.cos(time * 0.1 + i * 2) * 0.9, (time * 0.05 + i * 0.33) % 1.0 * h + 0.2, Math.sin(time * 0.1 + i * 2) * 0.9);
                        d.line(c.add(-0.15, 0, 0), c.add(0.15, 0, 0), d.camera().subtract(c).normalize(), 0.07, 0xFFFFFF, fade);
                        d.line(c.add(0, -0.15, 0), c.add(0, 0.15, 0), d.camera().subtract(c).normalize(), 0.07, 0xFFFFFF, fade);
                    }
                }
                case BLOOD -> {
                    for (int i = 0; i < 3; i++) {
                        d.ring(at.add(0, h * (0.25 + 0.3 * i), 0), UP, 0.45 + 0.05 * Math.sin(time * 0.4 + i), 0.05, color, fade * 1.4f);
                    }
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI * i / 3 + time * 0.05;
                        d.flame(at.add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4), h * 0.9, 0.25, time * 0.4 + i, 0xFF4050, color, fade);
                    }
                }
                case SPEED -> {
                    Vec3 vel = e.getDeltaMovement();
                    Vec3 back = vel.lengthSqr() > 1.0E-3 ? vel.normalize().scale(-2.5) : new Vec3(0, 0, 0);
                    for (int i = 0; i < 5; i++) {
                        Vec3 a = at.add(0, h * (0.15 + i * 0.18), 0);
                        d.bolt(a, a.add(back).add(0, Math.sin(time + i) * 0.2, 0), seed + e.tickCount + i * 31L, 0.3, 0.05, color, fade, 0);
                    }
                }
                case THRUSTERS -> {
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3 foot = at.add(FxDraw.perp(e.getLookAngle().multiply(1, 0, 1).normalize()).scale(0.15 * s));
                        d.beam(foot, foot.add(0, -0.9 - Math.sin(time * 2 + s) * 0.15, 0), 0.12, color, 1f);
                        d.flare(foot, 0.5, 0xFFFFFF, 0.9f);
                    }
                    Vec3 chest = at.add(0, h * 0.7, 0).add(e.getLookAngle().multiply(1, 0, 1).normalize().scale(0.3));
                    d.flare(chest, 0.35, 0x9FF0FF, 0.9f);
                }
                case FLIGHT, CLOAK, GLIDE -> {
                    Vec3 look = e.getLookAngle().multiply(1, 0, 1).normalize();
                    Vec3 back = at.add(0, h * 0.85, 0).subtract(look.scale(0.25));
                    Vec3 side = FxDraw.perp(look);
                    int cape = style == AuraStyle.FLIGHT ? 0xC01020 : style == AuraStyle.CLOAK ? 0xB0101A : 0x15151C;
                    double spread = style == AuraStyle.GLIDE ? 1.4 : 0.45;
                    d.ink();
                    int segs = 6;
                    for (int i = 0; i < segs; i++) {
                        double t0 = i / (double) segs, t1 = (i + 1) / (double) segs;
                        Vec3 l0 = back.add(side.scale(-0.3 - spread * t0)).add(0, -t0 * 1.3, 0).subtract(look.scale(t0 * 0.6 + Math.sin(time * 0.4 + t0 * 4) * 0.15 * t0));
                        Vec3 r0 = back.add(side.scale(0.3 + spread * t0)).add(0, -t0 * 1.3, 0).subtract(look.scale(t0 * 0.6 + Math.sin(time * 0.4 + t0 * 4 + 1) * 0.15 * t0));
                        Vec3 l1 = back.add(side.scale(-0.3 - spread * t1)).add(0, -t1 * 1.3, 0).subtract(look.scale(t1 * 0.6 + Math.sin(time * 0.4 + t1 * 4) * 0.15 * t1));
                        Vec3 r1 = back.add(side.scale(0.3 + spread * t1)).add(0, -t1 * 1.3, 0).subtract(look.scale(t1 * 0.6 + Math.sin(time * 0.4 + t1 * 4 + 1) * 0.15 * t1));
                        d.quad(l0, r0, r1, l1, argb(cape, 0.95f));
                    }
                    d.glow();
                    if (style == AuraStyle.CLOAK) {
                        d.ring(back.subtract(0, 1.3, 0).subtract(look.scale(0.6)), look, 0.5, 0.04, 0xFFD040, 0.8f);
                    }
                    if (style == AuraStyle.FLIGHT) {
                        d.ring(at.subtract(0, 0.2, 0), UP, 0.6 + Math.sin(time * 0.3) * 0.1, 0.08, color, fade);
                    }
                }
                case STRENGTH -> {
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * i / 4 + time * 0.03;
                        d.flame(at.add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5), h * 1.1, 0.4, time * 0.3 + i, 0xFFFFFF, color, fade);
                    }
                    d.ring(at.add(0, 0.05, 0), UP, 1.1, 0.25, color, fade);
                }
                default -> {
                    for (int i = 0; i < 10; i++) {
                        double a = Math.PI * i / 5 + time * 0.04;
                        d.flame(at.add(Math.cos(a) * 0.45, 0, Math.sin(a) * 0.45), h * (0.9 + 0.3 * Math.sin(time * 0.3 + i)), 0.35, time * 0.35 + i,
                                lerp(color, 0xFFFFFF, 0.6f), color, fade);
                    }
                    if (style == AuraStyle.FIRE) {
                        d.flare(at.add(0, h * 0.5, 0), 1.4, color, fade * 0.5f);
                    }
                    d.ring(at.add(0, 0.05, 0), UP, 0.85, 0.15, color, fade);
                }
            }
        }
    }

    // =================================================================== wards

    enum WardStyle {
        INFINITY(SceneIds.WARD_INFINITY), KINETIC(SceneIds.WARD_KINETIC), MAGNET(SceneIds.WARD_MAGNET), SERAPHIM(SceneIds.WARD_SERAPHIM), BRACER(SceneIds.WARD_BRACER);

        final ResourceLocation id;

        WardStyle(ResourceLocation id) {
            this.id = id;
        }
    }

    /** Barriers that follow the caster. Seraphim draws a rotating rune mandala in front of you. */
    static final class Ward extends Scene {
        private final WardStyle style;

        Ward(ScenePayload p, WardStyle style) {
            super(p);
            this.style = style;
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            float fade = envelope(pt, 5, 5);
            Vec3 c = e.getPosition(pt).add(0, e.getBbHeight() * 0.55, 0);
            double r = Math.max(1.2, param);
            double time = e.tickCount + pt;
            Vec3 look = e.getViewVector(pt);
            switch (style) {
                case INFINITY -> {
                    // A near-invisible lens: faint rim shimmer and slow concentric distortion bands.
                    d.sphere(c, r, color, 0.0f, 0.12f * fade, 14, 24);
                    for (int i = 0; i < 3; i++) {
                        double rr = r * (0.3 + ((time * 0.01 + i / 3.0) % 1.0) * 0.7);
                        d.ring(c, look, rr, 0.03, 0xFFFFFF, 0.15f * fade);
                    }
                }
                case KINETIC -> {
                    d.sphere(c, r, color, 0.04f * fade, 0.4f * fade, 10, 6);
                    for (int i = 0; i < 6; i++) {
                        d.polygon(c, new Vec3(Math.cos(i), Math.sin(i * 1.3), Math.sin(i)).normalize(), r, 6, time * 0.02, 0.05, color, 0.4f * fade);
                    }
                }
                case MAGNET -> {
                    for (int i = 0; i < 3; i++) {
                        Vec3 axis = new Vec3(Math.sin(time * 0.05 + i * 2), 0.6, Math.cos(time * 0.05 + i * 2)).normalize();
                        d.ring(c, axis, r, 0.07, i == 1 ? 0xC0C8FF : color, 0.7f * fade);
                    }
                }
                case SERAPHIM, BRACER -> {
                    Vec3 front = c.add(look.scale(1.4));
                    double s = style == WardStyle.BRACER ? 0.8 : 1.3;
                    double rot = time * 0.03;
                    d.disc(front, look, 0, s, argb(color, 0.18f * fade), argb(color, 0.05f * fade), 32);
                    d.ring(front, look, s, 0.05, color, fade);
                    d.ring(front, look, s * 0.82, 0.03, 0xFFF0C0, fade);
                    if (style == WardStyle.SERAPHIM) {
                        d.ring(front, look, s * 1.12, 0.02, color, fade * 0.7f);
                        d.star(front, look, s * 0.78, 0.55, 8, rot, 0.035, color, fade);
                        d.polygon(front, look, s * 0.55, 4, -rot * 1.6, 0.035, 0xFFF0C0, fade);
                        d.polygon(front, look, s * 0.55, 4, -rot * 1.6 + Math.PI / 4, 0.035, 0xFFF0C0, fade);
                        d.ring(front, look, s * 0.3, 0.03, color, fade);
                        // rune ticks around the rim
                        Vec3 u = FxDraw.perp(look), w = look.cross(u).normalize();
                        for (int i = 0; i < 36; i++) {
                            double a = rot * 2 + Math.PI * 2 * i / 36;
                            Vec3 p0 = FxDraw.onCircle(front, u, w, s * 0.86, a), p1 = FxDraw.onCircle(front, u, w, s * (i % 3 == 0 ? 1.06 : 0.95), a);
                            d.line(p0, p1, look, 0.025, color, fade);
                        }
                    } else {
                        d.star(front, look, s * 0.7, 0.3, 5, rot, 0.04, 0xFFFFFF, fade);
                    }
                }
            }
        }
    }

    /** Ripple where a ward stops something. */
    static final class Ripple extends Scene {
        Ripple(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float t = t(pt);
            Vec3 n = dir.normalize();
            for (int i = 0; i < 3; i++) {
                double tt = Mth.clamp(t * 1.4 - i * 0.15, 0, 1);
                d.ring(origin, n, 0.2 + tt * 1.4, 0.06, color, (float) (1 - tt));
            }
            d.flare(origin, 0.8 * (1 - t), 0xFFFFFF, 1 - t);
        }
    }

    /** Hit spark: star flare, ring and (for slashes) crossing blade lines. */
    static final class Impact extends Scene {
        private final boolean slash;

        Impact(ScenePayload p, boolean slash) {
            super(p);
            this.slash = slash;
        }

        @Override
        public void render(FxDraw d, float pt) {
            float t = t(pt), f = 1 - t;
            Vec3 n = d.camera().subtract(origin).normalize();
            double s = Math.max(0.6, param);
            d.flare(origin, s * 1.5 * f, color, f);
            d.star(origin, n, s * (0.6 + t), 0.2, 4, seed % 7, 0.06, 0xFFFFFF, f);
            d.ring(origin, n, s * (0.3 + t * 1.2), 0.06, color, f);
            if (slash) {
                RandomSource r = RandomSource.create(seed);
                for (int i = 0; i < 3; i++) {
                    Vec3 v = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize().scale(s * 1.6);
                    d.ribbon(origin.subtract(v), origin.add(v), 0.08 * f, argb(0xFFFFFF, f), argb(color, f));
                }
            }
        }
    }
}
