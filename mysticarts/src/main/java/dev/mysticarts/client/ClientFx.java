package dev.mysticarts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.MaClientConfig;
import dev.mysticarts.client.render.FxDraw;
import dev.mysticarts.client.render.MaDraw;
import dev.mysticarts.client.render.MaRenderTypes;
import dev.mysticarts.client.render.ScreenFx;
import dev.mysticarts.client.render.WarpFx;
import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.network.Payloads;
import dev.mysticarts.power.Source;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.util.ColorUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** One-shot spell visuals, the Snap cinematic, and every contribution to the full-screen grade. */
public final class ClientFx {
    private static final class Effect {
        final int kind;
        final Vec3 pos;
        final Vec3 dir;
        final int color;
        final float a;
        final float b;
        final int entity;
        final int life;
        int age;

        Effect(Payloads.Fx p, int life) {
            this.kind = p.kind();
            this.pos = new Vec3(p.x(), p.y(), p.z());
            this.dir = new Vec3(p.dx(), p.dy(), p.dz());
            this.color = p.color();
            this.a = p.a();
            this.b = p.b();
            this.entity = p.entity();
            this.life = life;
        }

        float t(float partial) {
            return Mth.clamp((age + partial) / life, 0f, 1f);
        }
    }

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static float shake;
    private static float flash;
    private static int flashColor = 0xFFFFFF;
    private static float frozenGrade;
    private static int frozenTicks;
    private static float mirrorBlend;
    private static float mirrorFlash;
    /** Snap cinematic: caster id, ticks elapsed and length; -1 when idle. */
    private static int snapCaster = -1;
    private static int snapAge;
    private static int snapLength;
    private static float fieldRed, fieldOrange, fieldGreen, fieldGold, fieldBlue;
    private static float nextRed, nextOrange, nextGreen, nextGold, nextBlue;

    private ClientFx() {}

    // ============================================================================================ events in

