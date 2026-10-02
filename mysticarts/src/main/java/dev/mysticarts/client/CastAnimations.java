package dev.mysticarts.client;

import dev.mysticarts.power.Poses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Arm poses for each casting gesture, blended in over vanilla animation by the cast's envelope. */
public final class CastAnimations {
    private static final float PI = (float) Math.PI;

    private CastAnimations() {}

    public static void apply(HumanoidModel<?> model, LivingEntity entity, float age, float headYaw, float headPitch) {
        if (!(entity instanceof Player)) return;
        CasterStates.Anim anim = CasterStates.anim(entity.getId());
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float blend = anim.poseBlend(partial);
        float flight = anim.flight(partial);
        if (flight > 0.01f && blend < 0.99f) {
            // levitating: arms drift back, legs trail together
            float k = flight * (1f - blend);
            float bob = Mth.sin(age * 0.08f) * 0.05f;
            mix(model.rightArm, 0.35f + bob, 0f, 0.18f, k);
            mix(model.leftArm, 0.35f + bob, 0f, -0.18f, k);
            mix(model.rightLeg, 0.15f, 0f, 0.03f, k);
            mix(model.leftLeg, 0.15f, 0f, -0.03f, k);
        }
        if (blend <= 0.001f || anim.pose == Poses.NONE) return;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        float yaw = headYaw * Mth.DEG_TO_RAD;
        float t = age;
        switch (anim.pose) {
            case Poses.PUSH -> {
                mix(model.rightArm, -PI / 2 + pitch, yaw - 0.15f, 0, blend);
                mix(model.leftArm, -PI / 2 + pitch, yaw + 0.15f, 0, blend);
            }
            case Poses.RAISE -> {
                float sway = Mth.sin(t * 0.15f) * 0.08f;
                mix(model.rightArm, -PI * 0.88f + sway, 0, 0.45f, blend);
                mix(model.leftArm, -PI * 0.88f - sway, 0, -0.45f, blend);
            }
            case Poses.SHIELD -> {
                mix(model.leftArm, -PI / 2 + pitch * 0.8f, yaw + 0.45f, 0, blend);
                mix(model.rightArm, -0.6f, -0.3f, 0.1f, blend);
            }
            case Poses.WHIP -> {
                float lash = Mth.sin(t * 0.6f) * 0.35f;
                mix(model.rightArm, -1.2f + pitch * 0.6f + lash, yaw - 0.4f, 0.9f, blend);
                mix(model.leftArm, -1.2f + pitch * 0.6f - lash, yaw + 0.4f, -0.9f, blend);
            }
            case Poses.SNAP -> {
                mix(model.rightArm, -PI * 0.62f + pitch * 0.5f, yaw - 0.1f, 0.05f, blend);
                mix(model.leftArm, -0.2f, 0, -0.15f, blend);
            }
            case Poses.POINT -> {
                mix(model.rightArm, -PI / 2 + pitch, yaw - 0.05f, 0, blend);
                mix(model.leftArm, -0.4f, 0.1f, -0.1f, blend);
            }
            case Poses.SLAM -> {
                mix(model.rightArm, -0.4f, -0.2f, 0.3f, blend);
                mix(model.leftArm, -0.4f, 0.2f, -0.3f, blend);
            }
            case Poses.CIRCLE -> {
                float c = t * 0.5f;
                mix(model.rightArm, -1.45f + pitch * 0.6f + Mth.sin(c) * 0.25f, yaw - 0.2f + Mth.cos(c) * 0.3f, Mth.cos(c) * 0.2f, blend);
                mix(model.leftArm, -1.15f + pitch * 0.5f, yaw + 0.5f, 0, blend);
            }
            case Poses.CHARGE -> {
                float tremble = Mth.sin(t * 2.3f) * 0.03f;
                mix(model.rightArm, -1.05f + tremble, -0.6f, 0, blend);
                mix(model.leftArm, -1.05f - tremble, 0.6f, 0, blend);
            }
            case Poses.MUDRA -> {
                float turn = Mth.sin(t * 0.2f) * 0.12f;
                mix(model.rightArm, -1.3f + turn, -0.75f, 0.1f, blend);
                mix(model.leftArm, -1.3f - turn, 0.75f, -0.1f, blend);
            }
            default -> {}
        }
    }

    private static void mix(ModelPart part, float x, float y, float z, float k) {
        part.xRot = Mth.lerp(k, part.xRot, x);
        part.yRot = Mth.lerp(k, part.yRot, y);
        part.zRot = Mth.lerp(k, part.zRot, z);
    }
}
