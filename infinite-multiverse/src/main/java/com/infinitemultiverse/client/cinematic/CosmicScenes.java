package com.infinitemultiverse.client.cinematic;

import static com.infinitemultiverse.client.cinematic.FxDraw.argb;
import static com.infinitemultiverse.client.cinematic.FxDraw.lerp;
import static com.infinitemultiverse.client.cinematic.GenericScenes.UP;

import com.infinitemultiverse.client.vfx.VfxSpawner;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.network.ScenePayload;
import com.infinitemultiverse.cosmic.gauntlet.InfinityStone;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Phase 3 scenes: the Infinity Stones and the Snap, portals, rifts, launches and time manipulation. */
final class CosmicScenes {
    private CosmicScenes() {
    }

    static void register(Map<ResourceLocation, Function<ScenePayload, Scene>> m) {
        m.put(SceneIds.STONE_SET, StoneSet::new);
        m.put(SceneIds.SPACE_WARP, SpaceWarp::new);
        m.put(SceneIds.TESSERACT, Tesseract::new);
        m.put(SceneIds.MIND_THRALL, MindThrall::new);
        m.put(SceneIds.PSIONIC_STORM, p -> new GenericScenes.Wave(p, GenericScenes.WaveStyle.MIND));
        m.put(SceneIds.REALITY_WARP, RealityWarp::new);
        m.put(SceneIds.REALITY_SHATTER, RealityShatter::new);
        m.put(SceneIds.TIME_REWIND, TimeRewind::new);
        m.put(SceneIds.TIME_FREEZE, TimeDome::new);
        m.put(SceneIds.TIME_FIELD, TimeDome::new);
        m.put(SceneIds.STASIS, Stasis::new);
        m.put(SceneIds.SOUL_DRAIN, SoulDrain::new);
        m.put(SceneIds.SOUL_HARVEST, p -> new GenericScenes.Wave(p, GenericScenes.WaveStyle.FIRE));
        m.put(SceneIds.SNAP, Snap::new);
        m.put(SceneIds.DUST, Dust::new);
        m.put(SceneIds.PORTAL_SHOT, PortalShot::new);
        m.put(SceneIds.PORTAL_TRANSIT, Burst::new);
        m.put(SceneIds.RIFT_OPEN, RiftOpen::new);
        m.put(SceneIds.RIFT_TRANSIT, Burst::new);
        m.put(SceneIds.LAUNCH, Launch::new);
        m.put(SceneIds.REENTRY, Reentry::new);
    }

    static Vec3 fist(Entity e, float pt) {
        return CursedScenes.hand(e, pt, -1);
    }

