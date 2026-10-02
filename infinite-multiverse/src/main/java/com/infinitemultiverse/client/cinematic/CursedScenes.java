package com.infinitemultiverse.client.cinematic;

import static com.infinitemultiverse.client.cinematic.FxDraw.argb;
import static com.infinitemultiverse.client.cinematic.FxDraw.lerp;
import static com.infinitemultiverse.client.cinematic.GenericScenes.UP;

import com.infinitemultiverse.client.vfx.VfxSpawner;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.network.ScenePayload;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Jujutsu scenes: Domain Expansion cutscenes and interiors, Hollow Purple, Blue, Red, Black Flash, Fuga, shikigami... */
final class CursedScenes {
    private CursedScenes() {
    }

    static void register(Map<ResourceLocation, Function<ScenePayload, Scene>> m) {
        GenericScenes.register(m);
        m.put(SceneIds.CHARGE, Charge::new);
        m.put(SceneIds.DOMAIN_CUTSCENE, DomainCutscene::new);
        m.put(SceneIds.UNLIMITED_VOID, UnlimitedVoid::new);
        m.put(SceneIds.MALEVOLENT_SHRINE, MalevolentShrine::new);
        m.put(SceneIds.CHIMERA_GARDEN, ChimeraGarden::new);
        m.put(SceneIds.HOLLOW_PURPLE, HollowPurple::new);
        m.put(SceneIds.BLUE, Blue::new);
        m.put(SceneIds.RED, Red::new);
        m.put(SceneIds.BLACK_FLASH, BlackFlash::new);
        m.put(SceneIds.DISMANTLE, Dismantle::new);
        m.put(SceneIds.CLEAVE, Cleave::new);
        m.put(SceneIds.WORLD_SLASH, WorldSlash::new);
        m.put(SceneIds.FUGA, Fuga::new);
        m.put(SceneIds.SHADOW_SUMMON, ShadowSummon::new);
        m.put(SceneIds.NUE, Nue::new);
        m.put(SceneIds.SPIRIT_ABSORB, p -> new Spirit(p, true));
        m.put(SceneIds.SPIRIT_RELEASE, p -> new Spirit(p, false));
        m.put(SceneIds.UZUMAKI, Uzumaki::new);
        m.put(SceneIds.SUPERNOVA, p -> new GenericScenes.Wave(p, GenericScenes.WaveStyle.BLOOD));
        m.put(SceneIds.TWIST, Twist::new);
    }

    static Vec3 hand(Entity e, float pt, double side) {
        Vec3 look = e.getViewVector(pt);
        Vec3 right = FxDraw.perp(look);
        return e.getEyePosition(pt).add(look.scale(0.7)).add(right.scale(-side * 0.35)).subtract(0, 0.35, 0);
    }

    // =================================================================== charge

