package com.infinitemultiverse.client.cinematic;

import static com.infinitemultiverse.client.cinematic.FxDraw.argb;
import static com.infinitemultiverse.client.cinematic.FxDraw.lerp;
import static com.infinitemultiverse.client.cinematic.GenericScenes.UP;

import com.infinitemultiverse.client.vfx.VfxSpawner;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.network.ScenePayload;
import java.util.ArrayList;
import java.util.List;
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

/** Mystic Arts scenes: sling-ring gateways, astral projection, the Eye of Agamotto and the Mirror Dimension. */
final class MysticScenes {
    private MysticScenes() {
    }

    static final int SPARK = 0xFF9A2A;
    static final int HOT = 0xFFE6A0;

    static void register(Map<ResourceLocation, Function<ScenePayload, Scene>> m) {
        m.put(SceneIds.SLING_PORTAL, SlingPortal::new);
        m.put(SceneIds.SPELL_CIRCLE, SpellCircle::new);
        m.put(SceneIds.ASTRAL_EXIT, p -> new Astral(p, true));
        m.put(SceneIds.ASTRAL_RETURN, p -> new Astral(p, false));
        m.put(SceneIds.ASTRAL_SPIRIT, AstralSpirit::new);
        m.put(SceneIds.TIME_EYE, TimeEye::new);
        m.put(SceneIds.MIRROR_ENTER, p -> new MirrorShatter(p, true));
        m.put(SceneIds.MIRROR_EXIT, p -> new MirrorShatter(p, false));
        m.put(SceneIds.MIRROR_WORLD, MirrorWorld::new);
    }

    /** Draws a sling-ring style rune circle (also used for spell-casting circles at the hands). */
    static void runeCircle(FxDraw d, Vec3 c, Vec3 n, double r, double rot, int col, float a) {
        d.disc(c, n, 0, r, argb(col, 0.12f * a), argb(col, 0.03f * a), 32);
        d.ring(c, n, r, 0.04, col, a);
        d.ring(c, n, r * 0.84, 0.025, HOT, a);
        d.ring(c, n, r * 1.1, 0.015, col, a * 0.7f);
        d.star(c, n, r * 0.8, 0.5, 6, rot, 0.03, col, a);
        d.polygon(c, n, r * 0.52, 3, -rot * 1.5, 0.03, HOT, a);
        d.polygon(c, n, r * 0.52, 3, -rot * 1.5 + Math.PI, 0.03, HOT, a);
        d.ring(c, n, r * 0.3, 0.025, col, a);
        Vec3 u = FxDraw.perp(n), w = n.cross(u).normalize();
        for (int i = 0; i < 24; i++) {
            double ang = rot * 1.3 + Math.PI * 2 * i / 24;
            d.line(FxDraw.onCircle(c, u, w, r * 0.88, ang), FxDraw.onCircle(c, u, w, r * (i % 2 == 0 ? 1.0 : 0.94), ang), n, 0.02, col, a);
        }
    }

    // =================================================================== Sling Ring

    /**
     * A sling-ring gateway. Opening: a single spark traces the circle like a hand gesture, then the rim ignites into a
     * churning band of hundreds of orbiting sparks around a molten edge; sparks shed and fall. The face shows a hazy
     * window tinted toward the destination. Closing: the rim collapses inward.
     */
    static final class SlingPortal extends Scene {
        static final int OPEN = 16;
        static final int CLOSE = 14;
        private final double radius;
        private final float[][] streaks;

        SlingPortal(ScenePayload p) {
            super(p);
            this.radius = Math.max(1.2, param);
            RandomSource r = RandomSource.create(seed);
            streaks = new float[220][4];
            for (float[] s : streaks) {
                s[0] = (float) (r.nextDouble() * Math.PI * 2);
                s[1] = (float) ((r.nextDouble() - 0.35) * 0.22);
                s[2] = (float) (0.08 + r.nextDouble() * 0.18);
                s[3] = (float) (0.15 + r.nextDouble() * 0.5);
            }
        }

