package com.infinitemultiverse.client.cinematic;

import static com.infinitemultiverse.client.cinematic.FxDraw.argb;
import static com.infinitemultiverse.client.cinematic.GenericScenes.UP;

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

/** Superhero scenes: lightning from the sky, ice, telekinetic holds, speed-force dashes, the Unibeam... */
final class HeroScenes {
    private HeroScenes() {
    }

    static void register(Map<ResourceLocation, Function<ScenePayload, Scene>> m) {
        m.put(SceneIds.CHAIN_LIGHTNING, p -> new GenericScenes.Beam(p, GenericScenes.BeamStyle.LIGHTNING));
        m.put(SceneIds.THUNDER, Thunder::new);
        m.put(SceneIds.ICE_SPIKES, p -> new GenericScenes.Wave(p, GenericScenes.WaveStyle.FROST));
        m.put(SceneIds.ICE_PATH, IcePath::new);
        m.put(SceneIds.ICE_SHARD, IceShard::new);
        m.put(SceneIds.GRIP, p -> new Grip(p, false));
        m.put(SceneIds.MYSTIC_BANDS, p -> new Grip(p, true));
        m.put(SceneIds.MIND_SCAN, MindScan::new);
        m.put(SceneIds.DASH, Dash::new);
        m.put(SceneIds.UNIBEAM, Unibeam::new);
        m.put(SceneIds.GRAPPLE, Grapple::new);
        m.put(SceneIds.MAGNET_PULL, MagnetPull::new);
    }

    /** A god-bolt: the sky darkens into a churning cloud ring, then a branching column slams down. */
    static final class Thunder extends Scene {
        Thunder(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            Vec3 target = origin.add(dir);
            Vec3 sky = target.add(0, 22, 0);
            float gather = Mth.clamp(a / 6, 0, 1);
            d.ink();
            d.disc(sky, UP, 0, 7 * gather, argb(0x101420, 0.85f), argb(0x101420, 0), 32);
            d.glow();
            d.ring(sky, UP, 5 * gather, 0.4, 0x9FD8FF, 0.4f * gather, 40, a * 0.1, Math.PI * 2);
            if (a >= 6) {
                float f = 1 - Mth.clamp((a - 6) / (duration - 6), 0, 1);
                d.bolt(sky, target, seed + age / 2, 3.5, 0.45, color, f, 3);
                d.bolt(sky, target, seed * 3 + age / 2, 2.5, 0.25, 0xFFFFFF, f, 2);
                d.ring(target.add(0, 0.1, 0), UP, 1 + (a - 6) * 0.8, 0.3, color, f);
                d.flare(target, 5 * f, 0xFFFFFF, f);
            }
        }

        @Override
        public float flash(float pt) {
            return age >= 6 && age < 9 ? 0.6f * falloff(origin.add(dir), 40) : 0;
        }

        @Override
        public float shake(float pt) {
            return age >= 6 && age < 14 ? 2.5f * falloff(origin.add(dir), 30) : 0;
        }
    }

