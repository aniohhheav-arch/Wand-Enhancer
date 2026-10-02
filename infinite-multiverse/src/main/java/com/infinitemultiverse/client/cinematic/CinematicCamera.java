package com.infinitemultiverse.client.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Camera shots for cutscenes, eased in from and back out to the player's own view. */
public final class CinematicCamera {
    public record Shot(Vec3 pos, float yaw, float pitch) {
        public static Shot looking(Vec3 pos, Vec3 target) {
            Vec3 d = target.subtract(pos);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
            float pitch = (float) -(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
            return new Shot(pos, yaw, pitch);
        }

        /** Orbit shot around {@code center} at {@code angleDeg} (0 = in front of yaw), looking at target. */
        public static Shot orbit(Vec3 center, float baseYaw, float angleDeg, double distance, double height, Vec3 target) {
            double a = Math.toRadians(baseYaw + angleDeg);
            Vec3 pos = center.add(-Math.sin(a) * distance, height, Math.cos(a) * distance);
            return looking(pos, target);
        }

        public Shot lerp(Shot o, float t) {
            float e = t * t * (3 - 2 * t);
            return new Shot(pos.lerp(o.pos, e), Mth.rotLerp(e, yaw, o.yaw), Mth.lerp(e, pitch, o.pitch));
        }
    }

    private static final float BLEND = 8f;

    private CinematicCamera() {
    }

    /** Called from the camera mixin; null means vanilla camera. */
    @Nullable
    public static Shot current(float pt) {
        Scene scene = SceneManager.cutscene();
        Minecraft mc = Minecraft.getInstance();
        Entity viewer = mc.getCameraEntity();
        if (scene == null || viewer == null) {
            return null;
        }
        Shot target = scene.shot(pt);
        if (target == null) {
            return null;
        }
        Shot own = new Shot(viewer.getEyePosition(pt), viewer.getViewYRot(pt), viewer.getViewXRot(pt));
        float a = scene.time(pt);
        float blend = Math.min(a / BLEND, (scene.duration - a) / BLEND);
        if (blend >= 1) {
            return target;
        }
        // Too close to the player's own eye: hand back to the vanilla first-person camera instead of a detached one.
        if (blend < 0.15f) {
            return null;
        }
        return own.lerp(target, Mth.clamp((blend - 0.15f) / 0.85f, 0, 1));
    }
}
