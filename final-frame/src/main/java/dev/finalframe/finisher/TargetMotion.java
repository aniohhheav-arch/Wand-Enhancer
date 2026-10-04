package dev.finalframe.finisher;

import net.minecraft.world.phys.Vec3;

/**
 * Choreographed target state at an instant.
 *
 * @param position  world position of the target's feet
 * @param bodyYaw   body yaw (degrees)
 * @param headYaw   head yaw (degrees)
 * @param leanPitch forward stumble lean around the feet (degrees, positive = falls forward)
 * @param leanRoll  side sway (degrees)
 */
public record TargetMotion(Vec3 position, float bodyYaw, float headYaw, float leanPitch, float leanRoll) {
}