        private double size(float pt) {
            float a = time(pt);
            float open = Mth.clamp((a - OPEN * 0.7f) / (OPEN * 0.3f), 0, 1);
            float close = Mth.clamp((duration - a) / CLOSE, 0, 1);
            return radius * Math.min(open < 1 ? 0.15 + 0.85 * open : 1, close);
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age < OPEN * 0.7f) {
                return;
            }
            Vec3 n = dir.normalize(), u = FxDraw.perp(n), w = n.cross(u).normalize();
            double r = size(0);
            for (int i = 0; i < s.count(10); i++) {
                double ang = s.rand() * Math.PI * 2;
                Vec3 at = FxDraw.onCircle(origin, u, w, r, ang);
                Vec3 tangent = u.scale(-Math.sin(ang)).add(w.scale(Math.cos(ang)));
                s.spark(at, tangent.scale(0.15 + s.rand() * 0.2).add(0, -0.05 - s.rand() * 0.1, 0), 0xFFFFFF, SPARK, 0.07f + s.rand() * 0.05f, 14 + (int) (s.rand() * 14), 0.96f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            Vec3 n = dir.normalize(), u = FxDraw.perp(n), w = n.cross(u).normalize();
            if (a < OPEN * 0.7f) {
                // a single spark traces the circle
                float k = a / (OPEN * 0.7f);
                double sweep = Math.PI * 2 * k;
                d.ring(origin, n, radius * 0.15 + radius * 0.85 * k * 0.2, 0.03, SPARK, 1, 48, Math.PI / 2, sweep);
                Vec3 head = FxDraw.onCircle(origin, u, w, radius * 0.15 + radius * 0.85 * k * 0.2, Math.PI / 2 + sweep);
                d.flare(head, 0.35, HOT, 1);
                return;
            }
            double r = size(pt);
            float alive = (float) (r / radius);
            double time = a;
            // hazy window
            d.ink();
            d.disc(origin, n, 0, r * 0.97, argb(0x0A0610, 0.55f * alive), argb(0x1A0C04, 0.25f * alive), 40);
            d.glow();
            d.disc(origin, n, r * 0.6, r, argb(SPARK, 0.0f), argb(SPARK, 0.35f * alive), 40);
            for (int i = 0; i < 3; i++) {
                d.ring(origin, n, r * (0.25 + 0.2 * i), 0.02, 0xFFB060, 0.15f * alive, 32, time * (0.05 + i * 0.03), Math.PI * 1.2);
            }
            // molten rim
            d.ring(origin, n, r, 0.14, SPARK, 0.9f * alive);
            d.ring(origin, n, r * 0.99, 0.04, HOT, alive);
            // hundreds of orbiting spark streaks: each is a short tangential arc with its own speed and radial offset
            for (float[] s : streaks) {
                double ang = s[0] + time * s[3];
                double rr = r * (1 + s[1]);
                Vec3 p0 = FxDraw.onCircle(origin, u, w, rr, ang);
                Vec3 p1 = FxDraw.onCircle(origin, u, w, rr + s[1] * 0.2, ang + s[2]);
                float heat = (float) Math.max(0, 1 - Math.abs(s[1]) * 6);
                d.ribbon(p0, p1, 0.035 + heat * 0.03, argb(lerp(0xFF6A10, HOT, heat), 0), argb(lerp(0xFF7A1A, HOT, heat * 0.7f), alive));
            }
            d.flare(origin.add(n.scale(0.02)), r * 0.4, 0xFFB060, 0.15f * alive);
        }

        @Override
        public float shake(float pt) {
            return age > OPEN * 0.7f && age < OPEN ? 0.3f * falloff(origin, 8) : 0;
        }
    }

    /** Spell-casting circles flaring at the caster's hands. */
    static final class SpellCircle extends Scene {
        SpellCircle(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            float f = envelope(pt, 3, 5);
            Vec3 look = e.getViewVector(pt);
            boolean firstPerson = localIsCaster() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
            double size = firstPerson ? 0.22 : 0.45;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 hand = CursedScenes.hand(e, pt, side * (firstPerson ? 1.6 : 1));
                runeCircle(d, hand.add(look.scale(firstPerson ? 0.5 : 0.2)).subtract(0, firstPerson ? 0.15 : 0, 0), look, size * f, time(pt) * 0.12 * side, color, f);
            }
        }
    }

    // =================================================================== Astral projection