    /** Frost mist and crystal growth under the caster while Ice Path is active. */
    static final class IcePath extends Scene {
        IcePath(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            Entity e = entity();
            if (e != null) {
                for (int i = 0; i < s.count(4); i++) {
                    s.mote(e.position().add(s.jitter(1.2).multiply(1, 0, 1)), new Vec3(0, 0.02, 0), 0xFFFFFF, 0xA8E8FF, 0.35f, 22, 0.95f);
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            Vec3 at = e.getPosition(pt);
            float f = envelope(pt, 3, 3);
            d.ring(at.add(0, 0.02, 0), UP, 1.4, 0.2, 0xA8E8FF, f);
            for (int i = 0; i < 6; i++) {
                double ang = Math.PI * i / 3 + e.tickCount * 0.05;
                GenericScenes.spike(d, at.add(Math.cos(ang) * 1.3, -0.1, Math.sin(ang) * 1.3), 0.4, 0.1, ang, 0xA8E8FF, f);
            }
        }
    }

    /** Spinning ice crystal riding on a projectile, with a frost trail. */
    static final class IceShard extends Scene {
        IceShard(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            Entity e = entity();
            if (e != null) {
                s.mote(e.position(), Vec3.ZERO, 0xFFFFFF, 0xA8E8FF, 0.2f, 12, 0.9f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null || !e.isAlive()) {
                return;
            }
            Vec3 at = e.getPosition(pt);
            Vec3 vel = e.getDeltaMovement();
            Vec3 n = vel.lengthSqr() > 1.0E-4 ? vel.normalize() : UP;
            Vec3 u = FxDraw.perp(n).scale(0.18), w = n.cross(FxDraw.perp(n)).scale(0.18);
            Vec3 tip = at.add(n.scale(0.5)), tail = at.subtract(n.scale(0.4));
            double rot = (e.tickCount + pt) * 0.6;
            for (int i = 0; i < 4; i++) {
                Vec3 side = u.scale(Math.cos(rot + i * Math.PI / 2)).add(w.scale(Math.sin(rot + i * Math.PI / 2)));
                d.tri(tip, at.add(side), tail, argb(0xFFFFFF, 0.95f), argb(0xA8E8FF, 0.8f), argb(0x6AB0E0, 0.6f));
            }
            d.ribbon(tail, tail.subtract(n.scale(1.5)), 0.25, argb(0xA8E8FF, 0.6f), argb(0xA8E8FF, 0));
            d.flare(at, 0.5, 0xA8E8FF, 0.6f);
        }
    }

    /** Telekinetic / mystic hold on a creature (entity = target, param = caster id): orbiting bands lock around it. */
    static final class Grip extends Scene {
        private final boolean mystic;

        Grip(ScenePayload p, boolean mystic) {
            super(p);
            this.mystic = mystic;
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null || !e.isAlive()) {
                return;
            }
            float f = envelope(pt, 4, 4);
            Vec3 c = e.getPosition(pt).add(0, e.getBbHeight() * 0.5, 0);
            double r = Math.max(e.getBbWidth(), e.getBbHeight()) * 0.7 + 0.3;
            double time = time(pt);
            for (int i = 0; i < 3; i++) {
                Vec3 axis = new Vec3(Math.sin(time * 0.07 + i * 2.1), Math.cos(i * 1.3), Math.cos(time * 0.05 + i)).normalize();
                d.ring(c, axis, r, 0.06, color, f);
                if (mystic) {
                    Vec3 u = FxDraw.perp(axis), w = axis.cross(u).normalize();
                    for (int k = 0; k < 16; k++) {
                        double ang = time * 0.1 + Math.PI * 2 * k / 16;
                        d.line(FxDraw.onCircle(c, u, w, r * 0.92, ang), FxDraw.onCircle(c, u, w, r * 1.08, ang + 0.05), axis, 0.03, 0xFFE0A0, f);
                    }
                }
            }
            d.sphere(c, r * 0.95, color, 0.0f, 0.35f * f, 10, 16);
            Entity caster = null;
            if (p.dir().lengthSqr() > 0 && param > 0) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                caster = mc.level == null ? null : mc.level.getEntity((int) param);
            }
            if (caster != null) {
                Vec3 hand = CursedScenes.hand(caster, pt, 0);
                d.helix(hand, c, 0.15, 3, 2, time * 0.5, 0.05, color, 0.7f * f);
            }
        }
    }

    /** A scanning eye glyph above the target and sweep lines across its body. */
    static final class MindScan extends Scene {
        MindScan(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            Vec3 c = e != null ? e.getPosition(pt) : origin;
            float f = envelope(pt, 3, 6);
            double h = e != null ? e.getBbHeight() : 2;
            Vec3 n = d.camera().subtract(c).normalize();
            Vec3 eye = c.add(0, h + 0.8, 0);
            d.ring(eye, n, 0.45, 0.04, color, f);
            d.flare(eye, 0.35, 0xFFFFFF, f);
            d.star(eye, n, 0.7, 0.5, 6, time(pt) * 0.05, 0.03, color, f);
            double y = (time(pt) * 0.08 % 1.0) * h;
            d.ring(c.add(0, y, 0), UP, 0.6, 0.04, color, f);
        }
    }

    /** Speed-force dash: a chain of fading afterimages and lightning along the path. */
    static final class Dash extends Scene {
        Dash(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = 1 - t(pt);
            Vec3 end = origin.add(dir);
            for (int i = 0; i < 8; i++) {
                double s = i / 7.0;
                Vec3 c = origin.lerp(end, s);
                float a = (float) (f * (0.2 + 0.6 * s));
                d.ribbon(c, c.add(0, 1.8, 0), 0.55, argb(color, a * 0.5f), argb(color, a * 0.2f));
                d.flare(c.add(0, 1.6, 0), 0.25, 0xFFFFFF, a);
            }
            d.bolt(origin.add(0, 1, 0), end.add(0, 1, 0), seed + age, 0.8, 0.08, color, f, 1);
            d.bolt(origin.add(0, 0.4, 0), end.add(0, 0.4, 0), seed * 5 + age, 0.6, 0.05, 0xFFFFFF, f, 0);
        }
    }

    /** Unibeam (Mark Armor): the chest reactor spins up in close-up, then a colossal beam with a corona of rings. */
    static final class Unibeam extends Scene {
        static final int FIRE = 15;

