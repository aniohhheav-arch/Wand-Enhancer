package dev.riftverse.client.cinematic;

import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.TransitKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Directs every travel sequence. A journey is a timeline: surge or gravitational pull, crossing flash, the wormhole
 * itself (held until the server confirms arrival), then emergence with a sweeping arrival shot and title card.
 */
public final class CinematicDirector {
    public enum Phase { NONE, SURGE, PULL, CROSSING, TUNNEL, EMERGE }

    private static final int PULL_TICKS = 70;
    private static final int CROSS_TICKS = 12;
    private static final int EMERGE_TICKS = 40;
    private static final int ARRIVAL_SHOT_TICKS = 70;

    private static Phase phase = Phase.NONE;
    private static TransitKind kind = TransitKind.RIFT;
    private static int tick;
    private static int teleportTick;
    private static Vec3 focus = Vec3.ZERO;
    public static int colorA = 0xFFFFFF;
    public static int colorB = 0xFFFFFF;
    private static boolean arrived;
    private static int arrivedTick;
    private static float progressAtArrival;
    private static int emergeStart;
    private static boolean travelSoundPlayed;

    private static int shotTick = -1;
    private static Vec3 shotStart = Vec3.ZERO;
    private static int shotLength;

    private static float shake;
    private static int shakeTicks;
    private static float flash;
    private static int flashColor = 0xFFFFFF;

    @Nullable
    private static String title;
    private static String subtitle = "";
    private static int titleColor = 0xFFFFFF;
    private static int titleTick = -1;

    private static int bossEntity = -1;
    private static int bossTick = -1;

    private CinematicDirector() {}

    // ---------------------------------------------------------------- triggers

    public static void begin(int kindId, int teleportAt, Vec3 focusPos, int a, int b) {
        kind = TransitKind.byId(kindId);
        teleportTick = teleportAt;
        focus = focusPos;
        colorA = a;
        colorB = b;
        tick = 0;
        arrived = false;
        travelSoundPlayed = false;
        progressAtArrival = 0f;
        phase = kind == TransitKind.BLACK_HOLE ? Phase.PULL : Phase.SURGE;
        shotTick = -1;
    }

    public static void arrive(String t, String sub, int color) {
        arrived = true;
        arrivedTick = tick;
        progressAtArrival = wormholeProgress(0f);
        title = t;
        subtitle = sub;
        titleColor = color;
        if (phase == Phase.NONE) {
            phase = Phase.EMERGE;
            emergeStart = tick;
            startArrivalShot();
        }
    }

    public static void shake(float intensity, int duration, float flashAmount, int color) {
        shake = Math.max(shake, intensity);
        shakeTicks = Math.max(shakeTicks, duration);
        if (flashAmount > flash) {
            flash = flashAmount;
            flashColor = color;
        }
    }

    public static void bossIntro(int entityId, String name, String sub, int color) {
        bossEntity = entityId;
        bossTick = 0;
        title = name;
        subtitle = sub;
        titleColor = color;
        titleTick = 0;
        shake(0.6f, 60, 0.3f, color);
    }

    /** Shows a title card with letterbox and a soft shake, without moving the camera. */
    public static void announce(String name, String sub, int color) {
        title = name;
        subtitle = sub;
        titleColor = color;
        titleTick = 0;
    }

    public static void reset() {
        RealityCinematics.stop();
        phase = Phase.NONE;
        tick = 0;
        shotTick = -1;
        bossTick = -1;
        titleTick = -1;
        shake = 0;
        flash = 0;
        CameraRig.set(null);
    }

    // ---------------------------------------------------------------- queries

    public static boolean inJourney() {
        return phase != Phase.NONE;
    }

    public static Phase phase() {
        return phase;
    }

    public static TransitKind kind() {
        return kind;
    }

    public static Vec3 focus() {
        return focus;
    }

    public static boolean inputLocked() {
        if (bossTick >= 0 && bossTick < 90) return true;
        if (phase == Phase.NONE) return RealityCinematics.locksInput();
        if (phase == Phase.EMERGE) return tick - emergeStart < EMERGE_TICKS * 0.6f;
        return true;
    }

    private static float t(float partial) {
        return tick + partial;
    }