    /** Glowing spirit silhouette: head, torso and limbs as soft golden strokes. */
    static void spiritBody(FxDraw d, Vec3 feet, Vec3 facing, float a, double time) {
        Vec3 side = FxDraw.perp(facing);
        Vec3 hip = feet.add(0, 0.85, 0), neck = feet.add(0, 1.45, 0), head = feet.add(0, 1.75, 0);
        int gold = 0xFFE6A0;
        d.flare(head, 0.45, gold, a);
        d.ribbon(hip, neck, 0.45, argb(gold, 0.6f * a), argb(gold, 0.7f * a));
        for (int s = -1; s <= 1; s += 2) {
            double swing = Math.sin(time * 0.15 + s) * 0.15;
            d.ribbon(neck.add(side.scale(0.25 * s)), neck.add(side.scale(0.35 * s)).add(0, -0.7, 0).add(facing.scale(swing)), 0.16, argb(gold, 0.6f * a), argb(gold, 0.2f * a));
            d.ribbon(hip.add(side.scale(0.12 * s)), feet.add(side.scale(0.15 * s)).subtract(facing.scale(swing)), 0.18, argb(gold, 0.55f * a), argb(gold, 0.1f * a));
        }
        d.flare(feet.add(0, 1, 0), 1.4, gold, 0.25f * a);
    }

    /**
     * Leaving the body (exit): the camera hangs over the shoulder as a golden spirit peels upward out of the body and
     * the view rises with it. Returning: the spirit streaks back into the body with a flash.
     */
    static final class Astral extends Scene {
        private final boolean exit;
        private final float baseYaw;

        Astral(ScenePayload p, boolean exit) {
            super(p);
            this.exit = exit;
            this.baseYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
        }

        @Override
        public boolean wantsCamera() {
            return exit;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            float k = t(pt);
            return CinematicCamera.Shot.orbit(origin, baseYaw, 25 + k * 40, 3.2 - k * 0.6, 1.2 + k * 1.6, origin.add(0, 1.2 + k * 1.2, 0));
        }

        @Override
        public void tick(VfxSpawner s) {
            for (int i = 0; i < s.count(4); i++) {
                s.mote(origin.add(s.jitter(0.4)).add(0, s.rand() * 2, 0), new Vec3(0, 0.05, 0), 0xFFFFFF, HOT, 0.15f, 20, 0.95f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float k = t(pt);
            Vec3 facing = new Vec3(dir.x, 0, dir.z).normalize();
            if (exit) {
                float rise = Mth.clamp(k * 1.4f, 0, 1);
                spiritBody(d, origin.add(0, rise * 1.2, 0).subtract(facing.scale(rise * 0.4)), facing, 1 - k * 0.3f, time(pt));
                d.ring(origin.add(0, 0.05, 0), UP, 0.6 + k * 1.8, 0.1, HOT, 1 - k);
            } else {
                Vec3 from = origin.add(dir);
                Vec3 at = from.lerp(origin, Mth.clamp(k * 2, 0, 1));
                spiritBody(d, at, facing, 1 - k, time(pt));
                d.ribbon(from.add(0, 1, 0), at.add(0, 1, 0), 0.3, argb(HOT, 0), argb(HOT, 0.6f * (1 - k)));
                if (k > 0.5f) {
                    d.flare(origin.add(0, 1, 0), 3 * (1 - k), 0xFFFFFF, (1 - k) * 2);
                }
            }
        }

        @Override
        public float flash(float pt) {
            return !exit && age > duration / 2 && age < duration / 2 + 3 && localIsCaster() ? 0.5f : 0;
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (watching) {
                SceneManager.title(g, Component.translatable("ability.infinitemultiverse.astral_projection"), null, HOT, envelope(pt, 5, 6), 2.6f, 0.78f);
            }
        }
    }

    /** The projected spirit: gold flames and a halo; for the projector, a gold-washed, softly breathing screen edge. */
    static final class AstralSpirit extends Scene {
        AstralSpirit(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null || (localIsCaster() && Minecraft.getInstance().options.getCameraType().isFirstPerson())) {
                return;
            }
            spiritBody(d, e.getPosition(pt), e.getViewVector(pt).multiply(1, 0, 1).normalize(), 0.8f * envelope(pt, 3, 3), e.tickCount + pt);
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (!localIsCaster()) {
                return;
            }
            int w = g.guiWidth(), h = g.guiHeight();
            float breathe = 0.5f + 0.5f * (float) Math.sin(time(pt) * 0.08);
            float f = envelope(pt, 3, 3) * (0.25f + 0.15f * breathe);
            g.fillGradient(0, 0, w, h / 5, argb(HOT, f), 0);
            g.fillGradient(0, h - h / 5, w, h, 0, argb(HOT, f));
            g.fill(0, 0, w / 14, h, argb(HOT, f * 0.4f));
            g.fill(w - w / 14, 0, w, h, argb(HOT, f * 0.4f));
        }
    }