    /** Converging motes and a growing orb at the caster's hand. */
    static final class Charge extends Scene {
        Charge(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            Vec3 h = hand(e, 0, 0);
            for (int i = 0; i < s.count(6); i++) {
                Vec3 from = h.add(s.randomUnit().scale(1.8));
                s.spark(from, h.subtract(from).scale(0.18), 0xFFFFFF, color, 0.12f, 6, 1f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            Entity e = entity();
            if (e == null) {
                return;
            }
            float t = t(pt);
            Vec3 h = hand(e, pt, 0);
            double r = Math.max(0.2, param) * (0.3 + t);
            d.orb(h, r, 0xFFFFFF, color, 0.9f);
            d.ring(h, e.getViewVector(pt), r * 2.4 * (1 - t) + r, 0.05, color, t);
        }

        @Override
        public float fov(float pt) {
            return localIsCaster() ? 1 - 0.08f * t(pt) : 1;
        }
    }

    // =================================================================== domains

    enum Domain {
        UNLIMITED_VOID("無量空処", "domain.infinitemultiverse.unlimited_void", 0x3D7BFF, 0xFFFFFF),
        MALEVOLENT_SHRINE("伏魔御廚子", "domain.infinitemultiverse.malevolent_shrine", 0xD0102A, 0xFF8090),
        CHIMERA_SHADOW_GARDEN("嵌合暗翳庭", "domain.infinitemultiverse.chimera_shadow_garden", 0x2A2A46, 0x9090C0);

        final String kanji;
        final String key;
        final int color;
        final int accent;

        Domain(String kanji, String key, int color, int accent) {
            this.kanji = kanji;
            this.key = key;
            this.color = color;
            this.accent = accent;
        }
    }

    /**
     * Domain Expansion cutscene (70 ticks): close-up on the caster with the "領域展開" card, a slow orbit while cursed energy
     * gathers, then a crane pull-back as the barrier sphere swallows the area and the domain's name is declared.
     */
    static final class DomainCutscene extends Scene {
        static final int OPEN_AT = 46;
        private final Domain domain;
        private final float baseYaw;

        DomainCutscene(ScenePayload p) {
            super(p);
            this.domain = Domain.values()[Mth.clamp(p.flags() >> 4, 0, 2)];
            this.baseYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            float a = time(pt);
            Entity e = entity();
            Vec3 c = e != null ? e.getPosition(pt) : origin;
            Vec3 eye = c.add(0, 1.62, 0);
            if (a < 18) {
                double push = 2.2 - a / 18 * 0.7;
                return CinematicCamera.Shot.orbit(c, baseYaw, 8 + a * 0.4f, push, 1.45, eye.subtract(0, 0.05, 0));
            }
            if (a < 40) {
                float k = (a - 18) / 22f;
                return CinematicCamera.Shot.orbit(c, baseYaw, 20 + k * 130, 3.5 + k, 1.0 + k * 0.6, eye.subtract(0, 0.4, 0));
            }
            float k = Mth.clamp((a - 40) / 26f, 0, 1);
            float e2 = 1 - (1 - k) * (1 - k);
            return CinematicCamera.Shot.orbit(c, baseYaw, 150 + k * 60, 4.5 + e2 * param * 1.4, 1.6 + e2 * param * 0.9, c.add(0, 1, 0));
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age < OPEN_AT) {
                for (int i = 0; i < s.count(8); i++) {
                    Vec3 from = origin.add(s.randomUnit().multiply(4, 0.5, 4)).add(0, 0.2, 0);
                    s.mote(from, origin.add(0, 1.2, 0).subtract(from).scale(0.06), domain.accent, domain.color, 0.25f, 16, 1f);
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            Vec3 c = origin.add(0, 1, 0);
            // gathering: dark coils rising around the caster
            if (a < OPEN_AT + 6) {
                float g = Mth.clamp(a / OPEN_AT, 0, 1);
                d.ink();
                for (int i = 0; i < 6; i++) {
                    double base = Math.PI * 2 * i / 6 + a * 0.08;
                    Vec3 prev = origin;
                    for (int k = 1; k <= 10; k++) {
                        double s = k / 10.0;
                        Vec3 q = origin.add(Math.cos(base + s * 3) * (1.5 - s), s * 3.2 * g, Math.sin(base + s * 3) * (1.5 - s));
                        d.ribbon(prev, q, 0.35 * (1 - s), argb(0x05050A, 0.85f * g), argb(0x05050A, 0.6f * g));
                        prev = q;
                    }
                }
                d.glow();
                d.ring(origin.add(0, 0.05, 0), UP, 1.4 + g, 0.12, domain.color, g);
                d.flare(c, 1.2 + g, domain.color, 0.4f * g);
            }
            // expansion: the barrier swallows the area
            if (a >= OPEN_AT - 4) {
                float k = Mth.clamp((a - (OPEN_AT - 4)) / 14f, 0, 1);
                float ease = 1 - (1 - k) * (1 - k) * (1 - k);
                double r = Math.max(0.5, param * ease);
                float out = 1 - Mth.clamp((a - (duration - 8)) / 8f, 0, 1);
                d.ink();
                d.sphere(c, r, domain == Domain.MALEVOLENT_SHRINE ? 0x200004 : 0x020208, 0.75f * out, 0.95f * out, 18, 32);
                d.glow();
                d.sphere(c, r * 1.01, domain.accent, 0.0f, 0.8f * out, 18, 32);
                d.ring(c, UP, r, 0.3, domain.accent, out);
                for (int i = 0; i < 3; i++) {
                    d.ring(c, new Vec3(Math.sin(i * 2.1), 0.3, Math.cos(i * 2.1)).normalize(), r * 1.02, 0.15, domain.color, 0.7f * out);
                }
            }
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (!watching) {
                return;
            }
            float a = time(pt);
            float first = Mth.clamp(Math.min((a - 4) / 4, (20 - a) / 4), 0, 1);
            SceneManager.title(g, Component.literal("領域展開"), Component.literal("D O M A I N   E X P A N S I O N"), 0xFFFFFF, first, 4.2f, 0.42f);
            float second = Mth.clamp(Math.min((a - OPEN_AT) / 4, (duration - 2 - a) / 5), 0, 1);
            SceneManager.title(g, Component.literal(domain.kanji), Component.translatable(domain.key).withStyle(ChatFormatting.BOLD), domain.accent, second, 3.6f, 0.4f);
        }

        @Override
        public float flash(float pt) {
            float a = time(pt) - OPEN_AT - 6;
            return a >= 0 && a < 5 ? (1 - a / 5) * 0.8f * falloff(origin, param * 3) : 0;
        }

        @Override
        public float shake(float pt) {
            float a = time(pt);
            return a > OPEN_AT && a < OPEN_AT + 14 ? 1.6f * falloff(origin, param * 2.5) : 0;
        }
    }

    /** Inside Unlimited Void: an infinite starfield, a turning galaxy and streams of raw information. */
    static final class UnlimitedVoid extends Scene {
        private final Vec3[] stars = new Vec3[260];

        UnlimitedVoid(ScenePayload p) {
            super(p);
            RandomSource r = RandomSource.create(seed);
            for (int i = 0; i < stars.length; i++) {
                double th = r.nextDouble() * Math.PI * 2, y = r.nextDouble() * 2 - 1, rr = Math.sqrt(1 - y * y);
                stars[i] = new Vec3(rr * Math.cos(th), y, rr * Math.sin(th)).scale(param * (0.35 + r.nextDouble() * 0.45));
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 10, 12);
            double time = time(pt);
            Vec3 c = origin.add(0, 1, 0);
            d.ink();
            // Kept well inside the barrier blocks (whose inner faces sit ~1 block in) so the void hides them completely.
            d.sphere(c, param * 0.84, 0x000003, 0.97f * f, 0.99f * f, 18, 32);
            d.glow();
            for (int i = 0; i < stars.length; i++) {
                double tw = 0.5 + 0.5 * Math.sin(time * 0.15 + i * 1.7);
                d.flare(c.add(stars[i]), 0.08 + (i % 7) * 0.025, i % 5 == 0 ? 0x9FC8FF : 0xFFFFFF, (float) (0.5 + 0.5 * tw) * f);
            }
            // galaxy disc overhead
            Vec3 gc = c.add(0, param * 0.45, 0);
            Vec3 axis = new Vec3(0.25, 1, 0.1).normalize();
            Vec3 u = FxDraw.perp(axis), w = axis.cross(u).normalize();
            for (int arm = 0; arm < 3; arm++) {
                Vec3 prev = gc;
                for (int k = 1; k < 40; k++) {
                    double s = k / 40.0;
                    double ang = arm * Math.PI * 2 / 3 + s * 5 + time * 0.01;
                    Vec3 q = FxDraw.onCircle(gc, u, w, s * param * 0.45, ang);
                    d.ribbon(prev, q, 0.9 * (1 - s) + 0.1, argb(lerp(0xFFFFFF, 0x6A7BFF, (float) s), 0.35f * f), argb(lerp(0xFFFFFF, 0x6A7BFF, (float) s), 0.25f * f));
                    prev = q;
                }
            }
            d.flare(gc, param * 0.12, 0xFFFFFF, 0.8f * f);
            // information streams rushing inward
            RandomSource r = RandomSource.create(seed + age / 3);
            for (int i = 0; i < 26; i++) {
                Vec3 from = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.3, r.nextDouble() - 0.5).normalize().scale(param * 0.8);
                double phase = ((time * 0.06 + i * 0.13) % 1.0);
                Vec3 head = c.add(from.scale(1 - phase));
                Vec3 tail = c.add(from.scale(Math.min(1, 1.15 - phase)));
                d.ribbon(tail, head, 0.06, argb(0xFFFFFF, 0), argb(0xFFFFFF, 0.8f * f));
            }
            d.ring(origin.add(0, 0.05, 0), UP, param * 0.8, 0.4, 0x8FB0FF, 0.5f * f);
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || localIsCaster() || mc.player.position().distanceTo(origin) > param) {
                return;
            }
            // The victim's mind drowns in infinite information.
            float f = envelope(pt, 10, 10);
            int w = g.guiWidth(), h = g.guiHeight();
            RandomSource r = RandomSource.create(age * 31L);
            g.fill(0, 0, w, h, argb(0xFFFFFF, 0.18f * f));
            for (int i = 0; i < 40; i++) {
                int y = r.nextInt(h), x = r.nextInt(w), len = 20 + r.nextInt(w / 2);
                g.fill(x, y, Math.min(w, x + len), y + 1, argb(0xFFFFFF, 0.5f * f));
            }
            String glyphs = "無量空処情報∞01ΣΔ◇";
            for (int i = 0; i < 18; i++) {
                int x = r.nextInt(w), y = r.nextInt(h);
                String s = String.valueOf(glyphs.charAt(r.nextInt(glyphs.length())));
                g.drawString(mc.font, s, x, y, argb(0x3050FF, 0.8f * f), false);
            }
        }

        @Override
        public float fov(float pt) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || localIsCaster() || mc.player.position().distanceTo(origin) > param) {
                return 1;
            }
            return 1 + 0.05f * (float) Math.sin(time(pt) * 0.3);
        }
    }

    /** Malevolent Shrine: a looming shrine of bone and shadow behind its master; the air is filled with endless cuts. */
    static final class MalevolentShrine extends Scene {
        MalevolentShrine(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            for (int i = 0; i < s.count(6); i++) {
                Vec3 pnt = origin.add(s.jitter(param * 0.9)).add(0, 1 + s.rand() * 2, 0);
                s.spark(pnt, s.randomUnit().scale(0.4), 0xFFFFFF, 0xD0102A, 0.1f, 4, 1f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 14, 12);
            float rise = Mth.clamp(time(pt) / 16f, 0, 1);
            Vec3 back = new Vec3(dir.x, 0, dir.z).normalize().scale(-5);
            Vec3 base = origin.add(back).subtract(0, (1 - rise) * 9, 0);
            shrine(d, base, new Vec3(dir.x, 0, dir.z).normalize(), f);
            // endless slashes
            RandomSource r = RandomSource.create(seed + age);
            for (int i = 0; i < 22; i++) {
                Vec3 c = origin.add((r.nextDouble() - 0.5) * param * 1.8, 0.3 + r.nextDouble() * 3, (r.nextDouble() - 0.5) * param * 1.8);
                Vec3 v = new Vec3(r.nextDouble() - 0.5, (r.nextDouble() - 0.5) * 0.6, r.nextDouble() - 0.5).normalize().scale(1 + r.nextDouble() * 2.5);
                d.ribbon(c.subtract(v), c.add(v), 0.05, argb(0xFFFFFF, 0.9f * f), argb(0xFF4050, 0.6f * f));
            }
            d.disc(origin.add(0, 0.04, 0), UP, 0, param, argb(0x400008, 0.0f), argb(0xD0102A, 0.25f * f), 40);
            d.ring(origin.add(0, 0.05, 0), UP, param, 0.35, 0xD0102A, f);
        }

        static void shrine(FxDraw d, Vec3 base, Vec3 fwd, float f) {
            Vec3 side = FxDraw.perp(fwd);
            int wood = 0x1A0306, bone = 0xE8DCC0;
            d.ink();
            // platform
            box(d, base, fwd, side, 3.2, 0.6, 2.4, 0, argb(wood, 0.95f * f));
            // pillars
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    box(d, base.add(side.scale(sx * 2.6)).add(fwd.scale(sz * 1.8)), fwd, side, 0.25, 4.2, 0.25, 0.6, argb(wood, 0.95f * f));
                }
            }
            // two curved roof tiers
            for (int tier = 0; tier < 2; tier++) {
                double y = 4.8 + tier * 1.6, w = 4.2 - tier * 1.1, depth = 3.0 - tier * 0.7;
                int seg = 12;
                for (int i = 0; i < seg; i++) {
                    double s0 = -1 + 2.0 * i / seg, s1 = -1 + 2.0 * (i + 1) / seg;
                    double c0 = s0 * s0 * 0.9, c1 = s1 * s1 * 0.9;
                    Vec3 a0 = base.add(side.scale(s0 * w)).add(0, y + c0, 0), a1 = base.add(side.scale(s1 * w)).add(0, y + c1, 0);
                    Vec3 top = base.add(0, y + 1.4, 0);
                    d.quad(a0.add(fwd.scale(depth)), a1.add(fwd.scale(depth)), top.add(side.scale(s1 * w * 0.2)), top.add(side.scale(s0 * w * 0.2)), argb(0x0A0103, 0.97f * f));
                    d.quad(a0.subtract(fwd.scale(depth)), a1.subtract(fwd.scale(depth)), top.add(side.scale(s1 * w * 0.2)), top.add(side.scale(s0 * w * 0.2)), argb(0x0A0103, 0.97f * f));
                }
            }
            // a gaping mouth of fangs under the eaves
            d.glow();
            for (int i = 0; i < 9; i++) {
                double s = -1 + 2.0 * i / 8;
                Vec3 top = base.add(side.scale(s * 2.2)).add(fwd.scale(2.0)).add(0, 4.4, 0);
                d.tri(top.add(side.scale(-0.15)), top.add(side.scale(0.15)), top.add(0, -0.7 - (i % 2) * 0.3, 0), argb(bone, 0.8f * f), argb(bone, 0.8f * f), argb(bone, 0.3f * f));
            }
            d.flare(base.add(fwd.scale(2)).add(0, 3, 0), 3.5, 0xD0102A, 0.45f * f);
            d.ring(base.add(fwd.scale(2.05)).add(0, 3, 0), fwd, 1.2, 0.06, 0xFF2030, 0.9f * f);
        }

        static void box(FxDraw d, Vec3 c, Vec3 fwd, Vec3 side, double hx, double h, double hz, double yOff, int col) {
            Vec3 b = c.add(0, yOff, 0);
            Vec3[] p = new Vec3[8];
            for (int i = 0; i < 8; i++) {
                p[i] = b.add(side.scale((i & 1) == 0 ? -hx : hx)).add(fwd.scale((i & 2) == 0 ? -hz : hz)).add(0, (i & 4) == 0 ? 0 : h, 0);
            }
            int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
            for (int[] q : faces) {
                d.quad(p[q[0]], p[q[1]], p[q[2]], p[q[3]], col);
            }
        }
    }

    /** Chimera Shadow Garden: a glossy lake of liquid shadow with shikigami silhouettes rising from it. */
    static final class ChimeraGarden extends Scene {
        ChimeraGarden(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            for (int i = 0; i < s.count(4); i++) {
                s.mote(origin.add(s.jitter(param * 0.8).multiply(1, 0, 1)).add(0, 0.1, 0), new Vec3(0, 0.05, 0), 0x40405A, 0x08080C, 0.4f, 30, 0.97f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 14, 12);
            double time = time(pt);
            Vec3 floor = origin.add(0, 0.06, 0);
            d.ink();
            d.disc(floor, UP, 0, param, argb(0x020204, 0.95f * f), argb(0x020204, 0.7f * f), 48);
            for (int i = 0; i < 9; i++) {
                double a = i * 2.39996, rr = param * (0.2 + (i % 4) * 0.2);
                Vec3 b = floor.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                Vec3 prev = b;
                double hgt = 2 + (i % 3) * 1.2;
                for (int k = 1; k <= 8; k++) {
                    double s = k / 8.0;
                    Vec3 q = b.add(Math.sin(time * 0.1 + i + s * 4) * 0.4 * s, s * hgt * Math.min(1, time / 20), Math.cos(time * 0.1 + i + s * 3) * 0.4 * s);
                    d.ribbon(prev, q, 0.5 * (1 - s) + 0.05, argb(0x050508, 0.9f * f), argb(0x050508, 0.7f * f));
                    prev = q;
                }
                d.glow();
                d.flare(prev, 0.12, 0xE0E0FF, f);
                d.ink();
            }
            d.glow();
            for (int i = 0; i < 4; i++) {
                double rr = param * ((time * 0.01 + i * 0.25) % 1.0);
                d.ring(floor, UP, rr, 0.08, 0x6A6A9A, 0.35f * f * (float) (1 - rr / param));
            }
            d.ring(floor, UP, param, 0.3, 0x9090C0, 0.6f * f);
        }
    }

    // =================================================================== Gojo

    /**
     * Hollow Technique: Purple (80 ticks). Blue gathers at one hand, Red at the other, they spiral into each other,
     * collide in a white flash, and the imaginary mass is fired — erasing everything along the line (param = length).
     */
    static final class HollowPurple extends Scene {
        static final int MERGE = 46;
        static final int LAUNCH = 52;
        static final int TRAVEL = 22;
        private final float baseYaw;

        HollowPurple(ScenePayload p) {
            super(p);
            this.baseYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        private Vec3 front() {
            return origin.add(dir.normalize().scale(1.3)).subtract(0, 0.15, 0);
        }

        private double radius() {
            return empowered() ? 3.4 : 2.2;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            float a = time(pt);
            Vec3 body = origin.subtract(0, 1.62, 0);
            if (a < MERGE) {
                float k = a / MERGE;
                return CinematicCamera.Shot.orbit(body, baseYaw, 65 - k * 20, 3.6 - k * 1.2, 1.5, front());
            }
            if (a < LAUNCH + 4) {
                return CinematicCamera.Shot.orbit(body, baseYaw, 30, 2.6, 1.7, front());
            }
            float k = Mth.clamp((a - LAUNCH - 4) / TRAVEL, 0, 1);
            Vec3 over = origin.subtract(dir.normalize().scale(3.2)).add(FxDraw.perp(dir.normalize()).scale(-1.1)).add(0, 0.6, 0);
            return CinematicCamera.Shot.looking(over.add(dir.normalize().scale(k * 4)), sphereAt(a));
        }

        private Vec3 sphereAt(float a) {
            float k = Mth.clamp((a - LAUNCH) / TRAVEL, 0, 1);
            return front().add(dir.normalize().scale(param * k));
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age > LAUNCH && age < LAUNCH + TRAVEL) {
                Vec3 at = sphereAt(age);
                for (int i = 0; i < s.count(30); i++) {
                    s.mote(at.add(s.randomUnit().scale(radius())), s.randomUnit().scale(0.3), 0xFFFFFF, 0xA040FF, 0.3f, 16, 0.9f);
                    s.spark(at.add(s.randomUnit().scale(radius() * 1.2)), s.randomUnit().scale(0.6), 0xE0C0FF, 0x5010A0, 0.2f, 10, 0.85f);
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            Vec3 f = front();
            Vec3 n = dir.normalize(), side = FxDraw.perp(n);
            if (a < MERGE + 1) {
                float blueIn = Mth.clamp((a - 4) / 8, 0, 1), redIn = Mth.clamp((a - 12) / 8, 0, 1);
                float k = Mth.clamp((a - 26) / (MERGE - 26), 0, 1);
                double spread = 1.3 * (1 - k * k);
                double ang = k * k * Math.PI * 3;
                Vec3 off = side.scale(Math.cos(ang) * spread).add(0, Math.sin(ang) * spread * 0.6, 0);
                Vec3 blue = f.add(off), red = f.subtract(off);
                if (blueIn > 0) {
                    blueOrb(d, blue, 0.45 * blueIn, a);
                }
                if (redIn > 0) {
                    d.orb(red, 0.45 * redIn, 0xFFE0E0, 0xFF1A2A, redIn);
                    d.ring(red, n, 0.7 + Math.sin(a * 0.5) * 0.1, 0.05, 0xFF3040, redIn);
                }
                if (k > 0.3) {
                    d.bolt(blue, red, seed + age, 0.5, 0.04, 0xC080FF, k, 1);
                }
            }
            if (a >= MERGE && a < LAUNCH + TRAVEL + 6) {
                float form = Mth.clamp((a - MERGE) / 6, 0, 1);
                float grow = Mth.clamp((a - LAUNCH) / 6, 0, 1);
                Vec3 at = sphereAt(a);
                // Compact between the hands, swelling to full size only once it is away from the camera.
                double r = radius() * form * (0.35 + 0.65 * grow);
                float fade = 1 - Mth.clamp((a - LAUNCH - TRAVEL) / 6, 0, 1);
                // An imaginary mass: a black core, a violet rim, and a halo that never washes over the core.
                d.ink();
                d.sphere(at, r * 0.82, 0x05000A, 0.96f * fade, 0.96f * fade, 14, 24);
                d.glow();
                d.sphere(at, r, 0xA040FF, 0.0f, 1.0f * fade, 16, 28);
                d.sphere(at, r * 1.18, 0x6020D0, 0.0f, 0.55f * fade, 12, 20);
                Vec3 view = d.camera().subtract(at).normalize();
                d.disc(at, view, r * 0.95, r * 2.6, argb(0xC080FF, 0.55f * fade), argb(0x6020D0, 0), 40);
                d.ring(at, view, r * 0.97, 0.08 * r, 0xFFFFFF, 0.9f * fade);
                for (int i = 0; i < 3; i++) {
                    d.ring(at, new Vec3(Math.sin(a * 0.2 + i * 2), Math.cos(a * 0.13 + i), Math.sin(i)).normalize(), r * 1.15, 0.08, 0xE0C0FF, 0.8f * fade);
                }
                if (a > LAUNCH) {
                    // the erased line left behind: a black scar with violet edges
                    d.ink();
                    d.ribbon(f, at, 0.35, argb(0x000000, 0.0f), argb(0x000000, 0.85f * fade));
                    d.glow();
                    d.ribbon(f, at, 0.9, argb(0x7020C0, 0.0f), argb(0xC080FF, 0.45f * fade));
                    d.helix(f, at, r * 0.5, at.distanceTo(f) * 0.15, 2, a * 0.6, 0.06, 0xE0C0FF, 0.6f * fade);
                }
            }
        }

        static void blueOrb(FxDraw d, Vec3 c, double r, float a) {
            d.ink();
            d.sphere(c, r * 0.6, 0x000818, 0.95f, 0.95f, 8, 14);
            d.glow();
            d.sphere(c, r, 0x2A6BFF, 0.05f, 0.9f, 10, 16);
            d.flare(c, r * 3, 0x3D7BFF, 0.6f);
            d.ring(c, new Vec3(0.2, 1, 0.3).normalize(), r * 1.8, 0.05, 0x9FD0FF, 0.9f);
            d.ring(c, new Vec3(Math.sin(a * 0.1), 0.4, Math.cos(a * 0.1)).normalize(), r * 1.4, 0.04, 0x3D7BFF, 0.8f);
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (!watching) {
                return;
            }
            float a = time(pt);
            SceneManager.title(g, Component.literal("術式順転「蒼」"), null, 0x6AA0FF, Mth.clamp(Math.min((a - 6) / 3, (22 - a) / 3), 0, 1), 2.4f, 0.25f);
            SceneManager.title(g, Component.literal("術式反転「赫」"), null, 0xFF5060, Mth.clamp(Math.min((a - 14) / 3, (30 - a) / 3), 0, 1), 2.4f, 0.75f);
            SceneManager.title(g, Component.literal("虚式「茈」"), Component.literal("H O L L O W   T E C H N I Q U E :   P U R P L E"), 0xC080FF,
                    Mth.clamp(Math.min((a - MERGE - 2) / 3, (LAUNCH + 16 - a) / 4), 0, 1), 4.0f, 0.4f);
        }

        @Override
        public float flash(float pt) {
            float a = time(pt) - MERGE;
            return a >= 0 && a < 6 ? (1 - a / 6) * falloff(origin, 40) : 0;
        }

        @Override
        public float shake(float pt) {
            float a = time(pt);
            if (a > MERGE && a < LAUNCH + TRAVEL) {
                return (empowered() ? 2.6f : 1.8f) * Math.max(falloff(origin, 30), falloff(sphereAt(a), 20));
            }
            return a > 26 && a < MERGE ? 0.4f * falloff(origin, 20) : 0;
        }

        @Override
        public float fov(float pt) {
            float a = time(pt) - LAUNCH;
            return a > 0 && a < 8 && localIsCaster() ? 1.12f - a * 0.015f : 1;
        }
    }

    /** Lapse: Blue — a collapsing point of attraction: dark core, lensing rim, accretion rings and spiralling streaks. */
    static final class Blue extends Scene {
        Blue(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            for (int i = 0; i < s.count(12); i++) {
                Vec3 from = origin.add(s.randomUnit().scale(param * (0.6 + s.rand() * 0.6)));
                Vec3 to = origin.subtract(from);
                s.spark(from, to.scale(0.12).add(to.cross(UP).normalize().scale(0.2)), 0xFFFFFF, 0x3D7BFF, 0.14f, 8, 1f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 4, 6);
            double time = time(pt);
            double core = (empowered() ? 1.4 : 0.8) * f;
            blueSwirl(d, origin, core, param, time, f);
        }

        static void blueSwirl(FxDraw d, Vec3 c, double core, double reach, double time, float f) {
            d.ink();
            d.sphere(c, core, 0x000510, 0.97f * f, 0.97f * f, 12, 20);
            d.glow();
            d.sphere(c, core * 1.15, 0x4A8BFF, 0.0f, 1.0f * f, 12, 20);
            d.flare(c, core * 4, 0x2A5BFF, 0.5f * f);
            for (int i = 0; i < 4; i++) {
                Vec3 axis = new Vec3(Math.sin(i * 1.3), 1.4, Math.cos(i * 1.7)).normalize();
                d.ring(c, axis, core * (1.8 + i * 0.6), 0.05 + 0.03 * i, i % 2 == 0 ? 0x9FD0FF : 0x3D7BFF, 0.8f * f, 48, time * (0.15 + i * 0.05), Math.PI * 1.6);
            }
            d.disc(c, new Vec3(0.15, 1, 0.1).normalize(), core * 1.3, core * 3.2, argb(0x9FD0FF, 0.5f * f), argb(0x2A5BFF, 0), 48);
            for (int i = 0; i < 14; i++) {
                double phase = (time * 0.05 + i / 14.0) % 1.0;
                double ang = i * 2.4 + phase * 4;
                double rr = reach * (1 - phase);
                Vec3 a = c.add(Math.cos(ang) * rr, Math.sin(i * 1.1) * rr * 0.4, Math.sin(ang) * rr);
                Vec3 b = c.add(Math.cos(ang + 0.5) * rr * 0.8, Math.sin(i * 1.1) * rr * 0.32, Math.sin(ang + 0.5) * rr * 0.8);
                d.ribbon(a, b, 0.07, argb(0x3D7BFF, 0), argb(0xCFE6FF, 0.9f * f));
            }
        }

        @Override
        public float shake(float pt) {
            return 0.25f * falloff(origin, param * 2);
        }
    }

    /** Reversal: Red — a violent repelling beam: crimson core, expanding shock rings and a blast at the end. */
    static final class Red extends Scene {
        Red(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age < 4) {
                Vec3 end = origin.add(dir);
                for (int i = 0; i < s.count(40); i++) {
                    s.spark(end, s.randomUnit().scale(0.6 + s.rand()), 0xFFFFFF, 0xFF1A2A, 0.25f, 14, 0.85f);
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float grow = Mth.clamp(time(pt) / 3f, 0, 1);
            float fade = 1 - Mth.clamp((time(pt) - 6) / (duration - 6), 0, 1);
            Vec3 n = dir.normalize();
            Vec3 b = origin.add(dir.scale(grow));
            double w = (empowered() ? 1.6 : 1.0) * Math.max(0.6, param);
            d.beam(origin, b, w * 0.6, 0xFF1A2A, fade);
            d.helix(origin, b, w * 1.1, dir.length() * 0.2, 2, time(pt) * 0.8, 0.12, 0xFF8090, fade * 0.8f);
            for (int i = 0; i < 6; i++) {
                double t = ((time(pt) * 0.12 + i / 6.0) % 1.0) * grow;
                d.ring(origin.add(dir.scale(t)), n, w * (1 + t * 3), 0.15, 0xFF3040, fade * (float) (1 - t));
            }
            d.orb(origin, w * 0.5, 0xFFFFFF, 0xFF1A2A, fade);
            if (grow >= 1) {
                double blast = w * 2 + time(pt) * 0.6;
                d.sphere(b, blast, 0xFF2030, 0.05f * fade, 0.7f * fade, 12, 20);
                d.flare(b, blast * 1.5, 0xFF6070, fade);
            }
        }

        @Override
        public float shake(float pt) {
            return age < 6 ? 1.6f * Math.max(falloff(origin, 16), falloff(origin.add(dir), 16)) : 0;
        }

        @Override
        public float flash(float pt) {
            return age < 2 ? 0.2f * falloff(origin.add(dir), 10) : 0;
        }
    }

    /** Black Flash: black lightning tearing out of the impact with a red corona, a hit-stop zoom and a hard flash. */
    static final class BlackFlash extends Scene {
        BlackFlash(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age == 0) {
                for (int i = 0; i < s.count(50); i++) {
                    s.spark(origin, s.randomUnit().scale(0.5 + s.rand()), 0xFF2040, 0x100004, 0.22f, 12, 0.85f);
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = 1 - t(pt);
            RandomSource r = RandomSource.create(seed);
            for (int i = 0; i < 9; i++) {
                Vec3 v = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.4, r.nextDouble() - 0.5).normalize().scale(2 + r.nextDouble() * 3);
                // a red corona under every black bolt
                d.glow();
                d.bolt(origin, origin.add(v), seed + i * 17L + age / 2, 1.0, 0.16, 0xFF0A20, f, 1);
                d.ink();
                d.bolt(origin, origin.add(v), seed + i * 17L + age / 2, 1.0, 0.07, 0x000000, f, 1);
            }
            d.glow();
            d.flare(origin, 2.5 * f, 0xFF0A20, f);
            d.ring(origin, d.camera().subtract(origin).normalize(), 0.5 + t(pt) * 3, 0.15, 0xFF2040, f);
        }

        @Override
        public float flash(float pt) {
            return age < 2 ? 0.55f * falloff(origin, 12) : 0;
        }

        @Override
        public float shake(float pt) {
            return age < 6 ? 2.2f * falloff(origin, 16) : 0;
        }

        @Override
        public float fov(float pt) {
            return age < 5 ? 1 - 0.12f * falloff(origin, 8) * (1 - t(pt)) : 1;
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            float f = falloff(origin, 10) * (1 - t(pt));
            if (f > 0.05f) {
                int w = g.guiWidth(), h = g.guiHeight();
                g.fillGradient(0, 0, w, h / 3, argb(0x000000, 0.6f * f), 0);
                g.fillGradient(0, h * 2 / 3, w, h, 0, argb(0x000000, 0.6f * f));
                g.fill(0, 0, w, h, argb(0x600010, 0.18f * f));
                SceneManager.title(g, Component.literal("黒閃"), null, 0xFF2040, f, 3f, 0.3f);
            }
        }
    }

    // =================================================================== Sukuna

    /** Dismantle: an invisible-fast crescent of cutting force flying along the line. */
    static final class Dismantle extends Scene {
        Dismantle(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float k = Mth.clamp(time(pt) / 5f, 0, 1);
            float f = 1 - Mth.clamp((time(pt) - 5) / 4f, 0, 1);
            Vec3 n = dir.normalize();
            Vec3 plane = n.cross(FxDraw.perp(n)).normalize();
            for (int i = 0; i < (empowered() ? 3 : 1); i++) {
                Vec3 c = origin.add(dir.scale(Math.max(0, k - i * 0.08)));
                Vec3 tilt = plane.add(FxDraw.perp(n).scale(0.3 * (i - 1))).normalize();
                d.crescent(c.subtract(n.scale(1.2)), n, tilt, 1.6, 0.18, Math.PI * 0.9, 0xFF3040, f);
                d.ribbon(origin, c, 0.04, argb(0xFF3040, 0), argb(0xFFFFFF, 0.5f * f));
            }
        }
    }

    /** Cleave: measuring lines lock onto the target, then a lattice of cuts lands at once. */
    static final class Cleave extends Scene {
        Cleave(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            Vec3 n = d.camera().subtract(origin).normalize();
            Vec3 u = FxDraw.perp(n), w = n.cross(u).normalize();
            double s = Math.max(1, param);
            if (a < 5) {
                float f = a / 5;
                d.ring(origin, n, s * (2 - f), 0.03, 0xFFFFFF, f);
                d.line(origin.subtract(u.scale(s * 2)), origin.add(u.scale(s * 2)), n, 0.02, 0xFF8090, f);
                d.line(origin.subtract(w.scale(s * 2)), origin.add(w.scale(s * 2)), n, 0.02, 0xFF8090, f);
                return;
            }
            float f = 1 - Mth.clamp((a - 5) / (duration - 5), 0, 1);
            for (int i = -2; i <= 2; i++) {
                Vec3 off = u.scale(i * s * 0.25);
                d.ribbon(origin.add(off).subtract(w.add(u.scale(0.3)).scale(s * 1.4)), origin.add(off).add(w.add(u.scale(0.3)).scale(s * 1.4)), 0.06, argb(0xFFFFFF, f), argb(0xFF2040, f));
                Vec3 off2 = w.scale(i * s * 0.25);
                d.ribbon(origin.add(off2).subtract(u.subtract(w.scale(0.3)).scale(s * 1.4)), origin.add(off2).add(u.subtract(w.scale(0.3)).scale(s * 1.4)), 0.06, argb(0xFFFFFF, f), argb(0xFF2040, f));
            }
            d.flare(origin, s * 1.5 * f, 0xFF2040, f);
        }

        @Override
        public float shake(float pt) {
            return age > 5 && age < 9 ? 0.8f * falloff(origin, 10) : 0;
        }
    }

    /** World-Cutting Slash (Shrine mastery V): a cut through space itself — a black seam haloed in white, splitting the screen. */
    static final class WorldSlash extends Scene {
        WorldSlash(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            float k = Mth.clamp(a / 4f, 0, 1);
            float f = 1 - Mth.clamp((a - 14) / (duration - 14f), 0, 1);
            Vec3 n = dir.normalize();
            Vec3 cut = FxDraw.perp(n).scale(0.35).add(0, 1, 0).normalize();
            double len = Math.max(20, param);
            for (int i = 0; i < 8; i++) {
                Vec3 c = origin.add(n.scale(len * i / 8.0 * k));
                Vec3 a0 = c.subtract(cut.scale(len * 0.35)), a1 = c.add(cut.scale(len * 0.35));
                d.glow();
                d.ribbon(a0, a1, 0.9, argb(0xFFFFFF, 0.5f * f), argb(0xFFE0E8, 0.5f * f));
                d.ink();
                d.ribbon(a0, a1, 0.18, argb(0x000000, f), argb(0x000000, f));
            }
            d.glow();
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            float f = falloff(origin, param) * (1 - t(pt));
            if (f < 0.05f) {
                return;
            }
            int w = g.guiWidth(), h = g.guiHeight();
            for (int i = 0; i < h; i += 2) {
                int x = (int) (w * 0.62 - i * 0.35);
                g.fill(x - 2, i, x + 2, i + 2, argb(0xFFFFFF, f));
                g.fill(x - 1, i, x + 1, i + 2, argb(0x000000, f));
            }
        }

        @Override
        public float flash(float pt) {
            return age < 3 ? 0.7f * falloff(origin, param) : 0;
        }

        @Override
        public float shake(float pt) {
            return age < 12 ? 3f * falloff(origin, param * 1.2) : 0;
        }
    }

    /**
     * Divine Flame: Fuga (60 ticks): "■ 開" — flame gathers into a bow in the caster's hands, the arrow is drawn and loosed,
     * flying along the line (param = length) and blooming into a pillar of fire.
     */
    static final class Fuga extends Scene {
        static final int RELEASE = 34;
        static final int FLIGHT = 8;
        private final float baseYaw;

        Fuga(ScenePayload p) {
            super(p);
            this.baseYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            float a = time(pt);
            Vec3 body = origin.subtract(0, 1.62, 0);
            if (a < RELEASE) {
                return CinematicCamera.Shot.orbit(body, baseYaw, 70 - a * 0.5f, 3.0, 1.4, origin.add(dir.normalize()));
            }
            float k = Mth.clamp((a - RELEASE) / (FLIGHT + 6f), 0, 1);
            return CinematicCamera.Shot.looking(origin.subtract(dir.normalize().scale(2.5)).add(0, 0.5, 0), origin.add(dir.scale(k)));
        }

        @Override
        public void tick(VfxSpawner s) {
            if (age >= RELEASE && age <= RELEASE + FLIGHT) {
                Vec3 at = origin.add(dir.scale((age - RELEASE) / (double) FLIGHT));
                for (int i = 0; i < s.count(20); i++) {
                    s.mote(at.add(s.jitter(0.4)), s.randomUnit().scale(0.08).add(0, 0.05, 0), 0xFFF0A0, 0xFF4A10, 0.3f, 18, 0.9f);
                }
            }
            if (age > RELEASE + FLIGHT && age < RELEASE + FLIGHT + 10) {
                Vec3 end = origin.add(dir);
                for (int i = 0; i < s.count(40); i++) {
                    s.mote(end.add(s.jitter(2)), new Vec3(0, 0.3 + s.rand() * 0.4, 0).add(s.jitter(0.2)), 0xFFF0A0, 0xFF3A00, 0.5f, 26, 0.92f);
                }
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            Vec3 n = dir.normalize(), side = FxDraw.perp(n), up = side.cross(n).normalize();
            Vec3 grip = origin.add(n.scale(0.9)).subtract(0, 0.25, 0);
            if (a < RELEASE + 2) {
                float form = Mth.clamp((a - 6) / 12, 0, 1);
                float draw = Mth.clamp((a - 18) / 12, 0, 1);
                // flame bow: an arc in the up/forward plane
                Vec3 prev = null;
                for (int i = 0; i <= 20; i++) {
                    double t = (i / 20.0 - 0.5) * form;
                    Vec3 q = grip.add(up.scale(t * 2.2)).add(n.scale(Math.cos(t * Math.PI) * 0.5 - 0.5 + 0.5 * form));
                    if (prev != null) {
                        d.ribbon(prev, q, 0.18, argb(0xFFF0A0, 0.95f), argb(0xFF6A10, 0.95f));
                        d.flame(q, 0.4, 0.2, a * 0.6 + i, 0xFFF0A0, 0xFF4A10, 0.6f);
                    }
                    prev = q;
                }
                Vec3 nock = grip.subtract(n.scale(0.9 * draw));
                d.ribbon(grip.add(up.scale(1.1 * form)), nock, 0.04, argb(0xFFFFFF, form), argb(0xFFFFFF, form));
                d.ribbon(grip.subtract(up.scale(1.1 * form)), nock, 0.04, argb(0xFFFFFF, form), argb(0xFFFFFF, form));
                if (draw > 0) {
                    d.beam(nock, grip.add(n.scale(0.6)), 0.08, 0xFF6A10, draw);
                    d.flare(grip.add(n.scale(0.6)), 0.5, 0xFFF0A0, draw);
                }
            }
            if (a >= RELEASE && a < RELEASE + FLIGHT + 2) {
                float k = Mth.clamp((a - RELEASE) / FLIGHT, 0, 1);
                Vec3 head = origin.add(dir.scale(k));
                d.beam(grip, head, 0.25, 0xFF5A10, 1);
                d.helix(grip, head, 0.5, dir.length() * 0.15 * k, 3, a, 0.1, 0xFFE080, 1);
                d.flare(head, 1.5, 0xFFF0A0, 1);
            }
            if (a >= RELEASE + FLIGHT) {
                float k = Mth.clamp((a - RELEASE - FLIGHT) / (duration - RELEASE - FLIGHT), 0, 1);
                float f = 1 - k;
                Vec3 end = origin.add(dir);
                double r = 2 + k * 6;
                d.sphere(end, r, 0xFF5A10, 0.15f * f, 0.8f * f, 12, 20);
                for (int i = 0; i < 16; i++) {
                    double ang = Math.PI * 2 * i / 16;
                    d.flame(end.add(Math.cos(ang) * r * 0.6, -1, Math.sin(ang) * r * 0.6), 4 + 6 * f, 1.4, a * 0.4 + i, 0xFFF0A0, 0xFF3A00, f);
                }
                d.flame(end.subtract(0, 1, 0), 14 * f + 2, 3, a * 0.3, 0xFFFFFF, 0xFF3A00, f);
                d.ring(end.subtract(0, 0.8, 0), UP, r * 1.4, 0.4, 0xFF6A10, f);
            }
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (watching) {
                float a = time(pt);
                SceneManager.title(g, Component.literal("■  開"), Component.literal("O P E N"), 0xFF8A2A, Mth.clamp(Math.min((a - 4) / 3, (RELEASE - a) / 4), 0, 1), 3.5f, 0.35f);
            }
        }

        @Override
        public float flash(float pt) {
            float a = time(pt) - RELEASE - FLIGHT;
            return a >= 0 && a < 5 ? (1 - a / 5) * 0.5f * falloff(origin.add(dir), 30) : 0;
        }

        @Override
        public float shake(float pt) {
            float a = time(pt) - RELEASE - FLIGHT;
            return a >= 0 && a < 14 ? 2.2f * Math.max(falloff(origin.add(dir), 28), falloff(origin, 10) * 0.6f) : 0;
        }
    }

    // =================================================================== Ten Shadows / spirits / speech

    /** A pool of liquid shadow opens and shikigami rise from it. */
    static final class ShadowSummon extends Scene {
        ShadowSummon(ScenePayload p) {
            super(p);
        }

        @Override
        public void tick(VfxSpawner s) {
            for (int i = 0; i < s.count(5); i++) {
                s.mote(origin.add(s.jitter(param).multiply(1, 0, 1)), new Vec3(0, 0.08 + s.rand() * 0.08, 0), 0x50506A, 0x050508, 0.35f, 20, 0.95f);
            }
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = envelope(pt, 5, 8);
            double r = Math.max(1, param) * Math.min(1, time(pt) / 6);
            Vec3 c = origin.add(0, 0.05, 0);
            d.ink();
            d.disc(c, UP, 0, r, argb(0x000000, 0.95f * f), argb(0x000000, 0.5f * f), 32);
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3 + time(pt) * 0.05;
                Vec3 b = c.add(Math.cos(ang) * r * 0.6, 0, Math.sin(ang) * r * 0.6);
                Vec3 prev = b;
                for (int k = 1; k < 7; k++) {
                    double s = k / 7.0;
                    Vec3 q = b.add(Math.sin(time(pt) * 0.3 + i + s * 5) * 0.25, s * 1.6 * f, Math.cos(time(pt) * 0.3 + i + s * 4) * 0.25);
                    d.ribbon(prev, q, 0.2 * (1 - s), argb(0x05050A, 0.9f * f), argb(0x05050A, 0.6f * f));
                    prev = q;
                }
            }
            d.glow();
            d.ring(c, UP, r, 0.08, 0x8080C0, 0.6f * f);
        }
    }

    /** Nue: a winged shadow dives from above and strikes in a column of lightning. */
    static final class Nue extends Scene {
        Nue(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            Vec3 target = origin.add(dir);
            float k = Mth.clamp(a / 6, 0, 1);
            Vec3 sky = target.add(-dir.x * 0.3, 14, -dir.z * 0.3);
            Vec3 bird = sky.lerp(target.add(0, 1.5, 0), k * k);
            Vec3 fwd = target.subtract(sky).normalize(), side = FxDraw.perp(fwd);
            double flap = Math.sin(a * 1.5) * 0.6;
            d.ink();
            d.tri(bird, bird.add(side.scale(2.2)).add(0, flap, 0).subtract(fwd.scale(0.8)), bird.subtract(fwd.scale(1.2)), argb(0x08080C, 0.95f), argb(0x08080C, 0.6f), argb(0x08080C, 0.9f));
            d.tri(bird, bird.subtract(side.scale(2.2)).add(0, flap, 0).subtract(fwd.scale(0.8)), bird.subtract(fwd.scale(1.2)), argb(0x08080C, 0.95f), argb(0x08080C, 0.6f), argb(0x08080C, 0.9f));
            d.glow();
            d.flare(bird.add(fwd.scale(0.3)), 0.25, 0xFFF07A, 1);
            if (a >= 6) {
                float f = 1 - Mth.clamp((a - 6) / (duration - 6), 0, 1);
                d.bolt(target.add(0, 16, 0), target, seed + age, 2.5, 0.25, 0xFFF07A, f, 2);
                d.ring(target.add(0, 0.1, 0), UP, 1 + (a - 6) * 0.6, 0.2, 0xFFF07A, f);
                d.flare(target, 3 * f, 0xFFFFC0, f);
            }
        }

        @Override
        public float flash(float pt) {
            return age >= 6 && age < 8 ? 0.35f * falloff(origin.add(dir), 20) : 0;
        }

        @Override
        public float shake(float pt) {
            return age >= 6 && age < 12 ? 1.2f * falloff(origin.add(dir), 18) : 0;
        }
    }

    /** Absorb: the creature unravels into a spiral swallowed by the caster. Release: a rift tears open and the spirit emerges. */
    static final class Spirit extends Scene {
        private final boolean absorb;

        Spirit(ScenePayload p, boolean absorb) {
            super(p);
            this.absorb = absorb;
        }

        @Override
        public void render(FxDraw d, float pt) {
            float t = t(pt), f = envelope(pt, 3, 6);
            if (absorb) {
                Vec3 from = origin, to = origin.add(dir);
                for (int i = 0; i < 4; i++) {
                    double ph = i * Math.PI / 2 + time(pt) * 0.6;
                    Vec3 prev = from;
                    for (int k = 1; k <= 16; k++) {
                        double s = k / 16.0 * t;
                        Vec3 q = from.lerp(to, s).add(Math.cos(ph + s * 10) * (1 - s) * 0.8, Math.sin(ph + s * 10) * (1 - s) * 0.8, 0);
                        d.ribbon(prev, q, 0.12, argb(0x6A3FA0, f), argb(0xD0A0FF, f));
                        prev = q;
                    }
                }
                d.ink();
                d.sphere(to, 0.4 * t, 0x0A0012, 0.9f * f, 0.9f * f, 8, 12);
                d.glow();
            } else {
                Vec3 n = dir.lengthSqr() > 0 ? dir.normalize() : UP;
                double r = 1.4 * Math.min(1, time(pt) / 6);
                d.ink();
                d.disc(origin, n, 0, r, argb(0x05000A, 0.95f * f), argb(0x05000A, 0.7f * f), 28);
                d.glow();
                d.ring(origin, n, r, 0.12, 0xA070E0, f);
                d.bolt(origin.add(FxDraw.perp(n).scale(r)), origin.subtract(FxDraw.perp(n).scale(r)), seed + age, 0.4, 0.04, 0xD0A0FF, f, 1);
            }
        }
    }

    /** Maximum: Uzumaki — every stored spirit compressed into a churning vortex, then one catastrophic spiral beam. */
    static final class Uzumaki extends Scene {
        static final int FIRE = 22;

        Uzumaki(ScenePayload p) {
            super(p);
        }

        @Override
        public boolean wantsCamera() {
            return true;
        }

        @Override
        public CinematicCamera.Shot shot(float pt) {
            float a = time(pt);
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
            Vec3 body = origin.subtract(0, 1.62, 0);
            if (a < FIRE) {
                return CinematicCamera.Shot.orbit(body, yaw, 35 + a, 3.4, 1.8, origin.add(dir.normalize().scale(2)));
            }
            return CinematicCamera.Shot.looking(origin.subtract(dir.normalize().scale(3)).add(0, 0.8, 0), origin.add(dir.scale(0.6)));
        }

        @Override
        public void render(FxDraw d, float pt) {
            float a = time(pt);
            Vec3 n = dir.normalize();
            Vec3 c = origin.add(n.scale(2));
            if (a < FIRE + 4) {
                float g = Mth.clamp(a / FIRE, 0, 1);
                for (int i = 0; i < 6; i++) {
                    d.helix(c.subtract(n.scale(0.1)), c.add(n.scale(0.1)), 0.4 + g * 1.6 * (i + 1) / 6, 0.2, 1, a * (0.3 + i * 0.05), 0.15, i % 2 == 0 ? 0x6A3FA0 : 0x200030, 0.9f);
                    d.ring(c, n, 0.3 + g * 1.8 * (i + 1) / 6, 0.12, i % 2 == 0 ? 0xA070E0 : 0x401060, 0.8f, 40, a * 0.2 * (i + 1), Math.PI * 1.5);
                }
                d.ink();
                d.sphere(c, 0.6 * g, 0x05000A, 0.95f, 0.95f, 10, 16);
                d.glow();
            }
            if (a >= FIRE) {
                float k = Mth.clamp((a - FIRE) / 3, 0, 1);
                float f = 1 - Mth.clamp((a - FIRE - 6) / (duration - FIRE - 6), 0, 1);
                Vec3 end = c.add(dir.scale(k));
                d.beam(c, end, 1.4, 0x6A3FA0, f);
                d.helix(c, end, 2.2, dir.length() * 0.18, 4, a * 0.5, 0.3, 0xD0A0FF, f);
                d.ink();
                d.helix(c, end, 1.6, dir.length() * 0.18, 3, -a * 0.4, 0.35, 0x0A0010, f * 0.8f);
                d.glow();
            }
        }

        @Override
        public void overlay(GuiGraphics g, float pt, boolean watching) {
            if (watching) {
                float a = time(pt);
                SceneManager.title(g, Component.literal("極ノ番「うずまき」"), Component.literal("M A X I M U M :   U Z U M A K I"), 0xB080F0, Mth.clamp(Math.min((a - 4) / 3, (FIRE + 6 - a) / 4), 0, 1), 3f, 0.35f);
            }
        }

        @Override
        public float shake(float pt) {
            return age >= FIRE && age < FIRE + 10 ? 2.2f * falloff(origin, 30) : age < FIRE ? 0.3f * falloff(origin, 20) : 0;
        }
    }

    /** Cursed Speech "Twist": the target's silhouette is wrung by spiralling bands. */
    static final class Twist extends Scene {
        Twist(ScenePayload p) {
            super(p);
        }

        @Override
        public void render(FxDraw d, float pt) {
            float f = 1 - t(pt);
            Vec3 bottom = origin.subtract(0, 1, 0), top = origin.add(0, 1, 0);
            d.helix(bottom, top, 0.7 * f + 0.2, 2.5, 3, time(pt) * 1.2, 0.08, 0xFFFFFF, f);
            d.helix(bottom, top, 0.9 * f + 0.2, -2, 2, -time(pt), 0.05, 0xE6E6FF, f * 0.7f);
            d.flare(origin, 1.2 * f, 0xE6E6FF, f);
        }

        @Override
        public float shake(float pt) {
            return age < 4 ? 0.8f * falloff(origin, 10) : 0;
        }
    }

    @Nullable
    static Entity entityOrNull(Scene s) {
        return s.entity();
    }
}