    /** 0..1 progress through the wormhole visuals. */
    public static float wormholeProgress(float partial) {
        float t = t(partial);
        if (kind == TransitKind.BLACK_HOLE) {
            float start = PULL_TICKS;
            float span = teleportTick + 50 - start;
            float p = (t - start) / span;
            if (!arrived) return Mth.clamp(p, 0f, 0.86f);
            float after = (t - arrivedTick) / 50f;
            return Mth.clamp(Math.max(p, progressAtArrival + (1f - progressAtArrival) * after), 0f, 1f);
        }
        float surge = surgeTicks();
        float p = (t - surge) / (teleportTick - surge + 25f);
        if (!arrived) return Mth.clamp(p, 0f, 0.8f);
        float after = (t - arrivedTick) / 20f;
        return Mth.clamp(Math.max(p, progressAtArrival + (1f - progressAtArrival) * after), 0f, 1f);
    }

    private static float surgeTicks() {
        return Math.max(8f, teleportTick * 0.35f);
    }

    /** Opacity of the wormhole overlay. */
    public static float overlay(float partial) {
        float t = t(partial);
        return switch (phase) {
            case NONE, PULL -> 0f;
            case SURGE -> CameraRig.easeIn((t - surgeTicks() * 0.6f) / (surgeTicks() * 0.4f));
            case CROSSING -> CameraRig.easeInOut((t - PULL_TICKS - 2) / (CROSS_TICKS - 4f));
            case TUNNEL -> 1f;
            case EMERGE -> 1f - CameraRig.easeOut((t - emergeStart) / EMERGE_TICKS);
        };
    }

    public static boolean blackHoleMode() {
        return kind == TransitKind.BLACK_HOLE;
    }

    public static float fovOffset(float partial) {
        float t = t(partial);
        float base = switch (phase) {
            case NONE -> 0f;
            case PULL -> 45f * CameraRig.easeIn(t / PULL_TICKS) + 6f * (float) Math.sin(t * 0.3) * (t / PULL_TICKS);
            case CROSSING -> 45f + 40f * CameraRig.easeOut((t - PULL_TICKS) / CROSS_TICKS);
            case SURGE -> 34f * CameraRig.easeIn(t / surgeTicks());
            case TUNNEL -> (kind == TransitKind.BLACK_HOLE ? 55f : 38f) + 6f * (float) Math.sin(t * 0.15);
            case EMERGE -> (kind == TransitKind.BLACK_HOLE ? 55f : 38f) * (1f - CameraRig.easeOut((t - emergeStart) / (EMERGE_TICKS * 0.8f)));
        };
        if (bossTick >= 0 && bossTick < 100) base -= 18f * (float) Math.sin(Math.min(1f, bossTick / 100f) * Math.PI);
        base += RealityCinematics.fovOffset(partial);
        return base;
    }

    public static float roll(float partial) {
        float t = t(partial);
        float r = switch (phase) {
            case PULL -> 38f * CameraRig.easeIn(t / PULL_TICKS) * (float) Math.sin(t * 0.05 + 0.6);
            case CROSSING, TUNNEL -> (float) Math.sin(t * 0.04) * 12f;
            case SURGE -> 6f * CameraRig.easeIn(t / surgeTicks());
            case EMERGE -> (float) Math.sin(t * 0.04) * 12f * (1f - CameraRig.easeOut((t - emergeStart) / EMERGE_TICKS));
            default -> 0f;
        };
        return r;
    }

    public static float shakeAmount(float partial) {
        float s = shakeTicks > 0 ? shake : 0f;
        float t = t(partial);
        if (phase == Phase.PULL) s += 0.15f + 0.65f * (t / PULL_TICKS);
        if (phase == Phase.CROSSING) s += 0.9f;
        if (phase == Phase.TUNNEL) s += kind == TransitKind.BLACK_HOLE ? 0.2f : 0.1f;
        if (phase == Phase.SURGE) s += 0.25f * (t / surgeTicks());
        return s;
    }

    public static float flash() {
        return flash;
    }

    public static int flashColor() {
        return flashColor;
    }

    public static float pullProgress(float partial) {
        return phase == Phase.PULL ? Mth.clamp(t(partial) / PULL_TICKS, 0f, 1f) : phase == Phase.CROSSING ? 1f : 0f;
    }

