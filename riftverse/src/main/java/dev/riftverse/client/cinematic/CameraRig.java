package dev.riftverse.client.cinematic;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A scripted camera pose that temporarily replaces the player's eyes. Applied by the Camera mixin. */
public final class CameraRig {
    public record Pose(Vec3 position, float yaw, float pitch, float roll, boolean detached) {}

    @Nullable
    private static Pose pose;

    private CameraRig() {}

    public static void set(@Nullable Pose p) {
        pose = p;
    }

    @Nullable
    public static Pose current() {
        return pose;
    }

    public static float yawTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
    }

    public static float pitchTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
    }

    public static float lerpAngle(float t, float a, float b) {
        return a + Mth.wrapDegrees(b - a) * t;
    }

    public static float easeInOut(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float easeOut(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    public static float easeIn(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * t;
    }
}
