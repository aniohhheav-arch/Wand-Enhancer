package dev.finalframe.client.anim;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** The humanoid parts a choreography may drive. Angles are radians in model space. */
public record Rig(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
    public static Rig of(HumanoidModel<?> model) {
        return new Rig(model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg);
    }

    public static void set(ModelPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }

    /** Blends a part's current (vanilla) rotation toward the given one by {@code w}. */
    public static void blend(ModelPart part, float x, float y, float z, float w) {
        part.xRot += (x - part.xRot) * w;
        part.yRot += (y - part.yRot) * w;
        part.zRot += (z - part.zRot) * w;
    }
}