    public static void handle(Payloads.Fx p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        RandomSource r = level.random;
        Vec3 pos = new Vec3(p.x(), p.y(), p.z());
        float q = MaClientConfig.particles();
        LocalPlayer me = mc.player;
        switch (p.kind()) {
            case FxKind.BURST -> burst(level, pos, p.color(), p.a(), (int) (20 * p.b() * q));
            case FxKind.RING -> add(p, (int) Math.max(8, p.b()));
            case FxKind.IMPACT -> {
                add(p, 10);
                burst(level, pos, p.color(), Math.max(0.4f, p.a() * 0.5f), (int) (14 * q * Math.max(1f, p.a())));
                level.addParticle(MaParticles.RING.get().with(p.color(), Math.max(0.6f, p.a()), 10), pos.x, pos.y, pos.z, 0, 0, 0);
            }
            case FxKind.SHIELD_HIT -> {
                add(p, 12);
                burst(level, pos, p.color(), 0.4f, (int) (8 * q));
            }
            case FxKind.CAST_CIRCLE -> add(p, (int) Math.max(10, p.b()));
            case FxKind.TELEPORT -> {
                Vec3 to = pos.add(p.dx(), p.dy(), p.dz());
                int n = (int) (Math.min(60, p.dx() * p.dx() + p.dy() * p.dy() + p.dz() * p.dz()) * q) + 6;
                for (int i = 0; i < n; i++) {
                    Vec3 at = pos.lerp(to, r.nextDouble());
                    Vec3 v = to.subtract(pos).normalize().scale(0.15);
                    level.addParticle(MaParticles.STREAK.get().with(p.color(), 0.4f, 12), at.x, at.y, at.z, v.x, v.y, v.z);
                }
                add(p, 12);
            }
            case FxKind.TIME_STOP -> {
                add(p, (int) p.b());
                if (me != null && me.position().distanceTo(pos) <= p.a() + 4) {
                    frozenTicks = Math.max(frozenTicks, (int) p.b());
                    flash = Math.max(flash, 0.35f);
                    flashColor = 0xB4FFC8;
                }
                shake = Math.max(shake, 0.4f);
            }
            case FxKind.TIME_RESUME -> {
                add(p, 16);
                if (me != null && me.position().distanceTo(pos) <= p.a() + 6) {
                    frozenTicks = 0;
                    flash = Math.max(flash, 0.25f);
                    flashColor = 0xD8FFE0;
                }
            }
            case FxKind.REWIND -> {
                add(p, (int) Math.max(20, p.b()));
                if (me != null && me.position().distanceTo(pos) <= p.a() + 4) {
                    frozenTicks = Math.max(frozenTicks, 12);
                    flash = Math.max(flash, 0.2f);
                    flashColor = 0x22E06A;
                }
            }
            case FxKind.DUST_AWAY -> add(p, 45);
            case FxKind.SCREEN -> {
                if (me != null) {
                    double d = me.position().distanceTo(pos);
                    float k = (float) Math.max(0.2, 1.0 - d / 48.0);
                    shake = Math.max(shake, p.a() * k);
                    if (p.b() * k > flash) {
                        flash = p.b() * k;
                        flashColor = p.color();
                    }
                }
            }
            case FxKind.WAVE -> {
                add(p, (int) Math.max(10, p.b()));
                for (int i = 0; i < 40 * q; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    level.addParticle(MaParticles.STREAK.get().with(p.color(), 0.6f, 18), pos.x, pos.y, pos.z, Math.cos(a) * 0.9, (r.nextDouble() - 0.5) * 0.3, Math.sin(a) * 0.9);
                }
            }
            case FxKind.ABSORB, FxKind.CHARGE_UP, FxKind.SOUL_RIP, FxKind.CLOCKS, FxKind.DETECT, FxKind.ARC, FxKind.CHAINS -> add(p, life(p));
            case FxKind.SNAP -> {
                snapCaster = p.entity();
                snapAge = 0;
                snapLength = (int) p.b();
                add(p, (int) p.b() + 40);
            }
            case FxKind.MIRROR -> {
                mirrorFlash = 1f;
                if (me != null) me.playSound(p.a() > 0.5f ? MaSounds.MIRROR_ENTER.get() : MaSounds.MIRROR_EXIT.get(), 0.6f, 1.2f);
            }
            case FxKind.TINT -> {
                flash = Math.max(flash, p.a());
                flashColor = p.color();
            }
            case FxKind.TRANSMUTE -> {
                for (int i = 0; i < 6 * q; i++) {
                    level.addParticle(MaParticles.SPARK.get().with(p.color(), 0.3f, 14), pos.x + (r.nextDouble() - 0.5), pos.y + (r.nextDouble() - 0.5),
                            pos.z + (r.nextDouble() - 0.5), 0, 0.05, 0);
                }
            }
            default -> {}
        }
    }

    private static int life(Payloads.Fx p) {
        return switch (p.kind()) {
            case FxKind.ARC -> 7;
            case FxKind.SOUL_RIP -> 24;
            case FxKind.CHAINS -> 16;
            case FxKind.DETECT -> 30;
            default -> (int) Math.max(10, p.b());
        };
    }

    private static void add(Payloads.Fx p, int life) {
        if (EFFECTS.size() < 256) EFFECTS.add(new Effect(p, Math.max(1, life)));
    }