    // =================================================================== Eye of Agamotto

    /**
     * Eye of Agamotto (50 ticks): the amulet's eye opens at the chest, green mandalas lock around the forearm and a
     * colossal clock face spins backward around the caster while green echoes stream in reverse.
     */
    static final class TimeEye extends Scene {
        static final int GREEN = 0x5CFF8A;
        private final float baseYaw;

        TimeEye(ScenePayload p) {
            super(p);
            this.baseYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            float k = t(pt);
            Entity e = entity();
            Vec3 at = e != null ? e.getPosition(pt) : origin;
            return CinematicCamera.Shot.orbit(at, baseYaw, -30 + k * 80, 2.6 + k * 1.5, 1.3 + k * 0.6, at.add(0, 1.2, 0));
        }

        @Override
        public void tick(VfxSpawner s) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            for (int i = 0; i < s.count(6); i++) {
                Vec3 at = e.position().add(s.randomUnit().scale(2.5)).add(0, 1, 0);
                s.mote(at, at.subtract(e.position().add(0, 1, 0)).scale(0.05), 0xFFFFFF, GREEN, 0.15f, 14, 1f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            Vec3 feet = e != null ? e.getPosition(pt) : origin;
            Vec3 look = e != null ? e.getViewVector(pt) : dir.normalize();
            float f = envelope(pt, 6, 10);
            double time = time(pt);
            Vec3 chest = feet.add(0, 1.25, 0).add(look.multiply(1, 0, 1).normalize().scale(0.3));
            // the eye opens
            float open = Mth.clamp((float) time / 10, 0, 1);
            Vec3 facing = look.multiply(1, 0, 1).normalize();
            Vec3 side = FxDraw.perp(facing);
            Vec3 prevT = null, prevB = null;
            for (int i = 0; i <= 16; i++) {
                double s = -1 + 2.0 * i / 16;
                double hgt = Math.sqrt(1 - s * s) * 0.12 * open;
                Vec3 top = chest.add(side.scale(s * 0.25)).add(0, hgt, 0), bot = chest.add(side.scale(s * 0.25)).add(0, -hgt, 0);
                if (prevT != null) {
                    d.ribbon(prevT, top, 0.03, argb(GREEN, f), argb(GREEN, f));
                    d.ribbon(prevB, bot, 0.03, argb(GREEN, f), argb(GREEN, f));
                }
                prevT = top;
                prevB = bot;
            }
            d.flare(chest, 0.25 * open, 0xFFFFFF, f);
            // forearm mandalas
            Vec3 hand = CursedScenes.hand(e != null ? e : Minecraft.getInstance().player, pt, -1);
            for (int i = 0; i < 3; i++) {
                runeCircle(d, hand.subtract(look.scale(0.15 * i)), look, 0.3 + 0.12 * i, time * 0.15 * (i % 2 == 0 ? 1 : -1), GREEN, f);
            }
            // giant clock spinning backward
            Vec3 c = feet.add(0, 1.1, 0);
            double R = 2.6;
            d.ring(c, UP, R, 0.06, GREEN, f);
            d.ring(c, UP, R * 0.9, 0.03, 0xB0FFC8, f);
            Vec3 u = FxDraw.perp(UP), w = UP.cross(u).normalize();
            for (int i = 0; i < 60; i++) {
                double ang = Math.PI * 2 * i / 60;
                d.line(FxDraw.onCircle(c, u, w, R * (i % 5 == 0 ? 0.8 : 0.86), ang), FxDraw.onCircle(c, u, w, R * 0.92, ang), UP, i % 5 == 0 ? 0.05 : 0.025, GREEN, f);
            }
            double hour = -time * 0.15, minute = -time * 0.9;
            d.line(c, FxDraw.onCircle(c, u, w, R * 0.5, hour), UP, 0.08, 0xFFFFFF, f);
            d.line(c, FxDraw.onCircle(c, u, w, R * 0.78, minute), UP, 0.05, 0xFFFFFF, f);
            for (int i = 0; i < 2; i++) {
                d.ring(c.add(0, 0.6 * (i + 1), 0), new Vec3(Math.sin(time * 0.05 + i), 1, Math.cos(time * 0.04)).normalize(), R * (0.6 - 0.15 * i), 0.03, GREEN, 0.7f * f);
            }
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (watching) {
                float a = time(pt);
                SceneManager.title(g, Component.translatable("ability.infinitemultiverse.time_reversal"), null, TimeEye.GREEN,
                        Mth.clamp(Math.min((a - 8) / 4, (duration - 4 - a) / 6), 0, 1), 2.8f, 0.8f);
            }
        }

        @Override
        public float fov(float pt) {
            return localIsCaster() ? 1 - 0.05f * (float) Math.sin(t(pt) * Math.PI) : 1;
        }
    }

