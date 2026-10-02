package dev.riftverse.client;

import dev.riftverse.client.cinematic.CinematicDirector;
import dev.riftverse.client.render.ScreenFx;
import dev.riftverse.client.sound.BlackHoleSound;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.universe.UniverseSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Client-side behaviour of anomalies and the composition of every screen-space effect. */
public final class ClientEffects {
    private static float holeProximity;
    private static Vec3 holeCenter = Vec3.ZERO;
    private static float riftProximity;
    private static Vec3 riftCenter = Vec3.ZERO;
    private static float nextHole;
    private static Vec3 nextHoleCenter = Vec3.ZERO;
    private static float glitchBurst;

    private ClientEffects() {}

    public static void startBlackHoleSound(BlackHoleEntity entity) {
        Minecraft.getInstance().getSoundManager().play(new BlackHoleSound(entity));
    }

    public static void blackHoleTick(BlackHoleEntity hole) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource r = hole.getRandom();
        Vec3 c = hole.position();
        float horizon = hole.horizonRadius() * hole.bloom(0);
        Vector3f n = hole.diskNormal();
        Vec3 axis = new Vec3(n.x(), n.y(), n.z());
        Vec3 u = axis.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = axis.cross(u).normalize();
        int count = hole.style() == BlackHoleEntity.STYLE_GRAVITY ? 1 : (int) Math.min(6, 1 + horizon);
        int hot = hole.style() == BlackHoleEntity.STYLE_NEXUS ? 0xFFD27A : hole.style() == BlackHoleEntity.STYLE_WARDEN ? 0xFFC14D : 0xFF9A50;
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double rad = horizon * (2.4 + r.nextDouble() * 5.5);
            Vec3 p = c.add(u.scale(Math.cos(a) * rad)).add(v.scale(Math.sin(a) * rad)).add(axis.scale((r.nextDouble() - 0.5) * horizon * 0.4));
            mc.level.addParticle(RvParticles.INFALL.get().with(r.nextInt(3) == 0 ? 0xFFE6C0 : hot, 0.25f + (float) horizon * 0.08f, 60), p.x, p.y, p.z, c.x, c.y, c.z);
        }
        if (r.nextInt(6) == 0 && hole.style() != BlackHoleEntity.STYLE_GRAVITY) {
            double side = r.nextBoolean() ? 1 : -1;
            Vec3 jet = axis.scale(side * (0.8 + r.nextDouble() * 0.4));
            Vec3 p = c.add(axis.scale(side * horizon * 1.2));
            mc.level.addParticle(RvParticles.STREAK.get().with(0xB0C8FF, 0.6f, 30), p.x, p.y, p.z, jet.x, jet.y, jet.z);
        }

        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator() || hole.isNexusCore()) return;
        Vec3 to = c.subtract(player.position().add(0, player.getBbHeight() * 0.5, 0));
        double d = to.length();
        float influence = hole.influenceRadius();
        if (d < influence) {
            float k = (float) (1.0 - d / influence);
            if (k > nextHole) {
                nextHole = k;
                nextHoleCenter = c;
            }
            if (!CinematicDirector.inJourney() && !player.getAbilities().flying) {
                Vec3 pull = hole.pullAt(to, d, horizon);
                if (!hole.captures()) pull = pull.scale(0.6);
                player.setDeltaMovement(player.getDeltaMovement().scale(0.97).add(pull.scale(0.85)));
                player.fallDistance = 0;
            }
        }
    }

    public static void bossAura(Entity entity, int color) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource r = entity.getRandom();
        if (mc.level == null || r.nextInt(2) != 0) return;
        double a = r.nextDouble() * Math.PI * 2;
        double rad = entity.getBbWidth() * 1.4;
        mc.level.addParticle(RvParticles.MOTE.get().with(color, 0.5f, 30), entity.getX() + Math.cos(a) * rad, entity.getY() + r.nextDouble() * entity.getBbHeight(),
                entity.getZ() + Math.sin(a) * rad, 0, 0.04, 0);
    }

    /** Rift renderers report how close the camera is to them each frame. */
    public static void reportRift(Vec3 center, double distance) {
        float k = (float) Mth.clamp(1.0 - distance / 9.0, 0.0, 1.0);
        if (k > riftProximity) {
            riftProximity = k;
            riftCenter = center;
        }
    }

    public static void tick() {
        holeProximity = Mth.lerp(0.3f, holeProximity, nextHole);
        holeCenter = nextHoleCenter;
        nextHole = 0f;
        glitchBurst *= 0.85f;
        UniverseSpec s = ClientUniverseState.spec();
        Minecraft mc = Minecraft.getInstance();
        if (s != null && s.glitch > 0.2f && mc.level != null && mc.level.random.nextFloat() < s.glitch * 0.02f) glitchBurst = 0.6f + s.glitch * 0.4f;
    }

    public static float ambientShake() {
        return holeProximity * holeProximity * 0.6f + riftProximity * 0.05f;
    }

    public static float ambientFov() {
        return holeProximity * holeProximity * 14f;
    }

    private static float[] screenPos(Matrix4f viewProj, Vec3 world, Vec3 camera) {
        Vector4f v = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1f);
        viewProj.transform(v);
        if (v.w <= 0.01f) return null;
        return new float[] {v.x / v.w * 0.5f + 0.5f, v.y / v.w * 0.5f + 0.5f};
    }

    /** Sums every source of screen effects for this frame. */
    public static void compose(float partial, Matrix4f viewProj, Vec3 camera) {
        ScreenFx.reset();
        UniverseSpec s = ClientUniverseState.spec();
        if (s != null) {
            ScreenFx.tint = s.gradeTint;
            ScreenFx.tintStrength = s.gradeStrength;
            ScreenFx.saturation = s.saturation;
            ScreenFx.glitch = s.glitch * 0.08f + glitchBurst;
            ScreenFx.wave = s.dreamlike ? 0.35f : 0f;
            ScreenFx.grain = 0.04f;
            ScreenFx.vignette = 0.18f;
            if (s.archetype == dev.riftverse.universe.Archetype.HOLLOW) ScreenFx.vignette = 0.45f;
        }
        if (holeProximity > 0.01f) {
            float k = holeProximity;
            ScreenFx.lens += k * k * 0.9f;
            ScreenFx.echo += k * k * 0.35f;
            ScreenFx.hue += k * k * k * 0.3f;
            ScreenFx.aberration += k * 0.9f;
            ScreenFx.vignette += k * 0.45f;
            ScreenFx.zoomBlur += k * k * 0.5f;
            float[] p = screenPos(viewProj, holeCenter, camera);
            if (p != null) {
                ScreenFx.centerX = p[0];
                ScreenFx.centerY = p[1];
                ScreenFx.swirl += k * k * 0.9f;
            }
        }
        if (riftProximity > 0.01f) {
            ScreenFx.aberration += riftProximity * 0.35f;
            ScreenFx.vignette += riftProximity * 0.15f;
        }
        riftProximity = 0f;

        CinematicDirector.Phase phase = CinematicDirector.phase();
        if (phase == CinematicDirector.Phase.PULL || phase == CinematicDirector.Phase.CROSSING) {
            float k = CinematicDirector.pullProgress(partial);
            ScreenFx.aberration += 0.5f + 1.6f * k;
            ScreenFx.zoomBlur += 1.1f * k;
            ScreenFx.swirl += 2.4f * k * k;
            ScreenFx.vignette += 0.35f + 0.45f * k;
            ScreenFx.warp += 0.25f * k;
            ScreenFx.grain += 0.1f;
            if (CinematicDirector.blackHoleMode()) mindBend(k, phase == CinematicDirector.Phase.CROSSING, partial);
            float[] p = screenPos(viewProj, CinematicDirector.focus(), camera);
            if (p != null) {
                ScreenFx.centerX = p[0];
                ScreenFx.centerY = p[1];
            }
        } else if (phase == CinematicDirector.Phase.SURGE) {
            float k = CinematicDirector.surgeProgress(partial);
            ScreenFx.aberration += 1.3f * k;
            ScreenFx.zoomBlur += 1.4f * k;
            ScreenFx.warp += 0.3f * k;
            ScreenFx.vignette += 0.3f * k;
            float[] p = screenPos(viewProj, CinematicDirector.focus(), camera);
            if (p != null) {
                ScreenFx.centerX = Mth.clamp(p[0], 0.2f, 0.8f);
                ScreenFx.centerY = Mth.clamp(p[1], 0.2f, 0.8f);
            }
        } else if (phase == CinematicDirector.Phase.EMERGE) {
            float k = 1f - CinematicDirector.overlay(partial);
            ScreenFx.aberration += (1f - k) * 1.4f;
            ScreenFx.warp -= (1f - k) * 0.2f;
        }
        float protocol = RealityCinematicsBridge.blackHoleWarp(partial);
        if (protocol > 0.01f) mindBend(protocol, false, partial);
        float shake = CinematicDirector.shakeAmount(partial) + ambientShake();
        ScreenFx.aberration += shake * 0.3f;
    }

    /**
     * Falling into a black hole, escalating in stages: first the Einstein ring and ghost echoes of the world; then
     * spaghettification, hue drift and flickering mirror folds; finally a spinning kaleidoscope and the picture
     * repeating inside itself forever, with inversion flashes as each threshold is crossed.
     */
    private static void mindBend(float k, boolean crossing, float partial) {
        float time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + partial;
        ScreenFx.lens += 0.3f + 0.9f * smooth(k, 0f, 0.35f);
        ScreenFx.echo += 0.6f * smooth(k, 0.1f, 0.5f);
        ScreenFx.stretch += 1.1f * smooth(k, 0.3f, 0.7f);
        ScreenFx.hue += 0.8f * smooth(k, 0.35f, 0.8f);
        ScreenFx.fold += smooth(k, 0.4f, 0.7f) * (0.5f + 0.5f * (float) Math.sin(time * 0.31));
        ScreenFx.kaleido += smooth(k, 0.6f, 0.95f);
        ScreenFx.droste += smooth(k, 0.65f, 1f);
        float pulse = 0f;
        for (float edge : new float[] {0.35f, 0.6f, 0.85f}) pulse += (float) Math.exp(-Math.pow((k - edge) * 35f, 2));
        if (crossing) {
            ScreenFx.kaleido = 1f;
            ScreenFx.droste = 1f;
            pulse += 0.5f + 0.5f * (float) Math.sin(time * 1.7);
        }
        ScreenFx.invert += Math.min(1f, pulse) * 0.9f;
        ScreenFx.glitch += 0.15f * k;
    }

    private static float smooth(float x, float a, float b) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }
}