    public static float surgeProgress(float partial) {
        return phase == Phase.SURGE ? Mth.clamp(t(partial) / surgeTicks(), 0f, 1f) : 0f;
    }

    /** Letterbox amount 0..1. */
    public static float letterbox(float partial) {
        float l = 0f;
        if (phase == Phase.PULL) l = CameraRig.easeInOut(t(partial) / 25f);
        if (phase == Phase.CROSSING || phase == Phase.TUNNEL) l = 1f;
        if (phase == Phase.EMERGE || titleTick >= 0) {
            float tt = titleTick < 0 ? 0 : titleTick + partial;
            l = Math.max(l, titleTick < 0 ? 0f : 1f - CameraRig.easeInOut((tt - 100f) / 30f));
        }
        if (bossTick >= 0) l = Math.max(l, 1f - CameraRig.easeInOut((bossTick + partial - 110f) / 25f));
        l = Math.max(l, RealityCinematics.letterbox(partial));
        return Mth.clamp(l, 0f, 1f);
    }

    @Nullable
    public static String title() {
        return title;
    }

    public static String subtitle() {
        return subtitle;
    }

    public static int titleColor() {
        return titleColor;
    }

    /** Title card opacity 0..1. */
    public static float titleAlpha(float partial) {
        if (titleTick < 0) return 0f;
        float t = titleTick + partial;
        if (t < 15) return t / 15f;
        if (t > 110) return Math.max(0f, 1f - (t - 110) / 25f);
        return 1f;
    }

    public static float titleTime(float partial) {
        return titleTick < 0 ? 0f : titleTick + partial;
    }

