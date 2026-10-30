package dev.infinitygauntlet.client.power;

import dev.infinitygauntlet.power.PowerPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * The {@code INFINITYGAUNTLET_POWER} arm pose added to {@link HumanoidModel.ArmPose} through META-INF/enumextensions.json.
 * One pose constant drives every Power Stone body animation: the transformer reads the player's current
 * {@link PowerPose} and its age and keys both arms by hand.
 */
public final class PowerArmPose {
    public static final EnumProxy<HumanoidModel.ArmPose> PROXY = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) PowerArmPose::apply);

    public static HumanoidModel.ArmPose get() {
        return PROXY.getValue();
    }

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        PowerPoses.State s = PowerPoses.get(entity.getId());
        if (s == null) {
            return;
        }
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float age = s.age + pt;
        float t = Mth.clamp(age / Math.max(1, s.ticks), 0, 1);
        boolean gauntlet = arm == s.gauntletArm;
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float out = arm == HumanoidArm.RIGHT ? 1 : -1;
        float aimX = -Mth.HALF_PI + model.head.xRot;
        float aimY = model.head.yRot;
        float tremble = Mth.sin(age * 2.7f) * 0.035f;
        switch (s.pose) {
            case CHARGE -> {
                if (gauntlet) {
                    set(part, aimX + 0.15f + tremble, aimY - out * 0.15f + tremble, out * 0.05f);
                } else {
                    set(part, aimX + 0.25f, aimY - out * 0.65f, 0); // bracing the gauntlet wrist
                }
            }
            case THRUST -> {
                float kick = 0.45f * (float) Math.exp(-age * 0.35f);
                if (gauntlet) {
                    set(part, aimX - kick + tremble * 0.5f, aimY, 0);
                } else {
                    set(part, 0.45f, 0, out * 0.35f);
                }
            }
            case PUNCH -> {
                float windup = smooth(age / 4f);
                float drive = smooth((age - 4) / 2f);
                if (gauntlet) {
                    float x = Mth.lerp(drive, Mth.lerp(windup, 0f, 0.75f), aimX);
                    set(part, x, Mth.lerp(drive, out * 0.35f, aimY - out * 0.1f), out * 0.1f * (1 - drive));
                } else {
                    set(part, -1.1f, -out * 0.55f, 0); // guard
                }
            }
            case SLAM -> {
                float up = smooth(t / 0.4f);
                float down = smooth((t - 0.4f) / 0.12f);
                float x = Mth.lerp(down, Mth.lerp(up, 0f, -Mth.PI + 0.25f), -0.35f);
                set(part, x, -out * 0.2f * (1 - down), out * 0.15f * up * (1 - down));
            }
            case SPREAD -> {
                float open = smooth(t / 0.25f);
                float burst = smooth((t - 0.85f) / 0.08f);
                set(part, -0.25f - 0.2f * burst + tremble * open, 0, out * (Mth.lerp(open, 0.1f, Mth.HALF_PI - 0.25f) + 0.25f * burst));
            }
            case RAISE -> {
                float up = smooth(t / 0.2f);
                if (gauntlet) {
                    set(part, Mth.lerp(up, 0f, -Mth.PI + 0.12f) + tremble, 0, out * 0.12f * up);
                } else {
                    set(part, -0.35f, 0, out * 0.25f);
                }
            }
            case HOLD -> {
                float grow = smooth(t / 0.15f);
                float strain = tremble * (1 + 2 * t);
                set(part, Mth.lerp(grow, 0f, -Mth.HALF_PI + model.head.xRot * 0.5f - 0.1f) + strain, -out * 0.4f * grow + strain, 0);
            }
            case OVERLOAD -> {
                if (gauntlet) {
                    part.xRot = part.xRot * 0.5f - 0.3f + tremble;
                    part.zRot = out * 0.22f;
                }
            }
        }
    }

    private static void set(ModelPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private PowerArmPose() {
    }
}