    // =================================================================== Mirror Dimension

    /**
     * Entering (or leaving) the Mirror Dimension (40 ticks): spell circles flare at the hands, reality cracks across the
     * screen like glass, mirrored shards peel away from the world and spin around the caster, everything tilts — and on
     * the shatter-flash you are through. Leaving plays it in reverse: the shards fly home and the cracks heal.
     */
    static final class MirrorShatter extends Scene {
        static final int SHATTER = 28;
        static final int TINT = 0xB070FF;
        private final boolean enter;
        private final List<List<float[]>> cracks = new ArrayList<>();
        private final Vec3[] shardPos = new Vec3[40];
        private final Vec3[] shardAxis = new Vec3[40];
        private final float baseYaw;

        MirrorShatter(ScenePayload p, boolean enter) {
            super(p);
            this.enter = enter;
            this.baseYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
            RandomSource r = RandomSource.create(seed);
            for (int i = 0; i < 16; i++) {
                List<float[]> line = new ArrayList<>();
                double ang = Math.PI * 2 * i / 16 + r.nextDouble() * 0.3;
                float x = 0.5f, y = 0.5f;
                line.add(new float[]{x, y});
                for (int k = 0; k < 8; k++) {
                    ang += (r.nextDouble() - 0.5) * 0.7;
                    x += (float) (Math.cos(ang) * (0.04 + r.nextDouble() * 0.05));
                    y += (float) (Math.sin(ang) * (0.04 + r.nextDouble() * 0.05));
                    line.add(new float[]{x, y});
                }
                cracks.add(line);
            }
            for (int i = 0; i < shardPos.length; i++) {
                double th = r.nextDouble() * Math.PI * 2;
                shardPos[i] = new Vec3(Math.cos(th) * (2.5 + r.nextDouble() * 5), r.nextDouble() * 4 - 0.5, Math.sin(th) * (2.5 + r.nextDouble() * 5));
                shardAxis[i] = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize();
            }
        }

