package dev.finalframe.finisher;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Choreography coordinate frame: origin at the performer's feet when the finisher began, yaw facing
 * the target. Local axes are x = performer's right, y = up, z = forward (towards the target).
 */
public record AnchorFrame(Vec3 origin, float yaw) {
    public Vec3 forward() {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    public Vec3 right() {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
    }

    public Vec3 toWorld(double x, double y, double z) {
        Vec3 f = forward();
        Vec3 r = right();
        return new Vec3(origin.x + r.x * x + f.x * z, origin.y + y, origin.z + r.z * x + f.z * z);
    }

    public Vec3 toWorld(Vec3 local) {
        return toWorld(local.x, local.y, local.z);
    }

    public Vec3 toLocal(Vec3 world) {
        Vec3 d = world.subtract(origin);
        Vec3 f = forward();
        Vec3 r = right();
        return new Vec3(d.x * r.x + d.z * r.z, d.y, d.x * f.x + d.z * f.z);
    }

    /** Yaw (degrees) of a local horizontal direction expressed in the world. */
    public float worldYaw(float localYawOffset) {
        return yaw + localYawOffset;
    }
}