    // ---------------------------------------------------------------- simulation

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        RealityCinematics.tick();
        if (shakeTicks > 0) shakeTicks--;
        else shake *= 0.85f;
        flash *= 0.88f;
        if (titleTick >= 0 && ++titleTick > 140) titleTick = -1;
        if (bossTick >= 0 && ++bossTick > 140) {
            bossTick = -1;
            bossEntity = -1;
        }
        if (shotTick >= 0 && ++shotTick > shotLength) shotTick = -1;
        if (phase == Phase.NONE || player == null) return;
        tick++;
        switch (phase) {
            case PULL -> {
                float k = tick / (float) PULL_TICKS;
                Vec3 to = focus.subtract(player.position().add(0, player.getBbHeight() * 0.5, 0));
                double d = to.length();
                if (d > 0.5) {
                    Vec3 dir = to.scale(1.0 / d);
                    Vec3 swirl = dir.cross(new Vec3(0, 1, 0));
                    if (swirl.lengthSqr() > 1e-4) swirl = swirl.normalize();
                    player.setDeltaMovement(dir.scale(0.15 + 0.95 * k * k).add(swirl.scale(0.25 * k)));
                    player.fallDistance = 0;
                }
                if (tick >= PULL_TICKS) {
                    phase = Phase.CROSSING;
                    flash = 1.0f;
                    flashColor = 0xFFD8A0;
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(RvSounds.WORMHOLE_TRAVEL.get(), 1.0f, 1.0f));
                    travelSoundPlayed = true;
                }
            }
            case CROSSING -> {
                player.setDeltaMovement(Vec3.ZERO);
                if (tick >= PULL_TICKS + CROSS_TICKS) phase = Phase.TUNNEL;
            }
            case SURGE -> {
                if (tick >= surgeTicks()) {
                    phase = Phase.TUNNEL;
                    flash = 0.7f;
                    flashColor = colorB;
                    if (!travelSoundPlayed) {
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(RvSounds.WORMHOLE_TRAVEL.get(), 1.25f, 0.7f));
                        travelSoundPlayed = true;
                    }
                }
            }
            case TUNNEL -> {
                player.setDeltaMovement(Vec3.ZERO);
                boolean done = arrived && wormholeProgress(0f) >= 1f;
                boolean timeout = tick > teleportTick + 160;
                if (done || timeout) {
                    phase = Phase.EMERGE;
                    emergeStart = tick;
                    flash = 1.0f;
                    flashColor = 0xFFFFFF;
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(RvSounds.WORMHOLE_EMERGE.get(), 1.0f, 0.9f));
                    if (title != null) titleTick = 0;
                    startArrivalShot();
                }
            }
            case EMERGE -> {
                if (tick - emergeStart > EMERGE_TICKS) phase = Phase.NONE;
            }
            default -> {}
        }
    }

    private static void startArrivalShot() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || kind == TransitKind.PORTAL) return;
        shotLength = kind == TransitKind.BLACK_HOLE ? ARRIVAL_SHOT_TICKS : 50;
        Vec3 eye = player.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        shotStart = eye.subtract(look.scale(kind == TransitKind.BLACK_HOLE ? 9 : 6)).add(0, kind == TransitKind.BLACK_HOLE ? 4 : 2.5, 0);
        shotTick = 0;
    }

    /** Computes the camera rig for this frame (called before the camera is set up). */
    public static void updateRig(float partial) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            CameraRig.set(null);
            return;
        }
        Vec3 eye = player.getEyePosition(partial);
        if (phase == Phase.NONE && bossTick < 0 && shotTick < 0 && RealityCinematics.updateRig(partial)) return;
        if (bossTick >= 0 && bossTick < 125 && mc.level != null) {
            Entity boss = mc.level.getEntity(bossEntity);
            if (boss != null) {
                float bt = bossTick + partial;
                Vec3 target = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
                Vec3 side = target.subtract(eye).cross(new Vec3(0, 1, 0)).normalize();
                Vec3 shotPos = eye.add(side.scale(2.5)).add(0, -0.6, 0).add(target.subtract(eye).normalize().scale(Math.min(6.0, bt * 0.05)));
                float in = CameraRig.easeInOut(bt / 20f);
                float out = 1f - CameraRig.easeInOut((bt - 100f) / 25f);
                float k = Math.min(in, out);
                Vec3 pos = eye.lerp(shotPos, k);
                float yaw = CameraRig.lerpAngle(k, player.getViewYRot(partial), CameraRig.yawTo(pos, target));
                float pitch = Mth.lerp(k, player.getViewXRot(partial), CameraRig.pitchTo(pos, target));
                CameraRig.set(new CameraRig.Pose(pos, yaw, pitch, 0f, k > 0.15f));
                return;
            }
        }
        if (phase == Phase.PULL || phase == Phase.CROSSING) {
            float k = pullProgress(partial);
            float e = CameraRig.easeIn(k);
            Vec3 axis = new Vec3(0, 1, 0);
            Vec3 toFocus = focus.subtract(eye);
            Vec3 side = toFocus.cross(axis);
            if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
            side = side.normalize();
            double spiral = Math.sin(k * Math.PI) * toFocus.length() * 0.18;
            double ang = k * 4.5;
            Vec3 offset = side.scale(Math.cos(ang) * spiral).add(axis.scale(Math.sin(ang) * spiral * 0.6));
            Vec3 pos = eye.lerp(focus, Math.min(0.97, e * 0.97)).add(offset);
            float yaw = CameraRig.yawTo(pos, focus);
            float pitch = CameraRig.pitchTo(pos, focus);
            float blend = CameraRig.easeInOut(k * 4f);
            yaw = CameraRig.lerpAngle(blend, player.getViewYRot(partial), yaw);
            pitch = Mth.lerp(blend, player.getViewXRot(partial), pitch);
            CameraRig.set(new CameraRig.Pose(pos, yaw, pitch, roll(partial), e > 0.05f));
            return;
        }
        if (shotTick >= 0) {
            float st = (shotTick + partial) / shotLength;
            float e = CameraRig.easeInOut(st);
            Vec3 pos = shotStart.lerp(eye, e);
            Vec3 lookTarget = eye.add(0, -0.25, 0);
            float yawLook = CameraRig.yawTo(pos, lookTarget);
            float pitchLook = CameraRig.pitchTo(pos, lookTarget);
            float settle = CameraRig.easeInOut((st - 0.55f) / 0.45f);
            float yaw = CameraRig.lerpAngle(settle, yawLook, player.getViewYRot(partial));
            float pitch = Mth.lerp(settle, pitchLook, player.getViewXRot(partial));
            CameraRig.set(new CameraRig.Pose(pos, yaw, pitch, roll(partial), e < 0.97f));
            return;
        }
        CameraRig.set(null);
    }
}