        Unibeam(ScenePayload p) {
            super(p);
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        private Vec3 chest(float pt) {
            Entity e = entity();
            Vec3 base = e != null ? e.getPosition(pt) : origin.subtract(0, 1.62, 0);
            return base.add(0, 1.3, 0).add(dir.normalize().scale(0.35));
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            float a = time(pt);
            Vec3 ch = chest(pt);
            if (a < FIRE) {
                return CinematicCamera.Shot.looking(ch.add(dir.normalize().scale(1.6 - a * 0.04)).add(FxDraw.perp(dir.normalize()).scale(0.4)), ch);
            }
            return CinematicCamera.Shot.looking(ch.subtract(dir.normalize().scale(2.5)).add(FxDraw.perp(dir.normalize()).scale(-1.2)).add(0, 0.7, 0), ch.add(dir.scale(0.5)));
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            Vec3 ch = chest(pt);
            Vec3 n = dir.normalize();
            if (a < FIRE + 2) {
                float g = Mth.clamp(a / FIRE, 0, 1);
                Vec3 tri0 = ch.add(n.scale(0.02));
                d.ring(tri0, n, 0.18, 0.03, 0xFFD040, 1);
                d.polygon(tri0, n, 0.14, 3, a * 0.4, 0.04, 0xC8F8FF, 1);
                d.flare(tri0, 0.2 + g * 0.8, 0xC8F8FF, g);
                for (int i = 0; i < 3; i++) {
                    d.ring(tri0.add(n.scale(0.1 + i * 0.25 * g)), n, 0.3 + i * 0.25 * (1 - g), 0.03, 0xC8F8FF, g);
                }
            }
            if (a >= FIRE) {
                float k = Mth.clamp((a - FIRE) / 3, 0, 1);
                float f = 1 - Mth.clamp((a - FIRE - 10) / (duration - FIRE - 10f), 0, 1);
                Vec3 end = ch.add(dir.scale(k));
                d.beam(ch, end, 1.0, 0xC8F8FF, f);
                for (int i = 0; i < 8; i++) {
                    double s = ((a * 0.07 + i / 8.0) % 1.0) * k;
                    d.ring(ch.add(dir.scale(s)), n, 1.8 - s * 0.8, 0.12, 0xFFFFFF, f * 0.7f);
                }
                d.flare(ch, 2.5, 0xFFFFFF, f);
            }
        }

        @Override
        public float shake(float pt) {
            return age >= FIRE && age < FIRE + 15 ? 1.4f * falloff(origin, 24) : 0;
        }
    }

    /** Grapple line from the caster's hand to the anchor while they are reeled in. */
    static final class Grapple extends Scene {
        Grapple(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            float f = envelope(pt, 1, 4);
            Vec3 hand = CursedScenes.hand(e, pt, -1);
            Vec3 anchor = origin.add(dir);
            float shoot = Mth.clamp(time(pt) / 3, 0, 1);
            Vec3 head = hand.lerp(anchor, shoot);
            d.ribbon(hand, head, 0.05, argb(0x303030, f), argb(0x505050, f));
            d.ribbon(hand, head, 0.15, argb(0xFFD040, 0.15f * f), argb(0xFFD040, 0.1f * f));
            d.star(head, d.camera().subtract(head).normalize(), 0.3, 0.3, 3, 0, 0.05, 0xE0E0E0, f);
            if (shoot >= 1) {
                d.flare(anchor, 0.6 * f, 0xFFFFFF, f);
            }
        }
    }

    /** Magnetic field lines bending from the pulled targets into the caster. */
    static final class MagnetPull extends Scene {
        MagnetPull(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 2, 6);
            RandomSource r = RandomSource.create(seed);
            Vec3 c = origin.add(0, 1, 0);
            for (int i = 0; i < 14; i++) {
                Vec3 out = c.add(new Vec3(r.nextDouble() - 0.5, (r.nextDouble() - 0.5) * 0.5, r.nextDouble() - 0.5).normalize().scale(param));
                Vec3 mid = c.lerp(out, 0.5).add(0, param * 0.25, 0);
                Vec3 prev = out;
                double flow = (time(pt) * 0.08) % 1.0;
                for (int k = 1; k <= 14; k++) {
                    double s = k / 14.0;
                    Vec3 q = out.scale((1 - s) * (1 - s)).add(mid.scale(2 * s * (1 - s))).add(c.scale(s * s));
                    float pulse = (float) Math.max(0, 1 - Math.abs(s - flow) * 5);
                    d.ribbon(prev, q, 0.06 + pulse * 0.1, argb(color, f * (0.4f + pulse)), argb(0xC0C8FF, f * (0.4f + pulse)));
                    prev = q;
                }
            }
        }
    }
}