        private float progress(float pt) {
            float k = Mth.clamp(time(pt) / SHATTER, 0, 1);
            return enter ? k : 1 - k;
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            float a = time(pt);
            Entity e = entity();
            Vec3 at = e != null ? e.getPosition(pt) : origin;
            float k = Mth.clamp(a / duration, 0, 1);
            return CinematicCamera.Shot.orbit(at, baseYaw, 160 + k * 120, 4.5 - k * 1.5, 1.8 - k * 0.6, at.add(0, 1.2, 0));
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age == SHATTER) {
                for (int i = 0; i < s.count(120); i++) {
                    s.spark(origin.add(0, 1, 0), s.randomUnit().scale(0.4 + s.rand() * 0.8), 0xFFFFFF, TINT, 0.2f, 20, 0.9f);
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            float k = progress(pt);
            double time = a;
            Entity e = entity();
            Vec3 at = e != null ? e.getPosition(pt) : origin;
            Vec3 c = at.add(0, 1.2, 0);
            // hand circles
            if (e != null && a < SHATTER + 4) {
                Vec3 look = e.getViewVector(pt);
                for (int side = -1; side <= 1; side += 2) {
                    runeCircle(d, CursedScenes.hand(e, pt, side).add(look.scale(0.25)), look, 0.5, time * 0.2 * side, SPARK, 1);
                }
            }
            // shards: start flush on an invisible sphere (the world's surface), peel outward and spin
            for (int i = 0; i < shardPos.length; i++) {
                Vec3 home = c.add(shardPos[i]);
                Vec3 pos = home.add(shardPos[i].normalize().scale(k * 3)).add(0, k * Math.sin(i + time * 0.1) * 1.5, 0);
                double rot = k * 6 + time * 0.05 * (i % 3 + 1);
                Vec3 ax = shardAxis[i];
                Vec3 u = FxDraw.perp(ax), w = ax.cross(u).normalize();
                double s = 0.6 + (i % 4) * 0.3;
                Vec3 p0 = FxDraw.onCircle(pos, u, w, s, rot), p1 = FxDraw.onCircle(pos, u, w, s * 0.8, rot + 2.1), p2 = FxDraw.onCircle(pos, u, w, s * 1.1, rot + 4.0);
                float al = Mth.clamp(k * 1.5f, 0, 1);
                d.tri(p0, p1, p2, argb(0xE8E0FF, 0.35f * al), argb(TINT, 0.2f * al), argb(0x80C0FF, 0.3f * al));
                d.ribbon(p0, p1, 0.04, argb(0xFFFFFF, al), argb(0xFFFFFF, al));
                d.ribbon(p1, p2, 0.04, argb(0xFFFFFF, al), argb(0xFFFFFF, al));
            }
            // kaleidoscope rings tilting around the caster
            for (int i = 0; i < 4; i++) {
                Vec3 axis = new Vec3(Math.sin(time * 0.04 + i * 1.6) * k, 1, Math.cos(time * 0.05 + i) * k).normalize();
                d.ring(c, axis, 2 + i * 1.3, 0.05, i % 2 == 0 ? TINT : SPARK, 0.8f * k);
            }
            d.sphere(c, 1.5 + k * 6, TINT, 0.0f, 0.4f * k, 14, 24);
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            float k = progress(pt);
            if (!watching && falloff(origin, 12) <= 0) {
                return;
            }
            int w = g.guiWidth(), h = g.guiHeight();
            float a = time(pt);
            // glass cracks spreading from the centre
            float reach = Mth.clamp(k * 1.3f, 0, 1);
            for (List<float[]> line : cracks) {
                int segs = (int) ((line.size() - 1) * reach);
                for (int i = 0; i < segs; i++) {
                    float[] p0 = line.get(i), p1 = line.get(i + 1);
                    drawLine(g, p0[0] * w, p0[1] * h, p1[0] * w, p1[1] * h, argb(0xFFFFFF, 0.85f), argb(TINT, 0.5f));
                }
            }
            // chromatic purple edges
            g.fillGradient(0, 0, w, h / 4, argb(TINT, 0.35f * k), 0);
            g.fillGradient(0, h * 3 / 4, w, h, 0, argb(TINT, 0.35f * k));
            if (watching) {
                SceneManager.title(g, Component.translatable("ability.infinitemultiverse.mirror_dimension"), null, TINT,
                        Mth.clamp(Math.min((a - 6) / 4, (duration - a) / 6), 0, 1), 3f, 0.82f);
            }
        }

        private static void drawLine(GuiGraphics g, float x0, float y0, float x1, float y1, int core, int glow) {
            int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            for (int i = 0; i <= steps; i++) {
                float t = steps == 0 ? 0 : i / (float) steps;
                int x = (int) Mth.lerp(t, x0, x1), y = (int) Mth.lerp(t, y0, y1);
                g.fill(x - 1, y - 1, x + 2, y + 2, glow);
                g.fill(x, y, x + 1, y + 1, core);
            }
        }

        @Override
        public float flash(float pt) {
            float a = time(pt) - SHATTER;
            return a >= 0 && a < 6 && (localIsCaster() || falloff(origin, 12) > 0) ? (1 - a / 6) * 0.9f : 0;
        }

        @Override
        public float shake(float pt) {
            float a = time(pt);
            return a > SHATTER - 8 && a < SHATTER + 3 ? 1.4f * Math.max(falloff(origin, 14), localIsCaster() ? 1 : 0) : 0;
        }

        @Override
        public float fov(float pt) {
            if (!localIsCaster() && falloff(origin, 12) <= 0) {
                return 1;
            }
            float a = time(pt) - SHATTER;
            return a > -10 && a < 0 ? 1 - 0.015f * (a + 10) : a >= 0 && a < 8 ? 1.2f - a * 0.025f : 1;
        }
    }