    private static void burst(ClientLevel level, Vec3 pos, int color, float size, int count) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian() + 0.3, r.nextGaussian()).scale(0.12 + r.nextDouble() * 0.12);
            level.addParticle(MaParticles.SPARK.get().with(color, size * (0.6f + r.nextFloat() * 0.6f), 14 + r.nextInt(10)), pos.x, pos.y, pos.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < count / 4; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(0.04);
            level.addParticle(MaParticles.MOTE.get().with(ColorUtil.lerp(color, 0xFFFFFF, 0.3f), size * 1.4f, 20), pos.x, pos.y, pos.z, v.x, v.y, v.z);
        }
    }

    /** Spell fields report how strongly they envelop the camera each frame. */
    public static void reportField(int kind, float k) {
        switch (kind) {
            case FieldKind.REALITY_DISTORT, FieldKind.REALITY_REWRITE -> nextRed = Math.max(nextRed, k);
            case FieldKind.SPECTRAL_DIMENSION, FieldKind.SOUL_DOMINION -> nextOrange = Math.max(nextOrange, k);
            case FieldKind.TIME_STOP, FieldKind.TEMPORAL_SINGULARITY, FieldKind.TIME_SLOW -> nextGreen = Math.max(nextGreen, kind == FieldKind.TIME_SLOW ? k * 0.4f : k);
            case FieldKind.MIND_LIFT, FieldKind.PSYCHIC_FIELD -> nextGold = Math.max(nextGold, k);
            case FieldKind.COSMIC_RIFT, FieldKind.SPACE_RIFT_ULT, FieldKind.GRAVITY -> nextBlue = Math.max(nextBlue, k);
            default -> {}
        }
    }

    // ============================================================================================ ticking

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        RandomSource r = level.random;
        float q = MaClientConfig.particles();
        shake *= 0.86f;
        flash *= 0.84f;
        mirrorFlash *= 0.9f;
        if (frozenTicks > 0) frozenTicks--;
        frozenGrade = Mth.lerp(0.2f, frozenGrade, frozenTicks > 0 ? 1f : 0f);
        boolean mirror = ClientPower.has(Payloads.PowerSync.F_MIRROR);
        mirrorBlend = Mth.lerp(0.1f, mirrorBlend, mirror ? 1f : 0f);
        fieldRed = Mth.lerp(0.15f, fieldRed, nextRed);
        fieldOrange = Mth.lerp(0.15f, fieldOrange, nextOrange);
        fieldGreen = Mth.lerp(0.15f, fieldGreen, nextGreen);
        fieldGold = Mth.lerp(0.15f, fieldGold, nextGold);
        fieldBlue = Mth.lerp(0.15f, fieldBlue, nextBlue);
        nextRed = nextOrange = nextGreen = nextGold = nextBlue = 0f;

        if (snapCaster >= 0) {
            snapAge++;
            if (snapAge > snapLength + 40) snapCaster = -1;
            else snapTick(level, r);
        }

        Iterator<Effect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            if (++e.age > e.life) {
                it.remove();
                continue;
            }
            Entity ent = e.entity >= 0 ? level.getEntity(e.entity) : null;
            switch (e.kind) {
                case FxKind.ABSORB, FxKind.CHARGE_UP -> {
                    Vec3 c = ent != null ? ent.position().add(0, ent.getBbHeight() * 0.55, 0) : e.pos;
                    int n = (int) Math.max(1, 3 * q * (e.kind == FxKind.CHARGE_UP ? 1 + e.a : 1));
                    for (int i = 0; i < n; i++) {
                        double a = r.nextDouble() * Math.PI * 2, b = r.nextDouble() * Math.PI - Math.PI / 2;
                        double rad = (e.kind == FxKind.ABSORB ? e.a : 2.5 + e.a) * (0.7 + r.nextDouble() * 0.6);
                        Vec3 from = c.add(Math.cos(a) * Math.cos(b) * rad, Math.sin(b) * rad, Math.sin(a) * Math.cos(b) * rad);
                        level.addParticle(MaParticles.INFALL.get().with(e.color, 0.3f, 30), from.x, from.y, from.z, c.x, c.y, c.z);
                    }
                }
                case FxKind.SOUL_RIP -> {
                    if (ent != null && e.age < 18) {
                        Vec3 from = ent.position().add(0, ent.getBbHeight() * 0.6, 0);
                        Vec3 to = e.pos;
                        for (int i = 0; i < 2 * q + 1; i++) {
                            Vec3 at = from.lerp(to, r.nextDouble() * 0.3 + e.age / 18.0 * 0.7);
                            level.addParticle(MaParticles.MOTE.get().with(e.color, 0.35f, 20), at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.02, 0.02, (r.nextDouble() - 0.5) * 0.02);
                        }
                    }
                }
                case FxKind.DUST_AWAY -> {
                    // the body crumbles from the top down, flakes carried away on a wind
                    float w = Math.max(0.3f, e.a), h = Math.max(0.5f, e.b);
                    float front = 1f - e.age / (float) e.life;
                    Vec3 base = ent != null ? ent.position() : e.pos;
                    for (int i = 0; i < 8 * q + 2; i++) {
                        double y = base.y + h * Math.min(1.0, front + r.nextDouble() * 0.25);
                        double x = base.x + (r.nextDouble() - 0.5) * w * 1.2, z = base.z + (r.nextDouble() - 0.5) * w * 1.2;
                        level.addParticle(MaParticles.ASH.get().with(r.nextInt(3) == 0 ? 0xFFB070 : e.color, 0.45f, 50 + r.nextInt(30)), x, y, z,
                                0.04 + r.nextDouble() * 0.04, 0.02 + r.nextDouble() * 0.03, 0.02 + r.nextDouble() * 0.03);
                    }
                }
                case FxKind.REWIND -> {
                    if (e.age % 2 == 0) {
                        double a = -e.age * 0.4;
                        for (int i = 0; i < 3; i++) {
                            double rad = e.a * (1 - e.age / (double) e.life) * (0.3 + i * 0.3);
                            level.addParticle(MaParticles.RUNE.get().with(0x22E06A, 0.5f, 20), e.pos.x + Math.cos(a + i * 2.1) * rad, e.pos.y + (r.nextDouble() - 0.5) * 2,
                                    e.pos.z + Math.sin(a + i * 2.1) * rad, 0, 0.02, 0);
                        }
                    }
                }
                default -> {}
            }
        }
    }

    /** The Snap: stones ignite one by one around the caster's hand, the world drains toward white, then the click. */
    private static void snapTick(ClientLevel level, RandomSource r) {
        Entity caster = level.getEntity(snapCaster);
        if (caster == null) return;
        float t = snapAge / (float) Math.max(1, snapLength);
        Vec3 hand = caster.position().add(0, caster.getBbHeight() * 0.85, 0).add(caster.getLookAngle().scale(0.6));
        int lit = Mth.clamp((int) ((t - 0.15f) / 0.6f * 6), 0, 6);
        for (int i = 0; i < lit; i++) {
            Source s = Source.STONES[i];
            if (r.nextInt(3) == 0) level.addParticle(MaParticles.MOTE.get().with(s.color, 0.35f, 20), hand.x, hand.y, hand.z,
                    (r.nextDouble() - 0.5) * 0.08, r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.08);
        }
        if (snapAge > 0 && snapAge % 15 == 0 && lit > 0 && lit <= 6 && caster == Minecraft.getInstance().player) {
            Minecraft.getInstance().player.playSound(MaSounds.STONE_SELECT.get(), 0.8f, 0.6f + lit * 0.12f);
        }
        if (t > 0.6f && t < 1f) {
            for (int i = 0; i < 4; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double rad = 4 + r.nextDouble() * 6;
                level.addParticle(MaParticles.INFALL.get().with(Source.STONES[r.nextInt(6)].color, 0.4f, 30), hand.x + Math.cos(a) * rad,
                        hand.y + (r.nextDouble() - 0.5) * 6, hand.z + Math.sin(a) * rad, hand.x, hand.y, hand.z);
            }
        }
        if (snapAge == snapLength && Minecraft.getInstance().player != null) {
            shake = Math.max(shake, 1.6f);
            flash = 1.2f;
            flashColor = 0xFFF4D0;
        }
    }

    // ============================================================================================ world rendering

    public static void render(PoseStack ps, MultiBufferSource buffers, Vec3 cam, float partial) {
        if (EFFECTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        VertexConsumer rune = buffers.getBuffer(MaRenderTypes.RUNE);
        VertexConsumer energy = buffers.getBuffer(MaRenderTypes.ENERGY);
        PoseStack.Pose pose = ps.last();
        float time = (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
        for (Effect e : EFFECTS) {
            float t = e.t(partial);
            Vector3f c = MaDraw.vec(e.pos.subtract(cam));
            Vector3f n = e.dir.lengthSqr() > 1e-4 ? MaDraw.vec(e.dir.normalize()) : new Vector3f(0, 1, 0);
            float fade = 1f - t;
            switch (e.kind) {
                case FxKind.RING -> {
                    float rad = e.a * (float) Math.sqrt(t);
                    MaDraw.rune(rune, pose, c, n, rad, 0, MaDraw.RIPPLE, e.age, e.color, fade);
                    MaDraw.bandRing(rune, pose, c, n, rad, 0.25f + e.a * 0.04f, e.age, e.color, fade, 40);
                    WarpFx.disc(e.pos, perp(e.dir, 0).scale(rad), perp(e.dir, 1).scale(rad), WarpFx.RIPPLE, 1.2f, t, e.color);
                }
                case FxKind.IMPACT -> {
                    MaDraw.runeBillboard(rune, pose, c, e.a * (0.6f + t * 1.4f), 0, MaDraw.HALO, 1, e.color, fade * 1.2f);
                    MaDraw.runeBillboard(rune, pose, c, e.a * (0.3f + t), time * 0.2f, MaDraw.STAR, 2, ColorUtil.lerp(e.color, 0xFFFFFF, 0.5f), fade);
                    if (e.a > 1.5f) WarpFx.sphere(e.pos, e.a * (0.5f + t * 2f), WarpFx.RIPPLE, 1f, t, e.color);
                }
                case FxKind.SHIELD_HIT -> {
                    MaDraw.rune(rune, pose, c, n, 0.6f + e.a * 0.4f * t, time * 0.1f, MaDraw.RIPPLE, e.age, e.color, fade * 1.4f);
                    MaDraw.rune(rune, pose, c, n, 0.4f + e.a * 0.2f, time * 0.2f, MaDraw.SHIELD, 3, e.color, fade);
                }
                case FxKind.CAST_CIRCLE -> {
                    float open = Math.min(1f, (e.age + partial) / 6f);
                    MaDraw.rune(rune, pose, c, n, e.a * open, time * 0.05f, MaDraw.SIGIL, e.entity & 511, e.color, Math.min(1f, fade * 3f));
                }
                case FxKind.TELEPORT -> {
                    Vec3 to = e.pos.add(e.dir);
                    Vec3 eye = Vec3.ZERO;
                    FxDraw.beam(energy, pose, e.pos.subtract(cam), to.subtract(cam), eye, 0.5f * fade, e.color, fade);
                    MaDraw.rune(rune, pose, c, MaDraw.vec(e.dir.normalize()), 1.2f * (1 + t), time * 0.1f, MaDraw.RIM, 7, e.color, fade);
                    MaDraw.rune(rune, pose, MaDraw.vec(to.subtract(cam)), MaDraw.vec(e.dir.normalize()), 1.2f * (1 + t), -time * 0.1f, MaDraw.RIM, 8, e.color, fade);
                }
                case FxKind.TIME_STOP -> {
                    float grow = Math.min(1f, (e.age + partial) / 12f);
                    float out = Math.min(1f, (e.life - e.age - partial) / 10f);
                    float k = grow * out;
                    MaDraw.rune(rune, pose, c, new Vector3f(0, 1, 0), e.a * grow, time * 0.01f, MaDraw.CLOCK, 11, e.color, 0.55f * k);
                    MaDraw.bandRing(rune, pose, c, new Vector3f(0, 1, 0), e.a * grow, 0.6f, 12, e.color, 0.6f * k, 64);
                    if (e.age < 14) WarpFx.sphere(e.pos, e.a * grow, WarpFx.RIPPLE, 1.5f, grow, e.color);
                }
                case FxKind.TIME_RESUME -> {
                    MaDraw.rune(rune, pose, c, new Vector3f(0, 1, 0), e.a * (0.3f + t), -time * 0.05f, MaDraw.CLOCK, 13, e.color, fade);
                    WarpFx.sphere(e.pos, Math.max(1f, e.a) * (0.3f + t), WarpFx.RIPPLE, 1.2f, t, e.color);
                }
                case FxKind.REWIND, FxKind.CLOCKS -> {
                    float size = e.kind == FxKind.REWIND ? 2.5f : e.a;
                    float dirSign = e.kind == FxKind.REWIND ? -1f : 1f;
                    Vector3f axis = e.kind == FxKind.REWIND ? new Vector3f(0, 1, 0) : n;
                    float k = Math.min(1f, (e.age + partial) / 6f) * Math.min(1f, (e.life - e.age - partial) / 8f);
                    for (int i = 0; i < 3; i++) {
                        Vector3f ci = new Vector3f(c).add(new Vector3f(axis).mul((i - 1) * 0.35f));
                        MaDraw.rune(rune, pose, ci, axis, size * (1f - i * 0.22f), dirSign * time * (0.06f + i * 0.04f), MaDraw.CLOCK, 20 + i, e.color, k * (0.8f - i * 0.2f));
                    }
                    if (e.kind == FxKind.REWIND) WarpFx.sphere(e.pos, Math.max(2f, e.a * 0.6f), WarpFx.TEMPORAL, 1f, t, e.color);
                }
                case FxKind.WAVE -> {
                    float rad = e.a * (1f - (1f - t) * (1f - t));
                    MaDraw.bandRing(rune, pose, c, new Vector3f(0, 1, 0), rad, 0.8f + e.a * 0.03f, 30, e.color, fade, 96);
                    MaDraw.rune(rune, pose, c, new Vector3f(0, 1, 0), rad, 0, MaDraw.RIPPLE, 31, e.color, fade * 0.6f);
                    WarpFx.sphere(e.pos, Math.max(1f, rad), WarpFx.RIPPLE, 1.6f, t, e.color);
                }
                case FxKind.DETECT -> {
                    float rad = e.a * t;
                    MaDraw.bandRing(rune, pose, c, new Vector3f(0, 1, 0), rad, 0.5f, 40, e.color, fade, 96);
                }
                case FxKind.ARC -> {
                    Vec3 from = e.pos.subtract(cam), to = e.pos.add(e.dir).subtract(cam);
                    Vec3 prev = from;
                    RandomSource r = RandomSource.create(e.age * 31L + e.pos.hashCode());
                    for (int i = 1; i <= 8; i++) {
                        Vec3 at = from.lerp(to, i / 8.0);
                        if (i < 8) at = at.add((r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4);
                        FxDraw.beam(energy, pose, prev, at, Vec3.ZERO, Math.max(0.05f, e.a) * fade, e.color, fade);
                        prev = at;
                    }
                }
                case FxKind.CHARGE_UP -> {
                    Entity ent = mc.level == null || e.entity < 0 ? null : mc.level.getEntity(e.entity);
                    Vec3 at = ent != null ? ent.getPosition(partial).add(0, ent.getBbHeight() * 0.55, 0) : e.pos;
                    float k = Math.min(1f, (e.age + partial) / (float) e.life);
                    MaDraw.runeBillboard(rune, pose, MaDraw.vec(at.subtract(cam)), 0.8f + k * 1.6f * Math.max(1f, e.a), time * 0.15f, MaDraw.HALO, 4, e.color, 0.4f + k * 0.6f);
                    MaDraw.rune(rune, pose, MaDraw.vec(ent != null ? ent.getPosition(partial).add(0, 0.05, 0).subtract(cam) : e.pos.subtract(cam)),
                            new Vector3f(0, 1, 0), 1.5f + k * Math.max(1f, e.a), time * 0.08f, MaDraw.SIGIL, 5, e.color, 0.5f + k * 0.5f);
                    WarpFx.sphere(at, 1.2f + k * 2f, WarpFx.HAZE, k, k, e.color);
                }
                case FxKind.SNAP -> renderSnap(rune, pose, e, cam, partial, time);
                default -> {}
            }
        }
    }

    private static void renderSnap(VertexConsumer rune, PoseStack.Pose pose, Effect e, Vec3 cam, float partial, float time) {
        Minecraft mc = Minecraft.getInstance();
        Entity caster = mc.level == null ? null : mc.level.getEntity(e.entity);
        if (caster == null) return;
        float t = Mth.clamp((snapAge + partial) / Math.max(1, snapLength), 0f, 1.3f);
        Vec3 hand = caster.getPosition(partial).add(0, caster.getBbHeight() * 0.85, 0).add(caster.getLookAngle().scale(0.6));
        Vector3f h = MaDraw.vec(hand.subtract(cam));
        int lit = Mth.clamp((int) ((t - 0.15f) / 0.6f * 6), 0, 6);
        for (int i = 0; i < lit; i++) {
            Source s = Source.STONES[i];
            double a = i * Math.PI / 3 + time * 0.05;
            Vector3f at = new Vector3f(h).add((float) Math.cos(a) * 0.5f, (float) Math.sin(a) * 0.5f, 0);
            MaDraw.runeBillboard(rune, pose, at, 0.35f, time * 0.1f, MaDraw.STAR, 40 + i, s.color, 0.9f);
        }
        float glow = Mth.clamp((t - 0.6f) / 0.4f, 0f, 1f);
        MaDraw.runeBillboard(rune, pose, h, 0.6f + glow * 3f, time * 0.05f, MaDraw.STAR, 50, 0xFFE27A, 0.4f + glow * 0.6f);
        MaDraw.rune(rune, pose, MaDraw.vec(caster.getPosition(partial).add(0, 0.05, 0).subtract(cam)), new Vector3f(0, 1, 0), 2f + glow * 4f,
                time * 0.03f, MaDraw.SIGIL, 51, 0xFFE27A, 0.3f + glow * 0.5f);
        if (glow > 0) WarpFx.sphere(hand, 1.5f + glow * 3f, WarpFx.PINCH, glow, glow, 0xFFE27A);
    }

    private static Vec3 perp(Vec3 n, int which) {
        Vec3 nn = n.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : n.normalize();
        Vec3 ref = Math.abs(nn.y) > 0.95 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 u = ref.cross(nn).normalize();
        return which == 0 ? u : nn.cross(u).normalize();
    }

    // ============================================================================================ screen

    public static void compose(float partial, Matrix4f viewProj, Vec3 camera) {
        ScreenFx.reset();
        float s = MaClientConfig.screen();
        Minecraft mc = Minecraft.getInstance();
        ScreenFx.flash = flash * s;
        ScreenFx.flashColor = flashColor;
        ScreenFx.aberration = shake * 0.4f * s;
        ScreenFx.frozen = Math.max(frozenGrade, fieldGreen * 0.8f) * s;
        if (mirrorBlend > 0.01f) {
            ScreenFx.kaleido = mirrorBlend * s;
            ScreenFx.hue = mirrorBlend * 0.6f * s;
            ScreenFx.aberration += mirrorBlend * 0.25f * s;
            ScreenFx.tint = 0x9FE8FF;
            ScreenFx.tintStrength = mirrorBlend * 0.15f * s;
        }
        if (mirrorFlash > 0.01f) {
            ScreenFx.flash += mirrorFlash * 0.6f * s;
            ScreenFx.flashColor = 0xCFF6FF;
            ScreenFx.zoomBlur += mirrorFlash * 0.8f * s;
        }
        if (ClientPower.has(Payloads.PowerSync.F_ASTRAL)) {
            ScreenFx.tint = 0x9FD4FF;
            ScreenFx.tintStrength = 0.35f * s;
            ScreenFx.saturation = 0.45f;
            ScreenFx.vignette = 0.5f * s;
            ScreenFx.vignetteColor = 0x0A1A3A;
            ScreenFx.wave = 0.3f * s;
        }
        if (ClientPower.has(Payloads.PowerSync.F_ASTRAL_FORM)) {
            ScreenFx.tint = 0xB8E0FF;
            ScreenFx.tintStrength = Math.max(ScreenFx.tintStrength, 0.25f * s);
            ScreenFx.aberration += 0.3f * s;
        }
        if (fieldRed > 0.01f) {
            ScreenFx.tint = 0xFF5050;
            ScreenFx.tintStrength = Math.max(ScreenFx.tintStrength, fieldRed * 0.35f * s);
            ScreenFx.wave += fieldRed * 0.5f * s;
            ScreenFx.aberration += fieldRed * 0.5f * s;
        }
        if (fieldOrange > 0.01f) {
            ScreenFx.tint = 0xFF9A40;
            ScreenFx.tintStrength = Math.max(ScreenFx.tintStrength, fieldOrange * 0.3f * s);
            ScreenFx.vignette += fieldOrange * 0.25f * s;
            ScreenFx.vignetteColor = 0x3A1000;
        }
        if (fieldGold > 0.01f) {
            ScreenFx.tint = 0xFFE07A;
            ScreenFx.tintStrength = Math.max(ScreenFx.tintStrength, fieldGold * 0.2f * s);
        }
        if (fieldBlue > 0.01f) {
            ScreenFx.aberration += fieldBlue * 0.6f * s;
            ScreenFx.vignette += fieldBlue * 0.2f * s;
            ScreenFx.vignetteColor = 0x05103A;
        }
        if (snapCaster >= 0) {
            float t = snapAge / (float) Math.max(1, snapLength);
            float build = Mth.clamp((t - 0.2f) / 0.8f, 0f, 1f);
            ScreenFx.whiteout = build * 0.8f * s;
            ScreenFx.aberration += build * build * 1.2f * s;
            ScreenFx.vignette += build * 0.4f * s;
            ScreenFx.vignetteColor = 0x1A1000;
            Entity caster = mc.level == null ? null : mc.level.getEntity(snapCaster);
            if (caster != null) {
                float[] p = screen(viewProj, caster.position().add(0, caster.getBbHeight() * 0.85, 0), camera);
                if (p != null) {
                    ScreenFx.centerX = p[0];
                    ScreenFx.centerY = p[1];
                    ScreenFx.zoomBlur += build * build * 0.8f * s;
                }
            }
            if (t > 1f) ScreenFx.whiteout = Math.max(0, 1f - (t - 1f) * 3f) * s;
        }
    }

    private static float[] screen(Matrix4f viewProj, Vec3 world, Vec3 camera) {
        Vector4f v = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1f);
        viewProj.transform(v);
        if (v.w <= 0.01f) return null;
        return new float[] {v.x / v.w * 0.5f + 0.5f, v.y / v.w * 0.5f + 0.5f};
    }

    public static float shake() {
        return shake * MaClientConfig.screen();
    }

    public static boolean snapActive() {
        return snapCaster >= 0 && MaClientConfig.SNAP_CINEMATIC.get();
    }

    public static float snapProgress(float partial) {
        return snapCaster < 0 ? 0 : (snapAge + partial) / Math.max(1, snapLength);
    }

    public static int snapCaster() {
        return snapCaster;
    }

    public static void clear() {
        EFFECTS.clear();
        snapCaster = -1;
        frozenTicks = 0;
        shake = flash = 0;
    }
}
