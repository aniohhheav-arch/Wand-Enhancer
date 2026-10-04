package dev.finalframe.client.camera;

import dev.finalframe.finisher.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A camera placement. {@code focus} is the point the shot frames; the collision solver pulls the camera
 * toward it when terrain would block the view.
 */
public record CameraPose(Vec3 position, float yaw, float pitch, float roll, float fov, @Nullable Vec3 focus) {
    public static CameraPose lookAt(Vec3 position, Vec3 target, float roll, float fov) {
        Vec3 d = target.subtract(position);
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, horizontal) * Mth.RAD_TO_DEG);
        return new CameraPose(position, yaw, pitch, roll, fov, target);
    }

    public CameraPose withFov(float newFov) {
        return new CameraPose(position, yaw, pitch, roll, newFov, focus);
    }

    public CameraPose offset(float dYaw, float dPitch, float dRoll) {
        return new CameraPose(position, yaw + dYaw, pitch + dPitch, roll + dRoll, fov, focus);
    }

    public CameraPose moved(Vec3 newPosition) {
        return new CameraPose(newPosition, yaw, pitch, roll, fov, focus);
    }

    /** Interpolates position, angles (shortest path) and FOV. */
    public static CameraPose blend(CameraPose a, CameraPose b, float t) {
        t = Ease.clamp01(t);
        Vec3 p = new Vec3(Mth.lerp(t, a.position.x, b.position.x), Mth.lerp(t, a.position.y, b.position.y),
            Mth.lerp(t, a.position.z, b.position.z));
        float yaw = a.yaw + Mth.wrapDegrees(b.yaw - a.yaw) * t;
        Vec3 focus = a.focus == null ? b.focus : b.focus == null ? a.focus
            : new Vec3(Mth.lerp(t, a.focus.x, b.focus.x), Mth.lerp(t, a.focus.y, b.focus.y), Mth.lerp(t, a.focus.z, b.focus.z));
        return new CameraPose(p, yaw, Mth.lerp(t, a.pitch, b.pitch), Mth.lerp(t, a.roll, b.roll), Mth.lerp(t, a.fov, b.fov), focus);
    }
}