    /**
     * Inside the Mirror Dimension: the sky folds into slowly turning kaleidoscope rings, giant mirror panels hang
     * around the copied region like cathedral glass, and geometric fractures drift through the air.
     */
    static final class MirrorWorld extends Scene {
        static final int TINT = 0xB070FF;

        MirrorWorld(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            for (int i = 0; i < s.count(5); i++) {
                Vec3 at = origin.add(s.jitter(14)).add(0, 4, 0);
                s.mote(at, new Vec3(0, 0.01, 0), 0xFFFFFF, i % 2 == 0 ? TINT : SPARK, 0.12f, 50, 0.99f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 15, 15);
            double time = time(pt);
            Vec3 sky = origin.add(0, 24, 0);
            for (int i = 0; i < 6; i++) {
                Vec3 axis = new Vec3(Math.sin(time * 0.006 + i), 3, Math.cos(time * 0.005 + i * 2)).normalize();
                d.ring(sky, axis, 8 + i * 6, 0.25 + i * 0.05, i % 2 == 0 ? TINT : SPARK, 0.45f * f, 64, time * 0.003 * (i + 1), Math.PI * 1.7);
                d.star(sky, axis, 6 + i * 6, 0.82, 12, -time * 0.002 * (i + 1), 0.12, 0xE0D0FF, 0.25f * f);
            }
            // mirror panels around the region
            for (int i = 0; i < 12; i++) {
                double ang = Math.PI * 2 * i / 12 + time * 0.002;
                Vec3 base = origin.add(Math.cos(ang) * 18, -2 + Math.sin(time * 0.02 + i) * 1.5, Math.sin(ang) * 18);
                Vec3 inward = origin.subtract(base).multiply(1, 0, 1).normalize();
                Vec3 side = FxDraw.perp(inward);
                Vec3 tilt = new Vec3(0, 1, 0).add(inward.scale(Math.sin(time * 0.01 + i) * 0.3)).normalize();
                Vec3 a0 = base.add(side.scale(-3)), a1 = base.add(side.scale(3)), b1 = a1.add(tilt.scale(14)), b0 = a0.add(tilt.scale(14));
                d.quad(a0, a1, b1, b0, argb(0x8070C0, 0.12f * f), argb(0x8070C0, 0.12f * f), argb(0xC0B0FF, 0.25f * f), argb(0xC0B0FF, 0.25f * f));
                d.ribbon(a0, b0, 0.15, argb(0xFFFFFF, 0.6f * f), argb(TINT, 0.6f * f));
                d.ribbon(a1, b1, 0.15, argb(0xFFFFFF, 0.6f * f), argb(TINT, 0.6f * f));
                d.ribbon(b0, b1, 0.15, argb(TINT, 0.6f * f), argb(TINT, 0.6f * f));
            }
            // drifting fractures
            RandomSource r = RandomSource.create(seed);
            for (int i = 0; i < 24; i++) {
                Vec3 c = origin.add((r.nextDouble() - 0.5) * 28, 2 + r.nextDouble() * 10 + Math.sin(time * 0.03 + i) * 0.8, (r.nextDouble() - 0.5) * 28);
                Vec3 ax = new Vec3(Math.sin(time * 0.02 + i), Math.cos(time * 0.015 + i), Math.sin(i)).normalize();
                d.polygon(c, ax, 0.6 + (i % 3) * 0.4, 3 + i % 4, time * 0.02, 0.05, i % 2 == 0 ? TINT : SPARK, 0.6f * f);
            }
            d.ring(origin.add(0, 0.05, 0), UP, 12, 0.3, TINT, 0.35f * f);
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.position().distanceTo(origin) > 40) {
                return;
            }
            int w = g.guiWidth(), h = g.guiHeight();
            float f = envelope(pt, 15, 15) * (0.22f + 0.06f * (float) Math.sin(time(pt) * 0.05));
            g.fillGradient(0, 0, w, h / 3, argb(TINT, f), 0);
            g.fillGradient(0, h * 2 / 3, w, h, 0, argb(0x4060FF, f));
        }
    }
}