    /** A gem with facets: an octahedron with a hot core. */
    static void gem(FxDraw d, Vec3 c, double r, int color, float a, double rot) {
        Vec3[] eq = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            double ang = rot + Math.PI / 2 * i;
            eq[i] = c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
        }
        Vec3 top = c.add(0, r * 1.3, 0), bottom = c.subtract(0, r * 1.3, 0);
        for (int i = 0; i < 4; i++) {
            Vec3 p0 = eq[i], p1 = eq[(i + 1) % 4];
            int light = argb(lerp(color, 0xFFFFFF, 0.5f), a), dark = argb(color, a * 0.8f);
            d.tri(top, p0, p1, light, dark, dark);
            d.tri(bottom, p0, p1, argb(color, a * 0.6f), dark, dark);
        }
        d.flare(c, r * 3, color, a * 0.6f);
    }

    // ================================================================= stones

    /** Setting a stone: it spirals into the gauntlet with a shock ring. */
    static final class StoneSet extends Scene {
        StoneSet(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            float k = t(pt);
            Vec3 f = fist(e, pt);
            Vec3 from = f.add(e.getViewVector(pt).scale(1.5)).add(0, 0.8, 0);
            Vec3 at = from.lerp(f, Math.min(1, k * 2));
            if (k < 0.5f) {
                gem(d, at, 0.12, color, 1, time(pt) * 0.4);
                d.helix(from, at, 0.2, 2, 2, time(pt), 0.03, color, 0.8f);
            } else {
                float f2 = (k - 0.5f) * 2;
                d.ring(f, e.getViewVector(pt), 0.2 + f2 * 1.6, 0.06, color, 1 - f2);
                d.flare(f, 0.8 * (1 - f2) + 0.2, color, 1 - f2 * 0.5f);
            }
        }
    }

    /** Space Warp: a blue fold opens at both ends and a streak of folded space connects them. */
    static final class SpaceWarp extends Scene {
        SpaceWarp(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = 1 - t(pt);
            Vec3 a = origin.add(0, 1, 0), b = origin.add(dir).add(0, 1, 0);
            for (Vec3 c : new Vec3[]{a, b}) {
                Vec3 n = d.camera().subtract(c).normalize();
                d.ring(c, n, 0.4 + t(pt) * 1.8, 0.08, color, f);
                d.polygon(c, n, 0.6 + t(pt) * 1.2, 4, time(pt) * 0.2, 0.05, 0xFFFFFF, f);
                d.flare(c, 1.4 * f, color, f);
            }
            d.helix(a, b, 0.3 * f, a.distanceTo(b) * 0.2, 2, time(pt), 0.05, color, f * 0.7f);
        }
    }

    /** Tesseract Fold: a rotating hyper-cube of blue light swallowing the battlefield. */
    static final class Tesseract extends Scene {
        Tesseract(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 6, 10);
            double time = time(pt);
            for (int layer = 0; layer < 2; layer++) {
                double s = (layer == 0 ? 1.6 : 0.8) * (1 + 0.15 * Math.sin(time * 0.2)) * (0.4 + 0.6 * f);
                double rot = time * (layer == 0 ? 0.06 : -0.09);
                Vec3[] v = new Vec3[8];
                for (int i = 0; i < 8; i++) {
                    double x = (i & 1) == 0 ? -s : s, y = (i & 2) == 0 ? -s : s, z = (i & 4) == 0 ? -s : s;
                    double xr = x * Math.cos(rot) - z * Math.sin(rot), zr = x * Math.sin(rot) + z * Math.cos(rot);
                    double yr = y * Math.cos(rot * 0.7) - zr * Math.sin(rot * 0.7), zr2 = y * Math.sin(rot * 0.7) + zr * Math.cos(rot * 0.7);
                    v[i] = origin.add(xr, yr, zr2);
                }
                int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
                for (int[] e : edges) {
                    d.ribbon(v[e[0]], v[e[1]], 0.12, argb(color, f), argb(0xBFE0FF, f));
                }
                if (layer == 0) {
                    d.sphere(origin, s * 0.9, color, 0.05f * f, 0.5f * f, 10, 16);
                }
            }
            d.flare(origin, 2.5 * f, 0xBFE0FF, f);
            for (int i = 0; i < 6; i++) {
                double r = param * (1 - ((time * 0.04 + i / 6.0) % 1.0));
                d.ring(origin.subtract(0, 0.9, 0), UP, r, 0.15, color, 0.5f * f * (float) (1 - r / param));
            }
        }

        @Override
        public float shake(float pt) {
            return age > 24 && age < 32 ? 1.6f * falloff(origin, 24) : 0.15f * falloff(origin, 20);
        }
    }

    /** A thrall's eyes burn gold and a halo of control turns over its head. */
    static final class MindThrall extends Scene {
        MindThrall(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null || !e.isAlive()) {
                return;
            }
            float f = envelope(pt, 5, 10);
            Vec3 head = e.getPosition(pt).add(0, e.getBbHeight() + 0.35, 0);
            d.ring(head, UP, 0.35, 0.04, color, f);
            d.star(head, UP, 0.45, 0.6, 6, time(pt) * 0.08, 0.03, color, f);
            Vec3 eye = e.getEyePosition(pt).add(e.getViewVector(pt).scale(0.3));
            d.flare(eye, 0.2, color, f);
        }
    }

    /** Reality Warp: red ribbons twist the air into a checker of unreal colour. */
    static final class RealityWarp extends Scene {
        RealityWarp(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 4, 12);
            double time = time(pt);
            for (int i = 0; i < 9; i++) {
                double a = i * 0.7 + time * 0.05;
                Vec3 prev = origin;
                for (int k = 1; k <= 16; k++) {
                    double s = k / 16.0;
                    Vec3 q = origin.add(Math.cos(a + s * 6) * param * s, s * 4 + Math.sin(time * 0.1 + i + s * 5), Math.sin(a + s * 6) * param * s);
                    d.ribbon(prev, q, 0.25 * (1 - s) + 0.05, argb(i % 2 == 0 ? 0xE0202A : 0x200008, f), argb(0xFF6070, f * 0.5f));
                    prev = q;
                }
            }
            d.sphere(origin.add(0, 1, 0), param * 0.6, color, 0.03f * f, 0.45f * f, 10, 18);
        }
    }

    /** Reality Shatter: the world breaks into floating red shards around you, then implodes. */
    static final class RealityShatter extends Scene {
        RealityShatter(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float k = t(pt), f = envelope(pt, 3, 10);
            RandomSource r = RandomSource.create(seed);
            Vec3 c = origin.add(0, 1, 0);
            for (int i = 0; i < 46; i++) {
                Vec3 dirv = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.7, r.nextDouble() - 0.5).normalize();
                double dist = param * (0.2 + r.nextDouble() * 0.8) * (k < 0.25 ? k * 4 : 1 - (k - 0.25) * 0.4);
                Vec3 at = c.add(dirv.scale(dist));
                Vec3 ax = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize();
                Vec3 u = FxDraw.perp(ax), w = ax.cross(u).normalize();
                double s = 0.4 + r.nextDouble() * 0.6, rot = time(pt) * 0.1 + i;
                Vec3 p0 = FxDraw.onCircle(at, u, w, s, rot), p1 = FxDraw.onCircle(at, u, w, s, rot + 2.2), p2 = FxDraw.onCircle(at, u, w, s * 0.7, rot + 4);
                d.ink();
                d.tri(p0, p1, p2, argb(0x200008, 0.85f * f), argb(0x000000, 0.85f * f), argb(0x400010, 0.85f * f));
                d.glow();
                d.ribbon(p0, p1, 0.05, argb(color, f), argb(0xFF8090, f));
                d.ribbon(p1, p2, 0.05, argb(color, f), argb(0xFF8090, f));
            }
            d.flare(c, 3 * (1 - k), color, f);
        }

        @Override
        public float shake(float pt) {
            return age > 8 && age < 16 ? 2f * falloff(origin, param * 1.5) : 0;
        }

        @Override
        public float flash(float pt) {
            return age > 9 && age < 12 ? 0.3f * falloff(origin, param) : 0;
        }
    }

    // ================================================================= time

    /** Rewind: a green ribbon of afterimages streaks backward along the path. */
    static final class TimeRewind extends Scene {
        TimeRewind(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = 1 - t(pt);
            Vec3 a = origin, b = origin.add(dir);
            for (int i = 0; i < 6; i++) {
                Vec3 c = a.lerp(b, i / 5.0);
                d.ribbon(c, c.add(0, 1.8, 0), 0.5, argb(color, 0.35f * f), argb(color, 0.1f * f));
                d.ring(c.add(0, 1, 0), d.camera().subtract(c).normalize(), 0.6, 0.03, 0xFFFFFF, 0.5f * f);
            }
            d.helix(a.add(0, 1, 0), b.add(0, 1, 0), 0.4, a.distanceTo(b) * 0.2, 2, -time(pt), 0.05, color, f);
        }
    }

    /** A dome of stopped time: clock faces turning slowly on its shell. */
    static final class TimeDome extends Scene {
        TimeDome(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            Vec3 c = (p.flags() & com.infinitemultiverse.core.cinematic.Cinematics.FOLLOW) != 0 && e != null ? e.getPosition(pt) : origin;
            float f = envelope(pt, 6, 8);
            double r = param * Math.min(1, time(pt) / 8);
            double time = time(pt);
            d.sphere(c, r, color, 0.02f * f, 0.35f * f, 16, 28);
            d.ring(c.add(0, 0.05, 0), UP, r, 0.1, color, f);
            for (int i = 0; i < 3; i++) {
                Vec3 axis = new Vec3(Math.sin(i * 2.1), 0.5, Math.cos(i * 2.1)).normalize();
                Vec3 face = c.add(axis.scale(r * 0.98)).add(0, r * 0.2, 0);
                Vec3 n = axis;
                d.ring(face, n, r * 0.25, 0.04, 0xFFFFFF, 0.7f * f);
                Vec3 u = FxDraw.perp(n), w = n.cross(u).normalize();
                for (int k = 0; k < 12; k++) {
                    double a = Math.PI * 2 * k / 12;
                    d.line(FxDraw.onCircle(face, u, w, r * 0.21, a), FxDraw.onCircle(face, u, w, r * 0.24, a), n, 0.03, color, f);
                }
                d.line(face, FxDraw.onCircle(face, u, w, r * 0.18, time * 0.01 + i), n, 0.04, 0xFFFFFF, f);
            }
        }
    }

    /** Stasis: the target is sealed in a frozen hourglass of light. */
    static final class Stasis extends Scene {
        Stasis(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null || !e.isAlive()) {
                return;
            }
            float f = envelope(pt, 4, 8);
            Vec3 c = e.getPosition(pt);
            double h = e.getBbHeight() + 0.4, r = e.getBbWidth() + 0.3;
            d.tube(c.subtract(0, 0.2, 0), c.add(0, h, 0), r, color, 0.5f * f, 16);
            d.ring(c.add(0, h, 0), UP, r, 0.06, 0xFFFFFF, f);
            d.ring(c.subtract(0, 0.15, 0), UP, r, 0.06, 0xFFFFFF, f);
            for (int i = 0; i < 4; i++) {
                double y = ((time(pt) * 0.01 + i / 4.0) % 1.0) * h;
                d.ring(c.add(0, y, 0), UP, r * 0.98, 0.03, color, 0.6f * f);
            }
        }
    }

    // ================================================================= soul

    /** Orange soul-fire streaming from the victim into the gauntlet. */
    static final class SoulDrain extends Scene {
        SoulDrain(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age < duration - 6) {
                Vec3 at = origin.add(dir.scale(s.rand()));
                s.mote(at, dir.normalize().scale(0.3), 0xFFE0A0, color, 0.2f, 10, 1f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 2, 8);
            Vec3 a = origin, b = origin.add(dir);
            d.helix(a, b, 0.35, a.distanceTo(b) * 0.25, 3, time(pt) * 0.5, 0.06, color, f);
            d.ribbon(a, b, 0.15, argb(color, f * 0.6f), argb(0xFFE0A0, f));
            d.flare(a, 1.2 * f, color, f);
        }
    }

    // ================================================================= the Snap

    /**
     * The Snap (110 ticks): the camera moves in on the raised gauntlet; each stone ignites in turn with its name, the
     * fist crackles with all six colours, the fingers snap (white flash, ringing), and the camera drifts back as dust
     * blows across the field.
     */
    static final class Snap extends Scene {
        static final int SNAP_AT = 62;

        Snap(ScenePayload p) {
            super(p);
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            Entity e = entity();
            if (e == null) {
                return null;
            }
            Vec3 f = fist(e, pt).add(0, 0.4, 0);
            float yaw = e.getViewYRot(pt);
            float a = time(pt);
            if (a < SNAP_AT) {
                float k = a / SNAP_AT;
                return CinematicCamera.Shot.orbit(e.getPosition(pt), yaw, 40 - k * 25, 2.2 - k * 0.9, 1.7, f);
            }
            float k = Mth.clamp((a - SNAP_AT) / 40f, 0, 1);
            return CinematicCamera.Shot.orbit(e.getPosition(pt), yaw, 15 + k * 150, 1.3 + k * 8, 1.7 + k * 3, e.getPosition(pt).add(0, 1.4, 0));
        }

        @Override
        public void tick(VfxSpawner s) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            Vec3 f = fist(e, 0).add(0, 0.4, 0);
            if (age < SNAP_AT) {
                int lit = Math.min(6, age / 8);
                for (int i = 0; lit > 0 && i < s.count(lit * 2); i++) {
                    int c = InfinityStone.values()[i % lit].color();
                    s.spark(f.add(s.randomUnit().scale(0.6)), s.randomUnit().scale(0.05), 0xFFFFFF, c, 0.08f, 10, 0.9f);
                }
            } else if (age == SNAP_AT) {
                for (int i = 0; i < s.count(160); i++) {
                    s.spark(f, s.randomUnit().scale(0.3 + s.rand() * 0.6), 0xFFFFFF, InfinityStone.values()[i % 6].color(), 0.15f, 20, 0.9f);
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            float a = time(pt);
            Vec3 f = fist(e, pt).add(0, 0.4, 0);
            Vec3 look = e.getViewVector(pt);
            int lit = Math.min(6, (int) (a / 8));
            for (int i = 0; i < lit; i++) {
                InfinityStone stone = InfinityStone.values()[i];
                double ang = Math.PI * 2 * i / 6 + a * 0.03;
                Vec3 gp = f.add(FxDraw.perp(look).scale(Math.cos(ang) * 0.45)).add(0, Math.sin(ang) * 0.45, 0);
                gem(d, gp, 0.07, stone.color(), 1, a * 0.2 + i);
                d.ribbon(gp, f, 0.04, argb(stone.color(), 0.8f), argb(0xFFFFFF, 0.9f));
            }
            if (a > 48 && a < SNAP_AT + 4) {
                for (int i = 0; i < 6; i++) {
                    d.bolt(f, f.add(new Vec3(Math.sin(i * 1.7), Math.cos(i * 2.3), Math.sin(i * 2.9)).scale(1.2)), seed + age + i, 0.3, 0.025,
                            InfinityStone.values()[i].color(), 1, 1);
                }
                d.flare(f, 0.6 + (a - 48) * 0.05, 0xFFE27A, 1);
            }
            if (a >= SNAP_AT) {
                float k = Mth.clamp((a - SNAP_AT) / 30f, 0, 1);
                d.ring(f, UP, 0.5 + k * param, 0.4, 0xFFE27A, 1 - k);
                d.ring(f, look, 0.5 + k * param * 0.6, 0.2, 0xFFFFFF, 1 - k);
            }
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (!watching) {
                return;
            }
            float a = time(pt);
            int lit = (int) (a / 8);
            if (lit < 6 && a > 2) {
                InfinityStone stone = InfinityStone.values()[Math.min(5, lit)];
                float al = Mth.clamp(Math.min((a - lit * 8) / 2, (lit * 8 + 8 - a) / 2), 0, 1);
                SceneManager.title(g, stone.displayName(), null, stone.color(), al, 2.4f, 0.78f);
            }
            if (a > SNAP_AT + 6) {
                float al = Mth.clamp(Math.min((a - SNAP_AT - 6) / 6, (duration - a) / 8), 0, 1);
                SceneManager.title(g, Component.translatable("message.infinitemultiverse.snap_title"), null, 0xFFE27A, al, 3.2f, 0.8f);
            }
        }

        @Override
        public float flash(float pt) {
            float a = time(pt) - SNAP_AT;
            return a >= 0 && a < 10 ? (1 - a / 10) * falloff(origin, param * 1.5f) : 0;
        }

        @Override
        public float shake(float pt) {
            float a = time(pt);
            return a > 48 && a < SNAP_AT ? 0.4f * falloff(origin, 20) : a >= SNAP_AT && a < SNAP_AT + 6 ? 2.5f * falloff(origin, param) : 0;
        }

        @Override
        public float fov(float pt) {
            float a = time(pt) - SNAP_AT;
            return localIsCaster() && a >= 0 && a < 10 ? 0.85f + a * 0.015f : 1;
        }
    }

    /** Disintegration: a creature crumbles to drifting ash from the head down. */
    static final class Dust extends Scene {
        Dust(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            Entity e = entity();
            Vec3 c = e != null ? e.position() : origin;
            double h = Math.max(0.5, param) * (1 - t(0));
            for (int i = 0; i < s.count(12); i++) {
                Vec3 at = c.add((s.rand() - 0.5) * 0.7, h * s.rand() + h * 0.4, (s.rand() - 0.5) * 0.7);
                s.mote(at, new Vec3(0.06 + s.rand() * 0.05, 0.02 + s.rand() * 0.04, (s.rand() - 0.5) * 0.04), 0x8A7058, 0x302018, 0.12f, 40, 0.98f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
        }
    }

    // ================================================================= portals & rifts

    /** Portal gun bolt. */
    static final class PortalShot extends Scene {
        PortalShot(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float k = Mth.clamp(time(pt) / 3, 0, 1), f = 1 - t(pt);
            Vec3 head = origin.add(dir.scale(k));
            d.ribbon(origin.add(dir.scale(Math.max(0, k - 0.3))), head, 0.12, argb(color, 0), argb(color, f));
            d.flare(head, 0.4, 0xFFFFFF, f);
            if (k >= 1 && param > 0) {
                d.ring(origin.add(dir), d.camera().subtract(origin.add(dir)).normalize(), 0.2 + t(pt) * 0.8, 0.05, color, f);
            }
        }
    }

    /** Flash ring where something passes through a portal or rift. */
    static final class Burst extends Scene {
        Burst(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = 1 - t(pt);
            Vec3 n = dir.lengthSqr() > 0 ? dir.normalize() : d.camera().subtract(origin).normalize();
            double s = Math.max(1, param);
            d.ring(origin, n, (0.4 + t(pt) * 1.4) * s, 0.06 * s, color, f);
            d.flare(origin, 1.2 * s * f, color, f);
        }
    }

    /** A rift tearing open: the jagged seam grows from a point with crackling arcs. The entity renderer draws it afterwards. */
    static final class RiftOpen extends Scene {
        RiftOpen(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = 1 - t(pt);
            d.flare(origin, 3 * f, color, f);
            for (int i = 0; i < 5; i++) {
                d.bolt(origin, origin.add(new Vec3(Math.sin(i * 1.3), Math.cos(i * 2.1), Math.sin(i * 0.7)).scale(2.5)), seed + age + i, 0.6, 0.04, color, f, 1);
            }
        }

        @Override
        public float shake(float pt) {
            return age < 10 ? 0.8f * falloff(origin, 16) : 0;
        }
    }

    // ================================================================= space

    /** Launch: camera low behind you as thrust plumes ignite and you rocket up into a darkening sky. */
    static final class Launch extends Scene {
        Launch(ScenePayload p) {
            super(p);
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            Entity e = entity();
            if (e == null) {
                return null;
            }
            Vec3 at = e.getPosition(pt);
            float yaw = e.getViewYRot(pt);
            float a = time(pt);
            if (a < 14) {
                return CinematicCamera.Shot.orbit(origin, yaw, 160, 5, 0.3, at.add(0, 1.5, 0));
            }
            return CinematicCamera.Shot.orbit(at, yaw, 160 + (a - 14) * 2, 4 + (a - 14) * 0.15, -2.5, at.add(0, 1, 0));
        }

        @Override
        public void tick(VfxSpawner s) {
            Entity e = entity();
            if (e == null || age < 8) {
                return;
            }
            for (int i = 0; i < s.count(20); i++) {
                s.mote(e.position().add(s.jitter(0.3)), new Vec3(0, -0.6 - s.rand() * 0.4, 0).add(s.jitter(0.08)), 0xFFF0C0, 0xFF6A1A, 0.35f, 16, 0.92f);
                s.mote(e.position().add(s.jitter(0.6)).subtract(0, 1.5, 0), s.jitter(0.1), 0x909090, 0x404040, 0.6f, 40, 0.96f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null || time(pt) < 8) {
                return;
            }
            Vec3 at = e.getPosition(pt);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 foot = at.add(FxDraw.perp(e.getViewVector(pt).multiply(1, 0, 1).normalize()).scale(0.15 * s));
                d.beam(foot, foot.subtract(0, 2.5 + Math.sin(time(pt) * 2) * 0.3, 0), 0.18, color, 1);
            }
            d.flare(at, 1.4, 0xFFF0C0, 0.9f);
            d.ring(origin.add(0, 0.05, 0), UP, 1 + time(pt) * 0.25, 0.3, 0xFF9A3A, envelope(pt, 0, 20));
        }

        @Override
        public float shake(float pt) {
            return localIsCaster() && age > 8 ? 0.6f + age * 0.02f : 0;
        }

        @Override
        public float flash(float pt) {
            return age > duration - 18 && age < duration - 12 ? 0.8f : 0;
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (!watching) {
                return;
            }
            float a = time(pt);
            int w = g.guiWidth(), h = g.guiHeight();
            float sky = Mth.clamp((a - 20) / 24, 0, 1);
            g.fill(0, 0, w, h, argb(0x02030A, sky * 0.85f));
            SceneManager.title(g, Component.literal(String.format(java.util.Locale.ROOT, "ALT  %,d m", (int) (Math.pow(Math.max(0, a - 8), 2.6) * 2))), null, 0x9FE0FF,
                    Mth.clamp((a - 10) / 4, 0, 1), 1.6f, 0.85f);
        }
    }

    /** Re-entry: wreathed in plasma, streaks of fire tear past as you fall back to the planet. */
    static final class Reentry extends Scene {
        Reentry(ScenePayload p) {
            super(p);
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            Entity e = entity();
            if (e == null) {
                return null;
            }
            Vec3 at = e.getPosition(pt);
            return CinematicCamera.Shot.orbit(at, e.getViewYRot(pt), 30 + time(pt) * 3, 3.5, 2.5, at.add(0, 0.8, 0));
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            Vec3 at = e.getPosition(pt).add(0, 0.9, 0);
            float k = Mth.clamp(time(pt) / 20, 0, 1);
            d.sphere(at.subtract(0, 0.4, 0), 1.3, 0xFF6A1A, 0.05f * k, 0.8f * k, 12, 20);
            RandomSource r = RandomSource.create(seed + age);
            for (int i = 0; i < 18; i++) {
                Vec3 off = new Vec3(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5).normalize().scale(1.3 + r.nextDouble());
                Vec3 base = at.add(off).subtract(0, 1, 0);
                d.ribbon(base, base.add(0, 4 + r.nextDouble() * 3, 0), 0.15, argb(0xFFF0C0, k), argb(0xFF3A00, 0));
            }
        }

        @Override
        public float shake(float pt) {
            return localIsCaster() ? 1.2f * Mth.clamp(time(pt) / 20, 0, 1) : 0;
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (!watching) {
                return;
            }
            float k = Mth.clamp(time(pt) / 20, 0, 1);
            int w = g.guiWidth(), h = g.guiHeight();
            g.fillGradient(0, 0, w, h / 3, argb(0xFF6A1A, 0.5f * k), 0);
            g.fillGradient(0, h * 2 / 3, w, h, 0, argb(0xFF3A00, 0.6f * k));
        }
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
